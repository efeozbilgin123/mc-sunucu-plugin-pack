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

    /**
     * Mezara girecek XP. Vanilla ölünce en fazla 100 XP düşürür (seviye×7, ≈7. seviye);
     * mezar oyuncunun toplam XP'sinin "oran" yüzdesini saklar, vanilladan az olmaz.
     */
    public static int mezarXp(int toplamXp, int vanillaDusen, int oranYuzde, boolean seviyeKorunuyor) {
        if (seviyeKorunuyor) return 0;
        long oranli = (long) Math.max(0, toplamXp) * Math.max(0, Math.min(100, oranYuzde)) / 100;
        return (int) Math.max(Math.max(0, vanillaDusen), oranli);
    }
}
