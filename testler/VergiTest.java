import tr.efe.polis.CezaSistemi;

public class VergiTest {
    public static void main(String[] a) {
        Dogrula.esit("vergi", CezaSistemi.turEtiketi(true), "vergi etiketi");
        Dogrula.esit("ceza", CezaSistemi.turEtiketi(false), "ceza etiketi");
        Dogrula.esit(false, CezaSistemi.turOku(null), "eski kayıt ceza sayılır");
        Dogrula.esit(true, CezaSistemi.turOku("vergi"), "vergi okunur");
        Dogrula.esit(false, CezaSistemi.turOku("ceza"), "ceza okunur");
        // Tab eki: kaç dakika önce yazıldığı değil, son ödemeye ne kadar kaldığı; "0dk" asla
        long dk = 60_000L, sa = 3_600_000L, gun = 86_400_000L;
        Dogrula.esit(" (vergi: 2g kaldı)", CezaSistemi.sekmeEki(true, false, 2 * gun), "vergi kalan süre");
        Dogrula.esit(" (borçlu: 5sa kaldı)", CezaSistemi.sekmeEki(false, false, 5 * sa + 10 * dk), "ceza kalan süre");
        Dogrula.esit(" (borçlu: 1dk kaldı)", CezaSistemi.sekmeEki(false, false, 20_000L), "bir dakikadan az: 1dk");
        Dogrula.esit(" (borçlu: GECİKTİ)", CezaSistemi.sekmeEki(false, true, 0), "geciken ceza");
        Dogrula.esit(" (vergi gecikti)", CezaSistemi.sekmeEki(true, true, 0), "geciken vergi");
        Dogrula.esit(" (borçlu)", CezaSistemi.sekmeEki(false, false, -1), "süresiz ceza");
    }
}
