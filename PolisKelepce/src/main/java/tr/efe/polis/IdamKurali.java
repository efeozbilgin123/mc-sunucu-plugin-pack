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
     * Sahne: 0-99 geri sayım (ayakta), 100'de ip bir anda çeker (3 tick, biraz fırlar, sonra oturur),
     * sonra asılı halde sönümlü sallanma; SAHNE_SON'da biter.
     */
    public static final int SAHNE_SON = 170;
    /** Asılıyken ayakların yerden yüksekliği (blok): çitin altında 3 blok boşlukta boyun çite dayanır. */
    public static final double ASILI_YUKSEKLIK = 1.25;

    /** Tick'e göre yerden yükseklik. */
    public static double yukseklik(int t) {
        if (t < 100) return 0;
        int d = t - 100;
        double tepe = ASILI_YUKSEKLIK + 0.1;
        if (d <= 2) return tepe * (d + 1) / 3.0;           // hızlı çekiş
        if (d < 8) return tepe - 0.1 * (d - 2) / 6.0;      // ipin esnemesi: hafifçe geri oturur
        return ASILI_YUKSEKLIK;
    }

    /** Asılıyken gövdenin dönmesi (derece): çekildiği an 0, sonra azalarak sallanır. */
    public static float salinim(int t) {
        if (t <= 100) return 0f;
        double d = t - 100;
        return (float) (14 * Math.exp(-d / 45.0) * Math.sin(d * 0.22));
    }

    /** Sarkaç gibi yana kayma (blok, bakış yönüne dik): en fazla 0,15. */
    public static double salinimKayma(int t) {
        if (t <= 100) return 0;
        double d = t - 100;
        return 0.15 * Math.exp(-d / 50.0) * Math.sin(d * 0.22 + Math.PI / 2) * Math.min(1.0, d / 4.0);
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
}
