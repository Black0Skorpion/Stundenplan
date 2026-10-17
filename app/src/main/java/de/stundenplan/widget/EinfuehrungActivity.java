package de.stundenplan.widget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Kurze Einführung beim ersten Start (überspringbar) mit einem erfundenen Beispiel-Stundenplan. */
public class EinfuehrungActivity extends Activity {

    static final String GESEHEN = "einfuehrung_gesehen";
    private static final int SEITEN = 3;

    private Ui ui;
    private FrameLayout seite;
    private LinearLayout punkte;
    private Button weiter;
    private int nr = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        getWindow().setStatusBarColor(ui.bg);
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setBackgroundColor(ui.bg);

        LinearLayout oben = new LinearLayout(this);
        oben.setGravity(Gravity.END);
        TextView skip = ui.text("Überspringen", 16, ui.akzent);
        skip.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(8));
        skip.setOnClickListener(v -> fertig(false));
        oben.addView(skip);
        w.addView(oben);

        seite = new FrameLayout(this);
        w.addView(seite, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        punkte = new LinearLayout(this);
        punkte.setGravity(Gravity.CENTER);
        punkte.setPadding(0, ui.dp(8), 0, ui.dp(8));
        w.addView(punkte);

        weiter = new Button(this);
        weiter.setAllCaps(false);
        weiter.setTextColor(Color.WHITE);
        weiter.setTextSize(17);
        weiter.setBackground(ui.tippbar(ui.akzent, 14));
        weiter.setOnClickListener(v -> { if (nr < SEITEN - 1) zeige(nr + 1); else fertig(true); });
        w.addView(weiter, ui.mitRand(20, 4, 20, 24));
        setContentView(w);

        GestureDetector g = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) < ui.dp(60)) return false;
                zeige(Math.max(0, Math.min(SEITEN - 1, nr + (dx < 0 ? 1 : -1))));
                return true;
            }
        });
        seite.setOnTouchListener((v, e) -> g.onTouchEvent(e));
        zeige(0);
    }

    private void zeige(int n) {
        nr = n;
        seite.removeAllViews();
        seite.addView(n == 0 ? seite1() : n == 1 ? seite2() : seite3());
        punkte.removeAllViews();
        for (int i = 0; i < SEITEN; i++) {
            View p = new View(this);
            p.setBackground(ui.rund(i == n ? ui.akzent : ui.linie, 4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(8), ui.dp(8));
            lp.setMargins(ui.dp(4), 0, ui.dp(4), 0);
            punkte.addView(p, lp);
        }
        weiter.setText(n < SEITEN - 1 ? "Weiter" : "Fächer aus PULS wählen");
    }

    private LinearLayout kopf(String titel, String text) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), ui.dp(8));
        TextView t = ui.fett(titel, 26, ui.vorder);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        TextView b = ui.text(text, 16, ui.gedimmt);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, ui.dp(8), 0, ui.dp(14));
        l.addView(b);
        return l;
    }

    private View seite1() {
        LinearLayout l = kopf("Dein Stundenplan", "Woche im Blick, nächste Veranstaltung mit Raum und Weg dorthin. So sieht das aus (Beispiel):");
        WochenView wv = new WochenView(this);
        long montag = WochenView.montag(System.currentTimeMillis(), 0);
        wv.setze(montag, demo(montag), t -> {});
        FrameLayout r = new FrameLayout(this);
        r.setBackground(ui.rund(ui.karte, 14));
        r.setPadding(ui.dp(6), ui.dp(8), ui.dp(6), ui.dp(6));
        r.addView(wv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ui.dp(300)));
        l.addView(r, ui.breit());
        return l;
    }

    private View seite2() {
        LinearLayout l = kopf("Unterwegs", "Alles auch ohne die App zu öffnen:");
        String[][] p = {
                {"Widgets", "Stundenplan, Mensa und Abfahrten auf dem Startbildschirm."},
                {"Lageplan", "Veranstaltung antippen → Raum auf dem Campusplan und Route."},
                {"Erinnerung & Wecker", "Mitteilung vor jeder Veranstaltung, Wecker vor der ersten am Tag."},
                {"Live Update", "Ab Android 16: laufende Vorlesung in der Statusleiste."},
                {"Teilen", "Plan per QR-Code weitergeben, Pläne anderer als Profil ansehen."}};
        for (String[] x : p) {
            LinearLayout z = new LinearLayout(this);
            z.setOrientation(LinearLayout.VERTICAL);
            z.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
            z.addView(ui.fett(x[0], 17, ui.vorder));
            z.addView(ui.text(x[1], 15, ui.gedimmt));
            l.addView(z, ui.breit());
        }
        return l;
    }

    private View seite3() {
        LinearLayout l = kopf("Los geht's", "Such deine Veranstaltungen aus PULS, wähl die Gruppen – die App gleicht Räume und Zeiten dann einmal täglich ab.");
        TextView t = ui.text("Hat jemand den gleichen Plan? Dann lass dir einfach den QR-Code zeigen und scanne ihn unter Einstellungen → „QR-Code scannen“.", 15, ui.gedimmt);
        t.setGravity(Gravity.CENTER);
        t.setPadding(ui.dp(8), ui.dp(20), ui.dp(8), 0);
        l.addView(t);
        return l;
    }

    private void fertig(boolean modulwahl) {
        Erinnerungen.prefs(this).edit().putBoolean(GESEHEN, true).apply();
        if (modulwahl) startActivity(new Intent(this, PulsActivity.class));
        finish();
    }

    /** Erfundene Woche, nur zur Anschauung */
    static List<Termin> demo(long montag) {
        List<Termin> l = new ArrayList<>();
        Object[][] d = {
                {0, 8 * 60 + 15, 9 * 60 + 45, "Beispielfach Analysis", "Vorlesung", "2.27.0.01"},
                {0, 10 * 60 + 15, 11 * 60 + 45, "Beispielfach Programmieren", "Übung", "2.70.0.08"},
                {1, 12 * 60 + 15, 13 * 60 + 45, "Beispielfach Programmieren", "Vorlesung", "2.25.F0.01"},
                {2, 8 * 60 + 15, 9 * 60 + 45, "Beispielfach Analysis", "Übung", "2.09.0.12"},
                {2, 14 * 60 + 15, 15 * 60 + 45, "Beispielfach Statistik", "Vorlesung", "1.08.0.58"},
                {3, 10 * 60 + 15, 11 * 60 + 45, "Beispielfach Statistik", "Tutorium", "1.09.2.13"},
                {4, 10 * 60 + 15, 11 * 60 + 45, "Beispielfach Analysis", "Vorlesung", "2.27.0.01"}};
        for (Object[] x : d) {
            long tag = MensaDaten.plusTage(montag, (Integer) x[0]);
            l.add(new Termin(tag + (Integer) x[1] * 60_000L, tag + (Integer) x[2] * 60_000L, (String) x[3], (String) x[4], (String) x[5]));
        }
        return l;
    }

    @Override
    public void onBackPressed() {
        fertig(false);
    }
}
