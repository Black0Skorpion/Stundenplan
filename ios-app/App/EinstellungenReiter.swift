import SwiftUI
import UniformTypeIdentifiers

struct EinstellungenReiter: View {
    @EnvironmentObject var zustand: AppZustand
    @State private var link = Speicher.einstellungen().link
    @State private var importieren = false

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
                    Text("Im Planer auf „Plan teilen“ tippen, Link kopieren und hier einfügen. Öffnet sich beim Tippen auf die Kopfzeile der Widgets. Ohne eigenen Link öffnet sich die leere Planer-Seite.")
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
                    Link("Speiseplan: OpenMensa", destination: Mensa.webseite)
                    Link("Planer: planner.joshuan.dev", destination: URL(string: Konfig.standardLink)!)
                }
            }
            .navigationTitle("Einstellungen")
            .fileImporter(isPresented: $importieren, allowedContentTypes: icsTypen) { ergebnis in
                if case .success(let url) = ergebnis { zustand.importiere(url) }
            }
        }
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
