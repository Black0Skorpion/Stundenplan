import Foundation
import SwiftUI

/// Abfahrten ab Golm über die freie VBB-Schnittstelle (v6.vbb.transport.rest, ohne Anmeldung).
enum Abfahrten {
    struct Haltestelle: Hashable {
        let id: String
        let name: String
    }

    static let haltestellen = [
        Haltestelle(id: "900220010", name: "Golm Bhf"),
        Haltestelle(id: "900220365", name: "Science Park/Uni"),
    ]

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

    /// Beide Haltestellen zusammen, nach Zeit sortiert. nil = nicht erreichbar und nichts zwischengespeichert.
    static func laden(minuten: Int = 90) async -> [Abfahrt]? {
        var alle: [Abfahrt] = []
        var irgendwasGeladen = false
        await withTaskGroup(of: [Abfahrt]?.self) { gruppe in
            for h in haltestellen {
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
            return Speicher.lesen([Abfahrt].self, "abfahrten.json")?.filter { $0.zeit > Date().addingTimeInterval(-60) }
        }
        // Dieselbe Fahrt hält oft an beiden Haltestellen – nur einmal zeigen (die frühere)
        var gesehen = Set<String>()
        let ergebnis = alle.sorted { $0.zeit < $1.zeit }.filter { gesehen.insert($0.id).inserted }
        Speicher.schreiben(ergebnis, "abfahrten.json")
        return ergebnis
    }

    private static func laden(_ h: Haltestelle, minuten: Int) async -> [Abfahrt]? {
        guard let url = URL(string: "https://v6.vbb.transport.rest/stops/\(h.id)/departures?duration=\(minuten)&results=25&remarks=false") else { return nil }
        var req = URLRequest(url: url)
        req.timeoutInterval = 12
        guard let (d, antwort) = try? await URLSession.shared.data(for: req),
              ((antwort as? HTTPURLResponse)?.statusCode ?? 200) < 400,
              let api = try? JSONDecoder().decode(ApiAntwort.self, from: d) else { return nil }
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
                .background(RoundedRectangle(cornerRadius: 4).fill(Abfahrten.farbe(a)))
            VStack(alignment: .leading, spacing: 0) {
                Text(Abfahrten.kurzRichtung(a.richtung))
                    .font(.system(size: kompakt ? 11 : 12, weight: .semibold))
                    .foregroundStyle(farben.vorder)
                    .strikethrough(a.ausfall)
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
