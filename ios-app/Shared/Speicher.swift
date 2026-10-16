import Foundation
#if canImport(WidgetKit)
import WidgetKit
#endif

/// Gemeinsamer Speicher von App und Widgets.
///
/// Beim Sideloading (iloader, SideStore, AltStore) wird die Bundle-ID um die Team-ID ergänzt,
/// z. B. "de.stundenplan.ios.ABCDE12345", und die App-Gruppe heißt "group.<diese ID>".
/// Deshalb wird die Gruppe zur Laufzeit gesucht. Gibt es keine, arbeitet jeder Teil für sich
/// (Widgets nutzen dann die eingebauten Termine).
enum Speicher {

    /// Bundle-ID der Haupt-App (auch wenn wir gerade im Widget laufen)
    static var hauptBundleID: String {
        let id = Bundle.main.bundleIdentifier ?? "de.stundenplan.ios"
        if Bundle.main.bundlePath.hasSuffix(".appex"), let punkt = id.lastIndex(of: ".") {
            return String(id[..<punkt])
        }
        return id
    }

    static var gruppenKandidaten: [String] {
        var k = ["group." + hauptBundleID]
        if !k.contains("group.de.stundenplan.ios") { k.append("group.de.stundenplan.ios") }
        return k
    }

    static let gruppenOrdner: URL? = {
        for id in gruppenKandidaten {
            if let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: id) {
                // Prüfen, ob wirklich geschrieben werden darf
                let test = url.appendingPathComponent(".test")
                if (try? Data("1".utf8).write(to: test)) != nil {
                    try? FileManager.default.removeItem(at: test)
                    return url
                }
            }
        }
        return nil
    }()

    static var geteilt: Bool { gruppenOrdner != nil }

    static var ordner: URL {
        if let g = gruppenOrdner { return g }
        let fm = FileManager.default
        let basis = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fm.temporaryDirectory
        try? fm.createDirectory(at: basis, withIntermediateDirectories: true)
        return basis
    }

    static func datei(_ name: String) -> URL { ordner.appendingPathComponent(name) }

    static func lesen<T: Decodable>(_ typ: T.Type, _ name: String) -> T? {
        guard let d = try? Data(contentsOf: datei(name)) else { return nil }
        let dec = JSONDecoder()
        dec.dateDecodingStrategy = .secondsSince1970
        return try? dec.decode(typ, from: d)
    }

    static func schreiben<T: Encodable>(_ wert: T, _ name: String) {
        let enc = JSONEncoder()
        enc.dateEncodingStrategy = .secondsSince1970
        if let d = try? enc.encode(wert) {
            try? d.write(to: datei(name), options: .atomic)
        }
    }

    // ───────────── Termine ─────────────

    private static let termineDatei = "termine.json"

    /// Alle Termine (eigener Import oder eingebaut), mit akademischem Viertel und eigenen Änderungen, sortiert.
    /// Ausgefallene Termine sind nur mit `mitAusfaellen` dabei (für die Anzeige in der App).
    static func termine(mitAusfaellen: Bool = false) -> [Termin] {
        let aenderungen = self.aenderungen()
        return rohTermine()
            .map(Plan.viertelAnpassen)
            .map { t -> Termin in
                guard let a = aenderungen[t.schluessel] else { return t }
                var n = t
                n.ausfall = a.ausfall
                if let r = a.raum, !r.isEmpty, r != t.raum {
                    n.alterRaum = t.raum
                    n.raum = r
                }
                return n
            }
            .filter { mitAusfaellen || !$0.ausfall }
            .sorted { $0.start < $1.start }
    }

    // ───────────── Eigene Änderungen (Ausfall, anderer Raum) ─────────────

    struct Aenderung: Codable {
        var ausfall: Bool = false
        var raum: String? = nil
        var leer: Bool { !ausfall && (raum ?? "").isEmpty }
    }

    static func aenderungen() -> [String: Aenderung] {
        lesen([String: Aenderung].self, "aenderungen.json") ?? [:]
    }

    static func aendere(_ t: Termin, _ aendern: (inout Aenderung) -> Void) {
        var alle = aenderungen()
        var a = alle[t.schluessel] ?? Aenderung()
        aendern(&a)
        alle[t.schluessel] = a.leer ? nil : a
        // Alte Einträge (vergangene Termine) aufräumen
        let grenze = Date().addingTimeInterval(-30 * 86400).timeIntervalSince1970
        alle = alle.filter { (Double($0.key.split(separator: "|").first ?? "") ?? .infinity) > grenze }
        schreiben(alle, "aenderungen.json")
        widgetsNeuLaden()
    }

    static func rohTermine() -> [Termin] {
        if let eigene = lesen([Termin].self, termineDatei), !eigene.isEmpty { return eigene }
        return eingebaut()
    }

    static var eigeneTermine: Bool { FileManager.default.fileExists(atPath: datei(termineDatei).path) }

    static func speichereTermine(_ termine: [Termin]) {
        schreiben(termine, termineDatei)
        widgetsNeuLaden()
    }

    static func eigeneLoeschen() {
        try? FileManager.default.removeItem(at: datei(termineDatei))
        widgetsNeuLaden()
    }

    /// Mitgelieferte Termine (WiSe 26/27) aus termine.txt
    static func eingebaut() -> [Termin] {
        guard let url = Bundle.main.url(forResource: "termine", withExtension: "txt"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return text.split(whereSeparator: \.isNewline).compactMap { zeile -> Termin? in
            if zeile.hasPrefix("#") { return nil }
            let t = zeile.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
            guard t.count >= 5, let s = ICS.utcKompakt(t[0]), let e = ICS.utcKompakt(t[1]) else { return nil }
            return Termin(start: s, ende: e, titel: t[2], typ: t[3], raum: t[4])
        }
    }

    // ───────────── Einstellungen ─────────────

    struct Einstellungen: Codable {
        var link: String = ""
        /// Mitteilung vor jeder Veranstaltung
        var erinnerungen: Bool = false
        var vorlaufMinuten: Int = 10
        /// Live-Aktivität (Dynamic Island) für die laufende Veranstaltung
        var liveAktivitaet: Bool = true
        /// Karten-App für Routen und Haltestellen
        var karten: KartenApp = .apple
        /// Campus für Abfahrten und Mensa
        var campus: CampusWahl = .automatisch
        /// Priorisierte Linien (z. B. "RB20") – stehen in den Abfahrten oben
        var favoriten: [String] = []
        /// Sperrbildschirm-Widgets ausblenden, solange die Live-Aktivität die Veranstaltung zeigt
        var sperrAusblenden: Bool = false

        init() {}

        // Fehlende Felder (ältere Versionen) bekommen Standardwerte
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            link = try c.decodeIfPresent(String.self, forKey: .link) ?? ""
            erinnerungen = try c.decodeIfPresent(Bool.self, forKey: .erinnerungen) ?? false
            vorlaufMinuten = try c.decodeIfPresent(Int.self, forKey: .vorlaufMinuten) ?? 10
            liveAktivitaet = try c.decodeIfPresent(Bool.self, forKey: .liveAktivitaet) ?? true
            karten = (try? c.decodeIfPresent(KartenApp.self, forKey: .karten)) ?? .apple
            campus = (try? c.decodeIfPresent(CampusWahl.self, forKey: .campus)) ?? .automatisch
            favoriten = (try? c.decodeIfPresent([String].self, forKey: .favoriten)) ?? []
            sperrAusblenden = (try? c.decodeIfPresent(Bool.self, forKey: .sperrAusblenden)) ?? false
        }
    }

    // ───────────── Laufende Live-Aktivität (für die Sperrbildschirm-Widgets) ─────────────

    /// Von der App geschrieben, wenn sie eine Live-Aktivität startet; die Widgets lesen es.
    struct LiveInfo: Codable {
        let schluessel: String
        let start: Date
        let ende: Date
    }

    static var liveInfo: LiveInfo? {
        guard let i = lesen(LiveInfo.self, "liveaktivitaet.json"), i.ende > Date() else { return nil }
        return i
    }

    static func setzeLiveInfo(_ i: LiveInfo?) {
        if let i {
            schreiben(i, "liveaktivitaet.json")
        } else {
            try? FileManager.default.removeItem(at: datei("liveaktivitaet.json"))
        }
        widgetsNeuLaden()
    }

    static func einstellungen() -> Einstellungen {
        lesen(Einstellungen.self, "einstellungen.json") ?? Einstellungen()
    }

    static func speichere(_ e: Einstellungen) {
        schreiben(e, "einstellungen.json")
        widgetsNeuLaden()
    }

    static var planerLink: URL {
        let eigener = einstellungen().link.trimmingCharacters(in: .whitespacesAndNewlines)
        if !eigener.isEmpty, let u = URL(string: eigener), u.scheme?.hasPrefix("http") == true { return u }
        return URL(string: Konfig.standardLink)!
    }

    // ───────────── Mensa-Blättern (Pfeile im Widget) ─────────────

    struct MensaVersatz: Codable {
        var datum: String
        var wert: Int
    }

    /// Versatz gilt nur für den heutigen Tag – um Mitternacht geht es wieder von vorn los.
    static func mensaVersatz() -> Int {
        guard let v = lesen(MensaVersatz.self, "mensa-versatz.json"),
              v.datum == Zeit.isoDatum(Date()) else { return 0 }
        return v.wert
    }

    static func setzeMensaVersatz(_ wert: Int) {
        schreiben(MensaVersatz(datum: Zeit.isoDatum(Date()), wert: max(0, wert)), "mensa-versatz.json")
    }

    static func widgetsNeuLaden() {
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }
}

// ───────────────────────── ICS ─────────────────────────

enum ICS {

    /// "20261012T1200Z" → Datum (UTC)
    static func utcKompakt(_ s: String) -> Date? {
        let z = Array(s)
        guard z.count >= 13,
              let j = Int(String(z[0..<4])), let mo = Int(String(z[4..<6])), let t = Int(String(z[6..<8])),
              let h = Int(String(z[9..<11])), let mi = Int(String(z[11..<13])) else { return nil }
        var k = Calendar(identifier: .gregorian)
        k.timeZone = TimeZone(identifier: "UTC")!
        return k.date(from: DateComponents(year: j, month: mo, day: t, hour: h, minute: mi))
    }

    private static func datum(_ wert: String, _ params: String) -> Date? {
        if params.uppercased().contains("VALUE=DATE") && !params.uppercased().contains("VALUE=DATE-TIME") { return nil }
        let w = Array(wert.trimmingCharacters(in: .whitespaces))
        guard w.count >= 13, w[8] == "T",
              let j = Int(String(w[0..<4])), let mo = Int(String(w[4..<6])), let t = Int(String(w[6..<8])),
              let h = Int(String(w[9..<11])), let mi = Int(String(w[11..<13])) else { return nil }
        let s = w.count >= 15 ? Int(String(w[13..<15])) ?? 0 : 0
        var k = Calendar(identifier: .gregorian)
        if w.last == "Z" {
            k.timeZone = TimeZone(identifier: "UTC")!
        } else if let tzid = params.split(separator: ";").first(where: { $0.uppercased().hasPrefix("TZID=") }),
                  let tz = TimeZone(identifier: String(tzid.dropFirst(5)).replacingOccurrences(of: "\"", with: "")) {
            k.timeZone = tz
        } else {
            k.timeZone = .current
        }
        return k.date(from: DateComponents(year: j, month: mo, day: t, hour: h, minute: mi, second: s))
    }

    private static func text(_ s: String) -> String {
        s.replacingOccurrences(of: "\\n", with: " ")
            .replacingOccurrences(of: "\\N", with: " ")
            .replacingOccurrences(of: "\\,", with: ",")
            .replacingOccurrences(of: "\\;", with: ";")
            .replacingOccurrences(of: "\\\\", with: "\\")
            .trimmingCharacters(in: .whitespaces)
    }

    private static func typErkennen(_ beschreibung: String, _ titel: String) -> String {
        let erster = beschreibung.components(separatedBy: "·").first?.trimmingCharacters(in: .whitespaces) ?? ""
        if Konfig.typen[erster] != nil { return erster }
        let alles = (beschreibung + " " + titel).lowercased()
        for typ in ["Zusatzübung", "Tutorium", "Übung", "Vorlesung", "Seminar", "Praktikum"] where alles.contains(typ.lowercased()) {
            return typ
        }
        return erster
    }

    static func parsen(_ inhalt: String) -> [Termin] {
        let entfaltet = inhalt
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\n ", with: "")
            .replacingOccurrences(of: "\n\t", with: "")
        var termine: [Termin] = []
        var ev: [String: String]? = nil
        var start: Date?, ende: Date?

        for zeile in entfaltet.components(separatedBy: "\n") {
            if zeile == "BEGIN:VEVENT" { ev = [:]; start = nil; ende = nil; continue }
            if zeile == "END:VEVENT" {
                if let e = ev, let s = start, let en = ende {
                    let titel = e["SUMMARY"] ?? "Termin"
                    termine.append(Termin(start: s, ende: en, titel: titel,
                                          typ: typErkennen(e["DESCRIPTION"] ?? "", titel),
                                          raum: e["LOCATION"] ?? ""))
                }
                ev = nil
                continue
            }
            guard ev != nil, let dp = zeile.firstIndex(of: ":") else { continue }
            let kopf = String(zeile[..<dp])
            let wert = String(zeile[zeile.index(after: dp)...])
            let teile = kopf.split(separator: ";", maxSplits: 1).map(String.init)
            let name = teile[0].uppercased()
            let params = teile.count > 1 ? teile[1] : ""
            switch name {
            case "DTSTART": start = datum(wert, params)
            case "DTEND": ende = datum(wert, params)
            case "SUMMARY", "DESCRIPTION", "LOCATION": ev?[name] = text(wert)
            default: break
            }
        }
        return termine.sorted { $0.start < $1.start }
    }
}
