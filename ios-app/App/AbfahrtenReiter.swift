import SwiftUI

struct AbfahrtenReiter: View {
    @Environment(\.colorScheme) private var schema
    @State private var liste: [Abfahrten.Abfahrt]?
    @State private var laedt = true
    @State private var nurZuege = false

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
                    Section {
                        if laedt && liste == nil {
                            ProgressView().frame(maxWidth: .infinity)
                        } else if let liste {
                            let sichtbar = liste.filter { $0.zeit > jetzt.addingTimeInterval(-60) && (!nurZuege || $0.istZug) }
                            if sichtbar.isEmpty {
                                Text("Keine Abfahrten in den nächsten 90 Minuten").foregroundStyle(farben.gedimmt)
                            }
                            ForEach(sichtbar) { a in
                                AbfahrtZeile(abfahrt: a, jetzt: jetzt, farben: farben)
                            }
                        } else {
                            Text("Abfahrten konnten nicht geladen werden").foregroundStyle(farben.gedimmt)
                        }
                    } header: {
                        Text("Golm Bhf · Science Park/Uni")
                    } footer: {
                        Text("Echtzeitdaten des VBB über v6.vbb.transport.rest. Zum Aktualisieren nach unten ziehen.")
                    }
                }
            }
            .refreshable { await laden() }
            .navigationTitle("Abfahrten")
        }
        .task {
            await laden()
            // Jede Minute neu laden, solange der Reiter offen ist
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 60_000_000_000)
                await laden()
            }
        }
    }

    private func laden() async {
        laedt = true
        if let neu = await Abfahrten.laden() { liste = neu }
        laedt = false
    }
}
