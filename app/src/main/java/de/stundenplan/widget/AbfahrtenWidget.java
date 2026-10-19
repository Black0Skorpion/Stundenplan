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
 * Widget „Abfahrten“: nächste Züge und Busse am gewählten Campus, priorisierte Linien immer dabei.
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
        String campus = Campus.aktuell(ctx);
        List<String> favoriten = Campus.favoriten(ctx);
        List<AbfahrtenDaten.Abfahrt> liste = AbfahrtenDaten.laden(ctx, campus, laden);
        boolean dunkel = WidgetThema.dunkel(ctx);
        long jetzt = System.currentTimeMillis();
        Intent app = new Intent(ctx, MainActivity.class).putExtra(MainActivity.EXTRA_REITER, MainActivity.ABFAHRTEN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent oeffnen = PendingIntent.getActivity(ctx, 300, app, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        for (int id : ids) {
            float[] g = StundenplanWidget.groesse(ctx, mgr, id, Zeichner.MITTEL);
            float skala = Math.min(ctx.getResources().getDisplayMetrics().density,
                    (float) Math.sqrt(2_500_000.0 / (g[0] * g[1])));
            for (int versuch = 0; versuch < 3; versuch++) {
                try {
                    Bitmap bild = AbfahrtenZeichner.zeichnen(g[0], g[1], skala, dunkel, jetzt, liste, campus, favoriten);
                    RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_abfahrten);
                WidgetThema.hintergrund(ctx, rv);
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
