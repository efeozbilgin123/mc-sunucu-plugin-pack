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
        Dogrula.esit(IdamKurali.SEHPA_YUKSEKLIK, IdamKurali.yukseklik(0), "geri sayımda sehpanın üstünde");
        Dogrula.esit(IdamKurali.SEHPA_YUKSEKLIK, IdamKurali.yukseklik(99), "geri sayım bitmeden sehpada");
        Dogrula.esit(200, IdamKurali.SAHNE_SON, "sahne 10 saniye (asılı kalma 5 sn)");
        // Gıyabi idam: op'un başının üstündeki darağacı çiti (dy: ayaktan yukarı blok)
        Dogrula.esit(4, IdamKurali.ustteCit(dy -> dy == 4, dy -> dy < 4, 6), "4 blok yukarıdaki çit bulunur");
        Dogrula.esit(-1, IdamKurali.ustteCit(dy -> false, dy -> true, 6), "üstte çit yok");
        Dogrula.esit(-1, IdamKurali.ustteCit(dy -> dy == 5, dy -> dy != 3, 6), "araya dolu blok (tavan) girerse yok");
        Dogrula.esit(-1, IdamKurali.ustteCit(dy -> dy == 8, dy -> true, 6), "çok yukarıdaki sayılmaz");
        Dogrula.esit("Efe", IdamKurali.testHedefi(new String[]{"test"}, "Efe"), "isim yazılmazsa kendi görünümü");
        Dogrula.esit("Ahmet", IdamKurali.testHedefi(new String[]{"test", "Ahmet"}, "Efe"), "yazılan oyuncunun görünümü");
        // Asılma: sehpa devrilir, mahkum yukarıdan aşağı düşer, ip gerilince hafif esner, sonra sallanır
        Dogrula.dogru(IdamKurali.SEHPA_YUKSEKLIK + 1.8 < 3, "sehpadayken başı çite değmez (3 blok boşluk)");
        for (int t = 100; t < 104; t++) {
            Dogrula.dogru(IdamKurali.yukseklik(t + 1) < IdamKurali.yukseklik(t), "düşerken hep aşağı iner (t=" + t + ")");
        }
        Dogrula.dogru(IdamKurali.yukseklik(105) < IdamKurali.ASILI_YUKSEKLIK, "ip gerilince hafif esner");
        Dogrula.esit(IdamKurali.ASILI_YUKSEKLIK, IdamKurali.yukseklik(150), "sonra asılı kalır");
        double enAz = 9, enCok = 0;
        for (int t = 0; t <= IdamKurali.SAHNE_SON; t++) {
            enAz = Math.min(enAz, IdamKurali.yukseklik(t));
            if (t >= 101) enCok = Math.max(enCok, IdamKurali.yukseklik(t));
        }
        Dogrula.dogru(enAz > 0.25, "ayaklar hiç yere değmez");
        Dogrula.dogru(enCok < IdamKurali.SEHPA_YUKSEKLIK, "düştükten sonra hiç yukarı fırlamaz");
        Dogrula.esit(0f, IdamKurali.salinim(100), "düşerken dönme başlamaz");
        Dogrula.dogru(Math.abs(IdamKurali.salinim(130)) > 0.5, "sonra döner");
        Dogrula.dogru(Math.abs(IdamKurali.salinimKayma(103)) == 0 && IdamKurali.salinimKayma(99) == 0, "ip gerilmeden sarkaç yok");
        double enBuyuk = 0;
        for (int t = 104; t <= IdamKurali.SAHNE_SON; t++) enBuyuk = Math.max(enBuyuk, Math.abs(IdamKurali.salinimKayma(t)));
        Dogrula.dogru(enBuyuk > 0.1 && enBuyuk <= 0.25, "sarkaç gibi ileri geri sallanır (en fazla 0,25 blok)");
        Dogrula.dogru(Math.abs(IdamKurali.salinimKayma(190)) < enBuyuk, "sallanma giderek azalır");
        // Sehpa: geri sayımda dik, 100'de tekmelenip hızlanarak devrilir, yan yatar
        Dogrula.esit(0f, IdamKurali.sehpaAcisi(99), "geri sayımda sehpa dik");
        Dogrula.dogru(IdamKurali.sehpaAcisi(100) > 0 && IdamKurali.sehpaAcisi(100) < IdamKurali.sehpaAcisi(102), "devrilmeye başlar");
        Dogrula.esit(90f, IdamKurali.sehpaAcisi(104), "yan yatar");
        Dogrula.esit(90f, IdamKurali.sehpaAcisi(150), "yatık kalır");
        // Asılıyken kamera serbest: yalnızca çekiliş anında ya da yerinden oynatılınca ışınlanır
        Dogrula.dogru(IdamKurali.isinlaGerekli(0, 0), "sahne başında yerine konur");
        Dogrula.dogru(IdamKurali.isinlaGerekli(103, 0), "düşerken ışınlanarak iner");
        Dogrula.dogru(!IdamKurali.isinlaGerekli(50, 0.1), "beklerken her tick ışınlanmaz (bakış kilitlenmesin)");
        Dogrula.dogru(!IdamKurali.isinlaGerekli(130, 0.2), "sallanırken her tick ışınlanmaz");
        Dogrula.dogru(IdamKurali.isinlaGerekli(130, 0.5), "itilip yerinden oynarsa geri konur");
        double top = 0;
        for (int t = 100; t < 140; t++) top += IdamKurali.kaymaHizi(t);
        Dogrula.dogru(Math.abs(top - (IdamKurali.salinimKayma(140) - IdamKurali.salinimKayma(100))) < 1e-9, "hızların toplamı sarkaç kaymasını verir");
        // Yön: mahkum idamı yapan kişiye (polis / op) dönük asılır; sehpa da ona doğru devrilir
        Dogrula.esit(0f, IdamKurali.yuzYonu(0, 5, 123f), "uygulayan güneyde: güneye bakar");
        Dogrula.esit(90f, IdamKurali.yuzYonu(-5, 0, 123f), "uygulayan batıda: batıya bakar");
        Dogrula.esit(180f, Math.abs(IdamKurali.yuzYonu(0, -5, 123f)), "uygulayan kuzeyde: kuzeye bakar");
        Dogrula.esit(-90f, IdamKurali.yuzYonu(5, 0, 123f), "uygulayan doğuda: doğuya bakar");
        Dogrula.esit(123f, IdamKurali.yuzYonu(0.1, 0.2, 123f), "tam altındaysa yedek yön");
    }
}
