# Live-Tipps des Renningenieurs – Design

Stand: 2026-10-22

## Ziel

Während der Fahrt blendet der Renningenieur im Live-Tab vor der nächsten Kurve einen kurzen Tipp ein, der aus der **zuletzt gefahrenen Runde** gegen die Vergleichsrunde stammt, z. B. „Kurve 4 · 180 m – 14 m später bremsen“.

Erfolgskriterien:
- Nach jeder gültigen Runde mit Vergleichsrunde (Live-Referenz) werden die teuersten Kurven (max. 3) für die nächste Runde vorgemerkt.
- Vor einer vorgemerkten Kurve erscheint ein Banner mit Kurvennummer, Entfernung und Hauptursache; es bleibt bis zum Scheitel stehen und verschwindet danach.
- Ein Schalter in den Einstellungen („Live-Tipps“) schaltet die Einblendung ein oder aus (Standard: an). Sprache wie die Tipps in der Analyse.

Nicht-Ziele (später): Sprachausgabe, Tipps in der ersten Runde ohne Vergleich, Tipps aus mehreren Runden gemittelt.

## Ablauf

- `LiveCoach` (`session/LiveCoach.kt`, reine Logik) hält die vorgemerkten `CornerReport`s, sortiert nach Position.
- `SessionController.onLap`: bei gültiger Runde und vorhandener Referenz `Engineer.analyze(lap, referenz)` **vor** dem Ersetzen der Referenz; die `tips` (max. 3) gehen an den Coach.
- Referenzwechsel (Gegner, Strecke, Sitzungsende, Löschen) leert den Coach, weil die Kurvenzonen zur Referenz gehören.
- Pro Frame: `tipAt(distanceM, speedMps)`; Ergebnis als `StateFlow<CoachTip?>`, Entfernung auf 10 m gerundet.

## Zeitpunkt

Anker ist der Bremspunkt der Vergleichsrunde (sonst Zonenanfang). Der Tipp erscheint `speed × 5 s` vorher (mind. 120 m, max. 400 m) und endet am Scheitel.

## Text

`EngineerText.liveTip`: `Kurve 4: 14 m später bremsen` – nur die Hauptursache, ohne Zeitverlust.
