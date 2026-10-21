# Analyse-Cockpit im Motorsport-Stil – Design

Stand: 2026-10-21

## Ziel

Die Analyse wird übersichtlicher, lesbarer und optisch zur Renn-App: ein Cockpit, das das Wichtige ohne Scrollen zeigt, eine Kurvenliste zum Durchgehen aller Kurven und ein Motorsport-Stil, der für die ganze App gilt.

Entschieden im Brainstorming (Skizzen im Browser):
- Aufteilung **Cockpit** (Kernzahlen oben, Karte und Kurven in der Mitte, Diagramme darunter) mit **Kurvenliste rechts neben der Karte**.
- Stil **Motorsport**: Carbon-Schwarz, rote Akzente, schmale Großbuchstaben, Bestwerte violett.
- Der Stil gilt **in der ganzen App als Grundlage** (Schrift, Farben, Bedienelemente). Neu aufgeteilt wird nur die Analyse.

Erfolgskriterien:
- Auf einem Desktop-Fenster ab 1280 × 800 sind Kopfzeile, Karte, Kurvenliste und das Delta-Diagramm ohne Scrollen sichtbar.
- Alle Kurven lassen sich der Reihe nach durchgehen (Klick, ◀ ▶, Pfeiltasten); jede Auswahl zoomt Karte und Diagramme.
- Außerhalb von `Theme.kt` stehen keine fest eingetragenen Farbwerte mehr.
- Die README-Screenshots zeigen den neuen Stil.

Nicht-Ziele: neue Aufteilung von Live-Dashboard, Sitzungen und übrigen Tabs; helle Themes oder Theme-Auswahl; neue Messgrößen oder Logik in Renningenieur, Viewport oder Export.

## Teil 1 – Grundlagen (ganze App)

### Schrift

- **Barlow Condensed** (SIL Open Font License 1.1) als Compose-Ressource in `shared/src/commonMain/composeResources/font/`, Schnitte Regular, Medium, SemiBold, Bold; Lizenzdatei `OFL.txt` daneben.
- Die Material-Typografie in `Gt7Theme` nutzt sie für alle Stile.
- Zahlen (Zeiten, Tempo, Tabellen) in Barlow Condensed mit Ziffern fester Breite (`fontFeatureSettings = "tnum"`) statt Monospace; `digits(size)` wird entsprechend umgestellt.

### Farben (`Theme.kt`)

Ein Objekt mit festen Namen ersetzt die losen Konstanten (`GoodGreen`, `BadRed`, `LapAColor` …), die an ihren bisherigen Stellen auf die neuen Namen zeigen:

| Name | Wert | Verwendung |
|---|---|---|
| `Carbon` | `#0A0A0A` | Hintergrund, mit feiner diagonaler Textur |
| `Surface` | `#141414` | Flächen, Karten |
| `SurfaceRaised` | `#1F1F1F` | gewählte Zeilen, Hover |
| `Line` | `#262626` | Gitter, Trennlinien |
| `TextPrimary` / `TextMuted` | `#F2F2F2` / `#9A9A9A` | Text, Beschriftungen |
| `Accent` | `#E10600` | Akzentkanten, aktive Schalter, Auswahl |
| `LapA` / `LapB` | `#FFD400` / `#00D2FF` | Runde A / B |
| `Loss` / `Gain` | `#E10600` / `#00E676` | Zeitverlust / -gewinn |
| `Best` | `#B14CFF` | Bestwerte (theoretische Bestzeit, beste Sektoren) |
| `Warn` | `#FFC107` | Warnungen, Referenz-Stern |

- Das Material-Farbschema (`darkColorScheme`) wird aus diesen Farben gebildet (background, surface, primary = Accent, …).
- Alle bisher fest eingetragenen Farben (rund 38 in 10 Dateien) wandern auf diese Namen.

### Bausteine (`ui/RaceStyle.kt`, neu)

- `RaceCard(accent: Color? = Accent)`: Fläche `Surface` mit 3 dp breiter Kante links in der Akzentfarbe (ohne Akzent: ohne Kante), Ecken 2 dp.
- `RaceLabel(text)`: kleine Großbuchstaben, Laufweite 0,14 em, `TextMuted`.
- `RaceNumber(text, color, size)`: große Zahl in Barlow Condensed Bold mit festen Ziffern.
- Schalter und Knöpfe: kantig (Ecken 2 dp), aktiv rot gefüllt, inaktiv mit `Line`-Rand. Umgesetzt über die Material-Shapes und -Farben im Theme, damit bestehende `FilterChip`/`Button` ohne Umbau folgen.

### Übrige Tabs

Live, Renn-Display, Sitzungen, Bestenliste, Strecken, Autos und Einstellungen behalten ihre Aufteilung und erhalten Schrift, Farben und Bedienelemente aus dem Theme. Das Renn-Display hat den Stil schon fast und übernimmt nur die Farbnamen.

## Teil 2 – Analyse-Cockpit (`ui/CockpitAnalysis.kt`, neu)

Ersetzt die Detailanalyse (`DetailAnalysis.kt`), die Vergleichsansicht (`CompareView` in `AnalysisScreen.kt`) und den Umschalter zwischen beiden. Die Einstellung `analysisLayout` wird nicht mehr gelesen.

### Kopfzeile

- Vier `RaceCard`s:
  - **Runde A** (`LapA`): Rundenzeit, darunter Fahrer · Auto.
  - **Runde B** bzw. **Vergleich** (`LapB`): gewählte Runde B; ohne B die automatische Vergleichsrunde mit ihrer Basis („Referenzrunde“, „gleiches Auto“ …); ohne Vergleich „–“.
  - **Differenz**: Gesamtverlust A gegen B bzw. Vergleich, `Loss` oder `Gain`.
  - **Theoretisch** (`Best`): theoretische Bestzeit der Sitzung von A; „–“ ohne vollständige Sektoren.
- Rechts daneben: Distanz/Zeit, +, −, Gesamt, CSV…, JSON…, PNG… als kompakte kantige Knöpfe.

### Mitte: Karte und Kurvenliste

- **Karte** (etwa 60 % Breite): Ideallinien A und B, Kurvennummern („T8“), gewählte Kurve hervorgehoben (Accent-Kreis), Punkte von A und B am Cursor bzw. bei der Wiedergabe; die Wiedergabeleiste direkt darunter.
- **Kurvenliste** (`ui/CornerList.kt`, neu), rechts:
  - Jede Kurve in Fahrtrichtung: Nummer, Verlust-Balken (Länge relativ zur teuersten Kurve, `Loss`; schnellere Kurven `Gain`), Verlust in s.
  - Die drei teuersten Kurven mit Zeitverlust tragen eine rote Marke.
  - Die gewählte Kurve klappt auf: Tipp als Satz (`EngineerText`), dazu Bremspunkt, Scheitel A/B, Gas, Gang.
  - Kopf mit ◀ ▶. Tastatur, solange die Analyse den Fokus hat: ↓ und → nächste Kurve, ↑ und ← vorige Kurve.
  - Auswahl über `AnalysisViewport.toggleCorner` (zoomt Karte und Diagramme wie bisher).
  - Ohne Vergleichsrunde oder Kurven: der passende Hinweis aus `EngineerText` an Stelle der Liste.

### Unten: Diagramme

- Immer sichtbar und größer: **Delta**, **Tempo**, **Gas/Bremse** in einem Diagramm (Gas `Gain`, Bremse `Loss`; A durchgezogen, B gestrichelt).
- Eingeklappt unter „Weitere Kanäle“: Gang, Drehzahl, Ladedruck, Rollen. Der Zustand gilt pro App-Lauf.
- Alle Diagramme behalten gemeinsamen Cursor, Zoom und Kurvenmarken; die Marken heißen „T1“ … „Tn“.
- Diagramm-Stil: Gitter `Line`, Beschriftungen `TextMuted` in Barlow Condensed, Titel als `RaceLabel`.

### Ganz unten: Sitzung

- Rundenzeiten-Diagramm und Rundentabelle (Vmax, Sektoren, beste Sektoren `Best`, theoretische Bestzeit) wie bisher, im neuen Stil.
- Die Listen „Spitzen/Täler“ entfallen.

### Telefon (kompakt)

Untereinander: Kopfzeile als 2 × 2 Karten, Karte, Kurvenliste, Diagramme, Sitzung. Die Kurvenliste zeigt Nummer, Verlust und Hauptursache; Antippen klappt die Details auf.

## Teil 3 – Logik und Tests

Neue reine Funktionen (in `ui/CornerList.kt` bzw. `ui/CockpitAnalysis.kt`):

| Funktion | Verhalten |
|---|---|
| `stepCorner(corners, selected, direction)` | nächste/vorige Kurve; ohne Auswahl die erste bzw. letzte; hält am ersten und letzten Eintrag an |
| `costliestCorners(reports, count = 3)` | Nummern der teuersten Kurven mit Verlust > 0, höchstens `count` |
| `lossBarFraction(report, reports)` | Balkenlänge 0–1 relativ zum größten Betrag |
| `cockpitHeader(lapA, pickedB, engineerState, sessionStats)` | Werte der vier Kopfzeilen-Karten, für gewählte Runde B, automatische Vergleichsrunde und fehlenden Vergleich |

Tests:
- `stepCorner`: vorwärts, rückwärts, Anfang/Ende, ohne Auswahl, leere Liste.
- `costliestCorners`: nur Kurven mit Verlust, Sortierung, weniger als drei verlustreiche Kurven.
- `lossBarFraction`: relative Länge, Gewinne, alle null.
- `cockpitHeader`: drei Fälle (B gewählt, automatische Vergleichsrunde, kein Vergleich), Theoretisch mit und ohne Sektoren.
- Farben: ein Test durchsucht `shared/src/commonMain/kotlin/de/bavaco/gt7/ui/*.kt` außer `Theme.kt` nach `Color(0x` und erwartet keinen Treffer.
- Bestehende Tests bleiben grün.
- Sichtprüfung: Screenshots über `ReadmeScreenshots` neu rendern (Analyse, Live, Renn-Display, Sitzungen), ansehen, README-Bilder ersetzen.
- Von Hand (Nutzer): Pfeiltasten und Aufklappen in der laufenden App.
