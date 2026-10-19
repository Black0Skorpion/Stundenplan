import Foundation
import SwiftUI

/// Abfahrten am gewählten Campus über die freie VBB-Schnittstelle (v6.vbb.transport.rest, ohne Anmeldung).
enum Abfahrten {
    struct Haltestelle: Hashable {
        let id: String
        let name: String
        /// Suchbegriff für Apple Karten / Google Maps
        let suche: String
    }

    struct Abfahrt: Codable, Hashable, Identifiable {
        let id: String
        let zeit: Date
        let geplant: Date
        /// Verspätung in Minuten (nil = keine Echtzeitdaten)
        let verspaetung: Int?
        let linie: String
        let produkt: String
        let richtung: String
        let gleis: String?
        let haltestelle: String
        let ausfall: Bool

        var istZug: Bool { produkt == "regional" || produkt == "suburban" || produkt == "express" }
    }

    // ───────────── Laden ─────────────

    private struct ApiAntwort: Decodable { let departures: [ApiAbfahrt] }
    private struct ApiAbfahrt: Decodable {
        let tripId: String
        let when: String?
        let plannedWhen: String?
        let delay: Int?
        let platform: String?
        let plannedPlatform: String?
        let direction: String?
        let cancelled: Bool?
        let line: ApiLinie?
    }
    private struct ApiLinie: Decodable { let name: String?; let product: String? }

    private static func datum(_ s: String?) -> Date? {
        guard let s else { return nil }
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: s)
    }

    struct Ergebnis {
        /// Abfahrten (frisch geladen oder letzter Stand); nil = gar nichts vorhanden
        let liste: [Abfahrt]?
        /// true = gerade erfolgreich geladen, false = nur der gespeicherte Stand
        let frisch: Bool
    }

    /// Alle Haltestellen des Campus zusammen, nach Zeit sortiert (für Widgets und Siri).
    static func laden(minuten: Int = 90, campus: Campus = Campus.aktuell) async -> [Abfahrt]? {
        await aktualisieren(minuten: minuten, campus: campus).liste
    }

    /// Wie `laden`, sagt aber zusätzlich, ob die Daten frisch sind (für die App).
    static func aktualisieren(minuten: Int = 90, campus: Campus = Campus.aktuell) async -> Ergebnis {
        var alle: [Abfahrt] = []
        var irgendwasGeladen = false
        await withTaskGroup(of: [Abfahrt]?.self) { gruppe in
            for h in campus.haltestellen {
                gruppe.addTask { await laden(h, minuten: minuten) }
            }
            for await teil in gruppe {
                if let teil {
                    irgendwasGeladen = true
                    alle.append(contentsOf: teil)
                }
            }
        }
        if !irgendwasGeladen {
            return Ergebnis(liste: ausCache(campus), frisch: false)
        }
        // Dieselbe Fahrt hält oft an mehreren Haltestellen – nur einmal zeigen (die frühere)
        var gesehen = Set<String>()
        let ergebnis = alle.sorted { $0.zeit < $1.zeit }.filter { gesehen.insert($0.id).inserted }
        Speicher.schreiben(ergebnis, datei(campus))
        return Ergebnis(liste: ergebnis, frisch: true)
    }

    private static func datei(_ campus: Campus) -> String { "abfahrten-\(campus.rawValue).json" }

    /// Letzter gespeicherter Stand (ohne bereits abgefahrene)
    static func ausCache(_ campus: Campus = Campus.aktuell) -> [Abfahrt]? {
        Speicher.lesen([Abfahrt].self, datei(campus))?.filter { $0.zeit > Date().addingTimeInterval(-60) }
    }

    /// Wann der gespeicherte Stand geladen wurde
    static func stand(_ campus: Campus = Campus.aktuell) -> Date? {
        (try? FileManager.default.attributesOfItem(atPath: Speicher.datei(datei(campus)).path))?[.modificationDate] as? Date
    }

    /// Quellen: VBB, ersatzweise BVG (gleiche Haltestellen-Nummern, gleiches Datenformat)
    static let quellen = ["https://v6.vbb.transport.rest", "https://v6.bvg.transport.rest"]

    private static func laden(_ h: Haltestelle, minuten: Int) async -> [Abfahrt]? {
        var api: ApiAntwort?
        for (i, quelle) in quellen.enumerated() {
            guard let url = URL(string: "\(quelle)/stops/\(h.id)/departures?duration=\(minuten)&results=25&remarks=false"),
                  let (d, status) = await Netz.laden(url, versuche: Netz.imWidget ? 1 : (i == 0 ? 3 : 2)), status < 400,
                  let a = try? JSONDecoder().decode(ApiAntwort.self, from: d) else { continue }
            api = a
            break
        }
        guard let api else { return nil }
        return api.departures.compactMap { a in
            guard let geplant = datum(a.plannedWhen) ?? datum(a.when) else { return nil }
            let zeit = datum(a.when) ?? geplant
            return Abfahrt(id: a.tripId, zeit: zeit, geplant: geplant,
                           verspaetung: a.delay.map { $0 / 60 },
                           linie: a.line?.name ?? "?", produkt: a.line?.product ?? "bus",
                           richtung: a.direction ?? "", gleis: a.platform ?? a.plannedPlatform,
                           haltestelle: h.name, ausfall: a.cancelled ?? false)
        }
    }

    // ───────────── Priorisierte Linien ─────────────

    static var favoriten: [String] { Speicher.einstellungen().favoriten }

    static func istFavorit(_ a: Abfahrt, _ favoriten: [String]) -> Bool {
        favoriten.contains { $0.caseInsensitiveCompare(a.linie) == .orderedSame }
    }

    /// Für knappe Anzeigen (Widgets): priorisierte Linien kommen auf jeden Fall rein,
    /// der Rest wird aufgefüllt; angezeigt wird weiter nach Zeit sortiert.
    static func auswahl(_ liste: [Abfahrt], anzahl: Int, favoriten: [String]) -> [Abfahrt] {
        let fav = liste.filter { istFavorit($0, favoriten) }.prefix(max(1, anzahl / 2))
        let rest = liste.filter { a in !fav.contains(a) }.prefix(anzahl - fav.count)
        return (Array(fav) + Array(rest)).sorted { $0.zeit < $1.zeit }
    }

    // ───────────── Darstellung ─────────────

    /// "Potsdam, Charlottenhof Bhf" → "Charlottenhof Bhf"
    static func kurzRichtung(_ r: String) -> String {
        var s = r
        for vorsilbe in ["Potsdam, ", "S+U ", "S "] where s.hasPrefix(vorsilbe) {
            s = String(s.dropFirst(vorsilbe.count))
        }
        return s.replacingOccurrences(of: " (Berlin)", with: "")
    }

    static func farbe(_ a: Abfahrt) -> Color {
        switch a.produkt {
        case "regional", "express": return Color(hex: 0xE2001A)   // Regionalbahn rot
        case "suburban": return Color(hex: 0x008D4F)              // S-Bahn grün
        case "tram": return Color(hex: 0xBE1414)
        default: return Color(hex: 0x95276E)                       // Bus (VBB-Lila)
        }
    }
}

/// Eine Zeile: [Linie] Richtung … Zeit (+Verspätung)
struct AbfahrtZeile: View {
    let abfahrt: Abfahrten.Abfahrt
    let jetzt: Date
    let farben: Farben
    var kompakt = false
    /// Priorisierte Linie (Stern vor der Richtung)
    var favorit = false

    var body: some View {
        let a = abfahrt
        let min = Int(a.zeit.timeIntervalSince(jetzt) / 60)
        HStack(spacing: 7) {
            Text(a.linie)
                .font(.system(size: kompakt ? 10 : 11, weight: .bold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(width: kompakt ? 32 : 38, height: kompakt ? 16 : 18)
                .farbFlaeche(RoundedRectangle(cornerRadius: 4), Abfahrten.farbe(a))
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 3) {
                    if favorit {
                        Image(systemName: "star.fill")
                            .font(.system(size: kompakt ? 8 : 9))
                            .foregroundStyle(Color(hex: 0xE0A100))
                    }
                    Text(Abfahrten.kurzRichtung(a.richtung))
                        .font(.system(size: kompakt ? 11 : 12, weight: .semibold))
                        .foregroundStyle(farben.vorder)
                        .strikethrough(a.ausfall)
                }
                if !kompakt {
                    Text(a.haltestelle + (a.gleis.map { " · Gl. \($0)" } ?? ""))
                        .font(.system(size: 9))
                        .foregroundStyle(farben.gedimmt)
                }
            }
            .lineLimit(1)
            Spacer(minLength: 2)
            VStack(alignment: .trailing, spacing: 0) {
                if a.ausfall {
                    Text("fällt aus").foregroundStyle(Palette.jetzt)
                } else {
                    Text(min <= 0 ? "jetzt" : "\(min) Min")
                        .foregroundStyle(farben.vorder)
                }
                HStack(spacing: 2) {
                    Text(Zeit.uhrzeit(a.geplant)).foregroundStyle(farben.gedimmt)
                    if let v = a.verspaetung, v != 0 {
                        Text(v > 0 ? "+\(v)" : "\(v)")
                            .foregroundStyle(v >= 3 ? Palette.jetzt : Color(hex: 0x1D9E75))
                    }
                }
                .font(.system(size: 9, weight: .medium))
            }
            .font(.system(size: kompakt ? 11 : 12, weight: .semibold))
            .monospacedDigit()
            .lineLimit(1)
        }
        .opacity(a.ausfall ? 0.6 : 1)
    }
}
