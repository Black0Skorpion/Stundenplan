package de.stundenplan.widget;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zugriff auf die öffentlichen Seiten von PULS (Vorlesungsverzeichnis der Uni Potsdam, ohne Anmeldung).
 * PULS hat keine offizielle Schnittstelle – hier werden die HTML-Seiten ausgelesen.
 * Gleiche Logik wie ios-app/App/Puls.swift. Nur reines Java (wird auf GitHub auch einzeln getestet).
 */
public final class Puls {
    private Puls() {}

    public static final String BASIS = "https://puls.uni-potsdam.de/qisserver/rds";
    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    // ───────────── Datenmodell ─────────────

    /** Eine Zeile aus „Übersicht über alle Veranstaltungstermine“ */
    public static final class Regel {
        public String art = "", tag = "", von = "", bis = "", rhythmus = "";
        public String beginn, ende;           // "12.10.2026"
        public String raum = "", lehrperson = "";
        public List<String> ausfaelle = new ArrayList<>();
        public String bemerkung;
        public List<String> weitereRaeume = new ArrayList<>();

        boolean einzel() { return rhythmus.toLowerCase().contains("einzel"); }

        /** "2.70.0.05 / 2.70.0.10" */
        public String raeume() {
            List<String> l = new ArrayList<>();
            l.add(raum);
            l.addAll(weitereRaeume);
            return String.join(" / ", l);
        }

        /** Derselbe Zeitslot (zum Ausblenden und Zusammenfassen) – wie auf iOS */
        public String schluessel() {
            return art + "|" + tag + "|" + von + "|" + bis + "|" + rhythmus + (einzel() ? "|" + (beginn == null ? "" : beginn) : "");
        }

        /** Gleich bis auf die Liste weiterer Räume (für „gemeinsam“) */
        boolean gleich(Regel o) {
            return art.equals(o.art) && tag.equals(o.tag) && von.equals(o.von) && bis.equals(o.bis)
                    && rhythmus.equals(o.rhythmus) && Objects.equals(beginn, o.beginn) && Objects.equals(ende, o.ende)
                    && raum.equals(o.raum) && lehrperson.equals(o.lehrperson) && ausfaelle.equals(o.ausfaelle)
                    && Objects.equals(bemerkung, o.bemerkung) && weitereRaeume.equals(o.weitereRaeume);
        }
    }

    public static final class Gruppe {
        public String nummer;
        public List<Regel> regeln = new ArrayList<>();
    }

    public static final class Veranstaltung {
        public String publishid, titel, art = "", semester = "";
        public List<Gruppe> gruppen = new ArrayList<>();
        public String moodle, belegfrist;

        static boolean enthaelt(List<Regel> l, Regel r) {
            for (Regel x : l) if (x.gleich(r)) return true;
            return false;
        }

        /** Termine, die in allen Gruppen gleich sind (meist die Vorlesung) */
        public List<Regel> gemeinsam() {
            List<Regel> aus = new ArrayList<>();
            if (gruppen.isEmpty()) return aus;
            for (Regel r : gruppen.get(0).regeln) {
                boolean ueberall = true;
                for (Gruppe g : gruppen) if (!enthaelt(g.regeln, r)) { ueberall = false; break; }
                if (ueberall) aus.add(r);
            }
            return aus;
        }

        /** Was eine Gruppe von den anderen unterscheidet */
        public List<Regel> eigenes(Gruppe g) {
            List<Regel> gem = gemeinsam();
            List<Regel> rest = new ArrayList<>();
            for (Regel r : g.regeln) if (!enthaelt(gem, r)) rest.add(r);
            return rest.isEmpty() ? g.regeln : rest;
        }

        public Gruppe gruppe(String nummer) {
            for (Gruppe g : gruppen) if (g.nummer.equals(nummer)) return g;
            return null;
        }
    }

    /** Ein Zeitslot zur Anzeige – fasst Zeilen zusammen, die sich nur im Raum/Zeitraum unterscheiden */
    public static final class Slot {
        public final Regel regel;
        public final List<Regel> alle;

        Slot(Regel regel, List<Regel> alle) { this.regel = regel; this.alle = alle; }

        public String raumText() {
            List<Regel> teile = new ArrayList<>(alle);
            Collections.sort(teile, (a, b) -> Long.compare(datumWert(a.beginn), datumWert(b.beginn)));
            List<String> raeume = new ArrayList<>();
            for (Regel r : teile) if (!raeume.contains(r.raeume())) raeume.add(r.raeume());
            if (raeume.size() <= 1) return raeume.isEmpty() ? "" : raeume.get(0);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < teile.size(); i++) {
                Regel r = teile.get(i);
                if (i > 0) sb.append(", ab ").append(r.beginn == null ? "?" : r.beginn.substring(0, Math.min(6, r.beginn.length()))).append(" ");
                sb.append(r.raeume());
            }
            return sb.toString();
        }

        public String kurz() {
            String wann = regel.einzel() && regel.beginn != null ? regel.beginn : regel.tag;
            String rt = raumText();
            return regel.art + " · " + wann + " " + regel.von + "–" + regel.bis + (rt.isEmpty() ? "" : " · " + rt);
        }

        /** Ohne Art, für Gruppen-Knöpfe: "Do 12:00–14:00" */
        public String zeit() {
            String wann = regel.einzel() && regel.beginn != null ? regel.beginn : regel.tag;
            return wann + " " + regel.von + "–" + regel.bis;
        }
    }

    public static List<Slot> slots(List<Regel> regeln) {
        LinkedHashMap<String, List<Regel>> g = new LinkedHashMap<>();
        for (Regel r : regeln) {
            List<Regel> l = g.get(r.schluessel());
            if (l == null) { l = new ArrayList<>(); g.put(r.schluessel(), l); }
            l.add(r);
        }
        List<Slot> aus = new ArrayList<>();
        for (List<Regel> l : g.values()) aus.add(new Slot(l.get(0), l));
        return aus;
    }

    /** Ein fertiger Termin */
    public static final class EinzelTermin {
        public final long start, ende;
        public final String art, raum;
        public String lehrperson = "";

        EinzelTermin(long start, long ende, String art, String raum) {
            this.start = start; this.ende = ende; this.art = art; this.raum = raum;
        }
    }

    // ───────────── Semester ─────────────

    /** PULS-Semesterkennung: 20262 = WiSe 2026/27, 20271 = SoSe 2027 */
    public static String semesterCode(long jetzt) {
        ZonedDateTime d = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(jetzt), ZONE);
        int jahr = d.getYear(), monat = d.getMonthValue();
        if (monat >= 9) return jahr + "2";
        if (monat <= 2) return (jahr - 1) + "2";
        return jahr + "1";
    }

    public static String naechstesSemester(String code) {
        try {
            int jahr = Integer.parseInt(code.substring(0, 4));
            return code.endsWith("1") ? jahr + "2" : (jahr + 1) + "1";
        } catch (Exception e) { return code; }
    }

    public static String semesterName(String code) {
        try {
            int jahr = Integer.parseInt(code.substring(0, 4));
            String n = String.valueOf(jahr + 1);
            return code.endsWith("1") ? "SoSe " + jahr : "WiSe " + jahr + "/" + n.substring(n.length() - 2);
        } catch (Exception e) { return code; }
    }

    // ───────────── Adressen ─────────────

    public static String veranstaltungURL(String publishid) {
        return BASIS + "?state=verpublish&status=init&vmfile=no&publishid=" + publishid
                + "&moduleCall=webInfo&publishConfFile=webInfo&publishSubDir=veranstaltung";
    }

    /** pfad = "" (oberste Ebene) oder "277989|279834|…" */
    public static String verzeichnisURL(String pfad, String semester) {
        String wurzel = pfad.isEmpty() ? "0" : pfad.replace("|", "%7C");
        return BASIS + "?state=wtree&search=1&trex=step&root1" + semester + "=" + wurzel + "&P.vx=kurz";
    }

    /** Ein Eintrag im Vorlesungsverzeichnis: Ordner (pfad) oder Veranstaltung (publishid) */
    public static final class Eintrag {
        public final String name, pfad, publishid;

        Eintrag(String name, String pfad, String publishid) {
            this.name = name; this.pfad = pfad; this.publishid = publishid;
        }

        public boolean ordner() { return pfad != null; }
    }

    /** Unterordner (eine Ebene tiefer als pfad) und Veranstaltungen einer Verzeichnisseite */
    public static List<Eintrag> verzeichnis(String html, String pfad) {
        int tiefe = pfad.isEmpty() ? 2 : pfad.split("\\|").length + 1;
        List<Eintrag> aus = new ArrayList<>();
        Set<String> gesehen = new HashSet<>();
        Pattern wurzel = Pattern.compile("root1\\d{5}=([0-9|%7C]+)");
        for (String[] a : alle("<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", html)) {
            String href = a[1].replace("&amp;", "&");
            String name = text(a[2]);
            Matcher m = wurzel.matcher(href);
            if (href.contains("state=wtree") && href.contains("P.vx=kurz") && m.find()) {
                String p = m.group(1).replace("%7C", "|");
                int stufen = p.split("\\|").length;
                if (stufen != tiefe || !(pfad.isEmpty() || p.startsWith(pfad + "|")) || name.isEmpty() || !gesehen.add("o" + p)) continue;
                aus.add(new Eintrag(name, p, null));
            } else if (href.contains("publishid=") && !name.isEmpty()) {
                String id = publishid(href);
                if (id != null && gesehen.add("v" + id)) aus.add(new Eintrag(name, null, id));
            }
        }
        return aus;
    }

    /** publishid aus einem eingefügten PULS-Link (oder einer reinen Nummer) */
    public static String publishid(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (!t.isEmpty() && t.matches("\\d+")) return t;
        for (String muster : new String[]{"publishid=(\\d+)", "veranstid=(\\d+)"}) {
            Matcher m = Pattern.compile(muster).matcher(t);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    // ───────────── HTML-Hilfen ─────────────

    private static final String[][] ERSATZ = {{"&nbsp;", " "}, {"&amp;", "&"}, {"&lt;", "<"}, {"&gt;", ">"},
            {"&quot;", "\""}, {"&#39;", "'"}, {"&auml;", "ä"}, {"&ouml;", "ö"}, {"&uuml;", "ü"}, {"&Auml;", "Ä"},
            {"&Ouml;", "Ö"}, {"&Uuml;", "Ü"}, {"&szlig;", "ß"}, {"&ndash;", "–"}, {"&shy;", ""}};

    public static String text(String html) {
        String s = html.replaceAll("<br\\s*/?>", " ").replaceAll("<[^>]+>", "");
        for (String[] e : ERSATZ) s = s.replace(e[0], e[1]);
        s = s.replaceAll("&#(\\d+);", "").replace(' ', ' ');
        return s.trim().replaceAll("\\s+", " ");
    }

    private static List<String[]> alle(String muster, String s) {
        Matcher m = Pattern.compile(muster, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(s);
        List<String[]> aus = new ArrayList<>();
        while (m.find()) {
            String[] g = new String[m.groupCount() + 1];
            for (int i = 0; i <= m.groupCount(); i++) g[i] = m.group(i) == null ? "" : m.group(i);
            aus.add(g);
        }
        return aus;
    }

    private static List<String> zellen(String zeile) {
        List<String> aus = new ArrayList<>();
        for (String[] g : alle("<t[dh][^>]*>(.*?)</t[dh]>", zeile)) aus.add(text(g[1]));
        return aus;
    }

    private static List<String> daten(String s) {
        List<String> aus = new ArrayList<>();
        for (String[] g : alle("(\\d{2}\\.\\d{2}\\.\\d{4})", s)) aus.add(g[1]);
        return aus;
    }

    // ───────────── Veranstaltungsseite auslesen ─────────────

    public static Veranstaltung veranstaltung(String html, String publishid) {
        String titel = "";
        for (String[] h : alle("<h1[^>]*>(.*?)</h1>", html)) {
            String t = text(h[1]);
            if (t.contains("Einzelansicht")) { titel = t; break; }
        }
        titel = titel.replaceAll("\\s*-\\s*Einzelansicht\\s*$", "");
        if (titel.isEmpty()) return null;

        Veranstaltung v = new Veranstaltung();
        v.publishid = publishid;
        v.titel = titel;
        List<String[]> grund = alle("<table[^>]*summary=\"Grunddaten[^\"]*\"[^>]*>(.*?)</table>", html);
        if (!grund.isEmpty()) {
            for (String[] zeile : alle("<tr[^>]*>(.*?)</tr>", grund.get(0)[1])) {
                List<String> z = zellen(zeile[1]);
                for (int i = 0; i < z.size() - 1; i++) {
                    switch (z.get(i)) {
                        case "Veranstaltungsart": v.art = z.get(i + 1); break;
                        case "Semester": v.semester = z.get(i + 1); break;
                        case "Belegungsfristen": v.belegfrist = z.get(i + 1); break;
                        default: break;
                    }
                }
            }
        }
        List<String[]> moodle = alle("<a[^>]*href=\"([^\"]+)\"[^>]*>\\s*Moodle[^<]*</a>", html);
        if (!moodle.isEmpty()) v.moodle = text(moodle.get(0)[1]);

        List<String[]> tabellen = alle("(<table[^>]*summary=\"Übersicht über alle Veranstaltungstermine\"[^>]*>)(.*?)</table>", html);
        int suchAb = 0;
        for (int i = 0; i < tabellen.size(); i++) {
            String[] t = tabellen.get(i);
            int pos = html.indexOf(t[1], suchAb);
            String nummer = String.valueOf(i + 1);
            if (pos >= 0) {
                List<String[]> g = alle("Gruppe\\s+([0-9A-Za-z]+)\\s*:", html.substring(suchAb, pos));
                if (!g.isEmpty()) nummer = g.get(g.size() - 1)[1];
                suchAb = pos + t[1].length();
            }
            List<Regel> regeln = new ArrayList<>();
            for (String[] zeile : alle("<tr[^>]*>(.*?)</tr>", t[2])) {
                List<String> z = zellen(zeile[1]);
                if (z.size() >= 2 && z.get(1).startsWith("Bemerkung") && !regeln.isEmpty()) {
                    regeln.get(regeln.size() - 1).bemerkung = String.join(" ", z.subList(2, z.size()));
                    continue;
                }
                if (z.size() < 8 || z.get(2).isEmpty() || z.get(2).equals("Tag")) continue;
                List<String[]> zeiten = alle("(\\d{1,2}:\\d{2})", z.get(3));
                if (zeiten.size() < 2) continue;
                List<String> d = daten(z.get(5));
                Regel r = new Regel();
                r.art = z.get(1).isEmpty() ? v.art : z.get(1);
                r.tag = z.get(2);
                r.von = zeiten.get(0)[1];
                r.bis = zeiten.get(1)[1];
                r.rhythmus = z.get(4);
                r.beginn = d.isEmpty() ? null : d.get(0);
                r.ende = d.size() > 1 ? d.get(1) : r.beginn;
                r.raum = raumKurz(z.get(6));
                r.lehrperson = z.get(7);
                r.ausfaelle = z.size() > 8 ? daten(z.get(8)) : new ArrayList<>();
                regeln.add(r);
            }
            // Gleicher Termin in mehreren Räumen → eine Zeile mit weiteren Räumen
            List<Regel> zusammen = new ArrayList<>();
            for (Regel r : regeln) {
                Regel da = null;
                for (Regel x : zusammen) {
                    if (x.schluessel().equals(r.schluessel()) && Objects.equals(x.beginn, r.beginn) && Objects.equals(x.ende, r.ende)) {
                        da = x;
                        break;
                    }
                }
                if (da != null) {
                    if (!r.raum.equals(da.raum) && !da.weitereRaeume.contains(r.raum)) da.weitereRaeume.add(r.raum);
                    Set<String> schnitt = new TreeSet<>(da.ausfaelle);
                    schnitt.retainAll(r.ausfaelle);
                    da.ausfaelle = new ArrayList<>(schnitt);
                } else {
                    zusammen.add(r);
                }
            }
            if (!zusammen.isEmpty()) {
                Gruppe g = new Gruppe();
                g.nummer = nummer;
                g.regeln = zusammen;
                v.gruppen.add(g);
            }
        }
        return v;
    }

    /** "2.27.0.01 Hörsaal" → "2.27.0.01" */
    public static String raumKurz(String s) {
        Matcher m = Pattern.compile("\\d\\.\\d{1,2}\\.[0-9A-Za-z-]+\\.[0-9A-Za-z.-]+").matcher(s);
        return m.find() ? m.group() : s;
    }

    // ───────────── Termine erzeugen ─────────────

    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    static LocalDate tag(String s) {
        try { return LocalDate.parse(s, TAG); } catch (Exception e) { return null; }
    }

    static long datumWert(String s) {
        LocalDate d = s == null ? null : tag(s);
        return d == null ? Long.MIN_VALUE : d.toEpochDay();
    }

    private static LocalTime zeit(String s) {
        try {
            String[] t = s.split(":");
            return LocalTime.of(Integer.parseInt(t[0].trim()), Integer.parseInt(t[1].trim()));
        } catch (Exception e) { return null; }
    }

    private static final Map<String, DayOfWeek> WOCHENTAGE = new HashMap<>();
    static {
        WOCHENTAGE.put("Mo", DayOfWeek.MONDAY); WOCHENTAGE.put("Di", DayOfWeek.TUESDAY);
        WOCHENTAGE.put("Mi", DayOfWeek.WEDNESDAY); WOCHENTAGE.put("Do", DayOfWeek.THURSDAY);
        WOCHENTAGE.put("Fr", DayOfWeek.FRIDAY); WOCHENTAGE.put("Sa", DayOfWeek.SATURDAY);
        WOCHENTAGE.put("So", DayOfWeek.SUNDAY);
    }

    /** Alle einzelnen Termine einer Regel (wöchentlich/14-täglich/Einzeltermin/Block), ohne Ausfälle */
    public static List<EinzelTermin> termine(Regel r) {
        List<EinzelTermin> aus = new ArrayList<>();
        if (r.beginn == null) return aus;
        String e = r.ende != null ? r.ende : r.beginn;
        LocalDate erster = tag(r.beginn), letzter = tag(e);
        LocalTime von = zeit(r.von), bis = zeit(r.bis);
        if (erster == null || letzter == null || von == null) return aus;
        Set<String> ausfall = new HashSet<>(r.ausfaelle);
        String rh = r.rhythmus.toLowerCase();
        List<LocalDate> tage = new ArrayList<>();
        if (rh.contains("einzel") || r.beginn.equals(e)) {
            tage.add(erster);
        } else if (rh.contains("block")) {
            boolean mitWochenende = rh.contains("sa") || rh.contains("so");
            for (LocalDate d = erster; !d.isAfter(letzter); d = d.plusDays(1)) {
                DayOfWeek wt = d.getDayOfWeek();
                if (mitWochenende || (wt != DayOfWeek.SATURDAY && wt != DayOfWeek.SUNDAY)) tage.add(d);
            }
        } else {
            int schritt = rh.contains("14") || rh.contains("zweiw") ? 14 : 7;
            LocalDate d = erster;
            DayOfWeek ziel = WOCHENTAGE.get(r.tag.length() >= 2 ? r.tag.substring(0, 2) : r.tag);
            if (ziel != null) {
                int n = 0;
                while (d.getDayOfWeek() != ziel && n < 7) { d = d.plusDays(1); n++; }
            }
            for (; !d.isAfter(letzter); d = d.plusDays(schritt)) tage.add(d);
        }
        long dauer = 90 * 60_000L;
        if (bis != null) dauer = Math.max(15 * 60_000L, (bis.toSecondOfDay() - von.toSecondOfDay()) * 1000L);
        for (LocalDate d : tage) {
            if (ausfall.contains(d.format(TAG))) continue;
            long start = LocalDateTime.of(d, von).atZone(ZONE).toInstant().toEpochMilli();
            EinzelTermin e = new EinzelTermin(start, start + dauer, r.art, r.raum);
            e.lehrperson = r.lehrperson == null ? "" : r.lehrperson.trim();
            aus.add(e);
        }
        return aus;
    }

    /** Alle Termine einer gewählten Gruppe (ohne ausgeblendete Regeln) */
    public static List<EinzelTermin> termine(Veranstaltung v, String gruppe, Set<String> ausgeblendet) {
        Gruppe g = gruppe == null ? null : v.gruppe(gruppe);
        if (g == null && !v.gruppen.isEmpty()) g = v.gruppen.get(0);
        List<Regel> regeln = new ArrayList<>();
        if (g != null) for (Regel r : g.regeln) if (ausgeblendet == null || !ausgeblendet.contains(r.schluessel())) regeln.add(r);
        return termine(regeln);
    }

    public static List<EinzelTermin> termine(List<Regel> regeln) {
        Set<String> gesehen = new HashSet<>();
        List<EinzelTermin> aus = new ArrayList<>();
        for (Regel r : regeln) {
            for (EinzelTermin t : termine(r)) {
                if (gesehen.add(t.start + "|" + t.art)) aus.add(t);
            }
        }
        Collections.sort(aus, (a, b) -> Long.compare(a.start, b.start));
        return aus;
    }

    /** Termine zweier Listen überschneiden sich irgendwo */
    public static boolean ueberschneiden(List<EinzelTermin> a, List<EinzelTermin> b) {
        for (EinzelTermin x : a) for (EinzelTermin y : b) if (x.start < y.ende && y.start < x.ende) return true;
        return false;
    }

    static List<String> liste(String... s) { return new ArrayList<>(Arrays.asList(s)); }
}
