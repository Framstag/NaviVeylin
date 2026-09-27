# browse-drive-modes — Tasks

## 1. State model (spec: map-modes)

- [x] 1.1 Add `MapMode` enum (`BROWSE`, `FREE_DRIVE`, `NAVIGATION`) and a derived `mode` property to `MapCanvasViewModel` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`): `isNavigating → NAVIGATION`, else `followMode → FREE_DRIVE`, else `BROWSE`. Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 1.2 Add `enterFreeDrive()` / `exitFreeDrive()` to `MapCanvasViewModel`: enter applies the drive preset (follow on, auto-zoom on, heading-up via `navNorthUp=false`, center on current GPS position); exit applies the browse preset (follow off, north-up via `freeFormNorthUp=true`, stay at current position). Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 1.3 Add `resetDrivePreset()` to `MapCanvasViewModel`: restores follow on, auto-zoom on, heading-up, driving zoom, and clears `driveSuspended`. Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 1.4 Replace `autoZoomPaused` with `driveSuspended: Boolean` in `MapCanvasUiState`; set it on manual pan/zoom/rotate in FREE_DRIVE (at the existing `disengageFollowMode()` call sites in `MapCanvasScreen.kt` gesture handlers), clear it in `resetDrivePreset()`. Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 1.5 Add `browseDrifted: Boolean` to `MapCanvasUiState`; set on manual pan/zoom in BROWSE, clear on browse re-center. Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 1.6 Add pre-navigation mode snapshot: observe `navState.isNavigating` transitions in `MapCanvasViewModel` scope; on true snapshot the current mode, on false restore it (spec: "Navigation end restores prior mode"). Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 1.7 Update `shouldShowReCenterButton` to the new logic: visible in FREE_DRIVE when `driveSuspended`, in BROWSE when `browseDrifted`, never at start. Verify: `:app:compileMobileDebugKotlin` succeeds.

## 2. Map screen wiring (spec: map-canvas-screen, map-modes)

- [x] 2.1 Add a dedicated Drive mode toggle button (`DriveModeButton`, car icon in BROWSE / exit icon in FREE_DRIVE) to the right-side widget column below the compass; wire it to `enterFreeDrive()`/`exitFreeDrive()`; hidden during NAVIGATION. The compass short-press reverts to the mode-dependent re-center. Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 2.2 Wire the re-center button action per mode: BROWSE → center on GPS position (stay browse, clear `browseDrifted`); FREE_DRIVE → `resetDrivePreset()`. Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 2.3 Add the `locationOptions` slot to the navigation right-side widget column (`MapCanvasScreen.kt` nav branch ~line 1675) so the sheet opens mid-route. Verify: `:app:compileMobileDebugKotlin` succeeds.

## 3. Config sheet (spec: location-options-ui)

- [x] 3.1 Restructure `LocationOptionsOverlay.kt`: header naming the current state ("Browse" / "Free drive" / "Navigation"), active-state section, General section; remove the "Map follows position" toggle. Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 3.2 Browse section shows orientation "North up" / "Free rotation" (`freeFormNorthUp`); Driving section (FREE_DRIVE and NAVIGATION) shows auto-zoom toggle + orientation "Follow direction" / "North up" (`navNorthUp`). Verify: `:app:compileMobileDebugKotlin` succeeds; sheet contains no mode-switch control.

## 4. Persistence (spec: map-modes)

- [x] 4.1 Stop restoring `followMode` from `SettingsStorage` in `initMap` (`MapCanvasViewModel.kt` ~line 950); keep the settings field for lenient decode. Verify: `:app:compileMobileDebugKotlin` succeeds; app starts in BROWSE regardless of persisted `followMode`.

## 5. Guidelines

- [x] 5.1 Update `guidelines/UI.md`: document the mode model (Browse / Free drive / Navigation), the center-button mode switch, drift-triggered re-center semantics, and the config sheet's per-state sections. Verify: doc reflects the new behavior.

## 6. Tests

- [x] 6.1 Add unit tests to `MapCanvasViewModelTest` for the mode derivation matrix (follow × isNavigating → BROWSE/FREE_DRIVE/NAVIGATION). Verify: `:app:test` passes.
- [x] 6.2 Add unit tests for preset application: `enterFreeDrive`/`exitFreeDrive`/`resetDrivePreset` set the expected follow/auto-zoom/orientation/zoom state. Verify: `:app:test` passes.
- [x] 6.3 Add unit tests for `shouldShowReCenterButton` per mode × suspension/drift, and for pre-navigation mode restoration. Verify: `:app:test` passes.
- [x] 6.4 Add Compose tests for the sheet: header text per state, section content per state, no mode-switch control present; plus `DriveModeButtonComposeTest` (car/exit icon per mode, hidden in NAVIGATION). Verify: `:app:test` passes.

## 7. Verification

- [x] 7.1 Build both flavors (`./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`) and verify compilation succeeds without warnings.
- [x] 7.2 Run the full unit test suite (`:core:test`, `:app:test`, `:auto:test`) and verify all existing tests still pass.
- [x] 7.3 On-device (phone): app starts in BROWSE with no re-center button; one tap on the Drive button (car icon) enters FREE_DRIVE (follow, auto-zoom, heading-up, centered); pan/zoom/rotate in drive shows the re-center button; pressing it restores standard drive values and hides the button; the Drive button shows the exit icon while driving. Verify: manual pass.
- [x] 7.4 On-device (phone): in BROWSE, pan/zoom away from GPS shows the re-center button; pressing it centers on GPS and stays in BROWSE. Verify: manual pass.
- [x] 7.5 On-device (phone): open the location-options sheet in each state — header and section match the state; no mode switch present; during NAVIGATION the sheet opens from the nav column and auto-zoom/orientation changes apply immediately. Verify: manual pass.
