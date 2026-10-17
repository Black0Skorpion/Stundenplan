package de.stundenplan.widget;

import android.view.View;
import android.widget.LinearLayout;

/** Reiter „Lageplan“: Räume des heutigen bzw. nächsten Tages mit Veranstaltungen. */
final class LageplanSeite implements MainActivity.Seite {

    private final LinearLayout wurzel;
    private final LageplanAnsicht plan;

    LageplanSeite(MainActivity act) {
        Ui ui = new Ui(act);
        wurzel = new LinearLayout(act);
        wurzel.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView t = ui.titel("Lageplan");
        t.setPadding(ui.dp(16), ui.dp(20), ui.dp(16), ui.dp(4));
        wurzel.addView(t);
        plan = new LageplanAnsicht(act);
        wurzel.addView(plan, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
    }

    @Override public View ansicht() { return wurzel; }

    @Override public void zeigen() { plan.naechsterTag(); }
}
