# Basemap Support — Tasks

## 1. Submodule Port (PR #1755) [spec: basemap-loading, basemap-render-delta]

- [x] 1.1 Kopiere `BasemapManager.java` aus origin/master (`d7d3695f6:libosmscout-client-java/java/com/framstag/libosmscout/client/BasemapManager.java`) in den lokalen Override-Source-Set von `:osmscout-client-java`
- [x] 1.2 Kopiere `BasemapManagerTest.java` aus origin/master als Basis für die Android-Port-Tests
- [x] 1.3 Kopiere `stylesheets/basemap-render.oss` aus origin/master nach `app/src/main/assets/stylesheets/`
- [x] 1.4 Portiere die Basemap-Render-Logik aus `d7d3695f6` (OSMScoutClient.cpp `renderWithRouteAndPois`: `basemapDatabase`-Param, `baseMapTiles`-Splice, Sea/Land-Precedence, Standalone-Basemap-Render) manuell auf die lokale NaviVeylin-Version — Funktion nicht blind ersetzen
- [x] 1.5 Ergänze JNI-Method `reloadBasemap()`: `native`-Deklaration in `OSMScoutClient.java`, JNI-Impl in `OSMScoutClient.cpp`, DBThread-Job der `basemapDatabase` schließt und `basemapLookupDirectory` neu scannt (Design Decision 4)
- [x] 1.6 Build-Verifikation: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` kompiliert ohne Fehler

## 2. BasemapManager Android-Port [spec: basemap-discovery, basemap-download, map-download-infrastructure-delta]

- [x] 2.1 Portiere `fetchAvailableBasemaps()`-Probe auf `HttpURLConnection` (GET `{provider.uri}/basemap/`, HTML-Dir-Listing-Parsing, stille Fehlerbehandlung mit Logging)
- [x] 2.2 Portiere `downloadBasemap()` auf `HttpURLConnection` mit Streaming-Download auf Temp-Datei + Fortschritts-Reporting via `MapDownloadListener`
- [x] 2.3 Implementiere tar.gz-Extraktion: `GZIPInputStream` + manueller Tar-Parser, Streaming nach `{mapsDir}/.basemap-tmp/` (nur `java.*`)
- [x] 2.4 Implementiere Atomic-Update: Temp-Dir → Swap mit Backup + Rollback bei Fehler (Design Decision 4); `{mapsDir}/basemap/` nie korrupt
- [x] 2.5 Implementiere Cancellation + Partial-File-Cleanup (Temp-Dateien + Temp-Dir) auf Abbruch/Fehler
- [x] 2.6 Implementiere `getInstalledBasemapInfo()`, `isUpdateAvailable()`, Varianten-Erkennung (full/minimal), `deleteBasemap()` (inkl. Reload-Trigger)
- [x] 2.7 Unit-Tests (plain JUnit, kein Netzwerk): Dir-Listing-Parsing, Versionsvergleich, Extraktion, Cancel, Cleanup, Delete

## 3. Hilt + Client-Wiring [spec: basemap-loading, map-download-infrastructure-delta]

- [x] 3.1 `MapDownloadModule`: `@Provides BasemapManager` (mapsDir + Default-Provider injiziert)
- [x] 3.2 Builder-Wiring: `withBasemapLookupDirectory(filesDir/maps/basemap)` genau dann, wenn Verzeichnis existiert (sonst weglassen)
- [x] 3.3 Nach Download/Update/Delete: `reloadBasemap()` aufrufen + Re-Render des aktuellen Viewports auslösen (Spec `basemap-loading`)

## 4. UI [spec: basemap-ui, map-download-ui-delta]

- [x] 4.1 `BasemapViewModel` (Hilt): StateFlow für Verfügbarkeit, Installations-Info (Größe/Version), Download-Fortschritt, Fehler; Aktionen download/update/delete/cancel
- [x] 4.2 Basemap-Sektion in `MapManagerScreen`: Status (installiert/verfügbar/nicht verfügbar), Download/Update/Delete-Controls, Fortschritt, Variantenwahl, Fehleranzeige mit Retry
- [x] 4.3 Basemap-Eintrag erscheint/verschwindet in "Installed Maps"-Liste; Download erscheint in "Active Downloads"-Sektion mit Cancel
- [x] 4.4 Status-Chip "Basemap: active" in `MapCanvasScreen`, State über `MapCanvasViewModel` (ausgeblendet ohne Basemap)
- [x] 4.5 Basemap-Downloads in Wake-Lock- + Foreground-Service-Lifecycle integrieren (gleicher Mechanismus wie Karten-Downloads)
- [x] 4.6 Compose-UI-Test (Robolectric, RoutePanel-ComposeTest-Muster): Basemap-Sektion States installiert/verfügbar/Fortschritt — Klassen mit FakeOSMScoutClient-Regeln beachten (Classloader-Regel, keine `@Config`-Overrides)

## 5. Stylesheet [spec: map-render-delta]

- [x] 5.1 Aktives App-Stylesheet auf Basemap-Typen prüfen (`_tile_sea`, `_tile_land`, `_tile_unknown`, `place_continent`, `place_country`, `basemap_boundary_country`); fehlende Regeln ergänzen (Design Decision 6)
- [x] 5.2 Render-Verifikation: Basemap allein (Region ohne Karte) + Overlay unter regionaler Karte + Sea/Land-Precedence

## 6. Build & Verify [regeln: immer Build + Tests]

- [x] 6.1 `./gradlew :osmscout-client-java:compileJava` + `:osmscout-client-java:test` — keine Kompilierfehler, Port-Tests grün
- [x] 6.2 `./gradlew :app:testDebugUnitTest` — bestehende Tests ohne Regressionen
- [x] 6.3 `./gradlew :app:assembleDebug` (alle 3 ABIs) — Build kompiliert
- [x] 6.4 Manueller Test auf Gerät/Emulator: Probe bei geöffnetem Map-Manager, Download mit Fortschritt, Rendering (ungedeckte Region + Overlay), Update, Delete mit Unload
