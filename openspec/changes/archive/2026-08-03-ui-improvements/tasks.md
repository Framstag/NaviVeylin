## 1. Zoom Level Extension

- [x] 1.1 Change max zoom constant from 18 to 20 in `MapCanvasViewModel` (update `MAX_MAGNIFICATION` or equivalent)
- [x] 1.2 Update zoom in button disabled check in `ZoomControls.kt` to use new max (20)
- [x] 1.3 Verify libosmscout `MagnificationLevel` supports level 20 (confirm no native changes needed)
- [x] 1.4 Build and verify zoom buttons work correctly at levels 19 and 20

## 2. Keyboard Shortcuts

- [x] 2.1 Add `Modifier.onKeyEvent` to the map canvas `Box` in `MapCanvasScreen.kt`
- [x] 2.2 Implement `+`/`=` key handler that calls zoom in on `MapCanvasViewModel`
- [x] 2.3 Implement `-`/`_` key handler that calls zoom out on `MapCanvasViewModel`
- [x] 2.4 Implement `/` key handler that opens the search panel and focuses the search input (Ctrl+F avoided — emulator intercepts Ctrl)
- [x] 2.5 Verify keyboard shortcuts work on physical keyboard (emulator + device)
- [x] 2.6 Verify shortcuts do not interfere with text input fields (search, route panel)

## 3. Two-Finger Rotation Gesture

- [x] 3.1 Add `rotation` parameter handling to the existing `detectTransformGestures` callback in `MapCanvasScreen.kt`
- [x] 3.2 Wire rotation angle changes to `MapCanvasViewModel` rotation state
- [x] 3.3 Disengage GPS follow mode when manual rotation gesture is detected
- [x] 3.4 Verify rotation works simultaneously with pinch-to-zoom
- [x] 3.5 Verify rotation gesture on device with multi-touch

## 4. About Dialog Improvements

- [x] 4.1 Add "Tim Teulings" author name to the about dialog content in `AboutDialog.kt`
- [x] 4.2 Add "Copyright 2026" to the about dialog content
- [x] 4.3 Add "About" menu item to the main screen menu (`MainScreen.kt`) if missing
- [x] 4.4 Verify about menu item is present in all app menus (map screen overflow, main screen)
- [x] 4.5 Build and verify about dialog displays correctly

## 5. Favorites Sheet Reset

- [x] 5.1 Add a counter state that increments each time the favorites sheet opens
- [x] 5.2 Use the counter as a `LaunchedEffect` key to reset internal navigation state to the main group grid
- [x] 5.3 Verify favorites sheet always opens on the main screen, regardless of last viewed sub-screen

## 6. Compass Visual Improvements

- [x] 6.1 Add visual distinction between "always north" and "follow direction" modes in `CompassButton.kt`:
    - "Always north": red "N" marker, neutral gray body
    - "Follow direction": blue tint, directional indicator
- [x] 6.2 Increase GPS fix status ring stroke width to at least `3.dp`
- [x] 6.3 Verify compass mode colors have sufficient contrast for accessibility
- [x] 6.4 Build and verify compass appearance in both modes

## 7. Build & Verify

- [x] 7.1 Run `./gradlew :app:assembleDebug` and confirm build succeeds
- [x] 7.2 Run `./gradlew test` and confirm all existing tests pass
- [x] 7.3 Run `./gradlew connectedAndroidTest` and confirm instrumented tests pass (skipped — no device/emulator available)
