package tr.efe.polis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Savaştan çıkma engeli: oyuncu oyuncuya vurunca ikisi de belirli süre "savaşta" sayılır.
 * Savaştayken bilerek çıkan ölür (eşyaları mezara gider). Bağlantısı kopan, atılan ölmez.
 */
public final class Savas implements Listener {

    private final PolisPlugin plugin;
    private final Map<UUID, Long> bitis = new HashMap<>();
    private final Map<UUID, UUID> sonRakip = new HashMap<>();
    private boolean kapaniyor = false;

    public Savas(PolisPlugin plugin) {
        this.plugin = plugin;
    }

    public void kapaniyor() {
        this.kapaniyor = true;
    }

    public boolean savastaMi(Player p) {
        Long b = bitis.get(p.getUniqueId());
        return b != null && b > System.currentTimeMillis();
    }

    public void etiketle(Player a, Player b) {
        long son = System.currentTimeMillis() + Math.max(1, plugin.getConfig().getLong("savas.sure", 15)) * 1000L;
        bitis.put(a.getUniqueId(), son);
        bitis.put(b.getUniqueId(), son);
        sonRakip.put(a.getUniqueId(), b.getUniqueId());
        sonRakip.put(b.getUniqueId(), a.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVurus(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player vurulan)) return;
        Entity v = e.getDamager();
        if (v instanceof Projectile pr && pr.getShooter() instanceof Player atan) v = atan;
        if (!(v instanceof Player vuran) || vuran.equals(vurulan)) return;
        etiketle(vuran, vurulan);
    }

    /** Her 5 tick: alt yazı. */
    public void tik() {
        long simdi = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = bitis.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> g = it.next();
            Player p = Bukkit.getPlayer(g.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            long kalan = g.getValue() - simdi;
            if (kalan > 0) {
                p.sendActionBar(Component.text("Savaştasın: " + (kalan / 1000 + 1) + " sn, çıkarsan ölürsün", NamedTextColor.RED));
            } else {
                it.remove();
                sonRakip.remove(g.getKey());
                p.sendActionBar(Component.text("Savaştan çıktın", NamedTextColor.GREEN));
            }
        }
    }

    @EventHandler
    public void onOlum(PlayerDeathEvent e) {
        bitis.remove(e.getEntity().getUniqueId());
        sonRakip.remove(e.getEntity().getUniqueId());
    }

    // LOWEST: kelepçe ve yaralı bilgisi Gozalti'nin çıkış işlemi silmeden önce okunmalı
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCik(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        boolean yarali = plugin.yarali().yaraliMi(p);
        boolean kelepceli = plugin.gozalti().kelepceliMi(p);
        boolean savasta = savastaMi(p);
        UUID rakip = sonRakip.remove(p.getUniqueId());
        bitis.remove(p.getUniqueId());
        boolean kapanis = kapaniyor || Bukkit.isStopping();
        if (kapanis) return; // restart'ta kimse ölmez, yaralı da ayakta kalır
        plugin.yarali().yaraliCikti(p);
        boolean bedrock = Kayitlar.bedrockMu(p.getUniqueId());
        if (!SavasKurali.olmeliMi(e.getReason().name(), false, kelepceli, yarali, savasta, bedrock)) {
            if (savasta && bedrock && !kelepceli && !yarali) {
                // Bedrock'ta bilerek çıkış ile bağlantı kopması ayırt edilemiyor: öldürme, ama kayda geçir ve rakibe haber ver
                plugin.kayit().log(p.getName() + " (Bedrock) savaştayken oyundan çıktı (ölmedi: bağlantı kopması olabilir)");
                Player r = rakip != null ? Bukkit.getPlayer(rakip) : null;
                if (r != null) plugin.mesaj(r, p.getName() + " savaşta oyundan düştü (Bedrock, bağlantı kopmuş olabilir).", NamedTextColor.YELLOW);
            }
            return;
        }
        p.setHealth(0);
        plugin.kayit().log(p.getName() + " savaştayken oyundan çıktı (öldü)");
        Player r = rakip != null ? Bukkit.getPlayer(rakip) : null;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p)) continue;
            boolean yakin = o.getWorld().equals(p.getWorld()) && o.getLocation().distance(p.getLocation()) <= 48;
            if (o.equals(r) || yakin) plugin.mesaj(o, p.getName() + " savaştan kaçmaya çalıştı ve öldü.", NamedTextColor.RED);
        }
    }
}
