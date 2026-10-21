# Analyse-Vertiefung – Design

Stand: 2026-10-21

## Ziel

Die Analyse holt bei den Funktionen auf, mit denen ApexTrace Runden vergleicht: Kurven mit Nummern, ein Vergleich Kurve für Kurve, Fahrtipps, ein gemeinsamer Cursor über alle Charts, Zoom, Distanz- oder Zeitachse und das Abspielen der Runde auf der Ideallinie.

Grundlage ist das Design des Renningenieurs (`2026-10-17-renningenieur-design.md`). Es gilt unverändert, mit den Ergänzungen und Abweichungen unten.

Erfolgskriterien:
- Nach Auswahl einer Runde zeigt die Analyse die Tipps des Renningenieurs und eine Kurventabelle mit allen Kurven: Zeitverlust, Bremspunkt, Scheitelgeschwindigkeit, Gas am Ausgang und Gang, jeweils Runde gegen Vergleich.
- Kurvennummern stehen auf der Streckenkarte, Kurvenmarken in allen Charts.
- Ein Klick auf Tipp oder Tabellenzeile wählt die Kurve: Die Karte hebt die Zone hervor, die Charts schattieren sie und zoomen auf sie.
- Zeigen oder Tippen in einem Chart setzt einen gemeinsamen Cursor. Alle Charts zeigen dort eine Linie mit den Werten von A und B, die Karte zeigt beide Positionen.
- Zoomen und Verschieben wirken auf alle Charts zugleich. „Gesamt“ setzt den Zoom zurück.
- Die x-Achse ist zwischen Distanz (m) und Zeit (s) umschaltbar.
- Wiedergabe: Play/Pause und ein Schieberegler unter der Karte. A und B fahren jeweils nach eigener Rundenzeit, der Chart-Cursor folgt A.
- Detailanalyse: ein Rundenzeiten-Chart der offenen Sitzung.

Nicht-Ziele: G-Kräfte (GT7 liefert sie nicht, aus x/z abgeleitet wären sie zu ungenau), Export als PNG/CSV/JSON (eigenes Design), Einheiten außer km/h in der Analyse, weitere Sprachen außer den Tipps, Wiedergabe in anderer Geschwindigkeit als 1×.

## Renningenieur – Ergänzungen

### Kurvenbericht

`Engineer.analyze(lap, reference)` liefert zu **jeder** Kurve einen Bericht, nicht nur zu den Tipp-Kurven:

```kotlin
data class CornerReport(
    val corner: Corner,
    val lossMs: Int,
    val brakeM: Float?, val refBrakeM: Float?,
    val apexKmh: Float, val refApexKmh: Float,
    val throttleM: Float?, val refThrottleM: Float?,
    val gear: Int, val refGear: Int,
    val causes: List<Cause>,
)
data class Debrief(val reports: List<CornerReport>, val tips: List<CornerReport>, val totalLossMs: Int)
```

- `tips` sind die Berichte mit Zeitverlust > 50 ms, absteigend nach Verlust, höchstens 5 – wie `Tip` im Renningenieur-Design. `CornerReport` ersetzt `Tip`.
- `reports` stehen in Fahrtrichtung, also nach Kurvennummer.
- Messwerte, die in einer Zone fehlen (z. B. keine Bremsung > 10 %), sind `null`. Für sie gibt es keine Ursache.
- Ursachen gibt es nur bei Zeitverlust (> 0 ms). Eine Kurve, die gleich schnell oder schneller ist, hat keine Ursache – sonst stünde z. B. „mehr Scheitelgeschwindigkeit“ bei einer Kurve, die Zeit gewinnt.
- Die Kurvenzonen enden spätestens beim kürzeren der beiden Distanzbereiche. Kurven, deren Scheitel dahinter liegt, entfallen.

### Vergleichsrunde

- Wie im Renningenieur-Design. Die automatische Vergleichsrunde wird nur für Tipps und Kurventabelle benutzt; die Charts zeigen weiter nur die gewählten Runden.
- `SessionRepository.comparisonLap(trackId, driverId, carCode, excludeLapId): ComparisonLap?` mit `ComparisonLap(lapId, timeMs, driverName, basis)` und `enum Basis { SAME_CAR, SAME_DRIVER, ANY }`.

## Gemeinsamer Analysezustand (`ui/AnalysisViewport.kt`)

Ein Zustand je Rundenpaar, gehalten in `AnalysisScreen` und an beide Layouts (Vergleich, Detailanalyse) gegeben. Ein Wechsel des Layouts behält Cursor, Zoom und Kurvenwahl.

```kotlin
enum class AxisMode { DISTANCE, TIME }   // x in m bzw. s
class AnalysisViewport(a: List<LapSample>, b: List<LapSample>?) {
    var mode: AxisMode
    var range: ClosedFloatingPointRange<Float>   // sichtbarer x-Bereich
    var cursor: Float?                           // x in der aktuellen Achse
    var selectedCorner: Int?
    var playing: Boolean
}
```

Reine Funktionen (testbar ohne UI):

| Funktion | Verhalten |
|---|---|
| `xOf(sample, mode)` | Distanz in m oder Zeit in s |
| `sampleAt(samples, x, mode)` | lineare Interpolation aller Kanäle, Gang vom vorigen Sample; `null` außerhalb der Runde |
| `fullRange(a, b, mode)` | 0 bis zum größten x beider Runden |
| `zoomRange(range, full, anchor, factor, minSpan)` | Zoom um `anchor`, begrenzt auf `full`, mindestens `minSpan` (20 m bzw. 1 s) |
| `panRange(range, full, dx)` | verschieben, an den Grenzen von `full` anhalten |
| `convertRange(range, from, to, a)` | Bereich über Runde A in die andere Achse umrechnen, damit die Ansicht beim Umschalten bleibt |
| `cornerRange(corner, mode, a, full)` | Zone mit 15 % Rand je Seite, in Zeit über Runde A |
| `distanceAtTime(samples, timeMs)` | Umkehrung zu `timeAtDistance` |

- Zeitachse: Jede Runde wird über ihre eigene Rundenzeit gezeichnet. Der Cursor liest beide Runden zur selben Zeit. Die Delta-Kurve liegt bei der Zeit, zu der A die jeweilige Distanz erreicht.
- Kurvenmarken und Kurvenzonen sind Distanzen. Auf der Zeitachse stehen sie dort, wo A die Distanz erreicht.
- Neue Runden (A oder B geändert): Zustand neu, voller Bereich, kein Cursor, keine Kurve, Wiedergabe aus.
- Wählt man eine Kurve, zoomen alle Charts auf `cornerRange`. Ein zweiter Klick hebt die Wahl auf, der Zoom bleibt.

## Wiedergabe (`ui/Playback.kt`)

- Uhr `tMs` von 0 bis zur längeren der beiden Rundenzeiten; Fortschritt per `withFrameMillis`, Geschwindigkeit 1×.
- `playbackFrame(a, b, tMs)`: Position von A zur Zeit `min(tMs, Ende A)`, ebenso B. Wer fertig ist, bleibt an der Ziellinie stehen. Dazu die Distanz von A für den Cursor.
- Der Cursor folgt A: auf der Distanzachse über `distanceAtTime`, auf der Zeitachse `tMs / 1000`.
- Am Ende hält die Wiedergabe an. Play beginnt am Ende wieder bei 0.
- Den Cursor von Hand zu bewegen hält die Wiedergabe an. Der Schieberegler setzt `tMs`.

## Charts (`ui/Charts.kt`)

`LineChart` bekommt optional den Viewport (ohne ihn bleibt der Chart statisch, z. B. für die Rundenzeiten).

- Gezeichnet wird nur der sichtbare Bereich; die x-Achse folgt `range`.
- Cursor als senkrechte Linie, oben rechts die Werte von A und B an der Cursorstelle.
- Kurvenmarken: dünne Linie am Scheitel mit der Nummer oben; die gewählte Zone ist schattiert.
- Bedienung:
  - Maus über dem Chart (Desktop) und Tippen setzen den Cursor; waagerechtes Ziehen bewegt ihn.
  - Strg/⌘ + Mausrad zoomt um die Mausposition; Shift + Mausrad und waagerechtes Scrollen verschieben. Das normale Mausrad bleibt beim Scrollen der Seite.
  - Zwei Finger: Spreizen zoomt, gemeinsames Verschieben verschiebt.
  - Knöpfe über den Charts: Distanz/Zeit, „+“, „−“, „Gesamt“.

## Karte

`RaceLine` (Detailanalyse) und `TrackMap` (Vergleich):
- Kurvennummern neben den Scheiteln (Position von A).
- Die gewählte Zone hervorgehoben (breite helle Linie über A).
- Punkte für A und B an der Cursorstelle bzw. während der Wiedergabe.
- Darunter die Wiedergabeleiste: Play/Pause, Schieberegler, Zeit.

## Renningenieur-Panel und Kurventabelle (`ui/EngineerPanel.kt`)

- Kopf: „Renningenieur“, Vergleichsbasis („verglichen mit: Max · 1:42.318 · gleiches Auto“), Gesamtverlust.
- Tipps als klickbare Zeilen (Text aus `EngineerText`), die gewählte Kurve hervorgehoben.
- Kurventabelle: `Kurve | Verlust | Bremspunkt | Scheitel | Gas | Gang`, je Zelle „A / Vergleich“, Verlust rot/grün. Bremspunkt und Gas als Abweichung in m („+14 m“), Scheitel in km/h, Gang „3 / 4“.
- Kompakt (Telefon): `Kurve | Verlust | Hauptursache`; Tippen klappt die übrigen Werte auf.
- Detailanalyse: Panel und Tabelle in der rechten Spalte über der Ideallinie. Vergleich: rechte Spalte (320 dp) neben Charts und Karte. Kompakt: unter der Analyse.
- Berechnung auf `Dispatchers.Default`, neu bei Rundenwechsel; bis dahin „Rechne…“.

## Rundenzeiten-Chart

In der Detailanalyse über der Rundentabelle: gültige Runden der offenen Sitzung, x = Rundennummer, y = Zeit in s. Bei weniger als zwei gültigen Runden entfällt der Chart.

## Einstellungen

Abschnitt „Renningenieur“ mit Sprachwahl Deutsch / English (Einstellung `tipLanguage`), wie im Renningenieur-Design.

## Tests

- **Renningenieur:** alle Tests aus dem Renningenieur-Design. Zusätzlich: Berichte auch für Kurven unter 50 ms; Messwerte `null` ohne Bremsung; Kurven hinter dem kürzeren Distanzbereich entfallen.
- **Viewport:** `sampleAt` nach Distanz und Zeit (Interpolation, Gang, außerhalb = null), `zoomRange` (Anker bleibt, Grenzen, Mindestspanne), `panRange` (Grenzen), `convertRange` hin und zurück, `cornerRange` mit Rand, `distanceAtTime`.
- **Wiedergabe:** Positionen von A und B zur Zeit t, wenn B langsamer ist; A bleibt nach seinem Ende an der Ziellinie; Cursor-Distanz folgt A.
- **Repository:** `comparisonLap` – Reihenfolge gleiches Auto > gleicher Fahrer > beliebig, gewählte Runde und ungültige Runden ausgeschlossen, andere Strecken ausgeschlossen.
- **Text:** jede Ursache in DE und EN, Zahlenformat, Vergleichsbasis.
