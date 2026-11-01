import tr.efe.bekci.GeriAlmaKurali;
import tr.efe.bekci.Islem.Tur;

public class GeriAlmaTest {
    public static void main(String[] a) {
        Dogrula.esit("minecraft:stone", GeriAlmaKurali.karar(Tur.KIR, "minecraft:stone", "minecraft:air", "minecraft:air"), "kırılan geri gelir");
        Dogrula.esit(null, GeriAlmaKurali.karar(Tur.KIR, "minecraft:stone", "minecraft:air", "minecraft:dirt"), "başkası koymuş: atla");
        Dogrula.esit("minecraft:stone", GeriAlmaKurali.karar(Tur.KIR, "minecraft:stone", "minecraft:air", "minecraft:cave_air"), "mağara havası da boş sayılır");
        Dogrula.esit("minecraft:air", GeriAlmaKurali.karar(Tur.KOY, "minecraft:air", "minecraft:tnt", "minecraft:tnt"), "koyduğu silinir");
        Dogrula.esit("minecraft:water[level=0]", GeriAlmaKurali.karar(Tur.KOY, "minecraft:water[level=0]", "minecraft:dirt", "minecraft:dirt"), "suyun içine koyduysa su geri gelir");
        Dogrula.esit(null, GeriAlmaKurali.karar(Tur.KOY, "minecraft:air", "minecraft:tnt", "minecraft:air"), "zaten kalkmış: atla");
        Dogrula.esit("minecraft:air", GeriAlmaKurali.karar(Tur.KOVA, "minecraft:air", "minecraft:lava", "minecraft:lava[level=0]"), "lav kaynağı silinir");
        Dogrula.esit(null, GeriAlmaKurali.karar(Tur.KOVA, "minecraft:air", "minecraft:lava", "minecraft:lava[level=3]"), "akan lav kaynak değil");
        Dogrula.esit("minecraft:air", GeriAlmaKurali.karar(Tur.ATES, "minecraft:air", "minecraft:fire", "minecraft:fire[age=3]"), "ateş söner");
        Dogrula.esit(null, GeriAlmaKurali.karar(Tur.AL, null, null, "minecraft:chest"), "sandık eşyası geri alınmaz");
        Dogrula.dogru(GeriAlmaKurali.buTickDevam(499, 3), "500 işlem ve 4 bölge yüklemesi altında devam");
        Dogrula.dogru(!GeriAlmaKurali.buTickDevam(500, 0), "tick başı 500 işlem sınırı");
        Dogrula.dogru(!GeriAlmaKurali.buTickDevam(10, 4), "tick başı 4 bölge yükleme sınırı (takılma olmasın)");
    }
}
