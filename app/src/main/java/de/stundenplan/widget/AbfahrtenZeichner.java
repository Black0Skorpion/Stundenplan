package de.stundenplan.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/** Zeichnet das Abfahrten-Widget als Bild. Maße in dp. */
public final class AbfahrtenZeichner extends Leinwand {

    private final int vorder, gedimmt, akzent, rot, gruen;

    private AbfahrtenZeichner(Canvas c, boolean dunkel) {
        super(c);
        vorder = Color.parseColor(dunkel ? "#F1EFE8" : "#2C2C2A");
        gedimmt = Color.parseColor(dunkel ? "#8E8D86" : "#888780");
        akzent = Color.parseColor(dunkel ? "#85B7EB" : "#185FA5");
        rot = Color.parseColor("#E24B4A");
        gruen = Color.parseColor("#1D9E75");
    }

    private String campus = Campus.GOLM;
    private List<String> favoriten = new ArrayList<>();
    private final int gold = Color.parseColor("#E0A100");

    public static Bitmap zeichnen(float b, float h, float skala, boolean dunkel, long jetzt, List<AbfahrtenDaten.Abfahrt> liste) {
        return zeichnen(b, h, skala, dunkel, jetzt, liste, Campus.GOLM, new ArrayList<>());
    }

    public static Bitmap zeichnen(float b, float h, float skala, boolean dunkel, long jetzt, List<AbfahrtenDaten.Abfahrt> liste,
                                  String campus, List<String> favoriten) {
        Bitmap bild = Bitmap.createBitmap(Math.max(1, Math.round(b * skala)), Math.max(1, Math.round(h * skala)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bild);
        canvas.scale(skala, skala);
        AbfahrtenZeichner z = new AbfahrtenZeichner(canvas, dunkel);
        z.campus = campus;
        z.favoriten = favoriten;
        z.inhalt(b, h, jetzt, liste);
        return bild;
    }

    private void inhalt(float b, float h, long jetzt, List<AbfahrtenDaten.Abfahrt> liste) {
        float rand = 12;
        boolean kompakt = b < 220;
        float innenB = b - 2 * rand;
        text(kompakt ? "Ab " + Campus.kurz(campus) : "Abfahrten " + Campus.kurz(campus), rand, rand - 2, innenB - 40, 12, MEDIUM, akzent, LINKS, 1, 0.8f);
        text(Lageplan.uhrzeitText(jetzt), rand, rand - 2, innenB, 11, NORMAL, gedimmt, RECHTS, 1, 1f);
        float y = rand + 20;
        if (liste == null) { hinweis("Nicht erreichbar", b, h); return; }
        float zeileH = kompakt ? 26 : 32;
        int platz = Math.max(1, (int) ((h - y - rand + 4) / zeileH));
        List<AbfahrtenDaten.Abfahrt> kommend = new ArrayList<>();
        for (AbfahrtenDaten.Abfahrt a : liste) if (a.zeit >= jetzt - 30_000) kommend.add(a);
        int n = 0;
        for (AbfahrtenDaten.Abfahrt a : AbfahrtenDaten.auswahl(kommend, platz, favoriten)) {
            zeile(a, rand, y, innenB, zeileH, kompakt, jetzt);
            y += zeileH;
            n++;
        }
        if (n == 0) hinweis("Keine Abfahrten", b, h);
    }

    private void hinweis(String s, float b, float h) {
        text(s, 0, h / 2 - 8, b, 13, MEDIUM, gedimmt, MITTE, 1, 1f);
    }

    private void zeile(AbfahrtenDaten.Abfahrt a, float x, float y, float b, float zh, boolean kompakt, long jetzt) {
        float badgeB = kompakt ? 32 : 38, badgeH = kompakt ? 16 : 18;
        float by = y + (kompakt ? 3 : 4);
        rundesRechteck(x, by, badgeB, badgeH, 4, Color.parseColor(AbfahrtenDaten.farbe(a)));
        text(a.linie, x, by + (badgeH - 13.2f) / 2f, badgeB, kompakt ? 10 : 11, FETT, WEISS, MITTE, 1, 0.6f);

        float rechtsB = kompakt ? 44 : 52;
        float tx = x + badgeB + 7;
        float tb = b - badgeB - 7 - rechtsB - 4;
        int deck = a.ausfall ? alpha(vorder, 0.55f) : vorder;
        if (Campus.istFavorit(favoriten, a.linie)) {
            text("★", tx, y + 1, 12, kompakt ? 10 : 11, FETT, gold, LINKS, 1, 1f);
            tx += 12;
            tb -= 12;
        }
        text(AbfahrtenDaten.kurzRichtung(a.richtung), tx, y + 1, tb, kompakt ? 11 : 12, MEDIUM, deck, LINKS, 1, 0.8f);
        if (!kompakt) {
            String unten = a.haltestelle + (a.gleis != null ? " · Gl. " + a.gleis : "");
            text(unten, tx, y + 16, tb, 9, NORMAL, gedimmt, LINKS, 1, 0.8f);
        }

        float rx = x + b - rechtsB;
        long min = (a.zeit - jetzt) / 60_000L;
        String oben = a.ausfall ? "fällt aus" : (min <= 0 ? "jetzt" : min + " Min");
        text(oben, rx, y + 1, rechtsB, kompakt ? 11 : 12, MEDIUM, a.ausfall ? rot : vorder, RECHTS, 1, 0.7f);
        String plan = Lageplan.uhrzeitText(a.geplant);
        if (a.verspaetung != null && a.verspaetung != 0) {
            String v = (a.verspaetung > 0 ? "+" : "") + a.verspaetung;
            float vb = breite(v, 9, MEDIUM);
            text(v, rx, y + (kompakt ? 14 : 16), rechtsB, 9, MEDIUM, a.verspaetung >= 3 ? rot : gruen, RECHTS, 1, 1f);
            text(plan, rx, y + (kompakt ? 14 : 16), rechtsB - vb - 3, 9, NORMAL, gedimmt, RECHTS, 1, 1f);
        } else {
            text(plan, rx, y + (kompakt ? 14 : 16), rechtsB, 9, NORMAL, gedimmt, RECHTS, 1, 1f);
        }
    }
}
