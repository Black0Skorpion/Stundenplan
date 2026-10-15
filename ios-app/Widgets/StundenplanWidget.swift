import WidgetKit
import SwiftUI

// ───────────────────────── Daten ─────────────────────────

struct StundenplanEintrag: TimelineEntry {
    let date: Date
    let termine: [Termin]
    let thema: ThemaOption
}

struct StundenplanProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> StundenplanEintrag {
        StundenplanEintrag(date: Date(), termine: Speicher.termine(), thema: .automatisch)
    }

    func snapshot(for configuration: StundenplanEinstellung, in context: Context) async -> StundenplanEintrag {
        StundenplanEintrag(date: Date(), termine: Speicher.termine(), thema: configuration.thema)
    }

    func timeline(for configuration: StundenplanEinstellung, in context: Context) async -> Timeline<StundenplanEintrag> {
        // Erinnerungen nachplanen (iOS behält nur 64) – nur mit gemeinsamem Speicher,
        // sonst kennt das Widget die Einstellung aus der App nicht.
        if Speicher.geteilt { await Erinnerungen.planen() }
        let jetzt = Date()
        let alle = Speicher.termine()
        // Wechsel (Beginn/Ende) und alle 15 Minuten für die rote Jetzt-Linie
        var zeiten = Set(Plan.wechselZeitpunkte(alle, ab: jetzt, stunden: 12))
        var t = jetzt
        for _ in 0..<48 {
            t = t.addingTimeInterval(15 * 60)
            zeiten.insert(t)
        }
        let eintraege = ([jetzt] + zeiten.sorted().prefix(90)).map {
            StundenplanEintrag(date: $0, termine: alle, thema: configuration.thema)
        }
        return Timeline(entries: eintraege, policy: .atEnd)
    }
}

// ───────────────────────── Widget ─────────────────────────

struct StundenplanWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Stundenplan", intent: StundenplanEinstellung.self, provider: StundenplanProvider()) { eintrag in
            StundenplanAnsicht(eintrag: eintrag)
        }
        .configurationDisplayName("Stundenplan")
        .description("Aktuelle Veranstaltung mit Countdown, Tagesplan oder ganze Woche.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .systemExtraLarge])
        .contentMarginsDisabled()
    }
}

struct StundenplanAnsicht: View {
    let eintrag: StundenplanEintrag
    @Environment(\.widgetFamily) private var familie
    @Environment(\.colorScheme) private var schema

    var body: some View {
        let farben = eintrag.thema.farben(schema)
        Group {
            switch familie {
            case .systemSmall: KleinAnsicht(eintrag: eintrag, farben: farben)
            case .systemMedium: MittelAnsicht(eintrag: eintrag, farben: farben)
            case .systemExtraLarge: ExtraGrossAnsicht(eintrag: eintrag, farben: farben)
            default: GrossAnsicht(eintrag: eintrag, farben: farben)
            }
        }
        .containerBackground(for: .widget) { farben.bg }
        // Tippen irgendwo im Widget öffnet die App auf der Seite „Plan“
        .widgetURL(Ziele.app)
    }
}

// ───────────────────────── Bausteine ─────────────────────────

struct LeerHinweis: View {
    let text: String
    let farben: Farben

    var body: some View {
        Text(text)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(farben.gedimmt)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// "Heute  Di, 13. Okt"
struct TagesKopf: View {
    let auswahl: Plan.TagesAuswahl
    let farben: Farben

    var body: some View {
        HStack(spacing: 5) {
            Text(auswahl.label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(auswahl.istHeute ? farben.akzent : farben.vorder)
            Text(["Heute", "Morgen"].contains(auswahl.label) ? Zeit.datumKurz(auswahl.datum) : Zeit.tagMonat(auswahl.datum))
                .font(.system(size: 11))
                .foregroundStyle(farben.gedimmt)
            Spacer(minLength: 0)
        }
        .lineLimit(1)
    }
}

struct WochenKopf: View {
    let woche: Plan.Woche
    let jetzt: Date
    let status: String?
    let farben: Farben

    var body: some View {
        HStack(spacing: 6) {
            Text(woche.naechsteWoche
                 ? "Nächste Woche · KW \(Zeit.kalenderwoche(woche.montag))"
                 : "\(Zeit.datumKurz(jetzt)) · KW \(Zeit.kalenderwoche(jetzt))")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(farben.vorder)
                .layoutPriority(1)
            Spacer(minLength: 4)
            if let status {
                Text(status)
                    .font(.system(size: 11))
                    .foregroundStyle(farben.gedimmt)
                    .minimumScaleFactor(0.8)
            }
        }
        .lineLimit(1)
        .frame(height: 16)
    }
}

struct WochenTeil: View {
    let eintrag: StundenplanEintrag
    let farben: Farben
    let mitStatus: Bool

    var body: some View {
        let woche = Plan.woche(eintrag.termine, eintrag.date)
        VStack(spacing: 5) {
            WochenKopf(woche: woche, jetzt: eintrag.date,
                       status: mitStatus ? Plan.statusText(eintrag.termine, eintrag.date) : nil, farben: farben)
            if woche.leer {
                LeerHinweis(text: "Keine Termine in dieser Woche", farben: farben)
            } else {
                WochenRaster(woche: woche, jetzt: eintrag.date, links: false)
            }
        }
    }
}

// ───────────────────────── Klein (2×2) ─────────────────────────

struct KleinAnsicht: View {
    let eintrag: StundenplanEintrag
    let farben: Farben

    var body: some View {
        let jetzt = eintrag.date
        if let auswahl = Plan.tagesAuswahl(eintrag.termine, jetzt), let fokus = Plan.fokus(auswahl, jetzt) {
            let danach = auswahl.termine.first(where: { $0.start > fokus.start })
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(auswahl.label)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(auswahl.istHeute ? farben.akzent : farben.vorder)
                    Spacer(minLength: 2)
                    Text(Zeit.tagMonat(auswahl.datum))
                        .font(.system(size: 11))
                        .foregroundStyle(farben.gedimmt)
                }
                .lineLimit(1)
                Spacer(minLength: 5)
                TerminKarte(termin: fokus, istHeute: auswahl.istHeute, jetzt: jetzt)
                Spacer(minLength: 5)
                HStack(spacing: 4) {
                    if let danach {
                        Text("Danach").foregroundStyle(farben.gedimmt)
                        Text(Zeit.uhrzeit(danach.start) + " " + Kurse.info(danach.titel).kurz)
                            .fontWeight(.semibold)
                            .foregroundStyle(farben.vorder)
                            .minimumScaleFactor(0.7)
                    } else {
                        Text(auswahl.istHeute ? "Danach frei" : "Sonst nichts an dem Tag")
                            .foregroundStyle(farben.gedimmt)
                    }
                    Spacer(minLength: 0)
                }
                .font(.system(size: 11))
                .lineLimit(1)
            }
            .padding(12)
        } else {
            LeerHinweis(text: "Keine weiteren Termine", farben: farben)
                .padding(12)
        }
    }
}

// ───────────────────────── Mittel (4×2) ─────────────────────────

struct MittelAnsicht: View {
    let eintrag: StundenplanEintrag
    let farben: Farben

    var body: some View {
        let jetzt = eintrag.date
        if let auswahl = Plan.tagesAuswahl(eintrag.termine, jetzt), let fokus = Plan.fokus(auswahl, jetzt) {
            GeometryReader { geo in
                let innenB = geo.size.width - 24
                let innenH = geo.size.height - 24
                let linksB = floor((innenB - 12) * 0.47)
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 6) {
                        TagesKopf(auswahl: auswahl, farben: farben)
                        TerminKarte(termin: fokus, istHeute: auswahl.istHeute, jetzt: jetzt, ausfuehrlich: true)
                        Spacer(minLength: 0)
                    }
                    .frame(width: linksB)
                    VStack(alignment: .leading, spacing: 6) {
                        let n = auswahl.termine.count
                        Text("\(n) " + (n == 1 ? "Termin" : "Termine"))
                            .font(.system(size: 11))
                            .foregroundStyle(farben.gedimmt)
                        TagesListe(termine: auswahl.termine, jetzt: jetzt, farben: farben,
                                   maxZeilen: max(1, Int((innenH - 20) / 33)), links: false)
                        Spacer(minLength: 0)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(width: innenB, height: innenH, alignment: .topLeading)
                .padding(12)
            }
        } else {
            LeerHinweis(text: "Keine weiteren Termine", farben: farben)
                .padding(12)
        }
    }
}

// ───────────────────────── Groß (4×4) ─────────────────────────

struct GrossAnsicht: View {
    let eintrag: StundenplanEintrag
    let farben: Farben

    var body: some View {
        WochenTeil(eintrag: eintrag, farben: farben, mitStatus: true)
            .padding(11)
    }
}

// ───────────────────────── Extragroß (iPad) ─────────────────────────

struct ExtraGrossAnsicht: View {
    let eintrag: StundenplanEintrag
    let farben: Farben

    var body: some View {
        let jetzt = eintrag.date
        GeometryReader { geo in
            let innenB = geo.size.width - 24
            let innenH = geo.size.height - 24
            let rechtsB = min(250, (innenB * 0.34).rounded())
            HStack(alignment: .top, spacing: 16) {
                WochenTeil(eintrag: eintrag, farben: farben, mitStatus: false)
                VStack(alignment: .leading, spacing: 6) {
                    if let auswahl = Plan.tagesAuswahl(eintrag.termine, jetzt), let fokus = Plan.fokus(auswahl, jetzt) {
                        TagesKopf(auswahl: auswahl, farben: farben)
                        TerminKarte(termin: fokus, istHeute: auswahl.istHeute, jetzt: jetzt, ausfuehrlich: true)
                        let n = auswahl.termine.count
                        Text((auswahl.istHeute ? "Heute · " : auswahl.label + " · ") + "\(n) " + (n == 1 ? "Termin" : "Termine"))
                            .font(.system(size: 11))
                            .foregroundStyle(farben.gedimmt)
                            .padding(.top, 6)
                        TagesListe(termine: auswahl.termine, jetzt: jetzt, farben: farben,
                                   maxZeilen: max(1, Int((innenH - 170) / 33)), links: false)
                        Spacer(minLength: 0)
                    } else {
                        LeerHinweis(text: "Keine weiteren Termine", farben: farben)
                    }
                }
                .frame(width: rechtsB)
            }
            .frame(width: innenB, height: innenH, alignment: .topLeading)
            .padding(12)
        }
    }
}
