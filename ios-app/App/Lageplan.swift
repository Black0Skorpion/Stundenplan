import Foundation
import SwiftUI

/// Lagepläne der drei Campus (aus den Lageplänen der Uni mit Legende, Stand Mai 2025).
enum Lageplan {
    /// Bild im App-Paket
    static func bild(_ c: Campus) -> String {
        switch c {
        case .golm: return "lageplan_golm"
        case .neuesPalais: return "lageplan_neuespalais"
        case .griebnitzsee: return "lageplan_griebnitzsee"
        }
    }

    /// Seitenverhältnis (Breite/Höhe), falls das Bild nicht geladen werden kann
    static func seitenverhaeltnis(_ c: Campus) -> Double {
        switch c {
        case .golm: return 1090.0 / 1490.0
        case .neuesPalais: return 1095.0 / 1392.0
        case .griebnitzsee: return 1093.0 / 1876.0
        }
    }

    /// Position der Hausnummer auf dem Plan (Anteil von Breite/Höhe)
    static func positionen(_ c: Campus) -> [String: (Double, Double)] {
        switch c {
        case .golm: return Positionen.golm
        case .neuesPalais: return Positionen.neuesPalais
        case .griebnitzsee: return Positionen.griebnitzsee
        }
    }

    static func einrichtungen(_ c: Campus) -> [String: [String]] {
        switch c {
        case .golm: return Einrichtungen.golm
        case .neuesPalais: return Einrichtungen.neuesPalais
        case .griebnitzsee: return Einrichtungen.griebnitzsee
        }
    }

    enum Positionen {
        static let golm: [String: (Double, Double)] = [
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

        static let neuesPalais: [String: (Double, Double)] = [
            "1": (0.4184, 0.6985), "2": (0.3946, 0.6250), "3": (0.4171, 0.5592), "4": (0.3653, 0.3742),
            "5": (0.4167, 0.3052), "6": (0.2686, 0.5382), "8": (0.5062, 0.7122), "9": (0.6788, 0.5482),
            "10": (0.6950, 0.5870), "11": (0.6788, 0.3569), "12": (0.5087, 0.2373), "13": (0.1789, 0.2778),
            "14": (0.7835, 0.1708), "15": (0.7139, 0.1790), "16": (0.3444, 0.2031), "17": (0.2079, 0.2135),
            "19": (0.5400, 0.9017), "22": (0.3085, 0.6482), "34": (0.3229, 0.2889), "35": (0.6019, 0.5468),
            "36": (0.3032, 0.4321), "T1": (0.4939, 0.0577), "T2": (0.4270, 0.0577), "T3": (0.3613, 0.0577),
        ]

        /// Haus 13 und 14 liegen außerhalb (Karl-Marx-Straße, im Kreis unten links)
        static let griebnitzsee: [String: (Double, Double)] = [
            "1": (0.6388, 0.6253), "2": (0.4271, 0.4949), "3": (0.4469, 0.4214), "5": (0.2945, 0.3226),
            "6": (0.6342, 0.5202), "7": (0.5720, 0.3987), "13": (0.1209, 0.8324), "14": (0.2504, 0.8381),
        ]
    }

    /// Einrichtungen laut Legende
    enum Einrichtungen {
        static let golm: [String: [String]] = [
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

        static let neuesPalais: [String: [String]] = [
            "1": ["Institut für Künste und Medien", "Institut für Slavistik"],
            "2": ["Arbeitsmedizin", "AV- und IT-Dienste – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Dezernat 6: Zentrale Dienste", "Poststelle – Dezernat 6: Zentrale Dienste", "Sicherheitswesen"],
            "3": ["Dezernat 3: Personal- und Rechtsangelegenheiten", "Dezernat 4: Haushalt und Beschaffung"],
            "4": ["Dezernat 5: Bau- und Facilitymanagement"],
            "5": ["Institut für Germanistik"],
            "6": ["Koordinationsbüro für Chancengleichheit", "Multimediaproduktion – ZIM – Zentrum für Informationstechnologie und Medienmanagement", "Netzwerk Studienqualität Brandenburg (sqb)", "Personalvertretungen", "Studierendenvertretung – AStA", "Zentrum für Qualitätsentwicklung in Lehre und Studium (ZfQ)"],
            "8": ["Auditorium maximum „Audimax“", "Co-Working-Space", "Chief Information Officer (CIO)", "Dezernat 1: Planung, Statistik, Forschungsangelegenheiten", "Dezernat 2: Studienangelegenheiten", "Dezernat 3: Personal- und Rechtsangelegenheiten", "International Office", "Leitung, Verwaltung und Organisation – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Service Center – ZIM – Zentrum für Informationstechnologie und Medienmanagement", "Welcome Center Potsdam – International Office"],
            "9": ["Career-Service", "Eltern-Kind-Raum", "Geschäftsstelle des Senats", "International Summer Campus Office – International Office", "Kanzlerbüro", "Potsdam Transfer – Zentrale wissenschaftliche Einrichtung für Gründung, Innovation, Wissens- und Technologietransfer", "Präsident", "Präsidialamt", "Präsidium", "Referat für Presse- und Öffentlichkeitsarbeit", "Universitätsbibliothek"],
            "10": ["Universitätsbibliothek"],
            "11": ["Co-Working-Space", "Dekanat – Philosophische Fakultät", "Historisches Institut", "Institut für Jüdische Studien/Jewish Studies", "Institut für Lebensgestaltung – Ethik – Religionskunde", "Institut für Philosophie", "Institut für Religionswissenschaft", "Klassische Philologie", "Nil StudentInnenkeller", "Universitätsbibliothek"],
            "12": ["Department für Sport- und Gesundheitswissenschaften", "Eltern-Kind-Raum", "Hochschulambulanz", "Mensa – Studierendenwerk West:Brandenburg"],
            "13": ["Dezernat 5: Bau- und Facilitymanagement", "Infrastrukturelles Gebäudemanagement/Campusteam – Dezernat 5: Bau- und Facilitymanagement", "Zentrum für Hochschulsport"],
            "14": ["Abraham Geiger Kolleg (AGK)", "Zacharias Frankel College (ZFC)"],
            "15": ["Institut für Jüdische Theologie (School of Jewish Theology)", "Institut für Philosophie"],
            "19": ["Institut für Anglistik und Amerikanistik", "Institut für Romanistik", "Zentrum für Sprachen und Schlüsselkompetenzen (Zessko)"],
            "22": ["Dezernat 4: Haushalt und Beschaffung", "EDUC – Präsidialamt", "Nathan Peter Levinson Stiftung", "Service für Familien – Dezernat 3: Personal- und Rechtsangelegenheiten", "TK-Servicestelle", "Zentrum für Qualitätsentwicklung in Lehre und Studium (ZfQ)"],
            "34": ["Department für Sport- und Gesundheitswissenschaften", "Knetpunkt", "Umkleiden"],
            "35": ["Co-Working-Space", "Unishop"],
            "36": ["Department für Sport- und Gesundheitswissenschaften", "Zentrum für Hochschulsport"],
            "T1": ["Wohnanlagen – Studierendenwerk West:Brandenburg"],
            "T2": ["Wohnanlagen – Studierendenwerk West:Brandenburg"],
            "T3": ["Wohnanlagen – Studierendenwerk West:Brandenburg"],
        ]
        static let griebnitzsee: [String: [String]] = [
            "1": ["Dekanat – Juristische Fakultät", "Dekanat – Wirtschafts- und Sozialwissenschaftliche Fakultät", "Juristische Fakultät", "Mensa – Studierendenwerk West:Brandenburg", "Poststelle – Dezernat 6", "Service Center – ZIM – Zentrum für Informationstechnologie und Medienmanagement"],
            "2": ["Wirtschafts- und Sozialwissenschaftliche Fakultät"],
            "3": ["Infrastrukturelles Gebäudemanagement (IGM)/Campusteam Griebnitzsee – Dezernat 5: Bau- und Facilitymanagement", "Wirtschafts- und Sozialwissenschaftliche Fakultät"],
            "5": ["Universitätsbibliothek"],
            "6": ["AV- und IT-Dienste – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Mensa – Studierendenwerk West:Brandenburg", "Multimediaproduktion – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Studienbüro – Juristische Fakultät", "Zentrum für Sprachen und Schlüsselkompetenzen (Zessko)"],
            "7": ["Kommunalwissenschaftliches Institut", "Potsdam Transfer – Zentrale wissenschaftliche Einrichtung für Gründung, Innovation, Wissens- und Technologietransfer", "Universitätsgesellschaft Potsdam e. V.", "UP Transfer GmbH", "Wirtschafts- und Sozialwissenschaftliche Fakultät"],
            "13": ["Wirtschaftsinformatik"],
            "14": ["Demonstrationsgebäude – Wirtschaftsinformatik"],
        ]
    }

    struct Markierung: Identifiable {
        let haus: String
        let label: String
        /// Farbe des Fachs (bei mehreren Terminen im Haus: der erste)
        let farbe: Color
        var id: String { haus }
    }

    /// Markierungen für eine Liste von Terminen auf dem Plan eines Campus;
    /// mehrere Termine im selben Haus werden zusammengefasst. Nummern zählen über alle Termine.
    static func markierungen(_ termine: [Termin], campus: Campus, nummern: Bool) -> [Markierung] {
        var proHaus: [String: [String]] = [:]
        var farben: [String: Color] = [:]
        var reihenfolge: [String] = []
        let pos = positionen(campus)
        for (i, t) in termine.enumerated() {
            guard let r = RaumTeile(t.raum), r.campus == campus.rawValue, pos[r.haus] != nil else { continue }
            let label = nummern ? "\(i + 1)" : "Raum \(r.rest)"
            if proHaus[r.haus] == nil {
                reihenfolge.append(r.haus)
                farben[r.haus] = Kurse.info(t.titel).farbe
            }
            proHaus[r.haus, default: []].append(label)
        }
        return reihenfolge.map { Markierung(haus: $0, label: proHaus[$0]!.joined(separator: " · "), farbe: farben[$0] ?? Palette.jetzt) }
    }

    /// Welche Campus-Pläne für diese Termine gebraucht werden (in Reihenfolge des ersten Auftretens)
    static func campusListe(_ termine: [Termin]) -> [Campus] {
        var liste: [Campus] = []
        for t in termine {
            if let c = Campus.aus(raum: t.raum), !liste.contains(c) { liste.append(c) }
        }
        return liste
    }

    static func ortText(_ raum: String) -> String {
        guard let r = RaumTeile(raum) else { return raum }
        guard let c = Campus(rawValue: r.campus) else {
            return "Haus \(r.haus) · Raum \(r.rest) · \(r.etageText)"
        }
        var s = "\(c.kurz) · Haus \(r.haus) · Raum \(r.rest) · \(r.etageText)"
        if positionen(c)[r.haus] == nil { s += " · nicht auf dem Plan" }
        return s
    }

    static func einrichtungText(_ raum: String) -> String? {
        guard let r = RaumTeile(raum), let c = Campus(rawValue: r.campus), let e = einrichtungen(c)[r.haus] else { return nil }
        return e.joined(separator: " · ")
    }
}
