# Stundenplan · Uni Potsdam (Golm, Neues Palais, Griebnitzsee)

Stundenplan-, Mensa- und Abfahrten-Widgets für alle drei Campus der Uni Potsdam – als **iOS-App** (iPhone/iPad),
als **Android-App** und als **Scriptable-Skripte** fürs iPhone. Grundlage ist der Plan aus
[planner.joshuan.dev](https://planner.joshuan.dev/de) (Export als `.ics`); eingebaut sind die Termine
des WiSe 26/27.

## Download

Alle Versionen liegen nach Plattform getrennt in Ordnern – alte Versionen bleiben erhalten:

- **iPhone/iPad (App):** [`releases/ios/`](releases/ios)
- **Android:** [`releases/android/`](releases/android)
- **Scriptable (iPhone/iPad, ohne Sideloading):** [`releases/scriptable/`](releases/scriptable) –
  aktuelle Fassung direkt: [Stundenplan.js](scriptable/Stundenplan.js) ·
  [AbfahrtenGolm.js](scriptable/AbfahrtenGolm.js) · [MensaGolm.js](scriptable/MensaGolm.js)

Zusätzlich gibt es jede Version als [Release](https://github.com/Black0Skorpion/Stundenplan/releases)
(Tags `ios-v…`, `android-v…` und `scriptable-v…`).

| Plattform | Datei | Release |
|---|---|---|
| iPhone/iPad | `Stundenplan-<Version>.ipa` | `ios-v…` (aktuell 0.5.0) |
| Android | `Stundenplan-<Version>.apk` | `android-v…` (aktuell 1.4) · [neueste](https://github.com/Black0Skorpion/Stundenplan/releases/latest) |
| Scriptable | `Stundenplan.js`, `AbfahrtenGolm.js`, `MensaGolm.js` | `scriptable-v…` (aktuell 1.0.0) |

## Funktionen

| | iOS-App | Android-App | Scriptable |
|---|:-:|:-:|:-:|
| Stundenplan-Widget klein/mittel/groß (Design B, Live-Countdown) | ✓ | ✓ | ✓ |
| Extragroß (iPad) | ✓ | – | ✓ |
| Sperrbildschirm (Zeile, Rechteck, rund – mit Varianten) | ✓ | – | ✓ |
| Campus wählbar (Golm, Neues Palais, Griebnitzsee oder automatisch nach Stundenplan) | ✓ | ✓ | ✓ |
| Mensa des Campus (OpenMensa), Tage blättern | ✓ Wischen/Pfeile | ✓ Pfeile | Parameter/Stapel |
| Abfahrten am Campus (VBB, Echtzeit), priorisierte Linien (z. B. RB20) | ✓ | ✓ | ✓ eigenes Script |
| Lagepläne aller drei Campus, Gebäude in der Farbe des Fachs markiert | ✓ | ✓ | ✓ |
| Bus-/Bahnverbindung bei Campuswechsel zwischen zwei Veranstaltungen | ✓ | ✓ | ✓ |
| Route zum Haus (zu Fuß) & Gehzeit zwischen Veranstaltungen | ✓ Apple Karten/Google Maps | ✓ Google Maps | ✓ Apple Karten |
| „Fällt aus“ / „Raum ändern“ für einzelne Termine | ✓ | ✓ | ✓ |
| Erinnerung vor Veranstaltungen (abschaltbar) | ✓ | ✓ mit Countdown | ✓ |
| Live-Aktivität (Dynamic Island), Sperrbildschirm-Widgets dabei ausblendbar | ✓ | – | – |
| Siri / Kurzbefehle | ✓ | – | ✓ („Hey Siri, Stundenplan“) |
| Veranstaltungen direkt aus PULS wählen (Gruppen, Überschneidungen), täglicher Abgleich | ✓ | – (folgt) | – |
| Wecker vor der ersten Veranstaltung (ab iOS 26 echter Wecker) | ✓ | – (folgt) | – |
| Plan teilen (QR-Code, .ics) und Pläne anderer als Profile | ✓ | – (folgt) | – |
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
- Tippen auf einen Termin öffnet den Lageplan mit **Route**, Gehzeit bzw. Verbindung bei Campuswechsel
  und **„Ändern“** (Fällt aus / Raum ändern / Zurücksetzen). Tippen daneben öffnet die Seite „Plan“.
  Die Widgets öffnen nie den Planer, sondern die App.
- **Veranstaltungen aus PULS:** Einstellungen → „Veranstaltungen aus PULS wählen“ → im Vorlesungsverzeichnis
  suchen oder PULS-Link einfügen, Gruppe wählen, „Als Stundenplan übernehmen“. Danach gleicht die App einmal
  täglich (standardmäßig nur im WLAN) Räume, Zeiten und Ausfälle mit PULS ab und meldet Änderungen.
- **Teilen & Profile:** Plan-Reiter → Teilen-Knopf zeigt einen QR-Code (PULS-Fächer mit Gruppen) bzw. teilt eine
  .ics-Datei. Empfangene Pläne als Profil speichern (oben links umschalten), als eigenen übernehmen oder in der
  Modulwahl anpassen.
- **Wecker:** Einstellungen → Wecker – klingelt eine einstellbare Zeit vor der ersten Veranstaltung des Tages.
- **Einstellungen:** Campus, priorisierte Linien, Karten-App (Apple/Google),
  Erinnerungen (Vorlauf 5–60 Min), Live-Aktivität (optional Sperrbildschirm-Widgets währenddessen ausblenden);
  ICS-Import und Planer-Link unter „Debug“. Änderungen je Version: [CHANGELOG.md](CHANGELOG.md).
- **Siri:** „Wo ist meine nächste Vorlesung in Stundenplan?“, „Was habe ich heute in Stundenplan?“,
  „Stundenplan Abfahrten“.

Gebaut wird die App auf GitHub (macOS-Runner, XcodeGen), Quellcode in `ios-app/`,
Versionsnummer in `ios-app/VERSION`.

## Android-App

1. Auf dem Handy die neueste `Stundenplan-<Version>.apk` aus den
   [Releases](https://github.com/Black0Skorpion/Stundenplan/releases/latest) herunterladen.
2. Öffnen und installieren (Installation aus unbekannten Quellen erlauben).
   Ab 1.4 mit festem Schlüssel signiert – Updates gehen direkt drüber. Von 1.3 oder älter einmalig
   die alte App deinstallieren.

**Widgets:** Startbildschirm gedrückt halten → Widgets → Stundenplan · Jetzt/Heute/Woche,
Mensa · Klein/Mittel/Groß, Abfahrten. Mensa und Abfahrten folgen dem in der App gewählten Campus. Alle Widgets lassen sich frei in der Größe ziehen.

**In der App:** ICS-Import, Campus, priorisierte Linien, Lageplan für heute, Erinnerungen (mit Countdown in der Mitteilung),
Planer-Link (öffnet sich im Lageplan über „Im Planer öffnen“). Im Lageplan gibt es pro Termin
Route (Google Maps), „Fällt aus“ und „Raum ändern“, bei Campuswechsel die Bus- und Bahnverbindungen.

Selbst bauen: Ordner in Android Studio öffnen → Run ▶. Kurznamen, Farben und Standard-Link:
`app/src/main/java/de/stundenplan/widget/Konfig.java`, Versionsnummer in `app/build.gradle`.

## iPhone mit Scriptable (ohne Sideloading)

Installieren: die Dateien aus [`scriptable/`](scriptable) (oder einem Versionsordner in
[`releases/scriptable/`](releases/scriptable)) in iCloud Drive › Scriptable legen – oder am iPhone die
Datei öffnen, „Raw“ antippen, alles kopieren und in Scriptable in ein Script mit **genau demselben Namen**
einfügen. Dateinamen nicht ändern, sonst verlieren bestehende Widgets ihr Script.
Direktlinks zum Kopieren: [Stundenplan.js](https://raw.githubusercontent.com/Black0Skorpion/Stundenplan/main/scriptable/Stundenplan.js) · [AbfahrtenGolm.js](https://raw.githubusercontent.com/Black0Skorpion/Stundenplan/main/scriptable/AbfahrtenGolm.js) ·
[MensaGolm.js](https://raw.githubusercontent.com/Black0Skorpion/Stundenplan/main/scriptable/MensaGolm.js)

- `scriptable/Stundenplan.js` – Stundenplan-Widgets inkl. Sperrbildschirm und Lageplan.
  In der App **Scriptable** ein Script anlegen, Inhalt einfügen, als Widget hinzufügen.
  Menü (Script in Scriptable starten): Einstellungen (Farbschema, Planer-Link, Sperrbildschirm-Varianten,
  Erinnerungen, Campus, priorisierte Linien), Lageplan für heute, Termin ändern (fällt aus / Raum), Im Planer öffnen.
  Im Lageplan pro Termin „Route“ (Apple Karten, zu Fuß) und „Ändern“; Gehzeit bei Hauswechsel,
  Bus-/Bahnverbindungen bei Campuswechsel. Die Pläne von Neues Palais und Griebnitzsee lädt das Script
  beim ersten Öffnen einmalig von GitHub.
  „Hey Siri, Stundenplan“ sagt die nächste Veranstaltung an.
- `scriptable/AbfahrtenGolm.js` – Abfahrten am Campus (klein/mittel/groß), Campus und priorisierte Linien aus den
  Einstellungen des Stundenplan-Scripts. Parameter `zug` = nur Züge, `hell`/`dunkel` = Farbschema,
  `golm`/`palais`/`griebnitzsee` = fester Campus. Antippen zeigt alle Abfahrten der nächsten 90 Minuten.
- `scriptable/MensaGolm.js` – Mensa-Widget. Tag per Widget-Parameter: leer (nächster Öffnungstag),
  `heute`, `morgen`, `+1`, `+2` … oder `mo`–`fr`; mehrere Widgets übereinander ergeben einen Stapel.
  Mensa des Campus aus den Stundenplan-Einstellungen, oder fest per `golm`/`palais`/`griebnitzsee`.

## Stundenplan aktualisieren

Im Planer den Plan als Kalender (`.ics`) exportieren und in der App importieren
(iOS: Datei teilen → Stundenplan oder Einstellungen → Debug → „ICS-Datei importieren“;
Android: „ICS-Datei importieren“; Scriptable: als `stundenplan.ics` in iCloud Drive › Scriptable ablegen).

## Datenquellen

- Stundenplan: [planner.joshuan.dev](https://planner.joshuan.dev/de) (ICS-Export)
- Speiseplan: OpenMensa – [Golm](https://openmensa.org/c/61), [Neues Palais](https://openmensa.org/c/55), [Griebnitzsee](https://openmensa.org/c/62)
- Abfahrten und Verbindungen: VBB über [v6.vbb.transport.rest](https://v6.vbb.transport.rest) (freier Dienst, fällt gelegentlich aus)
- Gebäudepositionen: [OpenStreetMap](https://www.openstreetmap.org) (© OpenStreetMap-Mitwirkende);
  einige Häuser aus dem Lageplan umgerechnet (Abweichung meist unter 20 m)
- Lagepläne: Universität Potsdam, „Lageplan mit Legende“ Golm, Neues Palais und Griebnitzsee (Stand Mai 2025)

Alle Daten werden nur auf dem Gerät verarbeitet; es gibt keinen eigenen Server.
