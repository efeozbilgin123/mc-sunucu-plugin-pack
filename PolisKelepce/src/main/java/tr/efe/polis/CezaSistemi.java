package tr.efe.polis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ceza / vergi.
 * - Polis (ya da op) /ceza ile bir oyuncuya eşya cinsinden ceza yazar.
 * - Oyuncu /ode ile öder; eşyalar cezayı yazanın envanterine gider (oyunda değilse girince teslim edilir).
 * - Kelepçeli oyuncudan polis /ceza tahsil ile zorla tahsil edebilir.
 * - config: ceza.aninda-tahsil true ise /ceza yazıldığı anda, oyuncunun üstünde varsa direkt alınır.
 */
public final class CezaSistemi implements Listener, CommandExecutor, TabCompleter {

    private record Kayit(int id, UUID hedef, String hedefIsim, UUID veren, String verenIsim,
                         Material esya, int miktar, String sebep, long zaman, long vadeMs, boolean vergi) {}

    private static final Map<String, Material> TURKCE = new LinkedHashMap<>();

    static {
        TURKCE.put("demir", Material.IRON_INGOT);
        TURKCE.put("altin", Material.GOLD_INGOT);
        TURKCE.put("elmas", Material.DIAMOND);
        TURKCE.put("zumrut", Material.EMERALD);
        TURKCE.put("bakir", Material.COPPER_INGOT);
        TURKCE.put("komur", Material.COAL);
        TURKCE.put("netherit", Material.NETHERITE_INGOT);
        TURKCE.put("ekmek", Material.BREAD);
        TURKCE.put("bugday", Material.WHEAT);
        TURKCE.put("kiziltas", Material.REDSTONE);
        TURKCE.put("lapis", Material.LAPIS_LAZULI);
    }

    private final PolisPlugin plugin;
    private final Gozalti gozalti;
    private final File dosya;

    private final List<Kayit> cezalar = new ArrayList<>();
    /** Ceza yazan oyunda değilken ödenenler: veren -> ödenen gerçek eşyalar (isim/büyü korunur). */
    private final Map<UUID, List<ItemStack>> teslimat = new HashMap<>();
    private int sonId = 0;

    public CezaSistemi(PolisPlugin plugin, Gozalti gozalti) {
        this.plugin = plugin;
        this.gozalti = gozalti;
        this.dosya = new File(plugin.getDataFolder(), "cezalar.yml");
    }

    public boolean borcluMu(UUID oyuncu) {
        for (Kayit k : cezalar) if (k.hedef().equals(oyuncu)) return true;
        return false;
    }

    /** Bir oyuncunun borç özeti (bossbar, karakol panosu, sicil için). */
    public record BorcOzet(UUID id, String isim, int adet, long enEski, long enYakinSon, long enYakinToplam,
                           boolean gec, long enCokGecikme, boolean vergi) {}

    public static String turEtiketi(boolean vergi) {
        return vergi ? "vergi" : "ceza";
    }

    /** cezalar.yml'deki "tur" alanı; eski kayıtlarda yoktur, ceza sayılır. */
    public static boolean turOku(String deger) {
        return "vergi".equals(deger);
    }

    /** Tüm borçlular, en uzun süredir ödemeyen en başta. */
    public List<BorcOzet> borcOzetleri() {
        // Ceza ve vergi ayrı özetlenir (aynı oyuncu iki kez görünebilir)
        Map<String, List<Kayit>> grup = new LinkedHashMap<>();
        for (Kayit k : cezalar) grup.computeIfAbsent(k.hedef() + (k.vergi() ? "/v" : "/c"), x -> new ArrayList<>()).add(k);
        long simdi = System.currentTimeMillis();
        List<BorcOzet> sonuc = new ArrayList<>();
        for (Map.Entry<String, List<Kayit>> g : grup.entrySet()) {
            List<Kayit> l = g.getValue();
            long enYakin = Long.MAX_VALUE, toplam = 0, gecikme = 0;
            boolean gec = false;
            for (Kayit k : l) {
                long son = sonOdeme(k);
                if (son < enYakin) {
                    enYakin = son;
                    toplam = son - k.zaman();
                }
                if (simdi > son) {
                    gec = true;
                    gecikme = Math.max(gecikme, simdi - son);
                }
            }
            sonuc.add(new BorcOzet(l.get(0).hedef(), l.get(l.size() - 1).hedefIsim(), l.size(), enEski(l), enYakin, toplam, gec, gecikme, l.get(0).vergi()));
        }
        sonuc.sort((a, b) -> Long.compare(a.enEski(), b.enEski()));
        return sonuc;
    }

    /** Oyuncunun ceza özeti; cezası yoksa vergi özeti; hiç borcu yoksa null. */
    public BorcOzet borcOzet(UUID oyuncu) {
        BorcOzet vergi = null;
        for (BorcOzet b : borcOzetleri()) {
            if (!b.id().equals(oyuncu)) continue;
            if (!b.vergi()) return b;
            vergi = b;
        }
        return vergi;
    }

    /** Vergide sicile işlenmez. */
    private void sicil(Kayit k, UUID id, String isim, Kayitlar.Alan alan) {
        if (!k.vergi()) kayit().say(id, isim, alan);
    }

    private Kayitlar kayit() {
        return plugin.kayit();
    }

    private boolean yetkili(CommandSender s) {
        return s.hasPermission("poliskelepce.admin") || (s instanceof Player p && gozalti.polisMi(p));
    }

    // ------------------------------------------------------------------
    // Komutlar
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("ode")) {
            odeKomut(s, args);
            return true;
        }
        if (command.getName().equalsIgnoreCase("vergi")) {
            if (args.length == 0) vergiYardim(s);
            else yaz(s, args, true);
            return true;
        }
        if (args.length == 0) {
            yardim(s);
            return true;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "liste" -> liste(s);
            case "borclular", "borçlular" -> borclular(s);
            case "iptal" -> iptal(s, args);
            case "tahsil" -> tahsil(s, args);
            case "yardim", "yardım" -> yardim(s);
            default -> yaz(s, args, false);
        }
        return true;
    }

    private void yardim(CommandSender s) {
        plugin.mesaj(s, "Ceza / vergi:", NamedTextColor.AQUA);
        satir(s, "/ceza <oyuncu> <miktar> <eşya> [süre] [sebep] - (polis) ceza yaz. Örn: /ceza Ahmet 5 demir vergi");
        satir(s, "  Toplu vergi: /ceza @a 5 demir vergi (sen hariç herkese)");
        satir(s, "  Ödeme süresi: /ceza Ahmet 5 demir 2g vergi (30dk, 12sa, 2g, 1g12sa). Yazmazsan varsayılan süre");
        satir(s, "Eşya: oyundaki her eşya olur. Türkçe kısaltmalar: demir, altin, elmas, zumrut, bakir, komur, netherit...");
        satir(s, "  ya da İngilizce adı (Tab ile tamamlanır): oak_log, cooked_beef ...");
        satir(s, "  ya da \"el\": elinde tuttuğun eşya. Örn: /ceza Ahmet 3 el vergi");
        satir(s, "/ode [no] - cezanı öde (eşyalar cezayı yazana gider; önce düz, sonra isimli/büyülü olanlar)");
        satir(s, "/ceza liste - cezaları göster (ne kadar önce yazıldığıyla)");
        satir(s, "/ceza borclular - (polis) borçlular, en uzun süredir ödemeyen üstte");
        satir(s, "/ceza iptal <no> - (yazan ya da op) cezayı iptal et");
        satir(s, "/ceza tahsil <oyuncu> - (polis) kelepçeli oyuncudan zorla tahsil et");
    }

    private void vergiYardim(CommandSender s) {
        plugin.mesaj(s, "Vergi (başkan ve op):", NamedTextColor.GOLD);
        satir(s, "/vergi <oyuncu|@a> <miktar> <eşya> [süre] [sebep] - örn: /vergi @a 5 demir 7g haftalık");
        satir(s, "Ödeme /ode ile. Vergi sicile işlenmez, [ARANIYOR] yapmaz, makbuz verilmez.");
    }

    private void yaz(CommandSender s, String[] args, boolean vergi) {
        if (vergi ? !(s.hasPermission("poliskelepce.admin") || (s instanceof Player bp && gozalti.baskanMi(bp))) : !yetkili(s)) {
            plugin.mesaj(s, vergi ? "Vergiyi sadece belediye başkanı yazabilir." : "Ceza yazmak için polis olmalısın.", NamedTextColor.RED);
            return;
        }
        if (args.length < 3) {
            plugin.mesaj(s, "Kullanım: /" + turEtiketi(vergi) + " <oyuncu> <miktar> <eşya> [süre] [sebep]", NamedTextColor.YELLOW);
            return;
        }
        int miktar;
        try {
            miktar = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            plugin.mesaj(s, "Miktar sayı olmalı.", NamedTextColor.RED);
            return;
        }
        long max = plugin.getConfig().getLong("ceza.max-miktar", 0);
        if (miktar < 1 || (max > 0 && miktar > max)) {
            plugin.mesaj(s, max > 0 ? "Miktar 1 ile " + max + " arasında olmalı." : "Miktar en az 1 olmalı.", NamedTextColor.RED);
            return;
        }
        Material esya;
        if (args[2].equalsIgnoreCase("el")) {
            if (!(s instanceof Player yazan)) {
                plugin.mesaj(s, "\"el\" sadece oyun içinde kullanılır.", NamedTextColor.RED);
                return;
            }
            esya = yazan.getInventory().getItemInMainHand().getType();
            if (esya == Material.AIR) {
                plugin.mesaj(s, "Elinde bir eşya tutmalısın.", NamedTextColor.RED);
                return;
            }
        } else {
            esya = esyaBul(args[2]);
        }
        if (esya == null) {
            plugin.mesaj(s, "Eşya anlaşılmadı: " + args[2] + ". /ceza yazarak listeye bak.", NamedTextColor.RED);
            return;
        }
        // İsteğe bağlı ödeme süresi: /ceza Ahmet 5 demir 2g vergi  (yazılmazsa varsayılan süre)
        int sebepBasi = 3;
        long vade = -1;
        if (args.length > 3) {
            long okunan = sureOku(args[3]);
            if (okunan > 0) {
                vade = okunan;
                sebepBasi = 4;
            }
        }
        String sebep = args.length > sebepBasi
                ? String.join(" ", java.util.Arrays.copyOfRange(args, sebepBasi, args.length)) : "belirtilmedi";

        // @a ile toplu vergi: komutu yazan hariç herkese
        for (Player hedef : Secici.coklu(s, args[0], true)) {
            cezaYaz(s, hedef, miktar, esya, sebep, vade, vergi);
        }
    }

    private void cezaYaz(CommandSender s, Player hedef, int miktar, Material esya, String sebep, long vade, boolean vergi) {
        UUID verenId = s instanceof Player vp ? vp.getUniqueId() : null;
        String verenIsim = s instanceof Player vp2 ? vp2.getName() : "Sunucu";
        Kayit k = new Kayit(++sonId, hedef.getUniqueId(), hedef.getName(), verenId, verenIsim, esya, miktar, sebep, System.currentTimeMillis(), vade, vergi);

        String tanim = miktar + " " + esyaAdi(esya);
        String tur = turEtiketi(vergi);
        sicil(k, hedef.getUniqueId(), hedef.getName(), Kayitlar.Alan.CEZA);
        if (plugin.getConfig().getBoolean("ceza.aninda-tahsil", false) && say(hedef.getInventory(), esya) >= miktar) {
            teslimEt(k, al(hedef.getInventory(), esya, miktar));
            sicil(k, hedef.getUniqueId(), hedef.getName(), Kayitlar.Alan.ODENEN);
            kayit().log("#" + k.id() + " " + verenIsim + " -> " + hedef.getName() + ": " + tanim + " " + tur + " yazdı ve anında tahsil etti (" + sebep + ")");
            makbuzVer(hedef, k, "ÖDENDİ");
            plugin.mesaj(s, hedef.getName() + " adlı oyuncudan " + tanim + " tahsil edildi (" + sebep + ").", NamedTextColor.GREEN);
            plugin.mesaj(hedef, verenIsim + " senden " + tanim + " tahsil etti. Sebep: " + sebep, NamedTextColor.RED);
            kaydet();
            return;
        }

        cezalar.add(k);
        kaydet();
        gozalti.sekmeIsmi(hedef);
        makbuzVer(hedef, k, null);
        kayit().log("#" + k.id() + " " + verenIsim + " -> " + hedef.getName() + ": " + tanim + " " + tur + " yazdı (" + sebep + ", "
                + (sonOdeme(k) == Long.MAX_VALUE ? "süresiz" : "süre " + sure(sonOdeme(k) - k.zaman())) + ")");
        String sureYazi = sonOdeme(k) == Long.MAX_VALUE ? "süresiz" : "ödeme süresi " + sure(sonOdeme(k) - k.zaman());
        plugin.mesaj(s, "#" + k.id() + " " + hedef.getName() + " adlı oyuncuya " + tanim + " " + tur + " yazıldı (" + sebep + ", " + sureYazi + ").", NamedTextColor.GREEN);
        plugin.mesaj(hedef, verenIsim + " sana " + tanim + " " + tur + " yazdı. Sebep: " + sebep + ". Ödeme süresi: "
                + (sonOdeme(k) == Long.MAX_VALUE ? "süresiz" : sure(sonOdeme(k) - k.zaman())) + ". Ödemek için: /ode", NamedTextColor.RED);
    }

    private void liste(CommandSender s) {
        boolean hepsi = yetkili(s);
        List<Kayit> gosterilecek = new ArrayList<>();
        for (Kayit k : cezalar) {
            if (hepsi || (s instanceof Player p && k.hedef().equals(p.getUniqueId()))) gosterilecek.add(k);
        }
        if (gosterilecek.isEmpty()) {
            plugin.mesaj(s, hepsi ? "Ödenmemiş ceza yok." : "Ödenmemiş cezan yok.", NamedTextColor.GRAY);
            return;
        }
        plugin.mesaj(s, hepsi ? "Ödenmemiş cezalar:" : "Cezaların:", NamedTextColor.AQUA);
        for (Kayit k : gosterilecek) {
            boolean gec = gecikmis(k);
            Component satir = Component.text("  #" + k.id() + " " + turEtiketi(k.vergi()) + " " + k.hedefIsim() + ": " + k.miktar() + " " + esyaAdi(k.esya())
                    + " (" + k.sebep() + ") - yazan: " + k.verenIsim() + " - " + sure(System.currentTimeMillis() - k.zaman())
                    + " önce - " + vadeDurumu(k), gec ? NamedTextColor.RED : NamedTextColor.GRAY);
            s.sendMessage(satir);
        }
    }

    /** Borçlular: en uzun süredir ödemeyen en üstte. Polis buna bakıp tutuklar. */
    private void borclular(CommandSender s) {
        if (!yetkili(s)) {
            plugin.mesaj(s, "Borçlu listesini sadece polis ve başkan görebilir. Kendi cezaların: /ceza liste", NamedTextColor.RED);
            return;
        }
        Map<UUID, List<Kayit>> grup = new LinkedHashMap<>();
        for (Kayit k : cezalar) grup.computeIfAbsent(k.hedef(), x -> new ArrayList<>()).add(k);
        if (grup.isEmpty()) {
            plugin.mesaj(s, "Borçlu yok.", NamedTextColor.GRAY);
            return;
        }
        List<Map.Entry<UUID, List<Kayit>>> sirali = new ArrayList<>(grup.entrySet());
        sirali.sort((a, b) -> Long.compare(enEski(a.getValue()), enEski(b.getValue())));
        long simdi = System.currentTimeMillis();
        plugin.mesaj(s, "Borçlular (en uzun süredir ödemeyen üstte):", NamedTextColor.AQUA);
        for (Map.Entry<UUID, List<Kayit>> g : sirali) {
            List<Kayit> l = g.getValue();
            long yas = simdi - enEski(l);
            boolean gec = false, vergiGec = false;
            int cezaAdet = 0, vergiAdet = 0;
            for (Kayit k : l) {
                if (k.vergi()) {
                    vergiAdet++;
                    vergiGec |= gecikmis(k);
                } else {
                    cezaAdet++;
                    gec |= gecikmis(k);
                }
            }
            Player p = Bukkit.getPlayer(g.getKey());
            String durum = p != null ? "oyunda" : "çevrimdışı";
            String adet = (cezaAdet > 0 ? cezaAdet + " ceza" : "") + (cezaAdet > 0 && vergiAdet > 0 ? " + " : "")
                    + (vergiAdet > 0 ? vergiAdet + " vergi" : "");
            Component satir = Component.text("  " + l.get(0).hedefIsim() + ": " + adet + ", en eskisi "
                    + sure(yas) + " önce (" + durum + ")", gec ? NamedTextColor.RED : NamedTextColor.GRAY);
            if (gec) satir = satir.append(Component.text("  GECİKMİŞ - tutuklanabilir", NamedTextColor.DARK_RED));
            else if (vergiGec) satir = satir.append(Component.text("  vergisi gecikmiş", NamedTextColor.GOLD));
            else {
                long yakin = Long.MAX_VALUE;
                for (Kayit k : l) yakin = Math.min(yakin, sonOdeme(k));
                if (yakin != Long.MAX_VALUE) satir = satir.append(Component.text("  son ödemeye " + sure(yakin - simdi), NamedTextColor.YELLOW));
            }
            s.sendMessage(satir);
        }
        long esik = plugin.getConfig().getLong("ceza.gecikme-saat", 72);
        satir(s, "Varsayılan ödeme süresi: " + (esik > 0 ? esik + " saat" : "süresiz") + " (config: ceza.gecikme-saat). Cezaya özel süre: /ceza Ahmet 5 demir 2g sebep");
    }

    private static long enEski(List<Kayit> l) {
        long m = Long.MAX_VALUE;
        for (Kayit k : l) m = Math.min(m, k.zaman());
        return m;
    }

    /** Oyuncunun en eski ödenmemiş cezasının ne kadar zamandır beklediği (ms); borcu yoksa -1. */
    public long enEskiBorcYasi(UUID oyuncu) {
        long m = Long.MAX_VALUE;
        for (Kayit k : cezalar) if (k.hedef().equals(oyuncu)) m = Math.min(m, k.zaman());
        return m == Long.MAX_VALUE ? -1 : System.currentTimeMillis() - m;
    }

    /** Cezanın son ödeme anı (ms). Özel süre yoksa varsayılan; varsayılan 0 ise süresiz (Long.MAX_VALUE). */
    private long sonOdeme(Kayit k) {
        if (k.vadeMs() > 0) return k.zaman() + k.vadeMs();
        long esik = plugin.getConfig().getLong("ceza.gecikme-saat", 72);
        return esik > 0 ? k.zaman() + esik * 3_600_000L : Long.MAX_VALUE;
    }

    private boolean gecikmis(Kayit k) {
        return System.currentTimeMillis() > sonOdeme(k);
    }

    private String vadeDurumu(Kayit k) {
        long son = sonOdeme(k);
        if (son == Long.MAX_VALUE) return "süresiz";
        long kalan = son - System.currentTimeMillis();
        return kalan >= 0 ? "son ödemeye " + sure(kalan) : sure(-kalan) + " GECİKTİ";
    }

    /** Oyuncunun süresi geçmiş cezası var mı? (Tab'daki "!" için) */
    public boolean gecikmisVar(UUID oyuncu) {
        for (Kayit k : cezalar) if (k.hedef().equals(oyuncu) && !k.vergi() && gecikmis(k)) return true;
        return false;
    }

    /** "30dk", "5sa", "2g", "1g12sa", "90m", "3d" gibi süreleri okur; anlaşılmazsa -1. */
    static long sureOku(String yazi) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(?:(\\d+)(?:g|gün|gun|d))?(?:(\\d+)(?:sa|saat|s|h))?(?:(\\d+)(?:dk|dakika|m))?$")
                .matcher(yazi.toLowerCase(java.util.Locale.ROOT));
        if (!m.matches() || (m.group(1) == null && m.group(2) == null && m.group(3) == null)) return -1;
        long dk = 0;
        if (m.group(1) != null) dk += Long.parseLong(m.group(1)) * 1440;
        if (m.group(2) != null) dk += Long.parseLong(m.group(2)) * 60;
        if (m.group(3) != null) dk += Long.parseLong(m.group(3));
        return dk > 0 ? dk * 60_000L : -1;
    }

    /** "2 gün 3 saat", "5 saat 10 dk", "12 dk" */
    static String sure(long ms) {
        long dk = Math.max(0, ms) / 60_000L;
        long gun = dk / 1440, saat = (dk % 1440) / 60, dakika = dk % 60;
        if (gun > 0) return gun + " gün " + saat + " saat";
        if (saat > 0) return saat + " saat " + dakika + " dk";
        return dakika + " dk";
    }

    /** Tab listesi için kısa: "3g", "5sa", "12dk" */
    /**
     * Tab listesinde ismin yanına: son ödemeye kalan süre (ya da gecikti). kalanMs = -1: süresiz.
     * Yazılalı kaç dakika olduğu gösterilmez; 1 dakikadan az kaldıysa "1dk".
     */
    public static String sekmeEki(boolean vergi, boolean gec, long kalanMs) {
        if (gec) return vergi ? " (vergi gecikti)" : " (borçlu: GECİKTİ)";
        String on = vergi ? " (vergi" : " (borçlu";
        if (kalanMs < 0) return on + ")";
        return on + ": " + kisaSure(Math.max(60_000L, kalanMs)) + " kaldı)";
    }

    static String kisaSure(long ms) {
        long dk = Math.max(0, ms) / 60_000L;
        if (dk >= 1440) return (dk / 1440) + "g";
        if (dk >= 60) return (dk / 60) + "sa";
        return dk + "dk";
    }

    private void iptal(CommandSender s, String[] args) {
        if (args.length < 2) {
            plugin.mesaj(s, "Kullanım: /ceza iptal <no>", NamedTextColor.YELLOW);
            return;
        }
        Kayit k = bul(args[1]);
        if (k == null) {
            plugin.mesaj(s, "Böyle bir ceza yok: " + args[1], NamedTextColor.RED);
            return;
        }
        boolean yazan = s instanceof Player p && p.getUniqueId().equals(k.veren());
        if (!yazan && !s.hasPermission("poliskelepce.admin")) {
            plugin.mesaj(s, "Sadece cezayı yazan ya da op iptal edebilir.", NamedTextColor.RED);
            return;
        }
        cezalar.remove(k);
        kaydet();
        sicil(k, k.hedef(), k.hedefIsim(), Kayitlar.Alan.IPTAL);
        kayit().log("#" + k.id() + " " + s.getName() + ", " + k.hedefIsim() + " adlı oyuncunun cezasını iptal etti (" + k.miktar() + " " + esyaAdi(k.esya()) + ")");
        Player h = Bukkit.getPlayer(k.hedef());
        if (h != null) {
            makbuzGuncelle(h, k, "İPTAL");
            gozalti.sekmeIsmi(h);
            plugin.mesaj(h, "#" + k.id() + " numaralı cezan iptal edildi.", NamedTextColor.GREEN);
        }
        plugin.mesaj(s, "#" + k.id() + " iptal edildi.", NamedTextColor.GREEN);
    }

    private void tahsil(CommandSender s, String[] args) {
        if (!(s instanceof Player polis) || !gozalti.polisMi(polis)) {
            plugin.mesaj(s, "Zorla tahsil sadece polislerin işi.", NamedTextColor.RED);
            return;
        }
        if (args.length < 2) {
            plugin.mesaj(s, "Kullanım: /ceza tahsil <oyuncu>", NamedTextColor.YELLOW);
            return;
        }
        for (Player h : Secici.coklu(s, args[1], true)) {
            tahsilEt(polis, h);
        }
    }

    private void tahsilEt(Player polis, Player h) {
        CommandSender s = polis;
        if (!gozalti.kelepceliMi(h)) {
            plugin.mesaj(s, h.getName() + ": zorla tahsil için kelepçeli olmalı.", NamedTextColor.RED);
            return;
        }
        if (!h.getWorld().equals(polis.getWorld()) || h.getLocation().distance(polis.getLocation()) > 6) {
            plugin.mesaj(s, h.getName() + ": yanında olmalı.", NamedTextColor.RED);
            return;
        }
        int odenen = 0;
        Iterator<Kayit> it = cezalar.iterator();
        while (it.hasNext()) {
            Kayit k = it.next();
            if (!k.hedef().equals(h.getUniqueId())) continue;
            if (say(h.getInventory(), k.esya()) < k.miktar()) continue;
            boolean gec = gecikmis(k);
            teslimEt(k, al(h.getInventory(), k.esya(), k.miktar()));
            it.remove();
            odenen++;
            sicil(k, h.getUniqueId(), h.getName(), Kayitlar.Alan.TAHSIL);
            if (gec) sicil(k, h.getUniqueId(), h.getName(), Kayitlar.Alan.GEC);
            kayit().log("#" + k.id() + " " + polis.getName() + ", " + h.getName() + " adlı oyuncudan " + k.miktar() + " "
                    + esyaAdi(k.esya()) + " zorla tahsil etti" + (gec ? " (gecikmişti)" : ""));
            makbuzGuncelle(h, k, "TAHSİL EDİLDİ");
            plugin.mesaj(h, "#" + k.id() + " cezan (" + k.miktar() + " " + esyaAdi(k.esya()) + ") zorla tahsil edildi.", NamedTextColor.RED);
        }
        kaydet();
        gozalti.sekmeIsmi(h);
        if (odenen == 0) {
            plugin.mesaj(s, h.getName() + " üstünde cezalarını karşılayacak eşya yok (ya da cezası yok).", NamedTextColor.YELLOW);
        } else {
            plugin.mesaj(s, h.getName() + " adlı oyuncudan " + odenen + " ceza tahsil edildi. Eşyalar cezayı yazanlara gitti.", NamedTextColor.GREEN);
        }
    }

    private void odeKomut(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return;
        Kayit k = null;
        if (args.length >= 1) {
            k = bul(args[0]);
            if (k != null && !k.hedef().equals(p.getUniqueId())) k = null;
        } else {
            for (Kayit c : cezalar) {
                if (c.hedef().equals(p.getUniqueId())) { k = c; break; }
            }
        }
        if (k == null) {
            plugin.mesaj(p, "Ödenecek borcun yok.", NamedTextColor.GRAY);
            return;
        }
        int var = say(p.getInventory(), k.esya());
        if (var < k.miktar()) {
            plugin.mesaj(p, "Yeterli " + esyaAdi(k.esya()) + " yok. Gerekli: " + k.miktar() + ", üstünde: " + var, NamedTextColor.RED);
            return;
        }
        boolean gec = gecikmis(k);
        List<ItemStack> odenen = al(p.getInventory(), k.esya(), k.miktar());
        cezalar.remove(k);
        teslimEt(k, odenen);
        kaydet();
        sicil(k, p.getUniqueId(), p.getName(), Kayitlar.Alan.ODENEN);
        if (gec) sicil(k, p.getUniqueId(), p.getName(), Kayitlar.Alan.GEC);
        kayit().log("#" + k.id() + " " + p.getName() + " " + turEtiketi(k.vergi()) + " ödedi: " + k.miktar() + " " + esyaAdi(k.esya())
                + " -> " + k.verenIsim() + (gec ? " (GEÇ ödedi)" : ""));
        makbuzGuncelle(p, k, "ÖDENDİ");
        gozalti.sekmeIsmi(p);
        plugin.mesaj(p, "#" + k.id() + " numaralı " + turEtiketi(k.vergi()) + " borcunu ödedin (" + k.miktar() + " " + esyaAdi(k.esya()) + ").", NamedTextColor.GREEN);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        List<String> sonuc = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("ode")) {
            if (args.length == 1 && s instanceof Player p) {
                for (Kayit k : cezalar) if (k.hedef().equals(p.getUniqueId())) sonuc.add(String.valueOf(k.id()));
            }
            return sonuc;
        }
        if (args.length == 1 && command.getName().equalsIgnoreCase("vergi")) {
            eslesen(sonuc, args[0], Secici.oneriler());
        } else if (args.length == 1) {
            List<String> sec = new ArrayList<>(List.of("liste", "borclular", "iptal", "tahsil"));
            sec.addAll(Secici.oneriler());
            eslesen(sonuc, args[0], sec);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("iptal")) {
            List<String> idler = new ArrayList<>();
            for (Kayit k : cezalar) idler.add(String.valueOf(k.id()));
            eslesen(sonuc, args[1], idler);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("tahsil")) {
            eslesen(sonuc, args[1], Secici.oneriler());
        } else if (args.length == 4 && !args[0].equalsIgnoreCase("iptal") && !args[0].equalsIgnoreCase("tahsil")
                && !args[0].equalsIgnoreCase("liste") && !args[0].equalsIgnoreCase("borclular")) {
            eslesen(sonuc, args[3], List.of("30dk", "12sa", "1g", "3g", "7g"));
        } else if (args.length == 3 && !args[0].equalsIgnoreCase("iptal") && !args[0].equalsIgnoreCase("tahsil")) {
            List<String> esyalar = new ArrayList<>(TURKCE.keySet());
            esyalar.add("el");
            for (Material m : Material.values()) {
                if (m.isItem() && m != Material.AIR && !m.name().startsWith("LEGACY_")) esyalar.add(m.name().toLowerCase(java.util.Locale.ROOT));
            }
            eslesen(sonuc, args[2], esyalar);
        }
        return sonuc;
    }

    // ------------------------------------------------------------------
    // Ceza makbuzu: ceza yazılınca oyuncuya imzalı kitap verilir, ödenince "ÖDENDİ" damgası basılır
    // ------------------------------------------------------------------

    private org.bukkit.NamespacedKey makbuzAnahtari() {
        return new org.bukkit.NamespacedKey(plugin, "makbuz");
    }

    private void makbuzVer(Player hedef, Kayit k, String damga) {
        if (k.vergi()) return;
        if (!plugin.getConfig().getBoolean("ceza.makbuz", true)) return;
        Gozalti.ver(hedef, makbuz(k, damga));
    }

    /** Oyuncunun envanterindeki o cezanın makbuzuna damga basar (ÖDENDİ / İPTAL / TAHSİL EDİLDİ). */
    private void makbuzGuncelle(Player p, Kayit k, String damga) {
        if (k.vergi()) return;
        org.bukkit.NamespacedKey key = makbuzAnahtari();
        ItemStack[] depo = p.getInventory().getStorageContents();
        boolean degisti = false;
        for (int i = 0; i < depo.length; i++) {
            if (makbuzNo(depo[i], key) == k.id()) {
                depo[i] = makbuz(k, damga);
                degisti = true;
            }
        }
        if (degisti) p.getInventory().setStorageContents(depo);
        if (makbuzNo(p.getInventory().getItemInOffHand(), key) == k.id()) p.getInventory().setItemInOffHand(makbuz(k, damga));
    }

    private static int makbuzNo(ItemStack is, org.bukkit.NamespacedKey key) {
        if (is == null || is.getType() != Material.WRITTEN_BOOK || !is.hasItemMeta()) return -1;
        Integer no = is.getItemMeta().getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
        return no == null ? -1 : no;
    }

    private ItemStack makbuz(Kayit k, String damga) {
        Kayitlar t = kayit();
        long son = sonOdeme(k);
        ItemStack kitap = new ItemStack(Material.WRITTEN_BOOK);
        if (!(kitap.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta bm)) return kitap;
        bm.title(Component.text("Ceza Makbuzu #" + k.id() + (damga != null ? " " + damga : "")));
        bm.author(Component.text("T.C. Belediye"));
        Component sayfa1 = Component.text("T.C. BELEDİYE\n", NamedTextColor.DARK_BLUE).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
                .append(Component.text("CEZA MAKBUZU\n\n", NamedTextColor.BLACK).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true))
                .append(Component.text("No: #" + k.id() + "\n"
                        + "Ad: " + k.hedefIsim() + "\n"
                        + "Tutar: " + k.miktar() + " " + esyaAdi(k.esya()) + "\n"
                        + "Sebep: " + k.sebep() + "\n"
                        + "Yazan: " + k.verenIsim() + "\n"
                        + "Tarih: " + t.kisaTarih(k.zaman()) + "\n"
                        + "Son: " + (son == Long.MAX_VALUE ? "süresiz" : t.kisaTarih(son)) + "\n\n", NamedTextColor.BLACK)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
        if (damga == null) {
            sayfa1 = sayfa1.append(Component.text("Ödemek için:\n/ode " + k.id(), NamedTextColor.DARK_RED)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
        } else {
            NamedTextColor renk = damga.equals("ÖDENDİ") ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_GRAY;
            sayfa1 = sayfa1.append(Component.text(damga + "\n", renk).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true))
                    .append(Component.text(t.kisaTarih(System.currentTimeMillis()), renk).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
        }
        Component sayfa2 = Component.text("Belge tarihi:\n" + t.tarih(k.zaman()) + "\n\n"
                + "Son ödeme:\n" + (son == Long.MAX_VALUE ? "süresiz" : t.tarih(son)) + "\n\n"
                + "Süresinde ödenmeyen ceza için polis gözaltı uygulayabilir.\n\nBu makbuzu saklayınız.", NamedTextColor.BLACK);
        bm.pages(List.of(sayfa1, sayfa2));
        bm.getPersistentDataContainer().set(makbuzAnahtari(), org.bukkit.persistence.PersistentDataType.INTEGER, k.id());
        kitap.setItemMeta(bm);
        return kitap;
    }

    // ------------------------------------------------------------------
    // Teslimat
    // ------------------------------------------------------------------

    private void teslimEt(Kayit k, List<ItemStack> esyalar) {
        if (k.veren() == null) return; // konsoldan yazıldıysa eşya kimseye gitmez
        Player veren = Bukkit.getPlayer(k.veren());
        if (veren != null) {
            for (ItemStack is : esyalar) Gozalti.ver(veren, is);
            plugin.mesaj(veren, k.hedefIsim() + ", #" + k.id() + " cezasını ödedi: " + k.miktar() + " " + esyaAdi(k.esya()) + " envanterine geldi.", NamedTextColor.GREEN);
        } else {
            teslimat.computeIfAbsent(k.veren(), x -> new ArrayList<>()).addAll(esyalar);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        List<ItemStack> bekleyen = teslimat.remove(p.getUniqueId());
        if (bekleyen != null && !bekleyen.isEmpty()) {
            int toplam = 0;
            for (ItemStack is : bekleyen) {
                Gozalti.ver(p, is);
                toplam += is.getAmount();
            }
            plugin.mesaj(p, "Sen yokken ödenen cezalar envanterine geldi (" + toplam + " eşya).", NamedTextColor.GREEN);
            kaydet();
        }
        int adet = 0;
        for (Kayit k : cezalar) if (k.hedef().equals(p.getUniqueId())) adet++;
        if (adet > 0) {
            plugin.mesaj(p, adet + " ödenmemiş borcun (ceza/vergi) var, en eskisi " + sure(enEskiBorcYasi(p.getUniqueId()))
                    + " önce yazıldı. Görmek için /ceza liste, ödemek için /ode", NamedTextColor.RED);
        }
        gozalti.sekmeIsmi(p);
    }

    // ------------------------------------------------------------------
    // Envanter yardımcıları
    // Önce düz eşyalar, sonra isimli/büyülü/hasarlı olanlar kullanılır.
    // Plugin'lerin özel eşyaları (çay, kelepçe, anahtar...) asla ceza olarak alınmaz.
    // ------------------------------------------------------------------

    private static boolean ozelEsya(ItemStack is) {
        if (!is.hasItemMeta()) return false;
        for (org.bukkit.NamespacedKey key : is.getItemMeta().getPersistentDataContainer().getKeys()) {
            String ns = key.getNamespace();
            if (ns.equals("cayocagi") || ns.equals("poliskelepce")) return true;
        }
        return false;
    }

    private static boolean uygun(ItemStack is, Material m) {
        return is != null && is.getType() == m && !ozelEsya(is);
    }

    private static int say(PlayerInventory inv, Material m) {
        int t = 0;
        for (ItemStack is : inv.getStorageContents()) if (uygun(is, m)) t += is.getAmount();
        return t;
    }

    /** Envanterden miktar kadar alır ve alınan gerçek eşyaları döndürür. */
    private static List<ItemStack> al(PlayerInventory inv, Material m, int miktar) {
        List<ItemStack> alinanlar = new ArrayList<>();
        ItemStack[] icerik = inv.getStorageContents();
        int kalan = miktar;
        for (int tur = 0; tur < 2 && kalan > 0; tur++) {
            boolean metasiz = tur == 0;
            for (int i = 0; i < icerik.length && kalan > 0; i++) {
                ItemStack is = icerik[i];
                if (!uygun(is, m) || is.hasItemMeta() == metasiz) continue;
                int n = Math.min(kalan, is.getAmount());
                ItemStack parca = is.clone();
                parca.setAmount(n);
                alinanlar.add(parca);
                is.setAmount(is.getAmount() - n);
                icerik[i] = is.getAmount() > 0 ? is : null;
                kalan -= n;
            }
        }
        inv.setStorageContents(icerik);
        return alinanlar;
    }

    static Material esyaBul(String ad) {
        String n = ad.toLowerCase(java.util.Locale.ROOT)
                .replace('ı', 'i').replace('ü', 'u').replace('ö', 'o')
                .replace('ş', 's').replace('ç', 'c').replace('ğ', 'g');
        Material m = TURKCE.get(n);
        if (m != null) return m;
        m = Material.matchMaterial(ad);
        return (m != null && m.isItem() && m != Material.AIR) ? m : null;
    }

    static String esyaAdi(Material m) {
        for (Map.Entry<String, Material> g : TURKCE.entrySet()) {
            if (g.getValue() == m) {
                return switch (g.getKey()) {
                    case "altin" -> "altın";
                    case "zumrut" -> "zümrüt";
                    case "bakir" -> "bakır";
                    case "komur" -> "kömür";
                    case "bugday" -> "buğday";
                    case "kiziltas" -> "kızıltaş";
                    default -> g.getKey();
                };
            }
        }
        return m.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private Kayit bul(String no) {
        try {
            int id = Integer.parseInt(no.replace("#", ""));
            for (Kayit k : cezalar) if (k.id() == id) return k;
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static void satir(CommandSender s, String metin) {
        s.sendMessage(Component.text("  " + metin, NamedTextColor.GRAY));
    }

    private static void eslesen(List<String> hedef, String yazilan, List<String> secenekler) {
        String y = yazilan.toLowerCase(java.util.Locale.ROOT);
        for (String s : secenekler) if (s.toLowerCase(java.util.Locale.ROOT).startsWith(y)) hedef.add(s);
    }

    // ------------------------------------------------------------------
    // Kayıt
    // ------------------------------------------------------------------

    public void yukle() {
        if (!dosya.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dosya);
        sonId = yml.getInt("son-id", 0);
        ConfigurationSection cs = yml.getConfigurationSection("cezalar");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                try {
                    String y = "cezalar." + k + ".";
                    String veren = yml.getString(y + "veren");
                    Material m = Material.matchMaterial(yml.getString(y + "esya", ""));
                    if (m == null) continue;
                    cezalar.add(new Kayit(Integer.parseInt(k),
                            UUID.fromString(yml.getString(y + "hedef")),
                            yml.getString(y + "hedef-isim", "?"),
                            veren == null || veren.isEmpty() ? null : UUID.fromString(veren),
                            yml.getString(y + "veren-isim", "?"),
                            m, yml.getInt(y + "miktar", 1), yml.getString(y + "sebep", ""),
                            yml.getLong(y + "zaman", System.currentTimeMillis()),
                            yml.getLong(y + "vade", -1L),
                            turOku(yml.getString(y + "tur"))));
                } catch (RuntimeException ignored) {
                    // bozuk kaydı atla
                }
            }
        }
        ConfigurationSection ts = yml.getConfigurationSection("teslimat");
        if (ts != null) {
            for (String oyuncu : ts.getKeys(false)) {
                try {
                    List<ItemStack> liste = new ArrayList<>();
                    List<?> ham = ts.getList(oyuncu);
                    if (ham != null) for (Object o : ham) if (o instanceof ItemStack is) liste.add(is);
                    if (!liste.isEmpty()) teslimat.put(UUID.fromString(oyuncu), liste);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void kaydet() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("son-id", sonId);
        for (Kayit k : cezalar) {
            String y = "cezalar." + k.id() + ".";
            yml.set(y + "hedef", k.hedef().toString());
            yml.set(y + "hedef-isim", k.hedefIsim());
            yml.set(y + "veren", k.veren() == null ? "" : k.veren().toString());
            yml.set(y + "veren-isim", k.verenIsim());
            yml.set(y + "esya", k.esya().name());
            yml.set(y + "miktar", k.miktar());
            yml.set(y + "sebep", k.sebep());
            yml.set(y + "zaman", k.zaman());
            yml.set(y + "vade", k.vadeMs());
            yml.set(y + "tur", turEtiketi(k.vergi()));
        }
        for (Map.Entry<UUID, List<ItemStack>> g : teslimat.entrySet()) {
            yml.set("teslimat." + g.getKey(), g.getValue());
        }
        try {
            if (!dosya.getParentFile().exists()) dosya.getParentFile().mkdirs();
            yml.save(dosya);
        } catch (IOException e) {
            plugin.getLogger().warning("cezalar.yml kaydedilemedi: " + e.getMessage());
        }
    }
}
