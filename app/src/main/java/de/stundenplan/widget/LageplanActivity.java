package de.stundenplan.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

/**
 * Zeigt den Lageplan Golm mit den Räumen eines Termins oder eines ganzen Tages.
 * Pro Termin: Route (Google Maps, zu Fuß), „Fällt aus“, „Raum ändern“.
 */
public class LageplanActivity extends Activity {

    public static final String EXTRA_TERMIN = "termin"; // Startzeit eines Termins (ms)
    public static final String EXTRA_TAG = "tag";       // Tagesbeginn (ms)

    private WebView web;
    private Bitmap plan;
    private long termin, tag;
    private List<Termin> termine = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        termin = getIntent().getLongExtra(EXTRA_TERMIN, 0);
        tag = getIntent().getLongExtra(EXTRA_TAG, 0);

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        plan = BitmapFactory.decodeResource(getResources(), R.drawable.lageplan_golm, o);

        web = new WebView(this);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if ("aktion".equals(url.getScheme())) {
                    aktion(url.getHost(), url.getLastPathSegment());
                } else {
                    // Route (Google Maps) und „Im Planer öffnen“ in der passenden App öffnen
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                }
                return true;
            }
        });
        setContentView(web);
        anzeigen();
    }

    /** Termine neu laden (mit Ausfällen und geänderten Räumen) und Seite neu aufbauen. */
    private void anzeigen() {
        List<Termin> alle = Daten.laden(this, true);
        termine = new ArrayList<>();
        String titel = "Lageplan Golm";
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
        String html = Lageplan.html(termine, titel, plan, StundenplanWidget.planerLink(this));
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    private void aktion(String was, String nummer) {
        int i;
        try {
            i = Integer.parseInt(nummer);
        } catch (Exception e) {
            return;
        }
        if (i < 0 || i >= termine.size()) return;
        Termin t = termine.get(i);
        if ("ausfall".equals(was)) {
            Aenderungen.setzen(this, t, !t.ausfall, Aenderungen.eigenerRaum(t));
            anzeigen();
        } else if ("zuruecksetzen".equals(was)) {
            Aenderungen.setzen(this, t, false, null);
            anzeigen();
        } else if ("raum".equals(was)) {
            EditText feld = new EditText(this);
            feld.setSingleLine(true);
            feld.setText(t.raum);
            feld.setHint("z. B. 2.70.0.10");
            new AlertDialog.Builder(this)
                    .setTitle("Raum ändern")
                    .setMessage("Gilt nur für diesen einen Termin. Widgets und Erinnerungen übernehmen den neuen Raum.")
                    .setView(feld)
                    .setPositiveButton("Speichern", (d, w) -> {
                        String neu = feld.getText().toString().trim();
                        String original = t.alterRaum != null ? t.alterRaum : t.raum;
                        Aenderungen.setzen(this, t, t.ausfall, neu.equals(original) ? null : neu);
                        anzeigen();
                    })
                    .setNegativeButton("Abbrechen", null)
                    .show();
        }
    }

    /** Intent für einen Termin bzw. einen Tag. */
    static Intent fuerTermin(android.content.Context ctx, Termin t) {
        return new Intent(ctx, LageplanActivity.class).putExtra(EXTRA_TERMIN, t.start)
                .setData(Uri.parse("stundenplan://lageplan/termin/" + t.start))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    static Intent fuerTag(android.content.Context ctx, long tag) {
        return new Intent(ctx, LageplanActivity.class).putExtra(EXTRA_TAG, tag)
                .setData(Uri.parse("stundenplan://lageplan/tag/" + tag))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
}
