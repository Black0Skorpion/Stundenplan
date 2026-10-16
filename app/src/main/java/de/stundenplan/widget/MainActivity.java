package de.stundenplan.widget;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.view.View;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.EditText;
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
    private static final int MITTEILUNGEN_ERLAUBEN = 2;
    private CheckBox erinnerungAn;
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
                + "Stundenplan: Klein (aktuelle Veranstaltung), Mittel (mit Tagesplan) und Groß (Woche).\n"
                + "Mensa: Klein, Mittel und Groß – mit den Pfeilen ‹ › oben rechts blättert man durch die Tage.\n"
                + "Abfahrten: nächste Züge und Busse am Campus, priorisierte Linien sind immer dabei.\n"
                + "Mensa und Abfahrten richten sich nach dem Campus unten (Automatisch = Campus der laufenden bzw. nächsten Veranstaltung).\n"
                + "Alle Widgets lassen sich frei in der Größe ziehen (z. B. 4×3).\n"
                + "Tippen auf eine Veranstaltung oder einen Wochentag zeigt den Lageplan des passenden Campus mit dem Gebäude – "
                + "dort gibt es auch die Route (Google Maps), „Fällt aus“ / „Raum ändern“ und bei einem Campuswechsel die Bus- und Bahnverbindungen.\n\n"
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

        Button lageplan = new Button(this);
        lageplan.setText("Lageplan für heute");
        lageplan.setOnClickListener(v -> startActivity(LageplanActivity.fuerTag(this, MensaDaten.tagesBeginn(System.currentTimeMillis()))));
        inhalt.addView(lageplan);

        // Campus für Mensa und Abfahrten
        TextView campusTitel = new TextView(this);
        campusTitel.setText("\nCampus (Mensa und Abfahrten)");
        campusTitel.setTextSize(18);
        inhalt.addView(campusTitel);
        Spinner campus = new Spinner(this);
        final String[] campusWerte = {Campus.AUTO, Campus.GOLM, Campus.NEUES_PALAIS, Campus.GRIEBNITZSEE};
        String[] campusNamen = {"Automatisch (nach Stundenplan)", "Golm", "Am Neuen Palais", "Griebnitzsee"};
        campus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, campusNamen));
        String wahl = Campus.wahl(this);
        for (int i = 0; i < campusWerte.length; i++) if (campusWerte[i].equals(wahl)) campus.setSelection(i);
        campus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (campusWerte[position].equals(Campus.wahl(MainActivity.this))) return;
                Campus.setzeWahl(MainActivity.this, campusWerte[position]);
                neuLaden();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        inhalt.addView(campus);

        TextView favTitel = new TextView(this);
        favTitel.setText("Priorisierte Linien (stehen im Abfahrten-Widget immer mit drin, z. B. „RB20, 605“):");
        inhalt.addView(favTitel);
        EditText favFeld = new EditText(this);
        favFeld.setSingleLine(true);
        favFeld.setHint("RB20");
        favFeld.setText(String.join(", ", Campus.favoriten(this)));
        inhalt.addView(favFeld);
        Button favSpeichern = new Button(this);
        favSpeichern.setText("Linien speichern");
        favSpeichern.setOnClickListener(v -> {
            Campus.setzeFavoriten(this, favFeld.getText().toString());
            favFeld.setText(String.join(", ", Campus.favoriten(this)));
            Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show();
            neuLaden();
        });
        inhalt.addView(favSpeichern);

        // Erinnerungen vor Veranstaltungen
        TextView erTitel = new TextView(this);
        erTitel.setText("\nErinnerungen");
        erTitel.setTextSize(18);
        inhalt.addView(erTitel);
        erinnerungAn = new CheckBox(this);
        erinnerungAn.setText("Vor Veranstaltungen erinnern (mit Raum und Countdown)");
        erinnerungAn.setChecked(Erinnerungen.an(this) && Erinnerungen.erlaubt(this));
        inhalt.addView(erinnerungAn);
        Spinner vorlauf = new Spinner(this);
        String[] namen = new String[Erinnerungen.VORLAUF_OPTIONEN.length];
        int gewaehlt = 1;
        for (int i = 0; i < namen.length; i++) {
            namen[i] = Erinnerungen.VORLAUF_OPTIONEN[i] + " Minuten vorher";
            if (Erinnerungen.VORLAUF_OPTIONEN[i] == Erinnerungen.vorlauf(this)) gewaehlt = i;
        }
        vorlauf.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, namen));
        vorlauf.setSelection(gewaehlt);
        vorlauf.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Erinnerungen.prefs(MainActivity.this).edit().putInt(Erinnerungen.VORLAUF, Erinnerungen.VORLAUF_OPTIONEN[position]).apply();
                Erinnerungen.planen(MainActivity.this);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        inhalt.addView(vorlauf);
        erinnerungAn.setOnCheckedChangeListener((knopf, an) -> {
            if (an && Build.VERSION.SDK_INT >= 33 && !Erinnerungen.erlaubt(this)) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, MITTEILUNGEN_ERLAUBEN);
                return;
            }
            Erinnerungen.prefs(this).edit().putBoolean(Erinnerungen.AN, an).apply();
            Erinnerungen.planen(this);
        });

        // Eigener Planer-Link: Lageplan → „Im Planer öffnen“ (die Widgets öffnen immer die App)
        TextView linkTitel = new TextView(this);
        linkTitel.setText("\nDein Planer-Link (im Planer über „Plan teilen“ kopieren, öffnet sich im Lageplan):");
        inhalt.addView(linkTitel);
        EditText linkFeld = new EditText(this);
        linkFeld.setSingleLine(true);
        linkFeld.setHint(Konfig.TAP_URL);
        linkFeld.setText(getSharedPreferences(StundenplanWidget.EINSTELLUNGEN, MODE_PRIVATE)
                .getString(StundenplanWidget.PLANER_LINK, ""));
        inhalt.addView(linkFeld);
        Button linkSpeichern = new Button(this);
        linkSpeichern.setText("Link speichern");
        linkSpeichern.setOnClickListener(v -> {
            String link = linkFeld.getText().toString().trim();
            if (!link.isEmpty() && !link.startsWith("http")) {
                Toast.makeText(this, "Der Link muss mit https:// beginnen", Toast.LENGTH_LONG).show();
                return;
            }
            getSharedPreferences(StundenplanWidget.EINSTELLUNGEN, MODE_PRIVATE).edit()
                    .putString(StundenplanWidget.PLANER_LINK, link).apply();
            StundenplanWidget.alleAktualisieren(this);
            Toast.makeText(this, link.isEmpty() ? "Standard-Link wird verwendet" : "Link gespeichert", Toast.LENGTH_SHORT).show();
        });
        inhalt.addView(linkSpeichern);

        vorschauen = new LinearLayout(this);
        vorschauen.setOrientation(LinearLayout.VERTICAL);
        vorschauen.setPadding(0, pad, 0, 0);
        inhalt.addView(vorschauen);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(inhalt);
        setContentView(scroll);
        neuLaden();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MITTEILUNGEN_ERLAUBEN) return;
        boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        Erinnerungen.prefs(this).edit().putBoolean(Erinnerungen.AN, ok).apply();
        erinnerungAn.setChecked(ok);
        if (!ok) Toast.makeText(this, "Mitteilungen sind nicht erlaubt (Einstellungen → Apps → Stundenplan)", Toast.LENGTH_LONG).show();
        Erinnerungen.planen(this);
    }

    private void neuLaden() {
        Erinnerungen.planen(this);
        boolean importiert = new File(getFilesDir(), Daten.ICS_DATEI).exists();
        quelle.setText(importiert ? "Quelle: importierte ICS-Datei" : "Quelle: eingebaute Termine (WiSe 26/27)");
        StundenplanWidget.alleAktualisieren(this);
        zeigeVorschau();
    }

    private void zeigeVorschau() {
        vorschauen.removeAllViews();
        TextView ueberschrift = new TextView(this);
        ueberschrift.setText("Stundenplan");
        ueberschrift.setTextSize(18);
        vorschauen.addView(ueberschrift);
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
            bild.setBackground(hintergrund(dunkel, d));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(b * d), Math.round(h * d));
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = Math.round(16 * d);
            vorschauen.addView(bild, lp);
        }
        zeigeMensaVorschau(dunkel, d, jetzt);
        zeigeAbfahrtenVorschau(dunkel, d, jetzt);
    }

    /** Abfahrten-Vorschau: im Hintergrund laden, dann anzeigen. */
    private void zeigeAbfahrtenVorschau(boolean dunkel, float d, long jetzt) {
        TextView ueberschrift = new TextView(this);
        final String campus = Campus.aktuell(this);
        final List<String> favoriten = Campus.favoriten(this);
        ueberschrift.setText("Abfahrten " + Campus.kurz(campus) + " (lädt …)");
        ueberschrift.setTextSize(18);
        vorschauen.addView(ueberschrift);
        new Thread(() -> {
            java.util.List<AbfahrtenDaten.Abfahrt> liste = AbfahrtenDaten.laden(this, campus, true);
            AbfahrtenWidget.alleAktualisieren(this, false);
            runOnUiThread(() -> {
                ueberschrift.setText("Abfahrten " + Campus.kurz(campus));
                float b = 340, h = 230;
                ImageView bild = new ImageView(this);
                bild.setImageBitmap(AbfahrtenZeichner.zeichnen(b, h, d, dunkel, jetzt, liste, campus, favoriten));
                bild.setBackground(hintergrund(dunkel, d));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(b * d), Math.round(h * d));
                lp.gravity = Gravity.CENTER_HORIZONTAL;
                lp.bottomMargin = Math.round(16 * d);
                vorschauen.addView(bild, lp);
            });
        }).start();
    }

    /** Mensa-Vorschau: Speiseplan im Hintergrund laden, dann anzeigen. */
    private void zeigeMensaVorschau(boolean dunkel, float d, long jetzt) {
        TextView ueberschrift = new TextView(this);
        ueberschrift.setText(Campus.mensaName(Campus.aktuell(this)) + " (lädt …)");
        ueberschrift.setTextSize(18);
        vorschauen.addView(ueberschrift);
        new Thread(() -> {
            List<MensaDaten.Tag> tage = MensaDaten.ladeTage(this, true);
            MensaDaten.Auswahl a = MensaDaten.waehleTag(tage, jetzt, 0);
            MensaDaten.Plan plan = a.tag.geschlossen ? null : MensaDaten.ladePlan(this, a.tag.datum, true);
            MensaWidget.alleAktualisieren(this, false);
            runOnUiThread(() -> {
                ueberschrift.setText(MensaDaten.MENSA_NAME);
                String[][] groessen = {{Zeichner.KLEIN, "160", "160"}, {Zeichner.MITTEL, "340", "160"}, {Zeichner.GROSS, "340", "356"}};
                for (String[] g : groessen) {
                    float b = Float.parseFloat(g[1]), h = Float.parseFloat(g[2]);
                    ImageView bild = new ImageView(this);
                    bild.setImageBitmap(MensaZeichner.zeichnen(g[0], b, h, d, dunkel, jetzt, a, plan));
                    bild.setBackground(hintergrund(dunkel, d));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(b * d), Math.round(h * d));
                    lp.gravity = Gravity.CENTER_HORIZONTAL;
                    lp.bottomMargin = Math.round(16 * d);
                    vorschauen.addView(bild, lp);
                }
            });
        }).start();
    }

    private static GradientDrawable hintergrund(boolean dunkel, float d) {
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(Color.parseColor(dunkel ? "#1C1C1E" : "#FFFFFF"));
        hg.setCornerRadius(22 * d);
        hg.setStroke(Math.max(1, Math.round(d)), Color.parseColor(dunkel ? "#3A3A3C" : "#D3D1C7"));
        return hg;
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
