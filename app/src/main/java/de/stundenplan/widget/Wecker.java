package de.stundenplan.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

/**
 * Wecker vor der ersten Veranstaltung des Tages (wie iOS). Es ist immer nur der nächste Wecker gestellt
 * (als echter Android-Wecker, erscheint auch in der Statusleiste); beim Klingeln öffnet sich WeckerActivity.
 * Ausgefallene Termine zählen nicht.
 */
public final class Wecker {
    private Wecker() {}

    static final String AN = "wecker_an";
    static final String VORLAUF = "wecker_vorlauf";
    static final String FRUEHESTENS = "wecker_fruehestens";
    static final int[] VORLAUF_OPTIONEN = {30, 45, 60, 75, 90, 105, 120, 150, 180};
    /** Minuten nach Mitternacht, 0 = egal */
    static final int[] FRUEHESTENS_OPTIONEN = {0, 300, 330, 360, 390, 420, 450};

    static SharedPreferences prefs(Context ctx) { return Erinnerungen.prefs(ctx); }

    static boolean an(Context ctx) { return prefs(ctx).getBoolean(AN, false); }

    static int vorlauf(Context ctx) { return prefs(ctx).getInt(VORLAUF, 90); }

    static int fruehestens(Context ctx) { return prefs(ctx).getInt(FRUEHESTENS, 0); }

    static String vorlaufText(int m) {
        int h = m / 60, r = m % 60;
        if (h == 0) return m + " Min";
        return r == 0 ? h + " Std" : h + " Std " + r + " Min";
    }

    static String uhrText(int minuten) {
        return minuten == 0 ? "egal" : String.format(java.util.Locale.GERMANY, "%d:%02d", minuten / 60, minuten % 60);
    }

    /** Nächste Weckzeit: [0] = Weckzeit, [1] = Beginn der ersten Veranstaltung; null = keine in 14 Tagen */
    static long[] naechste(Context ctx) {
        return naechste(Daten.laden(ctx), vorlauf(ctx), fruehestens(ctx), System.currentTimeMillis());
    }

    static long[] naechste(List<Termin> termine, int vorlauf, int fruehestens, long jetzt) {
        long bis = jetzt + 14 * 86400_000L;
        long letzterTag = -1;
        for (Termin t : termine) {            // sortiert, ohne ausgefallene
            if (t.ausfall || t.start <= jetzt || t.start > bis) continue;
            long tag = MensaDaten.tagesBeginn(t.start);
            if (tag == letzterTag) continue;  // nur die erste Veranstaltung des Tages
            letzterTag = tag;
            long wann = t.start - vorlauf * 60_000L;
            if (fruehestens > 0) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(tag);
                c.add(Calendar.MINUTE, fruehestens);
                long grenze = c.getTimeInMillis();
                if (wann < grenze) wann = Math.min(grenze, t.start - 15 * 60_000L);
            }
            if (wann > jetzt + 5_000) return new long[]{wann, t.start};
        }
        return null;
    }

    private static PendingIntent klingeln(Context ctx) {
        Intent i = new Intent(ctx, WeckerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, 21, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static boolean darfStellen(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am != null && (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms());
    }

    /** Stellt den nächsten Wecker (oder löscht ihn). */
    static void planen(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(klingeln(ctx));
        if (!an(ctx)) return;
        long[] n = naechste(ctx);
        if (n == null) return;
        Intent zeigen = new Intent(ctx, MainActivity.class);
        PendingIntent info = PendingIntent.getActivity(ctx, 22, zeigen, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (darfStellen(ctx)) {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(n[0], info), klingeln(ctx));
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n[0], klingeln(ctx));
        }
    }

    /** Erste Veranstaltung ab jetzt (für die Anzeige beim Klingeln) */
    static Termin ersteHeute(Context ctx) {
        long jetzt = System.currentTimeMillis();
        for (Termin t : Daten.laden(ctx)) if (t.start > jetzt - 30 * 60_000L && t.start < jetzt + 6 * 3600_000L) return t;
        return null;
    }
}
