import SwiftUI

/// Einstellungen → „Veranstaltungen aus PULS“: oben immer der Wochenplan als Vorschau,
/// darunter Suche und die gewählten Fächer. Ein Fach antippen → alle möglichen Gruppen erscheinen
/// grau im Plan und lassen sich dort (oder unten) direkt auswählen.
struct PulsAuswahlAnsicht: View {
    @EnvironmentObject var zustand: AppZustand
    @ObservedObject private var puls = PulsModell.shared
    @ObservedObject private var katalog = PulsKatalog.shared
    @State private var fokus: String?
    @State private var suche = ""
    @State private var meldung: String?
    @State private var laedt: String?
    @State private var optionen = false
    @State private var link = ""

    var body: some View {
        GeometryReader { geo in
            VStack(spacing: 0) {
                PlanerRaster(fokus: $fokus)
                    .frame(height: max(260, geo.size.height * 0.47))
                    .padding(.horizontal, 8)
                    .padding(.bottom, 6)
                Divider()
                liste
            }
        }
        .navigationTitle("Modulwahl")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { optionen = true } label: { Image(systemName: "gearshape") }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button(puls.auswahl.aktiv ? "Aktualisieren" : "Übernehmen") {
                    let n = puls.uebernehmen()
                    zustand.neuLaden()
                    meldung = "\(n) Termine als Stundenplan übernommen." +
                        (puls.fehlendeGruppen.isEmpty ? "" : " Bei \(puls.fehlendeGruppen.count) Fach/Fächern fehlt noch die Gruppe – dort wurde Gruppe 1 genommen.")
                }
                .fontWeight(.semibold)
                .disabled(puls.auswahl.kurse.isEmpty)
            }
        }
        .sheet(isPresented: $optionen) { NavigationStack { PulsOptionenAnsicht() } }
        .alert(meldung ?? "", isPresented: Binding(get: { meldung != nil }, set: { if !$0 { meldung = nil } })) {
            Button("OK", role: .cancel) {}
        }
        .task { await katalog.bereitstellen(puls.auswahl.semester) }
    }

    private var liste: some View {
        List {
            Section {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("Fach suchen, z. B. „Maschinenmodelle“ oder „INF-1030“", text: $suche)
                        .autocorrectionDisabled()
                    if !suche.isEmpty {
                        Button { suche = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }
                            .buttonStyle(.borderless)
                    }
                }
                if !suche.isEmpty { suchErgebnisse }
            } footer: {
                if katalog.laedt { Text("Suchkatalog wird geladen …") }
                else if let f = katalog.fehler { Text(f) }
            }

            Section {
                if puls.auswahl.kurse.isEmpty {
                    Text("Noch keine Fächer gewählt – oben suchen oder im Vorlesungsverzeichnis stöbern.")
                        .foregroundStyle(.secondary)
                }
                ForEach(puls.auswahl.kurse) { k in
                    KursZeile(kurs: k, fokus: $fokus)
                }
                .onDelete { i in i.map { puls.auswahl.kurse[$0].publishid }.forEach(puls.entfernen) }
            } header: {
                Text("Deine Fächer")
            } footer: {
                Text("Fach antippen: alle Gruppen erscheinen grau im Plan – Gruppe im Plan oder hier antippen. Zum Entfernen nach links wischen.")
            }

            Section {
                NavigationLink {
                    PulsVerzeichnisAnsicht(pfad: "", titel: "Vorlesungsverzeichnis")
                } label: {
                    Label("Im Vorlesungsverzeichnis stöbern", systemImage: "list.bullet.indent")
                }
                HStack {
                    TextField("PULS-Link einfügen", text: $link)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button("Hinzufügen") { Task { await hinzufuegen(Puls.publishid(ausLink: link) ?? "") ; link = "" } }
                        .buttonStyle(.borderless)
                        .disabled(Puls.publishid(ausLink: link) == nil)
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    @ViewBuilder
    private var suchErgebnisse: some View {
        let treffer = katalog.suchen(suche)
        if katalog.eintraege.isEmpty {
            Text(katalog.laedt ? "Lädt …" : "Kein Suchkatalog vorhanden.").foregroundStyle(.secondary)
            if !katalog.laedt {
                Button("Katalog jetzt laden") { Task { await katalog.bereitstellen(puls.auswahl.semester, erzwingen: true) } }
            }
        } else if treffer.isEmpty {
            Text("Nichts gefunden.").foregroundStyle(.secondary)
        }
        ForEach(treffer) { e in
            Button {
                Task { await hinzufuegen(e.publishid) }
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(e.titel).foregroundStyle(.primary).lineLimit(2)
                        if let k = e.kontext.first {
                            Text(k).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                    }
                    Spacer()
                    if laedt == e.publishid { ProgressView() }
                    else { Image(systemName: puls.enthaelt(e.publishid) ? "checkmark.circle.fill" : "plus.circle") }
                }
            }
            .disabled(puls.enthaelt(e.publishid) || laedt != nil)
        }
    }

    private func hinzufuegen(_ id: String) async {
        guard !id.isEmpty else { return }
        laedt = id
        let fehler = await puls.hinzufuegen(id)
        laedt = nil
        if let fehler { meldung = fehler; return }
        // Neues Fach gleich in den Fokus: seine Gruppen erscheinen im Plan
        fokus = id
        suche = ""
    }
}

/// Zahnrad: Semester, Abgleich, letzte Änderungen
struct PulsOptionenAnsicht: View {
    @EnvironmentObject var zustand: AppZustand
    @ObservedObject private var puls = PulsModell.shared
    @ObservedObject private var katalog = PulsKatalog.shared
    @Environment(\.dismiss) private var schliessen
    @State private var meldung: String?

    var body: some View {
        Form {
            Section {
                Picker("Semester", selection: Binding(get: { puls.auswahl.semester }, set: { neu in
                    puls.setzeSemester(neu)
                    Task { await katalog.bereitstellen(neu) }
                })) {
                    let jetzt = Puls.semesterCode()
                    ForEach([jetzt, Puls.naechstesSemester(jetzt)], id: \.self) { Text(Puls.semesterName($0)).tag($0) }
                }
                LabeledContent("Suchkatalog", value: katalog.eintraege.isEmpty ? "–" : "\(katalog.eintraege.count) Veranstaltungen")
                Button("Suchkatalog neu laden") { Task { await katalog.bereitstellen(puls.auswahl.semester, erzwingen: true) } }
                    .disabled(katalog.laedt)
            } footer: {
                Text("Daten aus dem öffentlichen Vorlesungsverzeichnis von PULS (ohne Anmeldung). Der Suchkatalog wird wöchentlich auf GitHub erneuert und nur im WLAN automatisch geladen.")
            }
            if puls.auswahl.aktiv {
                Section {
                    Toggle("Nur im WLAN", isOn: $puls.auswahl.nurWLAN)
                    Toggle("Mitteilung bei Änderungen", isOn: $puls.auswahl.mitteilungen)
                    LabeledContent("Zuletzt abgeglichen", value: puls.stand.letzterAbgleich.map { Zeit.datumKurz($0) + ", " + Zeit.uhrzeit($0) } ?? "–")
                    if let f = puls.stand.fehler {
                        Text(f).font(.footnote).foregroundStyle(Palette.jetzt)
                    }
                    Button {
                        Task {
                            let geaendert = await puls.abgleichen(erzwingen: true)
                            zustand.neuLaden()
                            meldung = geaendert ? "Stundenplan aktualisiert – Änderungen siehe unten." : (puls.stand.fehler ?? "Keine Änderungen.")
                        }
                    } label: {
                        HStack {
                            Text("Jetzt abgleichen")
                            if puls.laedt { Spacer(); ProgressView() }
                        }
                    }
                    .disabled(puls.laedt)
                    Button("Abgleich beenden", role: .destructive) { puls.deaktivieren() }
                } header: {
                    Text("Täglicher Abgleich")
                } footer: {
                    Text("Einmal am Tag lädt die App die Seiten deiner Fächer neu (zusammen etwa 50 KB) und übernimmt geänderte Räume, Zeiten und Ausfälle – beim Öffnen der App oder im Hintergrund, wenn iOS es zulässt.")
                }
                if !puls.stand.aenderungen.isEmpty {
                    Section("Letzte Änderungen") {
                        ForEach(puls.stand.aenderungen, id: \.self) { Text($0).font(.footnote) }
                    }
                }
            }
        }
        .navigationTitle("Optionen")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { schliessen() } } }
        .alert(meldung ?? "", isPresented: Binding(get: { meldung != nil }, set: { if !$0 { meldung = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }
}

// ───────────────────────── Wochenplan-Vorschau ─────────────────────────

/// Ein Kästchen im Vorschau-Plan
struct PlanerBlock: Identifiable, Hashable {
    enum Zustand: Hashable { case fest, option, konflikt }
    let id: String
    let publishid: String
    let gruppe: String?
    let tag: Int          // 0 = Mo … 4 = Fr
    let von: Int          // Minuten ab Mitternacht
    let bis: Int
    let kurz: String
    let art: String
    let raum: String
    let farbe: Color
    var zustand: Zustand
    var spalte = 0
    var spalten = 1
}

/// Wochenraster Mo–Fr mit allen gewählten Terminen (farbig) und – fürs Fach im Fokus – allen
/// möglichen Gruppen (grau, antippbar). Termine sind die wöchentlichen Regeln, ohne Einzeltermine.
struct PlanerRaster: View {
    @Binding var fokus: String?
    @ObservedObject private var puls = PulsModell.shared
    @Environment(\.colorScheme) private var schema

    private static let tage = ["Mo", "Di", "Mi", "Do", "Fr"]
    private static let tagNummer = ["Mo": 0, "Di": 1, "Mi": 2, "Do": 3, "Fr": 4]

    private static func minuten(_ s: String) -> Int {
        let t = s.split(separator: ":").compactMap { Int($0) }
        return t.count == 2 ? t[0] * 60 + t[1] : 0
    }

    private var bloecke: [PlanerBlock] {
        var liste: [PlanerBlock] = []
        for k in puls.auswahl.kurse {
            guard let v = puls.cache[k.publishid] else { continue }
            let info = Kurse.info(k.titel)
            func hinzu(_ regeln: [Puls.Regel], gruppe: String?, zustand: PlanerBlock.Zustand) {
                for (i, r) in regeln.enumerated() where !r.rhythmus.lowercased().contains("einzel") {
                    guard let tag = Self.tagNummer[String(r.tag.prefix(2))] else { continue }
                    liste.append(PlanerBlock(id: "\(k.publishid)-\(gruppe ?? "g")-\(i)-\(r.hashValue)", publishid: k.publishid, gruppe: gruppe,
                                             tag: tag, von: Self.minuten(r.von), bis: Self.minuten(r.bis),
                                             kurz: info.kurz, art: Kurse.typ(r.art).kurz, raum: r.raum,
                                             farbe: info.farbe, zustand: zustand))
                }
            }
            if v.gruppen.count <= 1 {
                hinzu(v.gruppen.first?.regeln ?? [], gruppe: v.gruppen.first?.nummer, zustand: .fest)
                continue
            }
            hinzu(v.gemeinsam, gruppe: nil, zustand: .fest)
            if let g = k.gruppe, let gruppe = v.gruppen.first(where: { $0.nummer == g }) {
                hinzu(v.eigenes(gruppe), gruppe: g, zustand: .fest)
            }
            // Fach im Fokus (oder ohne gewählte Gruppe): alle anderen Gruppen als graue Möglichkeiten
            if fokus == k.publishid || (k.gruppe == nil && fokus == nil) {
                for g in v.gruppen where g.nummer != k.gruppe {
                    hinzu(v.eigenes(g), gruppe: g.nummer, zustand: .option)
                }
            }
        }
        // Überschneidungen zwischen festen Terminen verschiedener Fächer markieren
        for i in liste.indices where liste[i].zustand == .fest {
            for j in liste.indices where j != i && liste[j].zustand != .option && liste[j].publishid != liste[i].publishid {
                if liste[i].tag == liste[j].tag && liste[i].von < liste[j].bis && liste[j].von < liste[i].bis {
                    liste[i].zustand = .konflikt
                }
            }
        }
        // Nebeneinander legen, was sich am selben Tag überschneidet
        for tag in 0..<5 {
            let idx = liste.indices.filter { liste[$0].tag == tag }.sorted { (liste[$0].von, liste[$0].bis) < (liste[$1].von, liste[$1].bis) }
            var gruppe: [Int] = []
            var ende = 0
            func abschliessen() {
                var spaltenEnde: [Int] = []
                for i in gruppe {
                    if let s = spaltenEnde.firstIndex(where: { $0 <= liste[i].von }) {
                        liste[i].spalte = s; spaltenEnde[s] = liste[i].bis
                    } else {
                        liste[i].spalte = spaltenEnde.count; spaltenEnde.append(liste[i].bis)
                    }
                }
                for i in gruppe { liste[i].spalten = max(1, spaltenEnde.count) }
                gruppe = []
            }
            for i in idx {
                if !gruppe.isEmpty && liste[i].von >= ende { abschliessen() }
                gruppe.append(i)
                ende = max(gruppe.count == 1 ? liste[i].bis : ende, liste[i].bis)
            }
            abschliessen()
        }
        return liste
    }

    var body: some View {
        let liste = bloecke
        let farben = Farben.fuer(dunkel: schema == .dark)
        let start = min(8 * 60, (liste.map(\.von).min() ?? 8 * 60) / 60 * 60)
        let ende = max(18 * 60, ((liste.map(\.bis).max() ?? 18 * 60) + 59) / 60 * 60)
        GeometryReader { geo in
            let achse: CGFloat = 26, kopf: CGFloat = 18
            let spaltenB = (geo.size.width - achse) / 5
            let proMin = (geo.size.height - kopf) / CGFloat(ende - start)
            ZStack(alignment: .topLeading) {
                // Kopf und Stundenlinien
                ForEach(0..<5, id: \.self) { t in
                    Text(Self.tage[t])
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(farben.gedimmt)
                        .frame(width: spaltenB, height: kopf)
                        .offset(x: achse + CGFloat(t) * spaltenB, y: 0)
                }
                ForEach(Array(stride(from: start, through: ende, by: 60)), id: \.self) { m in
                    let y = kopf + CGFloat(m - start) * proMin
                    Path { p in p.move(to: CGPoint(x: achse, y: y)); p.addLine(to: CGPoint(x: geo.size.width, y: y)) }
                        .stroke(farben.gedimmt.opacity(0.18), lineWidth: 0.5)
                    if m < ende {
                        Text("\(m / 60)")
                            .font(.system(size: 9))
                            .foregroundStyle(farben.gedimmt)
                            .frame(width: achse - 4, alignment: .trailing)
                            .offset(x: 0, y: y - 6)
                    }
                }
                // Termine
                ForEach(liste) { b in
                    let breite = spaltenB / CGFloat(b.spalten)
                    let x = achse + CGFloat(b.tag) * spaltenB + CGFloat(b.spalte) * breite
                    let y = kopf + CGFloat(b.von - start) * proMin
                    let h = max(14, CGFloat(b.bis - b.von) * proMin)
                    BlockAnsicht(block: b, fokus: fokus == b.publishid)
                        .frame(width: max(8, breite - 2), height: h - 2)
                        .offset(x: x + 1, y: y + 1)
                        .onTapGesture {
                            withAnimation(.easeInOut(duration: 0.15)) {
                                if b.zustand == .option, let g = b.gruppe {
                                    puls.waehle(b.publishid, gruppe: g)
                                } else {
                                    fokus = fokus == b.publishid ? nil : b.publishid
                                }
                            }
                        }
                }
                if liste.isEmpty {
                    Text("Hier erscheint dein Wochenplan.")
                        .font(.footnote)
                        .foregroundStyle(farben.gedimmt)
                        .frame(width: geo.size.width, height: geo.size.height)
                }
            }
        }
    }
}

private struct BlockAnsicht: View {
    let block: PlanerBlock
    let fokus: Bool

    var body: some View {
        let b = block
        let option = b.zustand == .option
        VStack(alignment: .leading, spacing: 0) {
            Text(b.kurz).font(.system(size: 9, weight: .bold)).lineLimit(1)
            Text(option ? "G\(b.gruppe ?? "") \(b.art)" : b.art).font(.system(size: 8, weight: .medium)).lineLimit(1)
            Text(b.raum).font(.system(size: 7)).lineLimit(1).opacity(0.85)
        }
        .minimumScaleFactor(0.6)
        .padding(.horizontal, 2)
        .padding(.vertical, 1)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .foregroundStyle(option ? Color.secondary : .white)
        .background(
            RoundedRectangle(cornerRadius: 4)
                .fill(option ? Color.gray.opacity(0.16) : b.farbe.opacity(fokus || !option ? 1 : 0.8))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 4)
                .strokeBorder(b.zustand == .konflikt ? Palette.jetzt : (option ? Color.gray.opacity(0.55) : (fokus ? Color.primary.opacity(0.6) : .clear)),
                              style: StrokeStyle(lineWidth: b.zustand == .konflikt ? 2 : 1, dash: option ? [3, 2] : []))
        )
        .contentShape(Rectangle())
    }
}

/// Zeile in „Deine Fächer“: antippen = Fokus (Gruppen im Plan zeigen), darunter Gruppen-Knöpfe
private struct KursZeile: View {
    let kurs: PulsModell.Kurs
    @Binding var fokus: String?
    @ObservedObject private var puls = PulsModell.shared

    var body: some View {
        let info = Kurse.info(kurs.titel)
        let aktiv = fokus == kurs.publishid
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 3).fill(info.farbe).frame(width: 5, height: 34)
                VStack(alignment: .leading, spacing: 2) {
                    Text(kurs.titel).lineLimit(2).foregroundStyle(.primary)
                    if let v = puls.cache[kurs.publishid] {
                        if v.gruppen.count <= 1 {
                            Text("eine Gruppe").font(.caption).foregroundStyle(.secondary)
                        } else if let g = kurs.gruppe {
                            Text("Gruppe \(g) von \(v.gruppen.count)").font(.caption).foregroundStyle(.secondary)
                        } else {
                            Text("Gruppe wählen (\(v.gruppen.count) zur Auswahl)").font(.caption.weight(.semibold)).foregroundStyle(Palette.jetzt)
                        }
                    }
                }
                Spacer()
                NavigationLink { PulsKursAnsicht(publishid: kurs.publishid) } label: {
                    Image(systemName: "info.circle")
                }
                .buttonStyle(.borderless)
                .fixedSize()
            }
            .contentShape(Rectangle())
            .onTapGesture { withAnimation { fokus = aktiv ? nil : kurs.publishid } }

            if aktiv, let v = puls.cache[kurs.publishid], v.gruppen.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(v.gruppen) { g in
                            let gewaehlt = kurs.gruppe == g.nummer
                            let konflikt = !puls.ueberschneidungen(kurs, gruppe: g.nummer).isEmpty
                            Button {
                                withAnimation { puls.waehle(kurs.publishid, gruppe: g.nummer) }
                            } label: {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text("Gruppe \(g.nummer)").font(.caption.weight(.bold))
                                    ForEach(v.eigenes(g).prefix(2), id: \.self) { r in
                                        Text("\(r.tag) \(r.von)–\(r.bis)").font(.caption2)
                                    }
                                    if konflikt { Text("Überschneidung").font(.caption2.weight(.semibold)) }
                                }
                                .padding(.horizontal, 8).padding(.vertical, 5)
                                .foregroundStyle(gewaehlt ? .white : (konflikt ? Palette.jetzt : .primary))
                                .background(RoundedRectangle(cornerRadius: 8).fill(gewaehlt ? info.farbe : Color.gray.opacity(0.15)))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }
}

/// Einzelne Veranstaltung: Gruppe wählen, gemeinsame Termine, Links
struct PulsKursAnsicht: View {
    let publishid: String
    @ObservedObject private var puls = PulsModell.shared
    @Environment(\.dismiss) private var schliessen

    var body: some View {
        let kurs = puls.auswahl.kurse.first(where: { $0.publishid == publishid })
        Form {
            if let v = puls.cache[publishid], let kurs {
                Section {
                    LabeledContent("Art", value: v.art)
                    if let f = v.belegfrist { LabeledContent("Belegfrist", value: f) }
                    Link(destination: v.url) { Label("In PULS öffnen (belegen)", systemImage: "safari") }
                    if let m = v.moodle, let u = URL(string: m) { Link(destination: u) { Label("Moodle-Kurs", systemImage: "graduationcap") } }
                } header: {
                    Text(v.titel)
                }
                if !v.gemeinsam.isEmpty && v.gruppen.count > 1 {
                    Section("Für alle Gruppen") {
                        ForEach(v.gemeinsam, id: \.self) { RegelZeile(regel: $0) }
                    }
                }
                Section {
                    ForEach(v.gruppen) { g in
                        let konflikte = puls.ueberschneidungen(kurs, gruppe: g.nummer)
                        Button {
                            puls.waehle(publishid, gruppe: g.nummer)
                        } label: {
                            HStack(alignment: .top) {
                                Image(systemName: kurs.gruppe == g.nummer ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(kurs.gruppe == g.nummer ? Color.accentColor : .secondary)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(v.gruppen.count > 1 ? "Gruppe \(g.nummer)" : "Termine").fontWeight(.semibold)
                                    ForEach(v.gruppen.count > 1 ? v.eigenes(g) : g.regeln, id: \.self) { RegelZeile(regel: $0) }
                                    if !konflikte.isEmpty {
                                        Label("Überschneidung mit " + konflikte.joined(separator: ", "), systemImage: "exclamationmark.triangle")
                                            .font(.caption)
                                            .foregroundStyle(Palette.jetzt)
                                    }
                                }
                            }
                            .foregroundStyle(.primary)
                        }
                    }
                } header: {
                    Text(v.gruppen.count > 1 ? "Gruppe wählen" : "Termine")
                }
                Section {
                    Button("Veranstaltung entfernen", role: .destructive) {
                        puls.entfernen(publishid)
                        schliessen()
                    }
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle(kurs.map { Kurse.info($0.titel).kurz } ?? "Veranstaltung")
        .navigationBarTitleDisplayMode(.inline)
        .task { if puls.cache[publishid] == nil { _ = await puls.laden(publishid) } }
    }
}

private struct RegelZeile: View {
    let regel: Puls.Regel

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(regel.kurz).font(.subheadline)
            let zeitraum = regel.rhythmus.lowercased().contains("einzel")
                ? "Einzeltermin"
                : "\(regel.rhythmus), \(regel.beginn ?? "") – \(regel.ende ?? "")"
            Text(zeitraum + (regel.ausfaelle.isEmpty ? "" : " · \(regel.ausfaelle.count) Ausfälle"))
                .font(.caption).foregroundStyle(.secondary)
            if !regel.lehrperson.isEmpty {
                Text(regel.lehrperson).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            if let b = regel.bemerkung, !b.isEmpty {
                Text(b).font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}

/// Vorlesungsverzeichnis durchblättern (Fakultät → Institut → Studiengang → Modul → Veranstaltung)
struct PulsVerzeichnisAnsicht: View {
    let pfad: String
    let titel: String
    @ObservedObject private var puls = PulsModell.shared
    @State private var eintraege: [Puls.Eintrag]?
    @State private var fehler = false
    @State private var laedt: String?
    @State private var meldung: String?

    var body: some View {
        List {
            if let eintraege {
                if eintraege.isEmpty { Text("Hier ist nichts eingetragen.").foregroundStyle(.secondary) }
                ForEach(eintraege) { e in
                    switch e.art {
                    case .ordner(let p):
                        NavigationLink(e.name) { PulsVerzeichnisAnsicht(pfad: p, titel: e.name) }
                    case .veranstaltung(let id):
                        Button {
                            Task { await hinzufuegen(id) }
                        } label: {
                            HStack {
                                Text(e.name).foregroundStyle(.primary)
                                Spacer()
                                if laedt == id { ProgressView() }
                                else { Image(systemName: puls.enthaelt(id) ? "checkmark.circle.fill" : "plus.circle") }
                            }
                        }
                        .disabled(puls.enthaelt(id) || laedt != nil)
                    }
                }
            } else if fehler {
                Text("PULS ist gerade nicht erreichbar.").foregroundStyle(.secondary)
                Button("Erneut versuchen") { Task { await laden() } }
            } else {
                ProgressView().frame(maxWidth: .infinity)
            }
        }
        .navigationTitle(titel)
        .navigationBarTitleDisplayMode(.inline)
        .task { if eintraege == nil { await laden() } }
        .alert(meldung ?? "", isPresented: Binding(get: { meldung != nil }, set: { if !$0 { meldung = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }

    private func laden() async {
        fehler = false
        eintraege = await puls.verzeichnis(pfad)
        fehler = eintraege == nil
    }

    private func hinzufuegen(_ id: String) async {
        laedt = id
        meldung = await puls.hinzufuegen(id)
        laedt = nil
    }
}
