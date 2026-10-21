# GT7 Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compose-Multiplatform-App (zuerst macOS), die GT7-Telemetrie von der PS5 empfängt, live anzeigt, Runden in SQLite speichert und Runden vergleicht.

**Architecture:** Ein KMP-Modul `shared` (Pakete `telemetry`, `session`, `storage`, `ui`) mit nahezu allem Code in `commonMain`, plus dünnes `desktopApp`-Modul. Datenfluss: `TelemetrySource` → `PacketDecoder` → `SessionEngine` (reine Zustandsmaschine) → `SessionController` (verdrahtet Engine, Repository, TrackMatcher) → Compose-Screens.

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.0, Ktor Network 3.4.2, kotlinx-coroutines 1.11.0, kotlinx-io 0.8.0, SQLDelight 2.1.0, Gradle 9.2.0, JDK 17.

**Spec:** `docs/superpowers/specs/2026-10-15-gt7-dashboard-design.md`

## Global Constraints

- Alle Logik und UI in `shared/src/commonMain`; `desktopMain` enthält nur SQLDelight-Treiber + App-Datenverzeichnis (`expect/actual`).
- Paketname-Basis: `de.bavaco.gt7`.
- UDP: Heartbeat `"A"` an PS5-Port **33739**, Empfang auf **33740**, Heartbeat sofort und alle **10 s**.
- Salsa20-Key: erste 32 Bytes von `"Simulator Interface Packet GT7 ver 0.0"`; IV1 = u32 LE an `0x40` des *verschlüsselten* Pakets; IV2 = IV1 xor `0xDEADBEAF`; Nonce = IV2 (LE) ‖ IV1 (LE).
- Gültiges Paket: Länge **296**, Magic `0x47375330` (u32 LE an Offset 0 nach Entschlüsselung).
- Sitzungs-Timeout **30 s** ohne Pakete; Runde ungültig bei Pause/Paket-ID rückwärts oder |gemessen − GT7-Zeit| > **2000 ms**.
- Track-Match: **200** Punkte, mittlerer Abstand < **15 m**. Schlupf-Alarm ab |Schlupf| ≥ **0.15**, nicht unter **10 km/h**. Benzinprognose aus den letzten bis zu **3** gültigen Runden.
- Gespeicherte Samples: **10 Hz** (≥ 100 ms Abstand). DB: `~/Library/Application Support/GT7Dashboard/gt7.db`.
- Diagramme mit Compose `Canvas`, keine Chart-Library. UI-Texte auf Deutsch.
- Wheel-Reihenfolge überall: FL, FR, RL, RR.

## Review Focus

1. **Discovery per Broadcast antwortet nicht** (PS5 reagiert evtl. nicht auf Broadcast-Heartbeat, oder macOS-Firewall blockt) → Discovery liefert nach Timeout `null`, UI fällt sichtbar auf manuelle IP zurück, nichts hängt. Test in Task 5 (`discoveryReturnsNullOnTimeout`).
2. **App startet mitten in einer Runde** → angebrochene erste Runde wird *nicht* gespeichert (nur Runden, deren Start-Inkrement beobachtet wurde). Test in Task 7 (`partialFirstLapIsNotRecorded`).
3. **GT7 aktualisiert `lastLapTimeMs` erst einige Frames nach dem Lap-Inkrement** → Runde wird mit der echten GT7-Zeit gespeichert, nicht mit dem alten Wert. Test in Task 7 (`lapTimeTakenFromDelayedLastLapField`).
4. **Paketlücke (WLAN-Aussetzer, > 0.5 s)** → Distanz-Integration springt nicht; Δt wird auf 0.1 s gedeckelt. Test in Task 7 (`gapDoesNotInflateDistance`).
5. **Erste Runde auf unbekannter Strecke, kein Referenzlauf** → Live-Delta ist `null` (UI zeigt „–“), kein Absturz; nach der ersten gültigen Runde wird sie zur Referenz. Test in Task 10 (`firstValidLapBecomesReference`).

---

## Dateistruktur

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew
shared/build.gradle.kts
shared/src/commonMain/kotlin/de/bavaco/gt7/
  telemetry/Salsa20.kt            Salsa20/20 Keystream + xor
  telemetry/TelemetryFrame.kt     data classes TelemetryFrame, Wheels, Flags
  telemetry/PacketDecoder.kt      decode(ByteArray): TelemetryFrame?
  telemetry/TelemetrySource.kt    interface + RawPacket
  telemetry/UdpTelemetrySource.kt Heartbeat + Empfang (Ktor)
  telemetry/Discovery.kt          discoverPs5()
  telemetry/PacketRecording.kt    Recorder-Format schreiben/lesen
  telemetry/ReplayTelemetrySource.kt
  telemetry/TelemetryPipeline.kt  Source → Frames + PacketStats
  session/LapModels.kt            LapSample, CompletedLap, SessionEvent, LiveState
  session/LapMath.kt              Delta, Benzin, Schlupf, Delta-Kurve
  session/SessionEngine.kt        Zustandsmaschine
  session/TrackMatcher.kt         Outline + Matching
  session/SessionController.kt    Verdrahtung, Track-Fragen, Referenzrunde
  storage/SessionRepository.kt
  storage/CarCatalog.kt
  storage/Platform.kt             expect fun createDriver(); expect fun appDataDir()
  ui/App.kt, ui/Theme.kt, ui/LiveScreen.kt, ui/AnalysisScreen.kt, ui/Charts.kt,
  ui/TrackMap.kt, ui/SettingsScreen.kt, ui/TrackDialog.kt, ui/Format.kt
shared/src/commonMain/sqldelight/de/bavaco/gt7/db/Gt7.sq
shared/src/commonMain/composeResources/files/cars.csv
shared/src/desktopMain/kotlin/de/bavaco/gt7/storage/Platform.desktop.kt
shared/src/commonTest/kotlin/de/bavaco/gt7/...   (Tests je Paket)
shared/src/desktopTest/kotlin/de/bavaco/gt7/...  (UDP- und DB-Tests, JVM)
desktopApp/build.gradle.kts
desktopApp/src/main/kotlin/de/bavaco/gt7/desktop/Main.kt
```

---

### Task 1: Gradle-Gerüst + leeres Fenster

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, Gradle-Wrapper
- Create: `shared/build.gradle.kts`, `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/App.kt`, `shared/src/commonTest/kotlin/de/bavaco/gt7/SmokeTest.kt`
- Create: `desktopApp/build.gradle.kts`, `desktopApp/src/main/kotlin/de/bavaco/gt7/desktop/Main.kt`

**Interfaces:**
- Produces: `@Composable fun App()` in `de.bavaco.gt7.ui`; Gradle-Tasks `:shared:desktopTest`, `:desktopApp:run`.

- [ ] **Step 1: Wrapper erzeugen:** `gradle wrapper --gradle-version 9.2.0`
- [ ] **Step 2: Version Catalog** mit den Versionen aus *Tech Stack*; Plugins: `kotlin-multiplatform`, `kotlin-jvm`, `compose-compiler` (`org.jetbrains.kotlin.plugin.compose`, gleiche Kotlin-Version), `compose-multiplatform` (`org.jetbrains.compose`), `sqldelight` (`app.cash.sqldelight`). Libraries: `ktor-network`, `kotlinx-coroutines-core`, `kotlinx-coroutines-test`, `kotlinx-coroutines-swing`, `kotlinx-io-core`, `sqldelight-sqlite-driver`, `sqldelight-coroutines-extensions`.
- [ ] **Step 3: `shared`** – KMP mit Target `jvm("desktop")`, Compose über Plugin-Accessors (`compose.runtime`, `compose.foundation`, `compose.material3`, `compose.components.resources`), SQLDelight-DB `Gt7Database`, Paket `de.bavaco.gt7.db`. `desktopApp` – `kotlin("jvm")` + Compose, `compose.desktop { application { mainClass = "de.bavaco.gt7.desktop.MainKt"; nativeDistributions { targetFormats(Dmg); packageName = "GT7 Dashboard"; packageVersion = "1.0.0" } } }`.
- [ ] **Step 4: Smoke-Test** `SmokeTest.runs()` mit `assertEquals(2, 1 + 1)`; `App()` zeigt `Text("GT7 Dashboard")`; `Main.kt` öffnet `Window(title = "GT7 Dashboard")` mit `App()`.
- [ ] **Step 5: Verifizieren:** `./gradlew :shared:desktopTest :desktopApp:compileKotlin` → BUILD SUCCESSFUL. Wenn eine Version nicht auflösbar ist: nächsthöhere verfügbare Patch-Version derselben Linie nehmen und im Catalog dokumentieren. `./gradlew :desktopApp:run` öffnet ein Fenster (manuell prüfen, dann schließen).
- [ ] **Step 6: Commit** `chore: scaffold KMP project with desktop app`

---

### Task 2: Salsa20

**Files:**
- Create: `shared/src/commonMain/kotlin/de/bavaco/gt7/telemetry/Salsa20.kt`
- Test: `shared/src/commonTest/kotlin/de/bavaco/gt7/telemetry/Salsa20Test.kt`

**Interfaces:**
- Produces: `object Salsa20 { fun xor(key: ByteArray /*32*/, nonce: ByteArray /*8*/, data: ByteArray): ByteArray }` (Zähler startet bei 0, 20 Runden).

- [ ] **Step 1: Failing Test** `keystreamMatchesEcryptSet1Vector0`: Key = `0x80` + 31×`0x00`, Nonce = 8×`0x00`, `xor(key, nonce, ByteArray(64)).toHex()` == `"e3be8fdd8beca2e3ea8ef9475b29a6e7003951e1097a5c38d23b7a5fad9f6844b22c97559e2723c7cbbd3fe4fc8d9a0744652a83e72a9c461876af4d7ef1a117"`. Zweiter Test `xorIsInvolution`: 150 zufällige Bytes, `xor(xor(d)) contentEquals d` (prüft auch Block-Übergang > 64 Bytes).
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*Salsa20Test*'` → FAIL (unresolved reference).
- [ ] **Step 3: Implementieren** – Standard-Salsa20/20 (Konstanten `"expand 32-byte k"`, Zustandslayout nach Bernstein, Wörter little-endian).
- [ ] **Step 4:** Tests → PASS.
- [ ] **Step 5: Commit** `feat(telemetry): add Salsa20 implementation`

---

### Task 3: TelemetryFrame + PacketDecoder

**Files:**
- Create: `telemetry/TelemetryFrame.kt`, `telemetry/PacketDecoder.kt`
- Test: `shared/src/commonTest/kotlin/de/bavaco/gt7/telemetry/PacketDecoderTest.kt`, `.../telemetry/TestPackets.kt` (Test-Helfer)

**Interfaces:**
- Consumes: `Salsa20.xor`
- Produces:
  ```kotlin
  data class Wheels(val fl: Float, val fr: Float, val rl: Float, val rr: Float)
  data class TelemetryFrame(
      val packetId: Int, val x: Float, val y: Float, val z: Float,
      val speedMps: Float, val rpm: Float, val rpmAlertMin: Int, val rpmAlertMax: Int,
      val gear: Int, val suggestedGear: Int, val throttle: Float /*0..1*/, val brake: Float /*0..1*/,
      val boostBar: Float /*raw − 1*/, val fuelLevel: Float, val fuelCapacity: Float,
      val tireTempC: Wheels, val wheelRps: Wheels, val tireRadiusM: Wheels,
      val currentLap: Int, val totalLaps: Int, val bestLapMs: Int, val lastLapMs: Int,
      val racePosition: Int, val totalPositions: Int, val carCode: Int,
      val onTrack: Boolean, val paused: Boolean, val loading: Boolean, val hasTurbo: Boolean,
  )
  object PacketDecoder { const val PACKET_SIZE = 296; fun decode(raw: ByteArray): TelemetryFrame? }
  ```
  Test-Helfer: `fun encryptForTest(plain: ByteArray, iv1: Int): ByteArray` (verschlüsselt mit Nonce aus `iv1`, schreibt danach `iv1` LE an `0x40` des Chiffrats) und `fun plainPacket(block: PacketBuilder.() -> Unit): ByteArray` (296 Bytes, Magic gesetzt, Setter für Offsets).

- [ ] **Step 1: Failing Tests**
  - `decodesKnownFields`: plain mit speed 50.0 m/s, rpm 7000, gear-Byte `0x34` (gear 4, suggested 3), throttle-Byte 255, brake-Byte 0, boost raw 1.8, fuel 40/100, currentLap 3, totalLaps 10, lastLap 102381, carCode 3332, flags = bit0|bit4 → `speedMps == 50f`, `gear == 4`, `suggestedGear == 3`, `throttle == 1f`, `boostBar == 0.8f ± 1e-4`, `currentLap == 3`, `lastLapMs == 102381`, `carCode == 3332`, `onTrack && hasTurbo && !paused`.
  - `rejectsWrongLength`: 295 Bytes → `null`.
  - `rejectsWrongMagic`: plain mit Magic 0 → `null`.
- [ ] **Step 2:** Tests → FAIL.
- [ ] **Step 3: Implementieren** – Offsets (alle LE; Floats = f32, i16 = signed short):
  `0x04` x,y,z · `0x3C` rpm · `0x44` fuelLevel · `0x48` fuelCapacity · `0x4C` speed · `0x50` boost raw · `0x60` tireTemp ×4 · `0x70` packetId (i32) · `0x74` currentLap (i16) · `0x76` totalLaps (i16) · `0x78` bestLap (i32) · `0x7C` lastLap (i32) · `0x84` position (i16) · `0x86` totalPositions (i16) · `0x88` rpmAlertMin (i16) · `0x8A` rpmAlertMax (i16) · `0x8E` flags (i16: bit0 onTrack, bit1 paused, bit2 loading, bit4 hasTurbo) · `0x90` gear-Byte (low nibble gear, high nibble suggested) · `0x91` throttle (u8/255) · `0x92` brake (u8/255) · `0xA4` wheelRps ×4 · `0xB4` tireRadius ×4 · `0x124` carCode (i32).
- [ ] **Step 4:** Tests → PASS.
- [ ] **Step 5: Commit** `feat(telemetry): decode GT7 packet type A`

---

### Task 4: TelemetrySource, Mitschnitt + Replay

**Files:**
- Create: `telemetry/TelemetrySource.kt`, `telemetry/PacketRecording.kt`, `telemetry/ReplayTelemetrySource.kt`
- Test: `commonTest/.../telemetry/PacketRecordingTest.kt`, `.../telemetry/ReplayTelemetrySourceTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class RawPacket(val atMs: Long, val bytes: ByteArray, val senderHost: String? = null)
  interface TelemetrySource { val packets: Flow<RawPacket> }
  class PacketRecorder(sink: kotlinx.io.Sink) : AutoCloseable { fun write(p: RawPacket) }
  fun readRecording(source: kotlinx.io.Source): List<RawPacket>
  class ReplayTelemetrySource(recording: List<RawPacket>, nowMs: () -> Long, realtime: Boolean = true) : TelemetrySource
  ```
  Format je Paket: `[i64 BE atMs][u16 BE len][bytes]`. Replay: emittiert in Aufnahme-Reihenfolge, wartet bei `realtime` die Abstände der Aufnahme ab (`delay`), `atMs` wird auf `nowMs()` beim Start rebased.

- [ ] **Step 1: Failing Tests**
  - `recordingRoundTrips`: 3 Pakete (Längen 296, 296, 10) in `Buffer` schreiben, `readRecording` → gleiche `atMs` und `bytes`.
  - `truncatedTailIsIgnored`: letzte 5 Bytes abschneiden → 2 Pakete statt 3, keine Exception.
  - `replayKeepsRelativeTiming` (runTest, virtual time): Aufnahme bei 1000/1016/1050 ms, `nowMs = { testScheduler.currentTime }` → emittierte `atMs` sind `0, 16, 50` und `currentTime` nach Collect == 50.
- [ ] **Step 2:** → FAIL. **Step 3:** implementieren. **Step 4:** → PASS.
- [ ] **Step 5: Commit** `feat(telemetry): add packet recording and replay source`

---

### Task 5: UDP-Client + Discovery

**Files:**
- Create: `telemetry/UdpTelemetrySource.kt`, `telemetry/Discovery.kt`
- Test: `shared/src/desktopTest/kotlin/de/bavaco/gt7/telemetry/UdpTelemetrySourceTest.kt`

**Interfaces:**
- Consumes: `TelemetrySource`, `RawPacket`, `PacketDecoder`
- Produces:
  ```kotlin
  class UdpTelemetrySource(host: String, sendPort: Int = 33739, receivePort: Int = 33740,
                           heartbeatIntervalMs: Long = 10_000, nowMs: () -> Long) : TelemetrySource
  suspend fun discoverPs5(timeoutMs: Long = 3_000, broadcastHost: String = "255.255.255.255",
                          sendPort: Int = 33739, receivePort: Int = 33740): String?
  ```
  Ein Ktor-UDP-Socket gebunden auf `0.0.0.0:receivePort` sendet die Heartbeats (Byte `'A'`) und empfängt. Flow ist cold; Socket wird bei Cancel geschlossen. Discovery: Socket mit `broadcast = true`, Heartbeat an `broadcastHost`, erstes Paket, das `PacketDecoder.decode != null` ergibt → Absender-Host; Timeout → `null`.

- [ ] **Step 1: Failing Tests** (Fake-PS5 auf `127.0.0.1` mit freiem Port, Client-`receivePort` ebenfalls frei gewählt):
  - `sendsHeartbeatAndEmitsReply`: Fake empfängt `"A"`, antwortet mit `encryptForTest(plainPacket{}, 1)` an Absender → Flow liefert 1 Paket mit 296 Bytes.
  - `repeatsHeartbeat`: `heartbeatIntervalMs = 200` → Fake zählt ≥ 3 Heartbeats in 700 ms.
  - `discoveryReturnsNullOnTimeout`: `broadcastHost = "127.0.0.1"`, Port ohne Empfänger, `timeoutMs = 300` → `null` innerhalb < 1 s.
  - `discoveryFindsSender`: Fake antwortet auf Heartbeat → `"127.0.0.1"`.
  (`encryptForTest`/`plainPacket` aus Task 3 nach `commonTest` sichtbar; desktopTest sieht commonTest.)
- [ ] **Step 2:** → FAIL. **Step 3:** implementieren. **Step 4:** → PASS.
- [ ] **Step 5: Commit** `feat(telemetry): UDP client with heartbeat and discovery`

---

### Task 6: TelemetryPipeline + LapMath

**Files:**
- Create: `telemetry/TelemetryPipeline.kt`, `session/LapModels.kt`, `session/LapMath.kt`
- Test: `commonTest/.../telemetry/TelemetryPipelineTest.kt`, `commonTest/.../session/LapMathTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class TimedFrame(val atMs: Long, val frame: TelemetryFrame, val senderHost: String?)
  data class PacketStats(val packetsPerSecond: Int, val dropped: Long, val lastPacketAtMs: Long?)
  class TelemetryPipeline(source: TelemetrySource, recorder: () -> PacketRecorder?) {
      val frames: Flow<TimedFrame>; val stats: StateFlow<PacketStats>
  } // recorder() wird pro Paket abgefragt und schreibt Rohpakete vor dem Dekodieren; packetsPerSecond = Pakete in den letzten 1000 ms (nach atMs)

  data class LapSample(val timeMs: Int, val distanceM: Float, val speedMps: Float, val throttle: Float,
                       val brake: Float, val gear: Int, val rpm: Float, val x: Float, val z: Float, val boostBar: Float)
  data class CompletedLap(val number: Int, val timeMs: Int, val measuredMs: Int, val fuelUsed: Float,
                          val isValid: Boolean, val samples: List<LapSample>)
  // LapMath.kt (top-level):
  fun timeAtDistance(samples: List<LapSample>, distanceM: Float): Float?   // lineare Interpolation, null außerhalb
  fun liveDeltaMs(currentMs: Int, distanceM: Float, reference: List<LapSample>?): Int?
  fun fuelLapsRemaining(fuelLevel: Float, recentFuelUsed: List<Float>): Float?   // letzte ≤3, null wenn leer oder Ø ≤ 0
  fun wheelSlip(wheelRps: Float, radiusM: Float, speedMps: Float): Float?        // |rps|·r / v − 1; null bei v < 10/3.6
  fun isSlipAlert(slip: Float?): Boolean                                         // |slip| ≥ 0.15
  fun deltaCurve(lap: List<LapSample>, reference: List<LapSample>, stepM: Float = 10f): List<Pair<Float, Int>>
  ```
- [ ] **Step 1: Failing Tests**
  - Pipeline `dropsInvalidAndCounts`: Source mit 2 gültigen + 1 kaputtem Paket → 2 Frames, `stats.value.dropped == 1L`; `recorderGetsRawPackets`: Fake-Recorder erhält alle 3.
  - `timeAtDistance` bei Samples (0 m, 0 ms), (100 m, 2000 ms) und 50 m → `1000f`; bei 150 m → `null`.
  - `liveDeltaMs(currentMs = 1200, distanceM = 50f, ref)` → `200`; `reference = null` → `null`.
  - `fuelLapsRemaining(30f, listOf(5f, 3f, 3f, 3f))` → `10f` (nur letzte 3); `listOf()` → `null`.
  - `wheelSlip(rps = 50f, r = 0.33f, v = 15f)` ≈ `0.1f`; `v = 2f` → `null`; `isSlipAlert(0.16f)` true, `0.14f` false, `-0.2f` true.
  - `deltaCurve` mit Referenz 10 % schneller → letzter Wert > 0, Länge = ⌊minLänge/10⌋+1.
- [ ] **Step 2:** → FAIL. **Step 3:** implementieren. **Step 4:** → PASS.
- [ ] **Step 5: Commit** `feat: telemetry pipeline and lap math`

---

### Task 7: SessionEngine

**Files:**
- Create: `session/SessionEngine.kt` (+ `LiveState`, `SessionEvent` in `LapModels.kt`)
- Test: `commonTest/.../session/SessionEngineTest.kt`, `commonTest/.../session/FrameFactory.kt` (`fun frame(...)` mit Defaults: onTrack=true, alles andere 0)

**Interfaces:**
- Consumes: `TelemetryFrame`, `LapSample`, `CompletedLap`, LapMath
- Produces:
  ```kotlin
  sealed interface SessionEvent {
      data class Started(val carCode: Int, val atMs: Long) : SessionEvent
      data class LapCompleted(val lap: CompletedLap) : SessionEvent
      data class Ended(val atMs: Long) : SessionEvent
  }
  data class LiveState(val frame: TelemetryFrame?, val sessionActive: Boolean, val lapTimeMs: Int,
                       val distanceM: Float, val deltaMs: Int?, val fuelLapsRemaining: Float?, val slip: List<Float?> /*FL,FR,RL,RR*/)
  class SessionEngine {
      val live: StateFlow<LiveState>
      fun onFrame(frame: TelemetryFrame, atMs: Long): List<SessionEvent>
      fun onTick(atMs: Long): List<SessionEvent>          // 30-s-Timeout
      fun setReference(samples: List<LapSample>?)
  }
  ```
  Regeln (aus der Spec):
  - **Start:** erster Frame mit `onTrack && !loading` ohne aktive Sitzung → `Started`.
  - **Ende:** `loading`, `carCode`-Wechsel (→ `Ended` und danach sofort neues `Started`) oder `onTick` mit `atMs − letzterFrame > 30_000`.
  - **Rundenwechsel:** `currentLap` steigt. Nur wenn auch der Start der laufenden Runde als Inkrement beobachtet wurde, wird die Runde abgeschlossen. Die abgeschlossene Runde bleibt „pending“, bis `lastLapMs` sich gegenüber dem Wert beim Inkrement ändert (dann `timeMs = lastLapMs`), spätestens nach 2000 ms (dann `timeMs = measuredMs`, `isValid = false`).
  - **Distanz/Zeit:** Δt = `atMs − prevAtMs`, auf 100 ms gedeckelt; Distanz += `speedMps · Δt`; nur wenn `onTrack && !paused`. `measuredMs` = Summe aller *ungedeckelten* Δt der Runde.
  - **Ungültig**, wenn in der Runde `paused` auftrat, `packetId` rückwärts lief oder |`measuredMs` − `timeMs`| > 2000.
  - Samples ≥ 100 ms Abstand (nach Rundenzeit), Fuel bei Rundenstart/-ende für `fuelUsed`.
  - `live` wird bei jedem Frame aktualisiert (Delta über `liveDeltaMs` mit Referenz, Benzin aus den letzten gültigen `fuelUsed` der Sitzung, Slip über `wheelSlip`).
- [ ] **Step 1: Failing Tests** (Frames im 16-ms-Takt, Helper `drive(laps, msPerLap, …)`):
  - `startsSessionOnFirstOnTrackFrame`
  - `completesLapOnLapIncrement`: Lap 1→2→3 mit lastLapMs-Update im selben Frame → ein `LapCompleted` für Runde 2 (Runde 1 war angebrochen), `timeMs` == gesetzter Wert, `isValid`.
  - `partialFirstLapIsNotRecorded`: Start bei Lap 5 → erstes Inkrement erzeugt kein `LapCompleted`.
  - `lapTimeTakenFromDelayedLastLapField`: lastLapMs ändert sich 10 Frames nach dem Inkrement → `timeMs` == neuer Wert.
  - `pauseInvalidatesLap`, `packetIdBackwardsInvalidatesLap`, `timeMismatchInvalidatesLap` (gemeldete Zeit 3 s neben gemessener).
  - `gapDoesNotInflateDistance`: 1 s Lücke bei 50 m/s → Distanz wächst um ≤ 5 m.
  - `samplesAre10Hz`: 1 s Fahrt → 10 ± 1 Samples.
  - `timeoutEndsSession`: `onTick(last + 30_001)` → `Ended`; `onTick(last + 29_000)` → leer.
  - `carChangeRestartsSession`: → `[Ended, Started]`.
  - `liveStateHasDeltaWithReference`: Referenz gesetzt → `live.value.deltaMs != null`.
- [ ] **Step 2:** → FAIL. **Step 3:** implementieren. **Step 4:** → PASS.
- [ ] **Step 5: Commit** `feat(session): session engine with lap detection`

---

### Task 8: TrackMatcher

**Files:**
- Create: `session/TrackMatcher.kt`
- Test: `commonTest/.../session/TrackMatcherTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class Track(val id: Long, val name: String, val outline: FloatArray)
  object TrackMatcher {
      const val POINTS = 200; const val THRESHOLD_M = 15f
      fun outlineOf(samples: List<LapSample>): FloatArray  // 2·POINTS Werte x,z; gleichabständig über Distanz, minus Startpunkt
      fun meanDistance(a: FloatArray, b: FloatArray): Float
      fun findMatch(outline: FloatArray, tracks: List<Track>): Track? // kleinster Abstand < THRESHOLD_M
  }
  ```
- [ ] **Step 1: Failing Tests**: Kreis r = 500 m vs. derselbe Kreis mit ±3 m Rauschen und anderer Sample-Dichte → Match; vs. Oval 800×300 m → kein Match; `outlineOf(...).size == 400`, erster Punkt `(0,0)`; zwei Kandidaten → der nähere gewinnt.
- [ ] **Step 2:** → FAIL. **Step 3:** implementieren. **Step 4:** → PASS.
- [ ] **Step 5: Commit** `feat(session): track matching by outline`

---

### Task 9: Persistenz (SQLDelight, Repository, CarCatalog)

**Files:**
- Create: `shared/src/commonMain/sqldelight/de/bavaco/gt7/db/Gt7.sq`, `storage/SessionRepository.kt`, `storage/CarCatalog.kt`, `storage/Platform.kt`, `desktopMain/.../storage/Platform.desktop.kt`
- Create: `shared/src/commonMain/composeResources/files/cars.csv`
- Test: `desktopTest/.../storage/SessionRepositoryTest.kt`, `commonTest/.../storage/CarCatalogTest.kt`

**Interfaces:**
- Consumes: `CompletedLap`, `LapSample`, `Track`
- Produces:
  ```kotlin
  data class SessionSummary(val id: Long, val trackId: Long?, val trackName: String?, val carCode: Int,
                            val startedAtMs: Long, val bestLapMs: Int?)
  data class LapSummary(val id: Long, val sessionId: Long, val number: Int, val timeMs: Int, val fuelUsed: Float, val isValid: Boolean)
  class SessionRepository(db: Gt7Database) {
      fun createSession(carCode: Int, startedAtMs: Long): Long
      fun endSession(id: Long, endedAtMs: Long)
      fun saveLap(sessionId: Long, lap: CompletedLap): Long         // Lap + Samples in einer Transaktion
      fun createTrack(name: String, outline: FloatArray): Long
      fun assignTrack(sessionId: Long, trackId: Long)
      fun tracks(): List<Track>
      fun lastSessionStartForTrack(trackId: Long): Long?
      fun bestLapSamples(trackId: Long): List<LapSample>?          // schnellste gültige Runde der Strecke
      fun sessions(): Flow<List<SessionSummary>>                    // neueste zuerst
      fun laps(sessionId: Long): List<LapSummary>
      fun samples(lapId: Long): List<LapSample>
      fun setting(key: String): String?; fun setSetting(key: String, value: String)
  }
  class CarCatalog(csv: String) { fun name(carCode: Int): String }  // Header mit Spalten "ID","ShortName"; unbekannt → "Auto #<id>"
  expect fun createDriver(): SqlDriver     // desktop: JdbcSqliteDriver("jdbc:sqlite:<appDataDir>/gt7.db", Properties(), Gt7Database.Schema)
  expect fun appDataDir(): String          // desktop: ~/Library/Application Support/GT7Dashboard (anlegen)
  ```
  Schema: Tabellen laut Spec (`track`, `session`, `lap`, `lap_sample` inkl. `time_ms`, `setting`); `outline` als BLOB (Floats LE); Index auf `lap_sample(lap_id)`.
- [ ] **Step 1: Failing Tests** (In-Memory-Treiber `JdbcSqliteDriver(IN_MEMORY)` + `Gt7Database.Schema.create`):
  - `savesAndReadsLapWithSamples` (Werte identisch).
  - `bestLapSamplesIgnoresInvalidLaps`: gültig 100 s, ungültig 90 s → Samples der 100-s-Runde.
  - `bestLapSamplesSpansSessionsOfSameTrack`.
  - `outlineRoundTrips`; `sessionsFlowEmitsNewestFirstWithBestLap`; `settingsUpsert`.
  - CarCatalog: `"ID,ShortName,Maker\n3332,GR Supra,1"` → `name(3332) == "GR Supra"`, `name(1) == "Auto #1"`.
- [ ] **Step 2:** → FAIL. **Step 3:** implementieren.
- [ ] **Step 4: cars.csv**: Datei `_data/db/cars.csv` aus dem GitHub-Repo `ddm999/gt7info` herunterladen und unter `composeResources/files/cars.csv` ablegen (bei fehlendem Netzzugang den User bitten). Falls nicht verfügbar: Datei nur mit Header anlegen – App fällt auf „Auto #id“ zurück.
- [ ] **Step 5:** Tests → PASS. **Step 6: Commit** `feat(storage): SQLDelight repository and car catalog`

---

### Task 10: SessionController

**Files:**
- Create: `session/SessionController.kt`
- Test: `desktopTest/.../session/SessionControllerTest.kt` (echte Engine + In-Memory-Repo + Fake-Source)

**Interfaces:**
- Consumes: `TelemetryPipeline`, `SessionEngine`, `SessionRepository`, `TrackMatcher`
- Produces:
  ```kotlin
  sealed interface TrackQuestion {
      data class Suggest(val track: Track, val lastDrivenAtMs: Long) : TrackQuestion
      data object AskName : TrackQuestion
  }
  enum class ConnectionStatus { Idle, Searching, Waiting, Receiving }
  class SessionController(scope: CoroutineScope, repo: SessionRepository, nowMs: () -> Long) {
      val live: StateFlow<LiveState>; val stats: StateFlow<PacketStats>
      val connection: StateFlow<ConnectionStatus>; val ps5Host: StateFlow<String?>
      val trackQuestion: StateFlow<TrackQuestion?>
      fun connect(host: String)                 // speichert Setting "ps5Host", startet UdpTelemetrySource
      fun discover()                            // Searching → bei Erfolg connect(host), sonst Idle
      fun startReplay(recording: List<RawPacket>)  // ersetzt aktive Source
      fun startRecording(sink: Sink); fun stopRecording()
      fun confirmTrack(); fun rejectSuggestion(); fun nameTrack(name: String)
  }
  ```
  Verhalten:
  - Events der Engine → Repository (`Started` → `createSession`, `LapCompleted` → `saveLap`, `Ended` → `endSession`). DB-Fehler werden geloggt (`println` auf stderr), Live läuft weiter.
  - Nach erster gültiger Runde einer Sitzung ohne Track: `findMatch` → `Suggest` (mit `lastSessionStartForTrack`) bzw. `AskName`. `confirmTrack` → `assignTrack` + Referenz = `bestLapSamples(track)`. `rejectSuggestion` → `AskName`. `nameTrack` → `createTrack(name, outline)` + `assignTrack`.
  - Referenz: anfangs `null`; jede gültige Runde, die schneller als die Referenz ist (oder keine Referenz existiert), wird neue Referenz.
  - `connection`: `Receiving`, wenn `stats.lastPacketAtMs` ≤ 2000 ms alt; sonst `Waiting` (bei gesetztem Host). Ticker jede Sekunde ruft `engine.onTick` und aktualisiert `connection`.
  - Beim Start: gespeicherten `ps5Host` laden → `connect`, sonst `discover()`.
- [ ] **Step 1: Failing Tests**:
  - `firstValidLapBecomesReference`: vor Runde `live.deltaMs == null`; nach erster gültiger Runde während der nächsten Runde `!= null`.
  - `unknownTrackAsksName` → `AskName`; `nameTrack("Spa")` → `tracks()` enthält „Spa“, Sitzung hat `trackId`.
  - `knownTrackIsSuggested`: zweite Sitzung gleicher Form → `Suggest(track = Spa)`; `confirmTrack` setzt Referenz auf beste gespeicherte Runde.
  - `lapsArePersisted`.
  - `connectionTurnsWaitingAfter2s`.
- [ ] **Step 2:** → FAIL. **Step 3:** implementieren. **Step 4:** → PASS.
- [ ] **Step 5: Commit** `feat(session): controller wiring engine, storage and track matching`

---

### Task 11: App-Shell + Live-Ansicht

**Files:**
- Create: `ui/Theme.kt`, `ui/Format.kt`, `ui/LiveScreen.kt`; Modify: `ui/App.kt`, `desktopApp/.../Main.kt`
- Test: `commonTest/.../ui/FormatTest.kt`

**Interfaces:**
- Consumes: `SessionController`, `CarCatalog`
- Produces:
  ```kotlin
  enum class SpeedUnit { KMH, MPH }
  fun formatLapTime(ms: Int?): String         // 102381 → "1:42.381", null/≤0 → "–:––.–––"
  fun formatDelta(ms: Int?): String           // 214 → "+0.214", -1050 → "−1.050", null → "–"
  fun formatSpeed(mps: Float, unit: SpeedUnit): String  // 58.9 m/s KMH → "212"
  @Composable fun App(controller: SessionController, cars: CarCatalog)
  @Composable fun LiveScreen(state: LiveState, connection: ConnectionStatus, host: String?, unit: SpeedUnit)
  ```
  `Main.kt` erzeugt Driver, Repository, Controller (Scope des Fensters), lädt `cars.csv` via `Res.readBytes("files/cars.csv")`.
  Tabs Live / Analyse / Einstellungen (Analyse/Einstellungen hier als Platzhalter). Live-Layout wie Spec: RPM-Balken (Farbe wechselt ab `rpmAlertMin`, Blinken ab `rpmAlertMax`), Gang groß, Tempo, Runde x/y, Position, letzte/beste Zeit, Delta (grün negativ / rot positiv), Gas-/Bremsbalken, Reifentemperaturen 2×2, Slip-Indikator je Rad (blinkt bei `isSlipAlert`), Benzin + Restrunden, Ladedruck nur bei `hasTurbo`. Ohne `Receiving`: Overlay „Warte auf PS5 (<host>)…“ bzw. „Suche PS5…“.
- [ ] **Step 1: Failing Tests** für die drei Formatfunktionen (Werte wie oben, plus `formatSpeed(58.9f, MPH) == "132"`).
- [ ] **Step 2:** → FAIL. **Step 3:** implementieren (Format + Screens). **Step 4:** Tests → PASS.
- [ ] **Step 5: Manuell prüfen:** `./gradlew :desktopApp:run`, in Einstellungen ist noch kein Replay – daher temporär prüfen, dass Overlay „Suche PS5…“ erscheint und das Fenster ohne Fehler läuft.
- [ ] **Step 6: Commit** `feat(ui): app shell and live dashboard`

---

### Task 12: Einstellungen + Track-Dialog

**Files:**
- Create: `ui/SettingsScreen.kt`, `ui/TrackDialog.kt`; Modify: `ui/App.kt`, `storage/Platform.kt` + `Platform.desktop.kt` (`expect fun pickRecordingFile(): String?`)

**Interfaces:**
- Consumes: `SessionController` (`connect`, `discover`, `startRecording`, `stopRecording`, `startReplay`, `trackQuestion`, `stats`), `readRecording`, `SessionRepository.setting/setSetting` (Key `"unit"`)
- Produces: `@Composable fun SettingsScreen(controller: SessionController, unit: SpeedUnit, onUnitChange: (SpeedUnit) -> Unit)`, `@Composable fun TrackDialog(question: TrackQuestion, onConfirm: () -> Unit, onReject: () -> Unit, onName: (String) -> Unit)`

  Einstellungen: IP-Feld + „Verbinden“, Button „PS5 suchen“, Einheit km/h/mph, Diagnose „<n> Pakete/s · <m> verworfen“, „Mitschnitt starten/stoppen“ (Datei `<appDataDir>/recordings/<yyyy-MM-dd_HH-mm-ss>.gt7rec`), „Mitschnitt abspielen…“ (Datei per `java.awt.FileDialog` – liegt in `desktopMain` hinter `expect fun pickRecordingFile(): String?`).
  Dialog: `Suggest` → „Gleiche Strecke wie am <dd.MM.> – <Name>?“ [Ja] [Nein]; `AskName` → Textfeld „Name der Strecke“ + [Speichern]. Wird in `App` über allen Tabs angezeigt, solange `trackQuestion != null`.
- [ ] **Step 1: Manuell prüfen** mit Replay: Mitschnitt (sobald vorhanden) abspielen → Live-Werte laufen, nach der ersten gültigen Runde erscheint der Dialog. Ohne Mitschnitt: Dialog-Composables in einer Desktop-`@Preview`/Testfenster mit beiden `TrackQuestion`-Varianten sichtbar prüfen.
- [ ] **Step 2: Commit** `feat(ui): settings, diagnostics, recording and track dialog`

---

### Task 13: Analyse-Ansicht

**Files:**
- Create: `ui/AnalysisScreen.kt`, `ui/Charts.kt`, `ui/TrackMap.kt`
- Test: `commonTest/.../ui/ChartScaleTest.kt`

**Interfaces:**
- Consumes: `SessionRepository.sessions/laps/samples`, `deltaCurve`, `formatLapTime`, `CarCatalog`
- Produces:
  ```kotlin
  data class Series(val points: List<Pair<Float, Float>>, val color: Color)
  fun niceAxis(min: Float, max: Float, ticks: Int = 5): List<Float>   // „runde“ Achsenwerte, z. B. (0, 237) → [0, 50, 100, 150, 200, 250]
  @Composable fun LineChart(title: String, series: List<Series>, yRange: ClosedFloatingPointRange<Float>? = null, modifier: Modifier)
  @Composable fun TrackMap(lap: List<LapSample>, delta: List<Pair<Float, Int>>, modifier: Modifier)  // Linie gefärbt nach Ableitung der Delta-Kurve: grün Gewinn, rot Verlust
  @Composable fun AnalysisScreen(repo: SessionRepository, cars: CarCatalog)
  ```
  Links Sitzungsliste (Strecke oder „Unbekannte Strecke“, Auto, Datum, Bestzeit) → Rundenliste (Zeit, Δ zur Sitzungsbesten, Verbrauch, ungültige Runden ausgegraut + „ungültig“). Max. zwei Runden auswählbar; zweite Runde darf aus anderer Sitzung *derselben Strecke* kommen (Sitzungsliste filtert dann auf `trackId`). Rechts: Charts über Distanz für Tempo, Gas/Bremse, Gang, Delta-Kurve; darunter `TrackMap`. Samples-Laden in `LaunchedEffect` auf `Dispatchers.Default`.
- [ ] **Step 1: Failing Test** `niceAxis(0f, 237f)` == `[0, 50, 100, 150, 200, 250]`; `niceAxis(-0.8f, 1.3f)` beginnt ≤ −0.8 und endet ≥ 1.3.
- [ ] **Step 2:** → FAIL. **Step 3:** implementieren. **Step 4:** → PASS.
- [ ] **Step 5: Manuell prüfen** mit Replay-Daten: zwei Runden wählen → alle Charts und Karte gezeichnet.
- [ ] **Step 6: Commit** `feat(ui): lap analysis with charts and track map`

---

### Task 14: macOS-Paket + Test an der PS5

**Files:**
- Modify: `desktopApp/build.gradle.kts` (`macOS { bundleID = "de.bavaco.gt7dashboard"; iconFile }` optional), `README.md` erstellen (Start, PS5-Setup, Mitschnitt)

- [ ] **Step 1:** `./gradlew :shared:desktopTest` → alle Tests PASS.
- [ ] **Step 2:** `./gradlew :desktopApp:packageDmg` → DMG unter `desktopApp/build/compose/binaries/main/dmg/`.
- [ ] **Step 3: Mit PS5 (User):** GT7 starten, App öffnen → Discovery oder IP eintragen; Live-Werte prüfen (Tempo stimmt mit Spiel überein, Gang, Gas/Bremse); 3 Runden fahren; Track-Dialog beantworten; Analyse zeigt Runden. macOS-Firewall-Dialog für eingehende UDP-Verbindungen bestätigen.
- [ ] **Step 4:** Mitschnitt von ~1 Runde aufnehmen und 3 Pakete als Fixture `commonTest/resources/real_packets.bin` + Test `decodesRealPacket` (Magic ok, Tempo > 0) ergänzen.
- [ ] **Step 5: Commit** `chore: macOS packaging, README and real packet fixture`
