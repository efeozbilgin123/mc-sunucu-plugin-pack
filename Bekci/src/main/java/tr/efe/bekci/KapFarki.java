package tr.efe.bekci;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Kap açılınca ve kapanınca alınan sayımların farkı. */
public final class KapFarki {
    private KapFarki() {}

    /** Pozitif = kondu, negatif = alındı; değişmeyenler yok. */
    public static Map<String, Integer> fark(Map<String, Integer> once, Map<String, Integer> sonra) {
        Set<String> hepsi = new HashSet<>(once.keySet());
        hepsi.addAll(sonra.keySet());
        Map<String, Integer> sonuc = new HashMap<>();
        for (String k : hepsi) {
            int d = sonra.getOrDefault(k, 0) - once.getOrDefault(k, 0);
            if (d != 0) sonuc.put(k, d);
        }
        return sonuc;
    }
}
