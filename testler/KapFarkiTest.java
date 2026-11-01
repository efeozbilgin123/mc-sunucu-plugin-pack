import java.util.Map;
import tr.efe.bekci.KapFarki;

public class KapFarkiTest {
    public static void main(String[] a) {
        Dogrula.esit(Map.of("elmas", -3), KapFarki.fark(Map.of("elmas", 5), Map.of("elmas", 2)), "3 elmas alındı");
        Dogrula.esit(Map.of("demir", 4), KapFarki.fark(Map.of(), Map.of("demir", 4)), "4 demir kondu");
        Dogrula.esit(Map.of(), KapFarki.fark(Map.of("a", 1), Map.of("a", 1)), "değişiklik yok");
        Dogrula.esit(Map.of("a", -1, "b", 2), KapFarki.fark(Map.of("a", 1), Map.of("b", 2)), "biri gitti biri geldi");
    }
}
