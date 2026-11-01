package tr.efe.bekci;

import java.util.UUID;

/** Mezar kuralları (saf). */
public final class MezarKurali {
    private MezarKurali() {}

    /** Sahibi, onu PvP'de öldüren, polis ve op her zaman; herkes ancak herkeseAcikMs dolunca. */
    public static boolean acabilir(UUID bakan, boolean polis, boolean op, UUID sahip, UUID olduren, long olusma, long simdi, long herkeseAcikMs) {
        if (bakan.equals(sahip) || bakan.equals(olduren) || polis || op) return true;
        return simdi - olusma >= herkeseAcikMs;
    }

    public static boolean silinmeli(long olusma, long simdi, long omurMs) {
        return simdi - olusma >= omurMs;
    }

    /** Silinmesine 1 saat (ya da daha az) kaldıysa ve henüz uyarılmadıysa. */
    public static boolean uyarmali(long olusma, long simdi, long omurMs, boolean uyarildi) {
        return !uyarildi && omurMs - (simdi - olusma) <= 3_600_000L;
    }
}
