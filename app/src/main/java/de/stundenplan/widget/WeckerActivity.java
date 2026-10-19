package de.stundenplan.widget;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Klingelt (Wecker-Ton, Vibration) und zeigt die erste Veranstaltung. „Aus“ beendet, „10 Min“ schlummert. */
public class WeckerActivity extends Activity {

    private MediaPlayer ton;
    private Vibrator vibration;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        float d = getResources().getDisplayMetrics().density;
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(Math.round(24 * d), Math.round(24 * d), Math.round(24 * d), Math.round(24 * d));
        l.setBackgroundColor(Color.parseColor("#1C1C1E"));

        Termin t = Wecker.ersteHeute(this);
        TextView uhr = new TextView(this);
        uhr.setText(Lageplan.uhrzeitText(System.currentTimeMillis()));
        uhr.setTextSize(64);
        uhr.setTextColor(Color.WHITE);
        uhr.setGravity(Gravity.CENTER);
        l.addView(uhr);
        TextView info = new TextView(this);
        info.setTextSize(20);
        info.setTextColor(Color.WHITE);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, Math.round(16 * d), 0, Math.round(32 * d));
        if (t != null) {
            String[] kurs = Konfig.kurs(t.titel);
            info.setText("Aufstehen!\n" + kurs[0] + " um " + Lageplan.uhrzeitText(t.start) + (t.raum.isEmpty() ? "" : "\n" + t.raum));
        } else {
            info.setText("Aufstehen!");
        }
        l.addView(info);

        Button aus = new Button(this);
        aus.setText("Aus");
        aus.setTextSize(22);
        aus.setOnClickListener(v -> beenden());
        l.addView(aus, new LinearLayout.LayoutParams(Math.round(220 * d), Math.round(72 * d)));
        Button schlummern = new Button(this);
        final int minuten = Wecker.schlummern(this);
        schlummern.setText(minuten + " Min schlummern");
        schlummern.setOnClickListener(v -> {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 23,
                        new android.content.Intent(this, WeckerActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
                long wann = System.currentTimeMillis() + minuten * 60_000L;
                if (Wecker.darfStellen(this)) am.setAlarmClock(new android.app.AlarmManager.AlarmClockInfo(wann, pi), pi);
                else am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, wann, pi);
            }
            stoppen();
            finish();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(220 * d), Math.round(56 * d));
        lp.topMargin = Math.round(12 * d);
        l.addView(schlummern, lp);
        setContentView(l);

        klingeln();
        // Nach 10 Minuten von selbst aufhören
        handler.postDelayed(this::stoppen, 10 * 60_000L);
        // Nächsten Wecker gleich stellen
        Wecker.planen(this);
    }

    private void klingeln() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ton = new MediaPlayer();
            ton.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ton.setDataSource(this, uri);
            ton.setLooping(true);
            ton.prepare();
            ton.start();
        } catch (Exception ignored) {}
        vibration = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibration != null && vibration.hasVibrator()) {
            vibration.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 600}, 0));
        }
    }

    private void stoppen() {
        if (ton != null) {
            try { ton.stop(); } catch (Exception ignored) {}
            ton.release();
            ton = null;
        }
        if (vibration != null) vibration.cancel();
    }

    private void beenden() {
        stoppen();
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stoppen();
        super.onDestroy();
    }
}
