// Variables used by Scriptable.
// These must be at the very top of the file. Do not edit.
// icon-color: deep-purple; icon-glyph: magic;
// Stundenplan-Widget für Scriptable (klein, mittel, groß, iPad extragroß und Sperrbildschirm)
// Design: kräftige, vollflächige Farben. Hell/Dunkel folgt automatisch dem iPhone.
//
// Größen: Klein (2×2) = aktuelle/nächste Veranstaltung, Mittel (4×2) = dazu der Tagesplan,
// Groß (4×4) = ganze Woche, Extragroß (nur iPad) = Woche plus aktuelle Veranstaltung und Tagesplan. Nach der letzten Veranstaltung zeigen Klein und Mittel den nächsten Tag.
//
// Widget-Parameter (optional, beim Bearbeiten des Widgets eintragen, mit Leerzeichen kombinierbar):
//   hell   → immer heller Modus
//   dunkel → immer dunkler Modus
//   transparent → ohne Hintergrund (durchsichtig im iOS-Look „Klar“/„Getönt“)
//   https://planner.joshuan.dev/de?s=…  → dein eigener Planer-Link, öffnet sich beim Tippen
//
// Einfacher: Script in der Scriptable-App starten → „Einstellungen“ (Farbschema, Planer-Link,
// Sperrbildschirm-Varianten per Auswahlmenü). Ein Widget-Parameter hat trotzdem Vorrang.
//
// Tippen: Raum/Karte/Tageszeile → Lageplan des passenden Campus mit markiertem Gebäude, Route (Apple Karten),
// bei Campuswechsel zwischen zwei Terminen die Bus- und Bahnverbindungen
// und „Ändern“ (fällt aus / anderer Raum); Wochentag in der Wochenansicht → alle Räume des Tages.
// Der Rest des Widgets öffnet das Script (Menü). Der Planer öffnet sich nur noch im Lageplan.
//
// Erinnerungen vor Veranstaltungen: Script starten → „Einstellungen“ → „Erinnerungen“.
// Siri: „Hey Siri, Stundenplan“ sagt die nächste Veranstaltung an.
//
// Sperrbildschirm-Widgets (iPhone): Variante per Einstellungen oder Parameter:
//   Zeile über der Uhr:  naechste (Standard) · raum · countdown
//   Rechteckig:          naechste (Standard) · raum · tag
//   Rund:                countdown (Standard) · raum · zeit
//
// Eigener Planer-Link: Im Planer auf "Plan teilen" tippen, Link kopieren und in den Einstellungen
// eintragen. Er öffnet sich im Lageplan über „Im Planer öffnen“.
//
// Stundenplan: Script starten → „Stundenplan-Datei wählen“ (z. B. die .ics, die die iOS-App unter
// „Plan teilen“ erzeugt) – oder die Datei als "stundenplan.ics" in iCloud Drive › Scriptable ablegen.
//
// Profile (Pläne anderer): Script starten → „Profile“ → „Profil hinzufügen“ (.ics-Datei). Ein Widget zeigt
// ein Profil mit dem Parameter profil=<kürzel>, z. B. „profil=max“ (das Kürzel steht im Profil-Menü).
// Wecker: Einstellungen → „Wecker“ (Mitteilung mit Ton vor der ersten Veranstaltung des Tages).

// ───────────────────────── Einstellungen ─────────────────────────

const CONFIG = {
  ICS_DATEI: "stundenplan.ics",
  // Termine, die zur vollen Stunde beginnen und enden (z. B. 8–10 Uhr),
  // werden als 8:15–9:45 dargestellt (akademisches Viertel).
  AKADEMISCHES_VIERTEL: true,
  STANDARD_START: 8,   // früheste angezeigte Stunde
  STANDARD_ENDE: 18,   // späteste angezeigte Stunde
  THEMA: "auto",       // "auto" | "hell" | "dunkel" | "transparent"
  TAP_URL: "https://planner.joshuan.dev/de", // Standard-Planer-Link (Lageplan → „Im Planer öffnen“)

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

// ───────────────────────── Daten laden ─────────────────────────

// Profil (Plan von jemand anderem): Widget-Parameter „profil=kürzel“ oder Link-Parameter „profil“
const PROFIL = (() => {
  const teil = (args.widgetParameter || "").trim().split(/\s+/).find(t => /^profil=/i.test(t))
  const q = (args.queryParameters || {}).profil
  return (teil ? teil.split("=")[1] : q || "").toLowerCase() || null
})()

function profilDatei(kuerzel) {
  return "stundenplan-profil-" + kuerzel + ".ics"
}

function dateiManager() {
  try { return FileManager.iCloud() } catch (e) { return FileManager.local() }
}

async function dateiLesen(name) {
  const kandidaten = []
  try { kandidaten.push(FileManager.iCloud()) } catch (e) {}
  kandidaten.push(FileManager.local())
  for (const fm of kandidaten) {
    try {
      const pfad = fm.joinPath(fm.documentsDirectory(), name)
      if (!fm.fileExists(pfad)) continue
      if (fm.isFileStoredIniCloud(pfad) && !fm.isFileDownloaded(pfad)) {
        await fm.downloadFileFromiCloud(pfad)
      }
      return fm.readString(pfad)
    } catch (e) {
      console.log(name + " konnte nicht gelesen werden: " + e)
    }
  }
  return null
}

// Termine aus der eigenen Datei (profil = null) oder aus einem Profil
async function ladeTermine(profil) {
  const text = await dateiLesen(profil ? profilDatei(profil) : CONFIG.ICS_DATEI)
  return text ? parseICS(text) : []
}

// Termine mit akademischem Viertel und eigenen Änderungen (fällt aus / anderer Raum), sortiert.
// Ausgefallene sind nur mit mitAusfaellen dabei (Lageplan, Ändern-Menü).
async function ladeAlle(mitAusfaellen, profil) {
  const p = profil === undefined ? PROFIL : profil
  const aend = p ? {} : aenderungen()
  const regeln = p ? {} : zeitRegeln()
  return (await ladeTermine(p))
    .map(viertelAnpassen)
    .map(t => {
      // eigene Zeit für die ganze Reihe
      const r = regeln[reihe(t)]
      return r ? zeitSetzen(t, r.von, r.bis, r.weckerFest !== false) : t
    })
    .map(t => {
      const a = aend[schluessel(t)]
      if (!a) return t
      let n = Object.assign({}, t)
      if (a.von != null && a.bis != null) n = zeitSetzen(n, a.von, a.bis, a.weckerFest !== false)
      if (a.ausfall) n.ausfall = true
      if (a.raum && a.raum !== t.raum) { n.alterRaum = t.raum; n.raum = a.raum }
      return n
    })
    .filter(t => mitAusfaellen || !t.ausfall)
    .sort((a, b) => a.start - b.start)
}

// Bleibt gleich, auch wenn Raum oder Zeit geändert werden
function schluessel(t) {
  return Math.floor((t.originalStart || t.start).getTime() / 1000) + "|" + t.titel
}

// Reihe: gleiche Veranstaltung, gleiche Art, gleicher Wochentag, gleiche (ursprüngliche) Uhrzeit
function reihe(t) {
  const o = t.originalStart || t.start
  return t.titel + "|" + t.typ + "|" + o.getDay() + "|" + (o.getHours() * 60 + o.getMinutes())
}

// Termin auf eigene Uhrzeiten setzen (Minuten nach Mitternacht). weckerFest: wird der Beginn nach hinten
// verschoben, richtet sich der Wecker weiter nach der ursprünglichen Zeit (Züge fahren nicht später).
function zeitSetzen(t, von, bis, weckerFest) {
  const n = Object.assign({}, t)
  if (!n.originalStart) { n.originalStart = t.start; n.originalEnde = t.ende }
  const tag = tagesBeginn(n.originalStart)
  n.start = new Date(tag.getTime() + von * 60000)
  n.ende = new Date(tag.getTime() + Math.max(bis, von + 5) * 60000)
  n.weckStart = weckerFest && n.start > n.originalStart ? n.originalStart : null
  return n
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
  const thema = teile.includes("hell") ? "hell" : teile.includes("dunkel") ? "dunkel"
    : teile.includes("transparent") || teile.includes("durchsichtig") ? "transparent" : (einstellungen().thema || CONFIG.THEMA)
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
    // „transparent“: kein eigener Hintergrund (richtig durchsichtig im iOS-Look „Klar“/„Getönt“)
    bg: thema === "transparent" ? Color.clear() : Color.dynamic(new Color(FARBEN.hell.bg), new Color(FARBEN.dunkel.bg)),
    vorder: Color.dynamic(new Color(FARBEN.hell.text), new Color(FARBEN.dunkel.text)),
    gedimmt: Color.dynamic(new Color(FARBEN.hell.gedimmt), new Color(FARBEN.dunkel.gedimmt)),
    akzent: Color.dynamic(new Color("#185FA5"), new Color("#85B7EB")),
  }
}

function basisWidget(farben, rand) {
  const w = new ListWidget()
  w.backgroundColor = farben.bg
  w.setPadding(rand, rand, rand, rand)
  // Kein Link: Tippen auf freie Fläche öffnet das Script (Menü), nie den Planer
  return w
}

// Link beim Tippen: Planer-Link aus dem Widget-Parameter, sonst CONFIG.TAP_URL
function tippLink() {
  const teile = (args.widgetParameter || "").trim().split(/\s+/)
  const eigener = teile.find(t => /^https?:\/\//i.test(t))
  return eigener || einstellungen().link || CONFIG.TAP_URL
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
  k.url = lageplanLinkTermin(t) // Tippen: Lageplan mit Raum
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
    // Am Vorabend: wann der Wecker klingelt
    const wk = !auswahl.istHeute && WECKER_NAECHSTER && WECKER_NAECHSTER.t.start.getTime() === t.start.getTime()
      ? " · ⏰ " + uhrzeit(WECKER_NAECHSTER.wann) : ""
    textHinzu(zeile1, (auswahl.istHeute ? "Als Nächstes" : "Erster Termin") + wk, statusFont, weiss, { deckkraft: 0.9 })
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
  if (!auswahl) { leerHinweis(w, farben, alle.length ? "Keine weiteren Termine" : "Noch kein Stundenplan – Script starten"); return w }

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
  if (!auswahl) { leerHinweis(w, farben, alle.length ? "Keine weiteren Termine" : "Noch kein Stundenplan – Script starten"); return w }

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
    r.url = lageplanLinkTermin(t) // Tippen: Lageplan mit Raum
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
    // Raster als Hintergrundbild, darüber unsichtbare Tippflächen pro Wochentag (→ Lageplan des Tages)
    const raster = parent.addStack()
    raster.size = new Size(breite, rasterH)
    raster.backgroundImage = zeichneRaster(breite, rasterH, tage, startH, endeH, jetzt, true)
    raster.layoutHorizontally()
    const achse = 20, luecke = 3
    const spaltenB = (breite - achse - luecke * (tage.length - 1)) / tage.length
    raster.addSpacer(achse)
    tage.forEach((tag, i) => {
      const spalte = raster.addStack()
      spalte.size = new Size(spaltenB, rasterH)
      if (tag.termine.length) spalte.url = lageplanLinkTag(tag.datum)
      spalte.addSpacer()
      if (i < tage.length - 1) raster.addSpacer(luecke)
    })
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

// ───────────────────────── Einstellungen (in der App wählbar) ─────────────────────────
// Werden gespeichert in iCloud Drive › Scriptable › stundenplan-einstellungen.json
// Reihenfolge: Widget-Parameter > Einstellungen > CONFIG oben im Script.

const EINSTELLUNGEN_DATEI = "stundenplan-einstellungen.json"

function einstellungsDateiPfad() {
  let fm
  try { fm = FileManager.iCloud() } catch (e) { fm = FileManager.local() }
  return { fm, pfad: fm.joinPath(fm.documentsDirectory(), EINSTELLUNGEN_DATEI) }
}

let _einstellungen = null
function einstellungen() {
  if (_einstellungen) return _einstellungen
  _einstellungen = {}
  try {
    const { fm, pfad } = einstellungsDateiPfad()
    if (fm.fileExists(pfad)) {
      if (fm.isFileStoredIniCloud(pfad) && !fm.isFileDownloaded(pfad)) {
        // Nicht heruntergeladen: Standardwerte verwenden (Download startet im Hintergrund)
        fm.downloadFileFromiCloud(pfad)
      } else {
        _einstellungen = JSON.parse(fm.readString(pfad)) || {}
      }
    }
  } catch (e) {
    _einstellungen = {}
  }
  return _einstellungen
}

function einstellungenSpeichern(neu) {
  _einstellungen = neu
  const { fm, pfad } = einstellungsDateiPfad()
  fm.writeString(pfad, JSON.stringify(neu, null, 2))
}

// ───────────────────────── Eigene Änderungen (fällt aus / anderer Raum) ─────────────────────────
// Gespeichert in iCloud Drive › Scriptable › stundenplan-aenderungen.json

function aenderungsDatei() {
  let fm
  try { fm = FileManager.iCloud() } catch (e) { fm = FileManager.local() }
  return { fm, pfad: fm.joinPath(fm.documentsDirectory(), "stundenplan-aenderungen.json") }
}

let _aenderungen = null
function aenderungen() {
  if (_aenderungen) return _aenderungen
  _aenderungen = {}
  try {
    const { fm, pfad } = aenderungsDatei()
    if (fm.fileExists(pfad)) {
      if (fm.isFileStoredIniCloud(pfad) && !fm.isFileDownloaded(pfad)) fm.downloadFileFromiCloud(pfad)
      else _aenderungen = JSON.parse(fm.readString(pfad)) || {}
    }
  } catch (e) { _aenderungen = {} }
  return _aenderungen
}

function aenderungSetzen(t, neu) {
  const alle = Object.assign({}, aenderungen())
  const k = schluessel(t)
  if (!neu || (!neu.ausfall && !neu.raum && neu.von == null)) delete alle[k]
  else alle[k] = neu
  // Alte Einträge (über 30 Tage vergangen) aufräumen
  const grenze = Date.now() / 1000 - 30 * 86400
  for (const key of Object.keys(alle)) if (Number(key.split("|")[0]) < grenze) delete alle[key]
  _aenderungen = alle
  const { fm, pfad } = aenderungsDatei()
  fm.writeString(pfad, JSON.stringify(alle, null, 2))
}

// ───── Eigene Zeiten für ganze Reihen (stundenplan-zeitregeln.json) ─────

let _zeitRegeln = null
function zeitRegelnDatei() {
  const { fm } = aenderungsDatei()
  return { fm, pfad: fm.joinPath(fm.documentsDirectory(), "stundenplan-zeitregeln.json") }
}

function zeitRegeln() {
  if (_zeitRegeln) return _zeitRegeln
  _zeitRegeln = {}
  try {
    const { fm, pfad } = zeitRegelnDatei()
    if (fm.fileExists(pfad)) {
      if (fm.isFileStoredIniCloud(pfad) && !fm.isFileDownloaded(pfad)) fm.downloadFileFromiCloud(pfad)
      else _zeitRegeln = JSON.parse(fm.readString(pfad)) || {}
    }
  } catch (e) { _zeitRegeln = {} }
  return _zeitRegeln
}

function zeitRegelSetzen(r, regel) {
  const alle = Object.assign({}, zeitRegeln())
  if (regel) alle[r] = regel
  else delete alle[r]
  _zeitRegeln = alle
  const { fm, pfad } = zeitRegelnDatei()
  fm.writeString(pfad, JSON.stringify(alle, null, 2))
}

function uhrText(m) { return Math.floor(m / 60) + ":" + String(m % 60).padStart(2, "0") }
function minutenVon(d) { return d.getHours() * 60 + d.getMinutes() }

// „10:30“, „10.30“, „1030“ oder „10“ → Minuten nach Mitternacht (null = ungültig)
function zeitLesen(s) {
  const m = String(s || "").trim().match(/^(\d{1,2})(?:[:.,]?(\d{2}))?$/)
  if (!m) return null
  const h = Number(m[1]), mi = Number(m[2] || 0)
  return h < 24 && mi < 60 ? h * 60 + mi : null
}

// Beginn/Ende selbst festlegen – nur dieser Termin oder die ganze Reihe
async function zeitAendern(t, alle) {
  const oStart = t.originalStart || t.start, oEnde = t.originalEnde || t.ende
  const oVon = minutenVon(oStart), oBis = minutenVon(oEnde)
  const r = reihe(t)
  const anzahl = alle.filter(x => reihe(x) === r).length
  const einzelnVorher = (aenderungen()[schluessel(t)] || {}).von != null

  const u = new Alert()
  u.title = "Zeit ändern – gilt für"
  u.message = t.titel + "\nursprünglich " + uhrText(oVon) + "–" + uhrText(oBis)
  u.addAction("Alle " + anzahl + " (jeden " + WOCHENTAGE_VOLL[oStart.getDay()] + " " + uhrText(oVon) + ")")
  u.addAction("Nur diesen Termin (" + datumKurz(t.start) + ")")
  if (t.originalStart) u.addDestructiveAction("Eigene Zeit entfernen")
  u.addCancelAction("Abbrechen")
  const ui = await u.presentSheet()
  if (ui < 0) return false
  if (ui === 2) {
    zeitRegelSetzen(r, null)
    const a = Object.assign({}, aenderungen()[schluessel(t)] || {})
    delete a.von; delete a.bis; delete a.weckerFest
    aenderungSetzen(t, a)
    return true
  }
  const nurDieser = ui === 1

  const z = new Alert()
  z.title = "Neue Zeit"
  z.message = "Erinnerungen und Widgets richten sich nach der neuen Zeit."
  const optionen = []
  if (oVon % 60 === 15) optionen.push(["Volle Stunde (s.t.): " + uhrText(oVon - 15) + "–" + uhrText(oBis + 15), oVon - 15, oBis + 15])
  optionen.push(["Wie bisher (c.t.): " + uhrText(oVon) + "–" + uhrText(oBis), oVon, oBis])
  optionen.forEach(o => z.addAction(o[0]))
  z.addAction("Eigene Zeit eingeben …")
  z.addCancelAction("Abbrechen")
  const zi = await z.presentSheet()
  if (zi < 0) return false
  let von, bis
  if (zi < optionen.length) {
    von = optionen[zi][1]; bis = optionen[zi][2]
  } else {
    const e = new Alert()
    e.title = "Eigene Zeit"
    e.message = "Uhrzeit z. B. als 10:30"
    e.addTextField("Beginn", uhrText(minutenVon(t.start)))
    e.addTextField("Ende", uhrText(minutenVon(t.ende)))
    e.addAction("Weiter")
    e.addCancelAction("Abbrechen")
    if (await e.presentAlert() < 0) return false
    von = zeitLesen(e.textFieldValue(0)); bis = zeitLesen(e.textFieldValue(1))
    if (von == null || bis == null || bis <= von) {
      await hinweisZeigen("Ungültige Zeit", "Bitte Beginn und Ende als Uhrzeit eingeben, das Ende nach dem Beginn.")
      return false
    }
  }

  // Später als ursprünglich? Dann fragen, wonach sich der Wecker richtet
  let weckerFest = true
  if (von > oVon) {
    const w = new Alert()
    w.title = "Wecker"
    w.message = "Die Veranstaltung beginnt später. Züge fahren aber nicht später – soll der Wecker trotzdem wie bisher klingeln?"
    w.addAction("Wecker bleibt bei " + uhrText(oVon) + " (wie bisher)")
    w.addAction("Wecker nach neuer Zeit (" + uhrText(von) + ")")
    w.addCancelAction("Abbrechen")
    const wi = await w.presentSheet()
    if (wi < 0) return false
    weckerFest = wi === 0
  }

  const wieOriginal = von === oVon && bis === oBis
  const a = Object.assign({}, aenderungen()[schluessel(t)] || {})
  if (nurDieser) {
    if (wieOriginal && !zeitRegeln()[r]) { delete a.von; delete a.bis; delete a.weckerFest }
    else { a.von = von; a.bis = bis; if (weckerFest) delete a.weckerFest; else a.weckerFest = false }
  } else {
    zeitRegelSetzen(r, wieOriginal ? null : (weckerFest ? { von, bis } : { von, bis, weckerFest: false }))
    delete a.von; delete a.bis; delete a.weckerFest
  }
  aenderungSetzen(t, a)
  if (!nurDieser && einzelnVorher) await hinweisZeigen("Gespeichert", "Gilt jetzt für die ganze Reihe; die Einzel-Zeit dieses Termins wurde dabei aufgehoben.")
  return true
}

async function hinweisZeigen(titel, text) {
  const h = new Alert()
  h.title = titel
  h.message = text
  h.addCancelAction("OK")
  await h.presentAlert()
}

async function aendernMenue(t, alle) {
  const a = new Alert()
  a.title = kursInfo(t.titel).kurz + " · " + datumKurz(t.start) + " " + uhrzeit(t.start)
  a.message = t.titel + "\n" + t.raum + (t.alterRaum ? " (ursprünglich " + t.alterRaum + ")" : "") + (t.ausfall ? "\nFällt aus" : "")
    + (t.originalStart ? "\nEigene Zeit, ursprünglich " + uhrzeit(t.originalStart) + "–" + uhrzeit(t.originalEnde) : "")
  a.addAction(t.ausfall ? "Findet doch statt" : "Fällt aus")
  a.addAction("Raum ändern …")
  a.addAction("Zeit ändern …")
  if (t.ausfall || t.alterRaum || (aenderungen()[schluessel(t)] || {}).von != null) a.addDestructiveAction("Änderungen zurücksetzen")
  a.addCancelAction("Abbrechen")
  const i = await a.presentSheet()
  if (i < 0) return false
  const eigenerRaum = t.alterRaum ? t.raum : undefined
  // eine eigene Zeit des Termins bleibt erhalten
  const bisher = aenderungen()[schluessel(t)] || {}
  const zeitTeil = bisher.von != null ? { von: bisher.von, bis: bisher.bis, weckerFest: bisher.weckerFest } : {}
  if (i === 2) return await zeitAendern(t, alle || await ladeAlle(true))
  if (i === 0) {
    aenderungSetzen(t, Object.assign({ ausfall: !t.ausfall, raum: eigenerRaum }, zeitTeil))
  } else if (i === 1) {
    const r = new Alert()
    r.title = "Raum ändern"
    r.message = "Gilt nur für diesen einen Termin. Widgets und Erinnerungen übernehmen den neuen Raum."
    r.addTextField("z. B. 2.70.0.10", t.raum)
    r.addAction("Speichern")
    r.addCancelAction("Abbrechen")
    if (await r.presentAlert() < 0) return false
    const neu = r.textFieldValue(0).trim()
    const original = t.alterRaum || t.raum
    aenderungSetzen(t, Object.assign({ ausfall: !!t.ausfall, raum: neu && neu !== original ? neu : undefined }, zeitTeil))
  } else {
    aenderungSetzen(t, null)
  }
  return true
}

// Termin auswählen und ändern (aus dem Script-Menü)
async function terminAendernMenue() {
  const alle = await ladeAlle(true)
  const heute = tagesBeginn(new Date())
  const tage = []
  for (let i = 0; i < 14 && tage.length < 7; i++) {
    const d = new Date(heute)
    d.setDate(heute.getDate() + i)
    const ts = termineAm(alle, d)
    if (ts.length) tage.push({ d, ts })
  }
  if (!tage.length) return
  const ta = new Alert()
  ta.title = "Welcher Tag?"
  tage.forEach(x => ta.addAction(datumKurz(x.d)))
  ta.addCancelAction("Abbrechen")
  const ti = await ta.presentSheet()
  if (ti < 0) return
  const ts = tage[ti].ts
  const ea = new Alert()
  ea.title = "Welcher Termin?"
  ts.forEach(t => ea.addAction((t.ausfall ? "✕ " : "") + uhrzeit(t.start) + " " + kursInfo(t.titel).kurz + " · " + t.raum))
  ea.addCancelAction("Abbrechen")
  const ei = await ea.presentSheet()
  if (ei < 0) return
  if (await aendernMenue(ts[ei], alle)) await erinnerungenPlanen(true)
}

// ───────────────────────── Erinnerungen ─────────────────────────
// Mitteilung vor jeder Veranstaltung. Geplant werden immer die nächsten 30 (iOS hat ein Limit),
// neu geplant beim Start des Scripts und höchstens stündlich durch die Widgets.

async function erinnerungenPlanen(sofort) {
  const e = einstellungen()
  let fm = FileManager.local()
  const stempel = fm.joinPath(fm.cacheDirectory(), "stundenplan-erinnerungen-zeit.txt")
  if (!sofort && fm.fileExists(stempel) && Date.now() - Number(fm.readString(stempel)) < 3600000) return
  fm.writeString(stempel, String(Date.now()))
  try {
    const alte = (await Notification.allPending()).filter(n => (n.identifier || "").startsWith("sp-"))
    if (alte.length) await Notification.removePending(alte.map(n => n.identifier))
  } catch (err) {}
  await weckerPlanen()
  if (!e.erinnerungen) return
  const vorlauf = e.vorlauf || 10
  const jetzt = new Date()
  const kommende = (await ladeAlle(false, null)).filter(t => t.start.getTime() - vorlauf * 60000 > jetzt.getTime()).slice(0, 30)
  for (const t of kommende) {
    const n = new Notification()
    n.identifier = "sp-" + Math.floor(t.start.getTime() / 1000)
    n.threadIdentifier = "stundenplan"
    n.title = kursInfo(t.titel).kurz + " in " + vorlauf + " Min"
    n.body = typInfo(t.typ).lang + " · " + t.raum + " · " + uhrzeit(t.start) + "–" + uhrzeit(t.ende)
    n.sound = "event"
    n.openURL = lageplanLinkTermin(t)
    n.setTriggerDate(new Date(t.start.getTime() - vorlauf * 60000))
    await n.schedule()
  }
}

// ───────────────────────── Wecker ─────────────────────────
// Mitteilung mit Ton vor der ersten Veranstaltung des Tages (Scriptable kann keine echten Wecker stellen).
// Nur an Tagen mit Veranstaltungen, ausgefallene zählen nicht; geplant für die nächsten 14 Tage.

function weckzeiten(alle, vorlauf, fruehestens, jetzt) {
  const bis = jetzt.getTime() + 14 * 86400000
  const tage = new Set()
  const aus = []
  // Bei nach hinten verschobenen Terminen ggf. die ursprüngliche Zeit (Züge fahren nicht später)
  const beginn = t => t.weckStart || t.start
  for (const t of alle.slice().sort((a, b) => beginn(a) - beginn(b))) {
    // ab Tagesbeginn zählen, damit eine schon laufende erste Veranstaltung den Tag „belegt“
    if (t.ausfall || beginn(t) < tagesBeginn(jetzt) || beginn(t).getTime() > bis) continue
    const tag = tagesBeginn(beginn(t)).getTime()
    if (tage.has(tag)) continue
    tage.add(tag)
    let wann = beginn(t).getTime() - vorlauf * 60000
    if (fruehestens) {
      const grenze = tag + fruehestens * 60000
      if (wann < grenze) wann = Math.min(grenze, beginn(t).getTime() - 15 * 60000)
    }
    if (wann > jetzt.getTime()) aus.push({ wann: new Date(wann), t })
  }
  return aus
}

async function weckerPlanen() {
  const e = einstellungen()
  try {
    const alte = (await Notification.allPending()).filter(n => (n.identifier || "").startsWith("wk-"))
    if (alte.length) await Notification.removePending(alte.map(n => n.identifier))
  } catch (err) {}
  if (!e.wecker) return
  const zeiten = weckzeiten(await ladeAlle(false, null), e.wecker, e.weckerFruehestens || 0, new Date())
  for (const { wann, t } of zeiten) {
    const n = new Notification()
    n.identifier = "wk-" + Math.floor(wann.getTime() / 1000)
    n.title = "Aufstehen: " + kursInfo(t.titel).kurz + " um " + uhrzeit(t.start)
    n.body = "Erste Veranstaltung heute" + (t.raum ? " · " + t.raum : "")
    n.sound = "alert"
    n.openURL = lageplanLinkTermin(t)
    n.setTriggerDate(wann)
    await n.schedule()
  }
}

// ───────────────────────── Siri ─────────────────────────

async function siriAntwort() {
  const jetzt = new Date()
  const t = naechsterTermin(await ladeAlle(false, null), jetzt)
  let text
  if (!t) text = "Du hast keine weiteren Termine."
  else {
    const r = raumAufteilen(t.raum)
    const ort = r ? "Haus " + r.haus + ", Raum " + r.etage + (r.raum ? "." + r.raum : "") : "Raum " + t.raum
    if (t.start <= jetzt) text = "Gerade läuft " + t.titel + " in " + ort + ", noch bis " + uhrzeit(t.ende) + "."
    else {
      const morgen = new Date(jetzt); morgen.setDate(jetzt.getDate() + 1)
      const wann = gleicherTag(t.start, jetzt) ? "heute um " + uhrzeit(t.start)
        : gleicherTag(t.start, morgen) ? "morgen um " + uhrzeit(t.start)
        : "am " + WOCHENTAGE_VOLL[t.start.getDay()] + " um " + uhrzeit(t.start)
      text = t.titel + ", " + typInfo(t.typ).lang + ", " + wann + " in " + ort + "."
    }
  }
  Script.setShortcutOutput(text)
  Speech.speak(text)
}

// ───────────────────────── Lagepläne (Golm, Neues Palais, Griebnitzsee) ─────────────────────────

const CAMPUS = { "1": "Am Neuen Palais", "2": "Golm", "3": "Griebnitzsee" }
const CAMPUS_KURZ = { "1": "Neues Palais", "2": "Golm", "3": "Griebnitzsee" }
// Ungefähre Mitte (falls ein Haus keine Koordinate hat)
const CAMPUS_MITTE = { "1": [52.4007, 13.0112], "2": [52.4083, 12.9765], "3": [52.3932, 13.1285] }

// Einrichtungen laut Legende der Lagepläne (Stand Mai 2025)
const EINRICHTUNGEN = {
  "2": {
    "1": ["Infrastrukturelles/Technisches Gebäudemanagement", "Institut für Umweltwissenschaften und Geographie", "Leitzentrale"],
    "2": ["Potsdam Research Institute for Multilingualism (PRIM)"],
    "4": ["Mensa"],
    "5": ["Multimediaproduktion (ZIM)"],
    "6": ["Department Musik und Kunst"],
    "7": ["Wohnanlagen (Studierendenwerk)"],
    "8": ["Service Center (ZIM)", "Gebäudemanagement"],
    "9": ["Institut für Mathematik"],
    "10": ["EPI-Center", "Fuhrpark"],
    "11": ["Alte Sporthalle", "Lehreinheit Wirtschaft-Arbeit-Technik"],
    "12": ["Institut für Umweltwissenschaften und Geographie"],
    "13": ["Atelier – Werkstätten Kunst", "Fachbereich Kunst", "Pädagogische Werkstatt"],
    "14": ["Department Linguistik", "Department Psychologie", "Lesecafé", "Poststelle"],
    "16": ["Department Grundschulpädagogik"],
    "17": ["Wohnanlagen (Studierendenwerk)"],
    "18": ["Universitätsbibliothek (IKMZ)"],
    "19": ["Sporthalle"],
    "20": ["Institut für Biochemie und Biologie"],
    "24": ["Department Erziehungswissenschaften", "Department Sport- und Gesundheitswissenschaften", "Department Lehrerbildung", "Institut für Umweltwissenschaften und Geographie"],
    "25": ["Institut für Biochemie und Biologie", "Institut für Chemie"],
    "26": ["Institut für Biochemie und Biologie", "Institut für Chemie"],
    "27": ["Institut für Erd- und Umweltwissenschaften"],
    "28": ["Institut für Physik und Astronomie", "Dekanat Math.-Nat. Fakultät"],
    "29": ["Institut für Geowissenschaften", "Institut für Biochemie und Biologie", "Institut für Chemie", "Potsdam Transfer"],
    "30": ["Gewächshaus"],
    "31": ["Department Inklusionspädagogik"],
    "32": ["Earth & Environment Centre (EEC)", "Zentrum für Naturstoffgenomik"],
    "35": ["Dekanat Humanwissenschaftliche Fakultät"],
    "48": ["Infopoint"],
    "60": ["Institut für Biochemie und Biologie"],
    "62": ["Fakultät für Gesundheitswissenschaften", "Zentrum für Lehrerbildung (ZeLB)"],
    "70": ["Institut für Informatik und Computational Science", "Institut für Umweltwissenschaften und Geographie"],
  },
  "1": {
    "1": ["Institut für Künste und Medien", "Institut für Slavistik"],
    "2": ["Arbeitsmedizin", "AV- und IT-Dienste – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Dezernat 6: Zentrale Dienste", "Poststelle – Dezernat 6: Zentrale Dienste", "Sicherheitswesen"],
    "3": ["Dezernat 3: Personal- und Rechtsangelegenheiten", "Dezernat 4: Haushalt und Beschaffung"],
    "4": ["Dezernat 5: Bau- und Facilitymanagement"],
    "5": ["Institut für Germanistik"],
    "6": ["Koordinationsbüro für Chancengleichheit", "Multimediaproduktion – ZIM – Zentrum für Informationstechnologie und Medienmanagement", "Netzwerk Studienqualität Brandenburg (sqb)", "Personalvertretungen", "Studierendenvertretung – AStA", "Zentrum für Qualitätsentwicklung in Lehre und Studium (ZfQ)"],
    "8": ["Auditorium maximum „Audimax“", "Co-Working-Space", "Chief Information Officer (CIO)", "Dezernat 1: Planung, Statistik, Forschungsangelegenheiten", "Dezernat 2: Studienangelegenheiten", "Dezernat 3: Personal- und Rechtsangelegenheiten", "International Office", "Leitung, Verwaltung und Organisation – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Service Center – ZIM – Zentrum für Informationstechnologie und Medienmanagement", "Welcome Center Potsdam – International Office"],
    "9": ["Career-Service", "Eltern-Kind-Raum", "Geschäftsstelle des Senats", "International Summer Campus Office – International Office", "Kanzlerbüro", "Potsdam Transfer – Zentrale wissenschaftliche Einrichtung für Gründung, Innovation, Wissens- und Technologietransfer", "Präsident", "Präsidialamt", "Präsidium", "Referat für Presse- und Öffentlichkeitsarbeit", "Universitätsbibliothek"],
    "10": ["Universitätsbibliothek"],
    "11": ["Co-Working-Space", "Dekanat – Philosophische Fakultät", "Historisches Institut", "Institut für Jüdische Studien/Jewish Studies", "Institut für Lebensgestaltung – Ethik – Religionskunde", "Institut für Philosophie", "Institut für Religionswissenschaft", "Klassische Philologie", "Nil StudentInnenkeller", "Universitätsbibliothek"],
    "12": ["Department für Sport- und Gesundheitswissenschaften", "Eltern-Kind-Raum", "Hochschulambulanz", "Mensa – Studierendenwerk West:Brandenburg"],
    "13": ["Dezernat 5: Bau- und Facilitymanagement", "Infrastrukturelles Gebäudemanagement/Campusteam – Dezernat 5: Bau- und Facilitymanagement", "Zentrum für Hochschulsport"],
    "14": ["Abraham Geiger Kolleg (AGK)", "Zacharias Frankel College (ZFC)"],
    "15": ["Institut für Jüdische Theologie (School of Jewish Theology)", "Institut für Philosophie"],
    "19": ["Institut für Anglistik und Amerikanistik", "Institut für Romanistik", "Zentrum für Sprachen und Schlüsselkompetenzen (Zessko)"],
    "22": ["Dezernat 4: Haushalt und Beschaffung", "EDUC – Präsidialamt", "Nathan Peter Levinson Stiftung", "Service für Familien – Dezernat 3: Personal- und Rechtsangelegenheiten", "TK-Servicestelle", "Zentrum für Qualitätsentwicklung in Lehre und Studium (ZfQ)"],
    "34": ["Department für Sport- und Gesundheitswissenschaften", "Knetpunkt", "Umkleiden"],
    "35": ["Co-Working-Space", "Unishop"],
    "36": ["Department für Sport- und Gesundheitswissenschaften", "Zentrum für Hochschulsport"],
    "T1": ["Wohnanlagen – Studierendenwerk West:Brandenburg"],
    "T2": ["Wohnanlagen – Studierendenwerk West:Brandenburg"],
    "T3": ["Wohnanlagen – Studierendenwerk West:Brandenburg"],
  },
  "3": {
    "1": ["Dekanat – Juristische Fakultät", "Dekanat – Wirtschafts- und Sozialwissenschaftliche Fakultät", "Juristische Fakultät", "Mensa – Studierendenwerk West:Brandenburg", "Poststelle – Dezernat 6", "Service Center – ZIM – Zentrum für Informationstechnologie und Medienmanagement"],
    "2": ["Wirtschafts- und Sozialwissenschaftliche Fakultät"],
    "3": ["Infrastrukturelles Gebäudemanagement (IGM)/Campusteam Griebnitzsee – Dezernat 5: Bau- und Facilitymanagement", "Wirtschafts- und Sozialwissenschaftliche Fakultät"],
    "5": ["Universitätsbibliothek"],
    "6": ["AV- und IT-Dienste – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Mensa – Studierendenwerk West:Brandenburg", "Multimediaproduktion – ZIM - Zentrum für Informationstechnologie und Medienmanagement", "Studienbüro – Juristische Fakultät", "Zentrum für Sprachen und Schlüsselkompetenzen (Zessko)"],
    "7": ["Kommunalwissenschaftliches Institut", "Potsdam Transfer – Zentrale wissenschaftliche Einrichtung für Gründung, Innovation, Wissens- und Technologietransfer", "Universitätsgesellschaft Potsdam e. V.", "UP Transfer GmbH", "Wirtschafts- und Sozialwissenschaftliche Fakultät"],
    "13": ["Wirtschaftsinformatik"],
    "14": ["Demonstrationsgebäude – Wirtschaftsinformatik"],
  },
}

// Position der Hausnummer auf dem Plan (Anteil von Breite/Höhe)
const GEBAEUDE = {
  "2": {"25": [0.3622, 0.4749], "28": [0.3706, 0.5459], "18": [0.3505, 0.6258], "14": [0.4879, 0.7238], "8": [0.6598, 0.5596], "11": [0.6606, 0.7432], "24": [0.6679, 0.7883], "19": [0.5668, 0.8721], "35": [0.6534, 0.8581], "20": [0.7211, 0.8428], "6": [0.7917, 0.5255], "5": [0.8661, 0.5624], "4": [0.8646, 0.6525], "1": [0.7804, 0.7095], "26": [0.5281, 0.4119], "10": [0.6594, 0.6776], "9": [0.7086, 0.6315], "2": [0.7602, 0.6604], "27": [0.4745, 0.5309], "29": [0.5251, 0.5677], "31": [0.5587, 0.7556], "30": [0.5462, 0.7166], "12": [0.9248, 0.5391], "17": [0.3888, 0.7199], "60": [0.8442, 0.1093], "13": [0.7916, 0.8074], "48": [0.2998, 0.6972], "32": [0.471, 0.6365], "70": [0.3853, 0.0464], "16": [0.3861, 0.813], "62": [0.0741, 0.2764], "7": [0.7796, 0.5907], "15": [0.4928, 0.7715]},
  "1": {"1": [0.4184, 0.6985], "2": [0.3946, 0.6250], "3": [0.4171, 0.5592], "4": [0.3653, 0.3742], "5": [0.4167, 0.3052], "6": [0.2686, 0.5382], "8": [0.5062, 0.7122], "9": [0.6788, 0.5482], "10": [0.6950, 0.5870], "11": [0.6788, 0.3569], "12": [0.5087, 0.2373], "13": [0.1789, 0.2778], "14": [0.7835, 0.1708], "15": [0.7139, 0.1790], "16": [0.3444, 0.2031], "17": [0.2079, 0.2135], "19": [0.5400, 0.9017], "22": [0.3085, 0.6482], "34": [0.3229, 0.2889], "35": [0.6019, 0.5468], "36": [0.3032, 0.4321], "T1": [0.4939, 0.0577], "T2": [0.4270, 0.0577], "T3": [0.3613, 0.0577]},
  "3": {"1": [0.6388, 0.6253], "2": [0.4271, 0.4949], "3": [0.4469, 0.4214], "5": [0.2945, 0.3226], "6": [0.6342, 0.5202], "7": [0.5720, 0.3987], "13": [0.1209, 0.8324], "14": [0.2504, 0.8381]},
}

// GPS der Häuser (OpenStreetMap; einige aus dem Lageplan umgerechnet, meist < 20 m Abweichung).
// Golm: Haus 60 und 62 fehlen bewusst (keine verlässliche Position).
const GPS = {
  "2": {
    "1": [52.407483, 12.977766], "2": [52.408040, 12.977722], "4": [52.407946, 12.978802],
    "5": [52.408763, 12.979165], "6": [52.409130, 12.978347], "7": [52.408606, 12.978036],
    "8": [52.409095, 12.977112], "9": [52.408388, 12.977198], "10": [52.407919, 12.976578],
    "11": [52.407285, 12.976441], "12": [52.408935, 12.979743], "13": [52.406575, 12.977690],
    "14": [52.407557, 12.974072], "15": [52.407182, 12.974515], "16": [52.406950, 12.973302],
    "17": [52.407606, 12.973495], "18": [52.408674, 12.973316], "19": [52.406160, 12.975317],
    "20": [52.406271, 12.976781], "24": [52.406796, 12.976390], "25": [52.410074, 12.973810],
    "26": [52.410513, 12.975774], "27": [52.409526, 12.975206], "28": [52.409403, 12.973729],
    "29": [52.409063, 12.975092], "30": [52.407654, 12.975229], "31": [52.407279, 12.975312],
    "32": [52.408465, 12.974605], "35": [52.406365, 12.976121], "48": [52.408053, 12.972589],
    "70": [52.413289, 12.974407],
  },
  "1": {
    "1": [52.399575, 13.010625], "2": [52.400017, 13.010410], "3": [52.400489, 13.010515],
    "4": [52.401705, 13.010230], "5": [52.402179, 13.010342], "6": [52.400560, 13.009658],
    "8": [52.399791, 13.011915], "9": [52.400617, 13.013159], "10": [52.400343, 13.013035],
    "11": [52.401926, 13.013022], "12": [52.402453, 13.011640], "13": [52.402297, 13.008556],
    "14": [52.403118, 13.013408], "15": [52.403118, 13.012742], "16": [52.402787, 13.009843],
    "17": [52.402689, 13.008692], "19": [52.398339, 13.012055], "22": [52.399967, 13.009836],
    "34": [52.402268, 13.009653], "35": [52.400500, 13.012309], "36": [52.401305, 13.009673],
    "T1": [52.403757, 13.010999], "T2": [52.403742, 13.010431], "T3": [52.403726, 13.009872],
  },
  "3": {
    "1": [52.392742, 13.127282], "2": [52.393519, 13.128819], "3": [52.393501, 13.129520],
    "5": [52.394031, 13.130795], "6": [52.392867, 13.127992], "7": [52.393115, 13.129530],
    "13": [52.396600, 13.120404], "14": [52.396240, 13.120494],
  },
}

// Pläne von Neues Palais und Griebnitzsee werden einmalig von GitHub geladen und gespeichert
// (Golm ist eingebettet, siehe LAGEPLAN_PNG ganz unten).
const PLAN_DATEIEN = { "1": "lageplan_neuespalais.png", "3": "lageplan_griebnitzsee.png" }
const PLAN_QUELLE = "https://raw.githubusercontent.com/Black0Skorpion/Stundenplan/main/ios-app/App/"

async function planBild(campus) {
  if (campus === "2") return Image.fromData(Data.fromBase64String(LAGEPLAN_PNG))
  const name = PLAN_DATEIEN[campus]
  if (!name) return null
  const fm = FileManager.local()
  const pfad = fm.joinPath(fm.documentsDirectory(), "stundenplan-" + name)
  if (fm.fileExists(pfad)) return fm.readImage(pfad)
  try {
    const r = new Request(PLAN_QUELLE + name)
    r.timeoutInterval = 20
    const bild = await r.loadImage()
    fm.writeImage(pfad, bild)
    return bild
  } catch (e) {
    return null
  }
}

function gpsVon(raum) {
  const r = raumAufteilen(raum)
  return r && GPS[r.campus] ? GPS[r.campus][r.haus] || null : null
}

function gpsOderCampus(raum) {
  const r = raumAufteilen(raum)
  return gpsVon(raum) || (r && CAMPUS_MITTE[r.campus]) || null
}

function campusWechsel(von, zu) {
  const a = raumAufteilen(von), b = raumAufteilen(zu)
  return !!(a && b && CAMPUS[a.campus] && CAMPUS[b.campus] && a.campus !== b.campus)
}

// Geschätzte Gehzeit (Luftlinie × 1,3, 75 m/min); null = unbekannt/anderer Campus, 0 = selbes Haus
function gehminuten(von, zu) {
  const a = raumAufteilen(von), b = raumAufteilen(zu)
  if (!a || !b || a.campus !== b.campus) return null
  if (a.haus === b.haus) return 0
  const p = gpsVon(von), q = gpsVon(zu)
  if (!p || !q) return null
  const rad = x => x * Math.PI / 180
  const dp = rad(q[0] - p[0]), dl = rad(q[1] - p[1])
  const h = Math.sin(dp / 2) ** 2 + Math.cos(rad(p[0])) * Math.cos(rad(q[0])) * Math.sin(dl / 2) ** 2
  const m = 2 * 6371000 * Math.asin(Math.min(1, Math.sqrt(h)))
  return Math.max(1, Math.round(m * 1.3 / 75))
}

// Apple Karten mit Fußweg zum Haus
function routeLink(raum) {
  const p = gpsVon(raum), r = raumAufteilen(raum)
  return p ? "maps://?daddr=" + p[0] + "," + p[1] + "&dirflg=w&q=" + encodeURIComponent("Uni Potsdam " + CAMPUS[r.campus] + " Haus " + r.haus) : null
}

// Apple Karten mit Bus und Bahn zwischen zwei Räumen (Campuswechsel)
function oepnvLink(von, zu) {
  const p = gpsOderCampus(von), q = gpsOderCampus(zu)
  return p && q ? "maps://?saddr=" + p[0] + "," + p[1] + "&daddr=" + q[0] + "," + q[1] + "&dirflg=r" : null
}

// "2.70.0.10" → { campus: "2", haus: "70", etage: "0", raum: "10" }  ("08" → "8")
function raumAufteilen(raum) {
  const t = (raum || "").split(".")
  if (t.length < 3) return null
  const haus = /^\d+$/.test(t[1]) ? String(Number(t[1])) : t[1]
  return { campus: t[0], haus, etage: t[2], raum: t.slice(3).join(".") }
}

function etageText(e) {
  if (e === "0") return "Erdgeschoss"
  if (/^-?\d+$/.test(e)) return Number(e) > 0 ? e + ". Obergeschoss" : Math.abs(Number(e)) + ". Untergeschoss"
  return "Ebene " + e
}

// ───────────────────────── Campus (für Mensa- und Abfahrten-Script) ─────────────────────────
// Einstellung: "auto" (nach Stundenplan) oder "1"/"2"/"3". Das Ergebnis wird für die anderen
// Scripts in iCloud Drive › Scriptable › stundenplan-campus.json abgelegt.

function campusAutomatisch(alle, jetzt) {
  const mit = alle.filter(t => !t.ausfall && raumAufteilen(t.raum) && CAMPUS[raumAufteilen(t.raum).campus])
    .sort((a, b) => a.start - b.start)
  const c = t => raumAufteilen(t.raum).campus
  const heute = mit.filter(t => gleicherTag(t.start, jetzt))
  const laufend = heute.find(t => t.ende > jetzt)
  if (laufend) return c(laufend)
  const letzter = heute[heute.length - 1]
  if (letzter && jetzt - letzter.ende < 2 * 3600000) return c(letzter)
  const naechster = mit.find(t => t.start > jetzt)
  if (naechster) return c(naechster)
  const anzahl = {}
  for (const t of mit) anzahl[c(t)] = (anzahl[c(t)] || 0) + 1
  const sortiert = Object.entries(anzahl).sort((a, b) => b[1] - a[1])
  return sortiert.length ? sortiert[0][0] : "2"
}

function campusAktuell(alle, jetzt) {
  const w = einstellungen().campus || "auto"
  return CAMPUS[w] ? w : campusAutomatisch(alle, jetzt || new Date())
}

function campusTeilen(alle) {
  try {
    const e = einstellungen()
    const daten = {
      wahl: e.campus || "auto",
      campus: campusAktuell(alle, new Date()),
      favoriten: e.favoriten || [],
      stand: new Date().toISOString(),
    }
    let fm
    try { fm = FileManager.iCloud() } catch (x) { fm = FileManager.local() }
    fm.writeString(fm.joinPath(fm.documentsDirectory(), "stundenplan-campus.json"), JSON.stringify(daten, null, 2))
  } catch (e) {}
}

// ───────────────────────── Verbindungen bei Campuswechsel ─────────────────────────

async function verbindungenSuchen(a, b) {
  const p = gpsOderCampus(a.raum), q = gpsOderCampus(b.raum)
  if (!p || !q) return null
  const ra = raumAufteilen(a.raum), rb = raumAufteilen(b.raum)
  const ab = new Date(Math.max(a.ende.getTime(), Date.now())).toISOString()
  const query = "from.latitude=" + p[0] + "&from.longitude=" + p[1]
    + "&from.address=" + encodeURIComponent("Uni Potsdam " + CAMPUS[ra.campus] + " Haus " + ra.haus)
    + "&to.latitude=" + q[0] + "&to.longitude=" + q[1]
    + "&to.address=" + encodeURIComponent("Uni Potsdam " + CAMPUS[rb.campus] + " Haus " + rb.haus)
    + "&departure=" + encodeURIComponent(ab) + "&results=3&stopovers=false&remarks=false&polylines=false&tickets=false"
  for (const quelle of ["https://v6.vbb.transport.rest", "https://v6.bvg.transport.rest"]) {
    for (let i = 0; i < 2; i++) {
      try {
        const r = new Request(quelle + "/journeys?" + query)
        r.timeoutInterval = 12
        const d = await r.loadJSON()
        if (d && Array.isArray(d.journeys)) {
          return d.journeys.map(j => j.legs.map(l => ({
            zuFuss: !!l.walking,
            linie: l.line ? l.line.name : null,
            produkt: l.line ? l.line.product : null,
            von: l.origin ? l.origin.name : null,
            nach: l.destination ? l.destination.name : null,
            gleis: l.departurePlatform || l.plannedDeparturePlatform || null,
            ab: new Date(l.departure || l.plannedDeparture),
            an: new Date(l.arrival || l.plannedArrival),
          })).filter(l => !isNaN(l.ab) && !isNaN(l.an))).filter(v => v.length)
        }
      } catch (e) {}
    }
  }
  return null
}

function kurzOrt(s) {
  let t = s || ""
  for (const v of ["Potsdam, ", "S+U ", "S "]) if (t.startsWith(v)) t = t.slice(v.length)
  return t
}

function verbindungHtml(a, b, liste) {
  const rb = raumAufteilen(b.raum)
  const zeit = Math.round((b.start - a.ende) / 60000)
  let h = `<div class="verb"><div class="vk">🚆 Campuswechsel nach ${htmlSchutz(CAMPUS[rb.campus])}<span>${zeit} Min Zeit</span></div>`
  if (liste === null) h += `<div class="vz">Verbindungssuche gerade nicht erreichbar.</div>`
  else if (!liste.length) h += `<div class="vz">Keine Verbindung gefunden.</div>`
  else {
    let offen = false
    for (const v of liste) {
      const ab = v[0].ab, an = v[v.length - 1].an
      const puenktlich = an <= b.start
      const linien = v.filter(l => l.linie).map(l => l.linie).join(" → ") || "zu Fuß"
      h += `<details${!offen && puenktlich ? " open" : ""}><summary><b>${uhrzeit(ab)} → ${uhrzeit(an)}</b> ${htmlSchutz(linien)} `
        + `<span class="${puenktlich ? "" : "spaet"}">${puenktlich ? Math.round((an - ab) / 60000) + " Min" : "zu spät"}</span></summary>`
      for (const l of v) {
        const min = Math.max(1, Math.round((l.an - l.ab) / 60000))
        const text = l.zuFuss
          ? `${min} Min zu Fuß ${l.nach ? "zu " + kurzOrt(l.nach) : "zum Haus"}`
          : `${uhrzeit(l.ab)} ${l.linie || "?"} ab ${kurzOrt(l.von)}${l.gleis ? " (Gl. " + l.gleis + ")" : ""} → ${uhrzeit(l.an)} ${l.nach ? kurzOrt(l.nach) : "Ziel"}`
        h += `<div class="vz">${l.zuFuss ? "🚶" : (l.produkt === "bus" ? "🚌" : "🚆")} ${htmlSchutz(text)}</div>`
      }
      h += `</details>`
      if (puenktlich) offen = true
    }
  }
  const link = oepnvLink(a.raum, b.raum)
  if (link) h += `<a class="vl" href="${htmlSchutz(link)}">In Apple Karten mit Bus &amp; Bahn</a>`
  return h + `</div>`
}

// Link, der Scriptable öffnet und den Lageplan zeigt
function lageplanLink(parameter) {
  if (PROFIL && !parameter.profil) parameter = Object.assign({ profil: PROFIL }, parameter)
  const q = Object.entries(parameter).map(([k, v]) => k + "=" + encodeURIComponent(v)).join("&")
  return "scriptable:///run/" + encodeURIComponent(Script.name()) + "?" + q
}

function lageplanLinkTermin(t) {
  return lageplanLink({ termin: t.start.getTime() })
}

function lageplanLinkTag(datum) {
  const d = new Date(datum)
  d.setHours(0, 0, 0, 0)
  return lageplanLink({ tag: d.getTime() })
}

function lageplanBild(plan, campus, markierungen) {
  const B = plan.size.width, H = plan.size.height
  const ctx = new DrawContext()
  ctx.size = new Size(B, H)
  ctx.respectScreenScale = false
  ctx.opaque = true
  ctx.setFillColor(Color.white())
  ctx.fillRect(new Rect(0, 0, B, H))
  ctx.drawImageInRect(plan, new Rect(0, 0, B, H))
  const geb = GEBAEUDE[campus] || {}
  // Mehrere Termine im selben Haus zusammenfassen
  const proHaus = {}
  for (const m of markierungen) {
    if (m.campus !== campus || !geb[m.haus]) continue
    ;(proHaus[m.haus] = proHaus[m.haus] || []).push(m)
  }
  for (const [haus, liste] of Object.entries(proHaus)) {
    const [fx, fy] = geb[haus]
    const x = fx * B, y = fy * H
    // Farbe des Fachs (bei mehreren Terminen im Haus: der erste)
    const rot = new Color(liste[0].farbe || "#E24B4A")
    const ring = new Path()
    ring.addEllipse(new Rect(x - 46, y - 46, 92, 92))
    ctx.addPath(ring)
    ctx.setFillColor(new Color(liste[0].farbe || "#E24B4A", 0.25))
    ctx.fillPath()
    ctx.addPath(ring)
    ctx.setStrokeColor(rot)
    ctx.setLineWidth(7)
    ctx.strokePath()
    // Beschriftung (Nummern oder Raum) als Schild
    const text = liste.map(m => m.label).join(" · ")
    ctx.setFont(Font.boldSystemFont(30))
    const breite = Math.min(520, 26 + text.length * 17)
    const sx = Math.max(6, Math.min(B - breite - 6, x - breite / 2))
    const sy = y - 46 - 52 < 4 ? y + 52 : y - 46 - 52
    const schild = new Path()
    schild.addRoundedRect(new Rect(sx, sy, breite, 46), 23, 23)
    ctx.addPath(schild)
    ctx.setFillColor(rot)
    ctx.fillPath()
    ctx.setTextColor(Color.white())
    ctx.setTextAlignedCenter()
    ctx.drawTextInRect(text, new Rect(sx, sy + 6, breite, 40))
  }
  return ctx.getImage()
}

function htmlSchutz(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;")
}

async function zeigeLageplan(alle, parameter) {
  let termine = []
  let titel
  if (parameter.termin) {
    const zeit = Number(parameter.termin)
    termine = alle.filter(t => t.start.getTime() === zeit)
    titel = termine.length ? datumKurz(termine[0].start) : "Termin"
  } else if (parameter.tag) {
    const tag = new Date(Number(parameter.tag))
    termine = termineAm(alle, tag)
    titel = datumKurz(tag)
  }

  // Verbindungen für jeden Campuswechsel (parallel)
  const wechsel = await Promise.all(termine.map((t, i) =>
    i > 0 && !t.ausfall && !termine[i - 1].ausfall && campusWechsel(termine[i - 1].raum, t.raum)
      ? verbindungenSuchen(termine[i - 1], t).catch(() => null) : Promise.resolve(undefined)))

  const markierungen = []
  const karten = termine.map((t, i) => {
    const kurs = kursInfo(t.titel)
    const typ = typInfo(t.typ)
    const r = raumAufteilen(t.raum)
    const nummer = termine.length > 1 ? String(i + 1) : null
    let ort = htmlSchutz(t.raum)
    let einrichtung = ""
    if (r) {
      const bekannt = !!CAMPUS[r.campus]
      ort = (bekannt ? CAMPUS_KURZ[r.campus] + " · " : "") + "Haus " + htmlSchutz(r.haus) + " · Raum " + htmlSchutz(r.etage + "." + r.raum) + " · " + etageText(r.etage)
      if (bekannt) {
        if (!GEBAEUDE[r.campus][r.haus]) ort += " · nicht auf dem Plan"
        else if (!t.ausfall) markierungen.push({ campus: r.campus, haus: r.haus, farbe: kurs.farbe, label: nummer || ("Raum " + r.etage + "." + r.raum) })
        const e = EINRICHTUNGEN[r.campus][r.haus]
        if (e) einrichtung = e.map(htmlSchutz).join(" · ")
      }
    }
    // Weg vom vorherigen Termin: Verbindungen bei Campuswechsel, sonst Gehzeit bei Hauswechsel
    let weg = ""
    if (i > 0 && !t.ausfall && !termine[i - 1].ausfall) {
      if (wechsel[i] !== undefined) {
        weg = verbindungHtml(termine[i - 1], t, wechsel[i])
      } else {
        const min = gehminuten(termine[i - 1].raum, t.raum)
        if (min) {
          const pause = Math.round((t.start - termine[i - 1].ende) / 60000)
          weg = `<div class="weg">🚶 ca. ${min} Min zu Haus ${htmlSchutz(r.haus)}${pause > 0 ? ` (${pause} Min Pause)` : ""}</div>`
        }
      }
    }
    const route = t.ausfall ? null : routeLink(t.raum)
    const aendern = "scriptable:///run/" + encodeURIComponent(Script.name()) + "?aendern=" + t.start.getTime()
    return `${weg}<div class="karte${t.ausfall ? " aus" : ""}" style="background:${kurs.farbe}">
      <div class="oben">${nummer ? `<span class="nr">${nummer}</span>` : ""}${htmlSchutz(typ.lang)} · ${uhrzeit(t.start)}–${uhrzeit(t.ende)}${t.ausfall ? ' <span class="marke">FÄLLT AUS</span>' : ""}</div>
      <div class="titel">${htmlSchutz(t.titel)}</div>
      <div class="ort">${ort}</div>
      ${t.alterRaum ? `<div class="einr">Geändert, ursprünglich ${htmlSchutz(t.alterRaum)}</div>` : ""}
      ${einrichtung ? `<div class="einr">${einrichtung}</div>` : ""}
      <div class="knoepfe">${route ? `<a class="k" href="${htmlSchutz(route)}">🚶 Route</a>` : ""}${PROFIL ? "" : `<a class="k" href="${htmlSchutz(aendern)}">✎ Ändern</a>`}</div>
    </div>`
  }).join("")

  // Pläne: gewählter Campus, sonst die Campus der Termine (in Reihenfolge), sonst der aktuelle
  let campusListe = []
  if (CAMPUS[parameter.plan]) campusListe = [parameter.plan]
  else {
    for (const m of markierungen) if (!campusListe.includes(m.campus)) campusListe.push(m.campus)
    if (!campusListe.length) campusListe = [campusAktuell(alle, new Date())]
  }
  let bilder = ""
  for (const c of campusListe) {
    const plan = await planBild(c)
    if (campusListe.length > 1 || !termine.length) bilder += `<h2>Campus ${htmlSchutz(CAMPUS[c])}</h2>`
    if (plan) bilder += `<img class="plan" src="data:image/png;base64,${Data.fromPNG(lageplanBild(plan, c, markierungen)).toBase64String()}">`
    else bilder += `<p class="leer">Plan ${htmlSchutz(CAMPUS[c])} konnte nicht geladen werden (einmalig Internet nötig).</p>`
  }
  const basis = Object.assign({}, parameter)
  delete basis.plan
  const wahl = Object.keys(CAMPUS).sort((a, b) => (a === "2" ? -1 : b === "2" ? 1 : a - b))
    .map(c => `<a href="${htmlSchutz(lageplanLink(Object.assign({}, basis, { plan: c })))}">${CAMPUS_KURZ[c]}</a>`).join("")
    + (termine.length ? `<a href="${htmlSchutz(lageplanLink(Object.keys(basis).length ? basis : { tag: new Date().setHours(0, 0, 0, 0) }))}">passend</a>` : "")
  const planerLink = tippLink()
  const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=6">
<style>
  :root { color-scheme: light dark; }
  body { font-family: -apple-system, system-ui; margin: 0; padding: 16px; background: #FFFFFF; color: #2C2C2A; overflow-wrap: anywhere; }
  @media (prefers-color-scheme: dark) { body { background: #1C1C1E; color: #F1EFE8; } .plan { border-color: #3A3A3C; } .verb { background: #2C2C2E !important; } }
  h1 { font-size: 22px; margin: 4px 0 12px; }
  h2 { font-size: 17px; margin: 14px 0 4px; }
  .karte { border-radius: 14px; padding: 12px 14px; color: #fff; margin-bottom: 10px; }
  .aus { opacity: .5; } .aus .titel { text-decoration: line-through; }
  .marke { background: rgba(255,255,255,.25); border-radius: 8px; padding: 1px 6px; font-size: 11px; }
  .oben { font-size: 13px; opacity: .9; font-weight: 600; }
  .nr { display: inline-block; background: #fff; color: #E24B4A; border-radius: 10px; min-width: 20px; text-align: center; margin-right: 6px; }
  .titel { font-size: 18px; font-weight: 700; margin: 3px 0; }
  .ort { font-size: 15px; font-weight: 600; }
  .einr { font-size: 13px; opacity: .85; margin-top: 4px; }
  .knoepfe { margin-top: 10px; display: flex; flex-wrap: wrap; gap: 8px; }
  .k { background: rgba(255,255,255,.22); color: #fff; text-decoration: none; border-radius: 16px; padding: 6px 12px; font-size: 14px; font-weight: 600; }
  .weg { font-size: 13px; color: #888780; margin: -4px 0 8px 4px; }
  .verb { background: #F1EFE8; border-radius: 12px; padding: 10px 12px; margin: -2px 0 10px; font-size: 13px; }
  .vk { font-weight: 700; margin-bottom: 6px; } .vk span { float: right; font-weight: 400; color: #888780; }
  details { margin: 4px 0; } .vz { color: #888780; font-size: 12px; margin: 3px 0 0 6px; } .spaet { color: #E24B4A; }
  a.vl { display: inline-block; margin-top: 8px; color: #185FA5; font-weight: 600; text-decoration: none; }
  .plan { width: 100%; border-radius: 14px; border: 1px solid #D3D1C7; background: #fff; margin-top: 6px; }
  .leer { color: #888780; }
  .wahl { font-size: 13px; color: #888780; margin-top: 12px; } .wahl a { margin-left: 10px; color: #185FA5; font-weight: 600; text-decoration: none; }
  a.knopf { display: block; text-align: center; margin: 14px 0 4px; padding: 12px; border-radius: 12px; background: #185FA5; color: #fff; text-decoration: none; font-weight: 600; }
  .hinweis { font-size: 12px; color: #888780; text-align: center; margin-top: 8px; }
</style></head><body>
<h1>${htmlSchutz(titel || "Lageplan")}</h1>
${karten || '<p class="leer">Keine Termine</p>'}
${bilder}
<p class="hinweis">Zum Vergrößern mit zwei Fingern zoomen.</p>
<div class="wahl">Plan:${wahl}</div>
${planerLink ? `<a class="knopf" href="${htmlSchutz(planerLink)}">Im Planer öffnen</a>` : ""}
</body></html>`
  const wv = new WebView()
  // Links (Route, Ändern, Planer, Plan wechseln) nicht in der Ansicht laden, sondern an iOS weitergeben:
  // maps:// öffnet Apple Karten, scriptable:// dieses Script, https den Browser.
  wv.shouldAllowRequest = req => {
    const u = req.url || ""
    if (/^(https?|maps|scriptable):/i.test(u)) {
      Safari.open(u)
      return false
    }
    return true
  }
  await wv.loadHTML(html)
  await wv.present(true)
}

// ───────────────────────── Einstellungsmenü ─────────────────────────

const VARIANTEN_NAMEN = {
  naechste: "Nächster Termin", raum: "Raum groß", countdown: "Countdown", tag: "Tagesübersicht", zeit: "Uhrzeit",
}

async function auswahlMenue(titel, optionen, aktuell) {
  const a = new Alert()
  a.title = titel
  optionen.forEach(([wert, name]) => a.addAction((wert === aktuell ? "✓ " : "") + name))
  a.addCancelAction("Abbrechen")
  const i = await a.presentSheet()
  return i >= 0 ? optionen[i][0] : undefined
}

function weckerText(m) {
  const h = Math.floor(m / 60), r = m % 60
  return h === 0 ? m + " Min" : r === 0 ? h + " Std" : h + " Std " + r + " Min"
}

function uhrText(m) {
  return Math.floor(m / 60) + ":" + String(m % 60).padStart(2, "0")
}

// ───────────────────────── Datei, Teilen, Profile ─────────────────────────

async function dateiWaehlen() {
  try {
    const pfade = await DocumentPicker.open(["public.calendar-event", "com.apple.ical.ics", "public.data"])
    if (!pfade || !pfade.length) return null
    return FileManager.local().readString(pfade[0])
  } catch (e) {
    return null
  }
}

function kopfWert(text, name) {
  const z = text.replace(/\r\n/g, "\n").replace(/\n[ \t]/g, "").split("\n")
  for (const zeile of z) {
    if (zeile === "BEGIN:VEVENT") break
    if (zeile.startsWith(name + ":")) return zeile.slice(name.length + 1)
  }
  return null
}

async function eigeneDateiWaehlen() {
  const text = await dateiWaehlen()
  if (!text) return
  const n = parseICS(text).length
  const a = new Alert()
  if (!n) {
    a.title = "Keine Termine gefunden"
    a.addAction("OK")
    await a.presentAlert()
    return
  }
  const fm = dateiManager()
  fm.writeString(fm.joinPath(fm.documentsDirectory(), CONFIG.ICS_DATEI), text)
  a.title = n + " Termine übernommen"
  a.message = "Die Widgets zeigen ab jetzt diesen Plan."
  a.addAction("OK")
  await a.presentAlert()
  await erinnerungenPlanen(true)
}

function icsZeit(d) {
  return d.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "")
}

function icsMaskiert(s) {
  return String(s).replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,").replace(/\n/g, "\\n")
}

function gefaltet(zeile) {
  const teile = []
  for (let i = 0, erste = true; i < zeile.length; erste = false) {
    const n = erste ? 73 : 72
    teile.push((erste ? "" : " ") + zeile.slice(i, i + n))
    i += n
  }
  return teile.join("\r\n")
}

// Eigenen Plan als .ics teilen (gleiches Format wie die App; die PULS-Auswahl aus der Datei bleibt erhalten)
async function planTeilen() {
  const e = einstellungen()
  const roh = await ladeTermine(null)
  if (!roh.length) {
    const a = new Alert()
    a.title = "Kein Stundenplan zum Teilen"
    a.addAction("OK")
    await a.presentAlert()
    return
  }
  const aend = aenderungen()
  const original = (await dateiLesen(CONFIG.ICS_DATEI)) || ""
  const freigabe = kopfWert(original, "X-STUNDENPLAN-FREIGABE")
  const z = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Stundenplan Uni Potsdam//DE", "CALSCALE:GREGORIAN",
    "X-WR-CALNAME:" + icsMaskiert(e.name ? "Stundenplan von " + e.name : "Stundenplan")]
  if (freigabe) z.push("X-STUNDENPLAN-FREIGABE:" + freigabe)
  const stempel = icsZeit(new Date())
  roh.forEach((t, i) => {
    const a = aend[schluessel(viertelAnpassen(t))]
    if (a && a.ausfall) return
    const raum = a && a.raum ? a.raum : t.raum
    z.push("BEGIN:VEVENT", "UID:" + Math.floor(t.start.getTime() / 1000) + "-" + i + "@stundenplan", "DTSTAMP:" + stempel,
      "DTSTART:" + icsZeit(t.start), "DTEND:" + icsZeit(t.ende), "SUMMARY:" + icsMaskiert(t.titel),
      "DESCRIPTION:" + icsMaskiert(t.typ), "LOCATION:" + icsMaskiert(raum), "END:VEVENT")
  })
  z.push("END:VCALENDAR")
  const fm = FileManager.local()
  const pfad = fm.joinPath(fm.temporaryDirectory(), (e.name ? "Stundenplan " + e.name : "Stundenplan").replace(/[^\p{L}\p{N} -]/gu, "") + ".ics")
  fm.writeString(pfad, z.map(gefaltet).join("\r\n") + "\r\n")
  // Vorschau mit Teilen-Knopf (AirDrop, Nachrichten, Dateien …)
  await QuickLook.present(pfad, false)
}

function profilListe() {
  return einstellungen().profile || []
}

function kuerzelAus(name) {
  const k = name.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]/g, "")
  return k || "profil"
}

async function profileMenue() {
  while (true) {
    const profile = profilListe()
    const a = new Alert()
    a.title = "Profile"
    a.message = "Pläne anderer. Ein Widget zeigt ein Profil mit dem Parameter profil=<kürzel>." +
      (profile.length ? "" : "\n\nNoch keine Profile.")
    profile.forEach(p => a.addAction(p.name + "  (profil=" + p.kuerzel + ")"))
    a.addAction("＋ Profil hinzufügen (.ics-Datei)")
    a.addCancelAction("Fertig")
    const i = await a.presentSheet()
    if (i < 0) return
    if (i === profile.length) { await profilHinzufuegen(); continue }
    const p = profile[i]
    const m = new Alert()
    m.title = p.name
    m.addAction("Wochenansicht zeigen")
    m.addAction("Umbenennen")
    m.addAction("Datei ersetzen")
    m.addDestructiveAction("Löschen")
    m.addCancelAction("Zurück")
    const j = await m.presentSheet()
    const e = Object.assign({}, einstellungen())
    if (j === 0) {
      const alle = await ladeAlle(false, p.kuerzel)
      const w = grossesWidget(alle, new Date(), themenFarben())
      await w.presentLarge()
    } else if (j === 1) {
      const l = new Alert()
      l.title = "Name"
      l.addTextField("Name", p.name)
      l.addAction("Speichern")
      l.addCancelAction("Abbrechen")
      if (await l.presentAlert() === 0 && l.textFieldValue(0).trim()) {
        e.profile = profile.map(x => x.kuerzel === p.kuerzel ? Object.assign({}, x, { name: l.textFieldValue(0).trim() }) : x)
        einstellungenSpeichern(e)
      }
    } else if (j === 2) {
      const text = await dateiWaehlen()
      if (text && parseICS(text).length) {
        const fm = dateiManager()
        fm.writeString(fm.joinPath(fm.documentsDirectory(), profilDatei(p.kuerzel)), text)
      }
    } else if (j === 3) {
      e.profile = profile.filter(x => x.kuerzel !== p.kuerzel)
      einstellungenSpeichern(e)
      try {
        const fm = dateiManager()
        fm.remove(fm.joinPath(fm.documentsDirectory(), profilDatei(p.kuerzel)))
      } catch (err) {}
    }
  }
}

async function profilHinzufuegen() {
  const text = await dateiWaehlen()
  if (!text) return
  if (!parseICS(text).length) {
    const a = new Alert()
    a.title = "Keine Termine gefunden"
    a.addAction("OK")
    await a.presentAlert()
    return
  }
  let vorschlag = (kopfWert(text, "X-WR-CALNAME") || "").replace(/\\,/g, ",").replace(/^Stundenplan von /, "")
  if (vorschlag === "Stundenplan") vorschlag = ""
  const l = new Alert()
  l.title = "Wessen Plan?"
  l.addTextField("Name", vorschlag)
  l.addAction("Speichern")
  l.addCancelAction("Abbrechen")
  if (await l.presentAlert() !== 0) return
  const name = l.textFieldValue(0).trim() || "Ohne Namen"
  const e = Object.assign({}, einstellungen())
  const profile = e.profile || []
  let kuerzel = kuerzelAus(name), n = 2
  while (profile.some(p => p.kuerzel === kuerzel)) kuerzel = kuerzelAus(name) + n++
  const fm = dateiManager()
  fm.writeString(fm.joinPath(fm.documentsDirectory(), profilDatei(kuerzel)), text)
  e.profile = profile.concat([{ name, kuerzel }])
  einstellungenSpeichern(e)
  const a = new Alert()
  a.title = "Profil gespeichert"
  a.message = "Widget-Parameter für dieses Profil: profil=" + kuerzel
  a.addAction("OK")
  await a.presentAlert()
}

async function zeigeEinstellungen() {
  const e = Object.assign({}, einstellungen())
  while (true) {
    const themaName = { auto: "Automatisch", hell: "Hell", dunkel: "Dunkel", transparent: "Transparent" }[e.thema || "auto"]
    const linkName = e.link ? "Eigener Link" : "Standard (leere Planer-Seite)"
    const eintraege = [
      ["thema", "Farbschema: " + themaName],
      ["link", "Planer-Link (im Lageplan): " + linkName],
      ["inline", "Sperrbildschirm Zeile: " + VARIANTEN_NAMEN[e.inline || "naechste"]],
      ["rechteck", "Sperrbildschirm Rechteck: " + VARIANTEN_NAMEN[e.rechteck || "naechste"]],
      ["rund", "Sperrbildschirm Rund: " + VARIANTEN_NAMEN[e.rund || "countdown"]],
      ["name", "Dein Name (beim Teilen): " + (e.name || "–")],
      ["erinnerungen", "Erinnerungen: " + (e.erinnerungen ? "an (" + (e.vorlauf || 10) + " Min vorher)" : "aus")],
      ["wecker", "Wecker: " + (e.wecker ? weckerText(e.wecker) + " vor der ersten Veranstaltung" + (e.weckerFruehestens ? ", frühestens " + uhrText(e.weckerFruehestens) : "") : "aus")],
      ["campus", "Campus (Mensa/Abfahrten): " + (CAMPUS[e.campus] || "Automatisch")],
      ["favoriten", "Priorisierte Linien: " + ((e.favoriten || []).join(", ") || "keine")],
    ]
    const a = new Alert()
    a.title = "Einstellungen"
    a.message = "Gilt für alle Widgets. Ein Widget-Parameter hat trotzdem Vorrang."
    eintraege.forEach(([, name]) => a.addAction(name))
    a.addCancelAction("Fertig")
    const i = await a.presentSheet()
    if (i < 0) break
    const schluessel = eintraege[i][0]
    if (schluessel === "thema") {
      const w = await auswahlMenue("Farbschema", [["auto", "Automatisch"], ["hell", "Hell"], ["dunkel", "Dunkel"], ["transparent", "Transparent (ohne Hintergrund)"]], e.thema || "auto")
      if (w !== undefined) e.thema = w
    } else if (schluessel === "erinnerungen") {
      const w = await auswahlMenue("Erinnerung vor Veranstaltungen",
        [["aus", "Aus"], [5, "5 Minuten vorher"], [10, "10 Minuten vorher"], [15, "15 Minuten vorher"],
         [20, "20 Minuten vorher"], [30, "30 Minuten vorher"], [60, "60 Minuten vorher"]],
        e.erinnerungen ? (e.vorlauf || 10) : "aus")
      if (w === "aus") e.erinnerungen = false
      else if (w !== undefined) { e.erinnerungen = true; e.vorlauf = w }
    } else if (schluessel === "name") {
      const l = new Alert()
      l.title = "Dein Name"
      l.message = "Steht auf deinem geteilten Plan."
      l.addTextField("Name", e.name || "")
      l.addAction("Speichern")
      l.addCancelAction("Abbrechen")
      if (await l.presentAlert() === 0) e.name = l.textFieldValue(0).trim()
    } else if (schluessel === "wecker") {
      const w = await auswahlMenue("Wecker vor der ersten Veranstaltung (Mitteilung mit Ton)",
        [["aus", "Aus"]].concat([30, 45, 60, 75, 90, 105, 120, 150, 180].map(m => [m, weckerText(m) + " vorher"])),
        e.wecker || "aus")
      if (w === "aus") e.wecker = 0
      else if (w !== undefined) {
        e.wecker = w
        const f = await auswahlMenue("Frühestens um",
          [[0, "egal"]].concat([300, 330, 360, 390, 420, 450].map(m => [m, uhrText(m)])), e.weckerFruehestens || 0)
        if (f !== undefined) e.weckerFruehestens = f
      }
    } else if (schluessel === "campus") {
      const w = await auswahlMenue("Campus für Mensa- und Abfahrten-Widget",
        [["auto", "Automatisch (nach Stundenplan)"], ["2", "Golm"], ["1", "Am Neuen Palais"], ["3", "Griebnitzsee"]], e.campus || "auto")
      if (w !== undefined) e.campus = w
    } else if (schluessel === "favoriten") {
      const l = new Alert()
      l.title = "Priorisierte Linien"
      l.message = "Stehen im Abfahrten-Widget immer mit drin. Mehrere mit Komma trennen, z. B. „RB20, 605“."
      l.addTextField("RB20", (e.favoriten || []).join(", "))
      l.addAction("Speichern")
      l.addCancelAction("Abbrechen")
      if (await l.presentAlert() === 0) {
        e.favoriten = l.textFieldValue(0).split(/[,;\s]+/).map(x => x.trim().toUpperCase()).filter(Boolean)
      }
    } else if (schluessel === "link") {
      const l = new Alert()
      l.title = "Planer-Link"
      l.message = "Im Planer auf „Plan teilen“ tippen, Link kopieren und hier einfügen."
      l.addTextField("https://planner.joshuan.dev/de?s=…", e.link || "")
      l.addAction("Speichern")
      l.addAction("Standard verwenden")
      l.addCancelAction("Abbrechen")
      const j = await l.presentAlert()
      if (j === 0) {
        const wert = l.textFieldValue(0).trim()
        e.link = /^https?:\/\//i.test(wert) ? wert : ""
      } else if (j === 1) e.link = ""
    } else {
      const optionen = {
        inline: ["naechste", "raum", "countdown"],
        rechteck: ["naechste", "raum", "tag"],
        rund: ["countdown", "raum", "zeit"],
      }[schluessel].map(v => [v, VARIANTEN_NAMEN[v]])
      const standard = schluessel === "rund" ? "countdown" : "naechste"
      const w = await auswahlMenue(eintraege[i][1].split(":")[0], optionen, e[schluessel] || standard)
      if (w !== undefined) e[schluessel] = w
    }
    einstellungenSpeichern(e)
    campusTeilen(await ladeAlle(false))
  }
  await erinnerungenPlanen(true)
}

// ───────────────────────── Sperrbildschirm (iPhone, iOS 16+) ─────────────────────────
// Varianten über den Widget-Parameter wählbar (siehe Kopf des Scripts).

function sperrVariante(erlaubt, standard, schluessel) {
  const teile = (args.widgetParameter || "").trim().toLowerCase().split(/\s+/)
  const gespeichert = einstellungen()[schluessel]
  return teile.find(t => erlaubt.includes(t)) || (erlaubt.includes(gespeichert) ? gespeichert : standard)
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

// Tippen auf ein Sperrbildschirm-Widget öffnet den Lageplan des nächsten Termins
function sperrWidget(termin) {
  const w = new ListWidget()
  if (termin) w.url = lageplanLinkTermin(termin)
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

// ── Zeile über der Uhr ── (teilt sich den Platz mit dem Datum → so kurz wie möglich)
function raumKurz(raum) {
  const r = raumAufteilen(raum)
  return r ? r.haus + "." + r.etage + (r.raum ? "." + r.raum : "") : raum
}

function sperrInline(alle, jetzt) {
  const t = naechsterTermin(alle, jetzt)
  if (!t) { const w = sperrWidget(); w.addText("Frei"); return w }
  const variante = sperrVariante(["naechste", "raum", "countdown"], "naechste", "inline")
  const kurs = kursInfo(t.titel).kurz
  const laufend = t.start <= jetzt
  const wann = gleicherTag(t.start, jetzt) ? uhrzeit(t.start) : WOCHENTAGE_LANG[t.start.getDay()] + " " + uhrzeit(t.start)
  let text
  if (variante === "raum") {
    text = raumKurz(t.raum) + (laufend ? " bis " + uhrzeit(t.ende) : " · " + wann)
  } else if (variante === "countdown" && (laufend || gleicherTag(t.start, jetzt))) {
    const min = Math.max(0, Math.ceil(((laufend ? t.ende : t.start) - jetzt) / 60000))
    const dauer = min < 60 ? min + " Min" : Math.floor(min / 60) + ":" + String(min % 60).padStart(2, "0") + " h"
    text = kurs + (laufend ? " noch " : " in ") + dauer
  } else {
    text = kurs + (laufend ? " bis " + uhrzeit(t.ende) : " " + wann)
  }
  const w = sperrWidget(t)
  w.addText(text)
  return w
}

// ── Rechteckig unter der Uhr ──
function sperrRechteck(alle, jetzt) {
  const variante = sperrVariante(["naechste", "raum", "tag"], "naechste", "rechteck")
  const w = sperrWidget()
  w.setPadding(2, 4, 2, 4)

  if (variante === "tag") {
    // Die nächsten (bis zu 3) Termine des Tages
    const auswahl = tagesAuswahl(alle, jetzt)
    if (!auswahl) return leerSperr("accessoryRectangular")
    w.url = lageplanLinkTag(auswahl.datum)
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
  w.url = lageplanLinkTermin(t)
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
  const variante = sperrVariante(["countdown", "raum", "zeit"], "countdown", "rund")
  const kurs = kursInfo(t.titel).kurz
  const laufend = t.start <= jetzt
  const w = sperrWidget(t)
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

// Lageplan Golm (PNG, Base64) – aus „2025_Lageplan_mit_Legende_Golm.pdf“
const LAGEPLAN_PNG = "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"

// ───────────────────────── Start ─────────────────────────

let WECKER_NAECHSTER = null

async function erstelleWidget(familie) {
  const jetzt = new Date()
  const alle = await ladeAlle(false)
  const ew = einstellungen()
  if (ew.wecker && !PROFIL) WECKER_NAECHSTER = weckzeiten(alle, ew.wecker, ew.weckerFruehestens || 0, jetzt)[0] || null
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
  if (!PROFIL) campusTeilen(alle)
  return w
}

const aufruf = args.queryParameters || {}
if (config.runsInWidget) {
  Script.setWidget(await erstelleWidget(config.widgetFamily))
  try { await erinnerungenPlanen(false) } catch (e) {}
} else if (config.runsWithSiri) {
  await siriAntwort()
} else if (aufruf.aendern) {
  // „Ändern“ im Lageplan
  const alle = await ladeAlle(true)
  const t = alle.find(x => x.start.getTime() === Number(aufruf.aendern))
  if (t && await aendernMenue(t, alle)) {
    await erinnerungenPlanen(true)
    // Zeit evtl. geändert: Termin über den gleichbleibenden Schlüssel wiederfinden
    const neu = await ladeAlle(true)
    const t2 = neu.find(x => schluessel(x) === schluessel(t))
    await zeigeLageplan(neu, { termin: String((t2 || t).start.getTime()) })
  }
} else if (aufruf.termin || aufruf.tag) {
  // Aus einem Widget oder einer Erinnerung heraus geöffnet: Lageplan zeigen
  const alle = await ladeAlle(true)
  await zeigeLageplan(alle, aufruf)
} else {
  await erinnerungenPlanen(true)
  campusTeilen(await ladeAlle(false))
  const start = new Alert()
  start.title = "Stundenplan"
  start.addAction("Widget-Vorschau")
  start.addAction("Einstellungen")
  start.addAction("Lageplan für heute")
  start.addAction("Termin ändern (fällt aus / Raum / Zeit)")
  start.addAction("Im Planer öffnen")
  start.addAction("Stundenplan-Datei wählen (.ics)")
  start.addAction("Meinen Plan teilen (.ics)")
  start.addAction("Profile (Pläne anderer)")
  start.addCancelAction("Abbrechen")
  const wahl = await start.presentSheet()
  if (wahl === 5) await eigeneDateiWaehlen()
  if (wahl === 6) await planTeilen()
  if (wahl === 7) await profileMenue()
  if (wahl === 1) await zeigeEinstellungen()
  if (wahl === 2) {
    const alle = await ladeAlle(true)
    await zeigeLageplan(alle, { tag: new Date().setHours(0, 0, 0, 0) })
  }
  if (wahl === 3) await terminAendernMenue()
  if (wahl === 4) Safari.open(tippLink())
  if (wahl === 0) {
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
}
Script.complete()
