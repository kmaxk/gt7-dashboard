# Fahrer, Gegner und Streckenkarte – Design

Stand: 2026-10-17 · Layout-Entwurf: Variante A (Karte als Seitenleiste), https://claude.ai/artifact/9k4s8u7Jd4GqwAtSGGiEC7

## Ziel

Mehrere Fahrer wechseln sich an **einer PS5** ab und fahren virtuell gegeneinander:
1. Jeder Fahrer hat ein Profil; Runden gehören dem Fahrer, der sie gefahren ist.
2. Vor dem Fahren wählt man einen **Gegner** (ein anderer Fahrer oder „Meine Bestzeit“). Beim Fahren zeigt das Live-Dashboard das Delta zu dessen Bestrunde und einen **Geisterpunkt** auf einer **Streckenkarte**.
3. Eine **Bestenliste** pro Strecke vergleicht die Fahrer.

Erfolgskriterien:
- Fahrer lassen sich im Live-Tab anlegen und auswählen; ein Fahrerwechsel braucht keine erneute Streckenbestätigung.
- Fährt Max gegen Anna, zeigen Delta, Prognose, Geisterpunkt und Abstand die Werte relativ zu Annas Bestrunde auf dieser Strecke (gleiches Auto bevorzugt).
- Bestehende Sitzungen und Runden bleiben nach dem Update erhalten und gehören „Fahrer 1“.

Nicht-Ziele: Fahrerwechsel innerhalb einer Sitzung, Datenaustausch zwischen Rechnern oder PS5s, Profilbilder/Farben pro Fahrer, gleichzeitiges Fahren.

## Datenmodell

Neue Tabelle und Spalte (Schema-Version 2):

```sql
CREATE TABLE driver (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);
-- session bekommt:
driver_id INTEGER REFERENCES driver(id)
```

`driver_id` ist in SQLite per `ALTER TABLE` nur nullable hinzufügbar; der Code schreibt immer einen Fahrer. Neue Sitzungen ohne Fahrer gibt es nicht.

**Migration `1.sqm`** (bestehende DB hat `user_version = 1`):
1. `driver` anlegen, „Fahrer 1“ einfügen.
2. `ALTER TABLE session ADD COLUMN driver_id …`
3. `UPDATE session SET driver_id = (SELECT id FROM driver WHERE name = 'Fahrer 1')`.

Neue Datenbanken (frisches Schema) legen „Fahrer 1“ beim ersten Start an, falls `driver` leer ist (Repository, nicht Schema), damit es immer einen aktiven Fahrer gibt.

Einstellungen (`setting`):
- `activeDriver` – ID des aktiven Fahrers. Fehlt oder zeigt auf einen gelöschten Fahrer → der Fahrer mit kleinster ID.
- `opponent` – `self` oder eine Fahrer-ID. Fehlt oder ungültig → `self`.

## Repository (`SessionRepository`)

Neu bzw. geändert:
- `drivers(): List<Driver>`, `createDriver(name): Long`, `renameDriver(id, name)`, `deleteDriver(id)` – Löschen nur, wenn der Fahrer keine Sitzungen hat (sonst `false`).
- `createSession(driverId, carCode, startedAtMs)`.
- `bestLap(trackId, driverId, carCode): BestLap?` – schnellste gültige Runde des Fahrers auf der Strecke mit diesem Auto; ohne Treffer die schnellste mit beliebigem Auto. `BestLap` enthält zusätzlich `carCode` und `drivenAtMs`, damit das Dashboard Auto und Datum anzeigen kann.
- `leaderboard(trackId, carCode: Int?): List<LeaderboardEntry>` – pro Fahrer die schnellste gültige Runde (optional nur ein Auto), sortiert nach Zeit; Eintrag mit Fahrer, Zeit, Auto, Datum.
- `sessions()` liefert zusätzlich Fahrer-ID und -Name.
- Das bisherige `bestLap(trackId)` entfällt.

## Controller (`SessionController`)

Zustand: `activeDriver: StateFlow<Driver>`, `opponent: StateFlow<Opponent>` (`Self` | `Driver(id, name)`), `drivers: StateFlow<List<Driver>>`, `reference: StateFlow<ReferenceInfo?>` (Besitzer, Zeit, Auto, Datum, `sameCar`, Samples).

- **`selectDriver(id)`**: speichert `activeDriver`. Läuft eine Sitzung, endet sie (wie bei Timeout) und die Engine startet beim nächsten Frame eine neue. **Strecke und Umriss bleiben erhalten** und werden der neuen Sitzung sofort zugeordnet; kein Streckendialog. Ist der neue Fahrer gleich dem Gegner, wird der Gegner `Self`.
- **`selectOpponent(o)`**: speichert `opponent`, lädt die Referenz neu, falls die Strecke bekannt ist.
- **`createDriver(name)`**: legt an, aktualisiert `drivers`; leere oder doppelte Namen werden abgelehnt (Ergebnis an die UI).
- **Referenz laden** (`loadReference`): bei Streckenbestätigung, Gegnerwechsel und Fahrerwechsel. Gegner-ID = aktiver Fahrer bei `Self`, sonst die gewählte ID; Abfrage `bestLap(track, gegnerId, carCode der Sitzung)`.
- **Eigene Runde als Referenz**: nur bei `opponent == Self` (wie heute: schneller als Referenz → neue Referenz). Bei einem anderen Gegner bleibt dessen Runde Referenz.
- `SessionEngine` erhält für Prognose und Delta wie bisher Samples und Zeit; für den Geisterpunkt zusätzlich nichts – die Position wird in der UI aus Referenz-Samples und `lapTimeMs` berechnet.

Die Engine muss eine Sitzung von außen beenden können: neue Methode `SessionEngine.endSession(atMs)`, die `Ended` liefert und den Zustand wie beim Timeout zurücksetzt.

## Streckenkarte im Live-Tab

- **Linie**: die Referenzrunde (x/z der Samples). Ohne Referenz die bisher gefahrene Linie der aktuellen Runde.
- **Eigener Punkt**: `frame.x/z`.
- **Geisterpunkt**: Position der Referenzrunde zur aktuellen Rundenzeit – reine Funktion `positionAtTime(samples, timeMs): Pair<Float, Float>?` (lineare Interpolation über `timeMs`, `null` außerhalb).
- Projektion: dieselbe `projectPath`-Logik wie die Analyse-Karte; beide Punkte werden mit der Projektion der Linie transformiert, damit sie auf ihr liegen. Die Projektion wird nur neu berechnet, wenn sich die Linie ändert.
- Der Live-Tab braucht dafür die Referenz-Samples: `LiveState` bekommt kein Sample-Array; die UI liest sie aus `controller.reference` (ändert sich selten).

## Oberfläche

**Live-Tab** (Variante A):
- Oben eine Leiste: Chip „Fahrer ▾“ und Chip „Gegner ▾“. Menü listet Fahrer (Gegner-Menü zusätzlich „Meine Bestzeit“, ohne den aktiven Fahrer) und „+ Neuer Fahrer“ mit Eingabefeld. Hinweis „Fahrerwechsel beendet die laufende Sitzung“.
- Rechts Seitenleiste (30 % Breite): Streckenkarte mit Legende, darunter „ABSTAND ZU ‹NAME›“ groß (grün/rot), Trend „▲ du holst auf“ / „▼ ‹Name› zieht weg“ (Vergleich mit dem Delta vor 1 s), Zeile „‹Name›s Bestrunde · gleiches Auto (‹Auto›) · ‹Datum›“ bzw. „anderes Auto: ‹Auto›“. Ohne Referenz: „Noch keine Runde von ‹Name› auf dieser Strecke“.
- Zeiten-Band: „BESTE“ wird zu „‹NAME›“ mit der Referenzzeit, „DELTA“ zu „VS. ‹NAME›“; bei `Self` bleiben die bisherigen Beschriftungen.
- Die Hauptspalte schrumpft auf 70 %; die Skalierung (`dashboardScale`) richtet sich weiter nach der Gesamtfläche, die Schriftgrößen im Hauptbereich sinken um ca. 12 % (wie im Entwurf).

**Neuer Tab „Bestenliste“** (zwischen Analyse und Einstellungen): Auswahl Strecke, Auswahl Auto („Alle Autos“ oder ein Auto, das auf der Strecke gefahren wurde). Tabelle: Platz, Fahrer, Zeit, Rückstand zum Ersten, Auto, Datum.

**Analyse**: Filter-Chips „Alle Fahrer“ / je Fahrer über der Sitzungsliste; jede Sitzungszeile zeigt den Fahrernamen. Rundenvergleich unverändert (Runden verschiedener Fahrer sind vergleichbar).

**Einstellungen**: Abschnitt „Fahrer“: Liste mit Umbenennen; „Löschen“ nur für Fahrer ohne Sitzungen, sonst ausgegraut mit Hinweis.

## Fehlerbehandlung

- DB-Fehler wie bisher über `db(...)` geloggt; UI fällt auf „keine Referenz“ zurück.
- Migration läuft in einer Transaktion (SQLDelight-Migration); schlägt sie fehl, startet die App nicht mit halbem Schema, sondern loggt den Fehler – die bestehende DB bleibt unverändert.
- Ungültige Settings (gelöschter Fahrer) → Rückfall wie oben beschrieben.

## Tests

- **Migration**: DB mit Schema v1 und Testdaten anlegen, migrieren → „Fahrer 1“ existiert, alle Sitzungen haben `driver_id`, Runden unverändert.
- **Repository**: `bestLap` mit gleichem Auto, Rückfall auf anderes Auto, kein Treffer; `leaderboard` mit/ohne Autofilter, ungültige Runden ignoriert; `deleteDriver` mit/ohne Sitzungen.
- **Controller**: Fahrerwechsel beendet Sitzung und übernimmt Strecke ohne Dialog; Gegnerwechsel lädt Referenz; eigene schnellere Runde ersetzt Referenz nur bei `Self`; Fahrer = Gegner → Gegner `Self`.
- **Reine Funktionen**: `positionAtTime` (Interpolation, Ränder), Trend-Berechnung.
- **UI**: Headless-Render des Live-Tabs mit Mock-Daten (Fenster und Vollbild) zur Sichtprüfung.
