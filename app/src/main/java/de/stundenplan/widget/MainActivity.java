package de.stundenplan.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Die App wie auf dem iPhone: Reiter Plan, Mensa, Abfahrten, Lageplan, Einstellungen.
 */
public class MainActivity extends Activity {

    static final String EXTRA_REITER = "reiter";
    static final int PLAN = 0, MENSA = 1, ABFAHRTEN = 2, LAGEPLAN = 3, EINSTELLUNGEN = 4;
    private static final String ANZEIGE = "anzeige_profil";

    /** Eine Seite hinter einem Reiter */
    interface Seite {
        View ansicht();
        /** Seite wird sichtbar (neu laden) */
        void zeigen();
        default void verstecken() {}
    }

    private Ui ui;
    private FrameLayout inhalt;
    private final TextView[] reiter = new TextView[5];
    private final Seite[] seiten = new Seite[5];
    private int aktiv = -1;
    final Handler handler = new Handler(Looper.getMainLooper());

    /** Welcher Plan im Plan-Reiter gezeigt wird (null = eigener) */
    static void setzeAnzeige(Context ctx, String profilId) {
        Erinnerungen.prefs(ctx).edit().putString(ANZEIGE, profilId).apply();
    }

    static String anzeige(Context ctx) {
        String id = Erinnerungen.prefs(ctx).getString(ANZEIGE, null);
        return Teilen.profil(ctx, id) != null ? id : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        Window w = getWindow();
        w.setStatusBarColor(ui.bg);
        w.setNavigationBarColor(ui.karte);
        if (!ui.dunkel) {
            int f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(f);
        }

        LinearLayout wurzel = new LinearLayout(this);
        wurzel.setOrientation(LinearLayout.VERTICAL);
        wurzel.setBackgroundColor(ui.bg);
        inhalt = new FrameLayout(this);
        wurzel.addView(inhalt, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        View strich = new View(this);
        strich.setBackgroundColor(ui.linie);
        wurzel.addView(strich, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, ui.dp(0.5f))));
        LinearLayout leiste = new LinearLayout(this);
        leiste.setBackgroundColor(ui.karte);
        String[] namen = {"Plan", "Mensa", "Abfahrten", "Lageplan", "Einstellungen"};
        for (int i = 0; i < 5; i++) {
            final int nr = i;
            TextView t = new TextView(this);
            t.setText(namen[i]);
            t.setCompoundDrawablePadding(ui.dp(2));
            t.setGravity(Gravity.CENTER);
            t.setTextSize(11);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setPadding(0, ui.dp(7), 0, ui.dp(7));
            t.setOnClickListener(v -> wechseln(nr));
            t.setBackground(ui.tippbar(ui.karte, 0));
            reiter[i] = t;
            leiste.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        wurzel.addView(leiste);
        setContentView(wurzel);

        seiten[PLAN] = new PlanSeite(this);
        seiten[MENSA] = new MensaSeite(this);
        seiten[ABFAHRTEN] = new AbfahrtenSeite(this);
        seiten[LAGEPLAN] = new LageplanSeite(this);
        seiten[EINSTELLUNGEN] = new EinstellungenSeite(this);

        int start = getIntent().getIntExtra(EXTRA_REITER, PLAN);
        wechseln(start >= 0 && start < 5 ? start : PLAN);

        // Beim ersten Start ohne Stundenplan: kurze Einführung
        if (savedInstanceState == null && !Erinnerungen.prefs(this).getBoolean(EinfuehrungActivity.GESEHEN, false) && !Daten.vorhanden(this)) {
            startActivity(new Intent(this, EinfuehrungActivity.class));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(EXTRA_REITER)) wechseln(intent.getIntExtra(EXTRA_REITER, PLAN));
    }

    void wechseln(int nr) {
        if (aktiv >= 0 && aktiv != nr) seiten[aktiv].verstecken();
        aktiv = nr;
        inhalt.removeAllViews();
        inhalt.addView(seiten[nr].ansicht(), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        for (int i = 0; i < 5; i++) {
            int f = i == nr ? ui.akzent : ui.gedimmt;
            reiter[i].setTextColor(f);
            reiter[i].setCompoundDrawables(null, new Symbol(i, f, ui.dp(24)), null, null);
        }
        seiten[nr].zeigen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (aktiv >= 0) seiten[aktiv].zeigen();
        // Alles Abhängige neu planen (Widgets, Erinnerungen, Wecker, Live Update) und höchstens täglich mit PULS abgleichen
        PulsDienst.planen(this);
        PulsDienst.nachAenderung(this);
        new Thread(() -> {
            boolean neu = PulsDienst.lehrpersonenNachtragen(this);
            if (PulsDienst.abgleichen(this, false) || neu) runOnUiThread(() -> { if (aktiv >= 0) seiten[aktiv].zeigen(); });
        }).start();
    }

    @Override
    protected void onPause() {
        if (aktiv >= 0) seiten[aktiv].verstecken();
        super.onPause();
    }

    // Ergebnisse an die Einstellungen weitergeben (Datei-Auswahl, Berechtigungen)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ((EinstellungenSeite) seiten[EINSTELLUNGEN]).ergebnis(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ((EinstellungenSeite) seiten[EINSTELLUNGEN]).berechtigung(requestCode, grantResults);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
