package de.stundenplan.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Geteilten Stundenplan öffnen (Link/QR-Code stundenplan://teilen?d=… oder .ics-Datei):
 * als Profil speichern, als eigenen Plan übernehmen oder in der Modulwahl öffnen und anpassen.
 */
public class EmpfangActivity extends Activity {

    static final String EXTRA_LINK = "link";

    private String name = "";
    private Teilen.Freigabe freigabe;
    private List<Termin> termine;      // bei Dateien schon da, beim QR-Code erst aus PULS
    private EditText nameFeld;
    private ProgressBar laeuft;
    private LinearLayout inhalt;

    /** Freigabe aus einem Link oder Text mit Link */
    static Teilen.Freigabe ausText(String text) {
        if (text == null) return null;
        int i = text.indexOf("stundenplan://teilen");
        if (i < 0) return null;
        Uri u = Uri.parse(text.substring(i).split("\\s")[0]);
        String d = u.getQueryParameter("d");
        return d == null ? null : Teilen.Freigabe.ausCode(d);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Geteilter Stundenplan");
        if (!lesen(getIntent())) {
            Toast.makeText(this, "Darin wurde kein Stundenplan gefunden.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        aufbauen();
    }

    private boolean lesen(Intent intent) {
        try {
            String link = intent.getStringExtra(EXTRA_LINK);
            if (link == null && Intent.ACTION_SEND.equals(intent.getAction())) link = intent.getStringExtra(Intent.EXTRA_TEXT);
            Uri daten = intent.getData();
            if (daten == null && Intent.ACTION_SEND.equals(intent.getAction())) daten = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (link == null && daten != null && "stundenplan".equals(daten.getScheme())) link = daten.toString();
            if (link != null) {
                freigabe = ausText(link);
                if (freigabe == null) return false;
                name = freigabe.name;
                return true;
            }
            if (daten == null) return false;
            String text;
            try (InputStream in = getContentResolver().openInputStream(daten)) {
                if (in == null) return false;
                text = Daten.lesen(in);
            }
            termine = Daten.parseICS(text);
            if (termine.isEmpty()) return false;
            freigabe = Teilen.kopfFreigabe(text);
            String n = Teilen.kopfName(text);
            name = n != null ? n : "";
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void aufbauen() {
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * d);
        inhalt = new LinearLayout(this);
        inhalt.setOrientation(LinearLayout.VERTICAL);
        inhalt.setPadding(pad, pad, pad, pad);

        TextView t = new TextView(this);
        t.setText("Wessen Plan?");
        t.setTypeface(Typeface.DEFAULT_BOLD);
        inhalt.addView(t);
        nameFeld = new EditText(this);
        nameFeld.setSingleLine(true);
        nameFeld.setHint("Name");
        nameFeld.setText(name);
        inhalt.addView(nameFeld);

        TextView info = new TextView(this);
        StringBuilder sb = new StringBuilder();
        if (freigabe != null && freigabe.mitPuls()) {
            sb.append("Fächer aus PULS:");
            for (PulsDienst.Kurs k : freigabe.kurse) sb.append("\n• ").append(k.titel).append(k.gruppe != null ? " (Gr. " + k.gruppe + ")" : "");
        }
        if (termine != null) sb.append(sb.length() > 0 ? "\n\n" : "").append(termine.size()).append(" Termine");
        info.setText(sb.toString());
        info.setPadding(0, pad / 2, 0, pad / 2);
        inhalt.addView(info);

        Button profil = new Button(this);
        profil.setText("Als Profil hinzufügen");
        profil.setOnClickListener(v -> alsProfil());
        inhalt.addView(profil);
        if (freigabe != null && freigabe.mitPuls()) {
            Button modulwahl = new Button(this);
            modulwahl.setText("Als meinen Plan in der Modulwahl öffnen");
            modulwahl.setOnClickListener(v -> fragen("Deine Fächerauswahl wird durch diese ersetzt. Der Stundenplan selbst ändert sich erst, wenn du in der Modulwahl „Übernehmen“ tippst.",
                    this::inModulwahl));
            inhalt.addView(modulwahl);
        }
        if (termine != null) {
            Button ueber = new Button(this);
            ueber.setText("Als meinen Plan übernehmen");
            ueber.setOnClickListener(v -> fragen("Deine Termine werden durch diese ersetzt.", this::uebernehmen));
            inhalt.addView(ueber);
        }
        TextView hilfe = new TextView(this);
        hilfe.setText("\nProfil: der Plan lässt sich in der App umschalten, dein eigener bleibt, wie er ist. "
                + "Modulwahl: gleiche Fächer und Gruppen als Startpunkt – anpassen und dann übernehmen.");
        hilfe.setAlpha(0.75f);
        inhalt.addView(hilfe);
        laeuft = new ProgressBar(this);
        laeuft.setVisibility(android.view.View.GONE);
        inhalt.addView(laeuft);

        ScrollView s = new ScrollView(this);
        s.addView(inhalt);
        setContentView(s);
    }

    private void fragen(String text, Runnable dann) {
        new AlertDialog.Builder(this).setTitle("Dein bisheriger Plan wird ersetzt").setMessage(text)
                .setPositiveButton("Weiter", (x, y) -> dann.run())
                .setNegativeButton("Abbrechen", null).show();
    }

    private String anzeigename() {
        String n = nameFeld.getText().toString().trim();
        return n.isEmpty() ? "Ohne Namen" : n;
    }

    private void alsProfil() {
        final String n = anzeigename();
        if (termine != null) { profilSpeichern(n, termine); return; }
        laeuft.setVisibility(android.view.View.VISIBLE);
        inhalt.setEnabled(false);
        Toast.makeText(this, "Termine werden aus PULS geladen …", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<Termin> t = Teilen.termineLaden(this, freigabe);
            runOnUiThread(() -> {
                laeuft.setVisibility(android.view.View.GONE);
                if (t == null) {
                    Toast.makeText(this, "Die Termine konnten nicht aus PULS geladen werden. Bitte später nochmal versuchen.", Toast.LENGTH_LONG).show();
                    return;
                }
                profilSpeichern(n, t);
            });
        }).start();
    }

    private void profilSpeichern(String n, List<Termin> t) {
        List<Teilen.Profil> alle = Teilen.profile(this);
        Teilen.Profil p = new Teilen.Profil();
        p.name = n;
        p.termine = new ArrayList<>(t);
        p.freigabe = freigabe != null && freigabe.mitPuls() ? freigabe.code() : null;
        alle.add(p);
        Teilen.speichern(this, alle);
        MainActivity.setzeAnzeige(this, p.id);
        Toast.makeText(this, "Profil „" + n + "“ gespeichert", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_REITER, MainActivity.PLAN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private void inModulwahl() {
        PulsDienst.Auswahl a = PulsDienst.auswahl(this);
        a.kurse = freigabe.kurse;
        if (freigabe.semester != null) a.semester = freigabe.semester;
        a.aktiv = false;
        PulsDienst.speichern(this, a);
        startActivity(new Intent(this, PulsActivity.class));
        finish();
    }

    private void uebernehmen() {
        PulsDienst.deaktivieren(this);
        PulsDienst.schreibenText(this, Daten.ICS_DATEI, Teilen.ics(termine, Teilen.name(this), null));
        PulsDienst.nachAenderung(this);
        MainActivity.setzeAnzeige(this, null);
        Toast.makeText(this, termine.size() + " Termine übernommen", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_REITER, MainActivity.PLAN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inhalt != null) inhalt.setBackgroundColor(Color.TRANSPARENT);
    }
}
