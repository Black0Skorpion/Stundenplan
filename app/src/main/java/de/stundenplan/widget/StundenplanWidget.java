package de.stundenplan.widget;

import android.app.AlarmManager;
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

/** Gemeinsame Basis der drei Widgets. Jede Größe ist eine eigene Unterklasse. */
public abstract class StundenplanWidget extends AppWidgetProvider {

    /** "klein", "mittel" oder "gross" */
    protected abstract String modus();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        List<Termin> alle = Daten.laden(ctx);
        for (int id : ids) aktualisiere(ctx, mgr, id, modus(), alle);
        planeNaechsteAktualisierung(ctx);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle neueOptionen) {
        aktualisiere(ctx, mgr, id, modus(), Daten.laden(ctx));
    }

    @Override
    public void onEnabled(Context ctx) {
        planeNaechsteAktualisierung(ctx);
    }

    // ───────────────────────── Aktualisieren ─────────────────────────

    private static final Class<?>[] ALLE_GROESSEN = {WidgetKlein.class, WidgetMittel.class, WidgetGross.class};

    /** Aktualisiert alle Stundenplan-Widgets und plant die nächste Aktualisierung. */
    public static void alleAktualisieren(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        List<Termin> alle = Daten.laden(ctx);
        boolean irgendeins = false;
        for (Class<?> k : ALLE_GROESSEN) {
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, k));
            String modus = k == WidgetKlein.class ? Zeichner.KLEIN : k == WidgetMittel.class ? Zeichner.MITTEL : Zeichner.GROSS;
            for (int id : ids) {
                aktualisiere(ctx, mgr, id, modus, alle);
                irgendeins = true;
            }
        }
        if (irgendeins || AbfahrtenWidget.vorhanden(ctx)) planeNaechsteAktualisierung(ctx);
    }

    /** Aktuelle Widget-Größe in dp: { Breite, Höhe }. */
    static float[] groesse(Context ctx, AppWidgetManager mgr, int id, String modus) {
        Bundle o = mgr.getAppWidgetOptions(id);
        boolean hochkant = ctx.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
        float b = hochkant ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
        float h = hochkant ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        if (b <= 0 || h <= 0) {
            b = Zeichner.KLEIN.equals(modus) ? 160 : 340;
            h = Zeichner.GROSS.equals(modus) ? 356 : 160;
        }
        return new float[]{b, h};
    }

    static void aktualisiere(Context ctx, AppWidgetManager mgr, int id, String modus, List<Termin> alle) {
        float[] g = groesse(ctx, mgr, id, modus);
        float b = g[0], h = g[1];

        boolean dunkel = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        float dichte = ctx.getResources().getDisplayMetrics().density;
        // Bildgröße begrenzen, damit Android das Bild sicher an den Startbildschirm übergeben kann
        float skala = Math.min(dichte, (float) Math.sqrt(2_500_000.0 / (b * h)));
        long jetzt = System.currentTimeMillis();

        for (int versuch = 0; versuch < 3; versuch++) {
            try {
                Zeichner.Ziele ziele = new Zeichner.Ziele();
                Bitmap bild = Zeichner.zeichnenMitZielen(modus, b, h, skala, dunkel, jetzt, alle, ziele);
                int layout = Zeichner.KLEIN.equals(modus) ? R.layout.widget_klein
                        : Zeichner.MITTEL.equals(modus) ? R.layout.widget_mittel : R.layout.widget_gross;
                RemoteViews rv = new RemoteViews(ctx.getPackageName(), layout);
                rv.setImageViewBitmap(R.id.bild, bild);
                PendingIntent planer = tippAktion(ctx); // öffnet die App
                rv.setOnClickPendingIntent(R.id.wurzel, planer);
                if (Zeichner.KLEIN.equals(modus) || Zeichner.MITTEL.equals(modus)) {
                    rv.setOnClickPendingIntent(R.id.zone_karte,
                            ziele.fokus != null ? lageplan(ctx, id, 1, LageplanActivity.fuerTermin(ctx, ziele.fokus)) : planer);
                }
                if (Zeichner.MITTEL.equals(modus)) {
                    rv.setOnClickPendingIntent(R.id.zone_kopf, planer);
                    int[] zeilen = {R.id.zone_z1, R.id.zone_z2, R.id.zone_z3, R.id.zone_z4};
                    for (int i = 0; i < zeilen.length; i++) {
                        rv.setOnClickPendingIntent(zeilen[i], i < ziele.zeilen.size()
                                ? lageplan(ctx, id, 2 + i, LageplanActivity.fuerTermin(ctx, ziele.zeilen.get(i))) : planer);
                    }
                }
                if (Zeichner.GROSS.equals(modus)) {
                    rv.setOnClickPendingIntent(R.id.zone_kopf, planer);
                    int[] tage = {R.id.zone_t1, R.id.zone_t2, R.id.zone_t3, R.id.zone_t4, R.id.zone_t5};
                    for (int i = 0; i < tage.length; i++) {
                        rv.setOnClickPendingIntent(tage[i], ziele.tagHatTermine[i]
                                ? lageplan(ctx, id, 10 + i, LageplanActivity.fuerTag(ctx, ziele.tage[i])) : planer);
                    }
                }
                mgr.updateAppWidget(id, rv);
                return;
            } catch (RuntimeException e) {
                skala *= 0.6f; // Bild zu groß → kleiner erneut versuchen
            }
        }
    }

    private static PendingIntent lageplan(Context ctx, int widgetId, int nr, Intent i) {
        return PendingIntent.getActivity(ctx, widgetId * 20 + nr, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static final String EINSTELLUNGEN = "einstellungen";
    static final String PLANER_LINK = "planer_link";

    /** Eigener Planer-Link aus der App, sonst der Standard aus Konfig.TAP_URL. */
    static String planerLink(Context ctx) {
        String eigener = ctx.getSharedPreferences(EINSTELLUNGEN, Context.MODE_PRIVATE).getString(PLANER_LINK, "");
        if (eigener != null && eigener.startsWith("http")) return eigener;
        return Konfig.TAP_URL;
    }

    /** Tippen auf Kopfzeile/leere Fläche: öffnet die App (nie direkt den Planer). */
    private static PendingIntent tippAktion(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class).putExtra(MainActivity.EXTRA_REITER, MainActivity.PLAN);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Plant die nächste Aktualisierung zum nächsten Minutenwechsel. Der Wecker weckt das Handy
     * nicht auf (RTC): Er läuft nur, solange das Handy ohnehin wach ist, und kostet kaum Akku.
     */
    static void planeNaechsteAktualisierung(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, Aktualisierer.class).setAction(Aktualisierer.AKTION);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long jetzt = System.currentTimeMillis();
        long naechsteMinute = (jetzt / 60000L + 1) * 60000L;
        am.setWindow(AlarmManager.RTC, naechsteMinute, 15_000L, pi);
    }

    static void abbrechen(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, Aktualisierer.class).setAction(Aktualisierer.AKTION);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
    }

    @Override
    public void onDisabled(Context ctx) {
        // Letztes Widget dieser Größe entfernt: Wecker nur stoppen, wenn gar keins mehr da ist
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        for (Class<?> k : ALLE_GROESSEN) {
            if (mgr.getAppWidgetIds(new ComponentName(ctx, k)).length > 0) return;
        }
        if (AbfahrtenWidget.vorhanden(ctx)) return;
        abbrechen(ctx);
    }
}
