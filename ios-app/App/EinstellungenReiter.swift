import SwiftUI
import UniformTypeIdentifiers

struct EinstellungenReiter: View {
    @EnvironmentObject var zustand: AppZustand
    @State private var link = Speicher.einstellungen().link
    @State private var importieren = false
    @State private var erinnerungen = Speicher.einstellungen().erinnerungen
    @State private var vorlauf = Speicher.einstellungen().vorlaufMinuten
    @State private var live = Speicher.einstellungen().liveAktivitaet
    @State private var sperrAusblenden = Speicher.einstellungen().sperrAusblenden
    @State private var karten = Speicher.einstellungen().karten
    @State private var neueLinie = ""

    private var icsTypen: [UTType] {
        var t: [UTType] = [.calendarEvent]
        if let ics = UTType(filenameExtension: "ics") { t.append(ics) }
        t.append(.data)
        return t
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Quelle", value: Speicher.eigeneTermine ? "Eigener Import" : "Eingebaut (WiSe 26/27)")
                    LabeledContent("Termine", value: "\(zustand.termine.count)")
                    Button("ICS-Datei importieren …") { importieren = true }
                    if Speicher.eigeneTermine {
                        Button("Eingebaute Termine verwenden", role: .destructive) { zustand.eingebauteVerwenden() }
                    }
                } header: {
                    Text("Stundenplan")
                } footer: {
                    Text("Im Planer den Plan als .ics exportieren und hier auswählen. Alternativ die Datei in der Dateien-App teilen → „Stundenplan“.")
                }

                Section {
                    TextField("https://planner.joshuan.dev/de?s=…", text: $link)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button("Speichern") { linkSpeichern(link) }
                    if !Speicher.einstellungen().link.isEmpty {
                        Button("Standard verwenden") {
                            link = ""
                            linkSpeichern("")
                        }
                    }
                } header: {
                    Text("Planer-Link")
                } footer: {
                    Text("Im Planer auf „Plan teilen“ tippen, Link kopieren und hier einfügen. Öffnet sich in der App über „Planer“ (oben rechts) und im Lageplan. Ohne eigenen Link öffnet sich die leere Planer-Seite.")
                }

                Section {
                    Toggle("Vor Veranstaltungen erinnern", isOn: $erinnerungen)
                    if erinnerungen {
                        Picker("Vorlauf", selection: $vorlauf) {
                            ForEach(Erinnerungen.vorlaufOptionen, id: \.self) { Text("\($0) Minuten").tag($0) }
                        }
                    }
                } header: {
                    Text("Erinnerungen")
                } footer: {
                    Text("Mitteilung mit Raum vor jeder Veranstaltung, z. B. „GdP in 10 Min“. Tippen öffnet den Lageplan. Läuft nur auf dem iPhone, ohne Server.")
                }

                Section {
                    Toggle("Countdown in der Dynamic Island", isOn: $live)
                    if live {
                        Toggle("Sperrbildschirm-Widgets dann ausblenden", isOn: $sperrAusblenden)
                    }
                } header: {
                    Text("Live-Aktivität")
                } footer: {
                    Text("Zeigt ab einer Stunde vorher den Countdown bis Beginn und den Raum in der Dynamic Island und auf dem Sperrbildschirm; während der Vorlesung Ende und Fortschritt. Ohne Server kann iOS sie nur starten, wenn die App geöffnet wird – am einfachsten über die Erinnerung. „Ausblenden“ blendet die Stundenplan-Widgets unter der Uhr aus, solange die Live-Aktivität die Veranstaltung zeigt, damit nichts doppelt steht.")
                }
                .onChange(of: sperrAusblenden) { _, an in
                    var e = Speicher.einstellungen()
                    e.sperrAusblenden = an
                    Speicher.speichere(e)
                }
                .onChange(of: live) { _, an in
                    var e = Speicher.einstellungen()
                    e.liveAktivitaet = an
                    Speicher.speichere(e)
                    LiveAktivitaet.aktualisieren(zustand.termine)
                }
                .onChange(of: erinnerungen) { _, an in erinnerungenAendern(an) }
                .onChange(of: vorlauf) { _, _ in speichereErinnerungen() }

                Section {
                    Picker("Campus", selection: $zustand.campusWahl) {
                        ForEach(CampusWahl.allCases) { Text($0.name).tag($0) }
                    }
                    if zustand.campusWahl == .automatisch {
                        LabeledContent("Gerade", value: zustand.campus.name)
                    }
                } header: {
                    Text("Campus")
                } footer: {
                    Text("Gilt für Abfahrten und Mensa (auch in den Widgets, wenn dort „Wie in der App“ eingestellt ist). „Automatisch“ nimmt den Campus der laufenden bzw. nächsten Veranstaltung. Der Lageplan zeigt immer den Campus des jeweiligen Raums.")
                }

                Section {
                    ForEach(zustand.favoriten, id: \.self) { l in
                        Label(l, systemImage: "star.fill")
                    }
                    .onDelete { zustand.favoriten.remove(atOffsets: $0) }
                    HStack {
                        TextField("Linie, z. B. RB20", text: $neueLinie)
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                            .onSubmit(linieHinzufuegen)
                        Button("Hinzufügen", action: linieHinzufuegen)
                            .disabled(neueLinie.trimmingCharacters(in: .whitespaces).isEmpty)
                            .buttonStyle(.borderless)
                    }
                } header: {
                    Text("Priorisierte Linien")
                } footer: {
                    Text("Diese Linien stehen in den Abfahrten oben und kommen im Widget immer mit rein. Zum Entfernen nach links wischen. Geht auch direkt in den Abfahrten: Linie gedrückt halten.")
                }

                Section {
                    Picker("Karten-App", selection: $karten) {
                        ForEach(KartenApp.allCases) { Text($0.name).tag($0) }
                    }
                } header: {
                    Text("Karten")
                } footer: {
                    Text("Wird für die Route im Lageplan und die Haltestellen im Abfahrten-Reiter verwendet. Google Maps öffnet die App, falls installiert, sonst die Webseite.")
                }
                .onChange(of: karten) { _, neu in
                    var e = Speicher.einstellungen()
                    e.karten = neu
                    Speicher.speichere(e)
                }

                Section {
                    LabeledContent("Datenaustausch", value: Speicher.geteilt ? "aktiv" : "nicht verfügbar")
                    Button("Widgets neu laden") { Speicher.widgetsNeuLaden() }
                } header: {
                    Text("Widgets")
                } footer: {
                    Text(Speicher.geteilt
                         ? "Widget gedrückt halten → „Widget bearbeiten“, um Farbschema, Variante oder Mensa-Tag auszuwählen."
                         : "Die App-Gruppe fehlt, daher zeigen die Widgets die eingebauten Termine und den Standard-Link. Ein Neuinstallieren über SideStore oder iloader richtet sie normalerweise ein.")
                }

                Section("Info") {
                    LabeledContent("Version", value: versionText)
                    Link("Speiseplan: OpenMensa", destination: Mensa.webseite(zustand.campus))
                    Link("Planer: planner.joshuan.dev", destination: URL(string: Konfig.standardLink)!)
                }
            }
            .navigationTitle("Einstellungen")
            .fileImporter(isPresented: $importieren, allowedContentTypes: icsTypen) { ergebnis in
                if case .success(let url) = ergebnis { zustand.importiere(url) }
            }
        }
    }

    private func linieHinzufuegen() {
        let l = neueLinie.trimmingCharacters(in: .whitespaces)
        guard !l.isEmpty else { return }
        if !zustand.istFavorit(l) { zustand.favoriten.append(l) }
        neueLinie = ""
    }

    private func erinnerungenAendern(_ an: Bool) {
        guard an else {
            speichereErinnerungen()
            return
        }
        Task { @MainActor in
            if await Erinnerungen.erlaubnisAnfragen() {
                speichereErinnerungen()
            } else {
                erinnerungen = false
                zustand.meldung = "Mitteilungen sind nicht erlaubt. Bitte in den iOS-Einstellungen → Mitteilungen → Stundenplan einschalten."
            }
        }
    }

    private func speichereErinnerungen() {
        var e = Speicher.einstellungen()
        e.erinnerungen = erinnerungen
        e.vorlaufMinuten = vorlauf
        Speicher.speichere(e)
        Task { await Erinnerungen.planen() }
    }

    private var versionText: String {
        let info = Bundle.main.infoDictionary
        let v = info?["CFBundleShortVersionString"] as? String ?? "–"
        let b = info?["CFBundleVersion"] as? String ?? "–"
        return "\(v) (Build \(b))"
    }

    private func linkSpeichern(_ wert: String) {
        let w = wert.trimmingCharacters(in: .whitespacesAndNewlines)
        if !w.isEmpty, !(w.lowercased().hasPrefix("http://") || w.lowercased().hasPrefix("https://")) {
            zustand.meldung = "Der Link muss mit https:// beginnen."
            return
        }
        var e = Speicher.einstellungen()
        e.link = w
        Speicher.speichere(e)
        zustand.meldung = w.isEmpty ? "Standard-Link wird verwendet." : "Planer-Link gespeichert."
    }
}
