package de.stundenplan.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Eigenen Termin anlegen oder bearbeiten (Plus im Plan) bzw. die Liste der eigenen Termine. */
public class EigenerTerminActivity extends Activity {

    static final String EXTRA_ID = "id", EXTRA_LISTE = "liste";

    private Ui ui;
    private LinearLayout inhalt;
    private EigeneTermine.Eintrag e;
    private boolean neu;
    private EditText titel, raum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        inhalt = new LinearLayout(this);
        inhalt.setOrientation(LinearLayout.VERTICAL);
        inhalt.setBackgroundColor(ui.bg);
        inhalt.setPadding(0, ui.dp(8), 0, ui.dp(30));
        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(ui.bg);
        s.addView(inhalt);
        setContentView(s);
        if (getIntent().getBooleanExtra(EXTRA_LISTE, false)) liste(); else formular(getIntent().getStringExtra(EXTRA_ID));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getIntent().getBooleanExtra(EXTRA_LISTE, false)) liste();
    }

    // ───────────── Liste ─────────────

    private void liste() {
        setTitle("Eigene Termine");
        inhalt.removeAllViews();
        List<EigeneTermine.Eintrag> alle = EigeneTermine.alle(this);
        alle.sort((a, b) -> Long.compare(a.start, b.start));
        LinearLayout g = ui.gruppe();
        List<View> z = new ArrayList<>();
        z.add(ui.knopfZeile("＋ Neuer Termin", v -> startActivity(new android.content.Intent(this, EigenerTerminActivity.class))));
        for (EigeneTermine.Eintrag x : alle) {
            LinearLayout zeile = ui.zeile(v -> startActivity(new android.content.Intent(this, EigenerTerminActivity.class).putExtra(EXTRA_ID, x.id)));
            LinearLayout m = new LinearLayout(this);
            m.setOrientation(LinearLayout.VERTICAL);
            m.addView(ui.fett(x.titel, 16, ui.vorder));
            m.addView(ui.text(beschreibung(x), 13, ui.gedimmt));
            zeile.addView(m, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            zeile.addView(ui.text("›", 20, ui.gedimmt));
            z.add(zeile);
        }
        ui.fuellen(g, z.toArray(new View[0]));
        inhalt.addView(g, ui.mitRand(16, 8, 16, 0));
        if (alle.isEmpty()) inhalt.addView(ui.fuss("Noch keine eigenen Termine."));
    }

    static String beschreibung(EigeneTermine.Eintrag x) {
        String wdh = x.abstandTage == 7 ? " · jede Woche" : x.abstandTage == 14 ? " · alle 2 Wochen" : "";
        return x.typ + " · " + Lageplan.datumKurz(x.start) + " " + Lageplan.uhrzeitText(x.start) + "–" + Lageplan.uhrzeitText(x.ende)
                + wdh + (x.raum.isEmpty() ? "" : " · " + x.raum);
    }

    // ───────────── Formular ─────────────

    private void formular(String id) {
        e = null;
        if (id != null) for (EigeneTermine.Eintrag x : EigeneTermine.alle(this)) if (x.id.equals(id)) e = x;
        neu = e == null;
        if (neu) {
            e = new EigeneTermine.Eintrag();
            Calendar c = Calendar.getInstance();
            c.add(Calendar.HOUR_OF_DAY, 1);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            e.start = c.getTimeInMillis();
            e.ende = e.start + 90 * 60_000L;
        }
        setTitle(neu ? "Neuer Termin" : "Termin bearbeiten");
        titel = feld("Titel, z. B. Lerngruppe Mathe", e.titel);
        raum = feld("Raum (optional), z. B. 2.70.0.10", e.raum);
        raum.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        zeichnen();
    }

    private EditText feld(String hinweis, String wert) {
        EditText f = new EditText(this);
        f.setSingleLine(true);
        f.setHint(hinweis);
        f.setText(wert);
        f.setBackground(null);
        f.setTextColor(ui.vorder);
        f.setHintTextColor(ui.gedimmt);
        return f;
    }

    private void zeichnen() {
        inhalt.removeAllViews();
        inhalt.addView(ui.abschnitt("Termin"));
        LinearLayout g = ui.gruppe();
        if (titel.getParent() != null) ((LinearLayout) titel.getParent()).removeView(titel);
        if (raum.getParent() != null) ((LinearLayout) raum.getParent()).removeView(raum);
        LinearLayout t1 = ui.zeile(null);
        t1.addView(titel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout t2 = ui.zeile(null);
        t2.addView(raum, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        ui.fuellen(g, t1, ui.wertZeile("Art", e.typ + " ›", v -> art()), t2);
        inhalt.addView(g);
        inhalt.addView(ui.fuss("Mit Raumnummer der Uni (z. B. 2.70.0.10) erscheint der Termin auch im Lageplan mit Route."));

        inhalt.addView(ui.abschnitt("Zeit"));
        g = ui.gruppe();
        String wdh = e.abstandTage == 7 ? "Jede Woche" : e.abstandTage == 14 ? "Alle 2 Wochen" : "Nie";
        List<View> z = new ArrayList<>();
        z.add(ui.wertZeile("Datum", Lageplan.datumKurz(e.start) + " ›", v -> datum()));
        z.add(ui.wertZeile("Beginn", Lageplan.uhrzeitText(e.start) + " ›", v -> uhrzeit(true)));
        z.add(ui.wertZeile("Ende", Lageplan.uhrzeitText(e.ende) + " ›", v -> uhrzeit(false)));
        z.add(ui.wertZeile("Wiederholen", wdh + " ›", v -> wiederholung()));
        if (e.abstandTage > 0) z.add(ui.wertZeile("Endet am", (e.bis > 0 ? Lageplan.datumKurz(e.bis) : "nach einem Jahr") + " ›", v -> bis()));
        ui.fuellen(g, z.toArray(new View[0]));
        inhalt.addView(g);
        inhalt.addView(ui.fuss("Eigene Termine zählen wie Veranstaltungen: Widgets, Erinnerungen und Wecker berücksichtigen sie. Beim Teilen des Plans wählst du aus, welche mitkommen."));

        LinearLayout k = ui.gruppe();
        List<View> kz = new ArrayList<>();
        kz.add(ui.knopfZeile("Sichern", v -> sichern()));
        if (!neu) {
            LinearLayout loeschen = ui.zeile(v -> new AlertDialog.Builder(this).setMessage("„" + e.titel + "“ löschen?")
                    .setPositiveButton("Löschen", (d, w) -> {
                        List<EigeneTermine.Eintrag> alle = EigeneTermine.alle(this);
                        alle.removeIf(x -> x.id.equals(e.id));
                        EigeneTermine.speichern(this, alle);
                        finish();
                    }).setNegativeButton("Abbrechen", null).show());
            loeschen.addView(ui.text("Termin löschen", 16, ui.rot));
            kz.add(loeschen);
        }
        ui.fuellen(k, kz.toArray(new View[0]));
        inhalt.addView(k, ui.mitRand(16, 24, 16, 0));
    }

    private void merken() {
        e.titel = titel.getText().toString().trim();
        e.raum = raum.getText().toString().trim();
    }

    private void art() {
        merken();
        new AlertDialog.Builder(this).setTitle("Art").setItems(EigeneTermine.ARTEN, (d, i) -> { e.typ = EigeneTermine.ARTEN[i]; zeichnen(); }).show();
    }

    private void datum() {
        merken();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(e.start);
        new DatePickerDialog(this, (v, j, m, t) -> {
            long dauer = e.ende - e.start;
            Calendar n = Calendar.getInstance();
            n.setTimeInMillis(e.start);
            n.set(j, m, t);
            e.start = n.getTimeInMillis();
            e.ende = e.start + dauer;
            zeichnen();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void uhrzeit(boolean beginn) {
        merken();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(beginn ? e.start : e.ende);
        new TimePickerDialog(this, (v, h, m) -> {
            long dauer = e.ende - e.start;
            Calendar n = Calendar.getInstance();
            n.setTimeInMillis(e.start);
            n.set(Calendar.HOUR_OF_DAY, h);
            n.set(Calendar.MINUTE, m);
            if (beginn) {
                e.start = n.getTimeInMillis();
                e.ende = e.start + dauer;
            } else {
                e.ende = n.getTimeInMillis();
                if (e.ende <= e.start) e.ende = e.start + 60 * 60_000L;
            }
            zeichnen();
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
    }

    private void wiederholung() {
        merken();
        String[] n = {"Nie", "Jede Woche", "Alle 2 Wochen"};
        int[] w = {0, 7, 14};
        new AlertDialog.Builder(this).setTitle("Wiederholen").setItems(n, (d, i) -> { e.abstandTage = w[i]; zeichnen(); }).show();
    }

    private void bis() {
        merken();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(e.bis > 0 ? e.bis : e.start + 90 * 86400_000L);
        DatePickerDialog d = new DatePickerDialog(this, (v, j, m, t) -> {
            Calendar n = Calendar.getInstance();
            n.set(j, m, t, 12, 0, 0);
            e.bis = n.getTimeInMillis();
            zeichnen();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.getDatePicker().setMinDate(e.start);
        d.show();
    }

    private void sichern() {
        merken();
        if (e.titel.isEmpty()) {
            Toast.makeText(this, "Bitte einen Titel eingeben.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (e.abstandTage == 0) e.bis = 0;
        List<EigeneTermine.Eintrag> alle = EigeneTermine.alle(this);
        boolean ersetzt = false;
        for (int i = 0; i < alle.size(); i++) if (alle.get(i).id.equals(e.id)) { alle.set(i, e); ersetzt = true; }
        if (!ersetzt) alle.add(e);
        EigeneTermine.speichern(this, alle);
        Toast.makeText(this, "Gesichert", Toast.LENGTH_SHORT).show();
        finish();
    }
}
