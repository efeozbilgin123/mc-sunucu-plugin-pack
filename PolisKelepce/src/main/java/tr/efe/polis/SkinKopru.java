package tr.efe.polis;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** SkinsRestorer kuruluysa oyuncunun skinini (textures değeri + imza) alır. Derleme bağımlılığı yok; yoksa null. */
final class SkinKopru {
    private SkinKopru() {}

    /** {değer, imza} ya da null. Ağ/veritabanı erişimi yapabilir: ana iş parçacığında çağırma. */
    static String[] skin(UUID uuid, String isim) {
        try {
            Plugin sr = Bukkit.getPluginManager().getPlugin("SkinsRestorer");
            if (sr == null || !sr.isEnabled()) return null;
            ClassLoader cl = sr.getClass().getClassLoader();
            Class<?> saglayici = cl.loadClass("net.skinsrestorer.api.SkinsRestorerProvider");
            Class<?> apiTur = cl.loadClass("net.skinsrestorer.api.SkinsRestorer");
            Class<?> depoTur = cl.loadClass("net.skinsrestorer.api.storage.PlayerStorage");
            Class<?> ozellikTur = cl.loadClass("net.skinsrestorer.api.property.SkinProperty");
            Object api = saglayici.getMethod("get").invoke(null);
            Object depo = apiTur.getMethod("getPlayerStorage").invoke(api);
            Object sonuc = depoTur.getMethod("getSkinForPlayer", UUID.class, String.class).invoke(depo, uuid, isim);
            if (!(sonuc instanceof Optional<?> o) || o.isEmpty()) return null;
            Object ozellik = o.get();
            String deger = (String) ozellikTur.getMethod("getValue").invoke(ozellik);
            String imza = (String) ozellikTur.getMethod("getSignature").invoke(ozellik);
            return deger == null ? null : new String[]{deger, imza};
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
