# Fahrer, Gegner und Streckenkarte – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fahrerprofile, Gegnerwahl mit Geisterpunkt auf einer Live-Streckenkarte, Bestenliste pro Strecke und Fahrerfilter in der Analyse.

**Architecture:** Der Fahrer hängt an der Sitzung (`session.driver_id`, Schema v2 per SQLDelight-Migration). `SessionController` hält aktiven Fahrer und Gegner, lädt die Referenzrunde des Gegners und beendet bei Fahrerwechsel die Sitzung über die Engine. Die UI berechnet den Geisterpunkt aus den Referenz-Samples und der aktuellen Rundenzeit.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (Desktop), SQLDelight 2.1, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-10-17-fahrer-und-gegner-design.md`

## Global Constraints

- Bestehende Daten bleiben erhalten: DB mit `user_version = 1` wird auf 2 migriert, alle Sitzungen gehören danach „Fahrer 1“.
- Es gibt immer mindestens einen Fahrer; Standardname exakt `Fahrer 1`.
- Settings-Keys exakt `activeDriver` (Fahrer-ID) und `opponent` (`self` oder Fahrer-ID).
- Gegner-Referenz: Bestrunde des Gegners auf der Strecke im selben Auto, sonst mit beliebigem Auto; nur gültige Runden.
- Eigene schnellere Runde ersetzt die Referenz nur bei Gegner `self`.
- Fahrerwechsel beendet die laufende Sitzung; Strecke und Umriss gehen ohne Dialog auf die neue Sitzung über.
- Löschen nur für Fahrer ohne Sitzungen.
- UI-Texte deutsch, exakt: „Fahrer“, „Gegner“, „Meine Bestzeit“, „+ Neuer Fahrer“, „Fahrerwechsel beendet die laufende Sitzung“, „ABSTAND ZU ‹NAME›“, „▲ du holst auf“, „▼ ‹Name› zieht weg“, „Noch keine Runde von ‹Name› auf dieser Strecke“, Tab „Bestenliste“, „Alle Autos“, „Alle Fahrer“.
- Tests: `./gradlew :shared:desktopTest` muss am Ende jeder Task grün sein.

## Review Focus

1. **Fahrernamen mit Leerzeichen / Groß-Kleinschreibung** („ anna “ neben „Anna“): erwartet wird Trimmen und Ablehnen von Dubletten ohne Rücksicht auf Groß-/Kleinschreibung → Test in Task 1 (`validateDriverName`).
2. **Gegner hat auf der Strecke keine Runde**: Referenz `null`, kein Geist, Hinweistext statt Abstand → Tests in Task 4 und Task 6.
3. **Fahrerwechsel, während eine Runde auf ihre GT7-Zeit wartet (pending)**: die Runde wird noch der alten Sitzung zugeschrieben (Fallback wie beim Timeout) → Test in Task 3.
4. **Aktuelle Runde länger als die Referenzrunde**: `positionAtTime` liefert `null`, der Geist verschwindet statt zu springen → Test in Task 5.
5. **Gespeicherter Fahrer/Gegner existiert nicht mehr** (gelöscht oder Settings kaputt): Rückfall auf kleinste Fahrer-ID bzw. `self` → Test in Task 4.

---

## File Structure

- `shared/src/commonMain/sqldelight/de/stiller/gt7/db/Gt7.sq` – Schema v2 (`driver`, `session.driver_id`) und neue Queries.
- `shared/src/commonMain/sqldelight/de/stiller/gt7/db/1.sqm` – **neu**, Migration v1 → v2.
- `shared/src/commonMain/kotlin/de/stiller/gt7/storage/SessionRepository.kt` – Fahrer-CRUD, `bestLap` je Fahrer/Auto, `leaderboard`.
- `shared/src/commonMain/kotlin/de/stiller/gt7/storage/Drivers.kt` – **neu**, `Driver`, `validateDriverName`.
- `shared/src/commonMain/kotlin/de/stiller/gt7/session/SessionEngine.kt` – `endSession(atMs)`.
- `shared/src/commonMain/kotlin/de/stiller/gt7/session/SessionController.kt` – Fahrer/Gegner/Referenz.
- `shared/src/commonMain/kotlin/de/stiller/gt7/session/Opponent.kt` – **neu**, `Opponent`, `ReferenceInfo`.
- `shared/src/commonMain/kotlin/de/stiller/gt7/session/LapMath.kt` – `positionAtTime`, `gapTrend`.
- `shared/src/commonMain/kotlin/de/stiller/gt7/ui/ChartScale.kt` – `MapProjection`.
- `shared/src/commonMain/kotlin/de/stiller/gt7/ui/LiveMap.kt` – **neu**, Live-Karte + Gegnerbox.
- `shared/src/commonMain/kotlin/de/stiller/gt7/ui/DriverPicker.kt` – **neu**, Chips mit Menü.
- `shared/src/commonMain/kotlin/de/stiller/gt7/ui/LiveScreen.kt` – Layout Variante A.
- `shared/src/commonMain/kotlin/de/stiller/gt7/ui/LeaderboardScreen.kt` – **neu**.
- `shared/src/commonMain/kotlin/de/stiller/gt7/ui/App.kt`, `AnalysisScreen.kt`, `AnalysisLists.kt`, `SettingsScreen.kt` – Tab, Filter, Fahrerverwaltung.

---

### Task 1: Schema v2, Migration und Fahrer im Repository

**Files:**
- Modify: `shared/src/commonMain/sqldelight/de/stiller/gt7/db/Gt7.sq`
- Create: `shared/src/commonMain/sqldelight/de/stiller/gt7/db/1.sqm`
- Create: `shared/src/commonMain/kotlin/de/stiller/gt7/storage/Drivers.kt`
- Modify: `shared/src/commonMain/kotlin/de/stiller/gt7/storage/SessionRepository.kt`
- Test: `shared/src/desktopTest/kotlin/de/stiller/gt7/storage/MigrationTest.kt` (neu), `shared/src/desktopTest/kotlin/de/stiller/gt7/storage/SessionRepositoryTest.kt`, `shared/src/commonTest/kotlin/de/stiller/gt7/storage/DriversTest.kt` (neu)

**Interfaces:**
- Produces:
  - `data class Driver(val id: Long, val name: String)` (storage)
  - `fun validateDriverName(name: String, existing: List<Driver>): String?` – liefert den getrimmten Namen oder `null`, wenn leer oder (case-insensitiv) schon vorhanden.
  - `SessionRepository.drivers(): List<Driver>` (nach id), `ensureDefaultDriver(): Driver`, `createDriver(name: String): Long`, `renameDriver(id: Long, name: String)`, `deleteDriver(id: Long): Boolean`
  - `SessionRepository.driversWithSessions(): Set<Long>` – IDs der Fahrer mit mindestens einer Sitzung
  - `SessionRepository.createSession(driverId: Long, carCode: Int, startedAtMs: Long): Long` (ersetzt die alte Signatur)

- [ ] **Step 1: Failing tests**
  - `DriversTest.trimsAndRejectsDuplicates`: `validateDriverName("  Anna ", emptyList()) == "Anna"`; `validateDriverName("anna", listOf(Driver(1,"Anna"))) == null`; `validateDriverName("   ", emptyList()) == null`.
  - `MigrationTest.migratesV1DatabaseKeepingSessions`: Datei-DB in `kotlin.io.path.createTempFile`, darin per `driver.execute` die **v1-DDL** (die heutigen `CREATE TABLE`-Statements aus `Gt7.sq` als Konstante im Test kopieren), `PRAGMA user_version = 1`, zwei Sitzungen + eine Runde einfügen; Treiber schließen. Dann `JdbcSqliteDriver("jdbc:sqlite:$path", Properties(), Gt7Database.Schema)` öffnen → `SessionRepository(...).drivers() == listOf(Driver(1, "Fahrer 1"))`, beide Sitzungen haben `driverId == 1` in `sessions().first()`, `laps(sessionId).size == 1`, `PRAGMA user_version` == 2.
  - `SessionRepositoryTest.freshDatabaseGetsDefaultDriver`: `repo().ensureDefaultDriver().name == "Fahrer 1"`, zweiter Aufruf liefert dieselbe id.
  - `SessionRepositoryTest.deleteOnlyDriversWithoutSessions`: Fahrer mit Sitzung → `deleteDriver == false` und Fahrer bleibt; ohne Sitzung → `true`.
  - Bestehende Repository-Tests auf `createSession(driverId, …)` umstellen (Fahrer über `ensureDefaultDriver()`).
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*Migration*' --tests '*Drivers*' --tests '*SessionRepository*'` → FAIL (unresolved references).
- [ ] **Step 3: Implementieren**
  - `Gt7.sq`: `CREATE TABLE driver (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE);` vor `session`; `session` bekommt `driver_id INTEGER REFERENCES driver(id)` als **letzte** Spalte (gleiche Spaltenreihenfolge wie nach `ALTER TABLE`). `insertSession` mit `driver_id`. Queries `allDrivers`, `insertDriver`, `renameDriver`, `deleteDriver`, `driversWithSessions` (`SELECT DISTINCT driver_id FROM session WHERE driver_id IS NOT NULL`).
  - `1.sqm`: `CREATE TABLE driver (…)`; `INSERT INTO driver(name) VALUES ('Fahrer 1');`; `ALTER TABLE session ADD COLUMN driver_id INTEGER REFERENCES driver(id);`; `UPDATE session SET driver_id = (SELECT id FROM driver WHERE name = 'Fahrer 1');`
  - `createDriver` lehnt nichts ab (Validierung ist Sache des Aufrufers); `ensureDefaultDriver` legt `Fahrer 1` an, wenn `drivers()` leer ist.
  - `shared/src/desktopMain/.../Platform.desktop.kt` bleibt: `JdbcSqliteDriver(url, Properties(), Schema)` führt die Migration anhand `user_version` aus – der MigrationTest beweist das.
- [ ] **Step 4:** Tests → PASS; komplette Suite `./gradlew :shared:desktopTest` grün (Controller ruft `createSession` – vorläufig mit `repo.ensureDefaultDriver().id`, Task 4 ersetzt das).
- [ ] **Step 5: Commit** `feat(storage): drivers table with migration from schema v1`

---

### Task 2: Bestrunde je Fahrer/Auto, Bestenliste, Sitzungen mit Fahrer

**Files:**
- Modify: `Gt7.sq`, `SessionRepository.kt`
- Test: `SessionRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1 (`Driver`, `createSession(driverId, …)`).
- Produces:
  - `data class BestLap(val timeMs: Int, val samples: List<LapSample>, val carCode: Int, val drivenAtMs: Long)` (ersetzt die alte)
  - `SessionRepository.bestLap(trackId: Long, driverId: Long, carCode: Int): BestLap?` – gleiches Auto zuerst, sonst beliebiges; `drivenAtMs` = `session.started_at_ms`.
  - `data class LeaderboardEntry(val driver: Driver, val timeMs: Int, val carCode: Int, val drivenAtMs: Long)`
  - `SessionRepository.leaderboard(trackId: Long, carCode: Int?): List<LeaderboardEntry>` – je Fahrer schnellste gültige Runde, aufsteigend nach Zeit.
  - `SessionRepository.carsOnTrack(trackId: Long): List<Int>`
  - `SessionSummary` + `driverId: Long?`, `driverName: String?`
  - Altes `bestLap(trackId)` entfällt.

- [ ] **Step 1: Failing tests**
  - `bestLapPrefersSameCar`: Anna fährt Auto 1 in 100 s und Auto 2 in 95 s → `bestLap(t, anna, 1).timeMs == 100_000`, `.carCode == 1`.
  - `bestLapFallsBackToOtherCar`: Anna nur Auto 2 → `bestLap(t, anna, 1)?.carCode == 2`.
  - `bestLapOnlyOwnDriverAndValid`: Max 90 s gültig, Anna 99 s gültig + 80 s ungültig → `bestLap(t, anna, 1).timeMs == 99_000`; ohne Runden → `null`.
  - `leaderboardOneEntryPerDriver`: Max 101 s / 99 s, Anna 100 s → Namen `["Max", "Anna"]`, Zeiten `[99_000, 100_000]`; mit `carCode` gefiltert nur Runden dieses Autos.
  - `sessionsCarryDriverName`: `sessions().first().single().driverName == "Max"`.
  - Bestehende `bestLap`-Tests auf die neue Signatur umschreiben.
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*SessionRepository*'` → FAIL.
- [ ] **Step 3:** Queries `bestLapForDriverAndCar`, `bestLapForDriver` (je `ORDER BY lap.time_ms, lap.id LIMIT 1`, `JOIN session`), `leaderboard` (Gruppierung je `driver_id` mit `MIN(time_ms)`, danach Join auf die Runde mit dieser Zeit für Auto und Datum; bei Gleichstand die frühere Runde), `carsOnTrack`, `sessionSummaries` + `LEFT JOIN driver`.
- [ ] **Step 4:** Tests → PASS, Suite grün (Controller-Aufruf von `bestLap` vorläufig mit aktivem Standardfahrer und Sitzungs-`carCode`).
- [ ] **Step 5: Commit** `feat(storage): best lap per driver and car, leaderboard`

---

### Task 3: Sitzung von außen beenden

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/stiller/gt7/session/SessionEngine.kt`
- Test: `shared/src/commonTest/kotlin/de/stiller/gt7/session/SessionEngineTest.kt`

**Interfaces:**
- Produces: `SessionEngine.endSession(atMs: Long): List<SessionEvent>` – leer, wenn keine Sitzung läuft; sonst wie Timeout: offene (pending) Runde per Fallback abschließen, `Ended`, `live` mit `sessionActive = false, deltaMs = null, predictedLapMs = null`. Der nächste `onFrame` startet eine neue Sitzung (`Started`).

- [ ] **Step 1: Failing tests**
  - `endSessionStopsAndNextFrameRestarts`: `driverInLap2()`, `drive(500)`, `endSession(now)` enthält `Ended`; `live.value.sessionActive == false`; nächster `step()` liefert `Started`.
  - `endSessionFinalizesPendingLap`: Rundenwechsel ohne aktualisiertes `lastLapMs` auslösen (Helper `nextLap(report = false)`), dann `endSession` → Events enthalten `LapCompleted` mit `isValid == false` vor `Ended`.
  - `endSessionWithoutSessionIsNoop`: frische Engine → `endSession(0)` ist leer.
- [ ] **Step 2:** `--tests '*SessionEngineTest*'` → FAIL.
- [ ] **Step 3:** Rumpf von `onTick`s Timeout-Zweig in eine private Funktion ziehen und von `endSession` mitnutzen.
- [ ] **Step 4:** → PASS. **Step 5: Commit** `feat(session): end a session on demand`

---

### Task 4: Fahrer, Gegner und Referenz im Controller

**Files:**
- Create: `shared/src/commonMain/kotlin/de/stiller/gt7/session/Opponent.kt`
- Modify: `shared/src/commonMain/kotlin/de/stiller/gt7/session/SessionController.kt`
- Test: `shared/src/desktopTest/kotlin/de/stiller/gt7/session/SessionControllerTest.kt`

**Interfaces:**
- Consumes: Task 1–3.
- Produces:
  - `sealed interface Opponent { data object Self : Opponent; data class Of(val driver: Driver) : Opponent }`
  - `data class ReferenceInfo(val owner: Driver, val isSelf: Boolean, val timeMs: Int, val carCode: Int, val drivenAtMs: Long, val sameCar: Boolean, val samples: List<LapSample>)`
  - `SessionController.drivers: StateFlow<List<Driver>>`, `activeDriver: StateFlow<Driver?>` (nach `start()` nie `null`), `opponent: StateFlow<Opponent>`, `reference: StateFlow<ReferenceInfo?>`
  - `selectDriver(id: Long)`, `selectOpponent(opponent: Opponent)`, `createDriver(name: String)` (ungültiger Name → ignoriert), `renameDriver(id: Long, name: String)`, `deleteDriver(id: Long)`
  - `driversWithSessions: StateFlow<Set<Long>>` – aktualisiert nach jeder neuen Sitzung und jeder Fahreränderung
  - Settings-Keys `activeDriver`, `opponent` (`self` | id).

- [ ] **Step 1: Failing tests** (bestehende Helfer `circleLaps`, `FakeSource`, virtuelle Zeit)
  - `loadsDriversAndFallsBackForUnknownSettings`: Settings `activeDriver = "99"`, `opponent = "42"` → nach `start()` + `runCurrent()`: `activeDriver.value?.name == "Fahrer 1"`, `opponent.value == Opponent.Self`.
  - `driverSwitchEndsSessionAndKeepsTrack`: Sitzung fährt 2 Runden, Strecke bestätigen; `createDriver("Anna")`, `selectDriver(anna)`; weiter Frames → zwei Sitzungen in `repo.sessions()`, neue mit `driverName == "Anna"` und **gleicher `trackId`**, `trackQuestion.value == null`.
  - `opponentReferenceIsTheirBestLap`: Anna hat auf der Strecke eine 100-s-Runde (direkt über Repository angelegt); Max fährt mit `selectOpponent(Opponent.Of(anna))` → nach Streckenbestätigung `reference.value?.owner?.name == "Anna"`, `timeMs == 100_000`.
  - `ownFasterLapReplacesOnlySelfReference`: gegen Anna → nach eigener schnellerer Runde bleibt `reference.value.owner == anna`; gegen `Self` → Referenz ist die eigene Runde.
  - `opponentWithoutLapsGivesNoReference`: Gegner ohne Runden → `reference.value == null`, `live.value.deltaMs == null`.
  - `selectingOpponentAsDriverResetsToSelf`: Gegner Anna, `selectDriver(anna)` → `opponent.value == Opponent.Self`.
- [ ] **Step 2:** `--tests '*SessionControllerTest*'` → FAIL.
- [ ] **Step 3: Implementieren**
  - `start()`: `ensureDefaultDriver()`, Settings lesen mit Rückfällen (Global Constraints), Flows setzen.
  - `SessionEvent.Started` → `createSession(activeDriver.id, …)`; wenn eine Strecke aus dem Fahrerwechsel übernommen wird (`carryTrack`), sofort `assignTrack` und `loadReference()`, kein `askForTrack`.
  - `selectDriver`: Setting speichern, `carryTrack = trackId to outline`, `handle(engine.endSession(nowMs()))`.
  - `loadReference()`: Gegner-ID = aktiver Fahrer bei `Self`; `repo.bestLap(trackId, id, carCode)`; `ReferenceInfo` mit `sameCar = best.carCode == carCode`; `setReference(samples, timeMs)` wie bisher.
  - `onLap`: Referenz nur bei `opponent == Self` ersetzen (dann `ReferenceInfo` mit `owner = activeDriver`, `isSelf = true`).
- [ ] **Step 4:** → PASS, Suite grün. **Step 5: Commit** `feat(session): drivers, opponent choice and opponent reference lap`

---

### Task 5: Geisterposition, Trend und Kartenprojektion

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/stiller/gt7/session/LapMath.kt`, `shared/src/commonMain/kotlin/de/stiller/gt7/ui/ChartScale.kt`, `shared/src/commonMain/kotlin/de/stiller/gt7/ui/TrackMap.kt`
- Test: `LapMathTest.kt`, `ChartScaleTest.kt`

**Interfaces:**
- Produces:
  - `fun positionAtTime(samples: List<LapSample>, timeMs: Int): Pair<Float, Float>?` – lineare Interpolation von `(x, z)` über `timeMs`; `null` vor dem ersten oder nach dem letzten Sample.
  - `enum class GapTrend { CLOSING, LOSING, STEADY }`, `fun gapTrend(deltaNowMs: Int?, deltaBeforeMs: Int?): GapTrend` – Änderung < 20 ms → `STEADY`; sinkendes Delta → `CLOSING`.
  - `class MapProjection(xs: List<Float>, zs: List<Float>, width: Float, height: Float, pad: Float) { fun map(x: Float, z: Float): Pair<Float, Float> }` – gleiche Abbildung wie `projectPath` heute; `projectPath` nutzt sie intern (Verhalten unverändert).

- [ ] **Step 1: Failing tests**
  - `positionInterpolatesByTime`: Samples `(t=0,x=0,z=0)`, `(t=1000,x=10,z=-20)` → `positionAtTime(s, 500) == 5f to -10f`.
  - `positionOutsideLapIsNull`: `positionAtTime(s, 1001) == null`, leere Liste → `null`.
  - `gapTrendDirections`: `gapTrend(-200, -100) == CLOSING`, `gapTrend(150, 100) == LOSING`, `gapTrend(105, 100) == STEADY`, `gapTrend(null, 100) == STEADY`.
  - `projectionMatchesProjectPath`: für eine Testrunde gilt `projectPath(...)[i] == MapProjection(...).map(x_i, z_i)` für alle i.
- [ ] **Step 2:** → FAIL. **Step 3:** implementieren. **Step 4:** → PASS.
- [ ] **Step 5: Commit** `feat: ghost position, gap trend and reusable map projection`

---

### Task 6: Live-Tab Variante A

**Files:**
- Create: `shared/src/commonMain/kotlin/de/stiller/gt7/ui/LiveMap.kt`, `shared/src/commonMain/kotlin/de/stiller/gt7/ui/DriverPicker.kt`
- Modify: `shared/src/commonMain/kotlin/de/stiller/gt7/ui/LiveScreen.kt`, `shared/src/commonMain/kotlin/de/stiller/gt7/ui/App.kt`
- Test: `shared/src/commonTest/kotlin/de/stiller/gt7/ui/LiveLabelsTest.kt` (neu)

**Interfaces:**
- Consumes: Task 4 (Controller-Flows/-Aktionen), Task 5 (`positionAtTime`, `gapTrend`, `MapProjection`), `CarCatalog` für Autonamen.
- Produces:
  - `fun opponentLabels(reference: ReferenceInfo?, opponent: Opponent, activeName: String): OpponentLabels` mit `data class OpponentLabels(val bestLabel: String, val deltaLabel: String, val gapTitle: String, val opponentName: String)` – bei `Self`: `"BESTE"`, `"DELTA"`, `"ABSTAND ZU DEINER BESTZEIT"`; sonst `"ANNA"`, `"VS. ANNA"`, `"ABSTAND ZU ANNA"`.
  - `LiveScreen(state, connection, host, unit, controller, cars)` – Signatur erweitert; `App.kt` übergibt `controller` und `cars`.

- [ ] **Step 1: Failing test** `LiveLabelsTest`: obige Labels für `Self` und für `Opponent.Of(Driver(2, "Anna"))`.
- [ ] **Step 2:** → FAIL. **Step 3:** `opponentLabels` implementieren → PASS.
- [ ] **Step 4: UI bauen** (nach Entwurf A):
  - `DriverPicker`: zwei Chips mit `DropdownMenu`; Gegner-Menü = „Meine Bestzeit“ + alle Fahrer außer dem aktiven; Eintrag „+ Neuer Fahrer“ öffnet ein `OutlinedTextField` + „Anlegen“, deaktiviert solange `validateDriverName(...) == null`. Daneben „Fahrerwechsel beendet die laufende Sitzung“.
  - `LiveMap`: `Canvas` mit Linie = `reference.samples` (sonst die laufende Runde aus `PedalTrace`-ähnlichem Puffer der `frame.x/z`, max. 2000 Punkte), eigener Punkt weiß, Geist in `primary` (`positionAtTime(reference.samples, state.lapTimeMs)`), Legende. `MapProjection` per `remember(line, size)`.
  - Gegnerbox: `gapTitle`, Delta groß (grün < 0), Trend aus `gapTrend(deltaNow, deltaVor1s)` (Delta-Wert vor 1 s per `remember`-Ringpuffer), Zeile „‹Name›s Bestrunde · gleiches Auto (‹Auto›) · ‹Datum›“ bzw. „anderes Auto: ‹Auto›“; ohne Referenz „Noch keine Runde von ‹Name› auf dieser Strecke“.
  - Layout: Hauptspalte `weight(0.7f)`, Seitenleiste `weight(0.3f)`; Schriftgrößen der Hauptspalte × 0.88.
  - Zeiten-Band: Label „BESTE“/„DELTA“ aus `opponentLabels`, Wert BESTE = `reference.timeMs` falls vorhanden, sonst wie bisher.
- [ ] **Step 5: Sichtprüfung:** temporäres Render-Setup wie in früheren Tasks (desktopTest-Abhängigkeit `compose.desktop.currentOs`, `ImageComposeScene`, nicht committen) für 1100×672 und 1512×934 mit Mock-Referenz und Gegner „Anna“; Bilder ansehen: nichts abgeschnitten, Geist auf der Linie. Setup danach entfernen.
- [ ] **Step 6:** Suite grün, `:desktopApp:compileKotlin` grün. **Commit** `feat(ui): live track map with ghost and driver/opponent picker`

---

### Task 7: Bestenliste, Analyse-Filter, Fahrerverwaltung

**Files:**
- Create: `shared/src/commonMain/kotlin/de/stiller/gt7/ui/LeaderboardScreen.kt`
- Modify: `App.kt` (Tabs `Live`, `Analyse`, `Bestenliste`, `Einstellungen`), `AnalysisScreen.kt`, `AnalysisLists.kt`, `SettingsScreen.kt`
- Test: `shared/src/commonTest/kotlin/de/stiller/gt7/ui/LeaderboardRowsTest.kt` (neu)

**Interfaces:**
- Consumes: Task 2 (`leaderboard`, `carsOnTrack`, `SessionSummary.driverName`), Task 4 (Controller-Aktionen für Fahrer).
- Produces: `fun leaderboardRows(entries: List<LeaderboardEntry>): List<LeaderboardRow>` mit `data class LeaderboardRow(val place: Int, val name: String, val timeMs: Int, val gapMs: Int, val carCode: Int, val drivenAtMs: Long)` – `gapMs` zum Ersten, Platz 1-basiert, gleiche Zeit → gleicher Platz.

- [ ] **Step 1: Failing test** `LeaderboardRowsTest`: Zeiten 99 000 / 100 000 / 100 000 → Plätze `[1, 2, 2]`, `gapMs` `[0, 1000, 1000]`; leere Liste → leer.
- [ ] **Step 2:** → FAIL. **Step 3:** implementieren → PASS.
- [ ] **Step 4: UI**
  - `LeaderboardScreen(repo, cars)`: Streckenauswahl (`repo.tracks()`), Autoauswahl „Alle Autos“ + `carsOnTrack`; Tabelle Platz · Fahrer · Zeit · Rückstand (`formatDelta`) · Auto · Datum (`formatDateTime`). Ohne Strecken: „Noch keine Strecken gespeichert“.
  - Analyse: Filter-Chips „Alle Fahrer“ + je Fahrer über `SessionList`, Sitzungszeile zeigt `driverName`.
  - Einstellungen: Abschnitt „Fahrer“ – Liste, Umbenennen (Validierung mit `validateDriverName`, eigener Name zählt nicht als Dublette), „Löschen“ nur aktiv, wenn `deleteDriver` möglich ist (aus `controller.driversWithSessions` (Task 4)).
- [ ] **Step 5:** Suite + `:desktopApp:compileKotlin` grün; App kurz mit Replay oder PS5 starten (Startskript), Tabs durchklicken.
- [ ] **Step 6: Commit** `feat(ui): leaderboard tab, driver filter in analysis, driver settings`
