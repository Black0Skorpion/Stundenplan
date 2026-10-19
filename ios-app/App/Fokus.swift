import AppIntents
import SwiftUI

/// „Nicht stören in Veranstaltungen“. iOS erlaubt Apps nicht, einen Fokus selbst einzuschalten –
/// das geht nur über eine Kurzbefehl-Automation mit der Aktion „Fokus einstellen … bis“.
/// Die App liefert dafür das Ende der laufenden (bzw. gleich beginnenden) Veranstaltung.
enum Fokus {
    static let vorlaufOptionen = [0, 5, 10, 15]
    /// Direkt aufeinanderfolgende Veranstaltungen (Lücke bis dahin) werden zusammengefasst
    static let luecke: TimeInterval = 20 * 60
    /// Automationen laufen nicht sekundengenau
    static let spielraum: TimeInterval = 5 * 60

    /// Bis wann der Fokus an sein soll – nil, wenn gerade keine Veranstaltung läuft oder gleich beginnt
    static func ende(_ termine: [Termin], _ e: Speicher.Einstellungen, jetzt: Date = Date()) -> Date? {
        guard e.fokus else { return nil }
        let liste = termine.filter { !$0.ausfall && $0.ende > jetzt }.sorted { $0.start < $1.start }
        guard let erster = liste.first,
              erster.start <= jetzt.addingTimeInterval(TimeInterval(e.fokusVorlauf * 60) + spielraum) else { return nil }
        var ende = erster.ende
        for t in liste.dropFirst() {
            guard t.start <= ende.addingTimeInterval(luecke) else { break }
            ende = max(ende, t.ende)
        }
        return ende
    }

    /// Uhrzeiten (Minuten nach Mitternacht), zu denen die Automation laufen muss:
    /// Beginn jedes Veranstaltungsblocks minus Vorlauf, aus den nächsten 4 Wochen
    static func uhrzeiten(_ termine: [Termin], vorlauf: Int, jetzt: Date = Date()) -> [Int] {
        let bis = jetzt.addingTimeInterval(28 * 86400)
        let liste = termine.filter { !$0.ausfall && $0.start > jetzt && $0.start < bis }.sorted { $0.start < $1.start }
        var minuten = Set<Int>()
        var blockEnde: Date? = nil
        for t in liste {
            if let b = blockEnde, Zeit.gleicherTag(b, t.start), t.start <= b.addingTimeInterval(luecke) {
                blockEnde = max(b, t.ende)
                continue
            }
            blockEnde = t.ende
            let k = Zeit.kalender.dateComponents([.hour, .minute], from: t.start.addingTimeInterval(-TimeInterval(vorlauf * 60)))
            minuten.insert((k.hour ?? 0) * 60 + (k.minute ?? 0))
        }
        return minuten.sorted()
    }

    static func text(_ m: Int) -> String { String(format: "%d:%02d", m / 60, m % 60) }
}

/// Kurzbefehl-Aktion für die Automation
struct VeranstaltungsEnde: AppIntent {
    static var title: LocalizedStringResource = "Ende der Veranstaltung (für Fokus)"
    static var description = IntentDescription(
        "Gibt zurück, bis wann gerade eine Veranstaltung läuft oder gleich beginnt (direkt aufeinanderfolgende zusammengefasst). Läuft keine, gibt es kein Ergebnis. Gedacht für „Fokus einstellen … bis“ in einer Automation.")

    func perform() async throws -> some IntentResult & ReturnsValue<Date?> {
        .result(value: Fokus.ende(Speicher.termine(), Speicher.einstellungen()))
    }
}

/// Einstellungen-Abschnitt mit Anleitung
struct FokusAnleitung: View {
    let vorlauf: Int
    @State private var zeiten: [Int] = []

    var body: some View {
        Form {
            Section {
                Text("iOS lässt Apps keinen Fokus selbst einschalten – das darf nur die Kurzbefehle-App. Deshalb richtest du einmal eine Automation ein; danach läuft alles von allein.")
                    .font(.callout)
            }
            Section("1. Kurzbefehl anlegen") {
                schritt("Kurzbefehle-App → „Kurzbefehle“ → „+“, Name z. B. „Vorlesung Fokus“.")
                schritt("Aktion „Ende der Veranstaltung (für Fokus)“ von Stundenplan hinzufügen.")
                schritt("Aktion „Wenn“ hinzufügen: „Ende der Veranstaltung“ → „hat einen Wert“.")
                schritt("Innerhalb von „Wenn“: Aktion „Fokus einstellen“ → hier wählst du den Fokus (z. B. „Nicht stören“ oder einen eigenen „Uni“-Fokus) → „Einschalten“ → „Bis Zeit“ → als Zeit „Ende der Veranstaltung“ einsetzen.")
            }
            Section {
                schritt("Kurzbefehle-App → „Automation“ → „+“ → „Tageszeit“.")
                schritt("Uhrzeit der Liste unten eintragen, „Wöchentlich“ Mo–Fr, „Sofort ausführen“ wählen, „Bei Ausführung mitteilen“ aus.")
                schritt("Als Aktion den Kurzbefehl „Vorlesung Fokus“ ausführen.")
                schritt("Für jede Uhrzeit wiederholen (Automation lange drücken → „Duplizieren“ geht am schnellsten).")
            } header: {
                Text("2. Automationen anlegen")
            } footer: {
                Text("Läuft zu einer Uhrzeit keine Veranstaltung (Ausfall, freier Tag), passiert einfach nichts. Nach der Veranstaltung schaltet iOS den Fokus selbst wieder aus.")
            }
            Section {
                if zeiten.isEmpty {
                    Text("In den nächsten 4 Wochen keine Veranstaltungen.").foregroundStyle(.secondary)
                } else {
                    ForEach(zeiten, id: \.self) { m in
                        Label(Fokus.text(m), systemImage: "clock")
                    }
                }
            } header: {
                Text("Uhrzeiten aus deinem Plan")
            } footer: {
                Text("Beginn jeder Veranstaltung (bzw. jedes Blocks direkt aufeinanderfolgender) minus Vorlauf, aus den nächsten 4 Wochen. Ändert sich dein Plan, schau hier wieder rein.")
            }
            Section {
                Button {
                    if let u = URL(string: "shortcuts://") { UIApplication.shared.open(u) }
                } label: {
                    Label("Kurzbefehle-App öffnen", systemImage: "arrow.up.forward.app")
                }
            }
        }
        .navigationTitle("Nicht stören einrichten")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { zeiten = Fokus.uhrzeiten(Speicher.termine(), vorlauf: vorlauf) }
    }

    private func schritt(_ s: String) -> some View {
        Text(s).font(.callout)
    }
}
