package tr.efe.bekci;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /bekci geri <ad> <süre> [yarıçap] ve /bekci onayla: bir oyuncunun blok işlemlerini geri alır. */
public final class GeriAlma {

    private record Bekleyen(List<Islem> kayitlar, long bitis, String ozet) {}


    private final BekciPlugin plugin;
    private final Map<String, Bekleyen> bekleyenler = new HashMap<>();

    public GeriAlma(BekciPlugin plugin) {
        this.plugin = plugin;
    }

    public void geri(CommandSender s, String[] args) {
        if (args.length < 3) {
            plugin.mesaj(s, "Kullanım: /bekci geri <ad> <süre> [yarıçap]  (örn: /bekci geri Ahmet 2sa)", NamedTextColor.YELLOW);
            return;
        }
        long sure = Sure.oku(args[2]);
        if (sure <= 0) {
            plugin.mesaj(s, "Süre anlaşılmadı: " + args[2], NamedTextColor.RED);
            return;
        }
        StringBuilder where = new StringBuilder("isim = ? COLLATE NOCASE AND zaman >= ? AND geri = 0 AND tur IN ('KIR','KOY','KOVA','ATES') AND "
                + GeriAlmaKurali.BASKASI_SONRA_DEGISTIRMEDI);
        List<Object> param = new ArrayList<>(List.of(args[1], System.currentTimeMillis() - sure));
        String alan = "her yerde";
        if (args.length >= 4) {
            if (!(s instanceof Player p)) {
                plugin.mesaj(s, "Yarıçap sadece oyun içinde kullanılır.", NamedTextColor.RED);
                return;
            }
            int r;
            try {
                r = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                plugin.mesaj(s, "Yarıçap sayı olmalı.", NamedTextColor.RED);
                return;
            }
            Location l = p.getLocation();
            where.append(" AND dunya = ? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ?");
            param.addAll(List.of(l.getWorld().getName(), l.getBlockX() - r, l.getBlockX() + r, l.getBlockY() - r, l.getBlockY() + r,
                    l.getBlockZ() - r, l.getBlockZ() + r));
            alan = r + " blok çevrende";
        }
        String ozet = args[1] + ", son " + Sure.yaz(sure) + ", " + alan;
        plugin.db().sorgu(where.toString(), param, 1_000_000, 0).thenAccept(liste -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (liste.isEmpty()) {
                plugin.mesaj(s, "Geri alınacak blok işlemi yok (" + ozet + ").", NamedTextColor.GRAY);
                return;
            }
            bekleyenler.put(s.getName(), new Bekleyen(liste, System.currentTimeMillis() + 60_000L, ozet));
            plugin.mesaj(s, "Önizleme: " + ozet + " -> " + liste.size() + " blok işlemi geri alınacak. Sonradan başkasının değiştirdiği bloklara dokunulmaz, sandık eşyaları geri gelmez.",
                    NamedTextColor.GOLD);
            plugin.mesaj(s, "Onaylamak için 60 sn içinde: /bekci onayla", NamedTextColor.YELLOW);
        }));
    }

    public void onayla(CommandSender s) {
        Bekleyen b = bekleyenler.remove(s.getName());
        if (b == null || b.bitis() < System.currentTimeMillis()) {
            plugin.mesaj(s, "Onaylanacak işlem yok. Önce /bekci geri <ad> <süre>", NamedTextColor.YELLOW);
            return;
        }
        plugin.mesaj(s, "Geri alma başladı (" + b.kayitlar().size() + " işlem)...", NamedTextColor.AQUA);
        List<Long> uygulanan = new ArrayList<>();
        int[] durum = {0, 0}; // sıra, atlanan
        BukkitTask[] gorev = new BukkitTask[1];
        gorev[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<Islem> l = b.kayitlar();
            int islenen = 0, yuklenen = 0;
            while (durum[0] < l.size() && GeriAlmaKurali.buTickDevam(islenen, yuklenen)) {
                Islem i = l.get(durum[0]++);
                World w = Bukkit.getWorld(i.dunya());
                if (w != null && !w.isChunkLoaded(i.x() >> 4, i.z() >> 4)) yuklenen++;
                if (uygula(i)) uygulanan.add(i.id());
                else durum[1]++;
                islenen++;
            }
            if (durum[0] < l.size()) return;
            gorev[0].cancel();
            plugin.db().isaretle(uygulanan);
            geriKaydi(s, b.ozet() + ": " + uygulanan.size() + " blok");
            plugin.mesaj(s, "Bitti: " + uygulanan.size() + " geri alındı, " + durum[1] + " atlandı (sonradan değişmiş).", NamedTextColor.GREEN);
        }, 1L, 1L);
    }

    private void geriKaydi(CommandSender s, String aciklama) {
        Location l = s instanceof Player p ? p.getLocation() : null;
        plugin.db().ekle(Islem.yeni(s instanceof Player p2 ? p2.getUniqueId() : null, s.getName(),
                l != null ? l.getWorld().getName() : "-", l != null ? l.getBlockX() : 0, l != null ? l.getBlockY() : 0,
                l != null ? l.getBlockZ() : 0, Islem.Tur.GERI, null, null, aciklama, 0));
    }

    private static boolean uygula(Islem i) {
        World w = Bukkit.getWorld(i.dunya());
        if (w == null) return false;
        Block b = w.getBlockAt(i.x(), i.y(), i.z());
        String hedef = GeriAlmaKurali.karar(i.tur(), i.eski(), i.yeni(), b.getBlockData().getAsString());
        if (hedef == null) return false;
        try {
            b.setBlockData(Bukkit.createBlockData(hedef), false);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
