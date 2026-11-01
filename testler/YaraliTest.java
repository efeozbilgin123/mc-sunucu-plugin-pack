import tr.efe.polis.Yarali;

public class YaraliTest {
    public static void main(String[] a) {
        Dogrula.esit(30, Yarali.kalpAraligiTik(60000, 60000), "tam süre: yavaş kalp");
        Dogrula.esit(8, Yarali.kalpAraligiTik(0, 60000), "süre bitti: en hızlı");
        int orta = Yarali.kalpAraligiTik(30000, 60000);
        Dogrula.dogru(orta >= 15 && orta <= 23, "yarı süre ortada: " + orta);
        Dogrula.esit(8, Yarali.kalpAraligiTik(-5, 60000), "negatif kalan en hızlı");
        Dogrula.esit(false, Yarali.pvpMi(null), "vuran yok");
    }
}
