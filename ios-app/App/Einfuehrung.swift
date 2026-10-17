import SwiftUI

/// Kurze Einführung beim ersten Start (überspringbar) mit einem erfundenen Demo-Stundenplan
struct Einfuehrung: View {
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.colorScheme) private var schema
    @State private var seite = 0

    private static let seiten = 3

    var body: some View {
        let farben = Farben.fuer(dunkel: schema == .dark)
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button("Überspringen") { fertig() }
                    .padding()
            }
            TabView(selection: $seite) {
                seite1(farben).tag(0)
                seite2(farben).tag(1)
                seite3(farben).tag(2)
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .indexViewStyle(.page(backgroundDisplayMode: .always))

            Button {
                if seite < Self.seiten - 1 { withAnimation { seite += 1 } } else { fertig(modulwahl: true) }
            } label: {
                Text(seite < Self.seiten - 1 ? "Weiter" : "Fächer aus PULS wählen")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
            .padding()
        }
        .background(farben.bg.ignoresSafeArea())
    }

    private func titel(_ t: String, _ text: String) -> some View {
        VStack(spacing: 8) {
            Text(t).font(.title2.weight(.bold)).multilineTextAlignment(.center)
            Text(text).font(.body).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .padding(.horizontal, 24)
    }

    private func seite1(_ farben: Farben) -> some View {
        VStack(spacing: 18) {
            titel("Dein Stundenplan", "Woche im Blick, nächste Veranstaltung mit Raum und Weg dorthin. So sieht das aus (Beispiel):")
            WochenRaster(woche: Plan.woche(Self.demo, Date()), jetzt: Date())
                .frame(height: 300)
                .padding(.horizontal, 12)
                .allowsHitTesting(false)
            Spacer(minLength: 0)
        }
        .padding(.top, 8)
    }

    private func seite2(_ farben: Farben) -> some View {
        VStack(spacing: 22) {
            titel("Unterwegs", "Alles auch ohne die App zu öffnen:")
            VStack(alignment: .leading, spacing: 16) {
                punkt("square.grid.2x2", "Widgets", "Stundenplan, Mensa und Abfahrten auf dem Home- und Sperrbildschirm.")
                punkt("map", "Lageplan", "Veranstaltung antippen → Raum auf dem Campusplan und Route.")
                punkt("bell.badge", "Erinnerung & Wecker", "Mitteilung vor jeder Veranstaltung, Wecker vor der ersten am Tag.")
                punkt("person.2", "Teilen", "Plan per QR-Code weitergeben, Pläne anderer als Profil ansehen.")
            }
            .padding(.horizontal, 28)
            Spacer(minLength: 0)
        }
        .padding(.top, 8)
    }

    private func seite3(_ farben: Farben) -> some View {
        VStack(spacing: 22) {
            titel("Los geht's", "Such deine Veranstaltungen aus PULS, wähl die Gruppen – die App gleicht Räume und Zeiten dann einmal täglich ab.")
            Image(systemName: "checklist").font(.system(size: 64)).foregroundStyle(farben.akzent)
            Text("Hat jemand den gleichen Plan? Dann lass dir einfach den QR-Code zeigen und scanne ihn mit der Kamera.")
                .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                .padding(.horizontal, 28)
            Spacer(minLength: 0)
        }
        .padding(.top, 8)
    }

    private func punkt(_ bild: String, _ t: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: bild).font(.title2).frame(width: 34)
            VStack(alignment: .leading, spacing: 2) {
                Text(t).font(.headline)
                Text(text).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }

    private func fertig(modulwahl: Bool = false) {
        var e = Speicher.einstellungen()
        e.einfuehrungGesehen = true
        Speicher.speichere(e)
        zustand.einfuehrung = false
        if modulwahl {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { zustand.modulwahl = true }
        }
    }

    /// Erfundene Woche (diese Woche), nur zur Anschauung
    static var demo: [Termin] {
        let montag = Plan.woche([], Date()).montag
        func t(_ tag: Int, _ von: Int, _ bis: Int, _ titel: String, _ typ: String, _ raum: String) -> Termin {
            let d = Zeit.plusTage(montag, tag)
            return Termin(start: d.addingTimeInterval(TimeInterval(von * 60)), ende: d.addingTimeInterval(TimeInterval(bis * 60)),
                          titel: titel, typ: typ, raum: raum)
        }
        return [
            t(0, 8 * 60 + 15, 9 * 60 + 45, "Beispielfach Analysis", "Vorlesung", "2.27.0.01"),
            t(0, 10 * 60 + 15, 11 * 60 + 45, "Beispielfach Programmieren", "Übung", "2.70.0.08"),
            t(1, 12 * 60 + 15, 13 * 60 + 45, "Beispielfach Programmieren", "Vorlesung", "2.25.F0.01"),
            t(2, 8 * 60 + 15, 9 * 60 + 45, "Beispielfach Analysis", "Übung", "2.09.0.12"),
            t(2, 14 * 60 + 15, 15 * 60 + 45, "Beispielfach Statistik", "Vorlesung", "1.08.0.58"),
            t(3, 10 * 60 + 15, 11 * 60 + 45, "Beispielfach Statistik", "Tutorium", "1.09.2.13"),
            t(4, 10 * 60 + 15, 11 * 60 + 45, "Beispielfach Analysis", "Vorlesung", "2.27.0.01"),
        ]
    }
}
