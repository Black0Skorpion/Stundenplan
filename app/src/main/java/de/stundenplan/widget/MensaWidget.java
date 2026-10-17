package de.stundenplan.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.List;

/**
 * Gemeinsame Basis der Mensa-Widgets. Mit den Pfeilen oben rechts blättert man durch die
 * Öffnungstage; um Mitternacht springt das Widget wieder auf den aktuellen Tag zurück.
 */
public abstract class MensaWidget extends AppWidgetProvider {

    static final String ZURUECK = "de.stundenplan.widget.MENSA_ZURUECK";
    static final String WEITER = "de.stundenplan.widget.MENSA_WEITER";
    private static final String PREFS = "mensa";

    protected abstract String modus();

    protected abstract int layout();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        imHintergrund(ctx, ids, true);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle neueOptionen) {
        imHintergrund(ctx, new int[]{id}, false);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) e.remove("v_" + id).remove("d_" + id);
        e.apply();
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String aktion = intent.getAction();
        if (ZURUECK.equals(aktion) || WEITER.equals(aktion)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                int v = versatz(ctx, id) + (WEITER.equals(aktion) ? 1 : -1);
                speichereVersatz(ctx, id, v);
                imHintergrund(ctx, new int[]{id}, true);
            }
            return;
        }
        super.onReceive(ctx, intent);
    }

    // ───────────────────────── Versatz je Widget ─────────────────────────

    private static int versatz(Context ctx, int id) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String heute = MensaDaten.iso(System.currentTimeMillis());
        if (!heute.equals(p.getString("d_" + id, ""))) return 0; // neuer Tag → wieder von vorn
        return p.getInt("v_" + id, 0);
    }

    private static void speichereVersatz(Context ctx, int id, int v) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("v_" + id, v)
                .putString("d_" + id, MensaDaten.iso(System.currentTimeMillis()))
                .apply();
    }

    // ───────────────────────── Zeichnen ─────────────────────────

    /** Netzwerk darf nicht im Hauptthread laufen: Arbeit in einen Hintergrund-Thread verlegen. */
    private void imHintergrund(Context ctx, int[] ids, boolean netz) {
        final PendingResult ergebnis = goAsync();
        final Context app = ctx.getApplicationContext();
        final String modus = modus();
        final int layout = layout();
        final Class<?> klasse = getClass();
        new Thread(() -> {
            try {
                AppWidgetManager mgr = AppWidgetManager.getInstance(app);
                for (int id : ids) aktualisiere(app, mgr, id, modus, layout, klasse, netz);
            } finally {
                ergebnis.finish();
            }
        }).start();
    }

    static void aktualisiere(Context ctx, AppWidgetManager mgr, int id, String modus, int layout,
                             Class<?> klasse, boolean netz) {
        long jetzt = System.currentTimeMillis();
        List<MensaDaten.Tag> tage = MensaDaten.ladeTage(ctx, netz);
        MensaDaten.Auswahl a = MensaDaten.waehleTag(tage, jetzt, versatz(ctx, id));
        speichereVersatz(ctx, id, a.versatz);
        MensaDaten.Plan plan = a.tag.geschlossen ? null : MensaDaten.ladePlan(ctx, a.tag.datum, netz);

        float[] g = StundenplanWidget.groesse(ctx, mgr, id, modus);
        boolean dunkel = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        float skala = Math.min(ctx.getResources().getDisplayMetrics().density,
                (float) Math.sqrt(2_500_000.0 / (g[0] * g[1])));

        for (int versuch = 0; versuch < 3; versuch++) {
            try {
                Bitmap bild = MensaZeichner.zeichnen(modus, g[0], g[1], skala, dunkel, jetzt, a, plan);
                RemoteViews rv = new RemoteViews(ctx.getPackageName(), layout);
                rv.setImageViewBitmap(R.id.bild, bild);
                // Tippen öffnet den Mensa-Reiter der App
                Intent web = new Intent(ctx, MainActivity.class).putExtra(MainActivity.EXTRA_REITER, MainActivity.MENSA)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                rv.setOnClickPendingIntent(R.id.wurzel, PendingIntent.getActivity(ctx, 100, web,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
                rv.setOnClickPendingIntent(R.id.zurueck, knopf(ctx, klasse, id, ZURUECK, 0));
                rv.setOnClickPendingIntent(R.id.weiter, knopf(ctx, klasse, id, WEITER, 1));
                mgr.updateAppWidget(id, rv);
                return;
            } catch (RuntimeException e) {
                skala *= 0.6f;
            }
        }
    }

    private static PendingIntent knopf(Context ctx, Class<?> klasse, int id, String aktion, int nr) {
        Intent i = new Intent(ctx, klasse).setAction(aktion);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        return PendingIntent.getBroadcast(ctx, id * 2 + nr, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Alle Mensa-Widgets neu zeichnen (z. B. aus der App heraus). Nicht im Hauptthread aufrufen. */
    static void alleAktualisieren(Context ctx, boolean netz) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        Object[][] arten = {
                {MensaKlein.class, Zeichner.KLEIN, R.layout.widget_mensa_klein},
                {MensaMittel.class, Zeichner.MITTEL, R.layout.widget_mensa},
                {MensaGross.class, Zeichner.GROSS, R.layout.widget_mensa}};
        for (Object[] art : arten) {
            Class<?> k = (Class<?>) art[0];
            for (int id : mgr.getAppWidgetIds(new ComponentName(ctx, k))) {
                aktualisiere(ctx, mgr, id, (String) art[1], (Integer) art[2], k, netz);
            }
        }
    }
}
