package tr.efe.polis;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;

/**
 * 1.9'daki /mesai kaldırıldı. Girişte eski üniforma eşyaları silinir;
 * mesaideyken sunucu kapandıysa mesai.yml'de sakli gerçek zırh geri verilir.
 * Dosyada kimse kalmayınca mesai.yml.eski olarak kenara alınır.
 */
public final class EskiMesaiGocu implements Listener {

    private final PolisPlugin plugin;
    private final File dosya;
    private final NamespacedKey anahtar;

    public EskiMesaiGocu(PolisPlugin plugin) {
        this.plugin = plugin;
        this.dosya = new File(plugin.getDataFolder(), "mesai.yml");
        this.anahtar = new NamespacedKey(plugin, "uniforma");
    }

    public void acilis() {
        for (Player p : Bukkit.getOnlinePlayers()) isle(p);
    }

    @EventHandler
    public void onGir(PlayerJoinEvent e) {
        isle(e.getPlayer());
    }

    private boolean uniforma(ItemStack is) {
        return is != null && is.hasItemMeta() && is.getItemMeta().getPersistentDataContainer().has(anahtar, PersistentDataType.STRING);
    }

    private void isle(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] zirh = inv.getArmorContents();
        for (int i = 0; i < zirh.length; i++) if (uniforma(zirh[i])) zirh[i] = null;
        ItemStack[] depo = inv.getStorageContents();
        boolean degisti = false;
        for (int i = 0; i < depo.length; i++) {
            if (uniforma(depo[i])) {
                depo[i] = null;
                degisti = true;
            }
        }
        if (degisti) inv.setStorageContents(depo);
        if (uniforma(inv.getItemInOffHand())) inv.setItemInOffHand(null);

        if (dosya.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(dosya);
            String yol = "zirh." + p.getUniqueId();
            if (yml.getConfigurationSection(yol) != null) {
                for (int i = 0; i < 4 && i < zirh.length; i++) {
                    ItemStack sakli = yml.getItemStack(yol + "." + i);
                    if (sakli == null) continue;
                    if (zirh[i] == null || zirh[i].getType() == Material.AIR) zirh[i] = sakli;
                    else Gozalti.ver(p, sakli);
                }
                yml.set(yol, null);
                plugin.mesaj(p, "Üniforma kaldırıldı; mesaideyken saklanan zırhın geri verildi.", net.kyori.adventure.text.format.NamedTextColor.YELLOW);
                try {
                    if (yml.getConfigurationSection("zirh") == null || yml.getConfigurationSection("zirh").getKeys(false).isEmpty()) {
                        dosya.renameTo(new File(dosya.getParentFile(), "mesai.yml.eski"));
                    } else {
                        yml.save(dosya);
                    }
                } catch (IOException ex) {
                    plugin.getLogger().warning("mesai.yml kaydedilemedi: " + ex.getMessage());
                }
            }
        }
        inv.setArmorContents(zirh);
    }
}
