import SwiftUI

// ───────────────────────── Links ─────────────────────────

/// Links, die die App öffnen (aus Widgets heraus oder in der App selbst).
enum Ziele {
    static func termin(_ t: Termin) -> URL {
        URL(string: "stundenplan://lageplan?termin=\(Int(t.start.timeIntervalSince1970))")!
    }
    static func tag(_ d: Date) -> URL {
        URL(string: "stundenplan://lageplan?tag=\(Int(Zeit.tagesBeginn(d).timeIntervalSince1970))")!
    }
    static let planer = URL(string: "stundenplan://planer")!
    /// Öffnet einfach die App (Reiter „Plan“) – Widgets verlinken nie direkt auf den Planer
    static let app = URL(string: "stundenplan://plan")!
    static let mensa = URL(string: "stundenplan://mensa")!
}

extension View {
    /// Macht den Inhalt antippbar, wenn ein Ziel angegeben ist (in Widgets und in der App).
    @ViewBuilder
    func verlinkt(_ url: URL?) -> some View {
        if let url {
            Link(destination: url) { self }
        } else {
            self
        }
    }
}

// ───────────────────────── Wochenraster ─────────────────────────

struct WochenRaster: View {
    let woche: Plan.Woche
    let jetzt: Date
    var mitDatum = true
    var links = true

    private let achse: CGFloat = 20
    private let kopf: CGFloat = 17
    private let luecke: CGFloat = 3

    var body: some View {
        GeometryReader { geo in
            let breite = geo.size.width
            let hoehe = geo.size.height
            let spaltenB = (breite - achse - luecke * 4) / 5
            let rasterH = hoehe - kopf - 1
            let spanne = CGFloat(max(1, woche.endeH - woche.startH))
            let yVon: (Double) -> CGFloat = { h in kopf + CGFloat(h - Double(woche.startH)) / spanne * rasterH }

            ZStack(alignment: .topLeading) {
                // Stundenlinien und Beschriftung
                ForEach(woche.startH...woche.endeH, id: \.self) { h in
                    let y = yVon(Double(h))
                    let gerade = (h - woche.startH) % 2 == 0
                    Rectangle()
                        .fill(Palette.raster.opacity(gerade ? 0.28 : 0.12))
                        .frame(width: breite - achse, height: 0.5)
                        .offset(x: achse, y: y)
                    if gerade {
                        Text("\(h)")
                            .font(.system(size: 9, weight: .medium))
                            .foregroundStyle(Palette.raster)
                            .frame(width: achse - 4, height: 12, alignment: .trailing)
                            .offset(x: 0, y: min(y - 6, hoehe - 12))
                    }
                }

                ForEach(Array(woche.tage.enumerated()), id: \.offset) { i, tag in
                    let x = achse + CGFloat(i) * (spaltenB + luecke)
                    let heute = Zeit.gleicherTag(tag.datum, jetzt)
                    let label = Zeit.wochentage[i] + (mitDatum ? " \(Zeit.tag(tag.datum))." : "")

                    // Tageskopf (Tippen: Lageplan des Tages)
                    Group {
                        if heute {
                            Text(label)
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: spaltenB - 4, height: 14)
                                .background(Capsule().fill(Palette.heute))
                                .frame(width: spaltenB, height: 14)
                        } else {
                            Text(label)
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundStyle(Palette.raster)
                                .frame(width: spaltenB, height: 14)
                        }
                    }
                    .verlinkt(links && !tag.termine.isEmpty ? Ziele.tag(tag.datum) : nil)
                    .offset(x: x, y: 0)

                    if heute {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(Palette.raster.opacity(0.12))
                            .frame(width: spaltenB, height: rasterH)
                            .offset(x: x, y: kopf)
                    }

                    // Blöcke
                    ForEach(Plan.platzieren(tag.termine)) { p in
                        let y1 = yVon(max(Double(woche.startH), Zeit.stunden(p.termin.start)))
                        let y2 = yVon(min(Double(woche.endeH), Zeit.stunden(p.termin.ende)))
                        let bSpalte = (spaltenB - CGFloat(p.spalten - 1) * 2) / CGFloat(p.spalten)
                        let bx = x + CGFloat(p.spalte) * (bSpalte + 2)
                        let hStapel = (y2 - y1 - CGFloat(p.stapelAnzahl - 1) * 2) / CGFloat(p.stapelAnzahl)
                        let by = y1 + CGFloat(p.stapel) * (hStapel + 2)
                        let h = max(8, hStapel - 1.5)
                        TerminBlock(termin: p.termin, breite: bSpalte, hoehe: h)
                            .verlinkt(links ? Ziele.termin(p.termin) : nil)
                            .offset(x: bx, y: by + 0.75)
                    }

                    // Aktuelle Uhrzeit
                    let jetztH = Zeit.stunden(jetzt)
                    if heute && jetztH >= Double(woche.startH) && jetztH <= Double(woche.endeH) {
                        let y = yVon(jetztH)
                        Rectangle()
                            .fill(Palette.jetzt)
                            .frame(width: spaltenB + 1, height: 2)
                            .offset(x: x - 1, y: y - 1)
                        Circle()
                            .fill(Palette.jetzt)
                            .frame(width: 7, height: 7)
                            .offset(x: x - 4, y: y - 3.5)
                    }
                }
            }
            .frame(width: breite, height: hoehe, alignment: .topLeading)
        }
    }
}

/// Farbiger Block im Wochenraster. Je nach Platz: Art · Kurs · Raum (Raum ist wichtiger als die Art).
struct TerminBlock: View {
    let termin: Termin
    let breite: CGFloat
    let hoehe: CGFloat

    var body: some View {
        let kurs = Kurse.info(termin.titel)
        let typ = Kurse.typ(termin.typ)
        let schmal = breite < 40
        let kurz = hoehe < 34
        let kompakt = hoehe < 40
        let gedimmt = Color.white.opacity(0.85)

        VStack(alignment: .leading, spacing: 0) {
            if kurz {
                Text(kurs.kurz).font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                if hoehe >= 16 {
                    Text(termin.raum).font(.system(size: 7.5, weight: .medium)).foregroundStyle(gedimmt)
                }
            } else if kompakt && !schmal {
                Text(kurs.kurz).font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                Text(termin.raum + "\u{00A0}·\u{00A0}" + (breite >= 80 ? typ.lang : typ.kurz))
                    .font(.system(size: 7.5, weight: .medium)).foregroundStyle(gedimmt)
                if hoehe >= 36 {
                    Text(Zeit.uhrzeit(termin.start) + "–" + Zeit.uhrzeit(termin.ende))
                        .font(.system(size: 7.5, weight: .medium)).foregroundStyle(gedimmt)
                }
            } else if schmal {
                Text(typ.kurz).font(.system(size: 7, weight: .medium)).foregroundStyle(gedimmt)
                Text(kurs.kurz.replacingOccurrences(of: "-", with: "-\n").replacingOccurrences(of: " ", with: "\n"))
                    .font(.system(size: 8.5, weight: .bold)).foregroundStyle(.white)
                    .lineLimit(2)
                Text(raumUmbruch(termin.raum))
                    .font(.system(size: 7, weight: .medium)).foregroundStyle(gedimmt)
                    .lineLimit(3)
            } else {
                Text(typ.lang).font(.system(size: 7.5, weight: .medium)).foregroundStyle(gedimmt)
                Text(kurs.kurz).font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                    .lineLimit(hoehe >= 52 ? 2 : 1)
                Text(termin.raum).font(.system(size: 7.5, weight: .medium)).foregroundStyle(gedimmt)
                    .lineLimit(2)
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.7)
        .padding(.horizontal, schmal || kurz ? 2 : 3.5)
        .padding(.vertical, kurz ? 1 : 3)
        .frame(width: breite, height: hoehe, alignment: .topLeading)
        .background(RoundedRectangle(cornerRadius: 5).fill(kurs.farbe))
        .clipped()
    }

    /// "2.70.0.10" → "2.70.\n0.10" (für schmale Blöcke)
    private func raumUmbruch(_ r: String) -> String {
        let t = r.split(separator: ".", omittingEmptySubsequences: false)
        guard t.count >= 3 else { return r }
        return t[0] + "." + t[1] + ".\n" + t.dropFirst(2).joined(separator: ".")
    }
}

// ───────────────────────── Karte (aktuelle/nächste Veranstaltung) ─────────────────────────

struct TerminKarte: View {
    let termin: Termin
    let istHeute: Bool
    let jetzt: Date
    var ausfuehrlich = false

    var body: some View {
        let kurs = Kurse.info(termin.titel)
        let typ = Kurse.typ(termin.typ)
        let laufend = termin.laeuft(jetzt)
        let bald = !laufend && istHeute && termin.start.timeIntervalSince(jetzt) <= 3600 && termin.start > jetzt

        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 3) {
                if laufend {
                    Text("Jetzt · noch")
                    Text(timerInterval: jetzt...termin.ende, countsDown: true)
                        .monospacedDigit()
                } else if bald {
                    Text("Beginnt in")
                    Text(timerInterval: jetzt...termin.start, countsDown: true)
                        .monospacedDigit()
                } else {
                    Text(istHeute ? "Als Nächstes" : "Erster Termin")
                }
            }
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(.white.opacity(0.9))
            .lineLimit(1)

            if ausfuehrlich {
                Text(termin.titel)
                    .font(.system(size: 15, weight: .bold))
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
            } else {
                Text(kurs.kurz)
                    .font(.system(size: 18, weight: .bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
            }
            Text(typ.lang + " · " + termin.raum)
                .font(.system(size: 11, weight: .medium))
                .opacity(0.9)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Text(Zeit.uhrzeit(termin.start) + "–" + Zeit.uhrzeit(termin.ende))
                .font(.system(size: 11, weight: .medium))
                .opacity(0.9)
            if laufend {
                ProgressView(timerInterval: termin.start...termin.ende, countsDown: false) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
                .progressViewStyle(.linear)
                .tint(.white)
                .padding(.top, 3)
            }
        }
        .foregroundStyle(.white)
        .padding(.vertical, 8)
        .padding(.horizontal, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12).fill(kurs.farbe))
    }
}

// ───────────────────────── Tagesliste ─────────────────────────

struct TagesListe: View {
    let termine: [Termin]
    let jetzt: Date
    let farben: Farben
    let maxZeilen: Int
    var links = true

    var body: some View {
        // Vergangene Termine zuerst ausblenden, wenn der Platz nicht reicht
        var liste = termine
        var mehr = 0
        if liste.count > maxZeilen {
            while liste.count > maxZeilen, let erster = liste.first, erster.ende <= jetzt { liste.removeFirst() }
            if liste.count > maxZeilen {
                mehr = liste.count - (maxZeilen - 1)
                liste = Array(liste.prefix(max(0, maxZeilen - 1)))
            }
        }
        return VStack(alignment: .leading, spacing: 3) {
            ForEach(liste) { t in
                TerminZeile(termin: t, jetzt: jetzt, farben: farben)
                    .verlinkt(links ? Ziele.termin(t) : nil)
            }
            if mehr > 0 {
                Text("+ \(mehr) weitere")
                    .font(.system(size: 10))
                    .foregroundStyle(farben.gedimmt)
            }
        }
    }
}

struct TerminZeile: View {
    let termin: Termin
    let jetzt: Date
    let farben: Farben

    var body: some View {
        let kurs = Kurse.info(termin.titel)
        let typ = Kurse.typ(termin.typ)
        let vorbei = termin.ende <= jetzt
        let laufend = termin.laeuft(jetzt)
        HStack(spacing: 7) {
            RoundedRectangle(cornerRadius: 2)
                .fill(kurs.farbe.opacity(vorbei ? 0.35 : 1))
                .frame(width: 4, height: 26)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 5) {
                    Text(Zeit.uhrzeit(termin.start))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(farben.gedimmt)
                    Text(kurs.kurz)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(farben.vorder)
                        .minimumScaleFactor(0.7)
                }
                Text(typ.lang + " · " + termin.raum)
                    .font(.system(size: 10))
                    .foregroundStyle(farben.gedimmt)
                    .minimumScaleFactor(0.75)
            }
            .lineLimit(1)
            .opacity(vorbei ? 0.45 : 1)
            Spacer(minLength: 0)
        }
        .padding(.trailing, 4)
        .frame(height: 30)
        .background(
            RoundedRectangle(cornerRadius: 6)
                .fill(laufend ? kurs.farbe.opacity(0.16) : .clear)
        )
    }
}

// ───────────────────────── Mensa-Zeile ─────────────────────────

struct GerichtZeile: View {
    let gericht: Mensa.Gericht
    let farben: Farben
    var zeilen = 2

    var body: some View {
        HStack(alignment: .top, spacing: 7) {
            RoundedRectangle(cornerRadius: 2)
                .fill(Color(hex: gericht.farbe))
                .frame(width: 4)
                .frame(minHeight: 24)
            Text(gericht.name)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(farben.vorder)
                .lineLimit(zeilen)
                .minimumScaleFactor(0.9)
                .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .trailing, spacing: 0) {
                Text(Mensa.preisText(gericht.preis))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(farben.vorder)
                Text(Mensa.kategorieLabel(gericht.kat))
                    .font(.system(size: 9))
                    .foregroundStyle(farben.gedimmt)
            }
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .frame(width: 54, alignment: .trailing)
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}
