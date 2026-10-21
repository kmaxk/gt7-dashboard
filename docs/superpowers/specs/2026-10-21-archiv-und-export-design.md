# Archiv und Export – Design

Stand: 2026-10-21

## Ziel

Die Sitzungsübersicht und die Rundenliste holen bei den Archivfunktionen von ApexTrace auf. Einzelne Runden lassen sich als Datei exportieren, die Analyse als Bild.

Erfolgskriterien:
- Sitzungen lassen sich umbenennen und einzeln löschen. Die Übersicht hat eine Textsuche über Name, Strecke, Auto und Fahrer.
- Je Fahrer und Strecke lässt sich eine Runde als **Referenzrunde** markieren (Stern). Sie gilt als Live-Referenz und als Vergleichsrunde des Renningenieurs.
- Die Rundentabelle zeigt Vmax, drei Sektorzeiten mit der jeweils besten hervorgehoben und die theoretische Bestzeit der Sitzung.
- Runde A lässt sich als CSV oder JSON speichern, die sichtbare Analyse als PNG.

Nicht-Ziele: Export ganzer Sitzungen als JSON/ZIP (dafür gibt es den Datenexport), Import einzelner Runden, Sektorgrenzen aus dem Spiel (GT7 liefert keine), Teilen im Netz.

## Datenbank (Migration 4)

```sql
ALTER TABLE session ADD COLUMN name TEXT;
ALTER TABLE lap ADD COLUMN is_reference INTEGER NOT NULL DEFAULT 0;
```

- `session.name`: frei wählbar, leer = kein Name.
- `lap.is_reference`: höchstens eine Runde je Fahrer und Strecke. Das Setzen hebt die bisherige Markierung desselben Fahrers auf derselben Strecke auf, in einer Transaktion.
- Der Datenexport trägt beide Spalten mit. Beim Import bleibt eine Referenzmarkierung nur, wenn der Fahrer auf der Strecke lokal noch keine Referenzrunde hat.

## Sitzungen

### Repository

| Funktion | Verhalten |
|---|---|
| `renameSession(id, name)` | leerer Name (nach `trim`) speichert `null` |
| `deleteSession(id)` | löscht Samples, Runden und Sitzung in einer Transaktion |
| `SessionSummary.name` | neu, aus `session.name` |

### Controller

- `activeSession: StateFlow<Long?>` – die laufende Sitzung.
- `deleteSession(id)`: lehnt die laufende Sitzung ab (`false`). Sonst löschen und danach Streckenlinie und Live-Referenz neu laden, weil eine gelöschte Runde beides gewesen sein kann.
- `setReferenceLap(lapId, on)`: markiert oder hebt auf, danach Live-Referenz neu laden.

### Oberfläche

- Kopf der geöffneten Sitzung: „Umbenennen“ (Dialog mit Textfeld) und „Löschen“ (Bestätigung „Sitzung löschen? … mit allen Runden. Das lässt sich nicht rückgängig machen.“). Bei der laufenden Sitzung ist „Löschen“ deaktiviert mit dem Hinweis „läuft gerade“.
- Nach dem Löschen zurück zur Übersicht; gewählte Runden der Sitzung fallen aus der Auswahl.
- Übersicht: Suchfeld über der Tabelle. Treffer, wenn Name, Strecke, Autoname oder Fahrer den Text enthalten (Groß-/Kleinschreibung egal, mehrere Wörter müssen alle passen). Die Suche wirkt zusätzlich zu Strecken- und Autofilter.
- Der Sitzungsname steht in der Streckenspalte vor der Strecke: „Quali-Training · Sugo IRC“.

## Referenzrunde

- Rundenliste: Stern je Runde (☆/★), nur bei gültigen Runden und Sitzungen mit Strecke. Ein Klick setzt oder hebt die Markierung auf. Neben dem Stern steht „REF“.
- Live: `bestLap(track, driver, car)` liefert zuerst die Referenzrunde des Fahrers auf der Strecke (egal welches Auto), sonst wie bisher. Ist eine Referenzrunde gesetzt, ersetzt eine schnellere neue Runde sie während der Fahrt **nicht**.
- Renningenieur: `comparisonLap` nimmt zuerst die Referenzrunde desselben Fahrers (außer der gewählten Runde), dann wie bisher. Neue Basis `REFERENCE`, Text „Referenzrunde“ / „reference lap“.

## Vmax, Sektoren, theoretische Bestzeit (`session/LapStats.kt`)

| Funktion | Verhalten |
|---|---|
| `vmaxKmh(samples)` | höchste Geschwindigkeit in km/h, `null` ohne Samples |
| `sectorBoundaries(lengthM)` | zwei Grenzen bei ⅓ und ⅔ der Länge |
| `sectorTimes(samples, boundaries)` | drei Zeiten in ms über `timeAtDistance`; `null`, wenn die Runde eine Grenze nicht erreicht |
| `theoreticalBest(sectors)` | Summe der besten Zeit je Sektor; `null` ohne vollständige Sektorzeiten |

- Die Sektorlänge kommt aus der schnellsten gültigen Runde der Sitzung (Distanz des letzten Samples), damit alle Runden einer Sitzung dieselben Grenzen haben.
- Nur gültige Runden zählen für die besten Sektoren und die theoretische Bestzeit; ungültige zeigen ihre Sektorzeiten grau.
- Rundentabelle (Detailanalyse): neue Spalten `Vmax`, `S1`, `S2`, `S3`; die beste Zeit je Sektor violett. Darunter: „Theoretische Bestzeit: 1:25.512 (−0.285 zur Bestzeit)“.

## Export

### Runde als Datei (`storage/LapExport.kt`, reine Funktionen)

- CSV: Kopfzeile `time_ms,distance_m,speed_kmh,throttle_pct,brake_pct,gear,rpm,x,z,boost_bar`, eine Zeile je Sample, Punkt als Dezimalzeichen, Prozent 0–100.
- JSON: `{"track":…,"car":…,"driver":…,"session_started_ms":…,"lap":3,"time_ms":…,"valid":true,"samples":[{…}]}` mit denselben Feldern wie die CSV. Texte JSON-escaped; fehlende Werte `null`.
- Dateiname: `<Strecke>_Runde-<n>_<m-ss-mmm>.<csv|json>`, Sonderzeichen durch `-` ersetzt, z. B. `Sugo-IRC_Runde-3_1-25-797.csv`. Ohne Strecke `Runde-3_…`.
- Gespeichert über den vorhandenen `FilePicker.save`.

### Analyse als PNG

- Knopf „PNG…“ in der Analyse-Werkzeugleiste. Er speichert, was gerade im Analysebereich zu sehen ist (Charts, Karte, Panels), nicht verdeckte Teile außerhalb des Scrollbereichs.
- Aufnahme über eine Compose-`GraphicsLayer` (`rememberGraphicsLayer`, `toImageBitmap`). PNG-Kodierung plattformabhängig: `expect fun encodePng(ImageBitmap): ByteArray` – Desktop über Skia, Android über `Bitmap.compress`.
- Dateiname: `<Strecke>_Runde-<n>_Analyse.png`.

### Oberfläche

In der Werkzeugleiste der Analyse, sobald Runde A gewählt ist: „CSV…“, „JSON…“ (Runde A) und „PNG…“. Fehler beim Schreiben erscheinen als kurze Meldung unter der Leiste.

## Tests

- **Repository:** umbenennen (leer → null), Sitzung löschen entfernt Runden und Samples und lässt andere Sitzungen stehen; Referenz setzen hebt die alte desselben Fahrers auf derselben Strecke auf, nicht die anderer Fahrer oder Strecken; `bestLap` bevorzugt die Referenzrunde (auch in anderem Auto); `comparisonLap` bevorzugt die Referenzrunde und schließt die gewählte Runde aus.
- **Migration:** Version-3-Datenbank wird auf 4 migriert, alte Sitzungen ohne Namen, Runden ohne Referenz.
- **Datenaustausch:** Name und Referenz kommen beim Import mit; Referenz nicht, wenn lokal schon eine existiert.
- **Controller:** laufende Sitzung lässt sich nicht löschen; markierte Referenz wird nicht von einer schnelleren Runde ersetzt.
- **LapStats:** Vmax, Sektorgrenzen, Sektorzeiten (auch Runde zu kurz → null), theoretische Bestzeit nur aus gültigen Runden.
- **Suche:** mehrere Wörter, Groß-/Kleinschreibung, Treffer über Autoname und Sitzungsname.
- **Export:** CSV-Kopf und Zeilen, JSON mit Escaping und `null`, Dateinamen mit Sonderzeichen.
