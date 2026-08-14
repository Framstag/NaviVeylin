## Why

NaviVeylin is a navigation app but shows no user location on the map. Without a GPS position marker, the map is just a static view — the user cannot see where they are, where they're heading, or how accurate the position fix is. This is the first step toward turn-by-turn navigation.

## What Changes

- Add runtime location permission request (ACCESS_FINE_LOCATION) with proper rationale
- Integrate Android FusedLocationProviderClient for GPS position updates
- Render a location marker overlay on the map canvas:
  - Accuracy circle (semi-transparent) around the estimated position
  - Direction arrow when bearing is available (≥ 0)
  - Small dot when bearing is unavailable
- Marker follows GPS updates in real-time, re-rendering on each position change
- Marker is rendered as a Compose overlay on top of the rendered map bitmap (not via JNI/Cairo), keeping the native renderer unchanged
- Location options button on the map screen that opens a small dialog
- Dialog contains a toggle to enable/disable "map follows position" (auto-center on GPS)
- When follow mode is active, the map re-centers on each GPS position update
- Follow mode disengages when the user manually pans or zooms the map

## Capabilities

### New Capabilities
- `gps-location-marker`: Render current GPS position on the map as a Compose overlay — accuracy circle + direction arrow or dot, updated on each location change
- `location-permissions`: Request and manage Android runtime location permissions (ACCESS_FINE_LOCATION) with rationale dialog and graceful degradation when denied
- `location-options-ui`: Location options button on the map screen with a dialog to toggle "map follows position" (auto-center on GPS)

### Modified Capabilities
- `map-canvas-screen`: Add GPS marker overlay composable and wire location updates into the screen layout
- `map-render`: No changes needed — marker is a Compose overlay, not a Cairo render pass

## Impact

- **New dependency**: Google Play Services Location (`com.google.android.gms:play-services-location`) for FusedLocationProviderClient
- **New file**: `app/src/main/java/com/naviveylin/location/LocationService.kt` — wraps FusedLocationProviderClient, exposes StateFlow<Location?>
- **New file**: `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` — Compose overlay composable for the marker
- **New file**: `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — options button + dialog composable
- **Modified**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — add overlay + permission handling + options button
- **Modified**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — wire LocationService, expose location state
- **Modified**: `app/src/main/java/com/naviveylin/di/AppModule.kt` — provide LocationService
- **Modified**: `app/build.gradle.kts` — add play-services-location dependency
- **No JNI/C++ changes** — marker is pure Compose overlay
