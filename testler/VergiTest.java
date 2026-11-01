import tr.efe.polis.CezaSistemi;

public class VergiTest {
    public static void main(String[] a) {
        Dogrula.esit("vergi", CezaSistemi.turEtiketi(true), "vergi etiketi");
        Dogrula.esit("ceza", CezaSistemi.turEtiketi(false), "ceza etiketi");
        Dogrula.esit(false, CezaSistemi.turOku(null), "eski kayıt ceza sayılır");
        Dogrula.esit(true, CezaSistemi.turOku("vergi"), "vergi okunur");
        Dogrula.esit(false, CezaSistemi.turOku("ceza"), "ceza okunur");
    }
}
