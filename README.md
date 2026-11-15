# MoCam – Kamera-App mit Logo-Einblendung & Filtern

Für das Huawei Mate 20 Pro (läuft auf jedem Android ab 8.0).

## Funktionen

**Video**
- Aufnahme in 4K / 1080p / 720p / 480p (16:9), mit oder ohne Ton
- Pause/Fortsetzen während der Aufnahme, Aufnahmezeit-Anzeige
- Dauerlicht (Taschenlampe)
- **Logo wird live ins Video eingebrannt.** Die Vorschau zeigt genau das, was aufgenommen wird.

**Foto**
- Bis ca. 12 MP (4:3), Blitz Aus/An/Auto
- Huawei-Herstellermodi, sofern das Gerät sie an Dritt-Apps freigibt: Auto-Szene, HDR, Nacht, Porträt (Bokeh), Beauty
- Logo optional auch auf Fotos

**Allgemein**
- 13 Live-Filter (Lebendig, S/W, Noir, Sepia, Vintage, Warm, Kalt, Kontrast, Matt, Kino, Nordisch, Invertiert)
- Zoom per Pinch, Schieberegler und Schnellwahl (0,6x / 1x / 2x / 5x / 10x, je nach Kamera)
- Tippen zum Fokussieren und Belichten, Belichtungskorrektur (EV)
- Selbstauslöser 3 s / 10 s, Raster (Drittel-Regel)
- Front-/Rückkamera (auch per Doppeltipp)
- Auslösen mit den Lautstärketasten
- Speichert in `Bilder/MoCam` und `Filme/MoCam`, Vorschaubild öffnet die Galerie

## Logo platzieren

1. Oben auf **🏷 Logo** tippen und **Logo wählen…**. Am besten ist eine PNG-Datei mit transparentem Hintergrund.
2. Logo im Bild mit einem Finger verschieben (oder an die Zielstelle tippen) und mit zwei Fingern größer oder kleiner ziehen.
3. Deckkraft einstellen und **Fertig** tippen. Position und Logo bleiben gespeichert.

## APK erzeugen

### Variante A: ohne Installation über GitHub (empfohlen)
1. Auf github.com ein neues (gern privates) Repository anlegen.
2. Den Inhalt dieses Ordners hochladen. Der Ordner `.github` muss mit hochgeladen werden.
3. Im Reiter **Actions** läuft der Workflow „APK bauen“ automatisch an (ca. 5–8 Min.).
4. Unten im fertigen Lauf unter **Artifacts** „MoCam-APK“ herunterladen und entpacken. Darin liegt `app-debug.apk`.

### Variante B: Android Studio
Ordner öffnen, Gradle-Sync abwarten und dann **Build → Build App Bundle(s)/APK(s) → Build APK(s)** wählen.

## Auf dem Mate 20 Pro installieren
1. APK aufs Handy kopieren (USB, Mail, Cloud).
2. Antippen. Bei Nachfrage für die Datei-App „Installation aus unbekannten Quellen erlauben“ aktivieren
   (Einstellungen → Sicherheit → Weitere Einstellungen → Apps aus externen Quellen installieren).
3. Beim ersten Start Kamera, Mikrofon (und unter Android 9 Speicher) erlauben.

Spätere Builds sind mit demselben Schlüssel signiert und lassen sich deshalb einfach drüber installieren.

## Bekannte Grenzen
- Huawei gibt fremden Apps keinen Zugriff auf die Leica-Bildverarbeitung, den Pro-/RAW-Modus und die Super-Makro-Funktion.
  Welche Objektive (Weitwinkel/Tele) erreichbar sind, entscheidet die Huawei-Firmware. Die Zoom-Schnellwahl zeigt, was verfügbar ist.
- Frontkamera: In der Vorschau erscheint das Logo gespiegelt (die Vorschau ist ein Spiegelbild). Im Video steht es richtig herum.
- Mit aktivem Huawei-Modus (HDR/Nacht …) kann die Live-Vorschau ohne Filter und Logo laufen. Beides wird dann beim Speichern des Fotos hinzugefügt.
- Falls das Logo im fertigen Video gedreht erscheint: Im Logo-Panel **Drehen ↻** antippen, bis es passt.

## Technik
Kotlin, CameraX 1.4 (Preview, ImageCapture, VideoCapture, Extensions). Filter und Logo laufen über einen
eigenen OpenGL-ES-Shader (`GlEffectProcessor`) als `CameraEffect` auf Vorschau und Videoencoder.
Für Fotos wird dieselbe Farbmatrix per `ColorMatrix` angewendet.

## Unterstützen
Dir gefällt MoCam? Über eine kleine Spende freue ich mich:
[**Spenden mit PayPal**](https://www.paypal.com/donate/?hosted_button_id=PFE8KWFRX3F7S)
