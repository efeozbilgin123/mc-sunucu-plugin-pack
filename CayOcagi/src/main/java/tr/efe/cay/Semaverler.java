package tr.efe.cay;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Demlenmekte olan kazanları tutar: konum -> çayın hazır olacağı zaman (epoch ms).
 * Sunucu kapanıp açılınca kaybolmasın diye data.yml'ye kaydedilir.
 */
public final class Semaverler {

    private final Map<String, Long> hazirZamani = new HashMap<>();
    private final Set<String> haberVerildi = new HashSet<>();
    /** Çay çalısı olarak işaretlenmiş tatlı meyve çalıları. */
    private final Set<String> fidanlar = new HashSet<>();
    private final File dosya;
    private final Logger log;

    public Semaverler(File dataFolder, Logger log) {
        this.dosya = new File(dataFolder, "data.yml");
        this.log = log;
    }

    /** Semaverin üstündeki yazı: "Demleniyor... %60" ya da "Çay hazır!". */
    public static String demYazisi(long kalanMs, long toplamMs) {
        if (kalanMs <= 0) return "Çay hazır!";
        long yuzde = toplamMs <= 0 ? 0 : Math.max(0, Math.min(99, (toplamMs - kalanMs) * 100 / toplamMs));
        return "Demleniyor... %" + yuzde;
    }

    public static String anahtar(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    public static Block blok(String anahtar) {
        String[] p = anahtar.split(";");
        if (p.length != 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean var(String anahtar) {
        return hazirZamani.containsKey(anahtar);
    }

    public boolean hazir(String anahtar) {
        Long t = hazirZamani.get(anahtar);
        return t != null && System.currentTimeMillis() >= t;
    }

    /** Kalan süre (saniye, yukarı yuvarlanmış). Hazırsa 0. */
    public long kalanSaniye(String anahtar) {
        Long t = hazirZamani.get(anahtar);
        if (t == null) return 0;
        long ms = t - System.currentTimeMillis();
        return ms <= 0 ? 0 : (ms + 999) / 1000;
    }

    public void baslat(String anahtar, long sureSaniye) {
        hazirZamani.put(anahtar, System.currentTimeMillis() + sureSaniye * 1000L);
        haberVerildi.remove(anahtar);
    }

    /** Kamp ateşi sönükken demlenmeyi dondurmak için süreyi uzatır. */
    public void ertele(String anahtar, long ms) {
        hazirZamani.computeIfPresent(anahtar, (k, v) -> v + ms);
    }

    public void kaldir(String anahtar) {
        hazirZamani.remove(anahtar);
        haberVerildi.remove(anahtar);
    }

    /** Hazır olduğu ilk kez fark edildiyse true döner (haber vermek için). */
    public boolean ilkKezHazir(String anahtar) {
        return haberVerildi.add(anahtar);
    }

    public void haberSil(String anahtar) {
        haberVerildi.remove(anahtar);
    }

    public void fidanEkle(String anahtar) {
        fidanlar.add(anahtar);
    }

    public boolean fidanVar(String anahtar) {
        return fidanlar.contains(anahtar);
    }

    public void fidanSil(String anahtar) {
        fidanlar.remove(anahtar);
    }

    public Iterator<Map.Entry<String, Long>> iterator() {
        return hazirZamani.entrySet().iterator();
    }

    public void yukle() {
        hazirZamani.clear();
        haberVerildi.clear();
        fidanlar.clear();
        if (!dosya.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dosya);
        fidanlar.addAll(yml.getStringList("fidanlar"));
        for (String satir : yml.getStringList("demlikler")) {
            int i = satir.lastIndexOf(';');
            if (i <= 0) continue;
            try {
                hazirZamani.put(satir.substring(0, i), Long.parseLong(satir.substring(i + 1)));
            } catch (NumberFormatException ignored) {
                // bozuk satırı atla
            }
        }
    }

    public void kaydet() {
        YamlConfiguration yml = new YamlConfiguration();
        List<String> satirlar = new ArrayList<>();
        for (Map.Entry<String, Long> e : hazirZamani.entrySet()) {
            satirlar.add(e.getKey() + ";" + e.getValue());
        }
        yml.set("demlikler", satirlar);
        yml.set("fidanlar", new ArrayList<>(fidanlar));
        try {
            if (!dosya.getParentFile().exists()) dosya.getParentFile().mkdirs();
            yml.save(dosya);
        } catch (IOException e) {
            log.warning("data.yml kaydedilemedi: " + e.getMessage());
        }
    }
}
