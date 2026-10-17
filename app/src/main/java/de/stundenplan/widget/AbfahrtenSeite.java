package de.stundenplan.widget;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Reiter „Abfahrten“: nächste Züge und Busse am Campus, priorisierte Linien oben. */
final class AbfahrtenSeite implements MainActivity.Seite {

    private final MainActivity act;
    private final Ui ui;
    private final LinearLayout wurzel, liste;
    private final TextView campusKnopf;
    private int ladeNr = 0;
    private final Runnable takt = new Runnable() {
        @Override public void run() {
            laden(AbfahrtenDaten.alter(act) > 60_000);
            act.handler.postDelayed(this, 60_000);
        }
    };

    AbfahrtenSeite(MainActivity act) {
        this.act = act;
        ui = new Ui(act);
        wurzel = new LinearLayout(act);
        wurzel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout kopf = new LinearLayout(act);
        kopf.setGravity(Gravity.CENTER_VERTICAL);
        kopf.setPadding(0, ui.dp(10), ui.dp(12), 0);
        kopf.addView(ui.titel("Abfahrten"), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        campusKnopf = ui.kapsel("", ui.akzent, false, v -> campusWaehlen());
        kopf.addView(campusKnopf);
        TextView neu = ui.kapsel("↻", ui.akzent, false, v -> laden(true));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nl.leftMargin = ui.dp(6);
        kopf.addView(neu, nl);
        TextView mehr = ui.kapsel("⋯", ui.akzent, false, v -> menue());
        kopf.addView(mehr, nl);
        wurzel.addView(kopf);
        ScrollView s = new ScrollView(act);
        liste = new LinearLayout(act);
        liste.setOrientation(LinearLayout.VERTICAL);
        liste.setPadding(0, 0, 0, ui.dp(24));
        s.addView(liste);
        wurzel.addView(s, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
    }

    @Override public View ansicht() { return wurzel; }

    @Override
    public void zeigen() {
        campusKnopf.setText(Campus.kurz(Campus.aktuell(act)) + " ▾");
        act.handler.removeCallbacks(takt);
        // Erst den letzten Stand sofort zeigen, dann frisch laden
        String campus = Campus.aktuell(act);
        new Thread(() -> {
            List<AbfahrtenDaten.Abfahrt> alt = AbfahrtenDaten.laden(act, campus, false);
            act.runOnUiThread(() -> { if (alt != null) anzeigen(alt, campus, true); });
        }).start();
        takt.run();
    }

    @Override public void verstecken() { act.handler.removeCallbacks(takt); }

    private void laden(boolean netz) {
        final String campus = Campus.aktuell(act);
        final int nr = ++ladeNr;
        new Thread(() -> {
            List<AbfahrtenDaten.Abfahrt> l = AbfahrtenDaten.laden(act, campus, netz);
            AbfahrtenWidget.alleAktualisieren(act, false);
            act.runOnUiThread(() -> {
                if (nr != ladeNr) return;
                anzeigen(l, campus, false);
            });
        }).start();
    }

    private void anzeigen(List<AbfahrtenDaten.Abfahrt> alle, String campus, boolean vorlaeufig) {
        liste.removeAllViews();
        if (alle == null) {
            TextView t = ui.text("Die Abfahrten konnten gerade nicht geladen werden (VBB nicht erreichbar oder kein Netz).", 15, ui.gedimmt);
            t.setPadding(ui.dp(20), ui.dp(30), ui.dp(20), ui.dp(10));
            liste.addView(t);
            liste.addView(ui.abschnitt("In Google Maps ansehen"));
            LinearLayout g = ui.gruppe();
            List<View> z = new ArrayList<>();
            for (String[] h : Campus.haltestellen(campus)) z.add(ui.knopfZeile(h[1], v -> haltestelle(h[1])));
            ui.fuellen(g, z.toArray(new View[0]));
            liste.addView(g);
            return;
        }
        long jetzt = System.currentTimeMillis();
        List<String> fav = Campus.favoriten(act);
        List<AbfahrtenDaten.Abfahrt> kommend = new ArrayList<>(), favs = new ArrayList<>();
        for (AbfahrtenDaten.Abfahrt a : alle) {
            if (a.zeit < jetzt - 60_000) continue;
            kommend.add(a);
            if (Campus.istFavorit(fav, a.linie) && favs.size() < 6) favs.add(a);
        }
        if (!favs.isEmpty()) {
            liste.addView(ui.abschnitt("Priorisiert"));
            liste.addView(gruppe(favs, fav, jetzt));
        }
        liste.addView(ui.abschnitt("Nächste Abfahrten"));
        if (kommend.isEmpty()) {
            liste.addView(ui.fuss("Gerade keine Abfahrten."));
        } else {
            liste.addView(gruppe(kommend.subList(0, Math.min(40, kommend.size())), fav, jetzt));
        }
        long alter = AbfahrtenDaten.alter(act);
        liste.addView(ui.fuss((vorlaeufig ? "Wird aktualisiert … " : "")
                + (alter < Long.MAX_VALUE ? "Stand: vor " + Math.max(0, alter / 60_000) + " Min · " : "")
                + "Linie gedrückt halten zum Priorisieren. Daten: VBB"));
    }

    private View gruppe(List<AbfahrtenDaten.Abfahrt> l, List<String> fav, long jetzt) {
        LinearLayout g = ui.gruppe();
        List<View> z = new ArrayList<>();
        for (AbfahrtenDaten.Abfahrt a : l) z.add(zeile(a, Campus.istFavorit(fav, a.linie), jetzt));
        ui.fuellen(g, z.toArray(new View[0]));
        return g;
    }

    private View zeile(AbfahrtenDaten.Abfahrt a, boolean favorit, long jetzt) {
        LinearLayout z = ui.zeile(v -> haltestelle(a.haltestelle));
        z.setOnLongClickListener(v -> { favoritFragen(a.linie); return true; });
        TextView linie = ui.fett(a.linie, 13, Color.WHITE);
        linie.setGravity(Gravity.CENTER);
        linie.setPadding(ui.dp(6), ui.dp(3), ui.dp(6), ui.dp(3));
        linie.setBackground(ui.rund(Color.parseColor(AbfahrtenDaten.farbe(a)), 5));
        linie.setMinWidth(ui.dp(46));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ll.rightMargin = ui.dp(10);
        z.addView(linie, ll);
        LinearLayout m = new LinearLayout(act);
        m.setOrientation(LinearLayout.VERTICAL);
        TextView r = ui.text((favorit ? "★ " : "") + AbfahrtenDaten.kurzRichtung(a.richtung), 15.5f, ui.vorder);
        r.setSingleLine(true);
        r.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (a.ausfall) r.setPaintFlags(r.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        m.addView(r);
        m.addView(ui.text(a.haltestelle + (a.gleis != null ? " · Gl. " + a.gleis : ""), 12.5f, ui.gedimmt));
        z.addView(m, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout rechts = new LinearLayout(act);
        rechts.setOrientation(LinearLayout.VERTICAL);
        rechts.setGravity(Gravity.END);
        long min = Math.max(0, (a.zeit - jetzt) / 60_000);
        TextView zeit = ui.fett(a.ausfall ? "fällt aus" : min <= 0 ? "jetzt" : min < 60 ? min + " Min" : Lageplan.uhrzeitText(a.zeit), 15, a.ausfall ? ui.rot : ui.vorder);
        zeit.setTypeface(Typeface.DEFAULT_BOLD);
        rechts.addView(zeit);
        String unten = Lageplan.uhrzeitText(a.zeit);
        if (a.verspaetung != null && a.verspaetung != 0) unten += (a.verspaetung > 0 ? " +" : " ") + a.verspaetung;
        TextView u = ui.text(unten, 12.5f, a.verspaetung != null && a.verspaetung > 0 ? ui.rot : ui.gedimmt);
        rechts.addView(u);
        z.addView(rechts);
        return z;
    }

    private void favoritFragen(String linie) {
        List<String> fav = new ArrayList<>(Campus.favoriten(act));
        boolean ist = Campus.istFavorit(fav, linie);
        new AlertDialog.Builder(act)
                .setTitle(linie)
                .setItems(new String[]{ist ? "Nicht mehr priorisieren" : "„" + linie + "“ priorisieren"}, (d, i) -> {
                    if (ist) fav.removeIf(f -> f.equalsIgnoreCase(linie)); else fav.add(linie);
                    Campus.setzeFavoriten(act, String.join(", ", fav));
                    AbfahrtenWidget.alleAktualisieren(act, false);
                    laden(false);
                }).show();
    }

    private void menue() {
        String campus = Campus.aktuell(act);
        String[][] h = Campus.haltestellen(campus);
        String[] punkte = new String[h.length];
        for (int i = 0; i < h.length; i++) punkte[i] = h[i][1] + " in Google Maps";
        new AlertDialog.Builder(act).setTitle("Haltestellen").setItems(punkte, (d, i) -> haltestelle(h[i][1])).show();
    }

    private void haltestelle(String name) {
        if (name == null || name.isEmpty()) return;
        try {
            act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(name + " Potsdam"))));
        } catch (Exception ignored) {}
    }

    private void campusWaehlen() {
        final String[] werte = {Campus.AUTO, Campus.GOLM, Campus.NEUES_PALAIS, Campus.GRIEBNITZSEE};
        String[] namen = {"Automatisch (nach Stundenplan)", "Golm", "Am Neuen Palais", "Griebnitzsee"};
        String wahl = Campus.wahl(act);
        for (int i = 0; i < werte.length; i++) if (werte[i].equals(wahl)) namen[i] = "✓ " + namen[i];
        new AlertDialog.Builder(act).setTitle("Campus").setItems(namen, (d, i) -> {
            Campus.setzeWahl(act, werte[i]);
            MensaWidget.alleAktualisieren(act, false);
            zeigen();
        }).show();
    }
}
