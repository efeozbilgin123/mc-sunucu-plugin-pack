package tr.efe.bekci;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mezar: ölünce eşyalar ve XP, ölenin kafası şeklindeki bir mezara girer.
 * Sahibi, onu PvP'de öldüren ve polis/başkan her zaman; herkes belirli süre sonra açabilir.
 * Açabilen kırabilir (eşyalar saçılır). Patlama/piston/su işlemez. Belirli süre sonra silinir.
 */
public final class Mezar implements Listener, CommandExecutor, TabCompleter {

    private static final class Kayit {
        int no;
        UUID sahip;
        String sahipIsim;
        UUID olduren;
        long olusma;
        Location yer;
        int xp;
        boolean uyarildi;
        final List<ItemStack> esyalar = new ArrayList<>();
        UUID ustYazi;
        UUID altYazi;
    }

    private static final class MezarEkrani implements InventoryHolder {
        private final int no;
        private Inventory envanter;

        private MezarEkrani(int no) {
            this.no = no;
        }

        @Override
        public Inventory getInventory() {
            return envanter;
        }
    }

    private final BekciPlugin plugin;
    private final File dosya;
    private final Map<Integer, Kayit> mezarlar = new LinkedHashMap<>();
    private final Map<String, Integer> konumdan = new HashMap<>();
    /** Silinen ama o an yüklü olmayan mezar blokları: bölge yüklenince kaldırılır. */
    private final Set<String> silinecekBloklar = new HashSet<>();
    private final Map<UUID, Location> sonZemin = new HashMap<>();
    /** Bu tick kırılan mezarlar: Bekçi kaydı bunları düz "KIR" olarak ayrıca yazmasın. */
    private final Set<String> yeniKirilan = new HashSet<>();

    public boolean mezarKirildi(Location l) {
        return l.getWorld() != null && yeniKirilan.contains(anahtar(l));
    }
    private int sonNo = 0;
    private int saniye = 0;

    public Mezar(BekciPlugin plugin) {
        this.plugin = plugin;
        this.dosya = new File(plugin.getDataFolder(), "mezarlar.yml");
    }

    private long herkeseAcikMs() {
        return Math.max(0, plugin.getConfig().getLong("mezar.herkese-acik-dk", 30)) * 60_000L;
    }

    private long omurMs() {
        return Math.max(1, plugin.getConfig().getLong("mezar.silinme-saat", 24)) * 3_600_000L;
    }

    private static String anahtar(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    private static boolean polis(Player p) {
        return p.hasPermission("poliskelepce.rol.polis");
    }

    // ------------------------------------------------------------------
    // Ölüm: mezar kur
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOlum(PlayerDeathEvent e) {
        if (!plugin.getConfig().getBoolean("mezar.acik", true) || e.getKeepInventory()) return;
        Player p = e.getEntity();
        List<ItemStack> dusen = new ArrayList<>();
        for (ItemStack is : e.getDrops()) if (is != null && is.getType() != Material.AIR) dusen.add(is.clone());
        int xp = MezarKurali.mezarXp(p.calculateTotalExperiencePoints(), e.getDroppedExp(),
                plugin.getConfig().getInt("mezar.xp-orani", 100), e.getKeepLevel());
        if (dusen.isEmpty() && xp <= 0) return;
        Block b = yerBul(p);
        if (b == null) return; // uygun yer yok: vanilla gibi yere düşsün
        e.getDrops().clear();
        e.setDroppedExp(0);

        Kayit k = new Kayit();
        k.no = ++sonNo;
        k.sahip = p.getUniqueId();
        k.sahipIsim = p.getName();
        Player olduren = p.getKiller();
        k.olduren = olduren != null && !olduren.equals(p) ? olduren.getUniqueId() : null;
        k.olusma = System.currentTimeMillis();
        k.yer = b.getLocation();
        k.xp = xp;
        // 54 yuvadan fazlası (olmaz ama) yere düşer
        for (int i = 0; i < dusen.size(); i++) {
            if (i < 54) k.esyalar.add(dusen.get(i));
            else b.getWorld().dropItemNaturally(b.getLocation(), dusen.get(i));
        }
        b.setType(Material.PLAYER_HEAD);
        if (b.getState() instanceof Skull s) {
            s.setOwningPlayer(p);
            s.update();
        }
        mezarlar.put(k.no, k);
        konumdan.put(anahtar(k.yer), k.no);
        yazilariGuncelle(k);
        kaydet();
        plugin.mesaj(p, "Mezarın: " + b.getX() + " " + b.getY() + " " + b.getZ() + " (" + b.getWorld().getName()
                + "). " + omurMs() / 3_600_000L + " saat sonra silinir. Liste: /mezar", NamedTextColor.GOLD);
    }

    private static boolean uygun(Block b) {
        return b.isEmpty() && !b.getRelative(BlockFace.DOWN).isPassable();
    }

    private Block yerBul(Player p) {
        Block olum = p.getLocation().getBlock();
        World w = olum.getWorld();
        if (olum.getY() > w.getMinHeight() && olum.getY() < w.getMaxHeight() - 1) {
            if (uygun(olum)) return olum;
            Block enIyi = null;
            double enYakin = Double.MAX_VALUE;
            for (int dx = -5; dx <= 5; dx++) for (int dy = -5; dy <= 5; dy++) for (int dz = -5; dz <= 5; dz++) {
                int y = olum.getY() + dy;
                if (y <= w.getMinHeight() || y >= w.getMaxHeight() - 1) continue;
                Block b = olum.getRelative(dx, dy, dz);
                if (!uygun(b)) continue;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < enYakin) {
                    enYakin = d;
                    enIyi = b;
                }
            }
            if (enIyi != null) return enIyi;
        }
        Location zemin = sonZemin.get(p.getUniqueId());
        if (zemin != null && zemin.getWorld() != null) {
            Block b = zemin.getBlock();
            if (b.isEmpty() || b.isLiquid()) return b;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Açma ve menü
    // ------------------------------------------------------------------

    private Kayit mezarAt(Block b) {
        if (b == null || b.getType() != Material.PLAYER_HEAD) return null;
        Integer no = konumdan.get(anahtar(b.getLocation()));
        return no == null ? null : mezarlar.get(no);
    }

    private boolean yetkili(Player p, Kayit k) {
        return MezarKurali.acabilir(p.getUniqueId(), polis(p), p.hasPermission("bekci.admin"), k.sahip, k.olduren, k.olusma, System.currentTimeMillis(), herkeseAcikMs());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTik(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        // Kelepçeli, yaralı, giriş yapmamış (AuthMe) ya da başka bir sebeple engellenmiş tıklama mezar açmaz
        if (e.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        Kayit k = mezarAt(e.getClickedBlock());
        if (k == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!yetkili(p, k)) {
            long kalan = herkeseAcikMs() - (System.currentTimeMillis() - k.olusma);
            plugin.mesaj(p, k.sahipIsim + " adlı oyuncunun mezarı. Herkese açılmasına " + Sure.yaz(kalan) + " var.", NamedTextColor.RED);
            return;
        }
        if (p.getUniqueId().equals(k.sahip) && k.xp > 0) {
            p.giveExp(k.xp);
            k.xp = 0;
            kaydet();
        }
        MezarEkrani ekran = new MezarEkrani(k.no);
        ekran.envanter = Bukkit.createInventory(ekran, 54, Component.text(k.sahipIsim + "'in mezarı"));
        ciz(ekran, k);
        p.openInventory(ekran.envanter);
        if (bosMu(k)) kaldir(k);
    }

    private static void ciz(MezarEkrani ekran, Kayit k) {
        for (int i = 0; i < 54; i++) {
            ItemStack is = i < k.esyalar.size() ? k.esyalar.get(i) : null;
            ekran.envanter.setItem(i, is == null ? null : is.clone());
        }
    }

    private static boolean bosMu(Kayit k) {
        if (k.xp > 0) return false;
        for (ItemStack is : k.esyalar) if (is != null) return false;
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuTik(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MezarEkrani ekran)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int ham = e.getRawSlot();
        if (ham < 0 || ham >= 54) return;
        Kayit k = mezarlar.get(ekran.no);
        if (k == null || !yetkili(p, k)) {
            Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            return;
        }
        if (ham >= k.esyalar.size()) return;
        ItemStack goster = e.getView().getTopInventory().getItem(ham);
        ItemStack gercek = k.esyalar.get(ham);
        if (goster == null || goster.getType() == Material.AIR) return;
        if (!goster.equals(gercek)) { // başkası almış: hiçbir şey taşıma, yenile
            ciz(ekran, k);
            return;
        }
        k.esyalar.set(ham, null);
        for (ItemStack artan : p.getInventory().addItem(gercek.clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), artan);
        ciz(ekran, k);
        Location l = k.yer;
        plugin.db().ekle(Islem.yeni(p.getUniqueId(), p.getName(), l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                Islem.Tur.MEZAR_AL, null, null, KayitDinleyici.ad(gercek) + " (" + k.sahipIsim + "'in mezarı)", gercek.getAmount()));
        if (bosMu(k)) kaldir(k);
        else kaydet();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuSurukle(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MezarEkrani) e.setCancelled(true);
    }

    // ------------------------------------------------------------------
    // Kırma ve korunma
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKir(BlockBreakEvent e) {
        Kayit k = mezarAt(e.getBlock());
        if (k == null) return;
        Player p = e.getPlayer();
        if (!yetkili(p, k)) {
            e.setCancelled(true);
            plugin.mesaj(p, "Bu mezarı kıramazsın.", NamedTextColor.RED);
            return;
        }
        e.setDropItems(false);
        yeniKirilan.add(anahtar(k.yer));
        Bukkit.getScheduler().runTask(plugin, () -> yeniKirilan.remove(anahtar(k.yer)));
        Location l = k.yer.clone().add(0.5, 0.5, 0.5);
        for (ItemStack is : k.esyalar) if (is != null) l.getWorld().dropItemNaturally(l, is);
        if (k.xp > 0) {
            int xp = k.xp;
            l.getWorld().spawn(l, ExperienceOrb.class, o -> o.setExperience(xp));
        }
        plugin.db().ekle(Islem.yeni(p.getUniqueId(), p.getName(), l.getWorld().getName(), k.yer.getBlockX(), k.yer.getBlockY(),
                k.yer.getBlockZ(), Islem.Tur.MEZAR_KIR, null, null, k.sahipIsim + ": " + ozet(k), 0));
        k.esyalar.clear();
        k.xp = 0;
        kaldirKayit(k, false);
    }

    private boolean mezarBlogu(Block b) {
        return b.getType() == Material.PLAYER_HEAD && konumdan.containsKey(anahtar(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPatlama(EntityExplodeEvent e) {
        e.blockList().removeIf(this::mezarBlogu);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlokPatlama(BlockExplodeEvent e) {
        e.blockList().removeIf(this::mezarBlogu);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (mezarBlogu(b)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonCek(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (mezarBlogu(b)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAkis(BlockFromToEvent e) {
        if (mezarBlogu(e.getToBlock())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------
    // Zamanlayıcı
    // ------------------------------------------------------------------

    /** Her saniye. */
    public void tik() {
        saniye++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOnGround() && !p.getLocation().getBlock().getRelative(BlockFace.DOWN).isPassable()) {
                sonZemin.put(p.getUniqueId(), p.getLocation().getBlock().getLocation());
            }
        }
        long simdi = System.currentTimeMillis();
        boolean degisti = false;
        for (Kayit k : new ArrayList<>(mezarlar.values())) {
            World w = k.yer.getWorld();
            boolean yuklu = w != null && w.isChunkLoaded(k.yer.getBlockX() >> 4, k.yer.getBlockZ() >> 4);
            if (MezarKurali.silinmeli(k.olusma, simdi, omurMs())) {
                if (k.yer.getWorld() != null) {
                    plugin.db().ekle(Islem.yeni(k.sahip, k.sahipIsim, k.yer.getWorld().getName(), k.yer.getBlockX(), k.yer.getBlockY(),
                            k.yer.getBlockZ(), Islem.Tur.MEZAR_SILINDI, null, null, ozet(k), 0));
                }
                kaldirKayit(k, true);
                continue;
            }
            if (MezarKurali.uyarmali(k.olusma, simdi, omurMs(), k.uyarildi)) {
                Player sahip = Bukkit.getPlayer(k.sahip);
                if (sahip != null) {
                    k.uyarildi = true;
                    degisti = true;
                    plugin.mesaj(sahip, "Mezarın 1 saat içinde silinecek: " + k.yer.getBlockX() + " " + k.yer.getBlockY() + " " + k.yer.getBlockZ(),
                            NamedTextColor.RED);
                }
            }
            if (!yuklu) continue;
            if (saniye % 60 == 1) yazilariGuncelle(k);
            if (saniye % 3 == 0 && plugin.getConfig().getBoolean("mezar.parcacik", true)) w.spawnParticle(Particle.SOUL, k.yer.clone().add(0.5, 0.6, 0.5), 3, 0.3, 0.3, 0.3, 0.01);
        }
        if (degisti) kaydet();
    }

    private void yazilariGuncelle(Kayit k) {
        World w = k.yer.getWorld();
        if (w == null || !w.isChunkLoaded(k.yer.getBlockX() >> 4, k.yer.getBlockZ() >> 4)) return;
        long simdi = System.currentTimeMillis();
        long acik = herkeseAcikMs() - (simdi - k.olusma);
        long silinme = omurMs() - (simdi - k.olusma);
        Component ust = Component.text("☠ " + k.sahipIsim + "'in mezarı", NamedTextColor.GRAY);
        Component alt = Component.text((acik > 0 ? "Herkese açılmasına " + Sure.yaz(acik) : "Herkese açık") + " / Silinmesine " + Sure.yaz(silinme),
                NamedTextColor.DARK_GRAY);
        k.ustYazi = yazi(k.ustYazi, k.yer.clone().add(0.5, 1.05, 0.5), ust);
        k.altYazi = yazi(k.altYazi, k.yer.clone().add(0.5, 0.8, 0.5), alt);
    }

    /** Görünmez, kalıcı olmayan isim standı (Bedrock'ta da görünür); yoksa kurar. */
    private UUID yazi(UUID id, Location l, Component ad) {
        Entity e = id != null ? Bukkit.getEntity(id) : null;
        if (e instanceof ArmorStand st) {
            st.customName(ad);
            return id;
        }
        ArmorStand st = l.getWorld().spawn(l, ArmorStand.class, a -> {
            a.setMarker(true);
            a.setInvisible(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setPersistent(false);
            a.setCustomNameVisible(true);
            a.customName(ad);
        });
        return st.getUniqueId();
    }

    private void kaldir(Kayit k) {
        kaldirKayit(k, true);
    }

    /** Kaydı siler; blokSil: mezar bloğunu da havaya çevir (yüklü değilse bölge yüklenince). */
    private void kaldirKayit(Kayit k, boolean blokSil) {
        mezarlar.remove(k.no);
        konumdan.remove(anahtar(k.yer));
        for (UUID id : new UUID[]{k.ustYazi, k.altYazi}) {
            Entity e = id != null ? Bukkit.getEntity(id) : null;
            if (e != null) e.remove();
        }
        if (blokSil && k.yer.getWorld() != null) {
            World w = k.yer.getWorld();
            if (w.isChunkLoaded(k.yer.getBlockX() >> 4, k.yer.getBlockZ() >> 4)) {
                Block b = k.yer.getBlock();
                if (b.getType() == Material.PLAYER_HEAD) b.setType(Material.AIR);
            } else {
                silinecekBloklar.add(anahtar(k.yer));
            }
        }
        for (HumanEntity h : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (h.getOpenInventory().getTopInventory().getHolder() instanceof MezarEkrani me && me.no == k.no) {
                Bukkit.getScheduler().runTask(plugin, () -> h.closeInventory());
            }
        }
        kaydet();
    }

    @EventHandler
    public void onBolge(ChunkLoadEvent e) {
        if (silinecekBloklar.isEmpty()) return;
        boolean degisti = false;
        for (String s : new ArrayList<>(silinecekBloklar)) {
            String[] p = s.split(";");
            if (p.length != 4 || !p[0].equals(e.getWorld().getName())) continue;
            int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
            if (x >> 4 != e.getChunk().getX() || z >> 4 != e.getChunk().getZ()) continue;
            Block b = e.getWorld().getBlockAt(x, y, z);
            if (b.getType() == Material.PLAYER_HEAD) b.setType(Material.AIR);
            silinecekBloklar.remove(s);
            degisti = true;
        }
        if (degisti) kaydet();
    }

    private static String ozet(Kayit k) {
        Map<String, Integer> say = new LinkedHashMap<>();
        for (ItemStack is : k.esyalar) if (is != null) say.merge(KayitDinleyici.ad(is), is.getAmount(), Integer::sum);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> g : say.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(g.getValue()).append(' ').append(g.getKey());
            if (sb.length() > 300) {
                sb.append(" ...");
                break;
            }
        }
        if (k.xp > 0) sb.append(sb.length() > 0 ? ", " : "").append(k.xp).append(" XP");
        return sb.length() == 0 ? "boş" : sb.toString();
    }

    // ------------------------------------------------------------------
    // /mezar
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
        String alt = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        boolean op = s.hasPermission("bekci.admin");
        long simdi = System.currentTimeMillis();
        if (alt.equals("sil") && op) {
            if (args.length < 2) { plugin.mesaj(s, "Kullanım: /mezar sil <no>", NamedTextColor.YELLOW); return true; }
            Kayit k;
            try {
                k = mezarlar.get(Integer.parseInt(args[1].replace("#", "")));
            } catch (NumberFormatException e) {
                k = null;
            }
            if (k == null) { plugin.mesaj(s, "Böyle bir mezar yok.", NamedTextColor.RED); return true; }
            if (k.yer.getWorld() != null) {
                plugin.db().ekle(Islem.yeni(s instanceof Player p ? p.getUniqueId() : null, s.getName(), k.yer.getWorld().getName(),
                        k.yer.getBlockX(), k.yer.getBlockY(), k.yer.getBlockZ(), Islem.Tur.MEZAR_SILINDI, null, null, "op sildi: " + ozet(k), 0));
            }
            kaldirKayit(k, true);
            plugin.mesaj(s, "#" + k.no + " silindi; içindeki eşyalar da gitti.", NamedTextColor.GREEN);
            return true;
        }
        boolean hepsi = alt.equals("liste") && op;
        List<Kayit> l = new ArrayList<>();
        for (Kayit k : mezarlar.values()) if (hepsi || (s instanceof Player p && k.sahip.equals(p.getUniqueId()))) l.add(k);
        if (l.isEmpty()) {
            plugin.mesaj(s, hepsi ? "Hiç mezar yok." : "Açık mezarın yok.", NamedTextColor.GRAY);
            return true;
        }
        plugin.mesaj(s, hepsi ? "Bütün mezarlar:" : "Mezarların:", NamedTextColor.GOLD);
        for (Kayit k : l) {
            s.sendMessage(Component.text("  #" + k.no + (hepsi ? " " + k.sahipIsim : "") + ": " + k.yer.getBlockX() + " " + k.yer.getBlockY() + " "
                    + k.yer.getBlockZ() + " (" + (k.yer.getWorld() != null ? k.yer.getWorld().getName() : "?") + ") - silinmesine "
                    + Sure.yaz(omurMs() - (simdi - k.olusma)), NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        List<String> sonuc = new ArrayList<>();
        if (!s.hasPermission("bekci.admin")) return sonuc;
        if (args.length == 1) {
            for (String x : List.of("liste", "sil")) if (x.startsWith(args[0].toLowerCase(Locale.ROOT))) sonuc.add(x);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sil")) {
            for (Integer no : mezarlar.keySet()) sonuc.add(String.valueOf(no));
        }
        return sonuc;
    }

    // ------------------------------------------------------------------
    // Kayıt
    // ------------------------------------------------------------------

    public void yukle() {
        if (!dosya.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dosya);
        sonNo = yml.getInt("son-no", 0);
        silinecekBloklar.addAll(yml.getStringList("silinecek-bloklar"));
        ConfigurationSection cs = yml.getConfigurationSection("mezar");
        if (cs == null) return;
        for (String no : cs.getKeys(false)) {
            try {
                String y = "mezar." + no + ".";
                World w = Bukkit.getWorld(yml.getString(y + "dunya", ""));
                if (w == null) continue;
                Kayit k = new Kayit();
                k.no = Integer.parseInt(no);
                k.sahip = UUID.fromString(yml.getString(y + "sahip"));
                k.sahipIsim = yml.getString(y + "sahip-isim", "?");
                String o = yml.getString(y + "olduren", "");
                k.olduren = o == null || o.isEmpty() ? null : UUID.fromString(o);
                k.olusma = yml.getLong(y + "olusma", System.currentTimeMillis());
                k.yer = new Location(w, yml.getInt(y + "x", 0), yml.getInt(y + "y", 0), yml.getInt(y + "z", 0), 0f, 0f);
                k.xp = yml.getInt(y + "xp", 0);
                k.uyarildi = yml.getBoolean(y + "uyarildi", false);
                int adet = yml.getInt(y + "adet", 0);
                for (int i = 0; i < adet; i++) k.esyalar.add(yml.getItemStack(y + "esya." + i));
                mezarlar.put(k.no, k);
                konumdan.put(anahtar(k.yer), k.no);
            } catch (RuntimeException ignored) {
                // bozuk kaydı atla
            }
        }
    }

    private void kaydet() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("son-no", sonNo);
        yml.set("silinecek-bloklar", new ArrayList<>(silinecekBloklar));
        for (Kayit k : mezarlar.values()) {
            if (k.yer.getWorld() == null) continue;
            String y = "mezar." + k.no + ".";
            yml.set(y + "sahip", k.sahip.toString());
            yml.set(y + "sahip-isim", k.sahipIsim);
            yml.set(y + "olduren", k.olduren == null ? "" : k.olduren.toString());
            yml.set(y + "olusma", k.olusma);
            yml.set(y + "dunya", k.yer.getWorld().getName());
            yml.set(y + "x", k.yer.getBlockX());
            yml.set(y + "y", k.yer.getBlockY());
            yml.set(y + "z", k.yer.getBlockZ());
            yml.set(y + "xp", k.xp);
            yml.set(y + "uyarildi", k.uyarildi);
            yml.set(y + "adet", k.esyalar.size());
            for (int i = 0; i < k.esyalar.size(); i++) if (k.esyalar.get(i) != null) yml.set(y + "esya." + i, k.esyalar.get(i));
        }
        try {
            if (!dosya.getParentFile().exists()) dosya.getParentFile().mkdirs();
            yml.save(dosya);
        } catch (IOException e) {
            plugin.getLogger().warning("mezarlar.yml kaydedilemedi: " + e.getMessage());
        }
    }

    /** Eklenti kapanırken: yazıları kaldır (kalıcı değiller), kaydet. */
    public void kapat() {
        for (Kayit k : mezarlar.values()) {
            for (UUID id : new UUID[]{k.ustYazi, k.altYazi}) {
                Entity e = id != null ? Bukkit.getEntity(id) : null;
                if (e != null) e.remove();
            }
        }
        kaydet();
    }
}
