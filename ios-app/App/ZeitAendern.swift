import SwiftUI

/// Beginn/Ende einer Veranstaltung selbst festlegen – nur für diesen Termin oder für die ganze Reihe
/// (gleiche Veranstaltung, gleiche Art, gleicher Wochentag, gleiche Uhrzeit).
struct ZeitAendernAnsicht: View {
    let termin: Termin
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.dismiss) private var schliessen

    enum Umfang: String, CaseIterable, Identifiable {
        case einzeln, reihe
        var id: String { rawValue }
    }

    @State private var umfang: Umfang = .reihe
    @State private var beginn = Date()
    @State private var ende = Date()

    private var original: (start: Date, ende: Date) {
        (termin.originalStart ?? termin.start, termin.originalEnde ?? termin.ende)
    }

    private var reiheText: String {
        let o = original.start
        return "jeden \(Zeit.wochentageVoll[Zeit.wochentag(o)]) \(Zeit.uhrzeit(o))"
    }

    private var anzahlReihe: Int {
        zustand.alleTermine.filter { $0.reihe == termin.reihe }.count
    }

    /// Im Stundenplan stehen volle Stunden (s.t.), die App macht daraus c.t. – hier wieder zurück
    private var stZeiten: (Date, Date)? {
        let o = original
        guard Zeit.kalender.component(.minute, from: o.start) == 15 else { return nil }
        return (o.start.addingTimeInterval(-15 * 60), o.ende.addingTimeInterval(15 * 60))
    }

    var body: some View {
        Form {
            Section {
                Text(termin.titel).font(.headline)
                Text("\(Kurse.typ(termin.typ).lang) · \(Zeit.datumKurz(termin.start)) · ursprünglich \(Zeit.uhrzeit(original.start))–\(Zeit.uhrzeit(original.ende))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Section {
                Picker("Gilt für", selection: $umfang) {
                    Text("Nur diesen Termin").tag(Umfang.einzeln)
                    Text("Alle (\(anzahlReihe))").tag(Umfang.reihe)
                }
                .pickerStyle(.segmented)
            } footer: {
                Text(umfang == .reihe
                     ? "Alle Termine dieser Veranstaltung \(reiheText) (\(Kurse.typ(termin.typ).lang)). Gilt auch für neue Termine nach dem PULS-Abgleich."
                     : "Nur der Termin am \(Zeit.datumKurz(termin.start)). Einzeln geänderte Termine haben Vorrang vor der Reihe.")
            }
            Section {
                DatePicker("Beginn", selection: $beginn, displayedComponents: .hourAndMinute)
                DatePicker("Ende", selection: $ende, displayedComponents: .hourAndMinute)
                if let st = stZeiten {
                    Button("Volle Stunde (s.t.): \(Zeit.uhrzeit(st.0))–\(Zeit.uhrzeit(st.1))") {
                        beginn = st.0
                        ende = st.1
                    }
                }
                Button("Wie bisher (c.t.): \(Zeit.uhrzeit(original.start))–\(Zeit.uhrzeit(original.ende))") {
                    beginn = original.start
                    ende = original.ende
                }
            } header: {
                Text("Zeit")
            } footer: {
                Text("Wecker, Erinnerungen, Widgets und „Nicht stören“ richten sich nach der neuen Zeit.")
            }
            if termin.zeitGeaendert {
                Section {
                    Button("Eigene Zeit entfernen", role: .destructive) { entfernen() }
                } footer: {
                    Text("Entfernt die eigene Zeit dieses Termins und die der ganzen Reihe.")
                }
            }
        }
        .navigationTitle("Zeit ändern")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { schliessen() } }
            ToolbarItem(placement: .confirmationAction) { Button("Sichern") { sichern() }.disabled(ende <= beginn) }
        }
        .onAppear {
            beginn = termin.start
            ende = termin.ende
            // Einzeln geändert? Dann standardmäßig auch nur diesen Termin bearbeiten
            if Speicher.aenderungen()[termin.schluessel]?.von != nil { umfang = .einzeln }
        }
    }

    private func sichern() {
        let von = Plan.minuten(beginn), bis = Plan.minuten(ende)
        let o = original
        let wieOriginal = von == Plan.minuten(o.start) && bis == Plan.minuten(o.ende)
        switch umfang {
        case .einzeln:
            zustand.aendere(termin) {
                $0.von = wieOriginal ? nil : von
                $0.bis = wieOriginal ? nil : bis
            }
            // Gibt es eine Reihen-Zeit, muss „wie ursprünglich“ einzeln festgehalten werden
            if wieOriginal, Speicher.zeitRegeln()[termin.reihe] != nil {
                zustand.aendere(termin) { $0.von = von; $0.bis = bis }
            }
        case .reihe:
            Speicher.setzeZeitRegel(termin.reihe, wieOriginal ? nil : Speicher.ZeitRegel(von: von, bis: bis))
            // Einzel-Zeit dieses Termins aufheben, damit die neue Reihen-Zeit greift
            zustand.aendere(termin) { $0.von = nil; $0.bis = nil }
        }
        schliessen()
    }

    private func entfernen() {
        Speicher.setzeZeitRegel(termin.reihe, nil)
        zustand.aendere(termin) { $0.von = nil; $0.bis = nil }
        schliessen()
    }
}
