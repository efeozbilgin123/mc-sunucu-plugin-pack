import tr.efe.polis.KelepceKurali;

/** Sürükleme: ışınlama yerine yumuşak çekme (takılıyor gibi görünmesin). */
public class KelepceTest {
    public static void main(String[] a) {
        Dogrula.esit(0.0, KelepceKurali.cekmeHizi(2.0, 2.5), "yakındayken çekilmez");
        Dogrula.dogru(KelepceKurali.cekmeHizi(3.0, 2.5) > 0, "biraz geride kalınca çekilir");
        Dogrula.dogru(KelepceKurali.cekmeHizi(6.0, 2.5) > KelepceKurali.cekmeHizi(3.0, 2.5), "uzaklaştıkça daha hızlı");
        Dogrula.esit(KelepceKurali.EN_FAZLA_HIZ, KelepceKurali.cekmeHizi(15.0, 2.5), "en fazla hız sınırı");
        Dogrula.dogru(KelepceKurali.isinlanmali(17, 16, 0), "çok uzaktaysa ışınlanır");
        Dogrula.dogru(KelepceKurali.isinlanmali(5, 16, 20), "1 saniyedir yaklaşamıyorsa (takıldı) ışınlanır");
        Dogrula.dogru(!KelepceKurali.isinlanmali(5, 16, 3), "kısa takılmada ışınlanmaz");
    }
}
