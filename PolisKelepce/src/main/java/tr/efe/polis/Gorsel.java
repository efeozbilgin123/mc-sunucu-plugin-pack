package tr.efe.polis;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Görsel şeyler (resource pack gerektirmez):
 * - Ekran yazısı: GÖZALTINA ALINDIN / SERBESTSİN / YARALANDIN ...
 * - Durum çubuğu (bossbar): gözaltı, yaralı geri sayımı, ödenmemiş ceza süresi
 * - /siren: polisin kafasında kırmızı-mavi ışık ve siren sesi
 * - Karakol panosu: havada duran canlı yazı, borçlular ve gözaltındakiler
 */
public final class Gorsel implements Listener, CommandExecutor, TabCompleter {

    private final PolisPlugin plugin;
    private final File dosya;
    private final NamespacedKey panoAnahtari;
    private final Map<UUID, BossBar> cubuklar = new HashMap<>();
    /** sireni açık polis -> kapanma anı */
    private final Map<UUID, Long> sirenler = new HashMap<>();
    /** Pano yerleri (en üst satırın konumu). Satırlar görünmez zırh standı isimleri: Bedrock'ta da görünür. */
    private final List<Location> panolar = new ArrayList<>();
    /** 1.9'daki TextDisplay panoları: bulununca yerine standlı pano kurulur. */
    private final List<UUID> eskiPanolar = new ArrayList<>();
    private final Map<String, List<UUID>> standlar = new HashMap<>();
    private int faz = 0;
    private int saniye = 0;

    public Gorsel(PolisPlugin plugin) {
        this.plugin = plugin;
        this.dosya = new File(plugin.getDataFolder(), "gorsel.yml");
        this.panoAnahtari = new NamespacedKey(plugin, "pano");
    }

    private boolean ayar(String yol) {
        return plugin.getConfig().getBoolean("gorsel." + yol, true);
    }

    // ------------------------------------------------------------------
    // Ekran yazısı
    // ------------------------------------------------------------------

    public void baslik(Player p, String ust, String alt, NamedTextColor renk, Sound ses, float perde) {
        if (ses != null) p.playSound(p.getLocation(), ses, 1f, perde);
        if (!ayar("ekran-yazisi")) return;
        p.showTitle(Title.title(
                Component.text(ust, renk).decoration(TextDecoration.BOLD, true),
                Component.text(alt, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(750))));
    }

    // ------------------------------------------------------------------
    // Zamanlayıcılar
    // ------------------------------------------------------------------

    /** Her saniye: bossbar ve kafa etiketleri; 10 saniyede bir pano. */
    public void tikSaniye() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            cubukGuncelle(p);
            plugin.etiket().guncelle(p);
        }
        if (++saniye % 10 == 0) panolariGuncelle();
    }

    /** Her 5 tick: siren. */
    public void tikHizli() {
        if (sirenler.isEmpty()) return;
        faz++;
        long simdi = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(sirenler.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || simdi > sirenler.get(id) || !plugin.gozalti().polisMi(p)) {
                sirenler.remove(id);
                if (p != null) plugin.mesaj(p, "Siren kapandı.", NamedTextColor.GRAY);
                continue;
            }
            World w = p.getWorld();
            Location bas = p.getLocation().add(0, 2.35, 0);
            org.bukkit.util.Vector yan = p.getLocation().getDirection().setY(0);
            if (yan.lengthSquared() > 0) yan.normalize();
            yan = new org.bukkit.util.Vector(-yan.getZ(), 0, yan.getX()).multiply(0.3);
            boolean kirmiziSolda = faz % 2 == 0;
            Particle.DustOptions kirmizi = new Particle.DustOptions(Color.fromRGB(0xFF1010), 1.3f);
            Particle.DustOptions mavi = new Particle.DustOptions(Color.fromRGB(0x1040FF), 1.3f);
            w.spawnParticle(Particle.DUST, bas.clone().add(yan), 5, 0.06, 0.06, 0.06, 0, kirmiziSolda ? kirmizi : mavi);
            w.spawnParticle(Particle.DUST, bas.clone().subtract(yan), 5, 0.06, 0.06, 0.06, 0, kirmiziSolda ? mavi : kirmizi);
            if (faz % 2 == 0) {
                // iki notalı "vii-vuu" siren; ses 2.0 ile ~32 bloktan duyulur
                float perde = (faz / 2) % 2 == 0 ? 1.6f : 1.1f;
                w.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 2f, perde);
            }
        }
    }

    // ------------------------------------------------------------------
    // Durum çubuğu (bossbar)
    // ------------------------------------------------------------------

    private void cubukGuncelle(Player p) {
        Component yazi = null;
        BossBar.Color renk = BossBar.Color.WHITE;
        float ilerleme = 1f;

        long[] yara = plugin.yarali().kalan(p);
        String polis = plugin.gozalti().polisIsmi(p);
        CezaSistemi.BorcOzet borc = plugin.ceza().borcOzet(p.getUniqueId());
        if (yara != null && polis != null) {
            yazi = Component.text("YARALI ve GÖZALTINDA - Polis: " + polis + "  |  süre durdu, biri seni kaldırabilir", NamedTextColor.RED);
            renk = BossBar.Color.RED;
            ilerleme = yara[1] > 0 ? (float) yara[0] / yara[1] : 0f;
        } else if (yara != null) {
            yazi = Component.text("YARALISIN - " + (yara[0] / 1000 + 1) + " sn içinde kaldırılmazsan öleceksin  |  pes et: /ol", NamedTextColor.RED);
            renk = BossBar.Color.RED;
            ilerleme = yara[1] > 0 ? (float) yara[0] / yara[1] : 0f;
        } else if (polis != null) {
            boolean surukleniyor = plugin.gozalti().surukleniyorMu(p);
            yazi = Component.text("Gözaltındasın - Polis: " + polis + (surukleniyor ? " (götürülüyorsun)" : " (bekletiliyorsun)"),
                    NamedTextColor.AQUA);
            renk = BossBar.Color.BLUE;
        } else if (borc != null) {
            long simdi = System.currentTimeMillis();
            String tur = borc.vergi() ? "vergi" : "ceza";
            String adet = borc.adet() > 1 ? borc.adet() + " ödenmemiş " + tur : "Ödenmemiş " + tur;
            if (borc.gec()) {
                yazi = borc.vergi()
                        ? Component.text("GECİKMİŞ VERGİ (" + kisa(borc.enCokGecikme()) + ")  /ode", NamedTextColor.RED)
                        : Component.text(adet + " - GECİKTİ (" + kisa(borc.enCokGecikme()) + ") - aranıyorsun!  /ode", NamedTextColor.RED);
                renk = BossBar.Color.RED;
            } else if (borc.enYakinSon() != Long.MAX_VALUE) {
                long kalan = borc.enYakinSon() - simdi;
                yazi = Component.text(adet + ": " + kisa(kalan) + " kaldı  /ode", NamedTextColor.YELLOW);
                renk = BossBar.Color.YELLOW;
                ilerleme = borc.enYakinToplam() > 0 ? (float) kalan / borc.enYakinToplam() : 1f;
            } else {
                yazi = Component.text(adet + " (süresiz)  /ode", NamedTextColor.YELLOW);
                renk = BossBar.Color.YELLOW;
            }
        }

        BossBar bar = cubuklar.get(p.getUniqueId());
        if (yazi == null || !ayar("bossbar")) {
            if (bar != null) {
                p.hideBossBar(bar);
                cubuklar.remove(p.getUniqueId());
            }
            return;
        }
        ilerleme = Math.max(0f, Math.min(1f, ilerleme));
        if (bar == null) {
            bar = BossBar.bossBar(yazi, ilerleme, renk, BossBar.Overlay.PROGRESS);
            cubuklar.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(yazi);
            bar.color(renk);
            bar.progress(ilerleme);
        }
    }

    /** "1g 4sa", "5sa 10dk", "12dk" */
    static String kisa(long ms) {
        long dk = Math.max(0, ms) / 60_000L;
        long g = dk / 1440, sa = (dk % 1440) / 60, d = dk % 60;
        if (g > 0) return g + "g " + sa + "sa";
        if (sa > 0) return sa + "sa " + d + "dk";
        return Math.max(1, d) + "dk";
    }

    // ------------------------------------------------------------------
    // Karakol panosu
    // ------------------------------------------------------------------

    private static String kimlik(Location l) {
        return l.getWorld().getName() + ";" + String.format(java.util.Locale.ROOT, "%.2f;%.2f;%.2f", l.getX(), l.getY(), l.getZ());
    }

    private void panolariGuncelle() {
        // Eski (TextDisplay) panoları yerinde standlı panoya çevir
        if (!eskiPanolar.isEmpty()) {
            boolean degisti = false;
            java.util.Iterator<UUID> it = eskiPanolar.iterator();
            while (it.hasNext()) {
                Entity e = Bukkit.getEntity(it.next());
                if (e == null) continue;
                panolar.add(e.getLocation());
                e.remove();
                it.remove();
                degisti = true;
            }
            if (degisti) kaydet();
        }
        if (panolar.isEmpty()) return;
        List<Component> satirlar = panoSatirlari();
        for (Location l : panolar) {
            if (l.getWorld() == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            String k = kimlik(l);
            List<UUID> mevcut = standlar.get(k);
            boolean saglam = mevcut != null && mevcut.size() == satirlar.size();
            if (saglam) {
                for (UUID u : mevcut) {
                    if (!(Bukkit.getEntity(u) instanceof ArmorStand)) {
                        saglam = false;
                        break;
                    }
                }
            }
            if (saglam) {
                for (int i = 0; i < satirlar.size(); i++) ((ArmorStand) Bukkit.getEntity(mevcut.get(i))).customName(satirlar.get(i));
                continue;
            }
            standlariSil(l);
            List<UUID> yeni = new ArrayList<>();
            for (int i = 0; i < satirlar.size(); i++) {
                Component satir = satirlar.get(i);
                ArmorStand st = l.getWorld().spawn(l.clone().add(0, -0.28 * i, 0), ArmorStand.class, a -> {
                    a.setMarker(true);
                    a.setInvisible(true);
                    a.setGravity(false);
                    a.setInvulnerable(true);
                    a.setPersistent(false); // kalıcı olmasın: bölge yüklenince eski satırlar üst üste binmesin, gerekirse yeniden kurulur
                    a.setCustomNameVisible(true);
                    a.customName(satir);
                    a.getPersistentDataContainer().set(panoAnahtari, PersistentDataType.STRING, k);
                });
                yeni.add(st.getUniqueId());
            }
            standlar.put(k, yeni);
        }
    }

    private void standlariSil(Location l) {
        String k = kimlik(l);
        List<UUID> eski = standlar.remove(k);
        if (eski != null) for (UUID u : eski) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        // Restart sonrası bellekte olmayan standlar: etiketinden bul
        for (Entity e : l.getWorld().getNearbyEntities(l.clone().add(0, -2, 0), 1.5, 3, 1.5)) {
            if (e instanceof ArmorStand && k.equals(e.getPersistentDataContainer().get(panoAnahtari, PersistentDataType.STRING))) e.remove();
        }
    }

    /** En fazla 14 satır. */
    private List<Component> panoSatirlari() {
        long simdi = System.currentTimeMillis();
        List<Component> l = new ArrayList<>();
        l.add(Component.text("KARAKOL PANOSU", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        List<CezaSistemi.BorcOzet> borclar = plugin.ceza().borcOzetleri();
        if (borclar.isEmpty()) l.add(Component.text("Borçlu yok", NamedTextColor.GREEN));
        int sigacak = 10;
        for (int n = 0; n < borclar.size(); n++) {
            if (n == sigacak) {
                l.add(Component.text("... ve " + (borclar.size() - sigacak) + " borç daha", NamedTextColor.GRAY));
                break;
            }
            CezaSistemi.BorcOzet b = borclar.get(n);
            String durum;
            NamedTextColor renk;
            if (b.gec()) {
                durum = "GECİKTİ " + kisa(b.enCokGecikme());
                renk = b.vergi() ? NamedTextColor.GOLD : NamedTextColor.RED;
            } else if (b.enYakinSon() != Long.MAX_VALUE) {
                durum = kisa(b.enYakinSon() - simdi) + " kaldı";
                renk = NamedTextColor.YELLOW;
            } else {
                durum = "süresiz";
                renk = NamedTextColor.GRAY;
            }
            l.add(Component.text(b.isim() + " - " + b.adet() + (b.vergi() ? " vergi - " : " ceza - ") + durum, renk));
        }
        List<String> gozalti = plugin.gozalti().kelepceliIsimleri();
        l.add(Component.text("Gözaltında: " + (gozalti.isEmpty() ? "kimse yok" : String.join(", ", gozalti)), NamedTextColor.AQUA));
        l.add(Component.text("Güncelleme: " + plugin.kayit().kisaTarih(simdi), NamedTextColor.DARK_GRAY));
        return l;
    }

    private void panoKur(Player p) {
        Location yer = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5));
        panolar.add(yer);
        kaydet();
        panolariGuncelle();
        plugin.mesaj(p, "Karakol panosu kuruldu (10 saniyede bir güncellenir). Kaldırmak için yanında: /pano kaldir", NamedTextColor.GREEN);
        plugin.kayit().log(p.getName() + " karakol panosu kurdu: " + yer.getWorld().getName() + " " + yer.getBlockX() + " " + yer.getBlockY() + " " + yer.getBlockZ());
    }

    private void panoKaldir(Player p) {
        Location enYakin = null;
        double mesafe = 8 * 8;
        for (Location l : panolar) {
            if (l.getWorld() == null || !l.getWorld().equals(p.getWorld())) continue;
            double d = l.distanceSquared(p.getLocation());
            if (d <= mesafe) {
                mesafe = d;
                enYakin = l;
            }
        }
        if (enYakin == null) {
            plugin.mesaj(p, "Yakında (8 blok) pano yok.", NamedTextColor.YELLOW);
            return;
        }
        standlariSil(enYakin);
        panolar.remove(enYakin);
        kaydet();
        plugin.mesaj(p, "Pano kaldırıldı.", NamedTextColor.GREEN);
    }

    // ------------------------------------------------------------------
    // Komutlar: /siren, /pano
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
        if (!(s instanceof Player p)) return true;
        String ad = command.getName().toLowerCase(Locale.ROOT);
        if (ad.equals("siren")) {
            if (!plugin.gozalti().polisMi(p)) {
                plugin.mesaj(p, "Sireni sadece polis açabilir.", NamedTextColor.RED);
                return true;
            }
            if (sirenler.remove(p.getUniqueId()) != null) {
                plugin.mesaj(p, "Siren kapandı.", NamedTextColor.GRAY);
                return true;
            }
            long sure = Math.max(5, plugin.getConfig().getLong("gorsel.siren-sure", 30));
            sirenler.put(p.getUniqueId(), System.currentTimeMillis() + sure * 1000L);
            plugin.mesaj(p, "Siren açıldı (" + sure + " sn). Kapatmak için tekrar /siren", NamedTextColor.AQUA);
            return true;
        }
        if (ad.equals("pano")) {
            if (!plugin.gozalti().polisMi(p) && !p.hasPermission("poliskelepce.admin")) {
                plugin.mesaj(p, "Panoyu sadece polis ve başkan kurabilir.", NamedTextColor.RED);
                return true;
            }
            String alt = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
            switch (alt) {
                case "kur" -> panoKur(p);
                case "kaldir", "kaldır" -> panoKaldir(p);
                default -> {
                    plugin.mesaj(p, "Karakol panosu: borçlular ve gözaltındakiler, havada canlı yazı.", NamedTextColor.AQUA);
                    p.sendMessage(Component.text("  /pano kur - baktığın yere, 1.5 blok önüne kurar", NamedTextColor.GRAY));
                    p.sendMessage(Component.text("  /pano kaldir - yakındaki panoyu kaldırır", NamedTextColor.GRAY));
                    p.sendMessage(Component.text("  Bedrock'ta bu yazı sınırlı görünebilir.", NamedTextColor.DARK_GRAY));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        List<String> l = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("pano") && args.length == 1) {
            for (String sec : List.of("kur", "kaldir")) if (sec.startsWith(args[0].toLowerCase(Locale.ROOT))) l.add(sec);
        }
        return l;
    }

    @EventHandler
    public void onCik(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        sirenler.remove(id);
        BossBar bar = cubuklar.remove(id);
        if (bar != null) e.getPlayer().hideBossBar(bar);
    }

    /** Plugin kapanırken: bossbar'ları gizle. */
    public void kapat() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            BossBar bar = cubuklar.remove(p.getUniqueId());
            if (bar != null) p.hideBossBar(bar);
        }
        sirenler.clear();
        kaydet();
    }

    // ------------------------------------------------------------------
    // Kayıt
    // ------------------------------------------------------------------

    public void yukle() {
        if (!dosya.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dosya);
        for (String s : yml.getStringList("panolar")) { // 1.9: TextDisplay kimlikleri
            try {
                eskiPanolar.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        eskiPanolar.addAll(okuUuid(yml.getStringList("eski-panolar")));
        for (String s : yml.getStringList("pano-yerleri")) {
            String[] p = s.split(";");
            if (p.length != 4) continue;
            World w = Bukkit.getWorld(p[0]);
            if (w == null) continue;
            try {
                panolar.add(new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), 0f, 0f));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static List<UUID> okuUuid(List<String> l) {
        List<UUID> sonuc = new ArrayList<>();
        for (String s : l) {
            try {
                sonuc.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return sonuc;
    }

    private void kaydet() {
        YamlConfiguration yml = new YamlConfiguration();
        List<String> yerler = new ArrayList<>();
        for (Location l : panolar) if (l.getWorld() != null) yerler.add(kimlik(l));
        yml.set("pano-yerleri", yerler);
        List<String> eski = new ArrayList<>();
        for (UUID u : eskiPanolar) eski.add(u.toString());
        yml.set("eski-panolar", eski);
        try {
            if (!dosya.getParentFile().exists()) dosya.getParentFile().mkdirs();
            yml.save(dosya);
        } catch (IOException e) {
            plugin.getLogger().warning("gorsel.yml kaydedilemedi: " + e.getMessage());
        }
    }
}
