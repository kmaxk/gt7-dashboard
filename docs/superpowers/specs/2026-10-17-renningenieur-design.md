# Renningenieur – Design

Stand: 2026-10-17

## Ziel

Im Analyse-Tab erklärt ein „Renningenieur“, **wo und warum** eine Runde Zeit gegenüber einer Vergleichsrunde verliert – als kurze, konkrete Fahrtipps auf Deutsch oder Englisch.

Erfolgskriterien:
- Nach Auswahl einer Runde zeigt die Analyse bis zu 5 Tipps mit Kurve, Zeitverlust und Ursache, z. B. „Kurve 4: 14 m später bremsen – kostet 0,21 s“.
- Ein Klick auf einen Tipp markiert die Kurve auf der Streckenkarte und den Abschnitt in den Charts.
- Die Sprache der Tipps ist in den Einstellungen umschaltbar (Deutsch / English); der Rest der App bleibt deutsch.

Nicht-Ziele: Live-Tipps während der Fahrt, Sprachausgabe, Texte von einem Sprachmodell, Übersetzung der übrigen App, benannte Kurven (z. B. „Spoon“), Setup-Empfehlungen, Reifenverschleiß (liefert GT7 nicht).

## Vergleichsrunde

- Zwei Runden gewählt: A wird mit B verglichen (wie der bestehende A/B-Vergleich).
- Eine Runde gewählt: Vergleich mit der schnellsten gültigen Runde auf derselben Strecke **außer der gewählten** – zuerst gleicher Fahrer und gleiches Auto, sonst gleicher Fahrer, sonst irgendein Fahrer. Gibt es keine andere gültige Runde auf der Strecke, zeigt das Panel „Keine Vergleichsrunde – fahr eine zweite Runde oder wähle zwei Runden.“
- Runden ohne Strecke: nur A/B-Vergleich möglich.
- Das Panel nennt die Basis, z. B. „verglichen mit: Max · 1:42.318 · gleiches Auto“.

Neue Repository-Abfrage `comparisonLap(trackId, driverId, carCode, excludeLapId)` mit dieser Reihenfolge.

## Analyse (`session/Engineer.kt`, reine Logik)

### Kurven

- Kurven stammen aus der **Vergleichsrunde**: jedes Geschwindigkeitsminimum aus `peaksAndValleys` ist ein Scheitel.
- Eine Kurvenzone reicht vom Geschwindigkeitsmaximum davor bis zum Maximum danach; Anfang und Ende der Runde begrenzen die erste und letzte Zone.
- Nummerierung in Fahrtrichtung, „Kurve 1…n“. Da die Zonen aus der Vergleichsrunde kommen, behält eine Kurve ihre Nummer, solange dieselbe Vergleichsrunde gilt.
- Beide Runden werden über die zurückgelegte Distanz ab Startlinie ausgerichtet (wie `deltaCurve` / `timeAtDistance`).

### Messgrößen je Kurve (Runde vs. Vergleich)

| Größe | Definition |
|---|---|
| Zeitverlust | Delta am Zonenende minus Delta am Zonenanfang |
| Bremspunkt | erste Distanz in der Zone vor dem Scheitel mit Bremse > 10 % |
| Scheitelgeschwindigkeit | Minimum der Geschwindigkeit in der Zone |
| Gas am Ausgang | erste Distanz nach dem Scheitel mit Gas > 80 % |
| Gang am Scheitel | Gang beim Geschwindigkeitsminimum |

### Ursachen und Schwellen

| Ursache | Bedingung |
|---|---|
| `BrakeEarly` / `BrakeLate` | Bremspunkt weicht > 5 m ab |
| `SlowApex` | Scheitel > 3 km/h langsamer |
| `OverspeedApex` | Scheitel > 3 km/h schneller, Kurve kostet trotzdem Zeit |
| `LateThrottle` | Gas am Ausgang > 10 m später |
| `GearDiffers` | anderer Gang am Scheitel |

Je Kurve werden alle zutreffenden Ursachen ermittelt. Die Hauptursache ist die erste in dieser Reihenfolge: Bremspunkt, Scheitel, Gas, Gang. Die übrigen werden als Zusatz angezeigt.

### Ergebnis

```kotlin
data class Corner(val number: Int, val startM: Float, val apexM: Float, val endM: Float)
data class Tip(val corner: Corner, val lossMs: Int, val causes: List<Cause>)
sealed interface Cause { /* BrakeEarly(meters), BrakeLate(meters), SlowApex(kmh), OverspeedApex(kmh), LateThrottle(meters), GearDiffers(lap, reference) */ }
data class Debrief(val tips: List<Tip>, val totalLossMs: Int)
```

- Nur Kurven mit Zeitverlust > 50 ms; Kurven ohne erkennbare Ursache erscheinen mit „Zeitverlust ohne klare Ursache – Linie prüfen“.
- Höchstens 5 Tipps, absteigend nach Zeitverlust.
- Keine Kurve über der Schwelle: leere Tippliste; die UI zeigt „Stark – überall gleich schnell oder schneller.“
- Zu kurze Runden (< 2 Samples) oder Vergleich ohne Kurven: `Debrief` mit leerer Liste und Hinweis.

## Sprache (`ui/EngineerText.kt`)

- `enum class TipLanguage { DE, EN }`, gespeichert als Einstellung `tipLanguage`, Standard DE.
- `EngineerText.tip(tip, language)`, `EngineerText.basis(...)`, `EngineerText.allGood(language)` erzeugen die Sätze; Zahlen werden sprachgerecht formatiert (DE „0,21 s“, EN „0.21 s“).
- Beispiele:
  - DE: „Kurve 4: 14 m später bremsen – kostet 0,21 s“, „Kurve 7: 6 km/h mehr Scheitelgeschwindigkeit – kostet 0,15 s“, „Kurve 2: früher ans Gas, 18 m später als die Vergleichsrunde – kostet 0,09 s“
  - EN: „Turn 4: brake 14 m later – costs 0.21 s“, „Turn 7: carry 6 km/h more through the apex – costs 0.15 s“, „Turn 2: get on the throttle earlier, 18 m later than the reference – costs 0.09 s“

## UI

- Analyse: Panel „Renningenieur“ unterhalb der Rundenauswahl, in beiden Analyse-Layouts. Inhalt: Vergleichsbasis, Tipps, ggf. Hinweis.
- Ein Klick auf einen Tipp wählt die Kurve aus: Die Streckenkarte hebt die Zone (Start bis Ende) hervor, die Charts schattieren den Distanzbereich. Ein zweiter Klick hebt die Auswahl auf.
- Einstellungen: Abschnitt „Renningenieur“ mit Sprachwahl Deutsch / English.
- Die Berechnung läuft auf `Dispatchers.Default` und wird bei Rundenwechsel neu gestartet.

## Tests

- **Kurvenerkennung:** synthetische Runde mit drei Bremszonen ergibt drei Kurven mit plausiblen Grenzen; Nummerierung in Fahrtrichtung.
- **Ursachen** mit einer Vergleichsrunde und einer gezielt veränderten Runde:
  - 15 m früher bremsen → `BrakeEarly(15)` als Hauptursache.
  - Scheitel 6 km/h langsamer → `SlowApex`.
  - Gas 20 m später → `LateThrottle`.
  - Scheitel schneller und trotzdem langsamer → `OverspeedApex`.
  - anderer Gang → `GearDiffers` als Zusatz.
- **Schwellen:** 4 m Bremspunkt-Abweichung und 40 ms Verlust erzeugen keinen Tipp.
- **Sortierung und Begrenzung:** 7 verlustreiche Kurven ergeben 5 Tipps, absteigend.
- **Alles schneller:** leere Liste.
- **Text:** jede Ursache in DE und EN, Zahlenformat.
- **Vergleichsrunde (Repository):** Reihenfolge gleicher Fahrer + Auto > gleicher Fahrer > beliebig; die gewählte Runde wird ausgeschlossen.
