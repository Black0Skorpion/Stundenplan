import Foundation
import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins
import VisionKit

// ───────────────────────── Freigabe (was geteilt wird) ─────────────────────────

/// Kurzform eines Stundenplans zum Teilen: Name + PULS-Auswahl (Fächer, Gruppen, ausgeblendete Termine).
/// Passt in einen QR-Code; die Termine lädt das andere Handy dann selbst aus PULS.
struct Freigabe: Codable, Equatable {
    struct Kurs: Codable, Equatable {
        var p: String
        var t: String
        var g: String?
        var a: [String]?
    }
    var v: Int = 1
    var name: String
    var semester: String?
    var kurse: [Kurs]

    var mitPuls: Bool { !kurse.isEmpty }

    var code: String? {
        guard let json = try? JSONEncoder().encode(self),
              let komprimiert = try? (json as NSData).compressed(using: .zlib) as Data else { return nil }
        return komprimiert.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func aus(code: String) -> Freigabe? {
        var b = code.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while b.count % 4 != 0 { b += "=" }
        guard let d = Data(base64Encoded: b),
              let roh = try? (d as NSData).decompressed(using: .zlib) as Data else { return nil }
        return try? JSONDecoder().decode(Freigabe.self, from: roh)
    }

    var link: URL? {
        guard let code else { return nil }
        return URL(string: "stundenplan://teilen?d=\(code)")
    }

    /// Der eigene Stundenplan
    @MainActor
    static var eigene: Freigabe {
        let a = PulsModell.shared.auswahl
        let name = Speicher.einstellungen().name.trimmingCharacters(in: .whitespaces)
        guard a.aktiv else { return Freigabe(name: name, semester: nil, kurse: []) }
        return Freigabe(name: name, semester: a.semester,
                        kurse: a.kurse.map { Kurs(p: $0.publishid, t: $0.titel, g: $0.gruppe, a: $0.ausgeblendet) })
    }

    var pulsKurse: [PulsModell.Kurs] {
        kurse.map { PulsModell.Kurs(publishid: $0.p, titel: $0.t, gruppe: $0.g, ausgeblendet: $0.a) }
    }
}

// ───────────────────────── Profile (Pläne von anderen) ─────────────────────────

struct Profil: Codable, Identifiable, Equatable {
    var id = UUID()
    var name: String
    /// Rohtermine (ohne akademisches Viertel – das kommt wie beim eigenen Plan beim Anzeigen dazu)
    var termine: [Termin]
    var freigabe: Freigabe?
    var stand: Date = Date()

    var angezeigt: [Termin] { termine.map(Plan.viertelAnpassen).sorted { $0.start < $1.start } }

    static func alle() -> [Profil] { Speicher.lesen([Profil].self, "profile.json") ?? [] }
    static func speichern(_ p: [Profil]) { Speicher.schreiben(p, "profile.json") }
}

enum Teilen {
    /// Termine einer geteilten PULS-Auswahl laden (über den gemeinsamen Zwischenspeicher)
    @MainActor
    static func termine(_ f: Freigabe) async -> [Termin]? {
        let puls = PulsModell.shared
        var alle: [Termin] = []
        var geladen = 0
        for k in f.pulsKurse {
            guard let v = await puls.laden(k.publishid) ?? puls.cache[k.publishid] else { continue }
            geladen += 1
            alle += Puls.termine(v, gruppe: k.gruppe, ohne: k.ausgeblendetSet)
                .map { Termin(start: $0.start, ende: $0.ende, titel: k.titel, typ: $0.art, raum: $0.raum) }
        }
        return geladen == 0 && !f.kurse.isEmpty ? nil : alle.sorted { $0.start < $1.start }
    }

    // ───────────── ICS ─────────────

    private static func utc(_ d: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "yyyyMMdd'T'HHmmss'Z'"
        return f.string(from: d)
    }

    private static func maskiert(_ s: String) -> String {
        s.replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: ";", with: "\\;")
            .replacingOccurrences(of: ",", with: "\\,")
            .replacingOccurrences(of: "\n", with: "\\n")
    }

    /// Zeilen nach 73 Zeichen umbrechen (ICS-Regel)
    private static func gefaltet(_ zeile: String) -> String {
        var rest = Substring(zeile)
        var teile: [String] = []
        var erste = true
        while !rest.isEmpty {
            let n = erste ? 73 : 72
            teile.append((erste ? "" : " ") + rest.prefix(n))
            rest = rest.dropFirst(n)
            erste = false
        }
        return teile.joined(separator: "\r\n")
    }

    static func ics(_ termine: [Termin], name: String, freigabe: Freigabe?) -> String {
        var z = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Stundenplan Uni Potsdam//DE", "CALSCALE:GREGORIAN",
                 "X-WR-CALNAME:" + maskiert(name.isEmpty ? "Stundenplan" : "Stundenplan von \(name)")]
        if let code = freigabe?.code { z.append("X-STUNDENPLAN-FREIGABE:" + code) }
        let jetzt = utc(Date())
        for (i, t) in termine.enumerated() {
            z += ["BEGIN:VEVENT",
                  "UID:\(Int(t.start.timeIntervalSince1970))-\(i)@stundenplan",
                  "DTSTAMP:" + jetzt,
                  "DTSTART:" + utc(t.start),
                  "DTEND:" + utc(t.ende),
                  "SUMMARY:" + maskiert(t.titel),
                  "DESCRIPTION:" + maskiert(t.typ),
                  "LOCATION:" + maskiert(t.raum),
                  "END:VEVENT"]
        }
        z.append("END:VCALENDAR")
        return z.map(gefaltet).joined(separator: "\r\n") + "\r\n"
    }

    /// Eigenen Plan als .ics-Datei (für AirDrop, Nachrichten, Kalender-Apps)
    @MainActor
    static func eigeneDatei() -> URL? {
        let name = Speicher.einstellungen().name.trimmingCharacters(in: .whitespaces)
        let text = ics(Speicher.termineZumTeilen(), name: name, freigabe: Freigabe.eigene)
        let datei = name.isEmpty ? "Stundenplan" : "Stundenplan \(name)"
        let erlaubt = datei.filter { $0.isLetter || $0.isNumber || $0 == " " || $0 == "-" }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(erlaubt).ics")
        do { try text.write(to: url, atomically: true, encoding: .utf8) } catch { return nil }
        return url
    }

    /// Kopfzeilen einer .ics-Datei (Name, Freigabe) lesen
    static func kopf(_ inhalt: String) -> (name: String?, freigabe: Freigabe?) {
        let entfaltet = inhalt.replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\n ", with: "").replacingOccurrences(of: "\n\t", with: "")
        var name: String?, freigabe: Freigabe?
        for zeile in entfaltet.components(separatedBy: "\n") {
            if zeile.hasPrefix("X-WR-CALNAME:") {
                var n = String(zeile.dropFirst(13)).replacingOccurrences(of: "\\,", with: ",")
                if n.hasPrefix("Stundenplan von ") { n = String(n.dropFirst(16)) }
                name = n
            } else if zeile.hasPrefix("X-STUNDENPLAN-FREIGABE:") {
                freigabe = Freigabe.aus(code: String(zeile.dropFirst(23)))
            }
            if zeile == "BEGIN:VEVENT" { break }
        }
        return (name, freigabe)
    }

    // ───────────── QR-Code ─────────────

    static func qrBild(_ text: String) -> UIImage? {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(text.utf8)
        f.correctionLevel = "M"
        guard let bild = f.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)),
              let cg = CIContext().createCGImage(bild, from: bild.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}

/// Etwas wurde empfangen (QR-Code/Link oder .ics-Datei) – Nutzer entscheidet, was damit passiert
struct Empfang: Identifiable {
    let id = UUID()
    var name: String
    var freigabe: Freigabe?
    /// Bei einer Datei schon da, beim QR-Code werden sie erst aus PULS geladen
    var termine: [Termin]?
}

// ───────────────────────── Ansichten ─────────────────────────

/// QR-Code und Datei zum Teilen des eigenen Plans
struct TeilenAnsicht: View {
    @Environment(\.dismiss) private var schliessen
    @State private var name = Speicher.einstellungen().name
    @State private var datei: URL?

    var body: some View {
        let f = Freigabe.eigene
        Form {
            Section {
                TextField("Dein Name", text: $name)
                    .textContentType(.givenName)
                    .onSubmit(nameSpeichern)
            } header: {
                Text("Name")
            } footer: {
                Text("Steht beim Empfänger über deinem Plan. Kann dort jederzeit geändert werden.")
            }

            Section {
                if f.mitPuls, let link = Freigabe(name: name, semester: f.semester, kurse: f.kurse).link,
                   let bild = Teilen.qrBild(link.absoluteString) {
                    Image(uiImage: bild)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: 280)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                    Text("\(f.kurse.count) Fächer mit Gruppen")
                        .font(.footnote).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                } else {
                    Text("Der QR-Code geht, sobald dein Plan aus der PULS-Modulwahl kommt. Als Datei teilen geht immer.")
                        .foregroundStyle(.secondary)
                }
            } header: {
                Text("QR-Code")
            } footer: {
                Text("Mit der Kamera-App oder in der Stundenplan-App unter Plan → Profile → „QR-Code scannen“ einlesen. Die Termine werden dann direkt aus PULS geladen.")
            }

            Section {
                if let datei {
                    ShareLink(item: datei) {
                        Label("Als Datei teilen (.ics)", systemImage: "square.and.arrow.up")
                    }
                } else {
                    Text("Keine Termine zum Teilen.").foregroundStyle(.secondary)
                }
            } footer: {
                Text("Per AirDrop, Nachricht usw. In der Stundenplan-App öffnen: als Profil speichern, als eigenen Plan übernehmen oder (bei PULS) in der Modulwahl anpassen. Andere Kalender-Apps können die Datei auch importieren.")
            }
        }
        .navigationTitle("Plan teilen")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) { Button("Fertig") { nameSpeichern(); schliessen() } }
        }
        .onAppear { datei = Teilen.eigeneDatei() }
        .onChange(of: name) { _, _ in nameSpeichern() }
    }

    private func nameSpeichern() {
        var e = Speicher.einstellungen()
        let n = name.trimmingCharacters(in: .whitespaces)
        guard e.name != n else { return }
        e.name = n
        Speicher.speichere(e)
        datei = Teilen.eigeneDatei()
    }
}

/// Empfangenen Plan übernehmen
struct EmpfangAnsicht: View {
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.dismiss) private var schliessen
    let empfang: Empfang
    @State private var name = ""
    @State private var laedt = false
    @State private var fehler: String?
    @State private var ersetzenFragen: Aktion?

    enum Aktion: Identifiable {
        case modulwahl, uebernehmen
        var id: Int { self == .modulwahl ? 0 : 1 }
    }

    var body: some View {
        Form {
            Section {
                TextField("Name", text: $name)
            } header: {
                Text("Wessen Plan?")
            }

            Section {
                if let f = empfang.freigabe, f.mitPuls {
                    ForEach(f.kurse, id: \.p) { k in
                        HStack {
                            RoundedRectangle(cornerRadius: 2).fill(Kurse.info(k.t).farbe).frame(width: 4, height: 22)
                            Text(k.t).lineLimit(2)
                            Spacer()
                            if let g = k.g { Text("Gr. \(g)").font(.caption).foregroundStyle(.secondary) }
                        }
                    }
                }
                if let t = empfang.termine {
                    LabeledContent("Termine", value: "\(t.count)")
                }
            } header: {
                Text(empfang.freigabe?.mitPuls == true ? "Fächer aus PULS" : "Inhalt")
            }

            Section {
                Button {
                    Task { await alsProfil() }
                } label: {
                    Label("Als Profil hinzufügen", systemImage: "person.crop.circle.badge.plus")
                }
                if empfang.freigabe?.mitPuls == true {
                    Button {
                        ersetzenFragen = .modulwahl
                    } label: {
                        Label("Als meinen Plan in der Modulwahl öffnen", systemImage: "checklist")
                    }
                }
                if empfang.termine != nil {
                    Button {
                        ersetzenFragen = .uebernehmen
                    } label: {
                        Label("Als meinen Plan übernehmen", systemImage: "square.and.arrow.down")
                    }
                }
            } footer: {
                Text("Profil: der Plan steht im Plan-Reiter zum Umschalten bereit, dein eigener bleibt, wie er ist. Modulwahl: gleiche Fächer und Gruppen als Startpunkt – anpassen und dann „Übernehmen“.")
            }
            if laedt {
                Section { HStack { ProgressView(); Text("Lädt Termine aus PULS …") } }
            }
            if let fehler {
                Section { Text(fehler).foregroundStyle(.red) }
            }
        }
        .disabled(laedt)
        .navigationTitle("Geteilter Stundenplan")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { schliessen() } }
        }
        .onAppear { name = empfang.name }
        .confirmationDialog("Dein bisheriger Plan wird ersetzt.", isPresented: Binding(
            get: { ersetzenFragen != nil }, set: { if !$0 { ersetzenFragen = nil } }), titleVisibility: .visible) {
            Button(ersetzenFragen == .modulwahl ? "In der Modulwahl öffnen" : "Übernehmen", role: .destructive) {
                let a = ersetzenFragen
                ersetzenFragen = nil
                if a == .modulwahl { inModulwahl() } else { uebernehmen() }
            }
        } message: {
            Text(ersetzenFragen == .modulwahl
                 ? "Deine Fächerauswahl wird durch diese ersetzt. Der Stundenplan selbst ändert sich erst, wenn du in der Modulwahl „Übernehmen“ tippst."
                 : "Deine Termine werden durch diese ersetzt.")
        }
    }

    private var anzeigename: String {
        let n = name.trimmingCharacters(in: .whitespaces)
        return n.isEmpty ? "Ohne Namen" : n
    }

    private func alsProfil() async {
        var termine = empfang.termine
        if termine == nil, let f = empfang.freigabe {
            laedt = true
            termine = await Teilen.termine(f)
            laedt = false
        }
        guard let termine else {
            fehler = "Die Termine konnten nicht aus PULS geladen werden. Bitte später nochmal versuchen."
            return
        }
        zustand.profilHinzufuegen(Profil(name: anzeigename, termine: termine, freigabe: empfang.freigabe))
        schliessen()
    }

    private func inModulwahl() {
        guard let f = empfang.freigabe else { return }
        let puls = PulsModell.shared
        var a = puls.auswahl
        a.kurse = f.pulsKurse
        if let s = f.semester { a.semester = s }
        a.aktiv = false
        puls.auswahl = a
        schliessen()
        zustand.profilID = nil
        zustand.reiter = .plan
        zustand.modulwahl = true
    }

    private func uebernehmen() {
        guard let t = empfang.termine else { return }
        Speicher.speichereTermine(t)
        PulsModell.shared.deaktivieren()
        zustand.profilID = nil
        zustand.termineGeaendert()
        zustand.meldung = "\(t.count) Termine übernommen."
        schliessen()
    }
}

/// Profile verwalten: umbenennen, aktualisieren, löschen, QR-Code scannen
struct ProfileAnsicht: View {
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.dismiss) private var schliessen
    @State private var name = Speicher.einstellungen().name
    @State private var scannen = false
    @State private var laedt: UUID?

    var body: some View {
        Form {
            Section {
                TextField("Dein Name", text: $name)
                    .onChange(of: name) { _, n in
                        var e = Speicher.einstellungen()
                        e.name = n.trimmingCharacters(in: .whitespaces)
                        Speicher.speichere(e)
                    }
            } header: {
                Text("Ich")
            } footer: {
                Text("Dein Name steht auf deinem geteilten Plan.")
            }

            Section {
                if zustand.profile.isEmpty {
                    Text("Noch keine Profile. Lass dir einen Plan per QR-Code oder Datei schicken.")
                        .foregroundStyle(.secondary)
                }
                ForEach($zustand.profile) { $p in
                    VStack(alignment: .leading, spacing: 4) {
                        TextField("Name", text: $p.name)
                            .font(.body.weight(.medium))
                        HStack {
                            Text("\(p.termine.count) Termine · Stand \(Zeit.datumKurz(p.stand))")
                            if p.freigabe?.mitPuls == true { Text("· PULS") }
                        }
                        .font(.caption).foregroundStyle(.secondary)
                        if p.freigabe?.mitPuls == true {
                            Button {
                                Task { await aktualisieren(p.id) }
                            } label: {
                                if laedt == p.id { ProgressView() } else { Label("Aus PULS aktualisieren", systemImage: "arrow.clockwise") }
                            }
                            .buttonStyle(.borderless)
                            .font(.caption)
                        }
                    }
                }
                .onDelete { i in
                    let weg = i.map { zustand.profile[$0].id }
                    if let id = zustand.profilID, weg.contains(id) { zustand.profilID = nil }
                    zustand.profile.remove(atOffsets: i)
                }
            } header: {
                Text("Profile")
            } footer: {
                Text("Namen antippen zum Ändern, nach links wischen zum Löschen. Profile sind nur zum Anschauen – Widgets, Erinnerungen und Wecker nutzen immer deinen eigenen Plan.")
            }

            Section {
                if DataScannerViewController.isSupported {
                    Button { scannen = true } label: { Label("QR-Code scannen", systemImage: "qrcode.viewfinder") }
                }
            } footer: {
                Text("Geht auch mit der normalen Kamera-App. Eine .ics-Datei einfach mit „Stundenplan“ öffnen.")
            }
        }
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) { Button("Fertig") { schliessen() } }
        }
        .sheet(isPresented: $scannen) {
            QRScanner { text in
                scannen = false
                if let url = URL(string: text), url.scheme == "stundenplan" {
                    schliessen()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { zustand.oeffne(url) }
                } else {
                    zustand.meldung = "Das ist kein Stundenplan-QR-Code."
                }
            }
            .ignoresSafeArea()
        }
    }

    private func aktualisieren(_ id: UUID) async {
        guard let i = zustand.profile.firstIndex(where: { $0.id == id }), let f = zustand.profile[i].freigabe else { return }
        laedt = id
        defer { laedt = nil }
        guard let t = await Teilen.termine(f) else {
            zustand.meldung = "PULS war nicht erreichbar."
            return
        }
        if let j = zustand.profile.firstIndex(where: { $0.id == id }) {
            zustand.profile[j].termine = t
            zustand.profile[j].stand = Date()
        }
    }
}

/// Kamera-Scanner für QR-Codes (VisionKit)
struct QRScanner: UIViewControllerRepresentable {
    let gefunden: (String) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let vc = DataScannerViewController(recognizedDataTypes: [.barcode(symbologies: [.qr])],
                                           qualityLevel: .balanced, isHighlightingEnabled: true)
        vc.delegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: DataScannerViewController, context: Context) {
        if !vc.isScanning, !context.coordinator.fertig { try? vc.startScanning() }
    }

    func makeCoordinator() -> Koordinator { Koordinator(gefunden) }

    @MainActor
    final class Koordinator: NSObject, DataScannerViewControllerDelegate {
        let gefunden: (String) -> Void
        var fertig = false
        init(_ g: @escaping (String) -> Void) { gefunden = g }

        func dataScanner(_ s: DataScannerViewController, didAdd items: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !fertig else { return }
            for item in items {
                if case .barcode(let b) = item, let text = b.payloadStringValue {
                    fertig = true
                    s.stopScanning()
                    gefunden(text)
                    return
                }
            }
        }
    }
}
