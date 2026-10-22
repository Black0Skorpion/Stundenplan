# Changelog

Nur was sich für dich in der App ändert – neu, geändert, entfernt, behoben.

## iOS 0.5.1.7

**Behoben**
- Wecker: Die App prüft jetzt bei jedem Öffnen und regelmäßig im Hintergrund, ob die Wecker im System wirklich gestellt sind, und stellt fehlende sofort neu. Vorher konnte ein von iOS entfernter Wecker (z. B. nach einem Update) unbemerkt fehlen.
- Wecker: Zwei gleichzeitige Neuplanungen konnten sich gegenseitig stören – sie laufen jetzt nacheinander.

**Neu**
- Lehrperson: steht jetzt beim Termin (Termin antippen) und in der großen Karte im Plan bzw. im großen Widget, sofern Platz ist. Wird aus PULS übernommen.
- Einstellungen → Debug → „Wecker im System“: zeigt, welche Wecker iOS gerade wirklich gestellt hat, mit Knopf „Wecker jetzt neu stellen“.

## iOS 0.5.1.6

**Behoben**
- Schlummern ging nicht, wenn der Wecker noch mit einer älteren App-Version gestellt worden war: Nach jedem Update werden die Wecker jetzt einmal neu gestellt, damit alle Einstellungen (z. B. Schlummerzeit) auch wirklich darin stecken.
- Ein Wecker, der gerade klingelt oder schlummert, wird beim Neuplanen nicht mehr versehentlich abgebrochen.
- Stundenplan-Widget: Der Hinweis „Erster Termin · Wecker 6:45“ am Vorabend passt jetzt in die Karte – bei wenig Platz wird er gekürzt.

**Neu**
- Sperrbildschirm-Widget (Rechteck): Am Vorabend steht neben dem ersten Termin des nächsten Tages, wann der Wecker klingelt (z. B. „MORGEN 8:15 · ⏰ 6:45“), auch in der Tagesübersicht.

## iOS 0.5.1.5

**Behoben**
- Widgets im iOS-Look „Klar“ bzw. „Getönt“: Veranstaltungen im Wochenplan, die Karte zur nächsten Veranstaltung, die Markierung des heutigen Tages und die Linien-Schilder der Abfahrten waren weiße Flächen ohne lesbare Schrift. Sie erscheinen dort jetzt als halbdurchsichtiges Glas mit lesbarer Schrift.

## iOS 0.5.1.4

**Neu**
- Widgets mit Farbschema „Transparent“ (neben Automatisch, Hell und Dunkel): Widget gedrückt halten → „Widget bearbeiten“ → Farbschema. Das Widget hat dann keinen eigenen Hintergrund. Richtig durchsichtig wird es im iOS-Look „Klar“ bzw. „Getönt“ (Home-Bildschirm bearbeiten → Anpassen); im normalen Look zeigt iOS dahinter seinen Standard-Hintergrund.

## Android 1.6.1.3

**Neu**
- Farbschema für alle Widgets: System, Hell, Dunkel oder Transparent (Einstellungen → Widgets). Bei „Transparent“ hat das Widget keinen Hintergrund, dein Hintergrundbild scheint durch; die Schrift richtet sich nach dem System.

## Scriptable 1.1.1.3

**Neu**
- Farbschema „Transparent“ für alle drei Widgets: im Stundenplan-Script unter Einstellungen → Farbschema oder als Widget-Parameter „transparent“ (auch bei Mensa und Abfahrten). Das Widget hat dann keinen eigenen Hintergrund – richtig durchsichtig im iOS-Look „Klar“ bzw. „Getönt“.

## iOS 0.5.1.3

**Neu**
- Zeiten von Veranstaltungen selbst festlegen: Termin antippen → „Ändern“ → „Zeit ändern …“. Gilt wahlweise nur für diesen einen Termin oder für alle Termine der Reihe (gleiche Veranstaltung, gleicher Wochentag, gleiche Uhrzeit) – auch für neue Termine nach dem PULS-Abgleich. Schnellwahl „Volle Stunde (s.t.)“ statt des automatischen c.t.
- Wecker bleibt bei der ursprünglichen Zeit: Wird eine Veranstaltung nach hinten verschoben (z. B. 10:15 → 10:30), klingelt der Wecker trotzdem wie bisher – Züge fahren ja nicht später. Lässt sich pro Änderung abschalten; bei einer früheren Zeit richtet sich der Wecker immer nach der neuen.
- Geänderte Termine zeigen die ursprüngliche Zeit; „Eigene Zeit entfernen“ bzw. „Änderungen zurücksetzen“ stellt sie wieder her. Erinnerungen, Widgets und „Nicht stören“ richten sich nach der neuen Zeit.

## Android 1.6.1.2

**Neu**
- Zeiten von Veranstaltungen selbst festlegen: im Lageplan bzw. nach Tippen auf einen Termin „🕒 Zeit ändern“. Gilt wahlweise nur für diesen Termin oder für alle Termine der Reihe (gleiche Veranstaltung, gleicher Wochentag, gleiche Uhrzeit) – auch nach dem PULS-Abgleich. Schnellwahl „Volle Stunde (s.t.)“ statt des automatischen c.t.
- Wecker bleibt bei der ursprünglichen Zeit: Wird eine Veranstaltung nach hinten verschoben (z. B. 10:15 → 10:30), klingelt der Wecker trotzdem wie bisher – Züge fahren ja nicht später. Lässt sich pro Änderung abschalten; bei einer früheren Zeit richtet sich der Wecker immer nach der neuen.
- Geänderte Termine zeigen die ursprüngliche Zeit; „Eigene Zeit entfernen“ bzw. „Zurücksetzen“ stellt sie wieder her. Erinnerungen und Widgets richten sich nach der neuen Zeit.

## Scriptable 1.1.1.2

**Neu**
- Zeiten von Veranstaltungen selbst festlegen: „Termin ändern“ (bzw. „Ändern“ im Lageplan) → „Zeit ändern …“. Gilt wahlweise nur für diesen Termin oder für alle Termine der Reihe – auch nach einem neuen Plan-Import. Schnellwahl „Volle Stunde (s.t.)“ statt des automatischen c.t. oder eigene Uhrzeit eingeben.
- Wecker bleibt bei der ursprünglichen Zeit: Beginnt eine Veranstaltung später, fragt das Skript, ob der Wecker trotzdem wie bisher klingeln soll (Züge fahren ja nicht später). Bei einer früheren Zeit richtet er sich immer nach der neuen.
- Geänderte Termine zeigen die ursprüngliche Zeit; „Eigene Zeit entfernen“ bzw. „Änderungen zurücksetzen“ stellt sie wieder her.

## iOS 0.5.1.2

**Neu**
- Nicht stören in Veranstaltungen: schaltet während deiner Veranstaltungen automatisch einen Fokus ein und danach wieder aus. Welcher Fokus (z. B. „Nicht stören“ oder ein eigener „Uni“-Fokus), wählst du beim Einrichten. Einschalten zu Beginn oder 5, 10 bzw. 15 Minuten vorher; direkt aufeinanderfolgende Veranstaltungen zählen als ein Block, ausgefallene gar nicht.
- Einrichtung in Einstellungen → Nicht stören mit Schritt-für-Schritt-Anleitung und den passenden Uhrzeiten aus deinem Plan. Weil iOS Apps keinen Fokus selbst einschalten lässt, läuft das über eine Kurzbefehl-Automation mit der neuen Aktion „Ende der Veranstaltung (für Fokus)“.

## iOS 0.5.1.1

**Behoben**
- Wecker klingelte vor jeder Vorlesung statt nur vor der ersten des Tages, sobald der Plan im Lauf des Tages neu berechnet wurde (z. B. beim Öffnen der App oder nach dem PULS-Abgleich). Jetzt gibt es pro Tag nur noch einen Wecker; hat die erste Veranstaltung schon begonnen, kommt der nächste erst am nächsten Morgen.

## Android 1.6.1.1

**Behoben**
- Wecker klingelte vor jeder Vorlesung statt nur vor der ersten des Tages, sobald der Plan im Lauf des Tages neu berechnet wurde (z. B. beim Öffnen der App oder nach dem PULS-Abgleich). Jetzt gibt es pro Tag nur noch einen Wecker; hat die erste Veranstaltung schon begonnen, kommt der nächste erst am nächsten Morgen.

## Scriptable 1.1.1.1

**Behoben**
- Wecker klingelte vor jeder Vorlesung statt nur vor der ersten des Tages, sobald der Plan im Lauf des Tages neu berechnet wurde (z. B. beim Öffnen der App oder nach dem PULS-Abgleich). Jetzt gibt es pro Tag nur noch einen Wecker; hat die erste Veranstaltung schon begonnen, kommt der nächste erst am nächsten Morgen.

## iOS 0.5.1

**Neu**
- Wecker mit Schlummern: Schlummerzeit einstellbar (5, 9, 10, 15 oder 20 Minuten, oder aus); beim Schlummern läuft ein Countdown auf dem Sperrbildschirm und in der Dynamic Island.
- Am Vorabend zeigt das Stundenplan-Widget beim ersten Termin des nächsten Tages, wann der Wecker klingelt („Erster Termin · Wecker 7:15“).
- Eigene Termine: über „+“ im Plan anlegen (z. B. Lerngruppe, Sprechstunde, Klausur), einmalig, jede Woche oder alle 2 Wochen, mit Raum. Sie zählen für Widgets, Erinnerungen und Wecker, werden beim PULS-Abgleich nicht überschrieben.
- Plan teilen: auswählen, welche eigenen Termine mitgeschickt werden (im QR-Code, im Link und in der Datei). Beim Empfänger landen sie im Profil bzw. bei den eigenen Terminen.

## Android 1.6.1

**Neu**
- Wecker: Schlummerzeit einstellbar (5, 9, 10, 15 oder 20 Minuten).
- Am Vorabend zeigen das Stundenplan-Widget und die Karte im Plan beim ersten Termin des nächsten Tages, wann der Wecker klingelt.
- Eigene Termine: über „+“ im Plan anlegen, einmalig, jede Woche oder alle 2 Wochen, mit Raum; Liste zum Bearbeiten und Löschen. Sie zählen für Widgets, Erinnerungen und Wecker und werden beim PULS-Abgleich nicht überschrieben.
- Plan teilen: auswählen, welche eigenen Termine mitgeschickt werden (im QR-Code, im Link und in der Datei). Beim Empfänger landen sie im Profil bzw. bei den eigenen Terminen.

## Scriptable 1.1.1

**Neu**
- Am Vorabend zeigt das Widget beim ersten Termin des nächsten Tages, wann die Wecker-Mitteilung kommt.

## iOS 0.5.0

**Neu**
- Wecker vor der ersten Veranstaltung des Tages: Vorlauf einstellbar (30 Min bis 3 Std), optional „frühestens um“. Klingelt nur an Tagen mit Veranstaltungen, ausgefallene Termine zählen nicht. Ab iOS 26 ein echter Wecker (auch im Lautlos-Modus), sonst eine Mitteilung mit Ton.
- Plan teilen: eigener Name in den Einstellungen, QR-Code mit deinen PULS-Fächern und Gruppen, oder als .ics-Datei (AirDrop, Nachrichten, andere Kalender-Apps).
- Geteilten Plan öffnen (Kamera, QR-Scanner in der App oder Datei): als Profil speichern, als eigenen Plan übernehmen oder in der Modulwahl öffnen und noch anpassen.
- Profile: Pläne anderer im Plan-Reiter oben links umschalten, Namen jederzeit änderbar, PULS-Profile lassen sich aktualisieren.
- Kurze Einführung beim ersten Start mit Beispiel-Stundenplan (überspringbar, in den Einstellungen nochmal aufrufbar).
- Leerer Plan zeigt direkt „Veranstaltungen aus PULS wählen“ und „QR-Code scannen“.

**Geändert**
- Einstellungen aufgeräumt: Datei-Import, Planer-Link, Widgets neu laden und Datenaustausch stehen jetzt unter „Debug“ ganz unten.
- Der Planer-Knopf oben im Plan-Reiter ist durch „Teilen“ und die Profil-Auswahl ersetzt (Planer weiterhin unter Debug).

**Entfernt**
- Die mitgelieferten Termine und „Eingebaute Termine verwenden“.

**Behoben**
- Modulwahl: Der ⓘ-Knopf öffnet jetzt die Details der Veranstaltung; der graue Pfeil daneben ist weg.

## Android 1.6

**Neu**
- Die App ist jetzt aufgebaut wie auf dem iPhone, mit Reitern unten: Plan, Mensa, Abfahrten, Lageplan, Einstellungen.
- Plan: Wochenraster zum Antippen (Termin → Lageplan, Wochentag → Räume des Tages), Wochen per Pfeil oder Wischen blättern, große Karte der laufenden bzw. nächsten Veranstaltung mit Countdown und Fortschritt, Tagesliste mit Fußweg bzw. Campuswechsel dazwischen.
- Profile direkt im Plan umschalten (oben links), Teilen oben rechts.
- Mensa: Speiseplan in der App, Tage per Kapsel oder Wischen, Campus oben rechts.
- Abfahrten: Liste in der App mit Verspätungen, priorisierte Linien oben, Linie gedrückt halten zum Priorisieren, Haltestellen in Google Maps; wenn nichts lädt, direkt zu Google Maps.
- Lageplan: eigener Reiter mit den Räumen des heutigen bzw. nächsten Tages.
- Kurze Einführung beim ersten Start mit Beispiel-Stundenplan (überspringbar, in den Einstellungen nochmal aufrufbar).
- Modulwahl: Wochenplan wie auf dem iPhone – Fach antippen, dann stehen alle Gruppen grau im Plan und lassen sich dort antippen; Überschneidungen rot umrandet; Termine gedrückt halten zum Aus- und Einblenden.
- Im Vorlesungsverzeichnis stöbern, mit „Zum Plan“-Knopf.

**Geändert**
- Einstellungen neu sortiert wie auf dem iPhone; die Widget-Vorschau steht jetzt unter „Debug“.
- Tippen aufs Mensa- bzw. Abfahrten-Widget öffnet den passenden Reiter der App.

## Android 1.5

**Neu**
- Modulwahl aus PULS wie auf dem iPhone: Fächer suchen (ganzer Katalog offline), PULS-Link einfügen, Gruppen wählen (mit Hinweis auf Überschneidungen, getrennt nach Gruppe und Vorlesung), einzelne wiederkehrende Termine ausblenden und wieder einblenden, Wochenvorschau vor dem Übernehmen.
- Täglicher Abgleich mit PULS (auf Wunsch nur ohne mobile Daten) mit Mitteilung bei Raum-, Zeit- oder Terminänderungen.
- Wecker vor der ersten Veranstaltung des Tages: Vorlauf 30 Min bis 3 Std, optional „frühestens um“, mit Schlummern. Klingelt nur an Tagen mit Veranstaltungen, ausgefallene Termine zählen nicht.
- Live Update (ab Android 16): laufende bzw. nächste Veranstaltung in der Statusleiste und auf dem Sperrbildschirm (Samsung: Now Bar) – vorher Countdown und Raum, während der Vorlesung Ende und Fortschritt.
- Plan teilen: eigener Name, QR-Code mit PULS-Fächern und Gruppen, Link oder .ics-Datei – kompatibel mit der iPhone-App.
- Geteilten Plan öffnen (QR-Code scannen, Link einfügen oder Datei): als Profil speichern, als eigenen Plan übernehmen oder in der Modulwahl öffnen und anpassen.
- Profile: Pläne anderer in der Vorschau anzeigen, umbenennen, aus PULS aktualisieren, löschen.

**Geändert**
- Einstellungen aufgeräumt: ICS-Import und Planer-Link stehen jetzt unter „Debug“ ganz unten.
- Eine importierte .ics-Datei lässt sich jetzt auch als Profil speichern.

**Entfernt**
- Die mitgelieferten Termine und „Eingebaute Termine verwenden“.

## Scriptable 1.1.0

**Neu**
- „Stundenplan-Datei wählen“: .ics direkt aus Dateien übernehmen (z. B. die von der iPhone-App geteilte Datei).
- „Meinen Plan teilen“: eigener Plan als .ics (AirDrop, Nachrichten …), mit eigenem Namen; die PULS-Auswahl aus der Datei bleibt erhalten.
- Profile: Pläne anderer als .ics hinzufügen, umbenennen, ersetzen, löschen; ein Widget zeigt ein Profil mit dem Parameter profil=<kürzel>.
- Wecker: Mitteilung mit Ton vor der ersten Veranstaltung des Tages (Vorlauf und „frühestens um“ einstellbar).
- Eigener Name in den Einstellungen.

**Entfernt**
- Die mitgelieferten Termine – ohne Datei zeigen die Widgets „Noch kein Stundenplan“.

## iOS 0.4.2

**Neu**
- Modulwahl: Gruppe direkt im Wochenplan antippen, Überschneidungen unterscheiden zwischen Gruppe und Vorlesung.
- Termine (z. B. Zusatzübungen) im Plan ausblenden und wieder einblenden – sie bleiben in der Modulwahl durchgestrichen sichtbar.
- „Zum Plan“-Knopf im Vorlesungsverzeichnis.

**Behoben**
- Gleiche Termine mit Raumwechsel im Semester erscheinen nicht mehr doppelt („ab 04.11. anderer Raum“).
- Größere Tippflächen in der Fächerliste.

## iOS 0.4.0

**Neu**
- Eigene Modulwahl aus PULS: Fächer suchen (ganzer Katalog offline), im Vorlesungsverzeichnis stöbern, Gruppen wählen, Vorschau im Wochenplan vor dem Übernehmen.
- Täglicher Abgleich mit PULS (auf Wunsch nur im WLAN) mit Mitteilung bei Raum- oder Zeitänderungen.

## Android 1.4

**Neu**
- Alle drei Campusse (Golm, Neues Palais, Griebnitzsee) für Abfahrten, Mensa und Lagepläne, auch „Automatisch“.
- Priorisierte Linien, Verbindungen bei Campuswechsel, Markierungen in der Fachfarbe.

**Behoben**
- Updates lassen sich installieren, ohne die App vorher zu löschen (fester Signaturschlüssel).

## Scriptable 1.0.0

**Neu**
- Stundenplan-, Abfahrten- und Mensa-Skript mit Campus-Auswahl, als Release auf GitHub.
