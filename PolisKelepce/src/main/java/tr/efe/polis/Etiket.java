package tr.efe.polis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafa üstü etiket (scoreboard takımı):
 * - Durum: [GÖZALTI] > [YARALI] > [ARANIYOR]; durum varken rol etiketi görünmez.
 * - Rol: [★ Polis], [♛ Başkan], [✚ Doktor]; oyuncunun vanilla takımı (ör. [Mimar]) varsa birleşik takım kurulur.
 * - Etiket kalkınca oyuncu eski takımına geri konur.
 */
public final class Etiket implements org.bukkit.event.Listener {

    private final PolisPlugin plugin;
    private final File dosya;
    /** Bizim takıma alınan oyuncunun önceki (vanilla) takımı. */
    private final Map<UUID, String> eskiTakim = new HashMap<>();
    /** Sohbet öneki için rol önbelleği (sohbet olayı ana iş parçacığında değil). */
    private final Map<UUID, String> rolOnbellek = new ConcurrentHashMap<>();

    public Etiket(PolisPlugin plugin) {
        this.plugin = plugin;
        this.dosya = new File(plugin.getDataFolder(), "etiket.yml");
    }

    // ------------------------------------------------------------------
    // Saf kurallar
    // ------------------------------------------------------------------

    public static String rolOneki(String rol, boolean simge) {
        return switch (rol) {
            case "baskan" -> simge ? "[♛ Başkan] " : "[Başkan] ";
            case "doktor" -> simge ? "[✚ Doktor] " : "[Doktor] ";
            default -> simge ? "[★ Polis] " : "[Polis] ";
        };
    }

    public static NamedTextColor rolRengi(String rol) {
        return switch (rol) {
            case "baskan" -> NamedTextColor.GOLD;
            case "doktor" -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.AQUA;
        };
    }

    public static String takimAdi(String durum, String rol, String eskiTakim) {
        if (durum != null) return "pk_" + durum;
        if (rol == null) return null;
        if (eskiTakim == null) return "pk_" + rol;
        return "pk_" + rol + "_" + Integer.toHexString(eskiTakim.hashCode() & 0xFFFFFF);
    }

    // ------------------------------------------------------------------
    // Oyuncu
    // ------------------------------------------------------------------

    public boolean simgeli() {
        return plugin.getConfig().getBoolean("gorsel.simgeler", true);
    }

    /** "polis", "baskan", "doktor" ya da null. */
    public String rol(Player p) {
        String r = plugin.gozalti().rolu(p);
        if (r == null && plugin.yarali().doktorMu(p)) r = "doktor";
        return r;
    }

    /** Sohbet için (herhangi bir iş parçacığından okunabilir). */
    public String onbellektekiRol(UUID id) {
        return rolOnbellek.get(id);
    }

    private Scoreboard skor() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    public void guncelle(Player p) {
        String rol = rol(p);
        if (rol == null) rolOnbellek.remove(p.getUniqueId());
        else rolOnbellek.put(p.getUniqueId(), rol);

        String durum = null;
        if (plugin.gozalti().kelepceliMi(p)) durum = "gozalti";
        else if (plugin.yarali().yaraliMi(p)) durum = "yarali";
        else if (plugin.ceza().gecikmisVar(p.getUniqueId())) durum = "araniyor";

        String isim = p.getName();
        Team simdiki = skor().getEntryTeam(isim);
        boolean bizde = simdiki != null && simdiki.getName().startsWith("pk_");
        String eski = bizde ? eskiTakim.get(p.getUniqueId()) : (simdiki != null ? simdiki.getName() : null);
        String istenen = plugin.getConfig().getBoolean("gorsel.kafa-etiketi", true) ? takimAdi(durum, rol, eski) : null;

        if (istenen == null) {
            if (bizde) birak(p, simdiki);
            return;
        }
        Team hedef = takim(istenen, durum, rol, eski);
        if (simdiki != null && simdiki.getName().equals(istenen)) return;
        if (!bizde) {
            if (eski != null) eskiTakim.put(p.getUniqueId(), eski);
            else eskiTakim.remove(p.getUniqueId());
            kaydet();
        }
        hedef.addEntry(isim); // önceki takımdan kendiliğinden çıkar
    }

    private void birak(Player p, Team bizim) {
        bizim.removeEntry(p.getName());
        String eski = eskiTakim.remove(p.getUniqueId());
        Team t = eski != null ? skor().getTeam(eski) : null;
        if (t != null) t.addEntry(p.getName());
        kaydet();
    }

    /** Takımı bulur ya da kurar; öneki her seferinde tazeler (eski takımın öneki ya da simge ayarı değişmiş olabilir). */
    private Team takim(String ad, String durum, String rol, String eski) {
        Scoreboard sb = skor();
        Team t = sb.getTeam(ad);
        if (t == null) t = sb.registerNewTeam(ad);
        Component onek;
        NamedTextColor renk;
        if (durum != null) {
            switch (durum) {
                case "gozalti" -> {
                    onek = Component.text("[GÖZALTI] ", NamedTextColor.RED);
                    renk = NamedTextColor.RED;
                }
                case "yarali" -> {
                    onek = Component.text("[YARALI] ", NamedTextColor.GOLD);
                    renk = NamedTextColor.GOLD;
                }
                default -> {
                    onek = Component.text("[ARANIYOR] ", NamedTextColor.DARK_RED);
                    renk = NamedTextColor.DARK_RED;
                }
            }
            onek = onek.decoration(TextDecoration.BOLD, true);
        } else {
            onek = Component.text(rolOneki(rol, simgeli()), rolRengi(rol));
            renk = NamedTextColor.WHITE;
            Team et = eski != null ? sb.getTeam(eski) : null;
            if (et != null) {
                onek = onek.append(et.prefix());
                if (et.hasColor()) {
                    TextColor c = et.color();
                    if (c instanceof NamedTextColor n) renk = n;
                }
                // Eski takımın soneki ve kuralları (isim görünürlüğü, çarpışma...) da korunsun
                if (!et.suffix().equals(t.suffix())) t.suffix(et.suffix());
                for (Team.Option o : Team.Option.values()) {
                    if (et.getOption(o) != t.getOption(o)) t.setOption(o, et.getOption(o));
                }
            }
        }
        // Sadece değiştiyse gönder (her saniye boşuna paket yollamasın)
        if (!onek.equals(t.prefix())) t.prefix(onek);
        if (!t.hasColor() || !renk.equals(t.color())) t.color(renk);
        return t;
    }

    /** Oyundan çıkan oyuncu eski takımına döner; eklenti kaldırılırsa kimse pk_ takımında kalmaz. Girince yeniden etiketlenir. */
    @org.bukkit.event.EventHandler
    public void onCik(org.bukkit.event.player.PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Team t = skor().getEntryTeam(p.getName());
        if (t != null && t.getName().startsWith("pk_")) birak(p, t);
    }

    /** Eklenti kapanırken: herkesi eski takımına geri koy. */
    public void kapat() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Team t = skor().getEntryTeam(p.getName());
            if (t != null && t.getName().startsWith("pk_")) birak(p, t);
        }
        kaydet();
    }

    // ------------------------------------------------------------------
    // Kayıt
    // ------------------------------------------------------------------

    public void yukle() {
        File kaynak = dosya.exists() ? dosya : new File(plugin.getDataFolder(), "gorsel.yml"); // 1.9'dan geçiş
        if (!kaynak.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(kaynak);
        ConfigurationSection cs = yml.getConfigurationSection("eski-takim");
        if (cs == null) return;
        for (String k : cs.getKeys(false)) {
            try {
                eskiTakim.put(UUID.fromString(k), cs.getString(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void kaydet() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, String> g : eskiTakim.entrySet()) yml.set("eski-takim." + g.getKey(), g.getValue());
        try {
            if (!dosya.getParentFile().exists()) dosya.getParentFile().mkdirs();
            yml.save(dosya);
        } catch (IOException e) {
            plugin.getLogger().warning("etiket.yml kaydedilemedi: " + e.getMessage());
        }
    }
}
