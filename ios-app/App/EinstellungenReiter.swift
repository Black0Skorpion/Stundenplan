import SwiftUI
import UniformTypeIdentifiers

struct EinstellungenReiter: View {
    @EnvironmentObject var zustand: AppZustand
    @State private var name = Speicher.einstellungen().name
    @State private var teilen = false
    @State private var wecker = Speicher.einstellungen().wecker
    @State private var weckerVorlauf = Speicher.einstellungen().weckerVorlauf
    @State private var weckerFruehestens = Speicher.einstellungen().weckerFruehestens
    @State private var weckerSchlummern = Speicher.einstellungen().weckerSchlummern
    @State private var naechsterWecker: Wecker.Weckzeit?
    @State private var fokus = Speicher.einstellungen().fokus
    @State private var fokusVorlauf = Speicher.einstellungen().fokusVorlauf
    @State private var erinnerungen = Speicher.einstellungen().erinnerungen
    @State private var vorlauf = Speicher.einstellungen().vorlaufMinuten
    @State private var live = Speicher.einstellungen().liveAktivitaet
    @State private var sperrAusblenden = Speicher.einstellungen().sperrAusblenden
    @State private var karten = Speicher.einstellungen().karten
    @State private var neueLinie = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Quelle", value: PulsModell.shared.auswahl.aktiv ? "PULS (\(PulsModell.shared.auswahl.kurse.count) Veranstaltungen)"
                                   : Speicher.eigeneTermine ? "Datei" : "noch keiner")
                    NavigationLink {
                        PulsAuswahlAnsicht()
                    } label: {
                        Label("Veranstaltungen aus PULS wählen", systemImage: "checklist")
                    }
                } header: {
                    Text("Stundenplan")
                } footer: {
                    Text("Veranstaltungen und Gruppen direkt aus PULS wählen – die App gleicht Räume und Zeiten dann einmal täglich ab.")
                }

                Section {
                    TextField("Dein Name", text: $name)
                        .onChange(of: name) { _, n in
                            var e = Speicher.einstellungen()
                            e.name = n.trimmingCharacters(in: .whitespaces)
                            Speicher.speichere(e)
                        }
                    Button { teilen = true } label: { Label("Meinen Plan teilen (QR-Code, Datei)", systemImage: "qrcode") }
                    NavigationLink {
                        ProfileAnsicht()
                    } label: {
                        LabeledContent {
                            Text("\(zustand.profile.count)")
                        } label: {
                            Label("Profile anderer", systemImage: "person.2")
                        }
                    }
                } header: {
                    Text("Teilen & Profile")
                } footer: {
                    Text("Der Name steht auf deinem geteilten Plan. Pläne anderer kannst du im Plan-Reiter oben links umschalten.")
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
                    Toggle("Vor der ersten Veranstaltung wecken", isOn: $wecker)
                    if wecker {
                        Picker("Vorher", selection: $weckerVorlauf) {
                            ForEach(Wecker.vorlaufOptionen, id: \.self) { Text(Wecker.vorlaufText($0)).tag($0) }
                        }
                        Picker("Frühestens um", selection: $weckerFruehestens) {
                            ForEach(Wecker.fruehestensOptionen, id: \.self) { Text(Wecker.uhrText($0)).tag($0) }
                        }
                        if Wecker.echt {
                            Picker("Schlummern", selection: $weckerSchlummern) {
                                ForEach(Wecker.schlummerOptionen, id: \.self) { Text($0 == 0 ? "aus" : "\($0) Min").tag($0) }
                            }
                        }
                        if let w = naechsterWecker {
                            LabeledContent("Nächster Wecker", value: "\(Zeit.datumKurz(w.wann)) \(Zeit.uhrzeit(w.wann))")
                        } else {
                            LabeledContent("Nächster Wecker", value: "keiner in den nächsten 14 Tagen")
                        }
                    }
                } header: {
                    Text("Wecker")
                } footer: {
                    Text(Wecker.echt
                         ? "Klingelt wie ein normaler Wecker (auch im Lautlos-Modus), mit Schlummern, nur an Tagen mit Veranstaltungen. Am Vorabend zeigt das Stundenplan-Widget, wann er klingelt. Ausgefallene Termine zählen nicht. Die nächsten 14 Tage sind immer geplant, beim Öffnen der App wird nachgezogen."
                         : "Echte Wecker gehen erst ab iOS 26 – hier kommt stattdessen eine Mitteilung mit Ton (nicht im Lautlos-Modus). Nur an Tagen mit Veranstaltungen, ausgefallene Termine zählen nicht.")
                }
                .onChange(of: wecker) { _, an in weckerAendern(an) }
                .onChange(of: weckerVorlauf) { _, _ in weckerSpeichern() }
                .onChange(of: weckerFruehestens) { _, _ in weckerSpeichern() }
                .onChange(of: weckerSchlummern) { _, _ in weckerSpeichern(erzwingen: true) }

                Section {
                    Toggle("Nicht stören in Veranstaltungen", isOn: $fokus)
                    if fokus {
                        Picker("Einschalten", selection: $fokusVorlauf) {
                            ForEach(Fokus.vorlaufOptionen, id: \.self) { Text($0 == 0 ? "zu Beginn" : "\($0) Min vorher").tag($0) }
                        }
                        NavigationLink {
                            FokusAnleitung(vorlauf: fokusVorlauf)
                        } label: {
                            Label("Einrichten (Kurzbefehl & Fokus wählen)", systemImage: "moon")
                        }
                    }
                } header: {
                    Text("Nicht stören")
                } footer: {
                    Text("Schaltet während deiner Veranstaltungen einen Fokus ein und danach wieder aus – welchen (z. B. „Nicht stören“ oder einen eigenen „Uni“-Fokus), wählst du beim Einrichten. Läuft über eine Kurzbefehl-Automation, weil iOS Apps keinen Fokus selbst einschalten lässt. Ausgefallene Termine zählen nicht.")
                }
                .onChange(of: fokus) { _, _ in fokusSpeichern() }
                .onChange(of: fokusVorlauf) { _, _ in fokusSpeichern() }

                Section {
                    Toggle("Live-Aktivität während der Veranstaltung", isOn: $live)
                    if live {
                        Toggle("Sperrbildschirm-Widgets dann ausblenden", isOn: $sperrAusblenden)
                    }
                } header: {
                    Text("Live-Aktivität")
                } footer: {
                    Text("Zeigt ab Beginn der Veranstaltung Raum, Ende und Fortschritt in der Dynamic Island und auf dem Sperrbildschirm (beides gehört bei iOS zusammen). Ab iOS 26 startet sie von selbst – die App merkt die nächsten Veranstaltungen vor, iOS zeigt dabei kurz einen Hinweis; davor nur, wenn die App gerade offen ist. Nach dem Ende steht nur noch „vorbei“ mit ✕ zum Schließen (ohne die App zu öffnen); beim nächsten Öffnen verschwindet sie von selbst. „Ausblenden“ blendet die Stundenplan-Widgets unter der Uhr aus, solange die Live-Aktivität läuft.")
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

                Section("Info") {
                    LabeledContent("Version", value: versionText)
                    Link("Speiseplan: OpenMensa", destination: Mensa.webseite(zustand.campus))
                    Button("Einführung nochmal zeigen") { zustand.einfuehrung = true }
                }

                Section {
                    NavigationLink {
                        DebugAnsicht()
                    } label: {
                        Label("Debug", systemImage: "ladybug")
                    }
                } footer: {
                    Text("Datei-Import, Planer-Link, Widgets und Datenaustausch – braucht man normalerweise nicht.")
                }
            }
            .navigationTitle("Einstellungen")
            .sheet(isPresented: $teilen) { NavigationStack { TeilenAnsicht() } }
            .onAppear { naechsterWecker = Wecker.naechster }
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

    private func weckerAendern(_ an: Bool) {
        guard an else {
            weckerSpeichern()
            return
        }
        Task { @MainActor in
            if await Wecker.erlaubnisAnfragen() {
                weckerSpeichern()
            } else {
                wecker = false
                zustand.meldung = Wecker.echt
                    ? "Wecker sind nicht erlaubt. Bitte in den iOS-Einstellungen → Stundenplan → Wecker einschalten."
                    : "Mitteilungen sind nicht erlaubt. Bitte in den iOS-Einstellungen → Mitteilungen → Stundenplan einschalten."
            }
        }
    }

    private func fokusSpeichern() {
        var e = Speicher.einstellungen()
        e.fokus = fokus
        e.fokusVorlauf = fokusVorlauf
        Speicher.speichere(e)
    }

    private func weckerSpeichern(erzwingen: Bool = false) {
        var e = Speicher.einstellungen()
        e.wecker = wecker
        e.weckerVorlauf = weckerVorlauf
        e.weckerFruehestens = weckerFruehestens
        e.weckerSchlummern = weckerSchlummern
        Speicher.speichere(e)
        naechsterWecker = Wecker.naechster
        Task { await Wecker.planen(erzwingen: erzwingen) }
    }
}

/// Selten gebraucht: Datei-Import, Planer-Link, Widgets
struct DebugAnsicht: View {
    @EnvironmentObject var zustand: AppZustand
    @State private var link = Speicher.einstellungen().link
    @State private var importieren = false
    @State private var weckerZeilen: [String] = []

    private var icsTypen: [UTType] {
        var t: [UTType] = [.calendarEvent]
        if let ics = UTType(filenameExtension: "ics") { t.append(ics) }
        t.append(.data)
        return t
    }

    var body: some View {
        Form {
            Section {
                ForEach(Array(weckerZeilen.enumerated()), id: \.offset) { _, z in
                    Text(z).font(.footnote.monospacedDigit())
                }
                Button("Wecker jetzt neu stellen") {
                    Task {
                        await Wecker.planen(erzwingen: true)
                        weckerZeilen = await Wecker.diagnose()
                    }
                }
            } header: {
                Text("Wecker im System")
            } footer: {
                Text("Zeigt, welche Wecker iOS gerade wirklich gestellt hat. Fehlt hier ein Tag mit Veranstaltung, „Wecker jetzt neu stellen“ antippen.")
            }
            .task { weckerZeilen = await Wecker.diagnose() }

            Section {
                LabeledContent("Termine", value: "\(zustand.termine.count)")
                Button("ICS-Datei importieren …") { importieren = true }
            } header: {
                Text("Stundenplan-Datei")
            } footer: {
                Text("Z. B. aus dem Planer exportiert. Geht auch über „Teilen“ in der Dateien-App → „Stundenplan“.")
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
                Link("Planer öffnen", destination: Speicher.planerLink)
            } header: {
                Text("Planer-Link")
            } footer: {
                Text("Link aus „Plan teilen“ im Planer (planner.joshuan.dev). Öffnet sich auch im Lageplan.")
            }

            Section {
                LabeledContent("Datenaustausch", value: Speicher.geteilt ? "aktiv" : "nicht verfügbar")
                Button("Widgets neu laden") { Speicher.widgetsNeuLaden() }
            } header: {
                Text("Widgets")
            } footer: {
                Text(Speicher.geteilt
                     ? "Widget gedrückt halten → „Widget bearbeiten“, um Farbschema, Variante oder Mensa-Tag auszuwählen."
                     : "Die App-Gruppe fehlt, daher sehen die Widgets keinen Stundenplan. Ein Neuinstallieren über SideStore oder iloader richtet sie normalerweise ein.")
            }
        }
        .navigationTitle("Debug")
        .fileImporter(isPresented: $importieren, allowedContentTypes: icsTypen) { ergebnis in
            if case .success(let url) = ergebnis { zustand.importiere(url) }
        }
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
