package de.stundenplan.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.List;

/**
 * Widget „Abfahrten Golm“: nächste Züge und Busse ab Golm Bhf und Science Park/Uni.
 * Daten werden höchstens alle 5 Minuten geladen; der Minuten-Wecker zeichnet dazwischen neu.
 */
public class AbfahrtenWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        imHintergrund(ctx, true);
        StundenplanWidget.planeNaechsteAktualisierung(ctx);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle neueOptionen) {
        imHintergrund(ctx, false);
    }

    private void imHintergrund(Context ctx, boolean netz) {
        final PendingResult ergebnis = goAsync();
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                alleAktualisieren(app, netz);
            } finally {
                ergebnis.finish();
            }
        }).start();
    }

    static boolean vorhanden(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, AbfahrtenWidget.class)).length > 0;
    }

    /** Nicht im Hauptthread aufrufen (Netzwerk). netz=false: nur neu zeichnen, außer die Daten sind zu alt. */
    static void alleAktualisieren(Context ctx, boolean netz) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, AbfahrtenWidget.class));
        if (ids.length == 0) return;
        boolean laden = netz || AbfahrtenDaten.alter(ctx) > AbfahrtenDaten.MAX_ALTER;
        List<AbfahrtenDaten.Abfahrt> liste = AbfahrtenDaten.laden(ctx, laden);
        boolean dunkel = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        long jetzt = System.currentTimeMillis();
        Intent app = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent oeffnen = PendingIntent.getActivity(ctx, 300, app, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        for (int id : ids) {
            float[] g = StundenplanWidget.groesse(ctx, mgr, id, Zeichner.MITTEL);
            float skala = Math.min(ctx.getResources().getDisplayMetrics().density,
                    (float) Math.sqrt(2_500_000.0 / (g[0] * g[1])));
            for (int versuch = 0; versuch < 3; versuch++) {
                try {
                    Bitmap bild = AbfahrtenZeichner.zeichnen(g[0], g[1], skala, dunkel, jetzt, liste);
                    RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_abfahrten);
                    rv.setImageViewBitmap(R.id.bild, bild);
                    rv.setOnClickPendingIntent(R.id.wurzel, oeffnen);
                    mgr.updateAppWidget(id, rv);
                    break;
                } catch (RuntimeException e) {
                    skala *= 0.6f;
                }
            }
        }
    }
}
