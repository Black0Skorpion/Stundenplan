import Foundation

/// Die drei Standorte der Uni Potsdam. Der Rohwert ist die erste Zahl der Raumnummer („2.70.0.10“ → Golm).
enum Campus: String, Codable, CaseIterable, Identifiable {
    case neuesPalais = "1"
    case golm = "2"
    case griebnitzsee = "3"

    var id: String { rawValue }

    var name: String {
        switch self {
        case .neuesPalais: return "Am Neuen Palais"
        case .golm: return "Golm"
        case .griebnitzsee: return "Griebnitzsee"
        }
    }

    /// Für enge Stellen (Widget-Kopf, Knöpfe)
    var kurz: String {
        switch self {
        case .neuesPalais: return "Neues Palais"
        case .golm: return "Golm"
        case .griebnitzsee: return "Griebnitzsee"
        }
    }

    /// Haltestellen in Laufweite (VBB-Nummern)
    var haltestellen: [Abfahrten.Haltestelle] {
        switch self {
        case .golm:
            return [Abfahrten.Haltestelle(id: "900220010", name: "Golm Bhf", suche: "Bahnhof Potsdam-Golm"),
                    Abfahrten.Haltestelle(id: "900220365", name: "Science Park/Uni", suche: "Haltestelle Science Park/Universität Potsdam Golm")]
        case .neuesPalais:
            return [Abfahrten.Haltestelle(id: "900230133", name: "Campus Universität", suche: "Haltestelle Campus Universität/A.-Geiger-Kolleg Potsdam"),
                    Abfahrten.Haltestelle(id: "900230132", name: "Neues Palais", suche: "Haltestelle Neues Palais Potsdam"),
                    Abfahrten.Haltestelle(id: "900230007", name: "Park Sanssouci Bhf", suche: "Bahnhof Potsdam Park Sanssouci")]
        case .griebnitzsee:
            return [Abfahrten.Haltestelle(id: "900230003", name: "Griebnitzsee Bhf", suche: "S Griebnitzsee Bhf Potsdam")]
        }
    }

    /// Ungefähre Mitte des Campus (falls ein Haus keine Koordinate hat)
    var mittelpunkt: (lat: Double, lon: Double) {
        switch self {
        case .neuesPalais: return (52.4007, 13.0112)
        case .golm: return (52.4083, 12.9765)
        case .griebnitzsee: return (52.3932, 13.1285)
        }
    }

    /// OpenMensa-Nummer der Mensa am Campus
    var mensaID: Int {
        switch self {
        case .neuesPalais: return 55
        case .golm: return 61
        case .griebnitzsee: return 62
        }
    }

    var mensaName: String {
        switch self {
        case .neuesPalais: return "Mensa Neues Palais"
        case .golm: return "Mensa Golm"
        case .griebnitzsee: return "Mensa Griebnitzsee"
        }
    }

    /// Campus eines Raums („1.08.0.58“ → Am Neuen Palais)
    static func aus(raum: String) -> Campus? {
        guard let r = RaumTeile(raum) else { return nil }
        return Campus(rawValue: r.campus)
    }

    /// Campus laut Einstellung (bei „Automatisch“ aus dem Stundenplan abgeleitet)
    static var aktuell: Campus {
        Speicher.einstellungen().campus.fest ?? automatisch(Speicher.termine(), Date())
    }

    /// Wo bin ich (gleich)? Laufende/nächste Veranstaltung heute → sonst die letzte von heute (bis 2 Std. danach)
    /// → sonst die nächste überhaupt → sonst der häufigste Campus im Plan → sonst Golm.
    static func automatisch(_ termine: [Termin], _ jetzt: Date) -> Campus {
        let mitCampus = termine.filter { !$0.ausfall && aus(raum: $0.raum) != nil }.sorted { $0.start < $1.start }
        let heute = mitCampus.filter { Zeit.gleicherTag($0.start, jetzt) }
        if let t = heute.first(where: { $0.ende > jetzt }) { return aus(raum: t.raum)! }
        if let t = heute.last, jetzt.timeIntervalSince(t.ende) < 2 * 3600 { return aus(raum: t.raum)! }
        if let t = mitCampus.first(where: { $0.start > jetzt }) { return aus(raum: t.raum)! }
        var anzahl: [Campus: Int] = [:]
        for t in mitCampus { anzahl[aus(raum: t.raum)!, default: 0] += 1 }
        return anzahl.max { $0.value < $1.value }?.key ?? .golm
    }
}

/// Einstellung: fester Campus oder automatisch nach Stundenplan
enum CampusWahl: String, Codable, CaseIterable, Identifiable {
    case automatisch
    case golm
    case neuesPalais
    case griebnitzsee

    var id: String { rawValue }

    var fest: Campus? {
        switch self {
        case .automatisch: return nil
        case .golm: return .golm
        case .neuesPalais: return .neuesPalais
        case .griebnitzsee: return .griebnitzsee
        }
    }

    var name: String { fest?.name ?? "Automatisch" }
}
