import Foundation

/// Welche Karten-App für Routen und Haltestellen geöffnet wird (Einstellungen → Karten).
enum KartenApp: String, Codable, CaseIterable, Identifiable {
    case apple
    case google

    var id: String { rawValue }
    var name: String {
        switch self {
        case .apple: return "Apple Karten"
        case .google: return "Google Maps"
        }
    }
}

/// Links in die gewählte Karten-App. Google-Links öffnen die Google-Maps-App, falls installiert, sonst die Webseite.
enum Karten {
    static var app: KartenApp { Speicher.einstellungen().karten }

    private static func kodiert(_ s: String) -> String {
        s.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
    }

    /// Fußweg zu einer Koordinate
    static func route(lat: Double, lon: Double, name: String) -> URL? {
        switch app {
        case .apple:
            return URL(string: "https://maps.apple.com/?daddr=\(lat),\(lon)&dirflg=w&q=\(kodiert(name))")
        case .google:
            return URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(lat),\(lon)&travelmode=walking")
        }
    }

    /// Mit Bus und Bahn von A nach B (für Campuswechsel)
    static func oepnv(von: (lat: Double, lon: Double), nach: (lat: Double, lon: Double), name: String) -> URL? {
        switch app {
        case .apple:
            return URL(string: "https://maps.apple.com/?saddr=\(von.lat),\(von.lon)&daddr=\(nach.lat),\(nach.lon)&dirflg=r&q=\(kodiert(name))")
        case .google:
            return URL(string: "https://www.google.com/maps/dir/?api=1&origin=\(von.lat),\(von.lon)&destination=\(nach.lat),\(nach.lon)&travelmode=transit")
        }
    }

    /// Ort suchen (z. B. eine Haltestelle – dort zeigen beide Apps die nächsten Abfahrten)
    static func suche(_ text: String) -> URL? {
        switch app {
        case .apple: return URL(string: "https://maps.apple.com/?q=\(kodiert(text))")
        case .google: return URL(string: "https://www.google.com/maps/search/?api=1&query=\(kodiert(text))")
        }
    }
}
