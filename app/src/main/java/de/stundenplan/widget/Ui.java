package de.stundenplan.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Kleine Bausteine für die Oberfläche (Farben wie in der iOS-App, Karten, Knöpfe). */
final class Ui {
    final Context ctx;
    final float d;
    final boolean dunkel;
    final int bg, karte, vorder, gedimmt, akzent, linie, rot;

    Ui(Context ctx) {
        this.ctx = ctx;
        d = ctx.getResources().getDisplayMetrics().density;
        dunkel = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        bg = Color.parseColor(dunkel ? "#000000" : "#F2F2F7");
        karte = Color.parseColor(dunkel ? "#1C1C1E" : "#FFFFFF");
        vorder = Color.parseColor(dunkel ? "#F1EFE8" : "#1C1C1E");
        gedimmt = Color.parseColor(dunkel ? "#8E8D86" : "#6D6C68");
        akzent = Color.parseColor(dunkel ? "#85B7EB" : "#185FA5");
        linie = Color.parseColor(dunkel ? "#38383A" : "#D8D7D2");
        rot = Color.parseColor("#E24B4A");
    }

    int dp(float v) { return Math.round(v * d); }

    GradientDrawable rund(int farbe, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(farbe);
        g.setCornerRadius(dp(radius));
        return g;
    }

    /** Antippbarer Hintergrund mit Welle */
    android.graphics.drawable.Drawable tippbar(int farbe, float radius) {
        return new RippleDrawable(ColorStateList.valueOf(Leinwand.alpha(vorder, 0.15f)), rund(farbe, radius), rund(Color.WHITE, radius));
    }

    TextView text(String s, float groesse, int farbe) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(groesse);
        t.setTextColor(farbe);
        return t;
    }

    TextView fett(String s, float groesse, int farbe) {
        TextView t = text(s, groesse, farbe);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Große Überschrift einer Seite */
    TextView titel(String s) {
        TextView t = fett(s, 30, vorder);
        t.setPadding(dp(16), dp(12), dp(16), dp(6));
        return t;
    }

    /** Abschnittsüberschrift wie in iOS-Listen */
    TextView abschnitt(String s) {
        TextView t = text(s.toUpperCase(java.util.Locale.GERMANY), 12.5f, gedimmt);
        t.setPadding(dp(20), dp(18), dp(20), dp(6));
        return t;
    }

    TextView fuss(String s) {
        TextView t = text(s, 12.5f, gedimmt);
        t.setPadding(dp(20), dp(6), dp(20), dp(4));
        return t;
    }

    /** Weiße/graue Karte mit Innenabstand, volle Breite mit Rand */
    LinearLayout gruppe() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rund(karte, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        l.setLayoutParams(lp);
        return l;
    }

    /** Zeile in einer Gruppe (antippbar, wenn klick != null) */
    LinearLayout zeile(View.OnClickListener klick) {
        LinearLayout z = new LinearLayout(ctx);
        z.setOrientation(LinearLayout.HORIZONTAL);
        z.setGravity(Gravity.CENTER_VERTICAL);
        z.setMinimumHeight(dp(46));
        z.setPadding(dp(16), dp(8), dp(16), dp(8));
        if (klick != null) {
            z.setOnClickListener(klick);
            z.setBackground(tippbar(Color.TRANSPARENT, 12));
        }
        return z;
    }

    /** Einfache Zeile mit Text (blau, wenn antippbar) */
    LinearLayout knopfZeile(String s, View.OnClickListener klick) {
        LinearLayout z = zeile(klick);
        TextView t = text(s, 16, klick != null ? akzent : vorder);
        z.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return z;
    }

    /** Zeile „Beschriftung … Wert“ */
    LinearLayout wertZeile(String links, String rechts, View.OnClickListener klick) {
        LinearLayout z = zeile(klick);
        TextView a = text(links, 16, vorder);
        z.addView(a, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView b = text(rechts, 15, gedimmt);
        b.setGravity(Gravity.END);
        z.addView(b);
        return z;
    }

    View trenner() {
        View v = new View(ctx);
        v.setBackgroundColor(linie);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
        lp.leftMargin = dp(16);
        v.setLayoutParams(lp);
        return v;
    }

    /** Fügt Zeilen mit Trennlinien in eine Gruppe ein */
    void fuellen(LinearLayout gruppe, View... zeilen) {
        for (View z : zeilen) {
            if (z == null) continue;
            if (gruppe.getChildCount() > 0) gruppe.addView(trenner());
            gruppe.addView(z);
        }
    }

    /** Kleiner Knopf (Kapsel) */
    TextView kapsel(String s, int farbe, boolean gefuellt, View.OnClickListener klick) {
        TextView t = text(s, 14, gefuellt ? Color.WHITE : farbe);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setGravity(Gravity.CENTER);
        t.setBackground(tippbar(gefuellt ? farbe : Leinwand.alpha(farbe, 0.14f), 16));
        if (klick != null) t.setOnClickListener(klick);
        return t;
    }

    Button knopf(String s, View.OnClickListener klick) {
        Button b = new Button(ctx);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(klick);
        return b;
    }

    LinearLayout.LayoutParams breit() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams mitRand(int l, int o, int r, int u) {
        LinearLayout.LayoutParams lp = breit();
        lp.setMargins(dp(l), dp(o), dp(r), dp(u));
        return lp;
    }
}
