import Foundation
import SwiftUI

// ───────────────────────── Grunddaten ─────────────────────────

struct Termin: Codable, Hashable, Identifiable {
    var start: Date
    var ende: Date
    var titel: String
    var typ: String
    var raum: String
    /// Von Hand als ausgefallen markiert
    var ausfall: Bool = false
    /// Ursprünglicher Raum, falls von Hand geändert
    var alterRaum: String? = nil

    init(start: Date, ende: Date, titel: String, typ: String, raum: String) {
        self.start = start
        self.ende = ende
        self.titel = titel
        self.typ = typ
        self.raum = raum
    }

    // Ältere gespeicherte Daten haben die neuen Felder noch nicht
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        start = try c.decode(Date.self, forKey: .start)
        ende = try c.decode(Date.self, forKey: .ende)
        titel = try c.decode(String.self, forKey: .titel)
        typ = try c.decode(String.self, forKey: .typ)
        raum = try c.decode(String.self, forKey: .raum)
        ausfall = try c.decodeIfPresent(Bool.self, forKey: .ausfall) ?? false
        alterRaum = try c.decodeIfPresent(String.self, forKey: .alterRaum)
    }

    var id: String { schluessel }
    /// Bleibt gleich, auch wenn der Raum geändert wird
    var schluessel: String { "\(Int(start.timeIntervalSince1970))|\(titel)" }
    var dauer: TimeInterval { ende.timeIntervalSince(start) }

    func laeuft(_ jetzt: Date) -> Bool { start <= jetzt && ende > jetzt }
}

struct KursInfo {
    let kurz: String
    let farbe: Color
    let hex: UInt32
}

struct TypInfo {
    let lang: String
    let kurz: String
}

enum Konfig {
    /// Öffnet sich beim Tippen auf die Kopfzeile, solange kein eigener Planer-Link eingetragen ist.
    static let standardLink = "https://planner.joshuan.dev/de"
    static let standardStart = 8
    static let standardEnde = 18
    /// Termine, die zur vollen Stunde beginnen und enden, werden als …:15–…:45 gezeigt.
    static let akademischesViertel = true

    static let kurse: [String: (kurz: String, farbe: UInt32)] = [
        "Grundlagen der Programmierung": ("GdP", 0x378ADD),
        "Grundlagen der Programmierung (Rechnerübung)": ("GdP-RÜ", 0x185FA5),
        "Mathematik für Informatik I": ("Mathe I", 0x1D9E75),
        "Mathematik für Informatik II": ("Mathe II", 0x639922),
        "Maschinenmodelle": ("MaMo", 0xD85A30),
        "Bioinformatik": ("Bioinf", 0x7F77DD),
        "Molekulare Evolutionsbiologie": ("MolEvo", 0xD4537E),
    ]
    static let ersatzFarben: [UInt32] = [0xBA7517, 0x993556, 0x0F6E56, 0x534AB7, 0x5F5E5A]

    static let typen: [String: TypInfo] = [
        "Vorlesung": TypInfo(lang: "Vorlesung", kurz: "VL"),
        "Übung": TypInfo(lang: "Übung", kurz: "Ü"),
        "Zusatzübung": TypInfo(lang: "Zusatzübung", kurz: "ZÜ"),
        "Tutorium": TypInfo(lang: "Tutorium", kurz: "Tut"),
        "Seminar": TypInfo(lang: "Seminar", kurz: "Sem"),
        "Praktikum": TypInfo(lang: "Praktikum", kurz: "Prak"),
    ]
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }
}

enum Kurse {
    static func info(_ titel: String) -> KursInfo {
        if let k = Konfig.kurse[titel] {
            return KursInfo(kurz: k.kurz, farbe: Color(hex: k.farbe), hex: k.farbe)
        }
        // Unbekannte Veranstaltung: Kürzel aus den Anfangsbuchstaben, Farbe stabil aus dem Titel
        let kurz: String
        if titel.count > 10 {
            kurz = String(titel.split(whereSeparator: { $0 == " " }).compactMap { $0.first }.prefix(6))
        } else {
            kurz = titel
        }
        let summe = titel.unicodeScalars.reduce(0) { $0 &+ Int($1.value) }
        let farbe = Konfig.ersatzFarben[summe % Konfig.ersatzFarben.count]
        return KursInfo(kurz: kurz, farbe: Color(hex: farbe), hex: farbe)
    }

    static func typ(_ typ: String) -> TypInfo {
        Konfig.typen[typ] ?? TypInfo(lang: typ, kurz: String(typ.prefix(3)))
    }
}

// ───────────────────────── Farben ─────────────────────────

enum Palette {
    static let raster = Color(hex: 0x888780)
    static let heute = Color(hex: 0x185FA5)
    static let jetzt = Color(hex: 0xE24B4A)
}

struct Farben {
    let bg: Color
    let vorder: Color
    let gedimmt: Color
    let akzent: Color

    static func fuer(dunkel: Bool) -> Farben {
        dunkel
            ? Farben(bg: Color(hex: 0x1C1C1E), vorder: Color(hex: 0xF1EFE8), gedimmt: Color(hex: 0x8E8D86), akzent: Color(hex: 0x85B7EB))
            : Farben(bg: Color(hex: 0xFFFFFF), vorder: Color(hex: 0x2C2C2A), gedimmt: Color(hex: 0x888780), akzent: Color(hex: 0x185FA5))
    }
}

// ───────────────────────── Datum & Text ─────────────────────────

enum Zeit {
    static var kalender: Calendar = {
        var k = Calendar(identifier: .gregorian)
        k.locale = Locale(identifier: "de_DE")
        k.firstWeekday = 2
        k.minimumDaysInFirstWeek = 4
        return k
    }()

    static let wochentage = ["Mo", "Di", "Mi", "Do", "Fr"]
    static let wochentageKurz = ["So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"]
    static let wochentageVoll = ["Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"]
    static let monate = ["Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"]

    static func uhrzeit(_ d: Date) -> String {
        let c = kalender.dateComponents([.hour, .minute], from: d)
        return "\(c.hour ?? 0):" + String(format: "%02d", c.minute ?? 0)
    }

    static func stunden(_ d: Date) -> Double {
        let c = kalender.dateComponents([.hour, .minute], from: d)
        return Double(c.hour ?? 0) + Double(c.minute ?? 0) / 60
    }

    static func tagesBeginn(_ d: Date) -> Date { kalender.startOfDay(for: d) }

    static func plusTage(_ d: Date, _ n: Int) -> Date {
        kalender.date(byAdding: .day, value: n, to: d) ?? d.addingTimeInterval(Double(n) * 86400)
    }

    static func gleicherTag(_ a: Date, _ b: Date) -> Bool { kalender.isDate(a, inSameDayAs: b) }

    /// 0 = Sonntag … 6 = Samstag
    static func wochentag(_ d: Date) -> Int { kalender.component(.weekday, from: d) - 1 }

    static func tag(_ d: Date) -> Int { kalender.component(.day, from: d) }
    static func monat(_ d: Date) -> Int { kalender.component(.month, from: d) - 1 }

    static func kalenderwoche(_ d: Date) -> Int {
        var iso = Calendar(identifier: .iso8601)
        iso.timeZone = kalender.timeZone
        return iso.component(.weekOfYear, from: d)
    }

    /// "Di, 13. Okt"
    static func datumKurz(_ d: Date) -> String {
        "\(wochentageKurz[wochentag(d)]), \(tag(d)). \(monate[monat(d)])"
    }

    /// "13. Okt"
    static func tagMonat(_ d: Date) -> String { "\(tag(d)). \(monate[monat(d)])" }

    static func minutenText(_ min: Int) -> String {
        if min < 60 { return "\(min) Min." }
        let h = min / 60, m = min % 60
        return "\(h) Std." + (m > 0 ? " \(m) Min." : "")
    }

    /// "14:15" (heute), "Morgen 8:15" oder "Mo 8:15"
    static func wannText(_ t: Termin, _ jetzt: Date) -> String {
        if gleicherTag(t.start, jetzt) { return uhrzeit(t.start) }
        if gleicherTag(t.start, plusTage(jetzt, 1)) { return "Morgen " + uhrzeit(t.start) }
        return wochentageKurz[wochentag(t.start)] + " " + uhrzeit(t.start)
    }

    static func isoDatum(_ d: Date) -> String {
        let c = kalender.dateComponents([.year, .month, .day], from: d)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    static func ausIso(_ s: String) -> Date? {
        let teile = s.split(separator: "-").compactMap { Int($0) }
        guard teile.count == 3 else { return nil }
        return kalender.date(from: DateComponents(year: teile[0], month: teile[1], day: teile[2]))
    }
}

// ───────────────────────── Raum ─────────────────────────

struct RaumTeile {
    let campus: String
    let haus: String
    let etage: String
    let raum: String

    /// "2.70.0.10" → Campus 2, Haus 70, Etage 0, Raum 10
    init?(_ text: String) {
        let t = text.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        guard t.count >= 3 else { return nil }
        campus = t[0]
        haus = t[1]
        etage = t[2]
        raum = t.dropFirst(3).joined(separator: ".")
    }

    var etageText: String {
        if etage == "0" { return "Erdgeschoss" }
        if let n = Int(etage) { return n > 0 ? "\(n). Obergeschoss" : "\(abs(n)). Untergeschoss" }
        return "Ebene \(etage)"
    }

    /// "0.10" – Etage und Raum ohne Campus/Haus
    var rest: String { raum.isEmpty ? etage : etage + "." + raum }
}
