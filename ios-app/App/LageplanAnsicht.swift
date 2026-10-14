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
                LageplanAnsicht(titel: "Lageplan Golm", schluessel: [])
            }
        }
    }
}

struct LageplanAnsicht: View {
    let titel: String
    let schluessel: [String]
    @EnvironmentObject var zustand: AppZustand
    @Environment(\.colorScheme) private var schema
    @State private var vollbild = false
    @State private var raumAendern: Termin?
    @State private var neuerRaum = ""

    /// Aktueller Stand der Termine (inkl. eigener Änderungen)
    private var termine: [Termin] {
        schluessel.compactMap { k in zustand.alleTermine.first(where: { $0.schluessel == k }) }
    }

    var body: some View {
        let farben = Farben.fuer(dunkel: schema == .dark)
        let liste = termine
        let markierungen = Lageplan.markierungen(liste.filter { !$0.ausfall })
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                if liste.isEmpty {
                    Text("Keine Termine").foregroundStyle(farben.gedimmt)
                }
                ForEach(Array(liste.enumerated()), id: \.element.id) { i, t in
                    if i > 0, let weg = wegText(liste[i - 1], t) {
                        Label(weg, systemImage: "figure.walk")
                            .font(.footnote.weight(.medium))
                            .foregroundStyle(farben.gedimmt)
                            .padding(.leading, 4)
                    }
                    karte(t, nummer: liste.count > 1 ? i + 1 : nil)
                }
                PlanBild(markierungen: markierungen)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(farben.gedimmt.opacity(0.3)))
                    .onTapGesture { vollbild = true }
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
        .fullScreenCover(isPresented: $vollbild) {
            ZoomPlan(markierungen: markierungen) { vollbild = false }
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
    let markierungen: [Lageplan.Markierung]
    private let bild = UIImage(named: "lageplan_golm") ?? UIImage()

    var body: some View {
        let seitenverhaeltnis = bild.size.height > 0 ? bild.size.width / bild.size.height : 1090.0 / 1490.0
        Image(uiImage: bild)
            .resizable()
            .aspectRatio(seitenverhaeltnis, contentMode: .fit)
            .background(Color.white)
            .overlay {
                GeometryReader { geo in
                    let b = geo.size.width, h = geo.size.height
                    let ring = b * 0.085
                    ForEach(markierungen) { m in
                        if let p = Lageplan.positionen[m.haus] {
                            let x = p.0 * b, y = p.1 * h
                            Circle()
                                .fill(Palette.jetzt.opacity(0.22))
                                .overlay(Circle().stroke(Palette.jetzt, lineWidth: max(2, b * 0.0065)))
                                .frame(width: ring, height: ring)
                                .position(x: x, y: y)
                            Text(m.label)
                                .font(.system(size: max(9, b * 0.028), weight: .bold))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(Palette.jetzt))
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
    let markierungen: [Lageplan.Markierung]
    let schliessen: () -> Void
    @State private var skala: CGFloat = 1
    @State private var letzteSkala: CGFloat = 1
    @State private var versatz: CGSize = .zero
    @State private var letzterVersatz: CGSize = .zero

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.white.ignoresSafeArea()
            PlanBild(markierungen: markierungen)
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
