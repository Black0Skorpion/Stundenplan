import ActivityKit
import AppIntents

/// ✕ auf der Live-Aktivität: schließt sie, ohne die App zu öffnen
struct LiveAktivitaetSchliessen: LiveActivityIntent {
    static var title: LocalizedStringResource = "Live-Aktivität schließen"
    static var isDiscoverable = false

    @Parameter(title: "Termin")
    var schluessel: String

    init() {}

    init(schluessel: String) {
        self.schluessel = schluessel
    }

    func perform() async throws -> some IntentResult {
        for a in Activity<VorlesungAttribute>.activities where a.attributes.schluessel == schluessel {
            await a.end(nil, dismissalPolicy: .immediate)
        }
        return .result()
    }
}
