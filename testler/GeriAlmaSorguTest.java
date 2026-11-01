import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import tr.efe.bekci.GeriAlmaKurali;
import tr.efe.bekci.Islem;
import tr.efe.bekci.Veritabani;

/** Geri alma, başkasının sonradan değiştirdiği konumu seçmemeli (gerçek SQLite). */
public class GeriAlmaSorguTest {
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
        UUID ahmet = UUID.randomUUID(), mehmet = UUID.randomUUID();
        // P: Ahmet kapıya taş koydu, Mehmet kırdı, sonra Mehmet aynı taşı kendi duvarı için koydu
        db.ekle(new Islem(0, 1000, ahmet, "Ahmet", "world", 1, 64, 1, Islem.Tur.KOY, "minecraft:air", "minecraft:cobblestone", null, 0, false));
        db.ekle(new Islem(0, 2000, mehmet, "Mehmet", "world", 1, 64, 1, Islem.Tur.KIR, "minecraft:cobblestone", "minecraft:air", null, 0, false));
        db.ekle(new Islem(0, 3000, mehmet, "Mehmet", "world", 1, 64, 1, Islem.Tur.KOY, "minecraft:air", "minecraft:cobblestone", null, 0, false));
        // Q: Ahmet taş kırdı, sonra kimse dokunmadı
        db.ekle(new Islem(0, 1500, ahmet, "Ahmet", "world", 5, 64, 5, Islem.Tur.KIR, "minecraft:stone", "minecraft:air", null, 0, false));
        // R: Ahmet koydu, sonra kendisi tekrar kırdı (kendi işi -> ikisi de seçilir)
        db.ekle(new Islem(0, 1200, ahmet, "Ahmet", "world", 9, 64, 9, Islem.Tur.KOY, "minecraft:air", "minecraft:dirt", null, 0, false));
        db.ekle(new Islem(0, 1300, ahmet, "Ahmet", "world", 9, 64, 9, Islem.Tur.KIR, "minecraft:dirt", "minecraft:air", null, 0, false));
        List<Islem> l = db.sorgu("isim = ? COLLATE NOCASE AND geri = 0 AND " + GeriAlmaKurali.BASKASI_SONRA_DEGISTIRMEDI,
                List.of("Ahmet"), 100, 0).get();
        Dogrula.esit(3, l.size(), "P hariç Ahmet'in 3 işlemi");
        for (Islem i : l) Dogrula.dogru(!(i.x() == 1), "P (Mehmet sonradan değiştirdi) seçilmedi");
        db.kapat();
    }
}
