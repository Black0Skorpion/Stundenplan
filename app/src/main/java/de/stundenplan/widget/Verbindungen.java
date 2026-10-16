package de.stundenplan.widget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Verbindungen zwischen zwei Veranstaltungen an verschiedenen Campus (VBB-Verbindungssuche, ohne Anmeldung).
 * Start und Ziel sind die Häuser selbst – Fußwege zur und von der Haltestelle sind schon enthalten.
 */
public final class Verbindungen {
    private Verbindungen() {}

    static final class Abschnitt {
        boolean zuFuss;
        String linie, produkt, von, nach, gleis;
        long ab, an;
    }

    static final class Verbindung {
        final List<Abschnitt> abschnitte = new ArrayList<>();

        long ab() { return abschnitte.get(0).ab; }
        long an() { return abschnitte.get(abschnitte.size() - 1).an; }

        String linien() {
            StringBuilder sb = new StringBuilder();
            for (Abschnitt a : abschnitte) {
                if (a.linie == null) continue;
                if (sb.length() > 0) sb.append(" → ");
                sb.append(a.linie);
            }
            return sb.length() == 0 ? "zu Fuß" : sb.toString();
        }
    }

    private static long datum(JSONObject o, String feld, String ersatz) {
        String s = o.isNull(feld) ? (o.isNull(ersatz) ? null : o.optString(ersatz)) : o.optString(feld);
        if (s == null || s.isEmpty()) return 0;
        try {
            return OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String ortName(String raum) {
        String c = Campus.ausRaum(raum);
        String[] r = Lageplan.raumAufteilen(raum);
        return "Uni Potsdam " + (c != null ? Campus.name(c) : "") + (r != null ? " Haus " + r[1] : "");
    }

    /** Bis zu drei Verbindungen ab Ende von a zum Raum von b. null = nicht erreichbar. Nicht im Hauptthread aufrufen. */
    static List<Verbindung> suchen(Termin a, Termin b) {
        double[] start = Orte.koordinateOderCampus(a.raum), ziel = Orte.koordinateOderCampus(b.raum);
        if (start == null || ziel == null) return null;
        try {
            String ab = OffsetDateTime.ofInstant(Instant.ofEpochMilli(Math.max(a.ende, System.currentTimeMillis())),
                    ZoneId.of("Europe/Berlin")).toString();
            String q = "from.latitude=" + start[0] + "&from.longitude=" + start[1]
                    + "&from.address=" + URLEncoder.encode(ortName(a.raum), "UTF-8")
                    + "&to.latitude=" + ziel[0] + "&to.longitude=" + ziel[1]
                    + "&to.address=" + URLEncoder.encode(ortName(b.raum), "UTF-8")
                    + "&departure=" + URLEncoder.encode(ab, "UTF-8")
                    + "&results=3&stopovers=false&remarks=false&polylines=false&tickets=false";
            for (String quelle : AbfahrtenDaten.QUELLEN) {
                for (int versuch = 0; versuch < 2; versuch++) {
                    List<Verbindung> l = holen(quelle + "/journeys?" + q);
                    if (l != null) return l;
                    try { Thread.sleep(1500); } catch (InterruptedException e) { return null; }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static List<Verbindung> holen(String adresse) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(adresse).openConnection();
            con.setConnectTimeout(8_000);
            con.setReadTimeout(15_000);
            con.setRequestProperty("Accept", "application/json");
            if (con.getResponseCode() >= 400) return null;
            String text;
            try (InputStream in = con.getInputStream()) {
                text = Daten.lesen(in);
            }
            JSONArray reisen = new JSONObject(text).getJSONArray("journeys");
            List<Verbindung> liste = new ArrayList<>();
            for (int i = 0; i < reisen.length(); i++) {
                JSONArray legs = reisen.getJSONObject(i).getJSONArray("legs");
                Verbindung v = new Verbindung();
                for (int j = 0; j < legs.length(); j++) {
                    JSONObject l = legs.getJSONObject(j);
                    Abschnitt a = new Abschnitt();
                    a.zuFuss = l.optBoolean("walking", false);
                    a.ab = datum(l, "departure", "plannedDeparture");
                    a.an = datum(l, "arrival", "plannedArrival");
                    if (a.ab == 0 || a.an == 0) continue;
                    JSONObject linie = l.optJSONObject("line");
                    if (linie != null) {
                        a.linie = linie.optString("name", null);
                        a.produkt = linie.optString("product", null);
                    }
                    JSONObject o = l.optJSONObject("origin"), d = l.optJSONObject("destination");
                    a.von = o != null && !o.isNull("name") ? o.optString("name") : null;
                    a.nach = d != null && !d.isNull("name") ? d.optString("name") : null;
                    a.gleis = l.isNull("departurePlatform") ? null : l.optString("departurePlatform");
                    v.abschnitte.add(a);
                }
                if (!v.abschnitte.isEmpty()) liste.add(v);
            }
            return liste;
        } catch (Exception e) {
            return null;
        }
    }

    static String text(Abschnitt a) {
        long min = Math.max(1, (a.an - a.ab) / 60_000L);
        if (a.zuFuss) {
            return min + " Min zu Fuß" + (a.nach != null ? " zu " + AbfahrtenDaten.kurzRichtung(a.nach) : " zum Haus");
        }
        return Lageplan.uhrzeitText(a.ab) + " " + (a.linie != null ? a.linie : "?") + " ab "
                + AbfahrtenDaten.kurzRichtung(a.von) + (a.gleis != null ? " (Gl. " + a.gleis + ")" : "")
                + " → " + Lageplan.uhrzeitText(a.an) + " " + (a.nach != null ? AbfahrtenDaten.kurzRichtung(a.nach) : "Ziel");
    }
}
