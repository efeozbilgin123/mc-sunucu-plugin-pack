package tr.efe.polis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Doktor rolü ve yaralı sistemi.
 * - Öldürecek hasar alan oyuncu ölmez, yere yığılır (yaralı). Belirli süre içinde kaldırılmazsa ölür.
 * - Yanına gidip sağ tıklayan (ya da /kaldir yazan) kişi onu birkaç saniyede kaldırır. Doktor daha hızlı ve
 *   daha çok canla kaldırır.
 * - Yaralıyken yürüyemez, vuramaz, blok/eşya kullanamaz. Yaralıya vurulursa (oyuncu ya da yaratık) ölür.
 * - /ol ile beklemeden ölebilir. Yaralıyken oyundan çıkan ölür.
 */
public final class Yarali implements Listener, CommandExecutor, TabCompleter {

    private record Durum(long baslangic, long bitis) {}

    /** Yaralıyı yere düşüren oyuncu: kan kaybından / pes ederek ölürse öldüren o sayılır (mezar yağma hakkı). */
    private final Map<UUID, UUID> yaralayan = new HashMap<>();

    private record Kaldirma(UUID hedef, long baslangic, long bitis, boolean doktor) {}

    private static final Set<String> SERBEST_KOMUTLAR = Set.of(
            "login", "l", "register", "reg", "logout", "msg", "tell", "w", "r", "ol", "sicil");

    private final PolisPlugin plugin;
    private final File dosya;
    private final Map<UUID, String> doktorlar = new LinkedHashMap<>();
    private final Map<UUID, Durum> yaralilar = new HashMap<>();
    /** kaldıran -> kaldırma */
    private final Map<UUID, Kaldirma> kaldirmalar = new HashMap<>();
    /** Kelepçeliyken yaralı çıkanlar: uuid -> {kalanMs, toplamMs} (yarali.yml) */
    private final Map<UUID, long[]> cevrimdisi = new HashMap<>();
    private int sayac = 0;
    /** Kalp atışı için son atıştan beri geçen tick. */
    private final Map<UUID, Integer> kalpSayac = new HashMap<>();
    private long sonTik = System.currentTimeMillis();

    public Yarali(PolisPlugin plugin) {
        this.plugin = plugin;
        this.dosya = new File(plugin.getDataFolder(), "doktorlar.yml");
    }

    /** Vuran bir oyuncu mu (direkt ya da attığı mermiyle)? */
    public static boolean pvpMi(Entity vuran) {
        if (vuran instanceof Player) return true;
        return vuran instanceof Projectile pr && pr.getShooter() instanceof Player;
    }

    /** Kalp atışı aralığı (tick): süre doluyken 30, bitmeye yaklaştıkça 8'e iner. */
    public static int kalpAraligiTik(long kalanMs, long toplamMs) {
        double oran = toplamMs <= 0 ? 0 : Math.max(0, Math.min(1, (double) kalanMs / toplamMs));
        return Math.max(8, (int) Math.round(8 + 22 * oran));
    }

    public boolean doktorMu(Player p) {
        return doktorlar.containsKey(p.getUniqueId());
    }

    public boolean yaraliMi(Player p) {
        return yaralilar.containsKey(p.getUniqueId());
    }

    /** Bossbar için: ölmesine kalan süre (ms) ve toplam süre; yaralı değilse null. */
    public long[] kalan(Player p) {
        Durum d = yaralilar.get(p.getUniqueId());
        if (d == null) return null;
        return new long[]{Math.max(0, d.bitis() - System.currentTimeMillis()), d.bitis() - d.baslangic()};
    }

    private boolean acik() {
        return plugin.getConfig().getBoolean("yarali.acik", true);
    }

    // ------------------------------------------------------------------
    // Yere yığılma ve bitirme
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHasar(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EntityDamageEvent.DamageCause neden = e.getCause();

        if (yaraliMi(p)) {
            // Yaralıya vurulursa (oyuncu, yaratık, ok...) ya da lav/boşluk: ölür. Diğer hasarlar (açlık, zehir, düşme) işlemez.
            boolean bitir = e instanceof EntityDamageByEntityEvent || neden == EntityDamageEvent.DamageCause.LAVA
                    || neden == EntityDamageEvent.DamageCause.VOID || neden == EntityDamageEvent.DamageCause.KILL
                    || neden == EntityDamageEvent.DamageCause.WORLD_BORDER;
            if (!bitir) {
                e.setCancelled(true);
                return;
            }
            String kim = e instanceof EntityDamageByEntityEvent ed ? vuranIsmi(ed.getDamager()) : null;
            yaralilar.remove(p.getUniqueId());
            UUID yk = yaralayan.remove(p.getUniqueId());
            boolean oyuncuBitirdi = e instanceof EntityDamageByEntityEvent ed2 && pvpMi(ed2.getDamager());
            Player ilkVuran = yk != null ? Bukkit.getPlayer(yk) : null;
            if (!oyuncuBitirdi && ilkVuran != null && !ilkVuran.equals(p)) p.setKiller(ilkVuran);
            kaldirmaIptal(p.getUniqueId());
            ayagaKaldirGorsel(p);
            plugin.kayit().say(p.getUniqueId(), p.getName(), Kayitlar.Alan.OLUM);
            plugin.kayit().log(p.getName() + " yaralıyken " + (kim != null ? kim + " tarafından bitirildi" : "öldü (" + nedenYazi(neden) + ")"));
            e.setDamage(p.getHealth() + p.getAbsorptionAmount() + 1000);
            return;
        }

        if (!acik()) return;
        // Yere düşme sadece PvP'de: yaratık, düşme, lav vb. normal ölüm
        if (!(e instanceof EntityDamageByEntityEvent pe) || !pvpMi(pe.getDamager())) return;
        double can = p.getHealth();
        double emilim = p.getAbsorptionAmount();
        boolean olumcul = emilim > 0 ? e.getFinalDamage() >= can + emilim : e.getFinalDamage() >= can;
        if (!olumcul) return;
        // Ölümsüzlük totemi elindeyse vanilla çalışsın
        if (p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) return;

        e.setCancelled(true);
        Entity vuran = pe.getDamager();
        if (vuran instanceof Projectile pr && pr.getShooter() instanceof Entity atan) vuran = atan;
        if (vuran instanceof Player saldiran) {
            yaralayan.put(p.getUniqueId(), saldiran.getUniqueId());
            if (plugin.savas() != null) plugin.savas().etiketle(saldiran, p); // yere düşüren vuruş da savaş sayılır
        }
        yereDus(p, vuranIsmi(pe.getDamager()) + " yaraladı");
    }

    private void yereDus(Player p, String sebep) {
        long simdi = System.currentTimeMillis();
        long sure = Math.max(5, plugin.getConfig().getLong("yarali.sure", 60)) * 1000L;
        yaralilar.put(p.getUniqueId(), new Durum(simdi, simdi + sure));
        p.setHealth(1.0);
        p.setFireTicks(0);
        p.setPose(Pose.SWIMMING, true);
        etkiVer(p);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
        plugin.gorsel().baslik(p, "YARALANDIN", "Doktor ya da arkadaşın seni kaldırabilir. Vazgeçmek için: /ol",
                NamedTextColor.RED, Sound.ENTITY_PLAYER_HURT, 0.5f);
        plugin.mesaj(p, "Yaralandın (" + sebep + "). " + (sure / 1000) + " saniye içinde kaldırılmazsan öleceksin. Vazgeçmek için /ol",
                NamedTextColor.RED);

        Location yer = p.getLocation();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p)) continue;
            boolean yakin = o.getWorld().equals(p.getWorld()) && o.getLocation().distance(yer) <= 48;
            if (doktorMu(o)) {
                String nerede = o.getWorld().equals(p.getWorld())
                        ? (int) o.getLocation().distance(yer) + " blok uzakta" : "başka boyutta";
                plugin.mesaj(o, "[Doktor] " + p.getName() + " yaralandı! Konum: " + yer.getBlockX() + " " + yer.getBlockY() + " "
                        + yer.getBlockZ() + " (" + nerede + ")", NamedTextColor.LIGHT_PURPLE);
                o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.8f);
            } else if (yakin) {
                plugin.mesaj(o, p.getName() + " yaralandı! Yanına gidip sağ tıkla ya da /kaldir " + p.getName(), NamedTextColor.YELLOW);
            }
        }
        plugin.kayit().say(p.getUniqueId(), p.getName(), Kayitlar.Alan.YARALANMA);
        plugin.kayit().log(p.getName() + " yaralandı (" + sebep + ") " + yer.getWorld().getName() + " " + yer.getBlockX() + " "
                + yer.getBlockY() + " " + yer.getBlockZ());
    }

    private void oldur(Player p, String neden) {
        if (yaralilar.remove(p.getUniqueId()) == null) return;
        kaldirmaIptal(p.getUniqueId());
        ayagaKaldirGorsel(p);
        plugin.kayit().say(p.getUniqueId(), p.getName(), Kayitlar.Alan.OLUM);
        plugin.kayit().log(p.getName() + " " + neden);
        UUID k = yaralayan.remove(p.getUniqueId());
        Player olduren = k != null ? Bukkit.getPlayer(k) : null;
        if (olduren != null && !olduren.equals(p)) p.setKiller(olduren);
        p.setHealth(0);
    }

    private static void etkiVer(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 6, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 4, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 4, false, false, true));
    }

    private static void ayagaKaldirGorsel(Player p) {
        p.setPose(Pose.STANDING, false);
        p.removePotionEffect(PotionEffectType.DARKNESS);
        p.removePotionEffect(PotionEffectType.WEAKNESS);
        p.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    private static String vuranIsmi(Entity vuran) {
        if (vuran instanceof Projectile pr && pr.getShooter() instanceof Entity atan) return atan.getName();
        return vuran.getName();
    }

    private static String nedenYazi(EntityDamageEvent.DamageCause c) {
        return switch (c.name()) {
            case "FALL" -> "düştü";
            case "FIRE", "FIRE_TICK", "CAMPFIRE", "HOT_FLOOR" -> "yandı";
            case "LAVA" -> "lava düştü";
            case "DROWNING" -> "boğuluyordu";
            case "STARVATION" -> "açlıktan";
            case "POISON", "WITHER" -> "zehirlendi";
            case "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> "patlama";
            case "SUFFOCATION" -> "sıkıştı";
            case "FREEZE" -> "dondu";
            case "MAGIC" -> "büyü";
            case "LIGHTNING" -> "yıldırım";
            case "CONTACT" -> "dikene takıldı";
            case "FALLING_BLOCK" -> "üstüne blok düştü";
            default -> c.name().toLowerCase(Locale.ROOT);
        };
    }

    // ------------------------------------------------------------------
    // Kaldırma
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onYaraliyaTik(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player k = e.getPlayer();
        if (yaraliMi(k)) {
            e.setCancelled(true);
            return;
        }
        if (!(e.getRightClicked() instanceof Player h) || !yaraliMi(h)) return;
        // Polis eşyası (kelepçe/anahtar) elindeyse ya da polis kelepçeliye Shift ile tıklıyorsa (üst arama) polis sistemi çalışsın
        if (plugin.items().turu(k.getInventory().getItemInMainHand()) != null) return;
        if (k.isSneaking() && plugin.gozalti().polisMi(k) && plugin.gozalti().kelepceliMi(h)) return;
        e.setCancelled(true);
        kaldirBasla(k, h);
    }

    private void kaldirBasla(Player k, Player h) {
        if (plugin.gozalti().kelepceliMi(k)) {
            plugin.mesaj(k, "Kelepçeliyken kimseyi kaldıramazsın.", NamedTextColor.RED);
            return;
        }
        Kaldirma mevcut = kaldirmalar.get(k.getUniqueId());
        if (mevcut != null && mevcut.hedef().equals(h.getUniqueId())) return; // zaten kaldırıyor
        for (Map.Entry<UUID, Kaldirma> g : kaldirmalar.entrySet()) {
            if (g.getValue().hedef().equals(h.getUniqueId()) && !g.getKey().equals(k.getUniqueId())) {
                Player diger = Bukkit.getPlayer(g.getKey());
                plugin.mesaj(k, (diger != null ? diger.getName() : "Biri") + " zaten " + h.getName() + " adlı oyuncuyu kaldırıyor.", NamedTextColor.YELLOW);
                return;
            }
        }
        boolean doktor = doktorMu(k);
        long sure = Math.max(1, plugin.getConfig().getLong(doktor ? "yarali.doktor-sure" : "yarali.arkadas-sure", doktor ? 3 : 8)) * 1000L;
        long simdi = System.currentTimeMillis();
        kaldirmalar.put(k.getUniqueId(), new Kaldirma(h.getUniqueId(), simdi, simdi + sure, doktor));
        plugin.mesaj(k, h.getName() + " adlı oyuncuyu kaldırıyorsun, yanından ayrılma (" + (sure / 1000) + " sn).", NamedTextColor.GREEN);
        plugin.mesaj(h, k.getName() + " seni kaldırıyor...", NamedTextColor.GREEN);
    }

    private void kaldir(Player h, Player k, boolean doktor) {
        yaralilar.remove(h.getUniqueId());
        yaralayan.remove(h.getUniqueId());
        ayagaKaldirGorsel(h);
        double can = plugin.getConfig().getDouble(doktor ? "yarali.doktor-can" : "yarali.arkadas-can", doktor ? 10 : 4);
        h.setHealth(Math.max(1, Math.min(h.getMaxHealth(), can)));
        if (doktor) {
            h.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10 * 20, 0));
        } else {
            // Doktorsuz kaldırılan bir süre sersem kalır
            h.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 20, 0));
            h.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30 * 20, 0));
        }
        h.getWorld().playSound(h.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        plugin.gorsel().baslik(h, "KALDIRILDIN", k.getName() + " seni ayağa kaldırdı", NamedTextColor.GREEN, null, 1f);
        plugin.mesaj(k, h.getName() + " adlı oyuncuyu kaldırdın.", NamedTextColor.GREEN);
        plugin.kayit().log(k.getName() + ", " + h.getName() + " adlı yaralıyı kaldırdı" + (doktor ? " (doktor)" : ""));
    }

    private void kaldirmaIptal(UUID yarali) {
        kaldirmalar.values().removeIf(k -> k.hedef().equals(yarali));
    }

    /** Her 5 tick. */
    public void tik() {
        long simdi = System.currentTimeMillis();
        long gecen = simdi - sonTik;
        sonTik = simdi;
        sayac++;

        Iterator<Map.Entry<UUID, Kaldirma>> ki = kaldirmalar.entrySet().iterator();
        while (ki.hasNext()) {
            Map.Entry<UUID, Kaldirma> g = ki.next();
            Kaldirma kd = g.getValue();
            Player k = Bukkit.getPlayer(g.getKey());
            Player h = Bukkit.getPlayer(kd.hedef());
            if (k == null || h == null || !yaraliMi(h)) {
                ki.remove();
                continue;
            }
            if (!k.getWorld().equals(h.getWorld()) || k.getLocation().distance(h.getLocation()) > 3.5 || yaraliMi(k)) {
                ki.remove();
                plugin.mesaj(k, "Kaldırma yarıda kaldı, yanında durmalısın.", NamedTextColor.RED);
                continue;
            }
            if (simdi >= kd.bitis()) {
                ki.remove();
                kaldir(h, k, kd.doktor());
                continue;
            }
            double oran = (double) (simdi - kd.baslangic()) / Math.max(1, kd.bitis() - kd.baslangic());
            Component cubuk = cubuk("Kaldırılıyor ", oran);
            k.sendActionBar(cubuk);
            h.sendActionBar(cubuk);
        }

        for (UUID id : new ArrayList<>(yaralilar.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                yaralilar.remove(id);
                continue;
            }
            Durum d = yaralilar.get(id);
            if (plugin.gozalti().kelepceliMi(p)) {
                // Gözaltındaki yaralı kan kaybından ölmez (kaçmak için ölemesin); süre durur, biri kaldırana kadar bekler
                d = new Durum(d.baslangic() + gecen, d.bitis() + gecen);
                yaralilar.put(id, d);
            } else if (simdi >= d.bitis()) {
                plugin.mesaj(p, "Kimse yetişemedi, kan kaybından öldün.", NamedTextColor.DARK_RED);
                oldur(p, "kan kaybından öldü (kimse kaldırmadı)");
                continue;
            }
            etkiVer(p);
            if (plugin.getConfig().getBoolean("gorsel.yarali-kalp", true)) {
                long kalanMs = Math.max(0, d.bitis() - simdi);
                int birikmis = kalpSayac.getOrDefault(id, 0) + 5;
                if (birikmis >= kalpAraligiTik(kalanMs, d.bitis() - d.baslangic())) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);
                    birikmis = 0;
                }
                kalpSayac.put(id, birikmis);
                if (kalanMs <= 15_000) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
            }
            if (sayac % 4 == 0) {
                p.setPose(Pose.SWIMMING, true);
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 0.2, 0), 4, 0.3, 0.05, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(0x8B0000), 1.0f));
            }
        }
    }

    private static Component cubuk(String baslik, double oran) {
        int dolu = (int) Math.round(Math.max(0, Math.min(1, oran)) * 20);
        return Component.text(baslik, NamedTextColor.GREEN)
                .append(Component.text("|".repeat(dolu), NamedTextColor.GREEN))
                .append(Component.text("|".repeat(20 - dolu), NamedTextColor.DARK_GRAY));
    }

    // ------------------------------------------------------------------
    // Yaralıyken kısıtlamalar
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHareket(PlayerMoveEvent e) {
        if (e instanceof PlayerTeleportEvent) return; // polis sürüklerken ışınlama serbest
        if (!yaraliMi(e.getPlayer())) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getZ() == to.getZ() && to.getY() <= from.getY()) return;
        Location yeni = from.clone();
        yeni.setY(Math.min(from.getY(), to.getY())); // düşebilir ama yürüyemez / zıplayamaz
        yeni.setYaw(to.getYaw());
        yeni.setPitch(to.getPitch());
        e.setTo(yeni);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCanDolu(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && yaraliMi(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEtkilesim(PlayerInteractEvent e) {
        if (yaraliMi(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKir(BlockBreakEvent e) {
        if (yaraliMi(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKoy(BlockPlaceEvent e) {
        if (yaraliMi(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAt(PlayerDropItemEvent e) {
        if (yaraliMi(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVur(EntityDamageByEntityEvent e) {
        Entity vuran = e.getDamager();
        if (vuran instanceof Projectile pr && pr.getShooter() instanceof Player atan) vuran = atan;
        if (vuran instanceof Player p && yaraliMi(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKomut(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!yaraliMi(p)) return;
        String msg = e.getMessage();
        String komut = msg.length() > 1 ? msg.substring(1).split(" ")[0].toLowerCase(Locale.ROOT) : "";
        int iki = komut.indexOf(':');
        if (iki >= 0) komut = komut.substring(iki + 1);
        if (!SERBEST_KOMUTLAR.contains(komut)) {
            e.setCancelled(true);
            plugin.mesaj(p, "Yaralıyken bu komutu kullanamazsın. Vazgeçmek için /ol", NamedTextColor.RED);
        }
    }

    /**
     * Oyundan çıkan oyuncu için (Savas dinleyicisi çağırır). Yaralıysa ölür ve true döner.
     * Kelepçeliyse ölmez: kalan süre yarali.yml'e yazılır, girince yine yaralı olur.
     */
    public boolean yaraliCikti(Player p) {
        kaldirmalar.remove(p.getUniqueId());
        kalpSayac.remove(p.getUniqueId());
        if (!yaraliMi(p)) return false;
        if (plugin.gozalti().kelepceliMi(p)) {
            Durum d = yaralilar.remove(p.getUniqueId());
            kaldirmaIptal(p.getUniqueId());
            cevrimdisi.put(p.getUniqueId(), new long[]{Math.max(1000, d.bitis() - System.currentTimeMillis()), d.bitis() - d.baslangic()});
            kaydet();
            return false;
        }
        oldur(p, "yaralıyken oyundan çıktı (öldü)");
        return true;
    }

    /** Girişte: kelepçeliyken yaralı çıkmışsa yine yere yatırılır. */
    public void yaraliGirdi(Player p) {
        long[] k = cevrimdisi.remove(p.getUniqueId());
        if (k == null) return;
        kaydet();
        long simdi = System.currentTimeMillis();
        yaralilar.put(p.getUniqueId(), new Durum(simdi - (k[1] - k[0]), simdi + k[0]));
        p.setHealth(1.0);
        p.setPose(Pose.SWIMMING, true);
        etkiVer(p);
        plugin.mesaj(p, "Hâlâ yaralısın. Biri seni kaldırabilir.", NamedTextColor.RED);
    }

    @EventHandler
    public void onGir(org.bukkit.event.player.PlayerJoinEvent e) {
        // Kelepçe önce geri takılsın (Gozalti.onJoin), sonra yaralılık
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (e.getPlayer().isOnline()) yaraliGirdi(e.getPlayer());
        });
    }

    /** Plugin kapanırken yaralıları ayağa kaldır (1 canla). */
    public void kapat() {
        for (UUID id : new ArrayList<>(yaralilar.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) ayagaKaldirGorsel(p);
        }
        yaralilar.clear();
        kaldirmalar.clear();
    }

    // ------------------------------------------------------------------
    // Komutlar: /doktor, /kaldir, /ol
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "doktor" -> doktorKomut(s, args);
            case "kaldir" -> {
                if (!(s instanceof Player k)) return true;
                if (args.length < 1) {
                    plugin.mesaj(k, "Kullanım: /kaldir <oyuncu>  (yaralının yanında)", NamedTextColor.YELLOW);
                    return true;
                }
                Player h = Secici.tekli(k, args[0]);
                if (h == null) return true;
                if (h.equals(k)) { plugin.mesaj(k, "Kendini kaldıramazsın.", NamedTextColor.RED); return true; }
                if (!yaraliMi(h)) { plugin.mesaj(k, h.getName() + " yaralı değil.", NamedTextColor.YELLOW); return true; }
                if (yaraliMi(k)) { plugin.mesaj(k, "Sen de yaralısın.", NamedTextColor.RED); return true; }
                if (!h.getWorld().equals(k.getWorld()) || h.getLocation().distance(k.getLocation()) > 3.5) {
                    plugin.mesaj(k, h.getName() + " adlı oyuncunun yanında olmalısın (3 blok).", NamedTextColor.RED);
                    return true;
                }
                kaldirBasla(k, h);
            }
            case "ol" -> {
                if (!(s instanceof Player p)) return true;
                if (!yaraliMi(p)) { plugin.mesaj(p, "Yaralı değilsin.", NamedTextColor.GRAY); return true; }
                if (plugin.gozalti().kelepceliMi(p)) { plugin.mesaj(p, "Gözaltındayken pes edemezsin, biri seni kaldıracak.", NamedTextColor.RED); return true; }
                oldur(p, "yaralıyken pes etti (/ol)");
            }
            default -> { return false; }
        }
        return true;
    }

    private void doktorKomut(CommandSender s, String[] args) {
        String alt = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (alt) {
            case "ekle" -> {
                if (!admin(s)) return;
                if (args.length < 2) { plugin.mesaj(s, "Kullanım: /doktor ekle <oyuncu>", NamedTextColor.YELLOW); return; }
                for (Player p : Secici.coklu(s, args[1], false)) {
                    doktorlar.put(p.getUniqueId(), p.getName());
                    plugin.gozalti().sekmeIsmi(p);
                    plugin.mesaj(s, p.getName() + " artık doktor.", NamedTextColor.GREEN);
                    plugin.mesaj(p, "Doktor oldun! Yaralıları daha hızlı ve daha çok canla kaldırırsın, biri yaralanınca haber gelir.",
                            NamedTextColor.LIGHT_PURPLE);
                    plugin.kayit().log(s.getName() + ", " + p.getName() + " adlı oyuncuyu doktor yaptı");
                }
                kaydet();
            }
            case "cikar", "çıkar" -> {
                if (!admin(s)) return;
                if (args.length < 2) { plugin.mesaj(s, "Kullanım: /doktor cikar <oyuncu>", NamedTextColor.YELLOW); return; }
                List<UUID> bulunan = new ArrayList<>();
                if (args[1].startsWith("@")) {
                    for (Player p : Secici.coklu(s, args[1], false)) if (doktorlar.containsKey(p.getUniqueId())) bulunan.add(p.getUniqueId());
                } else {
                    for (Map.Entry<UUID, String> g : doktorlar.entrySet()) {
                        if (g.getValue().equalsIgnoreCase(args[1])) { bulunan.add(g.getKey()); break; }
                    }
                }
                if (bulunan.isEmpty()) { plugin.mesaj(s, "Çıkarılacak doktor bulunamadı: " + args[1], NamedTextColor.RED); return; }
                for (UUID id : bulunan) {
                    String isim = doktorlar.remove(id);
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        plugin.gozalti().sekmeIsmi(p);
                        plugin.mesaj(p, "Artık doktor değilsin.", NamedTextColor.YELLOW);
                    }
                    plugin.mesaj(s, isim + " doktorluktan çıkarıldı.", NamedTextColor.GREEN);
                    plugin.kayit().log(s.getName() + ", " + isim + " adlı oyuncuyu doktorluktan çıkardı");
                }
                kaydet();
            }
            case "liste" -> {
                if (doktorlar.isEmpty()) plugin.mesaj(s, "Henüz doktor yok.", NamedTextColor.GRAY);
                else plugin.mesaj(s, "Doktorlar: " + String.join(", ", doktorlar.values()), NamedTextColor.LIGHT_PURPLE);
            }
            default -> {
                plugin.mesaj(s, "Doktor ve yaralı sistemi:", NamedTextColor.LIGHT_PURPLE);
                satir(s, "Ölecek kadar hasar alan ölmez, yere yığılır. " + plugin.getConfig().getLong("yarali.sure", 60)
                        + " sn içinde kaldırılmazsa ölür");
                satir(s, "Kaldırmak için yaralıya sağ tıkla ve yanında bekle (Bedrock: /kaldir <oyuncu>)");
                satir(s, "Doktor " + plugin.getConfig().getLong("yarali.doktor-sure", 3) + " sn'de, diğerleri "
                        + plugin.getConfig().getLong("yarali.arkadas-sure", 8) + " sn'de kaldırır. Doktor daha çok canla kaldırır");
                satir(s, "Yaralıya vurulursa ölür. Yaralıyken oyundan çıkan ölür. Beklemeden ölmek için: /ol");
                satir(s, "Biri yaralanınca doktorlara konumuyla haber gider");
                satir(s, "/doktor liste - doktorları gösterir");
                if (s.hasPermission("poliskelepce.admin")) satir(s, "/doktor ekle|cikar <oyuncu> - (op/başkan) doktor atar / alır");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        List<String> sonuc = new ArrayList<>();
        String ad = command.getName().toLowerCase(Locale.ROOT);
        if (ad.equals("doktor")) {
            if (args.length == 1) eslesen(sonuc, args[0], List.of("liste", "ekle", "cikar"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("ekle")) eslesen(sonuc, args[1], Secici.oneriler());
            else if (args.length == 2 && args[0].equalsIgnoreCase("cikar")) eslesen(sonuc, args[1], new ArrayList<>(doktorlar.values()));
        } else if (ad.equals("kaldir") && args.length == 1) {
            List<String> l = new ArrayList<>();
            for (UUID id : yaralilar.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) l.add(p.getName());
            }
            l.add("@p");
            eslesen(sonuc, args[0], l);
        }
        return sonuc;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("poliskelepce.admin")) return true;
        plugin.mesaj(s, "Bu komut için yetkin yok.", NamedTextColor.RED);
        return false;
    }

    private static void satir(CommandSender s, String metin) {
        s.sendMessage(Component.text("  " + metin, NamedTextColor.GRAY));
    }

    private static void eslesen(List<String> hedef, String yazilan, List<String> secenekler) {
        String y = yazilan.toLowerCase(Locale.ROOT);
        for (String s : secenekler) if (s.toLowerCase(Locale.ROOT).startsWith(y)) hedef.add(s);
    }

    // ------------------------------------------------------------------
    // Kayıt
    // ------------------------------------------------------------------

    public void yukle() {
        File yd = new File(plugin.getDataFolder(), "yarali.yml");
        if (yd.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(yd);
            ConfigurationSection ys = y.getConfigurationSection("yarali");
            if (ys != null) {
                for (String k : ys.getKeys(false)) {
                    try {
                        cevrimdisi.put(UUID.fromString(k), new long[]{y.getLong("yarali." + k + ".kalan", 30000), y.getLong("yarali." + k + ".toplam", 60000)});
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        if (!dosya.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dosya);
        ConfigurationSection cs = yml.getConfigurationSection("doktorlar");
        if (cs == null) return;
        for (String k : cs.getKeys(false)) {
            try {
                doktorlar.put(UUID.fromString(k), cs.getString(k, "?"));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void kaydet() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, long[]> g : cevrimdisi.entrySet()) {
            y.set("yarali." + g.getKey() + ".kalan", g.getValue()[0]);
            y.set("yarali." + g.getKey() + ".toplam", g.getValue()[1]);
        }
        try {
            if (!dosya.getParentFile().exists()) dosya.getParentFile().mkdirs();
            y.save(new File(plugin.getDataFolder(), "yarali.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("yarali.yml kaydedilemedi: " + e.getMessage());
        }
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, String> g : doktorlar.entrySet()) yml.set("doktorlar." + g.getKey(), g.getValue());
        try {
            if (!dosya.getParentFile().exists()) dosya.getParentFile().mkdirs();
            yml.save(dosya);
        } catch (IOException e) {
            plugin.getLogger().warning("doktorlar.yml kaydedilemedi: " + e.getMessage());
        }
    }
}
