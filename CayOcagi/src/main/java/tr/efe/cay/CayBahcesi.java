package tr.efe.cay;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Çay ekme: "Çay Fidanı" (görünüşü tatlı meyve) toprağa dikilince tatlı meyve çalısı olarak büyür.
 * Plugin bu çalıları "çay çalısı" olarak hatırlar; hasat edilince meyve yerine Taze Çay Yaprağı verir.
 * Vanilla davranış korunur: kemik tozu çalışır, Bedrock'ta da aynı şekilde dikilir/hasat edilir.
 */
public final class CayBahcesi implements Listener {

    private final CayItems items;
    private final Semaverler kayit;

    public CayBahcesi(CayItems items, Semaverler kayit) {
        this.items = items;
        this.kayit = kayit;
    }

    // Dikme: fidan vanilla gibi çalı olarak yerleşir, biz konumu çay çalısı olarak işaretleriz
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDik(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        String anahtar = Semaverler.anahtar(b);
        if (items.turu(e.getItemInHand()) != CayItems.Tur.FIDAN) {
            // Çay çalısı su/piston/patlamayla yok olmuşsa, oraya konan başka şey çay çalısı sayılmasın
            kayit.fidanSil(anahtar);
            return;
        }
        if (b.getType() == Material.SWEET_BERRY_BUSH) {
            kayit.fidanEkle(anahtar);
        }
    }

    // Hasat: meyve yerine taze çay yaprağı
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHasat(PlayerHarvestBlockEvent e) {
        Block b = e.getHarvestedBlock();
        if (!kayit.fidanVar(Semaverler.anahtar(b))) return;
        if (b.getType() != Material.SWEET_BERRY_BUSH) {
            kayit.fidanSil(Semaverler.anahtar(b));
            return;
        }
        int yas = b.getBlockData() instanceof Ageable a ? a.getAge() : 2;
        List<ItemStack> cikanlar = e.getItemsHarvested();
        cikanlar.clear();
        cikanlar.add(items.olustur(CayItems.Tur.YAPRAK, yapraklar(yas)));
    }

    // Kırma: fidan geri gelir, olgunsa yaprak da düşer (koruma plugin'lerinden sonra çalışsın)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKir(BlockBreakEvent e) {
        Block b = e.getBlock();
        String anahtar = Semaverler.anahtar(b);
        if (!kayit.fidanVar(anahtar)) return;
        kayit.fidanSil(anahtar);
        if (b.getType() != Material.SWEET_BERRY_BUSH) return;
        e.setDropItems(false);
        Player p = e.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.3, 0.5), items.olustur(CayItems.Tur.FIDAN, 1));
        int yas = b.getBlockData() instanceof Ageable a ? a.getAge() : 0;
        if (yas >= 2) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.3, 0.5), items.olustur(CayItems.Tur.YAPRAK, yapraklar(yas)));
        }
    }

    // Tilkiler çay yapraklarını yemesin, mob'lar çalıyı bozmasın
    @EventHandler(ignoreCancelled = true)
    public void onMob(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player) return;
        if (kayit.fidanVar(Semaverler.anahtar(e.getBlock()))) {
            e.setCancelled(true);
        }
    }

    // Fidan (tatlı meyve görünümlü) yenmesin
    @EventHandler(ignoreCancelled = true)
    public void onYe(PlayerItemConsumeEvent e) {
        if (items.turu(e.getItem()) == CayItems.Tur.FIDAN) {
            e.setCancelled(true);
        }
    }

    private static int yapraklar(int yas) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return yas >= 3 ? 2 + r.nextInt(2) : 1 + r.nextInt(2);
    }
}
