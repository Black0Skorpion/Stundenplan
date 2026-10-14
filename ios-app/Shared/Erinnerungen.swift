import Foundation
import UserNotifications

/// Lokale Mitteilungen vor Veranstaltungen (ohne Server, nur auf dem iPhone).
/// iOS erlaubt höchstens 64 geplante Mitteilungen – deshalb immer nur die nächsten 60,
/// neu geplant beim Öffnen der App und bei jeder Widget-Aktualisierung.
enum Erinnerungen {
    static let praefix = "termin-"
    static let vorlaufOptionen = [5, 10, 15, 20, 30, 45, 60]

    static var center: UNUserNotificationCenter { .current() }

    /// Fragt nach der Erlaubnis. Gibt zurück, ob Mitteilungen erlaubt sind.
    static func erlaubnisAnfragen() async -> Bool {
        let status = await center.notificationSettings().authorizationStatus
        switch status {
        case .authorized, .provisional, .ephemeral:
            return true
        case .notDetermined:
            return (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        default:
            return false
        }
    }

    static func planen() async {
        let alte = await center.pendingNotificationRequests()
            .map(\.identifier)
            .filter { $0.hasPrefix(praefix) }
        center.removePendingNotificationRequests(withIdentifiers: alte)

        let e = Speicher.einstellungen()
        guard e.erinnerungen else { return }
        let status = await center.notificationSettings().authorizationStatus
        guard status == .authorized || status == .provisional || status == .ephemeral else { return }

        let jetzt = Date()
        let vorlauf = TimeInterval(max(1, e.vorlaufMinuten) * 60)
        let kommende = Speicher.termine().filter { $0.start.addingTimeInterval(-vorlauf) > jetzt }.prefix(60)

        for t in kommende {
            let kurs = Kurse.info(t.titel).kurz
            let typ = Kurse.typ(t.typ).lang
            let inhalt = UNMutableNotificationContent()
            inhalt.title = "\(kurs) in \(e.vorlaufMinuten) Min"
            inhalt.body = "\(typ) · \(t.raum) · \(Zeit.uhrzeit(t.start))–\(Zeit.uhrzeit(t.ende))"
            inhalt.sound = .default
            inhalt.threadIdentifier = "stundenplan"
            inhalt.userInfo = ["url": Ziele.termin(t).absoluteString]

            let wann = t.start.addingTimeInterval(-vorlauf)
            let teile = Zeit.kalender.dateComponents([.year, .month, .day, .hour, .minute], from: wann)
            let ausloeser = UNCalendarNotificationTrigger(dateMatching: teile, repeats: false)
            let id = praefix + "\(Int(t.start.timeIntervalSince1970))-\(abs(t.titel.hashValue % 100000))"
            try? await center.add(UNNotificationRequest(identifier: id, content: inhalt, trigger: ausloeser))
        }
    }
}
