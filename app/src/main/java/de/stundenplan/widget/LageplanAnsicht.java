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
            Aenderungen.setzen(act, t, false, null);
            anzeigen();
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

}
