package de.stundenplan.widget;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Reiter „Mensa“: Speiseplan des Campus, Tage per Kapsel oder Wischen. */
final class MensaSeite implements MainActivity.Seite {

    private static final String[] WT = {"So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"};

    private final MainActivity act;
    private final Ui ui;
    private final LinearLayout wurzel, chips, plan;
    private final TextView campusKnopf;
    private final HorizontalScrollView chipScroll;
    private List<MensaDaten.Tag> offen = new ArrayList<>();
    private int index = 0;
    private int ladeNr = 0;

    MensaSeite(MainActivity act) {
        this.act = act;
        ui = new Ui(act);
        wurzel = new LinearLayout(act);
        wurzel.setOrientation(LinearLayout.VERTICAL);

        LinearLayout kopf = new LinearLayout(act);
        kopf.setGravity(Gravity.CENTER_VERTICAL);
        kopf.setPadding(0, ui.dp(10), ui.dp(12), 0);
        TextView titel = ui.titel("Mensa");
        kopf.addView(titel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        campusKnopf = ui.kapsel("", ui.akzent, false, v -> campusWaehlen());
        kopf.addView(campusKnopf);
        wurzel.addView(kopf);

        chipScroll = new HorizontalScrollView(act);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(act);
        chips.setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(8));
        chipScroll.addView(chips);
        wurzel.addView(chipScroll);

        ScrollView scroll = new ScrollView(act);
        plan = new LinearLayout(act);
        plan.setOrientation(LinearLayout.VERTICAL);
        plan.setPadding(0, 0, 0, ui.dp(24));
        scroll.addView(plan);
        wurzel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // Nach links/rechts wischen = nächster/vorheriger Tag
        GestureDetector g = new GestureDetector(act, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > ui.dp(70) && Math.abs(dx) > 1.5f * Math.abs(dy)) {
                    waehlen(index + (dx < 0 ? 1 : -1));
                    return true;
                }
                return false;
            }
        });
        scroll.setOnTouchListener((v, e) -> {
            g.onTouchEvent(e);
            return false;
        });
    }

    @Override public View ansicht() { return wurzel; }

    @Override
    public void zeigen() {
        String c = Campus.aktuell(act);
        campusKnopf.setText(Campus.kurz(c) + " ▾");
        final int nr = ++ladeNr;
        new Thread(() -> {
            List<MensaDaten.Tag> tage = MensaDaten.ladeTage(act, true);
            long heute = MensaDaten.tagesBeginn(System.currentTimeMillis());
            List<MensaDaten.Tag> o = new ArrayList<>();
            for (MensaDaten.Tag t : tage) if (!t.geschlossen && t.datum >= heute && o.size() < 8) o.add(t);
            MensaDaten.Auswahl a = MensaDaten.waehleTag(tage, System.currentTimeMillis(), 0);
            act.runOnUiThread(() -> {
                if (nr != ladeNr) return;
                offen = o;
                int start = 0;
                for (int i = 0; i < o.size(); i++) if (o.get(i).datum == a.tag.datum) start = i;
                waehlen(start);
            });
        }).start();
    }

    private void waehlen(int i) {
        if (offen.isEmpty()) {
            chips.removeAllViews();
            plan.removeAllViews();
            plan.addView(hinweis("In den nächsten Tagen hat die Mensa geschlossen."));
            return;
        }
        index = Math.max(0, Math.min(offen.size() - 1, i));
        chips.removeAllViews();
        for (int k = 0; k < offen.size(); k++) {
            final int nr = k;
            TextView c = ui.kapsel(tagText(offen.get(k).datum), ui.akzent, k == index, v -> waehlen(nr));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = ui.dp(6);
            chips.addView(c, lp);
        }
        final View gewaehlt = chips.getChildAt(index);
        chipScroll.post(() -> chipScroll.smoothScrollTo(Math.max(0, gewaehlt.getLeft() - ui.dp(40)), 0));
        laden(offen.get(index).datum);
    }

    private void laden(long tag) {
        plan.removeAllViews();
        plan.addView(hinweis("Lädt …"));
        final int nr = ++ladeNr;
        new Thread(() -> {
            MensaDaten.Plan p = MensaDaten.ladePlan(act, tag, true);
            act.runOnUiThread(() -> {
                if (nr != ladeNr) return;
                zeigePlan(p);
            });
        }).start();
    }

    private void zeigePlan(MensaDaten.Plan p) {
        plan.removeAllViews();
        if (p == null) {
            plan.addView(hinweis("Der Speiseplan konnte nicht geladen werden."));
        } else if (p.leer() && p.desserts.isEmpty() && p.theke.isEmpty()) {
            plan.addView(hinweis("Für diesen Tag ist noch nichts eingetragen."));
        } else {
            abschnitt("Mittags", p.gerichte);
            abschnitt("Theke", p.theke);
            abschnitt("Abends", p.abend);
            abschnitt("Desserts", p.desserts);
        }
        plan.addView(ui.fuss(MensaDaten.MENSA_NAME + " · Daten: OpenMensa"));
        LinearLayout g = ui.gruppe();
        ui.fuellen(g, ui.knopfZeile("Speiseplan im Browser öffnen", v ->
                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(MensaDaten.webseite(act))))));
        plan.addView(g);
    }

    private void abschnitt(String titel, List<MensaDaten.Gericht> liste) {
        if (liste.isEmpty()) return;
        plan.addView(ui.abschnitt(titel));
        LinearLayout g = ui.gruppe();
        List<View> zeilen = new ArrayList<>();
        for (MensaDaten.Gericht ge : liste) {
            LinearLayout z = ui.zeile(null);
            View b = new View(act);
            b.setBackground(ui.rund(ge.farbe, 2));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ui.dp(4), ui.dp(36));
            bl.rightMargin = ui.dp(10);
            z.addView(b, bl);
            LinearLayout m = new LinearLayout(act);
            m.setOrientation(LinearLayout.VERTICAL);
            TextView kat = ui.text(ge.kat, 12, ui.gedimmt);
            m.addView(kat);
            m.addView(ui.text(ge.name, 15.5f, ui.vorder));
            z.addView(m, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            if (!Double.isNaN(ge.preis)) {
                TextView pr = ui.fett(MensaDaten.preisText(ge.preis), 14, ui.vorder);
                pr.setPadding(ui.dp(8), 0, 0, 0);
                z.addView(pr);
            }
            zeilen.add(z);
        }
        ui.fuellen(g, zeilen.toArray(new View[0]));
        plan.addView(g);
    }

    private TextView hinweis(String s) {
        TextView t = ui.text(s, 15, ui.gedimmt);
        t.setGravity(Gravity.CENTER);
        t.setPadding(ui.dp(20), ui.dp(60), ui.dp(20), ui.dp(30));
        return t;
    }

    private static String tagText(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        long heute = MensaDaten.tagesBeginn(System.currentTimeMillis());
        String name = t == heute ? "Heute" : t == MensaDaten.plusTage(heute, 1) ? "Morgen" : WT[c.get(Calendar.DAY_OF_WEEK) - 1];
        return name + " " + c.get(Calendar.DAY_OF_MONTH) + "." + (c.get(Calendar.MONTH) + 1) + ".";
    }

    private void campusWaehlen() {
        final String[] werte = {Campus.AUTO, Campus.GOLM, Campus.NEUES_PALAIS, Campus.GRIEBNITZSEE};
        String[] namen = {"Automatisch (nach Stundenplan)", "Golm", "Am Neuen Palais", "Griebnitzsee"};
        String wahl = Campus.wahl(act);
        for (int i = 0; i < werte.length; i++) if (werte[i].equals(wahl)) namen[i] = "✓ " + namen[i];
        new AlertDialog.Builder(act).setTitle("Campus").setItems(namen, (d, i) -> {
            Campus.setzeWahl(act, werte[i]);
            MensaWidget.alleAktualisieren(act, false);
            AbfahrtenWidget.alleAktualisieren(act, false);
            zeigen();
        }).show();
    }

    static int textFarbe(int hg) {
        return Color.luminance(hg) > 0.6f ? Color.BLACK : Color.WHITE;
    }
}
