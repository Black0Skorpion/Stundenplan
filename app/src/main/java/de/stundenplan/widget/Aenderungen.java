package de.stundenplan.widget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Eigene Änderungen an einzelnen Terminen: „fällt aus“ oder anderer Raum.
 * Gespeichert als Schlüssel → "A" (Ausfall), "R:raum" oder "A|R:raum".
 */
public final class Aenderungen {
    private Aenderungen() {}

    private static final String PREFS = "aenderungen";

    static List<Termin> anwenden(Context ctx, List<Termin> termine, boolean mitAusfaellen) {
        Map<String, ?> alle = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getAll();
        List<Termin> aus = new ArrayList<>();
        for (Termin t : termine) {
            Object w = alle.get(t.schluessel());
            if (w instanceof String) {
                for (String teil : ((String) w).split("\\|")) {
                    if (teil.equals("A")) t.ausfall = true;
                    else if (teil.startsWith("R:") && teil.length() > 2 && !teil.substring(2).equals(t.raum)) {
                        t.alterRaum = t.raum;
                        t.raum = teil.substring(2);
                    }
                }
            }
            if (mitAusfaellen || !t.ausfall) aus.add(t);
        }
        return aus;
    }

    static void setzen(Context ctx, Termin t, boolean ausfall, String raum) {
        StringBuilder w = new StringBuilder();
        if (ausfall) w.append("A");
        if (raum != null && !raum.trim().isEmpty()) {
            if (w.length() > 0) w.append("|");
            w.append("R:").append(raum.trim().replace("|", ""));
        }
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (w.length() == 0) e.remove(t.schluessel()); else e.putString(t.schluessel(), w.toString());
        e.apply();
        // Widgets und Erinnerungen übernehmen die Änderung sofort
        StundenplanWidget.alleAktualisieren(ctx);
        Erinnerungen.planen(ctx);
        Wecker.planen(ctx);
        LiveUpdate.planen(ctx);
    }

    /** Raum, der gerade gespeichert ist (null = Original) */
    static String eigenerRaum(Termin t) {
        return t.alterRaum != null ? t.raum : null;
    }
}
