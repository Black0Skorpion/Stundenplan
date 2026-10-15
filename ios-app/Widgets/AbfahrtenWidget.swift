import WidgetKit
import SwiftUI

struct AbfahrtenEintrag: TimelineEntry {
    let date: Date
    let liste: [Abfahrten.Abfahrt]?
    let thema: ThemaOption
    var campus: Campus = .golm
    var favoriten: [String] = []
}

struct AbfahrtenProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> AbfahrtenEintrag {
        let jetzt = Date()
        let beispiel = [
            Abfahrten.Abfahrt(id: "1", zeit: jetzt.addingTimeInterval(240), geplant: jetzt.addingTimeInterval(240), verspaetung: 0,
                              linie: "RB21", produkt: "regional", richtung: "Berlin Friedrichstr.", gleis: "2",
                              haltestelle: "Golm Bhf", ausfall: false),
            Abfahrten.Abfahrt(id: "2", zeit: jetzt.addingTimeInterval(540), geplant: jetzt.addingTimeInterval(480), verspaetung: 1,
                              linie: "605", produkt: "bus", richtung: "Potsdam, Charlottenhof Bhf", gleis: nil,
                              haltestelle: "Science Park/Uni", ausfall: false),
        ]
        return AbfahrtenEintrag(date: jetzt, liste: beispiel, thema: .automatisch, favoriten: ["RB21"])
    }

    func snapshot(for configuration: AbfahrtenEinstellung, in context: Context) async -> AbfahrtenEintrag {
        if context.isPreview { return placeholder(in: context) }
        let campus = configuration.campus.campus
        return AbfahrtenEintrag(date: Date(), liste: await laden(configuration, campus), thema: configuration.thema,
                                campus: campus, favoriten: Abfahrten.favoriten)
    }

    func timeline(for configuration: AbfahrtenEinstellung, in context: Context) async -> Timeline<AbfahrtenEintrag> {
        let campus = configuration.campus.campus
        let favoriten = Abfahrten.favoriten
        let liste = await laden(configuration, campus)
        // Jede Minute ein Eintrag (Minuten-Anzeige), nach 15 Minuten neu laden
        let jetzt = Date()
        let eintraege = (0..<15).map { i in
            AbfahrtenEintrag(date: jetzt.addingTimeInterval(Double(i) * 60), liste: liste, thema: configuration.thema,
                             campus: campus, favoriten: favoriten)
        }
        return Timeline(entries: eintraege, policy: .after(jetzt.addingTimeInterval(15 * 60)))
    }

    private func laden(_ c: AbfahrtenEinstellung, _ campus: Campus) async -> [Abfahrten.Abfahrt]? {
        guard let l = await Abfahrten.laden(campus: campus) else { return nil }
        return c.nurZuege ? l.filter(\.istZug) : l
    }
}

struct AbfahrtenWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Abfahrten", intent: AbfahrtenEinstellung.self, provider: AbfahrtenProvider()) { e in
            AbfahrtenAnsicht(eintrag: e)
        }
        .configurationDisplayName("Abfahrten")
        .description("Nächste Züge und Busse am Campus. Priorisierte Linien (in der App festlegen) sind immer dabei.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}

struct AbfahrtenAnsicht: View {
    let eintrag: AbfahrtenEintrag
    @Environment(\.widgetFamily) private var familie
    @Environment(\.colorScheme) private var schema

    var body: some View {
        let farben = eintrag.thema.farben(schema)
        let jetzt = eintrag.date
        let anzahl = familie == .systemSmall ? 3 : familie == .systemMedium ? 4 : 10
        VStack(alignment: .leading, spacing: familie == .systemSmall ? 5 : 6) {
            HStack {
                Text(familie == .systemSmall ? "Ab \(eintrag.campus.kurz)" : "Abfahrten \(eintrag.campus.kurz)")
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(farben.akzent)
                Spacer()
                Text(Zeit.uhrzeit(jetzt))
                    .font(.system(size: 11))
                    .foregroundStyle(farben.gedimmt)
            }
            if let liste = eintrag.liste {
                let sichtbar = Abfahrten.auswahl(liste.filter { $0.zeit > jetzt.addingTimeInterval(-30) },
                                                 anzahl: anzahl, favoriten: eintrag.favoriten)
                if sichtbar.isEmpty {
                    LeerHinweis(text: "Keine Abfahrten", farben: farben)
                } else {
                    ForEach(sichtbar) { a in
                        AbfahrtZeile(abfahrt: a, jetzt: jetzt, farben: farben, kompakt: familie == .systemSmall,
                                     favorit: Abfahrten.istFavorit(a, eintrag.favoriten))
                    }
                    Spacer(minLength: 0)
                }
            } else {
                LeerHinweis(text: "Nicht erreichbar", farben: farben)
            }
        }
        .padding(12)
        .containerBackground(for: .widget) { farben.bg }
        .widgetURL(Ziele.abfahrten)
    }
}
