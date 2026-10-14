import ActivityKit
import Foundation

/// Daten der Live-Aktivität (Dynamic Island / Sperrbildschirm) für eine Veranstaltung
struct VorlesungAttribute: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var start: Date
        var ende: Date
        var titel: String
        var kurz: String
        var typ: String
        var raum: String
        var farbe: UInt32
        /// Nächster Termin danach (für die erweiterte Ansicht), z. B. "12:15 Mathe I · 2.70.0.10"
        var danach: String?
    }

    /// Schlüssel des Termins (damit dieselbe Aktivität nicht doppelt startet)
    var schluessel: String
}
