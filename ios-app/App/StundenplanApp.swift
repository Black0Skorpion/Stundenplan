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
        PulsHintergrund.registrieren()
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
                .onOpenURL { url in
                    // Von außen (Widget, Mitteilung, Kamera, Datei): immer den eigenen Plan zeigen
                    if url.host != "teilen" { zustand.profilID = nil }
                    zustand.oeffne(url)
                }
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
                        Task { await Wecker.planen() }
                        LiveAktivitaet.aktualisieren(zustand.termine)
                        // Höchstens einmal täglich mit PULS abgleichen (wenn die Auswahl aktiv ist)
                        Task { @MainActor in
                            if await PulsModell.shared.abgleichen() { zustand.neuLaden() }
                        }
                    } else if neu == .background, PulsModell.shared.auswahl.aktiv {
                        PulsHintergrund.planen()
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
    /// Empfangener Plan (QR-Code, Link oder .ics-Datei)
    @Published var empfang: Empfang?
    /// Modulwahl als eigenes Fenster (z. B. nach einem geteilten Plan)
    @Published var modulwahl = false
    @Published var einfuehrung = false
    /// Pläne von anderen
    @Published var profile: [Profil] = Profil.alle() {
        didSet { if profile != oldValue { Profil.speichern(profile) } }
    }
    /// Im Plan-Reiter angezeigtes Profil (nil = eigener Plan)
    @Published var profilID: UUID?

    var profil: Profil? { profilID.flatMap { id in profile.first { $0.id == id } } }

    /// Im Plan-Reiter angezeigte Termine (eigene oder Profil)
    var planTermine: [Termin] { profil?.angezeigt ?? termine }
    var planAlleTermine: [Termin] { profil?.angezeigt ?? alleTermine }

    /// Termin über den Schlüssel finden – erst im angezeigten Plan, dann im eigenen
    func termin(_ schluessel: String) -> Termin? {
        planAlleTermine.first { $0.schluessel == schluessel } ?? alleTermine.first { $0.schluessel == schluessel }
    }

    func profilHinzufuegen(_ p: Profil) {
        profile.append(p)
        profilID = p.id
        reiter = .plan
    }

    init() {
        let e = Speicher.einstellungen()
        if !e.einfuehrungGesehen && !Speicher.eigeneTermine {
            einfuehrung = true
        }
    }
    /// Campus für Abfahrten und Mensa (Einstellungen, Abfahrten- und Mensa-Reiter)
    @Published var campusWahl: CampusWahl = Speicher.einstellungen().campus {
        didSet {
            guard campusWahl != oldValue else { return }
            var e = Speicher.einstellungen()
            e.campus = campusWahl
            Speicher.speichere(e)
        }
    }
    /// Priorisierte Linien, z. B. ["RB20"]
    @Published var favoriten: [String] = Speicher.einstellungen().favoriten {
        didSet {
            guard favoriten != oldValue else { return }
            var e = Speicher.einstellungen()
            e.favoriten = favoriten
            Speicher.speichere(e)
        }
    }

    /// Tatsächlich verwendeter Campus (bei „Automatisch“ aus dem Stundenplan)
    var campus: Campus { campusWahl.fest ?? Campus.automatisch(termine, Date()) }

    func istFavorit(_ linie: String) -> Bool {
        favoriten.contains { $0.caseInsensitiveCompare(linie) == .orderedSame }
    }

    func favoritUmschalten(_ linie: String) {
        if istFavorit(linie) {
            favoriten.removeAll { $0.caseInsensitiveCompare(linie) == .orderedSame }
        } else {
            favoriten.append(linie)
        }
    }

    func neuLaden() {
        termine = Speicher.termine()
        alleTermine = Speicher.termine(mitAusfaellen: true)
    }

    /// Ausfall oder Raum von Hand ändern
    func aendere(_ t: Termin, _ aendern: (inout Speicher.Aenderung) -> Void) {
        Speicher.aendere(t, aendern)
        neuLaden()
        Task { await Erinnerungen.planen() }
        Task { await Wecker.planen() }
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
                var treffer = planAlleTermine.filter { abs($0.start.timeIntervalSince(zeit)) < 1 }
                if treffer.isEmpty { treffer = alleTermine.filter { abs($0.start.timeIntervalSince(zeit)) < 1 } }
                lageplan = LageplanAnfrage(titel: treffer.first.map { Zeit.datumKurz($0.start) } ?? "Termin",
                                           schluessel: treffer.map(\.schluessel))
            } else if let s = zahl("tag") {
                let tag = Date(timeIntervalSince1970: s)
                lageplan = LageplanAnfrage(titel: Zeit.datumKurz(tag),
                                           schluessel: Plan.termineAm(planAlleTermine, tag).map(\.schluessel))
            } else {
                reiter = .lageplan
            }
        case "planer":
            UIApplication.shared.open(Speicher.planerLink)
        case "teilen":
            guard let code = werte.first(where: { $0.name == "d" })?.value, let f = Freigabe.aus(code: code) else {
                meldung = "Dieser Link enthält keinen gültigen Stundenplan."
                return
            }
            lageplan = nil
            empfang = Empfang(name: f.name, freigabe: f, termine: nil)
        case "plan":
            lageplan = nil
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
        let kopf = Teilen.kopf(text)
        let name = kopf.name ?? url.deletingPathExtension().lastPathComponent
        lageplan = nil
        empfang = Empfang(name: name, freigabe: kopf.freigabe, termine: neu)
    }

    /// Termine wurden ersetzt (Import, PULS): alles Abhängige neu planen
    func termineGeaendert() {
        neuLaden()
        Task { await Erinnerungen.planen() }
        Task { await Wecker.planen() }
        LiveAktivitaet.aktualisieren(termine)
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
        .sheet(item: $zustand.empfang) { e in
            NavigationStack { EmpfangAnsicht(empfang: e) }
                .environmentObject(zustand)
        }
        .sheet(isPresented: $zustand.modulwahl) {
            NavigationStack {
                PulsAuswahlAnsicht()
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Schließen") { zustand.modulwahl = false }
                        }
                    }
            }
            .environmentObject(zustand)
        }
        .fullScreenCover(isPresented: $zustand.einfuehrung) {
            Einfuehrung()
                .environmentObject(zustand)
        }
        .alert(zustand.meldung ?? "", isPresented: Binding(
            get: { zustand.meldung != nil },
            set: { if !$0 { zustand.meldung = nil } }
        )) {
            Button("OK", role: .cancel) {}
        }
    }
}
