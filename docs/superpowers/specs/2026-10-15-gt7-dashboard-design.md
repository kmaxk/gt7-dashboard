# GT7 Dashboard – Design

Stand: 2026-10-15

## Ziel

Eine Kotlin-Multiplatform-App (zuerst macOS), die die Telemetrie von Gran Turismo 7 von einer PS5 im lokalen Netz empfängt und
1. **live** beim Fahren auf einem zweiten Bildschirm anzeigt und
2. gefahrene Runden **speichert und analysiert** (auch sitzungsübergreifend auf derselben Strecke).

Erfolgskriterien:
- Mac verbindet sich mit der PS5 (Discovery oder manuelle IP) und zeigt Live-Werte mit ~60 Hz ohne Ruckeln.
- Abgeschlossene Runden landen persistent in einer lokalen DB; zwei Runden (auch aus unterschiedlichen Sitzungen) lassen sich vergleichen.
- Sitzungen werden ihrer Strecke halbautomatisch zugeordnet.
- Entwicklung und Tests sind ohne PS5 möglich (Mitschnitt + Replay).

Nicht-Ziele (v1): Export, Setup-Notizen, mehrere PS5 gleichzeitig, fremde Fahrer, Reifendruck/-verschleiß (liefert GT7 nicht), Öl-/Wasseranzeige.

## Plattformen & Technik

- Kotlin Multiplatform, UI mit **Compose Multiplatform**.
- v1: Desktop-JVM-Target, ausgeliefert als macOS-App. Später Android und iOS (iPad als Zweitbildschirm) – deshalb liegt alle Logik und UI in `commonMain`.
- UDP: **Ktor Network Sockets** (JVM/Android/iOS).
- Salsa20: eigene Implementierung in `commonMain`.
- Persistenz: **SQLDelight**.
- Diagramme: eigene Compose-`Canvas`-Zeichnung, keine Chart-Library.

## Modulstruktur

```
shared/            KMP-Modul, gesamte Logik + Compose-UI
  commonMain/
    telemetry/     TelemetryClient, Discovery, Salsa20, PacketDecoder, TelemetryFrame, Recorder/Replay
    session/       SessionEngine, LiveState, TrackMatcher, Delta-Berechnung
    storage/       SessionRepository, SQLDelight-Schema, CarCatalog
    ui/            Screens, ViewModels, Canvas-Charts, Theme
  desktopMain/     SQLDelight-JDBC-Treiber, App-Datenverzeichnis
desktopApp/        main() für macOS, Fenster, Packaging
(später) androidApp/, iosApp/
```

`expect/actual` nur für: SQLDelight-Treiber und App-Datenverzeichnis.

## Datenfluss

```
PS5 (GT7) ──UDP :33740, ~60/s, Salsa20──▶ TelemetryClient ──Flow<ByteArray>──▶ PacketDecoder
   ▲                                                                              │
   └── Heartbeat alle ~10 s an :33739                              Flow<TelemetryFrame>
                                                                                  ▼
                                                                          SessionEngine
                                                     StateFlow<LiveState> │       │ abgeschlossene Runden
                                                                          ▼       ▼
                                                                    LiveScreen   SessionRepository ◀─▶ TrackMatcher
                                                                                  │
                                                                                  ▼
                                                                            AnalysisScreen
```

## Komponenten

### TelemetryClient
- Öffnet UDP-Socket auf Port 33740, sendet Heartbeat-Byte `A` an `<ps5-ip>:33739` sofort und danach alle 10 s.
- Liefert `Flow<ByteArray>` der Rohpakete. Kennt nur IP/Ports.
- **Discovery:** sendet Heartbeat an die Broadcast-Adresse; die Absenderadresse des ersten gültigen Pakets ist die PS5-IP. Fallback: manuelle IP in den Einstellungen. Zuletzt genutzte IP wird gespeichert.
- Abstraktion `TelemetrySource` (Interface: `Flow<ByteArray>`) mit Implementierungen `UdpTelemetrySource` und `ReplayTelemetrySource`.

### PacketDecoder
- Reine Funktion `decode(bytes: ByteArray): TelemetryFrame?`.
- Entschlüsselung: Salsa20, Key = erste 32 Bytes von `"Simulator Interface Packet GT7 ver 0.0"`, Nonce aus Bytes 0x40–0x43 (IV1), IV2 = IV1 XOR `0xDEADBEAF` (Pakettyp A), 8-Byte-Nonce = IV2 ‖ IV1.
- Validierung: Länge 296 Bytes, Magic `0x47375330` ("G7S0") an Offset 0.
- Parsing little-endian. Felder in `TelemetryFrame` (v1): Position x/y/z, Geschwindigkeit (m/s), RPM, Schaltblitz-Grenzen (rpm alert min/max), Gang, vorgeschlagener Gang, Gas, Bremse, Ladedruck (Wert − 1 bar), Benzinstand + Kapazität, Reifen-Oberflächentemperatur ×4, Raddrehzahl ×4, Reifenradius ×4, aktuelle Runde, Gesamtrunden, Bestzeit, letzte Rundenzeit, Position/Startplätze, Fahrzeug-ID, Paket-ID, Flags (auf Strecke, pausiert, lädt, Turbo vorhanden u. a.).
- Pakettypen B / `~` sind spätere Erweiterung (anderes XOR, längere Pakete).

### SessionEngine
- Zustandsmaschine über `Flow<TelemetryFrame>`; Zeitquelle (`Clock`) wird injiziert.
- **Neue Runde**, wenn `currentLap` steigt. Zurückgelegte Distanz innerhalb der Runde wird aus Geschwindigkeit × Δt integriert (Basis für Delta und Charts).
- **Sitzungsende** bei: Wechsel ins Menü/Laden (Flags), Fahrzeugwechsel oder > 30 s ohne Pakete.
- Liefert `StateFlow<LiveState>` (aktueller Frame + abgeleitete Werte: Live-Delta, Benzinprognose, Schlupf) und `Flow<CompletedLap>`.
- **Live-Delta:** Vergleich der aktuellen Rundenzeit mit der Bestrunde (gleiche Strecke, sonst gleiche Sitzung) an derselben Distanz (lineare Interpolation).
- **Benzinprognose:** verbleibender Sprit / Durchschnittsverbrauch der letzten bis zu 3 gültigen Runden.
- **Schlupf pro Rad:** `(raddrehzahl × reifenradius) / fahrzeuggeschwindigkeit − 1`; Schwelle für Anzeige ±15 %, unterhalb 10 km/h nicht ausgewertet.
- **Runde ungültig** (`is_valid = false`), wenn währenddessen pausiert/zurückgespult wurde (Paket-ID-Sprung rückwärts oder Pause-Flag) oder gemessene Zeit > 2 s von der GT7-Rundenzeit abweicht.

### TrackMatcher
- Nach der ersten gültigen Runde einer Sitzung: x/z-Linie auf 200 Punkte über die Distanz resampeln, auf Startpunkt zentrieren.
- Vergleich mit allen gespeicherten `track.outline` per mittlerem Punktabstand; Treffer, wenn < 15 m.
- Treffer → Vorschlag „Gleiche Strecke wie <Datum> – <Name>?“ (bestätigen/ablehnen). Kein Treffer oder abgelehnt → Dialog für neuen Streckennamen; Umriss wird als neuer `track` gespeichert.

### SessionRepository (SQLDelight)
DB-Datei: `~/Library/Application Support/GT7Dashboard/gt7.db`.

| Tabelle | Spalten |
|---|---|
| `track` | `id`, `name`, `outline` (BLOB, 200 × 2 Float) |
| `session` | `id`, `track_id` (nullable), `car_id`, `started_at`, `ended_at` |
| `lap` | `id`, `session_id`, `number`, `time_ms`, `fuel_used`, `is_valid` |
| `lap_sample` | `lap_id`, `time_ms`, `distance_m`, `speed`, `throttle`, `brake`, `gear`, `rpm`, `x`, `z`, `boost` |
| `setting` | `key`, `value` (zuletzt genutzte PS5-IP, Einheit) |

- Samples werden pro Runde auf 10 Hz heruntergerechnet gespeichert.
- `CarCatalog`: mitgelieferte `cars.csv` (Community-Liste ID → Name); unbekannte ID → „Auto #<id>“.

## Oberfläche

Drei Tabs: **Live**, **Analyse**, **Einstellungen**. Dunkles Theme, hoher Kontrast.

**Live:** RPM-Balken mit Schaltblitz (rot ab Alert-Grenze), Gang groß, Tempo, Runde x/y, Position, letzte/beste Rundenzeit, Live-Delta (farbig), Gas-/Bremsbalken, Reifentemperaturen ×4, Schlupf-Indikator pro Rad (blinkt), Benzin + Restrunden, Ladedruck (nur wenn Fahrzeug Turbo hat). Ohne Verbindung: Status „Warte auf PS5 (<ip>)…“.

**Analyse:** Sitzungsliste (Strecke, Auto, Datum, Bestzeit) → Rundenliste (Zeit, Δ zur Bestzeit, Verbrauch, ungültig markiert). Auswahl von zwei Runden derselben Strecke → Charts über Distanz: Tempo, Gas/Bremse, Gang, Delta-Kurve; Streckenkarte mit farbiger Markierung Zeitgewinn/-verlust.

**Einstellungen:** PS5-IP (manuell / „Suchen“), Einheit km/h ↔ mph, Diagnose (Pakete/s, verworfen), Mitschnitt starten/stoppen, Replay-Datei abspielen.

## Fehlerbehandlung

- Keine Pakete → Status „Warte auf PS5…“, Heartbeats laufen weiter, automatische Wiederaufnahme.
- Ungültiges Paket (Länge/Magic) → verwerfen, zählen, in Diagnose anzeigen.
- Pause/Replay/Rückspulen → Runde als ungültig markiert, nicht für Bestzeit/Delta verwendet.
- DB-Fehler → loggen; Live-Ansicht ist unabhängig von der DB.

## Tests (`commonTest`)

- Salsa20 gegen offizielle Testvektoren.
- PacketDecoder gegen echte, eingefrorene Pakete (Fixtures aus dem Mitschnitt-Feature).
- SessionEngine: synthetische Frame-Folgen (Rundenwechsel, Pause, Timeout, Fahrzeugwechsel, ungültige Runde), injizierte Clock.
- TrackMatcher: gleiche Strecke mit Rauschen → Treffer; andere Strecke → kein Treffer.
- Delta/Benzin/Schlupf-Berechnung als reine Funktionen.
- Repository gegen In-Memory-SQLite (JVM-Test).
- `ReplayTelemetrySource` spielt Mitschnitte in Echtzeit ab → UI ohne PS5 testbar.

Mitschnittformat: Binärdatei, je Paket `[u64 Zeitstempel ms][u16 Länge][Bytes]` (verschlüsselt, wie empfangen).
