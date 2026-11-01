package tr.efe.bekci;

/** Geri alma kararı (saf): bir kayıt şimdiki bloğa göre uygulanmalı mı, uygulanacaksa ne konmalı? */
public final class GeriAlmaKurali {

    /**
     * SQL koşulu: aynı konumda bu kayıttan SONRA, BAŞKA bir oyuncunun (geri alınmamış) blok işlemi yoksa.
     * Geri almada başkasının sonraki işini bozmamak için ana sorguya AND ile eklenir.
     */
    public static final String BASKASI_SONRA_DEGISTIRMEDI = "NOT EXISTS (SELECT 1 FROM islem b WHERE b.dunya = islem.dunya"
            + " AND b.x = islem.x AND b.y = islem.y AND b.z = islem.z AND (b.zaman > islem.zaman OR (b.zaman = islem.zaman AND b.id > islem.id))"
            + " AND IFNULL(b.uuid, '') <> IFNULL(islem.uuid, '') AND b.geri = 0 AND b.tur IN ('KIR','KOY','KOVA','ATES'))";

    private GeriAlmaKurali() {}

    /** Konulacak blok verisi; null = atla (sonradan başkası değiştirmiş ya da bu tür geri alınmaz). */
    public static String karar(Islem.Tur tur, String kayitEski, String kayitYeni, String simdiki) {
        if (simdiki == null) return null;
        return switch (tur) {
            case KIR -> (simdiki.equals(kayitYeni) || bosMu(simdiki)) ? kayitEski : null;
            case KOY -> simdiki.equals(kayitYeni) ? (kayitEski != null ? kayitEski : "minecraft:air") : null;
            case KOVA -> ad(simdiki).equals(ad(kayitYeni)) && kaynakMi(simdiki) ? "minecraft:air" : null;
            case ATES -> (ad(simdiki).equals("fire") || ad(simdiki).equals("soul_fire")) ? "minecraft:air" : null;
            default -> null;
        };
    }

    private static boolean bosMu(String veri) {
        String a = ad(veri);
        return a.equals("air") || a.equals("cave_air") || a.equals("void_air");
    }

    /** Sıvı kaynağı: seviye bilgisi yok ya da level=0. */
    private static boolean kaynakMi(String veri) {
        int k = veri.indexOf("level=");
        return k < 0 || veri.startsWith("0", k + 6) && !Character.isDigit(veri.length() > k + 7 ? veri.charAt(k + 7) : 'x');
    }

    static String ad(String veri) {
        if (veri == null) return "";
        String s = veri.startsWith("minecraft:") ? veri.substring(10) : veri;
        int k = s.indexOf('[');
        return k >= 0 ? s.substring(0, k) : s;
    }

    public static final int TICK_ISLEM = 500;
    public static final int TICK_BOLGE_YUKLEME = 4;

    /** Bu tick'te bir kayıt daha işlensin mi: en fazla 500 işlem ve yüklü olmayan en fazla 4 bölge (sunucu takılmasın). */
    public static boolean buTickDevam(int islenen, int yuklenenBolge) {
        return islenen < TICK_ISLEM && yuklenenBolge < TICK_BOLGE_YUKLEME;
    }
}
