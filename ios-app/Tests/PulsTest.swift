import Foundation

// Kleiner Test für Puls.swift: lädt echte PULS-Seiten und gibt aus, was erkannt wurde.
// Aufruf (macOS): swiftc ios-app/App/Puls.swift ios-app/Tests/PulsTest.swift -o pulstest && ./pulstest
@main
struct PulsTest {
    static func laden(_ url: URL) -> String {
        var ergebnis = ""
        let s = DispatchSemaphore(value: 0)
        var r = URLRequest(url: url)
        r.setValue("Stundenplan-Test (github.com/Black0Skorpion/Stundenplan)", forHTTPHeaderField: "User-Agent")
        URLSession.shared.dataTask(with: r) { d, _, _ in
            ergebnis = d.flatMap { String(data: $0, encoding: .utf8) ?? String(data: $0, encoding: .isoLatin1) } ?? ""
            s.signal()
        }.resume()
        s.wait()
        Thread.sleep(forTimeInterval: 1.5)
        return ergebnis
    }

    static func main() {
        let f = DateFormatter()
        f.locale = Locale(identifier: "de_DE")
        f.timeZone = Puls.zone
        f.dateFormat = "EE dd.MM. HH:mm"
        print("Semester:", Puls.semesterCode(), Puls.semesterName(Puls.semesterCode()))
        var fehler = 0
        for id in ["123991", "124000", "124001", "123976", "123977", "122607", "122492"] {
            guard let v = Puls.veranstaltung(ausHTML: laden(Puls.veranstaltungURL(id)), publishid: id) else {
                print("FEHLER: \(id) nicht lesbar"); fehler += 1; continue
            }
            print("\n== \(v.titel) [\(v.art), \(v.semester)] Gruppen: \(v.gruppen.count) Moodle: \(v.moodle != nil) Frist: \(v.belegfrist ?? "-")")
            for r in v.gemeinsam { print("   gemeinsam: \(r.kurz) \(r.rhythmus) \(r.beginn ?? "")–\(r.ende ?? "") Ausfälle: \(r.ausfaelle)") }
            for g in v.gruppen { print("   Gruppe \(g.nummer): " + v.eigenes(g).map(\.kurz).joined(separator: " | ")) }
            let t = Puls.termine(v, gruppe: v.gruppen.first?.nummer)
            print("   Termine Gruppe 1: \(t.count), erste: " + t.prefix(4).map { "\(f.string(from: $0.start)) \($0.art) \($0.raum)" }.joined(separator: "; "))
            if t.isEmpty { fehler += 1 }
        }
        // Verzeichnis
        let pfad = "277989|279834|282154|278938|277613|280402"
        let e = Puls.verzeichnis(ausHTML: laden(Puls.verzeichnisURL(pfad, semester: Puls.semesterCode())), pfad: pfad)
        print("\nVerzeichnis \(pfad): \(e.count) Einträge: " + e.prefix(6).map(\.name).joined(separator: " | "))
        let oben = Puls.verzeichnis(ausHTML: laden(Puls.verzeichnisURL("", semester: Puls.semesterCode())), pfad: "")
        print("Oberste Ebene: \(oben.count): " + oben.prefix(4).map(\.name).joined(separator: " | "))
        let modul = "277989|279834|282154|278938|277613|280402|277160"
        let m = Puls.verzeichnis(ausHTML: laden(Puls.verzeichnisURL(modul, semester: Puls.semesterCode())), pfad: modul)
        print("Modul GdP: " + m.map { "\($0.name) (\($0.id))" }.joined(separator: " | "))
        if e.isEmpty || oben.isEmpty || m.isEmpty { fehler += 1 }
        print("\nFehler: \(fehler)")
        exit(fehler == 0 ? 0 : 1)
    }
}
