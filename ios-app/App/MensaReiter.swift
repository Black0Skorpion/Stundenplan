import SwiftUI

/// Speiseplan der Mensa am gewählten Campus.
/// Seitlich wischen wechselt den Tag (oder oben einen Tag antippen).
/// Ablauf je Tag: sofort den gespeicherten Stand zeigen (falls vorhanden) → im Hintergrund neu laden.
struct MensaReiter: View {
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.colorScheme) private var schema
    @Environment(\.scenePhase) private var phase
    @State private var tage: [Mensa.Tag] = []
    @State private var gewaehlt: Date?
    @State private var plaene: [Date: Mensa.Speiseplan] = [:]
    @State private var laedt: Set<Date> = []
    /// Laden ist fehlgeschlagen (Anzeige zeigt dann ggf. den letzten Stand)
    @State private var fehler: Set<Date> = []
    @State private var geladenFuer: Campus?

    var body: some View {
        let farben = Farben.fuer(dunkel: schema == .dark)
        let campus = zustand.campus
        NavigationStack {
            VStack(spacing: 0) {
                tagesAuswahl(farben)
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                if offeneTage.isEmpty {
                    if tage.isEmpty {
                        ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        hinweis("In den nächsten Tagen geschlossen", farben)
                    }
                } else {
                    TabView(selection: $gewaehlt) {
                        ForEach(offeneTage, id: \.datum) { tag in
                            ScrollView {
                                seite(tag, farben)
                                    .padding(.horizontal)
                                    .padding(.bottom)
                            }
                            .refreshable { await alles(neu: true) }
                            .tag(Optional(tag.datum))
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                }
            }
            .background(farben.bg)
            .navigationTitle(campus.mensaName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    CampusMenu()
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Link(destination: Mensa.webseite(campus)) {
                        Label("OpenMensa", systemImage: "safari")
                    }
                }
            }
        }
        .task(id: campus) {
            if geladenFuer != campus {
                // Anderer Campus: alles zurücksetzen
                tage = []
                plaene = [:]
                fehler = []
                gewaehlt = nil
                geladenFuer = campus
            }
            await alles(neu: false)
        }
        .onChange(of: gewaehlt) { _, neu in
            guard let neu else { return }
            Task { await planLaden(neu) }
            // Nachbartage schon mal vorbereiten, damit das Wischen flüssig ist
            if let i = offeneTage.firstIndex(where: { $0.datum == neu }) {
                for j in [i - 1, i + 1] where offeneTage.indices.contains(j) {
                    let d = offeneTage[j].datum
                    if plaene[d] == nil { Task { await planLaden(d) } }
                }
            }
        }
        .onChange(of: phase) { _, neu in
            if neu == .active { Task { await alles(neu: false) } }
        }
    }

    // ───────────── Laden ─────────────

    /// Öffnungstage laden und den passenden Tag wählen (falls noch keiner gewählt ist).
    private func alles(neu: Bool) async {
        let campus = zustand.campus
        if tage.isEmpty || neu {
            let t = await Mensa.tage(campus: campus)
            guard campus == zustand.campus else { return }
            tage = t
        }
        if gewaehlt == nil || !offeneTage.contains(where: { $0.datum == gewaehlt }) {
            let auto = Mensa.waehle(.automatisch(0), tage, Date())
            gewaehlt = offeneTage.first(where: { $0.datum >= auto.datum })?.datum ?? offeneTage.first?.datum
        }
        if let g = gewaehlt { await planLaden(g, erzwingen: neu) }
    }

    private func planLaden(_ datum: Date, erzwingen: Bool = false) async {
        let campus = zustand.campus
        guard !laedt.contains(datum) else { return }
        // Sofort den letzten Stand zeigen, dann aktualisieren
        if plaene[datum] == nil, let alt = Mensa.speiseplanAusCache(datum, campus: campus) { plaene[datum] = alt }
        laedt.insert(datum)
        let neu = await Mensa.speiseplan(datum, campus: campus)
        laedt.remove(datum)
        guard campus == zustand.campus else { return }   // inzwischen anderer Campus gewählt
        if let neu {
            plaene[datum] = neu
            fehler.remove(datum)
        } else {
            fehler.insert(datum)
        }
    }

    // ───────────── Ansicht ─────────────

    private var offeneTage: [Mensa.Tag] {
        let heute = Zeit.tagesBeginn(Date())
        return Array(tage.filter { $0.datum >= heute && !$0.geschlossen }.prefix(10))
    }

    private func tagesAuswahl(_ farben: Farben) -> some View {
        ScrollViewReader { leser in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(offeneTage, id: \.datum) { tag in
                        let aktiv = gewaehlt.map { Zeit.gleicherTag($0, tag.datum) } ?? false
                        Button {
                            withAnimation { gewaehlt = tag.datum }
                        } label: {
                            VStack(spacing: 2) {
                                Text(Zeit.wochentageKurz[Zeit.wochentag(tag.datum)])
                                    .font(.caption.weight(.semibold))
                                Text("\(Zeit.tag(tag.datum)).")
                                    .font(.headline)
                            }
                            .frame(width: 48, height: 52)
                            .foregroundStyle(aktiv ? .white : farben.vorder)
                            .background(RoundedRectangle(cornerRadius: 12).fill(aktiv ? farben.akzent : farben.gedimmt.opacity(0.15)))
                        }
                        .buttonStyle(.plain)
                        .id(tag.datum)
                    }
                }
            }
            .onChange(of: gewaehlt) { _, neu in
                if let neu { withAnimation { leser.scrollTo(neu, anchor: .center) } }
            }
        }
    }

    @ViewBuilder
    private func seite(_ tag: Mensa.Tag, _ farben: Farben) -> some View {
        let plan = plaene[tag.datum]
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(Mensa.tagesLabel(tag.datum, Date()) + " · " + Zeit.datumKurz(tag.datum))
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(farben.vorder)
                Spacer()
                if laedt.contains(tag.datum) { ProgressView() }
            }
            if fehler.contains(tag.datum) {
                VStack(alignment: .leading, spacing: 6) {
                    Label(plan == nil ? "Speiseplan nicht erreichbar." : "Aktualisieren fehlgeschlagen – letzter Stand wird angezeigt.",
                          systemImage: "exclamationmark.triangle")
                        .font(.subheadline)
                        .foregroundStyle(Palette.jetzt)
                    Button("Erneut versuchen") { Task { await planLaden(tag.datum) } }
                }
            }
            if let plan {
                if plan.leer {
                    hinweis("Noch kein Speiseplan", farben)
                } else {
                    abschnitt(nil, plan.gerichte, farben)
                    abschnitt("Abends", plan.abend, farben)
                    abschnitt("Dessert", plan.desserts, farben)
                    if !plan.theke.isEmpty {
                        Text(Mensa.thekeText(plan.theke))
                            .font(.footnote)
                            .foregroundStyle(farben.gedimmt)
                    }
                    Text("Preise für Studierende · Daten: OpenMensa · Zum Wechseln des Tages seitlich wischen")
                        .font(.caption2)
                        .foregroundStyle(farben.gedimmt)
                }
            } else if !fehler.contains(tag.datum) {
                ProgressView().frame(maxWidth: .infinity, minHeight: 120)
            }
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
    }

    @ViewBuilder
    private func abschnitt(_ titel: String?, _ liste: [Mensa.Gericht], _ farben: Farben) -> some View {
        if !liste.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                if let titel {
                    Text(titel)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(farben.gedimmt)
                }
                ForEach(liste) { g in
                    GerichtZeile(gericht: g, farben: farben, zeilen: 4)
                }
            }
        }
    }

    private func hinweis(_ text: String, _ farben: Farben) -> some View {
        Text(text)
            .foregroundStyle(farben.gedimmt)
            .frame(maxWidth: .infinity, minHeight: 120)
    }
}
