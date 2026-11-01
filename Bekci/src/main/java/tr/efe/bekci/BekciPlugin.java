package tr.efe.bekci;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bekçi: blok/sandık kaydı, inceleme, geri alma, sandık bulucu ve mezar. */
public final class BekciPlugin extends JavaPlugin implements TabCompleter {

    private static final Component ONEK = Component.text("[Bekçi] ", NamedTextColor.DARK_AQUA);
    private Veritabani db;
    private Inceleme inceleme;
    private GeriAlma geriAlma;
    private Mezar mezar;
    private SandikBulucu sandik;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        db = new Veritabani(getLogger());
        if (!db.ac(new File(getDataFolder(), "kayit.db"))) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(new KayitDinleyici(this, db), this);
        inceleme = new Inceleme(this);
        getServer().getPluginManager().registerEvents(inceleme, this);
        geriAlma = new GeriAlma(this);
        getServer().getScheduler().runTaskTimer(this, db::bosalt, 20L, 20L);
        long gun = Math.max(1, getConfig().getLong("saklama-gun", 30));
        getServer().getScheduler().runTaskTimer(this, () -> db.temizle(System.currentTimeMillis() - gun * 86_400_000L), 100L, 6 * 3600 * 20L);
        sandik = new SandikBulucu(this);
        getServer().getPluginManager().registerEvents(sandik, this);
        mezar = new Mezar(this);
        mezar.yukle();
        getServer().getPluginManager().registerEvents(mezar, this);
        getServer().getScheduler().runTaskTimer(this, mezar::tik, 20L, 20L);
        PluginCommand mk = getCommand("mezar");
        if (mk != null) {
            mk.setExecutor(mezar);
            mk.setTabCompleter(mezar);
        }
        PluginCommand k = getCommand("bekci");
        if (k != null) {
            k.setExecutor(this);
            k.setTabCompleter(this);
        }
        ornek = this;
        getLogger().info("Bekçi açıldı.");
    }

    @Override
    public void onDisable() {
        ornek = null;
        if (sandik != null) sandik.kapat();
        if (mezar != null) mezar.kapat();
        if (db != null) db.kapat();
    }

    private static BekciPlugin ornek;

    /**
     * Diğer eklentiler için (Polis aramada aldıklarını buraya yazar; yansıma ile çağrılır).
     * tur: Islem.Tur adı; bilinmeyen türler yok sayılır.
     */
    public static void disKayit(java.util.UUID uuid, String isim, org.bukkit.Location yer, String tur, String esya, int miktar) {
        BekciPlugin b = ornek;
        if (b == null || b.db == null || yer == null || yer.getWorld() == null) return;
        Islem.Tur t;
        try {
            t = Islem.Tur.valueOf(tur);
        } catch (IllegalArgumentException e) {
            return;
        }
        b.db.ekle(Islem.yeni(uuid, isim, yer.getWorld().getName(), yer.getBlockX(), yer.getBlockY(), yer.getBlockZ(), t, null, null, esya, miktar));
    }

    public Mezar mezar() {
        return mezar;
    }

    public Veritabani db() {
        return db;
    }

    public void mesaj(CommandSender kime, String metin, NamedTextColor renk) {
        kime.sendMessage(ONEK.append(Component.text(metin, renk)));
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
        if (!s.hasPermission("bekci.admin")) {
            mesaj(s, "Bu komut sadece op için.", NamedTextColor.RED);
            return true;
        }
        String alt = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (alt) {
            case "bak" -> {
                if (s instanceof org.bukkit.entity.Player p) inceleme.bak(p);
            }
            case "oyuncu" -> inceleme.oyuncu(s, args);
            case "yakin" -> inceleme.yakin(s, args);
            case "geri" -> geriAlma.geri(s, args);
            case "onayla" -> geriAlma.onayla(s);
            case "sandik", "sandık" -> {
                String ne = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (ne.equals("oyuncu")) sandik.oyuncu(s, args);
                else if (ne.equals("yakin") && s instanceof org.bukkit.entity.Player p) sandik.yakin(p, args);
                else mesaj(s, "Kullanım: /bekci sandik yakin [yarıçap] | /bekci sandik oyuncu <ad>", NamedTextColor.YELLOW);
            }
            case "git" -> {
                if (s instanceof org.bukkit.entity.Player p) sandik.git(p, args);
            }
            case "ender" -> {
                if (s instanceof org.bukkit.entity.Player p) sandik.ender(p, args);
            }
            default -> yardim(s);
        }
        return true;
    }

    private void yardim(CommandSender s) {
        mesaj(s, "Bekçi komutları (op):", NamedTextColor.DARK_AQUA);
        satir(s, "/bekci bak - inceleme modu: bloğa tıkla, geçmişini gör");
        satir(s, "/bekci oyuncu <ad> <süre> [sayfa] - oyuncunun yaptıkları (süre: 30dk, 2sa, 1g)");
        satir(s, "/bekci yakin <yarıçap> <süre> [sayfa] - çevrende olanlar");
        satir(s, "/bekci geri <ad> <süre> [yarıçap] - oyuncunun kırdığı/koyduğu blokları geri al (önizleme + /bekci onayla)");
        satir(s, "/bekci sandik yakin [yarıçap] - etrafındaki sandık, varil, shulker... (Java'da duvar arkasından parlar)");
        satir(s, "/bekci sandik oyuncu <ad> - oyuncunun koyduğu ve hâlâ duran kaplar");
        satir(s, "/bekci git <no> - son listedeki kaba ışınlan");
        satir(s, "/bekci ender <oyuncu> - ender sandığına sadece bak");
        satir(s, "/mezar liste | /mezar sil <no> - mezarlar");
    }

    void satir(CommandSender s, String metin) {
        s.sendMessage(Component.text("  " + metin, NamedTextColor.GRAY));
    }

    private static final List<String> ALT = new ArrayList<>(List.of("bak", "oyuncu", "yakin", "geri", "onayla", "sandik", "git", "ender"));

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        List<String> sonuc = new ArrayList<>();
        if (!command.getName().equalsIgnoreCase("bekci")) return sonuc;
        if (args.length == 1) {
            for (String x : ALT) if (x.startsWith(args[0].toLowerCase(Locale.ROOT))) sonuc.add(x);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sandik")) {
            for (String x : List.of("yakin", "oyuncu")) if (x.startsWith(args[1].toLowerCase(Locale.ROOT))) sonuc.add(x);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("sandik") && args[1].equalsIgnoreCase("oyuncu")) {
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) sonuc.add(p.getName());
            }
        } else if (args.length == 2 && List.of("oyuncu", "geri", "ender").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) sonuc.add(p.getName());
            }
        } else if (args.length == 3 && List.of("oyuncu", "yakin", "geri").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (String x : List.of("30dk", "1sa", "6sa", "1g", "7g")) if (x.startsWith(args[2].toLowerCase(Locale.ROOT))) sonuc.add(x);
        }
        return sonuc;
    }
}
