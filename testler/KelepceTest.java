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
        // Bilekler: vücudun iki yanında (bakış yönüne dik), 0.35 blok
        double[] sag = KelepceKurali.bilekOfseti(0f, true), sol = KelepceKurali.bilekOfseti(0f, false);
        Dogrula.dogru(Math.abs(Math.abs(sag[0]) - 0.35) < 1e-6 && Math.abs(sag[1]) < 1e-6, "güneye bakarken bilekler doğu-batı ekseninde");
        Dogrula.dogru(Math.abs(sag[0] + sol[0]) < 1e-6, "iki bilek simetrik");
        double[] d = KelepceKurali.bilekOfseti(90f, true);
        Dogrula.dogru(Math.abs(d[0]) < 1e-6 && Math.abs(Math.abs(d[1]) - 0.35) < 1e-6, "batıya bakarken bilekler kuzey-güney ekseninde");
        Dogrula.dogru(KelepceKurali.bilekYuksekligi(true) < KelepceKurali.bilekYuksekligi(false), "eğilince bilekler alçalır");
    }
}
