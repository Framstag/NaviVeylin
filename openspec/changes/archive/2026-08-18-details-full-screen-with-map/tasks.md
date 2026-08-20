# details-full-screen-with-map Tasks

## 1. Reusable MiniMap widget

- [x] 1.1 Create `app/src/main/java/com/naviveylin/ui/map/MiniMap.kt` with `@Composable fun MiniMap(client: OSMScoutClient, lat: Double, lon: Double, initialMag: Int, modifier: Modifier = Modifier)` — owns a `MapRenderer` + `CoroutineScope(SupervisorJob() + Dispatchers.Default)` created in `remember`, disposed via `DisposableEffect` (scope.cancel() + `renderer.shutdown()`), canvas size fed via `onSizeChanged` (`renderer.screenWidth/Height`), initial `requestRender(lat, lon, initialMag, angle = 0.0)`, renders `frameFlow.bitmap` with scale-to-fill center-crop (mirror main map drawing)
- [x] 1.2 Add single-finger pan: `detectDragGestures` → `ProjectionUtils.dragDeltaToNewCenter(lat, lon, dx, dy, mag, w, h, dpi)` → `requestRender`; angle always `0.0` (north lock, no rotation gestures bound)
- [x] 1.3 Add small zoom buttons (+/−) overlaid on the mini map: `mag ± 1` clamped to the app's magnification limits (reuse `MapCanvasViewModel` clamp constants), zoom keeps center, re-renders
- [x] 1.4 Add object marker overlay: project `(lat, lon)` via `ProjectionUtils.geoToScreen` against the widget's viewport; draw pin only when inside canvas bounds; marker follows pan/zoom
- [x] 1.5 Add Robolectric compose test for `MiniMap` (uses `FakeOSMScoutClient`; verify zoom buttons visible, pan/zoom wiring invokes renderer viewport updates, marker composed) — runs under `@RunWith(RobolectricTestRunner::class)` with default sandbox config

## 2. Full-screen details dialog

- [x] 2.1 Expose the client from `MapCanvasViewModel`: `internal val osmscoutClient: OSMScoutClient get() = client`
- [x] 2.2 Rewrite `LocationDetailsSheet.kt` from `ModalBottomSheet` to a full-screen overlay `Surface`/`Box` composed in `MapCanvasScreen` when `state.showDetailsSheet` (no separate window, no scrim); layout top-to-bottom: object name (title) → `MiniMap` (fixed height ~200.dp, centered on `selectedLocation` at main-map magnification clamped) → coordinates → admin region entry → description sections (scrollable) → action row (Show/Route/favorite) unchanged
- [x] 2.3 Render admin region as list entry: label/value row ("Admin Region" / `adminRegionHierarchy`) above the description sections, styled like existing rows, hidden when hierarchy empty
- [x] 2.4 Verify address entry: confirm native `"Address"` (house number) row renders via the generic section renderer for objects with `addr:housenumber`; add assertion in dialog compose test (object description containing Address entry → "Address" row visible; without it → no address row)
- [x] 2.5 Add back gesture: `BackHandler` inside the dialog (composed only while open, wins over map handlers; activity dispatcher drives predictive back on API 33+); `android:enableOnBackInvokedCallback="true"` already present in the manifest — no change needed
- [x] 2.6 Preserve all entry points and actions: search result selection, long-press, POI search (`detailsFromPoiSearch` → "Show" button), favorites, Route button, favorite add/remove — no behavior regression
- [x] 2.7 Update/extend compose tests for the details dialog (full-screen container, name → mini map → list order, back closes dialog, empty-description case shows name + mini map + coordinates + favorite controls)

## 3. Verification

- [x] 3.1 Run `./gradlew test` — all unit and compose tests pass (mind the JNI stub classloader rule: `FakeOSMScoutClient`-instantiating tests stay Robolectric with default sandbox)
- [x] 3.2 Manual QA on device/emulator: open details from search, long-press, POI search; back gesture closes dialog; mini map pans/zooms north-up with marker; zoom clamped at limits; admin region entry visible; address entry visible for house-numbered object and absent otherwise
