package de.stundenplan.widget;

/** Mensa-Widget, Größe: Gross */
public class MensaGross extends MensaWidget {
    @Override
    protected String modus() {
        return Zeichner.GROSS;
    }

    @Override
    protected int layout() {
        return R.layout.widget_mensa;
    }
}
