import SwiftUI
import UIKit

/// Reiter „Lageplan“: Räume des heutigen (bzw. nächsten) Tages
struct LageplanReiter: View {
    @EnvironmentObject var zustand: AppZustand

    var body: some View {
        NavigationStack {
            if let auswahl = Plan.tagesAuswahl(zustand.termine, Date()) {
                LageplanAnsicht(titel: auswahl.label + " · " + Zeit.datumKurz(auswahl.datum), termine: auswahl.termine)
            } else {
                LageplanAnsicht(titel: "Lageplan Golm", termine: [])
            }
        }
    }
}

struct LageplanAnsicht: View {
    let titel: String
    let termine: [Termin]
    @Environment(\.colorScheme) private var schema
    @State private var vollbild = false

    var body: some View {
        let farben = Farben.fuer(dunkel: schema == .dark)
        let markierungen = Lageplan.markierungen(termine)
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                if termine.isEmpty {
                    Text("Keine Termine").foregroundStyle(farben.gedimmt)
                }
                ForEach(Array(termine.enumerated()), id: \.offset) { i, t in
                    karte(t, nummer: termine.count > 1 ? i + 1 : nil)
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
            }
            Text(t.titel).font(.headline)
            Text(Lageplan.ortText(t.raum)).font(.subheadline.weight(.semibold))
            if let e = Lageplan.einrichtungText(t.raum) {
                Text(e).font(.footnote).opacity(0.85)
            }
        }
        .foregroundStyle(.white)
        .padding(.vertical, 12)
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(kurs.farbe))
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
