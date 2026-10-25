# StoryForge 1.3.2 – Rückwärts abspielen (Android-Fix)

- Fehler behoben: Auf Android verdeckte der Beschreibungstext die beiden auswählbaren Dialogoptionen. Der Dialog zeigt jetzt die Aktionen selbst.
- Neuer Button **Reihenfolge umkehren** direkt unter der Clip-Zeitleiste.
- **Letzten Clip zuerst – ganzen Film umkehren**: kehrt die Reihenfolge sämtlicher Bilder und Videos um, sodass der bisherige letzte Clip als erster startet.
- **Nur Fotos umkehren**: Bilder werden rückwärts sortiert; Videos behalten ihre Position.
- Das Umordnen funktioniert für Vorschau und MP4-Export, bearbeitet keine Originaldateien und kann mit **Rückgängig** zurückgenommen werden.
- Automatische Regressionstests im Android-GitHub-Workflow.
- Paketkennung bleibt `at.storyforge.editor`. `versionCode 15`, `versionName 1.3.2`.
- Solange kein dauerhaftes Signierzertifikat eingerichtet ist, wird weiterhin nur eine Debug-APK erzeugt, die nicht garantiert über frühere Versionen installiert werden kann.
