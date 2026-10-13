package de.stundenplan.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Zeichnet die Mensa-Widgets (klein, mittel, groß) als Bild. Alle Maße in dp. */
public final class MensaZeichner extends Leinwand {

    /** Breite der antippbaren Pfeilfelder oben rechts (muss zu den Layouts passen). */
    public static final float PFEIL_FELD_KLEIN = 34;
    public static final float PFEIL_FELD = 44;

    private static final String[] WT_KURZ = {"So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"};
    private static final String[] WT_VOLL = {"Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"};
    private static final String[] MONATE = {"Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"};
    private static final float PREIS_B = 54;

    private final long jetzt;
    private final int vorder, gedimmt, akzent, knopf;

    private MensaZeichner(Canvas c, boolean dunkel, long jetzt) {
        super(c);
        this.jetzt = jetzt;
        vorder = Color.parseColor(dunkel ? "#F1EFE8" : "#2C2C2A");
        gedimmt = Color.parseColor(dunkel ? "#8E8D86" : "#888780");
        akzent = Color.parseColor(dunkel ? "#85B7EB" : "#185FA5");
        knopf = alpha(Color.parseColor("#888780"), dunkel ? 0.28f : 0.16f);
    }

    /**
     * Erzeugt das Bild. plan == null bedeutet "nicht erreichbar".
     * Der Hintergrund bleibt transparent (kommt aus dem Layout).
     */
    public static Bitmap zeichnen(String modus, float b, float h, float skala, boolean dunkel, long jetzt,
                                  MensaDaten.Auswahl auswahl, MensaDaten.Plan plan) {
        Bitmap bild = Bitmap.createBitmap(Math.max(1, Math.round(b * skala)), Math.max(1, Math.round(h * skala)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bild);
        canvas.scale(skala, skala);
        MensaZeichner z = new MensaZeichner(canvas, dunkel, jetzt);
        if (Zeichner.KLEIN.equals(modus)) z.klein(b, h, auswahl, plan);
        else if (Zeichner.MITTEL.equals(modus)) z.mittel(b, h, auswahl, plan);
        else z.gross(b, h, auswahl, plan);
        return bild;
    }

    // ───────────────────────── Hilfen ─────────────────────────

    private String tagesLabel(long tag, boolean kurz) {
        long heute = MensaDaten.tagesBeginn(jetzt);
        Calendar k = Calendar.getInstance(Locale.GERMANY);
        k.setTimeInMillis(tag);
        int d = k.get(Calendar.DAY_OF_MONTH);
        String monat = MONATE[k.get(Calendar.MONTH)];
        String wt = WT_KURZ[k.get(Calendar.DAY_OF_WEEK) - 1];
        if (tag == heute) return kurz ? "Heute" : "Heute · " + d + ". " + monat;
        if (tag == MensaDaten.plusTage(heute, 1)) return kurz ? "Morgen" : "Morgen · " + d + ". " + monat;
        return kurz ? wt + " " + d + "." : WT_VOLL[k.get(Calendar.DAY_OF_WEEK) - 1].substring(0, 2) + ", " + d + ". " + monat;
    }

    /** Kopfzeile mit Name, Tag und den Pfeilen zum Blättern (oben rechts). */
    private void kopf(float b, float rand, String name, MensaDaten.Auswahl a, boolean kleinesFeld) {
        float feld = kleinesFeld ? PFEIL_FELD_KLEIN : PFEIL_FELD;
        float knopfD = 22;
        float y = rand - 3;
        // Pfeile: Mittelpunkte in den Tippfeldern (rechts: weiter, links daneben: zurück)
        float xWeiter = b - feld / 2f;
        float xZurueck = b - feld - feld / 2f;
        if (!kleinesFeld) { xWeiter = b - rand - knopfD / 2f; xZurueck = xWeiter - knopfD - 8; }
        knopf(xZurueck, y + knopfD / 2f, knopfD, "‹", a.kannZurueck);
        knopf(xWeiter, y + knopfD / 2f, knopfD, "›", a.kannWeiter);

        float rechtsEnde = xZurueck - knopfD / 2f - 8;
        String label = tagesLabel(a.tag.datum, kleinesFeld);
        float lw = breite(label, 11, MEDIUM);
        float nw = rechtsEnde - rand - lw - 6;
        text(name, rand, y + 4, nw, 12, MEDIUM, akzent, LINKS, 1, 0.8f);
        text(label, rechtsEnde - lw - 1, y + 4.6f, lw + 1, 11, MEDIUM, a.versatz == 0 ? gedimmt : vorder, RECHTS, 1, 1f);
    }

    private void knopf(float cx, float cy, float d, String zeichen, boolean aktiv) {
        kreis(cx, cy, d / 2f, aktiv ? knopf : alpha(knopf, 0.4f));
        text(zeichen, cx - d / 2f, cy - 9.5f, d, 15, FETT, aktiv ? vorder : alpha(gedimmt, 0.5f), MITTE, 1, 1f);
    }

    private void hinweis(float x, float y, float b, float h, String s) {
        text(s, x, y + h / 2f - 9, b, 13, MEDIUM, gedimmt, MITTE, 2, 1f);
    }

    /** Status-Text, falls es nichts anzuzeigen gibt – sonst null. */
    private static String leerGrund(MensaDaten.Auswahl a, MensaDaten.Plan plan) {
        if (a.tag.geschlossen) return "Mensa geschlossen";
        if (plan == null) return "Speiseplan nicht erreichbar";
        if (plan.leer()) return "Noch kein Speiseplan";
        return null;
    }

    private static String katKurz(String kat) {
        return kat.replaceAll("(?i)^abend\\s*angebot\\s*", "Abend ").replaceAll("(?i)^tagesangebot.*", "Tagesang.");
    }

    private float zeilenHoehe(int zeilen) {
        return Math.max(26, zeilen * 14.4f);
    }

    private int zeilenFuer(String name, float breite, int max) {
        float nameB = breite - 4 - 7 - 6 - PREIS_B;
        return Math.round(text(name, 0, 0, nameB, 12, MEDIUM, vorder, LINKS, max, 0.9f, false) / 14.4f);
    }

    /** Gerichtzeile: Farbbalken | Name | Preis (rechts) mit Kategorie darunter. */
    private float gerichtZeile(float x, float y, float breite, MensaDaten.Gericht g, int zeilen) {
        float h = zeilenHoehe(zeilen);
        rundesRechteck(x, y + 1, 4, h - 2, 2, g.farbe);
        float nameB = breite - 4 - 7 - 6 - PREIS_B;
        text(g.name, x + 11, y, nameB, 12, MEDIUM, vorder, LINKS, zeilen, 0.9f);
        text(MensaDaten.preisText(g.preis), x + breite - PREIS_B, y, PREIS_B, 12, FETT, vorder, RECHTS, 1, 0.8f);
        text(katKurz(g.kat), x + breite - PREIS_B, y + 14.4f, PREIS_B, 9, NORMAL, gedimmt, RECHTS, 1, 0.8f);
        return h;
    }

    private String thekeText(List<MensaDaten.Gericht> theke) {
        StringBuilder sb = new StringBuilder();
        for (MensaDaten.Gericht g : theke) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(g.kat).append(' ').append(MensaDaten.preisText(g.preis));
            if (g.name.matches("(?i).*100\\s?g.*")) sb.append("/100 g");
        }
        return sb.toString();
    }

    // ───────────────────────── Klein (2×2) ─────────────────────────

    private void klein(float b, float h, MensaDaten.Auswahl a, MensaDaten.Plan plan) {
        float rand = 12;
        kopf(b, rand, "Mensa", a, true);
        float y = rand + 24;
        String grund = leerGrund(a, plan);
        if (grund != null) { hinweis(rand, y, b - 2 * rand, h - y - rand, grund); return; }
        List<MensaDaten.Gericht> liste = !plan.gerichte.isEmpty() ? plan.gerichte : plan.abend;
        int gezeigt = 0;
        for (MensaDaten.Gericht g : liste) {
            float nameH = text(g.name, 0, 0, b - 2 * rand - 10, 11, MEDIUM, vorder, LINKS, 2, 0.85f, false);
            float eintragH = nameH + 12;
            if (y + eintragH > h - rand - 12) break;
            rundesRechteck(rand, y + 1, 4, eintragH - 2, 2, g.farbe);
            text(g.name, rand + 10, y, b - 2 * rand - 10, 11, MEDIUM, vorder, LINKS, 2, 0.85f);
            text(MensaDaten.preisText(g.preis) + " · " + g.kat, rand + 10, y + nameH, b - 2 * rand - 10, 10, NORMAL, gedimmt, LINKS, 1, 0.85f);
            y += eintragH + 6;
            gezeigt++;
        }
        if (liste.size() > gezeigt) {
            text("+ " + (liste.size() - gezeigt) + " weitere", rand, h - rand - 12, b - 2 * rand, 10, NORMAL, gedimmt, LINKS, 1, 1f);
        }
    }

    // ───────────────────────── Mittel (4×2) ─────────────────────────

    private void mittel(float b, float h, MensaDaten.Auswahl a, MensaDaten.Plan plan) {
        float rand = 12;
        float innenB = b - 2 * rand;
        kopf(b, rand, MensaDaten.MENSA_NAME, a, false);
        float y = rand + 25;
        String grund = leerGrund(a, plan);
        if (grund != null) { hinweis(rand, y, innenB, h - y - rand, grund); return; }
        List<MensaDaten.Gericht> liste = new ArrayList<>(plan.gerichte);
        liste.addAll(plan.abend);
        float unten = h - rand;
        int gezeigt = 0;
        for (int i = 0; i < liste.size(); i++) {
            MensaDaten.Gericht g = liste.get(i);
            int z = zeilenFuer(g.name, innenB, 2);
            float zh = zeilenHoehe(z);
            boolean weitereFolgen = i < liste.size() - 1;
            if (y + zh > unten - (weitereFolgen ? 12 : 0)) break;
            y += gerichtZeile(rand, y, innenB, g, z) + 5;
            gezeigt++;
        }
        if (liste.size() > gezeigt) {
            text("+ " + (liste.size() - gezeigt) + " weitere", rand, unten - 12, innenB, 10, NORMAL, gedimmt, LINKS, 1, 1f);
        }
    }

    // ───────────────────────── Groß (4×4, frei in der Höhe) ─────────────────────────

    private static final class Eintrag {
        final MensaDaten.Gericht g;
        final int zeilen;
        final float h;

        Eintrag(MensaDaten.Gericht g, int zeilen, float h) {
            this.g = g;
            this.zeilen = zeilen;
            this.h = h;
        }
    }

    private List<Eintrag> planen(List<MensaDaten.Gericht> liste, float breite, int max) {
        List<Eintrag> e = new ArrayList<>();
        for (MensaDaten.Gericht g : liste) {
            int z = zeilenFuer(g.name, breite, max);
            e.add(new Eintrag(g, z, zeilenHoehe(z) + 8));
        }
        return e;
    }

    private static float summe(List<Eintrag> l) {
        float s = 0;
        for (Eintrag e : l) s += e.h;
        return s;
    }

    private void gross(float b, float h, MensaDaten.Auswahl a, MensaDaten.Plan plan) {
        float rand = 12;
        float breite = b - 2 * rand;
        kopf(b, rand, MensaDaten.MENSA_NAME, a, false);
        float y = rand + 25;
        String grund = leerGrund(a, plan);
        if (grund != null) { hinweis(rand, y, breite, h - y - rand, grund); return; }

        boolean mitTheke = !plan.theke.isEmpty();
        float unten = h - rand - (mitTheke ? 14 : 0);
        float rest = unten - y;
        int weggelassen = 0;

        List<Eintrag> haupt = planen(plan.gerichte, breite, 3);
        List<Eintrag> abend = planen(plan.abend, breite, 2);
        List<Eintrag> desserts = planen(plan.desserts, breite, 1);
        float gesamt = summe(haupt) + (abend.isEmpty() ? 0 : 16 + summe(abend)) + (desserts.isEmpty() ? 0 : 16 + summe(desserts));
        if (gesamt > rest) haupt = planen(plan.gerichte, breite, 2);

        for (Eintrag e : haupt) {
            if (e.h > rest) { weggelassen++; continue; }
            gerichtZeile(rand, y, breite, e.g, e.zeilen);
            y += e.h;
            rest -= e.h;
        }
        if (!abend.isEmpty()) {
            if (rest >= 16 + abend.get(0).h) {
                text("Abends", rand, y, breite, 10, MEDIUM, gedimmt, LINKS, 1, 1f);
                y += 16;
                rest -= 16;
                for (Eintrag e : abend) {
                    if (e.h > rest) { weggelassen++; continue; }
                    gerichtZeile(rand, y, breite, e.g, e.zeilen);
                    y += e.h;
                    rest -= e.h;
                }
            } else {
                weggelassen += abend.size();
            }
        }
        boolean dessertKurz = false;
        if (!desserts.isEmpty()) {
            if (rest >= 16 + summe(desserts)) {
                text("Dessert", rand, y, breite, 10, MEDIUM, gedimmt, LINKS, 1, 1f);
                y += 16;
                for (Eintrag e : desserts) {
                    gerichtZeile(rand, y, breite, e.g, e.zeilen);
                    y += e.h;
                }
                rest -= 16 + summe(desserts);
            } else {
                dessertKurz = rest >= 14 + (weggelassen > 0 ? 14 : 0);
            }
        }
        if (weggelassen > 0) {
            text("+ " + weggelassen + " weitere", rand, y, breite, 10, NORMAL, gedimmt, LINKS, 1, 1f);
        }
        float fy = h - rand - 12;
        if (mitTheke) {
            text(thekeText(plan.theke), rand, fy, breite, 10, NORMAL, gedimmt, LINKS, 1, 0.8f);
            fy -= 14;
        }
        if (dessertKurz) {
            StringBuilder sb = new StringBuilder("Dessert: ");
            for (int i = 0; i < plan.desserts.size(); i++) {
                if (i > 0) sb.append(" · ");
                sb.append(plan.desserts.get(i).name).append(' ').append(MensaDaten.preisText(plan.desserts.get(i).preis));
            }
            text(sb.toString(), rand, fy, breite, 10, NORMAL, gedimmt, LINKS, 1, 0.8f);
        }
    }
}
