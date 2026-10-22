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

/** Lagepläne der drei Campus: Gebäudepositionen, Legenden (Stand Mai 2025) und die Ansicht als HTML. */
public final class Lageplan {
    private Lageplan() {}

    /** Position der Hausnummer auf dem Plan (Anteil von Breite/Höhe). */
    private static final Map<String, float[]> POS_GOLM = new HashMap<>(), POS_NP = new HashMap<>(), POS_GR = new HashMap<>();
    /** Einrichtungen laut Legende. */
    private static final Map<String, String[]> EIN_GOLM = new HashMap<>(), EIN_NP = new HashMap<>(), EIN_GR = new HashMap<>();
    static {
        POS_GOLM.put("25", new float[]{0.3622f, 0.4749f});
        POS_GOLM.put("28", new float[]{0.3706f, 0.5459f});
        POS_GOLM.put("18", new float[]{0.3505f, 0.6258f});
        POS_GOLM.put("14", new float[]{0.4879f, 0.7238f});
        POS_GOLM.put("8", new float[]{0.6598f, 0.5596f});
        POS_GOLM.put("11", new float[]{0.6606f, 0.7432f});
        POS_GOLM.put("24", new float[]{0.6679f, 0.7883f});
        POS_GOLM.put("19", new float[]{0.5668f, 0.8721f});
        POS_GOLM.put("35", new float[]{0.6534f, 0.8581f});
        POS_GOLM.put("20", new float[]{0.7211f, 0.8428f});
        POS_GOLM.put("6", new float[]{0.7917f, 0.5255f});
        POS_GOLM.put("5", new float[]{0.8661f, 0.5624f});
        POS_GOLM.put("4", new float[]{0.8646f, 0.6525f});
        POS_GOLM.put("1", new float[]{0.7804f, 0.7095f});
        POS_GOLM.put("26", new float[]{0.5281f, 0.4119f});
        POS_GOLM.put("10", new float[]{0.6594f, 0.6776f});
        POS_GOLM.put("9", new float[]{0.7086f, 0.6315f});
        POS_GOLM.put("2", new float[]{0.7602f, 0.6604f});
        POS_GOLM.put("27", new float[]{0.4745f, 0.5309f});
        POS_GOLM.put("29", new float[]{0.5251f, 0.5677f});
        POS_GOLM.put("31", new float[]{0.5587f, 0.7556f});
        POS_GOLM.put("30", new float[]{0.5462f, 0.7166f});
        POS_GOLM.put("12", new float[]{0.9248f, 0.5391f});
        POS_GOLM.put("17", new float[]{0.3888f, 0.7199f});
        POS_GOLM.put("60", new float[]{0.8442f, 0.1093f});
        POS_GOLM.put("13", new float[]{0.7916f, 0.8074f});
        POS_GOLM.put("48", new float[]{0.2998f, 0.6972f});
        POS_GOLM.put("32", new float[]{0.471f, 0.6365f});
        POS_GOLM.put("70", new float[]{0.3853f, 0.0464f});
        POS_GOLM.put("16", new float[]{0.3861f, 0.813f});
        POS_GOLM.put("62", new float[]{0.0741f, 0.2764f});
        POS_GOLM.put("7", new float[]{0.7796f, 0.5907f});
        POS_GOLM.put("15", new float[]{0.4928f, 0.7715f});
        EIN_GOLM.put("1", new String[]{"Infrastrukturelles/Technisches Gebäudemanagement", "Institut für Umweltwissenschaften und Geographie", "Leitzentrale"});
        EIN_GOLM.put("2", new String[]{"Potsdam Research Institute for Multilingualism (PRIM)"});
        EIN_GOLM.put("4", new String[]{"Mensa"});
        EIN_GOLM.put("5", new String[]{"Multimediaproduktion (ZIM)"});
        EIN_GOLM.put("6", new String[]{"Department Musik und Kunst"});
        EIN_GOLM.put("7", new String[]{"Wohnanlagen (Studierendenwerk)"});
        EIN_GOLM.put("8", new String[]{"Service Center (ZIM)", "Gebäudemanagement"});
        EIN_GOLM.put("9", new String[]{"Institut für Mathematik"});
        EIN_GOLM.put("10", new String[]{"EPI-Center", "Fuhrpark"});
        EIN_GOLM.put("11", new String[]{"Alte Sporthalle", "Lehreinheit Wirtschaft-Arbeit-Technik"});
        EIN_GOLM.put("12", new String[]{"Institut für Umweltwissenschaften und Geographie"});
        EIN_GOLM.put("13", new String[]{"Atelier – Werkstätten Kunst", "Fachbereich Kunst", "Pädagogische Werkstatt"});
        EIN_GOLM.put("14", new String[]{"Department Linguistik", "Department Psychologie", "Lesecafé", "Poststelle"});
        EIN_GOLM.put("16", new String[]{"Department Grundschulpädagogik"});
        EIN_GOLM.put("17", new String[]{"Wohnanlagen (Studierendenwerk)"});
        EIN_GOLM.put("18", new String[]{"Universitätsbibliothek (IKMZ)"});
        EIN_GOLM.put("19", new String[]{"Sporthalle"});
        EIN_GOLM.put("20", new String[]{"Institut für Biochemie und Biologie"});
        EIN_GOLM.put("24", new String[]{"Department Erziehungswissenschaften", "Department Sport- und Gesundheitswissenschaften", "Department Lehrerbildung", "Institut für Umweltwissenschaften und Geographie"});
        EIN_GOLM.put("25", new String[]{"Institut für Biochemie und Biologie", "Institut für Chemie"});
        EIN_GOLM.put("26", new String[]{"Institut für Biochemie und Biologie", "Institut für Chemie"});
        EIN_GOLM.put("27", new String[]{"Institut für Erd- und Umweltwissenschaften"});
        EIN_GOLM.put("28", new String[]{"Institut für Physik und Astronomie", "Dekanat Math.-Nat. Fakultät"});
        EIN_GOLM.put("29", new String[]{"Institut für Geowissenschaften", "Institut für Biochemie und Biologie", "Institut für Chemie", "Potsdam Transfer"});
        EIN_GOLM.put("30", new String[]{"Gewächshaus"});
        EIN_GOLM.put("31", new String[]{"Department Inklusionspädagogik"});
        EIN_GOLM.put("32", new String[]{"Earth & Environment Centre (EEC)", "Zentrum für Naturstoffgenomik"});
        EIN_GOLM.put("35", new String[]{"Dekanat Humanwissenschaftliche Fakultät"});
        EIN_GOLM.put("48", new String[]{"Infopoint"});
        EIN_GOLM.put("60", new String[]{"Institut für Biochemie und Biologie"});
        EIN_GOLM.put("62", new String[]{"Fakultät für Gesundheitswissenschaften", "Zentrum für Lehrerbildung (ZeLB)"});
        EIN_GOLM.put("70", new String[]{"Institut für Informatik und Computational Science", "Institut für Umweltwissenschaften und Geographie"});
        POS_NP.put("1", new float[]{0.4184f, 0.6985f});
        POS_NP.put("2", new float[]{0.3946f, 0.625f});
        POS_NP.put("3", new float[]{0.4171f, 0.5592f});
        POS_NP.put("4", new float[]{0.3653f, 0.3742f});
        POS_NP.put("5", new float[]{0.4167f, 0.3052f});
        POS_NP.put("6", new float[]{0.2686f, 0.5382f});
        POS_NP.put("8", new float[]{0.5062f, 0.7122f});
        POS_NP.put("9", new float[]{0.6788f, 0.5482f});
        POS_NP.put("10", new float[]{0.695f, 0.587f});
        POS_NP.put("11", new float[]{0.6788f, 0.3569f});
        POS_NP.put("12", new float[]{0.5087f, 0.2373f});
        POS_NP.put("13", new float[]{0.1789f, 0.2778f});
        POS_NP.put("14", new float[]{0.7835f, 0.1708f});
        POS_NP.put("15", new float[]{0.7139f, 0.179f});
        POS_NP.put("16", new float[]{0.3444f, 0.2031f});
        POS_NP.put("17", new float[]{0.2079f, 0.2135f});
        POS_NP.put("19", new float[]{0.54f, 0.9017f});
        POS_NP.put("22", new float[]{0.3085f, 0.6482f});
        POS_NP.put("34", new float[]{0.3229f, 0.2889f});
        POS_NP.put("35", new float[]{0.6019f, 0.5468f});
        POS_NP.put("36", new float[]{0.3032f, 0.4321f});
        POS_NP.put("T1", new float[]{0.4939f, 0.0577f});
        POS_NP.put("T2", new float[]{0.427f, 0.0577f});
        POS_NP.put("T3", new float[]{0.3613f, 0.0577f});
        EIN_NP.put("1", new String[]{"Institut für Künste und Medien", "Institut für Slavistik"});
        EIN_NP.put("2", new String[]{"Arbeitsmedizin", "AV- und IT-Dienste – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Dezernat 6: Zentrale Dienste", "Poststelle – Dezernat 6: Zentrale Dienste", "Sicherheitswesen"});
        EIN_NP.put("3", new String[]{"Dezernat 3: Personal- und Rechtsangelegenheiten", "Dezernat 4: Haushalt und Beschaffung"});
        EIN_NP.put("4", new String[]{"Dezernat 5: Bau- und Facilitymanagement"});
        EIN_NP.put("5", new String[]{"Institut für Germanistik"});
        EIN_NP.put("6", new String[]{"Koordinationsbüro für Chancengleichheit", "Multimediaproduktion – ZIM – Zentrum für Informationstechnologie und Medienmanagement", "Netzwerk Studienqualität Brandenburg (sqb)", "Personalvertretungen", "Studierendenvertretung – AStA", "Zentrum für Qualitätsentwicklung in Lehre und Studium (ZfQ)"});
        EIN_NP.put("8", new String[]{"Auditorium maximum „Audimax“", "Co-Working-Space", "Chief Information Officer (CIO)", "Dezernat 1: Planung, Statistik, Forschungsangelegenheiten", "Dezernat 2: Studienangelegenheiten", "Dezernat 3: Personal- und Rechtsangelegenheiten", "International Office", "Leitung, Verwaltung und Organisation – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Service Center – ZIM – Zentrum für Informationstechnologie und Medienmanagement", "Welcome Center Potsdam – International Office"});
        EIN_NP.put("9", new String[]{"Career-Service", "Eltern-Kind-Raum", "Geschäftsstelle des Senats", "International Summer Campus Office – International Office", "Kanzlerbüro", "Potsdam Transfer – Zentrale wissenschaftliche Einrichtung für Gründung, Innovation, Wissens- und Technologietransfer", "Präsident", "Präsidialamt", "Präsidium", "Referat für Presse- und Öffentlichkeitsarbeit", "Universitätsbibliothek"});
        EIN_NP.put("10", new String[]{"Universitätsbibliothek"});
        EIN_NP.put("11", new String[]{"Co-Working-Space", "Dekanat – Philosophische Fakultät", "Historisches Institut", "Institut für Jüdische Studien/Jewish Studies", "Institut für Lebensgestaltung – Ethik – Religionskunde", "Institut für Philosophie", "Institut für Religionswissenschaft", "Klassische Philologie", "Nil StudentInnenkeller", "Universitätsbibliothek"});
        EIN_NP.put("12", new String[]{"Department für Sport- und Gesundheitswissenschaften", "Eltern-Kind-Raum", "Hochschulambulanz", "Mensa – Studierendenwerk West:Brandenburg"});
        EIN_NP.put("13", new String[]{"Dezernat 5: Bau- und Facilitymanagement", "Infrastrukturelles Gebäudemanagement/Campusteam – Dezernat 5: Bau- und Facilitymanagement", "Zentrum für Hochschulsport"});
        EIN_NP.put("14", new String[]{"Abraham Geiger Kolleg (AGK)", "Zacharias Frankel College (ZFC)"});
        EIN_NP.put("15", new String[]{"Institut für Jüdische Theologie (School of Jewish Theology)", "Institut für Philosophie"});
        EIN_NP.put("19", new String[]{"Institut für Anglistik und Amerikanistik", "Institut für Romanistik", "Zentrum für Sprachen und Schlüsselkompetenzen (Zessko)"});
        EIN_NP.put("22", new String[]{"Dezernat 4: Haushalt und Beschaffung", "EDUC – Präsidialamt", "Nathan Peter Levinson Stiftung", "Service für Familien – Dezernat 3: Personal- und Rechtsangelegenheiten", "TK-Servicestelle", "Zentrum für Qualitätsentwicklung in Lehre und Studium (ZfQ)"});
        EIN_NP.put("34", new String[]{"Department für Sport- und Gesundheitswissenschaften", "Knetpunkt", "Umkleiden"});
        EIN_NP.put("35", new String[]{"Co-Working-Space", "Unishop"});
        EIN_NP.put("36", new String[]{"Department für Sport- und Gesundheitswissenschaften", "Zentrum für Hochschulsport"});
        EIN_NP.put("T1", new String[]{"Wohnanlagen – Studierendenwerk West:Brandenburg"});
        EIN_NP.put("T2", new String[]{"Wohnanlagen – Studierendenwerk West:Brandenburg"});
        EIN_NP.put("T3", new String[]{"Wohnanlagen – Studierendenwerk West:Brandenburg"});
        POS_GR.put("1", new float[]{0.6388f, 0.6253f});
        POS_GR.put("2", new float[]{0.4271f, 0.4949f});
        POS_GR.put("3", new float[]{0.4469f, 0.4214f});
        POS_GR.put("5", new float[]{0.2945f, 0.3226f});
        POS_GR.put("6", new float[]{0.6342f, 0.5202f});
        POS_GR.put("7", new float[]{0.572f, 0.3987f});
        POS_GR.put("13", new float[]{0.1209f, 0.8324f});
        POS_GR.put("14", new float[]{0.2504f, 0.8381f});
        EIN_GR.put("1", new String[]{"Dekanat – Juristische Fakultät", "Dekanat – Wirtschafts- und Sozialwissenschaftliche Fakultät", "Juristische Fakultät", "Mensa – Studierendenwerk West:Brandenburg", "Poststelle – Dezernat 6", "Service Center – ZIM – Zentrum für Informationstechnologie und Medienmanagement"});
        EIN_GR.put("2", new String[]{"Wirtschafts- und Sozialwissenschaftliche Fakultät"});
        EIN_GR.put("3", new String[]{"Infrastrukturelles Gebäudemanagement (IGM)/Campusteam Griebnitzsee – Dezernat 5: Bau- und Facilitymanagement", "Wirtschafts- und Sozialwissenschaftliche Fakultät"});
        EIN_GR.put("5", new String[]{"Universitätsbibliothek"});
        EIN_GR.put("6", new String[]{"AV- und IT-Dienste – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Mensa – Studierendenwerk West:Brandenburg", "Multimediaproduktion – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Studienbüro – Juristische Fakultät", "Zentrum für Sprachen und Schlüsselkompetenzen (Zessko)"});
        EIN_GR.put("7", new String[]{"Kommunalwissenschaftliches Institut", "Potsdam Transfer – Zentrale wissenschaftliche Einrichtung für Gründung, Innovation, Wissens- und Technologietransfer", "Universitätsgesellschaft Potsdam e. V.", "UP Transfer GmbH", "Wirtschafts- und Sozialwissenschaftliche Fakultät"});
        EIN_GR.put("13", new String[]{"Wirtschaftsinformatik"});
        EIN_GR.put("14", new String[]{"Demonstrationsgebäude – Wirtschaftsinformatik"});
    }

    static Map<String, float[]> positionen(String campus) {
        switch (campus) {
            case Campus.NEUES_PALAIS: return POS_NP;
            case Campus.GRIEBNITZSEE: return POS_GR;
            default: return POS_GOLM;
        }
    }

    static Map<String, String[]> einrichtungen(String campus) {
        switch (campus) {
            case Campus.NEUES_PALAIS: return EIN_NP;
            case Campus.GRIEBNITZSEE: return EIN_GR;
            default: return EIN_GOLM;
        }
    }

    /** „Haus 70 · Erdgeschoss · Institut für …“ für die Karte im Plan (null = unbekannt) */
    static String einrichtungText(String raum) {
        String[] r = raumAufteilen(raum);
        if (r == null || !Campus.bekannt(r[0])) return null;
        StringBuilder sb = new StringBuilder("Haus " + r[1] + " · " + etageText(r[2]));
        String[] e = einrichtungen(r[0]).get(r[1]);
        if (e != null) sb.append(" · ").append(String.join(" · ", e));
        return sb.toString();
    }

    /** Bild-Ressource des Plans */
    static int bild(String campus) {
        switch (campus) {
            case Campus.NEUES_PALAIS: return R.drawable.lageplan_neuespalais;
            case Campus.GRIEBNITZSEE: return R.drawable.lageplan_griebnitzsee;
            default: return R.drawable.lageplan_golm;
        }
    }

    private static final String[] WT = {"So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"};
    private static final String[] MONATE = {"Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"};

    /** "2.70.0.10" → { "2", "70", "0", "10" } oder null */
    static String[] raumAufteilen(String raum) {
        if (raum == null) return null;
        String[] t = raum.split("\\.");
        if (t.length < 3) return null;
        StringBuilder rest = new StringBuilder();
        for (int i = 3; i < t.length; i++) { if (rest.length() > 0) rest.append('.'); rest.append(t[i]); }
        // "08" → "8" (Lagepläne nummerieren ohne führende Null)
        String haus = t[1].matches("\\d+") ? String.valueOf(Integer.parseInt(t[1])) : t[1];
        return new String[]{t[0], haus, t[2], rest.toString()};
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

    static String uhrzeitText(long t) { return uhrzeit(t); }

    static String datumKurz(long t) {
        Calendar c = Calendar.getInstance(Locale.GERMANY);
        c.setTimeInMillis(t);
        return WT[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH) + ". " + MONATE[c.get(Calendar.MONTH)];
    }

    /** Markiert die Gebäude auf dem Plan (Ring + rotes Schild). */
    static Bitmap markieren(Bitmap plan, String campus, Map<String, List<String>> proHaus, Map<String, String> farben) {
        Map<String, float[]> POS = positionen(campus);
        Bitmap b = plan.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        float B = b.getWidth(), H = b.getHeight();
        int rotStandard = Color.parseColor("#E24B4A");
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(30);
        text.setFakeBoldText(true);
        text.setTextAlign(Paint.Align.CENTER);
        for (Map.Entry<String, List<String>> e : proHaus.entrySet()) {
            float[] f = POS.get(e.getKey());
            if (f == null) continue;
            // Farbe des Fachs (bei mehreren Terminen im Haus: der erste)
            String fh = farben.get(e.getKey());
            int rot = fh != null ? Color.parseColor(fh) : rotStandard;
            float x = f[0] * B, y = f[1] * H;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(64, Color.red(rot), Color.green(rot), Color.blue(rot)));
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

    /** Ergebnis der Verbindungssuche je Termin-Index (Campuswechsel davor); null = lädt noch, leere Liste = nichts gefunden */
    static final class VerbindungsStand {
        boolean fehler;
        List<Verbindungen.Verbindung> liste;
    }

    private static String verbindungHtml(Termin a, Termin b, VerbindungsStand stand) {
        String ziel = Campus.ausRaum(b.raum);
        long zeit = (b.start - a.ende) / 60000L;
        StringBuilder h = new StringBuilder("<div class=\"verb\"><div class=\"vk\">🚆 Campuswechsel nach ")
                .append(schutz(ziel != null ? Campus.name(ziel) : "?")).append("<span>").append(zeit).append(" Min Zeit</span></div>");
        if (stand == null) {
            h.append("<div class=\"vz\">Suche Verbindungen …</div>");
        } else if (stand.fehler) {
            h.append("<div class=\"vz\">Verbindungssuche gerade nicht erreichbar.</div>");
        } else if (stand.liste.isEmpty()) {
            h.append("<div class=\"vz\">Keine Verbindung gefunden.</div>");
        } else {
            boolean erste = true;
            for (Verbindungen.Verbindung v : stand.liste) {
                boolean puenktlich = v.an() <= b.start;
                long min = (v.an() - v.ab()) / 60000L;
                h.append("<details").append(erste && puenktlich ? " open" : "").append("><summary><b>")
                        .append(uhrzeit(v.ab())).append(" → ").append(uhrzeit(v.an())).append("</b> ")
                        .append(schutz(v.linien())).append(" <span class=\"").append(puenktlich ? "" : "spaet").append("\">")
                        .append(puenktlich ? min + " Min" : "zu spät").append("</span></summary>");
                for (Verbindungen.Abschnitt ab : v.abschnitte) {
                    h.append("<div class=\"vz\">").append(ab.zuFuss ? "🚶 " : ("bus".equals(ab.produkt) ? "🚌 " : "🚆 "))
                            .append(schutz(Verbindungen.text(ab))).append("</div>");
                }
                h.append("</details>");
                if (puenktlich) erste = false;
            }
        }
        String url = Orte.oepnvUrl(a.raum, b.raum);
        if (url != null) h.append("<a class=\"vl\" href=\"").append(schutz(url)).append("\">In Google Maps mit Bus &amp; Bahn</a>");
        return h.append("</div>").toString();
    }

    /** Pläne in der Reihenfolge der Termine; ohne Termine der aktuelle Campus */
    static List<String> campusListe(List<Termin> termine, String ersatz) {
        List<String> l = new ArrayList<>();
        for (Termin t : termine) {
            String c = Campus.ausRaum(t.raum);
            if (c != null && !t.ausfall && !l.contains(c)) l.add(c);
        }
        if (l.isEmpty()) l.add(ersatz);
        return l;
    }

    /** Baut die Seite mit Karten (Termine), Verbindungen bei Campuswechsel und den markierten Plänen. */
    static String html(List<Termin> termine, String titel, Map<String, Bitmap> plaene, String planerLink,
                       Map<Integer, VerbindungsStand> verbindungen) {
        Map<String, Map<String, List<String>>> proCampus = new LinkedHashMap<>();
        Map<String, Map<String, String>> farbenProCampus = new HashMap<>();
        StringBuilder karten = new StringBuilder();
        for (int i = 0; i < termine.size(); i++) {
            Termin t = termine.get(i);
            if (i > 0 && !t.ausfall && !termine.get(i - 1).ausfall) {
                Termin vorher = termine.get(i - 1);
                if (Orte.campusWechsel(vorher.raum, t.raum)) {
                    karten.append(verbindungHtml(vorher, t, verbindungen.get(i)));
                } else {
                    // Gehzeit vom vorherigen Termin, wenn das Haus wechselt
                    int min = Orte.gehminuten(vorher.raum, t.raum);
                    String[] rz = raumAufteilen(t.raum);
                    if (min > 0 && rz != null) {
                        long pause = (t.start - vorher.ende) / 60000L;
                        karten.append("<div class=\"weg\">🚶 ca. ").append(min).append(" Min zu Haus ").append(schutz(rz[1]))
                                .append(pause > 0 ? " (" + pause + " Min Pause)" : "").append("</div>");
                    }
                }
            }
            String[] kurs = Konfig.kurs(t.titel);
            String[] typ = Konfig.typ(t.typ);
            String nummer = termine.size() > 1 ? String.valueOf(i + 1) : null;
            String[] r = raumAufteilen(t.raum);
            String ort = schutz(t.raum);
            String einr = "";
            if (r != null) {
                String c = Campus.bekannt(r[0]) ? r[0] : null;
                ort = (c != null ? Campus.kurz(c) + " · " : "") + "Haus " + schutz(r[1]) + " · Raum " + schutz(r[2] + "." + r[3]) + " · " + etageText(r[2]);
                if (c != null) {
                    if (!positionen(c).containsKey(r[1])) {
                        ort += " · nicht auf dem Plan";
                    } else if (!t.ausfall) {
                        Map<String, List<String>> proHaus = proCampus.get(c);
                        if (proHaus == null) { proHaus = new LinkedHashMap<>(); proCampus.put(c, proHaus); }
                        List<String> l = proHaus.get(r[1]);
                        if (l == null) {
                            l = new ArrayList<>();
                            proHaus.put(r[1], l);
                            Map<String, String> fc = farbenProCampus.get(c);
                            if (fc == null) { fc = new HashMap<>(); farbenProCampus.put(c, fc); }
                            fc.put(r[1], kurs[1]);
                        }
                        l.add(nummer != null ? nummer : "Raum " + r[2] + "." + r[3]);
                    }
                    String[] e = einrichtungen(c).get(r[1]);
                    if (e != null) einr = schutz(String.join(" · ", e));
                }
            }
            String route = t.ausfall ? null : Orte.routeUrl(t.raum);
            karten.append("<div class=\"karte").append(t.ausfall ? " aus" : "").append("\" style=\"background:").append(kurs[1]).append("\">")
                    .append("<div class=\"oben\">").append(nummer != null ? "<span class=\"nr\">" + nummer + "</span>" : "")
                    .append(schutz(typ[0])).append(" · ").append(uhrzeit(t.start)).append("–").append(uhrzeit(t.ende))
                    .append(t.ausfall ? " <span class=\"marke\">FÄLLT AUS</span>" : "").append("</div>")
                    .append("<div class=\"titel\">").append(schutz(t.titel)).append("</div>")
                    .append(t.lehrperson != null && !t.lehrperson.isEmpty() ? "<div class=\"einr\">👤 " + schutz(t.lehrperson) + "</div>" : "")
                    .append("<div class=\"ort\">").append(ort).append("</div>")
                    .append(t.alterRaum != null ? "<div class=\"einr\">Geändert, ursprünglich " + schutz(t.alterRaum) + "</div>" : "")
                    .append(t.zeitGeaendert() && (t.originalStart != t.start || t.originalEnde != t.ende)
                            ? "<div class=\"einr\">Eigene Zeit, ursprünglich " + uhrzeit(t.originalStart) + "–" + uhrzeit(t.originalEnde) + "</div>" : "")
                    .append(einr.isEmpty() ? "" : "<div class=\"einr\">" + einr + "</div>")
                    .append("<div class=\"knoepfe\">")
                    .append(route != null ? "<a class=\"k\" href=\"" + schutz(route) + "\">🚶 Route</a>" : "")
                    .append("<a class=\"k\" href=\"aktion://ausfall/").append(i).append("\">")
                    .append(t.ausfall ? "↩ Findet statt" : "✕ Fällt aus").append("</a>")
                    .append("<a class=\"k\" href=\"aktion://raum/").append(i).append("\">✎ Raum ändern</a>")
                    .append("<a class=\"k\" href=\"aktion://zeit/").append(i).append("\">🕒 Zeit ändern</a>")
                    .append(t.ausfall || t.alterRaum != null || t.zeitGeaendert() ? "<a class=\"k\" href=\"aktion://zuruecksetzen/" + i + "\">⟲ Zurücksetzen</a>" : "")
                    .append("</div></div>");
        }
        StringBuilder bilder = new StringBuilder();
        for (Map.Entry<String, Bitmap> e : plaene.entrySet()) {
            Map<String, List<String>> proHaus = proCampus.get(e.getKey());
            Map<String, String> fc = farbenProCampus.get(e.getKey());
            Bitmap markiert = markieren(e.getValue(), e.getKey(), proHaus != null ? proHaus : new HashMap<>(),
                    fc != null ? fc : new HashMap<>());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            markiert.compress(Bitmap.CompressFormat.PNG, 100, bos);
            if (plaene.size() > 1 || termine.isEmpty()) {
                bilder.append("<h2>Campus ").append(schutz(Campus.name(e.getKey()))).append("</h2>");
            }
            bilder.append("<img class=\"plan\" src=\"data:image/png;base64,")
                    .append(Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)).append("\">");
        }
        StringBuilder wahl = new StringBuilder("<div class=\"wahl\">Plan: ");
        for (String c : Campus.ALLE) {
            wahl.append("<a href=\"aktion://plan/").append(c).append("\">").append(schutz(Campus.kurz(c))).append("</a>");
        }
        wahl.append("<a href=\"aktion://plan/auto\">passend</a></div>");
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=6\">"
                + "<style>:root{color-scheme:light dark}body{font-family:sans-serif;margin:0;padding:16px;background:#FFFFFF;color:#2C2C2A}"
                + "@media(prefers-color-scheme:dark){body{background:#1C1C1E;color:#F1EFE8}.plan{border-color:#3A3A3C}.verb{background:#2C2C2E!important}}"
                + "h1{font-size:22px;margin:4px 0 12px}h2{font-size:17px;margin:14px 0 4px}.karte{border-radius:14px;padding:12px 14px;color:#fff;margin-bottom:10px}"
                + ".oben{font-size:13px;opacity:.9;font-weight:600}.nr{display:inline-block;background:#fff;color:#E24B4A;border-radius:10px;min-width:20px;text-align:center;margin-right:6px}"
                + ".titel{font-size:18px;font-weight:700;margin:3px 0}.ort{font-size:15px;font-weight:600}.einr{font-size:13px;opacity:.85;margin-top:4px}"
                + ".plan{width:100%;border-radius:14px;border:1px solid #D3D1C7;background:#fff;margin-top:6px}"
                + "a.knopf{display:block;text-align:center;margin:14px 0 4px;padding:12px;border-radius:12px;background:#185FA5;color:#fff;text-decoration:none;font-weight:600}"
                + ".hinweis{font-size:12px;color:#888780;text-align:center;margin-top:8px}.leer{color:#888780}"
                + ".aus{opacity:.5}.aus .titel{text-decoration:line-through}.marke{background:rgba(255,255,255,.25);border-radius:8px;padding:1px 6px;font-size:11px}"
                + ".knoepfe{margin-top:10px;display:flex;flex-wrap:wrap;gap:8px}.k{background:rgba(255,255,255,.22);color:#fff;text-decoration:none;border-radius:16px;padding:6px 12px;font-size:14px;font-weight:600}"
                + ".weg{font-size:13px;color:#888780;margin:-4px 0 8px 4px}"
                + ".verb{background:#F1EFE8;border-radius:12px;padding:10px 12px;margin:-2px 0 10px;font-size:13px}"
                + ".vk{font-weight:700;margin-bottom:6px}.vk span{float:right;font-weight:400;color:#888780}"
                + "details{margin:4px 0}summary{cursor:pointer}.vz{color:#888780;font-size:12px;margin:3px 0 0 6px}.spaet{color:#E24B4A}"
                + "a.vl{display:inline-block;margin-top:8px;color:#185FA5;font-weight:600;text-decoration:none}"
                + ".wahl{font-size:13px;color:#888780;margin:12px 0 0}.wahl a{margin-left:10px;color:#185FA5;text-decoration:none;font-weight:600}</style></head><body>"
                + "<h1>" + schutz(titel) + "</h1>"
                + (karten.length() > 0 ? karten.toString() : "<p class=\"leer\">Keine Termine</p>")
                + bilder
                + "<p class=\"hinweis\">Zum Vergrößern mit zwei Fingern zoomen.</p>"
                + wahl
                + (planerLink != null && !planerLink.isEmpty() ? "<a class=\"knopf\" href=\"" + schutz(planerLink) + "\">Im Planer öffnen</a>" : "")
                + "</body></html>";
    }
}
