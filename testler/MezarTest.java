import java.util.UUID;
import tr.efe.bekci.MezarKurali;

public class MezarTest {
    public static void main(String[] x) {
        UUID A = UUID.randomUUID(), K = UUID.randomUUID(), Y = UUID.randomUUID();
        long dk30 = 1_800_000L, gun = 86_400_000L;
        Dogrula.dogru(MezarKurali.acabilir(A, false, false, A, null, 0, 1, dk30), "sahibi açar");
        Dogrula.dogru(MezarKurali.acabilir(K, false, false, A, K, 0, 1, dk30), "öldüren açar");
        Dogrula.dogru(MezarKurali.acabilir(Y, true, false, A, null, 0, 1, dk30), "polis açar");
        Dogrula.dogru(!MezarKurali.acabilir(Y, false, false, A, K, 0, dk30 - 1, dk30), "yabancı 30 dk dolmadan açamaz");
        Dogrula.dogru(MezarKurali.acabilir(Y, false, false, A, null, 0, dk30, dk30), "30 dk sonra herkes");
        Dogrula.dogru(!MezarKurali.acabilir(Y, false, false, A, null, 0, 1, dk30), "PvP değilse öldüren yok, yabancı açamaz");
        Dogrula.dogru(MezarKurali.silinmeli(0, gun, gun), "24 saat doldu");
        Dogrula.dogru(!MezarKurali.silinmeli(0, gun - 1, gun), "henüz dolmadı");
        Dogrula.dogru(MezarKurali.uyarmali(0, gun - 3_600_000L, gun, false), "1 saat kala uyar");
        Dogrula.dogru(!MezarKurali.uyarmali(0, gun - 3_600_000L, gun, true), "bir kez uyar");
        Dogrula.dogru(!MezarKurali.uyarmali(0, gun - 3_600_001L, gun, false), "1 saatten fazla var");
        Dogrula.dogru(MezarKurali.acabilir(Y, false, true, A, K, 0, 1, dk30), "op her mezarı açar");
    }
}
