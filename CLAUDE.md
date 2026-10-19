# Hinweise für die Arbeit an diesem Repo

## Bauen
- Erst bauen/veröffentlichen (Push, der einen Build auslöst), wenn der Nutzer es ausdrücklich sagt.
  Änderungen vorher nur vorbereiten (z. B. auf einem Arbeits-Branch) und hier unter „Vorgemerkt“ notieren.

## Vorgemerkt (fertig auf Branch `wecker-eigene-termine`, noch nicht gebaut)
- iOS 0.5.1, Android 1.6.1, Scriptable 1.1.1: einstellbares Schlummern, „Wecker 7:15“ im Widget am Vorabend,
  eigene Termine über „+“ im Plan (Scriptable: nur der Wecker-Hinweis). Beim Bauen: Branch nach main mergen.
  Vorher mit „Android prüfen (Branch)“ prüfen; iOS wird erst auf main gebaut (AlarmKit-Schlummern mit Live-Aktivität
  ist neu und ungeprüft).

## Versionsnummern
- Kleinschrittig erhöhen: normale Änderungen und Fehlerbehebungen nur die letzte Ziffer
  (iOS 0.3.2 → 0.3.3, Android 1.4 → 1.4.1, Scriptable 1.0.0 → 1.0.1).
- Die mittlere Ziffer nur bei wirklich größeren Updates (viele neue Funktionen auf einmal).
- Versionen: iOS `ios-app/VERSION`, Android `app/build.gradle` (versionName + versionCode +1),
  Scriptable `scriptable/VERSION`.
- Alte Versionen/Releases nie löschen.

## Android
- Seit 1.5 auf dem Stand von iOS 0.5.0 (Modulwahl, Teilen/Profile, Wecker, Live Update). Neue iOS-Funktionen
  dort nachziehen, wenn der Nutzer es möchte. Auf Arbeits-Branches prüft der Workflow „Android prüfen (Branch)“
  den Build (ohne Release); APK-Releases nur von main.
- Parser-Test Java: Workflow „PULS-Test (Java)“ (app/test/PulsTest.java), Ergebnis im Branch daten-java.

## Releases
- Jedes Release bekommt einen vollständigen Changelog: nur Funktionen aus Nutzersicht (neu / geändert / entfernt /
  behoben), nichts Technisches. Datei CHANGELOG.md im Repo pflegen und im Release-Text verwenden.

## PULS
- Öffentliche Seiten ohne Anmeldung, keine offizielle Schnittstelle (HTML auslesen). Parser-Test:
  Workflow „PULS-Test (Swift)“ (ios-app/Tests/PulsTest.swift) lädt echte Seiten auf einem macOS-Runner.
- Sparsam abfragen: Abgleich höchstens etwa einmal täglich.

## Sonstiges
- Android-Releases werden mit festem Schlüssel aus den Secrets ANDROID_KEYSTORE_B64 /
  ANDROID_KEYSTORE_PASSWORT signiert (Schlüssel liegt nicht im Repo).
- Scriptable-Skripte: Dateinamen nie ändern (Widgets hängen am Namen).
