package tr.efe.polis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Polis kaydı ve sicil.
 * - Her olay (kelepçe, açma, ceza, ödeme, tahsil, yaralanma...) tarihiyle polis-log.txt dosyasına yazılır,
 *   son 1000 olay /polis log ile oyunda görülür.
 * - Her oyuncunun sicili sicil.yml'de tutulur: kaç kez gözaltı, kaç ceza, kaçı geç ödendi...
 */
public final class Kayitlar {

    public enum Alan { GOZALTI, CEZA, ODENEN, GEC, TAHSIL, IPTAL, YARALANMA, OLUM, KACMA, IDAM }

    /** Floodgate (Bedrock) oyuncularının UUID'sinin üst yarısı 0'dır. */
    public static boolean bedrockMu(UUID id) {
        return id.getMostSignificantBits() == 0;
    }

    static final class Sicil {
        String isim = "?";
        final int[] sayac = new int[Alan.values().length];
        long sonGozalti;
    }

    private static final int BELLEK = 1000;
    private static final int SAYFA = 10;

    private final PolisPlugin plugin;
    private final File logDosya;
    private final File sicilDosya;
    private final ArrayDeque<String> sonlar = new ArrayDeque<>();
    private final Map<UUID, Sicil> siciller = new HashMap<>();

    public Kayitlar(PolisPlugin plugin) {
        this.plugin = plugin;
        this.logDosya = new File(plugin.getDataFolder(), "polis-log.txt");
        this.sicilDosya = new File(plugin.getDataFolder(), "sicil.yml");
    }

    // ------------------------------------------------------------------
    // Zaman
    // ------------------------------------------------------------------

    ZoneId bolge() {
        try {
            return ZoneId.of(plugin.getConfig().getString("saat-dilimi", "Europe/Istanbul"));
        } catch (RuntimeException e) {
            return ZoneId.systemDefault();
        }
    }

    /** 01.11.2026 03:40 */
    String tarih(long ms) {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT).withZone(bolge()).format(Instant.ofEpochMilli(ms));
    }

    /** 01.11 03:40 */
    String kisaTarih(long ms) {
        return DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale.ROOT).withZone(bolge()).format(Instant.ofEpochMilli(ms));
    }

    // ------------------------------------------------------------------
    // Log
    // ------------------------------------------------------------------

    public void log(String metin) {
        String satir = "[" + tarih(System.currentTimeMillis()) + "] " + metin;
        sonlar.addLast(satir);
        while (sonlar.size() > BELLEK) sonlar.pollFirst();
        plugin.getLogger().info("[Kayit] " + metin);
        try {
            if (!logDosya.getParentFile().exists()) logDosya.getParentFile().mkdirs();
            Files.writeString(logDosya.toPath(), satir + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("polis-log.txt yazılamadı: " + e.getMessage());
        }
    }

    /** /polis log [oyuncu] [sayfa] : en yeni olay en üstte. */
    public void logGoster(CommandSender s, String[] args) {
        String filtre = null;
        int sayfa = 1;
        for (int i = 1; i < args.length; i++) {
            try {
                sayfa = Math.max(1, Integer.parseInt(args[i]));
            } catch (NumberFormatException e) {
                filtre = args[i].toLowerCase(Locale.ROOT);
            }
        }
        List<String> uyan = new ArrayList<>();
        Iterator<String> geri = sonlar.descendingIterator();
        while (geri.hasNext()) {
            String satir = geri.next();
            if (filtre == null || satir.toLowerCase(Locale.ROOT).contains(filtre)) uyan.add(satir);
        }
        if (uyan.isEmpty()) {
            plugin.mesaj(s, filtre == null ? "Kayıt yok." : "\"" + filtre + "\" için kayıt yok.", NamedTextColor.GRAY);
            return;
        }
        int toplamSayfa = (uyan.size() + SAYFA - 1) / SAYFA;
        sayfa = Math.min(sayfa, toplamSayfa);
        plugin.mesaj(s, "Polis kaydı" + (filtre != null ? " (" + filtre + ")" : "") + " - sayfa " + sayfa + "/" + toplamSayfa
                + " (en yeni üstte)", NamedTextColor.AQUA);
        for (int i = (sayfa - 1) * SAYFA; i < Math.min(uyan.size(), sayfa * SAYFA); i++) {
            s.sendMessage(Component.text("  " + uyan.get(i), NamedTextColor.GRAY));
        }
        if (sayfa < toplamSayfa) {
            s.sendMessage(Component.text("  Devamı: /polis log " + (filtre != null ? filtre + " " : "") + (sayfa + 1), NamedTextColor.DARK_GRAY));
        }
        s.sendMessage(Component.text("  Tüm kayıtlar sunucuda: plugins/PolisKelepce/polis-log.txt", NamedTextColor.DARK_GRAY));
    }

    // ------------------------------------------------------------------
    // Sicil
    // ------------------------------------------------------------------

    public void say(UUID id, String isim, Alan alan) {
        if (id == null) return;
        Sicil s = siciller.computeIfAbsent(id, x -> new Sicil());
        if (isim != null) s.isim = isim;
        s.sayac[alan.ordinal()]++;
        if (alan == Alan.GOZALTI) s.sonGozalti = System.currentTimeMillis();
        sicilKaydet();
    }

    /** /sicil [oyuncu] */
    private record Satir(String metin, NamedTextColor renk) {}

    /** /sicil [oyuncu] [yazi] : Java'da kitap, Bedrock'ta ya da "yazi" ile sohbet. */
    public void sicilGoster(CommandSender s, String[] args) {
        boolean yazi = args.length > 0 && args[args.length - 1].equalsIgnoreCase("yazi");
        if (yazi) args = java.util.Arrays.copyOf(args, args.length - 1);
        UUID id;
        String isim;
        if (args.length == 0) {
            if (!(s instanceof Player p)) {
                plugin.mesaj(s, "Kullanım: /sicil <oyuncu>", NamedTextColor.YELLOW);
                return;
            }
            id = p.getUniqueId();
            isim = p.getName();
        } else if (args[0].startsWith("@")) {
            Player p = Secici.tekli(s, args[0]);
            if (p == null) return;
            id = p.getUniqueId();
            isim = p.getName();
        } else {
            Player p = Bukkit.getPlayerExact(args[0]);
            if (p != null) {
                id = p.getUniqueId();
                isim = p.getName();
            } else {
                id = null;
                isim = args[0];
                for (Map.Entry<UUID, Sicil> g : siciller.entrySet()) {
                    if (g.getValue().isim.equalsIgnoreCase(args[0])) {
                        id = g.getKey();
                        isim = g.getValue().isim;
                        break;
                    }
                }
            }
        }
        List<Satir> kayit = new ArrayList<>();
        Sicil sc = id != null ? siciller.get(id) : null;
        if (sc == null) {
            kayit.add(new Satir("Temiz sicil, hiç kayıt yok.", NamedTextColor.DARK_GREEN));
        } else {
            int[] c = sc.sayac;
            kayit.add(new Satir("Gözaltı: " + c[Alan.GOZALTI.ordinal()] + " kez"
                    + (sc.sonGozalti > 0 ? " (son: " + tarih(sc.sonGozalti) + ")" : ""), NamedTextColor.GRAY));
            kayit.add(new Satir("Ceza: " + c[Alan.CEZA.ordinal()] + " aldı, " + c[Alan.ODENEN.ordinal()]
                    + " kendisi ödedi, " + c[Alan.TAHSIL.ordinal()] + " zorla tahsil, " + c[Alan.IPTAL.ordinal()] + " iptal",
                    NamedTextColor.GRAY));
            int gec = c[Alan.GEC.ordinal()];
            kayit.add(new Satir("Geç ödenen: " + gec, gec > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
            int kacma = c[Alan.KACMA.ordinal()];
            kayit.add(new Satir("Kaçma girişimi: " + kacma, kacma > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
            kayit.add(new Satir("Yaralanma: " + c[Alan.YARALANMA.ordinal()] + ", ölüm (yaralıyken): "
                    + c[Alan.OLUM.ordinal()], NamedTextColor.GRAY));
            if (c[Alan.IDAM.ordinal()] > 0) kayit.add(new Satir("İdam: " + c[Alan.IDAM.ordinal()], NamedTextColor.DARK_RED));
        }
        List<String> simdi = new ArrayList<>();
        if (id != null) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && plugin.gozalti().kelepceliMi(p)) simdi.add("kelepçeli");
            CezaSistemi.BorcOzet b = plugin.ceza().borcOzet(id);
            if (b != null) simdi.add(b.adet() + " ödenmemiş " + CezaSistemi.turEtiketi(b.vergi()) + (b.gec() ? " (GECİKMİŞ)" : ""));
            if (p != null && plugin.yarali().yaraliMi(p)) simdi.add("yaralı");
        }

        if (!yazi && s instanceof Player okuyan && !bedrockMu(okuyan.getUniqueId())) {
            okuyan.openBook(sicilKitabi(isim, kayit, simdi));
            return;
        }
        plugin.mesaj(s, "Sicil: " + isim, NamedTextColor.AQUA);
        for (Satir st : kayit) s.sendMessage(Component.text("  " + st.metin(), st.renk() == NamedTextColor.DARK_GREEN ? NamedTextColor.GREEN : st.renk()));
        if (!simdi.isEmpty()) s.sendMessage(Component.text("  Şu an: " + String.join(", ", simdi), NamedTextColor.RED));
    }

    private org.bukkit.inventory.ItemStack sicilKitabi(String isim, List<Satir> kayit, List<String> simdi) {
        org.bukkit.inventory.ItemStack kitap = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITTEN_BOOK);
        if (!(kitap.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta bm)) return kitap;
        bm.title(Component.text("Sicil: " + isim));
        bm.author(Component.text("Karakol"));
        Component s1 = Component.text("SİCİL KAYDI\n", NamedTextColor.DARK_BLUE).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
                .append(Component.text(isim + "\n\n", NamedTextColor.BLACK).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
        for (Satir st : kayit) {
            NamedTextColor r = st.renk() == NamedTextColor.GRAY ? NamedTextColor.BLACK : st.renk();
            s1 = s1.append(Component.text(st.metin() + "\n", r).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
        }
        Component s2 = Component.text("ŞU AN\n\n", NamedTextColor.DARK_BLUE).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
                .append(Component.text(simdi.isEmpty() ? "Temiz." : String.join("\n", simdi), simdi.isEmpty() ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_RED)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false))
                .append(Component.text("\n\n" + tarih(System.currentTimeMillis()), NamedTextColor.DARK_GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
        bm.pages(List.of(s1, s2));
        kitap.setItemMeta(bm);
        return kitap;
    }

    public List<String> sicilIsimleri() {
        List<String> l = new ArrayList<>();
        for (Sicil s : siciller.values()) l.add(s.isim);
        return l;
    }

    // ------------------------------------------------------------------
    // Dosya
    // ------------------------------------------------------------------

    public void yukle() {
        // Son olayları dosyadan belleğe al; dosya çok büyüdüyse kenara kaldır
        if (logDosya.exists()) {
            try {
                if (logDosya.length() > 5L * 1024 * 1024) {
                    File eski = new File(logDosya.getParentFile(), "polis-log-eski.txt");
                    if (eski.exists()) eski.delete();
                    logDosya.renameTo(eski);
                } else {
                    List<String> satirlar = Files.readAllLines(logDosya.toPath(), StandardCharsets.UTF_8);
                    for (int i = Math.max(0, satirlar.size() - BELLEK); i < satirlar.size(); i++) {
                        if (!satirlar.get(i).isBlank()) sonlar.addLast(satirlar.get(i));
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("polis-log.txt okunamadı: " + e.getMessage());
            }
        }
        if (!sicilDosya.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(sicilDosya);
        ConfigurationSection cs = yml.getConfigurationSection("sicil");
        if (cs == null) return;
        for (String k : cs.getKeys(false)) {
            try {
                Sicil s = new Sicil();
                String y = "sicil." + k + ".";
                s.isim = yml.getString(y + "isim", "?");
                for (Alan a : Alan.values()) s.sayac[a.ordinal()] = yml.getInt(y + a.name().toLowerCase(Locale.ROOT), 0);
                s.sonGozalti = yml.getLong(y + "son-gozalti", 0L);
                siciller.put(UUID.fromString(k), s);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void sicilKaydet() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Sicil> g : siciller.entrySet()) {
            String y = "sicil." + g.getKey() + ".";
            Sicil s = g.getValue();
            yml.set(y + "isim", s.isim);
            for (Alan a : Alan.values()) yml.set(y + a.name().toLowerCase(Locale.ROOT), s.sayac[a.ordinal()]);
            yml.set(y + "son-gozalti", s.sonGozalti);
        }
        try {
            if (!sicilDosya.getParentFile().exists()) sicilDosya.getParentFile().mkdirs();
            yml.save(sicilDosya);
        } catch (IOException e) {
            plugin.getLogger().warning("sicil.yml kaydedilemedi: " + e.getMessage());
        }
    }
}
