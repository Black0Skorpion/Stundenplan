package de.stundenplan.widget;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Speiseplan der Mensa am gewählten Campus über OpenMensa (openmensa.org), mit Zwischenspeicher. */
public final class MensaDaten {
    private MensaDaten() {}

    // ───── Einstellungen ─────
    /** Name der zuletzt geladenen Mensa (für die Überschrift im Widget) */
    public static volatile String MENSA_NAME = "Mensa Golm";
    public static final String PREISGRUPPE = "students";   // "students", "employees" oder "others"
    public static final int UMSCHALTEN_UM = 15;            // ab dieser Stunde wird der nächste Tag gezeigt
    public static final boolean ABEND_ANZEIGEN = true;     // Abendangebote zeigen (wenn anders als mittags)

    static String webseite(Context ctx) {
        return "https://openmensa.org/c/" + Campus.mensaId(Campus.aktuell(ctx));
    }

    /** Schnittstelle der Mensa am aktuellen Campus; merkt sich nebenbei den Namen für die Überschrift. */
    private static String api(Context ctx) {
        String c = Campus.aktuell(ctx);
        MENSA_NAME = Campus.mensaName(c);
        return "https://openmensa.org/api/v2/canteens/" + Campus.mensaId(c);
    }
    private static final long MAX_ALTER_MS = 60 * 60 * 1000L; // Daten höchstens stündlich neu laden

    // ───── Datenklassen ─────

    public static final class Tag {
        public final long datum;          // Tagesbeginn (Ortszeit)
        public final boolean geschlossen;

        Tag(long datum, boolean geschlossen) {
            this.datum = datum;
            this.geschlossen = geschlossen;
        }
    }

    public static final class Gericht {
        public final String name;
        public final String kat;
        public final double preis;        // NaN = unbekannt
        public final int farbe;

        Gericht(String name, String kat, double preis, int farbe) {
            this.name = name;
            this.kat = kat;
            this.preis = preis;
            this.farbe = farbe;
        }
    }

    public static final class Plan {
        public final List<Gericht> gerichte = new ArrayList<>();
        public final List<Gericht> abend = new ArrayList<>();
        public final List<Gericht> theke = new ArrayList<>();
        public final List<Gericht> desserts = new ArrayList<>();

        public boolean leer() {
            return gerichte.isEmpty() && abend.isEmpty();
        }
    }

    /** Ergebnis der Tagesauswahl im Widget. */
    public static final class Auswahl {
        public Tag tag;
        public int versatz;               // tatsächlich verwendeter Versatz (begrenzt)
        public boolean kannZurueck;
        public boolean kannWeiter;
    }

    // ───── Laden (Netz + Zwischenspeicher) ─────

    /** Lädt Text von url; bei Fehler oder ohne Netz der zuletzt gespeicherte Stand. */
    private static String laden(Context ctx, String url, String name, boolean netzErlaubt) {
        String id = url.replaceAll("^.*/canteens/(\\d+).*$", "$1");
        File f = new File(ctx.getCacheDir(), "mensa-" + id + "-" + name + ".json");
        boolean frisch = f.exists() && System.currentTimeMillis() - f.lastModified() < MAX_ALTER_MS;
        if (netzErlaubt && !frisch) {
            try {
                String text = holen(url);
                try (OutputStream out = new FileOutputStream(f)) {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
                return text;
            } catch (IOException e) {
                // weiter mit Zwischenspeicher
            }
        }
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                return lesen(in);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    private static String holen(String adresse) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(adresse).openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(15_000);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", "Stundenplan-Widget (Android)");
        try {
            int code = con.getResponseCode();
            if (code == 404) return "[]"; // kein Speiseplan für diesen Tag
            if (code >= 400) throw new IOException("HTTP " + code);
            try (InputStream in = con.getInputStream()) {
                return lesen(in);
            }
        } finally {
            con.disconnect();
        }
    }

    private static String lesen(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] puffer = new byte[8192];
        int n;
        while ((n = in.read(puffer)) > 0) bos.write(puffer, 0, n);
        return bos.toString("UTF-8");
    }

    // ───── Datum ─────

    static long tagesBeginn(long t) {
        Calendar c = Calendar.getInstance(Locale.GERMANY);
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static long plusTage(long t, int n) {
        Calendar c = Calendar.getInstance(Locale.GERMANY);
        c.setTimeInMillis(t);
        c.add(Calendar.DATE, n);
        return c.getTimeInMillis();
    }

    static String iso(long t) {
        Calendar c = Calendar.getInstance(Locale.GERMANY);
        c.setTimeInMillis(t);
        return String.format(Locale.GERMANY, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static long ausIso(String s) {
        Calendar c = Calendar.getInstance(Locale.GERMANY);
        c.clear();
        c.set(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(5, 7)) - 1, Integer.parseInt(s.substring(8, 10)));
        return c.getTimeInMillis();
    }

    private static boolean wochenende(long t) {
        Calendar c = Calendar.getInstance(Locale.GERMANY);
        c.setTimeInMillis(t);
        int wt = c.get(Calendar.DAY_OF_WEEK);
        return wt == Calendar.SATURDAY || wt == Calendar.SUNDAY;
    }

    // ───── Öffnungstage ─────

    public static List<Tag> ladeTage(Context ctx, boolean netz) {
        return parseTage(laden(ctx, api(ctx) + "/days", "tage", netz));
    }

    public static List<Tag> parseTage(String json) {
        List<Tag> tage = new ArrayList<>();
        if (json != null) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    String d = o.optString("date", "");
                    if (d.length() >= 10) tage.add(new Tag(ausIso(d), o.optBoolean("closed", false)));
                }
            } catch (JSONException e) {
                tage.clear();
            }
        }
        if (tage.isEmpty()) {
            // Ersatz ohne Netz: Mo–Fr der nächsten zwei Wochen
            long heute = tagesBeginn(System.currentTimeMillis());
            for (int i = 0; i < 14; i++) {
                long d = plusTage(heute, i);
                tage.add(new Tag(d, wochenende(d)));
            }
        }
        Collections.sort(tage, (x, y) -> Long.compare(x.datum, y.datum));
        return tage;
    }

    /**
     * Wählt den Tag: Ausgangspunkt ist heute (bis UMSCHALTEN_UM Uhr) bzw. der nächste Öffnungstag,
     * davon ausgehend versatz Öffnungstage vor oder zurück.
     */
    public static Auswahl waehleTag(List<Tag> tage, long jetzt, int versatz) {
        long heute = tagesBeginn(jetzt);
        Calendar c = Calendar.getInstance(Locale.GERMANY);
        c.setTimeInMillis(jetzt);
        boolean vorUmschalten = c.get(Calendar.HOUR_OF_DAY) < UMSCHALTEN_UM;
        List<Tag> offen = new ArrayList<>();
        for (Tag t : tage) if (!t.geschlossen && t.datum >= heute) offen.add(t);

        Auswahl a = new Auswahl();
        if (offen.isEmpty()) {
            a.tag = new Tag(heute, true);
            a.versatz = 0;
            return a;
        }
        int start = offen.size() - 1;
        for (int i = 0; i < offen.size(); i++) {
            if (offen.get(i).datum > heute || vorUmschalten) { start = i; break; }
        }
        int index = Math.max(0, Math.min(offen.size() - 1, start + versatz));
        a.tag = offen.get(index);
        a.versatz = index - start;
        a.kannZurueck = index > 0;
        a.kannWeiter = index < offen.size() - 1;
        return a;
    }

    /** Nächster Öffnungstag nach dem gegebenen Tag (für das große Querformat). */
    public static Tag naechsterOffenerTag(List<Tag> tage, Tag nach) {
        for (Tag t : tage) if (!t.geschlossen && t.datum > nach.datum) return t;
        return new Tag(plusTage(nach.datum, 1), false);
    }

    // ───── Speiseplan ─────

    /** null = nicht erreichbar (kein Netz und nichts gespeichert). */
    public static Plan ladePlan(Context ctx, long tag, boolean netz) {
        String json = laden(ctx, api(ctx) + "/days/" + iso(tag) + "/meals", iso(tag), netz);
        if (json == null) return null;
        return parsePlan(json);
    }

    private static final Pattern ANGEBOT = Pattern.compile("angebot\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final int[] ANGEBOT_FARBEN = {
            Color.parseColor("#1D9E75"), Color.parseColor("#639922"), Color.parseColor("#D85A30"),
            Color.parseColor("#378ADD"), Color.parseColor("#BA7517")};
    private static final int FARBE_TAGES = Color.parseColor("#7F77DD");
    private static final int FARBE_DESSERT = Color.parseColor("#D4537E");
    private static final int FARBE_SONST = Color.parseColor("#888780");

    private static String kategorieKurz(String k) {
        return k.replaceAll("(?i)\\s*-\\s*nur solange.*$", "").replaceAll("\\s+", " ").trim();
    }

    private static int farbe(String kat) {
        String k = kat.toLowerCase(Locale.GERMANY);
        Matcher m = ANGEBOT.matcher(kat);
        if ((k.startsWith("angebot") || k.startsWith("abend")) && m.find()) {
            int nr = Integer.parseInt(m.group(1));
            return nr >= 1 && nr <= ANGEBOT_FARBEN.length ? ANGEBOT_FARBEN[nr - 1] : FARBE_SONST;
        }
        if (k.contains("tagesangebot")) return FARBE_TAGES;
        if (k.contains("dessert")) return FARBE_DESSERT;
        return FARBE_SONST;
    }

    private static int sortierung(String kat) {
        String k = kat.toLowerCase(Locale.GERMANY);
        Matcher m = ANGEBOT.matcher(kat);
        if (k.startsWith("angebot") && m.find()) return Integer.parseInt(m.group(1));
        if (k.contains("tagesangebot")) return 50;
        return 90;
    }

    public static Plan parsePlan(String json) {
        Plan p = new Plan();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String kat = kategorieKurz(o.optString("category", ""));
                String name = o.optString("name", "").replaceAll("\\s+", " ").trim();
                double preis = Double.NaN;
                JSONObject preise = o.optJSONObject("prices");
                if (preise != null && !preise.isNull(PREISGRUPPE)) preis = preise.optDouble(PREISGRUPPE, Double.NaN);
                Gericht g = new Gericht(name, kat, preis, farbe(kat));
                String k = kat.toLowerCase(Locale.GERMANY);
                if (k.startsWith("abend")) p.abend.add(g);
                else if (k.contains("dessert")) p.desserts.add(g);
                else if (k.contains("theke")) p.theke.add(g);
                else p.gerichte.add(g);
            }
        } catch (JSONException e) {
            return p;
        }
        Collections.sort(p.gerichte, (x, y) -> Integer.compare(sortierung(x.kat), sortierung(y.kat)));
        // Abendangebote nur, wenn sie sich vom Mittag unterscheiden
        List<Gericht> abendNeu = new ArrayList<>();
        if (ABEND_ANZEIGEN) {
            for (Gericht a : p.abend) {
                boolean doppelt = false;
                for (Gericht g : p.gerichte) if (g.name.equals(a.name)) { doppelt = true; break; }
                if (!doppelt) abendNeu.add(a);
            }
        }
        p.abend.clear();
        p.abend.addAll(abendNeu);
        return p;
    }

    public static String preisText(double p) {
        if (Double.isNaN(p)) return "";
        return String.format(Locale.GERMANY, "%.2f €", p);
    }
}
