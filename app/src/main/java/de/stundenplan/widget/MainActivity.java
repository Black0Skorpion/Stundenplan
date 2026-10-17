package de.stundenplan.widget;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** App-Bildschirm: Stundenplan, Teilen & Profile, Wecker, Erinnerungen, Campus – und Vorschau der Widgets. */
public class MainActivity extends Activity {

    private static final int ICS_WAEHLEN = 1;
    private static final int MITTEILUNGEN_ERLAUBEN = 2;
    private static final String ANZEIGE = "anzeige_profil";

    private float d;
    private int pad;
    private CheckBox erinnerungAn;
    private LinearLayout vorschauen, profilListe, debug;
    private TextView quelle, weckerInfo;
    private Spinner anzeige;

    /** Welcher Plan in der Vorschau gezeigt wird (null = eigener) */
    static void setzeAnzeige(Context ctx, String profilId) {
        Erinnerungen.prefs(ctx).edit().putString(ANZEIGE, profilId).apply();
    }

    static String anzeige(Context ctx) {
        return Erinnerungen.prefs(ctx).getString(ANZEIGE, null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        pad = Math.round(20 * d);

        LinearLayout inhalt = new LinearLayout(this);
        inhalt.setOrientation(LinearLayout.VERTICAL);
        inhalt.setPadding(pad, pad, pad, pad);

        TextView titel = new TextView(this);
        titel.setText("Stundenplan");
        titel.setTextSize(26);
        titel.setTypeface(Typeface.DEFAULT_BOLD);
        inhalt.addView(titel);

        TextView info = klein("Widgets: Startbildschirm gedrückt halten → Widgets → Stundenplan (Stundenplan, Mensa, Abfahrten; frei in der Größe ziehbar). "
                + "Tippen auf eine Veranstaltung zeigt den Lageplan mit Route, „Fällt aus“ / „Raum ändern“ und bei Campuswechsel die Bus- und Bahnverbindungen.");
        inhalt.addView(info);

        // ───── Stundenplan ─────
        inhalt.addView(ueberschrift("Stundenplan"));
        quelle = new TextView(this);
        inhalt.addView(quelle);
        inhalt.addView(knopf("Veranstaltungen aus PULS wählen", v -> startActivity(new Intent(this, PulsActivity.class))));
        inhalt.addView(knopf("Lageplan für heute", v -> startActivity(LageplanActivity.fuerTag(this, MensaDaten.tagesBeginn(System.currentTimeMillis())))));

        // ───── Teilen & Profile ─────
        inhalt.addView(ueberschrift("Teilen & Profile"));
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Dein Name (steht auf deinem geteilten Plan)");
        name.setText(Teilen.name(this));
        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { Teilen.setzeName(MainActivity.this, s.toString()); }
        });
        inhalt.addView(name);
        inhalt.addView(knopf("Meinen Plan teilen (QR-Code, Datei)", v -> startActivity(new Intent(this, TeilenActivity.class))));
        LinearLayout empfangen = zeile();
        empfangen.addView(knopf("QR-Code scannen", v -> scannen()), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        empfangen.addView(knopf("Link einfügen", v -> linkEinfuegen()), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        inhalt.addView(empfangen);
        inhalt.addView(klein("Eine geteilte .ics-Datei einfach mit „Stundenplan“ öffnen."));
        profilListe = new LinearLayout(this);
        profilListe.setOrientation(LinearLayout.VERTICAL);
        inhalt.addView(profilListe);

        // ───── Wecker ─────
        inhalt.addView(ueberschrift("Wecker"));
        CheckBox weckerAn = new CheckBox(this);
        weckerAn.setText("Vor der ersten Veranstaltung des Tages wecken");
        weckerAn.setChecked(Wecker.an(this));
        inhalt.addView(weckerAn);
        LinearLayout weckerOpt = new LinearLayout(this);
        weckerOpt.setOrientation(LinearLayout.VERTICAL);
        String[] vorlaufNamen = new String[Wecker.VORLAUF_OPTIONEN.length];
        int vg = 4;
        for (int i = 0; i < vorlaufNamen.length; i++) {
            vorlaufNamen[i] = Wecker.vorlaufText(Wecker.VORLAUF_OPTIONEN[i]) + " vorher";
            if (Wecker.VORLAUF_OPTIONEN[i] == Wecker.vorlauf(this)) vg = i;
        }
        weckerOpt.addView(auswahl(vorlaufNamen, vg, i -> {
            Wecker.prefs(this).edit().putInt(Wecker.VORLAUF, Wecker.VORLAUF_OPTIONEN[i]).apply();
            weckerNeu();
        }));
        String[] frueh = new String[Wecker.FRUEHESTENS_OPTIONEN.length];
        int fg = 0;
        for (int i = 0; i < frueh.length; i++) {
            frueh[i] = Wecker.FRUEHESTENS_OPTIONEN[i] == 0 ? "frühestens: egal" : "frühestens um " + Wecker.uhrText(Wecker.FRUEHESTENS_OPTIONEN[i]);
            if (Wecker.FRUEHESTENS_OPTIONEN[i] == Wecker.fruehestens(this)) fg = i;
        }
        weckerOpt.addView(auswahl(frueh, fg, i -> {
            Wecker.prefs(this).edit().putInt(Wecker.FRUEHESTENS, Wecker.FRUEHESTENS_OPTIONEN[i]).apply();
            weckerNeu();
        }));
        weckerInfo = klein("");
        weckerOpt.addView(weckerInfo);
        weckerOpt.setVisibility(Wecker.an(this) ? View.VISIBLE : View.GONE);
        inhalt.addView(weckerOpt);
        weckerAn.setOnCheckedChangeListener((k, an) -> {
            Wecker.prefs(this).edit().putBoolean(Wecker.AN, an).apply();
            weckerOpt.setVisibility(an ? View.VISIBLE : View.GONE);
            weckerNeu();
        });

        // ───── Erinnerungen ─────
        inhalt.addView(ueberschrift("Erinnerungen"));
        erinnerungAn = new CheckBox(this);
        erinnerungAn.setText("Vor Veranstaltungen erinnern (mit Raum und Countdown)");
        erinnerungAn.setChecked(Erinnerungen.an(this) && Erinnerungen.erlaubt(this));
        inhalt.addView(erinnerungAn);
        String[] namen = new String[Erinnerungen.VORLAUF_OPTIONEN.length];
        int gewaehlt = 1;
        for (int i = 0; i < namen.length; i++) {
            namen[i] = Erinnerungen.VORLAUF_OPTIONEN[i] + " Minuten vorher";
            if (Erinnerungen.VORLAUF_OPTIONEN[i] == Erinnerungen.vorlauf(this)) gewaehlt = i;
        }
        inhalt.addView(auswahl(namen, gewaehlt, i -> {
            Erinnerungen.prefs(this).edit().putInt(Erinnerungen.VORLAUF, Erinnerungen.VORLAUF_OPTIONEN[i]).apply();
            Erinnerungen.planen(this);
        }));
        erinnerungAn.setOnCheckedChangeListener((knopf, an) -> {
            if (an && Build.VERSION.SDK_INT >= 33 && !Erinnerungen.erlaubt(this)) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, MITTEILUNGEN_ERLAUBEN);
                return;
            }
            Erinnerungen.prefs(this).edit().putBoolean(Erinnerungen.AN, an).apply();
            Erinnerungen.planen(this);
        });

        // ───── Live Update (Android 16) ─────
        if (LiveUpdate.verfuegbar()) {
            inhalt.addView(ueberschrift("Live Update"));
            CheckBox live = new CheckBox(this);
            live.setText("Laufende Veranstaltung in der Statusleiste und auf dem Sperrbildschirm");
            live.setChecked(LiveUpdate.an(this));
            live.setOnCheckedChangeListener((k, an) -> {
                Erinnerungen.prefs(this).edit().putBoolean(LiveUpdate.AN, an).apply();
                if (an && !Erinnerungen.erlaubt(this)) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, MITTEILUNGEN_ERLAUBEN);
                }
                LiveUpdate.planen(this);
            });
            inhalt.addView(live);
            inhalt.addView(klein("Ab einer Stunde vorher Countdown und Raum, während der Vorlesung Ende und Fortschritt (Samsung: Now Bar)."));
        }

        // ───── Campus ─────
        inhalt.addView(ueberschrift("Campus (Mensa und Abfahrten)"));
        final String[] campusWerte = {Campus.AUTO, Campus.GOLM, Campus.NEUES_PALAIS, Campus.GRIEBNITZSEE};
        String[] campusNamen = {"Automatisch (nach Stundenplan)", "Golm", "Am Neuen Palais", "Griebnitzsee"};
        int cw = 0;
        for (int i = 0; i < campusWerte.length; i++) if (campusWerte[i].equals(Campus.wahl(this))) cw = i;
        inhalt.addView(auswahl(campusNamen, cw, i -> {
            if (campusWerte[i].equals(Campus.wahl(this))) return;
            Campus.setzeWahl(this, campusWerte[i]);
            neuLaden();
        }));
        inhalt.addView(klein("Priorisierte Linien (stehen im Abfahrten-Widget immer mit drin, z. B. „RB20, 605“):"));
        EditText favFeld = new EditText(this);
        favFeld.setSingleLine(true);
        favFeld.setHint("RB20");
        favFeld.setText(String.join(", ", Campus.favoriten(this)));
        inhalt.addView(favFeld);
        inhalt.addView(knopf("Linien speichern", v -> {
            Campus.setzeFavoriten(this, favFeld.getText().toString());
            favFeld.setText(String.join(", ", Campus.favoriten(this)));
            Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show();
            neuLaden();
        }));

        // ───── Debug ─────
        Button debugKnopf = knopf("Debug ▾", null);
        inhalt.addView(debugKnopf);
        debug = new LinearLayout(this);
        debug.setOrientation(LinearLayout.VERTICAL);
        debug.setVisibility(View.GONE);
        debugKnopf.setOnClickListener(v -> debug.setVisibility(debug.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        debug.addView(klein("Stundenplan aus einer .ics-Datei (z. B. aus dem Planer exportiert):"));
        debug.addView(knopf("ICS-Datei importieren", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, ICS_WAEHLEN);
        }));
        debug.addView(klein("Planer-Link (planner.joshuan.dev, „Plan teilen“) – öffnet sich im Lageplan:"));
        EditText linkFeld = new EditText(this);
        linkFeld.setSingleLine(true);
        linkFeld.setHint(Konfig.TAP_URL);
        linkFeld.setText(getSharedPreferences(StundenplanWidget.EINSTELLUNGEN, MODE_PRIVATE).getString(StundenplanWidget.PLANER_LINK, ""));
        debug.addView(linkFeld);
        debug.addView(knopf("Link speichern", v -> {
            String link = linkFeld.getText().toString().trim();
            if (!link.isEmpty() && !link.startsWith("http")) {
                Toast.makeText(this, "Der Link muss mit https:// beginnen", Toast.LENGTH_LONG).show();
                return;
            }
            getSharedPreferences(StundenplanWidget.EINSTELLUNGEN, MODE_PRIVATE).edit().putString(StundenplanWidget.PLANER_LINK, link).apply();
            StundenplanWidget.alleAktualisieren(this);
            Toast.makeText(this, link.isEmpty() ? "Standard-Link wird verwendet" : "Link gespeichert", Toast.LENGTH_SHORT).show();
        }));
        debug.addView(knopf("Widgets neu zeichnen", v -> neuLaden()));
        inhalt.addView(debug);

        // ───── Vorschau ─────
        inhalt.addView(ueberschrift("Vorschau"));
        anzeige = new Spinner(this);
        inhalt.addView(anzeige);
        vorschauen = new LinearLayout(this);
        vorschauen.setOrientation(LinearLayout.VERTICAL);
        vorschauen.setPadding(0, pad / 2, 0, 0);
        inhalt.addView(vorschauen);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(inhalt);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        neuLaden();
        // Höchstens einmal täglich mit PULS abgleichen (Android plant das auch selbst im Hintergrund)
        new Thread(() -> {
            if (PulsDienst.abgleichen(this, false)) runOnUiThread(this::neuLaden);
        }).start();
    }

    // ───────────── Bausteine ─────────────

    interface Wahl { void gewaehlt(int i); }

    private Spinner auswahl(String[] namen, int start, Wahl w) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, namen));
        s.setSelection(start);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean erst = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (erst) { erst = false; return; }
                w.gewaehlt(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        return s;
    }

    private Button knopf(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private LinearLayout zeile() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private TextView ueberschrift(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(19);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, Math.round(22 * d), 0, Math.round(4 * d));
        return v;
    }

    private TextView klein(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setAlpha(0.75f);
        return v;
    }

    // ───────────── Wecker ─────────────

    private void weckerNeu() {
        Wecker.planen(this);
        if (!Wecker.an(this)) return;
        StringBuilder sb = new StringBuilder();
        long[] n = Wecker.naechste(this);
        sb.append(n == null ? "Kein Wecker in den nächsten 14 Tagen."
                : "Nächster Wecker: " + Lageplan.datumKurz(n[0]) + " " + Lageplan.uhrzeitText(n[0])
                + " (Beginn " + Lageplan.uhrzeitText(n[1]) + ")");
        sb.append("\nKlingelt nur an Tagen mit Veranstaltungen, ausgefallene Termine zählen nicht.");
        weckerInfo.setText(sb.toString());
        if (!Wecker.darfStellen(this) && Build.VERSION.SDK_INT >= 31) {
            weckerInfo.append("\n⚠ Für pünktliches Wecken bitte „Wecker und Erinnerungen“ erlauben (antippen).");
            weckerInfo.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))));
        }
    }

    // ───────────── Teilen & Profile ─────────────

    private void scannen() {
        try {
            GmsBarcodeScannerOptions o = new GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
            GmsBarcodeScanning.getClient(this, o).startScan()
                    .addOnSuccessListener(b -> empfangen(b.getRawValue()))
                    .addOnFailureListener(e -> Toast.makeText(this, "Scanner nicht verfügbar – alternativ den Link einfügen.", Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            Toast.makeText(this, "Scanner nicht verfügbar – alternativ den Link einfügen.", Toast.LENGTH_LONG).show();
        }
    }

    private void linkEinfuegen() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String text = null;
        if (cm != null && cm.hasPrimaryClip()) {
            ClipData c = cm.getPrimaryClip();
            if (c != null && c.getItemCount() > 0 && c.getItemAt(0).getText() != null) text = c.getItemAt(0).getText().toString();
        }
        if (EmpfangActivity.ausText(text) != null) { empfangen(text); return; }
        EditText feld = new EditText(this);
        feld.setHint("stundenplan://teilen?d=…");
        new AlertDialog.Builder(this).setTitle("Link einfügen").setView(feld)
                .setPositiveButton("Öffnen", (x, y) -> empfangen(feld.getText().toString()))
                .setNegativeButton("Abbrechen", null).show();
    }

    private void empfangen(String text) {
        if (EmpfangActivity.ausText(text) == null) {
            Toast.makeText(this, "Das ist kein Stundenplan-Link.", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, EmpfangActivity.class).putExtra(EmpfangActivity.EXTRA_LINK, text));
    }

    private void profileZeigen() {
        profilListe.removeAllViews();
        List<Teilen.Profil> alle = Teilen.profile(this);
        if (alle.isEmpty()) return;
        profilListe.addView(klein("Profile anderer:"));
        for (Teilen.Profil p : alle) {
            LinearLayout z = zeile();
            z.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(this);
            t.setText(p.name + "\n" + p.termine.size() + " Termine · Stand " + Lageplan.datumKurz(p.stand) + (p.freigabe != null ? " · PULS" : ""));
            t.setOnClickListener(v -> { setzeAnzeige(this, p.id); neuLaden(); });
            z.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            z.addView(knopf("⋯", v -> profilMenue(p)));
            profilListe.addView(z);
        }
    }

    private void profilMenue(Teilen.Profil p) {
        List<String> punkte = new ArrayList<>();
        punkte.add("Anzeigen");
        punkte.add("Umbenennen");
        if (p.freigabe != null) punkte.add("Aus PULS aktualisieren");
        punkte.add("Löschen");
        new AlertDialog.Builder(this).setTitle(p.name).setItems(punkte.toArray(new String[0]), (dlg, i) -> {
            String w = punkte.get(i);
            if (w.equals("Anzeigen")) { setzeAnzeige(this, p.id); neuLaden(); }
            else if (w.equals("Umbenennen")) umbenennen(p);
            else if (w.equals("Löschen")) {
                List<Teilen.Profil> alle = Teilen.profile(this);
                alle.removeIf(x -> x.id.equals(p.id));
                Teilen.speichern(this, alle);
                if (p.id.equals(anzeige(this))) setzeAnzeige(this, null);
                neuLaden();
            } else aktualisieren(p);
        }).show();
    }

    private void umbenennen(Teilen.Profil p) {
        EditText feld = new EditText(this);
        feld.setSingleLine(true);
        feld.setText(p.name);
        new AlertDialog.Builder(this).setTitle("Name").setView(feld)
                .setPositiveButton("Speichern", (x, y) -> {
                    List<Teilen.Profil> alle = Teilen.profile(this);
                    for (Teilen.Profil q : alle) if (q.id.equals(p.id)) q.name = feld.getText().toString().trim().isEmpty() ? q.name : feld.getText().toString().trim();
                    Teilen.speichern(this, alle);
                    neuLaden();
                })
                .setNegativeButton("Abbrechen", null).show();
    }

    private void aktualisieren(Teilen.Profil p) {
        Teilen.Freigabe f = p.freigabe();
        if (f == null) return;
        Toast.makeText(this, "Wird aus PULS geladen …", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<Termin> t = Teilen.termineLaden(this, f);
            runOnUiThread(() -> {
                if (t == null) { Toast.makeText(this, "PULS war nicht erreichbar.", Toast.LENGTH_LONG).show(); return; }
                List<Teilen.Profil> alle = Teilen.profile(this);
                for (Teilen.Profil q : alle) if (q.id.equals(p.id)) { q.termine = t; q.stand = System.currentTimeMillis(); }
                Teilen.speichern(this, alle);
                neuLaden();
            });
        }).start();
    }

    // ───────────── Anzeigen ─────────────

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MITTEILUNGEN_ERLAUBEN) return;
        boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (erinnerungAn.isChecked() || !ok) {
            Erinnerungen.prefs(this).edit().putBoolean(Erinnerungen.AN, ok && erinnerungAn.isChecked()).apply();
        }
        if (!ok) {
            erinnerungAn.setChecked(false);
            Toast.makeText(this, "Mitteilungen sind nicht erlaubt (Einstellungen → Apps → Stundenplan)", Toast.LENGTH_LONG).show();
        }
        Erinnerungen.planen(this);
        LiveUpdate.planen(this);
    }

    private void neuLaden() {
        PulsDienst.Auswahl a = PulsDienst.auswahl(this);
        int anzahl = Daten.laden(this).size();
        quelle.setText(a.aktiv ? "Quelle: PULS (" + a.kurse.size() + " Veranstaltungen, " + anzahl + " Termine)"
                : Daten.vorhanden(this) ? "Quelle: Datei (" + anzahl + " Termine)"
                : "Noch kein Stundenplan – Veranstaltungen aus PULS wählen oder einen geteilten Plan per QR-Code/Link öffnen.");
        PulsDienst.planen(this);
        PulsDienst.nachAenderung(this);
        weckerNeu();
        profileZeigen();
        anzeigeAuswahl();
        zeigeVorschau();
    }

    private void anzeigeAuswahl() {
        List<Teilen.Profil> alle = Teilen.profile(this);
        String[] namen = new String[alle.size() + 1];
        String eigener = Teilen.name(this);
        namen[0] = eigener.isEmpty() ? "Mein Plan" : eigener + " (ich)";
        int wahl = 0;
        String id = anzeige(this);
        for (int i = 0; i < alle.size(); i++) {
            namen[i + 1] = "Plan von " + alle.get(i).name;
            if (alle.get(i).id.equals(id)) wahl = i + 1;
        }
        anzeige.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, namen));
        anzeige.setSelection(wahl);
        anzeige.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean erst = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id2) {
                if (erst) { erst = false; return; }
                List<Teilen.Profil> l = Teilen.profile(MainActivity.this);
                setzeAnzeige(MainActivity.this, position == 0 || position - 1 >= l.size() ? null : l.get(position - 1).id);
                zeigeVorschau();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void zeigeVorschau() {
        vorschauen.removeAllViews();
        Teilen.Profil p = Teilen.profil(this, anzeige(this));
        List<Termin> alle = p != null ? p.angezeigt() : Daten.laden(this);
        boolean dunkel = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        long jetzt = System.currentTimeMillis();
        TextView ueberschrift = new TextView(this);
        ueberschrift.setText(p != null ? "Stundenplan von " + p.name : "Stundenplan");
        ueberschrift.setTextSize(17);
        vorschauen.addView(ueberschrift);
        String[][] groessen = p != null ? new String[][]{{Zeichner.MITTEL, "340", "160"}, {Zeichner.GROSS, "340", "356"}}
                : new String[][]{{Zeichner.KLEIN, "160", "160"}, {Zeichner.MITTEL, "340", "160"}, {Zeichner.GROSS, "340", "356"}};
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
        if (p == null) {
            zeigeMensaVorschau(dunkel, d, jetzt);
            zeigeAbfahrtenVorschau(dunkel, d, jetzt);
        }
    }

    /** Abfahrten-Vorschau: im Hintergrund laden, dann anzeigen. */
    private void zeigeAbfahrtenVorschau(boolean dunkel, float d, long jetzt) {
        TextView ueberschrift = new TextView(this);
        final String campus = Campus.aktuell(this);
        final List<String> favoriten = Campus.favoriten(this);
        ueberschrift.setText("Abfahrten " + Campus.kurz(campus) + " (lädt …)");
        ueberschrift.setTextSize(17);
        vorschauen.addView(ueberschrift);
        new Thread(() -> {
            List<AbfahrtenDaten.Abfahrt> liste = AbfahrtenDaten.laden(this, campus, true);
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
        ueberschrift.setTextSize(17);
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
        // Gleicher Weg wie eine geteilte Datei: als Profil, als eigenen Plan oder in der Modulwahl
        Intent i = new Intent(this, EmpfangActivity.class).setAction(Intent.ACTION_VIEW).setData(data.getData())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try (InputStream ignored = getContentResolver().openInputStream(data.getData())) {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Datei konnte nicht gelesen werden: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
