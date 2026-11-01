import java.util.Objects;

/** Küçük doğrulama yardımcıları (JUnit yok; sunucuya gitmez). */
public final class Dogrula {
    private Dogrula() {}

    public static void esit(Object beklenen, Object gercek, String ad) {
        if (!Objects.equals(beklenen, gercek)) {
            throw new AssertionError(ad + ": beklenen <" + beklenen + "> ama <" + gercek + ">");
        }
    }

    public static void dogru(boolean kosul, String ad) {
        if (!kosul) throw new AssertionError(ad + ": yanlış");
    }
}
