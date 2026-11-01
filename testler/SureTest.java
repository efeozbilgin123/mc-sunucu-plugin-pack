import tr.efe.bekci.Sure;

public class SureTest {
    public static void main(String[] a) {
        Dogrula.esit(7_200_000L, Sure.oku("2sa"), "2 saat");
        Dogrula.esit(129_600_000L, Sure.oku("1g12sa"), "1 gün 12 saat");
        Dogrula.esit(1_800_000L, Sure.oku("30dk"), "30 dakika");
        Dogrula.esit(-1L, Sure.oku("x"), "anlaşılmaz");
        Dogrula.esit("12 dk", Sure.yaz(12 * 60_000L), "kısa yazım");
        Dogrula.esit("2 sa 5 dk", Sure.yaz(125 * 60_000L), "saat dakika");
        Dogrula.esit("1 g 3 sa", Sure.yaz(27 * 3_600_000L), "gün saat");
    }
}
