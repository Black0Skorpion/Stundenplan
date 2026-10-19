package de.stundenplan.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.widget.RemoteViews;

/** Farbschema der Widgets: System, Hell, Dunkel oder Transparent (ohne Hintergrund). */
public final class WidgetThema {
    private WidgetThema() {}

    static final String[] NAMEN = {"System", "Hell", "Dunkel", "Transparent"};
    static final int SYSTEM = 0, HELL = 1, DUNKEL = 2, TRANSPARENT = 3;
    private static final String PREFS = "widgets";
    private static final String SCHLUESSEL = "thema";

    static int wahl(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(SCHLUESSEL, SYSTEM);
    }

    static void setzen(Context ctx, int w) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putInt(SCHLUESSEL, w).apply();
        // Alle Widgets sofort neu zeichnen (nicht im Hauptthread)
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            StundenplanWidget.alleAktualisieren(app);
            MensaWidget.alleAktualisieren(app, false);
            AbfahrtenWidget.alleAktualisieren(app, false);
        }).start();
    }

    /** Dunkle Schrift-/Flächenfarben verwenden? (Transparent: wie das System) */
    static boolean dunkel(Context ctx) {
        int w = wahl(ctx);
        if (w == HELL) return false;
        if (w == DUNKEL) return true;
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Hintergrund des Widgets (Element R.id.wurzel) passend zur Wahl setzen */
    static void hintergrund(Context ctx, RemoteViews rv) {
        int w = wahl(ctx);
        int res = w == HELL ? R.drawable.widget_hintergrund_hell
                : w == DUNKEL ? R.drawable.widget_hintergrund_dunkel
                : w == TRANSPARENT ? R.drawable.widget_hintergrund_transparent
                : R.drawable.widget_hintergrund;
        rv.setInt(R.id.wurzel, "setBackgroundResource", res);
    }
}
