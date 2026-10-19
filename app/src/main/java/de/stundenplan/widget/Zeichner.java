package de.stundenplan.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Zeichnet die Widgets (klein, mittel, groß) als Bild. Alle Maße in dp. */
public final class Zeichner extends Leinwand {

    public static final String KLEIN = "klein";
    public static final String MITTEL = "mittel";
    public static final String GROSS = "gross";

    private static final String[] WOCHENTAGE = {"Mo", "Di", "Mi", "Do", "Fr"};
    private static final String[] WT_KURZ = {"So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"};
    private static final String[] WT_VOLL = {"Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"};
    private static final String[] MONATE = {"Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"};

    private static final int RASTER = Color.parseColor("#888780");
    private static final int HEUTE = Color.parseColor("#185FA5");
    private static final int JETZT = Color.parseColor("#E24B4A");



    private final long jetzt;
    private final List<Termin> alle;
    private final int vorder, gedimmt, akzent;
    private final Calendar cal = Calendar.getInstance(Locale.GERMANY);

    private Zeichner(Canvas c, boolean dunkel, long jetzt, List<Termin> alle) {
        super(c);
        this.jetzt = jetzt;
        this.alle = alle;
        vorder = Color.parseColor(dunkel ? "#F1EFE8" : "#2C2C2A");
        gedimmt = Color.parseColor(dunkel ? "#8E8D86" : "#888780");
        akzent = Color.parseColor(dunkel ? "#85B7EB" : "#185FA5");
    }

    /** Erzeugt das Widget-Bild. Der Hintergrund bleibt transparent (kommt aus dem Layout). */
    /** Was im Widget zu sehen ist – für die Tippflächen (Lageplan). */
    public static final class Ziele {
        public Termin fokus;                          // Karte (klein/mittel)
        public final List<Termin> zeilen = new ArrayList<>(); // Tagesliste (mittel)
        public final long[] tage = new long[5];       // Wochentage (groß)
        public final boolean[] tagHatTermine = new boolean[5];
    }

    private final Ziele ziele = new Ziele();

    /** Nächster Wecker { Weckzeit, Beginn der ersten Veranstaltung } für den Hinweis am Vorabend (null = aus) */
    static volatile long[] wecker;

    /** Tippfläche im Wochenraster der App: Termin (oder null = Tageskopf) */
    public static final class Treffer {
        public final android.graphics.RectF rect;
        public final Termin termin;
        public final long tag;

        Treffer(android.graphics.RectF rect, Termin termin, long tag) {
            this.rect = rect;
            this.termin = termin;
            this.tag = tag;
        }
    }

    private List<Treffer> treffer;

    /**
     * Wochenraster für die App (ohne Kopfzeile), direkt auf eine Leinwand in dp.
     * Gibt die Tippflächen zurück (Termine und Tagesköpfe).
     */
    static List<Treffer> woche(Canvas canvas, float b, float h, boolean dunkel, long jetzt, long montag, List<Termin> alle) {
        Zeichner z = new Zeichner(canvas, dunkel, jetzt, alle);
        z.treffer = new ArrayList<>();
        long[] tage = new long[5];
        List<List<Termin>> termine = new ArrayList<>();
        int startH = Konfig.STANDARD_START, endeH = Konfig.STANDARD_ENDE;
        for (int i = 0; i < 5; i++) {
            tage[i] = z.plusTage(montag, i);
            List<Termin> l = z.termineAm(tage[i]);
            termine.add(l);
            for (Termin t : l) {
                startH = Math.min(startH, (int) Math.floor(z.stunden(t.start)));
                endeH = Math.max(endeH, (int) Math.ceil(z.stunden(t.ende)));
            }
        }
        z.raster(b, h, tage, termine, startH, endeH);
        return z.treffer;
    }

    public static Bitmap zeichnen(String modus, float breiteDp, float hoeheDp, float skala,
                                  boolean dunkel, long jetzt, List<Termin> alle) {
        return zeichnenMitZielen(modus, breiteDp, hoeheDp, skala, dunkel, jetzt, alle, null);
    }

    public static Bitmap zeichnenMitZielen(String modus, float breiteDp, float hoeheDp, float skala,
                                           boolean dunkel, long jetzt, List<Termin> alle, Ziele zielAus) {
        int bw = Math.max(1, Math.round(breiteDp * skala));
        int bh = Math.max(1, Math.round(hoeheDp * skala));
        Bitmap bild = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bild);
        canvas.scale(skala, skala);
        Zeichner z = new Zeichner(canvas, dunkel, jetzt, alle);
        if (KLEIN.equals(modus)) z.klein(breiteDp, hoeheDp);
        else if (MITTEL.equals(modus)) z.mittel(breiteDp, hoeheDp);
        else z.gross(breiteDp, hoeheDp);
        if (zielAus != null) {
            zielAus.fokus = z.ziele.fokus;
            zielAus.zeilen.addAll(z.ziele.zeilen);
            System.arraycopy(z.ziele.tage, 0, zielAus.tage, 0, 5);
            System.arraycopy(z.ziele.tagHatTermine, 0, zielAus.tagHatTermine, 0, 5);
        }
        return bild;
    }

    // ───────────────────────── Zeit-Hilfen ─────────────────────────

    private float stunden(long t) {
        cal.setTimeInMillis(t);
        return cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f;
    }

    private String uhrzeit(long t) {
        cal.setTimeInMillis(t);
        return cal.get(Calendar.HOUR_OF_DAY) + ":" + String.format(Locale.GERMANY, "%02d", cal.get(Calendar.MINUTE));
    }

    private long tagesBeginn(long t) {
        cal.setTimeInMillis(t);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long plusTage(long t, int n) {
        cal.setTimeInMillis(t);
        cal.add(Calendar.DATE, n);
        return cal.getTimeInMillis();
    }

    /** 0 = Sonntag … 6 = Samstag */
    private int wochentag(long t) {
        cal.setTimeInMillis(t);
        return cal.get(Calendar.DAY_OF_WEEK) - 1;
    }

    private int tagImMonat(long t) {
        cal.setTimeInMillis(t);
        return cal.get(Calendar.DAY_OF_MONTH);
    }

    private int monat(long t) {
        cal.setTimeInMillis(t);
        return cal.get(Calendar.MONTH);
    }

    private int kalenderwoche(long t) {
        Calendar k = Calendar.getInstance(Locale.GERMANY);
        k.setFirstDayOfWeek(Calendar.MONDAY);
        k.setMinimalDaysInFirstWeek(4);
        k.setTimeInMillis(t);
        return k.get(Calendar.WEEK_OF_YEAR);
    }

    private boolean gleicherTag(long a, long b) {
        return tagesBeginn(a) == tagesBeginn(b);
    }

    private String datumKurz(long t) {
        return WT_KURZ[wochentag(t)] + ", " + tagImMonat(t) + ". " + MONATE[monat(t)];
    }

    private static String minutenText(long min) {
        if (min < 60) return min + " Min.";
        long h = min / 60, m = min % 60;
        return h + " Std." + (m > 0 ? " " + m + " Min." : "");
    }

    private List<Termin> termineAm(long tag) {
        long bis = plusTage(tag, 1);
        List<Termin> l = new ArrayList<>();
        for (Termin t : alle) if (t.start >= tag && t.start < bis) l.add(t);
        return l;
    }

    // ───────────────────────── Zeichen-Hilfen ─────────────────────────

    private void leerHinweis(float b, float h, String hinweis) {
        text(hinweis, 12, h / 2f - 8, b - 24, 13, MEDIUM, gedimmt, MITTE, 2, 1f);
    }

    // ───────────────────────── Tagesauswahl (klein/mittel) ─────────────────────────

    private static final class Auswahl {
        long datum;
        List<Termin> termine;
        String label;
        boolean istHeute;
    }

    /** Heute, solange noch etwas ansteht – danach der nächste Tag mit Terminen. */
    private Auswahl tagesAuswahl() {
        long heute = tagesBeginn(jetzt);
        List<Termin> heuteTermine = termineAm(heute);
        for (Termin t : heuteTermine) {
            if (t.ende > jetzt) {
                Auswahl a = new Auswahl();
                a.datum = heute;
                a.termine = heuteTermine;
                a.label = "Heute";
                a.istHeute = true;
                return a;
            }
        }
        long morgen = plusTage(heute, 1);
        Termin naechster = null;
        for (Termin t : alle) {
            if (t.start >= morgen) { naechster = t; break; }
        }
        if (naechster == null) return null;
        Auswahl a = new Auswahl();
        a.datum = tagesBeginn(naechster.start);
        a.termine = termineAm(a.datum);
        a.label = a.datum == morgen ? "Morgen" : WT_VOLL[wochentag(a.datum)];
        a.istHeute = false;
        return a;
    }

    private Termin fokusTermin(Auswahl a) {
        for (Termin t : a.termine) if (t.start <= jetzt && t.ende > jetzt) return t;
        for (Termin t : a.termine) if (t.start > jetzt) return t;
        return a.termine.get(0);
    }

    // ───────────────────────── Karte (aktuelle / nächste Veranstaltung) ─────────────────────────

    private float karte(Termin t, Auswahl a, float x, float y, float breite, boolean ausfuehrlich) {
        String[] kurs = Konfig.kurs(t.titel);
        String[] typ = Konfig.typ(t.typ);
        int farbe = Color.parseColor(kurs[1]);
        int weiss90 = alpha(WEISS, 0.9f);
        float innen = breite - 20;
        boolean laufend = t.start <= jetzt && t.ende > jetzt;

        String status;
        if (laufend) {
            status = "Jetzt · noch " + minutenText((t.ende - jetzt + 59999) / 60000);
        } else if (a.istHeute && t.start - jetzt <= 60 * 60000L) {
            status = "Beginnt in " + minutenText((t.start - jetzt + 59999) / 60000);
        } else {
            status = a.istHeute ? "Als Nächstes" : "Erster Termin";
            // Am Vorabend: wann der Wecker klingelt
            long[] w = wecker;
            if (!a.istHeute && w != null && w[1] == t.start) status = "Erster Termin · ⏰ " + uhrzeit(w[0]);
        }

        String titel = ausfuehrlich ? t.titel : kurs[0];
        float titelGroesse = ausfuehrlich ? 15 : 18;
        int titelZeilen = ausfuehrlich ? 2 : 1;
        float titelMin = ausfuehrlich ? 0.8f : 0.6f;
        float titelH = text(titel, 0, 0, innen, titelGroesse, FETT, WEISS, LINKS, titelZeilen, titelMin, false);

        float h = 8 + 12 + 2 + titelH + 2 + 13.2f + 13.2f + (laufend ? 9 : 0) + 8;
        rundesRechteck(x, y, breite, h, 12, farbe);

        float cy = y + 8;
        text(status, x + 10, cy, innen, 10, MEDIUM, weiss90, LINKS, 1, 0.85f);
        cy += 12 + 2;
        text(titel, x + 10, cy, innen, titelGroesse, FETT, WEISS, LINKS, titelZeilen, titelMin);
        cy += titelH + 2;
        text(typ[0] + " · " + t.raum, x + 10, cy, innen, 11, MEDIUM, weiss90, LINKS, 1, 0.75f);
        cy += 13.2f;
        text(uhrzeit(t.start) + "–" + uhrzeit(t.ende), x + 10, cy, innen, 11, MEDIUM, weiss90, LINKS, 1, 1f);
        cy += 13.2f;
        if (laufend) {
            cy += 5;
            float anteil = (float) (jetzt - t.start) / (float) (t.ende - t.start);
            rundesRechteck(x + 10, cy, innen, 4, 2, alpha(WEISS, 0.3f));
            rundesRechteck(x + 10, cy, Math.max(4, innen * anteil), 4, 2, WEISS);
        }
        return h;
    }

    // ───────────────────────── Kleines Widget (2×2) ─────────────────────────

    private void klein(float b, float h) {
        float rand = 12;
        Auswahl a = tagesAuswahl();
        if (a == null) { leerHinweis(b, h, "Keine weiteren Termine"); return; }

        float halbe = (b - 2 * rand) / 2f;
        text(a.label, rand, rand, halbe + 10, 12, MEDIUM, a.istHeute ? akzent : vorder, LINKS, 1, 0.8f);
        text(tagImMonat(a.datum) + ". " + MONATE[monat(a.datum)], rand + halbe, rand + 0.6f, halbe, 11, NORMAL, gedimmt, RECHTS, 1, 1f);

        Termin fokus = fokusTermin(a);
        ziele.fokus = fokus;
        karte(fokus, a, rand, rand + 14.4f + 6, b - 2 * rand, false);

        Termin danach = null;
        for (Termin t : a.termine) if (t.start > fokus.start) { danach = t; break; }
        float fy = h - rand - 13.2f;
        if (danach != null) {
            float dw = breite("Danach", 11, NORMAL);
            text("Danach", rand, fy, dw + 1, 11, NORMAL, gedimmt, LINKS, 1, 1f);
            text(uhrzeit(danach.start) + " " + Konfig.kurs(danach.titel)[0], rand + dw + 4, fy,
                    b - 2 * rand - dw - 4, 11, FETT, vorder, LINKS, 1, 0.7f);
        } else {
            text(a.istHeute ? "Danach frei" : "Sonst nichts an dem Tag", rand, fy, b - 2 * rand, 11, NORMAL, gedimmt, LINKS, 1, 0.85f);
        }
    }

    // ───────────────────────── Mittleres Widget (4×2) ─────────────────────────

    private void mittel(float b, float h) {
        float rand = 12, luecke = 12;
        Auswahl a = tagesAuswahl();
        if (a == null) { leerHinweis(b, h, "Keine weiteren Termine"); return; }

        float innenB = b - 2 * rand;
        float innenH = h - 2 * rand;
        float linksB = (float) Math.floor((innenB - luecke) * 0.47f);
        float rechtsB = innenB - luecke - linksB;

        // Links: Kopf und Karte
        float lw = breite(a.label, 12, MEDIUM);
        text(a.label, rand, rand, lw + 1, 12, MEDIUM, a.istHeute ? akzent : vorder, LINKS, 1, 1f);
        String datum = (a.istHeute || "Morgen".equals(a.label)) ? datumKurz(a.datum)
                : tagImMonat(a.datum) + ". " + MONATE[monat(a.datum)];
        text(datum, rand + lw + 5, rand + 0.6f, linksB - lw - 5, 11, NORMAL, gedimmt, LINKS, 1, 0.85f);
        Termin fokus = fokusTermin(a);
        ziele.fokus = fokus;
        karte(fokus, a, rand, rand + 14.4f + 6, linksB, true);

        // Rechts: Tagesplan
        float rx = rand + linksB + luecke;
        int anzahl = a.termine.size();
        text(anzahl + (anzahl == 1 ? " Termin" : " Termine"), rx, rand, rechtsB, 11, NORMAL, gedimmt, LINKS, 1, 1f);

        float zeilenH = 30;
        int maxZeilen = Math.max(1, (int) Math.floor((innenH - 20) / (zeilenH + 3)));
        List<Termin> liste = new ArrayList<>(a.termine);
        int mehr = 0;
        if (liste.size() > maxZeilen) {
            while (liste.size() > maxZeilen && liste.get(0).ende <= jetzt) liste.remove(0); // Vergangenes zuerst ausblenden
            if (liste.size() > maxZeilen) {
                mehr = liste.size() - (maxZeilen - 1);
                liste = new ArrayList<>(liste.subList(0, maxZeilen - 1));
            }
        }

        float y = rand + 13.2f + 6;
        ziele.zeilen.addAll(liste);
        for (Termin t : liste) {
            String[] kurs = Konfig.kurs(t.titel);
            String[] typ = Konfig.typ(t.typ);
            int farbe = Color.parseColor(kurs[1]);
            boolean vorbei = t.ende <= jetzt;
            boolean laufend = t.start <= jetzt && t.ende > jetzt;
            float d = vorbei ? 0.45f : 1f;
            if (laufend) rundesRechteck(rx, y, rechtsB, zeilenH, 6, alpha(farbe, 0.16f));
            rundesRechteck(rx, y + 2, 4, zeilenH - 4, 2, alpha(farbe, vorbei ? 0.35f : 1f));
            float tx = rx + 11;
            float tw = rechtsB - 11 - 4;
            float ty = y + (zeilenH - 14.4f - 12f) / 2f;
            String zeit = uhrzeit(t.start);
            float zw = breite(zeit, 12, MEDIUM);
            text(zeit, tx, ty, zw + 1, 12, MEDIUM, alpha(gedimmt, d), LINKS, 1, 1f);
            text(kurs[0], tx + zw + 5, ty, tw - zw - 5, 12, FETT, alpha(vorder, d), LINKS, 1, 0.7f);
            text(typ[0] + " · " + t.raum, tx, ty + 14.4f, tw, 10, NORMAL, alpha(gedimmt, d), LINKS, 1, 0.75f);
            y += zeilenH + 3;
        }
        if (mehr > 0) text("+ " + mehr + " weitere", rx, y, rechtsB, 10, NORMAL, gedimmt, LINKS, 1, 1f);
    }

    // ───────────────────────── Großes Widget (4×4) ─────────────────────────

    private String statusText() {
        for (Termin t : alle) {
            if (t.start <= jetzt && t.ende > jetzt) {
                return "bis " + uhrzeit(t.ende) + " " + Konfig.kurs(t.titel)[0] + " · " + t.raum;
            }
        }
        for (Termin t : alle) {
            if (t.start > jetzt) {
                String tag = gleicherTag(t.start, jetzt) ? "" : WT_KURZ[wochentag(t.start)] + " ";
                return "ab " + tag + uhrzeit(t.start) + " " + Konfig.kurs(t.titel)[0] + " · " + t.raum;
            }
        }
        return "Keine weiteren Termine";
    }

    private void gross(float b, float h) {
        float rand = 11, kopfH = 16, abstand = 5;

        // Angezeigte Woche: aktuelle Woche, am Wochenende die nächste
        long heute = tagesBeginn(jetzt);
        int wt = (wochentag(heute) + 6) % 7; // 0 = Montag
        long montag = plusTage(heute, -wt);
        boolean naechsteWoche = wt >= 5;
        if (naechsteWoche) montag = plusTage(montag, 7);

        long[] tage = new long[5];
        List<List<Termin>> termine = new ArrayList<>();
        int startH = Konfig.STANDARD_START, endeH = Konfig.STANDARD_ENDE;
        boolean leer = true;
        for (int i = 0; i < 5; i++) {
            tage[i] = plusTage(montag, i);
            List<Termin> l = termineAm(tage[i]);
            ziele.tage[i] = tage[i];
            ziele.tagHatTermine[i] = !l.isEmpty();
            termine.add(l);
            for (Termin t : l) {
                leer = false;
                startH = Math.min(startH, (int) Math.floor(stunden(t.start)));
                endeH = Math.max(endeH, (int) Math.ceil(stunden(t.ende)));
            }
        }

        String titel = naechsteWoche ? "Nächste Woche · KW " + kalenderwoche(montag)
                : datumKurz(jetzt) + " · KW " + kalenderwoche(jetzt);
        float tw = breite(titel, 13, MEDIUM);
        text(titel, rand, rand, tw + 1, 13, MEDIUM, vorder, LINKS, 1, 1f);
        text(statusText(), rand + tw + 8, rand + 1.2f, b - 2 * rand - tw - 8, 11, NORMAL, gedimmt, RECHTS, 1, 0.8f);

        float rasterB = b - 2 * rand;
        float rasterH = h - 2 * rand - kopfH - abstand;
        if (leer) {
            leerHinweis(b, h, "Keine Termine in dieser Woche");
            return;
        }
        c.save();
        c.translate(rand, rand + kopfH + abstand);
        raster(rasterB, rasterH, tage, termine, startH, endeH);
        c.restore();
    }

    private void raster(float breite, float hoehe, long[] tage, List<List<Termin>> termine, int startH, int endeH) {
        float achse = 20, kopf = 17, luecke = 3;
        float spaltenB = (breite - achse - luecke * (tage.length - 1)) / tage.length;
        float rasterH = hoehe - kopf - 1;
        float proStunde = rasterH / (endeH - startH);

        for (int std = startH; std <= endeH; std++) {
            float y = kopf + (std - startH) * proStunde;
            boolean gerade = (std - startH) % 2 == 0;
            linie(achse, y, breite, y, alpha(RASTER, gerade ? 0.28f : 0.12f), 0.5f);
            if (gerade) {
                text(String.valueOf(std), 0, Math.min(y - 5.4f, hoehe - 11), achse - 4, 9, MEDIUM, RASTER, RECHTS, 1, 1f);
            }
        }

        for (int i = 0; i < tage.length; i++) {
            float x = achse + i * (spaltenB + luecke);
            boolean heute = gleicherTag(tage[i], jetzt);
            String label = WOCHENTAGE[i] + " " + tagImMonat(tage[i]) + ".";
            if (treffer != null) treffer.add(new Treffer(new android.graphics.RectF(x, 0, x + spaltenB, kopf), null, tage[i]));
            if (heute) {
                rundesRechteck(x + 2, 0, spaltenB - 4, 14, 7, HEUTE);
                text(label, x, 1, spaltenB, 10, MEDIUM, WEISS, MITTE, 1, 0.8f);
                rundesRechteck(x, kopf, spaltenB, rasterH, 6, alpha(RASTER, 0.12f));
            } else {
                text(label, x, 1, spaltenB, 10, MEDIUM, RASTER, MITTE, 1, 0.8f);
            }

            for (Termin t : spaltenZuweisen(termine.get(i))) {
                float y1 = kopf + (Math.max(startH, stunden(t.start)) - startH) * proStunde;
                float y2 = kopf + (Math.min(endeH, stunden(t.ende)) - startH) * proStunde;
                float bSpalte = (spaltenB - (t.spalten - 1) * 2) / t.spalten;
                float bx = x + t.spalte * (bSpalte + 2);
                float hStapel = (y2 - y1 - (t.stapelAnzahl - 1) * 2) / t.stapelAnzahl;
                float by = y1 + t.stapel * (hStapel + 2);
                block(t, bx, by + 0.75f, bSpalte, Math.max(8, hStapel - 1.5f));
                if (treffer != null) treffer.add(new Treffer(new android.graphics.RectF(bx, by, bx + bSpalte, by + Math.max(8, hStapel)), t, tage[i]));
            }

            float jetztH = stunden(jetzt);
            if (heute && jetztH >= startH && jetztH <= endeH) {
                float y = kopf + (jetztH - startH) * proStunde;
                linie(x - 1, y, x + spaltenB, y, JETZT, 2);
                kreis(x - 0.5f, y, 3.5f, JETZT);
            }
        }
    }

    /** Ordnet überlappende Termine nebeneinander bzw. (bei gleicher Zeit) übereinander an. */
    private static List<Termin> spaltenZuweisen(List<Termin> tagesTermine) {
        List<Termin> sortiert = new ArrayList<>(tagesTermine);
        sortiert.sort((a, b) -> a.start != b.start ? Long.compare(a.start, b.start) : Long.compare(b.ende, a.ende));
        List<Termin> fertig = new ArrayList<>();
        List<Termin> gruppe = new ArrayList<>();
        long gruppenEnde = 0;
        for (Termin t : sortiert) {
            if (!gruppe.isEmpty() && t.start >= gruppenEnde) {
                gruppeAbschliessen(gruppe, fertig);
                gruppe = new ArrayList<>();
            }
            gruppe.add(t);
            gruppenEnde = Math.max(gruppenEnde, t.ende);
        }
        if (!gruppe.isEmpty()) gruppeAbschliessen(gruppe, fertig);
        return fertig;
    }

    private static void gruppeAbschliessen(List<Termin> gruppe, List<Termin> fertig) {
        List<Long> spaltenEnde = new ArrayList<>();
        for (Termin t : gruppe) {
            int i = -1;
            for (int s = 0; s < spaltenEnde.size(); s++) if (spaltenEnde.get(s) <= t.start) { i = s; break; }
            if (i < 0) { i = spaltenEnde.size(); spaltenEnde.add(0L); }
            spaltenEnde.set(i, t.ende);
            t.spalte = i;
        }
        boolean gleichzeitig = gruppe.size() > 1;
        for (Termin t : gruppe) {
            if (t.start != gruppe.get(0).start || t.ende != gruppe.get(0).ende) { gleichzeitig = false; break; }
        }
        for (int i = 0; i < gruppe.size(); i++) {
            Termin t = gruppe.get(i);
            if (gleichzeitig) {
                t.spalte = 0; t.spalten = 1; t.stapel = i; t.stapelAnzahl = gruppe.size();
            } else {
                t.spalten = spaltenEnde.size(); t.stapel = 0; t.stapelAnzahl = 1;
            }
        }
        fertig.addAll(gruppe);
    }

    private void block(Termin t, float x, float y, float b, float h) {
        String[] kurs = Konfig.kurs(t.titel);
        String[] typ = Konfig.typ(t.typ);
        int farbe = Color.parseColor(kurs[1]);
        int tf = WEISS;
        if (t.stil == 1) {
            // Auswahlmöglichkeit: grau hinterlegt, gestrichelter Rahmen in der Fachfarbe
            rundesRechteck(x, y, b, h, 5, alpha(RASTER, 0.18f));
            rahmen(x, y, b, h, farbe, true);
            tf = farbe;
        } else if (t.stil == 2) {
            // Ausgeblendet: blass und durchgestrichen
            rundesRechteck(x, y, b, h, 5, alpha(farbe, 0.28f));
            linie(x + 2, y + h - 2, x + b - 2, y + 2, alpha(vorder, 0.5f), 1);
            tf = alpha(vorder, 0.6f);
        } else {
            rundesRechteck(x, y, b, h, 5, farbe);
            if (t.stil == 3) rahmen(x, y, b, h, JETZT, false);
        }
        if (t.marke != null) typ = new String[]{t.marke, t.marke};
        int weissGedimmt = alpha(tf, 0.85f);
        c.save();
        c.clipRect(x, y, x + b, y + h);

        if (h < 34) {
            // Kurzer Termin (z. B. 45 Min.): Titel, darunter Art · Raum
            text(kurs[0], x + 2, y + 1, b - 4, 9, FETT, tf, LINKS, 1, 1f);
            // Raum ist wichtiger als die Art – bei wenig Platz steht hier nur der Raum
            if (h >= 16) text(t.raum, x + 2, y + 9.5f, b - 4, 7.5f, MEDIUM, weissGedimmt, LINKS, 1, 0.85f);
        } else if (h < 40 && b >= 40) {
            // Wenig Höhe (z. B. Woche auf 4×3 verkleinert): Titel, darunter Art und Raum in einer Zeile
            float pad = 3;
            String art = b >= 80 ? typ[0] : typ[1];
            text(kurs[0], x + pad, y + pad - 0.5f, b - 2 * pad, 10, FETT, tf, LINKS, 1, 1f);
            // Raum zuerst, damit er bei Platzmangel nicht abgeschnitten wird
            text(t.raum + "\u00A0·\u00A0" + art, x + pad, y + pad + 11.5f, b - 2 * pad, 7.5f, MEDIUM, weissGedimmt, LINKS, 1, 1f);
            if (h >= 36) {
                text(uhrzeit(t.start) + "–" + uhrzeit(t.ende), x + pad, y + pad + 20.5f, b - 2 * pad, 7.5f, MEDIUM, weissGedimmt, LINKS, 1, 1f);
            }
        } else if (b < 40) {
            // Schmaler Block (teilweise überlappend)
            float pad = 2.5f;
            float innen = b - 2 * pad;
            float cy = y + pad - 0.5f;
            float unten = y + h - pad + 1.5f;
            text(typ[1], x + pad, cy, innen, 7, MEDIUM, weissGedimmt, LINKS, 1, 1f);
            cy += 8.4f;
            String titel = kurs[0].replaceFirst("-", "-\n").replaceFirst(" ", "\n");
            cy += text(titel, x + pad, cy, innen, 8.5f, FETT, tf, LINKS, 2, 1f);
            if (unten - cy >= 8.4f) {
                String raum = t.raum.replaceFirst("^([^.]+\\.[^.]+\\.)", "$1\n");
                text(raum, x + pad, cy, innen, 7, MEDIUM, weissGedimmt, LINKS, (int) ((unten - cy) / 8.4f), 1f);
            }
        } else {
            // Normaler Block: Art · Titel · Raum
            float pad = 3.5f;
            float innen = b - 2 * pad;
            float verfuegbar = h - 2 * pad + 1.5f;
            float cy = y + pad - 0.5f;
            text(typ[0], x + pad, cy, innen, 7.5f, MEDIUM, weissGedimmt, LINKS, 1, 1f);
            cy += 9;
            verfuegbar -= 9;
            int titelZeilen = Math.max(1, Math.min(2, (int) Math.floor((verfuegbar - 9) / 12)));
            text(kurs[0], x + pad, cy, innen, 10, FETT, tf, LINKS, titelZeilen, 1f);
            cy += titelZeilen * 12;
            verfuegbar -= titelZeilen * 12;
            if (verfuegbar >= 9) {
                text(t.raum, x + pad, cy, innen, 7.5f, MEDIUM, weissGedimmt, LINKS, (int) Math.floor(verfuegbar / 9), 1f);
            }
        }
        c.restore();
    }

    private void rahmen(float x, float y, float b, float h, int farbe, boolean gestrichelt) {
        fp.setStyle(android.graphics.Paint.Style.STROKE);
        fp.setStrokeWidth(gestrichelt ? 1.2f : 2f);
        fp.setColor(farbe);
        fp.setPathEffect(gestrichelt ? new android.graphics.DashPathEffect(new float[]{4, 3}, 0) : null);
        c.drawRoundRect(new android.graphics.RectF(x + 0.6f, y + 0.6f, x + b - 0.6f, y + h - 0.6f), 5, 5, fp);
        fp.setPathEffect(null);
    }

    /** Zeitpunkte, an denen sich die Anzeige ändert (Beginn/Ende von Terminen). */
    public static long naechsteGrenze(List<Termin> alle, long jetzt) {
        long beste = Long.MAX_VALUE;
        for (Termin t : alle) {
            if (t.start > jetzt) beste = Math.min(beste, t.start);
            if (t.ende > jetzt) beste = Math.min(beste, t.ende);
        }
        return beste;
    }
}
