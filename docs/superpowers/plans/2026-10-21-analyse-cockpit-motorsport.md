# Analyse-Cockpit im Motorsport-Stil – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Motorsport-Stil als Grundlage der ganzen App und eine neue Analyse als Cockpit mit Kurvenliste.

**Architecture:** Farben, Schrift und Formen liegen zentral in `Theme.kt`; `RaceStyle.kt` liefert wiederverwendbare Bausteine. Die Analyse wird in `CockpitAnalysis.kt` neu aufgebaut und ersetzt Detailanalyse und Vergleichsansicht; die Kurvenliste ist eine eigene Datei mit reiner, getesteter Navigationslogik. Viewport, Renningenieur und Export bleiben unverändert.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.12 (Material 3, Compose Resources), kotlin.test.

**Spec:** `docs/superpowers/specs/2026-10-21-analyse-cockpit-motorsport-design.md`

## Global Constraints

- Schrift: Barlow Condensed (SIL OFL 1.1), Schnitte Regular, Medium, SemiBold, Bold, in `shared/src/commonMain/composeResources/font/`, `OFL.txt` daneben.
- Farben exakt: Carbon `#0A0A0A`, Surface `#141414`, SurfaceRaised `#1F1F1F`, Line `#262626`, TextPrimary `#F2F2F2`, TextMuted `#9A9A9A`, Accent `#E10600`, LapA `#FFD400`, LapB `#00D2FF`, Loss `#E10600`, Gain `#00E676`, Best `#B14CFF`, Warn `#FFC107`.
- Außerhalb von `ui/Theme.kt` kein `Color(0x` in `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/`.
- Zahlen mit festen Ziffern: `fontFeatureSettings = "tnum"`.
- Kurvenmarken heißen „T1“ … „Tn“.
- Texte der App bleiben deutsch; nur Fahrtipps folgen `TipLanguage`.
- Commits mit der noreply-Adresse (lokale Git-Konfiguration), Abschluss-Zeile `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. Analyse ohne Runde B und ohne Vergleichsrunde (Runde ohne Strecke): Kopfzeile zeigt „–“, Kurvenliste den Hinweis, nichts stürzt ab → `cockpitHeader`-Test „kein Vergleich“ (Task 5).
2. Strecke ohne erkannte Kurven (Debrief mit leerer Liste): ◀ ▶ und Pfeiltasten tun nichts → `stepCorner` mit leerer Liste (Task 4).
3. Alle Kurven schneller als der Vergleich: keine rote Marke, Balken grün, keine Division durch null → `costliestCorners`/`lossBarFraction` „alle gewinnen“ bzw. „alle null“ (Task 4).
4. Sitzung ohne vollständige Sektoren (eine Runde, zu kurze Runde): Theoretisch „–“ → `cockpitHeader`-Test (Task 5).
5. Pfeiltasten während ein Textfeld (Suche) den Fokus hat dürfen keine Kurve wechseln → Tasten nur am fokussierbaren Cockpit-Container, nicht global (Task 6, Sichtprüfung).

---

### Task 1: Schrift und Farben im Theme

**Files:**
- Create: `shared/src/commonMain/composeResources/font/barlow_condensed_{regular,medium,semibold,bold}.ttf`, `shared/src/commonMain/composeResources/font/OFL.txt` (Download aus `github.com/google/fonts/ofl/barlowcondensed/`)
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/Theme.kt`
- Test: `shared/src/commonTest/kotlin/de/bavaco/gt7/ui/ThemeTest.kt`

**Interfaces:**
- Produces: `object RaceColors { val Carbon, Surface, SurfaceRaised, Line, TextPrimary, TextMuted, Accent, LapA, LapB, Loss, Gain, Best, Warn: Color }`; `fun raceColorScheme(): ColorScheme`; `@Composable fun raceFontFamily(): FontFamily`; `fun digits(size: TextUnit, family: FontFamily = FontFamily.Default): TextStyle` (Bold, `tnum`); `Gt7Theme` setzt colorScheme, Typography (alle Stile mit Barlow), Shapes (Ecken 2 dp) und einen Carbon-Hintergrund mit diagonaler Textur über `Modifier.carbon()`.
- Die alten Namen `GoodGreen`, `BadRed`, `WarnAmber`, `Panel`, `LapAColor`, `LapBColor` bleiben als Aliase auf `RaceColors` (Gain, Loss, Warn, Surface, LapA, LapB), damit bestehende Aufrufer kompilieren.

- [ ] **Step 1:** Test `raceColorSchemeUsesTheRacePalette`: `primary == Accent`, `background == Carbon`, `surface == Surface`, `onSurface == TextPrimary`, `outline == Line`; `RaceColors.LapA == Color(0xFFFFD400)`, `RaceColors.Best == Color(0xFFB14CFF)`.
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*ThemeTest*'` → FAIL (unresolved).
- [ ] **Step 3:** Fonts ablegen, `RaceColors`, `raceColorScheme()`, Typografie und Shapes in `Theme.kt`; `digits()` auf Barlow + `tnum`.
- [ ] **Step 4:** Test grün; `./gradlew :shared:compileKotlinDesktop` ohne Fehler.
- [ ] **Step 5:** Commit `feat(ui): motorsport palette and Barlow Condensed in the theme`.

### Task 2: Feste Farben auf das Theme umstellen

**Files:**
- Modify: alle Dateien unter `ui/` mit `Color(0x` außer `Theme.kt` (RaceDisplay, Charts, MapOverlay, DriverPicker, AnalysisLists, TracksScreen, TrackMap, LiveMap, EngineerPanel, DetailAnalysis)
- Test: `shared/src/desktopTest/kotlin/de/bavaco/gt7/ui/NoColorLiteralsTest.kt`

**Interfaces:**
- Consumes: `RaceColors` (Task 1).

- [ ] **Step 1:** Test `noColorLiteralsOutsideTheTheme`: liest alle `*.kt` in `src/commonMain/kotlin/de/bavaco/gt7/ui/` (Arbeitsverzeichnis des Tests ist `shared/`), ignoriert `Theme.kt`, erwartet leere Liste der Dateien mit `Color(0x`; Fehlermeldung nennt Datei:Zeile.
- [ ] **Step 2:** Test läuft rot und listet die Fundstellen.
- [ ] **Step 3:** Jede Fundstelle auf den passenden `RaceColors`-Namen umstellen (halbtransparente Werte als `RaceColors.X.copy(alpha = …)`); Werte ohne passenden Namen sind Grau-/Weißtöne → `TextPrimary`, `TextMuted`, `Line` oder `SurfaceRaised`.
- [ ] **Step 4:** Test grün, alle Tests grün.
- [ ] **Step 5:** Commit `refactor(ui): colors only from the theme`.

### Task 3: Bausteine RaceStyle

**Files:**
- Create: `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/RaceStyle.kt`

**Interfaces:**
- Produces: `@Composable fun RaceCard(modifier: Modifier = Modifier, accent: Color? = RaceColors.Accent, content: @Composable ColumnScope.() -> Unit)` (Surface-Fläche, 3 dp Kante links, Ecken 2 dp, Innenabstand 8×10 dp); `@Composable fun RaceLabel(text: String, modifier: Modifier = Modifier, color: Color = RaceColors.TextMuted)` (Großbuchstaben, 10 sp, SemiBold, letterSpacing 0.14 em); `@Composable fun RaceNumber(text: String, color: Color = RaceColors.TextPrimary, size: TextUnit = 22.sp, modifier: Modifier = Modifier)` (über `digits`).

- [ ] **Step 1:** Bausteine schreiben; reine UI ohne Logik, daher kein eigener Unit-Test (geprüft über Kompilieren und die Screenshots in Task 8).
- [ ] **Step 2:** `./gradlew :shared:compileKotlinDesktop` ohne Fehler.
- [ ] **Step 3:** Commit `feat(ui): race card, label and number building blocks`.

### Task 4: Kurvenliste – Logik

**Files:**
- Create: `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/CornerList.kt` (Logik-Teil)
- Test: `shared/src/commonTest/kotlin/de/bavaco/gt7/ui/CornerListTest.kt`

**Interfaces:**
- Consumes: `Corner`, `CornerReport` (session/Engineer.kt).
- Produces: `fun stepCorner(corners: List<Corner>, selected: Corner?, direction: Int): Corner?`; `fun costliestCorners(reports: List<CornerReport>, count: Int = 3): Set<Int>` (Kurvennummern); `fun lossBarFraction(report: CornerReport, reports: List<CornerReport>): Float`.

- [ ] **Step 1:** Tests mit Kurven 1–4:
  - `stepCorner(cs, null, +1) == cs[0]`, `stepCorner(cs, null, -1) == cs[3]`, `stepCorner(cs, cs[1], +1) == cs[2]`, `stepCorner(cs, cs[3], +1) == cs[3]`, `stepCorner(cs, cs[0], -1) == cs[0]`, `stepCorner(emptyList(), null, +1) == null`.
  - `costliestCorners` bei Verlusten [560, 140, −20, 840, 820] → `{4, 5, 1}`; bei [30, −10] → `{1}`; bei nur Gewinnen → leer.
  - `lossBarFraction`: Verluste [400, −200, 800] → 0.5, 0.25, 1.0; alle 0 → 0f.
- [ ] **Step 2:** Tests rot.
- [ ] **Step 3:** Implementieren; `lossBarFraction` teilt den Betrag durch den größten Betrag aller Berichte.
- [ ] **Step 4:** Tests grün.
- [ ] **Step 5:** Commit `feat(ui): corner list stepping, costliest corners and loss bars`.

### Task 5: Kopfzeile – Logik

**Files:**
- Create: `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/CockpitHeader.kt`
- Test: `shared/src/commonTest/kotlin/de/bavaco/gt7/ui/CockpitHeaderTest.kt`

**Interfaces:**
- Consumes: `LapSummary`, `SessionSummary`, `ComparisonLap`, `EngineerState`, `LapTableStats`.
- Produces:
  ```kotlin
  data class HeaderCard(val label: String, val value: String, val detail: String?, val tone: Tone)
  enum class Tone { LapA, LapB, Loss, Gain, Best, Neutral }
  fun cockpitHeader(lapA: LapSummary, sessionA: SessionSummary?, pickedB: LapSummary?, sessionB: SessionSummary?,
                    engineer: EngineerState, stats: LapTableStats?, carName: (Int) -> String): List<HeaderCard> // immer 4
  ```

- [ ] **Step 1:** Tests:
  - B gewählt (A 90 543, B 85 785): Karten `RUNDE A 1:30.543 (LapA)`, `RUNDE B 1:25.785 (LapB)`, `DIFFERENZ +4.758 (Loss)`, `THEORETISCH` aus `stats.theoreticalMs` (Best); Detail der A-Karte „Max · <Autoname>“.
  - Automatischer Vergleich (`EngineerState.Ready` mit `ComparisonLap(…, basis = REFERENCE)`, kein B): zweite Karte `VERGLEICH 1:25.785`, Detail „Max · Referenzrunde“; Differenz aus `debrief.totalLossMs`.
  - Kein Vergleich (`EngineerState.NoTrack`): zweite und dritte Karte Wert „–“, Tone Neutral.
  - Theoretisch ohne Sektoren (`stats = null` bzw. `theoreticalMs = null`): Wert „–“.
  - A schneller (Differenz −1 200): Tone Gain, Wert „−1.200“.
- [ ] **Step 2:** Tests rot.
- [ ] **Step 3:** Implementieren mit `formatLapTime`/`formatDelta`; Basis-Text deutsch wie `EngineerText.basis` (DE).
- [ ] **Step 4:** Tests grün.
- [ ] **Step 5:** Commit `feat(ui): cockpit header values`.

### Task 6: Kurvenliste – Oberfläche

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/CornerList.kt`
- Delete später (Task 7): `EngineerPanel.kt`

**Interfaces:**
- Consumes: Task 4, `AnalysisViewport.toggleCorner`, `EngineerText`.
- Produces: `@Composable fun CornerList(state: EngineerState, language: TipLanguage, viewport: AnalysisViewport, modifier: Modifier = Modifier)`; `fun Modifier.cornerKeys(state: EngineerState, viewport: AnalysisViewport): Modifier` (↓/→ vor, ↑/← zurück via `onPreviewKeyEvent`, nur wenn der Container fokussiert ist).

- [ ] **Step 1:** Liste: Kopf `RaceLabel("Kurven")` mit ◀ ▶; je Kurve Zeile „T<n>“, Balken (`lossBarFraction`, Loss bzw. Gain), Verlust signiert; rote Marke (4 dp Punkt) für `costliestCorners`; gewählte Zeile `SurfaceRaised` und aufgeklappt mit `EngineerText.tip` und Werten Bremse/Scheitel/Gas/Gang. Kompakt: Nummer, Verlust, Hauptursache, Aufklappen per Tippen. Ohne Ready-Zustand oder ohne Berichte: Hinweistext aus `EngineerText`.
- [ ] **Step 2:** Kompilieren.
- [ ] **Step 3:** Commit `feat(ui): corner list with stepping and details`.

### Task 7: Cockpit-Analyse und Diagramme

**Files:**
- Create: `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/CockpitAnalysis.kt`
- Modify: `ui/AnalysisScreen.kt` (Layout-Umschalter, `CompareView`, `LAYOUT_*` entfernen; Cockpit einsetzen), `ui/Charts.kt` (Stil, Marken „T<n>“), `ui/AnalysisControls.kt` (kantige Knöpfe), `ui/DetailAnalysis.kt` (Rundentabelle/Rundenzeiten-Diagramm als `SessionLaps` behalten, Rest entfernen; Spitzen/Täler entfallen)
- Delete: `ui/EngineerPanel.kt` (`EngineerState` zieht nach `CornerList.kt`)
- Test: `shared/src/commonTest/kotlin/de/bavaco/gt7/ui/AnalysisViewportTest.kt` (Marken-Beschriftung)

**Interfaces:**
- Consumes: Tasks 1–6, `AnalysisViewport`, `chartOverlay`, `deltaSeries`, `ExportActions`, `PlaybackBar`.
- Produces: `@Composable fun CockpitAnalysis(viewport, corners, engineer, language, laps, header: List<HeaderCard>, loadSamples, toolbar: @Composable () -> Unit, modifier)`; `ChartOverlay.markers` Beschriftung über `fun markerLabel(number: Int) = "T$number"`.

- [ ] **Step 1:** Test `markerLabelsAreTurnNumbers`: `markerLabel(8) == "T8"`.
- [ ] **Step 2:** Test rot, dann `markerLabel` in `Charts.kt` und in Diagramm und Karte verwenden; Test grün.
- [ ] **Step 3:** `CockpitAnalysis`: Kopfzeile (4 `RaceCard`s + `toolbar`), darunter Row Karte (`weight 0.6`, mit `PlaybackBar`) und `CornerList` (`weight 0.4`); darunter Diagramme Delta (180 dp), Tempo (180 dp), Gas/Bremse kombiniert (150 dp, Gas Gain, Bremse Loss, B gestrichelt); „Weitere Kanäle“ einklappbar (Gang, Drehzahl, Ladedruck, Rollen, je 120 dp; Zustand in `remember` auf `AnalysisScreen`-Ebene); unten `SessionLaps`. Ganzes Cockpit vertikal scrollbar, Container fokussierbar mit `cornerKeys`. Kompakt: Kopfzeile 2 × 2, dann Karte, Liste, Diagramme untereinander.
- [ ] **Step 4:** `AnalysisScreen` auf `CockpitAnalysis` umstellen; Export-Aufnahme (`capture`) umschließt das Cockpit; Kompakt-Sonderweg für das Panel entfällt.
- [ ] **Step 5:** Alle Tests grün, Desktop- und Android-Build fehlerfrei.
- [ ] **Step 6:** Commit `feat(ui): analysis cockpit with corner list replaces detail and compare views`.

### Task 8: Übrige Tabs, Screenshots, Abschluss

**Files:**
- Modify: nach Sichtprüfung einzelne Tabs (nur Stil, keine Aufteilung), `shared/src/desktopTest/kotlin/de/bavaco/gt7/screens/ReadmeScreenshots.kt` (Analyse über `CockpitAnalysis`), `docs/screenshots/*.png`, `README.md` (Analyse-Absatz)

- [ ] **Step 1:** Screenshots rendern (README-Befehl), alle vier ansehen; Lesbarkeit, Kontrast und Überläufe prüfen und beheben.
- [ ] **Step 2:** README-Absatz „Analyse mit Renningenieur“ an das Cockpit anpassen (Kurvenliste, ◀ ▶/Pfeiltasten).
- [ ] **Step 3:** `./gradlew :shared:desktopTest :desktopApp:compileKotlin :androidApp:assembleDebug` grün.
- [ ] **Step 4:** Commit `docs: screenshots and README for the analysis cockpit`, Push.
