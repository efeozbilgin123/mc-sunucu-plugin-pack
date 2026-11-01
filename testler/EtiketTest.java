import tr.efe.polis.Etiket;

public class EtiketTest {
    public static void main(String[] a) {
        Dogrula.esit("pk_gozalti", Etiket.takimAdi("gozalti", "polis", "mimar"), "durum rolü ezer");
        Dogrula.esit("pk_polis", Etiket.takimAdi(null, "polis", null), "sadece rol");
        String b = Etiket.takimAdi(null, "polis", "mimar");
        Dogrula.dogru(b != null && b.startsWith("pk_polis_"), "birleşik takım öneki: " + b);
        Dogrula.esit(b, Etiket.takimAdi(null, "polis", "mimar"), "kararlı ad");
        Dogrula.dogru(!b.equals(Etiket.takimAdi(null, "polis", "baska")), "farklı takım farklı ad");
        Dogrula.esit(null, Etiket.takimAdi(null, null, "mimar"), "rol ve durum yok");
        Dogrula.esit("[♛ Başkan] ", Etiket.rolOneki("baskan", true), "simgeli başkan");
        Dogrula.esit("[★ Polis] ", Etiket.rolOneki("polis", true), "simgeli polis");
        Dogrula.esit("[✚ Doktor] ", Etiket.rolOneki("doktor", true), "simgeli doktor");
        Dogrula.esit("[Doktor] ", Etiket.rolOneki("doktor", false), "simgesiz doktor");
    }
}
