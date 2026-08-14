## 1. Dependency & Build Setup

- [x] 1.1 Add `com.google.android.gms:play-services-location` dependency to `app/build.gradle.kts`
- [x] 1.2 Sync Gradle and verify build compiles

## 2. Location Service

- [x] 2.1 Create `app/src/main/java/com/naviveylin/location/LocationService.kt` — Hilt `@Singleton` wrapping `FusedLocationProviderClient`, exposes `StateFlow<Location?>`
- [x] 2.2 Implement `startLocationUpdates()` / `stopLocationUpdates()` with `LocationRequest` (PRIORITY_HIGH_ACCURACY, 1000ms interval, 5m min distance)
- [x] 2.3 Register `LocationService` in `AppModule.kt` as a Hilt `@Provides @Singleton`
- [x] 2.4 Verify `LocationService` compiles and injects without errors

## 3. Location Marker Overlay Composable

- [x] 3.1 Create `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` — Compose overlay composable
- [x] 3.2 Implement accuracy circle: semi-transparent fill + stroke, radius from GPS accuracy projected to screen pixels via `ProjectionUtils.computeScale()`
- [x] 3.3 Implement direction arrow: filled triangle rotated to bearing, ~24dp size, drawn with Compose `Canvas` `drawPath()`
- [x] 3.4 Implement fallback dot: filled circle ~10dp when bearing < 0
- [x] 3.5 Project GPS coordinate to screen position using `ProjectionUtils.geoToScreen()` with current viewport state
- [x] 3.6 Clamp minimum accuracy circle radius to 4dp for visibility at low zoom
- [x] 3.7 Hide marker when GPS position is outside visible viewport

## 4. Wire Location into ViewModel

- [x] 4.1 Inject `LocationService` into `MapCanvasViewModel`
- [x] 4.2 Collect `LocationService.location` StateFlow and expose as part of UI state (or as separate state for the overlay)
- [x] 4.3 Ensure marker re-projects on viewport changes (pan, zoom, rotation)

## 5. Permission Handling

- [x] 5.1 Add `ACCESS_FINE_LOCATION` to `AndroidManifest.xml`
- [x] 5.2 Add permission request logic in `MapCanvasScreen` using `rememberLauncherForActivityResult` with `RequestPermission`
- [x] 5.3 Show rationale dialog when permission was permanently denied, directing user to Settings
- [x] 5.4 Ensure map works normally without location permission (no crash, no error dialogs)

## 6. Location Options UI

- [x] 6.1 Create `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — options button composable positioned near zoom controls
- [x] 6.2 Implement options dialog as a `DropdownMenu`-style popup with a toggle switch
- [x] 6.3 Add `followMode: Boolean` state to `MapCanvasViewModel`
- [x] 6.4 Wire follow mode: when enabled, GPS location updates trigger `updateCenter()` + `renderMap()`
- [x] 6.5 Disengage follow mode on manual pan or zoom in `MapCanvasScreen` gesture handlers
- [x] 6.6 Wire options toggle to set follow mode state
- [x] 6.7 Add `LocationOptionsOverlay` to `MapCanvasScreen` layout

## 7. Integration & Wiring

- [x] 7.1 Add `LocationMarkerOverlay` composable to `MapCanvasScreen` layout, positioned on top of the map Canvas
- [x] 7.2 Wire `LocationService` start/stop to map screen lifecycle (start on composition, stop on dispose)
- [x] 7.3 Verify marker appears, updates, and disappears correctly on permission grant/deny

## 8. Testing & Verification

- [x] 8.1 Build debug APK and verify no compilation errors
- [x] 8.2 Run existing unit tests (`./gradlew test`) — all pass
- [x] 8.3 Manual test: location marker appears with accuracy circle on device with GPS
- [x] 8.4 Manual test: arrow rotates correctly when bearing changes (walking with device)
- [x] 8.5 Manual test: dot shown when bearing unavailable (stationary)
- [x] 8.6 Manual test: marker repositions correctly on pan and zoom
- [x] 8.7 Manual test: permission denied → no marker, no crash, all other features work
- [x] 8.8 Manual test: permission granted after denial → marker appears
- [x] 8.9 Manual test: options button visible on map screen
- [x] 8.10 Manual test: options dialog opens on button tap, shows follow-mode toggle
- [x] 8.11 Manual test: follow mode centers map on GPS position
- [x] 8.12 Manual test: follow mode disengages on manual pan
- [x] 8.13 Manual test: follow mode disengages on manual zoom
- [x] 8.14 Manual test: follow mode re-engages from options dialog
