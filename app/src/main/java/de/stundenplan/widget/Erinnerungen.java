package de.stundenplan.widget;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

import java.util.List;

/**
 * Mitteilung vor jeder Veranstaltung (abschaltbar in der App). Es ist immer nur der nächste
 * Wecker gestellt; wenn er klingelt, wird die Mitteilung gezeigt und der nächste gestellt.
 * Die Mitteilung zählt bis zum Beginn herunter.
 */
public final class Erinnerungen extends BroadcastReceiver {

    static final String AKTION = "de.stundenplan.widget.ERINNERUNG";
    static final String KANAL = "erinnerungen";
    static final String AN = "erinnerungen_an";
    static final String VORLAUF = "erinnerungen_vorlauf";
    static final int[] VORLAUF_OPTIONEN = {5, 10, 15, 20, 30, 45, 60};

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(StundenplanWidget.EINSTELLUNGEN, Context.MODE_PRIVATE);
    }

    static boolean an(Context ctx) { return prefs(ctx).getBoolean(AN, false); }

    static int vorlauf(Context ctx) { return prefs(ctx).getInt(VORLAUF, 10); }

    static boolean erlaubt(Context ctx) {
        if (Build.VERSION.SDK_INT >= 33) {
            return ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private static PendingIntent wecker(Context ctx) {
        Intent i = new Intent(ctx, Erinnerungen.class).setAction(AKTION);
        return PendingIntent.getBroadcast(ctx, 7, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Stellt den Wecker für die nächste Erinnerung (oder löscht ihn, wenn ausgeschaltet). */
    static void planen(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(wecker(ctx));
        if (!an(ctx) || !erlaubt(ctx)) return;
        long jetzt = System.currentTimeMillis();
        long vorlauf = vorlauf(ctx) * 60_000L;
        Long naechste = null;
        for (Termin t : Daten.laden(ctx)) {
            long wann = t.start - vorlauf;
            if (wann > jetzt + 5_000) { naechste = wann; break; }
        }
        if (naechste == null) return;
        boolean genau = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (genau) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, naechste, wecker(ctx));
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, naechste, wecker(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String aktion = intent.getAction();
        if (AKTION.equals(aktion)) zeigen(ctx);
        // Nach dem Klingeln, nach einem Neustart oder Update: nächste Erinnerung stellen
        planen(ctx);
        if (Intent.ACTION_BOOT_COMPLETED.equals(aktion) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(aktion)) {
            StundenplanWidget.alleAktualisieren(ctx);
        }
    }

    /** Zeigt die Mitteilung(en) für alle Termine, die jetzt in „Vorlauf“ Minuten beginnen. */
    private static void zeigen(Context ctx) {
        if (!an(ctx) || !erlaubt(ctx)) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(KANAL) == null) {
            NotificationChannel k = new NotificationChannel(KANAL, "Erinnerungen vor Veranstaltungen",
                    NotificationManager.IMPORTANCE_HIGH);
            k.setDescription("Mitteilung mit Raum vor jeder Veranstaltung");
            nm.createNotificationChannel(k);
        }
        long jetzt = System.currentTimeMillis();
        int vorlauf = vorlauf(ctx);
        List<Termin> alle = Daten.laden(ctx);
        for (Termin t : alle) {
            long wann = t.start - vorlauf * 60_000L;
            // Toleranz: Wecker kann ohne „genaue Wecker“-Erlaubnis etwas später kommen
            if (wann > jetzt + 60_000L || t.start < jetzt) continue;
            String[] kurs = Konfig.kurs(t.titel);
            String[] typ = Konfig.typ(t.typ);
            Intent oeffnen = LageplanActivity.fuerTermin(ctx, t);
            PendingIntent pi = PendingIntent.getActivity(ctx, (int) (t.start / 1000L % 100000), oeffnen,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(ctx, KANAL)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setColor(Color.parseColor(kurs[1]))
                    .setContentTitle(kurs[0] + " · " + t.raum)
                    .setContentText(typ[0] + " · " + Lageplan.uhrzeitText(t.start) + "–" + Lageplan.uhrzeitText(t.ende))
                    .setWhen(t.start)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setTimeoutAfter(Math.max(60_000L, t.ende - jetzt))
                    .setCategory(Notification.CATEGORY_EVENT)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify((int) (t.start / 1000L % 100000) + Math.abs(t.titel.hashCode() % 1000), n);
        }
    }
}
