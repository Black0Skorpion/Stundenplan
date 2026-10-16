import ActivityKit
import Foundation

/// Startet/beendet die Live-Aktivität für die laufende bzw. nächste Veranstaltung (ab 1 Stunde vorher).
/// Ohne eigenen Server kann iOS sie nur starten, während die App offen ist – z. B. beim Antippen
/// einer Erinnerung. Danach läuft der Countdown von selbst und endet mit der Veranstaltung.
enum LiveAktivitaet {

    static func aktualisieren(_ termine: [Termin]) {
        guard Speicher.einstellungen().liveAktivitaet,
              ActivityAuthorizationInfo().areActivitiesEnabled else {
            alleBeenden()
            return
        }
        let jetzt = Date()
        let ziel = termine.first(where: { $0.laeuft(jetzt) })
            ?? termine.first(where: { $0.start > jetzt && $0.start.timeIntervalSince(jetzt) <= 3600 })

        guard let t = ziel else {
            alleBeenden()
            return
        }

        let danach = termine.first(where: { $0.start >= t.ende && Zeit.gleicherTag($0.start, t.start) })
        let zustand = VorlesungAttribute.ContentState(
            start: t.start, ende: t.ende, titel: t.titel,
            kurz: Kurse.info(t.titel).kurz, typ: Kurse.typ(t.typ).lang, raum: t.raum,
            farbe: Kurse.info(t.titel).hex,
            danach: danach.map { "\(Zeit.uhrzeit($0.start)) \(Kurse.info($0.titel).kurz) · \($0.raum)" })
        // Vor Beginn wird die Aktivität zum Beginn „veraltet“ – dann zeichnet iOS sie neu und
        // zeigt ab da die laufende Vorlesung (ohne Countdown). Danach gilt das Ende.
        let inhalt = ActivityContent(state: zustand, staleDate: jetzt < t.start ? t.start : t.ende)

        // Läuft schon eine für diesen Termin? Dann nur aktualisieren (z. B. Raum geändert)
        var vorhanden = false
        for a in Activity<VorlesungAttribute>.activities {
            if a.attributes.schluessel == t.schluessel {
                vorhanden = true
                Task { await a.update(inhalt) }
            } else {
                Task { await a.end(nil, dismissalPolicy: .immediate) }
            }
        }
        if vorhanden {
            if Speicher.liveInfo?.schluessel != t.schluessel {
                Speicher.setzeLiveInfo(Speicher.LiveInfo(schluessel: t.schluessel, start: t.start, ende: t.ende))
            }
            return
        }
        guard (try? Activity.request(attributes: VorlesungAttribute(schluessel: t.schluessel),
                                     content: inhalt, pushType: nil)) != nil else { return }
        Speicher.setzeLiveInfo(Speicher.LiveInfo(schluessel: t.schluessel, start: t.start, ende: t.ende))
        // Nach dem Ende automatisch verschwinden
        let ende = t.ende
        Task {
            let warten = max(0, ende.timeIntervalSinceNow) + 5
            try? await Task.sleep(nanoseconds: UInt64(warten * 1_000_000_000))
            for a in Activity<VorlesungAttribute>.activities where a.attributes.schluessel == t.schluessel {
                await a.end(nil, dismissalPolicy: .immediate)
            }
            if Speicher.liveInfo?.schluessel == t.schluessel { Speicher.setzeLiveInfo(nil) }
        }
    }

    static func alleBeenden() {
        for a in Activity<VorlesungAttribute>.activities {
            Task { await a.end(nil, dismissalPolicy: .immediate) }
        }
        if Speicher.liveInfo != nil { Speicher.setzeLiveInfo(nil) }
    }
}
