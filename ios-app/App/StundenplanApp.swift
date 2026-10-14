import SwiftUI
import UIKit
import UserNotifications

/// Tippen auf eine Erinnerung öffnet den Lageplan des Termins
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    static var oeffnen: ((URL) -> Void)?
    static var wartend: URL?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    // Auch anzeigen, wenn die App gerade offen ist
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound, .list])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        if let s = response.notification.request.content.userInfo["url"] as? String, let url = URL(string: s) {
            DispatchQueue.main.async {
                if let oeffnen = AppDelegate.oeffnen { oeffnen(url) } else { AppDelegate.wartend = url }
            }
        }
        completionHandler()
    }
}

@main
struct StundenplanApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var zustand = AppZustand()
    @Environment(\.scenePhase) private var phase

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
                .onAppear {
                    AppDelegate.oeffnen = { [weak zustand] url in zustand?.oeffne(url) }
                    if let url = AppDelegate.wartend {
                        AppDelegate.wartend = nil
                        zustand.oeffne(url)
                    }
                }
                .onChange(of: phase) { _, neu in
                    if neu == .active {
                        zustand.neuLaden()
                        Task { await Erinnerungen.planen() }
                        LiveAktivitaet.aktualisieren(zustand.termine)
                    }
                }
        }
    }
}

enum Reiter: Hashable {
    case plan, mensa, abfahrten, lageplan, einstellungen
}

struct LageplanAnfrage: Identifiable {
    let id = UUID()
    let titel: String
    /// Schlüssel der Termine (bleiben gleich, auch wenn Raum/Ausfall geändert werden)
    let schluessel: [String]
}

final class AppZustand: ObservableObject {
    @Published var reiter: Reiter = .plan
    /// Ohne ausgefallene Termine (für Karte, „nächster Termin“ usw.)
    @Published var termine: [Termin] = Speicher.termine()
    /// Mit ausgefallenen Terminen (für Wochenraster und Tagesliste)
    @Published var alleTermine: [Termin] = Speicher.termine(mitAusfaellen: true)
    @Published var lageplan: LageplanAnfrage?
    @Published var meldung: String?

    func neuLaden() {
        termine = Speicher.termine()
        alleTermine = Speicher.termine(mitAusfaellen: true)
    }

    /// Ausfall oder Raum von Hand ändern
    func aendere(_ t: Termin, _ aendern: (inout Speicher.Aenderung) -> Void) {
        Speicher.aendere(t, aendern)
        neuLaden()
        Task { await Erinnerungen.planen() }
        LiveAktivitaet.aktualisieren(termine)
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
                let treffer = alleTermine.filter { abs($0.start.timeIntervalSince(zeit)) < 1 }
                lageplan = LageplanAnfrage(titel: treffer.first.map { Zeit.datumKurz($0.start) } ?? "Termin",
                                           schluessel: treffer.map(\.schluessel))
            } else if let s = zahl("tag") {
                let tag = Date(timeIntervalSince1970: s)
                lageplan = LageplanAnfrage(titel: Zeit.datumKurz(tag),
                                           schluessel: Plan.termineAm(alleTermine, tag).map(\.schluessel))
            } else {
                reiter = .lageplan
            }
        case "planer":
            UIApplication.shared.open(Speicher.planerLink)
        case "plan":
            reiter = .plan
        case "mensa":
            reiter = .mensa
        case "abfahrten":
            reiter = .abfahrten
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
        Task { await Erinnerungen.planen() }
        reiter = .plan
        meldung = "\(neu.count) Termine importiert." + (Speicher.geteilt ? "" : " Hinweis: Die Widgets können den Import nicht sehen (keine App-Gruppe).")
    }

    func eingebauteVerwenden() {
        Speicher.eigeneLoeschen()
        neuLaden()
        Task { await Erinnerungen.planen() }
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
            AbfahrtenReiter()
                .tabItem { Label("Abfahrten", systemImage: "tram") }
                .tag(Reiter.abfahrten)
            LageplanReiter()
                .tabItem { Label("Lageplan", systemImage: "map") }
                .tag(Reiter.lageplan)
            EinstellungenReiter()
                .tabItem { Label("Einstellungen", systemImage: "gearshape") }
                .tag(Reiter.einstellungen)
        }
        .sheet(item: $zustand.lageplan) { anfrage in
            NavigationStack {
                LageplanAnsicht(titel: anfrage.titel, schluessel: anfrage.schluessel)
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
