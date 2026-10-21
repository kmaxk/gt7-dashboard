# Autoverwaltung und Export/Import – Design

Stand: 2026-10-17

## Ziel

1. **Autonamen:** Statt „Auto #3332“ zeigt die App überall den Namen des Autos. Grundlage ist die Community-Liste (ddm999/gt7info), eigene Namen gehen vor, unbekannte Autos lassen sich selbst benennen.
2. **Export/Import:** Alle Daten lassen sich in eine Datei exportieren und auf einem anderen Rechner (oder später wieder) importieren. Den Ablageort – z. B. einen Nextcloud-, Google-Drive- oder Dropbox-Ordner – wählt man im Dateidialog. Der Import führt zusammen, statt zu ersetzen.

Erfolgskriterien:
- Ein Auto, das in einer Sitzung vorkommt, lässt sich im Tab „Autos“ umbenennen; der neue Name erscheint sofort in Live, Analyse und Bestenliste.
- Export auf Rechner A, Import auf Rechner B: B zeigt danach die Fahrer, Strecken, Runden und Bestenlisten von A zusätzlich zu den eigenen.
- Denselben Export zweimal zu importieren erzeugt keine Duplikate.

Nicht-Ziele: automatische Synchronisierung, direkte Anbindung an Cloud-APIs (WebDAV, Google Drive), Teil-Exporte, Ersetzen der lokalen Datenbank, Löschen von Autos, Aktualisieren der Community-Liste aus dem Internet.

## Teil 1: Autoverwaltung

### Daten

- `cars.csv` (Compose-Ressource) enthält die Community-Liste `ID,ShortName,Maker`; `maker.csv` (`ID,Name,Country`) liefert die Herstellernamen zur Anzeige und Suche. Beide werden einmalig ins Repo kopiert und nur mit App-Updates aktualisiert.
- Neue Tabelle (Schema-Version 4, Migration `3.sqm`):

```sql
CREATE TABLE car_name (
    car_code INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL
);
```

Sie enthält nur eigene Namen.

### CarCatalog

`CarCatalog(csv, makersCsv, custom: Map<Int, String>)`:
- `name(code)`: eigener Name, sonst Listenname, sonst „Auto #code“. Alle bestehenden Aufrufer bleiben unverändert.
- `listName(code)`, `maker(code)`, `isCustom(code)` für den Autos-Tab.
- `search(query)`: Autos der Liste und eigene Autos, deren Name, Hersteller oder ID die Suche enthält (ohne Groß-/Kleinschreibung).
- `withCustom(map)` liefert einen neuen Katalog mit anderen eigenen Namen; die CSV wird nicht neu gelesen.

`App` hält den Katalog als State. Nach jeder Namensänderung und nach einem Import lädt sie die eigenen Namen neu und reicht den neuen Katalog an alle Tabs weiter.

### Tab „Autos“ (zwischen Strecken und Einstellungen)

- **Gefahren:** alle Autos aus Sitzungen mit Name, Hersteller, Herkunft (Liste / eigener Name / unbekannt), Rundenzahl und zuletzt gefahren. Unbekannte Autos sind hervorgehoben.
- **Suche** über die ganze Liste (Name, Hersteller, ID).
- Je Auto **Umbenennen** und – bei eigenem Namen – **Zurücksetzen** auf den Listennamen.
- **Auto hinzufügen:** ID und Name, für Autos, die die Liste nicht kennt. Die ID muss eine positive Zahl sein.

## Teil 2: Export/Import

### Dateiformat

Eine SQLite-Datei `*.gt7export` mit dem Schema der App-Datenbank (inkl. `user_version`).

**Export** (unter dem Controller-Lock, damit während der Fahrt keine Runde verloren geht):
1. Existiert die Zieldatei, wird sie gelöscht (der Dateidialog hat das Überschreiben bestätigt).
2. Eine leere Datei mit dem aktuellen Schema wird angelegt und mit derselben Zusammenführung wie beim Import gefüllt. Einstellungen werden dabei nicht kopiert – PS5-IP, aktiver Fahrer und Anzeigeoptionen bleiben lokal. (Kein `VACUUM INTO`: Android 8–10 kennt es nicht.)
3. Die Datei ist geschlossen, wenn der Export zurückkehrt.

### Import (Zusammenführen)

1. Die Quelldatei wird in eine temporäre Datei im App-Datenordner kopiert, damit die Migration die Quelle nicht verändert.
2. `user_version` aus dem SQLite-Header lesen (Offset 60): kein SQLite-Header oder 0 → „keine GT7-Exportdatei“; größer als die eigene Schema-Version → „mit einer neueren App-Version exportiert“. Kleiner → beim Öffnen auf die aktuelle Version migriert.
3. In **einer Transaktion** der lokalen Datenbank:
   - **Fahrer** werden über den Namen (ohne Groß-/Kleinschreibung) zugeordnet, fehlende angelegt.
   - **Strecken** werden über den Namen (ohne Groß-/Kleinschreibung) zugeordnet – aber nur Strecken, die **vor** dem Import lokal vorhanden waren; zwei gleichnamige Strecken aus der Datei bleiben getrennt. Gibt es lokal mehrere Strecken mit dem Namen (z. B. GP und Nordschleife), entscheidet der Umriss: gleicher Umriss, sonst der nächstliegende. Bei vorhandenen Strecken bleiben lokaler Umriss und lokale Linienwahl. Fehlende Strecken werden mit Umriss angelegt.
   - **Sitzungen** mit gleichem Fahrer, Autocode und `started_at_ms` wie eine lokale Sitzung werden übersprungen. Alle anderen werden mit Runden und Samples unter neuen IDs kopiert.
   - Bei neu angelegten Strecken wird die gewählte Linie auf die neue Runden-ID umgeschrieben; ist die Runde nicht mit importiert worden, bleibt die Linie automatisch.
   - **Autonamen** werden nur für Codes ohne lokalen eigenen Namen übernommen.
4. Ein Fehler rollt alles zurück; die lokale Datenbank bleibt unverändert. Die temporäre Datei wird immer gelöscht.

Ergebnis `ImportResult(drivers, tracks, sessions, skippedSessions, carNames)`, angezeigt als z. B. „Importiert: 3 Fahrer, 2 Strecken, 41 Sitzungen (12 schon vorhanden), 5 Autonamen“.

### Code

- `storage/DataTransfer.kt` (commonMain): `exportTo(path)` und `importFrom(path): ImportResult`, arbeitet auf `Gt7Database`. Testbar mit Dateien im Temp-Ordner.
- `expect fun openDatabaseFile(path): SqlDriver` (mit Schema: legt an bzw. migriert) und `expect fun pickExportFile()` / `pickImportFile()` (AWT `FileDialog`, Endung `.gt7export`) neben `pickRecordingFile()`.
- `SessionController.importData(path)` führt den Import unter dem Controller-Lock aus und lädt danach Fahrer, Streckenlinie, Referenz und Kandidaten neu und erhöht `tracksVersion`.
- Einstellungen: Abschnitt „Daten“ mit „Exportieren…“ und „Importieren…“ und der Ergebnis- bzw. Fehlermeldung. Nach dem Import lädt die App den Autokatalog neu.

## Tests

- `CarCatalog`: Reihenfolge eigener Name > Liste > Fallback, Hersteller, Suche, `withCustom`.
- Repository: Autonamen setzen, überschreiben, zurücksetzen; gefahrene Autos mit Rundenzahl.
- Migration: v1-Datenbank landet bei `user_version` 4 mit leerer `car_name`.
- DataTransfer:
  - Export und Import in eine leere Datenbank ergibt dieselben Fahrer, Strecken, Sitzungen, Runden und Samples; Einstellungen kommen nicht mit.
  - Zweimal importieren erzeugt keine Duplikate.
  - Gleichnamige Fahrer und Strecken werden zusammengeführt; die Linie einer neuen Strecke zeigt auf die importierte Runde; Autonamen füllen nur Lücken.
  - Ein Export im v1-Schema wird migriert importiert, die Quelldatei bleibt unverändert.
  - Leere/kaputte Datei und Datei einer neueren Version liefern einen Fehler; die lokale Datenbank bleibt unverändert.
