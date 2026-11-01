import java.util.UUID;
import tr.efe.bekci.Inceleme;
import tr.efe.bekci.Islem;

public class IncelemeTest {
    public static void main(String[] a) {
        long simdi = 10_000_000L;
        Islem kir = new Islem(1, simdi - 12 * 60_000L, UUID.randomUUID(), "Ahmet", "world", 10, 64, -3,
                Islem.Tur.KIR, "minecraft:stone", "minecraft:air", null, 0, false);
        String s = Inceleme.satir(kir, simdi);
        Dogrula.dogru(s.contains("Ahmet KIRDI"), "fiil: " + s);
        Dogrula.dogru(s.contains("stone"), "blok adı: " + s);
        Dogrula.dogru(s.contains("(10 64 -3)"), "konum: " + s);
        Dogrula.dogru(s.contains("12 dk önce"), "zaman: " + s);
        Islem al = new Islem(2, simdi, UUID.randomUUID(), "Mehmet", "world", 1, 2, 3,
                Islem.Tur.AL, null, null, "diamond", 5, false);
        String s2 = Inceleme.satir(al, simdi);
        Dogrula.dogru(s2.contains("Mehmet ALDI 5 diamond"), "kaptan alma: " + s2);
        Dogrula.dogru(Inceleme.satir(new Islem(3, simdi, null, "Ali", "world", 0, 0, 0, Islem.Tur.KIR,
                "minecraft:oak_stairs[facing=east]", "minecraft:air", null, 0, true), simdi).contains("[geri alındı]"), "işaret");
    }
}
