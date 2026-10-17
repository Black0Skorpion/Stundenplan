package de.stundenplan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Wochenraster (Mo–Fr) wie in der iOS-App: Termine antippen bzw. gedrückt halten, Tagesköpfe antippen,
 * nach links/rechts wischen blättert die Woche.
 */
public class WochenView extends View {

    public interface Aktion {
        void termin(Termin t);
        default void lang(Termin t) {}
        default void tag(long tag) {}
        default void blaettern(int richtung) {}
    }

    private final float d;
    private final boolean dunkel;
    private long montag;
    private long jetzt = System.currentTimeMillis();
    private List<Termin> termine = new ArrayList<>();
    private List<Zeichner.Treffer> treffer = new ArrayList<>();
    private Aktion aktion;
    private final GestureDetector gesten;

    public WochenView(Context ctx) {
        super(ctx);
        d = ctx.getResources().getDisplayMetrics().density;
        dunkel = new Ui(ctx).dunkel;
        gesten = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Zeichner.Treffer t = finde(e);
                if (t == null || aktion == null) return false;
                if (t.termin != null) aktion.termin(t.termin); else aktion.tag(t.tag);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Zeichner.Treffer t = finde(e);
                if (t != null && t.termin != null && aktion != null) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    aktion.lang(t.termin);
                }
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || aktion == null) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > 80 * d && Math.abs(dx) > 1.5f * Math.abs(dy)) {
                    aktion.blaettern(dx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
    }

    public void setze(long montag, List<Termin> termine, Aktion aktion) {
        this.montag = montag;
        this.termine = termine;
        this.aktion = aktion;
        this.jetzt = System.currentTimeMillis();
        invalidate();
    }

    private Zeichner.Treffer finde(MotionEvent e) {
        float x = e.getX() / d, y = e.getY() / d;
        // Termine vor Tagesköpfen; bei Überlappung der zuletzt gezeichnete
        Zeichner.Treffer bester = null;
        for (Zeichner.Treffer t : treffer) {
            if (t.rect.contains(x, y) && (bester == null || bester.termin == null || t.termin != null)) bester = t;
        }
        return bester;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.save();
        c.scale(d, d);
        treffer = Zeichner.woche(c, getWidth() / d, getHeight() / d, dunkel, jetzt, montag, termine);
        c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean r = gesten.onTouchEvent(e);
        if (e.getAction() == MotionEvent.ACTION_DOWN && getParent() != null) {
            // Wischen zum Blättern nicht an die ScrollView abgeben
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return r || super.onTouchEvent(e);
    }

    /** Montag der Woche zu t (am Wochenende die nächste Woche), plus versatz Wochen */
    static long montag(long t, int versatz) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        int wt = (c.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7; // Mo = 0
        c.add(java.util.Calendar.DAY_OF_MONTH, -wt);
        if (wt >= 5) c.add(java.util.Calendar.DAY_OF_MONTH, 7);
        c.add(java.util.Calendar.DAY_OF_MONTH, 7 * versatz);
        return c.getTimeInMillis();
    }
}
