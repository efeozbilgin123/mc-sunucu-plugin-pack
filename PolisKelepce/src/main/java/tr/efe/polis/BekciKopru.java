package tr.efe.polis;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/** Bekçi eklentisi kuruluysa onun kaydına yazar (derleme bağımlılığı yok; Bekçi yoksa sessizce geçer). */
final class BekciKopru {
    private BekciKopru() {}

    private static Method yontem;

    /** tur: Bekçi'deki işlem türü adı (ör. "ENDER_AL", "AL"). */
    static void kaydet(Player oyuncu, Location yer, String tur, String esya, int miktar) {
        try {
            Plugin b = Bukkit.getPluginManager().getPlugin("Bekci");
            if (b == null || !b.isEnabled()) return;
            if (yontem == null || yontem.getDeclaringClass() != b.getClass()) {
                yontem = b.getClass().getMethod("disKayit", UUID.class, String.class, Location.class, String.class, String.class, int.class);
            }
            yontem.invoke(null, oyuncu.getUniqueId(), oyuncu.getName(), yer, tur, esya, miktar);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Bekçi yok ya da sürümü farklı: polis kaydı yine tutuluyor
        }
    }
}
