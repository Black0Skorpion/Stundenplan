import SwiftUI

/// Auswahl des Campus (oben links in Abfahrten und Mensa) – dieselbe Einstellung wie unter „Einstellungen“
struct CampusMenu: View {
    @EnvironmentObject var zustand: AppZustand

    var body: some View {
        Menu {
            Picker("Campus", selection: $zustand.campusWahl) {
                ForEach(CampusWahl.allCases) { w in
                    if w == .automatisch {
                        Label("Automatisch (nach Stundenplan)", systemImage: "wand.and.stars").tag(w)
                    } else {
                        Text(w.name).tag(w)
                    }
                }
            }
        } label: {
            HStack(spacing: 3) {
                if zustand.campusWahl == .automatisch {
                    Image(systemName: "wand.and.stars").font(.caption)
                }
                Text(zustand.campus.kurz)
                Image(systemName: "chevron.down").font(.caption2.weight(.bold))
            }
        }
        .accessibilityLabel("Campus: \(zustand.campus.name)")
    }
}

struct AbfahrtenReiter: View {
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.colorScheme) private var schema
    @Environment(\.scenePhase) private var phase
    /// Sofort der letzte Stand, danach aktualisiert
    @State private var liste: [Abfahrten.Abfahrt]?
    @State private var geladenFuer: Campus?
    @State private var stand: Date?
    @State private var laedt = false
    @State private var fehler = false
    @State private var nurZuege = false
    @State private var neueLinie = ""
    @State private var linieEingeben = false

    /// Haltestellen in der gewählten Karten-App (Einstellungen → Karten).
    /// Dort lassen sich über die Haltestelle die nächsten Abfahrten ansehen.
    private var mapsZiele: [(name: String, url: URL)] {
        zustand.campus.haltestellen.compactMap { h in Karten.suche(h.suche).map { (name: h.name, url: $0) } }
    }

    var body: some View {
        let farben = Farben.fuer(dunkel: schema == .dark)
        let campus = zustand.campus
        NavigationStack {
            TimelineView(.periodic(from: .now, by: 30)) { kontext in
                let jetzt = kontext.date
                let sichtbar = (liste ?? []).filter { $0.zeit > jetzt.addingTimeInterval(-60) && (!nurZuege || $0.istZug) }
                let priorisiert = sichtbar.filter { zustand.istFavorit($0.linie) }
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
                    if !zustand.favoriten.isEmpty && liste != nil {
                        Section {
                            if priorisiert.isEmpty {
                                Text("Keine Abfahrt von \(zustand.favoriten.joined(separator: ", ")) in den nächsten 90 Minuten")
                                    .foregroundStyle(farben.gedimmt)
                            }
                            ForEach(Array(priorisiert.prefix(3))) { a in
                                zeile(a, jetzt: jetzt, farben: farben)
                            }
                        } header: {
                            Label("Priorisiert", systemImage: "star.fill")
                        }
                    }
                    Section {
                        if liste != nil {
                            if sichtbar.isEmpty {
                                Text("Keine Abfahrten in den nächsten 90 Minuten").foregroundStyle(farben.gedimmt)
                            }
                            ForEach(sichtbar) { a in
                                zeile(a, jetzt: jetzt, farben: farben)
                            }
                        } else if laedt {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Text("Noch keine Daten").foregroundStyle(farben.gedimmt)
                        }
                    } header: {
                        HStack {
                            Text(campus.haltestellen.map(\.name).joined(separator: " · "))
                            Spacer()
                            if laedt { ProgressView().controlSize(.mini) }
                            else if let stand { Text("Stand \(Zeit.uhrzeit(stand))") }
                        }
                    } footer: {
                        Text("Linie gedrückt halten, um sie zu priorisieren. Echtzeitdaten des VBB (v6.vbb.transport.rest, ersatzweise BVG). Zum Aktualisieren nach unten ziehen.")
                    }
                }
            }
            .refreshable { await laden() }
            .navigationTitle("Abfahrten")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    CampusMenu()
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Section("In \(Karten.app.name) öffnen") {
                            ForEach(mapsZiele, id: \.name) { ziel in
                                Link(destination: ziel.url) { Label(ziel.name, systemImage: "mappin.and.ellipse") }
                            }
                        }
                        Section("Priorisierte Linien") {
                            ForEach(zustand.favoriten, id: \.self) { l in
                                Button(role: .destructive) { zustand.favoritUmschalten(l) } label: {
                                    Label("\(l) nicht mehr priorisieren", systemImage: "star.slash")
                                }
                            }
                            Button { neueLinie = ""; linieEingeben = true } label: {
                                Label("Linie hinzufügen …", systemImage: "star")
                            }
                        }
                    } label: {
                        Label("Mehr", systemImage: "ellipsis.circle")
                    }
                }
            }
            .alert("Linie priorisieren", isPresented: $linieEingeben) {
                TextField("z. B. RB20", text: $neueLinie)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                Button("Hinzufügen") {
                    let l = neueLinie.trimmingCharacters(in: .whitespaces)
                    if !l.isEmpty && !zustand.istFavorit(l) { zustand.favoritUmschalten(l) }
                }
                Button("Abbrechen", role: .cancel) {}
            } message: {
                Text("Priorisierte Linien stehen oben in der App und kommen im Widget immer mit rein.")
            }
        }
        .task(id: campus) {
            // Beim Öffnen und bei Campuswechsel: erst den gespeicherten Stand, dann neu laden
            if geladenFuer != campus {
                liste = Abfahrten.ausCache(campus)
                stand = Abfahrten.stand(campus)
                fehler = false
                geladenFuer = campus
            }
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

    private func zeile(_ a: Abfahrten.Abfahrt, jetzt: Date, farben: Farben) -> some View {
        let favorit = zustand.istFavorit(a.linie)
        return AbfahrtZeile(abfahrt: a, jetzt: jetzt, farben: farben, favorit: favorit)
            .contextMenu {
                Button { zustand.favoritUmschalten(a.linie) } label: {
                    Label(favorit ? "\(a.linie) nicht mehr priorisieren" : "\(a.linie) priorisieren",
                          systemImage: favorit ? "star.slash" : "star")
                }
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
        let campus = zustand.campus
        laedt = true
        let ergebnis = await Abfahrten.aktualisieren(campus: campus)
        laedt = false
        guard campus == zustand.campus else { return }   // inzwischen anderer Campus gewählt
        if let neu = ergebnis.liste { liste = neu }      // bei Fehler bleibt der letzte Stand sichtbar
        fehler = !ergebnis.frisch
        stand = Abfahrten.stand(campus)
        geladenFuer = campus
    }
}
