package de.stundenplan.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Empfängt den Minuten-Wecker sowie Zeit-/Zeitzonenänderungen und aktualisiert alle Widgets. */
public class Aktualisierer extends BroadcastReceiver {
    public static final String AKTION = "de.stundenplan.widget.AKTUALISIEREN";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        StundenplanWidget.alleAktualisieren(ctx);
    }
}
