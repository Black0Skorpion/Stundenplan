package de.stundenplan.widget;

/** Ein einzelner Termin (Zeiten in Millisekunden seit 1970, UTC). */
public final class Termin {
    public final long start;
    public final long ende;
    public final String titel;
    public final String typ;
    public final String raum;

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
}
