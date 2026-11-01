package tr.efe.bekci;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Blok, kova, ateş, TNT ve kap işlemlerini Bekçi kaydına yazar. */
public final class KayitDinleyici implements Listener {

    /** Açık kabın bilgisi: konum, ender mi, eşya adları; sayımlar KapIzleyici'de. */
    private record Goruntu(Location yer, boolean ender, Inventory envanter, Map<String, String> adlar) {}

    private final BekciPlugin plugin;
    private final Veritabani db;
    private final Map<UUID, Goruntu> acik = new HashMap<>();
    private final KapIzleyici izleyici = new KapIzleyici();

    public KayitDinleyici(BekciPlugin plugin, Veritabani db) {
        this.plugin = plugin;
        this.db = db;
    }

    private static String kapAnahtari(Location l, boolean ender, UUID sahip) {
        return ender ? "ender:" + sahip : l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    private static Map<String, Integer> sayim(Inventory inv, Map<String, String> adlar) {
        Map<String, Integer> m = new HashMap<>();
        say(inv.getContents(), m, adlar);
        return m;
    }

    private void farkYaz(Player p, Goruntu g, Map<String, Integer> fark) {
        for (Map.Entry<String, Integer> f : fark.entrySet()) {
            boolean kondu = f.getValue() > 0;
            Islem.Tur tur = g.ender() ? (kondu ? Islem.Tur.ENDER_KOY : Islem.Tur.ENDER_AL) : (kondu ? Islem.Tur.KOY_KAP : Islem.Tur.AL);
            yaz(p, g.yer(), tur, null, null, g.adlar().getOrDefault(f.getKey(), f.getKey()), Math.abs(f.getValue()));
        }
    }

    private void yaz(Player p, Location l, Islem.Tur tur, String eski, String yeni, String esya, int miktar) {
        db.ekle(Islem.yeni(p == null ? null : p.getUniqueId(), p == null ? "?" : p.getName(), l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), tur, eski, yeni, esya, miktar));
    }

    /** Eşya anahtarı: tür + (meta varsa) meta özeti. Aynı anahtarlılar birlikte sayılır. */
    static String anahtar(ItemStack is) {
        String t = is.getType().name().toLowerCase(Locale.ROOT);
        return is.hasItemMeta() ? t + "#" + Integer.toHexString(is.getItemMeta().hashCode()) : t;
    }

    static String ad(ItemStack is) {
        String t = is.getType().name().toLowerCase(Locale.ROOT);
        return is.hasItemMeta() ? t + " (özel)" : t;
    }

    static void say(ItemStack[] icerik, Map<String, Integer> sayim, Map<String, String> adlar) {
        for (ItemStack is : icerik) {
            if (is == null || is.getType() == Material.AIR) continue;
            String k = anahtar(is);
            sayim.merge(k, is.getAmount(), Integer::sum);
            adlar.putIfAbsent(k, ad(is));
        }
    }

    // ------------------------------------------------------------------
    // Bloklar
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKir(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (plugin.mezar() != null && plugin.mezar().mezarKirildi(b.getLocation())) return; // MEZAR_KIR olarak zaten yazıldı
        yaz(e.getPlayer(), b.getLocation(), Islem.Tur.KIR, b.getBlockData().getAsString(), "minecraft:air", null, 0);
        if (b.getState() instanceof Container c) {
            Map<String, Integer> sayim = new HashMap<>();
            Map<String, String> adlar = new HashMap<>();
            // Çift sandığın kırılan yarısında sadece o yarının içeriği düşer
            Inventory icerik = c instanceof org.bukkit.block.Chest ch ? ch.getBlockInventory() : c.getInventory();
            say(icerik.getContents(), sayim, adlar);
            for (Map.Entry<String, Integer> g : sayim.entrySet()) {
                yaz(e.getPlayer(), b.getLocation(), Islem.Tur.KIR_ICERIK, null, null, adlar.get(g.getKey()), g.getValue());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKoy(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        yaz(e.getPlayer(), b.getLocation(), Islem.Tur.KOY, e.getBlockReplacedState().getBlockData().getAsString(),
                b.getBlockData().getAsString(), null, 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKova(PlayerBucketEmptyEvent e) {
        Block b = e.getBlock();
        String sivi = e.getBucket() == Material.LAVA_BUCKET ? "minecraft:lava" : "minecraft:water";
        yaz(e.getPlayer(), b.getLocation(), Islem.Tur.KOVA, b.getBlockData().getAsString(), sivi,
                e.getBucket().name().toLowerCase(Locale.ROOT), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAtes(BlockIgniteEvent e) {
        if (e.getPlayer() == null) return;
        yaz(e.getPlayer(), e.getBlock().getLocation(), Islem.Tur.ATES, e.getBlock().getBlockData().getAsString(), "minecraft:fire", null, 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTnt(TNTPrimeEvent e) {
        Entity t = e.getPrimingEntity();
        if (t instanceof Projectile pr && pr.getShooter() instanceof Entity atan) t = atan;
        if (!(t instanceof Player p)) return;
        yaz(p, e.getBlock().getLocation(), Islem.Tur.TNT, "minecraft:tnt", "minecraft:air", null, 0);
    }

    // ------------------------------------------------------------------
    // Kaplar ve ender sandığı: açılışta görüntü, kapanışta fark
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAc(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory inv = e.getInventory();
        boolean ender = inv.getType() == InventoryType.ENDER_CHEST;
        InventoryHolder h = inv.getHolder();
        if (!ender && !(h instanceof BlockState) && !(h instanceof DoubleChest)) return; // eklenti menüleri, oyuncu envanteri
        Location yer = inv.getLocation();
        if (yer == null) {
            Block bakilan = p.getTargetBlockExact(6);
            yer = bakilan != null ? bakilan.getLocation() : p.getLocation();
        }
        Map<String, String> adlar = new HashMap<>();
        Inventory izlenen = ender ? p.getEnderChest() : inv;
        Goruntu g = new Goruntu(yer.clone(), ender, izlenen, adlar);
        acik.put(p.getUniqueId(), g);
        izleyici.ac(p.getUniqueId(), kapAnahtari(g.yer(), ender, p.getUniqueId()), sayim(izlenen, adlar));
    }

    // Her tıklama/sürüklemeden bir tick sonra: değişiklik tıklayana yazılır, aynı kabı açık tutan diğerlerinin görüntüsü tazelenir
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTik(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && acik.containsKey(p.getUniqueId())) sonrakiTik(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSurukle(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && acik.containsKey(p.getUniqueId())) sonrakiTik(p);
    }

    private void sonrakiTik(Player p) {
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            Goruntu g = acik.get(p.getUniqueId());
            if (g == null) return;
            farkYaz(p, g, izleyici.degisti(p.getUniqueId(), sayim(g.envanter(), g.adlar())));
        });
    }

    // Huni vb. kabı değiştirince açık tutanların üstüne yazılmasın: görüntüyü tazele
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHuni(org.bukkit.event.inventory.InventoryMoveItemEvent e) {
        for (Inventory inv : new Inventory[]{e.getSource(), e.getDestination()}) {
            Location l = inv.getLocation();
            if (l == null || l.getWorld() == null) continue;
            String kap = kapAnahtari(l, false, null);
            if (!izleyici.izleniyor(kap)) continue;
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> izleyici.disaridan(kap, sayim(inv, new HashMap<>())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKapat(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Goruntu g = acik.remove(p.getUniqueId());
        if (g == null) return;
        farkYaz(p, g, izleyici.kapat(p.getUniqueId(), sayim(g.envanter(), g.adlar())));
    }

    @EventHandler
    public void onCik(PlayerQuitEvent e) {
        acik.remove(e.getPlayer().getUniqueId());
        izleyici.unut(e.getPlayer().getUniqueId());
    }
}
