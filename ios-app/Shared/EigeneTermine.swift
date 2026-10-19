import Foundation

/// Eigene Termine (Plus im Plan): z. B. Lerngruppe, Sprechstunde, Klausur. Einmalig oder wiederkehrend.
/// Liegen getrennt vom PULS-/Datei-Stundenplan, werden beim Abgleich nicht überschrieben und nicht mitgeteilt.
enum EigeneTermine {
    struct Eintrag: Codable, Identifiable, Equatable {
        var id = UUID()
        var titel: String
        var typ: String
        var raum: String
        /// Erster Termin
        var start: Date
        var ende: Date
        /// 0 = einmalig, 7 = wöchentlich, 14 = alle zwei Wochen
        var abstandTage: Int = 0
        /// Letzter Tag der Wiederholung
        var bis: Date?
    }

    static let datei = "eigene-termine.json"
    static let arten = ["Termin", "Lerngruppe", "Sprechstunde", "Klausur", "Vorlesung", "Übung", "Tutorium", "Seminar", "Sport", "Arbeit"]

    static func alle() -> [Eintrag] {
        Speicher.lesen([Eintrag].self, datei) ?? []
    }

    static func speichern(_ e: [Eintrag]) {
        Speicher.schreiben(e, datei)
        Speicher.widgetsNeuLaden()
    }

    /// Alle einzelnen Termine (Wiederholungen ausgerollt, höchstens ein Jahr)
    static func termine() -> [Termin] {
        alle().flatMap(einzelne)
    }

    static func einzelne(_ e: Eintrag) -> [Termin] {
        let dauer = max(300, e.ende.timeIntervalSince(e.start))
        guard e.abstandTage > 0 else { return [Termin(start: e.start, ende: e.start.addingTimeInterval(dauer), titel: e.titel, typ: e.typ, raum: e.raum)] }
        let grenze = min(e.bis.map { Zeit.plusTage(Zeit.tagesBeginn($0), 1) } ?? e.start.addingTimeInterval(366 * 86400),
                         e.start.addingTimeInterval(366 * 86400))
        var aus: [Termin] = []
        var d = e.start
        while d < grenze && aus.count < 200 {
            aus.append(Termin(start: d, ende: d.addingTimeInterval(dauer), titel: e.titel, typ: e.typ, raum: e.raum))
            guard let n = Zeit.kalender.date(byAdding: .day, value: e.abstandTage, to: d) else { break }
            d = n
        }
        return aus
    }

    /// Gehört dieser (angezeigte) Termin zu einem eigenen Eintrag?
    static func eintrag(zu t: Termin) -> Eintrag? {
        alle().first { e in e.titel == t.titel && einzelne(e).contains { abs($0.start.timeIntervalSince(t.start)) < 1 } }
    }
}
