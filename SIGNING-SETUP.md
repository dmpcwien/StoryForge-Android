# StoryForge 1.3 – dauerhaft signierte APKs

Die App-ID bleibt `at.storyforge.editor`. Eine höhere Versionsnummer allein genügt **nicht** für ein Android-Update: Das Signaturzertifikat muss mit der auf dem Gerät installierten App übereinstimmen.

Das GitHub Actions Workflow baut **vorläufig Debug-APKs**, solange die vier Repository-Secrets fehlen. Der Debug-Schlüssel auf einem neuen GitHub-Runner ist nicht dauerhaft. Das Projekt enthält bewusst **keinen privaten Schlüssel**.

## Einmalig ein eigenes Zertifikat erstellen

Auf einem vertrauenswürdigen PC mit installiertem Java:

```sh
keytool -genkeypair -v -keystore storyforge-release.jks -alias storyforge -keyalg RSA -keysize 3072 -validity 10000
```

Bewahre `storyforge-release.jks` und die Kennwörter sicher und **außerhalb des öffentlichen Repositorys** auf. Ohne diesen Schlüssel sind spätere Updates der damit signierten App nicht möglich.

Gehe im GitHub-Repository zu **Settings → Secrets and variables → Actions → New repository secret** und erstelle:

- `SF_KEYSTORE_B64`: Base64-Inhalt der JKS-Datei (auf Linux mit `base64 -w 0 storyforge-release.jks`, auf macOS mit `base64 < storyforge-release.jks | tr -d '\n'`).
- `SF_STORE_PASSWORD`: Keystore-Passwort.
- `SF_KEY_ALIAS`: `storyforge` bzw. der verwendete Alias.
- `SF_KEY_PASSWORD`: Schlüssel-Passwort.

Dann unter **Actions → Android APK → Run workflow** den Build auslösen. Wenn die vier Secrets vorhanden sind, erzeugt der Workflow eine signierte Release-APK; andernfalls nur eine Debug-Testversion.

**Wichtig:** Auch mit dem neuen festen Zertifikat lässt sich eine ältere Debug-APK mit anderem Schlüssel nicht darüber installieren. Sichere zuerst die lokalen Projekte über **Backup → ZIP**, bevor du eine bestehende Installation entfernst und die signierte Version installierst. Innerhalb einer dauerhaft mit demselben Zertifikat signierten Release-Reihe bleiben Updates danach möglich.

## Funktionen und Einschränkungen

- Automatikmontage mit sechs Filmvorlagen, Beat-Heuristik, Stilleanalyse und Foto/Video-Wechsel
- Reihenfolge der Musik und sanfte Überblendungen, dynamische Originalton-Absenkung
- ZIP-Backup inklusive importierter Medien, Rückgängig und Wiederholen
- Gerenderte Effektvorschau (erste fünf Szenen; der Export dauert)
- Export im Hintergrund mit Android-Benachrichtigung
- MP4/H.264/AAC bis 4K/60 fps, sofern Quellmedien/Hardware ausreichend sind
- **HDR/10-Bit wird beim gegenwärtigen H.264-SDR-Export nicht erhalten.** Kein KI-Modell für Szenenverständnis enthalten. Stille- und Beatschätzung sind fehlertolerante Näherungsverfahren, keine perfekte automatische Regie.
