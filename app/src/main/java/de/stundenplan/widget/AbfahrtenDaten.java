package de.stundenplan.widget;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Abfahrten ab Golm über die freie VBB-Schnittstelle (v6.vbb.transport.rest, ohne Anmeldung). */
public final class AbfahrtenDaten {
    private AbfahrtenDaten() {}

    static final String[][] HALTESTELLEN = {{"900220010", "Golm Bhf"}, {"900220365", "Science Park/Uni"}};
    private static final String CACHE = "abfahrten.json";
    /** So alt dürfen die Daten werden, bevor neu geladen wird (der Minuten-Wecker zeichnet dazwischen nur neu) */
    static final long MAX_ALTER = 5 * 60_000L;

    static final class Abfahrt {
        String id, linie, produkt, richtung, gleis, haltestelle;
        long zeit, geplant;
        Integer verspaetung; // Minuten, null = keine Echtzeit
        boolean ausfall;

        boolean istZug() {
            return "regional".equals(produkt) || "suburban".equals(produkt) || "express".equals(produkt);
        }
    }

    private static long datum(String s) {
        if (s == null || s.isEmpty() || "null".equals(s)) return 0;
        try {
            return OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    private static List<Abfahrt> ladenVon(String id, String name) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL("https://v6.vbb.transport.rest/stops/" + id
                    + "/departures?duration=90&results=25&remarks=false").openConnection();
            con.setConnectTimeout(10_000);
            con.setReadTimeout(12_000);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("User-Agent", "Stundenplan-Widget (Android)");
            if (con.getResponseCode() >= 400) return null;
            String text;
            try (InputStream in = con.getInputStream()) {
                text = Daten.lesen(in);
            }
            JSONArray arr = new JSONObject(text).getJSONArray("departures");
            List<Abfahrt> liste = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Abfahrt a = new Abfahrt();
                a.id = o.optString("tripId", "");
                a.geplant = datum(o.optString("plannedWhen", null));
                a.zeit = datum(o.optString("when", null));
                if (a.geplant == 0) a.geplant = a.zeit;
                if (a.zeit == 0) a.zeit = a.geplant;
                if (a.zeit == 0) continue;
                a.verspaetung = o.isNull("delay") ? null : o.optInt("delay") / 60;
                JSONObject linie = o.optJSONObject("line");
                a.linie = linie != null ? linie.optString("name", "?") : "?";
                a.produkt = linie != null ? linie.optString("product", "bus") : "bus";
                a.richtung = o.optString("direction", "");
                a.gleis = o.isNull("platform") ? (o.isNull("plannedPlatform") ? null : o.optString("plannedPlatform")) : o.optString("platform");
                a.haltestelle = name;
                a.ausfall = o.optBoolean("cancelled", false);
                liste.add(a);
            }
            return liste;
        } catch (Exception e) {
            return null;
        }
    }

    /** Beide Haltestellen zusammen, nach Zeit sortiert; ohne Netz der letzte Stand. null = gar nichts da. */
    static List<Abfahrt> laden(Context ctx, boolean netz) {
        if (netz) {
            List<Abfahrt> alle = new ArrayList<>();
            boolean geladen = false;
            for (String[] h : HALTESTELLEN) {
                List<Abfahrt> teil = ladenVon(h[0], h[1]);
                if (teil != null) { geladen = true; alle.addAll(teil); }
            }
            if (geladen) {
                alle.sort((a, b) -> Long.compare(a.zeit, b.zeit));
                // Dieselbe Fahrt hält oft an beiden Haltestellen – nur die frühere zeigen
                Set<String> gesehen = new HashSet<>();
                List<Abfahrt> ergebnis = new ArrayList<>();
                for (Abfahrt a : alle) if (a.id.isEmpty() || gesehen.add(a.id)) ergebnis.add(a);
                speichern(ctx, ergebnis);
                return ergebnis;
            }
        }
        return ausCache(ctx);
    }

    static long alter(Context ctx) {
        File f = new File(ctx.getCacheDir(), CACHE);
        return f.exists() ? System.currentTimeMillis() - f.lastModified() : Long.MAX_VALUE;
    }

    private static void speichern(Context ctx, List<Abfahrt> liste) {
        try {
            JSONArray arr = new JSONArray();
            for (Abfahrt a : liste) {
                JSONObject o = new JSONObject();
                o.put("id", a.id); o.put("linie", a.linie); o.put("produkt", a.produkt);
                o.put("richtung", a.richtung); o.put("gleis", a.gleis == null ? "" : a.gleis);
                o.put("haltestelle", a.haltestelle); o.put("zeit", a.zeit); o.put("geplant", a.geplant);
                o.put("verspaetung", a.verspaetung == null ? -999 : a.verspaetung);
                o.put("ausfall", a.ausfall);
                arr.put(o);
            }
            try (OutputStream out = new FileOutputStream(new File(ctx.getCacheDir(), CACHE))) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static List<Abfahrt> ausCache(Context ctx) {
        File f = new File(ctx.getCacheDir(), CACHE);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            JSONArray arr = new JSONArray(Daten.lesen(in));
            List<Abfahrt> liste = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Abfahrt a = new Abfahrt();
                a.id = o.optString("id"); a.linie = o.optString("linie"); a.produkt = o.optString("produkt");
                a.richtung = o.optString("richtung"); a.gleis = o.optString("gleis");
                if (a.gleis.isEmpty()) a.gleis = null;
                a.haltestelle = o.optString("haltestelle");
                a.zeit = o.optLong("zeit"); a.geplant = o.optLong("geplant");
                int v = o.optInt("verspaetung", -999);
                a.verspaetung = v == -999 ? null : v;
                a.ausfall = o.optBoolean("ausfall");
                liste.add(a);
            }
            return liste;
        } catch (Exception e) {
            return null;
        }
    }

    /** "Potsdam, Charlottenhof Bhf" → "Charlottenhof Bhf" */
    static String kurzRichtung(String r) {
        String s = r == null ? "" : r;
        for (String v : new String[]{"Potsdam, ", "S+U ", "S "}) if (s.startsWith(v)) s = s.substring(v.length());
        return s.replace(" (Berlin)", "");
    }

    static String farbe(Abfahrt a) {
        switch (a.produkt == null ? "" : a.produkt) {
            case "regional": case "express": return "#E2001A";
            case "suburban": return "#008D4F";
            case "tram": return "#BE1414";
            default: return "#95276E";
        }
    }
}
