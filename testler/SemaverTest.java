import tr.efe.cay.Semaverler;

public class SemaverTest {
    public static void main(String[] a) {
        Dogrula.esit("Demleniyor... %0", Semaverler.demYazisi(60000, 60000), "yeni başladı");
        Dogrula.esit("Demleniyor... %60", Semaverler.demYazisi(24000, 60000), "yüzde 60");
        Dogrula.esit("Çay hazır!", Semaverler.demYazisi(0, 60000), "bitti");
        Dogrula.esit("Çay hazır!", Semaverler.demYazisi(-500, 60000), "geçti");
    }
}
