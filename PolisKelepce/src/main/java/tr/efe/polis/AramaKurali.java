package tr.efe.polis;

/**
 * Üst arama ekranı (54 yuva) düzeni:
 * 0-26 ana envanter (9-35), 27-35 hızlı erişim (0-8), 45-48 zırh (bot, pantolon, göğüslük, kask),
 * 50 sol el, 53 ender sandığı düğmesi. Diğer yuvalar boş.
 */
public final class AramaKurali {
    private AramaKurali() {}

    public static final int ZIRH_BOT = 100;
    public static final int ZIRH_KASK = 103;
    public static final int SOL_EL = 104;
    public static final int ENDER_DUGME = -2;
    public static final int GERI_DUGME = -3;

    /** Ekran yuvası -> gerçek envanter yuvası (0-35), zırh (100-103), sol el (104), düğme ya da -1. */
    public static int gercekYuva(int ekranYuvasi) {
        if (ekranYuvasi >= 0 && ekranYuvasi <= 26) return ekranYuvasi + 9;
        if (ekranYuvasi >= 27 && ekranYuvasi <= 35) return ekranYuvasi - 27;
        if (ekranYuvasi >= 45 && ekranYuvasi <= 48) return ZIRH_BOT + (ekranYuvasi - 45);
        if (ekranYuvasi == 50) return SOL_EL;
        if (ekranYuvasi == 53) return ENDER_DUGME;
        return -1;
    }

    /** Ender görünümü: 0-26 ender sandığı yuvaları, 53 geri düğmesi. */
    public static int enderYuva(int ekranYuvasi) {
        if (ekranYuvasi >= 0 && ekranYuvasi <= 26) return ekranYuvasi;
        if (ekranYuvasi == 53) return GERI_DUGME;
        return -1;
    }
}
