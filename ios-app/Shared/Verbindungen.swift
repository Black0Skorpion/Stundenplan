import Foundation

/// Verbindungen zwischen zwei Veranstaltungen an verschiedenen Campus (VBB-Verbindungssuche, ohne Anmeldung).
/// Start und Ziel sind die Häuser selbst – Fußwege zur und von der Haltestelle sind also schon enthalten.
enum Verbindungen {
    struct Abschnitt: Codable, Hashable {
        let zuFuss: Bool
        let linie: String?
        let produkt: String?
        let richtung: String?
        let von: String?
        let nach: String?
        let ab: Date
        let an: Date
        let meter: Int?
        let gleis: String?
    }

    struct Verbindung: Codable, Hashable, Identifiable {
        let abschnitte: [Abschnitt]
        var id: String { abschnitte.map { "\($0.ab.timeIntervalSince1970)\($0.linie ?? "-")" }.joined() }
        var ab: Date { abschnitte.first?.ab ?? Date() }
        var an: Date { abschnitte.last?.an ?? Date() }
        var minuten: Int { Int(an.timeIntervalSince(ab) / 60) }
        /// Kurzform: "695 → RB21"
        var linien: String {
            let l = abschnitte.compactMap(\.linie)
            return l.isEmpty ? "zu Fuß" : l.joined(separator: " → ")
        }
    }

    // ───────────── Laden ─────────────

    private struct ApiAntwort: Decodable { let journeys: [ApiReise] }
    private struct ApiReise: Decodable { let legs: [ApiAbschnitt] }
    private struct ApiAbschnitt: Decodable {
        let walking: Bool?
        let distance: Int?
        let departure: String?
        let plannedDeparture: String?
        let arrival: String?
        let plannedArrival: String?
        let direction: String?
        let departurePlatform: String?
        let plannedDeparturePlatform: String?
        let origin: ApiOrt?
        let destination: ApiOrt?
        let line: ApiLinie?
    }
    private struct ApiOrt: Decodable { let name: String? }
    private struct ApiLinie: Decodable { let name: String?; let product: String? }

    private static func datum(_ s: String?) -> Date? {
        guard let s else { return nil }
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: s)
    }

    private static func ort(_ raum: String) -> (lat: Double, lon: Double, name: String)? {
        guard let c = Campus.aus(raum: raum), let r = RaumTeile(raum) else { return nil }
        let k = Orte.koordinate(raum) ?? c.mittelpunkt
        return (k.lat, k.lon, "Uni Potsdam \(c.name) Haus \(r.haus)")
    }

    /// Verbindungen ab Ende von `a` zum Raum von `b` (bis zu drei). nil = nicht erreichbar.
    static func suchen(von a: Termin, nach b: Termin) async -> [Verbindung]? {
        guard let start = ort(a.raum), let ziel = ort(b.raum) else { return nil }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime]
        var teile = URLComponents()
        teile.queryItems = [
            URLQueryItem(name: "from.latitude", value: String(start.lat)),
            URLQueryItem(name: "from.longitude", value: String(start.lon)),
            URLQueryItem(name: "from.address", value: start.name),
            URLQueryItem(name: "to.latitude", value: String(ziel.lat)),
            URLQueryItem(name: "to.longitude", value: String(ziel.lon)),
            URLQueryItem(name: "to.address", value: ziel.name),
            URLQueryItem(name: "departure", value: iso.string(from: max(a.ende, Date()))),
            URLQueryItem(name: "results", value: "3"),
            URLQueryItem(name: "stopovers", value: "false"),
            URLQueryItem(name: "remarks", value: "false"),
            URLQueryItem(name: "polylines", value: "false"),
            URLQueryItem(name: "tickets", value: "false"),
        ]
        // "+" in der Zeitzone muss kodiert werden
        let abfrage = (teile.percentEncodedQuery ?? "").replacingOccurrences(of: "+", with: "%2B")
        for quelle in Abfahrten.quellen {
            guard let url = URL(string: "\(quelle)/journeys?\(abfrage)"),
                  let (d, status) = await Netz.laden(url, versuche: 2), status < 400,
                  let api = try? JSONDecoder().decode(ApiAntwort.self, from: d) else { continue }
            return api.journeys.compactMap { reise in
                let abschnitte = reise.legs.compactMap { l -> Abschnitt? in
                    guard let ab = datum(l.departure) ?? datum(l.plannedDeparture),
                          let an = datum(l.arrival) ?? datum(l.plannedArrival) else { return nil }
                    return Abschnitt(zuFuss: l.walking ?? false, linie: l.line?.name, produkt: l.line?.product,
                                     richtung: l.direction, von: l.origin?.name, nach: l.destination?.name,
                                     ab: ab, an: an, meter: l.distance,
                                     gleis: l.departurePlatform ?? l.plannedDeparturePlatform)
                }
                return abschnitte.isEmpty ? nil : Verbindung(abschnitte: abschnitte)
            }
        }
        return nil
    }

    // ───────────── Texte ─────────────

    static func haltestelle(_ name: String?) -> String {
        guard let name else { return "Ziel" }
        return Abfahrten.kurzRichtung(name)
    }

    /// "12:09 RB21 Golm Bhf → 12:17 Park Sanssouci Bhf" bzw. "6 Min zu Fuß zu Science Park/Universität"
    static func text(_ a: Abschnitt) -> String {
        let min = max(1, Int(a.an.timeIntervalSince(a.ab) / 60))
        if a.zuFuss {
            return "\(min) Min zu Fuß" + (a.nach != nil ? " zu \(haltestelle(a.nach))" : " zum Haus")
        }
        let gleis = a.gleis.map { " (Gl. \($0))" } ?? ""
        return "\(Zeit.uhrzeit(a.ab)) \(a.linie ?? "?") ab \(haltestelle(a.von))\(gleis) → \(Zeit.uhrzeit(a.an)) \(haltestelle(a.nach))"
    }
}
