import SwiftUI
import UIKit

@main
struct StundenplanApp: App {
    @StateObject private var zustand = AppZustand()

    var body: some Scene {
        WindowGroup {
            Hauptansicht()
                .environmentObject(zustand)
                // Links innerhalb der App (stundenplan://…) direkt selbst verarbeiten
                .environment(\.openURL, OpenURLAction { url in
                    if url.scheme == "stundenplan" {
                        zustand.oeffne(url)
                        return .handled
                    }
                    return .systemAction
                })
                // Aus Widgets heraus geöffnet oder .ics-Datei geteilt
                .onOpenURL { zustand.oeffne($0) }
        }
    }
}

enum Reiter: Hashable {
    case plan, mensa, lageplan, einstellungen
}

struct LageplanAnfrage: Identifiable {
    let id = UUID()
    let titel: String
    let termine: [Termin]
}

final class AppZustand: ObservableObject {
    @Published var reiter: Reiter = .plan
    @Published var termine: [Termin] = Speicher.termine()
    @Published var lageplan: LageplanAnfrage?
    @Published var meldung: String?

    func neuLaden() {
        termine = Speicher.termine()
    }

    func oeffne(_ url: URL) {
        if url.isFileURL {
            importiere(url)
            return
        }
        if url.scheme == "http" || url.scheme == "https" {
            UIApplication.shared.open(url)
            return
        }
        guard url.scheme == "stundenplan" else { return }
        let werte = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func zahl(_ name: String) -> Double? { werte.first(where: { $0.name == name })?.value.flatMap(Double.init) }

        switch url.host {
        case "lageplan":
            if let s = zahl("termin") {
                let zeit = Date(timeIntervalSince1970: s)
                let treffer = termine.filter { abs($0.start.timeIntervalSince(zeit)) < 1 }
                lageplan = LageplanAnfrage(titel: treffer.first.map { Zeit.datumKurz($0.start) } ?? "Termin",
                                           termine: treffer)
            } else if let s = zahl("tag") {
                let tag = Date(timeIntervalSince1970: s)
                lageplan = LageplanAnfrage(titel: Zeit.datumKurz(tag), termine: Plan.termineAm(termine, tag))
            } else {
                reiter = .lageplan
            }
        case "planer":
            UIApplication.shared.open(Speicher.planerLink)
        case "mensa":
            reiter = .mensa
        default:
            break
        }
    }

    func importiere(_ url: URL) {
        let zugriff = url.startAccessingSecurityScopedResource()
        defer { if zugriff { url.stopAccessingSecurityScopedResource() } }
        guard let daten = try? Data(contentsOf: url),
              let text = String(data: daten, encoding: .utf8) ?? String(data: daten, encoding: .isoLatin1) else {
            meldung = "Die Datei konnte nicht gelesen werden."
            return
        }
        let neu = ICS.parsen(text)
        guard !neu.isEmpty else {
            meldung = "In der Datei wurden keine Termine gefunden."
            return
        }
        Speicher.speichereTermine(neu)
        neuLaden()
        reiter = .plan
        meldung = "\(neu.count) Termine importiert." + (Speicher.geteilt ? "" : " Hinweis: Die Widgets können den Import nicht sehen (keine App-Gruppe).")
    }

    func eingebauteVerwenden() {
        Speicher.eigeneLoeschen()
        neuLaden()
        meldung = "Eingebaute Termine werden verwendet."
    }
}

struct Hauptansicht: View {
    @EnvironmentObject var zustand: AppZustand

    var body: some View {
        TabView(selection: $zustand.reiter) {
            PlanReiter()
                .tabItem { Label("Plan", systemImage: "calendar") }
                .tag(Reiter.plan)
            MensaReiter()
                .tabItem { Label("Mensa", systemImage: "fork.knife") }
                .tag(Reiter.mensa)
            LageplanReiter()
                .tabItem { Label("Lageplan", systemImage: "map") }
                .tag(Reiter.lageplan)
            EinstellungenReiter()
                .tabItem { Label("Einstellungen", systemImage: "gearshape") }
                .tag(Reiter.einstellungen)
        }
        .sheet(item: $zustand.lageplan) { anfrage in
            NavigationStack {
                LageplanAnsicht(titel: anfrage.titel, termine: anfrage.termine)
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Fertig") { zustand.lageplan = nil }
                        }
                    }
            }
        }
        .alert(zustand.meldung ?? "", isPresented: Binding(
            get: { zustand.meldung != nil },
            set: { if !$0 { zustand.meldung = nil } }
        )) {
            Button("OK", role: .cancel) {}
        }
    }
}
