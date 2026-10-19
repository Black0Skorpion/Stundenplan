package de.stundenplan.widget;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Eigenen Plan teilen: QR-Code (PULS-Auswahl mit Gruppen) oder .ics-Datei – gleiches Format wie die iOS-App. */
public class TeilenActivity extends Activity {

    private ImageView qr;
    private TextView qrInfo;
    /** Eigene Termine, die mitgeschickt werden (IDs) */
    private final java.util.Set<String> mit = new java.util.HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Plan teilen");
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * d);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(pad, pad, pad, pad);

        TextView nt = new TextView(this);
        nt.setText("Dein Name (steht beim Empfänger über deinem Plan):");
        l.addView(nt);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Dein Name");
        name.setText(Teilen.name(this));
        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                Teilen.setzeName(TeilenActivity.this, s.toString());
                qrZeichnen();
            }
        });
        l.addView(name);

        List<EigeneTermine.Eintrag> eigene = EigeneTermine.alle(this);
        if (!eigene.isEmpty()) {
            TextView et = new TextView(this);
            et.setText("\nEigene Termine mitschicken:");
            l.addView(et);
            eigene.sort((a, b) -> Long.compare(a.start, b.start));
            for (EigeneTermine.Eintrag e : eigene) {
                android.widget.CheckBox c = new android.widget.CheckBox(this);
                c.setText(e.titel + "\n" + EigenerTerminActivity.beschreibung(e));
                c.setOnCheckedChangeListener((k, an) -> {
                    if (an) mit.add(e.id); else mit.remove(e.id);
                    qrZeichnen();
                });
                l.addView(c);
            }
            TextView h = new TextView(this);
            h.setText("Nur die ausgewählten kommen mit – im QR-Code, im Link und in der Datei.");
            h.setAlpha(0.7f);
            l.addView(h);
        }

        qr = new ImageView(this);
        qr.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(Math.round(280 * d), Math.round(280 * d));
        qlp.gravity = Gravity.CENTER_HORIZONTAL;
        qlp.topMargin = Math.round(16 * d);
        l.addView(qr, qlp);
        qrInfo = new TextView(this);
        qrInfo.setGravity(Gravity.CENTER);
        qrInfo.setPadding(0, Math.round(8 * d), 0, Math.round(16 * d));
        l.addView(qrInfo);

        Button datei = new Button(this);
        datei.setText("Als Datei teilen (.ics)");
        datei.setOnClickListener(v -> dateiTeilen());
        l.addView(datei);
        Button link = new Button(this);
        link.setText("Link teilen");
        link.setOnClickListener(v -> {
            Teilen.Freigabe f = Teilen.eigeneFreigabe(this, mit);
            String s = f.leer() ? null : f.link();
            if (s == null) { Toast.makeText(this, "Ein Link geht nur, wenn dein Plan aus der PULS-Modulwahl kommt oder eigene Termine ausgewählt sind.", Toast.LENGTH_LONG).show(); return; }
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Mein Stundenplan" + (f.name.isEmpty() ? "" : " (" + f.name + ")")
                            + " – in der Stundenplan-App öffnen oder dort unter „Link einfügen“:\n" + s), "Link teilen"));
        });
        l.addView(link);

        TextView hilfe = new TextView(this);
        hilfe.setText("\nQR-Code: in der Stundenplan-App (iPhone oder Android) „QR-Code scannen“, oder mit der Kamera. "
                + "Die Termine werden dann direkt aus PULS geladen.\n\nDatei: per Messenger, Mail usw. In der Stundenplan-App öffnen "
                + "→ als Profil speichern, als eigenen Plan übernehmen oder (bei PULS) in der Modulwahl anpassen. Andere Kalender-Apps können sie auch importieren.");
        hilfe.setAlpha(0.75f);
        l.addView(hilfe);

        ScrollView s = new ScrollView(this);
        s.addView(l);
        setContentView(s);
        qrZeichnen();
    }

    private void qrZeichnen() {
        Teilen.Freigabe f = Teilen.eigeneFreigabe(this, mit);
        String link = f.leer() ? null : f.link();
        Bitmap b = link == null ? null : Teilen.qr(link, 800);
        if (b == null) {
            qr.setImageBitmap(null);
            qr.setVisibility(android.view.View.GONE);
            qrInfo.setText("Der QR-Code geht, sobald dein Plan aus der PULS-Modulwahl kommt. Als Datei teilen geht immer.");
        } else {
            qr.setVisibility(android.view.View.VISIBLE);
            qr.setImageBitmap(b);
            String info = f.mitPuls() ? f.kurse.size() + " Fächer mit Gruppen" : "";
            if (!f.eigene.isEmpty()) info += (info.isEmpty() ? "" : " · ") + f.eigene.size() + " eigene Termine";
            qrInfo.setText(info);
        }
    }

    private void dateiTeilen() {
        Teilen.Freigabe fg = Teilen.eigeneFreigabe(this, mit);
        List<Termin> t = new java.util.ArrayList<>(Teilen.eigeneZumTeilen(this));
        // Eigene Termine als normale Kalendereinträge (für andere Kalender-Apps) und zusätzlich in der Freigabe
        t.addAll(fg.eigeneEinzeln());
        if (t.isEmpty()) { Toast.makeText(this, "Keine Termine zum Teilen.", Toast.LENGTH_SHORT).show(); return; }
        String name = Teilen.name(this);
        String text = Teilen.ics(t, name, fg);
        String dateiname = (name.isEmpty() ? "Stundenplan" : "Stundenplan " + name).replaceAll("[^\\p{L}\\p{N} -]", "") + ".ics";
        File f = new File(Dateien.ordner(this), dateiname);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Toast.makeText(this, "Datei konnte nicht erstellt werden.", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Dateien.uri(f);
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/calendar").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri(dateiname, uri));
        startActivity(Intent.createChooser(i, "Stundenplan teilen"));
    }
}
