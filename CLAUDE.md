# Hinweise für die Arbeit an diesem Repo

## Versionsnummern
- Kleinschrittig erhöhen: normale Änderungen und Fehlerbehebungen nur die letzte Ziffer
  (iOS 0.3.2 → 0.3.3, Android 1.4 → 1.4.1, Scriptable 1.0.0 → 1.0.1).
- Die mittlere Ziffer nur bei wirklich größeren Updates (viele neue Funktionen auf einmal).
- Versionen: iOS `ios-app/VERSION`, Android `app/build.gradle` (versionName + versionCode +1),
  Scriptable `scriptable/VERSION`.
- Alte Versionen/Releases nie löschen.

## Erst beim nächsten Android-Build mit neuen Funktionen einbauen
- „Live Update“ (Android-Gegenstück zur Dynamic Island) für die laufende/nächste Veranstaltung:
  Android 16 `Notification.ProgressStyle` + `setRequestPromotedOngoing(true)` (Statusleisten-Chip,
  Sperrbildschirm; Samsung zeigt es in der Now Bar), auf älteren Android-Versionen die bisherige
  Countdown-Mitteilung. Verhalten wie iOS: vor Beginn Countdown, während der Vorlesung kein
  Countdown, nur Ende und Fortschritt.

- PULS-Modulwahl wie in iOS 0.4.0 (ios-app/App/Puls.swift, PulsDienst.swift, PulsAnsicht.swift) auf Android
  nachziehen, sobald der Nutzer bestätigt, dass sie auf iOS funktioniert. Abgleich dort per JobScheduler
  (nur ohne mobile Daten).

## PULS
- Öffentliche Seiten ohne Anmeldung, keine offizielle Schnittstelle (HTML auslesen). Parser-Test:
  Workflow „PULS-Test (Swift)“ (ios-app/Tests/PulsTest.swift) lädt echte Seiten auf einem macOS-Runner.
- Sparsam abfragen: Abgleich höchstens etwa einmal täglich.

## Sonstiges
- Android-Releases werden mit festem Schlüssel aus den Secrets ANDROID_KEYSTORE_B64 /
  ANDROID_KEYSTORE_PASSWORT signiert (Schlüssel liegt nicht im Repo).
- Scriptable-Skripte: Dateinamen nie ändern (Widgets hängen am Namen).
