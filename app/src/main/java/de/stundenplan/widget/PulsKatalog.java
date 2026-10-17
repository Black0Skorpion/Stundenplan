package de.stundenplan.widget;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.Inflater;

/**
 * Suchkatalog aller Veranstaltungen eines Semesters (wöchentlich auf GitHub aus dem öffentlichen
 * PULS-Verzeichnis erstellt, werkzeuge/puls_katalog.py). Wird einmal heruntergeladen, gesucht wird offline.
 */
public final class PulsKatalog {
    private PulsKatalog() {}

    static final String QUELLE = "https://github.com/Black0Skorpion/Stundenplan/releases/download/puls-katalog/";

    public static final class Eintrag {
        public final String publishid, titel, kontext, suchtext;

        Eintrag(String publishid, String titel, String kontext) {
            this.publishid = publishid;
            this.titel = titel;
            this.kontext = kontext;
            this.suchtext = normal(titel + " " + kontext);
        }
    }

    private static String geladenesSemester;
    private static List<Eintrag> eintraege = new ArrayList<>();

    static String normal(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.GERMANY);
    }

    private static File datei(Context ctx, String sem) { return new File(ctx.getFilesDir(), "puls-katalog-" + sem + ".deflate"); }

    public static synchronized boolean vorhanden(String sem) {
        return sem.equals(geladenesSemester) && !eintraege.isEmpty();
    }

    /**
     * Katalog bereitstellen (aus der Datei, bei Bedarf herunterladen: fehlt oder älter als 7 Tage).
     * Nur im Hintergrund aufrufen. Gibt eine Fehlermeldung zurück oder null.
     */
    public static String bereitstellen(Context ctx, String sem, boolean erzwingen) {
        File f = datei(ctx, sem);
        synchronized (PulsKatalog.class) {
            if (!sem.equals(geladenesSemester)) { eintraege = new ArrayList<>(); geladenesSemester = null; }
        }
        if (!vorhanden(sem) && f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                lesen(lesenBytes(in), sem);
            } catch (Exception ignored) {}
        }
        boolean veraltet = !f.exists() || System.currentTimeMillis() - f.lastModified() > 7 * 86400_000L;
        if (!erzwingen && vorhanden(sem) && !veraltet) return null;
        if (!erzwingen && vorhanden(sem) && !PulsDienst.imWLAN(ctx)) return null;
        byte[] d = holen(QUELLE + "puls-" + sem + ".deflate");
        if (d == null) return vorhanden(sem) ? null : "Der Suchkatalog für " + Puls.semesterName(sem) + " ist noch nicht verfügbar.";
        if (lesen(d, sem)) {
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(d); } catch (Exception ignored) {}
            return null;
        }
        return "Der Suchkatalog ist beschädigt.";
    }

    private static byte[] lesenBytes(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] p = new byte[16384];
        int n;
        while ((n = in.read(p)) > 0) b.write(p, 0, n);
        return b.toByteArray();
    }

    private static byte[] holen(String adresse) {
        try {
            // GitHub leitet auf den Datei-Server weiter
            String url = adresse;
            for (int i = 0; i < 5; i++) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                int code = c.getResponseCode();
                if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                    url = c.getHeaderField("Location");
                    c.disconnect();
                    continue;
                }
                if (code >= 400) return null;
                try (InputStream in = c.getInputStream()) {
                    return lesenBytes(in);
                } finally {
                    c.disconnect();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean lesen(byte[] komprimiert, String sem) {
        try {
            Inflater inf = new Inflater(true);
            byte[] mit = new byte[komprimiert.length + 1];
            System.arraycopy(komprimiert, 0, mit, 0, komprimiert.length);
            inf.setInput(mit);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] p = new byte[65536];
            int n;
            while (!inf.finished() && (n = inf.inflate(p)) > 0) b.write(p, 0, n);
            inf.end();
            JSONObject o = new JSONObject(b.toString("UTF-8"));
            JSONArray l = o.getJSONArray("veranstaltungen");
            List<Eintrag> neu = new ArrayList<>(l.length());
            for (int i = 0; i < l.length(); i++) {
                JSONArray z = l.getJSONArray(i);
                if (z.length() < 2) continue;
                StringBuilder kontext = new StringBuilder();
                if (z.length() > 2) {
                    JSONArray k = z.optJSONArray(2);
                    if (k != null) for (int j = 0; j < k.length(); j++) kontext.append(j > 0 ? " · " : "").append(k.optString(j));
                }
                neu.add(new Eintrag(z.optString(0), z.optString(1), kontext.toString()));
            }
            synchronized (PulsKatalog.class) {
                eintraege = neu;
                geladenesSemester = sem;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Alle Wörter der Suche müssen vorkommen (Titel oder Modul) */
    public static synchronized List<Eintrag> suchen(String anfrage, int maximal) {
        String[] woerter = normal(anfrage).trim().split("\\s+");
        List<Eintrag> treffer = new ArrayList<>();
        if (woerter.length == 0 || woerter[0].isEmpty()) return treffer;
        for (Eintrag e : eintraege) {
            boolean alle = true;
            for (String w : woerter) if (!e.suchtext.contains(w)) { alle = false; break; }
            if (alle) {
                treffer.add(e);
                if (treffer.size() >= maximal * 3) break;
            }
        }
        final String erstes = woerter[0];
        Collections.sort(treffer, (a, b) -> {
            boolean x = normal(a.titel).contains(erstes), y = normal(b.titel).contains(erstes);
            if (x != y) return x ? -1 : 1;
            return Integer.compare(a.titel.length(), b.titel.length());
        });
        return treffer.size() > maximal ? new ArrayList<>(treffer.subList(0, maximal)) : treffer;
    }
}
