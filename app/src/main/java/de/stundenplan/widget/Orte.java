package de.stundenplan.widget;

import java.util.HashMap;
import java.util.Map;

/**
 * GPS-Koordinaten der Häuser am Campus Golm (Quelle: OpenStreetMap; einige aus dem Lageplan
 * umgerechnet, Abweichung meist unter 20 m). Haus 60 und 62 fehlen bewusst.
 */
public final class Orte {
    private Orte() {}

    private static final Map<String, double[]> HAEUSER = new HashMap<>();
    static {
        double[][] w = {
                {1, 52.407483, 12.977766}, {2, 52.408040, 12.977722}, {4, 52.407946, 12.978802},
                {5, 52.408763, 12.979165}, {6, 52.409130, 12.978347}, {7, 52.408606, 12.978036},
                {8, 52.409095, 12.977112}, {9, 52.408388, 12.977198}, {10, 52.407919, 12.976578},
                {11, 52.407285, 12.976441}, {12, 52.408935, 12.979743}, {13, 52.406575, 12.977690},
                {14, 52.407557, 12.974072}, {15, 52.407182, 12.974515}, {16, 52.406950, 12.973302},
                {17, 52.407606, 12.973495}, {18, 52.408674, 12.973316}, {19, 52.406160, 12.975317},
                {20, 52.406271, 12.976781}, {24, 52.406796, 12.976390}, {25, 52.410074, 12.973810},
                {26, 52.410513, 12.975774}, {27, 52.409526, 12.975206}, {28, 52.409403, 12.973729},
                {29, 52.409063, 12.975092}, {30, 52.407654, 12.975229}, {31, 52.407279, 12.975312},
                {32, 52.408465, 12.974605}, {35, 52.406365, 12.976121}, {48, 52.408053, 12.972589},
                {70, 52.413289, 12.974407},
        };
        for (double[] e : w) HAEUSER.put(String.valueOf((int) e[0]), new double[]{e[1], e[2]});
    }

    static double[] koordinate(String raum) {
        String[] r = Lageplan.raumAufteilen(raum);
        if (r == null || !"2".equals(r[0])) return null;
        return HAEUSER.get(r[1]);
    }

    static double meter(double[] a, double[] b) {
        double r = 6_371_000.0;
        double p1 = Math.toRadians(a[0]), p2 = Math.toRadians(b[0]);
        double dp = p2 - p1, dl = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Geschätzte Gehzeit in Minuten (Luftlinie × 1,3, 75 m/min). -1 = unbekannt, 0 = selbes Haus */
    static int gehminuten(String von, String zu) {
        String[] a = Lageplan.raumAufteilen(von), b = Lageplan.raumAufteilen(zu);
        if (a == null || b == null) return -1;
        if (a[0].equals(b[0]) && a[1].equals(b[1])) return 0;
        double[] ka = koordinate(von), kb = koordinate(zu);
        if (ka == null || kb == null) return -1;
        return Math.max(1, (int) Math.round(meter(ka, kb) * 1.3 / 75));
    }

    /** Google Maps mit Fußweg zum Haus */
    static String routeUrl(String raum) {
        double[] k = koordinate(raum);
        if (k == null) return null;
        return "https://www.google.com/maps/dir/?api=1&travelmode=walking&destination=" + k[0] + "," + k[1];
    }
}
