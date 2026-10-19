import Foundation

/// Rechenlogik rund um den Stundenplan (gleich wie im Scriptable-Widget).
enum Plan {

    static func viertelAnpassen(_ t: Termin) -> Termin {
        guard Konfig.akademischesViertel else { return t }
        let k = Zeit.kalender
        let startMin = k.component(.minute, from: t.start)
        let endeMin = k.component(.minute, from: t.ende)
        if startMin == 0 && endeMin == 0 && t.dauer >= 90 * 60 {
            var n = t
            n.start = t.start.addingTimeInterval(15 * 60)
            n.ende = t.ende.addingTimeInterval(-15 * 60)
            return n
        }
        return t
    }

    /// Termin auf eigene Uhrzeiten setzen (Minuten nach Mitternacht); die ursprünglichen Zeiten bleiben gemerkt
    /// `weckerFest`: wird der Beginn nach hinten verschoben, bleibt der Wecker bei der ursprünglichen Zeit
    static func zeitSetzen(_ t: Termin, von: Int, bis: Int, weckerFest: Bool = true) -> Termin {
        var n = t
        if n.originalStart == nil {
            n.originalStart = t.start
            n.originalEnde = t.ende
        }
        let tag = Zeit.tagesBeginn(n.originalStart ?? t.start)
        n.start = tag.addingTimeInterval(TimeInterval(von * 60))
        n.ende = tag.addingTimeInterval(TimeInterval(max(bis, von + 5) * 60))
        let o = n.originalStart ?? t.start
        n.weckStart = weckerFest && n.start > o ? o : nil
        return n
    }

    static func minuten(_ d: Date) -> Int {
        let k = Zeit.kalender.dateComponents([.hour, .minute], from: d)
        return (k.hour ?? 0) * 60 + (k.minute ?? 0)
    }

    static func termineAm(_ alle: [Termin], _ tag: Date) -> [Termin] {
        let von = Zeit.tagesBeginn(tag)
        let bis = Zeit.plusTage(von, 1)
        return alle.filter { $0.start >= von && $0.start < bis }
    }

    struct TagesAuswahl {
        let datum: Date
        let termine: [Termin]
        let label: String
        let istHeute: Bool
    }

    /// Heute, solange noch etwas ansteht – danach der nächste Tag mit Terminen.
    static func tagesAuswahl(_ alle: [Termin], _ jetzt: Date) -> TagesAuswahl? {
        let heute = Zeit.tagesBeginn(jetzt)
        let heuteTermine = termineAm(alle, heute)
        if heuteTermine.contains(where: { $0.ende > jetzt }) {
            return TagesAuswahl(datum: heute, termine: heuteTermine, label: "Heute", istHeute: true)
        }
        let morgen = Zeit.plusTage(heute, 1)
        guard let naechster = alle.first(where: { $0.start >= morgen }) else { return nil }
        let tag = Zeit.tagesBeginn(naechster.start)
        let label = Zeit.gleicherTag(tag, morgen) ? "Morgen" : Zeit.wochentageVoll[Zeit.wochentag(tag)]
        return TagesAuswahl(datum: tag, termine: termineAm(alle, tag), label: label, istHeute: false)
    }

    static func fokus(_ auswahl: TagesAuswahl, _ jetzt: Date) -> Termin? {
        let t = auswahl.termine
        return t.first(where: { $0.laeuft(jetzt) }) ?? t.first(where: { $0.start > jetzt }) ?? t.first
    }

    static func naechster(_ alle: [Termin], _ jetzt: Date) -> Termin? {
        alle.first(where: { $0.laeuft(jetzt) }) ?? alle.first(where: { $0.start > jetzt })
    }

    static func statusText(_ alle: [Termin], _ jetzt: Date) -> String {
        if let l = alle.first(where: { $0.laeuft(jetzt) }) {
            return "bis \(Zeit.uhrzeit(l.ende)) \(Kurse.info(l.titel).kurz) · \(l.raum)"
        }
        guard let n = alle.first(where: { $0.start > jetzt }) else { return "Keine weiteren Termine" }
        let tag = Zeit.gleicherTag(n.start, jetzt) ? "" : Zeit.wochentageKurz[Zeit.wochentag(n.start)] + " "
        return "ab \(tag)\(Zeit.uhrzeit(n.start)) \(Kurse.info(n.titel).kurz) · \(n.raum)"
    }

    // ───────────── Woche ─────────────

    struct Tag: Identifiable {
        let datum: Date
        let termine: [Termin]
        var id: Date { datum }
    }

    struct Woche {
        let montag: Date
        let tage: [Tag]
        let naechsteWoche: Bool
        let startH: Int
        let endeH: Int
        var leer: Bool { tage.allSatisfy { $0.termine.isEmpty } }
    }

    /// Angezeigte Woche: aktuelle Woche, am Wochenende die nächste. `versatz` blättert in Wochen.
    static func woche(_ alle: [Termin], _ jetzt: Date, versatz: Int = 0) -> Woche {
        var montag = Zeit.tagesBeginn(jetzt)
        let wt = (Zeit.wochentag(montag) + 6) % 7
        montag = Zeit.plusTage(montag, -wt)
        let naechste = wt >= 5
        if naechste { montag = Zeit.plusTage(montag, 7) }
        montag = Zeit.plusTage(montag, 7 * versatz)
        let tage = (0..<5).map { i -> Tag in
            let d = Zeit.plusTage(montag, i)
            return Tag(datum: d, termine: termineAm(alle, d))
        }
        var startH = Konfig.standardStart
        var endeH = Konfig.standardEnde
        for t in tage.flatMap({ $0.termine }) {
            startH = min(startH, Int(floor(Zeit.stunden(t.start))))
            endeH = max(endeH, Int(ceil(Zeit.stunden(t.ende))))
        }
        return Woche(montag: montag, tage: tage, naechsteWoche: naechste && versatz == 0, startH: startH, endeH: endeH)
    }

    // ───────────── Überlappungen ─────────────

    struct Platziert: Identifiable {
        let termin: Termin
        var spalte = 0
        var spalten = 1
        var stapel = 0
        var stapelAnzahl = 1
        var id: String { termin.id }
    }

    /// Überlappende Termine nebeneinander; exakt gleichzeitige übereinander.
    static func platzieren(_ termine: [Termin]) -> [Platziert] {
        let sortiert = termine.sorted { $0.start != $1.start ? $0.start < $1.start : $0.ende > $1.ende }
        var fertig: [Platziert] = []
        var gruppe: [Platziert] = []
        var gruppenEnde = Date.distantPast

        func abschliessen() {
            var spaltenEnde: [Date] = []
            for i in gruppe.indices {
                let t = gruppe[i].termin
                if let frei = spaltenEnde.firstIndex(where: { $0 <= t.start }) {
                    gruppe[i].spalte = frei
                    spaltenEnde[frei] = t.ende
                } else {
                    gruppe[i].spalte = spaltenEnde.count
                    spaltenEnde.append(t.ende)
                }
            }
            let gleichzeitig = gruppe.count > 1 && gruppe.allSatisfy {
                $0.termin.start == gruppe[0].termin.start && $0.termin.ende == gruppe[0].termin.ende
            }
            for i in gruppe.indices {
                if gleichzeitig {
                    gruppe[i].spalte = 0
                    gruppe[i].spalten = 1
                    gruppe[i].stapel = i
                    gruppe[i].stapelAnzahl = gruppe.count
                } else {
                    gruppe[i].spalten = spaltenEnde.count
                }
            }
            fertig.append(contentsOf: gruppe)
            gruppe = []
        }

        for t in sortiert {
            if !gruppe.isEmpty && t.start >= gruppenEnde { abschliessen() }
            gruppe.append(Platziert(termin: t))
            gruppenEnde = max(gruppenEnde, t.ende)
        }
        if !gruppe.isEmpty { abschliessen() }
        return fertig
    }

    // ───────────── Widget-Aktualisierung ─────────────

    /// Zeitpunkte, an denen sich die Anzeige ändert (Beginn, Ende, 1 Stunde vor Beginn, Mitternacht).
    static func wechselZeitpunkte(_ alle: [Termin], ab jetzt: Date, stunden: Double = 24) -> [Date] {
        let bis = jetzt.addingTimeInterval(stunden * 3600)
        var punkte = Set<Date>()
        for t in alle where t.ende > jetzt && t.start < bis.addingTimeInterval(3600) {
            for p in [t.start.addingTimeInterval(-3600), t.start, t.ende] where p > jetzt && p <= bis {
                punkte.insert(p)
            }
        }
        var mitternacht = Zeit.plusTage(Zeit.tagesBeginn(jetzt), 1)
        while mitternacht <= bis {
            punkte.insert(mitternacht)
            mitternacht = Zeit.plusTage(mitternacht, 1)
        }
        return punkte.sorted()
    }
}
