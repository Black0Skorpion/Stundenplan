package de.stundenplan.widget;

import java.util.HashMap;
import java.util.Map;

/** Einstellungen: Kurznamen, Farben und Veranstaltungsarten. Hier anpassen. */
public final class Konfig {
    private Konfig() {}

    /** Termine, die zur vollen Stunde beginnen und enden (8–10 Uhr), als 8:15–9:45 anzeigen. */
    public static final boolean AKADEMISCHES_VIERTEL = true;
    public static final int STANDARD_START = 8;   // früheste angezeigte Stunde (großes Widget)
    public static final int STANDARD_ENDE = 18;   // späteste angezeigte Stunde (großes Widget)
    /** Wird beim Tippen aufs Widget geöffnet ("" = Stundenplan-App öffnen). */
    public static final String TAP_URL = "https://planner.joshuan.dev/de?s=ymoeYE_5okQN1490";

    /** Vollständiger Titel aus dem Kalender → { Kurzname, Farbe } */
    private static final Map<String, String[]> KURSE = new HashMap<>();
    static {
        KURSE.put("Grundlagen der Programmierung", new String[]{"GdP", "#378ADD"});
        KURSE.put("Grundlagen der Programmierung (Rechnerübung)", new String[]{"GdP-RÜ", "#185FA5"});
        KURSE.put("Mathematik für Informatik I", new String[]{"Mathe I", "#1D9E75"});
        KURSE.put("Mathematik für Informatik II", new String[]{"Mathe II", "#639922"});
        KURSE.put("Maschinenmodelle", new String[]{"MaMo", "#D85A30"});
        KURSE.put("Bioinformatik", new String[]{"Bioinf", "#7F77DD"});
        KURSE.put("Molekulare Evolutionsbiologie", new String[]{"MolEvo", "#D4537E"});
    }

    /** Farben für Veranstaltungen, die oben nicht eingetragen sind. */
    private static final String[] ERSATZ_FARBEN = {"#BA7517", "#993556", "#0F6E56", "#534AB7", "#5F5E5A"};

    /** Veranstaltungsart → { lange Bezeichnung, Kürzel } */
    private static final Map<String, String[]> TYPEN = new HashMap<>();
    static {
        TYPEN.put("Vorlesung", new String[]{"Vorlesung", "VL"});
        TYPEN.put("Übung", new String[]{"Übung", "Ü"});
        TYPEN.put("Zusatzübung", new String[]{"Zusatzübung", "ZÜ"});
        TYPEN.put("Tutorium", new String[]{"Tutorium", "Tut"});
        TYPEN.put("Seminar", new String[]{"Seminar", "Sem"});
        TYPEN.put("Praktikum", new String[]{"Praktikum", "Prak"});
    }

    static boolean istBekannterTyp(String typ) {
        return TYPEN.containsKey(typ);
    }

    private static final Map<String, String[]> ersatz = new HashMap<>();

    /** Liefert { Kurzname, Farbe } für einen Veranstaltungstitel. */
    public static synchronized String[] kurs(String titel) {
        String[] k = KURSE.get(titel);
        if (k != null) return k;
        k = ersatz.get(titel);
        if (k == null) {
            String kurz = titel;
            if (titel.length() > 10) {
                StringBuilder sb = new StringBuilder();
                for (String w : titel.trim().split("\\s+")) {
                    if (!w.isEmpty() && sb.length() < 6) sb.append(w.charAt(0));
                }
                kurz = sb.toString();
            }
            k = new String[]{kurz, ERSATZ_FARBEN[ersatz.size() % ERSATZ_FARBEN.length]};
            ersatz.put(titel, k);
        }
        return k;
    }

    /** Liefert { lange Bezeichnung, Kürzel } für eine Veranstaltungsart. */
    public static String[] typ(String typ) {
        String[] t = TYPEN.get(typ);
        if (t != null) return t;
        String kurz = typ.length() > 3 ? typ.substring(0, 3) : typ;
        return new String[]{typ, kurz};
    }
}
