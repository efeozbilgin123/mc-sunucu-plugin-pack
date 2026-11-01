import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import tr.efe.bekci.Islem;
import tr.efe.bekci.Veritabani;

/** Gerçek SQLite ile (sürücü jar'ı varsa). */
public class VeritabaniTest {
    public static void main(String[] a) throws Exception {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.out.println("SQLite sürücüsü yok, atlandı");
            return;
        }
        File d = Files.createTempDirectory("bekci").resolve("kayit.db").toFile();
        Veritabani db = new Veritabani(Logger.getLogger("test"));
        Dogrula.dogru(db.ac(d), "açıldı");
        UUID u = UUID.randomUUID();
        db.ekle(new Islem(0, 1000, u, "Ahmet", "world", 1, 2, 3, Islem.Tur.KIR, "minecraft:stone", "minecraft:air", null, 0, false));
        db.ekle(new Islem(0, 2000, u, "Ahmet", "world", 1, 2, 3, Islem.Tur.KOY, "minecraft:air", "minecraft:dirt", null, 0, false));
        db.ekle(new Islem(0, 3000, null, "?", "world", 9, 9, 9, Islem.Tur.ATES, null, "minecraft:fire", null, 0, false));
        List<Islem> l = db.sorgu("uuid = ?", List.of(u.toString()), 10, 0).get();
        Dogrula.esit(2, l.size(), "Ahmet'in 2 işlemi");
        Dogrula.esit(Islem.Tur.KOY, l.get(0).tur(), "en yeni üstte");
        db.isaretle(List.of(l.get(0).id()));
        List<Islem> l2 = db.sorgu("geri = 0", List.of(), 10, 0).get();
        Dogrula.esit(2, l2.size(), "işaretlenen hariç 2");
        db.temizle(2500);
        List<Islem> l3 = db.sorgu("1=1", List.of(), 10, 0).get();
        Dogrula.esit(1, l3.size(), "2500'den eskiler silindi");
        Dogrula.esit(Islem.Tur.ATES, l3.get(0).tur(), "kalan ateş");
        db.kapat();
    }
}
