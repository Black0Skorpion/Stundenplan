package de.stundenplan.widget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Eigene Änderungen an Terminen (wie in der iOS-App):
 * einzelne Termine: „fällt aus“, anderer Raum, eigene Zeit – gespeichert als Schlüssel →
 * Teile mit „|“ getrennt: "A" (Ausfall), "R:raum", "Z:von-bis" (Minuten nach Mitternacht), "W0" (Wecker folgt neuer Zeit);
 * ganze Reihen (gleiche Veranstaltung/Art/Wochentag/Uhrzeit): eigene Zeit, gespeichert als Reihe → "von-bis" bzw. "von-bis-0".
 */
public final class Aenderungen {
    private Aenderungen() {}

    private static final String PREFS = "aenderungen";
    private static final String REIHEN = "zeitregeln";

    /** Ein gespeicherter Eintrag für einen einzelnen Termin */
    static final class Eintrag {
        boolean ausfall = false;
        String raum = null;
        int von = -1, bis = -1;
        boolean weckerFest = true;

        boolean leer() { return !ausfall && (raum == null || raum.isEmpty()) && von < 0; }

        static Eintrag lesen(Object w) {
            Eintrag e = new Eintrag();
            if (!(w instanceof String)) return e;
            for (String teil : ((String) w).split("\\|")) {
                if (teil.equals("A")) e.ausfall = true;
                else if (teil.equals("W0")) e.weckerFest = false;
                else if (teil.startsWith("R:") && teil.length() > 2) e.raum = teil.substring(2);
                else if (teil.startsWith("Z:")) {
                    String[] z = teil.substring(2).split("-");
                    try {
                        e.von = Integer.parseInt(z[0]);
                        e.bis = Integer.parseInt(z[1]);
                    } catch (Exception ignored) {}
                }
            }
            return e;
        }

        String text() {
            List<String> t = new ArrayList<>();
            if (ausfall) t.add("A");
            if (raum != null && !raum.trim().isEmpty()) t.add("R:" + raum.trim().replace("|", ""));
            if (von >= 0) {
                t.add("Z:" + von + "-" + bis);
                if (!weckerFest) t.add("W0");
            }
            return String.join("|", t);
        }
    }

    static List<Termin> anwenden(Context ctx, List<Termin> termine, boolean mitAusfaellen) {
        Map<String, ?> alle = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getAll();
        Map<String, ?> reihen = ctx.getSharedPreferences(REIHEN, Context.MODE_PRIVATE).getAll();
        List<Termin> aus = new ArrayList<>();
        for (Termin t : termine) {
            // erst die Zeit der ganzen Reihe, dann der einzelne Termin
            Object r = reihen.get(t.reihe());
            if (r instanceof String) {
                String[] z = ((String) r).split("-");
                try {
                    zeitSetzen(t, Integer.parseInt(z[0]), Integer.parseInt(z[1]), z.length < 3 || !z[2].equals("0"));
                } catch (Exception ignored) {}
            }
            Eintrag e = Eintrag.lesen(alle.get(t.schluessel()));
            if (e.von >= 0) zeitSetzen(t, e.von, e.bis, e.weckerFest);
            if (e.ausfall) t.ausfall = true;
            if (e.raum != null && !e.raum.equals(t.raum)) {
                t.alterRaum = t.raum;
                t.raum = e.raum;
            }
            if (mitAusfaellen || !t.ausfall) aus.add(t);
        }
        java.util.Collections.sort(aus, (a, b) -> Long.compare(a.start, b.start));
        return aus;
    }

    /** Termin auf eigene Uhrzeiten setzen; weckerFest: bei späterem Beginn bleibt der Wecker bei der ursprünglichen Zeit */
    static void zeitSetzen(Termin t, int von, int bis, boolean weckerFest) {
        if (t.originalStart == 0) {
            t.originalStart = t.start;
            t.originalEnde = t.ende;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t.originalStart);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.MINUTE, von);
        t.start = c.getTimeInMillis();
        c.add(Calendar.MINUTE, Math.max(bis, von + 5) - von);
        t.ende = c.getTimeInMillis();
        t.weckStart = weckerFest && t.start > t.originalStart ? t.originalStart : 0;
    }

    static Eintrag eintrag(Context ctx, Termin t) {
        return Eintrag.lesen(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getAll().get(t.schluessel()));
    }

    private static void schreiben(Context ctx, Termin t, Eintrag e) {
        SharedPreferences.Editor ed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (e.leer()) ed.remove(t.schluessel()); else ed.putString(t.schluessel(), e.text());
        ed.apply();
        neuPlanen(ctx);
    }

    /** Ausfall/Raum setzen (eine eigene Zeit bleibt erhalten) */
    static void setzen(Context ctx, Termin t, boolean ausfall, String raum) {
        Eintrag e = eintrag(ctx, t);
        e.ausfall = ausfall;
        e.raum = raum;
        schreiben(ctx, t, e);
    }

    /** Alles für diesen Termin zurücksetzen (Ausfall, Raum, eigene Zeit des Termins) */
    static void zuruecksetzen(Context ctx, Termin t) {
        schreiben(ctx, t, new Eintrag());
    }

    /** Eigene Zeit nur für diesen Termin; von < 0 entfernt sie */
    static void zeitEinzeln(Context ctx, Termin t, int von, int bis, boolean weckerFest) {
        Eintrag e = eintrag(ctx, t);
        e.von = von;
        e.bis = von < 0 ? -1 : bis;
        e.weckerFest = weckerFest;
        schreiben(ctx, t, e);
    }

    static boolean hatReihe(Context ctx, Termin t) {
        return ctx.getSharedPreferences(REIHEN, Context.MODE_PRIVATE).contains(t.reihe());
    }

    static boolean reiheWeckerFest(Context ctx, Termin t) {
        String w = ctx.getSharedPreferences(REIHEN, Context.MODE_PRIVATE).getString(t.reihe(), "");
        return !w.endsWith("-0") || w.split("-").length < 3;
    }

    /** Eigene Zeit für die ganze Reihe; von < 0 entfernt sie. Die Einzel-Zeit dieses Termins wird aufgehoben. */
    static void zeitReihe(Context ctx, Termin t, int von, int bis, boolean weckerFest) {
        SharedPreferences.Editor ed = ctx.getSharedPreferences(REIHEN, Context.MODE_PRIVATE).edit();
        if (von < 0) ed.remove(t.reihe());
        else ed.putString(t.reihe(), von + "-" + bis + (weckerFest ? "" : "-0"));
        ed.apply();
        Eintrag e = eintrag(ctx, t);
        e.von = -1;
        e.bis = -1;
        e.weckerFest = true;
        schreiben(ctx, t, e);
    }

    private static void neuPlanen(Context ctx) {
        // Widgets, Erinnerungen und Wecker übernehmen die Änderung sofort
        StundenplanWidget.alleAktualisieren(ctx);
        Erinnerungen.planen(ctx);
        Wecker.planen(ctx);
        LiveUpdate.planen(ctx);
        NichtStoeren.planen(ctx);
    }

    /** Raum, der gerade gespeichert ist (null = Original) */
    static String eigenerRaum(Termin t) {
        return t.alterRaum != null ? t.raum : null;
    }
}
