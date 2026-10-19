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
        for t in termine where !t.ausfall && t.start >= heute && t.start < bis {
            let tag = Zeit.tagesBeginn(t.start)
            if let alt = erste[tag], alt.start <= t.start { continue }
            erste[tag] = t
        }
        return erste.values.sorted { $0.start < $1.start }.compactMap { t in
            var wann = t.start.addingTimeInterval(-TimeInterval(e.weckerVorlauf * 60))
            if e.weckerFruehestens > 0 {
                let grenze = Zeit.tagesBeginn(t.start).addingTimeInterval(TimeInterval(e.weckerFruehestens * 60))
                // nicht vor der Uhrzeit – aber spätestens 15 Min vor Beginn
                if wann < grenze { wann = min(grenze, t.start.addingTimeInterval(-15 * 60)) }
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

    /// Wecker neu planen (nur wenn sich etwas geändert hat; erzwingen z. B. nach geänderter Schlummerzeit)
    @MainActor
    static func planen(erzwingen: Bool = false) async {
        let e = Speicher.einstellungen()
        let neu = e.wecker ? zeiten(Speicher.termine(), e) : []
        let alt = Speicher.lesen([Weckzeit].self, "wecker.json") ?? []
        if !erzwingen && neu == alt.filter({ $0.wann > Date() }) && !neu.isEmpty { return }
        if neu.isEmpty && alt.isEmpty { return }

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

    @discardableResult
    static func planen(_ zeiten: [Wecker.Weckzeit], schlummern: Int) async -> Bool {
        let m = AlarmManager.shared
        // Alle Wecker dieser App ersetzen
        if let alle = try? m.alarms {
            for a in alle { try? m.cancel(id: a.id) }
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
