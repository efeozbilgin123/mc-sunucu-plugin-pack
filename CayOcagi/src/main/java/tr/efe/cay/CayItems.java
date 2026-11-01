package tr.efe.cay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugin'in özel eşyaları. Hepsi vanilla materyal + isim + PDC etiketi,
 * bu yüzden resource pack gerekmez ve Geyser üzerinden Bedrock'ta da görünür.
 */
public final class CayItems {

    public enum Tur {
        YAPRAK, KURU, CAY, SEKERLI, FIDAN;

        public static Tur parse(String s) {
            if (s == null) return null;
            String n = s.trim().toLowerCase(java.util.Locale.ROOT)
                    .replace('ç', 'c').replace('ş', 's').replace('ı', 'i')
                    .replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o');
            return switch (n) {
                case "yaprak", "taze" -> YAPRAK;
                case "kuru", "kurucay" -> KURU;
                case "cay" -> CAY;
                case "sekerli", "sekerlicay" -> SEKERLI;
                case "fidan", "cayfidani" -> FIDAN;
                default -> null;
            };
        }
    }

    // Tavşan kanı çay rengi
    private static final Color CAY_RENGI = Color.fromRGB(0x9B1C0C);
    private static final Color SEKERLI_RENGI = Color.fromRGB(0xB5481E);

    private final NamespacedKey turKey;

    public CayItems(Plugin plugin) {
        this.turKey = new NamespacedKey(plugin, "tur");
    }

    public ItemStack olustur(Tur tur, int adet) {
        ItemStack item;
        switch (tur) {
            case YAPRAK -> {
                item = new ItemStack(Material.GREEN_DYE, adet);
                ItemMeta meta = item.getItemMeta();
                isimVer(meta, Tur.YAPRAK, "Taze Çay Yaprağı", NamedTextColor.GREEN,
                        "Fırında, tütsülükte ya da kamp", "ateşinde kurutarak kuru çay yap.");
                item.setItemMeta(meta);
            }
            case KURU -> {
                item = new ItemStack(Material.DRIED_KELP, adet);
                ItemMeta meta = item.getItemMeta();
                isimVer(meta, Tur.KURU, "Kuru Çay", NamedTextColor.GOLD,
                        "Su dolu kazana at.", "Kazanın altında yanan kamp ateşi olmalı.");
                item.setItemMeta(meta);
            }
            case CAY -> {
                item = new ItemStack(Material.POTION, adet);
                PotionMeta meta = (PotionMeta) item.getItemMeta();
                meta.setColor(CAY_RENGI);
                meta.addCustomEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 90, 0), true);
                meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 30, 0), true);
                isimVer(meta, Tur.CAY, "Çay", TextColor.color(0xC0392B),
                        "Tavşan kanı, taze demlenmiş.", "Şekerle birleştirip şekerli çay yapabilirsin.");
                item.setItemMeta(meta);
            }
            case SEKERLI -> {
                item = new ItemStack(Material.POTION, adet);
                PotionMeta meta = (PotionMeta) item.getItemMeta();
                meta.setColor(SEKERLI_RENGI);
                meta.addCustomEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 150, 0), true);
                meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 0), true);
                meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 5, 0), true);
                isimVer(meta, Tur.SEKERLI, "Şekerli Çay", TextColor.color(0xE67E22),
                        "İki şeker, tam kıvamında.");
                item.setItemMeta(meta);
            }
            case FIDAN -> {
                item = new ItemStack(Material.SWEET_BERRIES, adet);
                ItemMeta meta = item.getItemMeta();
                isimVer(meta, Tur.FIDAN, "Çay Fidanı", NamedTextColor.DARK_GREEN,
                        "Çimen ya da toprağa dik, çay çalısı olur.",
                        "Olgunlaşınca sağ tıkla: taze çay yaprağı.");
                item.setItemMeta(meta);
            }
            default -> throw new IllegalArgumentException("Bilinmeyen tür: " + tur);
        }
        return item;
    }

    /** Eşya bu plugin'e aitse türünü döndürür, değilse null. */
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
