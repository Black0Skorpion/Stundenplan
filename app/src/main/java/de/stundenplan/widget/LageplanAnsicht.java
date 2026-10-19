package de.stundenplan.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lageplan (passender Campus) mit den Räumen eines Termins oder eines ganzen Tages – als Baustein für
 * den Reiter „Lageplan“ und die Lageplan-Seite. Pro Termin: Route, „Fällt aus“, „Raum ändern“;
 * bei Campuswechsel Bus- und Bahnverbindungen.
 */
class LageplanAnsicht extends FrameLayout {

    private WebView web;
    private final Map<String, Bitmap> bilder = new HashMap<>();
    private long termin, tag;
    private List<Termin> termine = new ArrayList<>();
    private String titel = "Lageplan";
    /** null = passend zu den Terminen, sonst fester Campus */
    private String gewaehlterPlan;
    /** Verbindungen je Termin-Index (Campuswechsel davor) */
    private final Map<Integer, Lageplan.VerbindungsStand> verbindungen = new HashMap<>();
    private final Map<String, Lageplan.VerbindungsStand> verbindungsCache = new HashMap<>();

    private final Activity act;
    /** null = eigener Plan, sonst Profil (nur ansehen, nicht ändern) */
    private String profil;

    LageplanAnsicht(Activity act) {
        super(act);
        this.act = act;
        web = new WebView(act);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if ("aktion".equals(url.getScheme())) {
                    if ("plan".equals(url.getHost())) {
                        String c = url.getLastPathSegment();
                        gewaehlterPlan = Campus.bekannt(c) ? c : null;
                        zeichnen();
                    } else {
                        aktion(url.getHost(), url.getLastPathSegment());
                    }
                } else {
                    // Route (Google Maps) und „Im Planer öffnen“ in der passenden App öffnen
                    try { act.startActivity(new Intent(Intent.ACTION_VIEW, url)); } catch (Exception ignored) {}
                }
                return true;
            }
        });
        addView(web, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Termin (Startzeit) oder Tag (Tagesbeginn) zeigen; profil = null für den eigenen Plan */
    void zeigen(long termin, long tag, String profil) {
        this.termin = termin;
        this.tag = tag;
        this.profil = profil;
        gewaehlterPlan = null;
        anzeigen();
    }

    /** Heute bzw. nächster Tag mit Veranstaltungen (für den Reiter „Lageplan“) */
    void naechsterTag() {
        long jetzt = System.currentTimeMillis();
        long t = MensaDaten.tagesBeginn(jetzt);
        for (Termin x : Daten.laden(act)) {
            if (x.ende > jetzt) { t = MensaDaten.tagesBeginn(x.start); break; }
        }
        zeigen(0, t, null);
    }

    void neu() { anzeigen(); }

    private Bitmap bild(String campus) {
        Bitmap b = bilder.get(campus);
        if (b == null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            b = BitmapFactory.decodeResource(act.getResources(), Lageplan.bild(campus), o);
            bilder.put(campus, b);
        }
        return b;
    }

    /** Termine neu laden (mit Ausfällen und geänderten Räumen) und Seite neu aufbauen. */
    private void anzeigen() {
        Teilen.Profil p = Teilen.profil(act, profil);
        List<Termin> alle = p != null ? p.angezeigt() : Daten.laden(act, true);
        termine = new ArrayList<>();
        titel = "Lageplan";
        if (termin > 0) {
            for (Termin t : alle) if (t.start == termin) termine.add(t);
            if (!termine.isEmpty()) titel = Lageplan.datumKurz(termine.get(0).start);
        } else if (tag > 0) {
            long ende = tag + 36L * 3600 * 1000; // bis sicher in den nächsten Tag (Zeitumstellung)
            for (Termin t : alle) {
                if (t.start >= tag && t.start < ende && Lageplan.datumKurz(t.start).equals(Lageplan.datumKurz(tag))) termine.add(t);
            }
            titel = Lageplan.datumKurz(tag);
        }
        verbindungen.clear();
        zeichnen();
        verbindungenSuchen();
    }

    private void zeichnen() {
        Map<String, Bitmap> plaene = new LinkedHashMap<>();
        List<String> liste = gewaehlterPlan != null ? java.util.Collections.singletonList(gewaehlterPlan)
                : Lageplan.campusListe(termine, Campus.aktuell(act));
        for (String c : liste) plaene.put(c, bild(c));
        String html = Lageplan.html(termine, titel, plaene, StundenplanWidget.planerLink(act), verbindungen);
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    /** Für jeden Campuswechsel im Hintergrund Verbindungen suchen, danach neu zeichnen. */
    private void verbindungenSuchen() {
        final List<Termin> liste = new ArrayList<>(termine);
        for (int i = 1; i < liste.size(); i++) {
            Termin a = liste.get(i - 1), b = liste.get(i);
            if (a.ausfall || b.ausfall || !Orte.campusWechsel(a.raum, b.raum)) continue;
            final int index = i;
            final String schluessel = a.ende + a.raum + "|" + b.start + b.raum;
            Lageplan.VerbindungsStand alt = verbindungsCache.get(schluessel);
            if (alt != null) { verbindungen.put(index, alt); continue; }
            new Thread(() -> {
                List<Verbindungen.Verbindung> v = Verbindungen.suchen(a, b);
                Lageplan.VerbindungsStand stand = new Lageplan.VerbindungsStand();
                stand.fehler = v == null;
                stand.liste = v != null ? v : new ArrayList<>();
                act.runOnUiThread(() -> {
                    if (!stand.fehler) verbindungsCache.put(schluessel, stand);
                    if (index < termine.size() && termine.get(index).start == b.start) {
                        verbindungen.put(index, stand);
                        zeichnen();
                    }
                });
            }).start();
        }
        if (!verbindungen.isEmpty()) zeichnen();
    }

    private void aktion(String was, String nummer) {
        int i;
        try {
            i = Integer.parseInt(nummer);
        } catch (Exception e) {
            return;
        }
        if (i < 0 || i >= termine.size()) return;
        if (profil != null) {
            android.widget.Toast.makeText(act, "Ändern geht nur beim eigenen Plan.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        Termin t = termine.get(i);
        if ("ausfall".equals(was)) {
            Aenderungen.setzen(act, t, !t.ausfall, Aenderungen.eigenerRaum(t));
            anzeigen();
        } else if ("zuruecksetzen".equals(was)) {
            Aenderungen.zuruecksetzen(act, t);
            anzeigen();
        } else if ("zeit".equals(was)) {
            zeitDialog(t);
        } else if ("raum".equals(was)) {
            EditText feld = new EditText(act);
            feld.setSingleLine(true);
            feld.setText(t.raum);
            feld.setHint("z. B. 2.70.0.10");
            new AlertDialog.Builder(act)
                    .setTitle("Raum ändern")
                    .setMessage("Gilt nur für diesen einen Termin. Widgets und Erinnerungen übernehmen den neuen Raum.")
                    .setView(feld)
                    .setPositiveButton("Speichern", (d, w) -> {
                        String neu = feld.getText().toString().trim();
                        String original = t.alterRaum != null ? t.alterRaum : t.raum;
                        Aenderungen.setzen(act, t, t.ausfall, neu.equals(original) ? null : neu);
                        anzeigen();
                    })
                    .setNegativeButton("Abbrechen", null)
                    .show();
        }
    }

    // ───── Zeit ändern (nur dieser Termin oder ganze Reihe) ─────

    private static String uhr(int m) { return String.format(java.util.Locale.GERMANY, "%d:%02d", m / 60, m % 60); }

    private static int minuten(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
    }

    private void zeitDialog(Termin t) {
        final long oStart = t.originalStart != 0 ? t.originalStart : t.start;
        final long oEnde = t.originalEnde != 0 ? t.originalEnde : t.ende;
        final int[] zeit = {minuten(t.start), minuten(t.ende)};
        final int oVon = minuten(oStart), oBis = minuten(oEnde);
        Aenderungen.Eintrag eintrag = Aenderungen.eintrag(act, t);
        boolean einzeln = eintrag.von >= 0;
        int anzahl = 0;
        for (Termin x : Daten.laden(act, true)) if (x.reihe().equals(t.reihe())) anzahl++;
        String[] tage = {"", "Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"};
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(oStart);
        String reiheText = "jeden " + tage[c.get(java.util.Calendar.DAY_OF_WEEK)] + " " + uhr(oVon);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout box = new android.widget.LinearLayout(act);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        android.widget.TextView info = new android.widget.TextView(act);
        info.setText(Lageplan.datumKurz(t.start) + " · ursprünglich " + uhr(oVon) + "–" + uhr(oBis));
        box.addView(info);

        android.widget.RadioGroup umfang = new android.widget.RadioGroup(act);
        android.widget.RadioButton nurDieser = new android.widget.RadioButton(act);
        nurDieser.setId(android.view.View.generateViewId());
        nurDieser.setText("Nur diesen Termin");
        android.widget.RadioButton alle = new android.widget.RadioButton(act);
        alle.setId(android.view.View.generateViewId());
        alle.setText("Alle " + anzahl + " (" + reiheText + ")");
        umfang.addView(nurDieser);
        umfang.addView(alle);
        umfang.check(einzeln ? nurDieser.getId() : alle.getId());
        box.addView(umfang);

        android.widget.Button beginn = new android.widget.Button(act);
        android.widget.Button ende = new android.widget.Button(act);
        Runnable beschriften = () -> {
            beginn.setText("Beginn " + uhr(zeit[0]));
            ende.setText("Ende " + uhr(zeit[1]));
        };
        beschriften.run();
        beginn.setOnClickListener(v -> new android.app.TimePickerDialog(act, (tp, h, m) -> {
            zeit[0] = h * 60 + m;
            beschriften.run();
        }, zeit[0] / 60, zeit[0] % 60, true).show());
        ende.setOnClickListener(v -> new android.app.TimePickerDialog(act, (tp, h, m) -> {
            zeit[1] = h * 60 + m;
            beschriften.run();
        }, zeit[1] / 60, zeit[1] % 60, true).show());
        android.widget.LinearLayout zeile = new android.widget.LinearLayout(act);
        zeile.addView(beginn, new android.widget.LinearLayout.LayoutParams(0, -2, 1));
        zeile.addView(ende, new android.widget.LinearLayout.LayoutParams(0, -2, 1));
        box.addView(zeile);

        android.widget.LinearLayout schnell = new android.widget.LinearLayout(act);
        if (oVon % 60 == 15) {
            android.widget.Button st = new android.widget.Button(act);
            st.setText("Volle Stunde (s.t.)");
            st.setAllCaps(false);
            st.setOnClickListener(v -> { zeit[0] = oVon - 15; zeit[1] = oBis + 15; beschriften.run(); });
            schnell.addView(st, new android.widget.LinearLayout.LayoutParams(0, -2, 1));
        }
        android.widget.Button wieBisher = new android.widget.Button(act);
        wieBisher.setText("Wie bisher (c.t.)");
        wieBisher.setAllCaps(false);
        wieBisher.setOnClickListener(v -> { zeit[0] = oVon; zeit[1] = oBis; beschriften.run(); });
        schnell.addView(wieBisher, new android.widget.LinearLayout.LayoutParams(0, -2, 1));
        box.addView(schnell);

        android.widget.CheckBox weckerFest = new android.widget.CheckBox(act);
        weckerFest.setText("Wecker bleibt bei der ursprünglichen Zeit");
        weckerFest.setChecked(einzeln ? eintrag.weckerFest : Aenderungen.reiheWeckerFest(act, t));
        box.addView(weckerFest);
        android.widget.TextView hilfe = new android.widget.TextView(act);
        hilfe.setTextSize(12);
        hilfe.setText("Wird die Veranstaltung nach hinten verschoben, klingelt der Wecker trotzdem wie bisher – Züge fahren ja nicht später. "
                + "Bei einer früheren Zeit richtet er sich immer nach der neuen. Erinnerungen und Widgets nehmen die neue Zeit.");
        box.addView(hilfe);

        android.widget.ScrollView rollen = new android.widget.ScrollView(act);
        rollen.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(act)
                .setTitle("Zeit ändern · " + t.titel)
                .setView(rollen)
                .setPositiveButton("Sichern", (d, w) -> {
                    if (zeit[1] <= zeit[0]) {
                        android.widget.Toast.makeText(act, "Das Ende muss nach dem Beginn liegen.", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean wieOriginal = zeit[0] == oVon && zeit[1] == oBis;
                    boolean fest = weckerFest.isChecked();
                    if (umfang.getCheckedRadioButtonId() == nurDieser.getId()) {
                        boolean festhalten = !wieOriginal || Aenderungen.hatReihe(act, t);
                        Aenderungen.zeitEinzeln(act, t, festhalten ? zeit[0] : -1, zeit[1], fest);
                    } else {
                        Aenderungen.zeitReihe(act, t, wieOriginal ? -1 : zeit[0], zeit[1], fest);
                    }
                    anzeigen();
                })
                .setNegativeButton("Abbrechen", null);
        if (t.zeitGeaendert()) {
            b.setNeutralButton("Eigene Zeit entfernen", (d, w) -> {
                Aenderungen.zeitReihe(act, t, -1, 0, true);
                anzeigen();
            });
        }
        b.show();
    }
}
