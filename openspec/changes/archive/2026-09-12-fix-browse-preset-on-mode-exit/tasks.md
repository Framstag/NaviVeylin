## 1. Core Implementation (spec: map-modes — navigation end restores prior mode)

- [x] 1.1 Extract shared BROWSE-representation helper in `MapCanvasViewModel.kt` (`applyBrowseRepresentation()`: follow off, `freeFormNorthUp = true`, `viewport.angle = 0.0`, `browseDrifted = false`) and refactor `exitFreeDrive()` to use it. Verify: `:app:compileMobileDebugKotlin` succeeds.
- [x] 1.2 Extend the nav-state restore branch in `setNavigationViewModel` (restore lands in BROWSE): after restoring `followMode`/`driveSuspended`, call the browse-representation helper and `renderMap()`; center and magnification must be untouched (spec: map-modes — "Navigation end keeps the viewport"). Verify: unit tests in 2.1 pass.
- [x] 1.3 Keep the FREE_DRIVE restore branch behavior (follow on, suspension restored, `renderMap()`, center/mag untouched). Verify: unit tests in 2.1 pass.

## 2. Unit Tests (spec: map-modes — navigation end restores prior mode)

- [x] 2.1 Add `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelNavEndRestoreTest.kt` (Robolectric, `FakeOSMScoutClient`, default sandbox): (a) browse-before-nav → nav start → stop → BROWSE with follow off, `freeFormNorthUp=true`, `angle=0.0`, `browseDrifted=false`, center+mag unchanged from routing end; (b) free-drive-before-nav → nav stop → follow on, suspension restored; (c) `stopNavigation()` end-to-end ordering (restore + callback) is idempotent; (d) nav end while suspended mid-nav restores pre-nav suspension. Verify: `:app:testMobileDebugUnitTest` passes for the new class, and the full `:app:testMobileDebugUnitTest` suite still passes (no RegExp of existing map tests).

## 3. Build & On-Device Verification

- [x] 3.1 Build debug APK (mobile flavor) and verify no compile warnings in `MapCanvasViewModel.kt`. Verify: `./gradlew :app:assembleMobileDebug` succeeds.
- [x] 3.2 On-device (phone/emulator): browse → start route → navigate (map rotates heading-up, driving zoom) → stop navigation → map returns to BROWSE with north-up orientation and follow off, stays at the routing end position/zoom, no re-center button. Verify: manual pass + `adb logcat -s NaviVeylin` shows restore + render lines.
- [x] 3.3 On-device: free drive → stop navigation while driving → map stays following (FREE_DRIVE); drive-toggle exit → BROWSE north-up. Verify: manual pass.
