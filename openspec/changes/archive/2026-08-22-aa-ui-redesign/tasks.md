## 1. Map screen restructure (spec: auto-map-layout, design D1/D1b)

- [x] 1.1 Replace `MapTemplate` with `MapWithContentTemplate` in `MapScreen.buildTemplate()`, keeping map controller, surface wiring, and gesture handling; verify `./gradlew :auto:compileDebugKotlin` compiles
- [x] 1.2 Move Menu + Search into the left-edge map action strip on the `MapController` (icon-only per the map-strip constraint, generated glyphs in `CarGlyphs`), parked-only host-enforced; verify `MapStripActionsTest` + `MapTemplateFactoryTest` (map strip carries the actions, icon-only)
- [x] 1.3 Move Zoom in/out into the right-edge template action strip, parked-only (unchanged behavior); verify `MapTemplateFactoryTest.templateCarriesRightStrip`
- [x] 1.4 Add Settings to the left map action strip (NOT parked-only — driving-safe, opens the shared settings screen); verify `MapStripActionsTest.settingsIsDrivingSafeAndIconOnly`
- [x] 1.5 Remove the dead "Map" content boxes — content is the smallest the host API allows (single space-title row, no header) via `MapTemplateFactory`; verify `MapTemplateFactoryTest` (no header, minimal content)
- [x] 1.6 Draw the rotating compass rose on the map surface: right-aligned, 48 dp (same size as strip buttons), red north pointer; verify `SurfaceIndicatorsTest`
- [x] 1.7 Replace the surface-drawn button columns with host action strips (always tappable, host-enforced parked-only); delete `MapSurfaceButtons`/`ParkingState`/`ParkingHeuristic` and their tests
- [x] 1.8 Turn the content box into a menu: four clickable rows (Starred favorites, All favorites, Search for POIs, Search history) via `MapTemplateFactory.buildMenuContent`; verify `MapTemplateFactoryTest.menuContentHasFourClickableRows`
- [x] 1.9 Draw a rotating compass rose on the map surface (host strips only support static icons), right-aligned to the display edge next to the right-edge controls; verify `SurfaceIndicatorsTest`
- [x] 1.10 Remove the static compass strip button; strip = Search + Settings + Zoom (host strips are the only reliably tappable chrome — device log proved the AAOS template host never forwards surface gestures, so the surface-drawn zoom experiment was reverted); remove the left map strip menu button; verify `MapZoomControls` deleted + `MapStripActionsTest`/`MapTemplateFactoryTest`
- [x] 1.11 Menu header: app icon (`Action.APP_ICON`) + "NaviVeylin" title; add "Free driving" (stop navigation + re-center), "Diagnostics" and "About" rows to the menu; verify `MapTemplateFactoryTest.menuHasSevenClickableRowsInOrder`
- [x] 1.12 Remove the compass rose from browse mode (navigation-only: `NavigationScreen` keeps rose + speed badge); verify `MapScreen` has no `SurfaceIndicators` call
- [x] 1.13 Fix `LocationService.startFusedUpdates` "invalid null looper" NPE (background-thread warmup): pass `Looper.getMainLooper()`; verify `:app` compiles

## 2. Settings screen (spec: auto-map-layout "Settings dialog reachable while driving", design D3)

- [x] 2.1 Reuse `PreferencesScreen` as the settings entry point pushed from the map Settings button; persistence via `AutoSettingsProvider` covered by `PreferencesScreenTest`
- [x] 2.2 Verify settings content matches the phone dialog minus `keepScreenOn` — `PreferencesScreenMapperTest` asserts exactly the 7 rows (follow mode, browse orientation, navigation orientation, auto-zoom, dark mode, lane hints, render mode) and no `keepScreenOn`
- [x] 2.3 Verify no parked-only guard wraps the settings flow (menu/search/zoom stay parked-only) — covered by `MapStripActionsTest`
- [x] 2.4 Wire shared-settings observation so changes take effect live: `MapScreen` re-reads settings periodically (~5 s) applying follow mode + north-up; `NavigationScreen` re-reads on state change for lane hints + orientation; verify build

## 2b. Favorites, history, search (spec: auto-map-layout "Content box acts as a menu")

- [x] 2.5 Add `starredOnly` filter to `FavoritesScreen` (attributes["starred"]=="true") with a dedicated empty state + title; verify build
- [x] 2.6 Add `AutoSearchHistoryProvider` (core) + Hilt impl over `SearchHistoryRepository` + `AutoEntryPoint` method; verify `:app` compiles
- [x] 2.7 Add `SearchHistoryScreen` (ListTemplate, tap → prefilled search) and `initialQuery` support in `SearchScreen` (`setInitialSearchText` + auto-run); verify build

## 3. Navigation screen layout (spec: auto-map-layout "Action/visualisation strips", design D2/D4)

- [x] 3.1 Split `NavigationScreen` strips via `NavigationTemplateFactory`: `setMapActionStrip` (left) = BACK + icon-only Stop (map strip allows zero custom titles in 1.7.0, so Stop carries a generated stop glyph); `setActionStrip` (right) = Zoom in/out; smoke tests in `NavigationTemplateFactoryTest` (strips set, estimate kept, no host `NavigationInfo`, non-navigating template builds)
- [x] 3.2 Add `SurfaceCallback` to `NavigationScreen` rendering the map via `AutoMapRenderer` (GPS follow, north-up or heading-up per the nav-north-up setting), reusing the `MapScreen` renderer wiring; emulator render check in 5.4
- [x] 3.3 Draw the hint panel on the surface: left-aligned, x-offset `ACTION_STRIP_INSET`, width capped to `surfaceWidth - ACTION_STRIP_INSET - VIEW_STRIP_RESERVE` (design D4/D5); `NavigationHintsOverlayTest` asserts hints never extend into the right strip region
- [x] 3.4 Port the turn-arrow rendering (`NavigationArrowRenderer`) to the surface canvas; draw distance, description, and lane guidance in the panel; `NavigationHintsOverlayTest` covers all `TurnType`/`LaneTurn` symbols and the panel draw
- [x] 3.5 Throttle surface re-renders with the existing `hasStateChanged` gating; `NavigationTemplateMapperTest` (35 tests) covers the unchanged-state gate
- [x] 3.6 Remove the host `NavigationInstruction` panel (hints are surface-only now — no duplicate hint source); verified by `NavigationTemplateFactoryTest.noHostInstructionPanel`
- [x] 3.7 Anchor the hint panel to the host stable area so host chrome never covers the hints; verify `NavigationHintsOverlayTest.panelStaysInsideStableArea`
- [x] 3.8 Draw right-edge visualisation indicators on the navigation surface: compass rose (bearing-based rotation) + speed-limit badge (warning color over the limit), inside the stable area; verify `SurfaceIndicatorsTest`

## 4. Strip geometry constants (design D5)

- [x] 4.1 Add `ACTION_STRIP_INSET` and `VIEW_STRIP_RESERVE` constants (surface px, derived from the phone's 64.dp values) in `SurfaceLayout`; `SurfaceLayoutTest` asserts the reserved regions are disjoint

## 5. Verification

- [x] 5.1 Run `./gradlew :auto:test` and verify all existing + new auto module unit tests pass (factory, overlay, layout, actions suites)
- [x] 5.2 Run `./gradlew test` and verify the full unit test suite still passes (no regression in app/core modules)
- [x] 5.3 Run `./gradlew :auto:assembleDebug` and verify the module builds without errors
- [x] 5.4 Manual device check (real head unit) — completed across device rounds 1–8: menu/settings/search work (ListTemplate fix), zoom works from the host strip (surface gestures confirmed not forwarded — device logcat: zero onClick/onScroll/onScale), menu opens all seven entries, settings reachable while driving, search results selectable, compass rose nav-only, history prefill works; pan remains host-gated (pan-mode); AAOS launch via CarAppActivity verified

## 6. Device round 3 fixes (AAOS host behaviour)

- [x] 6.1 Enable host pan mode: `MapController.setPanModeListener` (hosts only forward pan gestures to the surface while pan mode is active; pan mode also disengages follow); verify `:auto:compileDebugKotlin`
- [x] 6.2 Convert `RootScreen` from `SectionedItemTemplate` to `ListTemplate` (rendered black on AAOS — template not supported in that context) + add back action + `enableBackNavigation`; verify `RootScreenTest`
- [x] 6.3 Convert `PreferencesScreen` (settings dialog) to `ListTemplate`; verify `PreferencesScreenTest`
- [x] 6.4 Convert `FavoritesScreen` to `ListTemplate` with sectioned lists (one per group); verify build
- [x] 6.5 Make search results selectable: row tap starts navigation (click listeners and row actions are mutually exclusive — `ROW_CONSTRAINTS_SIMPLE`); verify `SearchScreen` compiles + existing tests pass
