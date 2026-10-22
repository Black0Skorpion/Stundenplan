package de.stundenplan.widget;

import android.app.AlarmManager;
import android.app.AutomaticZenRule;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.service.notification.Condition;

import java.util.List;

/**
 * „Nicht stören in Veranstaltungen“ (wie in der iOS-App, aber ohne Kurzbefehl-Umweg):
 * Ab Android 10 legt die App einen eigenen Modus „Vorlesung (Stundenplan)“ an, den man in den
 * Android-Einstellungen anpassen kann (wer/was trotzdem durchkommt), und schaltet ihn zu Beginn
 * jedes Veranstaltungsblocks ein und zum Ende wieder aus. Darunter: normales „Nicht stören“ (Priorität).
 * Braucht die Erlaubnis „Zugriff auf Nicht stören“.
 */
public final class NichtStoeren extends BroadcastReceiver {

    static final String AN = "fokus_an";
    static final String VORLAUF = "fokus_vorlauf";
    static final int[] VORLAUF_OPTIONEN = {0, 5, 10, 15};
    private static final String REGEL_ID = "fokus_regel";
    private static final String SELBST_AN = "fokus_selbst_an";
    private static final String AKTION = "de.stundenplan.widget.NICHT_STOEREN";
    private static final Uri BEDINGUNG = Uri.parse("condition://de.stundenplan.widget/vorlesung");
    /** Direkt aufeinanderfolgende Veranstaltungen (Lücke bis dahin) zählen als ein Block */
    private static final long LUECKE = 20 * 60_000L;

    static SharedPreferences prefs(Context ctx) { return Erinnerungen.prefs(ctx); }

    static boolean an(Context ctx) { return prefs(ctx).getBoolean(AN, false); }

    static int vorlauf(Context ctx) { return prefs(ctx).getInt(VORLAUF, 0); }

    static boolean erlaubt(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    /** Eigener Modus möglich (Android 10+) */
    static boolean eigenerModus() { return Build.VERSION.SDK_INT >= 29; }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        planen(ctx);
    }

    private static PendingIntent wecker(Context ctx) {
        Intent i = new Intent(ctx, NichtStoeren.class).setAction(AKTION);
        return PendingIntent.getBroadcast(ctx, 31, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Zustand jetzt setzen und den nächsten Wechsel stellen */
    static void planen(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(wecker(ctx));
        if (!an(ctx) || !erlaubt(ctx)) {
            setzen(ctx, false);
            return;
        }
        long jetzt = System.currentTimeMillis();
        long vor = vorlauf(ctx) * 60_000L;
        // Blöcke (Beginn − Vorlauf bis Ende), direkt aufeinanderfolgende zusammengefasst
        List<Termin> termine = Daten.laden(ctx);   // sortiert, ohne ausgefallene
        long blockVon = -1, blockBis = -1, naechstes = -1;
        boolean drin = false;
        for (Termin t : termine) {
            if (t.ende <= jetzt) continue;
            long von = t.start - vor;
            if (blockVon < 0) {
                blockVon = von;
                blockBis = t.ende;
            } else if (von <= blockBis + LUECKE) {
                blockBis = Math.max(blockBis, t.ende);
            } else {
                break;
            }
        }
        if (blockVon >= 0) {
            drin = jetzt >= blockVon && jetzt < blockBis;
            naechstes = drin ? blockBis : blockVon;
        }
        setzen(ctx, drin);
        if (naechstes > 0) {
            boolean genau = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (genau) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, naechstes + 1000, wecker(ctx));
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, naechstes + 1000, wecker(ctx));
        }
    }

    /** Modus ein- bzw. ausschalten (nur ausschalten, was die App selbst eingeschaltet hat) */
    private static void setzen(Context ctx, boolean ein) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || !nm.isNotificationPolicyAccessGranted()) return;
        try {
            if (eigenerModus()) {
                String id = regel(ctx, nm, ein);
                if (id == null) return;
                nm.setAutomaticZenRuleState(id, new Condition(BEDINGUNG, "Veranstaltung",
                        ein ? Condition.STATE_TRUE : Condition.STATE_FALSE));
            } else {
                boolean selbst = prefs(ctx).getBoolean(SELBST_AN, false);
                if (ein && !selbst && nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                    prefs(ctx).edit().putBoolean(SELBST_AN, true).apply();
                } else if (!ein && selbst) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                    prefs(ctx).edit().putBoolean(SELBST_AN, false).apply();
                }
            }
        } catch (Exception ignored) {
            // z. B. Erlaubnis inzwischen entzogen
        }
    }

    /** Eigenen Modus anlegen (falls nötig) und seine Kennung liefern */
    @android.annotation.TargetApi(29)
    private static String regel(Context ctx, NotificationManager nm, boolean anlegen) {
        String id = prefs(ctx).getString(REGEL_ID, null);
        if (id != null && nm.getAutomaticZenRule(id) != null) return id;
        if (!anlegen) return null;
        AutomaticZenRule r = new AutomaticZenRule("Vorlesung (Stundenplan)", null,
                new ComponentName(ctx, MainActivity.class), BEDINGUNG, null,
                NotificationManager.INTERRUPTION_FILTER_PRIORITY, true);
        id = nm.addAutomaticZenRule(r);
        prefs(ctx).edit().putString(REGEL_ID, id).apply();
        return id;
    }

    /** Ausschalten der Funktion: Modus aus und wieder entfernen */
    static void entfernen(Context ctx) {
        setzen(ctx, false);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        String id = prefs(ctx).getString(REGEL_ID, null);
        if (nm != null && id != null && eigenerModus() && nm.isNotificationPolicyAccessGranted()) {
            try { nm.removeAutomaticZenRule(id); } catch (Exception ignored) {}
        }
        prefs(ctx).edit().remove(REGEL_ID).apply();
    }

    /** Einstellungsseite für den eigenen Modus öffnen (ab Android 15 „Modi“), sonst die Nicht-stören-Einstellungen */
    static void modusEinstellungen(Context ctx) {
        String id = prefs(ctx).getString(REGEL_ID, null);
        Intent i = new Intent("android.settings.AUTOMATIC_ZEN_RULE_SETTINGS");
        if (id != null) i.putExtra("android.provider.extra.AUTOMATIC_ZEN_RULE_ID", id);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (Exception e) {
            try {
                ctx.startActivity(new Intent("android.settings.ZEN_MODE_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {}
        }
    }

    /** Erlaubnis „Zugriff auf Nicht stören“ anfragen (öffnet die Android-Einstellungen) */
    static void erlaubnisOeffnen(Context ctx) {
        try {
            ctx.startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {}
    }
}
