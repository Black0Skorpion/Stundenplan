import Foundation

/// Suchkatalog: alle Veranstaltungen eines Semesters (Titel, PULS-Nummer, Modul).
/// Wird wöchentlich auf GitHub aus dem öffentlichen PULS-Verzeichnis erstellt (werkzeuge/puls_katalog.py)
/// und hier einmal heruntergeladen (etwa 100–300 KB). Gesucht wird dann offline auf dem Gerät.
@MainActor
final class PulsKatalog: ObservableObject {
    static let shared = PulsKatalog()

    struct Eintrag: Identifiable, Hashable {
        let publishid: String
        let titel: String
        let kontext: [String]
        let suchtext: String
        var id: String { publishid }
    }

    private struct Datei: Decodable {
        let semester: String
        let stand: String
        let veranstaltungen: [[Wert]]
    }

    /// JSON-Felder sind gemischt (Text bzw. Liste von Texten)
    private enum Wert: Decodable {
        case text(String), liste([String])
        init(from decoder: Decoder) throws {
            let c = try decoder.singleValueContainer()
            if let s = try? c.decode(String.self) { self = .text(s) } else { self = .liste(try c.decode([String].self)) }
        }
        var text: String { if case .text(let s) = self { return s } else { return "" } }
        var liste: [String] { if case .liste(let l) = self { return l } else { return [] } }
    }

    @Published private(set) var eintraege: [Eintrag] = []
    @Published private(set) var semester: String?
    @Published private(set) var stand: String?
    @Published private(set) var laedt = false
    @Published private(set) var fehler: String?

    static let quelle = "https://github.com/Black0Skorpion/Stundenplan/releases/download/puls-katalog/"

    private func dateiname(_ sem: String) -> String { "puls-katalog-\(sem).deflate" }

    /// Gespeicherten Katalog laden; bei Bedarf (fehlt oder älter als 7 Tage) neu herunterladen.
    /// erzwingen = auch im Mobilnetz bzw. trotz frischem Stand.
    func bereitstellen(_ sem: String, erzwingen: Bool = false) async {
        if semester != sem { eintraege = []; semester = nil }
        let url = Speicher.datei(dateiname(sem))
        let alter = (try? FileManager.default.attributesOfItem(atPath: url.path))?[.modificationDate] as? Date
        if eintraege.isEmpty, let d = try? Data(contentsOf: url) { lesen(d, sem) }
        let veraltet = alter.map { Date().timeIntervalSince($0) > 7 * 86400 } ?? true
        guard erzwingen || eintraege.isEmpty || veraltet else { return }
        if !erzwingen, !eintraege.isEmpty, !(await PulsModell.imWLAN()) { return }
        guard !laedt, let quelle = URL(string: Self.quelle + "puls-\(sem).deflate") else { return }
        laedt = true
        fehler = nil
        defer { laedt = false }
        guard let (d, status) = await Netz.laden(quelle, versuche: 2), status < 400 else {
            if eintraege.isEmpty { fehler = "Der Suchkatalog für \(Puls.semesterName(sem)) ist noch nicht verfügbar." }
            return
        }
        if lesen(d, sem) { try? d.write(to: url, options: .atomic) }
    }

    @discardableResult
    private func lesen(_ komprimiert: Data, _ sem: String) -> Bool {
        guard let roh = try? (komprimiert as NSData).decompressed(using: .zlib) as Data,
              let datei = try? JSONDecoder().decode(Datei.self, from: roh) else {
            fehler = "Der Suchkatalog ist beschädigt."
            return false
        }
        eintraege = datei.veranstaltungen.compactMap { z in
            guard z.count >= 2 else { return nil }
            let titel = z[1].text
            let kontext = z.count > 2 ? z[2].liste : []
            return Eintrag(publishid: z[0].text, titel: titel, kontext: kontext,
                           suchtext: Self.normal(titel + " " + kontext.joined(separator: " ")))
        }
        semester = datei.semester
        stand = datei.stand
        fehler = nil
        return true
    }

    static func normal(_ s: String) -> String {
        s.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "de_DE"))
    }

    /// Alle Wörter der Suche müssen vorkommen (Titel oder Modul, z. B. „mathe inf 1“ oder „INF-1030“)
    func suchen(_ anfrage: String, maximal: Int = 60) -> [Eintrag] {
        let woerter = Self.normal(anfrage).split(whereSeparator: \.isWhitespace).map(String.init)
        guard !woerter.isEmpty else { return [] }
        var treffer: [Eintrag] = []
        for e in eintraege where woerter.allSatisfy({ e.suchtext.contains($0) }) {
            treffer.append(e)
            if treffer.count >= maximal * 3 { break }
        }
        // Treffer im Titel zuerst, dann kürzere Titel
        let erstes = woerter[0]
        return Array(treffer.sorted {
            let a = Self.normal($0.titel).contains(erstes), b = Self.normal($1.titel).contains(erstes)
            return a != b ? a : $0.titel.count < $1.titel.count
        }.prefix(maximal))
    }
}
