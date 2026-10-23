# Fahrdynamik-Analyse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GT7-Pakettyp B lesen, Lenkung/Gierrate/Beschleunigung/Schlupf pro Messpunkt speichern und daraus Trail-Braking-, Bremslöse-, Lenk- und Schlupf-Ursachen pro Kurve sowie neue Analyse-Diagramme ableiten.

**Architecture:** Der Decoder liest A (296 Byte) und B (316 Byte); die UDP-Quelle fordert B an und fällt bei Stille auf A zurück. `LapSample` bekommt sechs nullable Felder (Migration `5.sqm`). Ein neues reines Modul `session/Dynamics.kt` misst pro Kurvenzone `CornerDynamics`; `Engineer` vergleicht sie mit der Vergleichsrunde und erzeugt neue `Cause`s, die `EngineerText` in Tipps, Live-Banner und Sprache übersetzt. Die Analyse zeigt neue Diagramme und Kurvenwerte.

**Tech Stack:** Kotlin Multiplatform (commonMain, desktop, android), Compose Multiplatform 1.12, SQLDelight (SQLite), kotlin.test; Tests mit `./gradlew :shared:desktopTest`.

**Spec:** `docs/superpowers/specs/2026-10-23-fahrdynamik-analyse-design.md`

## Global Constraints

- Paket B: 316 Byte, IV-Maske `0xDEADBEEF`; Paket A: 296 Byte, Maske `0xDEADBEAF`. Lenkradwinkel 0x128 (rad), Sway 0x130, Heave 0x134, Surge 0x138 (m/s²). Gierrate = Winkelgeschwindigkeit y bei 0x30 (rad/s).
- Vorzeichen: links positiv für Lenkung und Gierrate (nach PS5-Mitschnitt in Task 8 bestätigen, im Decoder angleichen).
- Rückfall: fünf Sekunden ohne Paket nach Heartbeat „B“ → Heartbeat „A“ (und umgekehrt), bis Pakete kommen.
- Neue Messwerte sind nullable; nicht endliche Werte (NaN, ±∞) werden zu null.
- Schwellen (vorläufig, Konstanten in `Dynamics.kt`): Bremse gelöst < 5 %, Auslaufen ab 80 % Spitzendruck, Einlenken > 15 % des größten Lenkwinkels, Gas an > 80 % (`THROTTLE_ON`), Gegenlenken |Lenkung| > 2° und |Gierrate| > 0,2 rad/s für ≥ 200 ms, Untersteuern/Gierüberschuss > 25 % über Vergleich, Grip ≥ 0,1 g unter Vergleich, Lenkung beim Gasgeben ± 20 Prozentpunkte, Lösen ± 10 m, abrupt < ½ Vergleich und ≥ 150 ms kürzer, Blockieren Schlupf vorne < −15 %, Durchdrehen Schlupf hinten > 15 % bei Gas > 50 %.
- Ursachen nur bei Zeitverlust (`lossMs > 0`), wie bestehend. Reihenfolge: Bremsen (`BrakeEarly`, `BrakeLate`, `ReleaseEarly`, `ReleaseLate`, `ReleaseAbrupt`, `LockUp`), Scheitel (`SlowApex`, `OverspeedApex`, `Understeer`, `GripUnused`), Ausgang (`LateThrottle` oder `ThrottleLate`, `ThrottleOnLock`, `Oversteer`, `Wheelspin`), `GearDiffers`.
- Texte exakt wie in der Spec-Tabelle „Neue Ursachen“ (DE), Englisch sinngemäß im Stil der bestehenden `EngineerText`-Sätze.
- Commits mit der noreply-Adresse (lokale git config), Abschluss `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

- **Pakete fremder Länge** (z. B. „~“ mit 344 Byte, abgeschnittene Datagramme): `decode` liefert null, kein Absturz. → Test in Task 1.
- **NaN/∞ in Sway/Surge/Lenkung**: werden null, gelangen weder in die Datenbank noch in Diagramm-Skalen. → Test in Task 1.
- **Gierrate nahe null** (Geradeaus, Stillstand): Lenkgrad und Gierüberschuss nutzen nur Messpunkte mit |Gierrate| > 0,05 rad/s und |Lenkung| > 2°, sonst null – keine Division durch ~0. → Test in Task 4.
- **Runde mit B-Daten gegen Vergleichsrunde ohne** (alte Bestzeit als Referenz): vergleichende B-Ursachen entfallen, absolute (`LockUp`, `Wheelspin`, Gegenlenken) bleiben, Bremslösen/Auslaufen werden verglichen. → Test in Task 5.
- **Import einer Exportdatei ohne neue Spalten** und Wiedergabe alter A-Mitschnitte: funktionieren unverändert. → Tests in Task 3 und Task 1.

---

### Task 1: Paket B dekodieren

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/telemetry/PacketDecoder.kt`
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/telemetry/TelemetryFrame.kt`
- Modify: `shared/src/commonTest/kotlin/de/bavaco/gt7/telemetry/TestPackets.kt`
- Test: `shared/src/commonTest/kotlin/de/bavaco/gt7/telemetry/PacketDecoderTest.kt`

**Interfaces:**
- Produces: `PacketDecoder.PACKET_SIZE = 296`, `PacketDecoder.PACKET_SIZE_B = 316`; `TelemetryFrame` neue Felder (am Ende, mit Default null): `steeringRad: Float?`, `yawRate: Float?`, `latAccel: Float?`, `longAccel: Float?`. Test-Helfer `plainPacket(size: Int = PacketDecoder.PACKET_SIZE, block)` und `encryptForTest(plain, iv1, mask: Int = 0xDEADBEAF.toInt())`.

- [ ] **Step 1: Failing tests** in `PacketDecoderTest`:
  - `decodesPacketB`: B-Paket (316 Byte, Maske `0xDEADBEEF`) mit `float(0x128, 0.5f)`, `float(0x130, 9.81f)`, `float(0x138, -4f)`, `float(0x30, 0.7f)` → `steeringRad == 0.5f`, `latAccel == 9.81f`, `longAccel == -4f`, `yawRate == 0.7f`, A-Felder (z. B. Tempo) wie gesetzt.
  - `packetAHasYawButNoSteering`: A-Paket mit `float(0x30, 0.7f)` → `yawRate == 0.7f`, `steeringRad == null`, `latAccel == null`.
  - `nonFiniteDynamicsBecomeNull`: B-Paket mit `Float.NaN` bei 0x128 und `Float.POSITIVE_INFINITY` bei 0x130 → beide null.
  - `rejectsOtherLengths`: 344 und 300 Byte → `decode` == null.
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*PacketDecoderTest'` → FAIL (Felder fehlen).
- [ ] **Step 3:** Decoder: Maske nach Länge (296 → `0xDEADBEAF`, 316 → `0xDEADBEEF`, sonst null). `yawRate = f32(p, 0x30)` immer; B-Felder nur bei 316 Byte. Hilfsfunktion `finite(v: Float): Float? = v.takeIf { it.isFinite() }` für alle vier neuen Werte.
- [ ] **Step 4:** Test-Kommando wieder → PASS; komplette Suite `./gradlew :shared:desktopTest` grün (Mitschnitt-/Replay-Tests mit A-Paketen unverändert).
- [ ] **Step 5:** Commit `feat(telemetry): decode GT7 packet type B with steering, yaw rate and accelerations`.

### Task 2: Heartbeat „B“ mit Rückfall auf „A“

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/telemetry/UdpTelemetrySource.kt`
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/telemetry/Discovery.kt`
- Test: `shared/src/desktopTest/kotlin/de/bavaco/gt7/telemetry/UdpTelemetrySourceTest.kt`

**Interfaces:**
- Produces: `internal fun heartbeatPacket(type: Char = 'B'): Source`; `internal fun nextHeartbeat(current: Char, silentMs: Long): Char` – bei `silentMs >= 5_000` wechselt `'B'`↔`'A'`, sonst `current`. `UdpTelemetrySource(…, silenceSwitchMs: Long = 5_000)`.

- [ ] **Step 1: Failing tests:**
  - `nextHeartbeatTogglesOnlyAfterSilence`: `nextHeartbeat('B', 4_999) == 'B'`, `nextHeartbeat('B', 5_000) == 'A'`, `nextHeartbeat('A', 5_000) == 'B'`.
  - `sendsHeartbeatB` (anpassen von `sendsHeartbeatAndEmitsReply`): erstes empfangenes Heartbeat-Byte ist `'B'`.
  - `fallsBackToAWhenSilent`: Quelle mit `silenceSwitchMs = 200`, Gegenstelle antwortet nicht → innerhalb von 1 s kommt ein Heartbeat `'A'`.
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*UdpTelemetrySourceTest'` → FAIL.
- [ ] **Step 3:** Heartbeat-Schleife prüft jede Sekunde (bzw. `min(1_000, silenceSwitchMs)`): Zeit seit letztem Datagramm (oder Start) ≥ Schwelle → Typ mit `nextHeartbeat` wechseln, sofort neuen Heartbeat senden, Wechsel per `println("[GT7] no packets, heartbeat now $type")` loggen. Reguläres Senden alle `heartbeatIntervalMs` bleibt. `Discovery` sendet `heartbeatPacket('B')` und akzeptiert jedes Paket, das `PacketDecoder.decode` annimmt.
- [ ] **Step 4:** Tests → PASS, ganze Suite grün.
- [ ] **Step 5:** Commit `feat(telemetry): request packet type B, fall back to A when GT7 stays silent`.

### Task 3: Neue Messwerte speichern

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/session/LapModels.kt` (LapSample)
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/session/SessionEngine.kt` (`sample`)
- Create: `shared/src/commonMain/sqldelight/de/bavaco/gt7/db/5.sqm`
- Modify: `shared/src/commonMain/sqldelight/de/bavaco/gt7/db/Gt7.sq` (Tabelle, `insertSample`, `samplesForLap`)
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/storage/SessionRepository.kt` (`saveLap`, `samples`)
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/storage/LapExport.kt` (CSV/JSON-Spalten)
- Test: `shared/src/desktopTest/kotlin/de/bavaco/gt7/storage/MigrationTest.kt`, `SessionRepositoryTest.kt`, `DataTransferTest.kt`; `shared/src/commonTest/kotlin/de/bavaco/gt7/session/SessionEngineTest.kt`; `shared/src/commonTest/kotlin/de/bavaco/gt7/storage/LapExportTest.kt`

**Interfaces:**
- Consumes: `TelemetryFrame.steeringRad/yawRate/latAccel/longAccel` (Task 1), `wheelSlip` (LapMath).
- Produces: `LapSample` neue Felder am Ende, alle `Float? = null`: `steeringRad`, `yawRate`, `latAccel`, `longAccel`, `slipFront`, `slipRear`. Spalten `steering_rad`, `yaw_rate`, `lat_accel`, `long_accel`, `slip_front`, `slip_rear` (REAL, nullable).

- [ ] **Step 1: Failing tests:**
  - `SessionEngineTest.samplesCarryDynamics`: Frames mit `steeringRad = 0.3f`, `yawRate = 0.5f`, `latAccel = 12f`, `longAccel = -3f`, Raddrehzahlen so, dass vorne 0 % und hinten 20 % Schlupf → gespeicherte Messpunkte tragen diese Werte, `slipFront ≈ 0f`, `slipRear ≈ 0.2f` (Toleranz 0.01). `FrameFactory.frame` bekommt die vier neuen Parameter.
  - `SessionEngineTest.slipNullBelowSlipSpeed`: Tempo 2 m/s → `slipFront == null`.
  - `SessionRepositoryTest.savesAndLoadsDynamics`: Runde mit gesetzten und mit null-Werten speichern und lesen → gleich.
  - `MigrationTest.migratesV5AddingDynamicsColumns`: Datenbank mit `user_version = 5` und einem alten Messpunkt (ohne neue Spalten) öffnen → Messpunkt geladen, neue Felder null.
  - `DataTransferTest.importsExportWithoutDynamicsColumns`: Exportdatei vor Migration 5 importieren → Runden und Messpunkte da, neue Felder null.
  - `LapExportTest`: CSV-Kopf endet mit `steering_deg,yaw_deg_s,lat_g,long_g,slip_front_pct,slip_rear_pct`; leere Werte als leeres Feld.
- [ ] **Step 2:** `./gradlew :shared:desktopTest` → neue Tests FAIL.
- [ ] **Step 3:** Implementieren. `5.sqm`: sechs `ALTER TABLE lap_sample ADD COLUMN … REAL;`. `Gt7.sq`: Spalten in `CREATE TABLE` mit Kommentar „added by migration 5“, `insertSample`/`samplesForLap` erweitern. `SessionEngine.sample`: Achsschlupf = Mittel der beiden `wheelSlip`-Werte der Achse, null wenn einer null ist. Export: Grad = rad × 180/π, g = m/s² / 9,81, Schlupf in %. Bestehende Aufrufer von `LapSample(...)` bleiben dank Defaults unverändert.
- [ ] **Step 4:** Ganze Suite grün; `./gradlew :androidApp:compileDebugKotlin` kompiliert.
- [ ] **Step 5:** Commit `feat(storage): store steering, yaw rate, accelerations and axle slip per sample`.

### Task 4: Kennzahlen pro Kurve (`Dynamics`)

**Files:**
- Create: `shared/src/commonMain/kotlin/de/bavaco/gt7/session/Dynamics.kt`
- Test: `shared/src/commonTest/kotlin/de/bavaco/gt7/session/DynamicsTest.kt`

**Interfaces:**
- Consumes: `LapSample` (Task 3), `Corner` (Engineer.kt).
- Produces:
  ```kotlin
  data class CornerDynamics(
      val releaseM: Float?,          // Bremse < 5 % gelöst, letzte Bremsphase vor dem Scheitel
      val releaseMs: Int?,           // 80 % Spitzendruck bis gelöst
      val trailM: Float?,            // Bremsstrecke > 5 % nach Einlenkpunkt (ohne Lenkung: in den letzten 30 % vor dem Scheitel)
      val steerAtThrottlePct: Int?,  // |Lenkung| beim Gas > 80 % in % des Kurvenmaximums
      val steerPerYaw: Float?,       // Mittel |Lenkung|/|Gierrate|, Einlenkpunkt..Scheitel
      val countersteerMs: Int?,      // längste Gegenlenk-Phase; null ohne Lenkdaten
      val yawPerSteerExit: Float?,   // Mittel |Gierrate|/|Lenkung|, Scheitel..Ende
      val apexLatG: Float?,          // |latAccel| am Scheitel / 9,81
      val minFrontSlip: Float?,      // unter Bremse > 5 %
      val maxRearSlip: Float?,       // bei Gas > 50 %
  ) { companion object { val NONE: CornerDynamics } }

  object Dynamics {
      fun measure(samples: List<LapSample>, corner: Corner): CornerDynamics
  }
  ```
  Alle Schwellen aus „Global Constraints“ als `private const val` in dieser Datei.

- [ ] **Step 1: Failing tests** – je eine konstruierte Kurvenzone (Helfer `zone(...)` im Test baut Messpunkte im 10-m-Raster):
  - `releasePointAndReleaseTime`: Bremse 100 % bis 40 m vor Scheitel, dann linear auf 0 über 0,4 s → `releaseM` = Stelle des Lösens, `releaseMs` ≈ 400 (± 100).
  - `withoutDynamicsOnlyBrakeMetrics`: Messpunkte ohne B-Felder → `releaseM`/`releaseMs`/`trailM` gesetzt, alle anderen null.
  - `trailBrakingAfterTurnIn`: Lenkung setzt 30 m vor Scheitel ein, Bremse bis 10 m vor Scheitel → `trailM` ≈ 20.
  - `steeringAtThrottle`: Max-Lenkung 1,0 rad, beim Gas-Einsatz 0,7 rad → `steerAtThrottlePct == 70`.
  - `countersteerDetected`: 300 ms Lenkung −0,2 rad bei Gierrate +0,5 rad/s → `countersteerMs` ≥ 200; ohne → 0.
  - `yawNearZeroIgnored`: Gierrate 0,01 rad/s überall → `steerPerYaw == null`, `yawPerSteerExit == null`.
  - `lockUpAndWheelspin`: Schlupf vorne −0,2 unter Bremse → `minFrontSlip == -0.2f`; Schlupf hinten 0,25 bei Gas 0,9 → `maxRearSlip == 0.25f`.
  - `apexLateralG`: `latAccel = 14.715f` am Scheitel → `apexLatG ≈ 1.5f`.
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*DynamicsTest'` → FAIL.
- [ ] **Step 3:** `Dynamics.measure` implementieren; Zone = Messpunkte in `corner.startM..corner.endM`, Scheitel = langsamster Punkt (wie `Engineer.measure`). Gegenlenken nur zählen, wenn beide Werte vorhanden und die Schwellen erfüllt sind; Dauer aus `timeMs`-Differenzen.
- [ ] **Step 4:** Tests → PASS.
- [ ] **Step 5:** Commit `feat(engineer): measure braking release, trail braking, steering and slip per corner`.

### Task 5: Neue Ursachen im Renningenieur

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/session/Engineer.kt`
- Test: `shared/src/commonTest/kotlin/de/bavaco/gt7/session/EngineerTest.kt`

**Interfaces:**
- Consumes: `Dynamics.measure`, `CornerDynamics` (Task 4).
- Produces: `CornerReport` neue Felder am Ende `val dynamics: CornerDynamics = CornerDynamics.NONE`, `val refDynamics: CornerDynamics = CornerDynamics.NONE`. Neue `Cause`s:
  ```kotlin
  data class ReleaseEarly(val meters: Int) : Cause
  data class ReleaseLate(val meters: Int) : Cause
  data class ReleaseAbrupt(val ms: Int, val refMs: Int) : Cause
  data object LockUp : Cause
  data object Understeer : Cause
  data class GripUnused(val g: Float, val refG: Float) : Cause
  data class ThrottleLate(val pct: Int, val refPct: Int) : Cause
  data class ThrottleOnLock(val pct: Int, val refPct: Int) : Cause
  data class Oversteer(val countersteer: Boolean) : Cause
  data object Wheelspin : Cause
  ```

- [ ] **Step 1: Failing tests** (Runde/Vergleich aus zwei konstruierten Runden mit derselben Kurve, Runde verliert Zeit):
  - `releaseEarlyAndAbrupt`: Runde löst 15 m früher und in 0,1 s, Vergleich in 0,4 s → Ursachen enthalten `ReleaseEarly(15)` und `ReleaseAbrupt(100, 400)` (± 1 Messraster) in dieser Reihenfolge, beide nach `BrakeEarly`/`BrakeLate`, vor `SlowApex`.
  - `noNewCausesWithoutLoss`: gleiche Fehler, aber Runde schneller → keine Ursachen.
  - `throttleLateReplacesLateThrottle`: Gas 20 m später und bei 20 % statt 50 % Lenkung → `ThrottleLate(20, 50)` vorhanden, `LateThrottle` nicht.
  - `throttleOnLock`: 70 % statt 40 % → `ThrottleOnLock(70, 40)`.
  - `understeerAndOversteer`: Lenkgrad 1,4× Vergleich → `Understeer`; 300 ms Gegenlenken → `Oversteer(true)`.
  - `referenceWithoutDynamics`: Runde mit B-Daten, Vergleich ohne; Runde blockiert vorne und lenkt gegen, Lösen 15 m früher → `ReleaseEarly`, `LockUp`, `Oversteer(true)` vorhanden; `Understeer`, `GripUnused`, `ThrottleOnLock`, `ThrottleLate` nicht.
  - `causeOrderFollowsCornerPhase`: Runde mit `BrakeEarly`, `Wheelspin`, `GripUnused`, `ReleaseAbrupt` → Reihenfolge `BrakeEarly, ReleaseAbrupt, GripUnused, Wheelspin`.
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*EngineerTest'` → FAIL.
- [ ] **Step 3:** `Measures` um `CornerDynamics` erweitern (`Dynamics.measure` in `measure`), `causes` gemäß Spec-Tabelle und Reihenfolge; vergleichende Ursachen nur, wenn beide Werte nicht null sind. `ReleaseLate` nur, wenn zusätzlich `own.apexKmh < ref.apexKmh`. Bestehende Tests unverändert grün.
- [ ] **Step 4:** Tests → PASS, Suite grün.
- [ ] **Step 5:** Commit `feat(engineer): causes for braking release, steering, under-/oversteer, lock-up and wheelspin`.

### Task 6: Texte für Tipps, Live und Sprache

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/EngineerText.kt`
- Test: `shared/src/commonTest/kotlin/de/bavaco/gt7/ui/EngineerTextTest.kt`

**Interfaces:**
- Consumes: neue `Cause`s (Task 5).
- Produces: `EngineerText.cause` für alle neuen Ursachen; `spoken` schreibt zusätzlich aus: `<Zahl> g` → `<Zahl> G` (DE) bzw. unverändert (EN), `<Zahl> %` → `<Zahl> Prozent`/`<Zahl> percent`, `<Zahl> s` → `<Zahl> Sekunden`/`<Zahl> seconds`.

- [ ] **Step 1: Failing tests:** `everyNewCauseInGermanAndEnglish` mit den exakten DE-Texten der Spec-Tabelle, eingebettet wie bestehend (`"Kurve 4: <Ursache> – kostet 0,21 s"`), z. B.
  - `ReleaseEarly(12)` → `"Kurve 4: Bremse 12 m früher gelöst – länger mit in die Kurve nehmen – kostet 0,21 s"`
  - `ReleaseAbrupt(100, 400)` → `"… Bremse abrupt gelöst (0,1 s statt 0,4 s) – weicher auslaufen lassen – kostet 0,21 s"`
  - `GripUnused(1.3f, 1.5f)` → `"… 1,3 g statt 1,5 g am Scheitel – Grip nicht ausgenutzt – kostet 0,21 s"`
  - `ThrottleOnLock(70, 40)` → `"… Vollgas bei 70 % Lenkung (Vergleich 40 %) – erst Lenkung öffnen – kostet 0,21 s"`
  - `Oversteer(true)` → `"… Übersteuern am Ausgang (gegengelenkt) – Gas sanfter …"`, `Oversteer(false)` ohne Klammer.
  - restliche gemäß Tabelle; Englisch je ein Satz im bestehenden Stil.
  - `spokenWritesOutNewUnits`: `spoken` für `ReleaseAbrupt(100, 400)` enthält „0,1 Sekunden statt 0,4 Sekunden“, für `ThrottleOnLock` „70 Prozent“.
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*EngineerTextTest'` → FAIL.
- [ ] **Step 3:** Texte ergänzen; Sekunden mit einer Nachkommastelle über vorhandenes `tenths`.
- [ ] **Step 4:** Tests → PASS.
- [ ] **Step 5:** Commit `feat(engineer): German and English texts for the new causes`.

### Task 7: Analyse – Diagramme und Kurventabelle

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/CockpitAnalysis.kt` (`Charts`)
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/CornerList.kt`
- Create: `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/DynamicsLogic.kt` (reine Hilfen für Serien und Detailtext)
- Test: `shared/src/commonTest/kotlin/de/bavaco/gt7/ui/DynamicsLogicTest.kt`

**Interfaces:**
- Consumes: `LapSample`-Felder (Task 3), `CornerReport.dynamics/refDynamics`, `Cause.Understeer/Oversteer` (Task 5).
- Produces: `fun hasDynamics(samples: List<LapSample>): Boolean` (irgendein `steeringRad != null`); `fun dynamicsDetail(r: CornerReport): String`; `fun handlingMark(r: CornerReport): String?` → `"US"`, `"ÜS"` oder null (bei beiden: `"US/ÜS"`).

- [ ] **Step 1: Failing tests** in `DynamicsLogicTest`:
  - `detailLine`: Bericht mit `releaseM` −12 m gegenüber Vergleich, `releaseMs` 100/400, Lenkung beim Gas 70/40 → `"Lösen −12 m · Auslaufen 0,1 / 0,4 s · Gas bei 70 / 40 % Lenkung"`.
  - `detailLineMissingValues`: ohne Dynamikwerte → `"Lösen – · Auslaufen – / – s · Gas bei – / – % Lenkung"`.
  - `handlingMarks`: `Understeer` → `"US"`, `Oversteer(true)` → `"ÜS"`, beide → `"US/ÜS"`, keine → null.
  - `hasDynamicsFalseForOldLaps`.
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*DynamicsLogicTest'` → FAIL.
- [ ] **Step 3:** UI:
  - `Charts`: nach „Gas / Bremse (%)“ immer `chart("Lenkung (°)", MoreChartHeight)` mit Lenkung in Grad; zweiter aufklappbarer Bereich „Fahrdynamik“ (eigener Zustand wie `more`, Schalter direkt unter „Weitere Kanäle“) mit „Gierrate (°/s)“, „Querbeschleunigung (g)“, „Schlupf (%)“ (vorne durchgezogen, hinten `dashed = true`, A `RaceColors.LapA`, B `RaceColors.LapB`). Serien nur aus Messpunkten mit Wert. Hat weder A noch B Daten (`hasDynamics`), zeigt das Diagramm statt Linien den Text „Keine Lenk- und Dynamikdaten – Runde vor Version 0.6 aufgezeichnet“.
  - `CornerList`: Detailzeile um `dynamicsDetail(r)` ergänzen (neue Zeile unter der bestehenden), `handlingMark` als kleines Kürzel in `RaceColors.Warn` rechts neben der Kurvennummer in der eingeklappten Zeile.
- [ ] **Step 4:** Tests → PASS; `SCREENSHOT_OUT=<scratch> SCREENSHOT_DB=<db> ./gradlew :shared:desktopTest --tests '*ReadmeScreenshots' --rerun` und `analysis.png` ansehen: Diagramm „Lenkung“ mit Hinweistext (alte Runden), Detailzeile lesbar, nichts abgeschnitten.
- [ ] **Step 5:** Commit `feat(analysis): steering and dynamics charts, release and steering values per corner`.

### Task 8: Abgleich an der PS5

**Files:**
- Modify (falls nötig): `PacketDecoder.kt` (Vorzeichen), `Dynamics.kt` (Schwellen), `README.md`, `docs/screenshots/analysis.png`

- [ ] **Step 1:** App starten, in den Einstellungen „Mitschnitt“ starten, an der PS5 2–3 Runden fahren, dabei bewusst: eine Linkskurve, eine Rechtskurve, einmal Gegenlenken, einmal Durchdrehen. Mitschnitt beenden.
- [ ] **Step 2:** Mitschnitt mit einem Testskript (scratch, nicht im Repo) dekodieren: Länge 316, Lenkung in Linkskurve positiv, Gierrate in Linkskurve positiv, Sway in Kurven ~10–20 m/s². Abweichende Vorzeichen im Decoder umdrehen, Test in `PacketDecoderTest` entsprechend ergänzen.
- [ ] **Step 3:** Mitschnitt abspielen, Analyse der Runden ansehen: werden die bewusst gefahrenen Fehler erkannt, keine offensichtlichen Fehlalarme? Schwellen in `Dynamics.kt` anpassen, Begründung als Kommentar an der Konstante.
- [ ] **Step 4:** Ganze Suite grün; README-Abschnitt Analyse um Fahrdynamik ergänzen; Screenshots neu erzeugen (Namen prüfen: nur „Max“/„Fahrer N“).
- [ ] **Step 5:** Commit `feat(analysis): calibrate driving dynamics on real GT7 data`.
