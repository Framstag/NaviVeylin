# details-full-screen-with-map Design

## Context

Current `LocationDetailsSheet` is a `ModalBottomSheet` (draggable, `skipPartiallyExpanded = true`, map visible behind). Opened from: search result selection, long-press, POI search (`detailsFromPoiSearch` → "Show" button), favorites. Data available:

- `LocationEntry`: `label`, `adminRegionHierarchy`, `lat`/`lon`, `matchQuality`.
- `ObjectDescription.entries`: `sectionKey`/`subsectionKey`/`labelKey`/`value`. Native `DescriptionService` already emits an `"Address"` entry (`labelKey = "Address"`, value = house number, section `"Location"`) when the object has `addr:housenumber` (`AddressFeature`).
- `MapRenderer(client, dpi, scope)`: `requestRender(lat, lon, mag, angle)`, `frameFlow`, `currentViewportFlow`, `shutdown()`. Renderer instances render on their own coroutine scope; JNI renders queue on the shared client dbThread.
- `ProjectionUtils` (`:core`): `dragDeltaToNewCenter` (unrotated pan math), `zoomAtCursor`, `computeScale`, `geoToScreen` — everything needed for an unrotated interactive map.
- Codebase pattern: full-screen overlays are composed conditionally inside `MapCanvasScreen` (FavoritesSheet, RoutePanel). Back handling via `BackHandler`/`OnBackPressedDispatcher` (spec: back-gesture).

## Goals / Non-Goals

**Goals:**
- Full-screen details view: name → interactive mini map → structured list → actions.
- Reusable `MiniMap` widget: independent viewport, zoom buttons, pan, north lock, object marker.
- Back gesture (incl. predictive back API 33+) closes the dialog.
- Admin region as list entry; address (house number) as list entry.

**Non-Goals:**
- Rotation in the mini map (always north).
- Mini-map marker interactivity (marker is display-only).
- Any JNI/native change (data already available).
- Migrating other sheets (route panel, favorites) to the widget in this change — widget API is prepared for that, usage is not.

## Decisions

### D1: Full-screen dialog is a conditional in-app overlay, not a `Dialog` window

Options: (a) Compose `Dialog(usePlatformDefaultWidth = false)`; (b) in-app full-screen `Surface`/`Box` composed in `MapCanvasScreen` when `showDetailsSheet`; (c) navigation route.

Chosen: **(b)**. Matches the existing full-screen overlay pattern (FavoritesSheet, RoutePanel), avoids a second window layer (a known footgun in this screen — `ModalBottomSheet` is a separate window, see `MapCanvasScreen` route-panel comment), and gives full control over insets/layout. Back gesture is handled by `BackHandler(enabled = showDetailsSheet) { viewModel.dismissDetailsSheet() }`, which routes through `OnBackPressedDispatcher` and supports the predictive back animation on API 33+ when the app opts in (`android:enableOnBackInvokedCallback="true"` — verify in manifest; if not enabled, the back button still works and only the gesture preview is degraded).

### D2: Reusable `MiniMap` widget owns its own `MapRenderer` + lifecycle

New file `app/src/main/java/com/naviveylin/ui/map/MiniMap.kt`:

```kotlin
@Composable
fun MiniMap(
    client: OSMScoutClient,
    lat: Double, lon: Double,
    initialMag: Int,
    modifier: Modifier = Modifier
)
```

- Creates its own `MapRenderer(client, dpi, CoroutineScope(SupervisorJob() + Dispatchers.Default))` in `remember`; `DisposableEffect` cancels the scope and calls `renderer.shutdown()` on dispose (same teardown `initMap` already performs).
- Canvas size via `onSizeChanged` → `renderer.screenWidth/Height`; then `renderer.requestRender(lat, lon, initialMag, angle = 0.0)`.
- Renders `frameFlow.bitmap` with the same scale-to-fill center-crop the main map uses; overlays the object marker.
- **Pan**: `detectDragGestures` → `ProjectionUtils.dragDeltaToNewCenter(lat, lon, dx, dy, mag, w, h, dpi)` → `requestRender`.
- **Zoom buttons**: small overlay `IconButton`s (+/−), `mag ± 1` clamped to the app's magnification limits; zoom keeps center (angle stays 0).
- **North lock**: no rotation gestures are ever bound; every `requestRender` passes `angle = 0.0`.
- **Marker**: Compose overlay; project `(lat, lon)` via `ProjectionUtils.geoToScreen` against the widget's own viewport; pin drawn only when inside canvas bounds.
- Initial magnification: caller passes it (details dialog uses the main map's current magnification, clamped).

Alternatives considered: (i) reusing the main renderer's bitmap with a crop window — pan/zoom would outrun the rendered pixels or require re-render at main-map viewport semantics (would move the main map); rejected. (ii) static snapshot — rejected by requirement (interactive). (iii) sharing the main renderer instance — the mini map's viewport state would collide with the main map's; rejected.

### D3: Two renderers share the client — serialized, disposable

The widget's renderer and the main renderer both call into the same `OSMScoutClient`; native renders queue on the client's dbThread, so concurrent requests serialize — no locking needed. Trade-off: a second tile cache lives while the dialog is open. Bounded by dialog lifetime; `shutdown()` on dispose clears it.

### D4: Client reaches the widget via the ViewModel

`client` is currently `private` in `MapCanvasViewModel`. Add `internal val osmscoutClient: OSMScoutClient get() = client` and pass it to `MiniMap` from `MapCanvasScreen`. The widget stays framework-free (no Hilt), so future screens (route panel, favorites) can embed it with any client they already hold.

### D5: Admin region rendered as explicit list entry at the UI layer

`adminRegionHierarchy` lives on `LocationEntry` (state `selectedLocation`), not in `ObjectDescription` — it cannot come from the section renderer. Render it as a dedicated label/value row ("Admin Region" / hierarchy) above the description sections, styled like the other label/value rows (subdued label, 120.dp label column). Hidden when empty (spec: no entry without hierarchy).

### D6: Address comes from the existing native description entry — no extraction

The generic section renderer already draws the native `"Address"` entry (house number) as a row under the `"Location"` section. Adding a separate extraction would duplicate the row. The requirement is satisfied by existing rendering; the change formalizes it and adds a verification test (object with `addr:housenumber` → "Address" row visible). If `ObjectDescription` is absent (description fetch failed), there is no house-number data source — the address entry is then simply absent, matching the "no house number → no entry" scenario.

### D7: Layout order

Top to bottom: object name (title) → `MiniMap` (fixed height, e.g. 200.dp) → coordinates → admin region entry → description sections (scrollable) → action row (Show when from POI search, Route, favorite add/remove — unchanged from today). Back gesture closes; no scrim.

## Risks / Trade-offs

- [Double render load: main renderer + mini renderer active while dialog open] → Mini map renders only on size change / pan end / zoom tap (debounced by `MapRenderer`); dialog lifetime is short.
- [`shutdown()` while a render job is in flight] → Same pattern as `initMap` re-entry (cancel scope, then shutdown); renderer treats cancelled scope as terminal.
- [Duplicate tile cache memory] → Bounded by dialog lifetime; disposed on close.
- [Predictive back preview may not appear if `enableOnBackInvokedCallback` is not set] → Verify manifest/theme; if unset, back button still closes (spec scenario on API 33+ needs the flag — make it a task check; flipping the flag is a one-liner in the manifest).
- [Compose test for widget needs a renderer without real JNI] → `FakeOSMScoutClient` (host-side stub) already exists; widget tests verify structure/gesture wiring with the fake, not native pixels.

## Migration Plan

Code-only change, no data/schema migration. Rollback: revert the commit; the previous bottom-sheet behavior returns (the map-behind-sheet UX regresses, acceptable).

## Open Questions

None blocking. Initial mini-map magnification is pinned to the main map's clamped magnification at open (decided, not deferrable).
