# Stundenplan-Widget für Android

Eigenständige App mit drei Widgets:

- **Stundenplan · Jetzt** (2×2): aktuelle bzw. nächste Veranstaltung
- **Stundenplan · Heute** (4×2): dazu der Tagesplan
- **Stundenplan · Woche** (4×4): Wochenraster Mo–Fr

Hell/Dunkel folgt dem System. Die Widgets aktualisieren sich jede Minute, solange das Handy an ist.

## APK bauen – Variante A: GitHub (ohne Android Studio)

1. Neues (privates) Repository auf GitHub anlegen und den Inhalt dieses Ordners hochladen.
2. Unter **Actions** läuft automatisch „APK bauen“ (ca. 3–5 Minuten).
3. Im fertigen Lauf unten bei **Artifacts** „Stundenplan-APK“ herunterladen und entpacken.
4. `app-debug.apk` aufs Handy schicken und installieren (Installation aus unbekannten Quellen erlauben).

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

Kurznamen, Farben und die Adresse, die beim Tippen geöffnet wird, stehen in
`app/src/main/java/de/stundenplan/widget/Konfig.java`.

## iPhone (Scriptable)

Die iOS-Version liegt unter `ios/Stundenplan.js`. In der App **Scriptable** ein neues Script anlegen,
den Inhalt einfügen und als Widget (klein, mittel oder groß) auf den Home-Bildschirm legen.
