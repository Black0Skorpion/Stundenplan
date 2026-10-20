import WidgetKit
import SwiftUI

// ───────────────────────── Daten ─────────────────────────

struct SperrEintrag: TimelineEntry {
    let date: Date
    let termine: [Termin]
    let variante: SperrVariante
    /// Einstellung „Sperrbildschirm-Widgets ausblenden, solange die Live-Aktivität läuft“
    var ausblendenBis: Date? = nil

    var ausgeblendet: Bool { ausblendenBis.map { date < $0 } ?? false }

    /// Zeitraum, in dem die Live-Aktivität die Veranstaltung zeigt (nur wenn die Einstellung an ist)
    static func ausblendenBis() -> Date? {
        let e = Speicher.einstellungen()
        guard e.sperrAusblenden, e.liveAktivitaet, let info = Speicher.liveInfo else { return nil }
        return info.ende
    }
}

struct SperrProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SperrEintrag {
        SperrEintrag(date: Date(), termine: Speicher.termine(), variante: .standard)
    }

    func snapshot(for configuration: SperrEinstellung, in context: Context) async -> SperrEintrag {
        SperrEintrag(date: Date(), termine: Speicher.termine(), variante: configuration.variante,
                     ausblendenBis: context.isPreview ? nil : SperrEintrag.ausblendenBis())
    }

    func timeline(for configuration: SperrEinstellung, in context: Context) async -> Timeline<SperrEintrag> {
        let jetzt = Date()
        let alle = Speicher.termine()
        var zeiten = Set(Plan.wechselZeitpunkte(alle, ab: jetzt, stunden: 24))
        let bis = SperrEintrag.ausblendenBis()
        if let bis { zeiten.insert(bis) }   // nach der Live-Aktivität wieder einblenden
        // Zeile über der Uhr zeigt Minuten als Text → alle 5 Minuten für die nächsten 3 Stunden
        if configuration.variante.fuer(.accessoryInline) == .countdown {
            var t = jetzt
            for _ in 0..<36 {
                t = t.addingTimeInterval(5 * 60)
                zeiten.insert(t)
            }
        }
        let eintraege = ([jetzt] + zeiten.sorted().prefix(90)).map {
            SperrEintrag(date: $0, termine: alle, variante: configuration.variante, ausblendenBis: bis)
        }
        return Timeline(entries: eintraege, policy: .atEnd)
    }
}

// ───────────────────────── Widget ─────────────────────────

struct SperrWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "StundenplanSperr", intent: SperrEinstellung.self, provider: SperrProvider()) { eintrag in
            SperrAnsicht(eintrag: eintrag)
        }
        .configurationDisplayName("Stundenplan")
        .description("Nächste Veranstaltung mit Raum oder Countdown. Anzeige über „Widget bearbeiten“ wählbar.")
        .supportedFamilies([.accessoryInline, .accessoryRectangular, .accessoryCircular])
    }
}

struct SperrAnsicht: View {
    let eintrag: SperrEintrag
    @Environment(\.widgetFamily) private var familie

    var body: some View {
        let variante = eintrag.variante.fuer(familie)
        let jetzt = eintrag.date
        let naechster = Plan.naechster(eintrag.termine, jetzt)
        Group {
            if eintrag.ausgeblendet {
                // Die Live-Aktivität zeigt die Veranstaltung schon – hier nichts doppelt anzeigen
                Color.clear
            } else {
                switch familie {
                case .accessoryInline:
                    SperrZeile(termin: naechster, jetzt: jetzt, variante: variante)
                case .accessoryCircular:
                    SperrRund(termin: naechster, jetzt: jetzt, variante: variante)
                default:
                    SperrRechteck(alle: eintrag.termine, termin: naechster, jetzt: jetzt, variante: variante)
                }
            }
        }
        .containerBackground(for: .widget) { Color.clear }
        .widgetURL(ziel(variante, naechster, jetzt))
    }

    /// Tippen: Lageplan (mit Route) des Termins bzw. bei der Tagesübersicht des ganzen Tages
    private func ziel(_ variante: SperrVariante, _ naechster: Termin?, _ jetzt: Date) -> URL {
        if variante == .tag, let auswahl = Plan.tagesAuswahl(eintrag.termine, jetzt) {
            return Ziele.tag(auswahl.datum)
        }
        return naechster.map(Ziele.termin) ?? Ziele.app
    }
}

// ───────────────────────── Zeile über der Uhr ─────────────────────────

struct SperrZeile: View {
    let termin: Termin?
    let jetzt: Date
    let variante: SperrVariante

    // Die Zeile teilt sich den Platz mit dem Datum – deshalb so kurz wie möglich, ohne Symbol.
    var body: some View {
        if let t = termin {
            Text(text(t))
        } else {
            Text("Frei")
        }
    }

    /// "2.70.0.10" → "70.0.10" (Campus weglassen)
    private func raumKurz(_ raum: String) -> String {
        guard let r = RaumTeile(raum) else { return raum }
        return r.haus + "." + r.rest
    }

    /// "14:15" heute, sonst "Mo 8:15"
    private func wann(_ t: Termin) -> String {
        if Zeit.gleicherTag(t.start, jetzt) { return Zeit.uhrzeit(t.start) }
        return Zeit.wochentageKurz[Zeit.wochentag(t.start)] + " " + Zeit.uhrzeit(t.start)
    }

    /// "25 Min" bzw. "1:10 h"
    private func dauer(_ bis: Date) -> String {
        let min = max(0, Int(ceil(bis.timeIntervalSince(jetzt) / 60)))
        return min < 60 ? "\(min) Min" : "\(min / 60):" + String(format: "%02d", min % 60) + " h"
    }

    private func text(_ t: Termin) -> String {
        let kurs = Kurse.info(t.titel).kurz
        let laufend = t.laeuft(jetzt)
        switch variante {
        case .raum:
            return raumKurz(t.raum) + (laufend ? " bis " + Zeit.uhrzeit(t.ende) : " · " + wann(t))
        case .countdown where laufend || Zeit.gleicherTag(t.start, jetzt):
            return kurs + (laufend ? " noch " : " in ") + dauer(laufend ? t.ende : t.start)
        default:
            return kurs + (laufend ? " bis " + Zeit.uhrzeit(t.ende) : " " + wann(t))
        }
    }
}

// ───────────────────────── Rechteck ─────────────────────────

struct SperrRechteck: View {
    let alle: [Termin]
    let termin: Termin?
    let jetzt: Date
    let variante: SperrVariante

    var body: some View {
        if variante == .tag {
            tagesUebersicht
        } else if let t = termin {
            let kurs = Kurse.info(t.titel)
            let typ = Kurse.typ(t.typ)
            let laufend = t.laeuft(jetzt)
            if variante == .raum {
                VStack(alignment: .leading, spacing: 0) {
                    Text(kurs.kurz + " · " + (laufend ? "bis " + Zeit.uhrzeit(t.ende) : Zeit.wannText(t, jetzt)))
                        .font(.system(size: 12, weight: .semibold))
                        .opacity(0.8)
                    Text(t.raum)
                        .font(.system(size: 24, weight: .bold))
                        .minimumScaleFactor(0.6)
                    Text(typ.lang)
                        .font(.system(size: 11))
                        .opacity(0.7)
                }
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                let bald = !laufend && Zeit.gleicherTag(t.start, jetzt) && t.start.timeIntervalSince(jetzt) <= 3600
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 4) {
                        if laufend {
                            Text("JETZT · NOCH")
                            Text(timerInterval: jetzt...t.ende, countsDown: true).monospacedDigit()
                        } else if bald {
                            Text("BEGINNT IN")
                            Text(timerInterval: jetzt...t.start, countsDown: true).monospacedDigit()
                        } else {
                            Text(Zeit.wannText(t, jetzt).uppercased())
                            // Am Vorabend: wann der Wecker klingelt
                            if !Zeit.gleicherTag(t.start, jetzt), let w = WeckerStand.fuer(t) {
                                Text("·")
                                Image(systemName: "alarm.fill")
                                Text(Zeit.uhrzeit(w))
                            }
                        }
                    }
                    .font(.system(size: 10, weight: .bold))
                    .opacity(0.75)
                    Text(kurs.kurz + " · " + typ.lang)
                        .font(.system(size: 15, weight: .bold))
                        .minimumScaleFactor(0.75)
                    Text(t.raum + " · " + Zeit.uhrzeit(t.start) + "–" + Zeit.uhrzeit(t.ende))
                        .font(.system(size: 13, weight: .medium))
                        .opacity(0.9)
                        .minimumScaleFactor(0.75)
                }
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        } else {
            Text("Keine weiteren Termine")
                .font(.system(size: 13, weight: .semibold))
                .opacity(0.8)
        }
    }

    @ViewBuilder
    private var tagesUebersicht: some View {
        if let auswahl = Plan.tagesAuswahl(alle, jetzt) {
            let rest = Array(auswahl.termine.filter { $0.ende > jetzt }.prefix(3))
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 3) {
                    Text(auswahl.label.uppercased())
                    if !auswahl.istHeute, let erster = auswahl.termine.first, let w = WeckerStand.fuer(erster) {
                        Text("·")
                        Image(systemName: "alarm.fill")
                        Text(Zeit.uhrzeit(w))
                    }
                }
                .font(.system(size: 10, weight: .bold))
                .opacity(0.7)
                ForEach(rest) { t in
                    HStack(spacing: 5) {
                        Text(Zeit.uhrzeit(t.start))
                            .font(.system(size: 12, weight: .semibold).monospacedDigit())
                            .opacity(t.laeuft(jetzt) ? 1 : 0.8)
                        Text(Kurse.info(t.titel).kurz)
                            .font(.system(size: 12, weight: .bold))
                        Text(t.raum)
                            .font(.system(size: 12))
                            .opacity(0.8)
                    }
                    .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            Text("Keine weiteren Termine")
                .font(.system(size: 13, weight: .semibold))
                .opacity(0.8)
        }
    }
}

// ───────────────────────── Rund ─────────────────────────

struct SperrRund: View {
    let termin: Termin?
    let jetzt: Date
    let variante: SperrVariante

    var body: some View {
        ZStack {
            AccessoryWidgetBackground()
            if let t = termin {
                inhalt(t)
            } else {
                Text("–").font(.system(size: 13, weight: .semibold))
            }
        }
    }

    @ViewBuilder
    private func inhalt(_ t: Termin) -> some View {
        let kurs = Kurse.info(t.titel).kurz
        let laufend = t.laeuft(jetzt)
        let heute = Zeit.gleicherTag(t.start, jetzt)
        switch variante {
        case .raum:
            let r = RaumTeile(t.raum)
            VStack(spacing: 0) {
                Text(r?.haus ?? t.raum)
                    .font(.system(size: 22, weight: .bold))
                    .minimumScaleFactor(0.5)
                if let r {
                    Text(r.rest)
                        .font(.system(size: 11, weight: .semibold))
                        .opacity(0.8)
                        .minimumScaleFactor(0.6)
                }
            }
            .lineLimit(1)
            .padding(4)
        case .zeit:
            VStack(spacing: 0) {
                Text(laufend ? "bis" : kurs)
                    .font(.system(size: 10, weight: .semibold))
                    .opacity(0.75)
                Text(Zeit.uhrzeit(laufend ? t.ende : t.start))
                    .font(.system(size: 17, weight: .bold))
                if !laufend && !heute {
                    Text(Zeit.wochentageKurz[Zeit.wochentag(t.start)])
                        .font(.system(size: 9, weight: .semibold))
                        .opacity(0.7)
                }
            }
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .padding(4)
        default:
            // Countdown mit Ring: läuft gerade → Fortschritt der Veranstaltung, bald → letzte Stunde davor
            let bald = !laufend && heute && t.start.timeIntervalSince(jetzt) <= 3600
            if laufend || bald {
                let bereich = laufend ? t.start...t.ende : t.start.addingTimeInterval(-3600)...t.start
                ProgressView(timerInterval: bereich, countsDown: false) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
                .progressViewStyle(.circular)
                .padding(2)
            }
            VStack(spacing: 0) {
                Text(kurs)
                    .font(.system(size: 9, weight: .semibold))
                    .opacity(0.8)
                if laufend || heute {
                    Text(timerInterval: jetzt...(laufend ? t.ende : max(jetzt, t.start)), countsDown: true)
                        .font(.system(size: 13, weight: .bold))
                        .monospacedDigit()
                        .multilineTextAlignment(.center)
                } else {
                    Text(Zeit.uhrzeit(t.start))
                        .font(.system(size: 14, weight: .bold))
                    Text(Zeit.wochentageKurz[Zeit.wochentag(t.start)])
                        .font(.system(size: 9, weight: .semibold))
                        .opacity(0.7)
                }
            }
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .padding(6)
        }
    }
}
