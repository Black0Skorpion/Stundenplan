import SwiftUI

struct PlanReiter: View {
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.colorScheme) private var schema
    @State private var wochenVersatz = 0

    var body: some View {
        NavigationStack {
            TimelineView(.periodic(from: .now, by: 30)) { kontext in
                let jetzt = kontext.date
                let farben = Farben.fuer(dunkel: schema == .dark)
                let woche = Plan.woche(zustand.termine, jetzt, versatz: wochenVersatz)

                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        wochenKopf(woche, farben)

                        if woche.leer {
                            Text("Keine Termine in dieser Woche")
                                .foregroundStyle(farben.gedimmt)
                                .frame(maxWidth: .infinity, minHeight: 200)
                        } else {
                            WochenRaster(woche: woche, jetzt: jetzt)
                                .frame(height: 480)
                        }

                        if let auswahl = Plan.tagesAuswahl(zustand.termine, jetzt) {
                            tagesTeil(auswahl, jetzt, farben)
                        } else {
                            Text("Keine weiteren Termine")
                                .foregroundStyle(farben.gedimmt)
                        }
                    }
                    .padding()
                }
                .background(farben.bg)
            }
            .navigationTitle("Stundenplan")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Link(destination: Ziele.planer) {
                        Label("Planer", systemImage: "safari")
                    }
                }
            }
        }
    }

    private func wochenKopf(_ woche: Plan.Woche, _ farben: Farben) -> some View {
        let freitag = Zeit.plusTage(woche.montag, 4)
        return HStack {
            Button { wochenVersatz -= 1 } label: { Image(systemName: "chevron.left") }
            Spacer()
            VStack(spacing: 1) {
                Text("KW \(Zeit.kalenderwoche(woche.montag))")
                    .font(.headline)
                Text("\(Zeit.tagMonat(woche.montag)) – \(Zeit.tagMonat(freitag))")
                    .font(.caption)
                    .foregroundStyle(farben.gedimmt)
            }
            .onTapGesture { wochenVersatz = 0 }
            Spacer()
            Button { wochenVersatz += 1 } label: { Image(systemName: "chevron.right") }
        }
        .font(.title3)
    }

    private func tagesTeil(_ auswahl: Plan.TagesAuswahl, _ jetzt: Date, _ farben: Farben) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(auswahl.label)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(auswahl.istHeute ? farben.akzent : farben.vorder)
                Text(Zeit.datumKurz(auswahl.datum))
                    .foregroundStyle(farben.gedimmt)
                Spacer()
                Link(destination: Ziele.tag(auswahl.datum)) {
                    Label("Lageplan", systemImage: "map")
                        .font(.subheadline)
                }
            }
            if let fokus = Plan.fokus(auswahl, jetzt) {
                TerminKarte(termin: fokus, istHeute: auswahl.istHeute, jetzt: jetzt, ausfuehrlich: true)
                    .verlinkt(Ziele.termin(fokus))
            }
            TagesListe(termine: auswahl.termine, jetzt: jetzt, farben: farben, maxZeilen: 99)
        }
    }
}
