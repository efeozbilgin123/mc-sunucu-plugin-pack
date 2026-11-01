import org.bukkit.Material;
import tr.efe.bekci.SandikBulucu;

public class SandikTest {
    public static void main(String[] x) {
        Dogrula.dogru(SandikBulucu.kapMi(Material.CHEST), "sandık kap");
        Dogrula.dogru(SandikBulucu.kapMi(Material.BARREL), "varil kap");
        Dogrula.dogru(SandikBulucu.kapMi(Material.SHULKER_BOX), "shulker kap");
        Dogrula.dogru(SandikBulucu.kapMi(Material.RED_SHULKER_BOX), "renkli shulker kap");
        Dogrula.dogru(SandikBulucu.kapMi(Material.COPPER_CHEST), "bakır sandık kap");
        Dogrula.dogru(!SandikBulucu.kapMi(Material.ENDER_CHEST), "ender sandığı hariç");
        Dogrula.dogru(!SandikBulucu.kapMi(Material.STONE), "taş değil");
        Dogrula.dogru(!SandikBulucu.kapMi(null), "null değil");
    }
}
