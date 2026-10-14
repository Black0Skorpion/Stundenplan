# Stundenplan-Widget für Android

Eigenständige App mit drei Widgets:

- **Stundenplan · Jetzt** (2×2): aktuelle bzw. nächste Veranstaltung
- **Stundenplan · Heute** (4×2): dazu der Tagesplan
- **Stundenplan · Woche** (4×4): Wochenraster Mo–Fr

Dazu drei Mensa-Widgets (**Mensa · Klein/Mittel/Groß**) für die Mensa Golm – mit den Pfeilen ‹ › oben
rechts blättert man durch die Öffnungstage.

Hell/Dunkel folgt dem System. Alle Widgets lassen sich frei in der Größe ziehen (z. B. 4×3).
Die Stundenplan-Widgets aktualisieren sich jede Minute, solange das Handy an ist; der Speiseplan
wird höchstens stündlich neu geladen und ist auch offline (letzter Stand) verfügbar.

## App installieren (fertige APK)

Bei jeder Änderung baut GitHub die App automatisch und stellt sie als Release bereit:

1. Auf dem Android-Handy im Browser **github.com/Black0Skorpion/Stundenplan/releases/latest** öffnen (eingeloggt).
2. Unter **Assets** auf **Stundenplan.apk** tippen und herunterladen.
3. Datei öffnen und installieren (Installation aus unbekannten Quellen erlauben, ggf. „Trotzdem installieren“).

## APK bauen – Variante B: Android Studio

1. Android Studio installieren und diesen Ordner über **File › Open** öffnen.
2. Warten, bis „Gradle Sync“ fertig ist.
3. Handy per USB mit aktiviertem USB-Debugging verbinden und auf **Run ▶** klicken,
   oder über **Build › Build App Bundle(s) / APK(s) › Build APK(s)** eine APK erzeugen.

## Widget hinzufügen

Startbildschirm gedrückt halten → **Widgets** → **Stundenplan** → gewünschte Größe ziehen.

## Stundenplan ändern

App „Stundenplan“ öffnen → **ICS-Datei importieren** → die im Planer exportierte `.ics` auswählen.
**Eingebaute Termine verwenden** setzt auf die mitgelieferten Termine (WiSe 26/27) zurück.

**Eigener Planer-Link:** In der App den über „Plan teilen“ kopierten Link einfügen und speichern –
beim Tippen auf ein Stundenplan-Widget öffnet sich dann genau dieser Plan.

Kurznamen, Farben und der Standard-Link stehen in
`app/src/main/java/de/stundenplan/widget/Konfig.java`.

## iPhone (Scriptable)

Die iOS-Version liegt unter `ios/Stundenplan.js`. In der App **Scriptable** ein neues Script anlegen,
den Inhalt einfügen und als Widget (klein, mittel oder groß – auf dem iPad auch extragroß) auf den Home-Bildschirm legen.
Eigener Planer-Link: oben im Script bei `CONFIG.TAP_URL` eintragen oder als Widget-Parameter.
Sperrbildschirm-Widgets (Zeile, Rechteck, rund) mit Varianten per Parameter – Details im Kopf des Scripts.

## Mensa-Widget (iPhone/iPad, Scriptable)

`ios/MensaGolm.js` zeigt den Speiseplan der Mensa Golm (Daten von openmensa.org).
Den Tag wählt man über den Widget-Parameter: leer (nächster Öffnungstag), `heute`, `morgen`,
`+1`, `+2` … oder `mo`–`fr`. Mehrere Mensa-Widgets gleicher Größe übereinander ziehen ergibt einen
Stapel, durch den man wischen kann (z. B. Parameter leer, +1, +2, +3, +4).
