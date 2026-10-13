package de.stundenplan.widget;

/** Mensa-Widget, Größe: Mittel */
public class MensaMittel extends MensaWidget {
    @Override
    protected String modus() {
        return Zeichner.MITTEL;
    }

    @Override
    protected int layout() {
        return R.layout.widget_mensa;
    }
}
