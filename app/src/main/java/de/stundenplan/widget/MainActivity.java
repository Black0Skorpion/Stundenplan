package de.stundenplan.widget;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** App-Bildschirm: Stundenplan (.ics) importieren und Vorschau der drei Widgets. */
public class MainActivity extends Activity {

    private static final int ICS_WAEHLEN = 1;
    private LinearLayout vorschauen;
    private TextView quelle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * d);

        LinearLayout inhalt = new LinearLayout(this);
        inhalt.setOrientation(LinearLayout.VERTICAL);
        inhalt.setPadding(pad, pad, pad, pad);

        TextView titel = new TextView(this);
        titel.setText("Stundenplan-Widget");
        titel.setTextSize(24);
        inhalt.addView(titel);

        TextView info = new TextView(this);
        info.setText("Widget hinzufügen: Startbildschirm gedrückt halten → Widgets → Stundenplan. "
                + "Es gibt drei Größen: Klein (aktuelle Veranstaltung), Mittel (mit Tagesplan) und Groß (Woche).\n\n"
                + "Neuen Stundenplan übernehmen: im Planer als Kalender (.ics) exportieren und hier importieren.");
        info.setPadding(0, pad / 2, 0, pad / 2);
        inhalt.addView(info);

        quelle = new TextView(this);
        inhalt.addView(quelle);

        Button importieren = new Button(this);
        importieren.setText("ICS-Datei importieren");
        importieren.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, ICS_WAEHLEN);
        });
        inhalt.addView(importieren);

        Button zuruecksetzen = new Button(this);
        zuruecksetzen.setText("Eingebaute Termine verwenden");
        zuruecksetzen.setOnClickListener(v -> {
            File f = new File(getFilesDir(), Daten.ICS_DATEI);
            if (f.exists() && f.delete()) Toast.makeText(this, "Import entfernt", Toast.LENGTH_SHORT).show();
            neuLaden();
        });
        inhalt.addView(zuruecksetzen);

        vorschauen = new LinearLayout(this);
        vorschauen.setOrientation(LinearLayout.VERTICAL);
        vorschauen.setPadding(0, pad, 0, 0);
        inhalt.addView(vorschauen);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(inhalt);
        setContentView(scroll);
        neuLaden();
    }

    private void neuLaden() {
        boolean importiert = new File(getFilesDir(), Daten.ICS_DATEI).exists();
        quelle.setText(importiert ? "Quelle: importierte ICS-Datei" : "Quelle: eingebaute Termine (WiSe 26/27)");
        StundenplanWidget.alleAktualisieren(this);
        zeigeVorschau();
    }

    private void zeigeVorschau() {
        vorschauen.removeAllViews();
        List<Termin> alle = Daten.laden(this);
        boolean dunkel = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        float d = getResources().getDisplayMetrics().density;
        long jetzt = System.currentTimeMillis();
        String[][] groessen = {{Zeichner.KLEIN, "160", "160"}, {Zeichner.MITTEL, "340", "160"}, {Zeichner.GROSS, "340", "356"}};
        for (String[] g : groessen) {
            float b = Float.parseFloat(g[1]), h = Float.parseFloat(g[2]);
            ImageView bild = new ImageView(this);
            bild.setImageBitmap(Zeichner.zeichnen(g[0], b, h, d, dunkel, jetzt, alle));
            GradientDrawable hg = new GradientDrawable();
            hg.setColor(Color.parseColor(dunkel ? "#1C1C1E" : "#FFFFFF"));
            hg.setCornerRadius(22 * d);
            hg.setStroke(Math.max(1, Math.round(d)), Color.parseColor(dunkel ? "#3A3A3C" : "#D3D1C7"));
            bild.setBackground(hg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(b * d), Math.round(h * d));
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = Math.round(16 * d);
            vorschauen.addView(bild, lp);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != ICS_WAEHLEN || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Datei leer");
            String text = Daten.lesen(in);
            int anzahl = Daten.parseICS(text).size();
            if (anzahl == 0) {
                Toast.makeText(this, "Keine Termine in der Datei gefunden", Toast.LENGTH_LONG).show();
                return;
            }
            try (OutputStream out = new FileOutputStream(new File(getFilesDir(), Daten.ICS_DATEI))) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, anzahl + " Termine importiert", Toast.LENGTH_SHORT).show();
            neuLaden();
        } catch (Exception e) {
            Toast.makeText(this, "Import fehlgeschlagen: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
