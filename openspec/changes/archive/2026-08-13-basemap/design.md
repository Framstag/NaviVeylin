# Basemap Support — Design

## Context

NaviVeylin baut die C++-Client-Libs aus dem geforkten Submodul `app/src/main/cpp/libosmscout` (lokaler HEAD `bebd53483`, 2026-08-02; origin/master 27 Commits voraus). Das Submodul enthält bereits die Basemap-Grundlagen aus früheren Upstream-Änderungen: `withBasemapLookupDirectory()` im Builder, DBThread lädt die Basemap als Overlay-DB (`DBThread.cpp` 268–295), `SynchronousDBJob2`-Overload mit `basemapDatabase`-Parameter, Label-Precedence- und Routing-Crash-Fixes. Es fehlen: Java-seitiger `BasemapManager`, die JNI-Render-Logik (PR #1755) im lokal angepassten `renderWithRouteAndPois`, das Stylesheet und die UI.

App-Seite: Provider karry.cz (`https://osmscout.karry.cz`, Basemap-Konvention `{uri}/basemap/`), Karten unter `filesDir/maps/`, HTTP strikt via `HttpURLConnection` (Spec `map-download-infrastructure`), Compose-UI. `:osmscout-client-java`-Modul kompiliert Submodul-Java mit lokalem Override-Pattern (OSMScoutClientBuilder, OSMScoutClient, MapDownloadManager, AvailableMapEntry).

Motivation: proposal.md — Why. Anforderungen: specs/ — 4 neue + 3 Delta-Capabilities.

## Goals / Non-Goals

**Goals:**
- Basemap auf Android: Discovery, Download, Update, Delete, Reload — ohne neue Dependencies
- Upstream-PR-#1755-Logik auf die lokale, divergierte JNI-Render-Pipeline portieren (nicht blind übernehmen)
- Basemap rendert als Overlay unter regionalen Karten, sichtbar in ungedeckten Bereichen
- Download-Lifecycle (Wake Lock, Foreground Service) für Basemap-Downloads wiederverwenden

**Non-Goals:**
- Keine Änderungen an libosmscout-Core-Libs (DBThread-Overlay-Support existiert bereits); nur minimaler, gezielter Zusatz für Reload/Unload
- Kein vollständiger Submodule-Merge auf origin/master (bringt Piper-TTS/Qt-Änderungen mit Build-Risiko) — separater Sync später
- Keine Server-/Provider-Änderungen, kein Auto-Download beim ersten Start
- Kein Basemap-Erstellungs-Tooling (BasemapImport)
- Keine JavaFX-UI-Übernahme aus JavaScout (Compose-UI)

## Decisions

### Decision 1: Submodule — gezielt portieren statt Voll-Merge

**Gewählt:** Aus PR #1755 (Merge `d7d3695f6`) nur die app-relevanten Dateien übernehmen: `BasemapManager.java` + `BasemapManagerTest.java` (neue Dateien, konfliktfrei) und `stylesheets/basemap-render.oss` direkt aus origin/master kopieren; den JNI-Hunk (`renderWithRouteAndPois` Basemap-Logik) manuell auf den lokalen, NaviVeylin-angepassten Funktionstext portieren. JavaScout-JavaFX-UI (MainController, MapDownloadController, fxml) entfällt.

**Alternativen:**
- **Voll-Merge origin/master in lokalen Branch** — 27 Commits, darunter Piper-TTS/Qt-Churn mit CMake/vcpkg-Risiko; lohnt als eigener Sync-Change, nicht hier.
- **`git cherry-pick -m 1 d7d3695f6`** — Merge-Commit-Pick ist unhandlich, JNI-Hunk kollidiert ohnehin mit lokalen Änderungen → manueller Port nötig, unabhängig von der Methode.

### Decision 2: BasemapManager als lokaler Override im `:osmscout-client-java`-Modul

**Gewählt:** `BasemapManager.java` kommt in den lokalen Override-Bereich des `:osmscout-client-java`-Moduls (bestehendes Pattern), nicht ins Submodul. Grund: der Android-Port (HttpURLConnection statt `java.net.http`, `filesDir/maps`-Pfade) weicht vom Upstream ab; so bleibt die Submodul-Datei für künftige Syncs sauber.

**Alternativen:**
- **Im Submodul ablegen** — divergiert den Fork weiter; Sync-Konflikte bei jedem Update.

### Decision 3: HTTP via HttpURLConnection + Streaming

**Gewählt:** Probe und Download nutzen `java.net.http`-freies `HttpURLConnection` (Spec: kein Desugaring). Download streamt auf Temp-Datei; Extraktion per `GZIPInputStream` + manuellem Tar-Parser (nur `java.*`, wie Upstream). Keine neue Dependency.

**Alternativen:**
- **Upstream `java.net.http.HttpClient` übernehmen** — verletzt `map-download-infrastructure`-Spec (Desugaring liefert `java.net.http` nicht).
- **Apache Commons Compress** — neue Dependency, unnötig (Upstream parst manuell).

### Decision 4: Atomic Update + Reload via temp dir + JNI-Relaod

**Gewählt:** Download+Extraktion nach `filesDir/maps/.basemap-tmp`; nach Erfolg Swap: altes Verzeichnis → Backup, temp → `basemap`, Backup löschen, bei Fehler Rollback. Danach Reload per neuem JNI-Method `reloadBasemap()`: ein `SynchronousDBJob2` in DBThread, der `basemapDatabase` schließt und `basemapLookupDirectory` neu scannt (lädt neue Version bzw. leert bei Delete). Kleiner, gezielter Zusatz in libosmscout-client + JNI — kein Client-Rebuild.

**Alternativen:**
- **Client komplett neu bauen** (Builder + Renderer) — reißt gesamten State ab (Map-Position, Favoriten-ViewModels), zu teuer für einen optionalen Layer.
- **Reload nur über `openDatabase()`** (Upstream-Ansatz) — deckt Download, aber kein Unload nach Delete ohne zusätzliche Mechanik.
- **Neustart nötig machen** — schlechte UX, widerspricht Spec `basemap-loading` (Reload/Unload zur Laufzeit).

### Decision 5: UI — Sektion in MapManagerScreen, kein Status-Chip im Map-View

**Gewählt:** Compose statt JavaFX-Port. `BasemapViewModel` (Hilt) hält StateFlow für Verfügbarkeit, Installations-Info (Größe/Version), Download-Fortschritt, Fehler. Sektion im bestehenden `MapManagerScreen` (kein Tab/Screen — Spec `map-download-ui`). Basemap-Downloads laufen über denselben Download-Lifecycle (Wake Lock + Foreground Service) wie Karten-Downloads, damit der Prozess bei großen Archiven (≈39 MB komprimiert) nicht stirbt. Kein Status-Chip im Map-View: die Basemap rendert still als Overlay (Nutzer-Entscheid, Spec `basemap-ui` entsprechend angepasst).

**Alternativen:**
- **Eigener Screen** — unnötige Navigation (Spec verlangt Integration in bestehende Screen).

### Decision 6: Stylesheet — ship + verifizieren

**Gewählt:** `basemap-render.oss` in `assets/stylesheets/` (AssetCopier kopiert). Wichtig: DBThread erzeugt den StyleConfig der Basemap-DB mit demselben aktiven Stylesheet wie regionale Karten (`makeStyleConfig(typeConfig)`). Daher muss das aktive App-Stylesheet die Basemap-Typen (`_tile_sea`, `_tile_land`, `_tile_unknown`, `place_continent`, `place_country`, `basemap_boundary_country`) abdecken. Implementierung prüft das und ergänzt fehlende Regeln im App-Stylesheet; `basemap-render.oss` dient als Referenz/Minimal-Stylesheet.

**Alternativen:**
- **Automatischer Stylesheet-Wechsel auf `basemap-render.oss` bei reiner Basemap-Ansicht** — doppelte StyleConfig-Verwaltung, Overengineering.

## Risks / Trade-offs

- **Server verschiebt Basemap-Pfad** → Probe schlägt still fehl, geloggt; UI zeigt "Basemap unavailable" (Spec-konform). Kein Nutzerfehler.
- **Korruptes Archiv** → Gzip-CRC-Validierung vor Extraktion, Cleanup auf Fehler; bestehende Installation unangetastet (Temp-Dir).
- **Speicher bei 39 MB+ Archiv** → Streaming (Download + Extraktion), nie komplett im Heap.
- **JNI-Port-Konflikte mit lokalen Render-Änderungen** → manueller Port + Render-Verifikation (Basemap allein, Overlay, sea/land-Precedence).
- **Fehlende Basemap-Typen im App-Stylesheet** → Verifikation + Regeln ergänzen (Decision 6).
- **Doppelte Küstenlinien an Kartenrändern bei niedrigem Zoom** → erwartet; Basemap ist Fallback, regionale Karte gewinnt.
- **Speicher-/CPU-Last der Weltkarte auf schwachen Geräten** → optional, nutzerinitiiert; Delete jederzeit möglich.

## Migration Plan

- Keine Datenmigration: `filesDir/maps/basemap/` ist neu und optional.
- Ausrollen: Submodul-Update (gezielter Port), JNI-Neubau (CMake), App-Build; alte APKs ohne Basemap bleiben funktionsfähig.
- Rollback: `filesDir/maps/basemap/` löschen + Submodul/JNI-Revert; App läuft ohne Basemap normal weiter.

## Open Questions

- **Exaktes Listing-Format von `https://osmscout.karry.cz/basemap/`** — Upstream parst Apache-Stil-Dir-Listing; Parser wird bei Implementierung gegen den Live-Server validiert (Archiv-Namens-/Datumsformat). Ändert weder Specs noch Ansatz noch Task-Breakdown.
- **Namens-/Versionskonvention der Archive (full vs minimal)** — Feld-Validierung zur Laufzeit; Fallback: Single-Variant ohne Auswahl (Spec gedeckt).
