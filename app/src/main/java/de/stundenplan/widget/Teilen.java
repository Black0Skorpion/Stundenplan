package de.stundenplan.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Stundenplan teilen (gleiches Format wie die iOS-App):
 * QR-Code/Link stundenplan://teilen?d=<base64url(roh-DEFLATE(JSON))> mit Name und PULS-Auswahl,
 * oder .ics-Datei mit X-WR-CALNAME und X-STUNDENPLAN-FREIGABE. Dazu die Profile (Pläne anderer).
 */
public final class Teilen {
    private Teilen() {}

    // ───────────── Name ─────────────

    static String name(Context ctx) {
        return Erinnerungen.prefs(ctx).getString("name", "").trim();
    }

    static void setzeName(Context ctx, String n) {
        Erinnerungen.prefs(ctx).edit().putString("name", n == null ? "" : n.trim()).apply();
    }

    // ───────────── Freigabe ─────────────

    public static final class Freigabe {
        public String name = "";
        public String semester;
        public List<PulsDienst.Kurs> kurse = new ArrayList<>();
        /** Ausgewählte eigene Termine */
        public List<EigeneTermine.Eintrag> eigene = new ArrayList<>();

        boolean mitPuls() { return !kurse.isEmpty(); }

        boolean leer() { return kurse.isEmpty() && eigene.isEmpty(); }

        /** Mitgeschickte eigene Termine einzeln (ohne akademisches Viertel) */
        List<Termin> eigeneEinzeln() {
            List<Termin> l = new ArrayList<>();
            for (EigeneTermine.Eintrag e : eigene) l.addAll(EigeneTermine.einzelne(e));
            return l;
        }

        String json() throws Exception {
            JSONObject o = new JSONObject().put("v", 1).put("name", name);
            if (semester != null) o.put("semester", semester);
            JSONArray l = new JSONArray();
            for (PulsDienst.Kurs k : kurse) {
                JSONObject j = new JSONObject().put("p", k.publishid).put("t", k.titel);
                if (k.gruppe != null) j.put("g", k.gruppe);
                if (k.ausgeblendet != null && !k.ausgeblendet.isEmpty()) j.put("a", new JSONArray(k.ausgeblendet));
                l.put(j);
            }
            o.put("kurse", l);
            if (!eigene.isEmpty()) {
                JSONArray e = new JSONArray();
                for (EigeneTermine.Eintrag x : eigene) {
                    JSONObject j = new JSONObject().put("t", x.titel).put("y", x.typ).put("r", x.raum)
                            .put("s", x.start / 1000).put("d", Math.max(5, (x.ende - x.start) / 60_000)).put("w", x.abstandTage);
                    if (x.bis > 0) j.put("b", x.bis / 1000);
                    e.put(j);
                }
                o.put("e", e);
            }
            return o.toString();
        }

        static Freigabe ausJson(String s) throws Exception {
            JSONObject o = new JSONObject(s);
            Freigabe f = new Freigabe();
            f.name = o.optString("name", "");
            f.semester = o.isNull("semester") ? null : o.optString("semester", null);
            JSONArray l = o.optJSONArray("kurse");
            if (l != null) for (int i = 0; i < l.length(); i++) {
                JSONObject j = l.getJSONObject(i);
                PulsDienst.Kurs k = new PulsDienst.Kurs();
                k.publishid = j.getString("p");
                k.titel = j.optString("t", "");
                k.gruppe = j.isNull("g") ? null : j.optString("g", null);
                k.ausgeblendet = PulsDienst.strings(j.optJSONArray("a"));
                f.kurse.add(k);
            }
            JSONArray e = o.optJSONArray("e");
            if (e != null) for (int i = 0; i < e.length(); i++) {
                JSONObject j = e.getJSONObject(i);
                EigeneTermine.Eintrag x = new EigeneTermine.Eintrag();
                x.titel = j.optString("t");
                x.typ = j.optString("y", "Termin");
                x.raum = j.optString("r");
                x.start = j.getLong("s") * 1000;
                x.ende = x.start + j.optLong("d", 90) * 60_000;
                x.abstandTage = j.optInt("w", 0);
                x.bis = j.has("b") && !j.isNull("b") ? j.getLong("b") * 1000 : 0;
                f.eigene.add(x);
            }
            return f;
        }

        /** Kurzer Code (base64url ohne Auffüllung) */
        String code() {
            try {
                byte[] roh = json().getBytes(StandardCharsets.UTF_8);
                Deflater d = new Deflater(9, true);
                d.setInput(roh);
                d.finish();
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] p = new byte[4096];
                while (!d.finished()) b.write(p, 0, d.deflate(p));
                d.end();
                return Base64.encodeToString(b.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            } catch (Exception e) {
                return null;
            }
        }

        static Freigabe ausCode(String code) {
            try {
                byte[] d = Base64.decode(code.trim(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                Inflater inf = new Inflater(true);
                // Roh-DEFLATE braucht beim Entpacken ein Füllbyte am Ende
                byte[] mit = new byte[d.length + 1];
                System.arraycopy(d, 0, mit, 0, d.length);
                inf.setInput(mit);
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] p = new byte[4096];
                int n;
                while (!inf.finished() && (n = inf.inflate(p)) > 0) b.write(p, 0, n);
                inf.end();
                return ausJson(b.toString("UTF-8"));
            } catch (Exception e) {
                return null;
            }
        }

        String link() {
            String c = code();
            return c == null ? null : "stundenplan://teilen?d=" + c;
        }
    }

    /** Der eigene Plan zum Teilen (PULS-Auswahl nur, wenn der Plan daraus kommt) */
    static Freigabe eigeneFreigabe(Context ctx) {
        return eigeneFreigabe(ctx, new java.util.HashSet<>());
    }

    /** … mit den ausgewählten eigenen Terminen (IDs) */
    static Freigabe eigeneFreigabe(Context ctx, java.util.Set<String> mit) {
        Freigabe f = new Freigabe();
        f.name = name(ctx);
        for (EigeneTermine.Eintrag e : EigeneTermine.alle(ctx)) if (mit.contains(e.id)) f.eigene.add(e);
        PulsDienst.Auswahl a = PulsDienst.auswahl(ctx);
        if (a.aktiv) {
            f.semester = a.semester;
            f.kurse = a.kurse;
        }
        return f;
    }

    // ───────────── Termine als JSON ─────────────

    static JSONArray termineJson(List<Termin> t) {
        JSONArray l = new JSONArray();
        try {
            for (Termin x : t) l.put(new JSONObject().put("s", x.start).put("e", x.ende).put("t", x.titel).put("y", x.typ).put("r", x.raum));
        } catch (Exception ignored) {}
        return l;
    }

    static List<Termin> termineAus(String s) {
        List<Termin> aus = new ArrayList<>();
        try {
            JSONArray l = new JSONArray(s);
            for (int i = 0; i < l.length(); i++) {
                JSONObject o = l.getJSONObject(i);
                aus.add(new Termin(o.getLong("s"), o.getLong("e"), o.optString("t"), o.optString("y"), o.optString("r")));
            }
        } catch (Exception ignored) {}
        return aus;
    }

    // ───────────── ICS ─────────────

    private static String utc(long t) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(t));
    }

    private static String maskiert(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }

    private static String gefaltet(String zeile) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        boolean erste = true;
        while (i < zeile.length()) {
            int n = erste ? 73 : 72;
            int ende = Math.min(zeile.length(), i + n);
            if (!erste) sb.append("\r\n ");
            sb.append(zeile, i, ende);
            i = ende;
            erste = false;
        }
        return sb.toString();
    }

    static String ics(List<Termin> termine, String name, Freigabe freigabe) {
        List<String> z = new ArrayList<>();
        z.add("BEGIN:VCALENDAR");
        z.add("VERSION:2.0");
        z.add("PRODID:-//Stundenplan Uni Potsdam//DE");
        z.add("CALSCALE:GREGORIAN");
        z.add("X-WR-CALNAME:" + maskiert(name == null || name.isEmpty() ? "Stundenplan" : "Stundenplan von " + name));
        String code = freigabe != null && !freigabe.leer() ? freigabe.code() : null;
        if (code != null) z.add("X-STUNDENPLAN-FREIGABE:" + code);
        String jetzt = utc(System.currentTimeMillis());
        int i = 0;
        for (Termin t : termine) {
            z.add("BEGIN:VEVENT");
            z.add("UID:" + (t.start / 1000) + "-" + (i++) + "@stundenplan");
            z.add("DTSTAMP:" + jetzt);
            z.add("DTSTART:" + utc(t.start));
            z.add("DTEND:" + utc(t.ende));
            z.add("SUMMARY:" + maskiert(t.titel));
            z.add("DESCRIPTION:" + maskiert(t.typ));
            z.add("LOCATION:" + maskiert(t.raum));
            z.add("END:VEVENT");
        }
        z.add("END:VCALENDAR");
        StringBuilder sb = new StringBuilder();
        for (String s : z) sb.append(gefaltet(s)).append("\r\n");
        return sb.toString();
    }

    /** Name und Freigabe aus dem Kopf einer .ics-Datei: [0] = Name (oder null), Freigabe über kopfFreigabe */
    static String kopfName(String inhalt) {
        for (String zeile : entfaltet(inhalt)) {
            if (zeile.equals("BEGIN:VEVENT")) break;
            if (zeile.startsWith("X-WR-CALNAME:")) {
                String n = zeile.substring(13).replace("\\,", ",").replace("\\;", ";");
                return n.startsWith("Stundenplan von ") ? n.substring(16) : n;
            }
        }
        return null;
    }

    static Freigabe kopfFreigabe(String inhalt) {
        for (String zeile : entfaltet(inhalt)) {
            if (zeile.equals("BEGIN:VEVENT")) break;
            if (zeile.startsWith("X-STUNDENPLAN-FREIGABE:")) return Freigabe.ausCode(zeile.substring(23));
        }
        return null;
    }

    private static String[] entfaltet(String s) {
        return s.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split("\n");
    }

    /** Eigene Termine zum Teilen: wie gespeichert (ohne akademisches Viertel), mit geändertem Raum, ohne Ausfälle */
    static List<Termin> eigeneZumTeilen(Context ctx) {
        return Daten.ladenRohMitAenderungen(ctx);
    }

    // ───────────── QR-Code ─────────────

    static Bitmap qr(String text, int groesse) {
        try {
            Map<EncodeHintType, Object> h = new EnumMap<>(EncodeHintType.class);
            h.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            h.put(EncodeHintType.MARGIN, 2);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, groesse, groesse, h);
            int w = m.getWidth(), hh = m.getHeight();
            int[] px = new int[w * hh];
            for (int y = 0; y < hh; y++) for (int x = 0; x < w; x++) px[y * w + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            return Bitmap.createBitmap(px, w, hh, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            return null;
        }
    }

    // ───────────── Profile ─────────────

    public static final class Profil {
        public String id = UUID.randomUUID().toString();
        public String name = "";
        public List<Termin> termine = new ArrayList<>();
        public String freigabe;   // Code der PULS-Auswahl (zum Aktualisieren)
        public long stand = System.currentTimeMillis();
        /** Mitgeschickte eigene Termine (genaue Zeiten, ohne akademisches Viertel) */
        public List<Termin> eigene = new ArrayList<>();

        Freigabe freigabe() { return freigabe == null ? null : Freigabe.ausCode(freigabe); }

        /** Mit akademischem Viertel, sortiert */
        List<Termin> angezeigt() {
            List<Termin> l = Daten.aufbereiten(termine);
            l.addAll(eigene);
            l.sort((a, b) -> Long.compare(a.start, b.start));
            return l;
        }
    }

    static synchronized List<Profil> profile(Context ctx) {
        List<Profil> aus = new ArrayList<>();
        String s = PulsDienst.lesenText(ctx, "profile.json");
        if (s == null) return aus;
        try {
            JSONArray l = new JSONArray(s);
            for (int i = 0; i < l.length(); i++) {
                JSONObject o = l.getJSONObject(i);
                Profil p = new Profil();
                p.id = o.optString("id", p.id);
                p.name = o.optString("name");
                p.freigabe = o.isNull("freigabe") ? null : o.optString("freigabe", null);
                p.stand = o.optLong("stand", 0);
                p.termine = termineAus(o.optJSONArray("termine") == null ? "[]" : o.getJSONArray("termine").toString());
                p.eigene = termineAus(o.optJSONArray("eigene") == null ? "[]" : o.getJSONArray("eigene").toString());
                aus.add(p);
            }
        } catch (Exception ignored) {}
        return aus;
    }

    static synchronized void speichern(Context ctx, List<Profil> profile) {
        JSONArray l = new JSONArray();
        try {
            for (Profil p : profile) {
                JSONObject o = new JSONObject().put("id", p.id).put("name", p.name).put("stand", p.stand).put("termine", termineJson(p.termine))
                        .put("eigene", termineJson(p.eigene));
                if (p.freigabe != null) o.put("freigabe", p.freigabe);
                l.put(o);
            }
        } catch (Exception ignored) {}
        PulsDienst.schreibenText(ctx, "profile.json", l.toString());
    }

    static Profil profil(Context ctx, String id) {
        if (id == null) return null;
        for (Profil p : profile(ctx)) if (p.id.equals(id)) return p;
        return null;
    }

    /** Termine einer geteilten PULS-Auswahl laden (nur im Hintergrund). null = PULS nicht erreichbar. */
    static List<Termin> termineLaden(Context ctx, Freigabe f) {
        List<Termin> alle = new ArrayList<>();
        int geladen = 0;
        for (PulsDienst.Kurs k : f.kurse) {
            Puls.Veranstaltung v = PulsDienst.laden(ctx, k.publishid);
            if (v == null) v = PulsDienst.cache(ctx).get(k.publishid);
            if (v == null) continue;
            geladen++;
            for (Puls.EinzelTermin e : Puls.termine(v, k.gruppe, k.ausgeblendetSet())) {
                alle.add(new Termin(e.start, e.ende, k.titel, e.art, e.raum));
            }
        }
        if (geladen == 0 && !f.kurse.isEmpty()) return null;
        Collections.sort(alle, (a, b) -> Long.compare(a.start, b.start));
        return alle;
    }
}
