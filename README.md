# Stundenplan · Uni Potsdam, Campus Golm

Stundenplan-, Mensa- und Abfahrten-Widgets für den Campus Golm – als **iOS-App** (iPhone/iPad),
als **Android-App** und als **Scriptable-Skripte** fürs iPhone. Grundlage ist der Plan aus
[planner.joshuan.dev](https://planner.joshuan.dev/de) (Export als `.ics`); eingebaut sind die Termine
des WiSe 26/27.

## Download

Alle Versionen liegen nach Plattform getrennt in Ordnern – alte Versionen bleiben erhalten:

- **iPhone/iPad:** [`releases/ios/`](releases/ios)
- **Android:** [`releases/android/`](releases/android)

Zusätzlich gibt es jede Version als [Release](https://github.com/Black0Skorpion/Stundenplan/releases)
(Tags `ios-v…` und `android-v…`).

| Plattform | Datei | Release |
|---|---|---|
| iPhone/iPad | `Stundenplan-<Version>.ipa` | `ios-v…` (aktuell 0.2.0) |
| Android | `Stundenplan-<Version>.apk` | `android-v…` (aktuell 1.1) · [neueste](https://github.com/Black0Skorpion/Stundenplan/releases/latest) |

## Funktionen

| | iOS-App | Android-App | Scriptable |
|---|:-:|:-:|:-:|
| Stundenplan-Widget klein/mittel/groß (Design B, Live-Countdown) | ✓ | ✓ | ✓ |
| Extragroß (iPad) | ✓ | – | ✓ |
| Sperrbildschirm (Zeile, Rechteck, rund – mit Varianten) | ✓ | – | ✓ |
| Mensa Golm (OpenMensa), Tage blättern | ✓ Pfeile | ✓ Pfeile | Parameter/Stapel |
| Abfahrten ab Golm Bhf & Science Park/Uni (VBB, Echtzeit) | ✓ | ✓ | ✓ eigenes Script |
| Lageplan Golm mit markiertem Gebäude | ✓ | ✓ | ✓ |
| Route zum Haus (zu Fuß) & Gehzeit zwischen Veranstaltungen | ✓ Apple Karten | ✓ Google Maps | ✓ Apple Karten |
| „Fällt aus“ / „Raum ändern“ für einzelne Termine | ✓ | ✓ | ✓ |
| Erinnerung vor Veranstaltungen (abschaltbar) | ✓ | ✓ mit Countdown | ✓ |
| Live-Aktivität (Dynamic Island) | ✓ | – | – |
| Siri / Kurzbefehle | ✓ | – | ✓ („Hey Siri, Stundenplan“) |
| ICS-Import, eigener Planer-Link | ✓ | ✓ | ✓ |

Akademisches Viertel: Termine zur vollen Stunde (z. B. 8–10 Uhr) werden als 8:15–9:45 gezeigt.
Hell/Dunkel folgt dem System (iOS-Widgets: wählbar über „Widget bearbeiten“).

## iOS-App (iPhone/iPad)

Ohne bezahlten Apple-Developer-Account per Sideloading mit einer (Zweit-)Apple-ID:

- **Am PC:** [iloader](https://github.com/nab138/iloader) → „IPA importieren“ → `Stundenplan-<Version>.ipa`.
- **Am iPhone:** in [SideStore](https://sidestore.io) → „My Apps“ → „+“ → IPA auswählen
  (LocalDevVPN muss an sein). SideStore erneuert die App dann automatisch.

Kostenlos signierte Apps laufen **7 Tage** und müssen danach neu signiert bzw. installiert werden.
Neue Versionen einfach drüber installieren – Widgets und Einstellungen bleiben erhalten.

**Bedienung**
- Widgets: Home-/Sperrbildschirm gedrückt halten → Widget hinzufügen → „Stundenplan“.
  Farbschema, Sperrbildschirm-Anzeige, Mensa-Tag usw. über **„Widget bearbeiten“**.
- Tippen auf einen Termin öffnet den Lageplan mit **Route**, Gehzeit und **„Ändern“**
  (Fällt aus / Raum ändern / Zurücksetzen). Die Widgets öffnen nie den Planer, sondern die App.
- **Einstellungen:** ICS-Import, Planer-Link, Erinnerungen (Vorlauf 5–60 Min), Live-Aktivität.
- **Siri:** „Wo ist meine nächste Vorlesung in Stundenplan?“, „Was habe ich heute in Stundenplan?“,
  „Stundenplan Abfahrten“.

Gebaut wird die App auf GitHub (macOS-Runner, XcodeGen), Quellcode in `ios-app/`,
Versionsnummer in `ios-app/VERSION`.

## Android-App

1. Auf dem Handy die neueste `Stundenplan-<Version>.apk` aus den
   [Releases](https://github.com/Black0Skorpion/Stundenplan/releases/latest) herunterladen.
2. Öffnen und installieren (Installation aus unbekannten Quellen erlauben).

**Widgets:** Startbildschirm gedrückt halten → Widgets → Stundenplan · Jetzt/Heute/Woche,
Mensa · Klein/Mittel/Groß, Abfahrten Golm. Alle Widgets lassen sich frei in der Größe ziehen.

**In der App:** ICS-Import, Lageplan für heute, Erinnerungen (mit Countdown in der Mitteilung),
Planer-Link (öffnet sich im Lageplan über „Im Planer öffnen“). Im Lageplan gibt es pro Termin
Route (Google Maps), „Fällt aus“ und „Raum ändern“.

Selbst bauen: Ordner in Android Studio öffnen → Run ▶. Kurznamen, Farben und Standard-Link:
`app/src/main/java/de/stundenplan/widget/Konfig.java`, Versionsnummer in `app/build.gradle`.

## iPhone mit Scriptable (ohne Sideloading)

- `ios/Stundenplan.js` – Stundenplan-Widgets inkl. Sperrbildschirm und Lageplan.
  In der App **Scriptable** ein Script anlegen, Inhalt einfügen, als Widget hinzufügen.
  Menü (Script in Scriptable starten): Einstellungen (Farbschema, Planer-Link, Sperrbildschirm-Varianten,
  Erinnerungen), Lageplan für heute, Termin ändern (fällt aus / Raum), Im Planer öffnen.
  Im Lageplan pro Termin „Route“ (Apple Karten, zu Fuß) und „Ändern“; Gehzeit bei Hauswechsel.
  „Hey Siri, Stundenplan“ sagt die nächste Veranstaltung an.
- `ios/AbfahrtenGolm.js` – Abfahrten ab Golm Bhf und Science Park/Uni (klein/mittel/groß).
  Parameter `zug` = nur Züge, `hell`/`dunkel` = Farbschema. Antippen zeigt alle Abfahrten der nächsten 90 Minuten.
- `ios/MensaGolm.js` – Mensa-Widget. Tag per Widget-Parameter: leer (nächster Öffnungstag),
  `heute`, `morgen`, `+1`, `+2` … oder `mo`–`fr`; mehrere Widgets übereinander ergeben einen Stapel.

## Stundenplan aktualisieren

Im Planer den Plan als Kalender (`.ics`) exportieren und in der App importieren
(iOS: Einstellungen → „ICS-Datei importieren“ oder Datei teilen → Stundenplan;
Android: „ICS-Datei importieren“; Scriptable: als `stundenplan.ics` in iCloud Drive › Scriptable ablegen).

## Datenquellen

- Stundenplan: [planner.joshuan.dev](https://planner.joshuan.dev/de) (ICS-Export)
- Speiseplan: [OpenMensa](https://openmensa.org/c/61) (Mensa Golm)
- Abfahrten: VBB über [v6.vbb.transport.rest](https://v6.vbb.transport.rest)
- Gebäudepositionen: [OpenStreetMap](https://www.openstreetmap.org) (© OpenStreetMap-Mitwirkende);
  einige Häuser aus dem Lageplan umgerechnet (Abweichung meist unter 20 m)
- Lageplan: Universität Potsdam, „Lageplan mit Legende Golm“ (Stand 2025)

Alle Daten werden nur auf dem Gerät verarbeitet; es gibt keinen eigenen Server.
