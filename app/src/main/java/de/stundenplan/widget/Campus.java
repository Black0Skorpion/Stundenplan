package de.stundenplan.widget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Die drei Standorte der Uni Potsdam. Die Nummer ist die erste Zahl der Raumnummer
 * („2.70.0.10“ → Golm). Einstellung „auto“ = Campus der laufenden/nächsten Veranstaltung.
 */
public final class Campus {
    private Campus() {}

    public static final String NEUES_PALAIS = "1", GOLM = "2", GRIEBNITZSEE = "3", AUTO = "auto";
    static final String[] ALLE = {GOLM, NEUES_PALAIS, GRIEBNITZSEE};

    static final String EINSTELLUNGEN = "campus";
    static final String WAHL = "wahl";
    static final String FAVORITEN = "favoriten";

    static String name(String c) {
        switch (c) {
            case NEUES_PALAIS: return "Am Neuen Palais";
            case GRIEBNITZSEE: return "Griebnitzsee";
            case GOLM: return "Golm";
            default: return "Campus " + c;
        }
    }

    static String kurz(String c) {
        return NEUES_PALAIS.equals(c) ? "Neues Palais" : name(c);
    }

    static boolean bekannt(String c) {
        return GOLM.equals(c) || NEUES_PALAIS.equals(c) || GRIEBNITZSEE.equals(c);
    }

    /** Haltestellen in Laufweite: { VBB-Nummer, Name } */
    static String[][] haltestellen(String c) {
        switch (c) {
            case NEUES_PALAIS:
                return new String[][]{{"900230133", "Campus Universität"}, {"900230132", "Neues Palais"}, {"900230007", "Park Sanssouci Bhf"}};
            case GRIEBNITZSEE:
                return new String[][]{{"900230003", "Griebnitzsee Bhf"}};
            default:
                return new String[][]{{"900220010", "Golm Bhf"}, {"900220365", "Science Park/Uni"}};
        }
    }

    static int mensaId(String c) {
        switch (c) {
            case NEUES_PALAIS: return 55;
            case GRIEBNITZSEE: return 62;
            default: return 61;
        }
    }

    static String mensaName(String c) {
        switch (c) {
            case NEUES_PALAIS: return "Mensa Neues Palais";
            case GRIEBNITZSEE: return "Mensa Griebnitzsee";
            default: return "Mensa Golm";
        }
    }

    /** Ungefähre Mitte (falls ein Haus keine Koordinate hat) */
    static double[] mittelpunkt(String c) {
        switch (c) {
            case NEUES_PALAIS: return new double[]{52.4007, 13.0112};
            case GRIEBNITZSEE: return new double[]{52.3932, 13.1285};
            default: return new double[]{52.4083, 12.9765};
        }
    }

    /** Campus eines Raums oder null */
    static String ausRaum(String raum) {
        String[] r = Lageplan.raumAufteilen(raum);
        return r != null && bekannt(r[0]) ? r[0] : null;
    }

    // ───── Einstellungen ─────

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(EINSTELLUNGEN, Context.MODE_PRIVATE);
    }

    /** "auto", "1", "2" oder "3" */
    static String wahl(Context ctx) {
        String w = prefs(ctx).getString(WAHL, AUTO);
        return AUTO.equals(w) || bekannt(w) ? w : AUTO;
    }

    static void setzeWahl(Context ctx, String w) {
        prefs(ctx).edit().putString(WAHL, w).apply();
    }

    /** Tatsächlich verwendeter Campus */
    static String aktuell(Context ctx) {
        String w = wahl(ctx);
        return AUTO.equals(w) ? automatisch(Daten.laden(ctx), System.currentTimeMillis()) : w;
    }

    /**
     * Laufende/nächste Veranstaltung heute → sonst die letzte von heute (bis 2 Std. danach)
     * → sonst die nächste überhaupt → sonst der häufigste Campus → sonst Golm.
     */
    static String automatisch(List<Termin> termine, long jetzt) {
        List<Termin> mit = new ArrayList<>();
        for (Termin t : termine) if (!t.ausfall && ausRaum(t.raum) != null) mit.add(t);
        mit.sort((a, b) -> Long.compare(a.start, b.start));
        long heute = MensaDaten.tagesBeginn(jetzt), morgen = MensaDaten.plusTage(heute, 1);
        Termin letzterHeute = null;
        for (Termin t : mit) {
            if (t.start >= heute && t.start < morgen) {
                if (t.ende > jetzt) return ausRaum(t.raum);
                letzterHeute = t;
            }
        }
        if (letzterHeute != null && jetzt - letzterHeute.ende < 2 * 3600_000L) return ausRaum(letzterHeute.raum);
        for (Termin t : mit) if (t.start > jetzt) return ausRaum(t.raum);
        Map<String, Integer> anzahl = new HashMap<>();
        String bester = GOLM;
        int max = 0;
        for (Termin t : mit) {
            String c = ausRaum(t.raum);
            int n = anzahl.containsKey(c) ? anzahl.get(c) + 1 : 1;
            anzahl.put(c, n);
            if (n > max) { max = n; bester = c; }
        }
        return bester;
    }

    // ───── Priorisierte Linien ─────

    static List<String> favoriten(Context ctx) {
        List<String> l = new ArrayList<>();
        for (String s : prefs(ctx).getString(FAVORITEN, "").split(",")) {
            if (!s.trim().isEmpty()) l.add(s.trim());
        }
        return l;
    }

    static void setzeFavoriten(Context ctx, String text) {
        StringBuilder sb = new StringBuilder();
        for (String s : text.split("[,;\\s]+")) {
            if (s.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(s.trim().toUpperCase(java.util.Locale.GERMANY));
        }
        prefs(ctx).edit().putString(FAVORITEN, sb.toString()).apply();
    }

    static boolean istFavorit(List<String> favoriten, String linie) {
        for (String f : favoriten) if (f.equalsIgnoreCase(linie)) return true;
        return false;
    }
}
