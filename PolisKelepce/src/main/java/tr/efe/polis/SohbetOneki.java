package tr.efe.polis;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Sohbette rol öneki: "[★ Polis] Efe: mesaj". Mesajın kendisine dokunmaz. */
public final class SohbetOneki implements Listener {

    private final PolisPlugin plugin;

    public SohbetOneki(PolisPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSohbet(AsyncChatEvent e) {
        String rol = plugin.etiket().onbellektekiRol(e.getPlayer().getUniqueId());
        if (rol == null) return;
        Component onek = Component.text(Etiket.rolOneki(rol, plugin.etiket().simgeli()), Etiket.rolRengi(rol));
        ChatRenderer asil = e.renderer();
        e.renderer((kaynak, isim, mesaj, izleyen) -> asil.render(kaynak, onek.append(isim), mesaj, izleyen));
    }
}
