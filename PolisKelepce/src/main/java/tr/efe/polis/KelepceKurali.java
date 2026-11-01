package tr.efe.polis;

/** Sürükleme kuralları (saf): mahkum ışınlanmak yerine polisin arkasından yumuşakça çekilir. */
public final class KelepceKurali {
    private KelepceKurali() {}

    /** Tick başına en fazla çekme hızı (blok/tick); koşan polise yetişir ama fırlamaz. */
    public static final double EN_FAZLA_HIZ = 0.6;
    /** Bu kadar tick yaklaşamazsa (köşeye takıldı) ışınlanır. */
    public static final int TAKILMA_TIK = 20;

    /** Mahkum polisten bu kadar uzaktayken uygulanacak çekme hızı; yakınsa 0. */
    public static double cekmeHizi(double mesafe, double cekme) {
        if (mesafe <= cekme) return 0;
        return Math.min(EN_FAZLA_HIZ, 0.15 + (mesafe - cekme) * 0.12);
    }

    /** Çok uzaksa ya da bir süredir yaklaşamıyorsa ışınla. */
    public static boolean isinlanmali(double mesafe, double isinlamaMesafesi, int takiliTik) {
        return mesafe > isinlamaMesafesi || takiliTik >= TAKILMA_TIK;
    }

}
