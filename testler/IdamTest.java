import tr.efe.polis.IdamKurali;

public class IdamTest {
    public static void main(String[] a) {
        long dk30 = 30 * 60_000L, gun = 24 * 3_600_000L;
        Dogrula.esit(dk30, IdamKurali.sure(null, dk30, gun), "süre yok: varsayılan");
        Dogrula.esit(2 * 3_600_000L, IdamKurali.sure("2sa", dk30, gun), "2 saat");
        Dogrula.esit(gun, IdamKurali.sure("3g", dk30, gun), "tavan 1 gün");
        Dogrula.esit(dk30, IdamKurali.sure("abc", dk30, gun), "okunamayan: varsayılan");
        Dogrula.esit(false, IdamKurali.bosYeterli(2), "2 blok yetmez");
        Dogrula.esit(true, IdamKurali.bosYeterli(3), "3 blok yeter");
        Dogrula.esit(true, IdamKurali.bosYeterli(5), "daha fazlası yeter");
        Dogrula.dogru(IdamKurali.devamEtmeli(true, true, false), "onay var, kelepçeli, canlı: sahne sürer");
        Dogrula.dogru(!IdamKurali.devamEtmeli(false, true, false), "/idam iptal: sahne durur");
        Dogrula.dogru(!IdamKurali.devamEtmeli(true, false, false), "kelepçe açıldı: sahne durur");
        Dogrula.dogru(!IdamKurali.devamEtmeli(true, true, true), "mahkum öldü: sahne durur");
        Dogrula.dogru(!IdamKurali.listeGorebilir(false, false, false), "sıradan oyuncu idam listesini göremez");
        Dogrula.dogru(IdamKurali.listeGorebilir(false, false, true), "polis görür");
        Dogrula.dogru(IdamKurali.listeGorebilir(true, false, false), "başkan görür");
        Dogrula.dogru(IdamKurali.listeGorebilir(false, true, false), "op görür");
        Dogrula.esit("hiç girmemiş", IdamKurali.cevrimdisiHata(false, false), "sunucuya hiç girmemiş oyuncu");
        Dogrula.esit("oyunda", IdamKurali.cevrimdisiHata(true, true), "oyundaki oyuncu: normal idam kullanılır");
        Dogrula.esit(null, IdamKurali.cevrimdisiHata(true, false), "oyunda olmayan bilinen oyuncu");
        // Sahne zaman çizelgesi (oyuncu ve manken sahnesi aynı)
        Dogrula.esit(0.0, IdamKurali.yukseklik(0), "geri sayımda yerde");
        Dogrula.esit(0.0, IdamKurali.yukseklik(99), "geri sayım bitmeden yerde");
        Dogrula.esit(0.5, IdamKurali.yukseklik(110), "kaldırılırken yarı yolda");
        Dogrula.esit(1.0, IdamKurali.yukseklik(130), "1 blok kaldırılmış");
        Dogrula.esit(0f, IdamKurali.salinim(119), "kaldırılırken sallanmaz");
        Dogrula.dogru(Math.abs(IdamKurali.salinim(125)) <= 8f && IdamKurali.salinim(125) != 0f, "asılıyken en fazla 8 derece sallanır");
        Dogrula.esit(160, IdamKurali.SAHNE_SON, "sahne 8 saniye");
        // Gıyabi idam: op'un başının üstündeki darağacı çiti (dy: ayaktan yukarı blok)
        Dogrula.esit(4, IdamKurali.ustteCit(dy -> dy == 4, dy -> dy < 4, 6), "4 blok yukarıdaki çit bulunur");
        Dogrula.esit(-1, IdamKurali.ustteCit(dy -> false, dy -> true, 6), "üstte çit yok");
        Dogrula.esit(-1, IdamKurali.ustteCit(dy -> dy == 5, dy -> dy != 3, 6), "araya dolu blok (tavan) girerse yok");
        Dogrula.esit(-1, IdamKurali.ustteCit(dy -> dy == 8, dy -> true, 6), "çok yukarıdaki sayılmaz");
        Dogrula.esit("Efe", IdamKurali.testHedefi(new String[]{"test"}, "Efe"), "isim yazılmazsa kendi görünümü");
        Dogrula.esit("Ahmet", IdamKurali.testHedefi(new String[]{"test", "Ahmet"}, "Efe"), "yazılan oyuncunun görünümü");
    }
}
