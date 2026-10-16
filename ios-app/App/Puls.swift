import Foundation

/// Zugriff auf die öffentlichen Seiten von PULS (Vorlesungsverzeichnis der Uni Potsdam, ohne Anmeldung).
/// PULS hat keine offizielle Schnittstelle – hier werden die HTML-Seiten ausgelesen.
/// Diese Datei braucht nur Foundation (wird auf GitHub auch einzeln getestet).
enum Puls {
    static let basis = "https://puls.uni-potsdam.de/qisserver/rds"

    // ───────────── Datenmodell ─────────────

    /// Eine Zeile aus „Übersicht über alle Veranstaltungstermine“
    struct Regel: Codable, Hashable {
        var art: String          // Vorlesung, Übung, Tutorium …
        var tag: String          // Mo, Di …
        var von: String          // "12:00"
        var bis: String          // "14:00"
        var rhythmus: String     // wöchentlich, 14täglich, Einzeltermin, Block …
        var beginn: String?      // "12.10.2026"
        var ende: String?        // "01.02.2027" (bei Einzeltermin = beginn)
        var raum: String
        var lehrperson: String
        var ausfaelle: [String]  // "26.10.2026", …
        var bemerkung: String?

        /// Kurzform für die Auswahl: "Übung · Do 12:00–14:00 · 2.70.0.10"
        var kurz: String {
            let wann = rhythmus.lowercased().contains("einzel") ? (beginn ?? tag) : tag
            return "\(art) · \(wann) \(von)–\(bis)" + (raum.isEmpty ? "" : " · \(raum)")
        }
    }

    struct Gruppe: Codable, Hashable, Identifiable {
        var nummer: String
        var regeln: [Regel]
        var id: String { nummer }
    }

    struct Veranstaltung: Codable, Hashable, Identifiable {
        var publishid: String
        var titel: String
        var art: String
        var semester: String
        var gruppen: [Gruppe]
        var moodle: String?
        var belegfrist: String?
        var id: String { publishid }

        var url: URL { Puls.veranstaltungURL(publishid) }

        /// Termine, die in allen Gruppen gleich sind (meist die Vorlesung)
        var gemeinsam: [Regel] {
            guard let erste = gruppen.first else { return [] }
            return erste.regeln.filter { r in gruppen.allSatisfy { $0.regeln.contains(r) } }
        }

        /// Was eine Gruppe von den anderen unterscheidet (zur Anzeige in der Auswahl)
        func eigenes(_ g: Gruppe) -> [Regel] {
            let gem = gemeinsam
            let rest = g.regeln.filter { !gem.contains($0) }
            return rest.isEmpty ? g.regeln : rest
        }
    }

    /// Ein Eintrag im Vorlesungsverzeichnis (Ordner oder Veranstaltung)
    struct Eintrag: Hashable, Identifiable {
        enum Art: Hashable { case ordner(pfad: String), veranstaltung(publishid: String) }
        let name: String
        let art: Art
        var id: String {
            switch art {
            case .ordner(let p): return "o" + p
            case .veranstaltung(let v): return "v" + v
            }
        }
    }

    /// Ein fertiger Termin (wird in der App zu `Termin`)
    struct EinzelTermin: Hashable {
        let start: Date
        let ende: Date
        let art: String
        let raum: String
    }

    // ───────────── Semester ─────────────

    /// PULS-Semesterkennung: 20262 = WiSe 2026/27, 20271 = SoSe 2027
    static func semesterCode(_ d: Date = Date()) -> String {
        let k = Calendar(identifier: .gregorian).dateComponents(in: zone, from: d)
        let jahr = k.year ?? 2026, monat = k.month ?? 10
        // Ab September wird schon das Wintersemester geplant, ab März das Sommersemester
        if monat >= 9 { return "\(jahr)2" }
        if monat <= 2 { return "\(jahr - 1)2" }
        return "\(jahr)1"
    }

    static func naechstesSemester(_ code: String) -> String {
        guard let jahr = Int(code.prefix(4)) else { return code }
        return code.hasSuffix("1") ? "\(jahr)2" : "\(jahr + 1)1"
    }

    static func semesterName(_ code: String) -> String {
        guard let jahr = Int(code.prefix(4)) else { return code }
        return code.hasSuffix("1") ? "SoSe \(jahr)" : "WiSe \(jahr)/\(String(jahr + 1).suffix(2))"
    }

    // ───────────── Adressen ─────────────

    static func veranstaltungURL(_ publishid: String) -> URL {
        URL(string: "\(basis)?state=verpublish&status=init&vmfile=no&publishid=\(publishid)&moduleCall=webInfo&publishConfFile=webInfo&publishSubDir=veranstaltung")!
    }

    /// pfad = "" (oberste Ebene) oder "277989|279834|…"
    static func verzeichnisURL(_ pfad: String, semester: String) -> URL {
        let wurzel = pfad.isEmpty ? "0" : pfad.replacingOccurrences(of: "|", with: "%7C")
        return URL(string: "\(basis)?state=wtree&search=1&trex=step&root1\(semester)=\(wurzel)&P.vx=kurz")!
    }

    /// publishid aus einem eingefügten PULS-Link (oder einer reinen Nummer)
    static func publishid(ausLink text: String) -> String? {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.allSatisfy(\.isNumber), !t.isEmpty { return t }
        for muster in [#"publishid=(\d+)"#, #"veranstid=(\d+)"#] {
            if let r = t.range(of: muster, options: .regularExpression) {
                return String(t[r].filter(\.isNumber))
            }
        }
        return nil
    }

    // ───────────── HTML-Hilfen ─────────────

    static let zone = TimeZone(identifier: "Europe/Berlin")!

    static func text(_ html: String) -> String {
        var s = html.replacingOccurrences(of: #"<br\s*/?>"#, with: " ", options: .regularExpression)
        s = s.replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
        let ersatz = ["&nbsp;": " ", "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": "\"", "&#39;": "'",
                      "&auml;": "ä", "&ouml;": "ö", "&uuml;": "ü", "&Auml;": "Ä", "&Ouml;": "Ö", "&Uuml;": "Ü",
                      "&szlig;": "ß", "&ndash;": "–", "&shy;": ""]
        for (k, v) in ersatz { s = s.replacingOccurrences(of: k, with: v) }
        s = s.replacingOccurrences(of: #"&#(\d+);"#, with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: "\u{00A0}", with: " ")
        return s.split(whereSeparator: \.isWhitespace).joined(separator: " ")
    }

    private static func alle(_ muster: String, in s: String) -> [[String]] {
        guard let re = try? NSRegularExpression(pattern: muster, options: [.dotMatchesLineSeparators, .caseInsensitive]) else { return [] }
        let ns = s as NSString
        return re.matches(in: s, range: NSRange(location: 0, length: ns.length)).map { m in
            (0..<m.numberOfRanges).map { i in
                let r = m.range(at: i)
                return r.location == NSNotFound ? "" : ns.substring(with: r)
            }
        }
    }

    private static func zellen(_ zeile: String) -> [String] {
        alle(#"<t[dh][^>]*>(.*?)</t[dh]>"#, in: zeile).map { text($0[1]) }
    }

    private static func daten(_ s: String) -> [String] {
        alle(#"(\d{2}\.\d{2}\.\d{4})"#, in: s).map { $0[1] }
    }

    // ───────────── Veranstaltungsseite auslesen ─────────────

    static func veranstaltung(ausHTML html: String, publishid: String) -> Veranstaltung? {
        // Titel: <h1>Maschinenmodelle - Einzelansicht</h1>
        var titel = alle(#"<h1[^>]*>(.*?)</h1>"#, in: html).map { text($0[1]) }
            .first(where: { $0.contains("Einzelansicht") }) ?? ""
        titel = titel.replacingOccurrences(of: #"\s*-\s*Einzelansicht\s*$"#, with: "", options: .regularExpression)
        guard !titel.isEmpty else { return nil }

        // Grunddaten
        var art = "", semester = "", belegfrist: String?
        if let grund = alle(#"<table[^>]*summary="Grunddaten[^"]*"[^>]*>(.*?)</table>"#, in: html).first?[1] {
            for zeile in alle(#"<tr[^>]*>(.*?)</tr>"#, in: grund).map({ $0[1] }) {
                let z = zellen(zeile)
                for i in stride(from: 0, to: z.count - 1, by: 1) {
                    switch z[i] {
                    case "Veranstaltungsart": art = z[i + 1]
                    case "Semester": semester = z[i + 1]
                    case "Belegungsfristen": belegfrist = z[i + 1]
                    default: break
                    }
                }
            }
        }
        let moodle = alle(#"<a[^>]*href="([^"]+)"[^>]*>\s*Moodle[^<]*</a>"#, in: html).first.map { text($0[1]) }

        // Gruppen: jede Termintabelle gehört zur letzten Überschrift „Gruppe N:“ davor
        let tabellen = alle(#"(<table[^>]*summary="Übersicht über alle Veranstaltungstermine"[^>]*>)(.*?)</table>"#, in: html)
        let ns = html as NSString
        var gruppen: [Gruppe] = []
        var suchAb = 0
        for (i, t) in tabellen.enumerated() {
            let pos = ns.range(of: t[1], options: [], range: NSRange(location: suchAb, length: ns.length - suchAb)).location
            var nummer = "\(i + 1)"
            if pos != NSNotFound {
                let davor = ns.substring(with: NSRange(location: suchAb, length: pos - suchAb))
                if let g = alle(#"Gruppe\s+([0-9A-Za-z]+)\s*:"#, in: davor).last { nummer = g[1] }
                suchAb = pos + (t[1] as NSString).length
            }
            var regeln: [Regel] = []
            for zeile in alle(#"<tr[^>]*>(.*?)</tr>"#, in: t[2]).map({ $0[1] }) {
                let z = zellen(zeile)
                if z.count >= 2, z[1].hasPrefix("Bemerkung"), !regeln.isEmpty {
                    regeln[regeln.count - 1].bemerkung = z.dropFirst(2).joined(separator: " ")
                    continue
                }
                guard z.count >= 8, !z[2].isEmpty, z[2] != "Tag" else { continue }
                let zeiten = alle(#"(\d{1,2}:\d{2})"#, in: z[3]).map { $0[1] }
                guard zeiten.count >= 2 else { continue }
                let d = daten(z[5])
                regeln.append(Regel(
                    art: z[1].isEmpty ? art : z[1], tag: z[2], von: zeiten[0], bis: zeiten[1],
                    rhythmus: z[4], beginn: d.first, ende: d.count > 1 ? d[1] : d.first,
                    raum: raumKurz(z[6]), lehrperson: z[7],
                    ausfaelle: z.count > 8 ? daten(z[8]) : [], bemerkung: nil))
            }
            if !regeln.isEmpty { gruppen.append(Gruppe(nummer: nummer, regeln: regeln)) }
        }
        return Veranstaltung(publishid: publishid, titel: titel, art: art, semester: semester,
                             gruppen: gruppen, moodle: moodle, belegfrist: belegfrist)
    }

    /// "2.27.0.01 Hörsaal" → "2.27.0.01" (Raumnummern im Format Campus.Haus.Etage.Raum)
    static func raumKurz(_ s: String) -> String {
        if let r = s.range(of: #"\d\.\d{1,2}\.[0-9A-Za-z-]+\.[0-9A-Za-z.-]+"#, options: .regularExpression) {
            return String(s[r])
        }
        return s
    }

    // ───────────── Vorlesungsverzeichnis auslesen ─────────────

    /// Unterordner (eine Ebene tiefer als `pfad`) und Veranstaltungen einer Verzeichnisseite
    static func verzeichnis(ausHTML html: String, pfad: String) -> [Eintrag] {
        let tiefe = pfad.isEmpty ? 1 : pfad.split(separator: "|").count + 1
        var ergebnis: [Eintrag] = []
        var gesehen = Set<String>()
        for a in alle(#"<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>"#, in: html) {
            let href = a[1].replacingOccurrences(of: "&amp;", with: "&")
            let name = text(a[2])
            if href.contains("state=wtree"), href.contains("P.vx=kurz"),
               let r = href.range(of: #"root1\d{5}=([0-9|%7C]+)"#, options: .regularExpression) {
                let teil = String(href[r]).split(separator: "=").last.map(String.init) ?? ""
                let p = teil.replacingOccurrences(of: "%7C", with: "|")
                let stufen = p.split(separator: "|").count
                // Nur direkte Unterordner (keine Brotkrumen-Links nach oben)
                guard stufen == tiefe, pfad.isEmpty || p.hasPrefix(pfad + "|"), !name.isEmpty, gesehen.insert("o" + p).inserted else { continue }
                ergebnis.append(Eintrag(name: name, art: .ordner(pfad: p)))
            } else if href.contains("publishid="), !name.isEmpty, let id = publishid(ausLink: href),
                      gesehen.insert("v" + id).inserted {
                ergebnis.append(Eintrag(name: name, art: .veranstaltung(publishid: id)))
            }
        }
        return ergebnis
    }

    // ───────────── Termine erzeugen ─────────────

    private static func datum(_ s: String, _ zeit: String) -> Date? {
        let f = DateFormatter()
        f.locale = Locale(identifier: "de_DE")
        f.timeZone = zone
        f.dateFormat = "dd.MM.yyyy HH:mm"
        return f.date(from: "\(s) \(zeit)")
    }

    private static let wochentage = ["So": 1, "Mo": 2, "Di": 3, "Mi": 4, "Do": 5, "Fr": 6, "Sa": 7]

    /// Alle einzelnen Termine einer Regel (wöchentlich/14-täglich/Einzeltermin/Block), ohne Ausfälle
    static func termine(_ r: Regel) -> [EinzelTermin] {
        guard let b = r.beginn else { return [] }
        let e = r.ende ?? b
        var kal = Calendar(identifier: .gregorian)
        kal.timeZone = zone
        guard let erster = datum(b, r.von), let letzter = datum(e, r.von) else { return [] }
        let ausfall = Set(r.ausfaelle)
        let f = DateFormatter()
        f.locale = Locale(identifier: "de_DE")
        f.timeZone = zone
        f.dateFormat = "dd.MM.yyyy"

        let rh = r.rhythmus.lowercased()
        var tage: [Date] = []
        if rh.contains("einzel") || b == e {
            tage = [erster]
        } else if rh.contains("block") {
            // Blockveranstaltung: jeden Tag im Zeitraum (Wochenende nur, wenn ausdrücklich genannt)
            let mitWochenende = rh.contains("sa") || rh.contains("so")
            var d = erster
            while d <= letzter {
                let wt = kal.component(.weekday, from: d)
                if mitWochenende || (wt != 1 && wt != 7) { tage.append(d) }
                d = kal.date(byAdding: .day, value: 1, to: d)!
            }
        } else {
            let schritt = rh.contains("14") || rh.contains("zweiw") ? 14 : 7
            var d = erster
            // Auf den angegebenen Wochentag schieben, falls der Beginn auf einen anderen Tag fällt
            if let ziel = wochentage[String(r.tag.prefix(2))] {
                var n = 0
                while kal.component(.weekday, from: d) != ziel && n < 7 {
                    d = kal.date(byAdding: .day, value: 1, to: d)!
                    n += 1
                }
            }
            while d <= letzter {
                tage.append(d)
                d = kal.date(byAdding: .day, value: schritt, to: d)!
            }
        }
        let dauer: TimeInterval = {
            guard let s = datum(b, r.von), let en = datum(b, r.bis) else { return 5400 }
            return max(900, en.timeIntervalSince(s))
        }()
        return tage.filter { !ausfall.contains(f.string(from: $0)) }
            .map { EinzelTermin(start: $0, ende: $0.addingTimeInterval(dauer), art: r.art, raum: r.raum) }
    }

    /// Alle Termine einer gewählten Gruppe
    static func termine(_ v: Veranstaltung, gruppe: String?) -> [EinzelTermin] {
        let g = v.gruppen.first(where: { $0.nummer == gruppe }) ?? v.gruppen.first
        var gesehen = Set<String>()
        return (g?.regeln ?? []).flatMap(termine)
            .filter { gesehen.insert("\($0.start.timeIntervalSince1970)|\($0.art)").inserted }
            .sorted { $0.start < $1.start }
    }
}
