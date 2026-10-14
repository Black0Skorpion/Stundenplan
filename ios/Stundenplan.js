// Stundenplan-Widget für Scriptable (klein, mittel, groß, iPad extragroß und Sperrbildschirm)
// Design: kräftige, vollflächige Farben. Hell/Dunkel folgt automatisch dem iPhone.
//
// Größen: Klein (2×2) = aktuelle/nächste Veranstaltung, Mittel (4×2) = dazu der Tagesplan,
// Groß (4×4) = ganze Woche, Extragroß (nur iPad) = Woche plus aktuelle Veranstaltung und Tagesplan. Nach der letzten Veranstaltung zeigen Klein und Mittel den nächsten Tag.
//
// Widget-Parameter (optional, beim Bearbeiten des Widgets eintragen, mit Leerzeichen kombinierbar):
//   hell   → immer heller Modus
//   dunkel → immer dunkler Modus
//   https://planner.joshuan.dev/de?s=…  → dein eigener Planer-Link, öffnet sich beim Tippen
//
// Sperrbildschirm-Widgets (iPhone): Variante ebenfalls über den Parameter wählen:
//   Zeile über der Uhr:  naechste (Standard) · raum · countdown
//   Rechteckig:          naechste (Standard) · raum · tag
//   Rund:                countdown (Standard) · raum · zeit
//
// Eigener Planer-Link: Im Planer auf "Plan teilen" tippen, Link kopieren und entweder oben bei
// CONFIG.TAP_URL eintragen (gilt für alle Widgets) oder als Widget-Parameter (nur dieses Widget).
//
// Stundenplan aktualisieren: Neue .ics-Datei aus dem Planer exportieren und als
// "stundenplan.ics" in iCloud Drive › Scriptable ablegen. Liegt dort keine Datei,
// werden die unten eingebetteten Termine (WiSe 26/27) verwendet.

// ───────────────────────── Einstellungen ─────────────────────────

const CONFIG = {
  ICS_DATEI: "stundenplan.ics",
  // Termine, die zur vollen Stunde beginnen und enden (z. B. 8–10 Uhr),
  // werden als 8:15–9:45 dargestellt (akademisches Viertel).
  AKADEMISCHES_VIERTEL: true,
  STANDARD_START: 8,   // früheste angezeigte Stunde
  STANDARD_ENDE: 18,   // späteste angezeigte Stunde
  THEMA: "auto",       // "auto" | "hell" | "dunkel"
  TAP_URL: "https://planner.joshuan.dev/de", // öffnet beim Tippen aufs Widget ("" = Scriptable öffnen)

  // Kurznamen und Farben pro Veranstaltung (vollständiger Titel aus dem Kalender)
  KURSE: {
    "Grundlagen der Programmierung":               { kurz: "GdP",      farbe: "#378ADD" },
    "Grundlagen der Programmierung (Rechnerübung)": { kurz: "GdP-RÜ",   farbe: "#185FA5" },
    "Mathematik für Informatik I":                 { kurz: "Mathe I",  farbe: "#1D9E75" },
    "Mathematik für Informatik II":                { kurz: "Mathe II", farbe: "#639922" },
    "Maschinenmodelle":                            { kurz: "MaMo",     farbe: "#D85A30" },
    "Bioinformatik":                               { kurz: "Bioinf",   farbe: "#7F77DD" },
    "Molekulare Evolutionsbiologie":               { kurz: "MolEvo",   farbe: "#D4537E" },
  },
  // Farben für Veranstaltungen, die oben nicht eingetragen sind
  ERSATZ_FARBEN: ["#BA7517", "#993556", "#0F6E56", "#534AB7", "#5F5E5A"],

  // Veranstaltungsart: lange Bezeichnung (breite Blöcke) und Kürzel (schmale Blöcke)
  TYPEN: {
    "Vorlesung":   { lang: "Vorlesung",   kurz: "VL" },
    "Übung":       { lang: "Übung",       kurz: "Ü" },
    "Zusatzübung": { lang: "Zusatzübung", kurz: "ZÜ" },
    "Tutorium":    { lang: "Tutorium",    kurz: "Tut" },
    "Seminar":     { lang: "Seminar",     kurz: "Sem" },
    "Praktikum":   { lang: "Praktikum",   kurz: "Prak" },
  },
}

// Farben, die im hellen und dunklen Modus funktionieren
const FARBEN = {
  hell:   { bg: "#FFFFFF", text: "#2C2C2A", gedimmt: "#888780" },
  dunkel: { bg: "#1C1C1E", text: "#F1EFE8", gedimmt: "#8E8D86" },
  raster: "#888780",
  heute: "#185FA5",
  jetzt: "#E24B4A",
}

const WOCHENTAGE = ["Mo", "Di", "Mi", "Do", "Fr"]
const WOCHENTAGE_LANG = ["So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"]
const WOCHENTAGE_VOLL = ["Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"]
const MONATE = ["Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"]

// ───────────────────────── Eingebettete Termine ─────────────────────────
// Format: Start (UTC) | Ende (UTC) | Kurs-Index | Typ-Index | Raum

const EINGEBETTET_KURSE = ["Bioinformatik", "Grundlagen der Programmierung", "Grundlagen der Programmierung (Rechnerübung)", "Maschinenmodelle", "Mathematik für Informatik I", "Mathematik für Informatik II", "Molekulare Evolutionsbiologie"]
const EINGEBETTET_TYPEN = ["Tutorium", "Vorlesung", "Übung"]
const EINGEBETTET = `
20261012T1000Z|20261012T1200Z|3|1|2.27.0.01
20261012T1200Z|20261012T1400Z|1|1|2.12.0.01
20261013T0600Z|20261013T0800Z|2|2|2.70.0.01
20261013T0800Z|20261013T1000Z|4|2|2.70.0.10
20261013T1200Z|20261013T1400Z|5|1|2.28.0.108
20261013T1400Z|20261013T1600Z|4|1|2.27.1.01
20261014T0600Z|20261014T0800Z|1|2|2.70.0.05
20261014T0800Z|20261014T1000Z|5|2|2.70.0.10
20261014T1015Z|20261014T1100Z|6|1|2.25.F0.01
20261015T0715Z|20261015T0800Z|0|2|2.70.0.01
20261015T1000Z|20261015T1200Z|3|2|2.70.0.10
20261015T1200Z|20261015T1400Z|3|0|2.70.0.11
20261016T0615Z|20261016T0745Z|0|1|2.25.F0.01
20261019T1000Z|20261019T1200Z|3|1|2.27.0.01
20261019T1200Z|20261019T1400Z|1|1|2.12.0.01
20261020T0600Z|20261020T0800Z|2|2|2.70.0.01
20261020T0800Z|20261020T1000Z|4|2|2.70.0.10
20261020T1200Z|20261020T1400Z|5|1|2.28.0.108
20261020T1400Z|20261020T1600Z|4|1|2.27.1.01
20261021T0600Z|20261021T0800Z|1|2|2.70.0.05
20261021T0800Z|20261021T1000Z|5|2|2.70.0.10
20261021T1015Z|20261021T1100Z|6|1|2.25.F0.01
20261022T0715Z|20261022T0800Z|0|2|2.70.0.01
20261022T1000Z|20261022T1200Z|3|2|2.70.0.10
20261022T1200Z|20261022T1400Z|3|0|2.70.0.11
20261023T0615Z|20261023T0745Z|0|1|2.25.F0.01
20261026T1100Z|20261026T1300Z|1|1|2.27.0.01
20261026T1300Z|20261026T1500Z|3|1|2.12.0.01
20261027T0700Z|20261027T0900Z|2|2|2.70.0.01
20261027T0900Z|20261027T1100Z|4|2|2.70.0.10
20261027T1300Z|20261027T1500Z|5|1|2.28.0.108
20261027T1500Z|20261027T1700Z|4|1|2.27.1.01
20261028T0700Z|20261028T0900Z|1|2|2.70.0.05
20261028T0900Z|20261028T1100Z|5|2|2.70.0.10
20261028T1115Z|20261028T1200Z|6|1|2.25.F0.01
20261029T0815Z|20261029T0900Z|0|2|2.70.0.01
20261029T1100Z|20261029T1300Z|3|2|2.70.0.10
20261029T1300Z|20261029T1500Z|3|0|2.70.0.11
20261030T0715Z|20261030T0845Z|0|1|2.25.F0.01
20261102T1100Z|20261102T1300Z|3|1|2.27.0.01
20261102T1300Z|20261102T1500Z|1|1|2.12.0.01
20261103T0700Z|20261103T0900Z|2|2|2.70.0.01
20261103T0900Z|20261103T1100Z|4|2|2.70.0.10
20261103T1300Z|20261103T1500Z|5|1|2.28.0.108
20261103T1500Z|20261103T1700Z|4|1|2.27.1.01
20261104T0700Z|20261104T0900Z|1|2|2.70.0.10
20261104T0900Z|20261104T1100Z|5|2|2.70.0.10
20261104T1115Z|20261104T1200Z|6|1|2.25.F0.01
20261105T0815Z|20261105T0900Z|0|2|2.70.0.01
20261105T1100Z|20261105T1300Z|3|2|2.70.0.10
20261105T1300Z|20261105T1500Z|3|0|2.70.0.11
20261106T0715Z|20261106T0845Z|0|1|2.25.F0.01
20261109T1100Z|20261109T1300Z|3|1|2.27.0.01
20261109T1300Z|20261109T1500Z|1|1|2.12.0.01
20261110T0700Z|20261110T0900Z|2|2|2.70.0.01
20261110T0900Z|20261110T1100Z|4|2|2.70.0.10
20261110T1300Z|20261110T1500Z|5|1|2.28.0.108
20261110T1500Z|20261110T1700Z|4|1|2.27.1.01
20261111T0700Z|20261111T0900Z|1|2|2.70.0.10
20261111T0900Z|20261111T1100Z|5|2|2.70.0.10
20261112T0815Z|20261112T0900Z|0|2|2.70.0.01
20261112T1100Z|20261112T1300Z|3|2|2.70.0.10
20261112T1300Z|20261112T1500Z|3|0|2.70.0.11
20261113T0715Z|20261113T0845Z|0|1|2.25.F0.01
20261116T1100Z|20261116T1300Z|3|1|2.27.0.01
20261116T1300Z|20261116T1500Z|1|1|2.12.0.01
20261117T0700Z|20261117T0900Z|2|2|2.70.0.01
20261117T0900Z|20261117T1100Z|4|2|2.70.0.10
20261117T1300Z|20261117T1500Z|5|1|2.28.0.108
20261117T1500Z|20261117T1700Z|4|1|2.27.1.01
20261118T0700Z|20261118T0900Z|1|2|2.70.0.10
20261118T0900Z|20261118T1100Z|5|2|2.70.0.10
20261118T1115Z|20261118T1200Z|6|1|2.25.F0.01
20261119T0815Z|20261119T0900Z|0|2|2.70.0.01
20261119T1100Z|20261119T1300Z|3|2|2.70.0.10
20261119T1300Z|20261119T1500Z|3|0|2.70.0.11
20261120T0715Z|20261120T0845Z|0|1|2.25.F0.01
20261123T1100Z|20261123T1300Z|3|1|2.27.0.01
20261123T1300Z|20261123T1500Z|1|1|2.12.0.01
20261124T0700Z|20261124T0900Z|2|2|2.70.0.01
20261124T0900Z|20261124T1100Z|4|2|2.70.0.10
20261124T1300Z|20261124T1500Z|5|1|2.28.0.108
20261124T1500Z|20261124T1700Z|4|1|2.27.1.01
20261125T0700Z|20261125T0900Z|1|2|2.70.0.10
20261125T0900Z|20261125T1100Z|5|2|2.70.0.10
20261125T1115Z|20261125T1200Z|6|1|2.25.F0.01
20261126T0815Z|20261126T0900Z|0|2|2.70.0.01
20261126T1100Z|20261126T1300Z|3|2|2.70.0.10
20261126T1300Z|20261126T1500Z|3|0|2.70.0.11
20261127T0715Z|20261127T0845Z|0|1|2.25.F0.01
20261130T1100Z|20261130T1300Z|3|1|2.27.0.01
20261130T1300Z|20261130T1500Z|1|1|2.12.0.01
20261201T0700Z|20261201T0900Z|2|2|2.70.0.01
20261201T0900Z|20261201T1100Z|4|2|2.70.0.10
20261201T1300Z|20261201T1500Z|5|1|2.28.0.108
20261201T1500Z|20261201T1700Z|4|1|2.27.1.01
20261202T0700Z|20261202T0900Z|1|2|2.70.0.10
20261202T0900Z|20261202T1100Z|5|2|2.70.0.10
20261202T1115Z|20261202T1200Z|6|1|2.25.F0.01
20261203T0815Z|20261203T0900Z|0|2|2.70.0.01
20261203T1100Z|20261203T1300Z|3|2|2.70.0.10
20261203T1300Z|20261203T1500Z|3|0|2.70.0.11
20261204T0715Z|20261204T0845Z|0|1|2.25.F0.01
20261207T1100Z|20261207T1300Z|3|1|2.27.0.01
20261207T1300Z|20261207T1500Z|1|1|2.12.0.01
20261208T0700Z|20261208T0900Z|2|2|2.70.0.01
20261208T0900Z|20261208T1100Z|4|2|2.70.0.10
20261208T1300Z|20261208T1500Z|5|1|2.28.0.108
20261208T1500Z|20261208T1700Z|4|1|2.27.1.01
20261209T0700Z|20261209T0900Z|1|2|2.70.0.10
20261209T0900Z|20261209T1100Z|5|2|2.70.0.10
20261209T1115Z|20261209T1200Z|6|1|2.25.F0.01
20261210T0815Z|20261210T0900Z|0|2|2.70.0.01
20261210T1100Z|20261210T1300Z|3|2|2.70.0.10
20261210T1300Z|20261210T1500Z|3|0|2.70.0.11
20261211T0715Z|20261211T0845Z|0|1|2.25.F0.01
20261214T1100Z|20261214T1300Z|3|1|2.27.0.01
20261214T1300Z|20261214T1500Z|1|1|2.12.0.01
20261215T0700Z|20261215T0900Z|2|2|2.70.0.01
20261215T0900Z|20261215T1100Z|4|2|2.70.0.10
20261215T1300Z|20261215T1500Z|5|1|2.28.0.108
20261215T1500Z|20261215T1700Z|4|1|2.27.1.01
20261216T0700Z|20261216T0900Z|1|2|2.70.0.10
20261216T0900Z|20261216T1100Z|5|2|2.70.0.10
20261216T1115Z|20261216T1200Z|6|1|2.25.F0.01
20261217T0815Z|20261217T0900Z|0|2|2.70.0.01
20261217T1100Z|20261217T1300Z|3|2|2.70.0.10
20261217T1300Z|20261217T1500Z|3|0|2.70.0.11
20261218T0715Z|20261218T0845Z|0|1|2.25.F0.01
20270104T1100Z|20270104T1300Z|3|1|2.27.0.01
20270104T1300Z|20270104T1500Z|1|1|2.12.0.01
20270105T0700Z|20270105T0900Z|2|2|2.70.0.01
20270105T0900Z|20270105T1100Z|4|2|2.70.0.10
20270105T1300Z|20270105T1500Z|5|1|2.28.0.108
20270105T1500Z|20270105T1700Z|4|1|2.27.1.01
20270106T0700Z|20270106T0900Z|1|2|2.70.0.10
20270106T0900Z|20270106T1100Z|5|2|2.70.0.10
20270106T1115Z|20270106T1200Z|6|1|2.25.F0.01
20270107T0815Z|20270107T0900Z|0|2|2.70.0.01
20270107T1100Z|20270107T1300Z|3|2|2.70.0.10
20270107T1300Z|20270107T1500Z|3|0|2.70.0.11
20270108T0715Z|20270108T0845Z|0|1|2.25.F0.01
20270111T1100Z|20270111T1300Z|3|1|2.27.0.01
20270111T1300Z|20270111T1500Z|1|1|2.12.0.01
20270112T0700Z|20270112T0900Z|2|2|2.70.0.01
20270112T0900Z|20270112T1100Z|4|2|2.70.0.10
20270112T1300Z|20270112T1500Z|5|1|2.28.0.108
20270112T1500Z|20270112T1700Z|4|1|2.27.1.01
20270113T0700Z|20270113T0900Z|1|2|2.70.0.10
20270113T0900Z|20270113T1100Z|5|2|2.70.0.10
20270113T1115Z|20270113T1200Z|6|1|2.25.F0.01
20270114T0815Z|20270114T0900Z|0|2|2.70.0.01
20270114T1100Z|20270114T1300Z|3|2|2.70.0.10
20270114T1300Z|20270114T1500Z|3|0|2.70.0.11
20270115T0715Z|20270115T0845Z|0|1|2.25.F0.01
20270118T1100Z|20270118T1300Z|3|1|2.27.0.01
20270118T1300Z|20270118T1500Z|1|1|2.12.0.01
20270119T0700Z|20270119T0900Z|2|2|2.70.0.01
20270119T0900Z|20270119T1100Z|4|2|2.70.0.10
20270119T1300Z|20270119T1500Z|5|1|2.28.0.108
20270119T1500Z|20270119T1700Z|4|1|2.27.1.01
20270120T0700Z|20270120T0900Z|1|2|2.70.0.10
20270120T0900Z|20270120T1100Z|5|2|2.70.0.10
20270120T1115Z|20270120T1200Z|6|1|2.25.F0.01
20270121T0815Z|20270121T0900Z|0|2|2.70.0.01
20270121T1100Z|20270121T1300Z|3|2|2.70.0.10
20270121T1300Z|20270121T1500Z|3|0|2.70.0.11
20270122T0715Z|20270122T0845Z|0|1|2.25.F0.01
20270125T1100Z|20270125T1300Z|3|1|2.27.0.01
20270125T1300Z|20270125T1500Z|1|1|2.12.0.01
20270126T0700Z|20270126T0900Z|2|2|2.70.0.01
20270126T0900Z|20270126T1100Z|4|2|2.70.0.10
20270126T1300Z|20270126T1500Z|5|1|2.28.0.108
20270126T1500Z|20270126T1700Z|4|1|2.27.1.01
20270127T0700Z|20270127T0900Z|1|2|2.70.0.10
20270127T0900Z|20270127T1100Z|5|2|2.70.0.10
20270127T1115Z|20270127T1200Z|6|1|2.25.F0.01
20270128T0815Z|20270128T0900Z|0|2|2.70.0.01
20270128T1100Z|20270128T1300Z|3|2|2.70.0.10
20270128T1300Z|20270128T1500Z|3|0|2.70.0.11
20270129T0715Z|20270129T0845Z|0|1|2.25.F0.01
20270201T1100Z|20270201T1300Z|3|1|2.27.0.01
20270201T1300Z|20270201T1500Z|1|1|2.12.0.01
20270202T0700Z|20270202T0900Z|2|2|2.70.0.01
20270202T0900Z|20270202T1100Z|4|2|2.70.0.10
20270202T1300Z|20270202T1500Z|5|1|2.28.0.108
20270202T1500Z|20270202T1700Z|4|1|2.27.1.01
20270203T0700Z|20270203T0900Z|1|2|2.70.0.10
20270203T0900Z|20270203T1100Z|5|2|2.70.0.10
20270203T1115Z|20270203T1200Z|6|1|2.25.F0.01
20270204T0815Z|20270204T0900Z|0|2|2.70.0.01
20270204T1100Z|20270204T1300Z|3|2|2.70.0.10
20270204T1300Z|20270204T1500Z|3|0|2.70.0.11
20270205T0715Z|20270205T0845Z|0|1|2.25.F0.01
`

// ───────────────────────── Daten laden ─────────────────────────

async function ladeTermine() {
  const kandidaten = []
  try { kandidaten.push(FileManager.iCloud()) } catch (e) {}
  kandidaten.push(FileManager.local())
  for (const fm of kandidaten) {
    try {
      const pfad = fm.joinPath(fm.documentsDirectory(), CONFIG.ICS_DATEI)
      if (!fm.fileExists(pfad)) continue
      if (fm.isFileStoredIniCloud(pfad) && !fm.isFileDownloaded(pfad)) {
        await fm.downloadFileFromiCloud(pfad)
      }
      const termine = parseICS(fm.readString(pfad))
      if (termine.length > 0) return termine
    } catch (e) {
      console.log("ICS-Datei konnte nicht gelesen werden: " + e)
    }
  }
  return eingebetteteTermine()
}

function utcAusKompakt(s) {
  // 20261012T1200Z
  return new Date(Date.UTC(+s.slice(0, 4), +s.slice(4, 6) - 1, +s.slice(6, 8), +s.slice(9, 11), +s.slice(11, 13)))
}

function eingebetteteTermine() {
  return EINGEBETTET.trim().split("\n").map(z => {
    const [s, e, k, t, raum] = z.split("|")
    return {
      start: utcAusKompakt(s),
      ende: utcAusKompakt(e),
      titel: EINGEBETTET_KURSE[+k],
      typ: EINGEBETTET_TYPEN[+t],
      raum: raum,
    }
  })
}

function icsDatum(wert, params) {
  if (/VALUE=DATE(?!-)/.test(params) || wert.length === 8) return null // ganztägig → ignorieren
  const m = wert.match(/^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})?(Z)?$/)
  if (!m) return null
  const [, j, mo, t, h, mi, s, z] = m
  if (z) return new Date(Date.UTC(+j, +mo - 1, +t, +h, +mi, +(s || 0)))
  return new Date(+j, +mo - 1, +t, +h, +mi, +(s || 0)) // lokale Zeit des iPhones
}

function icsText(s) {
  return (s || "").replace(/\\n/gi, " ").replace(/\\,/g, ",").replace(/\\;/g, ";").replace(/\\\\/g, "\\").trim()
}

function typErkennen(beschreibung, titel) {
  const erster = (beschreibung || "").split("·")[0].trim()
  if (CONFIG.TYPEN[erster]) return erster
  const alles = (beschreibung + " " + titel).toLowerCase()
  for (const typ of ["Zusatzübung", "Tutorium", "Übung", "Vorlesung", "Seminar", "Praktikum"]) {
    if (alles.includes(typ.toLowerCase())) return typ
  }
  return erster
}

function parseICS(text) {
  const zeilen = text.replace(/\r\n/g, "\n").replace(/\n[ \t]/g, "").split("\n")
  const termine = []
  let ev = null
  for (const zeile of zeilen) {
    if (zeile === "BEGIN:VEVENT") { ev = {}; continue }
    if (zeile === "END:VEVENT") {
      if (ev && ev.start && ev.ende) {
        termine.push({
          start: ev.start,
          ende: ev.ende,
          titel: ev.titel || "Termin",
          typ: typErkennen(ev.beschreibung, ev.titel || ""),
          raum: ev.raum || "",
        })
      }
      ev = null
      continue
    }
    if (!ev) continue
    const doppelpunkt = zeile.indexOf(":")
    if (doppelpunkt < 0) continue
    const kopf = zeile.slice(0, doppelpunkt)
    const wert = zeile.slice(doppelpunkt + 1)
    const [name, ...params] = kopf.split(";")
    const p = params.join(";")
    if (name === "DTSTART") ev.start = icsDatum(wert, p)
    else if (name === "DTEND") ev.ende = icsDatum(wert, p)
    else if (name === "SUMMARY") ev.titel = icsText(wert)
    else if (name === "DESCRIPTION") ev.beschreibung = icsText(wert)
    else if (name === "LOCATION") ev.raum = icsText(wert)
  }
  return termine
}

// ───────────────────────── Hilfsfunktionen ─────────────────────────

function viertelAnpassen(t) {
  const dauer = (t.ende - t.start) / 60000
  if (CONFIG.AKADEMISCHES_VIERTEL && t.start.getMinutes() === 0 && t.ende.getMinutes() === 0 && dauer >= 90) {
    return Object.assign({}, t, {
      start: new Date(t.start.getTime() + 15 * 60000),
      ende: new Date(t.ende.getTime() - 15 * 60000),
    })
  }
  return t
}

const ersatzZuordnung = {}
function kursInfo(titel) {
  if (CONFIG.KURSE[titel]) return CONFIG.KURSE[titel]
  if (!ersatzZuordnung[titel]) {
    const n = Object.keys(ersatzZuordnung).length
    const kurz = titel.length > 10 ? titel.split(/\s+/).map(w => w[0]).join("").slice(0, 6) : titel
    ersatzZuordnung[titel] = { kurz: kurz, farbe: CONFIG.ERSATZ_FARBEN[n % CONFIG.ERSATZ_FARBEN.length] }
  }
  return ersatzZuordnung[titel]
}

function typInfo(typ) {
  return CONFIG.TYPEN[typ] || { lang: typ, kurz: typ.slice(0, 3) }
}

function stunden(d) {
  return d.getHours() + d.getMinutes() / 60
}

function uhrzeit(d) {
  return d.getHours() + ":" + String(d.getMinutes()).padStart(2, "0")
}

function kalenderwoche(d) {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()))
  const tag = t.getUTCDay() || 7
  t.setUTCDate(t.getUTCDate() + 4 - tag)
  const jahresStart = new Date(Date.UTC(t.getUTCFullYear(), 0, 1))
  return Math.ceil(((t - jahresStart) / 86400000 + 1) / 7)
}

function gleicherTag(a, b) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

// Ordnet überlappende Termine eines Tages nebeneinander an
function spaltenZuweisen(termine) {
  const sortiert = termine.slice().sort((a, b) => a.start - b.start || b.ende - a.ende)
  let gruppe = []
  let gruppenEnde = 0
  const fertig = []
  const abschliessen = () => {
    const spalten = []
    for (const t of gruppe) {
      let i = spalten.findIndex(ende => ende <= t.start)
      if (i < 0) { i = spalten.length; spalten.push(0) }
      spalten[i] = t.ende
      t.spalte = i
    }
    for (const t of gruppe) t.spalten = spalten.length
    // Exakt gleichzeitige Termine werden übereinander gestapelt (volle Breite, besser lesbar)
    const gleichzeitig = gruppe.length > 1 && gruppe.every(t =>
      t.start.getTime() === gruppe[0].start.getTime() && t.ende.getTime() === gruppe[0].ende.getTime())
    gruppe.forEach((t, i) => {
      if (gleichzeitig) { t.spalte = 0; t.spalten = 1; t.stapel = i; t.stapelAnzahl = gruppe.length }
      else { t.stapel = 0; t.stapelAnzahl = 1 }
    })
    fertig.push(...gruppe)
    gruppe = []
  }
  for (const t of sortiert) {
    if (gruppe.length && t.start >= gruppenEnde) abschliessen()
    gruppe.push(t)
    gruppenEnde = Math.max(gruppenEnde, t.ende.getTime())
  }
  if (gruppe.length) abschliessen()
  return fertig
}

// Größe des Widgets je nach Gerät (in Punkten)
function widgetGroesse(familie) {
  const s = Device.screenSize()
  const kurz = Math.round(Math.min(s.width, s.height))
  const lang = Math.round(Math.max(s.width, s.height))

  if (Device.isPad()) {
    // iPad: [klein, mittel-/groß-Breite, extragroß-Breite] laut Apple (Gerätegrößen)
    const ipad = {
      "768x1024": [120, 260, 540], "744x1133": [120, 260, 540], "810x1080": [124, 272, 568],
      "820x1180": [136, 300, 628], "834x1112": [132, 288, 600], "834x1194": [136, 300, 628],
      "834x1210": [136, 300, 628], "954x1373": [162, 350, 726], "970x1389": [162, 350, 726],
      "1024x1366": [160, 356, 748], "1032x1376": [160, 356, 748], "1192x1590": [188, 412, 860],
    }
    let g = ipad[kurz + "x" + lang]
    if (!g) g = kurz >= 1000 ? ipad["1024x1366"] : kurz >= 900 ? ipad["954x1373"] : kurz >= 820 ? ipad["834x1194"] : kurz >= 800 ? ipad["810x1080"] : ipad["744x1133"]
    if (familie === "small") return { breite: g[0], hoehe: g[0] }
    if (familie === "medium") return { breite: g[1], hoehe: g[0] }
    if (familie === "extraLarge") return { breite: g[2], hoehe: g[1] }
    return { breite: g[1], hoehe: g[1] }
  }

  // iPhone: [Breite groß/mittel, Höhe groß, Kantenlänge klein]
  const tabelle = {
    "440x956": [364, 382, 170], "430x932": [364, 382, 170], "428x926": [364, 382, 170],
    "414x896": [360, 379, 169], "402x874": [338, 354, 158], "393x852": [338, 354, 158],
    "390x844": [338, 354, 158], "375x812": [329, 345, 155], "414x736": [348, 357, 159],
    "375x667": [321, 324, 148], "320x568": [292, 311, 141],
  }
  const g = tabelle[kurz + "x" + lang] || [338, 354, 158]
  if (familie === "small") return { breite: g[2], hoehe: g[2] }
  if (familie === "medium") return { breite: g[0], hoehe: g[2] }
  return { breite: g[0], hoehe: g[1] }
}

// ───────────────────────── Zeichnen ─────────────────────────

function text(ctx, inhalt, rect, font, farbe, ausrichtung) {
  ctx.setFont(font)
  ctx.setTextColor(farbe)
  if (ausrichtung === "mitte") ctx.setTextAlignedCenter()
  else if (ausrichtung === "rechts") ctx.setTextAlignedRight()
  else ctx.setTextAlignedLeft()
  ctx.drawTextInRect(inhalt, rect)
}

function rundesRechteck(ctx, rect, radius, farbe) {
  const p = new Path()
  p.addRoundedRect(rect, radius, radius)
  ctx.addPath(p)
  ctx.setFillColor(farbe)
  ctx.fillPath()
}

function linie(ctx, x1, y1, x2, y2, farbe, staerke) {
  const p = new Path()
  p.move(new Point(x1, y1))
  p.addLine(new Point(x2, y2))
  ctx.addPath(p)
  ctx.setStrokeColor(farbe)
  ctx.setLineWidth(staerke)
  ctx.strokePath()
}

function zeichneBlock(ctx, t, x, y, b, h) {
  const kurs = kursInfo(t.titel)
  const typ = typInfo(t.typ)
  rundesRechteck(ctx, new Rect(x, y, b, h), 5, new Color(kurs.farbe))

  const weiss = Color.white()
  const weissGedimmt = new Color("#FFFFFF", 0.85)
  const schmal = b < 40   // nebeneinanderliegende Termine
  const kurz = h < 34     // z. B. 45 Minuten
  const kompakt = h < 40  // z. B. 90 Minuten auf dem iPad (flachere Widgets)

  if (kurz) {
    // Kurzer Termin: Titel, darunter Art · Raum
    const pad = 2
    const innenB = b - 2 * pad
    text(ctx, kurs.kurz, new Rect(x + pad, y + 1, innenB, 11), Font.boldSystemFont(9), weiss)
    if (h >= 16) {
      // Raum ist wichtiger als die Art – bei wenig Platz steht hier nur der Raum
      text(ctx, t.raum, new Rect(x + pad, y + 9.5, innenB, 9), Font.mediumSystemFont(7.5), weissGedimmt)
    }
    return
  }

  if (kompakt && !schmal) {
    // Wenig Höhe: Titel, darunter Art und Raum in einer Zeile
    const pad = 3
    const innenB = b - 2 * pad
    const art = b >= 80 ? typ.lang : typ.kurz
    text(ctx, kurs.kurz, new Rect(x + pad, y + pad - 0.5, innenB, 12), Font.boldSystemFont(10), weiss)
    // Raum zuerst, damit er bei Platzmangel nicht abgeschnitten wird
    text(ctx, t.raum + "\u00A0·\u00A0" + art, new Rect(x + pad, y + pad + 11.5, innenB, 9), Font.mediumSystemFont(7.5), weissGedimmt)
    if (h >= 36) {
      // Bei etwas mehr Platz: Uhrzeit als dritte Zeile
      text(ctx, uhrzeit(t.start) + "–" + uhrzeit(t.ende), new Rect(x + pad, y + pad + 20.5, innenB, 9), Font.mediumSystemFont(7.5), weissGedimmt)
    }
    return
  }

  if (schmal) {
    // Schmaler Block: Kürzel der Art, Titel und Raum dürfen umbrechen
    const pad = 2.5
    const innenB = b - 2 * pad
    let cy = y + pad - 0.5
    const unten = y + h - pad + 1.5
    const kleinFont = Font.mediumSystemFont(7)
    const kleinH = 8.5
    const titelFont = Font.boldSystemFont(8.5)
    const titelH = 10
    text(ctx, typ.kurz, new Rect(x + pad, cy, innenB, kleinH), kleinFont, weissGedimmt)
    cy += kleinH
    const titelZeilen = kurs.kurz.includes(" ") || kurs.kurz.includes("-") ? 2 : 1
    const titelUmbruch = kurs.kurz.replace(/-/, "-\n").replace(/ /, "\n")
    const titelHoehe = Math.min(titelZeilen * titelH, unten - cy - kleinH)
    text(ctx, titelZeilen === 2 ? titelUmbruch : kurs.kurz, new Rect(x + pad, cy, innenB, titelHoehe), titelFont, weiss)
    cy += titelHoehe
    const raumUmbruch = t.raum.replace(/^([^.]+\.[^.]+\.)/, "$1\n")
    if (unten - cy >= kleinH) {
      text(ctx, raumUmbruch, new Rect(x + pad, cy, innenB, unten - cy), kleinFont, weissGedimmt)
    }
    return
  }

  // Normaler Block: Art · Titel · Raum
  const pad = 3.5
  const innenB = b - 2 * pad
  const titelFont = Font.boldSystemFont(10)
  const titelH = 12
  const kleinFont = Font.mediumSystemFont(7.5)
  const kleinH = 9
  let verfuegbar = h - 2 * pad + 1.5
  let cy = y + pad - 0.5
  text(ctx, typ.lang, new Rect(x + pad, cy, innenB, kleinH), kleinFont, weissGedimmt)
  cy += kleinH
  verfuegbar -= kleinH
  const titelZeilen = Math.max(1, Math.min(2, Math.floor((verfuegbar - kleinH) / titelH)))
  text(ctx, kurs.kurz, new Rect(x + pad, cy, innenB, titelZeilen * titelH), titelFont, weiss)
  cy += titelZeilen * titelH
  verfuegbar -= titelZeilen * titelH
  if (verfuegbar >= kleinH) {
    text(ctx, t.raum, new Rect(x + pad, cy, innenB, Math.floor(verfuegbar / kleinH) * kleinH), kleinFont, weissGedimmt)
  }
}

function zeichneRaster(breite, hoehe, tage, startH, endeH, jetzt, mitTagesDatum) {
  const ctx = new DrawContext()
  ctx.size = new Size(breite, hoehe)
  ctx.opaque = false
  ctx.respectScreenScale = true

  const achse = 20
  const kopf = 17
  const luecke = 3
  const spaltenB = (breite - achse - luecke * (tage.length - 1)) / tage.length
  const rasterH = hoehe - kopf - 1
  const yVon = h => kopf + (h - startH) / (endeH - startH) * rasterH
  const rasterFarbe = new Color(FARBEN.raster, 0.28)
  const beschriftung = new Color(FARBEN.raster)

  // Stundenlinien und Beschriftung
  for (let h = startH; h <= endeH; h++) {
    const y = yVon(h)
    const gerade = (h - startH) % 2 === 0
    linie(ctx, achse, y, breite, y, gerade ? rasterFarbe : new Color(FARBEN.raster, 0.12), 0.5)
    if (gerade) text(ctx, String(h), new Rect(0, Math.min(y - 6, hoehe - 12), achse - 4, 12), Font.mediumSystemFont(9), beschriftung, "rechts")
  }

  tage.forEach((tag, i) => {
    const x = achse + i * (spaltenB + luecke)
    const heute = gleicherTag(tag.datum, jetzt)

    // Tageskopf
    const label = WOCHENTAGE[i] + (mitTagesDatum ? " " + tag.datum.getDate() + "." : "")
    if (heute) {
      rundesRechteck(ctx, new Rect(x + 2, 0, spaltenB - 4, 14), 7, new Color(FARBEN.heute))
      text(ctx, label, new Rect(x, 1, spaltenB, 13), Font.semiboldSystemFont(10), Color.white(), "mitte")
      rundesRechteck(ctx, new Rect(x, kopf, spaltenB, rasterH), 6, new Color(FARBEN.raster, 0.12))
    } else {
      text(ctx, label, new Rect(x, 1, spaltenB, 13), Font.semiboldSystemFont(10), beschriftung, "mitte")
    }

    // Blöcke
    for (const t of spaltenZuweisen(tag.termine)) {
      const y1 = yVon(Math.max(startH, stunden(t.start)))
      const y2 = yVon(Math.min(endeH, stunden(t.ende)))
      const bSpalte = (spaltenB - (t.spalten - 1) * 2) / t.spalten
      const bx = x + t.spalte * (bSpalte + 2)
      const hStapel = (y2 - y1 - (t.stapelAnzahl - 1) * 2) / t.stapelAnzahl
      const by = y1 + t.stapel * (hStapel + 2)
      zeichneBlock(ctx, t, bx, by + 0.75, bSpalte, Math.max(8, hStapel - 1.5))
    }

    // Aktuelle Uhrzeit
    const jetztH = stunden(jetzt)
    if (heute && jetztH >= startH && jetztH <= endeH) {
      const y = yVon(jetztH)
      linie(ctx, x - 1, y, x + spaltenB, y, new Color(FARBEN.jetzt), 2)
      const p = new Path()
      p.addEllipse(new Rect(x - 4, y - 3.5, 7, 7))
      ctx.addPath(p)
      ctx.setFillColor(new Color(FARBEN.jetzt))
      ctx.fillPath()
    }
  })

  return ctx.getImage()
}

// ───────────────────────── Gemeinsame Widget-Bausteine ─────────────────────────

function themenFarben() {
  const teile = (args.widgetParameter || "").trim().toLowerCase().split(/\s+/)
  const thema = teile.includes("hell") ? "hell" : teile.includes("dunkel") ? "dunkel" : CONFIG.THEMA
  if (thema === "hell" || thema === "dunkel") {
    const f = FARBEN[thema]
    return {
      bg: new Color(f.bg),
      vorder: new Color(f.text),
      gedimmt: new Color(f.gedimmt),
      akzent: new Color(thema === "hell" ? "#185FA5" : "#85B7EB"),
    }
  }
  return {
    bg: Color.dynamic(new Color(FARBEN.hell.bg), new Color(FARBEN.dunkel.bg)),
    vorder: Color.dynamic(new Color(FARBEN.hell.text), new Color(FARBEN.dunkel.text)),
    gedimmt: Color.dynamic(new Color(FARBEN.hell.gedimmt), new Color(FARBEN.dunkel.gedimmt)),
    akzent: Color.dynamic(new Color("#185FA5"), new Color("#85B7EB")),
  }
}

function basisWidget(farben, rand) {
  const w = new ListWidget()
  w.backgroundColor = farben.bg
  w.setPadding(rand, rand, rand, rand)
  const link = tippLink()
  if (link) w.url = link
  return w
}

// Link beim Tippen: Planer-Link aus dem Widget-Parameter, sonst CONFIG.TAP_URL
function tippLink() {
  const teile = (args.widgetParameter || "").trim().split(/\s+/)
  const eigener = teile.find(t => /^https?:\/\//i.test(t))
  return eigener || CONFIG.TAP_URL
}

function textHinzu(stack, inhalt, font, farbe, optionen) {
  const t = stack.addText(inhalt)
  t.font = font
  t.textColor = farbe
  t.lineLimit = (optionen && optionen.zeilen) || 1
  t.minimumScaleFactor = (optionen && optionen.skalierung) || 0.85
  if (optionen && optionen.deckkraft !== undefined) t.textOpacity = optionen.deckkraft
  return t
}

function aktualisierung(w, alle, jetzt) {
  // Nächste Aktualisierung: beim nächsten Beginn/Ende, spätestens in 15 Minuten
  const grenzen = alle.flatMap(t => [t.start, t.ende]).filter(d => d > jetzt).sort((a, b) => a - b)
  const in15 = new Date(jetzt.getTime() + 15 * 60000)
  w.refreshAfterDate = grenzen.length && grenzen[0] < in15 ? grenzen[0] : in15
}

function tagesBeginn(d) {
  const x = new Date(d)
  x.setHours(0, 0, 0, 0)
  return x
}

function termineAm(alle, tag) {
  const bis = new Date(tag)
  bis.setDate(tag.getDate() + 1)
  return alle.filter(t => t.start >= tag && t.start < bis)
}

// Welcher Tag wird im kleinen/mittleren Widget gezeigt?
// Heute, solange noch etwas ansteht – danach der nächste Tag mit Terminen.
function tagesAuswahl(alle, jetzt) {
  const heute = tagesBeginn(jetzt)
  const heuteTermine = termineAm(alle, heute)
  if (heuteTermine.some(t => t.ende > jetzt)) {
    return { datum: heute, termine: heuteTermine, label: "Heute", istHeute: true }
  }
  const morgen = new Date(heute)
  morgen.setDate(heute.getDate() + 1)
  const naechster = alle.find(t => t.start >= morgen)
  if (!naechster) return null
  const tag = tagesBeginn(naechster.start)
  const label = gleicherTag(tag, morgen) ? "Morgen" : WOCHENTAGE_VOLL[tag.getDay()]
  return { datum: tag, termine: termineAm(alle, tag), label, istHeute: false }
}

function fokusTermin(auswahl, jetzt) {
  const t = auswahl.termine
  return t.find(x => x.start <= jetzt && x.ende > jetzt) || t.find(x => x.start > jetzt) || t[0]
}

function datumKurz(d) {
  return WOCHENTAGE_LANG[d.getDay()] + ", " + d.getDate() + ". " + MONATE[d.getMonth()]
}

function minutenText(min) {
  if (min < 60) return min + " Min."
  const h = Math.floor(min / 60)
  const m = min % 60
  return h + " Std." + (m ? " " + m + " Min." : "")
}

function fortschrittsBalken(breite, anteil) {
  const ctx = new DrawContext()
  ctx.size = new Size(breite, 4)
  ctx.opaque = false
  ctx.respectScreenScale = true
  rundesRechteck(ctx, new Rect(0, 0, breite, 4), 2, new Color("#FFFFFF", 0.3))
  rundesRechteck(ctx, new Rect(0, 0, Math.max(4, breite * anteil), 4), 2, Color.white())
  return ctx.getImage()
}

// Farbige Karte für die aktuelle bzw. nächste Veranstaltung
function karte(parent, t, auswahl, jetzt, breite, ausfuehrlich) {
  const kurs = kursInfo(t.titel)
  const typ = typInfo(t.typ)
  const weiss = Color.white()
  const k = parent.addStack()
  k.layoutVertically()
  k.backgroundColor = new Color(kurs.farbe)
  k.cornerRadius = 12
  k.setPadding(8, 10, 8, 10)
  k.size = new Size(breite, 0)

  const laufend = t.start <= jetzt && t.ende > jetzt
  const statusFont = Font.semiboldSystemFont(10)
  const zeile1 = k.addStack()
  zeile1.layoutHorizontally()
  // Countdown, den iOS selbst live herunterzählt (auch ohne Neuladen des Widgets)
  const countdown = (vorText, ziel) => {
    textHinzu(zeile1, vorText, statusFont, weiss, { deckkraft: 0.9, skalierung: 1 })
    zeile1.addSpacer(3)
    const d = zeile1.addDate(ziel)
    d.applyTimerStyle()
    d.font = Font.semiboldMonospacedSystemFont ? Font.semiboldMonospacedSystemFont(10) : statusFont
    d.textColor = weiss
    d.textOpacity = 0.9
    d.lineLimit = 1
    d.leftAlignText()
  }
  if (laufend) {
    countdown("Jetzt · noch", t.ende)
  } else if (auswahl.istHeute && t.start - jetzt <= 60 * 60000) {
    countdown("Beginnt in", t.start)
  } else {
    textHinzu(zeile1, auswahl.istHeute ? "Als Nächstes" : "Erster Termin", statusFont, weiss, { deckkraft: 0.9 })
    zeile1.addSpacer()
  }
  k.addSpacer(2)
  if (ausfuehrlich) {
    textHinzu(k, t.titel, Font.boldSystemFont(15), weiss, { zeilen: 2, skalierung: 0.8 })
  } else {
    textHinzu(k, kurs.kurz, Font.boldSystemFont(18), weiss, { skalierung: 0.6 })
  }
  k.addSpacer(2)
  textHinzu(k, typ.lang + " · " + t.raum, Font.mediumSystemFont(11), weiss, { deckkraft: 0.9, skalierung: 0.75 })
  textHinzu(k, uhrzeit(t.start) + "–" + uhrzeit(t.ende), Font.mediumSystemFont(11), weiss, { deckkraft: 0.9 })
  if (laufend) {
    k.addSpacer(5)
    const innen = breite - 20
    const anteil = (jetzt - t.start) / (t.ende - t.start)
    const bild = k.addImage(fortschrittsBalken(innen, anteil))
    bild.imageSize = new Size(innen, 4)
  }
  return k
}

function leerHinweis(w, farben, text) {
  w.addSpacer()
  const t = textHinzu(w, text, Font.mediumSystemFont(13), farben.gedimmt, { zeilen: 2 })
  t.centerAlignText()
  w.addSpacer()
}

// ───────────────────────── Kleines Widget (2×2) ─────────────────────────

function kleinesWidget(alle, jetzt, farben) {
  const groesse = widgetGroesse("small")
  const rand = 12
  const w = basisWidget(farben, rand)
  const auswahl = tagesAuswahl(alle, jetzt)
  if (!auswahl) { leerHinweis(w, farben, "Keine weiteren Termine"); return w }

  const kopf = w.addStack()
  kopf.layoutHorizontally()
  kopf.centerAlignContent()
  textHinzu(kopf, auswahl.label, Font.semiboldSystemFont(12), auswahl.istHeute ? farben.akzent : farben.vorder)
  kopf.addSpacer()
  textHinzu(kopf, auswahl.datum.getDate() + ". " + MONATE[auswahl.datum.getMonth()], Font.systemFont(11), farben.gedimmt)
  w.addSpacer(6)

  const fokus = fokusTermin(auswahl, jetzt)
  karte(w, fokus, auswahl, jetzt, groesse.breite - 2 * rand, false)
  w.addSpacer()

  const danach = auswahl.termine.find(t => t.start > fokus.start)
  const fuss = w.addStack()
  fuss.layoutHorizontally()
  fuss.centerAlignContent()
  if (danach) {
    textHinzu(fuss, "Danach", Font.systemFont(11), farben.gedimmt)
    fuss.addSpacer(4)
    textHinzu(fuss, uhrzeit(danach.start) + " " + kursInfo(danach.titel).kurz, Font.semiboldSystemFont(11), farben.vorder, { skalierung: 0.7 })
  } else {
    textHinzu(fuss, auswahl.istHeute ? "Danach frei" : "Sonst nichts an dem Tag", Font.systemFont(11), farben.gedimmt)
  }
  fuss.addSpacer()
  return w
}

// ───────────────────────── Mittleres Widget (4×2) ─────────────────────────

function mittleresWidget(alle, jetzt, farben) {
  const groesse = widgetGroesse("medium")
  const rand = 12
  const luecke = 12
  const w = basisWidget(farben, rand)
  const auswahl = tagesAuswahl(alle, jetzt)
  if (!auswahl) { leerHinweis(w, farben, "Keine weiteren Termine"); return w }

  const innenB = groesse.breite - 2 * rand
  const innenH = groesse.hoehe - 2 * rand
  const linksB = Math.floor((innenB - luecke) * 0.47)
  const rechtsB = innenB - luecke - linksB

  const zeile = w.addStack()
  zeile.layoutHorizontally()
  zeile.topAlignContent()

  // Links: Kopf und Karte
  const links = zeile.addStack()
  links.layoutVertically()
  links.size = new Size(linksB, innenH)
  tagesKopf(links, auswahl, farben)
  links.addSpacer(6)
  const fokus = fokusTermin(auswahl, jetzt)
  karte(links, fokus, auswahl, jetzt, linksB, true)
  links.addSpacer()

  zeile.addSpacer(luecke)

  // Rechts: Tagesplan
  const rechts = zeile.addStack()
  rechts.layoutVertically()
  rechts.size = new Size(rechtsB, innenH)
  const rKopf = rechts.addStack()
  rKopf.layoutHorizontally()
  const anzahl = auswahl.termine.length
  textHinzu(rKopf, anzahl + (anzahl === 1 ? " Termin" : " Termine"), Font.systemFont(11), farben.gedimmt)
  rKopf.addSpacer()
  rechts.addSpacer(6)
  tagesListe(rechts, auswahl, jetzt, farben, rechtsB, innenH - 20)
  rechts.addSpacer()
  return w
}

// Liste der Termine eines Tages (mittleres und extragroßes Widget)
function tagesListe(parent, auswahl, jetzt, farben, breite, verfuegbarH) {
  const zeilenH = 30
  const maxZeilen = Math.max(1, Math.floor(verfuegbarH / (zeilenH + 3)))
  let liste = auswahl.termine.slice()
  let mehr = 0
  if (liste.length > maxZeilen) {
    // Vergangene Termine zuerst ausblenden
    while (liste.length > maxZeilen && liste[0].ende <= jetzt) liste.shift()
    if (liste.length > maxZeilen) {
      mehr = liste.length - (maxZeilen - 1)
      liste = liste.slice(0, maxZeilen - 1)
    }
  }

  liste.forEach((t, i) => {
    const kurs = kursInfo(t.titel)
    const typ = typInfo(t.typ)
    const vorbei = t.ende <= jetzt
    const laufend = t.start <= jetzt && t.ende > jetzt
    const r = parent.addStack()
    r.layoutHorizontally()
    r.centerAlignContent()
    r.size = new Size(breite, zeilenH)
    r.setPadding(0, 0, 0, 4)
    if (laufend) {
      r.backgroundColor = new Color(kurs.farbe, 0.16)
      r.cornerRadius = 6
    }
    const balken = r.addStack()
    balken.size = new Size(4, zeilenH - 4)
    balken.backgroundColor = new Color(kurs.farbe, vorbei ? 0.35 : 1)
    balken.cornerRadius = 2
    r.addSpacer(7)
    const txt = r.addStack()
    txt.layoutVertically()
    const oben = txt.addStack()
    oben.layoutHorizontally()
    const d = vorbei ? 0.45 : 1
    textHinzu(oben, uhrzeit(t.start), Font.mediumSystemFont(12), farben.gedimmt, { deckkraft: d })
    oben.addSpacer(5)
    textHinzu(oben, kurs.kurz, Font.boldSystemFont(12), farben.vorder, { deckkraft: d, skalierung: 0.7 })
    textHinzu(txt, typ.lang + " · " + t.raum, Font.systemFont(10), farben.gedimmt, { deckkraft: d, skalierung: 0.75 })
    r.addSpacer()
    if (i < liste.length - 1 || mehr) parent.addSpacer(3)
  })
  if (mehr) {
    textHinzu(parent, "+ " + mehr + " weitere", Font.systemFont(10), farben.gedimmt)
  }
}

// Kopfzeile "Heute Di, 13. Okt" für mittleres und extragroßes Widget
function tagesKopf(parent, auswahl, farben) {
  const kopf = parent.addStack()
  kopf.layoutHorizontally()
  kopf.centerAlignContent()
  textHinzu(kopf, auswahl.label, Font.semiboldSystemFont(12), auswahl.istHeute ? farben.akzent : farben.vorder)
  kopf.addSpacer(5)
  const datumText = ["Heute", "Morgen"].includes(auswahl.label) ? datumKurz(auswahl.datum) : auswahl.datum.getDate() + ". " + MONATE[auswahl.datum.getMonth()]
  textHinzu(kopf, datumText, Font.systemFont(11), farben.gedimmt)
  kopf.addSpacer()
}

// ───────────────────────── Großes Widget (4×4) ─────────────────────────

function statusText(alle, jetzt) {
  const laufend = alle.find(t => t.start <= jetzt && t.ende > jetzt)
  if (laufend) {
    return "bis " + uhrzeit(laufend.ende) + " " + kursInfo(laufend.titel).kurz + " · " + laufend.raum
  }
  const naechster = alle.filter(t => t.start > jetzt).sort((a, b) => a.start - b.start)[0]
  if (!naechster) return "Keine weiteren Termine"
  const tag = gleicherTag(naechster.start, jetzt) ? "" : WOCHENTAGE_LANG[naechster.start.getDay()] + " "
  return "ab " + tag + uhrzeit(naechster.start) + " " + kursInfo(naechster.titel).kurz + " · " + naechster.raum
}

// Wochenansicht (Kopfzeile + Raster) in einen Stack zeichnen – für Groß und Extragroß
function wochenAnsicht(parent, alle, jetzt, farben, breite, hoehe, mitStatus) {
  // Angezeigte Woche: aktuelle Woche, am Wochenende die nächste
  const montag = new Date(jetzt)
  montag.setHours(0, 0, 0, 0)
  const wochentag = (montag.getDay() + 6) % 7
  montag.setDate(montag.getDate() - wochentag)
  const naechsteWoche = wochentag >= 5
  if (naechsteWoche) montag.setDate(montag.getDate() + 7)

  const tage = WOCHENTAGE.map((_, i) => {
    const datum = new Date(montag)
    datum.setDate(montag.getDate() + i)
    return { datum, termine: termineAm(alle, datum) }
  })

  // Angezeigter Zeitraum (wird erweitert, falls nötig)
  const wochenTermine = tage.flatMap(t => t.termine)
  let startH = CONFIG.STANDARD_START
  let endeH = CONFIG.STANDARD_ENDE
  for (const t of wochenTermine) {
    startH = Math.min(startH, Math.floor(stunden(t.start)))
    endeH = Math.max(endeH, Math.ceil(stunden(t.ende)))
  }

  const kopfH = 16
  const abstand = 5
  const kopf = parent.addStack()
  kopf.layoutHorizontally()
  kopf.centerAlignContent()
  kopf.size = new Size(breite, kopfH)
  const titel = naechsteWoche
    ? "Nächste Woche · KW " + kalenderwoche(montag)
    : datumKurz(jetzt) + " · KW " + kalenderwoche(jetzt)
  textHinzu(kopf, titel, Font.semiboldSystemFont(13), farben.vorder, { skalierung: 1 })
  kopf.addSpacer()
  if (mitStatus) textHinzu(kopf, statusText(alle, jetzt), Font.systemFont(11), farben.gedimmt, { skalierung: 0.8 })

  parent.addSpacer(abstand)

  const rasterH = hoehe - kopfH - abstand
  if (wochenTermine.length === 0) {
    parent.addSpacer()
    textHinzu(parent, "Keine Termine in dieser Woche", Font.mediumSystemFont(13), farben.gedimmt)
    parent.addSpacer()
  } else {
    const bild = parent.addImage(zeichneRaster(breite, rasterH, tage, startH, endeH, jetzt, true))
    bild.imageSize = new Size(breite, rasterH)
  }
}

function grossesWidget(alle, jetzt, farben) {
  const groesse = widgetGroesse("large")
  const rand = 11
  const w = basisWidget(farben, rand)
  wochenAnsicht(w, alle, jetzt, farben, groesse.breite - 2 * rand, groesse.hoehe - 2 * rand, true)
  return w
}

// ───────────────────────── Extragroßes Widget (nur iPad) ─────────────────────────

function extraGrossesWidget(alle, jetzt, farben) {
  const groesse = widgetGroesse("extraLarge")
  const rand = 12
  const luecke = 16
  const w = basisWidget(farben, rand)
  const innenB = groesse.breite - 2 * rand
  const innenH = groesse.hoehe - 2 * rand
  const rechtsB = Math.round(Math.min(250, innenB * 0.34))
  const linksB = innenB - luecke - rechtsB

  const zeile = w.addStack()
  zeile.layoutHorizontally()
  zeile.topAlignContent()

  // Links: Woche
  const links = zeile.addStack()
  links.layoutVertically()
  links.size = new Size(linksB, innenH)
  wochenAnsicht(links, alle, jetzt, farben, linksB, innenH, false)

  zeile.addSpacer(luecke)

  // Rechts: aktuelle/nächste Veranstaltung und Tagesplan
  const rechts = zeile.addStack()
  rechts.layoutVertically()
  rechts.size = new Size(rechtsB, innenH)
  const auswahl = tagesAuswahl(alle, jetzt)
  if (!auswahl) {
    rechts.addSpacer()
    textHinzu(rechts, "Keine weiteren Termine", Font.mediumSystemFont(13), farben.gedimmt)
    rechts.addSpacer()
    return w
  }
  tagesKopf(rechts, auswahl, farben)
  rechts.addSpacer(6)
  const fokus = fokusTermin(auswahl, jetzt)
  karte(rechts, fokus, auswahl, jetzt, rechtsB, true)
  rechts.addSpacer(12)
  const anzahl = auswahl.termine.length
  const lKopf = rechts.addStack()
  lKopf.layoutHorizontally()
  textHinzu(lKopf, (auswahl.istHeute ? "Heute · " : auswahl.label + " · ") + anzahl + (anzahl === 1 ? " Termin" : " Termine"), Font.systemFont(11), farben.gedimmt)
  lKopf.addSpacer()
  rechts.addSpacer(5)
  // Platz für die Liste: Gesamthöhe minus Kopf, Karte (ca. 112) und Abstände
  tagesListe(rechts, auswahl, jetzt, farben, rechtsB, innenH - 14 - 6 - 112 - 12 - 13 - 5)
  rechts.addSpacer()
  return w
}

// ───────────────────────── Sperrbildschirm (iPhone, iOS 16+) ─────────────────────────
// Varianten über den Widget-Parameter wählbar (siehe Kopf des Scripts).

function sperrVariante(erlaubt, standard) {
  const teile = (args.widgetParameter || "").trim().toLowerCase().split(/\s+/)
  return teile.find(t => erlaubt.includes(t)) || standard
}

function naechsterTermin(alle, jetzt) {
  return alle.find(t => t.start <= jetzt && t.ende > jetzt) || alle.find(t => t.start > jetzt) || null
}

// "14:15" (heute), "Morgen 8:15" oder "Mo 8:15"
function wannText(t, jetzt) {
  if (gleicherTag(t.start, jetzt)) return uhrzeit(t.start)
  const morgen = new Date(jetzt)
  morgen.setDate(morgen.getDate() + 1)
  if (gleicherTag(t.start, morgen)) return "Morgen " + uhrzeit(t.start)
  return WOCHENTAGE_LANG[t.start.getDay()] + " " + uhrzeit(t.start)
}

// "2.70.0.10" → { haus: "70", rest: "0.10" }
function raumTeile(raum) {
  const m = (raum || "").match(/^\d+\.([^.]+)\.(.+)$/)
  return m ? { haus: m[1], rest: m[2] } : { haus: raum || "–", rest: "" }
}

function sperrWidget() {
  const w = new ListWidget()
  const link = tippLink()
  if (link) w.url = link
  return w
}

function sperrText(stack, inhalt, font, deckkraft, optionen) {
  const t = stack.addText(inhalt)
  t.font = font
  t.textColor = Color.white()
  t.textOpacity = deckkraft === undefined ? 1 : deckkraft
  t.lineLimit = (optionen && optionen.zeilen) || 1
  t.minimumScaleFactor = (optionen && optionen.skalierung) || 0.7
  if (optionen && optionen.mitte) t.centerAlignText()
  return t
}

// Zentrierte Zeile (für runde Widgets)
function mittig(parent) {
  const z = parent.addStack()
  z.layoutHorizontally()
  z.addSpacer()
  const innen = z.addStack()
  innen.layoutVertically()
  z.addSpacer()
  return innen
}

function leerSperr(familie) {
  const w = sperrWidget()
  if (familie === "accessoryInline") { w.addText("Keine Termine"); return w }
  w.addAccessoryWidgetBackground = familie === "accessoryCircular"
  const z = mittig(w)
  sperrText(z, familie === "accessoryCircular" ? "–" : "Keine weiteren Termine", Font.semiboldSystemFont(13), 0.8, { mitte: true })
  return w
}

// ── Zeile über der Uhr ──
function sperrInline(alle, jetzt) {
  const t = naechsterTermin(alle, jetzt)
  if (!t) return leerSperr("accessoryInline")
  const variante = sperrVariante(["naechste", "raum", "countdown"], "naechste")
  const kurs = kursInfo(t.titel).kurz
  const laufend = t.start <= jetzt
  let text
  if (variante === "raum") {
    text = t.raum + " · " + kurs + (laufend ? " bis " + uhrzeit(t.ende) : " " + wannText(t, jetzt))
  } else if (variante === "countdown" && (laufend || gleicherTag(t.start, jetzt))) {
    const min = Math.ceil(((laufend ? t.ende : t.start) - jetzt) / 60000)
    text = kurs + (laufend ? " noch " : " in ") + minutenText(min)
  } else {
    text = laufend ? "bis " + uhrzeit(t.ende) + " · " + kurs + " · " + t.raum : wannText(t, jetzt) + " " + kurs + " · " + t.raum
  }
  const w = sperrWidget()
  const symbol = SFSymbol.named(laufend ? "clock" : "calendar")
  if (symbol) w.addImage(symbol.image)
  w.addText(text)
  return w
}

// ── Rechteckig unter der Uhr ──
function sperrRechteck(alle, jetzt) {
  const variante = sperrVariante(["naechste", "raum", "tag"], "naechste")
  const w = sperrWidget()
  w.setPadding(2, 4, 2, 4)

  if (variante === "tag") {
    // Die nächsten (bis zu 3) Termine des Tages
    const auswahl = tagesAuswahl(alle, jetzt)
    if (!auswahl) return leerSperr("accessoryRectangular")
    const rest = auswahl.termine.filter(t => t.ende > jetzt).slice(0, 3)
    sperrText(w, auswahl.label.toUpperCase(), Font.boldSystemFont(10), 0.7)
    for (const t of rest) {
      const z = w.addStack()
      z.layoutHorizontally()
      const laufend = t.start <= jetzt
      sperrText(z, uhrzeit(t.start), Font.semiboldMonospacedSystemFont ? Font.semiboldMonospacedSystemFont(12) : Font.semiboldSystemFont(12), laufend ? 1 : 0.8)
      z.addSpacer(5)
      sperrText(z, kursInfo(t.titel).kurz, Font.boldSystemFont(12), 1)
      z.addSpacer(4)
      sperrText(z, t.raum, Font.systemFont(12), 0.8)
      z.addSpacer()
    }
    w.addSpacer()
    return w
  }

  const t = naechsterTermin(alle, jetzt)
  if (!t) return leerSperr("accessoryRectangular")
  const kurs = kursInfo(t.titel)
  const typ = typInfo(t.typ)
  const laufend = t.start <= jetzt

  if (variante === "raum") {
    // Großer Raum, darüber Fach und Zeit
    sperrText(w, kurs.kurz + " · " + (laufend ? "bis " + uhrzeit(t.ende) : wannText(t, jetzt)), Font.semiboldSystemFont(12), 0.8)
    sperrText(w, t.raum, Font.boldSystemFont(24), 1, { skalierung: 0.6 })
    sperrText(w, typ.lang, Font.systemFont(11), 0.7)
    return w
  }

  // Standard: Status mit Live-Countdown, Fach, Raum und Zeit
  const z1 = w.addStack()
  z1.layoutHorizontally()
  const bald = !laufend && gleicherTag(t.start, jetzt) && t.start - jetzt <= 60 * 60000
  if (laufend || bald) {
    sperrText(z1, laufend ? "JETZT · NOCH" : "BEGINNT IN", Font.boldSystemFont(10), 0.75)
    z1.addSpacer(4)
    const d = z1.addDate(laufend ? t.ende : t.start)
    d.applyTimerStyle()
    d.font = Font.boldSystemFont(10)
    d.textColor = Color.white()
    d.textOpacity = 0.75
  } else {
    sperrText(z1, wannText(t, jetzt).toUpperCase(), Font.boldSystemFont(10), 0.75)
  }
  z1.addSpacer()
  sperrText(w, kurs.kurz + " · " + typ.lang, Font.boldSystemFont(15), 1, { skalierung: 0.75 })
  sperrText(w, t.raum + " · " + uhrzeit(t.start) + "–" + uhrzeit(t.ende), Font.mediumSystemFont(13), 0.9, { skalierung: 0.75 })
  return w
}

// Fortschrittsring für das runde Widget (Polygon, da Scriptable keine Bögen zeichnet)
function ringBild(anteil) {
  const g = 72
  const ctx = new DrawContext()
  ctx.size = new Size(g, g)
  ctx.opaque = false
  ctx.respectScreenScale = true
  const r = g / 2 - 4
  const zeichne = (bis, farbe) => {
    const p = new Path()
    const schritte = Math.max(2, Math.round(120 * bis))
    for (let i = 0; i <= schritte; i++) {
      const w = -Math.PI / 2 + 2 * Math.PI * bis * (i / schritte)
      const pt = new Point(g / 2 + r * Math.cos(w), g / 2 + r * Math.sin(w))
      if (i === 0) p.move(pt)
      else p.addLine(pt)
    }
    ctx.addPath(p)
    ctx.setStrokeColor(farbe)
    ctx.setLineWidth(4.5)
    ctx.strokePath()
  }
  zeichne(1, new Color("#FFFFFF", 0.25))
  if (anteil > 0.005) zeichne(Math.min(1, anteil), Color.white())
  return ctx.getImage()
}

// ── Rund ──
function sperrKreis(alle, jetzt) {
  const t = naechsterTermin(alle, jetzt)
  if (!t) return leerSperr("accessoryCircular")
  const variante = sperrVariante(["countdown", "raum", "zeit"], "countdown")
  const kurs = kursInfo(t.titel).kurz
  const laufend = t.start <= jetzt
  const w = sperrWidget()
  w.addAccessoryWidgetBackground = true
  w.setPadding(4, 4, 4, 4)
  w.addSpacer()

  if (variante === "raum") {
    // Gebäude groß, Raum klein: "70" / "0.10"
    const r = raumTeile(t.raum)
    const z = mittig(w)
    sperrText(z, r.haus, Font.boldSystemFont(22), 1, { mitte: true, skalierung: 0.5 })
    sperrText(z, r.rest, Font.semiboldSystemFont(11), 0.8, { mitte: true, skalierung: 0.6 })
  } else if (variante === "zeit") {
    const z = mittig(w)
    sperrText(z, laufend ? "bis" : kurs, Font.semiboldSystemFont(10), 0.75, { mitte: true, skalierung: 0.6 })
    sperrText(z, uhrzeit(laufend ? t.ende : t.start), Font.boldSystemFont(17), 1, { mitte: true, skalierung: 0.6 })
    if (!laufend && !gleicherTag(t.start, jetzt)) sperrText(z, WOCHENTAGE_LANG[t.start.getDay()], Font.semiboldSystemFont(9), 0.7, { mitte: true })
  } else {
    // Countdown mit Ring: läuft gerade → Fortschritt der Veranstaltung
    const heute = gleicherTag(t.start, jetzt)
    if (laufend) w.backgroundImage = ringBild((jetzt - t.start) / (t.ende - t.start))
    else if (heute && t.start - jetzt <= 60 * 60000) w.backgroundImage = ringBild(1 - (t.start - jetzt) / (60 * 60000))
    const z = mittig(w)
    sperrText(z, kurs, Font.semiboldSystemFont(9), 0.8, { mitte: true, skalierung: 0.6 })
    if (laufend || heute) {
      const d = z.addDate(laufend ? t.ende : t.start)
      d.applyTimerStyle()
      d.font = Font.boldSystemFont(13)
      d.textColor = Color.white()
      d.centerAlignText()
      d.minimumScaleFactor = 0.6
      d.lineLimit = 1
    } else {
      sperrText(z, uhrzeit(t.start), Font.boldSystemFont(14), 1, { mitte: true })
      sperrText(z, WOCHENTAGE_LANG[t.start.getDay()], Font.semiboldSystemFont(9), 0.7, { mitte: true })
    }
  }
  w.addSpacer()
  return w
}

// ───────────────────────── Start ─────────────────────────

async function erstelleWidget(familie) {
  const jetzt = new Date()
  const alle = (await ladeTermine()).map(viertelAnpassen).sort((a, b) => a.start - b.start)
  const farben = themenFarben()
  let w
  if (familie === "accessoryInline") w = sperrInline(alle, jetzt)
  else if (familie === "accessoryRectangular") w = sperrRechteck(alle, jetzt)
  else if (familie === "accessoryCircular") w = sperrKreis(alle, jetzt)
  else if (familie === "small") w = kleinesWidget(alle, jetzt, farben)
  else if (familie === "medium") w = mittleresWidget(alle, jetzt, farben)
  else if (familie === "extraLarge") w = extraGrossesWidget(alle, jetzt, farben)
  else w = grossesWidget(alle, jetzt, farben)
  aktualisierung(w, alle, jetzt)
  return w
}

if (config.runsInWidget) {
  Script.setWidget(await erstelleWidget(config.widgetFamily))
} else {
  // In der App: Vorschau-Größe auswählen
  const groessen = [
    ["Klein (2×2)", "small", "presentSmall"],
    ["Mittel (4×2)", "medium", "presentMedium"],
    ["Groß (4×4)", "large", "presentLarge"],
  ]
  if (Device.isPad()) groessen.push(["Extragroß (8×4, iPad)", "extraLarge", "presentExtraLarge"])
  groessen.push(["Sperrbildschirm: Zeile", "accessoryInline", "presentAccessoryInline"])
  groessen.push(["Sperrbildschirm: Rechteck", "accessoryRectangular", "presentAccessoryRectangular"])
  groessen.push(["Sperrbildschirm: Rund", "accessoryCircular", "presentAccessoryCircular"])
  const auswahl = new Alert()
  auswahl.title = "Vorschau"
  for (const [name] of groessen) auswahl.addAction(name)
  auswahl.addCancelAction("Abbrechen")
  const i = await auswahl.presentSheet()
  if (i >= 0 && i < groessen.length) {
    const [, familie, methode] = groessen[i]
    const w = await erstelleWidget(familie)
    if (typeof w[methode] === "function") await w[methode]()
    else await w.presentSmall()
  }
}
Script.complete()
