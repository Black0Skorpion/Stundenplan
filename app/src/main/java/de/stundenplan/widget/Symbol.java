package de.stundenplan.widget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Schlichte Symbole für die Reiterleiste (selbst gezeichnet, in einer Farbe). Gezeichnet auf 24×24. */
final class Symbol extends Drawable {

    static final int PLAN = 0, MENSA = 1, ABFAHRTEN = 2, LAGEPLAN = 3, EINSTELLUNGEN = 4;

    private final int art;
    private final int groesse;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Symbol(int art, int farbe, int groessePx) {
        this.art = art;
        this.groesse = groessePx;
        p.setColor(farbe);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        setBounds(0, 0, groessePx, groessePx);
    }

    @Override public int getIntrinsicWidth() { return groesse; }
    @Override public int getIntrinsicHeight() { return groesse; }

    private void strich(float breite) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(breite);
    }

    private void voll() { p.setStyle(Paint.Style.FILL); }

    @Override
    public void draw(Canvas c) {
        c.save();
        float s = getBounds().width() / 24f;
        c.translate(getBounds().left, getBounds().top);
        c.scale(s, s);
        switch (art) {
            case PLAN: {
                strich(1.8f);
                c.drawRoundRect(new RectF(3.5f, 5, 20.5f, 20.5f), 3, 3, p);
                c.drawLine(3.5f, 9.5f, 20.5f, 9.5f, p);
                c.drawLine(8, 3, 8, 6.5f, p);
                c.drawLine(16, 3, 16, 6.5f, p);
                voll();
                for (int z = 0; z < 2; z++) for (int sp = 0; sp < 3; sp++) c.drawCircle(8 + sp * 4, 13.2f + z * 3.8f, 1.1f, p);
                break;
            }
            case MENSA: {
                strich(1.8f);
                // Gabel
                c.drawLine(6, 3, 6, 9, p);
                c.drawLine(9, 3, 9, 9, p);
                c.drawLine(12, 3, 12, 9, p);
                Path g = new Path();
                g.moveTo(6, 9);
                g.quadTo(6, 12, 9, 12);
                g.quadTo(12, 12, 12, 9);
                c.drawPath(g, p);
                c.drawLine(9, 12, 9, 21, p);
                // Messer
                Path m = new Path();
                m.moveTo(17.5f, 21);
                m.lineTo(17.5f, 3);
                m.quadTo(20.5f, 6, 20.5f, 12);
                m.lineTo(17.5f, 12);
                c.drawPath(m, p);
                break;
            }
            case ABFAHRTEN: {
                strich(1.8f);
                c.drawRoundRect(new RectF(5, 3, 19, 17.5f), 3.5f, 3.5f, p);
                c.drawLine(5, 10.5f, 19, 10.5f, p);
                c.drawLine(8, 21, 10, 17.5f, p);
                c.drawLine(16, 21, 14, 17.5f, p);
                voll();
                c.drawCircle(9, 14, 1.2f, p);
                c.drawCircle(15, 14, 1.2f, p);
                break;
            }
            case LAGEPLAN: {
                strich(1.8f);
                Path k = new Path();
                k.moveTo(3, 6);
                k.lineTo(9, 3.5f);
                k.lineTo(15, 6);
                k.lineTo(21, 3.5f);
                k.lineTo(21, 18);
                k.lineTo(15, 20.5f);
                k.lineTo(9, 18);
                k.lineTo(3, 20.5f);
                k.close();
                c.drawPath(k, p);
                c.drawLine(9, 3.5f, 9, 18, p);
                c.drawLine(15, 6, 15, 20.5f, p);
                break;
            }
            default: {
                // Zahnrad
                voll();
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45, 12, 12);
                    c.drawRoundRect(new RectF(10.4f, 2, 13.6f, 6), 0.8f, 0.8f, p);
                    c.restore();
                }
                strich(2.6f);
                c.drawCircle(12, 12, 6.2f, p);
                strich(1.8f);
                c.drawCircle(12, 12, 2.4f, p);
                break;
            }
        }
        c.restore();
    }

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
