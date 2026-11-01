package tr.efe.polis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Komutlarda oyuncu adı yerine @a, @p, @r, @s seçicileri kullanılabilsin.
 * - @p / @r komutu yazanı hiç seçmez: @p = en yakındaki BAŞKA oyuncu, @r = rastgele BAŞKA oyuncu.
 * - Köşeli parantezli seçiciler (@a[distance=..10] gibi) sadece yetkililere açık;
 *   aksi halde sıradan oyuncular başkalarının konumunu sorgulayabilirdi.
 */
final class Secici {

    private static final String YETKI = "poliskelepce.admin";

    private Secici() {
    }

    /**
     * Oyuncu adı ya da seçiciyi oyunculara çevirir.
     * @param kendiniHaric @a gibi çoklu seçicide komutu yazanı listeden çıkar (örn. /ceza @a ile kendine ceza yazma)
     */
    static List<Player> coklu(CommandSender s, String arg, boolean kendiniHaric) {
        List<Player> liste = new ArrayList<>();
        if (!arg.startsWith("@")) {
            Player p = Bukkit.getPlayerExact(arg);
            if (p != null) {
                liste.add(p);
            } else {
                s.sendMessage(Component.text("Oyuncu bulunamadı (oyunda olmalı): " + arg, NamedTextColor.RED));
            }
            return liste;
        }

        if (arg.contains("[") && !s.hasPermission(YETKI)) {
            s.sendMessage(Component.text("Köşeli parantezli seçicileri sadece yetkililer kullanabilir. @a, @p, @r, @s kullan.", NamedTextColor.RED));
            return liste;
        }

        String sec = arg.toLowerCase(Locale.ROOT);
        boolean tekKisi = sec.startsWith("@p") || sec.startsWith("@r");
        String sorgu = arg;
        if (tekKisi && s instanceof Player) {
            // @p ve @r yazanı seçmesin: herkesi sıralayıp yazanı çıkarıp ilkini alacağız
            String sira = sec.startsWith("@p") ? "sort=nearest" : "sort=random";
            String ek = arg.substring(2);
            // distance=0.. → sadece yazanın dünyasındaki oyuncular
            sorgu = ek.startsWith("[") ? "@a[" + sira + ",distance=0..," + ek.substring(1) : "@a[" + sira + ",distance=0..]";
        }
        try {
            for (Entity e : Bukkit.selectEntities(s, sorgu)) {
                if (e instanceof Player p) liste.add(p);
            }
        } catch (IllegalArgumentException ex) {
            s.sendMessage(Component.text("Geçersiz seçici: " + arg, NamedTextColor.RED));
            return new ArrayList<>();
        }
        if ((tekKisi || kendiniHaric) && s instanceof Player ben) liste.remove(ben);
        if (tekKisi && liste.size() > 1) liste = new ArrayList<>(liste.subList(0, 1));
        if (liste.isEmpty()) {
            s.sendMessage(Component.text("Seçiciye uyan oyuncu yok: " + arg, NamedTextColor.RED));
        }
        return liste;
    }

    /** Tek oyuncu: seçici birden fazla kişiye uyarsa komutu yazana en yakın olanı alır (kendisi hariç). */
    static Player tekli(CommandSender s, String arg) {
        List<Player> liste = coklu(s, arg, true);
        if (liste.isEmpty()) return null;
        if (liste.size() == 1 || !(s instanceof Player ben)) return liste.get(0);
        Player enYakin = null;
        double enAz = Double.MAX_VALUE;
        for (Player p : liste) {
            if (!p.getWorld().equals(ben.getWorld())) continue;
            double d = p.getLocation().distanceSquared(ben.getLocation());
            if (d < enAz) {
                enAz = d;
                enYakin = p;
            }
        }
        return enYakin != null ? enYakin : liste.get(0);
    }

    /** Tab tamamlama için oyuncu adları + seçiciler. */
    static List<String> oneriler() {
        List<String> l = new ArrayList<>(List.of("@a", "@p", "@r", "@s"));
        for (Player p : Bukkit.getOnlinePlayers()) l.add(p.getName());
        return l;
    }
}
