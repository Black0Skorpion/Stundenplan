import Foundation

/// Lageplan Campus Golm (aus „2025_Lageplan_mit_Legende_Golm.pdf“).
enum Lageplan {
    /// Position der Hausnummer auf dem Plan (Anteil von Breite/Höhe)
    static let positionen: [String: (Double, Double)] = [
        "25": (0.3622, 0.4749), "28": (0.3706, 0.5459), "18": (0.3505, 0.6258), "14": (0.4879, 0.7238),
        "8": (0.6598, 0.5596), "11": (0.6606, 0.7432), "24": (0.6679, 0.7883), "19": (0.5668, 0.8721),
        "35": (0.6534, 0.8581), "20": (0.7211, 0.8428), "6": (0.7917, 0.5255), "5": (0.8661, 0.5624),
        "4": (0.8646, 0.6525), "1": (0.7804, 0.7095), "26": (0.5281, 0.4119), "10": (0.6594, 0.6776),
        "9": (0.7086, 0.6315), "2": (0.7602, 0.6604), "27": (0.4745, 0.5309), "29": (0.5251, 0.5677),
        "31": (0.5587, 0.7556), "30": (0.5462, 0.7166), "12": (0.9248, 0.5391), "17": (0.3888, 0.7199),
        "60": (0.8442, 0.1093), "13": (0.7916, 0.8074), "48": (0.2998, 0.6972), "32": (0.4710, 0.6365),
        "70": (0.3853, 0.0464), "16": (0.3861, 0.8130), "62": (0.0741, 0.2764), "7": (0.7796, 0.5907),
        "15": (0.4928, 0.7715),
    ]

    /// Einrichtungen laut Legende (Stand Mai 2025)
    static let einrichtungen: [String: [String]] = [
        "1": ["Infrastrukturelles/Technisches Gebäudemanagement", "Institut für Umweltwissenschaften und Geographie", "Leitzentrale"],
        "2": ["Potsdam Research Institute for Multilingualism (PRIM)"],
        "4": ["Mensa"],
        "5": ["Multimediaproduktion (ZIM)"],
        "6": ["Department Musik und Kunst"],
        "7": ["Wohnanlagen (Studierendenwerk)"],
        "8": ["Service Center (ZIM)", "Gebäudemanagement"],
        "9": ["Institut für Mathematik"],
        "10": ["EPI-Center", "Fuhrpark"],
        "11": ["Alte Sporthalle", "Lehreinheit Wirtschaft-Arbeit-Technik"],
        "12": ["Institut für Umweltwissenschaften und Geographie"],
        "13": ["Atelier – Werkstätten Kunst", "Fachbereich Kunst", "Pädagogische Werkstatt"],
        "14": ["Department Linguistik", "Department Psychologie", "Lesecafé", "Poststelle"],
        "16": ["Department Grundschulpädagogik"],
        "17": ["Wohnanlagen (Studierendenwerk)"],
        "18": ["Universitätsbibliothek (IKMZ)"],
        "19": ["Sporthalle"],
        "20": ["Institut für Biochemie und Biologie"],
        "24": ["Department Erziehungswissenschaften", "Department Sport- und Gesundheitswissenschaften", "Department Lehrerbildung", "Institut für Umweltwissenschaften und Geographie"],
        "25": ["Institut für Biochemie und Biologie", "Institut für Chemie"],
        "26": ["Institut für Biochemie und Biologie", "Institut für Chemie"],
        "27": ["Institut für Erd- und Umweltwissenschaften"],
        "28": ["Institut für Physik und Astronomie", "Dekanat Math.-Nat. Fakultät"],
        "29": ["Institut für Geowissenschaften", "Institut für Biochemie und Biologie", "Institut für Chemie", "Potsdam Transfer"],
        "30": ["Gewächshaus"],
        "31": ["Department Inklusionspädagogik"],
        "32": ["Earth & Environment Centre (EEC)", "Zentrum für Naturstoffgenomik"],
        "35": ["Dekanat Humanwissenschaftliche Fakultät"],
        "48": ["Infopoint"],
        "60": ["Institut für Biochemie und Biologie"],
        "62": ["Fakultät für Gesundheitswissenschaften", "Zentrum für Lehrerbildung (ZeLB)"],
        "70": ["Institut für Informatik und Computational Science", "Institut für Umweltwissenschaften und Geographie"],
    ]

    static let campus = ["1": "Am Neuen Palais", "2": "Golm", "3": "Griebnitzsee"]

    struct Markierung: Identifiable {
        let haus: String
        let label: String
        var id: String { haus }
    }

    /// Markierungen für eine Liste von Terminen; mehrere Termine im selben Haus werden zusammengefasst.
    static func markierungen(_ termine: [Termin]) -> [Markierung] {
        var proHaus: [String: [String]] = [:]
        var reihenfolge: [String] = []
        for (i, t) in termine.enumerated() {
            guard let r = RaumTeile(t.raum), r.campus == "2", positionen[r.haus] != nil else { continue }
            let label = termine.count > 1 ? "\(i + 1)" : "Raum \(r.rest)"
            if proHaus[r.haus] == nil { reihenfolge.append(r.haus) }
            proHaus[r.haus, default: []].append(label)
        }
        return reihenfolge.map { Markierung(haus: $0, label: proHaus[$0]!.joined(separator: " · ")) }
    }

    static func ortText(_ raum: String) -> String {
        guard let r = RaumTeile(raum) else { return raum }
        var s = "Haus \(r.haus) · Raum \(r.rest) · \(r.etageText)"
        if r.campus != "2" {
            s += " · Campus \(campus[r.campus] ?? r.campus) (nicht auf diesem Plan)"
        } else if positionen[r.haus] == nil {
            s += " · nicht auf dem Plan"
        }
        return s
    }

    static func einrichtungText(_ raum: String) -> String? {
        guard let r = RaumTeile(raum), r.campus == "2", let e = einrichtungen[r.haus] else { return nil }
        return e.joined(separator: " · ")
    }
}
