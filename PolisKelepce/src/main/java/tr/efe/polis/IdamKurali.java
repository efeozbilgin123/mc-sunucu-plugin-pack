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

    /** Sahne: 0-99 geri sayım, 100-119 kaldırılma, 120-159 sallanma, 160'ta son. */
    public static final int SAHNE_SON = 160;

    /** Tick'e göre yerden yükseklik (blok): geri sayımda 0, sonra 20 tick'te 1 bloğa çıkar. */
    public static double yukseklik(int t) {
        return t < 100 ? 0 : Math.min(1.0, (t - 100) * 0.05);
    }

    /** Asılıyken sağa sola dönme (derece); kaldırılma bitene kadar 0. */
    public static float salinim(int t) {
        return t < 120 ? 0f : (float) (8 * Math.sin(t * 0.3));
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
