import WidgetKit
import SwiftUI
import AppIntents

// ───────────────────────── Daten ─────────────────────────

struct MensaEintrag: TimelineEntry {
    let date: Date
    let tag: Mensa.Tag
    let plan: Mensa.Speiseplan?
    /// Zweiter Tag (nur Extragroß auf dem iPad)
    let tag2: Mensa.Tag?
    let plan2: Mensa.Speiseplan?
    let thema: ThemaOption
    let versatz: Int
    var campus: Campus = .golm
}

/// Überschrift „Mensa Golm“ usw. – wird von der Widget-Ansicht gesetzt
private struct MensaTitelSchluessel: EnvironmentKey {
    static let defaultValue = "Mensa"
}

extension EnvironmentValues {
    var mensaTitel: String {
        get { self[MensaTitelSchluessel.self] }
        set { self[MensaTitelSchluessel.self] = newValue }
    }
}

struct MensaProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> MensaEintrag {
        let heute = Mensa.Tag(datum: Zeit.tagesBeginn(Date()), geschlossen: false)
        let beispiel = Mensa.Speiseplan(gerichte: [
            Mensa.Gericht(name: "Gemüsecurry mit Reis", kat: "Angebot 1", preis: 2.9, farbe: 0x1D9E75),
            Mensa.Gericht(name: "Pasta mit Tomatensoße", kat: "Angebot 2", preis: 2.5, farbe: 0x639922),
            Mensa.Gericht(name: "Hähnchenbrust mit Kartoffeln", kat: "Angebot 3", preis: 3.9, farbe: 0xD85A30),
        ])
        return MensaEintrag(date: Date(), tag: heute, plan: beispiel, tag2: nil, plan2: nil, thema: .automatisch, versatz: 0)
    }

    func snapshot(for configuration: MensaEinstellung, in context: Context) async -> MensaEintrag {
        if context.isPreview { return placeholder(in: context) }
        return await laden(configuration, context)
    }

    func timeline(for configuration: MensaEinstellung, in context: Context) async -> Timeline<MensaEintrag> {
        let eintrag = await laden(configuration, context)
        // Neu laden: in 2 Stunden, um 15 Uhr (Umschalten auf den nächsten Tag) oder um Mitternacht
        let jetzt = Date()
        var naechstes = jetzt.addingTimeInterval(2 * 3600)
        if let um15 = Zeit.kalender.date(bySettingHour: Mensa.umschaltenUm, minute: 0, second: 5, of: jetzt), um15 > jetzt {
            naechstes = min(naechstes, um15)
        }
        naechstes = min(naechstes, Zeit.plusTage(Zeit.tagesBeginn(jetzt), 1).addingTimeInterval(5))
        return Timeline(entries: [eintrag], policy: .after(naechstes))
    }

    private func laden(_ c: MensaEinstellung, _ context: Context) async -> MensaEintrag {
        let jetzt = Date()
        let versatz = Speicher.mensaVersatz()
        let campus = c.campus.campus
        let tage = await Mensa.tage(campus: campus)
        let tag = Mensa.waehle(c.tag.auswahl, tage, jetzt, versatz: versatz)
        let plan = tag.geschlossen ? nil : await Mensa.speiseplan(tag.datum, campus: campus)
        var tag2: Mensa.Tag? = nil
        var plan2: Mensa.Speiseplan? = nil
        if context.family == .systemExtraLarge {
            let t2 = Mensa.naechsterOffener(tage, nach: tag)
            tag2 = t2
            plan2 = await Mensa.speiseplan(t2.datum, campus: campus)
        }
        return MensaEintrag(date: jetzt, tag: tag, plan: plan, tag2: tag2, plan2: plan2, thema: c.thema, versatz: versatz, campus: campus)
    }
}

// ───────────────────────── Widget ─────────────────────────

struct MensaWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Mensa", intent: MensaEinstellung.self, provider: MensaProvider()) { eintrag in
            MensaAnsicht(eintrag: eintrag)
        }
        .configurationDisplayName("Mensa")
        .description("Speiseplan mit Preisen für Studierende. Pfeile oben rechts blättern durch die Tage – oder mehrere Mensa-Widgets (Tag „+1“, „+2“ …) stapeln und wischen.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .systemExtraLarge])
        .contentMarginsDisabled()
    }
}

struct MensaAnsicht: View {
    let eintrag: MensaEintrag
    @Environment(\.widgetFamily) private var familie
    @Environment(\.colorScheme) private var schema

    var body: some View {
        let farben = eintrag.thema.farben(schema)
        Group {
            switch familie {
            case .systemSmall:
                MensaKlein(eintrag: eintrag, farben: farben)
            case .systemMedium:
                MensaMittel(eintrag: eintrag, farben: farben)
            case .systemExtraLarge:
                HStack(alignment: .top, spacing: 18) {
                    MensaSpalte(tag: eintrag.tag, plan: eintrag.plan, jetzt: eintrag.date, farben: farben, pfeile: true, versatz: eintrag.versatz)
                    if let t2 = eintrag.tag2 {
                        MensaSpalte(tag: t2, plan: eintrag.plan2, jetzt: eintrag.date, farben: farben, pfeile: false, versatz: 0)
                    }
                }
                .padding(12)
            default:
                MensaSpalte(tag: eintrag.tag, plan: eintrag.plan, jetzt: eintrag.date, farben: farben, pfeile: true, versatz: eintrag.versatz)
                    .padding(12)
            }
        }
        .environment(\.mensaTitel, eintrag.campus.mensaName)
        .containerBackground(for: .widget) { farben.bg }
        .widgetURL(Ziele.mensa)
    }
}

// ───────────────────────── Bausteine ─────────────────────────

/// Pfeile zum Blättern (interaktiv ab iOS 17). Die Tippfläche ist deutlich größer als das Symbol.
struct MensaPfeile: View {
    let farben: Farben
    let versatz: Int

    var body: some View {
        HStack(spacing: 0) {
            Button(intent: MensaBlaettern(richtung: -1)) {
                Image(systemName: "chevron.left")
                    .frame(width: 34, height: 30)
                    .contentShape(Rectangle())
            }
            .disabled(versatz <= 0)
            .opacity(versatz <= 0 ? 0.35 : 1)
            Button(intent: MensaBlaettern(richtung: 1)) {
                Image(systemName: "chevron.right")
                    .frame(width: 34, height: 30)
                    .contentShape(Rectangle())
            }
        }
        .buttonStyle(.plain)
        .font(.system(size: 15, weight: .bold))
        .foregroundStyle(farben.akzent)
        .padding(.vertical, -6)     // größere Tippfläche, ohne die Zeile höher zu machen
        .padding(.trailing, -8)
    }
}

struct MensaKopf: View {
    let tag: Mensa.Tag
    let jetzt: Date
    let farben: Farben
    let pfeile: Bool
    let versatz: Int
    var kurz = false
    @Environment(\.mensaTitel) private var titel

    var body: some View {
        HStack(spacing: 4) {
            Text(kurz ? "Mensa" : titel)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(farben.akzent)
            Spacer(minLength: 2)
            Text(rechts)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(farben.gedimmt)
                .minimumScaleFactor(0.8)
            if pfeile {
                MensaPfeile(farben: farben, versatz: versatz)
            }
        }
        .lineLimit(1)
        .frame(height: 18)
    }

    private var rechts: String {
        let label = Mensa.tagesLabel(tag.datum, jetzt)
        if kurz {
            return label == "Heute" ? "Heute" : Zeit.wochentageKurz[Zeit.wochentag(tag.datum)] + " \(Zeit.tag(tag.datum))."
        }
        return ["Heute", "Morgen"].contains(label) ? label + " · " + Zeit.tagMonat(tag.datum) : Zeit.datumKurz(tag.datum)
    }
}

struct MensaHinweis: View {
    let tag: Mensa.Tag
    let plan: Mensa.Speiseplan?
    let farben: Farben
    var kurz = false

    /// nil, wenn es etwas anzuzeigen gibt
    static func text(_ tag: Mensa.Tag, _ plan: Mensa.Speiseplan?, kurz: Bool) -> String? {
        if tag.geschlossen { return kurz ? "Geschlossen" : "Mensa geschlossen" }
        guard let plan else { return kurz ? "Nicht erreichbar" : "Speiseplan nicht erreichbar" }
        if plan.leer { return "Noch kein Speiseplan" }
        return nil
    }

    var body: some View {
        Text(MensaHinweis.text(tag, plan, kurz: kurz) ?? "")
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(farben.gedimmt)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// ───────────────────────── Klein ─────────────────────────

struct MensaKlein: View {
    let eintrag: MensaEintrag
    let farben: Farben

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            MensaKopf(tag: eintrag.tag, jetzt: eintrag.date, farben: farben, pfeile: true, versatz: eintrag.versatz, kurz: true)
            if MensaHinweis.text(eintrag.tag, eintrag.plan, kurz: true) != nil {
                MensaHinweis(tag: eintrag.tag, plan: eintrag.plan, farben: farben, kurz: true)
            } else if let plan = eintrag.plan {
                let liste = plan.alle
                ForEach(liste.prefix(2)) { g in
                    HStack(alignment: .top, spacing: 6) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(Color(hex: g.farbe))
                            .frame(width: 4, height: 40)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(g.name)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(farben.vorder)
                                .lineLimit(2)
                                .minimumScaleFactor(0.85)
                            Text(Mensa.preisText(g.preis) + " · " + g.kat)
                                .font(.system(size: 10))
                                .foregroundStyle(farben.gedimmt)
                                .lineLimit(1)
                        }
                    }
                }
                Spacer(minLength: 0)
                if liste.count > 2 {
                    Text("+ \(liste.count - 2) weitere")
                        .font(.system(size: 10))
                        .foregroundStyle(farben.gedimmt)
                }
            }
        }
        .padding(12)
    }
}

// ───────────────────────── Mittel ─────────────────────────

struct MensaMittel: View {
    let eintrag: MensaEintrag
    let farben: Farben

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            MensaKopf(tag: eintrag.tag, jetzt: eintrag.date, farben: farben, pfeile: true, versatz: eintrag.versatz)
            if MensaHinweis.text(eintrag.tag, eintrag.plan, kurz: false) != nil {
                MensaHinweis(tag: eintrag.tag, plan: eintrag.plan, farben: farben)
            } else if let plan = eintrag.plan {
                let liste = plan.alle
                ForEach(liste.prefix(3)) { g in
                    GerichtZeile(gericht: g, farben: farben, zeilen: 1)
                }
                if liste.count > 3 {
                    Text("+ \(liste.count - 3) weitere")
                        .font(.system(size: 10))
                        .foregroundStyle(farben.gedimmt)
                }
                Spacer(minLength: 0)
            }
        }
        .padding(11)
    }
}

// ───────────────────────── Groß / Extragroß: ganzer Tag ─────────────────────────

struct MensaSpalte: View {
    let tag: Mensa.Tag
    let plan: Mensa.Speiseplan?
    let jetzt: Date
    let farben: Farben
    let pfeile: Bool
    let versatz: Int

    var body: some View {
        GeometryReader { geo in
            VStack(alignment: .leading, spacing: 0) {
                MensaKopf(tag: tag, jetzt: jetzt, farben: farben, pfeile: pfeile, versatz: versatz)
                    .padding(.bottom, 8)
                if MensaHinweis.text(tag, plan, kurz: false) != nil {
                    MensaHinweis(tag: tag, plan: plan, farben: farben)
                } else if let plan {
                    inhalt(plan, hoehe: geo.size.height - 26)
                }
            }
        }
    }

    private struct Planung {
        var haupt: [Mensa.Gericht] = []
        var abend: [Mensa.Gericht] = []
        var dessertVoll = false
        var weggelassen = 0
    }

    /// Plant grob, was in die verfügbare Höhe passt (Hauptgerichte zuerst, dann Abend und Dessert).
    private func planen(_ plan: Mensa.Speiseplan, hoehe: CGFloat) -> Planung {
        let zeileH: CGFloat = 38
        let titelH: CGFloat = 18
        var rest = hoehe - (plan.theke.isEmpty ? 0 : 16) - 14
        var p = Planung()
        p.haupt = Array(plan.gerichte.prefix(max(0, Int(rest / zeileH))))
        rest -= CGFloat(p.haupt.count) * zeileH
        let abendPlatz = Int(max(0, rest - titelH) / zeileH)
        if !plan.abend.isEmpty && abendPlatz > 0 {
            p.abend = Array(plan.abend.prefix(abendPlatz))
            rest -= titelH + CGFloat(p.abend.count) * zeileH
        }
        p.dessertVoll = !plan.desserts.isEmpty && rest >= titelH + CGFloat(plan.desserts.count) * 30
        p.weggelassen = plan.gerichte.count - p.haupt.count + plan.abend.count - p.abend.count
        return p
    }

    @ViewBuilder
    private func inhalt(_ plan: Mensa.Speiseplan, hoehe: CGFloat) -> some View {
        let p = planen(plan, hoehe: hoehe)
        let haupt = p.haupt
        let abend = p.abend
        let dessertVoll = p.dessertVoll
        let weggelassen = p.weggelassen

        VStack(alignment: .leading, spacing: 8) {
            ForEach(haupt) { g in GerichtZeile(gericht: g, farben: farben) }
            if !abend.isEmpty {
                Text("Abends")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(farben.gedimmt)
                ForEach(abend) { g in GerichtZeile(gericht: g, farben: farben) }
            }
            if dessertVoll {
                Text("Dessert")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(farben.gedimmt)
                ForEach(plan.desserts) { g in GerichtZeile(gericht: g, farben: farben, zeilen: 1) }
            }
            if weggelassen > 0 {
                Text("+ \(weggelassen) weitere")
                    .font(.system(size: 10))
                    .foregroundStyle(farben.gedimmt)
            }
            Spacer(minLength: 0)
            if !dessertVoll && !plan.desserts.isEmpty {
                Text("Dessert: " + plan.desserts.map { $0.name + " " + Mensa.preisText($0.preis) }.joined(separator: " · "))
                    .font(.system(size: 10))
                    .foregroundStyle(farben.gedimmt)
                    .lineLimit(1)
            }
            if !plan.theke.isEmpty {
                Text(Mensa.thekeText(plan.theke))
                    .font(.system(size: 10))
                    .foregroundStyle(farben.gedimmt)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
    }
}
