import SwiftUI

/// Speiseplan der Mensa Golm.
/// Ablauf: Tag wählen → sofort den gespeicherten Stand zeigen (falls vorhanden) → im Hintergrund neu laden.
struct MensaReiter: View {
    @Environment(\.colorScheme) private var schema
    @Environment(\.scenePhase) private var phase
    @State private var tage: [Mensa.Tag] = []
    @State private var gewaehlt: Mensa.Tag?
    @State private var plan: Mensa.Speiseplan?
    @State private var laedt = false
    /// Laden ist fehlgeschlagen (Anzeige zeigt dann ggf. den letzten Stand)
    @State private var fehler = false

    var body: some View {
        let farben = Farben.fuer(dunkel: schema == .dark)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    tagesAuswahl(farben)
                    inhalt(farben)
                }
                .padding()
            }
            .background(farben.bg)
            .refreshable { await alles(neu: true) }
            .navigationTitle(Mensa.name)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Link(destination: Mensa.webseite) {
                        Label("OpenMensa", systemImage: "safari")
                    }
                }
            }
        }
        .task { await alles(neu: false) }
        .onChange(of: phase) { _, neu in
            if neu == .active { Task { await alles(neu: false) } }
        }
    }

    // ───────────── Laden ─────────────

    /// Öffnungstage laden und den passenden Tag wählen (falls noch keiner gewählt ist).
    private func alles(neu: Bool) async {
        if tage.isEmpty || neu {
            tage = await Mensa.tage()
        }
        if gewaehlt == nil {
            gewaehlt = Mensa.waehle(.automatisch(0), tage, Date())
        }
        await planLaden()
    }

    private func planLaden() async {
        guard let tag = gewaehlt, !laedt else { return }
        if tag.geschlossen {
            plan = nil
            fehler = false
            return
        }
        // Sofort den letzten Stand zeigen, dann aktualisieren
        if plan == nil { plan = Mensa.speiseplanAusCache(tag.datum) }
        laedt = true
        let neu = await Mensa.speiseplan(tag.datum)
        laedt = false
        guard gewaehlt == tag else { return }   // inzwischen anderer Tag gewählt
        if let neu { plan = neu }
        fehler = neu == nil
    }

    private func waehle(_ tag: Mensa.Tag) {
        gewaehlt = tag
        plan = Mensa.speiseplanAusCache(tag.datum)
        fehler = false
        Task { await planLaden() }
    }

    // ───────────── Ansicht ─────────────

    private var offeneTage: [Mensa.Tag] {
        let heute = Zeit.tagesBeginn(Date())
        return Array(tage.filter { $0.datum >= heute && !$0.geschlossen }.prefix(10))
    }

    private func tagesAuswahl(_ farben: Farben) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(offeneTage, id: \.datum) { tag in
                    let aktiv = gewaehlt.map { Zeit.gleicherTag($0.datum, tag.datum) } ?? false
                    Button { waehle(tag) } label: {
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
                }
            }
        }
    }

    @ViewBuilder
    private func inhalt(_ farben: Farben) -> some View {
        if let tag = gewaehlt {
            HStack {
                Text(Mensa.tagesLabel(tag.datum, Date()) + " · " + Zeit.datumKurz(tag.datum))
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(farben.vorder)
                Spacer()
                if laedt { ProgressView() }
            }
        }
        if fehler {
            VStack(alignment: .leading, spacing: 6) {
                Label(plan == nil ? "Speiseplan nicht erreichbar." : "Aktualisieren fehlgeschlagen – letzter Stand wird angezeigt.",
                      systemImage: "exclamationmark.triangle")
                    .font(.subheadline)
                    .foregroundStyle(Palette.jetzt)
                Button("Erneut versuchen") { Task { await planLaden() } }
            }
        }
        if gewaehlt?.geschlossen == true {
            hinweis("Mensa geschlossen", farben)
        } else if let plan {
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
                Text("Preise für Studierende · Daten: OpenMensa")
                    .font(.caption2)
                    .foregroundStyle(farben.gedimmt)
            }
        } else if laedt || gewaehlt == nil {
            ProgressView().frame(maxWidth: .infinity, minHeight: 120)
        }
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
