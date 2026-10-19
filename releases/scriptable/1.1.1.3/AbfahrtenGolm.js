// Variables used by Scriptable.
// These must be at the very top of the file. Do not edit.
// icon-color: purple; icon-glyph: bus;
// Abfahrten-Widget für Scriptable – Uni Potsdam (Golm, Am Neuen Palais, Griebnitzsee)
// Daten: VBB über v6.vbb.transport.rest (frei, ohne Anmeldung), mit Echtzeit/Verspätungen.
// Größen: Klein (3 Abfahrten), Mittel (4), Groß (bis 10). Die Minuten zählen von selbst herunter.
//
// Widget-Parameter (optional, mit Leerzeichen kombinierbar):
//   zug     → nur Züge (Regionalbahn/S-Bahn), keine Busse
//   hell / dunkel → festes Farbschema; transparent → ohne Hintergrund (durchsichtig im iOS-Look „Klar“)
//   golm / palais / griebnitzsee → fester Campus
// Ohne Campus-Parameter gilt die Einstellung aus dem Stundenplan-Script (Einstellungen → Campus;
// „Automatisch“ = Campus der laufenden bzw. nächsten Veranstaltung). Priorisierte Linien
// (ebenfalls dort einstellbar) sind im Widget immer dabei und haben einen Stern.
//
// Tippen öffnet die Liste aller Abfahrten der nächsten 90 Minuten.

const CAMPUS = {
  "2": { name: "Golm", haltestellen: [{ id: "900220010", name: "Golm Bhf" }, { id: "900220365", name: "Science Park/Uni" }] },
  "1": { name: "Neues Palais", haltestellen: [{ id: "900230133", name: "Campus Universität" }, { id: "900230132", name: "Neues Palais" }, { id: "900230007", name: "Park Sanssouci Bhf" }] },
  "3": { name: "Griebnitzsee", haltestellen: [{ id: "900230003", name: "Griebnitzsee Bhf" }] },
}

// Campus: Widget-Parameter > Einstellung des Stundenplan-Scripts (stundenplan-campus.json) > Golm
function campusLesen() {
  let geteilt = {}
  try {
    let fm
    try { fm = FileManager.iCloud() } catch (e) { fm = FileManager.local() }
    const pfad = fm.joinPath(fm.documentsDirectory(), "stundenplan-campus.json")
    if (fm.fileExists(pfad)) {
      if (fm.isFileStoredIniCloud(pfad) && !fm.isFileDownloaded(pfad)) fm.downloadFileFromiCloud(pfad)
      else geteilt = JSON.parse(fm.readString(pfad)) || {}
    }
  } catch (e) {}
  const roh = (args.widgetParameter || "").toLowerCase()
  const c = /golm/.test(roh) ? "2" : /palais/.test(roh) ? "1" : /griebnitz/.test(roh) ? "3"
    : (CAMPUS[geteilt.campus] ? geteilt.campus : "2")
  return { id: c, name: CAMPUS[c].name, haltestellen: CAMPUS[c].haltestellen,
           favoriten: (geteilt.favoriten || []).map(x => String(x).toUpperCase()) }
}

const CAMPUS_JETZT = campusLesen()
const HALTESTELLEN = CAMPUS_JETZT.haltestellen

function istFavorit(a) {
  return CAMPUS_JETZT.favoriten.includes(String(a.linie).toUpperCase())
}

// Priorisierte Linien kommen auf jeden Fall rein (höchstens die Hälfte), Rest nach Zeit
function auswahl(liste, anzahl) {
  const fav = liste.filter(istFavorit).slice(0, Math.max(1, Math.floor(anzahl / 2)))
  const rest = liste.filter(a => !fav.includes(a)).slice(0, anzahl - fav.length)
  return fav.concat(rest).sort((a, b) => a.zeit - b.zeit)
}

const FARBEN = {
  hell: { bg: "#FFFFFF", text: "#2C2C2A", gedimmt: "#888780", akzent: "#185FA5" },
  dunkel: { bg: "#1C1C1E", text: "#F1EFE8", gedimmt: "#8E8D86", akzent: "#85B7EB" },
}

// ───────────────────────── Daten ─────────────────────────

// Quellen: VBB, ersatzweise BVG (gleiche Haltestellen-Nummern). Die freien Dienste haken
// gelegentlich – deshalb mehrere Versuche.
const QUELLEN = ["https://v6.vbb.transport.rest", "https://v6.bvg.transport.rest"]

async function ladeRoh(h) {
  for (let q = 0; q < QUELLEN.length; q++) {
    // Im Widget bleibt wenig Zeit → weniger Versuche
    const versuche = config.runsInWidget ? 1 : (q === 0 ? 3 : 2)
    for (let i = 0; i < versuche; i++) {
      try {
        const r = new Request(QUELLEN[q] + "/stops/" + h.id + "/departures?duration=90&results=25&remarks=false")
        r.timeoutInterval = 8
        const d = await r.loadJSON()
        if (d && Array.isArray(d.departures)) return d
      } catch (e) {}
      await new Promise(fertig => Timer.schedule(1500 * (i + 1), false, fertig))
    }
  }
  throw new Error("keine Daten")
}

async function ladeHaltestelle(h) {
  const d = await ladeRoh(h)
  return d.departures.map(a => {
    const geplant = new Date(a.plannedWhen || a.when)
    return {
      id: a.tripId,
      zeit: new Date(a.when || a.plannedWhen),
      geplant,
      verspaetung: typeof a.delay === "number" ? Math.round(a.delay / 60) : null,
      linie: (a.line && a.line.name) || "?",
      produkt: (a.line && a.line.product) || "bus",
      richtung: a.direction || "",
      gleis: a.platform || a.plannedPlatform || null,
      haltestelle: h.name,
      ausfall: !!a.cancelled,
    }
  }).filter(a => !isNaN(a.zeit))
}

async function ladeAbfahrten() {
  const fm = FileManager.local()
  const cache = fm.joinPath(fm.cacheDirectory(), "abfahrten-campus" + CAMPUS_JETZT.id + ".json")
  const teile = await Promise.all(HALTESTELLEN.map(h => ladeHaltestelle(h).catch(() => null)))
  if (teile.every(t => t === null)) {
    // Offline: letzten Stand verwenden
    if (!fm.fileExists(cache)) return null
    return JSON.parse(fm.readString(cache)).map(a => Object.assign(a, { zeit: new Date(a.zeit), geplant: new Date(a.geplant) }))
  }
  // Dieselbe Fahrt hält oft an beiden Haltestellen – nur die frühere zeigen
  const gesehen = new Set()
  const alle = teile.flat().filter(Boolean).sort((a, b) => a.zeit - b.zeit)
    .filter(a => !a.id || (!gesehen.has(a.id) && gesehen.add(a.id)))
  fm.writeString(cache, JSON.stringify(alle))
  return alle
}

function istZug(a) {
  return a.produkt === "regional" || a.produkt === "suburban" || a.produkt === "express"
}

function kurzRichtung(r) {
  let s = r || ""
  for (const v of ["Potsdam, ", "S+U ", "S "]) if (s.startsWith(v)) s = s.slice(v.length)
  return s.replace(" (Berlin)", "")
}

function linienFarbe(a) {
  if (a.produkt === "regional" || a.produkt === "express") return "#E2001A"
  if (a.produkt === "suburban") return "#008D4F"
  if (a.produkt === "tram") return "#BE1414"
  return "#95276E"
}

function uhrzeit(d) {
  return d.getHours() + ":" + String(d.getMinutes()).padStart(2, "0")
}

// ───────────────────────── Widget ─────────────────────────

function parameter() {
  const t = (args.widgetParameter || "").toLowerCase().split(/\s+/)
  return { nurZuege: t.includes("zug") || t.includes("züge") || t.includes("zuege"),
           thema: t.includes("hell") ? "hell" : t.includes("dunkel") ? "dunkel"
             : t.includes("transparent") || t.includes("durchsichtig") ? "transparent" : "auto" }
}

function farben(thema) {
  if (thema === "hell" || thema === "dunkel") {
    const f = FARBEN[thema]
    return { bg: new Color(f.bg), vorder: new Color(f.text), gedimmt: new Color(f.gedimmt), akzent: new Color(f.akzent) }
  }
  const dyn = k => Color.dynamic(new Color(FARBEN.hell[k]), new Color(FARBEN.dunkel[k]))
  // „transparent“: kein eigener Hintergrund (richtig durchsichtig im iOS-Look „Klar“/„Getönt“)
  return { bg: thema === "transparent" ? Color.clear() : dyn("bg"), vorder: dyn("text"), gedimmt: dyn("gedimmt"), akzent: dyn("akzent") }
}

function text(stack, inhalt, font, farbe, optionen) {
  const t = stack.addText(inhalt)
  t.font = font
  t.textColor = farbe
  t.lineLimit = 1
  t.minimumScaleFactor = (optionen && optionen.skalierung) || 0.75
  if (optionen && optionen.rechts) t.rightAlignText()
  return t
}

function zeile(w, a, f, kompakt, breite) {
  const z = w.addStack()
  z.layoutHorizontally()
  z.centerAlignContent()
  z.size = new Size(breite, kompakt ? 24 : 30)

  // Linie als farbiges Schild
  const schild = z.addStack()
  schild.size = new Size(kompakt ? 32 : 38, kompakt ? 16 : 18)
  schild.backgroundColor = new Color(linienFarbe(a))
  schild.cornerRadius = 4
  schild.centerAlignContent()
  schild.addSpacer()
  text(schild, a.linie, Font.boldSystemFont(kompakt ? 10 : 11), Color.white(), { skalierung: 0.6 })
  schild.addSpacer()
  z.addSpacer(7)

  const mitte = z.addStack()
  mitte.layoutVertically()
  const r = text(mitte, (istFavorit(a) ? "★ " : "") + kurzRichtung(a.richtung), Font.semiboldSystemFont(kompakt ? 11 : 12), f.vorder)
  if (a.ausfall) r.textOpacity = 0.55
  if (!kompakt) text(mitte, a.haltestelle + (a.gleis ? " · Gl. " + a.gleis : ""), Font.systemFont(9), f.gedimmt)
  z.addSpacer()

  const rechts = z.addStack()
  rechts.layoutVertically()
  const r1 = rechts.addStack()
  r1.addSpacer()
  if (a.ausfall) {
    text(r1, "fällt aus", Font.semiboldSystemFont(kompakt ? 11 : 12), new Color("#E24B4A"))
  } else {
    // Minuten bis zur Abfahrt – zählt iOS von selbst weiter
    const d = r1.addDate(a.zeit)
    d.applyRelativeStyle()
    d.font = Font.semiboldMonospacedSystemFont ? Font.semiboldMonospacedSystemFont(kompakt ? 10 : 11) : Font.semiboldSystemFont(11)
    d.textColor = f.vorder
    d.lineLimit = 1
    d.minimumScaleFactor = 0.6
    d.rightAlignText()
  }
  const r2 = rechts.addStack()
  r2.addSpacer()
  text(r2, uhrzeit(a.geplant), Font.mediumSystemFont(9), f.gedimmt)
  if (a.verspaetung) {
    r2.addSpacer(3)
    text(r2, (a.verspaetung > 0 ? "+" : "") + a.verspaetung, Font.mediumSystemFont(9),
      new Color(a.verspaetung >= 3 ? "#E24B4A" : "#1D9E75"))
  }
}

async function erstelleWidget(familie) {
  const p = parameter()
  const f = farben(p.thema)
  const w = new ListWidget()
  w.backgroundColor = f.bg
  w.setPadding(12, 12, 12, 12)
  w.refreshAfterDate = new Date(Date.now() + 5 * 60000)

  const kompakt = familie === "small"
  const breite = kompakt ? 134 : familie === "large" || familie === "medium" ? 314 : 314
  const anzahl = kompakt ? 3 : familie === "medium" ? 4 : 10

  const kopf = w.addStack()
  kopf.layoutHorizontally()
  text(kopf, kompakt ? "Ab " + CAMPUS_JETZT.name : "Abfahrten " + CAMPUS_JETZT.name + (p.nurZuege ? " · Züge" : ""), Font.semiboldSystemFont(12), f.akzent)
  kopf.addSpacer()
  text(kopf, uhrzeit(new Date()), Font.systemFont(11), f.gedimmt)
  w.addSpacer(5)

  let liste = await ladeAbfahrten()
  if (!liste) {
    w.addSpacer()
    text(w, "Nicht erreichbar", Font.mediumSystemFont(13), f.gedimmt)
    w.addSpacer()
    return w
  }
  const jetzt = Date.now()
  liste = auswahl(liste.filter(a => a.zeit.getTime() > jetzt - 30000 && (!p.nurZuege || istZug(a))), anzahl)
  if (!liste.length) {
    w.addSpacer()
    text(w, "Keine Abfahrten", Font.mediumSystemFont(13), f.gedimmt)
  }
  liste.forEach(a => zeile(w, a, f, kompakt, breite))
  w.addSpacer()
  return w
}

// ───────────────────────── In der App: Liste ─────────────────────────

async function zeigeListe() {
  const liste = await ladeAbfahrten()
  const t = new UITable()
  t.showSeparators = true
  const kopf = new UITableRow()
  kopf.isHeader = true
  kopf.addText("Abfahrten " + CAMPUS_JETZT.name, HALTESTELLEN.map(h => h.name).join(" · ") + " · nächste 90 Minuten")
  t.addRow(kopf)
  if (!liste) {
    const r = new UITableRow()
    r.addText("Abfahrten konnten nicht geladen werden")
    t.addRow(r)
  } else {
    for (const a of liste.filter(x => x.zeit.getTime() > Date.now() - 30000)) {
      const r = new UITableRow()
      r.height = 52
      const l = r.addText(a.linie)
      l.widthWeight = 16
      l.titleColor = new Color(linienFarbe(a))
      l.titleFont = Font.boldSystemFont(15)
      const m = r.addText((istFavorit(a) ? "★ " : "") + kurzRichtung(a.richtung), a.haltestelle + (a.gleis ? " · Gl. " + a.gleis : ""))
      m.widthWeight = 60
      const min = Math.max(0, Math.round((a.zeit - Date.now()) / 60000))
      const v = a.verspaetung ? " (" + (a.verspaetung > 0 ? "+" : "") + a.verspaetung + ")" : ""
      const z = r.addText(a.ausfall ? "fällt aus" : (min === 0 ? "jetzt" : min + " Min"), uhrzeit(a.geplant) + v)
      z.widthWeight = 24
      z.rightAligned()
      t.addRow(r)
    }
  }
  await t.present()
}

if (config.runsInWidget) {
  Script.setWidget(await erstelleWidget(config.widgetFamily))
} else if (config.runsWithSiri) {
  const liste = (await ladeAbfahrten()) || []
  const naechste = auswahl(liste.filter(a => !a.ausfall && a.zeit > new Date()), 3)
  const satz = naechste.length
    ? "Ab " + CAMPUS_JETZT.name + ": " + naechste.map(a => a.linie + " nach " + kurzRichtung(a.richtung) + " in " + Math.max(0, Math.round((a.zeit - Date.now()) / 60000)) + " Minuten").join(", ") + "."
    : "Gerade keine Abfahrten."
  Script.setShortcutOutput(satz)
  Speech.speak(satz)
} else {
  await zeigeListe()
}
Script.complete()
