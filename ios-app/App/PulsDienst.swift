import Foundation
import Network
import UserNotifications
import BackgroundTasks

/// Eigene Modulwahl aus PULS: gewählte Veranstaltungen/Gruppen, Zwischenspeicher, täglicher Abgleich.
@MainActor
final class PulsModell: ObservableObject {
    static let shared = PulsModell()

    struct Kurs: Codable, Hashable, Identifiable {
        var publishid: String
        var titel: String
        /// Gewählte Gruppe (nil = noch nicht gewählt; bei nur einer Gruppe automatisch)
        var gruppe: String?
        var id: String { publishid }
    }

    struct Auswahl: Codable {
        var semester: String = Puls.semesterCode()
        var kurse: [Kurs] = []
        /// true = der Stundenplan kommt aus dieser Auswahl und wird täglich abgeglichen
        var aktiv: Bool = false
        var nurWLAN: Bool = true
        var mitteilungen: Bool = true
    }

    struct Stand: Codable {
        var letzterAbgleich: Date?
        var letzterVersuch: Date?
        /// Letzte erkannte Änderungen, neueste zuerst
        var aenderungen: [String] = []
        var fehler: String?
    }

    @Published var auswahl: Auswahl { didSet { Speicher.schreiben(auswahl, "puls-auswahl.json") } }
    @Published private(set) var cache: [String: Puls.Veranstaltung]
    @Published private(set) var stand: Stand { didSet { Speicher.schreiben(stand, "puls-stand.json") } }
    @Published private(set) var laedt = false

    private var verzeichnisCache: [String: [Puls.Eintrag]] = [:]

    private init() {
        auswahl = Speicher.lesen(Auswahl.self, "puls-auswahl.json") ?? Auswahl()
        cache = Speicher.lesen([String: Puls.Veranstaltung].self, "puls-cache.json") ?? [:]
        stand = Speicher.lesen(Stand.self, "puls-stand.json") ?? Stand()
    }

    private func cacheSpeichern() { Speicher.schreiben(cache, "puls-cache.json") }

    // ───────────── Laden ─────────────

    private static func html(_ url: URL) async -> String? {
        guard let (d, status) = await Netz.laden(url, versuche: 2), status < 400 else { return nil }
        return String(data: d, encoding: .utf8) ?? String(data: d, encoding: .isoLatin1)
    }

    /// Eine Veranstaltung frisch von PULS laden (und zwischenspeichern)
    func laden(_ publishid: String) async -> Puls.Veranstaltung? {
        guard let h = await Self.html(Puls.veranstaltungURL(publishid)),
              let v = Puls.veranstaltung(ausHTML: h, publishid: publishid), !v.gruppen.isEmpty else { return nil }
        cache[publishid] = v
        cacheSpeichern()
        return v
    }

    func verzeichnis(_ pfad: String) async -> [Puls.Eintrag]? {
        let schluessel = auswahl.semester + "#" + pfad
        if let e = verzeichnisCache[schluessel] { return e }
        guard let h = await Self.html(Puls.verzeichnisURL(pfad, semester: auswahl.semester)) else { return nil }
        let e = Puls.verzeichnis(ausHTML: h, pfad: pfad)
        verzeichnisCache[schluessel] = e
        return e
    }

    // ───────────── Auswahl bearbeiten ─────────────

    func enthaelt(_ publishid: String) -> Bool { auswahl.kurse.contains { $0.publishid == publishid } }

    /// Hinzufügen (lädt die Veranstaltung). Gibt eine Fehlermeldung zurück oder nil.
    func hinzufuegen(_ publishid: String) async -> String? {
        if enthaelt(publishid) { return nil }
        guard let v = await laden(publishid) else { return "Die Veranstaltung konnte nicht geladen werden (keine Termine gefunden oder PULS nicht erreichbar)." }
        auswahl.kurse.append(Kurs(publishid: publishid, titel: v.titel, gruppe: v.gruppen.count == 1 ? v.gruppen[0].nummer : nil))
        return nil
    }

    func entfernen(_ publishid: String) {
        auswahl.kurse.removeAll { $0.publishid == publishid }
    }

    func waehle(_ publishid: String, gruppe: String) {
        guard let i = auswahl.kurse.firstIndex(where: { $0.publishid == publishid }) else { return }
        auswahl.kurse[i].gruppe = gruppe
    }

    func setzeSemester(_ code: String) {
        guard code != auswahl.semester else { return }
        auswahl.semester = code
        verzeichnisCache = [:]
    }

    // ───────────── Termine ─────────────

    /// Termine einer Veranstaltung mit gewählter (oder angegebener) Gruppe
    func einzeltermine(_ k: Kurs, gruppe: String? = nil) -> [Puls.EinzelTermin] {
        guard let v = cache[k.publishid] else { return [] }
        return Puls.termine(v, gruppe: gruppe ?? k.gruppe)
    }

    func termine() -> [Termin] {
        auswahl.kurse.flatMap { k in
            einzeltermine(k).map { Termin(start: $0.start, ende: $0.ende, titel: k.titel, typ: $0.art, raum: $0.raum) }
        }.sorted { $0.start < $1.start }
    }

    /// Überschneidungen einer Gruppe mit den anderen gewählten Veranstaltungen (Titel der anderen)
    func ueberschneidungen(_ k: Kurs, gruppe: String) -> [String] {
        let eigene = einzeltermine(k, gruppe: gruppe)
        var treffer = Set<String>()
        for anderer in auswahl.kurse where anderer.publishid != k.publishid && anderer.gruppe != nil {
            let fremd = einzeltermine(anderer)
            if eigene.contains(where: { e in fremd.contains { f in e.start < f.ende && f.start < e.ende } }) {
                treffer.insert(Kurse.info(anderer.titel).kurz)
            }
        }
        return treffer.sorted()
    }

    var fehlendeGruppen: [Kurs] { auswahl.kurse.filter { $0.gruppe == nil } }

    /// Auswahl als Stundenplan übernehmen
    func uebernehmen() -> Int {
        let t = termine()
        Speicher.speichereTermine(t)
        Speicher.schreiben(t, "puls-termine.json")
        auswahl.aktiv = true
        stand.letzterAbgleich = Date()
        stand.fehler = nil
        Task { await Erinnerungen.planen() }
        PulsHintergrund.planen()
        return t.count
    }

    /// Stundenplan kommt wieder aus ICS/eingebaut: Abgleich aus
    func deaktivieren() {
        auswahl.aktiv = false
    }

    // ───────────── Täglicher Abgleich ─────────────

    /// Gleicht die gewählten Veranstaltungen mit PULS ab (höchstens etwa einmal täglich, ggf. nur im WLAN).
    /// Gibt true zurück, wenn sich der Stundenplan geändert hat.
    @discardableResult
    func abgleichen(erzwingen: Bool = false) async -> Bool {
        guard auswahl.aktiv, !auswahl.kurse.isEmpty, !laedt else { return false }
        if !erzwingen {
            if let l = stand.letzterAbgleich, Date().timeIntervalSince(l) < 20 * 3600 { return false }
            if let v = stand.letzterVersuch, Date().timeIntervalSince(v) < 30 * 60 { return false }
            if auswahl.nurWLAN, !(await Self.imWLAN()) { return false }
        }
        laedt = true
        stand.letzterVersuch = Date()
        defer { laedt = false }

        var fehlgeschlagen: [String] = []
        for k in auswahl.kurse {
            if await laden(k.publishid) == nil { fehlgeschlagen.append(Kurse.info(k.titel).kurz) }
        }
        if fehlgeschlagen.count == auswahl.kurse.count {
            stand.fehler = "PULS war nicht erreichbar."
            return false
        }
        // Gewählte Gruppe existiert nicht mehr? Dann bei einer einzigen Gruppe diese nehmen
        for (i, k) in auswahl.kurse.enumerated() {
            if let v = cache[k.publishid], let g = k.gruppe, !v.gruppen.contains(where: { $0.nummer == g }) {
                auswahl.kurse[i].gruppe = v.gruppen.count == 1 ? v.gruppen[0].nummer : nil
            }
        }
        let alt = Speicher.lesen([Termin].self, "puls-termine.json") ?? []
        let neu = termine()
        let meldungen = Self.vergleich(alt, neu)
        stand.letzterAbgleich = Date()
        stand.fehler = fehlgeschlagen.isEmpty ? nil : "Nicht geladen: " + fehlgeschlagen.joined(separator: ", ")
        guard !meldungen.isEmpty else { return false }

        Speicher.speichereTermine(neu)
        Speicher.schreiben(neu, "puls-termine.json")
        let datum = Zeit.datumKurz(Date())
        stand.aenderungen = (meldungen.map { "\(datum): \($0)" } + stand.aenderungen).prefix(30).map { $0 }
        await Erinnerungen.planen()
        if auswahl.mitteilungen { await Self.mitteilen(meldungen) }
        return true
    }

    /// Unterschiede zwischen altem und neuem Stand, als kurze Sätze
    static func vergleich(_ alt: [Termin], _ neu: [Termin]) -> [String] {
        func schluessel(_ t: Termin) -> String { "\(t.titel)|\(t.typ)|\(Int(t.start.timeIntervalSince1970))" }
        let a = Dictionary(alt.map { (schluessel($0), $0) }, uniquingKeysWith: { x, _ in x })
        let n = Dictionary(neu.map { (schluessel($0), $0) }, uniquingKeysWith: { x, _ in x })
        let jetzt = Date()
        var saetze: [String] = []
        // Raum geändert
        for (k, t) in n.sorted(by: { $0.value.start < $1.value.start }) where t.ende > jetzt {
            if let o = a[k], o.raum != t.raum {
                saetze.append("\(Kurse.info(t.titel).kurz) \(t.typ) \(Zeit.datumKurz(t.start)) \(Zeit.uhrzeit(t.start)): Raum \(o.raum) → \(t.raum)")
            }
        }
        let weg = a.filter { n[$0.key] == nil && $0.value.ende > jetzt }.map(\.value).sorted { $0.start < $1.start }
        let dazu = n.filter { a[$0.key] == nil && $0.value.ende > jetzt }.map(\.value).sorted { $0.start < $1.start }
        // Viele Änderungen auf einmal (z. B. neue Gruppe): zusammenfassen
        if weg.count + dazu.count > 6 {
            if !weg.isEmpty || !dazu.isEmpty {
                saetze.append("\(dazu.count) Termine neu, \(weg.count) entfallen")
            }
        } else {
            for t in weg { saetze.append("\(Kurse.info(t.titel).kurz) \(t.typ) \(Zeit.datumKurz(t.start)) \(Zeit.uhrzeit(t.start)) entfällt") }
            for t in dazu { saetze.append("\(Kurse.info(t.titel).kurz) \(t.typ) neu: \(Zeit.datumKurz(t.start)) \(Zeit.uhrzeit(t.start)), \(t.raum)") }
        }
        return saetze
    }

    private static func mitteilen(_ meldungen: [String]) async {
        let c = UNMutableNotificationContent()
        c.title = meldungen.count == 1 ? "Änderung im Stundenplan" : "\(meldungen.count) Änderungen im Stundenplan"
        c.body = meldungen.prefix(4).joined(separator: "\n") + (meldungen.count > 4 ? "\n…" : "")
        c.sound = .default
        c.userInfo = ["url": "stundenplan://plan"]
        let r = UNNotificationRequest(identifier: "puls-\(Int(Date().timeIntervalSince1970))", content: c, trigger: nil)
        try? await UNUserNotificationCenter.current().add(r)
    }

    /// Ist das Gerät gerade im WLAN (und nicht im Mobilnetz/Hotspot)?
    static func imWLAN() async -> Bool {
        await withCheckedContinuation { (fertig: CheckedContinuation<Bool, Never>) in
            let m = NWPathMonitor()
            var erledigt = false
            m.pathUpdateHandler = { p in
                guard !erledigt else { return }
                erledigt = true
                m.cancel()
                fertig.resume(returning: p.status == .satisfied && p.usesInterfaceType(.wifi) && !p.isExpensive)
            }
            m.start(queue: DispatchQueue(label: "wlan"))
        }
    }
}

/// Abgleich im Hintergrund (iOS entscheidet selbst, wann – meist nachts am Ladegerät im WLAN)
enum PulsHintergrund {
    static let kennung = "de.stundenplan.ios.puls"

    static func registrieren() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: kennung, using: nil) { aufgabe in
            guard let aufgabe = aufgabe as? BGAppRefreshTask else { return }
            planen()
            let t = Task { @MainActor in
                let geaendert = await PulsModell.shared.abgleichen()
                if geaendert { Speicher.widgetsNeuLaden() }
                aufgabe.setTaskCompleted(success: true)
            }
            aufgabe.expirationHandler = { t.cancel() }
        }
    }

    static func planen() {
        let r = BGAppRefreshTaskRequest(identifier: kennung)
        r.earliestBeginDate = Date().addingTimeInterval(12 * 3600)
        try? BGTaskScheduler.shared.submit(r)
    }
}
