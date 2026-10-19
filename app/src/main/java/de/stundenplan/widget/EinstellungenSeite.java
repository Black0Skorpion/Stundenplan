package de.stundenplan.widget;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.util.ArrayList;
import java.util.List;

/** Reiter „Einstellungen“ – aufgebaut wie auf dem iPhone, Seltenes unter „Debug“. */
final class EinstellungenSeite implements MainActivity.Seite {

    static final int ICS_WAEHLEN = 1;
    static final int MITTEILUNGEN_ERLAUBEN = 2;

    private final MainActivity act;
    private final Ui ui;
    private final ScrollView scroll;
    private final LinearLayout inhalt;
    private boolean debugOffen = false;
    /** Was nach der Mitteilungs-Erlaubnis eingeschaltet werden soll */
    private String wartet;

    EinstellungenSeite(MainActivity act) {
        this.act = act;
        ui = new Ui(act);
        scroll = new ScrollView(act);
        inhalt = new LinearLayout(act);
        inhalt.setOrientation(LinearLayout.VERTICAL);
        inhalt.setPadding(0, ui.dp(10), 0, ui.dp(30));
        scroll.addView(inhalt);
    }

    @Override public View ansicht() { return scroll; }

    @Override
    public void zeigen() {
        int y = scroll.getScrollY();
        aufbauen();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    // ───────────── Bausteine ─────────────

    private LinearLayout schalter(String text, boolean an, Switch.OnCheckedChangeListener l) {
        LinearLayout z = ui.zeile(null);
        TextView t = ui.text(text, 16, ui.vorder);
        z.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch s = new Switch(act);
        s.setChecked(an);
        s.setOnCheckedChangeListener(l);
        z.addView(s);
        return z;
    }

    interface Wahl { void gewaehlt(int i); }

    /** Zeile mit aktuellem Wert; Antippen öffnet eine Auswahl */
    private LinearLayout auswahl(String text, String[] namen, int aktuell, Wahl w) {
        return ui.wertZeile(text, namen[Math.max(0, Math.min(namen.length - 1, aktuell))] + " ›", v -> {
            String[] m = namen.clone();
            if (aktuell >= 0 && aktuell < m.length) m[aktuell] = "✓ " + m[aktuell];
            new AlertDialog.Builder(act).setTitle(text).setItems(m, (d, i) -> { w.gewaehlt(i); aufbauen(); }).show();
        });
    }

    private void aufbauen() {
        inhalt.removeAllViews();
        inhalt.addView(ui.titel("Einstellungen"));

        // ───── Stundenplan ─────
        PulsDienst.Auswahl a = PulsDienst.auswahl(act);
        int anzahl = Daten.laden(act).size();
        inhalt.addView(ui.abschnitt("Stundenplan"));
        LinearLayout g = ui.gruppe();
        ui.fuellen(g,
                ui.wertZeile("Quelle", a.aktiv ? "PULS (" + a.kurse.size() + " Veranstaltungen)" : Daten.vorhanden(act) ? "Datei" : "noch keiner", null),
                ui.wertZeile("Termine", String.valueOf(anzahl), null),
                ui.knopfZeile("Veranstaltungen aus PULS wählen", v -> act.startActivity(new Intent(act, PulsActivity.class))));
        inhalt.addView(g);
        inhalt.addView(ui.fuss("Veranstaltungen und Gruppen direkt aus PULS wählen – die App gleicht Räume und Zeiten dann einmal täglich ab."));

        // ───── Teilen & Profile ─────
        inhalt.addView(ui.abschnitt("Teilen & Profile"));
        g = ui.gruppe();
        LinearLayout nameZeile = ui.zeile(null);
        nameZeile.addView(ui.text("Dein Name", 16, ui.vorder), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        EditText name = new EditText(act);
        name.setSingleLine(true);
        name.setHint("Name");
        name.setText(Teilen.name(act));
        name.setMinWidth(ui.dp(140));
        name.setBackground(null);
        name.setGravity(android.view.Gravity.END);
        name.setTextColor(ui.gedimmt);
        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void afterTextChanged(Editable s) { Teilen.setzeName(act, s.toString()); }
        });
        nameZeile.addView(name);
        List<View> profilZeilen = new ArrayList<>();
        profilZeilen.add(nameZeile);
        profilZeilen.add(ui.knopfZeile("Meinen Plan teilen (QR-Code, Datei)", v -> act.startActivity(new Intent(act, TeilenActivity.class))));
        profilZeilen.add(ui.knopfZeile("QR-Code scannen", v -> scannen(act)));
        profilZeilen.add(ui.knopfZeile("Link einfügen", v -> linkEinfuegen()));
        for (Teilen.Profil p : Teilen.profile(act)) {
            profilZeilen.add(ui.wertZeile(p.name, p.termine.size() + " Termine ›", v -> profilMenue(p)));
        }
        ui.fuellen(g, profilZeilen.toArray(new View[0]));
        inhalt.addView(g);
        inhalt.addView(ui.fuss("Der Name steht auf deinem geteilten Plan. Pläne anderer im Plan-Reiter oben links umschalten. Eine geteilte .ics-Datei einfach mit „Stundenplan“ öffnen."));

        // ───── Wecker ─────
        inhalt.addView(ui.abschnitt("Wecker"));
        g = ui.gruppe();
        List<View> wz = new ArrayList<>();
        wz.add(schalter("Vor der ersten Veranstaltung wecken", Wecker.an(act), (s, an) -> {
            Wecker.prefs(act).edit().putBoolean(Wecker.AN, an).apply();
            Wecker.planen(act);
            aufbauen();
        }));
        if (Wecker.an(act)) {
            String[] vn = new String[Wecker.VORLAUF_OPTIONEN.length];
            int vg = 0;
            for (int i = 0; i < vn.length; i++) {
                vn[i] = Wecker.vorlaufText(Wecker.VORLAUF_OPTIONEN[i]);
                if (Wecker.VORLAUF_OPTIONEN[i] == Wecker.vorlauf(act)) vg = i;
            }
            wz.add(auswahl("Vorher", vn, vg, i -> {
                Wecker.prefs(act).edit().putInt(Wecker.VORLAUF, Wecker.VORLAUF_OPTIONEN[i]).apply();
                Wecker.planen(act);
            }));
            String[] fn = new String[Wecker.FRUEHESTENS_OPTIONEN.length];
            int fg = 0;
            for (int i = 0; i < fn.length; i++) {
                fn[i] = Wecker.uhrText(Wecker.FRUEHESTENS_OPTIONEN[i]);
                if (Wecker.FRUEHESTENS_OPTIONEN[i] == Wecker.fruehestens(act)) fg = i;
            }
            wz.add(auswahl("Frühestens um", fn, fg, i -> {
                Wecker.prefs(act).edit().putInt(Wecker.FRUEHESTENS, Wecker.FRUEHESTENS_OPTIONEN[i]).apply();
                Wecker.planen(act);
            }));
            String[] sn = new String[Wecker.SCHLUMMER_OPTIONEN.length];
            int sg = 1;
            for (int i = 0; i < sn.length; i++) {
                sn[i] = Wecker.SCHLUMMER_OPTIONEN[i] + " Min";
                if (Wecker.SCHLUMMER_OPTIONEN[i] == Wecker.schlummern(act)) sg = i;
            }
            wz.add(auswahl("Schlummern", sn, sg, i -> Wecker.prefs(act).edit().putInt(Wecker.SCHLUMMERN, Wecker.SCHLUMMER_OPTIONEN[i]).apply()));
            long[] n = Wecker.naechste(act);
            wz.add(ui.wertZeile("Nächster Wecker", n == null ? "keiner in 14 Tagen"
                    : Lageplan.datumKurz(n[0]) + " " + Lageplan.uhrzeitText(n[0]), null));
            if (!Wecker.darfStellen(act) && Build.VERSION.SDK_INT >= 31) {
                wz.add(ui.knopfZeile("„Wecker und Erinnerungen“ erlauben", v -> act.startActivity(
                        new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + act.getPackageName())))));
            }
        }
        ui.fuellen(g, wz.toArray(new View[0]));
        inhalt.addView(g);
        inhalt.addView(ui.fuss("Klingelt wie ein Wecker (mit Schlummern), nur an Tagen mit Veranstaltungen. Ausgefallene Termine zählen nicht. Am Vorabend zeigt das Stundenplan-Widget, wann er klingelt."));

        // ───── Erinnerungen ─────
        inhalt.addView(ui.abschnitt("Erinnerungen"));
        g = ui.gruppe();
        List<View> ez = new ArrayList<>();
        boolean erAn = Erinnerungen.an(act) && Erinnerungen.erlaubt(act);
        ez.add(schalter("Vor Veranstaltungen erinnern", erAn, (s, an) -> {
            if (an && !Erinnerungen.erlaubt(act)) { erlauben(Erinnerungen.AN); return; }
            Erinnerungen.prefs(act).edit().putBoolean(Erinnerungen.AN, an).apply();
            Erinnerungen.planen(act);
            aufbauen();
        }));
        if (erAn) {
            String[] en = new String[Erinnerungen.VORLAUF_OPTIONEN.length];
            int eg = 0;
            for (int i = 0; i < en.length; i++) {
                en[i] = Erinnerungen.VORLAUF_OPTIONEN[i] + " Minuten";
                if (Erinnerungen.VORLAUF_OPTIONEN[i] == Erinnerungen.vorlauf(act)) eg = i;
            }
            ez.add(auswahl("Vorlauf", en, eg, i -> {
                Erinnerungen.prefs(act).edit().putInt(Erinnerungen.VORLAUF, Erinnerungen.VORLAUF_OPTIONEN[i]).apply();
                Erinnerungen.planen(act);
            }));
        }
        ui.fuellen(g, ez.toArray(new View[0]));
        inhalt.addView(g);
        inhalt.addView(ui.fuss("Mitteilung mit Raum und Countdown vor jeder Veranstaltung. Tippen öffnet den Lageplan."));

        // ───── Live Update ─────
        if (LiveUpdate.verfuegbar()) {
            inhalt.addView(ui.abschnitt("Live Update"));
            g = ui.gruppe();
            ui.fuellen(g, schalter("Laufende Veranstaltung anzeigen", LiveUpdate.an(act) && Erinnerungen.erlaubt(act), (s, an) -> {
                if (an && !Erinnerungen.erlaubt(act)) { erlauben(LiveUpdate.AN); return; }
                Erinnerungen.prefs(act).edit().putBoolean(LiveUpdate.AN, an).apply();
                LiveUpdate.planen(act);
            }));
            inhalt.addView(g);
            inhalt.addView(ui.fuss("In der Statusleiste und auf dem Sperrbildschirm (Samsung: Now Bar): ab einer Stunde vorher Countdown und Raum, während der Vorlesung Ende und Fortschritt."));
        }

        // ───── Campus ─────
        inhalt.addView(ui.abschnitt("Campus"));
        g = ui.gruppe();
        final String[] werte = {Campus.AUTO, Campus.GOLM, Campus.NEUES_PALAIS, Campus.GRIEBNITZSEE};
        String[] namen = {"Automatisch", "Golm", "Am Neuen Palais", "Griebnitzsee"};
        int cw = 0;
        for (int i = 0; i < werte.length; i++) if (werte[i].equals(Campus.wahl(act))) cw = i;
        List<View> cz = new ArrayList<>();
        cz.add(auswahl("Campus", namen, cw, i -> {
            Campus.setzeWahl(act, werte[i]);
            MensaWidget.alleAktualisieren(act, false);
            AbfahrtenWidget.alleAktualisieren(act, false);
        }));
        if (cw == 0) cz.add(ui.wertZeile("Gerade", Campus.name(Campus.aktuell(act)), null));
        ui.fuellen(g, cz.toArray(new View[0]));
        inhalt.addView(g);
        inhalt.addView(ui.fuss("Gilt für Mensa und Abfahrten (auch in den Widgets). „Automatisch“ nimmt den Campus der laufenden bzw. nächsten Veranstaltung."));

        // ───── Priorisierte Linien ─────
        inhalt.addView(ui.abschnitt("Priorisierte Linien"));
        g = ui.gruppe();
        List<View> lz = new ArrayList<>();
        for (String l : Campus.favoriten(act)) {
            lz.add(ui.wertZeile("★ " + l, "Entfernen", v -> {
                List<String> f = new ArrayList<>(Campus.favoriten(act));
                f.remove(l);
                Campus.setzeFavoriten(act, String.join(", ", f));
                AbfahrtenWidget.alleAktualisieren(act, false);
                aufbauen();
            }));
        }
        lz.add(ui.knopfZeile("Linie hinzufügen …", v -> {
            EditText f = new EditText(act);
            f.setHint("z. B. RB20");
            f.setSingleLine(true);
            f.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            new AlertDialog.Builder(act).setTitle("Linie priorisieren").setView(f)
                    .setPositiveButton("Hinzufügen", (d, w) -> {
                        List<String> fav = new ArrayList<>(Campus.favoriten(act));
                        fav.add(f.getText().toString().trim());
                        Campus.setzeFavoriten(act, String.join(", ", fav));
                        AbfahrtenWidget.alleAktualisieren(act, false);
                        aufbauen();
                    }).setNegativeButton("Abbrechen", null).show();
        }));
        ui.fuellen(g, lz.toArray(new View[0]));
        inhalt.addView(g);
        inhalt.addView(ui.fuss("Stehen in den Abfahrten oben und im Widget immer mit drin. Geht auch direkt in den Abfahrten: Linie gedrückt halten."));

        // ───── Info ─────
        inhalt.addView(ui.abschnitt("Widgets"));
        g = ui.gruppe();
        ui.fuellen(g, auswahl("Farbschema", WidgetThema.NAMEN, WidgetThema.wahl(act), i -> WidgetThema.setzen(act, i)));
        inhalt.addView(g);
        inhalt.addView(ui.fuss("Gilt für alle Widgets (Stundenplan, Mensa, Abfahrten). „Transparent“: ohne Hintergrund, das Hintergrundbild scheint durch; die Schrift richtet sich nach dem System (hell/dunkel)."));

        inhalt.addView(ui.abschnitt("Info"));
        g = ui.gruppe();
        String version = "–";
        try { version = act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName; } catch (Exception ignored) {}
        ui.fuellen(g,
                ui.wertZeile("Version", version, null),
                ui.knopfZeile("Einführung nochmal zeigen", v -> act.startActivity(new Intent(act, EinfuehrungActivity.class))),
                ui.knopfZeile("Speiseplan: OpenMensa", v -> act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(MensaDaten.webseite(act))))));
        inhalt.addView(g);

        // ───── Debug ─────
        inhalt.addView(ui.abschnitt(""));
        g = ui.gruppe();
        List<View> dz = new ArrayList<>();
        dz.add(ui.knopfZeile(debugOffen ? "Debug ▴" : "Debug ▾", v -> { debugOffen = !debugOffen; aufbauen(); }));
        if (debugOffen) {
            dz.add(ui.knopfZeile("ICS-Datei importieren …", v -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                act.startActivityForResult(i, ICS_WAEHLEN);
            }));
            String link = act.getSharedPreferences(StundenplanWidget.EINSTELLUNGEN, Context.MODE_PRIVATE).getString(StundenplanWidget.PLANER_LINK, "");
            dz.add(ui.wertZeile("Planer-Link", link.isEmpty() ? "Standard ›" : "eigener ›", v -> planerLink()));
            dz.add(ui.knopfZeile("Planer öffnen", v -> act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(StundenplanWidget.planerLink(act))))));
            dz.add(ui.knopfZeile("Widget-Vorschau", v -> act.startActivity(new Intent(act, VorschauActivity.class))));
            dz.add(ui.knopfZeile("Widgets neu zeichnen", v -> {
                PulsDienst.nachAenderung(act);
                MensaWidget.alleAktualisieren(act, true);
                AbfahrtenWidget.alleAktualisieren(act, true);
                Toast.makeText(act, "Widgets werden neu gezeichnet", Toast.LENGTH_SHORT).show();
            }));
        }
        ui.fuellen(g, dz.toArray(new View[0]));
        inhalt.addView(g);
        if (debugOffen) inhalt.addView(ui.fuss("Datei-Import, Planer-Link und Widgets – braucht man normalerweise nicht."));
    }

    // ───────────── Aktionen ─────────────

    private void planerLink() {
        EditText f = new EditText(act);
        f.setSingleLine(true);
        f.setHint(Konfig.TAP_URL);
        f.setText(act.getSharedPreferences(StundenplanWidget.EINSTELLUNGEN, Context.MODE_PRIVATE).getString(StundenplanWidget.PLANER_LINK, ""));
        new AlertDialog.Builder(act).setTitle("Planer-Link")
                .setMessage("Im Planer (planner.joshuan.dev) auf „Plan teilen“ tippen und den Link hier einfügen. Öffnet sich im Lageplan.")
                .setView(f)
                .setPositiveButton("Speichern", (d, w) -> {
                    String l = f.getText().toString().trim();
                    if (!l.isEmpty() && !l.startsWith("http")) {
                        Toast.makeText(act, "Der Link muss mit https:// beginnen", Toast.LENGTH_LONG).show();
                        return;
                    }
                    act.getSharedPreferences(StundenplanWidget.EINSTELLUNGEN, Context.MODE_PRIVATE).edit().putString(StundenplanWidget.PLANER_LINK, l).apply();
                    StundenplanWidget.alleAktualisieren(act);
                    aufbauen();
                })
                .setNeutralButton("Standard", (d, w) -> {
                    act.getSharedPreferences(StundenplanWidget.EINSTELLUNGEN, Context.MODE_PRIVATE).edit().putString(StundenplanWidget.PLANER_LINK, "").apply();
                    aufbauen();
                })
                .setNegativeButton("Abbrechen", null).show();
    }

    private void erlauben(String schluessel) {
        wartet = schluessel;
        if (Build.VERSION.SDK_INT >= 33) {
            act.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, MITTEILUNGEN_ERLAUBEN);
        }
    }

    void berechtigung(int code, int[] ergebnis) {
        if (code != MITTEILUNGEN_ERLAUBEN) return;
        boolean ok = ergebnis.length > 0 && ergebnis[0] == PackageManager.PERMISSION_GRANTED;
        if (wartet != null) Erinnerungen.prefs(act).edit().putBoolean(wartet, ok).apply();
        wartet = null;
        if (!ok) Toast.makeText(act, "Mitteilungen sind nicht erlaubt (Einstellungen → Apps → Stundenplan)", Toast.LENGTH_LONG).show();
        Erinnerungen.planen(act);
        LiveUpdate.planen(act);
        aufbauen();
    }

    void ergebnis(int code, int resultCode, Intent data) {
        if (code != ICS_WAEHLEN || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        // Gleicher Weg wie eine geteilte Datei: als Profil, als eigenen Plan oder in der Modulwahl
        act.startActivity(new Intent(act, EmpfangActivity.class).setAction(Intent.ACTION_VIEW).setData(data.getData())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    }

    /** QR-Code eines geteilten Plans scannen (über die Google-Play-Dienste, ohne Kamera-Berechtigung) */
    static void scannen(Activity act) {
        try {
            GmsBarcodeScannerOptions o = new GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
            GmsBarcodeScanning.getClient(act, o).startScan()
                    .addOnSuccessListener(b -> empfangen(act, b.getRawValue()))
                    .addOnFailureListener(e -> Toast.makeText(act, "Scanner nicht verfügbar – alternativ den Link einfügen.", Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            Toast.makeText(act, "Scanner nicht verfügbar – alternativ den Link einfügen.", Toast.LENGTH_LONG).show();
        }
    }

    static void empfangen(Activity act, String text) {
        if (EmpfangActivity.ausText(text) == null) {
            Toast.makeText(act, "Das ist kein Stundenplan-Link.", Toast.LENGTH_LONG).show();
            return;
        }
        act.startActivity(new Intent(act, EmpfangActivity.class).putExtra(EmpfangActivity.EXTRA_LINK, text));
    }

    private void linkEinfuegen() {
        ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        String text = null;
        if (cm != null && cm.hasPrimaryClip()) {
            ClipData c = cm.getPrimaryClip();
            if (c != null && c.getItemCount() > 0 && c.getItemAt(0).getText() != null) text = c.getItemAt(0).getText().toString();
        }
        if (EmpfangActivity.ausText(text) != null) { empfangen(act, text); return; }
        EditText feld = new EditText(act);
        feld.setHint("stundenplan://teilen?d=…");
        new AlertDialog.Builder(act).setTitle("Link einfügen").setView(feld)
                .setPositiveButton("Öffnen", (x, y) -> empfangen(act, feld.getText().toString()))
                .setNegativeButton("Abbrechen", null).show();
    }

    private void profilMenue(Teilen.Profil p) {
        List<String> punkte = new ArrayList<>();
        punkte.add("Im Plan anzeigen");
        punkte.add("Umbenennen");
        if (p.freigabe != null) punkte.add("Aus PULS aktualisieren");
        punkte.add("Löschen");
        new AlertDialog.Builder(act).setTitle(p.name).setItems(punkte.toArray(new String[0]), (dlg, i) -> {
            String w = punkte.get(i);
            if (w.startsWith("Im Plan")) { MainActivity.setzeAnzeige(act, p.id); act.wechseln(MainActivity.PLAN); }
            else if (w.equals("Umbenennen")) umbenennen(p);
            else if (w.equals("Löschen")) {
                List<Teilen.Profil> alle = Teilen.profile(act);
                alle.removeIf(x -> x.id.equals(p.id));
                Teilen.speichern(act, alle);
                aufbauen();
            } else aktualisieren(p);
        }).show();
    }

    private void umbenennen(Teilen.Profil p) {
        EditText feld = new EditText(act);
        feld.setSingleLine(true);
        feld.setText(p.name);
        new AlertDialog.Builder(act).setTitle("Name").setView(feld)
                .setPositiveButton("Speichern", (x, y) -> {
                    String n = feld.getText().toString().trim();
                    if (n.isEmpty()) return;
                    List<Teilen.Profil> alle = Teilen.profile(act);
                    for (Teilen.Profil q : alle) if (q.id.equals(p.id)) q.name = n;
                    Teilen.speichern(act, alle);
                    aufbauen();
                })
                .setNegativeButton("Abbrechen", null).show();
    }

    private void aktualisieren(Teilen.Profil p) {
        Teilen.Freigabe f = p.freigabe();
        if (f == null) return;
        Toast.makeText(act, "Wird aus PULS geladen …", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<Termin> t = Teilen.termineLaden(act, f);
            act.runOnUiThread(() -> {
                if (t == null) { Toast.makeText(act, "PULS war nicht erreichbar.", Toast.LENGTH_LONG).show(); return; }
                List<Teilen.Profil> alle = Teilen.profile(act);
                for (Teilen.Profil q : alle) if (q.id.equals(p.id)) { q.termine = t; q.stand = System.currentTimeMillis(); }
                Teilen.speichern(act, alle);
                aufbauen();
            });
        }).start();
    }
}
