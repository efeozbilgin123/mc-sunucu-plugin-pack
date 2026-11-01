package tr.efe.bekci;

import java.util.UUID;

/** Bekçi kaydındaki tek işlem. */
public record Islem(long id, long zaman, UUID uuid, String isim, String dunya, int x, int y, int z, Tur tur,
                    String eski, String yeni, String esya, int miktar, boolean geriAlindi) {

    public enum Tur { KIR, KOY, KOVA, ATES, TNT, AL, KOY_KAP, KIR_ICERIK, ENDER_AL, ENDER_KOY, MEZAR_AL, MEZAR_KIR, MEZAR_SILINDI, GERI }

    /** Yeni kayıt (id veritabanında verilir). */
    public static Islem yeni(UUID uuid, String isim, String dunya, int x, int y, int z, Tur tur, String eski, String yeni, String esya, int miktar) {
        return new Islem(0, System.currentTimeMillis(), uuid, isim, dunya, x, y, z, tur, eski, yeni, esya, miktar, false);
    }
}
