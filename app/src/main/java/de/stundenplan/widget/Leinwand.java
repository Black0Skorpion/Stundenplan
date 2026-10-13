package de.stundenplan.widget;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Gemeinsame Zeichen-Hilfen für alle Widgets (Text mit Umbruch/Kürzung, Formen). Maße in dp. */
abstract class Leinwand {

    static final Typeface NORMAL = Typeface.DEFAULT;
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface FETT = Typeface.DEFAULT_BOLD;
    static final int WEISS = Color.WHITE;

    static final int LINKS = 0, MITTE = 1, RECHTS = 2;

    final Canvas c;
    final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);

    Leinwand(Canvas c) {
        this.c = c;
    }

    static int alpha(int farbe, float a) {
        return Color.argb(Math.round(Color.alpha(farbe) * a), Color.red(farbe), Color.green(farbe), Color.blue(farbe));
    }

    void rundesRechteck(float x, float y, float b, float h, float r, int farbe) {
        fp.setStyle(Paint.Style.FILL);
        fp.setColor(farbe);
        c.drawRoundRect(new RectF(x, y, x + b, y + h), r, r, fp);
    }

    void linie(float x1, float y1, float x2, float y2, int farbe, float staerke) {
        fp.setStyle(Paint.Style.STROKE);
        fp.setStrokeWidth(staerke);
        fp.setColor(farbe);
        c.drawLine(x1, y1, x2, y2, fp);
    }

    void kreis(float cx, float cy, float r, int farbe) {
        fp.setStyle(Paint.Style.FILL);
        fp.setColor(farbe);
        c.drawCircle(cx, cy, r, fp);
    }

    float breite(String s, float groesse, Typeface schrift) {
        tp.setTypeface(schrift);
        tp.setTextSize(groesse);
        return tp.measureText(s);
    }

    private List<String> umbrechen(String s, float w) {
        List<String> zeilen = new ArrayList<>();
        for (String absatz : s.split("\n", -1)) {
            String aktuell = "";
            for (String wort : absatz.split(" ")) {
                String versuch = aktuell.isEmpty() ? wort : aktuell + " " + wort;
                if (tp.measureText(versuch) > w && !aktuell.isEmpty()) {
                    zeilen.add(aktuell);
                    aktuell = wort;
                } else {
                    aktuell = versuch;
                }
            }
            zeilen.add(aktuell);
        }
        return zeilen;
    }

    /**
     * Schreibt Text in ein Feld (x, y = obere linke Ecke, Breite w).
     * Zeilenhöhe = 1,2 × Schriftgröße. Zu lange Wörter werden bis minSkala verkleinert,
     * danach mit "…" gekürzt. Gibt die (benötigte) Höhe zurück.
     */
    float text(String s, float x, float y, float w, float groesse, Typeface schrift, int farbe,
               int ausrichtung, int maxZeilen, float minSkala, boolean zeichnen) {
        if (s == null || w <= 0) return 0;
        tp.setTypeface(schrift);
        tp.setTextSize(groesse);
        tp.setColor(farbe);
        float g = groesse;
        if (minSkala < 1f) {
            float breitestes = 0;
            if (maxZeilen <= 1) breitestes = tp.measureText(s.replace("\n", " "));
            else for (String wort : s.split("[ \n]")) breitestes = Math.max(breitestes, tp.measureText(wort));
            if (breitestes > w) {
                g = Math.max(groesse * minSkala, groesse * w / breitestes);
                tp.setTextSize(g);
            }
        }
        List<String> zeilen;
        if (maxZeilen <= 1) {
            zeilen = new ArrayList<>();
            zeilen.add(s.replace("\n", " "));
        } else {
            zeilen = umbrechen(s, w);
        }
        int anzahl = Math.max(1, Math.min(maxZeilen, zeilen.size()));
        if (zeilen.size() > anzahl) {
            List<String> gekuerzt = new ArrayList<>(zeilen.subList(0, anzahl));
            gekuerzt.set(anzahl - 1, gekuerzt.get(anzahl - 1) + " " + zeilen.get(anzahl) + "…");
            zeilen = gekuerzt;
        }
        float zeilenH = g * 1.2f;
        if (zeichnen) {
            Paint.FontMetrics fm = tp.getFontMetrics();
            for (int i = 0; i < anzahl; i++) {
                String zeile = TextUtils.ellipsize(zeilen.get(i), tp, w, TextUtils.TruncateAt.END).toString();
                float zw = tp.measureText(zeile);
                float zx = ausrichtung == MITTE ? x + (w - zw) / 2f : ausrichtung == RECHTS ? x + w - zw : x;
                float grundlinie = y + i * zeilenH + zeilenH / 2f - (fm.ascent + fm.descent) / 2f;
                c.drawText(zeile, zx, grundlinie, tp);
            }
        }
        return anzahl * zeilenH;
    }

    float text(String s, float x, float y, float w, float groesse, Typeface schrift, int farbe,
               int ausrichtung, int maxZeilen, float minSkala) {
        return text(s, x, y, w, groesse, schrift, farbe, ausrichtung, maxZeilen, minSkala, true);
    }
}
