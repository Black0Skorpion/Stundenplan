import Foundation
import SwiftUI
import UserNotifications
import ActivityKit
#if canImport(AlarmKit)
import AlarmKit
#endif

/// Wecker vor der ersten Veranstaltung des Tages.
/// Ab iOS 26 ein echter Wecker (AlarmKit: klingelt auch im Lautlos-Modus und im Fokus),
/// sonst eine Mitteilung mit Ton. Geplant werden immer die nächsten 14 Tage; ausgefallene Termine zählen nicht.
enum Wecker {
    static let vorlaufOptionen = [30, 45, 60, 75, 90, 105, 120, 150, 180]
    static let fruehestensOptionen = [0, 300, 330, 360, 390, 420, 450]
    /// Schlummern in Minuten (0 = kein Schlummern-Knopf)
    static let schlummerOptionen = [0, 5, 9, 10, 15, 20]
    static let praefix = "wecker-"
    private static let tage = 14

    struct Weckzeit: Codable, Equatable {
        let wann: Date
        let titel: String
        let start: Date
        let raum: String
    }

    /// Echter Wecker möglich (iOS 26 und mit dem passenden SDK gebaut)
    static var echt: Bool {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) { return true }
        #endif
        return false
    }

    static func vorlaufText(_ m: Int) -> String {
        let h = m / 60, r = m % 60
        if h == 0 { return "\(m) Min" }
        return r == 0 ? "\(h) Std" : "\(h) Std \(r) Min"
    }

    static func uhrText(_ minuten: Int) -> String {
        minuten == 0 ? "egal" : String(format: "%d:%02d", minuten / 60, minuten % 60)
    }

    /// Weckzeiten aus den Terminen: je Tag die erste (nicht ausgefallene) Veranstaltung minus Vorlauf
    static func zeiten(_ termine: [Termin], _ e: Speicher.Einstellungen, jetzt: Date = Date()) -> [Weckzeit] {
        let bis = jetzt.addingTimeInterval(TimeInterval(tage * 86400))
        var erste: [Date: Termin] = [:]
        // Ab Tagesbeginn zählen: hat die erste Veranstaltung heute schon begonnen, gibt es heute keinen Wecker mehr
        // (sonst würde vor jeder weiteren Veranstaltung des Tages geweckt)
        let heute = Zeit.tagesBeginn(jetzt)
        // Bei nach hinten verschobenen Terminen zählt ggf. die ursprüngliche Zeit (weckStart)
        func beginn(_ t: Termin) -> Date { t.weckStart ?? t.start }
        for t in termine where !t.ausfall && beginn(t) >= heute && beginn(t) < bis {
            let tag = Zeit.tagesBeginn(beginn(t))
            if let alt = erste[tag], beginn(alt) <= beginn(t) { continue }
            erste[tag] = t
        }
        return erste.values.sorted { beginn($0) < beginn($1) }.compactMap { t in
            var wann = beginn(t).addingTimeInterval(-TimeInterval(e.weckerVorlauf * 60))
            if e.weckerFruehestens > 0 {
                let grenze = Zeit.tagesBeginn(beginn(t)).addingTimeInterval(TimeInterval(e.weckerFruehestens * 60))
                // nicht vor der Uhrzeit – aber spätestens 15 Min vor Beginn
                if wann < grenze { wann = min(grenze, beginn(t).addingTimeInterval(-15 * 60)) }
            }
            guard wann > jetzt else { return nil }
            return Weckzeit(wann: wann, titel: t.titel, start: t.start, raum: t.raum)
        }
    }

    static var naechster: Weckzeit? {
        let e = Speicher.einstellungen()
        guard e.wecker else { return nil }
        return zeiten(Speicher.termine(), e).first
    }

    /// Erlaubnis anfragen (Wecker bzw. Mitteilungen). Gibt zurück, ob es klappt.
    @MainActor
    static func erlaubnisAnfragen() async -> Bool {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) { return await AlarmPlaner.erlaubnis() }
        #endif
        return await Erinnerungen.erlaubnisAnfragen()
    }

    /// Läuft gerade eine Planung? Dann nicht parallel (zwei gleichzeitige würden sich gegenseitig Wecker löschen)
    @MainActor private static var plantGerade = false
    @MainActor private static var nochmal = false

    /// Wecker neu planen (nur wenn sich etwas geändert hat oder die gestellten Wecker nicht mehr stimmen;
    /// erzwingen z. B. nach geänderter Schlummerzeit)
    @MainActor
    static func planen(erzwingen: Bool = false) async {
        if plantGerade {
            nochmal = true
            return
        }
        plantGerade = true
        defer { plantGerade = false }
        await planenEinmal(erzwingen: erzwingen)
        while nochmal {
            nochmal = false
            await planenEinmal(erzwingen: false)
        }
    }

    @MainActor
    private static func planenEinmal(erzwingen: Bool) async {
        let e = Speicher.einstellungen()
        let neu = e.wecker ? zeiten(Speicher.termine(), e) : []
        let alt = Speicher.lesen([Weckzeit].self, "wecker.json") ?? []
        // Nach einem App-Update immer neu stellen, damit neue Einstellungen (z. B. Schlummern) auch
        // bei unveränderten Weckzeiten in den schon geplanten Weckern ankommen
        let version = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""
        let neueVersion = UserDefaults.standard.string(forKey: "weckerGeplantMitVersion") != version
        // Auch prüfen, ob die Wecker im System wirklich (noch) gestellt sind – z. B. nach einem Update
        // oder wenn iOS sie entfernt hat. Sonst würde die App das nie bemerken.
        if !erzwingen && !neueVersion && neu == alt.filter({ $0.wann > Date() }) && !neu.isEmpty
            && systemStimmt(neu) { return }
        if neu.isEmpty && alt.isEmpty && !neueVersion { return }
        UserDefaults.standard.set(version, forKey: "weckerGeplantMitVersion")

        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            let ok = await AlarmPlaner.planen(neu, schlummern: e.weckerSchlummern)
            Speicher.schreiben(ok ? neu : [], "wecker.json")
            Speicher.widgetsNeuLaden()
            return
        }
        #endif
        await mitteilungenPlanen(neu)
        Speicher.schreiben(neu, "wecker.json")
        Speicher.widgetsNeuLaden()
    }

    /// Stimmen die im System gestellten Wecker mit den geplanten Zeiten überein?
    @MainActor
    static func systemStimmt(_ zeiten: [Weckzeit]) -> Bool {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            let gestellt = AlarmPlaner.gestellteZeiten()
            let soll = zeiten.map { Int($0.wann.timeIntervalSince1970) }.sorted()
            return gestellt == soll
        }
        #endif
        return true
    }

    /// Für die Fehlersuche: was im System gestellt ist
    @MainActor
    static func diagnose() async -> [String] {
        var z: [String] = []
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            z.append("Erlaubnis: " + AlarmPlaner.erlaubnisText())
            z += AlarmPlaner.beschreibung()
            return z
        }
        #endif
        let c = UNUserNotificationCenter.current()
        let offen = await c.pendingNotificationRequests().filter { $0.identifier.hasPrefix(praefix) }
        z.append("Ersatz-Mitteilungen geplant: \(offen.count)")
        return z
    }

    static func titel(_ w: Weckzeit) -> String {
        "\(Kurse.info(w.titel).kurz) um \(Zeit.uhrzeit(w.start))"
    }

    /// Ersatz ohne AlarmKit: Mitteilung mit Ton
    private static func mitteilungenPlanen(_ zeiten: [Weckzeit]) async {
        let c = UNUserNotificationCenter.current()
        let alte = await c.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(praefix) }
        c.removePendingNotificationRequests(withIdentifiers: alte)
        for w in zeiten {
            let inhalt = UNMutableNotificationContent()
            inhalt.title = "Aufstehen: " + titel(w)
            inhalt.body = w.raum.isEmpty ? "Erste Veranstaltung heute" : "Erste Veranstaltung heute · \(w.raum)"
            inhalt.sound = .default
            inhalt.interruptionLevel = .timeSensitive
            inhalt.userInfo = ["url": "stundenplan://plan"]
            let teile = Zeit.kalender.dateComponents([.year, .month, .day, .hour, .minute], from: w.wann)
            let r = UNNotificationRequest(identifier: praefix + "\(Int(w.wann.timeIntervalSince1970))", content: inhalt,
                                          trigger: UNCalendarNotificationTrigger(dateMatching: teile, repeats: false))
            try? await c.add(r)
        }
    }
}

#if canImport(AlarmKit)
@available(iOS 26.0, *)
enum AlarmPlaner {
    static func erlaubnis() async -> Bool {
        let m = AlarmManager.shared
        switch m.authorizationState {
        case .authorized: return true
        case .notDetermined: return (try? await m.requestAuthorization()) == .authorized
        default: return false
        }
    }

    /// Zeitpunkte (Sekunden) der gestellten, noch nicht ausgelösten Wecker
    static func gestellteZeiten() -> [Int] {
        guard let alle = try? AlarmManager.shared.alarms else { return [] }
        return alle.filter { $0.state == .scheduled }.compactMap { a -> Int? in
            if case .fixed(let d)? = a.schedule { return Int(d.timeIntervalSince1970) }
            return nil
        }.sorted()
    }

    static func erlaubnisText() -> String {
        switch AlarmManager.shared.authorizationState {
        case .authorized: return "ja"
        case .denied: return "abgelehnt"
        case .notDetermined: return "noch nicht gefragt"
        @unknown default: return "unbekannt"
        }
    }

    static func beschreibung() -> [String] {
        guard let alle = try? AlarmManager.shared.alarms else { return ["Wecker nicht lesbar"] }
        if alle.isEmpty { return ["Keine Wecker im System gestellt"] }
        let f = DateFormatter()
        f.locale = Locale(identifier: "de_DE")
        f.dateFormat = "EE dd.MM. HH:mm"
        return alle.map { a -> String in
            var wann = "?"
            if case .fixed(let d)? = a.schedule { wann = f.string(from: d) }
            let zustand: String
            switch a.state {
            case .scheduled: zustand = "gestellt"
            case .countdown: zustand = "schlummert"
            case .paused: zustand = "pausiert"
            case .alerting: zustand = "klingelt"
            @unknown default: zustand = "?"
            }
            return "\(wann) · \(zustand)"
        }
    }

    @discardableResult
    static func planen(_ zeiten: [Wecker.Weckzeit], schlummern: Int) async -> Bool {
        let m = AlarmManager.shared
        // Alle geplanten Wecker dieser App ersetzen – aber keinen, der gerade klingelt oder schlummert
        if let alle = try? m.alarms {
            for a in alle where a.state == .scheduled { try? m.cancel(id: a.id) }
        }
        guard m.authorizationState == .authorized else { return false }
        for w in zeiten {
            let stopp = AlarmButton(text: "Aus", textColor: .white, systemImageName: "alarm")
            let titel = LocalizedStringResource(String.LocalizationValue(Wecker.titel(w)))
            let praesentation: AlarmPresentation
            var dauer: Alarm.CountdownDuration? = nil
            if schlummern > 0 {
                // Schlummern: Countdown, danach klingelt es erneut
                let schlummer = AlarmButton(text: "Schlummern", textColor: .white, systemImageName: "zzz")
                let alarm = AlarmPresentation.Alert(title: titel, stopButton: stopp,
                                                    secondaryButton: schlummer, secondaryButtonBehavior: .countdown)
                let countdown = AlarmPresentation.Countdown(title: LocalizedStringResource(String.LocalizationValue("Schlummern")))
                praesentation = AlarmPresentation(alert: alarm, countdown: countdown)
                dauer = Alarm.CountdownDuration(preAlert: nil, postAlert: TimeInterval(schlummern * 60))
            } else {
                praesentation = AlarmPresentation(alert: AlarmPresentation.Alert(title: titel, stopButton: stopp))
            }
            let attribute = AlarmAttributes<WeckerDaten>(presentation: praesentation,
                                                         metadata: WeckerDaten(), tintColor: .orange)
            let konfig = AlarmManager.AlarmConfiguration<WeckerDaten>(countdownDuration: dauer,
                                                                      schedule: .fixed(w.wann),
                                                                      attributes: attribute,
                                                                      stopIntent: nil,
                                                                      secondaryIntent: nil,
                                                                      sound: .default)
            _ = try? await m.schedule(id: UUID(), configuration: konfig)
        }
        return true
    }
}
#endif
