package de.stundenplan.widget;

/** Ein einzelner Termin (Zeiten in Millisekunden seit 1970, UTC). */
public final class Termin {
    public final long start;
    public final long ende;
    public final String titel;
    public final String typ;
    public String raum;
    /** Von Hand als ausgefallen markiert */
    public boolean ausfall = false;
    /** Ursprünglicher Raum, falls von Hand geändert */
    public String alterRaum = null;

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
        return new Termin(neuStart, neuEnde, titel, typ, raum);
    }

    /** Bleibt gleich, auch wenn der Raum geändert wird (wie in der iOS-App) */
    public String schluessel() {
        return (start / 1000L) + "|" + titel;
    }
}
