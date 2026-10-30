# StoryForge 1.3.7

## Medienimport und Sicherungen
Beim Import aus der Galerie sowie beim ZIP-Backup und ZIP-Wiederherstellen zeigt die App eine sichtbare modale Fortschrittsanzeige. Während der Dateioperation können keine anderen Editor-Aktionen ausgelöst werden. Der Medienimport zeigt den Zähler importierter/verarbeiteter Dateien.

## Texteditor mit Live-Vorschau
- Einen Text hinzufügen oder bearbeiten zeigt das Bild des aktuellen Clips beziehungsweise des betroffenen Zeitpunkts.
- Der Text lässt sich mit dem Finger im Bild verschieben.
- Ein Schieberegler verändert die Schriftgröße sofort in der Vorschau.
- Position, Text, Anzeigebeginn/-ende, Farbe und Schriftgröße werden im gespeicherten Projekt erhalten.
- Die normale Filmvorschau berücksichtigt Textgröße und Position, ebenso der FFmpeg-MP4-Export.
- Vorhandene Projekte bleiben rückwärtskompatibel.

Testlauf: `gradle --no-daemon :app:testDebugUnitTest`; APK-Build in GitHub Actions.
