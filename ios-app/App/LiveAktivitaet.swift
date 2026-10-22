import ActivityKit
import Foundation

/// Live-Aktivität (Dynamic Island / Sperrbildschirm) für die laufende Veranstaltung – erst ab Beginn.
/// Ab iOS 26 werden die nächsten Veranstaltungen vorgemerkt: iOS startet die Aktivität dann selbst zum Beginn,
/// auch wenn die App zu ist. Vorher (iOS 18–25) kann sie nur starten, während die App offen ist.
/// Beenden: zum Ende wird sie „veraltet“ und zeigt „vorbei“ mit ✕ (schließt ohne App); beim nächsten Öffnen
/// der App (oder im Hintergrund-Abgleich) wird sie ganz entfernt.
enum LiveAktivitaet {
    /// So viele Veranstaltungen werden höchstens vorgemerkt (iOS begrenzt gleichzeitige Aktivitäten)
    private static let maxVorgemerkt = 3

    static func aktualisieren(_ termine: [Termin]) {
        guard Speicher.einstellungen().liveAktivitaet,
              ActivityAuthorizationInfo().areActivitiesEnabled else {
            alleBeenden()
            return
        }
        let jetzt = Date()
        let laufend = termine.first(where: { $0.laeuft(jetzt) && !$0.ausfall })
        var gewuenscht: [Termin] = laufend.map { [$0] } ?? []
        if #available(iOS 26.0, *) {
            // Die nächsten Veranstaltungen (heute und morgen) vormerken
            let bis = Zeit.plusTage(Zeit.tagesBeginn(jetzt), 2)
            gewuenscht += termine.filter { !$0.ausfall && $0.start > jetzt && $0.start < bis }
                .prefix(maxVorgemerkt - gewuenscht.count)
        }
        let schluessel = Set(gewuenscht.map(\.schluessel))

        // Nicht mehr gewünschte (vorbei, ausgefallen, Plan geändert) beenden bzw. Vormerkung zurücknehmen
        var vorhanden: [String: Activity<VorlesungAttribute>] = [:]
        for a in Activity<VorlesungAttribute>.activities {
            if schluessel.contains(a.attributes.schluessel), vorhanden[a.attributes.schluessel] == nil {
                vorhanden[a.attributes.schluessel] = a
            } else {
                Task { await a.end(nil, dismissalPolicy: .immediate) }
            }
        }

        for t in gewuenscht {
            let inhalt = ActivityContent(state: zustand(t, termine), staleDate: t.ende)
            if let a = vorhanden[t.schluessel] {
                Task { await a.update(inhalt) }   // z. B. Raum geändert
                continue
            }
            let attribute = VorlesungAttribute(schluessel: t.schluessel)
            if t.start > jetzt {
                if #available(iOS 26.0, *) {
                    // iOS verlangt beim Vormerken einen Hinweis zum Start
                    let hinweis = AlertConfiguration(
                        title: LocalizedStringResource(String.LocalizationValue("\(Kurse.info(t.titel).kurz) beginnt")),
                        body: LocalizedStringResource(String.LocalizationValue("\(Kurse.typ(t.typ).lang) · \(t.raum) · bis \(Zeit.uhrzeit(t.ende))")),
                        sound: .default)
                    _ = try? Activity.request(attributes: attribute, content: inhalt, pushType: nil,
                                              style: .standard, alertConfiguration: hinweis, start: t.start)
                }
            } else {
                _ = try? Activity.request(attributes: attribute, content: inhalt, pushType: nil)
                // Solange die App offen bleibt: nach dem Ende entfernen
                let ende = t.ende, k = t.schluessel
                Task {
                    try? await Task.sleep(nanoseconds: UInt64((max(0, ende.timeIntervalSinceNow) + 5) * 1_000_000_000))
                    for a in Activity<VorlesungAttribute>.activities where a.attributes.schluessel == k {
                        await a.end(nil, dismissalPolicy: .immediate)
                    }
                }
            }
        }

        // Für „Sperrbildschirm-Widgets ausblenden, solange die Live-Aktivität läuft“
        let info = gewuenscht.first.map { Speicher.LiveInfo(schluessel: $0.schluessel, start: $0.start, ende: $0.ende) }
        if Speicher.liveInfo?.schluessel != info?.schluessel || Speicher.liveInfo?.start != info?.start {
            Speicher.setzeLiveInfo(info)
        }
    }

    private static func zustand(_ t: Termin, _ termine: [Termin]) -> VorlesungAttribute.ContentState {
        let danach = termine.first(where: { $0.start >= t.ende && Zeit.gleicherTag($0.start, t.start) && !$0.ausfall })
        return VorlesungAttribute.ContentState(
            start: t.start, ende: t.ende, titel: t.titel,
            kurz: Kurse.info(t.titel).kurz, typ: Kurse.typ(t.typ).lang, raum: t.raum,
            farbe: Kurse.info(t.titel).hex,
            danach: danach.map { "\(Zeit.uhrzeit($0.start)) \(Kurse.info($0.titel).kurz) · \($0.raum)" })
    }

    static func alleBeenden() {
        for a in Activity<VorlesungAttribute>.activities {
            Task { await a.end(nil, dismissalPolicy: .immediate) }
        }
        if Speicher.liveInfo != nil { Speicher.setzeLiveInfo(nil) }
    }
}
