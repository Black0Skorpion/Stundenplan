import SwiftUI

/// Zwischen zwei Terminen an verschiedenen Campus: passende Bus- und Bahnverbindungen.
struct VerbindungKarte: View {
    let von: Termin
    let nach: Termin
    let farben: Farben
    @State private var verbindungen: [Verbindungen.Verbindung]?
    @State private var laedt = false
    @State private var fehler = false
    @State private var offen: String?

    var body: some View {
        let zeit = Int(nach.start.timeIntervalSince(von.ende) / 60)
        let ziel = Campus.aus(raum: nach.raum)
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "tram.fill")
                Text("Campuswechsel nach \(ziel?.name ?? "?")")
                    .fontWeight(.semibold)
                Spacer()
                Text("\(zeit) Min Zeit")
                    .foregroundStyle(farben.gedimmt)
            }
            .font(.footnote)
            .foregroundStyle(farben.vorder)

            if let verbindungen {
                if verbindungen.isEmpty {
                    Text("Keine Verbindung gefunden.").font(.footnote).foregroundStyle(farben.gedimmt)
                }
                ForEach(verbindungen) { v in
                    zeile(v)
                }
            } else if laedt {
                HStack(spacing: 6) {
                    ProgressView().controlSize(.small)
                    Text("Suche Verbindungen …").font(.footnote).foregroundStyle(farben.gedimmt)
                }
            } else if fehler {
                HStack {
                    Text("Verbindungssuche gerade nicht erreichbar.").font(.footnote).foregroundStyle(farben.gedimmt)
                    Button("Erneut") { Task { await laden() } }.font(.footnote)
                }
            }

            if let url = kartenLink {
                Link(destination: url) {
                    Label("In \(Karten.app.name) mit Bus & Bahn", systemImage: "arrow.triangle.turn.up.right.diamond")
                        .font(.footnote.weight(.semibold))
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12).fill(farben.gedimmt.opacity(0.12)))
        .task(id: von.schluessel + nach.schluessel) { await laden() }
    }

    private var kartenLink: URL? {
        guard let a = Campus.aus(raum: von.raum), let b = Campus.aus(raum: nach.raum) else { return nil }
        let start = Orte.koordinate(von.raum) ?? a.mittelpunkt
        let ziel = Orte.koordinate(nach.raum) ?? b.mittelpunkt
        let haus = RaumTeile(nach.raum)?.haus ?? ""
        return Karten.oepnv(von: start, nach: ziel, name: "Uni Potsdam \(b.name) Haus \(haus)")
    }

    @ViewBuilder
    private func zeile(_ v: Verbindungen.Verbindung) -> some View {
        let puenktlich = v.an <= nach.start
        let aufgeklappt = offen == v.id
        VStack(alignment: .leading, spacing: 4) {
            Button {
                withAnimation { offen = aufgeklappt ? nil : v.id }
            } label: {
                HStack(spacing: 6) {
                    Text("\(Zeit.uhrzeit(v.ab)) → \(Zeit.uhrzeit(v.an))")
                        .fontWeight(.semibold)
                        .monospacedDigit()
                    Text(v.linien)
                        .foregroundStyle(farben.gedimmt)
                        .lineLimit(1)
                    Spacer()
                    Text(puenktlich ? "\(v.minuten) Min" : "zu spät")
                        .foregroundStyle(puenktlich ? farben.gedimmt : Palette.jetzt)
                    Image(systemName: aufgeklappt ? "chevron.up" : "chevron.down")
                        .font(.caption2)
                        .foregroundStyle(farben.gedimmt)
                }
                .font(.footnote)
                .foregroundStyle(farben.vorder)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if aufgeklappt {
                ForEach(Array(v.abschnitte.enumerated()), id: \.offset) { _, a in
                    Label(Verbindungen.text(a), systemImage: a.zuFuss ? "figure.walk" : (a.produkt == "bus" ? "bus" : "tram"))
                        .font(.caption)
                        .foregroundStyle(farben.gedimmt)
                }
            }
        }
    }

    private func laden() async {
        guard !laedt else { return }
        laedt = true
        fehler = false
        let ergebnis = await Verbindungen.suchen(von: von, nach: nach)
        laedt = false
        if let ergebnis {
            verbindungen = ergebnis
            offen = ergebnis.first(where: { $0.an <= nach.start })?.id ?? ergebnis.first?.id
        } else {
            fehler = true
        }
    }
}
