package de.stundenplan.widget;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Reiter „Plan“: Woche, nächste Veranstaltung, Tagesliste mit Wegen – eigener Plan oder Profil. */
final class PlanSeite implements MainActivity.Seite {

    private static final String[] WT = {"Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"};

    private final MainActivity act;
    private final Ui ui;
    private final ScrollView scroll;
    private final LinearLayout inhalt;
    private int versatz = 0;
    private final Runnable takt = new Runnable() {
        @Override public void run() {
            aufbauen();
            act.handler.postDelayed(this, 30_000);
        }
    };

    PlanSeite(MainActivity act) {
        this.act = act;
        ui = new Ui(act);
        scroll = new ScrollView(act);
        inhalt = new LinearLayout(act);
        inhalt.setOrientation(LinearLayout.VERTICAL);
        inhalt.setPadding(0, 0, 0, ui.dp(24));
        scroll.addView(inhalt);
    }

    @Override public View ansicht() { return scroll; }

    @Override
    public void zeigen() {
        act.handler.removeCallbacks(takt);
        takt.run();
    }

    @Override public void verstecken() { act.handler.removeCallbacks(takt); }

    private String profilId() { return MainActivity.anzeige(act); }

    private void aufbauen() {
        inhalt.removeAllViews();
        Teilen.Profil profil = Teilen.profil(act, profilId());
        List<Termin> alle = profil != null ? profil.angezeigt() : Daten.laden(act, true);
        List<Termin> aktive = new ArrayList<>();
        for (Termin t : alle) if (!t.ausfall) aktive.add(t);
        long jetzt = System.currentTimeMillis();

        // Kopf: Profil-Menü, Titel, Teilen
        LinearLayout kopf = new LinearLayout(act);
        kopf.setGravity(Gravity.CENTER_VERTICAL);
        kopf.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), 0);
        kopf.addView(ui.kapsel(profil != null ? profil.name + " ▾" : "Profile ▾", ui.akzent, false, v -> profilMenue()));
        View luecke = new View(act);
        kopf.addView(luecke, new LinearLayout.LayoutParams(0, 1, 1));
        if (profil != null) {
            kopf.addView(ui.kapsel("Mein Plan", ui.akzent, false, v -> { MainActivity.setzeAnzeige(act, null); versatz = 0; aufbauen(); }));
        } else {
            kopf.addView(ui.kapsel("＋", ui.akzent, false, v -> plusMenue()));
            View l2 = new View(act);
            kopf.addView(l2, new LinearLayout.LayoutParams(ui.dp(6), 1));
            kopf.addView(ui.kapsel("Teilen", ui.akzent, false, v -> act.startActivity(new Intent(act, TeilenActivity.class))));
        }
        inhalt.addView(kopf);
        inhalt.addView(ui.titel(profil != null ? "Plan von " + profil.name : "Stundenplan"));

        if (alle.isEmpty() && profil == null) inhalt.addView(leer());

        // Woche
        long montag = WochenView.montag(jetzt, versatz);
        long freitag = montag + 4 * 86400_000L;
        LinearLayout woche = new LinearLayout(act);
        woche.setGravity(Gravity.CENTER_VERTICAL);
        woche.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        TextView zurueck = ui.fett("‹", 30, ui.akzent);
        zurueck.setPadding(ui.dp(14), 0, ui.dp(14), 0);
        zurueck.setOnClickListener(v -> { versatz--; aufbauen(); });
        TextView vor = ui.fett("›", 30, ui.akzent);
        vor.setPadding(ui.dp(14), 0, ui.dp(14), 0);
        vor.setOnClickListener(v -> { versatz++; aufbauen(); });
        LinearLayout mitte = new LinearLayout(act);
        mitte.setOrientation(LinearLayout.VERTICAL);
        mitte.setGravity(Gravity.CENTER);
        mitte.addView(ui.fett("KW " + kalenderwoche(montag), 17, ui.vorder));
        mitte.addView(ui.text(tagMonat(montag) + " – " + tagMonat(freitag), 13, ui.gedimmt));
        mitte.setOnClickListener(v -> { versatz = 0; aufbauen(); });
        woche.addView(zurueck);
        woche.addView(mitte, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        woche.addView(vor);
        inhalt.addView(woche);

        boolean wocheLeer = true;
        for (Termin t : alle) if (t.start >= montag && t.start < montag + 5 * 86400_000L + 3600_000L) { wocheLeer = false; break; }
        if (wocheLeer) {
            TextView l = ui.text("Keine Termine in dieser Woche", 15, ui.gedimmt);
            l.setGravity(Gravity.CENTER);
            l.setPadding(0, ui.dp(70), 0, ui.dp(70));
            inhalt.addView(l);
        } else {
            WochenView wv = new WochenView(act);
            FrameLayout rahmen = new FrameLayout(act);
            rahmen.setBackground(ui.rund(ui.karte, 14));
            rahmen.setPadding(ui.dp(6), ui.dp(8), ui.dp(6), ui.dp(6));
            rahmen.addView(wv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ui.dp(480)));
            inhalt.addView(rahmen, ui.mitRand(12, 6, 12, 6));
            wv.setze(montag, alle, new WochenView.Aktion() {
                @Override public void termin(Termin t) { lageplan(t.start, 0); }
                @Override public void tag(long tag) { lageplan(0, tag); }
                @Override public void blaettern(int richtung) { versatz += richtung; aufbauen(); }
            });
        }

        // Tagesteil
        long tag = tagesAuswahl(aktive, jetzt);
        if (tag < 0) {
            TextView l = ui.text("Keine weiteren Termine", 15, ui.gedimmt);
            l.setPadding(ui.dp(20), ui.dp(14), ui.dp(20), 0);
            inhalt.addView(l);
            return;
        }
        boolean istHeute = gleicherTag(tag, jetzt);
        LinearLayout tk = new LinearLayout(act);
        tk.setGravity(Gravity.BOTTOM);
        tk.setPadding(ui.dp(20), ui.dp(18), ui.dp(16), ui.dp(6));
        tk.addView(ui.fett(label(tag, jetzt), 21, istHeute ? ui.akzent : ui.vorder));
        TextView datum = ui.text("  " + Lageplan.datumKurz(tag), 15, ui.gedimmt);
        tk.addView(datum, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tk.addView(ui.kapsel("Lageplan", ui.akzent, false, v -> lageplan(0, tag)));
        inhalt.addView(tk);

        List<Termin> amTag = new ArrayList<>();
        for (Termin t : alle) if (gleicherTag(t.start, tag)) amTag.add(t);
        Termin fokus = null;
        for (Termin t : amTag) if (!t.ausfall && t.ende > jetzt) { fokus = t; break; }
        if (fokus != null) inhalt.addView(karte(fokus, jetzt), ui.mitRand(16, 4, 16, 10));

        LinearLayout liste = ui.gruppe();
        Termin vorher = null;
        for (Termin t : amTag) {
            if (vorher != null && !vorher.ausfall && !t.ausfall) {
                String weg = wegText(vorher, t);
                if (weg != null) {
                    TextView w = ui.text("🚶 " + weg, 12, ui.gedimmt);
                    w.setPadding(ui.dp(30), ui.dp(2), ui.dp(16), ui.dp(2));
                    liste.addView(w);
                }
            }
            liste.addView(zeile(t, jetzt));
            vorher = t;
        }
        inhalt.addView(liste);
    }

    // ───────────── Bausteine ─────────────

    private View leer() {
        LinearLayout k = new LinearLayout(act);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setBackground(ui.rund(ui.karte, 14));
        k.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        k.addView(ui.fett("Noch kein Stundenplan", 17, ui.vorder));
        TextView t = ui.text("Wähle deine Veranstaltungen direkt aus PULS – oder lass dir den Plan von jemandem per QR-Code, Link oder Datei schicken.", 14, ui.gedimmt);
        t.setPadding(0, ui.dp(4), 0, ui.dp(10));
        k.addView(t);
        LinearLayout knoepfe = new LinearLayout(act);
        knoepfe.addView(ui.kapsel("Aus PULS wählen", ui.akzent, true, v -> act.startActivity(new Intent(act, PulsActivity.class))));
        View l = new View(act);
        knoepfe.addView(l, new LinearLayout.LayoutParams(ui.dp(8), 1));
        knoepfe.addView(ui.kapsel("QR-Code scannen", ui.akzent, false, v -> EinstellungenSeite.scannen(act)));
        k.addView(knoepfe);
        LinearLayout.LayoutParams lp = ui.mitRand(16, 4, 16, 12);
        k.setLayoutParams(lp);
        return k;
    }

    /** Große Karte der laufenden bzw. nächsten Veranstaltung */
    private View karte(Termin t, long jetzt) {
        String[] kurs = Konfig.kurs(t.titel);
        int farbe = Color.parseColor(kurs[1]);
        LinearLayout k = new LinearLayout(act);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        k.setBackground(ui.tippbar(farbe, 16));
        k.setOnClickListener(v -> lageplan(t.start, 0));
        boolean laeuft = jetzt >= t.start;
        long min = Math.max(0, ((laeuft ? t.ende : t.start) - jetzt + 59_999) / 60_000);
        String status = laeuft ? "JETZT · noch " + dauer(min) : (gleicherTag(t.start, jetzt) ? "in " + dauer(min) : Lageplan.uhrzeitText(t.start));
        TextView st = ui.fett(status, 12.5f, Leinwand.alpha(Color.WHITE, 0.9f));
        k.addView(st);
        TextView ti = ui.fett(kurs[0], 26, Color.WHITE);
        k.addView(ti);
        k.addView(ui.text(Konfig.typ(t.typ)[0] + " · " + Lageplan.uhrzeitText(t.start) + "–" + Lageplan.uhrzeitText(t.ende), 15, Leinwand.alpha(Color.WHITE, 0.9f)));
        TextView raum = ui.fett(t.raum.isEmpty() ? "Raum unbekannt" : t.raum, 18, Color.WHITE);
        raum.setPadding(0, ui.dp(4), 0, 0);
        k.addView(raum);
        String ort = Lageplan.einrichtungText(t.raum);
        if (ort != null) k.addView(ui.text(ort, 13, Leinwand.alpha(Color.WHITE, 0.85f)));
        if (t.alterRaum != null) k.addView(ui.text("Geändert, ursprünglich " + t.alterRaum, 12.5f, Leinwand.alpha(Color.WHITE, 0.85f)));
        if (!gleicherTag(t.start, jetzt) && profilId() == null && Wecker.an(act)) {
            long[] w = Wecker.naechste(act);
            if (w != null && w[1] == t.start) {
                TextView wt = ui.fett("⏰ Wecker " + Lageplan.datumKurz(w[0]) + " " + Lageplan.uhrzeitText(w[0]), 13, Color.WHITE);
                wt.setPadding(0, ui.dp(6), 0, 0);
                k.addView(wt);
            }
        }
        if (laeuft) {
            ProgressBar p = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
            p.setMax(1000);
            p.setProgress((int) (1000 * (jetzt - t.start) / Math.max(1, t.ende - t.start)));
            p.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            p.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Leinwand.alpha(Color.WHITE, 0.35f)));
            k.addView(p, ui.mitRand(0, 8, 0, 0));
        }
        return k;
    }

    private View zeile(Termin t, long jetzt) {
        String[] kurs = Konfig.kurs(t.titel);
        LinearLayout z = ui.zeile(v -> lageplan(t.start, 0));
        z.setMinimumHeight(ui.dp(52));
        View balken = new View(act);
        balken.setBackground(ui.rund(Color.parseColor(kurs[1]), 2));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ui.dp(4), ui.dp(34));
        bl.rightMargin = ui.dp(10);
        z.addView(balken, bl);
        LinearLayout mitte = new LinearLayout(act);
        mitte.setOrientation(LinearLayout.VERTICAL);
        TextView a = ui.fett(kurs[0] + "  ", 16, t.ende < jetzt ? ui.gedimmt : ui.vorder);
        TextView b = ui.text(Konfig.typ(t.typ)[0] + " · " + Lageplan.uhrzeitText(t.start) + "–" + Lageplan.uhrzeitText(t.ende), 13, ui.gedimmt);
        if (t.ausfall) {
            a.setPaintFlags(a.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            b.setText("Fällt aus · " + b.getText());
        }
        mitte.addView(a);
        mitte.addView(b);
        z.addView(mitte, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView r = ui.text(t.raum, 14, t.alterRaum != null ? ui.rot : ui.gedimmt);
        r.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        z.addView(r);
        return z;
    }

    private void lageplan(long termin, long tag) {
        act.startActivity(LageplanActivity.intern(act, termin, tag, profilId()));
    }

    private void plusMenue() {
        int n = EigeneTermine.alle(act).size();
        new AlertDialog.Builder(act).setItems(new String[]{"Neuer Termin", "Eigene Termine (" + n + ")"}, (d, i) -> {
            Intent in = new Intent(act, EigenerTerminActivity.class);
            if (i == 1) in.putExtra(EigenerTerminActivity.EXTRA_LISTE, true);
            act.startActivity(in);
        }).show();
    }

    private void profilMenue() {
        List<Teilen.Profil> alle = Teilen.profile(act);
        String eigener = Teilen.name(act);
        List<String> punkte = new ArrayList<>();
        String aktuell = profilId();
        punkte.add((aktuell == null ? "✓ " : "") + (eigener.isEmpty() ? "Mein Plan" : eigener + " (ich)"));
        for (Teilen.Profil p : alle) punkte.add((p.id.equals(aktuell) ? "✓ " : "") + p.name);
        punkte.add("Profile verwalten …");
        new AlertDialog.Builder(act).setTitle("Welcher Plan?").setItems(punkte.toArray(new String[0]), (d, i) -> {
            if (i == 0) MainActivity.setzeAnzeige(act, null);
            else if (i <= alle.size()) MainActivity.setzeAnzeige(act, alle.get(i - 1).id);
            else { act.wechseln(MainActivity.EINSTELLUNGEN); return; }
            versatz = 0;
            aufbauen();
        }).show();
    }

    // ───────────── Hilfen ─────────────

    /** Heute, wenn noch etwas kommt, sonst der nächste Tag mit Terminen; -1 = nichts mehr */
    static long tagesAuswahl(List<Termin> aktive, long jetzt) {
        for (Termin t : aktive) if (t.ende > jetzt) return MensaDaten.tagesBeginn(t.start);
        return -1;
    }

    private static boolean gleicherTag(long a, long b) {
        return MensaDaten.tagesBeginn(a) == MensaDaten.tagesBeginn(b);
    }

    private static String label(long tag, long jetzt) {
        long heute = MensaDaten.tagesBeginn(jetzt);
        if (tag == heute) return "Heute";
        if (tag == MensaDaten.plusTage(heute, 1)) return "Morgen";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(tag);
        return WT[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    private static String tagMonat(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.DAY_OF_MONTH) + "." + (c.get(Calendar.MONTH) + 1) + ".";
    }

    private static int kalenderwoche(long t) {
        Calendar k = Calendar.getInstance(Locale.GERMANY);
        k.setFirstDayOfWeek(Calendar.MONDAY);
        k.setMinimalDaysInFirstWeek(4);
        k.setTimeInMillis(t);
        return k.get(Calendar.WEEK_OF_YEAR);
    }

    static String dauer(long min) {
        if (min < 60) return min + " Min";
        return (min / 60) + " Std" + (min % 60 > 0 ? " " + (min % 60) + " Min" : "");
    }

    /** Weg zwischen zwei Terminen: Gehzeit im Campus, sonst Campuswechsel mit verfügbarer Zeit */
    static String wegText(Termin a, Termin b) {
        long pause = Math.max(0, (b.start - a.ende) / 60_000);
        if (Orte.campusWechsel(a.raum, b.raum)) {
            String c = Campus.ausRaum(b.raum);
            return "Campuswechsel nach " + (c != null ? Campus.kurz(c) : "?") + " (" + pause + " Min Zeit)";
        }
        int m = Orte.gehminuten(a.raum, b.raum);
        if (m <= 0) return null;
        return m + " Min Fußweg" + (m > pause ? " – knapp!" : "");
    }
}
