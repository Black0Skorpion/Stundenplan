import Foundation
#if canImport(AlarmKit)
import AlarmKit
#endif

/// Geplante Wecker (von der App in wecker.json geschrieben) – damit die Widgets „Wecker 7:15“ zeigen können.
enum WeckerStand {
    struct Eintrag: Codable {
        let wann: Date
        let titel: String
        let start: Date
        let raum: String
    }

    static func alle() -> [Eintrag] {
        Speicher.lesen([Eintrag].self, "wecker.json") ?? []
    }

    /// Weckzeit für die erste Veranstaltung eines Tages (nil = kein Wecker)
    static func fuer(_ t: Termin) -> Date? {
        guard Speicher.einstellungen().wecker else { return nil }
        return alle().first { abs($0.start.timeIntervalSince(t.start)) < 60 && $0.wann > Date() }?.wann
    }
}

#if canImport(AlarmKit)
/// Zusatzdaten der Wecker (App und Widget, für die Schlummer-Anzeige)
@available(iOS 26.0, *)
struct WeckerDaten: AlarmMetadata {}
#endif
