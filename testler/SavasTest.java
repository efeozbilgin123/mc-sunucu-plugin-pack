import tr.efe.polis.SavasKurali;

public class SavasTest {
    public static void main(String[] a) {
        Dogrula.esit(true, SavasKurali.olmeliMi("DISCONNECTED", false, false, false, true, false), "bilerek çıkan ölür");
        Dogrula.esit(false, SavasKurali.olmeliMi("TIMED_OUT", false, false, false, true, false), "bağlantı kopması");
        Dogrula.esit(false, SavasKurali.olmeliMi("KICKED", false, false, false, true, false), "atılma");
        Dogrula.esit(false, SavasKurali.olmeliMi("ERRONEOUS_STATE", false, false, false, true, false), "hatalı durum");
        Dogrula.esit(false, SavasKurali.olmeliMi("DISCONNECTED", true, false, false, true, false), "sunucu kapanıyor");
        Dogrula.esit(false, SavasKurali.olmeliMi("DISCONNECTED", false, true, false, true, false), "kelepçeli");
        Dogrula.esit(false, SavasKurali.olmeliMi("DISCONNECTED", false, false, true, true, false), "yaralı (Yarali halleder)");
        Dogrula.esit(false, SavasKurali.olmeliMi("DISCONNECTED", false, false, false, false, false), "savaşta değil");
        Dogrula.esit(false, SavasKurali.olmeliMi("DISCONNECTED", false, false, false, true, true),
                "Bedrock (Geyser kopmayı DISCONNECTED gösterir) ölmez");
    }
}
