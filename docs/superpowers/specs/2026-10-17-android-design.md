# Android-Version – Design

Stand: 2026-10-17

## Ziel

Die App läuft auf Android-Smartphones und -Tablets mit vollem Funktionsumfang (Live, Analyse, Bestenliste, Strecken, Autos, Einstellungen). Auf dem Smartphone ist sie vor allem zweiter Bildschirm beim Fahren, auf dem Tablet ersetzt sie den Rechner.

Erfolgskriterien:
- Ein Smartphone im selben WLAN wie die PS5 findet die PS5 per „PS5 suchen“, zeigt Live-Telemetrie und speichert Runden – ohne Rechner.
- Alle Tabs sind im Querformat auf einem Smartphone (≈ 800 × 360 dp) bedienbar, ohne dass Inhalte abgeschnitten werden.
- Ein Export vom Desktop lässt sich auf Android importieren und umgekehrt.
- Jeder GitHub-Release enthält neben DMG und MSI eine signierte APK.

Nicht-Ziele: Hochformat, Google Play Store, iOS, Wear OS, Benachrichtigungen oder Telemetrie im Hintergrund, Synchronisierung zwischen Geräten.

## Build-Struktur

- `shared` bekommt neben `jvm("desktop")` ein Android-Target (minSdk 26, compileSdk 36, JVM-Toolchain 17). Mit Gradle 9.2 / Kotlin 2.4 wird das zum Stand passende Android Gradle Plugin verwendet; bei AGP 9 über das Plugin `com.android.kotlin.multiplatform.library`. Die genauen Versionen legt der Plan nach einem Probe-Build fest.
- Neues Modul `:androidApp` (Application-Plugin): `Gt7Application` initialisiert die Plattformschicht in `onCreate()` (auch nach einem Prozess-Neustart vor allem anderen); `MainActivity` (ComponentActivity) ruft `App(...)` aus `shared` auf; Abhängigkeiten werden wie in `desktopApp/Main.kt` erzeugt (Repository, Controller, DataTransfer, Autokatalog).
- `applicationId` `de.bavaco.gt7dashboard`, `versionName` = Release-Version, `versionCode` fortlaufend.
- App-Icon aus `desktopApp/icons/app-icon.svg` als adaptives Icon.

## Plattformschicht (`androidMain`)

Gegenstück zu `Platform.desktop.kt`:

| Funktion | Android |
|---|---|
| `createDriver()` | `AndroidSqliteDriver(Gt7Database.Schema, context, "gt7.db")` – Schema und Migrationen wie Desktop |
| `appDataDir()` | `context.filesDir` |
| `openDatabaseFile(path)` | `AndroidSqliteDriver` auf den Pfad, ohne Schema |
| Dateiauswahl | Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) |

Den `Context` erhält die Plattformschicht einmalig beim Start (`initPlatform(context)` in `Gt7Application.onCreate()`).

**Dateiauswahl:** SAF liefert Content-URIs statt Pfaden. Die `pick…File()`-Funktionen werden `suspend` und liefern eine Datei im App-Cache:
- Import und Mitschnitt abspielen: Die gewählte Datei wird in den Cache kopiert, der Pfad geht an den bestehenden Code.
- Export: Der Code schreibt in eine Cache-Datei; danach kopiert die Plattformschicht sie in die gewählte URI.
- Mitschnitt aufnehmen braucht keine Auswahl; Aufnahmen landen wie auf dem Desktop unter `appDataDir()/recordings`. Weil dieser Ordner auf Android privat ist, fragt die App nach „Mitschnitt stoppen“, wohin der Mitschnitt gespeichert werden soll (z. B. Downloads oder Drive); von dort lässt er sich mit „Mitschnitt abspielen…“ wieder wählen.
- Export liest die Datenbank unter dem Controller-Lock, damit während der Fahrt keine Runde verloren geht; die Exportdatei ist geschlossen, bevor sie in die URI kopiert wird.

Dafür wird die Dateiauswahl als kleine Schnittstelle gekapselt (`FilePicker` mit `pickForRead(extension)` und `saveAs(suggestedName, write: (path) -> Unit)`). Desktop setzt sie mit dem bestehenden AWT-`FileDialog` um.

**Netzwerk:** UDP-Empfang und Discovery bleiben unverändert (ktor-network). Berechtigungen: `INTERNET`, `ACCESS_NETWORK_STATE`. Discovery sendet Broadcast und empfängt Unicast; eine Multicast-Sperre ist nicht nötig. Das wird auf einem echten Gerät geprüft.

**Lebenszyklus:** Der Controller lebt in einem `ViewModel`, damit Drehen oder Konfigurationswechsel keine Sitzung beenden. Geht die App in den Hintergrund, läuft der Empfang weiter, solange Android den Prozess lässt; garantiert wird das nicht (kein Foreground-Service).

## Darstellung

- Querformat fest (`screenOrientation="sensorLandscape"`).
- Im Live-Tab bleibt der Bildschirm an (`keepScreenOn`), System-Leisten werden ausgeblendet (immersiv).
- **Kompakt-Modus** unter 840 dp Breite (Smartphone quer), aus `BoxWithConstraints` in `App` abgeleitet und als `CompositionLocal` bereitgestellt:
  - Tab-Leiste scrollbar (`PrimaryScrollableTabRow`). Im Live-Tab ist sie ausgeblendet und über einen kleinen Knopf „☰“ unten links erreichbar.
  - Live und Race-Display verkleinern sich bis Faktor 0,45 (Desktop: nie unter 1), damit sie auf ≈ 800 × 360 dp passen.
  - Analyse: Liste und Charts untereinander statt nebeneinander.
  - Bestenliste und Autos: Spalten ohne feste Breiten, lange Namen gekürzt.
  - Einstellungen sind bereits scrollbar.
- Tablets (≥ 840 dp) verwenden das Desktop-Layout. Der Desktop ist nie kompakt.

## Verteilung

- `release.yml` bekommt einen dritten Job (ubuntu-latest): Tests, `:androidApp:assembleRelease`, signiert mit einem Keystore aus GitHub-Secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`), APK an den Release anhängen.
- README: Keystore einmalig anlegen und als Secrets hinterlegen; APK-Installation („Unbekannte Apps installieren“); Datenübertragung per Export/Import.

## Offener Punkt (vor der Umsetzung prüfen)

Sendet GT7 an **zwei Empfänger gleichzeitig**, wenn Rechner und Smartphone beide Heartbeats schicken? Wenn nein, kann jeweils nur ein Gerät verbunden sein; die App funktioniert trotzdem, das README weist darauf hin.

## Tests

- Bestehende Logiktests in `commonTest` laufen zusätzlich auf dem Android-Target (Unit-Tests auf der JVM).
- Instrumentierter Smoke-Test (`androidInstrumentedTest`): Datenbank anlegen, Runde speichern und lesen, v1-Datenbank migrieren, Export und Import über `DataTransfer`.
- Kompakt-Modus: Logik der Breitenschwelle als Unit-Test.
- Manuell auf einem echten Gerät: Discovery, Live-Telemetrie, Runde speichern, Export → Desktop-Import, Desktop-Export → Android-Import, Drehen während einer Sitzung.
