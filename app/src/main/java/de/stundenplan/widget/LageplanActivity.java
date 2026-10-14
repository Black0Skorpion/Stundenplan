package de.stundenplan.widget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.List;

/** Zeigt den Lageplan Golm mit den Räumen eines Termins oder eines ganzen Tages. */
public class LageplanActivity extends Activity {

    public static final String EXTRA_TERMIN = "termin"; // Startzeit eines Termins (ms)
    public static final String EXTRA_TAG = "tag";       // Tagesbeginn (ms)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long termin = getIntent().getLongExtra(EXTRA_TERMIN, 0);
        long tag = getIntent().getLongExtra(EXTRA_TAG, 0);

        List<Termin> alle = Daten.laden(this);
        List<Termin> termine = new ArrayList<>();
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

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap plan = BitmapFactory.decodeResource(getResources(), R.drawable.lageplan_golm, o);
        String html = Lageplan.html(termine, titel, plan, StundenplanWidget.planerLink(this));

        WebView web = new WebView(this);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Links (z. B. „Im Planer öffnen“) im Browser öffnen
                startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                return true;
            }
        });
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        setContentView(web);
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
