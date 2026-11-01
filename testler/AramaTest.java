import tr.efe.polis.AramaKurali;

public class AramaTest {
    public static void main(String[] a) {
        Dogrula.esit(9, AramaKurali.gercekYuva(0), "ilk üst yuva");
        Dogrula.esit(35, AramaKurali.gercekYuva(26), "son ana envanter");
        Dogrula.esit(0, AramaKurali.gercekYuva(27), "hızlı erişim başı");
        Dogrula.esit(8, AramaKurali.gercekYuva(35), "hızlı erişim sonu");
        Dogrula.esit(AramaKurali.ZIRH_BOT, AramaKurali.gercekYuva(45), "bot");
        Dogrula.esit(AramaKurali.ZIRH_KASK, AramaKurali.gercekYuva(48), "kask");
        Dogrula.esit(103, AramaKurali.ZIRH_KASK, "kask kodu");
        Dogrula.esit(100, AramaKurali.ZIRH_BOT, "bot kodu");
        Dogrula.esit(AramaKurali.SOL_EL, AramaKurali.gercekYuva(50), "sol el");
        Dogrula.esit(AramaKurali.ENDER_DUGME, AramaKurali.gercekYuva(53), "ender düğmesi");
        Dogrula.esit(-1, AramaKurali.gercekYuva(40), "boş yuva");
        Dogrula.esit(-1, AramaKurali.gercekYuva(54), "ekran dışı");
        Dogrula.esit(-1, AramaKurali.gercekYuva(-3), "negatif");
    }
}
