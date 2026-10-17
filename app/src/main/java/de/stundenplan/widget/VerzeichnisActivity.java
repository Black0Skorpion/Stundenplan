package de.stundenplan.widget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Im Vorlesungsverzeichnis von PULS stöbern (Ordner öffnen, Veranstaltungen hinzufügen). „Zum Plan“ führt zurück. */
public class VerzeichnisActivity extends Activity {

    static final String EXTRA_PFAD = "pfad", EXTRA_TITEL = "titel";

    private Ui ui;
    private LinearLayout liste;
    private String pfad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        pfad = getIntent().getStringExtra(EXTRA_PFAD);
        if (pfad == null) pfad = "";
        String titel = getIntent().getStringExtra(EXTRA_TITEL);
        setTitle(titel != null ? titel : "Vorlesungsverzeichnis");
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setBackgroundColor(ui.bg);
        LinearLayout kopf = new LinearLayout(this);
        kopf.setGravity(Gravity.CENTER_VERTICAL);
        kopf.setPadding(ui.dp(16), ui.dp(10), ui.dp(12), ui.dp(4));
        TextView t = ui.fett(titel != null ? titel : "Vorlesungsverzeichnis", 18, ui.vorder);
        kopf.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        kopf.addView(ui.kapsel("Zum Plan", ui.akzent, true, v -> zumPlan()));
        w.addView(kopf);
        ScrollView s = new ScrollView(this);
        liste = new LinearLayout(this);
        liste.setOrientation(LinearLayout.VERTICAL);
        liste.setPadding(0, ui.dp(6), 0, ui.dp(24));
        s.addView(liste);
        w.addView(s, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(w);
        laden();
    }

    private void zumPlan() {
        startActivity(new Intent(this, PulsActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void laden() {
        liste.removeAllViews();
        liste.addView(ui.fuss("Lädt aus PULS …"));
        new Thread(() -> {
            List<Puls.Eintrag> e = PulsDienst.verzeichnis(this, pfad);
            runOnUiThread(() -> zeigen(e));
        }).start();
    }

    private void zeigen(List<Puls.Eintrag> e) {
        liste.removeAllViews();
        if (e == null) {
            liste.addView(ui.fuss("PULS ist gerade nicht erreichbar."));
            LinearLayout g = ui.gruppe();
            ui.fuellen(g, ui.knopfZeile("Nochmal versuchen", v -> laden()));
            liste.addView(g);
            return;
        }
        if (e.isEmpty()) { liste.addView(ui.fuss("Hier ist nichts eingetragen.")); return; }
        PulsDienst.Auswahl a = PulsDienst.auswahl(this);
        LinearLayout g = ui.gruppe();
        List<View> z = new ArrayList<>();
        for (Puls.Eintrag x : e) {
            if (x.ordner()) {
                z.add(ui.wertZeile(x.name, "›", v -> startActivity(new Intent(this, VerzeichnisActivity.class)
                        .putExtra(EXTRA_PFAD, x.pfad).putExtra(EXTRA_TITEL, x.name))));
            } else {
                boolean drin = a.kurs(x.publishid) != null;
                LinearLayout zeile = ui.zeile(drin ? null : v -> hinzufuegen(x));
                TextView n = ui.text(x.name, 16, ui.vorder);
                zeile.addView(n, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                zeile.addView(ui.fett(drin ? "✓" : "＋", 20, ui.akzent));
                z.add(zeile);
            }
        }
        ui.fuellen(g, z.toArray(new View[0]));
        liste.addView(g);
        liste.addView(ui.fuss("Ordner öffnen, Veranstaltung antippen zum Hinzufügen. „Zum Plan“ führt zurück zur Modulwahl."));
    }

    private void hinzufuegen(Puls.Eintrag x) {
        Toast.makeText(this, "„" + x.name + "“ wird geladen …", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String f = PulsDienst.hinzufuegen(this, x.publishid);
            runOnUiThread(() -> {
                Toast.makeText(this, f != null ? f : "Hinzugefügt – im Plan antippen, um die Gruppe zu wählen.", Toast.LENGTH_LONG).show();
                laden();
            });
        }).start();
    }
}
