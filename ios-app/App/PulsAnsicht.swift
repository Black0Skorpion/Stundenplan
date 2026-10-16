import SwiftUI

/// Einstellungen → „Veranstaltungen aus PULS“: eigene Modulwahl statt Planer/ICS
struct PulsAuswahlAnsicht: View {
    @EnvironmentObject var zustand: AppZustand
    @ObservedObject private var puls = PulsModell.shared
    @State private var link = ""
    @State private var meldung: String?
    @State private var laedtLink = false

    var body: some View {
        Form {
            Section {
                Picker("Semester", selection: Binding(get: { puls.auswahl.semester }, set: { puls.setzeSemester($0) })) {
                    let jetzt = Puls.semesterCode()
                    ForEach([jetzt, Puls.naechstesSemester(jetzt)], id: \.self) { Text(Puls.semesterName($0)).tag($0) }
                }
            } footer: {
                Text("Daten aus dem öffentlichen Vorlesungsverzeichnis von PULS (ohne Anmeldung).")
            }

            Section {
                if puls.auswahl.kurse.isEmpty {
                    Text("Noch keine Veranstaltungen gewählt.").foregroundStyle(.secondary)
                }
                ForEach(puls.auswahl.kurse) { k in
                    NavigationLink {
                        PulsKursAnsicht(publishid: k.publishid)
                    } label: {
                        KursZeile(kurs: k)
                    }
                }
                .onDelete { i in i.map { puls.auswahl.kurse[$0].publishid }.forEach(puls.entfernen) }
            } header: {
                Text("Deine Veranstaltungen")
            } footer: {
                if !puls.fehlendeGruppen.isEmpty {
                    Text("Bei \(puls.fehlendeGruppen.count) Veranstaltung(en) fehlt noch die Gruppe (Übung/Tutorium).")
                        .foregroundStyle(Palette.jetzt)
                }
            }

            Section("Hinzufügen") {
                NavigationLink {
                    PulsVerzeichnisAnsicht(pfad: "", titel: "Vorlesungsverzeichnis")
                } label: {
                    Label("Im Vorlesungsverzeichnis suchen", systemImage: "list.bullet.indent")
                }
                HStack {
                    TextField("PULS-Link oder Nummer einfügen", text: $link)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    if laedtLink {
                        ProgressView()
                    } else {
                        Button("Hinzufügen") { Task { await linkHinzufuegen() } }
                            .buttonStyle(.borderless)
                            .disabled(Puls.publishid(ausLink: link) == nil)
                    }
                }
            }

            Section {
                Button {
                    let n = puls.uebernehmen()
                    zustand.neuLaden()
                    meldung = "\(n) Termine aus PULS übernommen."
                } label: {
                    Label(puls.auswahl.aktiv ? "Stundenplan neu erzeugen" : "Als Stundenplan übernehmen", systemImage: "checkmark.circle")
                }
                .disabled(puls.auswahl.kurse.isEmpty)
            } footer: {
                Text("Ersetzt den bisherigen Stundenplan (Import bzw. eingebaute Termine). Eigene Änderungen (fällt aus, Raum) bleiben erhalten, solange der Termin gleich bleibt.")
            }

            if puls.auswahl.aktiv {
                Section {
                    Toggle("Nur im WLAN", isOn: $puls.auswahl.nurWLAN)
                    Toggle("Mitteilung bei Änderungen", isOn: $puls.auswahl.mitteilungen)
                    LabeledContent("Zuletzt abgeglichen", value: puls.stand.letzterAbgleich.map { Zeit.datumKurz($0) + ", " + Zeit.uhrzeit($0) } ?? "–")
                    if let f = puls.stand.fehler {
                        Text(f).font(.footnote).foregroundStyle(Palette.jetzt)
                    }
                    Button {
                        Task {
                            let geaendert = await puls.abgleichen(erzwingen: true)
                            zustand.neuLaden()
                            meldung = geaendert ? "Stundenplan aktualisiert – Änderungen siehe unten." : (puls.stand.fehler ?? "Keine Änderungen.")
                        }
                    } label: {
                        HStack {
                            Text("Jetzt abgleichen")
                            if puls.laedt { Spacer(); ProgressView() }
                        }
                    }
                    .disabled(puls.laedt)
                    Button("Abgleich beenden", role: .destructive) { puls.deaktivieren() }
                } header: {
                    Text("Täglicher Abgleich")
                } footer: {
                    Text("Einmal am Tag lädt die App die Seiten deiner Veranstaltungen neu (zusammen etwa 50 KB) und übernimmt geänderte Räume, Zeiten und Ausfälle – beim Öffnen der App oder im Hintergrund, wenn iOS es zulässt.")
                }
                if !puls.stand.aenderungen.isEmpty {
                    Section("Letzte Änderungen") {
                        ForEach(puls.stand.aenderungen, id: \.self) { Text($0).font(.footnote) }
                    }
                }
            }
        }
        .navigationTitle("Veranstaltungen aus PULS")
        .alert(meldung ?? "", isPresented: Binding(get: { meldung != nil }, set: { if !$0 { meldung = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }

    private func linkHinzufuegen() async {
        guard let id = Puls.publishid(ausLink: link) else { return }
        laedtLink = true
        let fehler = await puls.hinzufuegen(id)
        laedtLink = false
        if let fehler { meldung = fehler } else { link = "" }
    }
}

/// Zeile in „Deine Veranstaltungen“
private struct KursZeile: View {
    let kurs: PulsModell.Kurs
    @ObservedObject private var puls = PulsModell.shared

    var body: some View {
        let info = Kurse.info(kurs.titel)
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 3).fill(info.farbe).frame(width: 5, height: 34)
            VStack(alignment: .leading, spacing: 2) {
                Text(kurs.titel).lineLimit(2)
                if let g = kurs.gruppe, let v = puls.cache[kurs.publishid], let gruppe = v.gruppen.first(where: { $0.nummer == g }) {
                    let text = v.gruppen.count > 1 ? "Gruppe \(g): " + v.eigenes(gruppe).map(\.kurz).joined(separator: ", ") : "eine Gruppe"
                    Text(text).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                } else {
                    Text("Gruppe wählen").font(.caption.weight(.semibold)).foregroundStyle(Palette.jetzt)
                }
            }
        }
    }
}

/// Einzelne Veranstaltung: Gruppe wählen, gemeinsame Termine, Links
struct PulsKursAnsicht: View {
    let publishid: String
    @ObservedObject private var puls = PulsModell.shared
    @Environment(\.dismiss) private var schliessen

    var body: some View {
        let kurs = puls.auswahl.kurse.first(where: { $0.publishid == publishid })
        Form {
            if let v = puls.cache[publishid], let kurs {
                Section {
                    LabeledContent("Art", value: v.art)
                    if let f = v.belegfrist { LabeledContent("Belegfrist", value: f) }
                    Link(destination: v.url) { Label("In PULS öffnen (belegen)", systemImage: "safari") }
                    if let m = v.moodle, let u = URL(string: m) { Link(destination: u) { Label("Moodle-Kurs", systemImage: "graduationcap") } }
                } header: {
                    Text(v.titel)
                }
                if !v.gemeinsam.isEmpty && v.gruppen.count > 1 {
                    Section("Für alle Gruppen") {
                        ForEach(v.gemeinsam, id: \.self) { RegelZeile(regel: $0) }
                    }
                }
                Section {
                    ForEach(v.gruppen) { g in
                        let konflikte = puls.ueberschneidungen(kurs, gruppe: g.nummer)
                        Button {
                            puls.waehle(publishid, gruppe: g.nummer)
                        } label: {
                            HStack(alignment: .top) {
                                Image(systemName: kurs.gruppe == g.nummer ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(kurs.gruppe == g.nummer ? Color.accentColor : .secondary)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(v.gruppen.count > 1 ? "Gruppe \(g.nummer)" : "Termine").fontWeight(.semibold)
                                    ForEach(v.gruppen.count > 1 ? v.eigenes(g) : g.regeln, id: \.self) { RegelZeile(regel: $0) }
                                    if !konflikte.isEmpty {
                                        Label("Überschneidung mit " + konflikte.joined(separator: ", "), systemImage: "exclamationmark.triangle")
                                            .font(.caption)
                                            .foregroundStyle(Palette.jetzt)
                                    }
                                }
                            }
                            .foregroundStyle(.primary)
                        }
                    }
                } header: {
                    Text(v.gruppen.count > 1 ? "Gruppe wählen" : "Termine")
                }
                Section {
                    Button("Veranstaltung entfernen", role: .destructive) {
                        puls.entfernen(publishid)
                        schliessen()
                    }
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle(kurs.map { Kurse.info($0.titel).kurz } ?? "Veranstaltung")
        .navigationBarTitleDisplayMode(.inline)
        .task { if puls.cache[publishid] == nil { _ = await puls.laden(publishid) } }
    }
}

private struct RegelZeile: View {
    let regel: Puls.Regel

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(regel.kurz).font(.subheadline)
            let zeitraum = regel.rhythmus.lowercased().contains("einzel")
                ? "Einzeltermin"
                : "\(regel.rhythmus), \(regel.beginn ?? "") – \(regel.ende ?? "")"
            Text(zeitraum + (regel.ausfaelle.isEmpty ? "" : " · \(regel.ausfaelle.count) Ausfälle"))
                .font(.caption).foregroundStyle(.secondary)
            if !regel.lehrperson.isEmpty {
                Text(regel.lehrperson).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            if let b = regel.bemerkung, !b.isEmpty {
                Text(b).font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}

/// Vorlesungsverzeichnis durchblättern (Fakultät → Institut → Studiengang → Modul → Veranstaltung)
struct PulsVerzeichnisAnsicht: View {
    let pfad: String
    let titel: String
    @ObservedObject private var puls = PulsModell.shared
    @State private var eintraege: [Puls.Eintrag]?
    @State private var fehler = false
    @State private var laedt: String?
    @State private var meldung: String?

    var body: some View {
        List {
            if let eintraege {
                if eintraege.isEmpty { Text("Hier ist nichts eingetragen.").foregroundStyle(.secondary) }
                ForEach(eintraege) { e in
                    switch e.art {
                    case .ordner(let p):
                        NavigationLink(e.name) { PulsVerzeichnisAnsicht(pfad: p, titel: e.name) }
                    case .veranstaltung(let id):
                        Button {
                            Task { await hinzufuegen(id) }
                        } label: {
                            HStack {
                                Text(e.name).foregroundStyle(.primary)
                                Spacer()
                                if laedt == id { ProgressView() }
                                else { Image(systemName: puls.enthaelt(id) ? "checkmark.circle.fill" : "plus.circle") }
                            }
                        }
                        .disabled(puls.enthaelt(id) || laedt != nil)
                    }
                }
            } else if fehler {
                Text("PULS ist gerade nicht erreichbar.").foregroundStyle(.secondary)
                Button("Erneut versuchen") { Task { await laden() } }
            } else {
                ProgressView().frame(maxWidth: .infinity)
            }
        }
        .navigationTitle(titel)
        .navigationBarTitleDisplayMode(.inline)
        .task { if eintraege == nil { await laden() } }
        .alert(meldung ?? "", isPresented: Binding(get: { meldung != nil }, set: { if !$0 { meldung = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }

    private func laden() async {
        fehler = false
        eintraege = await puls.verzeichnis(pfad)
        fehler = eintraege == nil
    }

    private func hinzufuegen(_ id: String) async {
        laedt = id
        meldung = await puls.hinzufuegen(id)
        laedt = nil
    }
}
