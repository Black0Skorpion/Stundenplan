import SwiftUI

struct PlanReiter: View {
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.colorScheme) private var schema
    @State private var wochenVersatz = 0
    @State private var teilen = false
    @State private var profileZeigen = false

    var body: some View {
        NavigationStack {
            TimelineView(.periodic(from: .now, by: 30)) { kontext in
                let jetzt = kontext.date
                let farben = Farben.fuer(dunkel: schema == .dark)
                let woche = Plan.woche(zustand.planAlleTermine, jetzt, versatz: wochenVersatz)

                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        if zustand.planAlleTermine.isEmpty && zustand.profil == nil {
                            leererPlan(farben)
                        }

                        wochenKopf(woche, farben)

                        if woche.leer {
                            Text("Keine Termine in dieser Woche")
                                .foregroundStyle(farben.gedimmt)
                                .frame(maxWidth: .infinity, minHeight: 200)
                        } else {
                            WochenRaster(woche: woche, jetzt: jetzt)
                                .frame(height: 480)
                        }

                        if let auswahl = Plan.tagesAuswahl(zustand.planTermine, jetzt) {
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
            .navigationTitle(zustand.profil.map { "Plan von \($0.name)" } ?? "Stundenplan")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { profilMenu }
                ToolbarItem(placement: .topBarTrailing) {
                    if zustand.profil != nil {
                        Button("Mein Plan") { zustand.profilID = nil }
                    } else {
                        Button { teilen = true } label: { Label("Teilen", systemImage: "square.and.arrow.up") }
                    }
                }
            }
            .sheet(isPresented: $teilen) { NavigationStack { TeilenAnsicht() } }
            .sheet(isPresented: $profileZeigen) { NavigationStack { ProfileAnsicht() }.environmentObject(zustand) }
            .onChange(of: zustand.profilID) { _, _ in wochenVersatz = 0 }
        }
    }

    private var profilMenu: some View {
        Menu {
            Picker("Plan", selection: $zustand.profilID) {
                let name = Speicher.einstellungen().name
                Label(name.isEmpty ? "Mein Plan" : "\(name) (ich)", systemImage: "person.fill").tag(UUID?.none)
                ForEach(zustand.profile) { p in
                    Label(p.name, systemImage: "person").tag(UUID?.some(p.id))
                }
            }
            Divider()
            Button { profileZeigen = true } label: { Label("Profile verwalten …", systemImage: "person.2") }
        } label: {
            Image(systemName: zustand.profil == nil ? "person.crop.circle" : "person.crop.circle.fill")
        }
    }

    private func leererPlan(_ farben: Farben) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Noch kein Stundenplan").font(.headline)
            Text("Wähle deine Veranstaltungen direkt aus PULS – oder lass dir den Plan von jemandem per QR-Code oder Datei schicken.")
                .font(.subheadline).foregroundStyle(farben.gedimmt)
            Button { zustand.modulwahl = true } label: {
                Label("Veranstaltungen aus PULS wählen", systemImage: "checklist")
            }
            .buttonStyle(.borderedProminent)
            Button { profileZeigen = true } label: {
                Label("QR-Code scannen", systemImage: "qrcode.viewfinder")
            }
            .buttonStyle(.bordered)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.gray.opacity(0.12)))
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
            TagesListe(termine: Plan.termineAm(zustand.planAlleTermine, auswahl.datum), jetzt: jetzt, farben: farben,
                       maxZeilen: 99, wege: true)
        }
    }
}
