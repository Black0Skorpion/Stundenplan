import WidgetKit
import SwiftUI

struct AbfahrtenEintrag: TimelineEntry {
    let date: Date
    let liste: [Abfahrten.Abfahrt]?
    let thema: ThemaOption
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
        return AbfahrtenEintrag(date: jetzt, liste: beispiel, thema: .automatisch)
    }

    func snapshot(for configuration: AbfahrtenEinstellung, in context: Context) async -> AbfahrtenEintrag {
        if context.isPreview { return placeholder(in: context) }
        return AbfahrtenEintrag(date: Date(), liste: await laden(configuration), thema: configuration.thema)
    }

    func timeline(for configuration: AbfahrtenEinstellung, in context: Context) async -> Timeline<AbfahrtenEintrag> {
        let liste = await laden(configuration)
        // Jede Minute ein Eintrag (Minuten-Anzeige), nach 15 Minuten neu laden
        let jetzt = Date()
        let eintraege = (0..<15).map { i in
            AbfahrtenEintrag(date: jetzt.addingTimeInterval(Double(i) * 60), liste: liste, thema: configuration.thema)
        }
        return Timeline(entries: eintraege, policy: .after(jetzt.addingTimeInterval(15 * 60)))
    }

    private func laden(_ c: AbfahrtenEinstellung) async -> [Abfahrten.Abfahrt]? {
        guard let l = await Abfahrten.laden() else { return nil }
        return c.nurZuege ? l.filter(\.istZug) : l
    }
}

struct AbfahrtenWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Abfahrten", intent: AbfahrtenEinstellung.self, provider: AbfahrtenProvider()) { e in
            AbfahrtenAnsicht(eintrag: e)
        }
        .configurationDisplayName("Abfahrten Golm")
        .description("Nächste Züge und Busse ab Golm Bhf und Science Park/Uni.")
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
                Text(familie == .systemSmall ? "Ab Golm" : "Abfahrten Golm")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(farben.akzent)
                Spacer()
                Text(Zeit.uhrzeit(jetzt))
                    .font(.system(size: 11))
                    .foregroundStyle(farben.gedimmt)
            }
            if let liste = eintrag.liste {
                let sichtbar = Array(liste.filter { $0.zeit > jetzt.addingTimeInterval(-30) }.prefix(anzahl))
                if sichtbar.isEmpty {
                    LeerHinweis(text: "Keine Abfahrten", farben: farben)
                } else {
                    ForEach(sichtbar) { a in
                        AbfahrtZeile(abfahrt: a, jetzt: jetzt, farben: farben, kompakt: familie == .systemSmall)
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
