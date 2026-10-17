package de.stundenplan.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modulwahl aus PULS (wie auf iOS): Fächer suchen, Gruppen wählen, Termine ausblenden,
 * Vorschau der Woche, dann als Stundenplan übernehmen. Danach täglicher Abgleich.
 */
public class PulsActivity extends Activity {

    private final ExecutorService hintergrund = Executors.newSingleThreadExecutor();
    private float d;
    private boolean dunkel;
    private LinearLayout faecher, treffer, optionen;
    private TextView katalogStatus, kopf;
    private WochenView raster;
    private TextView vorschauTitel;
    /** Fach, dessen Gruppen grau im Plan erscheinen */
    private String fokus;
    private Button uebernehmen;
    private EditText suche;
    private int wochenVersatz = 0;
    private String laedt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Modulwahl (PULS)");
        d = getResources().getDisplayMetrics().density;
        dunkel = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int pad = dp(16);

        LinearLayout inhalt = new LinearLayout(this);
        inhalt.setOrientation(LinearLayout.VERTICAL);
        inhalt.setPadding(pad, pad, pad, pad);

        kopf = new TextView(this);
        kopf.setTextSize(20);
        inhalt.addView(kopf);

        LinearLayout knoepfe = zeile();
        uebernehmen = new Button(this);
        uebernehmen.setOnClickListener(v -> uebernehmen());
        knoepfe.addView(uebernehmen);
        Button opt = new Button(this);
        opt.setText("Optionen");
        opt.setOnClickListener(v -> optionen.setVisibility(optionen.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        knoepfe.addView(opt);
        inhalt.addView(knoepfe);

        optionen = new LinearLayout(this);
        optionen.setOrientation(LinearLayout.VERTICAL);
        optionen.setVisibility(View.GONE);
        inhalt.addView(optionen);

        // Wochenvorschau
        LinearLayout woche = zeile();
        woche.setGravity(Gravity.CENTER_VERTICAL);
        Button zurueck = new Button(this);
        zurueck.setText("‹");
        zurueck.setOnClickListener(v -> { wochenVersatz--; vorschauZeichnen(); });
        Button vor = new Button(this);
        vor.setText("›");
        vor.setOnClickListener(v -> { wochenVersatz++; vorschauZeichnen(); });
        vorschauTitel = new TextView(this);
        vorschauTitel.setGravity(Gravity.CENTER);
        vorschauTitel.setOnClickListener(v -> { wochenVersatz = 0; vorschauZeichnen(); });
        woche.addView(zurueck);
        woche.addView(vorschauTitel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        woche.addView(vor);
        inhalt.addView(woche);
        raster = new WochenView(this);
        android.widget.FrameLayout rahmen = new android.widget.FrameLayout(this);
        GradientDrawable rhg = new GradientDrawable();
        rhg.setColor(Color.parseColor(dunkel ? "#1C1C1E" : "#FFFFFF"));
        rhg.setCornerRadius(dp(14));
        rahmen.setBackground(rhg);
        rahmen.setPadding(dp(4), dp(8), dp(4), dp(4));
        rahmen.addView(raster, new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, dp(400)));
        inhalt.addView(rahmen);
        inhalt.addView(klein("Fach antippen: alle Gruppen erscheinen grau gestrichelt im Plan – Gruppe dort antippen zum Wählen. "
                + "Termin gedrückt halten: aus dem Stundenplan aus- bzw. wieder einblenden. Wischen blättert die Woche."));

        // Suche
        inhalt.addView(ueberschrift("Fach suchen"));
        suche = new EditText(this);
        suche.setSingleLine(true);
        suche.setHint("z. B. „Maschinenmodelle“ oder „INF-1030“");
        suche.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { trefferZeigen(); }
        });
        inhalt.addView(suche);
        katalogStatus = klein("");
        inhalt.addView(katalogStatus);
        treffer = new LinearLayout(this);
        treffer.setOrientation(LinearLayout.VERTICAL);
        inhalt.addView(treffer);

        inhalt.addView(ueberschrift("Deine Fächer"));
        inhalt.addView(klein("Gruppe antippen zum Wählen. „Termine“: einzelne wiederkehrende Termine (z. B. Zusatzübungen) aus dem Stundenplan nehmen – sie bleiben hier sichtbar und lassen sich wieder einblenden."));
        faecher = new LinearLayout(this);
        faecher.setOrientation(LinearLayout.VERTICAL);
        inhalt.addView(faecher);

        // PULS-Link
        inhalt.addView(ueberschrift("PULS-Link einfügen"));
        LinearLayout linkZeile = zeile();
        EditText link = new EditText(this);
        link.setSingleLine(true);
        link.setHint("Link zur Veranstaltung in PULS");
        linkZeile.addView(link, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button dazu = new Button(this);
        dazu.setText("Hinzufügen");
        dazu.setOnClickListener(v -> {
            String id = Puls.publishid(link.getText().toString());
            if (id == null) { Toast.makeText(this, "Kein PULS-Link erkannt", Toast.LENGTH_SHORT).show(); return; }
            link.setText("");
            hinzufuegen(id);
        });
        linkZeile.addView(dazu);
        inhalt.addView(linkZeile);
        Button stoebern = new Button(this);
        stoebern.setText("Im Vorlesungsverzeichnis stöbern");
        stoebern.setOnClickListener(v -> startActivity(new Intent(this, VerzeichnisActivity.class)));
        inhalt.addView(stoebern);
        Button oeffnen = new Button(this);
        oeffnen.setText("PULS im Browser öffnen");
        oeffnen.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Puls.BASIS + "?state=wtree&search=1&trex=step&root1" + PulsDienst.auswahl(this).semester + "=0&P.vx=kurz"))));
        inhalt.addView(oeffnen);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(inhalt);
        setContentView(scroll);

        allesNeu();
        katalogLaden(false);
        hintergrund.execute(() -> {
            PulsDienst.fehlendeLaden(this);
            runOnUiThread(this::allesNeu);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (kopf != null) allesNeu();
    }

    @Override
    protected void onDestroy() {
        hintergrund.shutdown();
        super.onDestroy();
    }

    // ───────────── Hilfen ─────────────

    private int dp(float v) { return Math.round(v * d); }

    private LinearLayout zeile() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private TextView ueberschrift(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(18);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(18), 0, dp(4));
        return v;
    }

    private TextView klein(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setAlpha(0.75f);
        return v;
    }

    private GradientDrawable rahmen(int farbe, boolean gefuellt) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        if (gefuellt) g.setColor(farbe);
        else {
            g.setColor(Color.argb(30, 128, 128, 128));
            g.setStroke(Math.max(1, dp(1)), farbe);
        }
        return g;
    }

    private void allesNeu() {
        PulsDienst.Auswahl a = PulsDienst.auswahl(this);
        kopf.setText("Semester: " + Puls.semesterName(a.semester));
        uebernehmen.setText(a.aktiv ? "Aktualisieren" : "Als Stundenplan übernehmen");
        uebernehmen.setEnabled(!a.kurse.isEmpty());
        optionenZeigen(a);
        faecherZeigen(a);
        vorschauZeichnen();
    }

    // ───────────── Vorschau ─────────────

    private void vorschauZeichnen() {
        PulsDienst.Auswahl a = PulsDienst.auswahl(this);
        List<Termin> t = rasterTermine(a);
        long jetzt = System.currentTimeMillis();
        // Vor Semesterbeginn: die erste Vorlesungswoche zeigen
        long basis = jetzt;
        long erster = Long.MAX_VALUE;
        for (Termin x : t) if (x.stil != 1) erster = Math.min(erster, x.start);
        if (erster != Long.MAX_VALUE && erster > jetzt) basis = erster;
        long mo = WochenView.montag(basis, wochenVersatz);
        vorschauTitel.setText("Woche vom " + Lageplan.datumKurz(mo) + (wochenVersatz == 0 ? "" : "  (antippen: zurück)"));
        raster.setze(mo, t, new WochenView.Aktion() {
            @Override public void termin(Termin x) { rasterTippen(x); }
            @Override public void lang(Termin x) { rasterHalten(x); }
            @Override public void blaettern(int r) { wochenVersatz += r; vorschauZeichnen(); }
        });
    }

    /** Termine für das Raster: gewählte Gruppen, ausgeblendete blass, beim Fokus-Fach die anderen Gruppen grau */
    private List<Termin> rasterTermine(PulsDienst.Auswahl a) {
        List<Termin> aus = new ArrayList<>();
        for (PulsDienst.Kurs k : a.kurse) {
            Puls.Veranstaltung v = PulsDienst.cache(this).get(k.publishid);
            if (v == null || v.gruppen.isEmpty()) continue;
            Puls.Gruppe g = k.gruppe != null && v.gruppe(k.gruppe) != null ? v.gruppe(k.gruppe) : v.gruppen.get(0);
            Set<String> weg = new HashSet<>(k.ausgeblendet);
            for (Puls.Regel r : g.regeln) {
                for (Puls.EinzelTermin e : Puls.termine(r)) {
                    Termin x = termin(e, k.titel, weg.contains(r.schluessel()) ? 2 : 0, null, new String[]{k.publishid, g.nummer, r.schluessel()});
                    aus.add(x);
                }
            }
            if (k.publishid.equals(fokus) && v.gruppen.size() > 1) {
                for (Puls.Gruppe og : v.gruppen) {
                    if (og == g) continue;
                    for (Puls.Regel r : v.eigenes(og)) for (Puls.EinzelTermin e : Puls.termine(r)) {
                        aus.add(termin(e, k.titel, 1, "Gr. " + og.nummer, new String[]{k.publishid, og.nummer, r.schluessel()}));
                    }
                }
            }
        }
        // Überschneidungen zwischen verschiedenen Fächern rot umranden
        for (Termin x : aus) {
            if (x.stil != 0) continue;
            for (Termin y : aus) {
                if (y == x || y.stil != 0 && y.stil != 3) continue;
                if (((String[]) y.bezug)[0].equals(((String[]) x.bezug)[0])) continue;
                if (x.start < y.ende && y.start < x.ende) { x.stil = 3; break; }
            }
        }
        aus.sort((x, y) -> Long.compare(x.start, y.start));
        return aus;
    }

    private static Termin termin(Puls.EinzelTermin e, String titel, int stil, String marke, String[] bezug) {
        Termin roh = new Termin(e.start, e.ende, titel, e.art, e.raum);
        Termin t = Daten.viertelAnpassen(roh);
        t.stil = stil;
        t.marke = marke;
        t.bezug = bezug;
        return t;
    }

    private void rasterTippen(Termin x) {
        String[] b = (String[]) x.bezug;
        if (x.stil == 1) {
            // Gruppe wählen
            PulsDienst.Auswahl neu = PulsDienst.auswahl(this);
            PulsDienst.Kurs kk = neu.kurs(b[0]);
            if (kk != null) kk.gruppe = b[1];
            PulsDienst.speichern(this, neu);
            Toast.makeText(this, Konfig.kurs(x.titel)[0] + ": Gruppe " + b[1] + " gewählt", Toast.LENGTH_SHORT).show();
        } else {
            fokus = b[0].equals(fokus) ? null : b[0];
        }
        allesNeu();
    }

    private void rasterHalten(Termin x) {
        if (x.stil == 1) return;
        String[] b = (String[]) x.bezug;
        boolean weg = x.stil == 2;
        new AlertDialog.Builder(this)
                .setTitle(Konfig.kurs(x.titel)[0] + " · " + x.typ)
                .setItems(new String[]{weg ? "Wieder im Stundenplan anzeigen" : "Aus dem Stundenplan ausblenden (alle Wochen)"}, (d, i) -> {
                    PulsDienst.Auswahl neu = PulsDienst.auswahl(this);
                    PulsDienst.Kurs kk = neu.kurs(b[0]);
                    if (kk == null) return;
                    Set<String> s = new HashSet<>(kk.ausgeblendet);
                    if (weg) s.remove(b[2]); else s.add(b[2]);
                    kk.ausgeblendet = new ArrayList<>(new java.util.TreeSet<>(s));
                    PulsDienst.speichern(this, neu);
                    allesNeu();
                }).show();
    }

    private static long montag(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        int wt = (c.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7; // Mo = 0
        if (wt >= 5) c.add(java.util.Calendar.DAY_OF_MONTH, 7); // Wochenende: nächste Woche (wie das Widget)
        c.add(java.util.Calendar.DAY_OF_MONTH, -wt);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        return c.getTimeInMillis();
    }

    // ───────────── Suche ─────────────

    private void katalogLaden(boolean erzwingen) {
        String sem = PulsDienst.auswahl(this).semester;
        katalogStatus.setText("Suchkatalog wird geladen …");
        hintergrund.execute(() -> {
            String f = PulsKatalog.bereitstellen(this, sem, erzwingen);
            runOnUiThread(() -> {
                katalogStatus.setText(f != null ? f : "");
                trefferZeigen();
            });
        });
    }

    private void trefferZeigen() {
        treffer.removeAllViews();
        String q = suche.getText().toString().trim();
        if (q.isEmpty()) return;
        String sem = PulsDienst.auswahl(this).semester;
        if (!PulsKatalog.vorhanden(sem)) {
            Button laden = new Button(this);
            laden.setText("Suchkatalog jetzt laden");
            laden.setOnClickListener(v -> katalogLaden(true));
            treffer.addView(laden);
            return;
        }
        List<PulsKatalog.Eintrag> l = PulsKatalog.suchen(q, 30);
        if (l.isEmpty()) { treffer.addView(klein("Nichts gefunden.")); return; }
        PulsDienst.Auswahl a = PulsDienst.auswahl(this);
        for (PulsKatalog.Eintrag e : l) {
            boolean drin = a.kurs(e.publishid) != null;
            TextView t = new TextView(this);
            t.setText((drin ? "✓ " : (e.publishid.equals(laedt) ? "… " : "＋ ")) + e.titel + (e.kontext.isEmpty() ? "" : "\n" + e.kontext));
            t.setPadding(dp(4), dp(10), dp(4), dp(10));
            t.setTextSize(15);
            if (!drin) t.setOnClickListener(v -> hinzufuegen(e.publishid));
            treffer.addView(t);
        }
    }

    private void hinzufuegen(String id) {
        laedt = id;
        trefferZeigen();
        Toast.makeText(this, "Wird aus PULS geladen …", Toast.LENGTH_SHORT).show();
        hintergrund.execute(() -> {
            String fehler = PulsDienst.hinzufuegen(this, id);
            runOnUiThread(() -> {
                laedt = null;
                if (fehler != null) Toast.makeText(this, fehler, Toast.LENGTH_LONG).show();
                allesNeu();
                trefferZeigen();
            });
        });
    }

    // ───────────── Fächer ─────────────

    private void faecherZeigen(PulsDienst.Auswahl a) {
        faecher.removeAllViews();
        if (a.kurse.isEmpty()) {
            faecher.addView(klein("Noch keine Fächer gewählt – oben suchen oder einen PULS-Link einfügen."));
            return;
        }
        for (PulsDienst.Kurs k : a.kurse) faecher.addView(kursKarte(a, k));
    }

    private View kursKarte(PulsDienst.Auswahl a, PulsDienst.Kurs k) {
        int farbe = Color.parseColor(Konfig.kurs(k.titel)[1]);
        LinearLayout karte = new LinearLayout(this);
        karte.setOrientation(LinearLayout.VERTICAL);
        karte.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable hg = new GradientDrawable();
        hg.setCornerRadius(dp(12));
        hg.setColor(Color.argb(dunkel ? 40 : 18, 128, 128, 128));
        boolean imFokus = k.publishid.equals(fokus);
        hg.setStroke(dp(imFokus ? 4 : 2), farbe);
        karte.setBackground(hg);
        karte.setOnClickListener(x -> { fokus = imFokus ? null : k.publishid; allesNeu(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        karte.setLayoutParams(lp);

        TextView titel = new TextView(this);
        titel.setText(k.titel);
        titel.setTextSize(16);
        titel.setTypeface(Typeface.DEFAULT_BOLD);
        karte.addView(titel);
        if (imFokus) karte.addView(klein("Alle Gruppen stehen grau im Plan oben."));

        Puls.Veranstaltung v = PulsDienst.cache(this).get(k.publishid);
        if (v == null) {
            karte.addView(klein("Wird geladen …"));
        } else {
            if (v.gruppen.size() <= 1) {
                karte.addView(klein("eine Gruppe"));
            } else {
                karte.addView(klein(k.gruppe != null ? "Gruppe " + k.gruppe + " von " + v.gruppen.size()
                        : "Gruppe wählen (" + v.gruppen.size() + " zur Auswahl)"));
                HorizontalScrollView hs = new HorizontalScrollView(this);
                LinearLayout chips = zeile();
                for (Puls.Gruppe g : v.gruppen) {
                    boolean gewaehlt = g.nummer.equals(k.gruppe);
                    boolean konflikt = !PulsDienst.ueberschneidungen(this, a, k, g.nummer).get(0).isEmpty();
                    StringBuilder text = new StringBuilder("Gruppe " + g.nummer);
                    List<Puls.Slot> s = Puls.slots(v.eigenes(g));
                    for (int i = 0; i < Math.min(2, s.size()); i++) text.append("\n").append(s.get(i).zeit());
                    if (konflikt) text.append("\nÜberschneidung");
                    TextView chip = new TextView(this);
                    chip.setText(text);
                    chip.setTextSize(12);
                    chip.setPadding(dp(10), dp(6), dp(10), dp(6));
                    chip.setTextColor(gewaehlt ? Color.WHITE : (konflikt ? Color.parseColor("#E24B4A") : chip.getCurrentTextColor()));
                    chip.setBackground(rahmen(farbe, gewaehlt));
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    clp.rightMargin = dp(6);
                    chip.setOnClickListener(x -> {
                        PulsDienst.Auswahl neu = PulsDienst.auswahl(this);
                        PulsDienst.Kurs kk = neu.kurs(k.publishid);
                        if (kk != null) kk.gruppe = g.nummer;
                        PulsDienst.speichern(this, neu);
                        allesNeu();
                    });
                    chips.addView(chip, clp);
                }
                hs.addView(chips);
                hs.setPadding(0, dp(6), 0, dp(6));
                karte.addView(hs);
            }
            if (!v.gruppen.isEmpty()) {
                List<String> allgemein = PulsDienst.ueberschneidungen(this, a, k, k.gruppe != null ? k.gruppe : v.gruppen.get(0).nummer).get(1);
                if (!allgemein.isEmpty()) {
                    TextView w = klein("⚠ Vorlesung überschneidet sich mit " + String.join(", ", allgemein));
                    w.setTextColor(Color.parseColor("#E24B4A"));
                    w.setAlpha(1f);
                    karte.addView(w);
                }
            }
            if (!k.ausgeblendet.isEmpty()) {
                karte.addView(klein(k.ausgeblendet.size() + " Termin" + (k.ausgeblendet.size() == 1 ? "" : "e") + " ausgeblendet"));
            }
        }

        LinearLayout aktionen = zeile();
        Button termine = new Button(this);
        termine.setText("Termine");
        termine.setEnabled(v != null);
        termine.setOnClickListener(x -> termineDialog(k.publishid));
        Button details = new Button(this);
        details.setText("Details");
        details.setEnabled(v != null);
        details.setOnClickListener(x -> detailsDialog(k.publishid));
        Button weg = new Button(this);
        weg.setText("Entfernen");
        weg.setOnClickListener(x -> new AlertDialog.Builder(this)
                .setMessage("„" + k.titel + "“ aus der Auswahl entfernen?")
                .setPositiveButton("Entfernen", (dlg, w) -> {
                    PulsDienst.Auswahl neu = PulsDienst.auswahl(this);
                    neu.kurse.removeIf(z -> z.publishid.equals(k.publishid));
                    PulsDienst.speichern(this, neu);
                    allesNeu();
                })
                .setNegativeButton("Abbrechen", null).show());
        aktionen.addView(termine);
        aktionen.addView(details);
        aktionen.addView(weg);
        karte.addView(aktionen);
        return karte;
    }

    /** Wiederkehrende Termine der gewählten Gruppe aus dem Stundenplan nehmen bzw. wieder aufnehmen */
    private void termineDialog(String publishid) {
        PulsDienst.Auswahl a = PulsDienst.auswahl(this);
        PulsDienst.Kurs k = a.kurs(publishid);
        Puls.Veranstaltung v = PulsDienst.cache(this).get(publishid);
        if (k == null || v == null || v.gruppen.isEmpty()) return;
        Puls.Gruppe g = k.gruppe != null && v.gruppe(k.gruppe) != null ? v.gruppe(k.gruppe) : v.gruppen.get(0);
        List<Puls.Slot> slots = Puls.slots(g.regeln);
        String[] namen = new String[slots.size()];
        boolean[] an = new boolean[slots.size()];
        Set<String> weg = k.ausgeblendetSet();
        for (int i = 0; i < slots.size(); i++) {
            namen[i] = slots.get(i).kurz();
            an[i] = !weg.contains(slots.get(i).regel.schluessel());
        }
        new AlertDialog.Builder(this)
                .setTitle("Im Stundenplan anzeigen")
                .setMultiChoiceItems(namen, an, (dlg, i, wert) -> an[i] = wert)
                .setPositiveButton("Fertig", (dlg, w) -> {
                    PulsDienst.Auswahl neu = PulsDienst.auswahl(this);
                    PulsDienst.Kurs kk = neu.kurs(publishid);
                    if (kk == null) return;
                    Set<String> s = new HashSet<>(kk.ausgeblendet);
                    for (int i = 0; i < slots.size(); i++) {
                        String sch = slots.get(i).regel.schluessel();
                        if (an[i]) s.remove(sch); else s.add(sch);
                    }
                    kk.ausgeblendet = new ArrayList<>(new java.util.TreeSet<>(s));
                    PulsDienst.speichern(this, neu);
                    allesNeu();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void detailsDialog(String publishid) {
        Puls.Veranstaltung v = PulsDienst.cache(this).get(publishid);
        if (v == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Art: ").append(v.art).append("\nSemester: ").append(v.semester);
        if (v.belegfrist != null) sb.append("\nBelegung: ").append(v.belegfrist);
        List<Puls.Regel> gem = v.gruppen.size() > 1 ? v.gemeinsam() : new ArrayList<>();
        if (!gem.isEmpty()) {
            sb.append("\n\nFür alle Gruppen:");
            for (Puls.Slot s : Puls.slots(gem)) sb.append("\n• ").append(s.kurz());
        }
        for (Puls.Gruppe g : v.gruppen) {
            sb.append("\n\n").append(v.gruppen.size() > 1 ? "Gruppe " + g.nummer + ":" : "Termine:");
            for (Puls.Slot s : Puls.slots(v.gruppen.size() > 1 ? v.eigenes(g) : g.regeln)) {
                sb.append("\n• ").append(s.kurz());
                if (!s.regel.lehrperson.isEmpty()) sb.append(" (").append(s.regel.lehrperson).append(")");
            }
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(v.titel)
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("In PULS öffnen", (dlg, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Puls.veranstaltungURL(publishid)))));
        if (v.moodle != null) b.setNegativeButton("Moodle", (dlg, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(v.moodle))));
        b.show();
    }

    // ───────────── Optionen ─────────────

    private void optionenZeigen(PulsDienst.Auswahl a) {
        optionen.removeAllViews();
        String jetzt = Puls.semesterCode(System.currentTimeMillis());
        LinearLayout sem = zeile();
        for (String code : new String[]{jetzt, Puls.naechstesSemester(jetzt)}) {
            Button b = new Button(this);
            b.setText((code.equals(a.semester) ? "✓ " : "") + Puls.semesterName(code));
            b.setOnClickListener(v -> {
                PulsDienst.Auswahl n = PulsDienst.auswahl(this);
                if (code.equals(n.semester)) return;
                n.semester = code;
                PulsDienst.speichern(this, n);
                allesNeu();
                katalogLaden(false);
            });
            sem.addView(b);
        }
        optionen.addView(klein("Semester"));
        optionen.addView(sem);

        CheckBox wlan = new CheckBox(this);
        wlan.setText("Täglicher Abgleich nur ohne mobile Daten (WLAN)");
        wlan.setChecked(a.nurWLAN);
        wlan.setOnCheckedChangeListener((x, an) -> {
            PulsDienst.Auswahl n = PulsDienst.auswahl(this);
            n.nurWLAN = an;
            PulsDienst.speichern(this, n);
            PulsDienst.planen(this);
        });
        optionen.addView(wlan);
        CheckBox mit = new CheckBox(this);
        mit.setText("Mitteilung bei Änderungen (Raum, Zeit, Ausfall)");
        mit.setChecked(a.mitteilungen);
        mit.setOnCheckedChangeListener((x, an) -> {
            PulsDienst.Auswahl n = PulsDienst.auswahl(this);
            n.mitteilungen = an;
            PulsDienst.speichern(this, n);
        });
        optionen.addView(mit);

        PulsDienst.Stand s = PulsDienst.stand(this);
        optionen.addView(klein(a.aktiv
                ? "Abgleich aktiv. Zuletzt: " + (s.letzterAbgleich > 0 ? Lageplan.datumKurz(s.letzterAbgleich) + " " + Lageplan.uhrzeitText(s.letzterAbgleich) : "noch nie")
                + (s.fehler != null ? "\n" + s.fehler : "")
                : "Der Abgleich startet, sobald du die Auswahl als Stundenplan übernimmst."));
        if (a.aktiv) {
            Button jetztAbgleichen = new Button(this);
            jetztAbgleichen.setText("Jetzt mit PULS abgleichen");
            jetztAbgleichen.setOnClickListener(v -> {
                Toast.makeText(this, "Wird abgeglichen …", Toast.LENGTH_SHORT).show();
                hintergrund.execute(() -> {
                    boolean geaendert = PulsDienst.abgleichen(this, true);
                    runOnUiThread(() -> {
                        Toast.makeText(this, geaendert ? "Stundenplan aktualisiert" : "Keine Änderungen", Toast.LENGTH_SHORT).show();
                        allesNeu();
                    });
                });
            });
            optionen.addView(jetztAbgleichen);
        }
        if (!s.aenderungen.isEmpty()) {
            optionen.addView(klein("Letzte Änderungen:\n• " + String.join("\n• ", s.aenderungen.subList(0, Math.min(10, s.aenderungen.size())))));
        }
        Button katalog = new Button(this);
        katalog.setText("Suchkatalog neu laden");
        katalog.setOnClickListener(v -> katalogLaden(true));
        optionen.addView(katalog);
    }

    // ───────────── Übernehmen ─────────────

    private void uebernehmen() {
        PulsDienst.Auswahl a = PulsDienst.auswahl(this);
        int ohneGruppe = 0;
        for (PulsDienst.Kurs k : a.kurse) {
            Puls.Veranstaltung v = PulsDienst.cache(this).get(k.publishid);
            if (k.gruppe == null && v != null && v.gruppen.size() > 1) ohneGruppe++;
        }
        final int fehlend = ohneGruppe;
        hintergrund.execute(() -> {
            int n = PulsDienst.uebernehmen(this);
            runOnUiThread(() -> {
                new AlertDialog.Builder(this)
                        .setMessage(n + " Termine als Stundenplan übernommen."
                                + (fehlend > 0 ? " Bei " + fehlend + " Fach/Fächern fehlt noch die Gruppe – dort wurde Gruppe 1 genommen." : "")
                                + "\n\nDie App gleicht ab jetzt einmal täglich mit PULS ab.")
                        .setPositiveButton("OK", null)
                        .show();
                allesNeu();
            });
        });
    }
}
