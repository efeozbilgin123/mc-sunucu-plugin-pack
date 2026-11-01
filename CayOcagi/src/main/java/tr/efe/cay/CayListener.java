package tr.efe.cay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class CayListener implements Listener {

    private final CayPlugin plugin;
    private final CayItems items;
    private final Semaverler semaverler;
    /** Semaver anahtarı -> üstündeki yazı standı (kalıcı değil; yoksa yeniden kurulur). */
    private final Map<String, java.util.UUID> etiketler = new java.util.HashMap<>();
    private int saniye = 0;

    public CayListener(CayPlugin plugin, CayItems items, Semaverler semaverler) {
        this.plugin = plugin;
        this.items = items;
        this.semaverler = semaverler;
    }

    // ------------------------------------------------------------------
    // Semaver: su dolu kazan + altında yanan kamp ateşi
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        ItemStack elde = p.getInventory().getItemInMainHand();
        CayItems.Tur tur = items.turu(elde);

        // Taze yaprak yeşil boya olduğu için tabelaya vs. sürülmesin
        if (tur == CayItems.Tur.YAPRAK) {
            e.setUseItemInHand(Event.Result.DENY);
        }

        Block b = e.getClickedBlock();
        if (b == null) return;
        Material tip = b.getType();
        if (tip != Material.WATER_CAULDRON && tip != Material.CAULDRON) return;

        String anahtar = Semaverler.anahtar(b);

        // 1) Kuru çayı kazana atma
        if (tur == CayItems.Tur.KURU) {
            e.setCancelled(true);
            if (tip != Material.WATER_CAULDRON) {
                plugin.mesaj(p, "Önce kazana su koymalısın.", NamedTextColor.RED);
                return;
            }
            if (!yananKampAtesi(b.getRelative(BlockFace.DOWN))) {
                plugin.mesaj(p, "Kazanın hemen altında yanan bir kamp ateşi olmalı.", NamedTextColor.RED);
                return;
            }
            if (semaverler.var(anahtar)) {
                if (semaverler.hazir(anahtar)) {
                    plugin.mesaj(p, "Bu semaverde çay zaten hazır. Cam şişeyle doldur.", NamedTextColor.YELLOW);
                } else {
                    plugin.mesaj(p, "Bu semaver zaten demleniyor (" + semaverler.kalanSaniye(anahtar) + " sn).", NamedTextColor.YELLOW);
                }
                return;
            }
            eldenEksilt(p, elde);
            long sure = Math.max(1, plugin.getConfig().getLong("demlenme-suresi", 60));
            semaverler.baslat(anahtar, sure);
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 0.6f, 1.2f);
            plugin.mesaj(p, "Çay demlenmeye başladı! Yaklaşık " + sure + " saniye sonra hazır.", NamedTextColor.GOLD);
            return;
        }

        // Demlenen ya da hazır semavere su eklenmesin (sonsuz çay olmasın)
        if (semaverler.var(anahtar) && (elde.getType() == Material.WATER_BUCKET
                || (elde.getType() == Material.POTION && tur == null))) {
            e.setCancelled(true);
            plugin.mesaj(p, "Semaverde çay var, şimdi su ekleyemezsin.", NamedTextColor.YELLOW);
            return;
        }

        // 2) Hazır çayı cam şişeyle doldurma
        if (elde.getType() == Material.GLASS_BOTTLE && semaverler.var(anahtar)) {
            e.setCancelled(true); // vanilla su şişesi doldurmasın
            if (tip != Material.WATER_CAULDRON) {
                semaverler.kaldir(anahtar);
                return;
            }
            if (!semaverler.hazir(anahtar)) {
                p.sendActionBar(Component.text("Çay daha demleniyor... " + semaverler.kalanSaniye(anahtar) + " sn", NamedTextColor.GOLD));
                return;
            }
            eldenEksilt(p, elde);
            ver(p, items.olustur(CayItems.Tur.CAY, 1));

            BlockData data = b.getBlockData();
            if (data instanceof Levelled lv) {
                int yeni = lv.getLevel() - 1;
                if (yeni <= 0) {
                    b.setType(Material.CAULDRON);
                    semaverler.kaldir(anahtar);
                } else {
                    lv.setLevel(yeni);
                    b.setBlockData(lv);
                }
            }
            b.getWorld().playSound(b.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 0.9f);
            p.sendActionBar(Component.text("Afiyet olsun!", NamedTextColor.GOLD));
        }
    }

    /**
     * Semavere hiçbir yoldan su eklenmesin: sol el kova/şişe, yağmur, damlataş vb.
     * (Aksi halde tek kuru çayla sonsuz çay alınabilir.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKazanSeviye(CauldronLevelChangeEvent e) {
        Block b = e.getBlock();
        if (!semaverler.var(Semaverler.anahtar(b))) return;
        if (e.getNewLevel() > e.getOldLevel()) {
            e.setCancelled(true);
            if (e.getEntity() instanceof Player p) {
                plugin.mesaj(p, "Semaverde çay var, şimdi su ekleyemezsin.", NamedTextColor.YELLOW);
            }
        }
    }

    /** Her saniye çalışır: buhar efekti, sönük ateşte bekletme, hazır olunca haber. */
    public void tik() {
        long simdi = System.currentTimeMillis();
        saniye++;
        boolean yazili = plugin.getConfig().getBoolean("gorsel.semaver-yazisi", true);
        long toplam = Math.max(1, plugin.getConfig().getLong("demlenme-suresi", 60)) * 1000L;
        double mesafe = plugin.getConfig().getDouble("haber-mesafesi", 16);
        Iterator<Map.Entry<String, Long>> it = semaverler.iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> giris = it.next();
            String anahtar = giris.getKey();
            Block b = Semaverler.blok(anahtar);
            if (b == null) continue;
            World w = b.getWorld();
            if (!w.isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) continue;

            if (b.getType() != Material.WATER_CAULDRON) {
                it.remove();
                semaverler.haberSil(anahtar);
                continue;
            }

            Location ust = b.getLocation().add(0.5, 1.0, 0.5);
            if (yazili) etiketYaz(anahtar, b, Semaverler.demYazisi(giris.getValue() - simdi, toplam), simdi >= giris.getValue());
            if (simdi < giris.getValue()) {
                if (!yananKampAtesi(b.getRelative(BlockFace.DOWN))) {
                    giris.setValue(giris.getValue() + 1000L); // ateş sönükse bekle
                    continue;
                }
                w.spawnParticle(Particle.BUBBLE_POP, ust, 4, 0.2, 0.05, 0.2, 0.0);
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, ust, 2, 0.15, 0.0, 0.15, 0.01);
                if (saniye % 4 == 0) w.playSound(b.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.6f, 1.2f);
            } else {
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, ust, 1, 0.1, 0.0, 0.1, 0.01);
                if (semaverler.ilkKezHazir(anahtar)) {
                    w.playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.4f);
                    for (Player p : w.getPlayers()) {
                        if (p.getLocation().distanceSquared(b.getLocation()) <= mesafe * mesafe) {
                            plugin.mesaj(p, "Çay demlendi! Cam şişeyle semaverden doldurabilirsin.", NamedTextColor.GOLD);
                        }
                    }
                }
            }
        }
        // Semaveri biten ya da yazısı kapatılanların yazısını kaldır
        Iterator<Map.Entry<String, java.util.UUID>> ei = etiketler.entrySet().iterator();
        while (ei.hasNext()) {
            Map.Entry<String, java.util.UUID> g = ei.next();
            if (yazili && semaverler.var(g.getKey())) continue;
            org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(g.getValue());
            if (e != null) e.remove();
            ei.remove();
        }
    }

    private void etiketYaz(String anahtar, Block b, String yazi, boolean hazir) {
        Component ad = Component.text(yazi, hazir ? NamedTextColor.GREEN : NamedTextColor.GOLD);
        java.util.UUID id = etiketler.get(anahtar);
        org.bukkit.entity.Entity e = id != null ? org.bukkit.Bukkit.getEntity(id) : null;
        if (e instanceof org.bukkit.entity.ArmorStand st) {
            st.customName(ad);
            return;
        }
        org.bukkit.entity.ArmorStand st = b.getWorld().spawn(b.getLocation().add(0.5, 1.2, 0.5), org.bukkit.entity.ArmorStand.class, a -> {
            a.setMarker(true);
            a.setInvisible(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setPersistent(false); // kaydedilmez: restart sonrası sahipsiz yazı kalmaz
            a.setCustomNameVisible(true);
            a.customName(ad);
        });
        etiketler.put(anahtar, st.getUniqueId());
    }

    /** Eklenti kapanırken yazıları sil. */
    public void kapat() {
        for (java.util.UUID id : etiketler.values()) {
            org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        etiketler.clear();
    }

    // ------------------------------------------------------------------
    // Taze çay yaprağı: çimen / eğrelti kırınca şansa bağlı düşer
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();

        if (b.getType() == Material.WATER_CAULDRON || b.getType() == Material.CAULDRON) {
            semaverler.kaldir(Semaverler.anahtar(b));
            return;
        }

        if (e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Material m = b.getType();
        if (m != Material.SHORT_GRASS && m != Material.TALL_GRASS && m != Material.FERN && m != Material.LARGE_FERN) return;
        double sans = plugin.getConfig().getDouble("yaprak-sansi", 0.08);
        if (ThreadLocalRandom.current().nextDouble() < sans) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.3, 0.5), items.olustur(CayItems.Tur.YAPRAK, 1));
        }
    }

    // ------------------------------------------------------------------
    // Korumalar: özel eşyalar vanilla işlerde kullanılmasın
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (items.turu(e.getItem()) == CayItems.Tur.KURU) {
            e.setCancelled(true);
            plugin.mesaj(e.getPlayer(), "Kuru çay yenmez, semaverde demlenir.", NamedTextColor.YELLOW);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        CayItems.Tur tur = items.turu(e.getItemInHand());
        if (tur == CayItems.Tur.YAPRAK || tur == CayItems.Tur.KURU) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        ItemStack elde = e.getHand() == EquipmentSlot.OFF_HAND
                ? e.getPlayer().getInventory().getItemInOffHand()
                : e.getPlayer().getInventory().getItemInMainHand();
        if (items.turu(elde) == CayItems.Tur.YAPRAK) {
            e.setCancelled(true); // koyun boyamasın
        }
    }

    // Crafter bloğu (otomatik çalışma masası) da özel eşyaları vanilla tariflerde kullanmasın
    @EventHandler(ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent e) {
        if (e.getRecipe() instanceof Keyed k && k.getKey().getNamespace().equals(plugin.namespace())) return;
        if (!(e.getBlock().getState() instanceof org.bukkit.block.Crafter c)) return;
        for (ItemStack is : c.getInventory().getContents()) {
            if (items.turu(is) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        boolean ozelVar = false;
        for (ItemStack is : e.getInventory().getMatrix()) {
            if (items.turu(is) != null) {
                ozelVar = true;
                break;
            }
        }
        if (!ozelVar) return;
        Recipe r = e.getRecipe();
        boolean bizim = r instanceof Keyed k && k.getKey().getNamespace().equals(plugin.namespace());
        if (!bizim) {
            e.getInventory().setResult(null);
        }
    }

    // ------------------------------------------------------------------

    static boolean yananKampAtesi(Block b) {
        Material m = b.getType();
        if (m != Material.CAMPFIRE && m != Material.SOUL_CAMPFIRE) return false;
        return b.getBlockData() instanceof Lightable l && l.isLit();
    }

    private static void eldenEksilt(Player p, ItemStack elde) {
        if (p.getGameMode() == GameMode.CREATIVE) return;
        elde.setAmount(elde.getAmount() - 1);
        p.getInventory().setItemInMainHand(elde.getAmount() > 0 ? elde : null);
    }

    private static void ver(Player p, ItemStack item) {
        CayPlugin.envantereVer(p, item);
    }
}
