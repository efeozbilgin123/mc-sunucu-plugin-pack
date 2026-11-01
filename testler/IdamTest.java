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
        Dogrula.esit(170, IdamKurali.SAHNE_SON, "sahne 8,5 saniye (asılı kalma 3,5 sn)");
        // Gıyabi idam: op'un başının üstündeki darağacı çiti (dy: ayaktan yukarı blok)
        Dogrula.esit(4, IdamKurali.ustteCit(dy -> dy == 4, dy -> dy < 4, 6), "4 blok yukarıdaki çit bulunur");
        Dogrula.esit(-1, IdamKurali.ustteCit(dy -> false, dy -> true, 6), "üstte çit yok");
        Dogrula.esit(-1, IdamKurali.ustteCit(dy -> dy == 5, dy -> dy != 3, 6), "araya dolu blok (tavan) girerse yok");
        Dogrula.esit(-1, IdamKurali.ustteCit(dy -> dy == 8, dy -> true, 6), "çok yukarıdaki sayılmaz");
        Dogrula.esit("Efe", IdamKurali.testHedefi(new String[]{"test"}, "Efe"), "isim yazılmazsa kendi görünümü");
        Dogrula.esit("Ahmet", IdamKurali.testHedefi(new String[]{"test", "Ahmet"}, "Efe"), "yazılan oyuncunun görünümü");
        // Asılma: geri sayım bitince ip bir anda çeker (yavaş yükselme değil), ayaklar yerden kesilir, sönümlü sallanır
        Dogrula.dogru(IdamKurali.yukseklik(102) > 1.25, "3 tick içinde çekilip biraz fırlar");
        Dogrula.esit(IdamKurali.ASILI_YUKSEKLIK, IdamKurali.yukseklik(130), "asılı kalır (ayaklar yerden 1,25 blok)");
        Dogrula.dogru(IdamKurali.yukseklik(101) > 0.4, "çekilme hızlı (ilk tick'lerde)");
        Dogrula.esit(0f, IdamKurali.salinim(100), "çekildiği an sallanma başlamaz");
        Dogrula.dogru(Math.abs(IdamKurali.salinim(110)) > Math.abs(IdamKurali.salinim(150)) || Math.abs(IdamKurali.salinim(150)) < 3f,
                "sallanma giderek azalır");
        Dogrula.dogru(Math.abs(IdamKurali.salinimKayma(112)) <= 0.15 && IdamKurali.salinimKayma(99) == 0,
                "gövde de sarkaç gibi hafif kayar (en fazla 0,15 blok)");
        // Asılıyken kamera serbest: yalnızca çekiliş anında ya da yerinden oynatılınca ışınlanır
        Dogrula.dogru(IdamKurali.isinlaGerekli(0, 0), "sahne başında yerine konur");
        Dogrula.dogru(IdamKurali.isinlaGerekli(103, 0), "ip çekilirken ışınlanarak yükselir");
        Dogrula.dogru(!IdamKurali.isinlaGerekli(50, 0.1), "beklerken her tick ışınlanmaz (bakış kilitlenmesin)");
        Dogrula.dogru(!IdamKurali.isinlaGerekli(130, 0.2), "sallanırken her tick ışınlanmaz");
        Dogrula.dogru(IdamKurali.isinlaGerekli(130, 0.5), "itilip yerinden oynarsa geri konur");
        double top = 0;
        for (int t = 100; t < 140; t++) top += IdamKurali.kaymaHizi(t);
        Dogrula.dogru(Math.abs(top - (IdamKurali.salinimKayma(140) - IdamKurali.salinimKayma(100))) < 1e-9, "hızların toplamı sarkaç kaymasını verir");
    }
}
