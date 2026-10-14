import AppIntents
import WidgetKit
import SwiftUI

// ───────────────────────── Farbschema ─────────────────────────

enum ThemaOption: String, AppEnum, CaseIterable {
    case automatisch, hell, dunkel

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Farbschema"
    static var caseDisplayRepresentations: [ThemaOption: DisplayRepresentation] = [
        .automatisch: "Automatisch",
        .hell: "Hell",
        .dunkel: "Dunkel",
    ]

    func farben(_ schema: ColorScheme) -> Farben {
        switch self {
        case .hell: return .fuer(dunkel: false)
        case .dunkel: return .fuer(dunkel: true)
        case .automatisch: return .fuer(dunkel: schema == .dark)
        }
    }
}

// ───────────────────────── Stundenplan ─────────────────────────

struct StundenplanEinstellung: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Stundenplan"
    static var description = IntentDescription("Aktuelle Veranstaltung, Tagesplan und Woche.")

    @Parameter(title: "Farbschema", default: .automatisch)
    var thema: ThemaOption
}

// ───────────────────────── Sperrbildschirm ─────────────────────────

enum SperrVariante: String, AppEnum, CaseIterable {
    case standard, naechste, raum, countdown, tag, zeit

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Anzeige"
    static var caseDisplayRepresentations: [SperrVariante: DisplayRepresentation] = [
        .standard: "Standard",
        .naechste: "Nächster Termin",
        .raum: "Raum groß",
        .countdown: "Countdown",
        .tag: "Tagesübersicht (Rechteck)",
        .zeit: "Uhrzeit (Rund)",
    ]

    /// Passende Variante für die Widget-Form (nicht jede Variante gibt es in jeder Form)
    func fuer(_ familie: WidgetFamily) -> SperrVariante {
        switch familie {
        case .accessoryInline:
            return [.naechste, .raum, .countdown].contains(self) ? self : .naechste
        case .accessoryCircular:
            return [.countdown, .raum, .zeit].contains(self) ? self : .countdown
        default:
            return [.naechste, .raum, .tag].contains(self) ? self : .naechste
        }
    }
}

struct SperrEinstellung: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Stundenplan (Sperrbildschirm)"
    static var description = IntentDescription("Nächste Veranstaltung auf dem Sperrbildschirm.")

    @Parameter(title: "Anzeige", default: .standard)
    var variante: SperrVariante
}

// ───────────────────────── Mensa ─────────────────────────

enum MensaTagOption: String, AppEnum, CaseIterable {
    case automatisch, heute, morgen, plus1, plus2, plus3, plus4, montag, dienstag, mittwoch, donnerstag, freitag

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Tag"
    static var caseDisplayRepresentations: [MensaTagOption: DisplayRepresentation] = [
        .automatisch: "Automatisch (bis 15 Uhr heute)",
        .heute: "Immer heute",
        .morgen: "Nächster Öffnungstag",
        .plus1: "+1 Öffnungstag",
        .plus2: "+2 Öffnungstage",
        .plus3: "+3 Öffnungstage",
        .plus4: "+4 Öffnungstage",
        .montag: "Montag",
        .dienstag: "Dienstag",
        .mittwoch: "Mittwoch",
        .donnerstag: "Donnerstag",
        .freitag: "Freitag",
    ]

    var auswahl: Mensa.Auswahl {
        switch self {
        case .automatisch: return .automatisch(0)
        case .heute: return .heute
        case .morgen: return .morgen
        case .plus1: return .automatisch(1)
        case .plus2: return .automatisch(2)
        case .plus3: return .automatisch(3)
        case .plus4: return .automatisch(4)
        case .montag: return .wochentag(1)
        case .dienstag: return .wochentag(2)
        case .mittwoch: return .wochentag(3)
        case .donnerstag: return .wochentag(4)
        case .freitag: return .wochentag(5)
        }
    }
}

struct MensaEinstellung: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Mensa Golm"
    static var description = IntentDescription("Speiseplan der Mensa Golm. Mit den Pfeilen blätterst du durch die Tage.")

    @Parameter(title: "Tag", default: .automatisch)
    var tag: MensaTagOption

    @Parameter(title: "Farbschema", default: .automatisch)
    var thema: ThemaOption
}

/// Pfeile im Mensa-Widget: einen Öffnungstag vor/zurück (gilt bis Mitternacht)
struct MensaBlaettern: AppIntent {
    static var title: LocalizedStringResource = "Mensa-Tag wechseln"
    static var isDiscoverable = false

    @Parameter(title: "Richtung")
    var richtung: Int

    init() {}

    init(richtung: Int) {
        self.richtung = richtung
    }

    func perform() async throws -> some IntentResult {
        Speicher.setzeMensaVersatz(Speicher.mensaVersatz() + richtung)
        return .result()
    }
}
