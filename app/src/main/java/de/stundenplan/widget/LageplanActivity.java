package de.stundenplan.widget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Eigene Seite für den Lageplan eines Termins bzw. Tages (aus Widgets, Mitteilungen und der App). */
public class LageplanActivity extends Activity {

    public static final String EXTRA_TERMIN = "termin"; // Startzeit eines Termins (ms)
    public static final String EXTRA_TAG = "tag";       // Tagesbeginn (ms)
    public static final String EXTRA_PROFIL = "profil"; // Profil-ID (Plan von jemand anderem)

    private LageplanAnsicht ansicht;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Lageplan");
        ansicht = new LageplanAnsicht(this);
        setContentView(ansicht);
        ansicht.zeigen(getIntent().getLongExtra(EXTRA_TERMIN, 0), getIntent().getLongExtra(EXTRA_TAG, 0),
                getIntent().getStringExtra(EXTRA_PROFIL));
    }

    /** Intent für einen Termin bzw. einen Tag (von außen: Widget, Mitteilung). */
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

    /** Aus der App heraus (Zurück führt wieder in die App) */
    static Intent intern(android.content.Context ctx, long termin, long tag, String profil) {
        return new Intent(ctx, LageplanActivity.class).putExtra(EXTRA_TERMIN, termin).putExtra(EXTRA_TAG, tag)
                .putExtra(EXTRA_PROFIL, profil);
    }
}
