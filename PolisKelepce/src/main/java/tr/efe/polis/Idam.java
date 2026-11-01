package tr.efe.polis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * İdam: başkan karar verir (/idam Ahmet [süre]), polis elle uygular (elinde sıradan kayışla Shift + çite sağ tık).
 * Çitin altında en az 3 blok boşluk olmalı. 5 sn geri sayım, 1 sn kaldırılma, 2 sn sallanma; sonra ölüm.
 * İdam edilen belirlenen süre boyunca sunucuya giremez.
 */
public final class Idam implements Listener, CommandExecutor, TabCompleter {

    private record Onay(long bitis, long sureMs, String onaylayan) {}

    private static final class Sahne {
        UUID polis;
        UUID mahkum;
        String mahkumIsim;
        Location taban;
        float yaw;
        Location cit;
        Chicken ip;
        LeashHitch dugum;
        int tick;
        BukkitTask gorev;
        Onay onay;
    }

    private final PolisPlugin plugin;
    private final File dosya;
    private final Map<UUID, Onay> onaylar = new HashMap<>();
    private final Map<UUID, String> isimler = new HashMap<>();
    /** uuid -> yasağın bittiği an (giriş olayı ayrı iş parçacığında okur). */
    private final Map<UUID, Long> yasaklar = new ConcurrentHashMap<>();
    private final Map<UUID, Sahne> sahneler = new HashMap<>();
    private final Set<UUID> idamEdilen = new HashSet<>();
    /** Oyunda değilken (gıyabında) idam edilenler: yasak bitip girdiklerinde ölürler. uuid -> kararı veren */
    private final Map<UUID, String> giyabi = new HashMap<>();
    /** Gıyabi idamın yapıldığı darağacı (çit): oyuncu dönünce burada ölür. */
    private final Map<UUID, Location> giyabiYer = new HashMap<>();

    public Idam(PolisPlugin plugin) {
        this.plugin = plugin;
        this.dosya = new File(plugin.getDataFolder(), "idam.yml");
    }

    private long ayarSure(String yol, String varsayilan) {
        long s = CezaSistemi.sureOku(plugin.getConfig().getString(yol, varsayilan));
        return s > 0 ? s : CezaSistemi.sureOku(varsayilan);
    }

    // ------------------------------------------------------------------
    // Komut: /idam <oyuncu> [süre] | iptal | affet | liste
    // ------------------------------------------------------------------

    private boolean karar(CommandSender s) {
        return s.hasPermission("poliskelepce.admin") || (s instanceof Player p && plugin.gozalti().baskanMi(p));
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
        if (args.length == 0) {
            yardim(s);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "liste" -> liste(s);
            case "cevrimdisi", "çevrimdışı", "giyabi" -> cevrimdisi(s, args);
            case "test" -> testSahnesi(s, args);
            case "iptal" -> {
                if (!karar(s)) { plugin.mesaj(s, "Bunu sadece belediye başkanı yapabilir.", NamedTextColor.RED); return true; }
                if (args.length < 2) { plugin.mesaj(s, "Kullanım: /idam iptal <oyuncu>", NamedTextColor.YELLOW); return true; }
                UUID id = isimdenBul(args[1], onaylar.keySet());
                if (id == null || onaylar.remove(id) == null) { plugin.mesaj(s, "Bekleyen idam onayı yok: " + args[1], NamedTextColor.YELLOW); return true; }
                kaydet();
                plugin.kayit().log(s.getName() + ", " + isimler.get(id) + " için idam onayını iptal etti");
                plugin.mesaj(s, isimler.get(id) + " için idam onayı iptal edildi.", NamedTextColor.GREEN);
            }
            case "affet" -> {
                if (!s.hasPermission("poliskelepce.admin")) { plugin.mesaj(s, "Yasağı sadece op kaldırabilir.", NamedTextColor.RED); return true; }
                if (args.length < 2) { plugin.mesaj(s, "Kullanım: /idam affet <oyuncu>", NamedTextColor.YELLOW); return true; }
                UUID id = isimdenBul(args[1], yasaklar.keySet());
                if (id == null) id = isimdenBul(args[1], giyabi.keySet());
                boolean yasakVardi = id != null && yasaklar.remove(id) != null;
                boolean giyabiVardi = id != null && giyabi.remove(id) != null;
                if (id != null) giyabiYer.remove(id);
                if (!yasakVardi && !giyabiVardi) { plugin.mesaj(s, "Süren idam yasağı yok: " + args[1], NamedTextColor.YELLOW); return true; }
                kaydet();
                plugin.kayit().log(s.getName() + ", " + isimler.get(id) + " adlı oyuncunun idam yasağını kaldırdı");
                plugin.mesaj(s, isimler.get(id) + " artık sunucuya girebilir.", NamedTextColor.GREEN);
            }
            default -> onayla(s, args);
        }
        return true;
    }

    private UUID isimdenBul(String isim, Set<UUID> icinden) {
        for (UUID id : icinden) if (isimler.getOrDefault(id, "").equalsIgnoreCase(isim)) return id;
        Player p = Bukkit.getPlayerExact(isim);
        return p != null && icinden.contains(p.getUniqueId()) ? p.getUniqueId() : null;
    }

    private void onayla(CommandSender s, String[] args) {
        if (!karar(s)) {
            plugin.mesaj(s, "İdama sadece belediye başkanı karar verebilir.", NamedTextColor.RED);
            return;
        }
        Player h = Secici.tekli(s, args[0]);
        if (h == null) return;
        long sure = IdamKurali.sure(args.length > 1 ? args[1] : null, ayarSure("idam.varsayilan-sure", "30dk"),
                ayarSure("idam.en-fazla-sure", "1g"));
        long gecerlilik = Math.max(1, plugin.getConfig().getLong("idam.onay-dk", 10)) * 60_000L;
        onaylar.put(h.getUniqueId(), new Onay(System.currentTimeMillis() + gecerlilik, sure, s.getName()));
        isimler.put(h.getUniqueId(), h.getName());
        kaydet();
        plugin.kayit().log(s.getName() + ", " + h.getName() + " için idam kararı verdi (yasak " + CezaSistemi.sure(sure) + ")");
        plugin.mesaj(s, h.getName() + " için idam onaylandı (" + gecerlilik / 60_000 + " dk geçerli, sonrasında "
                + CezaSistemi.sure(sure) + " sunucuya giremez).", NamedTextColor.GREEN);
        plugin.mesaj(h, s.getName() + " senin için idam kararı verdi.", NamedTextColor.DARK_RED);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (plugin.gozalti().polisMi(o) && !o.equals(s)) {
                plugin.mesaj(o, h.getName() + " için idam onayı var. Kelepçeliyken elinde kayışla Shift + çite sağ tıkla.", NamedTextColor.GOLD);
            }
        }
    }

    private void liste(CommandSender s) {
        boolean baskan = s instanceof Player bp && plugin.gozalti().baskanMi(bp);
        boolean polis = s instanceof Player pp && plugin.gozalti().polisMi(pp);
        if (!IdamKurali.listeGorebilir(baskan, s.hasPermission("poliskelepce.admin"), polis)) {
            plugin.mesaj(s, "İdam listesini sadece polis, başkan ve op görebilir.", NamedTextColor.RED);
            return;
        }
        long simdi = System.currentTimeMillis();
        onaylar.values().removeIf(o -> o.bitis() < simdi);
        yasaklar.values().removeIf(b -> b < simdi);
        if (onaylar.isEmpty() && yasaklar.isEmpty() && giyabi.isEmpty()) {
            plugin.mesaj(s, "Bekleyen idam onayı ya da süren yasak yok.", NamedTextColor.GRAY);
            return;
        }
        for (Map.Entry<UUID, Onay> g : onaylar.entrySet()) {
            s.sendMessage(Component.text("  Onay: " + isimler.get(g.getKey()) + " (karar: " + g.getValue().onaylayan()
                    + ", " + CezaSistemi.sure(g.getValue().bitis() - simdi) + " geçerli)", NamedTextColor.GOLD));
        }
        for (Map.Entry<UUID, Long> g : yasaklar.entrySet()) {
            s.sendMessage(Component.text("  Yasaklı: " + isimler.get(g.getKey()) + " (kalan " + CezaSistemi.sure(g.getValue() - simdi) + ")",
                    NamedTextColor.RED));
        }
        for (Map.Entry<UUID, String> g : giyabi.entrySet()) {
            s.sendMessage(Component.text("  Gıyabi idam (girince ölecek): " + isimler.get(g.getKey()) + " (karar: " + g.getValue() + ")",
                    NamedTextColor.DARK_RED));
        }
    }

    /** /idam cevrimdisi <oyuncu> [süre]: sadece op. Yasak hemen başlar; oyuncu yasak bitip girdiğinde ölür (eşyaları mezara). */
    private void cevrimdisi(CommandSender s, String[] args) {
        if (!s.hasPermission("poliskelepce.idam-cevrimdisi")) {
            plugin.mesaj(s, "Oyunda olmayanı sadece op idam edebilir.", NamedTextColor.RED);
            return;
        }
        if (args.length < 2) {
            plugin.mesaj(s, "Kullanım: /idam cevrimdisi <oyuncu> [süre]", NamedTextColor.YELLOW);
            return;
        }
        org.bukkit.OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(args[1]);
        String hata = IdamKurali.cevrimdisiHata(o != null, o != null && o.isOnline());
        if (hata != null) {
            plugin.mesaj(s, args[1] + ": " + (hata.equals("oyunda")
                    ? "şu an oyunda. Oyundakini normal idamla (/idam " + args[1] + ") cezalandır."
                    : "bu isimde sunucuya hiç girmiş bir oyuncu yok."), NamedTextColor.RED);
            return;
        }
        // Darağacı: oyundaki op'un baktığı ya da başının üstündeki çit (konsoldan yazılırsa sahnesiz)
        Block cit = null;
        if (s instanceof Player op) {
            cit = daragaciBul(op);
            if (cit == null) {
                plugin.mesaj(s, "Darağacının altında dur ya da çitine bak (çitin altında en az 3 blok boşluk olmalı).", NamedTextColor.RED);
                return;
            }
        }
        UUID id = o.getUniqueId();
        String isim = o.getName() != null ? o.getName() : args[1];
        long sure = IdamKurali.sure(args.length > 2 ? args[2] : null, ayarSure("idam.varsayilan-sure", "30dk"),
                ayarSure("idam.en-fazla-sure", "1g"));
        yasaklar.put(id, System.currentTimeMillis() + sure);
        giyabi.put(id, s.getName());
        if (cit != null) giyabiYer.put(id, cit.getLocation());
        else giyabiYer.remove(id);
        onaylar.remove(id);
        isimler.put(id, isim);
        kaydet();
        plugin.kayit().say(id, isim, Kayitlar.Alan.IDAM);
        plugin.kayit().log(s.getName() + ", " + isim + " adlı oyuncuyu gıyabında idam etti (yasak " + CezaSistemi.sure(sure) + ")");
        plugin.mesaj(s, isim + " gıyabında idam edildi: " + CezaSistemi.sure(sure)
                + " sunucuya giremez, sonra girdiğinde " + (cit != null ? "bu darağacında" : "olduğu yerde") + " ölür (eşyaları mezara). Geri almak için: /idam affet " + isim, NamedTextColor.GREEN);
        if (cit == null) { // konsoldan: sahne yok
            Bukkit.broadcast(Component.text(isim + " gıyabında idam edildi.", NamedTextColor.GRAY));
            return;
        }
        final Block daragaci = cit;
        // Skin (SkinsRestorer) ağ/veritabanı erişebilir: ayrı iş parçacığında al, sahneyi ana iş parçacığında oynat
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String[] skin = SkinKopru.skin(id, isim);
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    mankenSahnesi(daragaci, id, isim, skin);
                } catch (RuntimeException ex) { // manken desteklenmiyorsa vb.: sahnesiz devam
                    plugin.getLogger().warning("Gıyabi idam sahnesi oynatılamadı: " + ex);
                    Bukkit.broadcast(Component.text(isim + " gıyabında idam edildi.", NamedTextColor.GRAY));
                }
            });
        });
    }

    // ------------------------------------------------------------------
    // Darağacı ve manken sahnesi (gıyabi idam)
    // ------------------------------------------------------------------

    private static int altBos(Block cit) {
        int bos = 0;
        while (bos < 6 && cit.getRelative(BlockFace.DOWN, bos + 1).isPassable()) bos++;
        return bos;
    }

    /** Op'un baktığı çit ya da başının üstündeki ilk çit; altında en az 3 blok boşluk varsa. */
    private static Block daragaciBul(Player op) {
        Block bakilan = op.getTargetBlockExact(6);
        if (gecerliCit(bakilan)) return bakilan;
        Block ayak = op.getLocation().getBlock();
        int dy = IdamKurali.ustteCit(y -> ayak.getRelative(BlockFace.UP, y).getType().name().endsWith("_FENCE"),
                y -> ayak.getRelative(BlockFace.UP, y).isPassable(), 6);
        Block ust = dy > 0 ? ayak.getRelative(BlockFace.UP, dy) : null;
        return gecerliCit(ust) ? ust : null;
    }

    private static boolean gecerliCit(Block b) {
        return b != null && b.getType().name().endsWith("_FENCE") && IdamKurali.bosYeterli(altBos(b));
    }

    /** Oyuncunun görünümündeki mankenle idam sahnesi (Ahmet oyunda değilken). */
    /** /idam test [oyuncu]: darağacının altında sahneyi sadece gösterir; kimse ölmez, yasaklanmaz, kayda geçmez. */
    private void testSahnesi(CommandSender s, String[] args) {
        if (!(s instanceof Player op) || !s.hasPermission("poliskelepce.idam-cevrimdisi")) {
            plugin.mesaj(s, "İdam testini sadece op oyun içinde yapabilir.", NamedTextColor.RED);
            return;
        }
        Block cit = daragaciBul(op);
        if (cit == null) {
            plugin.mesaj(s, "Darağacının altında dur ya da çitine bak (çitin altında en az 3 blok boşluk olmalı).", NamedTextColor.RED);
            return;
        }
        String isim = IdamKurali.testHedefi(args, op.getName());
        org.bukkit.OfflinePlayer o = isim.equalsIgnoreCase(op.getName()) ? op : Bukkit.getOfflinePlayerIfCached(isim);
        UUID id = o != null ? o.getUniqueId() : UUID.nameUUIDFromBytes(("OfflinePlayer:" + isim).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String gosterilen = o != null && o.getName() != null ? o.getName() : isim;
        plugin.mesaj(s, "Test: " + gosterilen + " görünümüyle idam sahnesi oynuyor (kimse ölmez, yasak yok).", NamedTextColor.AQUA);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String[] skin = SkinKopru.skin(id, gosterilen);
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    mankenSahnesi(cit, id, gosterilen, skin, true);
                } catch (RuntimeException ex) {
                    plugin.mesaj(s, "Sahne oynatılamadı: " + ex.getMessage(), NamedTextColor.RED);
                }
            });
        });
    }

    private void mankenSahnesi(Block cit, UUID id, String isim, String[] skin) {
        mankenSahnesi(cit, id, isim, skin, false);
    }

    private void mankenSahnesi(Block cit, UUID id, String isim, String[] skin, boolean test) {
        org.bukkit.Chunk bolge = cit.getChunk();
        bolge.addPluginChunkTicket(plugin); // kimse yakında değilse de sahne bitsin
        Location taban = cit.getLocation().add(0.5, -3, 0.5);
        float yaw = taban.getYaw();
        io.papermc.paper.datacomponent.item.ResolvableProfile.Builder profil =
                io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile().uuid(id);
        try {
            profil.name(isim);
        } catch (IllegalArgumentException ignored) {
            // isim profil kuralına uymuyorsa (Türkçe harf vb.) sadece uuid
        }
        if (skin != null) profil.addProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", skin[0], skin[1]));
        io.papermc.paper.datacomponent.item.ResolvableProfile hazir = profil.build();
        Mannequin manken = cit.getWorld().spawn(taban, Mannequin.class, m -> {
            m.setProfile(hazir);
            m.setImmovable(true);
            m.setInvulnerable(true);
            m.setGravity(false);
            m.setSilent(true);
            m.setPersistent(false);
            m.setDescription(null);
            m.customName(Component.text(isim, NamedTextColor.GRAY));
            m.setCustomNameVisible(true);
        });
        Object[] ip = ipKur(cit, taban.clone().add(0, 1.45, 0));
        Chicken tavuk = (Chicken) ip[0];
        LeashHitch dugum = (LeashHitch) ip[1];
        boolean efekt = plugin.getConfig().getBoolean("gorsel.idam-efekt", true);
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, gorev -> {
            int t = tick[0]++;
            boolean son = t >= IdamKurali.SAHNE_SON || !manken.isValid();
            if (son) {
                gorev.cancel();
                if (efekt) cit.getWorld().spawnParticle(Particle.LARGE_SMOKE, manken.getLocation().add(0, 1.4, 0), 30, 0.3, 0.4, 0.3, 0.02);
                manken.remove();
                tavuk.remove();
                if (dugum != null) dugum.remove();
                bolge.removePluginChunkTicket(plugin);
                if (!test) Bukkit.broadcast(Component.text(isim + " gıyabında idam edildi.", NamedTextColor.GRAY));
                return;
            }
            Location yer = taban.clone().add(0, IdamKurali.yukseklik(t), 0);
            yer.setYaw(yaw + IdamKurali.salinim(t));
            yer.setPitch(t >= 100 ? 20f : 0f);
            manken.teleport(yer);
            tavuk.teleport(yer.clone().add(0, 1.45, 0));
            if (dugum == null && t % 2 == 0) ipCiz(cit.getLocation().add(0.5, 0.5, 0.5), yer.clone().add(0, 1.45, 0));
            if (efekt && (t == 0 || t == 10 || t == 20)) cit.getWorld().playSound(cit.getLocation(), Sound.BLOCK_BELL_USE, 2f, 0.8f);
            if (t < 100 && t % 20 == 0) {
                if (efekt) cit.getWorld().playSound(cit.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 2f, 0.6f);
                int kalan = 5 - t / 20;
                for (Player o : cit.getWorld().getPlayers()) {
                    if (o.getLocation().distance(taban) <= 32) {
                        plugin.gorsel().baslik(o, (test ? "TEST - İDAM: " : "İDAM: ") + isim, kalan + (test ? "  (deneme)" : "  (gıyabında)"),
                                NamedTextColor.DARK_RED, null, 1f);
                    }
                }
            }
            if (efekt && t == 120) cit.getWorld().playSound(taban, Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.6f);
        }, 1L, 1L);
    }

    /** Gıyabında idam edilen geri geldi: giriş yapınca (AuthMe) ölür. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGeldi(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!giyabi.containsKey(p.getUniqueId())) return;
        int[] sayac = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, gorev -> {
            if (!p.isOnline() || !giyabi.containsKey(p.getUniqueId())) { // çıktı ya da affedildi: bekleyen ceza durur
                gorev.cancel();
                return;
            }
            if (!girisYapti(p) && ++sayac[0] < 600) return; // AuthMe girişini en fazla 10 dk bekle
            gorev.cancel();
            String karar = giyabi.remove(p.getUniqueId());
            kaydet();
            plugin.gozalti().kelepceCikarSessiz(p);
            Location yer = giyabiYer.remove(p.getUniqueId());
            if (yer != null && yer.getWorld() != null && gecerliCit(yer.getBlock())) {
                p.teleport(yer.clone().add(0.5, -3, 0.5)); // darağacının altında ölür, eşyaları oradaki mezara
            }
            plugin.mesaj(p, "Sen yokken idam edildin (karar: " + karar + "). Ceza şimdi uygulandı.", NamedTextColor.DARK_RED);
            idamEdilen.add(p.getUniqueId());
            if (plugin.getConfig().getBoolean("gorsel.idam-efekt", true)) {
                p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1.4, 0), 30, 0.3, 0.4, 0.3, 0.02);
            }
            p.setHealth(0);
            plugin.kayit().log(p.getName() + " geri geldi, gıyabi idam cezası uygulandı");
        }, 20L, 20L);
    }

    /** AuthMe kuruluysa oyuncu giriş yaptı mı; değilse hep true (derleme bağımlılığı yok). */
    private static boolean girisYapti(Player p) {
        try {
            org.bukkit.plugin.Plugin a = Bukkit.getPluginManager().getPlugin("AuthMe");
            if (a == null || !a.isEnabled()) return true;
            Class<?> api = a.getClass().getClassLoader().loadClass("fr.xephi.authme.api.v3.AuthMeApi");
            Object ornek = api.getMethod("getInstance").invoke(null);
            return (Boolean) api.getMethod("isAuthenticated", Player.class).invoke(ornek, p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    private void yardim(CommandSender s) {
        plugin.mesaj(s, "İdam:", NamedTextColor.DARK_RED);
        s.sendMessage(Component.text("  /idam <oyuncu> [süre] - (başkan) idam kararı. Süre: sonra sunucuya giremeyeceği süre (varsayılan 30dk, en fazla 1g)", NamedTextColor.GRAY));
        s.sendMessage(Component.text("  Polis uygular: mahkum kelepçeli, elinde sıradan kayışla Shift + çite sağ tık", NamedTextColor.GRAY));
        s.sendMessage(Component.text("  Çitin altında en az 3 blok boşluk olmalı", NamedTextColor.GRAY));
        s.sendMessage(Component.text("  /idam iptal <oyuncu>, /idam liste, /idam affet <oyuncu> (op)", NamedTextColor.GRAY));
        if (s.hasPermission("poliskelepce.idam-cevrimdisi")) {
            s.sendMessage(Component.text("  /idam test [oyuncu] - (op) darağacının altında sahneyi dene: kimse ölmez, yasak yok", NamedTextColor.GRAY));
            s.sendMessage(Component.text("  /idam cevrimdisi <oyuncu> [süre] - (op) darağacının altında yaz: oyunda olmayan oyuncu görünümüyle orada asılır, yasak başlar, girince orada ölür", NamedTextColor.GRAY));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        List<String> sonuc = new ArrayList<>();
        if (args.length == 1) {
            List<String> sec = new ArrayList<>(List.of("liste", "iptal", "affet"));
            if (s.hasPermission("poliskelepce.idam-cevrimdisi")) sec.addAll(List.of("cevrimdisi", "test"));
            sec.addAll(Secici.oneriler());
            for (String x : sec) if (x.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) sonuc.add(x);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("iptal") || args[0].equalsIgnoreCase("affet"))) {
            for (String x : isimler.values()) if (x.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) sonuc.add(x);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("cevrimdisi") || args[0].equalsIgnoreCase("test"))) {
            for (String x : plugin.kayit().sicilIsimleri()) {
                if (Bukkit.getPlayerExact(x) == null && x.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) sonuc.add(x);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("cevrimdisi")) {
            for (String x : List.of("30dk", "1sa", "2sa", "12sa", "1g")) if (x.startsWith(args[2].toLowerCase(Locale.ROOT))) sonuc.add(x);
        } else if (args.length == 2) {
            for (String x : List.of("30dk", "1sa", "2sa", "12sa", "1g")) if (x.startsWith(args[1].toLowerCase(Locale.ROOT))) sonuc.add(x);
        }
        return sonuc;
    }

    // ------------------------------------------------------------------
    // Darağacı
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onCit(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Player polis = e.getPlayer();
        Block cit = e.getClickedBlock();
        ItemStack elde = e.getItem();
        if (cit == null || elde == null || elde.getType() != Material.LEAD || !polis.isSneaking()) return;
        if (!cit.getType().name().endsWith("_FENCE")) return;
        if (plugin.items().turu(elde) != null) return; // kelepçe değil, sıradan kayış
        if (!plugin.gozalti().polisMi(polis)) return;
        e.setCancelled(true);
        baslat(polis, cit);
    }

    private void baslat(Player polis, Block cit) {
        long simdi = System.currentTimeMillis();
        Player mahkum = null;
        double enYakin = 4 * 4;
        for (Player o : polis.getWorld().getPlayers()) {
            Onay on = onaylar.get(o.getUniqueId());
            if (on == null || on.bitis() < simdi || !plugin.gozalti().kelepceliMi(o) || sahneler.containsKey(o.getUniqueId())) continue;
            double d = o.getLocation().distanceSquared(polis.getLocation());
            if (d <= enYakin) {
                enYakin = d;
                mahkum = o;
            }
        }
        if (mahkum == null) {
            plugin.mesaj(polis, "Yanında (4 blok) idam onayı olan kelepçeli bir mahkum yok.", NamedTextColor.YELLOW);
            return;
        }
        int bos = 0;
        while (bos < 6 && cit.getRelative(BlockFace.DOWN, bos + 1).isPassable()) bos++;
        if (!IdamKurali.bosYeterli(bos)) {
            plugin.mesaj(polis, "Darağacı için çitin altında en az 3 blok boşluk lazım.", NamedTextColor.RED);
            return;
        }

        Sahne sh = new Sahne();
        sh.polis = polis.getUniqueId();
        sh.mahkum = mahkum.getUniqueId();
        sh.mahkumIsim = mahkum.getName();
        sh.cit = cit.getLocation();
        sh.yaw = mahkum.getLocation().getYaw();
        sh.taban = cit.getLocation().add(0.5, -3, 0.5);
        sh.taban.setYaw(sh.yaw);
        sh.onay = onaylar.get(mahkum.getUniqueId());
        plugin.gozalti().sabitle(mahkum, sh.taban);
        mahkum.teleport(sh.taban);

        // Çitten boyna ip: görünmez yaratık + kayış düğümü
        Object[] ip = ipKur(cit, sh.taban.clone().add(0, 1.45, 0));
        sh.ip = (Chicken) ip[0];
        sh.dugum = (LeashHitch) ip[1];
        if (polis.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            ItemStack el = polis.getInventory().getItemInMainHand();
            el.setAmount(el.getAmount() - 1);
            polis.getInventory().setItemInMainHand(el.getAmount() > 0 ? el : null);
        }
        sahneler.put(sh.mahkum, sh);
        plugin.mesaj(mahkum, "İdam ediliyorsun.", NamedTextColor.DARK_RED);
        sh.gorev = Bukkit.getScheduler().runTaskTimer(plugin, () -> adim(sh), 1L, 1L);
    }

    private void adim(Sahne sh) {
        Player polis = Bukkit.getPlayer(sh.polis);
        Player mahkum = Bukkit.getPlayer(sh.mahkum);
        if (mahkum == null) return; // çıkış olayı halleder
        if (polis == null || !polis.getWorld().equals(mahkum.getWorld()) || polis.getLocation().distance(mahkum.getLocation()) > 8) {
            iptal(sh, "Polis uzaklaştı, idam yarıda kaldı (onay geçerli).");
            return;
        }
        if (!IdamKurali.devamEtmeli(onaylar.containsKey(sh.mahkum), plugin.gozalti().kelepceliMi(mahkum), mahkum.isDead())) {
            iptal(sh, "İdam durduruldu (onay iptal edildi, kelepçe açıldı ya da mahkum öldü).");
            return;
        }
        int t = sh.tick++;
        Location yer = sh.taban.clone().add(0, IdamKurali.yukseklik(t), 0);
        yer.setYaw(sh.yaw + IdamKurali.salinim(t));
        yer.setPitch(t >= 100 ? 20f : 0f);
        mahkum.teleport(yer);
        if (sh.ip != null) sh.ip.teleport(yer.clone().add(0, 1.45, 0));
        if (sh.dugum == null && t % 2 == 0) ipCiz(sh.cit.clone().add(0.5, 0.5, 0.5), yer.clone().add(0, 1.45, 0));

        boolean efekt = plugin.getConfig().getBoolean("gorsel.idam-efekt", true);
        if (efekt && (t == 0 || t == 10 || t == 20)) sh.cit.getWorld().playSound(sh.cit, Sound.BLOCK_BELL_USE, 2f, 0.8f);
        if (t < 100 && t % 20 == 0) {
            if (efekt) sh.cit.getWorld().playSound(sh.cit, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 2f, 0.6f);
            int kalan = 5 - t / 20;
            for (Player o : mahkum.getWorld().getPlayers()) {
                if (o.getLocation().distance(mahkum.getLocation()) <= 32) {
                    plugin.gorsel().baslik(o, "İDAM: " + sh.mahkumIsim, String.valueOf(kalan), NamedTextColor.DARK_RED, null, 1f);
                }
            }
        }
        if (efekt && t == 120) mahkum.getWorld().playSound(mahkum.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.6f);
        if (t >= IdamKurali.SAHNE_SON) bitir(sh, mahkum, polis.getName(), true);
    }

    /** Çitten boyna ip: {görünmez tavuk, kayış düğümü (kurulamazsa null: ip parçacıkla çizilir)} */
    private static Object[] ipKur(Block cit, Location boyun) {
        Chicken tavuk = cit.getWorld().spawn(boyun, Chicken.class, c -> {
            c.setInvisible(true);
            c.setSilent(true);
            c.setAI(false);
            c.setInvulnerable(true);
            c.setGravity(false);
            c.setPersistent(false);
            c.setBaby();
        });
        LeashHitch dugum;
        try {
            dugum = cit.getWorld().spawn(cit.getLocation(), LeashHitch.class);
            tavuk.setLeashHolder(dugum);
        } catch (RuntimeException ex) {
            dugum = null;
        }
        return new Object[]{tavuk, dugum};
    }

    private static void ipCiz(Location a, Location b) {
        double mesafe = a.distance(b);
        org.bukkit.util.Vector adim = b.toVector().subtract(a.toVector()).normalize().multiply(0.25);
        Location n = a.clone();
        Particle.DustOptions renk = new Particle.DustOptions(org.bukkit.Color.fromRGB(0x8B5A2B), 0.6f);
        for (double d = 0; d < mesafe; d += 0.25) {
            a.getWorld().spawnParticle(Particle.DUST, n, 1, 0, 0, 0, 0, renk);
            n.add(adim);
        }
    }

    private void temizle(Sahne sh) {
        if (sh.gorev != null) sh.gorev.cancel();
        if (sh.ip != null) {
            sh.ip.setLeashHolder(null); // kayış yere düşmesin
            sh.ip.remove();
        }
        if (sh.dugum != null) sh.dugum.remove();
        sahneler.remove(sh.mahkum);
    }

    private void iptal(Sahne sh, String neden) {
        temizle(sh);
        Player mahkum = Bukkit.getPlayer(sh.mahkum);
        if (mahkum != null && !mahkum.isDead()) {
            mahkum.teleport(sh.taban);
            if (plugin.gozalti().kelepceliMi(mahkum)) plugin.gozalti().sabitle(mahkum, sh.taban);
        }
        Player polis = Bukkit.getPlayer(sh.polis);
        if (polis != null && !neden.isEmpty()) plugin.mesaj(polis, neden, NamedTextColor.YELLOW);
    }

    /** İdamı tamamlar: ölüm, duyuru, kayıt, yasak; atma isteğe bağlı (çıkış sırasında atılmaz). */
    private void bitir(Sahne sh, Player mahkum, String polisIsim, boolean at) {
        temizle(sh);
        long sure = sh.onay != null ? sh.onay.sureMs() : ayarSure("idam.varsayilan-sure", "30dk");
        String onaylayan = sh.onay != null ? sh.onay.onaylayan() : "?";
        plugin.gozalti().kelepceCikarSessiz(mahkum);
        idamEdilen.add(mahkum.getUniqueId());
        if (plugin.getConfig().getBoolean("gorsel.idam-efekt", true)) mahkum.getWorld().spawnParticle(Particle.LARGE_SMOKE, mahkum.getLocation().add(0, 1.4, 0), 30, 0.3, 0.4, 0.3, 0.02);
        mahkum.setHealth(0);
        onaylar.remove(mahkum.getUniqueId());
        yasaklar.put(mahkum.getUniqueId(), System.currentTimeMillis() + sure);
        isimler.put(mahkum.getUniqueId(), mahkum.getName());
        kaydet();
        plugin.kayit().say(mahkum.getUniqueId(), mahkum.getName(), Kayitlar.Alan.IDAM);
        plugin.kayit().log(polisIsim + ", " + mahkum.getName() + " adlı oyuncuyu idam etti (karar: " + onaylayan + ", yasak "
                + CezaSistemi.sure(sure) + ")");
        Bukkit.broadcast(Component.text(mahkum.getName() + " idam edildi.", NamedTextColor.GRAY));
        if (at) {
            String mesaj = "İdam edildin. " + CezaSistemi.sure(sure) + " sonra tekrar girebilirsin.";
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (mahkum.isOnline()) mahkum.kick(Component.text(mesaj, NamedTextColor.RED));
            }, 40L);
        }
    }

    @EventHandler
    public void onOlum(PlayerDeathEvent e) {
        if (idamEdilen.remove(e.getEntity().getUniqueId())) {
            e.deathMessage(Component.text(e.getEntity().getName() + " idam edildi", NamedTextColor.GRAY));
        }
    }

    // LOWEST: kelepçe kaydı Gozalti tarafından silinmeden önce
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCik(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Sahne sh = sahneler.get(id);
        if (sh != null) { // geri sayımda kaçan mahkum: idam yine sayılır
            bitir(sh, e.getPlayer(), Bukkit.getPlayer(sh.polis) != null ? Bukkit.getPlayer(sh.polis).getName() : "?", false);
            return;
        }
        for (Sahne s : new ArrayList<>(sahneler.values())) {
            if (s.polis.equals(id)) iptal(s, "");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGiris(AsyncPlayerPreLoginEvent e) {
        Long bitis = yasaklar.get(e.getUniqueId());
        if (bitis == null) return;
        long kalan = bitis - System.currentTimeMillis();
        if (kalan <= 0) {
            yasaklar.remove(e.getUniqueId());
            return;
        }
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                Component.text("İdam edildin. Kalan: " + CezaSistemi.sure(kalan), NamedTextColor.RED));
    }

    public void kapat() {
        for (Sahne sh : new ArrayList<>(sahneler.values())) iptal(sh, "Sunucu kapanıyor, idam yarıda kaldı.");
        kaydet();
    }

    // ------------------------------------------------------------------
    // Kayıt
    // ------------------------------------------------------------------

    public void yukle() {
        if (!dosya.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dosya);
        ConfigurationSection ys = yml.getConfigurationSection("yasak");
        if (ys != null) for (String k : ys.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                yasaklar.put(id, yml.getLong("yasak." + k + ".bitis", 0));
                isimler.put(id, yml.getString("yasak." + k + ".isim", "?"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ConfigurationSection gs = yml.getConfigurationSection("giyabi");
        if (gs != null) for (String k : gs.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                giyabi.put(id, yml.getString("giyabi." + k + ".karar", "?"));
                org.bukkit.World gw = Bukkit.getWorld(yml.getString("giyabi." + k + ".dunya", ""));
                if (gw != null) giyabiYer.put(id, new Location(gw, yml.getInt("giyabi." + k + ".x", 0),
                        yml.getInt("giyabi." + k + ".y", 0), yml.getInt("giyabi." + k + ".z", 0), 0f, 0f));
                isimler.put(id, yml.getString("giyabi." + k + ".isim", "?"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ConfigurationSection os = yml.getConfigurationSection("onay");
        if (os != null) for (String k : os.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                String y = "onay." + k + ".";
                onaylar.put(id, new Onay(yml.getLong(y + "bitis", 0), yml.getLong(y + "sure", 1_800_000L), yml.getString(y + "onaylayan", "?")));
                isimler.put(id, yml.getString(y + "isim", "?"));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void kaydet() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> g : yasaklar.entrySet()) {
            yml.set("yasak." + g.getKey() + ".bitis", g.getValue());
            yml.set("yasak." + g.getKey() + ".isim", isimler.getOrDefault(g.getKey(), "?"));
        }
        for (Map.Entry<UUID, String> g : giyabi.entrySet()) {
            yml.set("giyabi." + g.getKey() + ".karar", g.getValue());
            Location gy = giyabiYer.get(g.getKey());
            if (gy != null && gy.getWorld() != null) {
                yml.set("giyabi." + g.getKey() + ".dunya", gy.getWorld().getName());
                yml.set("giyabi." + g.getKey() + ".x", gy.getBlockX());
                yml.set("giyabi." + g.getKey() + ".y", gy.getBlockY());
                yml.set("giyabi." + g.getKey() + ".z", gy.getBlockZ());
            }
            yml.set("giyabi." + g.getKey() + ".isim", isimler.getOrDefault(g.getKey(), "?"));
        }
        for (Map.Entry<UUID, Onay> g : onaylar.entrySet()) {
            String y = "onay." + g.getKey() + ".";
            yml.set(y + "bitis", g.getValue().bitis());
            yml.set(y + "sure", g.getValue().sureMs());
            yml.set(y + "onaylayan", g.getValue().onaylayan());
            yml.set(y + "isim", isimler.getOrDefault(g.getKey(), "?"));
        }
        try {
            if (!dosya.getParentFile().exists()) dosya.getParentFile().mkdirs();
            yml.save(dosya);
        } catch (IOException e) {
            plugin.getLogger().warning("idam.yml kaydedilemedi: " + e.getMessage());
        }
    }
}
