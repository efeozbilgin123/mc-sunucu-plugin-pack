package tr.efe.polis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** Kelepçe ve anahtar: vanilla eşya + isim + PDC etiketi (resource pack gerekmez, Bedrock'ta görünür). */
public final class PolisItems {

    public enum Tur {
        KELEPCE, ANAHTAR;

        public static Tur parse(String s) {
            if (s == null) return null;
            String n = s.trim().toLowerCase(java.util.Locale.ROOT).replace('ç', 'c');
            return switch (n) {
                case "kelepce" -> KELEPCE;
                default -> null;
            };
        }
    }

    private final NamespacedKey turKey;
    private final NamespacedKey kelepceIdKey;

    public PolisItems(Plugin plugin) {
        this.turKey = new NamespacedKey(plugin, "tur");
        this.kelepceIdKey = new NamespacedKey(plugin, "kelepce_id");
    }

    /** Belirli bir kelepçeye ait anahtar. Sadece o kelepçeyi açar. */
    public ItemStack anahtar(String hedefIsim, String kelepceId) {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK, 1);
        ItemMeta meta = item.getItemMeta();
        isimVer(meta, Tur.ANAHTAR, "Kelepçe Anahtarı (" + hedefIsim + ")", NamedTextColor.YELLOW,
                "Sadece " + hedefIsim + " adlı oyuncunun kelepçesini açar.",
                "Polis: sağ tık bırak/götür, Shift + sağ tık aç.",
                "Polis değilsen: sağ tık aç.");
        meta.getPersistentDataContainer().set(kelepceIdKey, PersistentDataType.STRING, kelepceId);
        item.setItemMeta(meta);
        return item;
    }

    /** Anahtarın açtığı kelepçenin kimliği (anahtar değilse null). */
    public String anahtarKelepceId(ItemStack item) {
        if (turu(item) != Tur.ANAHTAR) return null;
        return item.getItemMeta().getPersistentDataContainer().get(kelepceIdKey, PersistentDataType.STRING);
    }

    public ItemStack olustur(Tur tur, int adet) {
        ItemStack item;
        ItemMeta meta;
        switch (tur) {
            case KELEPCE -> {
                item = new ItemStack(Material.LEAD, adet);
                meta = item.getItemMeta();
                isimVer(meta, tur, "Kelepçe", NamedTextColor.AQUA,
                        "Sadece polisler kullanabilir.",
                        "Oyuncuya sağ tık: tak. Seni takip eder.",
                        "Takınca kelepçe onun bileğine geçer, anahtarı sana gelir.");
            }
            default -> throw new IllegalArgumentException("Bilinmeyen tür: " + tur);
        }
        item.setItemMeta(meta);
        return item;
    }

    public Tur turu(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        String deger = item.getItemMeta().getPersistentDataContainer().get(turKey, PersistentDataType.STRING);
        if (deger == null) return null;
        try {
            return Tur.valueOf(deger);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void isimVer(ItemMeta meta, Tur tur, String isim, TextColor renk, String... aciklama) {
        meta.displayName(Component.text(isim, renk).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String satir : aciklama) {
            lore.add(Component.text(satir, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(turKey, PersistentDataType.STRING, tur.name());
    }
}
