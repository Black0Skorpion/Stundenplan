package de.stundenplan.widget;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * Eigene Termine (Plus im Plan): z. B. Lerngruppe, Sprechstunde, Klausur – einmalig oder wiederkehrend.
 * Getrennt vom PULS-/Datei-Stundenplan gespeichert, werden beim Abgleich nicht überschrieben und nicht mitgeteilt.
 */
final class EigeneTermine {
    private EigeneTermine() {}

    static final String DATEI = "eigene-termine.json";
    static final String[] ARTEN = {"Termin", "Lerngruppe", "Sprechstunde", "Klausur", "Vorlesung", "Übung", "Tutorium", "Seminar", "Sport", "Arbeit"};

    static final class Eintrag {
        String id = UUID.randomUUID().toString();
        String titel = "", typ = "Termin", raum = "";
        long start, ende;
        /** 0 = einmalig, 7 = wöchentlich, 14 = alle zwei Wochen */
        int abstandTage = 0;
        /** Letzter Tag der Wiederholung (0 = ein Jahr) */
        long bis = 0;
    }

    static List<Eintrag> alle(Context ctx) {
        List<Eintrag> aus = new ArrayList<>();
        String s = PulsDienst.lesenText(ctx, DATEI);
        if (s == null) return aus;
        try {
            JSONArray l = new JSONArray(s);
            for (int i = 0; i < l.length(); i++) {
                JSONObject o = l.getJSONObject(i);
                Eintrag e = new Eintrag();
                e.id = o.optString("id", e.id);
                e.titel = o.optString("titel");
                e.typ = o.optString("typ", "Termin");
                e.raum = o.optString("raum");
                e.start = o.getLong("start");
                e.ende = o.getLong("ende");
                e.abstandTage = o.optInt("abstand", 0);
                e.bis = o.optLong("bis", 0);
                aus.add(e);
            }
        } catch (Exception ignored) {}
        return aus;
    }

    static void speichern(Context ctx, List<Eintrag> alle) {
        JSONArray l = new JSONArray();
        try {
            for (Eintrag e : alle) {
                l.put(new JSONObject().put("id", e.id).put("titel", e.titel).put("typ", e.typ).put("raum", e.raum)
                        .put("start", e.start).put("ende", e.ende).put("abstand", e.abstandTage).put("bis", e.bis));
            }
        } catch (Exception ignored) {}
        PulsDienst.schreibenText(ctx, DATEI, l.toString());
        PulsDienst.nachAenderung(ctx);
    }

    /** Alle einzelnen Termine (Wiederholungen ausgerollt, höchstens ein Jahr) */
    static List<Termin> termine(Context ctx) {
        List<Termin> aus = new ArrayList<>();
        for (Eintrag e : alle(ctx)) aus.addAll(einzelne(e));
        return aus;
    }

    static List<Termin> einzelne(Eintrag e) {
        List<Termin> aus = new ArrayList<>();
        long dauer = Math.max(5 * 60_000L, e.ende - e.start);
        if (e.abstandTage <= 0) {
            aus.add(new Termin(e.start, e.start + dauer, e.titel, e.typ, e.raum));
            return aus;
        }
        long grenze = e.start + 366 * 86400_000L;
        if (e.bis > 0) grenze = Math.min(grenze, MensaDaten.plusTage(MensaDaten.tagesBeginn(e.bis), 1));
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(e.start);
        while (c.getTimeInMillis() < grenze && aus.size() < 200) {
            long s = c.getTimeInMillis();
            aus.add(new Termin(s, s + dauer, e.titel, e.typ, e.raum));
            c.add(Calendar.DAY_OF_MONTH, e.abstandTage);
        }
        return aus;
    }
}
