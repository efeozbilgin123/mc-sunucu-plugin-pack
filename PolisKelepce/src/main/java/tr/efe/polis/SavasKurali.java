package tr.efe.polis;

/** Savaştan çıkma kuralı (saf, Bukkit'e bağlı değil). */
public final class SavasKurali {
    private SavasKurali() {}

    /**
     * Oyundan çıkan oyuncu ölmeli mi?
     * Sadece bilerek çıkış (DISCONNECTED); bağlantı kopması, atılma, sunucu kapanışı öldürmez.
     * Kelepçeli için kelepçe kuralı, yaralı için yaralı kuralı geçerli.
     * Bedrock (Geyser) oyuncusunun bağlantısı kopsa da sunucuya DISCONNECTED gelir; ayırt edilemediği için Bedrock ölmez.
     */
    public static boolean olmeliMi(String cikisNedeni, boolean sunucuKapaniyor, boolean kelepceli, boolean yarali, boolean savasta,
                                   boolean bedrock) {
        return "DISCONNECTED".equals(cikisNedeni) && !sunucuKapaniyor && !kelepceli && !yarali && savasta && !bedrock;
    }
}
