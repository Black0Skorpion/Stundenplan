import Foundation
import SwiftUI

/// Speiseplan der Mensa am gewählten Campus über OpenMensa (frei, ohne Anmeldung).
enum Mensa {
    static func api(_ c: Campus) -> String { "https://openmensa.org/api/v2/canteens/\(c.mensaID)" }
    static func webseite(_ c: Campus = Campus.aktuell) -> URL { URL(string: "https://openmensa.org/c/\(c.mensaID)")! }
    static var name: String { Campus.aktuell.mensaName }
    /// Ab dieser Stunde zeigt "Automatisch" schon den nächsten Öffnungstag.
    static let umschaltenUm = 15

    struct Tag: Codable, Hashable {
        let datum: Date
        let geschlossen: Bool
    }

    struct Gericht: Hashable, Identifiable {
        let name: String
        let kat: String
        let preis: Double?
        let farbe: UInt32
        var id: String { kat + "|" + name }
    }

    struct Speiseplan {
        var gerichte: [Gericht] = []
        var abend: [Gericht] = []
        var theke: [Gericht] = []
        var desserts: [Gericht] = []
        var leer: Bool { gerichte.isEmpty && abend.isEmpty }
        /// Hauptgerichte und Abendangebote zusammen (für kleine Ansichten)
        var alle: [Gericht] { gerichte + abend }
    }

    // ───────────── Laden (mit Zwischenspeicher für offline) ─────────────

    private struct ApiTag: Codable { let date: String; let closed: Bool }
    private struct ApiGericht: Codable {
        let name: String?
        let category: String?
        let prices: [String: Double?]?
    }

    private static func cacheDatei(_ c: Campus, _ name: String) -> URL {
        Speicher.datei("mensa-\(c.mensaID)-\(name).json")
    }

    private static func laden(_ pfad: String, cache: String, campus: Campus) async -> Data? {
        let cacheURL = cacheDatei(campus, cache)
        guard let url = URL(string: api(campus) + pfad) else { return nil }
        if let (daten, status) = await Netz.laden(url) {
            if status == 404 { return Data("[]".utf8) }   // kein Speiseplan für diesen Tag
            try? daten.write(to: cacheURL, options: .atomic)
            return daten
        }
        // Offline bzw. Dienst gestört: letzten Stand verwenden
        return try? Data(contentsOf: cacheURL)
    }

    /// Nur der zwischengespeicherte Stand (zum sofortigen Anzeigen beim Öffnen)
    static func speiseplanAusCache(_ tag: Date, campus: Campus = Campus.aktuell) -> Speiseplan? {
        guard let d = try? Data(contentsOf: cacheDatei(campus, Zeit.isoDatum(tag))),
              let liste = try? JSONDecoder().decode([ApiGericht].self, from: d) else { return nil }
        return aufbereiten(liste)
    }

    static func tage(campus: Campus = Campus.aktuell) async -> [Tag] {
        if let d = await laden("/days", cache: "tage", campus: campus),
           let liste = try? JSONDecoder().decode([ApiTag].self, from: d), !liste.isEmpty {
            return liste.compactMap { t in Zeit.ausIso(t.date).map { Tag(datum: $0, geschlossen: t.closed) } }
                .sorted { $0.datum < $1.datum }
        }
        // Ersatz: Mo–Fr der nächsten zwei Wochen
        let heute = Zeit.tagesBeginn(Date())
        return (0..<14).map { i in
            let d = Zeit.plusTage(heute, i)
            let wt = Zeit.wochentag(d)
            return Tag(datum: d, geschlossen: wt == 0 || wt == 6)
        }
    }

    /// nil = nicht erreichbar (und nichts zwischengespeichert)
    static func speiseplan(_ tag: Date, campus: Campus = Campus.aktuell) async -> Speiseplan? {
        let iso = Zeit.isoDatum(tag)
        guard let d = await laden("/days/\(iso)/meals", cache: iso, campus: campus),
              let liste = try? JSONDecoder().decode([ApiGericht].self, from: d) else { return nil }
        return aufbereiten(liste)
    }

    // ───────────── Aufbereiten ─────────────

    private static func kategorieKurz(_ k: String) -> String {
        var s = k
        if let r = s.range(of: #"\s*-\s*nur solange.*$"#, options: [.regularExpression, .caseInsensitive]) {
            s.removeSubrange(r)
        }
        return s.split(whereSeparator: \.isWhitespace).joined(separator: " ")
    }

    private static func nummer(_ kat: String) -> Int? {
        guard let r = kat.range(of: #"angebot\s*(\d+)"#, options: [.regularExpression, .caseInsensitive]) else { return nil }
        return Int(kat[r].filter(\.isNumber))
    }

    static func farbe(_ kat: String) -> UInt32 {
        let k = kat.lowercased()
        let farben: [Int: UInt32] = [1: 0x1D9E75, 2: 0x639922, 3: 0xD85A30, 4: 0x378ADD, 5: 0xBA7517]
        if let n = nummer(kat), k.hasPrefix("angebot") || k.hasPrefix("abend") { return farben[n] ?? 0x888780 }
        if k.contains("tagesangebot") { return 0x7F77DD }
        if k.contains("dessert") { return 0xD4537E }
        return 0x888780
    }

    private static func sortierung(_ kat: String) -> Int {
        let k = kat.lowercased()
        if let n = nummer(kat), k.hasPrefix("angebot") { return n }
        if k.contains("tagesangebot") { return 50 }
        return 90
    }

    private static func aufbereiten(_ liste: [ApiGericht]) -> Speiseplan {
        var p = Speiseplan()
        var abend: [Gericht] = []
        for m in liste {
            let kat = kategorieKurz(m.category ?? "")
            let name = (m.name ?? "").split(whereSeparator: \.isWhitespace).joined(separator: " ")
            let preis = m.prices?["students"] ?? nil
            let g = Gericht(name: name, kat: kat, preis: preis, farbe: farbe(kat))
            let k = kat.lowercased()
            if k.hasPrefix("abend") { abend.append(g) }
            else if k.contains("dessert") { p.desserts.append(g) }
            else if k.contains("theke") { p.theke.append(g) }
            else { p.gerichte.append(g) }
        }
        p.gerichte.sort { sortierung($0.kat) < sortierung($1.kat) }
        // Abendangebote nur, wenn sie sich vom Mittag unterscheiden
        p.abend = abend.filter { a in !p.gerichte.contains(where: { $0.name == a.name }) }
        return p
    }

    // ───────────── Texte ─────────────

    static func preisText(_ p: Double?) -> String {
        guard let p else { return "" }
        return String(format: "%.2f", p).replacingOccurrences(of: ".", with: ",") + " €"
    }

    static func kategorieLabel(_ kat: String) -> String {
        if kat.lowercased().hasPrefix("abend") {
            return kat.replacingOccurrences(of: #"^[Aa]bend\s*[Aa]ngebot\s*"#, with: "Abend ", options: .regularExpression)
        }
        if kat.lowercased().hasPrefix("tagesangebot") { return "Tagesang." }
        return kat
    }

    static func thekeText(_ theke: [Gericht]) -> String {
        theke.map { g in
            g.kat + " " + preisText(g.preis) + (g.name.range(of: #"100\s?g"#, options: .regularExpression) != nil ? "/100 g" : "")
        }.joined(separator: " · ")
    }

    static func tagesLabel(_ tag: Date, _ jetzt: Date) -> String {
        let diff = Zeit.kalender.dateComponents([.day], from: Zeit.tagesBeginn(jetzt), to: Zeit.tagesBeginn(tag)).day ?? 0
        if diff == 0 { return "Heute" }
        if diff == 1 { return "Morgen" }
        return Zeit.wochentageVoll[Zeit.wochentag(tag)]
    }

    // ───────────── Tag wählen ─────────────

    enum Auswahl: Hashable {
        case automatisch(Int)   // +N Öffnungstage nach dem automatisch gewählten Tag
        case heute
        case morgen
        case wochentag(Int)     // 1 = Montag … 5 = Freitag
    }

    static func waehle(_ a: Auswahl, _ tage: [Tag], _ jetzt: Date, versatz: Int = 0) -> Tag {
        let heute = Zeit.tagesBeginn(jetzt)
        let offen = tage.filter { !$0.geschlossen && $0.datum >= heute }
        func info(_ d: Date) -> Tag {
            if let t = tage.first(where: { Zeit.gleicherTag($0.datum, d) }) { return t }
            let wt = Zeit.wochentag(d)
            return Tag(datum: d, geschlossen: wt == 0 || wt == 6)
        }
        var basis: Tag
        switch a {
        case .heute:
            basis = info(heute)
        case .wochentag(let w):
            let diff = (w - Zeit.wochentag(heute) + 7) % 7
            basis = info(Zeit.plusTage(heute, diff))
        case .morgen:
            basis = offen.first(where: { $0.datum > heute }) ?? info(Zeit.plusTage(heute, 1))
        case .automatisch(let n):
            let stunde = Zeit.kalender.component(.hour, from: jetzt)
            let start = offen.firstIndex(where: { $0.datum > heute || stunde < umschaltenUm }) ?? offen.count
            basis = start + n < offen.count ? offen[start + n] : info(Zeit.plusTage(heute, 1 + n))
        }
        guard versatz != 0 else { return basis }
        // Mit den Pfeilen durch die Öffnungstage blättern
        let ab = offen.firstIndex(where: { $0.datum >= basis.datum }) ?? offen.count
        let ziel = ab + versatz
        if ziel >= 0 && ziel < offen.count { return offen[ziel] }
        if ziel < 0 { return offen.first ?? basis }
        return info(Zeit.plusTage(basis.datum, versatz))
    }

    static func naechsterOffener(_ tage: [Tag], nach: Tag) -> Tag {
        tage.first(where: { !$0.geschlossen && $0.datum > nach.datum })
            ?? Tag(datum: Zeit.plusTage(nach.datum, 1), geschlossen: false)
    }
}
