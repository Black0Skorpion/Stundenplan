import Foundation

/// GPS-Koordinaten der Häuser am Campus Golm.
/// Quelle: OpenStreetMap (Gebäude „Haus N“). Häuser ohne Eintrag (≈) wurden aus ihrer Lage
/// auf dem Lageplan umgerechnet – Abweichung meist unter 20 m. Haus 60 und 62 fehlen bewusst
/// (außerhalb bzw. im Ausschnitt des Plans, keine verlässliche Position).
enum Orte {
    static let haeuser: [String: (lat: Double, lon: Double)] = [
        "1": (52.407483, 12.977766), "2": (52.408040, 12.977722), "4": (52.407946, 12.978802),
        "5": (52.408763, 12.979165), "6": (52.409130, 12.978347), "7": (52.408606, 12.978036),
        "8": (52.409095, 12.977112), "9": (52.408388, 12.977198), "10": (52.407919, 12.976578),
        "11": (52.407285, 12.976441), "12": (52.408935, 12.979743), "13": (52.406575, 12.977690), // ≈
        "14": (52.407557, 12.974072), "15": (52.407182, 12.974515), // ≈
        "16": (52.406950, 12.973302), "17": (52.407606, 12.973495),
        "18": (52.408674, 12.973316), // ≈ IKMZ
        "19": (52.406160, 12.975317), "20": (52.406271, 12.976781), "24": (52.406796, 12.976390),
        "25": (52.410074, 12.973810), "26": (52.410513, 12.975774), // ≈
        "27": (52.409526, 12.975206), "28": (52.409403, 12.973729), // 28 ≈
        "29": (52.409063, 12.975092), "30": (52.407654, 12.975229), "31": (52.407279, 12.975312),
        "32": (52.408465, 12.974605), // ≈
        "35": (52.406365, 12.976121), "48": (52.408053, 12.972589), // 48 ≈
        "70": (52.413289, 12.974407),
    ]

    static func koordinate(_ raum: String) -> (lat: Double, lon: Double)? {
        guard let r = RaumTeile(raum), r.campus == "2" else { return nil }
        return haeuser[r.haus]
    }

    static func meter(_ a: (lat: Double, lon: Double), _ b: (lat: Double, lon: Double)) -> Double {
        let r = 6_371_000.0
        let p1 = a.lat * .pi / 180, p2 = b.lat * .pi / 180
        let dp = p2 - p1, dl = (b.lon - a.lon) * .pi / 180
        let h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(min(1, sqrt(h)))
    }

    /// Geschätzte Gehzeit (Luftlinie × 1,3 Umweg, 75 m pro Minute). nil = unbekannt, 0 = selbes Haus
    static func gehminuten(von a: String, zu b: String) -> Int? {
        guard let ra = RaumTeile(a), let rb = RaumTeile(b) else { return nil }
        if ra.campus == rb.campus && ra.haus == rb.haus { return 0 }
        guard let ka = koordinate(a), let kb = koordinate(b) else { return nil }
        return max(1, Int((meter(ka, kb) * 1.3 / 75).rounded()))
    }

    /// Apple Karten mit Fußweg zum Haus
    static func kartenURL(_ raum: String) -> URL? {
        guard let k = koordinate(raum), let r = RaumTeile(raum) else { return nil }
        let name = "Uni Potsdam Haus \(r.haus)".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "https://maps.apple.com/?daddr=\(k.lat),\(k.lon)&dirflg=w&q=\(name)")
    }
}
