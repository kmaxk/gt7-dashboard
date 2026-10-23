# Fahrdynamik-Analyse – Design

Stand: 2026-10-23

## Ziel

Die Analyse wertet aus, wie gebremst, eingelenkt und herausbeschleunigt wird: Trail Braking und Bremslösen, Gasannahme relativ zum Lenkwinkel, Scheitelgeschwindigkeit, Unter- und Übersteuern, Schlupf, Gierrate und Querbeschleunigung. Der Renningenieur nennt daraus neue Ursachen pro Kurve; neue Diagramme zeigen die Kanäle zum Selbst-Nachsehen.

Grundlage sind die Designs des Renningenieurs (`2026-10-17-renningenieur-design.md`) und der Analyse-Vertiefung (`2026-10-21-analyse-vertiefung-design.md`). Sie gelten unverändert, mit den Ergänzungen unten.

Erfolgskriterien:
- Die App fordert von GT7 Pakete vom Typ „B“ an und speichert pro Messpunkt zusätzlich Lenkwinkel, Gierrate, Quer- und Längsbeschleunigung sowie Schlupf vorne und hinten.
- Für jede Kurve misst der Renningenieur Bremslösepunkt, Auslaufzeit der Bremse, Trail-Braking-Strecke, Lenkeinschlag beim Gasgeben, Unter-/Übersteuern, Blockieren, Durchdrehen und Querbeschleunigung am Scheitel – für Runde und Vergleichsrunde.
- Daraus entstehen neue Ursachen in Tipps, Kurventabelle, Live-Tipps und Sprachausgabe, auf Deutsch und Englisch.
- Die Analyse zeigt „Lenkung (°)“ immer und „Gierrate“, „Querbeschleunigung“, „Schlupf“ unter „Fahrdynamik“.
- Alte Runden (nur Paket A) bleiben voll nutzbar: Bremslösen und Auslaufen werden auch für sie ausgewertet; alles, was Lenkung oder Dynamik braucht, entfällt mit einem Hinweis.

Nicht-Ziele: Pakettyp „~“ (gefilterte Pedale, Energierückgewinnung), Reifenverschleiß oder -druck (liefert GT7 nicht), absolute Fahrzeugmodelle mit Radstand oder Lenkübersetzung (liefert GT7 nicht), Auswertung über mehrere Runden gemittelt, neue Live-Anzeigen außer den Tipps.

## Daten

### Paket B

GT7 antwortet auf den Heartbeat „B“ mit 316 Byte statt 296. Die ersten 296 Byte sind das bekannte A-Paket, danach folgen ([PDTools `SimulatorPacket`](https://github.com/Nenkai/PDTools)):

| Offset | Feld | Typ |
|---|---|---|
| 0x128 | Lenkradwinkel (rad) | float |
| 0x12C | Füllwert | float |
| 0x130 | Sway – Querbeschleunigung | float |
| 0x134 | Heave – Vertikalbeschleunigung | float |
| 0x138 | Surge – Längsbeschleunigung | float |

Die Entschlüsselung ist dieselbe wie bei A, nur mit der IV-Maske `0xDEADBEEF` statt `0xDEADBEAF`. Die Gierrate steht schon im A-Paket: Winkelgeschwindigkeit um die Hochachse (y) bei 0x30, Teil des Vektors ab 0x2C.

- `UdpTelemetrySource` und `Discovery` senden „B“ statt „A“.
- `PacketDecoder.decode` nimmt 296 und 316 Byte an und wählt die Maske nach der Länge. Alte Mitschnitte (nur A) bleiben abspielbar.
- **Rückfall:** Kommt nach dem Heartbeat „B“ fünf Sekunden lang kein gültiges Paket, sendet die Quelle wieder „A“ und meldet das im Log.
- **Prüfung vor dem Weiterbauen:** Ein kurzer Mitschnitt an der PS5 bestätigt Länge, Vorzeichen und Einheiten: Lenkung links/rechts, Sway in m/s², Gierrate in rad/s. Vorzeichen, die nicht zur Annahme passen, werden im Decoder angeglichen, sodass „links“ überall positiv ist.

### TelemetryFrame

Neue Felder, null ohne B-Daten:

```kotlin
val steeringRad: Float? = null,   // Lenkrad, links positiv
val yawRate: Float? = null,       // rad/s, links positiv; aus dem A-Teil, daher immer gesetzt
val latAccel: Float? = null,      // m/s², Sway
val longAccel: Float? = null,     // m/s², Surge
```

`yawRate` ist auch bei A-Paketen gesetzt, wird aber erst ab dieser Version gespeichert.

### Gespeicherte Messpunkte

`LapSample` und `lap_sample` bekommen sechs Werte, die leer sein dürfen (Migration `5.sqm`, `ALTER TABLE … ADD COLUMN`):

| Spalte | Bedeutung |
|---|---|
| `steering_rad` | Lenkradwinkel |
| `yaw_rate` | Gierrate rad/s |
| `lat_accel` | Querbeschleunigung m/s² |
| `long_accel` | Längsbeschleunigung m/s² |
| `slip_front` | Schlupf Vorderachse, Mittel der beiden Räder |
| `slip_rear` | Schlupf Hinterachse |

Schlupf wird beim Messen mit `wheelSlip` aus Raddrehzahl, Radradius und Geschwindigkeit berechnet, wie heute live; unter 10 km/h bleibt er leer. Der Datenexport und -import nimmt die neuen Spalten mit; Exportdateien älterer Versionen werden mit leeren Werten eingelesen. Der CSV-Export einzelner Runden bekommt die Spalten ebenfalls.

## Auswertungen

Neues Modul `session/Dynamics.kt`: reine Funktionen, die zu einer Kurve aus den Messpunkten einer Runde die Kennzahlen bilden. `Engineer.measure` ruft es auf; `CornerReport` bekommt die Kennzahlen für Runde und Vergleich, `Cause` die neuen Ursachen. Neue Ursachen entstehen nur, wenn die Kurve Zeit kostet (wie bisher ab 50 ms) und der Unterschied die Toleranz übersteigt.

### Kennzahlen pro Kurve

| Kennzahl | Messung | braucht B |
|---|---|---|
| Bremslösepunkt | letzte Stelle in der Bremsphase vor dem Scheitel, an der die Bremse unter 5 % fällt (m) | nein |
| Auslaufzeit | Zeit von 80 % des Spitzendrucks bis zum Lösen (ms) | nein |
| Einlenkpunkt | erste Stelle in der Kurvenzone mit mehr als 15 % des größten Lenkwinkels der Kurve | ja |
| Trail-Braking-Strecke | Strecke mit Bremse über 5 % nach dem Einlenkpunkt; ohne B: Strecke mit Bremse über 5 % in den letzten 30 % vor dem Scheitel | nein (genauer mit B) |
| Lenkeinschlag beim Gasgeben | Lenkwinkel an der Stelle, an der das Gas 80 % erreicht, in % des größten Lenkwinkels der Kurve | ja |
| Lenkgrad | Mittel von \|Lenkwinkel\| / \|Gierrate\| zwischen Einlenkpunkt und Scheitel | ja |
| Gegenlenken | längste Dauer, in der Lenkung und Gierrate verschiedene Vorzeichen haben (\|Lenkung\| > 2°, \|Gierrate\| > 0,2 rad/s) | ja |
| Gierüberschuss am Ausgang | Mittel von \|Gierrate\| / \|Lenkwinkel\| zwischen Scheitel und Ausgang | ja |
| Querbeschleunigung am Scheitel | Betrag am Scheitel, in g | ja |
| Blockieren | kleinster Schlupf vorne unter Bremse | ja |
| Durchdrehen | größter Schlupf hinten bei Gas über 50 % | ja |

### Neue Ursachen

| Ursache | Bedingung | Tipp (DE) |
|---|---|---|
| `ReleaseEarly(m)` | Bremse mehr als 10 m früher gelöst als im Vergleich | „Bremse 12 m früher gelöst – länger mit in die Kurve nehmen“ |
| `ReleaseLate(m)` | mehr als 10 m später gelöst und Scheitel langsamer | „Bremse 12 m später gelöst – früher lösen“ |
| `ReleaseAbrupt(ms, refMs)` | Auslaufzeit unter der Hälfte des Vergleichs und mindestens 150 ms kürzer | „Bremse abrupt gelöst (0,1 s statt 0,4 s) – weicher auslaufen lassen“ |
| `LockUp` | Schlupf vorne unter −15 % | „Vorderräder blockieren beim Bremsen“ |
| `Understeer` | Lenkgrad mehr als 25 % über dem Vergleich | „Untersteuern am Eingang – später einlenken oder mehr Tempo abbauen“ |
| `GripUnused(g, refG)` | Querbeschleunigung am Scheitel mindestens 0,1 g unter dem Vergleich | „1,3 g statt 1,5 g am Scheitel – Grip nicht ausgenutzt“ |
| `ThrottleOnLock(pct, refPct)` | mindestens 20 Prozentpunkte mehr Lenkeinschlag beim Gasgeben als im Vergleich | „Vollgas bei 70 % Lenkung (Vergleich 40 %) – erst Lenkung öffnen“ |
| `ThrottleLate(pct, refPct)` | mindestens 20 Prozentpunkte weniger Lenkeinschlag beim Gasgeben und Gas später als im Vergleich | „Gas erst bei 20 % Lenkung (Vergleich 50 %) – früher ans Gas“ |
| `Oversteer(countersteer)` | Gegenlenken mindestens 0,2 s, oder Gierüberschuss mehr als 25 % über dem Vergleich | „Übersteuern am Ausgang (gegengelenkt) – Gas sanfter“ |
| `Wheelspin` | Schlupf hinten über 15 % | „Räder drehen durch am Ausgang“ |

`ThrottleLate` ergänzt das bestehende `LateThrottle` (Meter): Liegen beide vor, wird nur `ThrottleLate` genannt, weil es den Grund mitliefert. `LockUp`, `Wheelspin` und Gegenlenken sind absolute Befunde und brauchen keinen Vergleich; alle anderen vergleichen mit der Vergleichsrunde.

**Reihenfolge** der Ursachen nach Kurvenphase: Bremsen (`BrakeEarly`, `BrakeLate`, `ReleaseEarly`, `ReleaseLate`, `ReleaseAbrupt`, `LockUp`), Scheitel (`SlowApex`, `OverspeedApex`, `Understeer`, `GripUnused`), Ausgang (`LateThrottle`/`ThrottleLate`, `ThrottleOnLock`, `Oversteer`, `Wheelspin`), Gang. Der Tipp nennt die erste, die übrigen in Klammern.

**Schwellen** stehen als Konstanten in `Dynamics.kt`. Sie gelten als vorläufig und werden an den ersten B-Runden der PS5 abgeglichen, bevor das Feature als fertig gilt.

## Darstellung

### Diagramme

- **„Lenkung (°)“** – immer sichtbar, direkt unter „Gas / Bremse“. Lenkradwinkel in Grad, A gelb, B cyan.
- **„Fahrdynamik“** – aufklappbar wie „Weitere Kanäle“, mit:
  - „Gierrate (°/s)“
  - „Querbeschleunigung (g)“
  - „Schlupf (%)“ – Vorderachse durchgezogen, Hinterachse gestrichelt, A gelb, B cyan
- Hat eine Runde keine B-Daten, fehlen ihre Linien; hat keine der beiden welche, zeigt das Diagramm „Keine Lenk- und Dynamikdaten – Runde vor Version 0.6 aufgezeichnet“.

### Kurventabelle

- Die Detailzeile ergänzt: „Lösen −12 m · Auslaufen 0,1 / 0,4 s · Gas bei 70 / 40 % Lenkung“. Fehlende Werte als „–“.
- Kurven mit `Understeer` zeigen „US“, mit `Oversteer` „ÜS“ als kleines Kürzel in `RaceColors.Warn` in der eingeklappten Zeile.

### Tipps, live und gesprochen

- `EngineerText.cause` bekommt die neuen Ursachen auf Deutsch und Englisch, `liveTip` und `spoken` nutzen sie ohne Änderung. Zahlen mit Einheiten (g, %, s) werden für die Sprachausgabe ausgeschrieben.
- Die Live-Tipps erhalten die neuen Ursachen automatisch, weil sie auf denselben Kurvenberichten beruhen.

## Tests

- `PacketDecoderTest`: A-Paket (296 Byte) und B-Paket (316 Byte) mit bekannten Werten; falsche Länge ergibt null.
- `UdpTelemetrySource`: Rückfall auf „A“, wenn fünf Sekunden kein gültiges Paket kommt.
- `MigrationTest`: Datenbank vor Migration `5.sqm` wird gehoben, alte Messpunkte haben leere neue Werte.
- `DataTransferTest`: Export und Import mit neuen Spalten; Import einer Exportdatei ohne die neuen Spalten.
- `DynamicsTest`: konstruierte Runden für jede Kennzahl – früh gelöste Bremse, abruptes Lösen, Gegenlenken, Durchdrehen, Blockieren, Untersteuern, Lenkeinschlag beim Gasgeben; Runden ohne B-Daten liefern leere Kennzahlen, aber Bremslösen und Auslaufen.
- `EngineerTest`: neue Ursachen nur bei Zeitverlust, richtige Reihenfolge, `ThrottleLate` ersetzt `LateThrottle`.
- `EngineerTextTest`: jede neue Ursache auf Deutsch und Englisch, gesprochen mit ausgeschriebenen Einheiten.
- Abgleich an der PS5: Mitschnitt einiger Runden, Vorzeichen und Schwellen prüfen, README-Screenshot der Analyse mit Lenkung erneuern.
