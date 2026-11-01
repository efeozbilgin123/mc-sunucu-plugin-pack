package tr.efe.bekci;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "30dk", "2sa", "1g12sa" gibi süreler. */
public final class Sure {
    private Sure() {}

    private static final Pattern DESEN = Pattern.compile("^(?:(\\d+)(?:g|gün|gun|d))?(?:(\\d+)(?:sa|saat|s|h))?(?:(\\d+)(?:dk|dakika|m))?$");

    /** Süre (ms); anlaşılmazsa -1. */
    public static long oku(String yazi) {
        Matcher m = DESEN.matcher(yazi.toLowerCase(Locale.ROOT));
        if (!m.matches() || (m.group(1) == null && m.group(2) == null && m.group(3) == null)) return -1;
        long dk = 0;
        if (m.group(1) != null) dk += Long.parseLong(m.group(1)) * 1440;
        if (m.group(2) != null) dk += Long.parseLong(m.group(2)) * 60;
        if (m.group(3) != null) dk += Long.parseLong(m.group(3));
        return dk > 0 ? dk * 60_000L : -1;
    }

    /** "1 g 3 sa", "2 sa 5 dk", "12 dk" */
    public static String yaz(long ms) {
        long dk = Math.max(0, ms) / 60_000L;
        long g = dk / 1440, sa = (dk % 1440) / 60, d = dk % 60;
        if (g > 0) return g + " g " + sa + " sa";
        if (sa > 0) return sa + " sa " + d + " dk";
        return d + " dk";
    }
}
