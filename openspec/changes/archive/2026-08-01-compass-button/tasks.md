## 1. State & ViewModel

- [x] 1.1 Add `GpsFixQuality` enum (`NONE`, `POOR`, `GOOD`) to `MapCanvasViewModel`
- [x] 1.2 Add `StateFlow<GpsFixQuality>` computed from `LocationService.location` — `NONE` if null or >5s stale, `POOR` if accuracy > 50m, `GOOD` if accuracy ≤ 50m
- [x] 1.3 Apply 2-second debounce on fix quality changes to prevent ring color flicker
- [x] 1.4 Expose `GpsFixQuality` as state in `MapCanvasUiState` or via a separate flow collected in the composable

## 2. Compass Composable

- [x] 2.1 Create `CompassButton.kt` in `app/src/main/java/com/naviveylin/ui/map/`
- [x] 2.2 Render compass needle on `Canvas` — line with red tip pointing north, rotating via `Modifier.rotate()` or `drawRotate()`
- [x] 2.3 Animate rotation with `animateFloatAsState(rotationDegrees, tweenSpec(300ms))` — rotation angle derived from `mapAngle` in `MapCanvasUiState`
- [x] 2.4 Draw colored ring around compass — light red/green/yellow based on `GpsFixQuality`
- [x] 2.5 Add `Modifier.combinedClickable(onClick, onLongClick)` for short press and long press
- [x] 2.6 Short press handler: enable follow mode + center on current GPS location (or show snackbar if no fix)
- [x] 2.7 Long press handler: toggle orientation — call `onSetFreeFormOrientation`/`onSetNavOrientation` to flip between north-up and follow-direction
- [x] 2.8 Size compass button consistently with other overlay buttons (48dp × 48dp, matching `FilledTonalIconButton` style)

## 3. Wire into Map Screen

- [x] 3.1 Add compass button to `MapCanvasScreen.kt` overlay `Column` — positioned below menu button, above search button
- [x] 3.2 Pass `GpsFixQuality`, `mapAngle`, `followMode`, `freeFormNorthUp`, `navNorthUp`, `isNavigating` state from `MapCanvasViewModel` to compass composable
- [x] 3.3 Wire short press → `viewModel.onToggleFollowMode(true)` + `viewModel.updateCenter(lat, lon)`
- [x] 3.4 Wire long press → toggle orientation via existing callbacks (same as location options bottom sheet)

## 4. Build & Verify

- [x] 4.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` and fix any compilation errors
- [x] 4.2 Run existing unit tests: `./gradlew test`
- [x] 4.3 Run existing instrumented tests: `./gradlew connectedAndroidTest`
- [x] 4.4 Manual verification: compass rotates with map, ring shows correct GPS fix color, short press re-centers, long press toggles mode, mode stays in sync with location options bottom sheet
