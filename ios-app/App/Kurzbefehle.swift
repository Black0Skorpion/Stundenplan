import AppIntents
import Foundation

/// „Hey Siri, wo ist meine nächste Vorlesung in Stundenplan?“
struct NaechsteVorlesung: AppIntent {
    static var title: LocalizedStringResource = "Nächste Vorlesung"
    static var description = IntentDescription("Sagt, wann und wo deine nächste Veranstaltung ist.")

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let jetzt = Date()
        guard let t = Plan.naechster(Speicher.termine(), jetzt) else {
            return .result(dialog: "Du hast keine weiteren Termine.")
        }
        return .result(dialog: "\(Sprache.satz(t, jetzt))")
    }
}

/// „Hey Siri, was habe ich heute in Stundenplan?“
struct HeutigerPlan: AppIntent {
    static var title: LocalizedStringResource = "Heutiger Plan"
    static var description = IntentDescription("Zählt die Veranstaltungen von heute (bzw. dem nächsten Tag) auf.")

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let jetzt = Date()
        guard let auswahl = Plan.tagesAuswahl(Speicher.termine(), jetzt) else {
            return .result(dialog: "Du hast keine weiteren Termine.")
        }
        let rest = auswahl.termine.filter { $0.ende > jetzt }
        let teile = rest.map { "\(Zeit.uhrzeit($0.start)) \($0.titel) in \(Sprache.raum($0.raum))" }
        let tag = auswahl.istHeute ? "Heute" : auswahl.label
        let text = "\(tag) \(rest.count == 1 ? "hast du einen Termin" : "hast du \(rest.count) Termine"): " + teile.joined(separator: ", ") + "."
        return .result(dialog: "\(text)")
    }
}

/// „Hey Siri, nächster Zug ab Golm in Stundenplan“
struct NaechsteAbfahrt: AppIntent {
    static var title: LocalizedStringResource = "Abfahrten ab Golm"
    static var description = IntentDescription("Nennt die nächsten Abfahrten ab Golm.")

    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard let liste = await Abfahrten.laden(), !liste.isEmpty else {
            return .result(dialog: "Die Abfahrten konnten gerade nicht geladen werden.")
        }
        let jetzt = Date()
        let teile = liste.filter { !$0.ausfall && $0.zeit > jetzt }.prefix(3).map { a -> String in
            let min = max(0, Int(a.zeit.timeIntervalSince(jetzt) / 60))
            return "\(a.linie) nach \(Abfahrten.kurzRichtung(a.richtung)) in \(min) Minuten"
        }
        return .result(dialog: "\(teile.joined(separator: ", ")).")
    }
}

struct StundenplanKurzbefehle: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: NaechsteVorlesung(),
            phrases: [
                "Wo ist meine nächste Vorlesung in \(.applicationName)",
                "Nächste Vorlesung in \(.applicationName)",
                "\(.applicationName) nächste Vorlesung",
            ],
            shortTitle: "Nächste Vorlesung",
            systemImageName: "calendar"
        )
        AppShortcut(
            intent: HeutigerPlan(),
            phrases: [
                "Was habe ich heute in \(.applicationName)",
                "\(.applicationName) heute",
            ],
            shortTitle: "Heutiger Plan",
            systemImageName: "list.bullet"
        )
        AppShortcut(
            intent: NaechsteAbfahrt(),
            phrases: [
                "Nächste Abfahrt in \(.applicationName)",
                "\(.applicationName) Abfahrten",
            ],
            shortTitle: "Abfahrten Golm",
            systemImageName: "tram"
        )
    }
}

/// Sätze zum Vorlesen
enum Sprache {
    static func raum(_ raum: String) -> String {
        guard let r = RaumTeile(raum) else { return "Raum \(raum)" }
        return "Haus \(r.haus), Raum \(r.rest)"
    }

    static func satz(_ t: Termin, _ jetzt: Date) -> String {
        if t.laeuft(jetzt) {
            return "Gerade läuft \(t.titel) in \(raum(t.raum)), noch bis \(Zeit.uhrzeit(t.ende))."
        }
        let wann: String
        if Zeit.gleicherTag(t.start, jetzt) {
            wann = "heute um \(Zeit.uhrzeit(t.start))"
        } else if Zeit.gleicherTag(t.start, Zeit.plusTage(jetzt, 1)) {
            wann = "morgen um \(Zeit.uhrzeit(t.start))"
        } else {
            wann = "am \(Zeit.wochentageVoll[Zeit.wochentag(t.start)]) um \(Zeit.uhrzeit(t.start))"
        }
        return "\(t.titel), \(Kurse.typ(t.typ).lang), \(wann) in \(raum(t.raum))."
    }
}
