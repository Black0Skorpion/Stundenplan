// Variables used by Scriptable.
// These must be at the very top of the file. Do not edit.
// icon-color: orange; icon-glyph: utensils;
// Mensa-Widget für Scriptable – Mensen der Uni Potsdam (Golm, Am Neuen Palais, Griebnitzsee)
// Daten: OpenMensa (openmensa.org), frei und ohne Anmeldung.
// Größen: Klein, Mittel, Groß und – auf dem iPad – Extragroß (zwei Tage nebeneinander).
//
// Tag auswählen über den Widget-Parameter (Widget gedrückt halten → Widget bearbeiten → Parameter):
//   (leer)            nächster Öffnungstag: heute bis 15 Uhr, danach der nächste Tag
//   heute             immer heute (auch wenn geschlossen)
//   morgen            nächster Öffnungstag nach heute
//   +1, +2, +3 …      1., 2., 3. … Öffnungstag nach dem automatisch gewählten Tag
//   mo, di, mi, do, fr  dieser Wochentag (ist er schon vorbei: nächste Woche)
//   hell / dunkel     festes Farbschema (kombinierbar, z. B. "+1 dunkel")
//   golm / palais / griebnitzsee  feste Mensa (sonst die Campus-Einstellung des Stundenplan-Scripts;
//                     „Automatisch“ = Campus der laufenden bzw. nächsten Veranstaltung)
//
// Mehrere Tage in einem Slot: Mehrere Mensa-Widgets gleicher Größe auf dem Home-Bildschirm
// übereinander ziehen (ergibt einen Stapel) und z. B. die Parameter leer, +1, +2, +3, +4 eintragen.
// Dann wischt man im Stapel durch die nächsten Tage.

// ───────────────────────── Einstellungen ─────────────────────────

// Mensen bei OpenMensa je Campus (erste Zahl der Raumnummer)
const MENSEN = {
  "2": { id: 61, name: "Mensa Golm" },
  "1": { id: 55, name: "Mensa Neues Palais" },
  "3": { id: 62, name: "Mensa Griebnitzsee" },
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
  if (/golm/.test(roh)) return "2"
  if (/palais/.test(roh)) return "1"
  if (/griebnitz/.test(roh)) return "3"
  return MENSEN[geteilt.campus] ? geteilt.campus : "2"
}

const MENSA = MENSEN[campusLesen()]

const CONFIG = {
  MENSA_ID: MENSA.id,
  MENSA_NAME: MENSA.name,
  PREISGRUPPE: "students",       // "students", "employees" oder "others"
  UMSCHALTEN_UM: 15,             // ab dieser Stunde zeigt "leer" schon den nächsten Tag
  ABEND_ANZEIGEN: true,          // Abendangebote anzeigen (nur wenn sie sich vom Mittag unterscheiden)
  THEMA: "auto",                 // "auto" | "hell" | "dunkel"
  TAP_URL: "https://openmensa.org/c/" + MENSA.id, // beim Tippen öffnen ("" = Scriptable öffnen)
}

const API = "https://openmensa.org/api/v2/canteens/" + CONFIG.MENSA_ID

// Farben je Kategorie (Angebot 1–4, Tagesangebot, Rest)
const KATEGORIE_FARBEN = {
  1: "#1D9E75", 2: "#639922", 3: "#D85A30", 4: "#378ADD", 5: "#BA7517",
  tages: "#7F77DD", dessert: "#D4537E", sonst: "#888780",
}

const FARBEN = {
  hell: { bg: "#FFFFFF", text: "#2C2C2A", gedimmt: "#888780", akzent: "#185FA5" },
  dunkel: { bg: "#1C1C1E", text: "#F1EFE8", gedimmt: "#8E8D86", akzent: "#85B7EB" },
}

const WOCHENTAGE_LANG = ["So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"]
const WOCHENTAGE_VOLL = ["Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"]
const MONATE = ["Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"]
const WT_KUERZEL = { so: 0, mo: 1, di: 2, mi: 3, do: 4, fr: 5, sa: 6 }

// ───────────────────────── Hilfsfunktionen ─────────────────────────

function isoDatum(d) {
  return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0")
}

function ausIso(s) {
  const [j, m, t] = s.split("-").map(Number)
  return new Date(j, m - 1, t)
}

function tagesBeginn(d) {
  const x = new Date(d)
  x.setHours(0, 0, 0, 0)
  return x
}

function plusTage(d, n) {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x
}

function preisText(p) {
  if (p === null || p === undefined) return ""
  return p.toFixed(2).replace(".", ",") + " €"
}

function tagesLabel(tag, heute) {
  const diff = Math.round((tagesBeginn(tag) - tagesBeginn(heute)) / 86400000)
  if (diff === 0) return "Heute"
  if (diff === 1) return "Morgen"
  return WOCHENTAGE_VOLL[tag.getDay()]
}

function datumKurz(d) {
  return WOCHENTAGE_LANG[d.getDay()] + ", " + d.getDate() + ". " + MONATE[d.getMonth()]
}

// ───────────────────────── Daten laden (mit Zwischenspeicher) ─────────────────────────

async function ladeJSON(url, cacheName) {
  const fm = FileManager.local()
  const pfad = fm.joinPath(fm.cacheDirectory(), "mensa-" + CONFIG.MENSA_ID + "-" + cacheName + ".json")
  try {
    const r = new Request(url)
    r.timeoutInterval = 15
    const daten = await r.loadJSON()
    const status = r.response ? r.response.statusCode : 200
    if (status === 404) return [] // kein Speiseplan für diesen Tag
    if (status >= 400 || !Array.isArray(daten)) throw new Error("HTTP " + status)
    fm.writeString(pfad, JSON.stringify(daten))
    return daten
  } catch (e) {
    // Offline: letzten Stand verwenden
    if (fm.fileExists(pfad)) return JSON.parse(fm.readString(pfad))
    return null
  }
}

async function ladeTage() {
  const tage = await ladeJSON(API + "/days", "tage")
  if (tage && tage.length) {
    return tage.map(t => ({ datum: ausIso(t.date), geschlossen: !!t.closed }))
      .sort((a, b) => a.datum - b.datum)
  }
  // Ersatz, falls OpenMensa nicht erreichbar ist: Mo–Fr der nächsten zwei Wochen
  const heute = tagesBeginn(new Date())
  const liste = []
  for (let i = 0; i < 14; i++) {
    const d = plusTage(heute, i)
    liste.push({ datum: d, geschlossen: d.getDay() === 0 || d.getDay() === 6 })
  }
  return liste
}

async function ladeSpeiseplan(tag) {
  const meals = await ladeJSON(API + "/days/" + isoDatum(tag) + "/meals", isoDatum(tag))
  if (meals === null) return null
  return aufbereiten(meals)
}

function kategorieKurz(k) {
  return (k || "").replace(/\s*-\s*nur solange.*$/i, "").replace(/\s+/g, " ").trim()
}

function kategorieFarbe(kat) {
  const nr = kat.match(/angebot\s*(\d+)/i)
  if (nr && /^(abend\s*)?angebot/i.test(kat)) return KATEGORIE_FARBEN[nr[1]] || KATEGORIE_FARBEN.sonst
  if (/tagesangebot/i.test(kat)) return KATEGORIE_FARBEN.tages
  if (/dessert/i.test(kat)) return KATEGORIE_FARBEN.dessert
  return KATEGORIE_FARBEN.sonst
}

function sortierSchluessel(kat) {
  const nr = kat.match(/angebot\s*(\d+)/i)
  if (nr && /^angebot/i.test(kat)) return Number(nr[1])
  if (/tagesangebot/i.test(kat)) return 50
  return 90
}

function aufbereiten(meals) {
  const gerichte = [], abend = [], theke = [], desserts = []
  for (const m of meals) {
    const kat = kategorieKurz(m.category)
    const name = (m.name || "").replace(/\s+/g, " ").trim()
    const g = { name, kat, preis: m.prices ? m.prices[CONFIG.PREISGRUPPE] : null, farbe: kategorieFarbe(kat) }
    if (/^abend/i.test(kat)) abend.push(g)
    else if (/dessert/i.test(kat)) desserts.push(g)
    else if (/theke/i.test(kat)) theke.push(g)
    else gerichte.push(g)
  }
  gerichte.sort((a, b) => sortierSchluessel(a.kat) - sortierSchluessel(b.kat))
  const abendNeu = abend.filter(a => !gerichte.some(g => g.name === a.name))
  return { gerichte, abend: CONFIG.ABEND_ANZEIGEN ? abendNeu : [], theke, desserts }
}

// ───────────────────────── Tag auswählen ─────────────────────────

function parameterLesen() {
  const roh = (args.widgetParameter || "").trim().toLowerCase()
  const teile = roh.split(/\s+/).filter(Boolean)
  const p = { thema: null, modus: "auto", wert: 0 }
  for (const t of teile) {
    if (t === "hell" || t === "dunkel") p.thema = t
    else if (t === "heute") p.modus = "heute"
    else if (t === "morgen") p.modus = "morgen"
    else if (/^\+?\d+$/.test(t)) { p.modus = "auto"; p.wert = Number(t.replace("+", "")) }
    else if (WT_KUERZEL[t.slice(0, 2)] !== undefined) { p.modus = "wochentag"; p.wert = WT_KUERZEL[t.slice(0, 2)] }
  }
  return p
}

function waehleTag(p, tage, jetzt) {
  const heute = tagesBeginn(jetzt)
  const offen = tage.filter(t => !t.geschlossen && t.datum >= heute)
  const infoZu = d => {
    const eintrag = tage.find(t => isoDatum(t.datum) === isoDatum(d))
    return { datum: d, geschlossen: eintrag ? eintrag.geschlossen : (d.getDay() === 0 || d.getDay() === 6) }
  }
  if (p.modus === "heute") return infoZu(heute)
  if (p.modus === "wochentag") {
    const diff = (p.wert - heute.getDay() + 7) % 7
    return infoZu(plusTage(heute, diff))
  }
  if (p.modus === "morgen") {
    const t = offen.find(t => t.datum > heute)
    return t || infoZu(plusTage(heute, 1))
  }
  // auto (+N)
  let start = offen.findIndex(t => t.datum > heute || jetzt.getHours() < CONFIG.UMSCHALTEN_UM)
  if (start < 0) start = offen.length
  const t = offen[start + p.wert]
  return t || infoZu(plusTage(heute, 1 + p.wert))
}

function naechsterOffenerTag(tage, nach) {
  return tage.find(t => !t.geschlossen && t.datum > nach.datum) || { datum: plusTage(nach.datum, 1), geschlossen: false }
}

// ───────────────────────── Widget-Bausteine ─────────────────────────

function themenFarben(p) {
  const thema = p.thema || CONFIG.THEMA
  if (thema === "hell" || thema === "dunkel") {
    const f = FARBEN[thema]
    return { bg: new Color(f.bg), vorder: new Color(f.text), gedimmt: new Color(f.gedimmt), akzent: new Color(f.akzent) }
  }
  const dyn = k => Color.dynamic(new Color(FARBEN.hell[k]), new Color(FARBEN.dunkel[k]))
  return { bg: dyn("bg"), vorder: dyn("text"), gedimmt: dyn("gedimmt"), akzent: dyn("akzent") }
}

function textHinzu(stack, inhalt, font, farbe, optionen) {
  const t = stack.addText(inhalt)
  t.font = font
  t.textColor = farbe
  t.lineLimit = (optionen && optionen.zeilen) || 1
  t.minimumScaleFactor = (optionen && optionen.skalierung) || 0.85
  return t
}

function kopf(parent, info, jetzt, farben, breite) {
  const k = parent.addStack()
  k.layoutHorizontally()
  k.centerAlignContent()
  k.size = new Size(breite, 15)
  textHinzu(k, CONFIG.MENSA_NAME, Font.semiboldSystemFont(12), farben.akzent)
  k.addSpacer()
  const label = tagesLabel(info.datum, jetzt)
  const rechts = ["Heute", "Morgen"].includes(label) ? label + " · " + info.datum.getDate() + ". " + MONATE[info.datum.getMonth()] : datumKurz(info.datum)
  textHinzu(k, rechts, Font.mediumSystemFont(11), farben.gedimmt)
}

function hinweis(parent, text, farben) {
  parent.addSpacer()
  const z = parent.addStack()
  z.layoutHorizontally()
  z.addSpacer()
  const t = textHinzu(z, text, Font.mediumSystemFont(13), farben.gedimmt, { zeilen: 2 })
  t.centerAlignText()
  z.addSpacer()
  parent.addSpacer()
}

function kategorieKurzLabel(kat) {
  return kat.replace(/^abend\s*angebot\s*/i, "Abend ").replace(/^tagesangebot.*/i, "Tagesang.")
}

// Eine Gerichtzeile: Farbbalken | Name (bis zu 2 Zeilen) | Preis und Kategorie (feste Breite)
const PREIS_B = 54
function gerichtZeile(parent, g, farben, breite, namensZeilen) {
  const z = parent.addStack()
  z.layoutHorizontally()
  z.topAlignContent()
  z.size = new Size(breite, 0)
  const balken = z.addStack()
  balken.size = new Size(4, Math.max(26, namensZeilen * 14.4) - 2)
  balken.backgroundColor = new Color(g.farbe)
  balken.cornerRadius = 2
  z.addSpacer(7)
  const txt = z.addStack()
  txt.layoutVertically()
  txt.size = new Size(breite - 4 - 7 - 6 - PREIS_B, 0)
  textHinzu(txt, g.name, Font.semiboldSystemFont(12), farben.vorder, { zeilen: namensZeilen, skalierung: 0.9 })
  z.addSpacer(6)
  const preis = z.addStack()
  preis.layoutVertically()
  preis.size = new Size(PREIS_B, 0)
  const p1 = preis.addStack()
  p1.addSpacer()
  textHinzu(p1, preisText(g.preis), Font.semiboldSystemFont(12), farben.vorder, { skalierung: 0.8 })
  const p2 = preis.addStack()
  p2.addSpacer()
  textHinzu(p2, kategorieKurzLabel(g.kat), Font.systemFont(9), farben.gedimmt, { skalierung: 0.8 })
}

// Geschätzte Zeilenzahl eines Namens (für die Platzplanung)
function zeilenFuer(name, breite, maxZeilen) {
  const zeichenProZeile = Math.max(10, Math.floor((breite - 4 - 7 - 6 - PREIS_B) / 6.4))
  return Math.min(maxZeilen || 2, Math.ceil(name.length / zeichenProZeile))
}

// Höhe einer Gerichtzeile
function zeilenHoehe(zeilen) {
  return Math.max(26, zeilen * 14.4)
}

function thekeText(theke) {
  return theke.map(g => g.kat + " " + preisText(g.preis) + (/100\s?g/i.test(g.name) ? "/100 g" : "")).join(" · ")
}

// Fußzeile mit fester Breite (damit lange Texte sauber gekürzt werden)
function fussZeile(parent, text, farben, breite) {
  const z = parent.addStack()
  z.layoutHorizontally()
  z.size = new Size(breite, 0)
  textHinzu(z, text, Font.systemFont(10), farben.gedimmt, { skalierung: 0.8 })
  z.addSpacer()
}

function abschnittTitel(parent, text, farben) {
  textHinzu(parent, text, Font.semiboldSystemFont(10), farben.gedimmt)
  parent.addSpacer(4)
}

// Inhalt einer Tagesspalte (Groß und Extragroß)
function tagesSpalte(parent, info, plan, jetzt, farben, breite, hoehe) {
  kopf(parent, info, jetzt, farben, breite)
  parent.addSpacer(8)
  if (info.geschlossen) { hinweis(parent, "Mensa geschlossen", farben); return }
  if (plan === null) { hinweis(parent, "Speiseplan nicht erreichbar", farben); return }
  if (plan.gerichte.length === 0 && plan.abend.length === 0) { hinweis(parent, "Noch kein Speiseplan", farben); return }

  // Platz unten für die Theke (und ggf. Desserts als Kurzzeile) reservieren
  let rest = hoehe - 23 - (plan.theke.length ? 22 : 0)
  let weggelassen = 0

  // Platz für Zeilen ausrechnen, ohne schon zu zeichnen
  const planen = (liste, maxZeilen) => liste.map(g => {
    const z = zeilenFuer(g.name, breite, maxZeilen)
    return { g, z, h: zeilenHoehe(z) + 8 }
  })
  const summe = l => l.reduce((n, e) => n + e.h, 0)

  // Hauptgerichte: bis zu 3 Zeilen, wenn genug Platz für alles da ist, sonst 2
  let haupt = planen(plan.gerichte, 3)
  const dessertsVoll = planen(plan.desserts, 1)
  const abendVoll = planen(plan.abend, 2)
  const gesamt = summe(haupt) + (abendVoll.length ? 16 + summe(abendVoll) : 0) + (dessertsVoll.length ? 16 + summe(dessertsVoll) : 0)
  if (gesamt > rest) haupt = planen(plan.gerichte, 2)

  for (const e of haupt) {
    if (e.h > rest) { weggelassen++; continue }
    gerichtZeile(parent, e.g, farben, breite, e.z)
    parent.addSpacer(8)
    rest -= e.h
  }

  if (abendVoll.length) {
    if (rest >= 16 + abendVoll[0].h) {
      abschnittTitel(parent, "Abends", farben)
      rest -= 16
      for (const e of abendVoll) {
        if (e.h > rest) { weggelassen++; continue }
        gerichtZeile(parent, e.g, farben, breite, e.z)
        parent.addSpacer(8)
        rest -= e.h
      }
    } else weggelassen += abendVoll.length
  }

  // Desserts: eigene Zeilen, wenn Platz ist – sonst eine Kurzzeile
  let dessertKurz = false
  if (dessertsVoll.length) {
    if (rest >= 16 + summe(dessertsVoll)) {
      abschnittTitel(parent, "Dessert", farben)
      for (const e of dessertsVoll) {
        gerichtZeile(parent, e.g, farben, breite, e.z)
        parent.addSpacer(8)
      }
      rest -= 16 + summe(dessertsVoll)
    } else dessertKurz = true
  }

  if (weggelassen) textHinzu(parent, "+ " + weggelassen + " weitere", Font.systemFont(10), farben.gedimmt)
  parent.addSpacer()

  if (dessertKurz) {
    fussZeile(parent, "Dessert: " + plan.desserts.map(g => g.name + " " + preisText(g.preis)).join(" · "), farben, breite)
  }
  if (plan.theke.length) fussZeile(parent, thekeText(plan.theke), farben, breite)
}

// ───────────────────────── Widget-Größen ─────────────────────────

function widgetGroesse(familie) {
  const s = Device.screenSize()
  const kurz = Math.round(Math.min(s.width, s.height))
  const lang = Math.round(Math.max(s.width, s.height))
  if (Device.isPad()) {
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

function basisWidget(farben, rand) {
  const w = new ListWidget()
  w.backgroundColor = farben.bg
  w.setPadding(rand, rand, rand, rand)
  if (CONFIG.TAP_URL) w.url = CONFIG.TAP_URL
  return w
}

// Klein (2×2): die ersten zwei Gerichte
function kleinesWidget(info, plan, jetzt, farben) {
  const groesse = widgetGroesse("small")
  const rand = 12
  const w = basisWidget(farben, rand)
  const innenB = groesse.breite - 2 * rand
  // Kopf kompakt: "Golm" und Tag
  const k = w.addStack()
  k.layoutHorizontally()
  k.centerAlignContent()
  textHinzu(k, "Mensa", Font.semiboldSystemFont(12), farben.akzent)
  k.addSpacer()
  textHinzu(k, tagesLabel(info.datum, jetzt) === "Heute" ? "Heute" : WOCHENTAGE_LANG[info.datum.getDay()] + " " + info.datum.getDate() + ".", Font.mediumSystemFont(11), farben.gedimmt)
  w.addSpacer(6)
  if (info.geschlossen) { hinweis(w, "Geschlossen", farben); return w }
  if (plan === null) { hinweis(w, "Nicht erreichbar", farben); return w }
  const liste = plan.gerichte.length ? plan.gerichte : plan.abend
  if (!liste.length) { hinweis(w, "Noch kein Speiseplan", farben); return w }

  liste.slice(0, 2).forEach((g, i) => {
    const z = w.addStack()
    z.layoutHorizontally()
    z.topAlignContent()
    const balken = z.addStack()
    balken.size = new Size(4, 40)
    balken.backgroundColor = new Color(g.farbe)
    balken.cornerRadius = 2
    z.addSpacer(6)
    const txt = z.addStack()
    txt.layoutVertically()
    txt.size = new Size(innenB - 10, 0)
    textHinzu(txt, g.name, Font.semiboldSystemFont(11), farben.vorder, { zeilen: 2, skalierung: 0.85 })
    textHinzu(txt, preisText(g.preis) + " · " + g.kat, Font.systemFont(10), farben.gedimmt)
    if (i === 0) w.addSpacer(6)
  })
  w.addSpacer()
  if (liste.length > 2) textHinzu(w, "+ " + (liste.length - 2) + " weitere", Font.systemFont(10), farben.gedimmt)
  return w
}

// Mittel (4×2): bis zu vier Gerichte, je eine Zeile
function mittleresWidget(info, plan, jetzt, farben) {
  const groesse = widgetGroesse("medium")
  const rand = 11
  const w = basisWidget(farben, rand)
  const innenB = groesse.breite - 2 * rand
  const innenH = groesse.hoehe - 2 * rand
  kopf(w, info, jetzt, farben, innenB)
  w.addSpacer(6)
  if (info.geschlossen) { hinweis(w, "Mensa geschlossen", farben); return w }
  if (plan === null) { hinweis(w, "Speiseplan nicht erreichbar", farben); return w }
  const liste = plan.gerichte.concat(plan.abend)
  if (!liste.length) { hinweis(w, "Noch kein Speiseplan", farben); return w }

  // So viele Gerichte wie passen (Namen bis zu 2 Zeilen)
  let rest = innenH - 21
  let mehr = 0
  const zeigen = []
  for (const g of liste) {
    const z = zeilenFuer(g.name, innenB)
    const h = zeilenHoehe(z) + 5
    if (h > rest - (liste.length - zeigen.length - 1 > 0 ? 12 : 0)) { mehr++; continue }
    zeigen.push([g, z])
    rest -= h
  }
  zeigen.forEach(([g, z], i) => {
    gerichtZeile(w, g, farben, innenB, z)
    if (i < zeigen.length - 1 || mehr) w.addSpacer(5)
  })
  if (mehr) textHinzu(w, "+ " + mehr + " weitere", Font.systemFont(10), farben.gedimmt)
  w.addSpacer()
  return w
}

// Groß (4×4): ganzer Tag
function grossesWidget(info, plan, jetzt, farben) {
  const groesse = widgetGroesse("large")
  const rand = 12
  const w = basisWidget(farben, rand)
  const spalte = w.addStack()
  spalte.layoutVertically()
  spalte.size = new Size(groesse.breite - 2 * rand, groesse.hoehe - 2 * rand)
  tagesSpalte(spalte, info, plan, jetzt, farben, groesse.breite - 2 * rand, groesse.hoehe - 2 * rand)
  return w
}

// Extragroß (iPad): gewählter Tag und nächster Öffnungstag nebeneinander
function extraGrossesWidget(info, plan, info2, plan2, jetzt, farben) {
  const groesse = widgetGroesse("extraLarge")
  const rand = 12
  const luecke = 18
  const w = basisWidget(farben, rand)
  const innenH = groesse.hoehe - 2 * rand
  const spaltenB = Math.floor((groesse.breite - 2 * rand - luecke) / 2)
  const zeile = w.addStack()
  zeile.layoutHorizontally()
  zeile.topAlignContent()
  const links = zeile.addStack()
  links.layoutVertically()
  links.size = new Size(spaltenB, innenH)
  tagesSpalte(links, info, plan, jetzt, farben, spaltenB, innenH)
  zeile.addSpacer(luecke)
  const rechts = zeile.addStack()
  rechts.layoutVertically()
  rechts.size = new Size(spaltenB, innenH)
  tagesSpalte(rechts, info2, plan2, jetzt, farben, spaltenB, innenH)
  return w
}

// ───────────────────────── Start ─────────────────────────

async function erstelleWidget(familie) {
  const jetzt = new Date()
  const p = parameterLesen()
  const farben = themenFarben(p)
  const tage = await ladeTage()
  const info = waehleTag(p, tage, jetzt)
  const plan = info.geschlossen ? null : await ladeSpeiseplan(info.datum)
  let w
  if (familie === "small") w = kleinesWidget(info, plan, jetzt, farben)
  else if (familie === "medium") w = mittleresWidget(info, plan, jetzt, farben)
  else if (familie === "extraLarge") {
    const info2 = naechsterOffenerTag(tage, info)
    const plan2 = info2.geschlossen ? null : await ladeSpeiseplan(info2.datum)
    w = extraGrossesWidget(info, plan, info2, plan2, jetzt, farben)
  } else w = grossesWidget(info, plan, jetzt, farben)

  // Neu laden: um Mitternacht, zur Umschaltzeit oder spätestens in 3 Stunden
  const mitternacht = plusTage(tagesBeginn(jetzt), 1)
  const umschalten = new Date(tagesBeginn(jetzt).getTime() + CONFIG.UMSCHALTEN_UM * 3600000)
  const kandidaten = [mitternacht, new Date(jetzt.getTime() + 3 * 3600000)]
  if (umschalten > jetzt) kandidaten.push(umschalten)
  w.refreshAfterDate = new Date(Math.min(...kandidaten.map(d => d.getTime())))
  return w
}

if (config.runsInWidget) {
  Script.setWidget(await erstelleWidget(config.widgetFamily))
} else {
  const auswahl = new Alert()
  auswahl.title = "Vorschau"
  auswahl.addAction("Klein (2×2)")
  auswahl.addAction("Mittel (4×2)")
  auswahl.addAction("Groß (4×4)")
  if (Device.isPad()) auswahl.addAction("Extragroß (iPad)")
  auswahl.addCancelAction("Abbrechen")
  const i = await auswahl.presentSheet()
  if (i === 0) await (await erstelleWidget("small")).presentSmall()
  if (i === 1) await (await erstelleWidget("medium")).presentMedium()
  if (i === 2) await (await erstelleWidget("large")).presentLarge()
  if (i === 3) await (await erstelleWidget("extraLarge")).presentExtraLarge()
}
Script.complete()
