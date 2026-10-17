package de.stundenplan.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Eigene Modulwahl aus PULS: gewählte Veranstaltungen/Gruppen, Zwischenspeicher, täglicher Abgleich
 * (wie ios-app/App/PulsDienst.swift). Netzwerk-Methoden nie im UI-Thread aufrufen.
 */
public final class PulsDienst {
    private PulsDienst() {}

    static final String KANAL = "puls";
    static final int JOB_ID = 4711;

    // ───────────── Datenmodell ─────────────

    public static final class Kurs {
        public String publishid, titel, gruppe;
        public List<String> ausgeblendet = new ArrayList<>();

        Set<String> ausgeblendetSet() { return new HashSet<>(ausgeblendet); }
    }

    public static final class Auswahl {
        public String semester = Puls.semesterCode(System.currentTimeMillis());
        public List<Kurs> kurse = new ArrayList<>();
        public boolean aktiv = false, nurWLAN = true, mitteilungen = true;

        public Kurs kurs(String publishid) {
            for (Kurs k : kurse) if (k.publishid.equals(publishid)) return k;
            return null;
        }
    }

    public static final class Stand {
        public long letzterAbgleich, letzterVersuch;
        public List<String> aenderungen = new ArrayList<>();
        public String fehler;
    }

    // ───────────── Dateien ─────────────

    static File datei(Context ctx, String name) { return new File(ctx.getFilesDir(), name); }

    static String lesenText(Context ctx, String name) {
        File f = datei(ctx, name);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return Daten.lesen(in);
        } catch (Exception e) {
            return null;
        }
    }

    static void schreibenText(Context ctx, String name, String text) {
        File f = datei(ctx, name);
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return;
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }

    // ───────────── Auswahl ─────────────

    public static synchronized Auswahl auswahl(Context ctx) {
        Auswahl a = new Auswahl();
        String s = lesenText(ctx, "puls-auswahl.json");
        if (s == null) return a;
        try {
            JSONObject o = new JSONObject(s);
            a.semester = o.optString("semester", a.semester);
            a.aktiv = o.optBoolean("aktiv", false);
            a.nurWLAN = o.optBoolean("nurWLAN", true);
            a.mitteilungen = o.optBoolean("mitteilungen", true);
            JSONArray l = o.optJSONArray("kurse");
            if (l != null) for (int i = 0; i < l.length(); i++) {
                JSONObject k = l.getJSONObject(i);
                Kurs kurs = new Kurs();
                kurs.publishid = k.getString("publishid");
                kurs.titel = k.optString("titel", "");
                kurs.gruppe = k.isNull("gruppe") ? null : k.optString("gruppe", null);
                kurs.ausgeblendet = strings(k.optJSONArray("ausgeblendet"));
                a.kurse.add(kurs);
            }
        } catch (Exception ignored) {}
        return a;
    }

    public static synchronized void speichern(Context ctx, Auswahl a) {
        try {
            JSONObject o = new JSONObject();
            o.put("semester", a.semester).put("aktiv", a.aktiv).put("nurWLAN", a.nurWLAN).put("mitteilungen", a.mitteilungen);
            JSONArray l = new JSONArray();
            for (Kurs k : a.kurse) {
                JSONObject j = new JSONObject().put("publishid", k.publishid).put("titel", k.titel);
                if (k.gruppe != null) j.put("gruppe", k.gruppe);
                j.put("ausgeblendet", new JSONArray(k.ausgeblendet));
                l.put(j);
            }
            o.put("kurse", l);
            schreibenText(ctx, "puls-auswahl.json", o.toString());
        } catch (Exception ignored) {}
    }

    static List<String> strings(JSONArray a) {
        List<String> l = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) l.add(a.optString(i));
        return l;
    }

    // ───────────── Stand ─────────────

    public static synchronized Stand stand(Context ctx) {
        Stand s = new Stand();
        String t = lesenText(ctx, "puls-stand.json");
        if (t == null) return s;
        try {
            JSONObject o = new JSONObject(t);
            s.letzterAbgleich = o.optLong("letzterAbgleich", 0);
            s.letzterVersuch = o.optLong("letzterVersuch", 0);
            s.aenderungen = strings(o.optJSONArray("aenderungen"));
            s.fehler = o.isNull("fehler") ? null : o.optString("fehler", null);
        } catch (Exception ignored) {}
        return s;
    }

    static synchronized void speichern(Context ctx, Stand s) {
        try {
            JSONObject o = new JSONObject().put("letzterAbgleich", s.letzterAbgleich).put("letzterVersuch", s.letzterVersuch)
                    .put("aenderungen", new JSONArray(s.aenderungen));
            if (s.fehler != null) o.put("fehler", s.fehler);
            schreibenText(ctx, "puls-stand.json", o.toString());
        } catch (Exception ignored) {}
    }

    // ───────────── Zwischenspeicher der Veranstaltungen ─────────────

    private static Map<String, Puls.Veranstaltung> cache;

    public static synchronized Map<String, Puls.Veranstaltung> cache(Context ctx) {
        if (cache != null) return cache;
        cache = new HashMap<>();
        String t = lesenText(ctx, "puls-cache.json");
        if (t != null) {
            try {
                JSONObject o = new JSONObject(t);
                for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String k = it.next();
                    cache.put(k, veranstaltungAus(o.getJSONObject(k)));
                }
            } catch (Exception ignored) {}
        }
        return cache;
    }

    private static synchronized void cacheSpeichern(Context ctx) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Puls.Veranstaltung> e : cache(ctx).entrySet()) o.put(e.getKey(), json(e.getValue()));
            schreibenText(ctx, "puls-cache.json", o.toString());
        } catch (Exception ignored) {}
    }

    static JSONObject json(Puls.Veranstaltung v) throws Exception {
        JSONObject o = new JSONObject().put("publishid", v.publishid).put("titel", v.titel).put("art", v.art).put("semester", v.semester);
        if (v.moodle != null) o.put("moodle", v.moodle);
        if (v.belegfrist != null) o.put("belegfrist", v.belegfrist);
        JSONArray gs = new JSONArray();
        for (Puls.Gruppe g : v.gruppen) {
            JSONArray rs = new JSONArray();
            for (Puls.Regel r : g.regeln) {
                JSONObject j = new JSONObject().put("art", r.art).put("tag", r.tag).put("von", r.von).put("bis", r.bis)
                        .put("rhythmus", r.rhythmus).put("raum", r.raum).put("lehrperson", r.lehrperson)
                        .put("ausfaelle", new JSONArray(r.ausfaelle)).put("weitereRaeume", new JSONArray(r.weitereRaeume));
                if (r.beginn != null) j.put("beginn", r.beginn);
                if (r.ende != null) j.put("ende", r.ende);
                if (r.bemerkung != null) j.put("bemerkung", r.bemerkung);
                rs.put(j);
            }
            gs.put(new JSONObject().put("nummer", g.nummer).put("regeln", rs));
        }
        return o.put("gruppen", gs);
    }

    static Puls.Veranstaltung veranstaltungAus(JSONObject o) throws Exception {
        Puls.Veranstaltung v = new Puls.Veranstaltung();
        v.publishid = o.getString("publishid");
        v.titel = o.optString("titel");
        v.art = o.optString("art");
        v.semester = o.optString("semester");
        v.moodle = o.isNull("moodle") ? null : o.optString("moodle", null);
        v.belegfrist = o.isNull("belegfrist") ? null : o.optString("belegfrist", null);
        JSONArray gs = o.optJSONArray("gruppen");
        if (gs != null) for (int i = 0; i < gs.length(); i++) {
            JSONObject gj = gs.getJSONObject(i);
            Puls.Gruppe g = new Puls.Gruppe();
            g.nummer = gj.optString("nummer");
            JSONArray rs = gj.optJSONArray("regeln");
            if (rs != null) for (int k = 0; k < rs.length(); k++) {
                JSONObject j = rs.getJSONObject(k);
                Puls.Regel r = new Puls.Regel();
                r.art = j.optString("art"); r.tag = j.optString("tag"); r.von = j.optString("von"); r.bis = j.optString("bis");
                r.rhythmus = j.optString("rhythmus"); r.raum = j.optString("raum"); r.lehrperson = j.optString("lehrperson");
                r.beginn = j.isNull("beginn") ? null : j.optString("beginn", null);
                r.ende = j.isNull("ende") ? null : j.optString("ende", null);
                r.bemerkung = j.isNull("bemerkung") ? null : j.optString("bemerkung", null);
                r.ausfaelle = strings(j.optJSONArray("ausfaelle"));
                r.weitereRaeume = strings(j.optJSONArray("weitereRaeume"));
                g.regeln.add(r);
            }
            v.gruppen.add(g);
        }
        return v;
    }

    // ───────────── Laden (nur im Hintergrund!) ─────────────

    static String holen(String adresse) {
        HttpURLConnection c = null;
        for (int versuch = 0; versuch < 2; versuch++) {
            try {
                c = (HttpURLConnection) new URL(adresse).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", "Stundenplan-App (github.com/Black0Skorpion/Stundenplan)");
                if (c.getResponseCode() >= 400) continue;
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] p = new byte[16384];
                    int n;
                    while ((n = in.read(p)) > 0) b.write(p, 0, n);
                    return b.toString("UTF-8");
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    /** Eine Veranstaltung frisch von PULS laden (und zwischenspeichern) */
    public static Puls.Veranstaltung laden(Context ctx, String publishid) {
        String h = holen(Puls.veranstaltungURL(publishid));
        if (h == null) return null;
        Puls.Veranstaltung v = Puls.veranstaltung(h, publishid);
        if (v == null || v.gruppen.isEmpty()) return null;
        synchronized (PulsDienst.class) {
            cache(ctx).put(publishid, v);
            cacheSpeichern(ctx);
        }
        return v;
    }

    /** Fehlende Veranstaltungen nachladen */
    public static void fehlendeLaden(Context ctx) {
        for (Kurs k : auswahl(ctx).kurse) if (cache(ctx).get(k.publishid) == null) laden(ctx, k.publishid);
    }

    /** Hinzufügen (lädt die Veranstaltung). Gibt eine Fehlermeldung zurück oder null. */
    public static String hinzufuegen(Context ctx, String publishid) {
        Auswahl a = auswahl(ctx);
        if (a.kurs(publishid) != null) return null;
        Puls.Veranstaltung v = laden(ctx, publishid);
        if (v == null) return "Die Veranstaltung konnte nicht geladen werden (keine Termine gefunden oder PULS nicht erreichbar).";
        a = auswahl(ctx);
        Kurs k = new Kurs();
        k.publishid = publishid;
        k.titel = v.titel;
        k.gruppe = v.gruppen.size() == 1 ? v.gruppen.get(0).nummer : null;
        a.kurse.add(k);
        speichern(ctx, a);
        return null;
    }

    // ───────────── Termine ─────────────

    static List<Puls.EinzelTermin> einzeltermine(Context ctx, Kurs k, String gruppe) {
        Puls.Veranstaltung v = cache(ctx).get(k.publishid);
        if (v == null) return new ArrayList<>();
        return Puls.termine(v, gruppe != null ? gruppe : k.gruppe, k.ausgeblendetSet());
    }

    /** Rohtermine (ohne akademisches Viertel) aus einer Auswahl */
    public static List<Termin> termine(Context ctx, List<Kurs> kurse) {
        List<Termin> aus = new ArrayList<>();
        for (Kurs k : kurse) for (Puls.EinzelTermin e : einzeltermine(ctx, k, null)) {
            aus.add(new Termin(e.start, e.ende, k.titel, e.art, e.raum));
        }
        Collections.sort(aus, (x, y) -> Long.compare(x.start, y.start));
        return aus;
    }

    /** Überschneidungen: [0] = durch die Gruppe selbst, [1] = durch gemeinsame Termine (Vorlesung) */
    public static List<List<String>> ueberschneidungen(Context ctx, Auswahl a, Kurs k, String gruppe) {
        List<List<String>> aus = new ArrayList<>();
        aus.add(new ArrayList<>());
        aus.add(new ArrayList<>());
        Puls.Veranstaltung v = cache(ctx).get(k.publishid);
        Puls.Gruppe g = v == null ? null : v.gruppe(gruppe);
        if (g == null) return aus;
        Set<String> weg = k.ausgeblendetSet();
        List<Puls.Regel> eigen = new ArrayList<>(), gemeinsam = new ArrayList<>();
        for (Puls.Regel r : v.eigenes(g)) if (!weg.contains(r.schluessel())) eigen.add(r);
        if (v.gruppen.size() > 1) for (Puls.Regel r : v.gemeinsam()) if (!weg.contains(r.schluessel())) gemeinsam.add(r);
        List<Puls.EinzelTermin> te = Puls.termine(eigen), tg = Puls.termine(gemeinsam);
        Set<String> n1 = new TreeSet<>(), n2 = new TreeSet<>();
        for (Kurs anderer : a.kurse) {
            if (anderer.publishid.equals(k.publishid) || anderer.gruppe == null) continue;
            List<Puls.EinzelTermin> fremd = einzeltermine(ctx, anderer, null);
            String name = Konfig.kurs(anderer.titel)[0];
            if (Puls.ueberschneiden(te, fremd)) n1.add(name);
            if (Puls.ueberschneiden(tg, fremd)) n2.add(name);
        }
        aus.get(0).addAll(n1);
        aus.get(1).addAll(n2);
        return aus;
    }

    // ───────────── Übernehmen ─────────────

    /** Auswahl als Stundenplan übernehmen (schreibt die Termine als Kalenderdatei). Gibt die Anzahl zurück. */
    public static int uebernehmen(Context ctx) {
        Auswahl a = auswahl(ctx);
        a.aktiv = true;
        speichern(ctx, a);
        List<Termin> t = termine(ctx, a.kurse);
        terminePlanSpeichern(ctx, t);
        schreibenText(ctx, "puls-termine.json", Teilen.termineJson(t).toString());
        Stand s = stand(ctx);
        s.letzterAbgleich = System.currentTimeMillis();
        s.fehler = null;
        speichern(ctx, s);
        planen(ctx);
        return t.size();
    }

    /** Termine als eigenen Stundenplan speichern (Kalenderdatei wie beim Import) und alles neu planen */
    static void terminePlanSpeichern(Context ctx, List<Termin> t) {
        String name = Teilen.name(ctx);
        schreibenText(ctx, Daten.ICS_DATEI, Teilen.ics(t, name, Teilen.eigeneFreigabe(ctx)));
        nachAenderung(ctx);
    }

    /** Nach jeder Änderung des eigenen Plans: Widgets, Erinnerungen, Wecker, Live Update */
    static void nachAenderung(Context ctx) {
        StundenplanWidget.alleAktualisieren(ctx);
        Erinnerungen.planen(ctx);
        Wecker.planen(ctx);
        LiveUpdate.planen(ctx);
    }

    public static void deaktivieren(Context ctx) {
        Auswahl a = auswahl(ctx);
        a.aktiv = false;
        speichern(ctx, a);
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) js.cancel(JOB_ID);
    }

    // ───────────── Täglicher Abgleich ─────────────

    /** Täglichen Abgleich bei Android anmelden (ggf. nur ohne mobile Daten) */
    public static void planen(Context ctx) {
        Auswahl a = auswahl(ctx);
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!a.aktiv || a.kurse.isEmpty()) {
            js.cancel(JOB_ID);
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, PulsJob.class))
                .setRequiredNetworkType(a.nurWLAN ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(24 * 3600_000L)
                .setPersisted(true)
                .build();
        try { js.schedule(job); } catch (Exception ignored) {}
    }

    public static boolean imWLAN(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    /** Gleicht mit PULS ab (höchstens etwa einmal täglich). true = Stundenplan hat sich geändert. Nur im Hintergrund! */
    public static boolean abgleichen(Context ctx, boolean erzwingen) {
        Auswahl a = auswahl(ctx);
        if (!a.aktiv || a.kurse.isEmpty()) return false;
        Stand s = stand(ctx);
        long jetzt = System.currentTimeMillis();
        if (!erzwingen) {
            if (jetzt - s.letzterAbgleich < 20 * 3600_000L) return false;
            if (jetzt - s.letzterVersuch < 30 * 60_000L) return false;
            if (a.nurWLAN && !imWLAN(ctx)) return false;
        }
        s.letzterVersuch = jetzt;
        speichern(ctx, s);

        List<String> fehlgeschlagen = new ArrayList<>();
        for (Kurs k : a.kurse) if (laden(ctx, k.publishid) == null) fehlgeschlagen.add(Konfig.kurs(k.titel)[0]);
        s = stand(ctx);
        if (fehlgeschlagen.size() == a.kurse.size()) {
            s.fehler = "PULS war nicht erreichbar.";
            speichern(ctx, s);
            return false;
        }
        // Gewählte Gruppe gibt es nicht mehr? Bei nur einer Gruppe diese nehmen
        a = auswahl(ctx);
        for (Kurs k : a.kurse) {
            Puls.Veranstaltung v = cache(ctx).get(k.publishid);
            if (v != null && k.gruppe != null && v.gruppe(k.gruppe) == null) {
                k.gruppe = v.gruppen.size() == 1 ? v.gruppen.get(0).nummer : null;
            }
        }
        speichern(ctx, a);
        String altText = lesenText(ctx, "puls-termine.json");
        List<Termin> alt = altText == null ? new ArrayList<>() : Teilen.termineAus(altText);
        List<Termin> neu = termine(ctx, a.kurse);
        List<String> meldungen = vergleich(alt, neu);
        s.letzterAbgleich = System.currentTimeMillis();
        s.fehler = fehlgeschlagen.isEmpty() ? null : "Nicht geladen: " + String.join(", ", fehlgeschlagen);
        if (meldungen.isEmpty()) {
            speichern(ctx, s);
            return false;
        }
        terminePlanSpeichern(ctx, neu);
        schreibenText(ctx, "puls-termine.json", Teilen.termineJson(neu).toString());
        String datum = Lageplan.datumKurz(System.currentTimeMillis());
        List<String> alle = new ArrayList<>();
        for (String m : meldungen) alle.add(datum + ": " + m);
        alle.addAll(s.aenderungen);
        s.aenderungen = new ArrayList<>(alle.subList(0, Math.min(30, alle.size())));
        speichern(ctx, s);
        if (a.mitteilungen) mitteilen(ctx, meldungen);
        return true;
    }

    static String kurzZeit(Termin t) {
        return Lageplan.datumKurz(t.start) + " " + Lageplan.uhrzeitText(t.start);
    }

    /** Unterschiede zwischen altem und neuem Stand, als kurze Sätze */
    static List<String> vergleich(List<Termin> alt, List<Termin> neu) {
        Map<String, Termin> a = new LinkedHashMap<>(), n = new LinkedHashMap<>();
        for (Termin t : alt) a.putIfAbsent(t.titel + "|" + t.typ + "|" + t.start / 1000, t);
        for (Termin t : neu) n.putIfAbsent(t.titel + "|" + t.typ + "|" + t.start / 1000, t);
        long jetzt = System.currentTimeMillis();
        List<String> saetze = new ArrayList<>();
        for (Map.Entry<String, Termin> e : n.entrySet()) {
            Termin t = e.getValue(), o = a.get(e.getKey());
            if (t.ende > jetzt && o != null && !o.raum.equals(t.raum)) {
                saetze.add(Konfig.kurs(t.titel)[0] + " " + t.typ + " " + kurzZeit(t) + ": Raum " + o.raum + " → " + t.raum);
            }
        }
        List<Termin> weg = new ArrayList<>(), dazu = new ArrayList<>();
        for (Map.Entry<String, Termin> e : a.entrySet()) if (!n.containsKey(e.getKey()) && e.getValue().ende > jetzt) weg.add(e.getValue());
        for (Map.Entry<String, Termin> e : n.entrySet()) if (!a.containsKey(e.getKey()) && e.getValue().ende > jetzt) dazu.add(e.getValue());
        if (weg.size() + dazu.size() > 6) {
            saetze.add(dazu.size() + " Termine neu, " + weg.size() + " entfallen");
        } else {
            for (Termin t : weg) saetze.add(Konfig.kurs(t.titel)[0] + " " + t.typ + " " + kurzZeit(t) + " entfällt");
            for (Termin t : dazu) saetze.add(Konfig.kurs(t.titel)[0] + " " + t.typ + " neu: " + kurzZeit(t) + ", " + t.raum);
        }
        return saetze;
    }

    private static void mitteilen(Context ctx, List<String> meldungen) {
        if (!Erinnerungen.erlaubt(ctx)) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(KANAL) == null) {
            NotificationChannel k = new NotificationChannel(KANAL, "Änderungen aus PULS", NotificationManager.IMPORTANCE_DEFAULT);
            k.setDescription("Raum- und Zeitänderungen beim täglichen Abgleich");
            nm.createNotificationChannel(k);
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(4, meldungen.size()); i++) text.append(i > 0 ? "\n" : "").append(meldungen.get(i));
        if (meldungen.size() > 4) text.append("\n…");
        PendingIntent pi = PendingIntent.getActivity(ctx, 41, new Intent(ctx, PulsActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(ctx, KANAL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(meldungen.size() == 1 ? "Änderung im Stundenplan" : meldungen.size() + " Änderungen im Stundenplan")
                .setContentText(meldungen.get(0))
                .setStyle(new Notification.BigTextStyle().bigText(text.toString()))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify((int) (System.currentTimeMillis() / 1000 % 100000), n);
    }
}
