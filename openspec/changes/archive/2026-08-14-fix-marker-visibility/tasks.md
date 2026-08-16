# Tasks — fix-marker-visibility

## 1. Marker State in UI (overlay input)

- [x] 1.1 Add `gpsMarkerLat`, `gpsMarkerLon`, `gpsMarkerBearing`, `gpsMarkerAccuracy` fields to `MapCanvasUiState` (spec: gps-location-marker — "Marker projects against displayed bitmap viewport")
- [x] 1.2 In `MapCanvasViewModel` `location.collect`: set `gpsMarkerLat/Lon` from `markerLat/markerLon` (nav-filtered in routing mode, raw GPS otherwise — never the smoothed center); set `gpsMarkerBearing` from follow-mode `markerBearing` / non-follow `loc.bearing` (NaN when unavailable); set `gpsMarkerAccuracy` from `loc.accuracy` (spec: gps-location-marker — "Marker rendered on dedicated overlay target"; design D3)
- [x] 1.3 On null location, clear marker state (`gpsMarkerLat/Lon = NaN`) instead of calling `mapRenderer?.clearGpsMarker()` (spec: gps-location-marker — "Marker hidden leaves no residue")

## 2. Compose Overlay on Map Canvas

- [x] 2.1 Change `LocationMarkerOverlay` signature from `location: Location?` to explicit `lat/lon/bearing/accuracy` (bearing NaN → north-up arrow; lat/lon NaN → render nothing) (spec: gps-location-marker — "Marker rendered on dedicated overlay target")
- [x] 2.2 Compose `LocationMarkerOverlay` in `MapCanvasScreen` inside the same `Box` as the map image, positioned above it, fed by `uiState.gpsMarker*` + `uiState.renderViewport` (front-buffer viewport) + screen size + dpi (spec: gps-location-marker — "Marker projects against displayed bitmap viewport"; design D2)
- [x] 2.3 Verify marker stays anchored during pan/zoom/rotate gestures (overlay reprojects per frame against last emitted front-buffer viewport; no marker pixels in map bitmap) (spec: gps-location-marker scenarios "Marker stays anchored during pan" / "Marker anchored during rotation placeholder")

## 3. Remove Native Marker Path

- [x] 3.1 Remove GPS marker state block, marker snapshot read, and Cairo marker draw block from `OSMScoutClient.cpp` (spec: double-buffering — "Buffers hold only rendered map content")
- [x] 3.2 Remove `setGpsMarker`/`clearGpsMarker` JNI exports from `OSMScoutClient.cpp` and their `native` declarations from `OSMScoutClient.java` (spec: tile-cache — "Cached tiles contain only static map content")
- [x] 3.3 Remove marker plumbing from `MapRenderer`: `gpsMarker*` fields, `RenderJob`/`PendingRender` marker members, `setGpsMarker`/`clearGpsMarker` API, `client.setGpsMarker(...)` call in `executeRender`, `gpsRenderMinIntervalMs` throttle + `lastGpsRenderMs`/`lastMarker*` state, and the obsolete marker screen-position log (spec: tile-cache; double-buffering)
- [x] 3.4 Remove `setGpsMarker`/`clearGpsMarker` overrides from `FakeOSMScoutClient` and delete any remaining `MapRenderer` marker-triggered render logic (spec: tile-cache — "Overlay change does not purge cache")
- [x] 3.5 Grep for stale `setGpsMarker`/`clearGpsMarker`/`gpsMarker` references across `app/`, `:osmscout-jni`, tests — none may remain (spec: gps-location-marker — "Marker rendered on dedicated overlay target")

## 4. Tests

- [x] 4.1 Migrate `MapRendererGpsMarkerTest`: drop marker-state/throttle assertions; keep viewport/emission coverage (spec: double-buffering)
- [x] 4.2 Update `MapCanvasViewModelFollowModeTest`: assert `gpsMarkerLat/Lon` hold the nav-filtered position (not raw fix), `gpsMarkerBearing` follows the course priority chain, no renderer marker calls (spec: gps-location-marker — "Marker rendered on dedicated overlay target"; design D3)
- [x] 4.3 Add `LocationMarkerOverlay` projection tests: off-screen culling, projection against front-buffer viewport, bearing sign convention `screenBearing = bearing + angle`, north-up fallback (spec: gps-location-marker — "Marker projects against displayed bitmap viewport")
- [x] 4.4 Run `./gradlew test` — full suite green (incl. Robolectric classloader rule: no `@Config` on classes touching `FakeOSMScoutClient`)

## 5. Build & Device Verification

- [x] 5.1 Build debug APK: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`
- [x] 5.2 On device: follow-location mode — marker at vehicle position, arrow aligned with travel, no ghost markers after pan, no marker pixels in cached tiles while moving (spec: gps-location-marker)
- [x] 5.3 On device: routing mode — marker on nav-filtered position, correct orientation after turns, marker visible while map rotates (spec: gps-location-marker; marker-render-accuracy)
- [x] 5.4 On device: GPS loss — marker disappears cleanly; zoom/pan after that shows no marker residue (spec: tile-cache — "Marker moves and cached tiles are reused"; double-buffering — "Sub-region blit after marker move")

## 6. Finalize

- [x] 6.1 Update `guidelines/MapRendering.md` to final state: pipeline §1 (overlay stage after front buffer), §6 angle convention (Kotlin `screenBearing`), §8 marker rules (Compose overlay, front-buffer viewport projection, no native bake), §12 (no marker in `PendingRender`), §13 (blits copy pure map content), regression checklist (ghost-marker / wrong-viewport entries)
- [x] 6.2 Verify no other docs reference the native marker path (search `gpsMarker`, `setGpsMarker` in `docs/`, `README`, `AGENTS.md`)
- [x] 6.3 `openspec sync` then archive the change
