# NaviVeylin TODO — Features JavaScout Has, NaviVeylin Doesn't

Based on analysis of [JavaScout](https://github.com/Framstag/libosmscout/tree/master/JavaScout) (libosmscout's reference JavaFX app) and its [OpenSpec specs](https://github.com/Framstag/libosmscout/tree/master/openspec/specs).

**Legend:** ✗ = missing | ⏳ = in progress / blocked

---

## 1. Route Calculation & Visualization

| Feature | Status | Notes |
|---------|--------|-------|
| Avoid tolls/ferries checkboxes | ✗ | `RoutingProfile` supports avoid flags — no UI yet |
| Bug: route polyline persists after navigation stop | ✗ | Stopping navigation (`stopNavigation()` + `setNavigating(false)`) clears `NavigationState` but route polyline + markers remain on map. Plumbing exists (`clearRouteSignal` → `mapRenderer.clearRoute()`) but is not triggered on nav stop — wire `routePanelViewModel.clearRoute()` into the stop path. |

## 2. Turn-by-Turn Navigation

| Feature | Status | Notes |
|---------|--------|-------|
| Voice guidance / audio instructions | ✗ | JavaScout `onVoiceInstruction(int[])` callback exists in JNI |

## 3. GPX Track Import & Playback

> libosmscout submodule (master) already has GPX import/render support (archived change `javascout-gpx-track-import-render`) and JavaScout has `TrackPlayer` — but nothing is wired into the NaviVeylin app yet.

| Feature | Status | Notes |
|---------|--------|-------|
| GPX file import | ✗ | `importGpxTrack()` exists in JNI — no app UI |
| Track rendering on map | ✗ | `renderWithRouteAndPois()` accepts `trackLats`/`trackLons` |
| Track playback (simulated GPS) | ✗ | JavaScout `TrackPlayer.java` with speed multiplier |
| Track playback toolbar (play/pause/stop/speed) | ✗ | JavaScout `trackToolbar` HBox |

## 4. Object Description & Long-Press

| Feature | Status | Notes |
|---------|--------|-------|
| Long-press timeout configuration | ✗ | Hardcoded 500ms — JavaScout configurable |

## 5. UI / Shell

| Feature | Status | Notes |
|---------|--------|-------|
| Responsive layout (small screen support) | ✗ | JavaScout `SMALL_SCREEN_THRESHOLD` (600px) |
| DPI-aware UI scaling | ✗ | JavaScout `UIScale.java` |
| Internationalisation (i18n) | ✗ | Multi-language UI support (strings, units, formats) |

## 6. Rendering

| Feature | Status | Notes |
|---------|--------|-------|
| Track rendering on map | ✗ | |

## 7. Android Auto
