import SwiftUI
import UIKit

/// Reiter „Lageplan“: Räume des heutigen (bzw. nächsten) Tages
struct LageplanReiter: View {
    @EnvironmentObject var zustand: AppZustand

    var body: some View {
        NavigationStack {
            if let auswahl = Plan.tagesAuswahl(zustand.termine, Date()) {
                LageplanAnsicht(titel: auswahl.label + " · " + Zeit.datumKurz(auswahl.datum),
                                schluessel: Plan.termineAm(zustand.alleTermine, auswahl.datum).map(\.schluessel))
            } else {
                LageplanAnsicht(titel: "Lageplan", schluessel: [])
            }
        }
    }
}

struct LageplanAnsicht: View {
    let titel: String
    let schluessel: [String]
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.colorScheme) private var schema
    @State private var vollbild: Campus?
    /// Plan, der ohne Termine (bzw. zusätzlich) angezeigt wird
    @State private var gewaehlterCampus: Campus?
    @State private var raumAendern: Termin?
    @State private var neuerRaum = ""

    /// Aktueller Stand der Termine (inkl. eigener Änderungen)
    private var termine: [Termin] {
        schluessel.compactMap { k in zustand.alleTermine.first(where: { $0.schluessel == k }) }
    }

    var body: some View {
        let farben = Farben.fuer(dunkel: schema == .dark)
        let liste = termine
        let aktiv = liste.filter { !$0.ausfall }
        let nummern = aktiv.count > 1
        let plaene = planListe(aktiv)
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                if liste.isEmpty {
                    Text("Keine Termine").foregroundStyle(farben.gedimmt)
                }
                ForEach(Array(liste.enumerated()), id: \.element.id) { i, t in
                    if i > 0, !liste[i - 1].ausfall, !t.ausfall, Orte.campusWechsel(von: liste[i - 1].raum, zu: t.raum) {
                        VerbindungKarte(von: liste[i - 1], nach: t, farben: farben)
                    } else if i > 0, let weg = wegText(liste[i - 1], t) {
                        Label(weg, systemImage: "figure.walk")
                            .font(.footnote.weight(.medium))
                            .foregroundStyle(farben.gedimmt)
                            .padding(.leading, 4)
                    }
                    karte(t, nummer: liste.count > 1 ? i + 1 : nil)
                }
                ForEach(plaene) { campus in
                    if plaene.count > 1 || liste.isEmpty {
                        Text("Campus \(campus.name)")
                            .font(.headline)
                            .foregroundStyle(farben.vorder)
                            .padding(.top, 4)
                    }
                    PlanBild(campus: campus, markierungen: Lageplan.markierungen(aktiv, campus: campus, nummern: nummern))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(farben.gedimmt.opacity(0.3)))
                        .onTapGesture { vollbild = campus }
                }
                Text("Zum Vergrößern auf den Plan tippen.")
                    .font(.caption)
                    .foregroundStyle(farben.gedimmt)
                    .frame(maxWidth: .infinity)
                Link(destination: Speicher.planerLink) {
                    Text("Im Planer öffnen")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.heute))
                        .foregroundStyle(.white)
                }
            }
            .padding()
        }
        .background(farben.bg)
        .navigationTitle(titel)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Picker("Plan", selection: Binding(get: { gewaehlterCampus }, set: { gewaehlterCampus = $0 })) {
                        Text("Passend zu den Terminen").tag(Campus?.none)
                        ForEach(Campus.allCases) { c in
                            Text(c.name).tag(Campus?.some(c))
                        }
                    }
                } label: {
                    Label("Campus-Plan", systemImage: "map")
                }
            }
        }
        .fullScreenCover(item: $vollbild) { campus in
            ZoomPlan(campus: campus, markierungen: Lageplan.markierungen(termine.filter { !$0.ausfall }, campus: campus,
                                                                        nummern: termine.filter { !$0.ausfall }.count > 1)) { vollbild = nil }
        }
        .alert("Raum ändern", isPresented: Binding(get: { raumAendern != nil }, set: { if !$0 { raumAendern = nil } })) {
            TextField("z. B. 2.70.0.10", text: $neuerRaum)
                .textInputAutocapitalization(.never)
            Button("Speichern") {
                if let t = raumAendern {
                    let r = neuerRaum.trimmingCharacters(in: .whitespaces)
                    zustand.aendere(t) { $0.raum = r.isEmpty ? nil : r }
                }
                raumAendern = nil
            }
            Button("Abbrechen", role: .cancel) { raumAendern = nil }
        } message: {
            Text("Gilt nur für diesen einen Termin. Widgets und Erinnerungen übernehmen den neuen Raum.")
        }
    }

    /// Pläne in der Reihenfolge der Termine; ohne Termine der gewählte bzw. aktuelle Campus
    private func planListe(_ aktiv: [Termin]) -> [Campus] {
        if let g = gewaehlterCampus { return [g] }
        let liste = Lageplan.campusListe(aktiv)
        return liste.isEmpty ? [zustand.campus] : liste
    }

    private func karte(_ t: Termin, nummer: Int?) -> some View {
        let kurs = Kurse.info(t.titel)
        let typ = Kurse.typ(t.typ)
        return VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                if let nummer {
                    Text("\(nummer)")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Palette.jetzt)
                        .frame(minWidth: 20)
                        .background(Capsule().fill(.white))
                }
                Text("\(typ.lang) · \(Zeit.uhrzeit(t.start))–\(Zeit.uhrzeit(t.ende))")
                    .font(.footnote.weight(.semibold))
                    .opacity(0.9)
                if t.ausfall {
                    Text("FÄLLT AUS")
                        .font(.caption2.weight(.heavy))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .background(Capsule().fill(.white.opacity(0.25)))
                }
            }
            Text(t.titel).font(.headline).strikethrough(t.ausfall)
            Text(Lageplan.ortText(t.raum)).font(.subheadline.weight(.semibold))
            if let alt = t.alterRaum {
                Text("Geändert, ursprünglich \(alt)").font(.footnote).opacity(0.85)
            }
            if let e = Lageplan.einrichtungText(t.raum) {
                Text(e).font(.footnote).opacity(0.85)
            }
            HStack(spacing: 8) {
                if let route = Orte.kartenURL(t.raum), !t.ausfall {
                    Link(destination: route) {
                        Label("Route", systemImage: "figure.walk").kartenKnopf()
                    }
                }
                Menu {
                    Button(t.ausfall ? "Findet doch statt" : "Fällt aus",
                           systemImage: t.ausfall ? "arrow.uturn.backward" : "xmark.circle") {
                        zustand.aendere(t) { $0.ausfall.toggle() }
                    }
                    Button("Raum ändern …", systemImage: "door.left.hand.open") {
                        neuerRaum = t.raum
                        raumAendern = t
                    }
                    if t.ausfall || t.alterRaum != nil {
                        Button("Änderungen zurücksetzen", systemImage: "arrow.counterclockwise") {
                            zustand.aendere(t) { $0 = Speicher.Aenderung() }
                        }
                    }
                } label: {
                    Label("Ändern", systemImage: "pencil").kartenKnopf()
                }
            }
            .padding(.top, 6)
        }
        .foregroundStyle(.white)
        .padding(.vertical, 12)
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(kurs.farbe.opacity(t.ausfall ? 0.45 : 1)))
    }
}

/// Halbtransparenter Knopf auf den farbigen Karten
extension View {
    func kartenKnopf() -> some View {
        self
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Capsule().fill(.white.opacity(0.22)))
    }
}

/// Lageplan mit roten Ringen um die markierten Häuser
struct PlanBild: View {
    let campus: Campus
    let markierungen: [Lageplan.Markierung]

    var body: some View {
        let bild = UIImage(named: Lageplan.bild(campus)) ?? UIImage()
        let positionen = Lageplan.positionen(campus)
        let seitenverhaeltnis = bild.size.height > 0 ? bild.size.width / bild.size.height : Lageplan.seitenverhaeltnis(campus)
        Image(uiImage: bild)
            .resizable()
            .aspectRatio(seitenverhaeltnis, contentMode: .fit)
            .background(Color.white)
            .overlay {
                GeometryReader { geo in
                    let b = geo.size.width, h = geo.size.height
                    let ring = b * 0.085
                    ForEach(markierungen) { m in
                        if let p = positionen[m.haus] {
                            let x = p.0 * b, y = p.1 * h
                            Circle()
                                .fill(m.farbe.opacity(0.25))
                                .overlay(Circle().stroke(m.farbe, lineWidth: max(2, b * 0.0065)))
                                .frame(width: ring, height: ring)
                                .position(x: x, y: y)
                            Text(m.label)
                                .font(.system(size: max(9, b * 0.028), weight: .bold))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(m.farbe))
                                .fixedSize()
                                .position(x: x, y: y - ring / 2 - b * 0.03 < 8 ? y + ring / 2 + b * 0.03 : y - ring / 2 - b * 0.03)
                        }
                    }
                }
            }
    }
}

/// Vollbild mit Zoom (zwei Finger) und Verschieben
struct ZoomPlan: View {
    let campus: Campus
    let markierungen: [Lageplan.Markierung]
    let schliessen: () -> Void
    @State private var skala: CGFloat = 1
    @State private var letzteSkala: CGFloat = 1
    @State private var versatz: CGSize = .zero
    @State private var letzterVersatz: CGSize = .zero

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.white.ignoresSafeArea()
            PlanBild(campus: campus, markierungen: markierungen)
                .scaleEffect(skala)
                .offset(versatz)
                .gesture(
                    MagnificationGesture()
                        .onChanged { skala = max(1, min(6, letzteSkala * $0)) }
                        .onEnded { _ in letzteSkala = skala }
                        .simultaneously(with: DragGesture()
                            .onChanged { v in
                                versatz = CGSize(width: letzterVersatz.width + v.translation.width,
                                                 height: letzterVersatz.height + v.translation.height)
                            }
                            .onEnded { _ in letzterVersatz = versatz })
                )
                .onTapGesture(count: 2) {
                    withAnimation {
                        if skala > 1 { skala = 1; versatz = .zero } else { skala = 2.5 }
                        letzteSkala = skala
                        letzterVersatz = versatz
                    }
                }
            Button(action: schliessen) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 30))
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.white, .black.opacity(0.6))
            }
            .padding()
        }
    }
}
