package tr.efe.polis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class PolisPlugin extends JavaPlugin {

    private static final Component ONEK = Component.text("[Polis] ", NamedTextColor.AQUA);

    private PolisItems items;
    private Gozalti gozalti;
    private CezaSistemi ceza;
    private Kayitlar kayit;
    private Yarali yarali;
    private Etiket etiket;
    private Savas savas;
    private Idam idam;
    private Gorsel gorsel;
    private final List<NamespacedKey> tarifler = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        items = new PolisItems(this);
        tarifleriEkle(items);

        kayit = new Kayitlar(this);
        kayit.yukle();
        gozalti = new Gozalti(this, items);
        ceza = new CezaSistemi(this, gozalti);
        gozalti.cezaBagla(ceza);
        yarali = new Yarali(this);
        etiket = new Etiket(this);
        savas = new Savas(this);
        idam = new Idam(this);
        idam.yukle();
        gorsel = new Gorsel(this);
        ceza.yukle();
        gozalti.yukle();
        yarali.yukle();
        etiket.yukle();
        gorsel.yukle();
        gozalti.acilistaGeriYukle();
        getServer().getPluginManager().registerEvents(gozalti, this);
        getServer().getPluginManager().registerEvents(etiket, this);
        getServer().getPluginManager().registerEvents(ceza, this);
        getServer().getPluginManager().registerEvents(yarali, this);
        getServer().getPluginManager().registerEvents(new SohbetOneki(this), this);
        getServer().getPluginManager().registerEvents(savas, this);
        getServer().getPluginManager().registerEvents(idam, this);
        komut(idam, "idam");
        EskiMesaiGocu goc = new EskiMesaiGocu(this);
        getServer().getPluginManager().registerEvents(goc, this);
        goc.acilis();
        getServer().getPluginManager().registerEvents(gorsel, this);
        komut(yarali, "doktor", "kaldir", "ol");
        komut(gorsel, "siren", "pano");
        PluginCommand sicil = getCommand("sicil");
        if (sicil != null) {
            sicil.setExecutor((s, c, l, a) -> {
                kayit.sicilGoster(s, a);
                return true;
            });
            sicil.setTabCompleter((s, c, l, a) -> {
                List<String> sonuc = new ArrayList<>();
                if (a.length != 1) return sonuc;
                List<String> isimler = new ArrayList<>(Secici.oneriler());
                for (String i : kayit.sicilIsimleri()) if (!isimler.contains(i)) isimler.add(i);
                for (String i : isimler) if (i.toLowerCase(java.util.Locale.ROOT).startsWith(a[0].toLowerCase(java.util.Locale.ROOT))) sonuc.add(i);
                return sonuc;
            });
        }
        getServer().getScheduler().runTaskTimer(this, () -> {
            gorsel.tikHizli();
            yarali.tik();
            savas.tik();
        }, 5L, 5L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            gorsel.tikSaniye();
        }, 20L, 20L);
        for (String ad : new String[]{"ceza", "ode", "vergi"}) {
            PluginCommand k = getCommand(ad);
            if (k != null) {
                k.setExecutor(ceza);
                k.setTabCompleter(ceza);
            }
        }
        getServer().getScheduler().runTaskTimer(this, gozalti::tik, 5L, 5L);
        // Tab'daki "(borçlu 3g)" süreleri dakikada bir güncellensin
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) gozalti.sekmeIsmi(p);
        }, 1200L, 1200L);

        for (String ad : new String[]{"polis", "baskan", "serbest", "kelepce", "anahtar", "ara", "koy"}) {
            PluginCommand k = getCommand(ad);
            if (k != null) {
                k.setExecutor(gozalti);
                k.setTabCompleter(gozalti);
            }
        }
        getLogger().info("Polis sistemi açıldı.");
    }

    private <T extends org.bukkit.command.CommandExecutor & org.bukkit.command.TabCompleter> void komut(T yurutucu, String... adlar) {
        for (String ad : adlar) {
            PluginCommand k = getCommand(ad);
            if (k != null) {
                k.setExecutor(yurutucu);
                k.setTabCompleter(yurutucu);
            }
        }
    }

    public PolisItems items() { return items; }
    public Gozalti gozalti() { return gozalti; }
    public CezaSistemi ceza() { return ceza; }
    public Kayitlar kayit() { return kayit; }
    public Yarali yarali() { return yarali; }
    public Etiket etiket() { return etiket; }
    public Savas savas() { return savas; }
    public Gorsel gorsel() { return gorsel; }

    @Override
    public void onDisable() {
        if (savas != null) savas.kapaniyor();
        if (idam != null) idam.kapat();
        if (etiket != null) etiket.kapat();
        if (yarali != null) yarali.kapat();
        if (gorsel != null) gorsel.kapat();
        if (gozalti != null) gozalti.kapat();
        if (ceza != null) ceza.kaydet();
        for (NamespacedKey k : tarifler) Bukkit.removeRecipe(k);
        tarifler.clear();
    }

    public String namespace() {
        return getName().toLowerCase(java.util.Locale.ROOT);
    }

    public void mesaj(CommandSender kime, String metin, TextColor renk) {
        kime.sendMessage(ONEK.append(Component.text(metin, renk)));
    }

    private void tarifleriEkle(PolisItems items) {
        // Kelepçe: demir külçe - demir parçacığı - demir külçe (yan yana)
        NamespacedKey kk = new NamespacedKey(this, "kelepce");
        ShapedRecipe kelepce = new ShapedRecipe(kk, items.olustur(PolisItems.Tur.KELEPCE, 1));
        kelepce.shape("INI");
        kelepce.setIngredient('I', Material.IRON_INGOT);
        kelepce.setIngredient('N', Material.IRON_NUGGET);
        tarifEkle(kk, kelepce);

    }

    private void tarifEkle(NamespacedKey key, ShapedRecipe tarif) {
        Bukkit.removeRecipe(key);
        if (Bukkit.addRecipe(tarif)) {
            tarifler.add(key);
        } else {
            getLogger().warning("Tarif eklenemedi: " + key);
        }
    }

    public boolean bizimTarif(NamespacedKey key) {
        return key != null && key.getNamespace().equals(namespace());
    }
}
