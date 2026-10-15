import SwiftUI

struct AbfahrtenReiter: View {
    @Environment(\.colorScheme) private var schema
    @Environment(\.scenePhase) private var phase
    /// Sofort der letzte Stand, danach aktualisiert
    @State private var liste: [Abfahrten.Abfahrt]? = Abfahrten.ausCache()
    @State private var stand: Date? = Abfahrten.stand
    @State private var laedt = false
    @State private var fehler = false
    @State private var nurZuege = false

    /// Haltestelle in der gewählten Karten-App (Einstellungen → Karten).
    /// Dort lassen sich über die Haltestelle die nächsten Abfahrten ansehen.
    private var mapsZiele: [(name: String, url: URL)] {
        [("Golm Bhf", "Bahnhof Potsdam-Golm"),
         ("Science Park/Uni", "Haltestelle Science Park/Universität Potsdam Golm")]
            .compactMap { ziel in Karten.suche(ziel.1).map { (name: ziel.0, url: $0) } }
    }

    var body: some View {
        let farben = Farben.fuer(dunkel: schema == .dark)
        NavigationStack {
            TimelineView(.periodic(from: .now, by: 30)) { kontext in
                let jetzt = kontext.date
                List {
                    Section {
                        Picker("Anzeige", selection: $nurZuege) {
                            Text("Alle").tag(false)
                            Text("Nur Züge").tag(true)
                        }
                        .pickerStyle(.segmented)
                    }
                    if fehler {
                        Section {
                            Label("Aktualisieren fehlgeschlagen – der VBB-Dienst antwortet gerade nicht.", systemImage: "exclamationmark.triangle")
                                .foregroundStyle(Palette.jetzt)
                            Button("Erneut versuchen") { Task { await laden() } }
                            mapsKnoepfe
                        } footer: {
                            Text("In \(Karten.app.name) die Haltestelle antippen, um die nächsten Abfahrten zu sehen.")
                        }
                    }
                    Section {
                        if let liste {
                            let sichtbar = liste.filter { $0.zeit > jetzt.addingTimeInterval(-60) && (!nurZuege || $0.istZug) }
                            if sichtbar.isEmpty {
                                Text("Keine Abfahrten in den nächsten 90 Minuten").foregroundStyle(farben.gedimmt)
                            }
                            ForEach(sichtbar) { a in
                                AbfahrtZeile(abfahrt: a, jetzt: jetzt, farben: farben)
                            }
                        } else if laedt {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Text("Noch keine Daten").foregroundStyle(farben.gedimmt)
                        }
                    } header: {
                        HStack {
                            Text("Golm Bhf · Science Park/Uni")
                            Spacer()
                            if laedt { ProgressView().controlSize(.mini) }
                            else if let stand { Text("Stand \(Zeit.uhrzeit(stand))") }
                        }
                    } footer: {
                        Text("Echtzeitdaten des VBB (v6.vbb.transport.rest, ersatzweise BVG). Zum Aktualisieren nach unten ziehen.")
                    }
                }
            }
            .refreshable { await laden() }
            .navigationTitle("Abfahrten")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        ForEach(mapsZiele, id: \.name) { ziel in
                            Link(destination: ziel.url) { Label(ziel.name, systemImage: "mappin.and.ellipse") }
                        }
                    } label: {
                        Label("In \(Karten.app.name) öffnen", systemImage: "map")
                    }
                }
            }
        }
        .task {
            await laden()
            // Jede Minute neu laden, solange der Reiter offen ist
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 60_000_000_000)
                if Task.isCancelled { break }
                await laden()
            }
        }
        .onChange(of: phase) { _, neu in
            if neu == .active { Task { await laden() } }
        }
    }

    /// Ein Knopf je Haltestelle: "Golm Bhf in Apple Karten" …
    @ViewBuilder
    private var mapsKnoepfe: some View {
        ForEach(mapsZiele, id: \.name) { ziel in
            Link(destination: ziel.url) {
                Label("\(ziel.name) in \(Karten.app.name)", systemImage: "map")
            }
        }
    }

    private func laden() async {
        guard !laedt else { return }
        laedt = true
        let ergebnis = await Abfahrten.aktualisieren()
        if let neu = ergebnis.liste { liste = neu }   // bei Fehler bleibt der letzte Stand sichtbar
        fehler = !ergebnis.frisch
        stand = Abfahrten.stand
        laedt = false
    }
}
