package de.stundenplan.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lageplan Campus Golm: Gebäudepositionen, Legende und die Ansicht als HTML. */
public final class Lageplan {
    private Lageplan() {}

    /** Position der Hausnummer auf dem Plan (Anteil von Breite/Höhe). */
    private static final Map<String, float[]> POS = new HashMap<>();
    /** Einrichtungen laut Legende (Stand Mai 2025). */
    private static final Map<String, String[]> EIN = new HashMap<>();
    static {
        POS.put("25", new float[]{0.3622f, 0.4749f});
        POS.put("28", new float[]{0.3706f, 0.5459f});
        POS.put("18", new float[]{0.3505f, 0.6258f});
        POS.put("14", new float[]{0.4879f, 0.7238f});
        POS.put("8", new float[]{0.6598f, 0.5596f});
        POS.put("11", new float[]{0.6606f, 0.7432f});
        POS.put("24", new float[]{0.6679f, 0.7883f});
        POS.put("19", new float[]{0.5668f, 0.8721f});
        POS.put("35", new float[]{0.6534f, 0.8581f});
        POS.put("20", new float[]{0.7211f, 0.8428f});
        POS.put("6", new float[]{0.7917f, 0.5255f});
        POS.put("5", new float[]{0.8661f, 0.5624f});
        POS.put("4", new float[]{0.8646f, 0.6525f});
        POS.put("1", new float[]{0.7804f, 0.7095f});
        POS.put("26", new float[]{0.5281f, 0.4119f});
        POS.put("10", new float[]{0.6594f, 0.6776f});
        POS.put("9", new float[]{0.7086f, 0.6315f});
        POS.put("2", new float[]{0.7602f, 0.6604f});
        POS.put("27", new float[]{0.4745f, 0.5309f});
        POS.put("29", new float[]{0.5251f, 0.5677f});
        POS.put("31", new float[]{0.5587f, 0.7556f});
        POS.put("30", new float[]{0.5462f, 0.7166f});
        POS.put("12", new float[]{0.9248f, 0.5391f});
        POS.put("17", new float[]{0.3888f, 0.7199f});
        POS.put("60", new float[]{0.8442f, 0.1093f});
        POS.put("13", new float[]{0.7916f, 0.8074f});
        POS.put("48", new float[]{0.2998f, 0.6972f});
        POS.put("32", new float[]{0.471f, 0.6365f});
        POS.put("70", new float[]{0.3853f, 0.0464f});
        POS.put("16", new float[]{0.3861f, 0.813f});
        POS.put("62", new float[]{0.0741f, 0.2764f});
        POS.put("7", new float[]{0.7796f, 0.5907f});
        POS.put("15", new float[]{0.4928f, 0.7715f});
        EIN.put("1", new String[]{"Infrastrukturelles/Technisches Gebäudemanagement", "Institut für Umweltwissenschaften und Geographie", "Leitzentrale"});
        EIN.put("2", new String[]{"Potsdam Research Institute for Multilingualism (PRIM)"});
        EIN.put("4", new String[]{"Mensa"});
        EIN.put("5", new String[]{"Multimediaproduktion (ZIM)"});
        EIN.put("6", new String[]{"Department Musik und Kunst"});
        EIN.put("7", new String[]{"Wohnanlagen (Studierendenwerk)"});
        EIN.put("8", new String[]{"Service Center (ZIM)", "Gebäudemanagement"});
        EIN.put("9", new String[]{"Institut für Mathematik"});
        EIN.put("10", new String[]{"EPI-Center", "Fuhrpark"});
        EIN.put("11", new String[]{"Alte Sporthalle", "Lehreinheit Wirtschaft-Arbeit-Technik"});
        EIN.put("12", new String[]{"Institut für Umweltwissenschaften und Geographie"});
        EIN.put("13", new String[]{"Atelier – Werkstätten Kunst", "Fachbereich Kunst", "Pädagogische Werkstatt"});
        EIN.put("14", new String[]{"Department Linguistik", "Department Psychologie", "Lesecafé", "Poststelle"});
        EIN.put("16", new String[]{"Department Grundschulpädagogik"});
        EIN.put("17", new String[]{"Wohnanlagen (Studierendenwerk)"});
        EIN.put("18", new String[]{"Universitätsbibliothek (IKMZ)"});
        EIN.put("19", new String[]{"Sporthalle"});
        EIN.put("20", new String[]{"Institut für Biochemie und Biologie"});
        EIN.put("24", new String[]{"Department Erziehungswissenschaften", "Department Sport- und Gesundheitswissenschaften", "Department Lehrerbildung", "Institut für Umweltwissenschaften und Geographie"});
        EIN.put("25", new String[]{"Institut für Biochemie und Biologie", "Institut für Chemie"});
        EIN.put("26", new String[]{"Institut für Biochemie und Biologie", "Institut für Chemie"});
        EIN.put("27", new String[]{"Institut für Erd- und Umweltwissenschaften"});
        EIN.put("28", new String[]{"Institut für Physik und Astronomie", "Dekanat Math.-Nat. Fakultät"});
        EIN.put("29", new String[]{"Institut für Geowissenschaften", "Institut für Biochemie und Biologie", "Institut für Chemie", "Potsdam Transfer"});
        EIN.put("30", new String[]{"Gewächshaus"});
        EIN.put("31", new String[]{"Department Inklusionspädagogik"});
        EIN.put("32", new String[]{"Earth & Environment Centre (EEC)", "Zentrum für Naturstoffgenomik"});
        EIN.put("35", new String[]{"Dekanat Humanwissenschaftliche Fakultät"});
        EIN.put("48", new String[]{"Infopoint"});
        EIN.put("60", new String[]{"Institut für Biochemie und Biologie"});
        EIN.put("62", new String[]{"Fakultät für Gesundheitswissenschaften", "Zentrum für Lehrerbildung (ZeLB)"});
        EIN.put("70", new String[]{"Institut für Informatik und Computational Science", "Institut für Umweltwissenschaften und Geographie"});
    }

    private static final String[] CAMPUS = {"", "Am Neuen Palais", "Golm", "Griebnitzsee"};
    private static final String[] WT = {"So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"};
    private static final String[] MONATE = {"Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"};

    /** "2.70.0.10" → { "2", "70", "0", "10" } oder null */
    static String[] raumAufteilen(String raum) {
        if (raum == null) return null;
        String[] t = raum.split("\\.");
        if (t.length < 3) return null;
        StringBuilder rest = new StringBuilder();
        for (int i = 3; i < t.length; i++) { if (rest.length() > 0) rest.append('.'); rest.append(t[i]); }
        return new String[]{t[0], t[1], t[2], rest.toString()};
    }

    static String etageText(String e) {
        if ("0".equals(e)) return "Erdgeschoss";
        if (e.matches("-?\\d+")) {
            int n = Integer.parseInt(e);
            return n > 0 ? n + ". Obergeschoss" : Math.abs(n) + ". Untergeschoss";
        }
        return "Ebene " + e;
    }

    private static String schutz(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String uhrzeit(long t) {
        Calendar c = Calendar.getInstance(Locale.GERMANY);
        c.setTimeInMillis(t);
        return c.get(Calendar.HOUR_OF_DAY) + ":" + String.format(Locale.GERMANY, "%02d", c.get(Calendar.MINUTE));
    }

    static String datumKurz(long t) {
        Calendar c = Calendar.getInstance(Locale.GERMANY);
        c.setTimeInMillis(t);
        return WT[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH) + ". " + MONATE[c.get(Calendar.MONTH)];
    }

    /** Markiert die Gebäude auf dem Plan (Ring + rotes Schild). */
    static Bitmap markieren(Bitmap plan, Map<String, List<String>> proHaus) {
        Bitmap b = plan.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        float B = b.getWidth(), H = b.getHeight();
        int rot = Color.parseColor("#E24B4A");
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(30);
        text.setFakeBoldText(true);
        text.setTextAlign(Paint.Align.CENTER);
        for (Map.Entry<String, List<String>> e : proHaus.entrySet()) {
            float[] f = POS.get(e.getKey());
            if (f == null) continue;
            float x = f[0] * B, y = f[1] * H;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(56, 226, 75, 74));
            c.drawCircle(x, y, 46, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            p.setColor(rot);
            c.drawCircle(x, y, 46, p);
            String label = String.join(" · ", e.getValue());
            float breite = Math.min(520, text.measureText(label) + 34);
            float sx = Math.max(6, Math.min(B - breite - 6, x - breite / 2));
            float sy = y - 46 - 52 < 4 ? y + 52 : y - 46 - 52;
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(sx, sy, sx + breite, sy + 46), 23, 23, p);
            c.drawText(label, sx + breite / 2, sy + 33, text);
        }
        return b;
    }

    /** Baut die Seite mit Karten (Termine) und markiertem Plan. */
    static String html(List<Termin> termine, String titel, Bitmap plan, String planerLink) {
        Map<String, List<String>> proHaus = new LinkedHashMap<>();
        StringBuilder karten = new StringBuilder();
        for (int i = 0; i < termine.size(); i++) {
            Termin t = termine.get(i);
            String[] kurs = Konfig.kurs(t.titel);
            String[] typ = Konfig.typ(t.typ);
            String nummer = termine.size() > 1 ? String.valueOf(i + 1) : null;
            String[] r = raumAufteilen(t.raum);
            String ort = schutz(t.raum);
            String einr = "";
            if (r != null) {
                ort = "Haus " + schutz(r[1]) + " · Raum " + schutz(r[2] + "." + r[3]) + " · " + etageText(r[2]);
                if (!"2".equals(r[0])) {
                    int ci = r[0].matches("\\d") ? Integer.parseInt(r[0]) : 0;
                    ort += " · Campus " + (ci > 0 && ci < CAMPUS.length ? CAMPUS[ci] : r[0]) + " (nicht auf diesem Plan)";
                } else {
                    if (!POS.containsKey(r[1])) ort += " · nicht auf dem Plan";
                    List<String> l = proHaus.get(r[1]);
                    if (l == null) { l = new ArrayList<>(); proHaus.put(r[1], l); }
                    l.add(nummer != null ? nummer : "Raum " + r[2] + "." + r[3]);
                    String[] e = EIN.get(r[1]);
                    if (e != null) einr = schutz(String.join(" · ", e));
                }
            }
            karten.append("<div class=\"karte\" style=\"background:").append(kurs[1]).append("\">")
                    .append("<div class=\"oben\">").append(nummer != null ? "<span class=\"nr\">" + nummer + "</span>" : "")
                    .append(schutz(typ[0])).append(" · ").append(uhrzeit(t.start)).append("–").append(uhrzeit(t.ende)).append("</div>")
                    .append("<div class=\"titel\">").append(schutz(t.titel)).append("</div>")
                    .append("<div class=\"ort\">").append(ort).append("</div>")
                    .append(einr.isEmpty() ? "" : "<div class=\"einr\">" + einr + "</div>")
                    .append("</div>");
        }
        Bitmap markiert = markieren(plan, proHaus);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        markiert.compress(Bitmap.CompressFormat.PNG, 100, bos);
        String bild = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=6\">"
                + "<style>:root{color-scheme:light dark}body{font-family:sans-serif;margin:0;padding:16px;background:#FFFFFF;color:#2C2C2A}"
                + "@media(prefers-color-scheme:dark){body{background:#1C1C1E;color:#F1EFE8}.plan{border-color:#3A3A3C}}"
                + "h1{font-size:22px;margin:4px 0 12px}.karte{border-radius:14px;padding:12px 14px;color:#fff;margin-bottom:10px}"
                + ".oben{font-size:13px;opacity:.9;font-weight:600}.nr{display:inline-block;background:#fff;color:#E24B4A;border-radius:10px;min-width:20px;text-align:center;margin-right:6px}"
                + ".titel{font-size:18px;font-weight:700;margin:3px 0}.ort{font-size:15px;font-weight:600}.einr{font-size:13px;opacity:.85;margin-top:4px}"
                + ".plan{width:100%;border-radius:14px;border:1px solid #D3D1C7;background:#fff;margin-top:6px}"
                + "a.knopf{display:block;text-align:center;margin:14px 0 4px;padding:12px;border-radius:12px;background:#185FA5;color:#fff;text-decoration:none;font-weight:600}"
                + ".hinweis{font-size:12px;color:#888780;text-align:center;margin-top:8px}.leer{color:#888780}</style></head><body>"
                + "<h1>" + schutz(titel) + "</h1>"
                + (karten.length() > 0 ? karten.toString() : "<p class=\"leer\">Keine Termine</p>")
                + "<img class=\"plan\" src=\"data:image/png;base64," + bild + "\">"
                + "<p class=\"hinweis\">Zum Vergrößern mit zwei Fingern zoomen.</p>"
                + (planerLink != null && !planerLink.isEmpty() ? "<a class=\"knopf\" href=\"" + schutz(planerLink) + "\">Im Planer öffnen</a>" : "")
                + "</body></html>";
    }
}
