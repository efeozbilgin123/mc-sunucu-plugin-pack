package tr.efe.bekci;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kap görüntüleri (saf). Aynı kabı birden fazla kişi açık tutarken ve huni çalışırken
 * yapılan değişikliğin doğru kişiye yazılması için her değişiklikte herkesin görüntüsü tazelenir.
 */
public final class KapIzleyici {

    private record Acik(String kap, Map<String, Integer> sayim) {}

    private final Map<UUID, Acik> acik = new HashMap<>();

    public void ac(UUID izleyen, String kap, Map<String, Integer> sayim) {
        acik.put(izleyen, new Acik(kap, new HashMap<>(sayim)));
    }

    public boolean izliyor(UUID izleyen) {
        return acik.containsKey(izleyen);
    }

    public boolean izleniyor(String kap) {
        for (Acik a : acik.values()) if (a.kap().equals(kap)) return true;
        return false;
    }

    /** İzleyen kabı değiştirdi: onun farkı döner, aynı kaptaki herkesin görüntüsü yeni sayıma çekilir. */
    public Map<String, Integer> degisti(UUID izleyen, Map<String, Integer> yeni) {
        Acik a = acik.get(izleyen);
        if (a == null) return Map.of();
        Map<String, Integer> fark = KapFarki.fark(a.sayim(), yeni);
        disaridan(a.kap(), yeni);
        return fark;
    }

    /** Kap dışarıdan (huni, başka kaynak) değişti: kimseye yazmadan görüntüleri tazele. */
    public void disaridan(String kap, Map<String, Integer> yeni) {
        for (Map.Entry<UUID, Acik> g : acik.entrySet()) {
            if (g.getValue().kap().equals(kap)) g.setValue(new Acik(kap, new HashMap<>(yeni)));
        }
    }

    /** Kapatınca son fark döner, izleme biter. */
    public Map<String, Integer> kapat(UUID izleyen, Map<String, Integer> son) {
        Acik a = acik.remove(izleyen);
        return a == null ? Map.of() : KapFarki.fark(a.sayim(), son);
    }

    public void unut(UUID izleyen) {
        acik.remove(izleyen);
    }
}
