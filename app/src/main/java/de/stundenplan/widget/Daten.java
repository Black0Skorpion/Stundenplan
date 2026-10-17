package de.stundenplan.widget;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lädt die Termine aus der Kalenderdatei (PULS-Auswahl oder Import). */
public final class Daten {
    private Daten() {}

    public static final String ICS_DATEI = "stundenplan.ics";

    /** Termine ohne ausgefallene (für Widgets und Erinnerungen) */
    public static List<Termin> laden(Context ctx) {
        return laden(ctx, false);
    }

    /** Mit eigenen Änderungen (Raum/Ausfall); ausgefallene nur mit mitAusfaellen */
    public static List<Termin> laden(Context ctx, boolean mitAusfaellen) {
        return Aenderungen.anwenden(ctx, ladenRoh(ctx), mitAusfaellen);
    }

    /** Rohtermine wie gespeichert (ohne akademisches Viertel) */
    static List<Termin> ladenRohOhneViertel(Context ctx) {
        File f = new File(ctx.getFilesDir(), ICS_DATEI);
        if (!f.exists()) return new ArrayList<>();
        try (InputStream in = new FileInputStream(f)) {
            return parseICS(lesen(in));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static List<Termin> ladenRoh(Context ctx) {
        return aufbereiten(ladenRohOhneViertel(ctx));
    }

    /** Zum Teilen: Rohzeiten, mit eigenem Raum, ohne ausgefallene Termine */
    static List<Termin> ladenRohMitAenderungen(Context ctx) {
        List<Termin> aus = new ArrayList<>();
        for (Termin roh : ladenRohOhneViertel(ctx)) {
            Termin angepasst = viertelAnpassen(roh);
            List<Termin> eins = new ArrayList<>();
            eins.add(angepasst);
            List<Termin> geaendert = Aenderungen.anwenden(ctx, eins, true);
            Termin g = geaendert.get(0);
            if (g.ausfall) continue;
            roh.raum = g.raum;
            aus.add(roh);
        }
        return aus;
    }

    static boolean vorhanden(Context ctx) {
        return new File(ctx.getFilesDir(), ICS_DATEI).exists();
    }

    public static List<Termin> aufbereiten(List<Termin> roh) {
        List<Termin> aus = new ArrayList<>();
        for (Termin t : roh) aus.add(viertelAnpassen(t));
        Collections.sort(aus, (a, b) -> Long.compare(a.start, b.start));
        return aus;
    }

    public static String lesen(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] puffer = new byte[8192];
        int n;
        while ((n = in.read(puffer)) > 0) bos.write(puffer, 0, n);
        return bos.toString("UTF-8");
    }

    // ───── ICS ─────

    private static final Pattern DATUM =
            Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})?(Z)?$");

    private static Long icsDatum(String wert, String params) {
        if (params.contains("VALUE=DATE") && !params.contains("VALUE=DATE-TIME")) return null;
        Matcher m = DATUM.matcher(wert.trim());
        if (!m.matches()) return null;
        boolean utc = m.group(7) != null;
        Calendar c = utc ? Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.GERMANY)
                : Calendar.getInstance(Locale.GERMANY); // ohne Z: Ortszeit des Handys
        c.clear();
        c.set(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)),
                m.group(6) != null ? Integer.parseInt(m.group(6)) : 0);
        return c.getTimeInMillis();
    }

    private static String icsText(String s) {
        if (s == null) return "";
        return s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",")
                .replace("\\;", ";").replace("\\\\", "\\").trim();
    }

    private static String typErkennen(String beschreibung, String titel) {
        String erster = beschreibung.split("·")[0].trim();
        if (Konfig.istBekannterTyp(erster)) return erster;
        String alles = (beschreibung + " " + titel).toLowerCase(Locale.GERMANY);
        for (String typ : new String[]{"Zusatzübung", "Tutorium", "Übung", "Vorlesung", "Seminar", "Praktikum"}) {
            if (alles.contains(typ.toLowerCase(Locale.GERMANY))) return typ;
        }
        return erster;
    }

    public static List<Termin> parseICS(String text) {
        String entfaltet = text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "");
        List<Termin> liste = new ArrayList<>();
        boolean imEvent = false;
        Long start = null, ende = null;
        String titel = null, beschreibung = "", raum = "";
        for (String zeile : entfaltet.split("\n")) {
            if (zeile.equals("BEGIN:VEVENT")) {
                imEvent = true;
                start = null; ende = null; titel = null; beschreibung = ""; raum = "";
                continue;
            }
            if (zeile.equals("END:VEVENT")) {
                if (imEvent && start != null && ende != null) {
                    String t = titel != null ? titel : "Termin";
                    liste.add(new Termin(start, ende, t, typErkennen(beschreibung, t), raum));
                }
                imEvent = false;
                continue;
            }
            if (!imEvent) continue;
            int dp = zeile.indexOf(':');
            if (dp < 0) continue;
            String kopf = zeile.substring(0, dp);
            String wert = zeile.substring(dp + 1);
            int sk = kopf.indexOf(';');
            String name = sk < 0 ? kopf : kopf.substring(0, sk);
            String params = sk < 0 ? "" : kopf.substring(sk + 1);
            switch (name) {
                case "DTSTART": start = icsDatum(wert, params); break;
                case "DTEND": ende = icsDatum(wert, params); break;
                case "SUMMARY": titel = icsText(wert); break;
                case "DESCRIPTION": beschreibung = icsText(wert); break;
                case "LOCATION": raum = icsText(wert); break;
                default: break;
            }
        }
        return liste;
    }

    // ───── Akademisches Viertel ─────

    static Termin viertelAnpassen(Termin t) {
        if (!Konfig.AKADEMISCHES_VIERTEL) return t;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t.start);
        int minStart = c.get(Calendar.MINUTE);
        c.setTimeInMillis(t.ende);
        int minEnde = c.get(Calendar.MINUTE);
        long dauer = (t.ende - t.start) / 60000L;
        if (minStart == 0 && minEnde == 0 && dauer >= 90) {
            return t.mitZeiten(t.start + 15 * 60000L, t.ende - 15 * 60000L);
        }
        return t;
    }
}
