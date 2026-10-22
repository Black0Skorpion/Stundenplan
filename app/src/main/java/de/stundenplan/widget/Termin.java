package de.stundenplan.widget;

/** Ein einzelner Termin (Zeiten in Millisekunden seit 1970, UTC). */
public final class Termin {
    public long start;
    public long ende;
    public final String titel;
    public final String typ;
    public String raum;
    /** Von Hand als ausgefallen markiert */
    public boolean ausfall = false;
    /** Ursprünglicher Raum, falls von Hand geändert */
    public String alterRaum = null;
    /** Ursprüngliche Zeiten (mit akademischem Viertel), falls von Hand geändert; 0 = nicht geändert */
    public long originalStart = 0, originalEnde = 0;
    /** Bei nach hinten verschobener Zeit: Beginn, nach dem sich der Wecker richtet; 0 = wie start */
    public long weckStart = 0;
    /** Lehrperson(en) laut PULS ("" = unbekannt) */
    public String lehrperson = "";

    /** Darstellung im Wochenraster: 0 normal, 1 Auswahlmöglichkeit (grau), 2 ausgeblendet, 3 Überschneidung */
    public int stil = 0;
    /** Zusatztext statt der Art (z. B. „Gr. 2“) */
    public String marke;
    /** Beliebiger Bezug für Tippen (z. B. Gruppe in der Modulwahl) */
    public Object bezug;

    // Layout-Hilfswerte für das Wochenraster
    int spalte = 0;
    int spalten = 1;
    int stapel = 0;
    int stapelAnzahl = 1;

    public Termin(long start, long ende, String titel, String typ, String raum) {
        this.start = start;
        this.ende = ende;
        this.titel = titel;
        this.typ = typ;
        this.raum = raum;
    }

    Termin mitZeiten(long neuStart, long neuEnde) {
        Termin n = new Termin(neuStart, neuEnde, titel, typ, raum);
        n.lehrperson = lehrperson;
        return n;
    }

    /** Bleibt gleich, auch wenn Raum oder Zeit geändert werden (wie in der iOS-App) */
    public String schluessel() {
        return ((originalStart != 0 ? originalStart : start) / 1000L) + "|" + titel;
    }

    public boolean zeitGeaendert() { return originalStart != 0; }

    /** Beginn, nach dem sich der Wecker richtet */
    public long weckBeginn() { return weckStart != 0 ? weckStart : start; }

    /** Reihe: gleiche Veranstaltung, gleiche Art, gleicher Wochentag, gleiche (ursprüngliche) Uhrzeit */
    public String reihe() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(originalStart != 0 ? originalStart : start);
        return titel + "|" + typ + "|" + c.get(java.util.Calendar.DAY_OF_WEEK) + "|"
                + (c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE));
    }
}
