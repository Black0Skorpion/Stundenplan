package de.stundenplan.widget;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * „Live Update“ (Android 16): die laufende bzw. nächste Veranstaltung als hervorgehobene Mitteilung
 * (Chip in der Statusleiste, Sperrbildschirm; Samsung: Now Bar) – das Gegenstück zur Dynamic Island.
 * Erst ab Beginn (davor gibt es das Widget): Raum, Ende und Fortschritt; zum Ende verschwindet es von selbst.
 */
public final class LiveUpdate extends BroadcastReceiver {

    static final String AKTION = "de.stundenplan.widget.LIVE_UPDATE";
    static final String KANAL = "live";
    static final String AN = "live_an";
    static final int ID = 9001;
    /** Wie lange vor Beginn es erscheint (0 = erst zum Beginn, wie in der iOS-App) */
    static final long VORHER = 0;

    static boolean verfuegbar() { return Build.VERSION.SDK_INT >= 36; }

    static boolean an(Context ctx) { return Erinnerungen.prefs(ctx).getBoolean(AN, true); }

    private static PendingIntent wecker(Context ctx) {
        Intent i = new Intent(ctx, LiveUpdate.class).setAction(AKTION);
        return PendingIntent.getBroadcast(ctx, 9, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        planen(ctx);
    }

    /** Mitteilung zeigen/aktualisieren und den nächsten Zeitpunkt stellen */
    static void planen(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (am == null || nm == null) return;
        am.cancel(wecker(ctx));
        if (!verfuegbar() || !an(ctx) || !Erinnerungen.erlaubt(ctx)) {
            nm.cancel(ID);
            return;
        }
        long jetzt = System.currentTimeMillis();
        Termin t = null;
        for (Termin x : Daten.laden(ctx)) if (x.ende > jetzt) { t = x; break; }
        if (t == null) { nm.cancel(ID); return; }

        long naechstes;
        if (jetzt < t.start - VORHER) {
            nm.cancel(ID);
            naechstes = t.start - VORHER;
        } else {
            zeigen(ctx, nm, t, jetzt);
            naechstes = jetzt < t.start ? t.start : Math.min(t.ende, jetzt + 5 * 60_000L);
            if (naechstes >= t.ende) naechstes = t.ende + 1000;
        }
        boolean genau = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (genau) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, naechstes, wecker(ctx));
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, naechstes, wecker(ctx));
    }

    @android.annotation.TargetApi(36)
    private static void zeigen(Context ctx, NotificationManager nm, Termin t, long jetzt) {
        if (Build.VERSION.SDK_INT < 36) return;
        if (nm.getNotificationChannel(KANAL) == null) {
            NotificationChannel k = new NotificationChannel(KANAL, "Live Update (laufende Veranstaltung)", NotificationManager.IMPORTANCE_DEFAULT);
            k.setDescription("Countdown bis Beginn, während der Vorlesung Ende und Fortschritt");
            k.setSound(null, null);
            k.enableVibration(false);
            nm.createNotificationChannel(k);
        }
        String[] kurs = Konfig.kurs(t.titel);
        int farbe = Color.parseColor(kurs[1]);
        boolean laeuft = jetzt >= t.start;
        int dauer = (int) Math.max(1, (t.ende - t.start) / 60_000L);
        int vorbei = laeuft ? (int) Math.min(dauer, (jetzt - t.start) / 60_000L) : 0;

        List<Notification.ProgressStyle.Segment> teile = new ArrayList<>();
        teile.add(new Notification.ProgressStyle.Segment(dauer).setColor(farbe));
        Notification.ProgressStyle stil = new Notification.ProgressStyle()
                .setProgressSegments(teile)
                .setProgress(vorbei);

        PendingIntent oeffnen = PendingIntent.getActivity(ctx, 90, LageplanActivity.fuerTermin(ctx, t),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String raum = t.raum.isEmpty() ? "" : t.raum;
        Notification.Builder b = new Notification.Builder(ctx, KANAL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(kurs[0] + (raum.isEmpty() ? "" : " · " + raum))
                .setContentIntent(oeffnen)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_EVENT)
                .setTimeoutAfter(Math.max(60_000L, t.ende - jetzt))
                .setStyle(stil);
        // Als „Live Update“ hervorheben (Notification.EXTRA_REQUEST_PROMOTED_ONGOING)
        android.os.Bundle hervorheben = new android.os.Bundle();
        hervorheben.putBoolean("android.requestPromotedOngoing", true);
        b.addExtras(hervorheben);
        if (laeuft) {
            // Während der Vorlesung: kein Countdown, nur Ende und Fortschritt
            b.setContentText(Konfig.typ(t.typ)[0] + " · bis " + Lageplan.uhrzeitText(t.ende))
                    .setShowWhen(false)
                    .setShortCriticalText("bis " + Lageplan.uhrzeitText(t.ende));
        } else {
            b.setContentText(Konfig.typ(t.typ)[0] + " · " + Lageplan.uhrzeitText(t.start) + "–" + Lageplan.uhrzeitText(t.ende))
                    .setWhen(t.start)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setShortCriticalText(raum.isEmpty() ? kurs[0] : raum);
        }
        nm.notify(ID, b.build());
    }
}
