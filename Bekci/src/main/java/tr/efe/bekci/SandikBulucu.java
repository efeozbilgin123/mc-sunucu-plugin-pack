package tr.efe.bekci;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sandık bulucu (op):
 * - /bekci sandik yakin [yarıçap]: yüklü bölgedeki bütün kaplar, en yakından; Java'da duvar arkasından parlar.
 * - /bekci sandik oyuncu <ad>: oyuncunun kurulumdan sonra koyduğu ve hâlâ duran kaplar.
 * - /bekci git <no>: son listedeki kaba ışınlan (Bedrock için).
 * - /bekci ender <oyuncu>: ender sandığına sadece bak.
 */
public final class SandikBulucu implements Listener {

    private static final Set<String> KAPLAR = Set.of("CHEST", "TRAPPED_CHEST", "BARREL", "FURNACE", "BLAST_FURNACE", "SMOKER",
            "HOPPER", "DISPENSER", "DROPPER", "BREWING_STAND", "CRAFTER");

    /** Ender sandığı hariç bütün kaplar (renkli shulker ve bakır sandık türleri dahil). */
    public static boolean kapMi(Material m) {
        if (m == null) return false;
        String n = m.name();
        return KAPLAR.contains(n) || n.endsWith("SHULKER_BOX") || n.endsWith("COPPER_CHEST");
    }

    static boolean bedrockMu(UUID id) {
        return id.getMostSignificantBits() == 0;
    }

    private record Bulunan(Location yer, double uzaklik, int dolu, Material tur) {}

    private static final class EnderBakis implements InventoryHolder {
        private Inventory envanter;

        @Override
        public Inventory getInventory() {
            return envanter;
        }
    }

    private final BekciPlugin plugin;
    /** oyuncu -> son listedeki kap konumları (/bekci git için) */
    private final Map<UUID, List<Location>> sonListe = new HashMap<>();
    private final List<UUID> parlayanlar = new ArrayList<>();

    public SandikBulucu(BekciPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // /bekci sandik yakin [yarıçap]
    // ------------------------------------------------------------------

    public void yakin(Player p, String[] args) {
        int vars = plugin.getConfig().getInt("sandik.varsayilan", 64);
        int enFazla = plugin.getConfig().getInt("sandik.en-fazla", 160);
        int r = vars;
        if (args.length >= 3) {
            try {
                r = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
            }
        }
        r = Math.max(1, Math.min(enFazla, r));
        Location ben = p.getLocation();
        World w = p.getWorld();
        List<Bulunan> bulunan = new ArrayList<>();
        int cx0 = (ben.getBlockX() - r) >> 4, cx1 = (ben.getBlockX() + r) >> 4;
        int cz0 = (ben.getBlockZ() - r) >> 4, cz1 = (ben.getBlockZ() + r) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!w.isChunkLoaded(cx, cz)) continue;
                Chunk c = w.getChunkAt(cx, cz);
                for (BlockState st : c.getTileEntities()) {
                    if (!kapMi(st.getType())) continue;
                    double d = st.getLocation().distance(ben);
                    if (d > r) continue;
                    int dolu = 0;
                    if (st instanceof Container k) for (ItemStack is : k.getInventory().getContents()) if (is != null && is.getType() != Material.AIR) dolu++;
                    bulunan.add(new Bulunan(st.getLocation(), d, dolu, st.getType()));
                }
            }
        }
        bulunan.sort((a, b) -> Double.compare(a.uzaklik(), b.uzaklik()));
        if (bulunan.size() > 30) bulunan = new ArrayList<>(bulunan.subList(0, 30));
        if (bulunan.isEmpty()) {
            plugin.mesaj(p, r + " blok içinde (yüklü bölgede) kap yok.", NamedTextColor.GRAY);
            return;
        }
        List<Location> konumlar = new ArrayList<>();
        for (Bulunan b : bulunan) konumlar.add(b.yer());
        sonListe.put(p.getUniqueId(), konumlar);

        // Koyan kişiyi tek sorguyla bul: bu konumlardaki son KOY kayıtları
        StringBuilder where = new StringBuilder("tur = 'KOY' AND dunya = ? AND (");
        List<Object> param = new ArrayList<>();
        param.add(w.getName());
        for (int i = 0; i < bulunan.size(); i++) {
            Location l = bulunan.get(i).yer();
            if (i > 0) where.append(" OR ");
            where.append("(x = ? AND y = ? AND z = ?)");
            param.add(l.getBlockX());
            param.add(l.getBlockY());
            param.add(l.getBlockZ());
        }
        where.append(")");
        final List<Bulunan> liste = bulunan;
        final int yaricap = r;
        plugin.db().sorgu(where.toString(), param, 500, 0).thenAccept(kayitlar -> Bukkit.getScheduler().runTask(plugin, () -> {
            Map<String, String> koyan = new HashMap<>();
            for (Islem i : kayitlar) koyan.putIfAbsent(i.x() + ";" + i.y() + ";" + i.z(), i.isim()); // en yeni ilk gelir
            plugin.mesaj(p, yaricap + " blok içindeki kaplar (" + liste.size() + ", en yakın üstte):", NamedTextColor.DARK_AQUA);
            for (int n = 0; n < liste.size(); n++) {
                Bulunan b = liste.get(n);
                Location l = b.yer();
                String kim = koyan.getOrDefault(l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ(), "bilinmiyor");
                Component satir = Component.text("  " + (n + 1) + ". " + b.tur().name().toLowerCase(java.util.Locale.ROOT) + " "
                        + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + " - " + (int) b.uzaklik() + " blok - "
                        + b.dolu() + " dolu yuva - koyan: " + kim, NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.runCommand("/bekci git " + (n + 1)));
                p.sendMessage(satir);
            }
            plugin.satir(p, "Tıkla ya da /bekci git <no> ile ışınlan. Kapların üstünde 30 sn parçacık sütunu var.");
            isaretle(p, liste);
        }));
    }

    /** 30 sn: sadece bu oyuncunun gördüğü parçacık sütunu; Java'da duvar arkasından görünen parlayan çerçeve. */
    private void isaretle(Player p, List<Bulunan> liste) {
        List<UUID> bunlar = new ArrayList<>();
        if (!bedrockMu(p.getUniqueId()) && plugin.getConfig().getBoolean("sandik.xray", true)) {
            for (Bulunan b : liste) {
                Location l = b.yer().clone();
                org.bukkit.block.data.BlockData veri = l.getBlock().getBlockData();
                // Blok görüntüsü: çarpışma kutusu yok, sandığı açmayı engellemez; parlayan çerçeve duvar arkasından görünür
                BlockDisplay d = l.getWorld().spawn(l, BlockDisplay.class, bd -> {
                    bd.setVisibleByDefault(false);
                    bd.setBlock(veri);
                    bd.setPersistent(false);
                    bd.setGlowing(true);
                    bd.setGlowColorOverride(org.bukkit.Color.fromRGB(0xFFC800));
                    bd.setViewRange(3f);
                });
                p.showEntity(plugin, d);
                bunlar.add(d.getUniqueId());
            }
            parlayanlar.addAll(bunlar);
        }
        int[] sayac = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, gorev -> {
            if (++sayac[0] > 30 || !p.isOnline()) {
                for (UUID id : bunlar) {
                    Entity e = Bukkit.getEntity(id);
                    if (e != null) e.remove();
                }
                parlayanlar.removeAll(bunlar);
                gorev.cancel();
                return;
            }
            for (Bulunan b : liste) {
                if (!b.yer().getWorld().equals(p.getWorld())) continue;
                for (double y = 1; y <= 6; y += 0.5) {
                    p.spawnParticle(Particle.END_ROD, b.yer().clone().add(0.5, y, 0.5), 1, 0, 0, 0, 0);
                }
            }
        }, 0L, 20L);
    }

    // ------------------------------------------------------------------
    // /bekci git <no>
    // ------------------------------------------------------------------

    public void git(Player p, String[] args) {
        List<Location> l = sonListe.get(p.getUniqueId());
        if (l == null || args.length < 2) {
            plugin.mesaj(p, "Önce /bekci sandik yakin ya da /bekci sandik oyuncu ile liste al, sonra /bekci git <no>.", NamedTextColor.YELLOW);
            return;
        }
        int no;
        try {
            no = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            no = -1;
        }
        if (no < 1 || no > l.size()) {
            plugin.mesaj(p, "Listede böyle bir numara yok (1-" + l.size() + ").", NamedTextColor.RED);
            return;
        }
        Location hedef = l.get(no - 1).clone().add(0.5, 1, 0.5);
        hedef.setYaw(p.getLocation().getYaw());
        hedef.setPitch(p.getLocation().getPitch());
        p.teleport(hedef);
    }

    // ------------------------------------------------------------------
    // /bekci sandik oyuncu <ad>
    // ------------------------------------------------------------------

    public void oyuncu(CommandSender s, String[] args) {
        if (args.length < 3) {
            plugin.mesaj(s, "Kullanım: /bekci sandik oyuncu <ad>", NamedTextColor.YELLOW);
            return;
        }
        String ad = args[2];
        plugin.db().sorgu("isim = ? COLLATE NOCASE AND tur = 'KOY'", List.of(ad), 5000, 0)
                .thenAccept(kayitlar -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, Islem> son = new LinkedHashMap<>();
                    for (Islem i : kayitlar) {
                        Material m = malzeme(i.yeni());
                        if (!kapMi(m)) continue;
                        son.putIfAbsent(i.dunya() + ";" + i.x() + ";" + i.y() + ";" + i.z(), i);
                    }
                    List<Location> konumlar = new ArrayList<>();
                    List<Component> satirlar = new ArrayList<>();
                    for (Islem i : son.values()) {
                        World w = Bukkit.getWorld(i.dunya());
                        if (w == null) continue;
                        Material m = malzeme(i.yeni());
                        String durum;
                        if (w.isChunkLoaded(i.x() >> 4, i.z() >> 4)) {
                            if (w.getBlockAt(i.x(), i.y(), i.z()).getType() != m) continue; // kırılmış / değişmiş
                            durum = "duruyor";
                        } else {
                            durum = "kontrol edilmedi";
                        }
                        konumlar.add(new Location(w, i.x(), i.y(), i.z(), 0f, 0f));
                        int no = konumlar.size();
                        satirlar.add(Component.text("  " + no + ". " + m.name().toLowerCase(java.util.Locale.ROOT) + " " + i.x() + " " + i.y() + " "
                                + i.z() + " (" + i.dunya() + ") - " + durum, NamedTextColor.GRAY)
                                .clickEvent(ClickEvent.runCommand("/bekci git " + no)));
                        if (konumlar.size() >= 50) break;
                    }
                    if (konumlar.isEmpty()) {
                        plugin.mesaj(s, ad + " adlı oyuncunun koyduğu ve hâlâ duran kap bulunamadı (sadece kurulumdan sonrası bilinir).", NamedTextColor.GRAY);
                        return;
                    }
                    if (s instanceof Player p) sonListe.put(p.getUniqueId(), konumlar);
                    plugin.mesaj(s, ad + " adlı oyuncunun koyduğu kaplar (" + konumlar.size() + "):", NamedTextColor.DARK_AQUA);
                    for (Component c : satirlar) s.sendMessage(c);
                    plugin.satir(s, "Tıkla ya da /bekci git <no> ile ışınlan.");
                }));
    }

    private static Material malzeme(String blokVerisi) {
        if (blokVerisi == null) return null;
        int k = blokVerisi.indexOf('[');
        return Material.matchMaterial(k >= 0 ? blokVerisi.substring(0, k) : blokVerisi);
    }

    // ------------------------------------------------------------------
    // /bekci ender <oyuncu>
    // ------------------------------------------------------------------

    public void ender(Player p, String[] args) {
        if (args.length < 2) {
            plugin.mesaj(p, "Kullanım: /bekci ender <oyuncu>", NamedTextColor.YELLOW);
            return;
        }
        Player h = Bukkit.getPlayerExact(args[1]);
        if (h == null) {
            plugin.mesaj(p, args[1] + " oyunda değil (ender sandığına sadece oyundayken bakılabilir).", NamedTextColor.RED);
            return;
        }
        EnderBakis b = new EnderBakis();
        b.envanter = Bukkit.createInventory(b, 27, Component.text(h.getName() + " - ender sandığı (sadece bakış)"));
        ItemStack[] ic = h.getEnderChest().getContents();
        for (int i = 0; i < ic.length && i < 27; i++) if (ic[i] != null) b.envanter.setItem(i, ic[i].clone());
        p.openInventory(b.envanter);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTik(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EnderBakis) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSurukle(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EnderBakis) e.setCancelled(true);
    }

    /** Eklenti kapanırken parlayan işaretleri sil. */
    public void kapat() {
        for (UUID id : parlayanlar) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        parlayanlar.clear();
    }
}
