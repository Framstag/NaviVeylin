# Make Menu Material — Tasks

## 1. Animated map menu (spec: map-menu)

- [x] 1.1 Rewrite `MapMenu` in `MapCanvasScreen.kt` as a custom popup: `Popup` + `AnimatedVisibility` with fade/scale in and out, M3 `Surface` with menu shape/elevation, and `DropdownMenuItem` rows. Verify `:app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` compiles
- [x] 1.2 Add leading icons to menu items (Download Maps, Favorites, Search POIs, About) using M3 `DropdownMenuItem` `leadingIcon`. Verify icons render in the running app
- [x] 1.3 Switch the trigger button from `MoreVert` to hamburger `Icons.Default.Menu`, keeping the `FilledTonalIconButton` style/shadow of other overlay buttons. Verify visually
- [x] 1.4 Ensure dismissal works for outside tap, system back (focusable popup), and item selection. Verify manually on device/emulator

## 2. Layout split: action column left, view column right

- [x] 2.1 Move toaster button to top-left (`align(TopStart)`) in portrait and landscape. Verify position in both orientations
- [x] 2.2 Extract left action column (menu, search, favorites) and right view column (compass, location options, zoom) shared composables; use in both orientation branches (spec: map-canvas-screen, landscape-layout). Verify column order top-to-bottom matches specs
- [x] 2.3 Move re-center (MyLocation) button to bottom-left when visible (`!followMode && gpsFix != NONE`), both orientations. Verify visibility conditions unchanged
- [x] 2.4 Update `MapMenuComposeTest.kt` for the new toaster button + animated menu and verify `./gradlew :app:testDebugUnitTest` passes

## 3. Navigation hints offset (spec: nav-hints-layout)

- [x] 3.1 Introduce shared constant for toaster button start inset; offset `NextTurnOverlay` to start right of the toaster button and cap width against both button columns. Verify hints no longer touch the left display edge and don't overlap buttons during navigation

## 4. Verification

- [x] 4.1 Run `./gradlew test` and confirm all unit tests pass
- [x] 4.2 Run `./gradlew :app:assembleDebug` (all ABIs) and confirm the build succeeds
- [x] 4.3 Manual smoke test: open/close menu with animation, verify all 4 menu actions work, check portrait + landscape layouts, and verify navigation hints placement
