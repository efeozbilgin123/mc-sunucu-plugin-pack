package tr.efe.polis;

/** İdam kuralları (saf). */
public final class IdamKurali {
    private IdamKurali() {}

    /** Sunucuya girememe süresi: yazılmadıysa ya da okunamazsa varsayılan, tavanı aşarsa tavan. */
    public static long sure(String arg, long varsayilanMs, long enFazlaMs) {
        long s = arg == null ? -1 : CezaSistemi.sureOku(arg);
        if (s <= 0) s = varsayilanMs;
        return Math.min(s, enFazlaMs);
    }

    /** Darağacı: çitin altında en az 3 blok boşluk (ayaklar yere değmesin). */
    public static boolean bosYeterli(int altBosBlok) {
        return altBosBlok >= 3;
    }

    /** İdam sahnesi sürsün mü: onay hâlâ duruyor (iptal edilmedi), mahkum hâlâ kelepçeli ve canlı. */
    public static boolean devamEtmeli(boolean onayVar, boolean kelepceli, boolean olu) {
        return onayVar && kelepceli && !olu;
    }

    /** /idam liste: sadece başkan, op ve polis. */
    public static boolean listeGorebilir(boolean baskan, boolean admin, boolean polis) {
        return baskan || admin || polis;
    }

    /** Gıyabi idam hedefi uygun mu: null = uygun; değilse sebep ("hiç girmemiş" / "oyunda"). */
    public static String cevrimdisiHata(boolean bilinen, boolean oyunda) {
        if (!bilinen) return "hiç girmemiş";
        if (oyunda) return "oyunda";
        return null;
    }

    /**
     * Sahne: 0-99 geri sayım (sehpanın üstünde), 100'de sehpa tekmelenip devrilir, mahkum
     * yukarıdan aşağı düşer, ip gerilince hafif esner, sonra sarkaç gibi sallanıp yavaşça döner;
     * SAHNE_SON'da biter.
     */
    public static final int SAHNE_SON = 200;
    /** Sehpanın yüksekliği = geri sayımda ayakların yerden yüksekliği (blok). */
    public static final double SEHPA_YUKSEKLIK = 1.0;
    /** Asılıyken ayakların yerden yüksekliği (blok). */
    public static final double ASILI_YUKSEKLIK = 0.4;
    /** Düşüş kaç tick sürer (Minecraft yerçekimine yakın). */
    public static final int DUSUS_TIK = 4;
    private static final double[] ESNEME = {-0.09, -0.04, 0.02, 0.01};

    /** Tick'e göre ayakların yerden yüksekliği. */
    public static double yukseklik(int t) {
        if (t < 100) return SEHPA_YUKSEKLIK;
        int d = t - 100;
        if (d <= DUSUS_TIK) {
            double k = (double) d / DUSUS_TIK;
            return SEHPA_YUKSEKLIK - (SEHPA_YUKSEKLIK - ASILI_YUKSEKLIK) * k * k; // serbest düşüş: hızlanarak
        }
        int e = d - DUSUS_TIK - 1;
        if (e < ESNEME.length) return ASILI_YUKSEKLIK + ESNEME[e]; // ip gerilir, hafif esneyip oturur
        return ASILI_YUKSEKLIK;
    }

    /** Sallanma ip gerildiği an başlar. */
    public static final int SALINIM_BASLA = 100 + DUSUS_TIK;

    /** Asılıyken gövdenin kendi etrafında yavaşça dönmesi (derece). */
    public static float salinim(int t) {
        if (t <= SALINIM_BASLA) return 0f;
        double d = t - SALINIM_BASLA;
        return (float) (22 * Math.exp(-d / 90.0) * Math.sin(d * 0.07));
    }

    /**
     * Sarkaç gibi ileri-geri kayma (blok, bakış yönünde): ~1,2 blok ipte bir gidip gelme ≈ 2,2 sn,
     * sehpa öne tekmelendiği için öne doğru başlar, giderek azalır.
     */
    public static double salinimKayma(int t) {
        if (t <= SALINIM_BASLA) return 0;
        double d = t - SALINIM_BASLA;
        return 0.22 * Math.exp(-d / 80.0) * Math.sin(d * 0.143);
    }

    /** Sehpanın devrilme açısı (derece, 0 = dik, 90 = yan yatmış); bu tick'te varılacak açı. */
    public static float sehpaAcisi(int t) {
        if (t < 100) return 0f;
        double k = Math.min(1.0, (t - 100 + 2) / 6.0);
        return (float) (90 * k * k);
    }

    /**
     * Ayaktan yukarı doğru ilk çitin yüksekliği (1..enFazla); araya dolu bir blok girerse ya da çit yoksa -1.
     * citMi(dy): o yükseklikteki blok çit mi; bosMu(dy): içinden geçilebilir mi.
     */
    public static int ustteCit(java.util.function.IntPredicate citMi, java.util.function.IntPredicate bosMu, int enFazla) {
        for (int dy = 1; dy <= enFazla; dy++) {
            if (citMi.test(dy)) return dy;
            if (!bosMu.test(dy)) return -1;
        }
        return -1;
    }

    /** /idam test [oyuncu]: kimin görünümüyle oynanacak (yazılmazsa komutu yazan). */
    public static String testHedefi(String[] args, String kendiIsim) {
        return args.length > 1 && !args[1].isBlank() ? args[1] : kendiIsim;
    }

    /** Düşüş ve esneme sırasında (yükseklik değişirken) bu tick'e kadar. */
    public static final int CEKILIS_SON = 100 + DUSUS_TIK + 4;

    /**
     * Asılı oyuncuyu bu tick ışınlamak gerekiyor mu? Her tick ışınlamak kamerayı kilitler;
     * bu yüzden yalnızca başta, ip çekilirken ya da 0.3 bloktan fazla yerinden oynadıysa.
     */
    public static boolean isinlaGerekli(int t, double sapma) {
        return t == 0 || (t >= 100 && t <= CEKILIS_SON) || sapma > 0.3;
    }

    /** Sarkaç kaymasının bu tick'teki değişimi (hız olarak verilir, kamerayı etkilemez). */
    public static double kaymaHizi(int t) {
        return salinimKayma(t + 1) - salinimKayma(t);
    }
}
