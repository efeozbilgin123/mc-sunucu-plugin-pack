package tr.efe.cay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class CayPlugin extends JavaPlugin implements TabCompleter {

    private static final Component ONEK = Component.text("[Çay Ocağı] ", NamedTextColor.GOLD);

    private CayItems items;
    private Semaverler semaverler;
    private CayListener dinleyici;
    private final List<NamespacedKey> tarifler = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Güncellemeyle gelen yeni ayarları mevcut config.yml'ye ekle
        getConfig().options().copyDefaults(true);
        saveConfig();
        items = new CayItems(this);
        semaverler = new Semaverler(getDataFolder(), getLogger());
        semaverler.yukle();

        tarifleriEkle();

        CayListener listener = new CayListener(this, items, semaverler);
        dinleyici = listener;
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(new CayBahcesi(items, semaverler), this);
        getServer().getScheduler().runTaskTimer(this, listener::tik, 20L, 20L);
        // Arada bir kaydet (çökme olursa demlikler kaybolmasın)
        getServer().getScheduler().runTaskTimer(this, semaverler::kaydet, 20L * 60, 20L * 60);

        PluginCommand komut = getCommand("cay");
        if (komut != null) {
            komut.setExecutor(this);
            komut.setTabCompleter(this);
        }
        getLogger().info("Çay ocağı açıldı. Afiyet olsun!");
    }

    @Override
    public void onDisable() {
        if (dinleyici != null) dinleyici.kapat();
        if (semaverler != null) semaverler.kaydet();
        for (NamespacedKey k : tarifler) {
            Bukkit.removeRecipe(k);
        }
        tarifler.clear();
    }

    public String namespace() {
        return getName().toLowerCase(java.util.Locale.ROOT);
    }

    public void mesaj(CommandSender kime, String metin, TextColor renk) {
        kime.sendMessage(ONEK.append(Component.text(metin, renk)));
    }

    // ------------------------------------------------------------------

    private void tarifleriEkle() {
        ItemStack kuru = items.olustur(CayItems.Tur.KURU, 1);
        RecipeChoice yaprak = new RecipeChoice.ExactChoice(items.olustur(CayItems.Tur.YAPRAK, 1));

        NamespacedKey firin = new NamespacedKey(this, "kuru_cay_firin");
        tarifEkle(firin, new FurnaceRecipe(firin, kuru, yaprak, 0.35f, 200));

        NamespacedKey tutsu = new NamespacedKey(this, "kuru_cay_tutsuluk");
        tarifEkle(tutsu, new SmokingRecipe(tutsu, kuru, yaprak, 0.35f, 100));

        NamespacedKey kamp = new NamespacedKey(this, "kuru_cay_kamp");
        tarifEkle(kamp, new CampfireRecipe(kamp, kuru, yaprak, 0.35f, 600));

        // Çay fidanı: 2 taze çay yaprağı + kemik tozu
        NamespacedKey fidanKey = new NamespacedKey(this, "cay_fidani");
        ShapelessRecipe fidan = new ShapelessRecipe(fidanKey, items.olustur(CayItems.Tur.FIDAN, 1));
        fidan.addIngredient(yaprak);
        fidan.addIngredient(yaprak);
        fidan.addIngredient(Material.BONE_MEAL);
        tarifEkle(fidanKey, fidan);

        NamespacedKey seker = new NamespacedKey(this, "sekerli_cay");
        ShapelessRecipe sekerli = new ShapelessRecipe(seker, items.olustur(CayItems.Tur.SEKERLI, 1));
        sekerli.addIngredient(new RecipeChoice.ExactChoice(items.olustur(CayItems.Tur.CAY, 1)));
        sekerli.addIngredient(Material.SUGAR);
        tarifEkle(seker, sekerli);
    }

    private void tarifEkle(NamespacedKey key, org.bukkit.inventory.Recipe tarif) {
        Bukkit.removeRecipe(key); // /reload sonrası çakışmasın
        if (Bukkit.addRecipe(tarif)) {
            tarifler.add(key);
        } else {
            getLogger().warning("Tarif eklenemedi: " + key);
        }
    }

    // ------------------------------------------------------------------
    // /cay komutu
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("yardim") || args[0].equalsIgnoreCase("yardım")) {
            yardim(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("yap")) {
            if (!(sender instanceof Player p)) {
                mesaj(sender, "Bu komut oyun içinde kullanılır.", NamedTextColor.RED);
                return true;
            }
            yap(p, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("ver")) {
            if (!sender.hasPermission("cayocagi.ver")) {
                mesaj(sender, "Bu komut için yetkin yok.", NamedTextColor.RED);
                return true;
            }
            if (args.length < 3) {
                mesaj(sender, "Kullanım: /cay ver <oyuncu> <yaprak|kuru|cay|sekerli|fidan> [adet]", NamedTextColor.YELLOW);
                return true;
            }
            List<Player> hedefler = Secici.coklu(sender, args[1], false);
            if (hedefler.isEmpty()) return true;
            CayItems.Tur tur = CayItems.Tur.parse(args[2]);
            if (tur == null) {
                mesaj(sender, "Tür yaprak, kuru, cay, sekerli ya da fidan olmalı.", NamedTextColor.RED);
                return true;
            }
            int adet = 1;
            if (args.length >= 4) {
                try {
                    adet = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                } catch (NumberFormatException e) {
                    mesaj(sender, "Adet sayı olmalı.", NamedTextColor.RED);
                    return true;
                }
            }
            for (Player hedef : hedefler) {
                envantereVer(hedef, items.olustur(tur, adet));
            }
            mesaj(sender, hedefler.size() == 1
                    ? hedefler.get(0).getName() + " adlı oyuncuya " + adet + " adet verildi."
                    : hedefler.size() + " oyuncuya " + adet + "'er adet verildi.", NamedTextColor.GREEN);
            return true;
        }

        yardim(sender);
        return true;
    }

    // ------------------------------------------------------------------
    // /cay yap: çalışma masası / fırın kullanmadan üretim (Bedrock dostu)
    // ------------------------------------------------------------------

    private void yap(Player p, String[] args) {
        if (args.length < 2) {
            mesaj(p, "Kullanım: /cay yap <kuru|fidan|sekerli> [adet]", NamedTextColor.YELLOW);
            return;
        }
        int istenen = 1;
        if (args.length >= 3) {
            try {
                istenen = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                mesaj(p, "Adet sayı olmalı.", NamedTextColor.RED);
                return;
            }
        }
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "kuru" -> {
                if (!yakindaAtesVar(p)) {
                    mesaj(p, "Kurutmak için yakınında (4 blok) yanan bir kamp ateşi ya da fırın olmalı.", NamedTextColor.RED);
                    return;
                }
                int n = Math.min(istenen, ozelSay(inv, CayItems.Tur.YAPRAK));
                if (n == 0) { eksik(p, "Taze Çay Yaprağı"); return; }
                ozelAl(inv, CayItems.Tur.YAPRAK, n);
                envantereVer(p, items.olustur(CayItems.Tur.KURU, n));
                mesaj(p, n + " Kuru Çay hazır.", NamedTextColor.GREEN);
            }
            case "fidan" -> {
                int n = Math.min(istenen, Math.min(ozelSay(inv, CayItems.Tur.YAPRAK) / 2, duzSay(inv, Material.BONE_MEAL)));
                if (n == 0) { eksik(p, "2 Taze Çay Yaprağı + 1 kemik tozu"); return; }
                ozelAl(inv, CayItems.Tur.YAPRAK, n * 2);
                duzAl(inv, Material.BONE_MEAL, n);
                envantereVer(p, items.olustur(CayItems.Tur.FIDAN, n));
                mesaj(p, n + " Çay Fidanı hazır.", NamedTextColor.GREEN);
            }
            case "sekerli", "şekerli" -> {
                int n = Math.min(istenen, Math.min(ozelSay(inv, CayItems.Tur.CAY), duzSay(inv, Material.SUGAR)));
                if (n == 0) { eksik(p, "1 Çay + 1 şeker"); return; }
                ozelAl(inv, CayItems.Tur.CAY, n);
                duzAl(inv, Material.SUGAR, n);
                envantereVer(p, items.olustur(CayItems.Tur.SEKERLI, n));
                mesaj(p, n + " Şekerli Çay hazır.", NamedTextColor.GREEN);
            }
            default -> mesaj(p, "kuru, fidan ya da sekerli yaz.", NamedTextColor.RED);
        }
    }

    private void eksik(Player p, String gerekli) {
        mesaj(p, "Malzeme yetersiz. Gerekli: " + gerekli, NamedTextColor.RED);
    }

    private static boolean yakindaAtesVar(Player p) {
        org.bukkit.block.Block merkez = p.getLocation().getBlock();
        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -4; z <= 4; z++) {
                    org.bukkit.block.Block b = merkez.getRelative(x, y, z);
                    Material m = b.getType();
                    if (m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE || m == Material.FURNACE
                            || m == Material.SMOKER || m == Material.BLAST_FURNACE) {
                        if (b.getBlockData() instanceof org.bukkit.block.data.Lightable l && l.isLit()) return true;
                    }
                }
            }
        }
        return false;
    }

    private int ozelSay(org.bukkit.inventory.PlayerInventory inv, CayItems.Tur tur) {
        int t = 0;
        for (ItemStack is : inv.getStorageContents()) if (items.turu(is) == tur) t += is.getAmount();
        return t;
    }

    private void ozelAl(org.bukkit.inventory.PlayerInventory inv, CayItems.Tur tur, int miktar) {
        ItemStack[] icerik = inv.getStorageContents();
        for (int i = 0; i < icerik.length && miktar > 0; i++) {
            if (items.turu(icerik[i]) != tur) continue;
            int al = Math.min(miktar, icerik[i].getAmount());
            icerik[i].setAmount(icerik[i].getAmount() - al);
            if (icerik[i].getAmount() <= 0) icerik[i] = null;
            miktar -= al;
        }
        inv.setStorageContents(icerik);
    }

    private static int duzSay(org.bukkit.inventory.PlayerInventory inv, Material m) {
        int t = 0;
        for (ItemStack is : inv.getStorageContents()) if (is != null && is.getType() == m && !is.hasItemMeta()) t += is.getAmount();
        return t;
    }

    private static void duzAl(org.bukkit.inventory.PlayerInventory inv, Material m, int miktar) {
        ItemStack[] icerik = inv.getStorageContents();
        for (int i = 0; i < icerik.length && miktar > 0; i++) {
            ItemStack is = icerik[i];
            if (is == null || is.getType() != m || is.hasItemMeta()) continue;
            int al = Math.min(miktar, is.getAmount());
            is.setAmount(is.getAmount() - al);
            if (is.getAmount() <= 0) icerik[i] = null;
            miktar -= al;
        }
        inv.setStorageContents(icerik);
    }

    /** Eşyayı yasal yığınlara bölerek verir (iksirler 1'erli); sığmayan yere düşer. */
    static void envantereVer(Player p, ItemStack item) {
        int yigin = Math.max(1, item.getMaxStackSize());
        int kalan = item.getAmount();
        while (kalan > 0) {
            ItemStack parca = item.clone();
            parca.setAmount(Math.min(yigin, kalan));
            kalan -= parca.getAmount();
            for (ItemStack artan : p.getInventory().addItem(parca).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), artan);
            }
        }
    }

    private void yardim(CommandSender s) {
        mesaj(s, "Çay nasıl yapılır:", NamedTextColor.GOLD);
        satir(s, "1) Çimen ya da eğrelti kır, bazen Taze Çay Yaprağı düşer.");
        satir(s, "2) Yaprağı fırında, tütsülükte ya da kamp ateşinde kurut: Kuru Çay.");
        satir(s, "3) Semaver kur: yanan kamp ateşinin üstüne kazan koy, içine su doldur.");
        satir(s, "4) Kuru Çay ile kazana sağ tıkla, çay demlenmeye başlar.");
        satir(s, "5) Demlenince cam şişeyle sağ tıkla: her su seviyesi 1 bardak.");
        satir(s, "6) Çay + şeker (çalışma masası) = Şekerli Çay.");
        satir(s, "Çay bahçesi: 2 Taze Çay Yaprağı + kemik tozu = Çay Fidanı.");
        satir(s, "  Fidanı toprağa dik, büyüyünce sağ tıkla: taze yaprak. Kırarsan fidan geri gelir.");
        satir(s, "Çalışma masası/fırın yerine (Bedrock için de): /cay yap <kuru|fidan|sekerli> [adet]");
        satir(s, "  kuru: yakınında yanan kamp ateşi ya da fırın olmalı");
        if (s.hasPermission("cayocagi.ver")) {
            satir(s, "Admin: /cay ver <oyuncu> <yaprak|kuru|cay|sekerli|fidan> [adet]");
        }
    }

    private void satir(CommandSender s, String metin) {
        s.sendMessage(Component.text("  " + metin, NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> sonuc = new ArrayList<>();
        if (args.length == 1) {
            ekleEslesen(sonuc, args[0], List.of("yardim", "yap", "ver"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("ver")) {
            ekleEslesen(sonuc, args[1], Secici.oneriler());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("yap")) {
            ekleEslesen(sonuc, args[1], List.of("kuru", "fidan", "sekerli"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("yap")) {
            ekleEslesen(sonuc, args[2], List.of("1", "4", "16", "64"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("ver")) {
            ekleEslesen(sonuc, args[2], List.of("yaprak", "kuru", "cay", "sekerli", "fidan"));
        }
        return sonuc;
    }

    private static void ekleEslesen(List<String> hedef, String yazilan, List<String> secenekler) {
        String y = yazilan.toLowerCase(java.util.Locale.ROOT);
        for (String s : secenekler) {
            if (s.toLowerCase(java.util.Locale.ROOT).startsWith(y)) hedef.add(s);
        }
    }
}
