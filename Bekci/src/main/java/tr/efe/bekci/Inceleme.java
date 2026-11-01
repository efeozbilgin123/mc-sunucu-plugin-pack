package tr.efe.bekci;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** /bekci bak, /bekci oyuncu, /bekci yakin */
public final class Inceleme implements Listener {

    private static final int SAYFA = 10;

    private final BekciPlugin plugin;
    private final Set<UUID> inceleyenler = new HashSet<>();

    public Inceleme(BekciPlugin plugin) {
        this.plugin = plugin;
    }

    /** "[12 dk önce] Ahmet KIRDI stone (10 64 -3)" */
    public static String satir(Islem i, long simdi) {
        String ne = switch (i.tur()) {
            case KIR -> "KIRDI " + blokAdi(i.eski());
            case KOY -> "KOYDU " + blokAdi(i.yeni());
            case KOVA -> "DÖKTÜ " + blokAdi(i.yeni());
            case ATES -> "ATEŞ YAKTI";
            case TNT -> "TNT YAKTI";
            case AL -> "ALDI " + i.miktar() + " " + i.esya();
            case KOY_KAP -> "KOYDU " + i.miktar() + " " + i.esya();
            case KIR_ICERIK -> "KIRDIĞI KAPTA: " + i.miktar() + " " + i.esya();
            case ENDER_AL -> "ENDERDEN ALDI " + i.miktar() + " " + i.esya();
            case ENDER_KOY -> "ENDERE KOYDU " + i.miktar() + " " + i.esya();
            case MEZAR_AL -> "MEZARDAN ALDI " + i.miktar() + " " + i.esya();
            case MEZAR_KIR -> "MEZAR KIRDI" + (i.esya() != null ? " (" + i.esya() + ")" : "");
            case MEZAR_SILINDI -> "MEZARI SİLİNDİ" + (i.esya() != null ? ": " + i.esya() : "");
            case GERI -> "GERİ ALDI: " + (i.esya() != null ? i.esya() : "");
        };
        return "[" + Sure.yaz(simdi - i.zaman()) + " önce] " + i.isim() + " " + ne + " (" + i.x() + " " + i.y() + " " + i.z() + ")"
                + (i.geriAlindi() ? " [geri alındı]" : "");
    }

    /** "minecraft:oak_stairs[facing=east]" -> "oak_stairs" */
    static String blokAdi(String veri) {
        if (veri == null) return "?";
        String s = veri.startsWith("minecraft:") ? veri.substring(10) : veri;
        int k = s.indexOf('[');
        return k >= 0 ? s.substring(0, k) : s;
    }

    // ------------------------------------------------------------------
    // İnceleme modu
    // ------------------------------------------------------------------

    public void bak(Player p) {
        if (inceleyenler.remove(p.getUniqueId())) {
            plugin.mesaj(p, "İnceleme modu kapandı.", NamedTextColor.GRAY);
        } else {
            inceleyenler.add(p.getUniqueId());
            plugin.mesaj(p, "İnceleme modu açık: sol tık = o blok, sağ tık = tıkladığın yüzün önü (kırılan blokların yeri). Kapatmak: /bekci bak",
                    NamedTextColor.AQUA);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTik(PlayerInteractEvent e) {
        if (!inceleyenler.contains(e.getPlayer().getUniqueId()) || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            konumGoster(e.getPlayer(), b.getLocation());
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            konumGoster(e.getPlayer(), b.getRelative(e.getBlockFace()).getLocation());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKir(BlockBreakEvent e) {
        if (inceleyenler.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onCik(PlayerQuitEvent e) {
        inceleyenler.remove(e.getPlayer().getUniqueId());
    }

    private void konumGoster(Player p, Location l) {
        plugin.db().sorgu("dunya = ? AND x = ? AND y = ? AND z = ?",
                List.of(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()), SAYFA, 0)
                .thenAccept(liste -> Bukkit.getScheduler().runTask(plugin, () ->
                        yazdir(p, "Konum " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ(), liste, 1, null)));
    }

    // ------------------------------------------------------------------
    // Sorgular
    // ------------------------------------------------------------------

    /** /bekci oyuncu <ad> <süre> [sayfa] */
    public void oyuncu(CommandSender s, String[] args) {
        if (args.length < 3) {
            plugin.mesaj(s, "Kullanım: /bekci oyuncu <ad> <süre> [sayfa]  (süre: 30dk, 2sa, 1g)", NamedTextColor.YELLOW);
            return;
        }
        long sure = Sure.oku(args[2]);
        if (sure <= 0) {
            plugin.mesaj(s, "Süre anlaşılmadı: " + args[2], NamedTextColor.RED);
            return;
        }
        int sayfa = sayfa(args, 3);
        plugin.db().sorgu("isim = ? COLLATE NOCASE AND zaman >= ?", List.of(args[1], System.currentTimeMillis() - sure), SAYFA, (sayfa - 1) * SAYFA)
                .thenAccept(liste -> Bukkit.getScheduler().runTask(plugin, () -> yazdir(s, args[1] + ", son " + Sure.yaz(sure), liste, sayfa,
                        "/bekci oyuncu " + args[1] + " " + args[2] + " " + (sayfa + 1))));
    }

    /** /bekci yakin <yarıçap> <süre> [sayfa] */
    public void yakin(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return;
        if (args.length < 3) {
            plugin.mesaj(s, "Kullanım: /bekci yakin <yarıçap> <süre> [sayfa]", NamedTextColor.YELLOW);
            return;
        }
        int r;
        try {
            r = Math.max(1, Math.min(100, Integer.parseInt(args[1])));
        } catch (NumberFormatException e) {
            plugin.mesaj(s, "Yarıçap sayı olmalı.", NamedTextColor.RED);
            return;
        }
        long sure = Sure.oku(args[2]);
        if (sure <= 0) {
            plugin.mesaj(s, "Süre anlaşılmadı: " + args[2], NamedTextColor.RED);
            return;
        }
        int sayfa = sayfa(args, 3);
        Location l = p.getLocation();
        List<Object> param = new ArrayList<>(List.of(l.getWorld().getName(),
                l.getBlockX() - r, l.getBlockX() + r, l.getBlockY() - r, l.getBlockY() + r, l.getBlockZ() - r, l.getBlockZ() + r,
                System.currentTimeMillis() - sure));
        plugin.db().sorgu("dunya = ? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ? AND zaman >= ?", param, SAYFA, (sayfa - 1) * SAYFA)
                .thenAccept(liste -> Bukkit.getScheduler().runTask(plugin, () -> yazdir(s, r + " blok çevre, son " + Sure.yaz(sure), liste, sayfa,
                        "/bekci yakin " + args[1] + " " + args[2] + " " + (sayfa + 1))));
    }

    private static int sayfa(String[] args, int i) {
        if (args.length <= i) return 1;
        try {
            return Math.max(1, Integer.parseInt(args[i]));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void yazdir(CommandSender s, String baslik, List<Islem> liste, int sayfa, String devam) {
        if (liste.isEmpty()) {
            plugin.mesaj(s, baslik + ": kayıt yok" + (sayfa > 1 ? " (sayfa " + sayfa + ")" : "") + ".", NamedTextColor.GRAY);
            return;
        }
        plugin.mesaj(s, baslik + " - sayfa " + sayfa + " (en yeni üstte):", NamedTextColor.AQUA);
        long simdi = System.currentTimeMillis();
        for (Islem i : liste) s.sendMessage(Component.text("  " + satir(i, simdi), renk(i.tur())));
        if (devam != null && liste.size() == SAYFA) s.sendMessage(Component.text("  Devamı: " + devam, NamedTextColor.DARK_GRAY));
    }

    private static NamedTextColor renk(Islem.Tur t) {
        return switch (t) {
            case KIR, AL, ENDER_AL, MEZAR_AL, MEZAR_KIR, KIR_ICERIK -> NamedTextColor.RED;
            case KOY, KOY_KAP, ENDER_KOY -> NamedTextColor.GREEN;
            case KOVA, ATES, TNT -> NamedTextColor.GOLD;
            default -> NamedTextColor.GRAY;
        };
    }
}
