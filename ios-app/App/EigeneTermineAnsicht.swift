import SwiftUI

/// Eigenen Termin anlegen oder bearbeiten (Plus im Plan)
struct EigenerTerminAnsicht: View {
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.dismiss) private var schliessen
    @State var eintrag: EigeneTermine.Eintrag
    let neu: Bool
    @State private var mitEnde = false

    init(eintrag: EigeneTermine.Eintrag? = nil) {
        if let eintrag {
            _eintrag = State(initialValue: eintrag)
            neu = false
        } else {
            // Vorschlag: nächste volle Stunde, 90 Minuten
            let k = Zeit.kalender
            let jetzt = Date()
            let start = k.date(bySettingHour: k.component(.hour, from: jetzt) + 1, minute: 0, second: 0, of: jetzt) ?? jetzt
            _eintrag = State(initialValue: EigeneTermine.Eintrag(titel: "", typ: "Termin", raum: "", start: start,
                                                                 ende: start.addingTimeInterval(90 * 60)))
            neu = true
        }
    }

    var body: some View {
        Form {
            Section {
                TextField("Titel, z. B. Lerngruppe Mathe", text: $eintrag.titel)
                Picker("Art", selection: $eintrag.typ) {
                    ForEach(EigeneTermine.arten, id: \.self) { Text($0).tag($0) }
                }
                TextField("Raum (optional), z. B. 2.70.0.10", text: $eintrag.raum)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            } footer: {
                Text("Mit Raumnummer der Uni (z. B. 2.70.0.10) erscheint der Termin auch im Lageplan mit Route.")
            }
            Section {
                DatePicker("Beginn", selection: $eintrag.start)
                    .onChange(of: eintrag.start) { vorher, nachher in
                        // Ende mitverschieben
                        eintrag.ende = eintrag.ende.addingTimeInterval(nachher.timeIntervalSince(vorher))
                    }
                DatePicker("Ende", selection: $eintrag.ende, in: eintrag.start..., displayedComponents: [.hourAndMinute])
                Picker("Wiederholen", selection: $eintrag.abstandTage) {
                    Text("Nie").tag(0)
                    Text("Jede Woche").tag(7)
                    Text("Alle 2 Wochen").tag(14)
                }
                if eintrag.abstandTage > 0 {
                    Toggle("Endet am", isOn: $mitEnde)
                    if mitEnde {
                        DatePicker("Letzter Tag", selection: Binding(get: { eintrag.bis ?? eintrag.start.addingTimeInterval(90 * 86400) },
                                                                    set: { eintrag.bis = $0 }),
                                   in: eintrag.start..., displayedComponents: [.date])
                    }
                }
            } footer: {
                Text("Eigene Termine zählen wie Veranstaltungen: Widgets, Erinnerungen und Wecker berücksichtigen sie. Beim Teilen des Plans wählst du aus, welche mitkommen.")
            }
            if !neu {
                Section {
                    Button("Termin löschen", role: .destructive) {
                        EigeneTermine.speichern(EigeneTermine.alle().filter { $0.id != eintrag.id })
                        zustand.termineGeaendert()
                        schliessen()
                    }
                }
            }
        }
        .navigationTitle(neu ? "Neuer Termin" : "Termin bearbeiten")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { schliessen() } }
            ToolbarItem(placement: .confirmationAction) {
                Button("Sichern") { sichern() }
                    .disabled(eintrag.titel.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .onAppear { mitEnde = eintrag.bis != nil }
    }

    private func sichern() {
        var e = eintrag
        e.titel = e.titel.trimmingCharacters(in: .whitespaces)
        e.raum = e.raum.trimmingCharacters(in: .whitespaces)
        if !mitEnde || e.abstandTage == 0 { e.bis = nil }
        // Ende am selben Tag wie der Beginn
        let k = Zeit.kalender
        let h = k.component(.hour, from: e.ende), m = k.component(.minute, from: e.ende)
        e.ende = k.date(bySettingHour: h, minute: m, second: 0, of: e.start) ?? e.ende
        if e.ende <= e.start { e.ende = e.start.addingTimeInterval(60 * 60) }
        var alle = EigeneTermine.alle()
        if let i = alle.firstIndex(where: { $0.id == e.id }) { alle[i] = e } else { alle.append(e) }
        EigeneTermine.speichern(alle)
        zustand.termineGeaendert()
        schliessen()
    }
}

/// Liste der eigenen Termine
struct EigeneTermineListe: View {
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.dismiss) private var schliessen
    @State private var liste = EigeneTermine.alle()

    var body: some View {
        List {
            if liste.isEmpty {
                Text("Noch keine eigenen Termine. Mit „+“ im Plan anlegen.").foregroundStyle(.secondary)
            }
            ForEach(liste.sorted { $0.start < $1.start }) { e in
                NavigationLink {
                    EigenerTerminAnsicht(eintrag: e)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(e.titel).font(.body.weight(.medium))
                        Text(beschreibung(e)).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .onDelete { i in
                let sortiert = liste.sorted { $0.start < $1.start }
                let weg = Set(i.map { sortiert[$0].id })
                EigeneTermine.speichern(EigeneTermine.alle().filter { !weg.contains($0.id) })
                liste = EigeneTermine.alle()
                zustand.termineGeaendert()
            }
        }
        .navigationTitle("Eigene Termine")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { schliessen() } } }
        .onAppear { liste = EigeneTermine.alle() }
    }

    private func beschreibung(_ e: EigeneTermine.Eintrag) -> String {
        let wann = Zeit.datumKurz(e.start) + " " + Zeit.uhrzeit(e.start) + "–" + Zeit.uhrzeit(e.ende)
        let wdh = e.abstandTage == 7 ? " · jede Woche" : e.abstandTage == 14 ? " · alle 2 Wochen" : ""
        return e.typ + " · " + wann + wdh + (e.raum.isEmpty ? "" : " · " + e.raum)
    }
}
