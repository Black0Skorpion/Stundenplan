package de.stundenplan.widget;

/** Mensa-Widget, Größe: Klein */
public class MensaKlein extends MensaWidget {
    @Override
    protected String modus() {
        return Zeichner.KLEIN;
    }

    @Override
    protected int layout() {
        return R.layout.widget_mensa_klein;
    }
}
