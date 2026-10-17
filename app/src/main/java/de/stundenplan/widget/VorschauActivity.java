package de.stundenplan.widget;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Vorschau aller Widgets (Stundenplan, Mensa, Abfahrten) – unter Einstellungen → Debug. */
public class VorschauActivity extends Activity {

    private LinearLayout vorschauen;
    private float d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Widget-Vorschau");
        Ui ui = new Ui(this);
        d = ui.d;
        vorschauen = new LinearLayout(this);
        vorschauen.setOrientation(LinearLayout.VERTICAL);
        vorschauen.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(24));
        ScrollView s = new ScrollView(this);
        s.addView(vorschauen);
        setContentView(s);

        boolean dunkel = ui.dunkel;
        long jetzt = System.currentTimeMillis();
        ueberschrift("Stundenplan");
        List<Termin> alle = Daten.laden(this);
        String[][] groessen = {{Zeichner.KLEIN, "160", "160"}, {Zeichner.MITTEL, "340", "160"}, {Zeichner.GROSS, "340", "356"}};
        for (String[] g : groessen) {
            float b = Float.parseFloat(g[1]), h = Float.parseFloat(g[2]);
            bild(Zeichner.zeichnen(g[0], b, h, d, dunkel, jetzt, alle), b, h, dunkel);
        }
        TextView mensa = ueberschrift(Campus.mensaName(Campus.aktuell(this)) + " (lädt …)");
        TextView abf = ueberschrift("Abfahrten " + Campus.kurz(Campus.aktuell(this)) + " (lädt …)");
        new Thread(() -> {
            List<MensaDaten.Tag> tage = MensaDaten.ladeTage(this, true);
            MensaDaten.Auswahl a = MensaDaten.waehleTag(tage, jetzt, 0);
            MensaDaten.Plan plan = a.tag.geschlossen ? null : MensaDaten.ladePlan(this, a.tag.datum, true);
            String campus = Campus.aktuell(this);
            List<AbfahrtenDaten.Abfahrt> liste = AbfahrtenDaten.laden(this, campus, true);
            runOnUiThread(() -> {
                mensa.setText(MensaDaten.MENSA_NAME);
                int pos = vorschauen.indexOfChild(mensa) + 1;
                for (String[] g : groessen) {
                    float b = Float.parseFloat(g[1]), h = Float.parseFloat(g[2]);
                    vorschauen.addView(bildView(MensaZeichner.zeichnen(g[0], b, h, d, dunkel, jetzt, a, plan), b, h, dunkel), pos++);
                }
                abf.setText("Abfahrten " + Campus.kurz(campus));
                vorschauen.addView(bildView(AbfahrtenZeichner.zeichnen(340, 230, d, dunkel, jetzt, liste, campus, Campus.favoriten(this)), 340, 230, dunkel));
            });
        }).start();
    }

    private TextView ueberschrift(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(17);
        t.setPadding(0, Math.round(10 * d), 0, Math.round(6 * d));
        vorschauen.addView(t);
        return t;
    }

    private void bild(android.graphics.Bitmap b, float w, float h, boolean dunkel) {
        vorschauen.addView(bildView(b, w, h, dunkel));
    }

    private ImageView bildView(android.graphics.Bitmap b, float w, float h, boolean dunkel) {
        ImageView v = new ImageView(this);
        v.setImageBitmap(b);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(Color.parseColor(dunkel ? "#1C1C1E" : "#FFFFFF"));
        hg.setCornerRadius(22 * d);
        hg.setStroke(Math.max(1, Math.round(d)), Color.parseColor(dunkel ? "#3A3A3C" : "#D3D1C7"));
        v.setBackground(hg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(w * d), Math.round(h * d));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = Math.round(16 * d);
        v.setLayoutParams(lp);
        return v;
    }
}
