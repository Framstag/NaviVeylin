## 1. Foundation — resource structure and lint gate

- [x] 1.1 Create `app/src/main/res/values-de/strings.xml` with German translations of all existing `values/strings.xml` entries (spec: German is fully supported) and verify `./gradlew :app:assembleMobileDebug` compiles
- [x] 1.2 Create `auto/src/main/res/values/strings.xml` and `auto/src/main/res/values-de/strings.xml` (empty shells, English + German) and verify `./gradlew :auto:assembleDebug` compiles and resources merge into the app build
- [x] 1.3 Enable `HardcodedText` lint check at error severity in `app/build.gradle.kts` and `auto/build.gradle.kts` (spec: all user-facing text is translatable) and verify `./gradlew :app:lintMobileDebug` reports the check is active
- [x] 1.4 Verify lint `HardcodedText` actually flags Compose `Text("...")` literals in this AGP version; if not, add a custom lint check or CI grep gate as fallback (design D4 risk) and verify it fails on a sample hardcoded literal

## 2. App string extraction — map and search UI

- [x] 2.1 Extract hardcoded strings in `ui/map/` (MapCanvasScreen, SearchPanel, LocationDetailsSheet, LocationOptionsOverlay, CandidatePickerSheet, SearchHistorySheet, MiniMap, SpeedWidget, CompassButton, ZoomControls) into `values/strings.xml` + German `values-de/` and convert call sites to `stringResource` (spec: all user-facing text is translatable)
- [x] 2.2 Extract strings in `ui/map/PoiSearchPanel.kt` (spec: POI category names localized) — keep `categoryLabelRes` mapping, add German `poi_category_*` translations, verify `poi_search_radius` format arg still renders
- [x] 2.3 Extract strings in `ui/route/` (RoutePanel, RouteSummaryDialog, FavoritePickerDialog) and `ui/navigation/` (NextTurnOverlay, NavigationStateOverlay, NavigationDetailsOverlay) into resources with German translations (spec: all user-facing text is translatable)
- [x] 2.4 Extract strings in `ui/mapmanager/` (MapManagerScreen, BasemapSection), `ui/favorites/` (FavoritesSheet), `ui/addressbook/` (AddressBookSheet, AddressBookRationaleDialog), `ui/attribution/` (OsmAttributionOverlay), `ui/about/` (AboutDialog, DiagnosticsDialog) into resources with German translations (spec: diagnostics UI text is translated)
- [x] 2.5 Extract remaining app literals in `ui/MainScreen.kt`, `navigation/NavGraph.kt`, `data/` and any other user-facing strings; verify `grep` for `Text("` / `text = "` / `contentDescription = "` in `app/src/main/java` returns only logcat/debug literals (spec: all user-facing text is translatable)

## 3. Auto string extraction

- [x] 3.1 Extract strings in `auto/src/main/java/com/naviveylin/auto/` screens (RootScreen, MapScreen, NavigationScreen, SearchScreen, FavoritesScreen, AddressBookScreen, PoiSearchScreen, PoiResultsScreen, DetailsScreen, RouteDescriptionScreen, PreferencesScreen, AboutScreen, DiagnosticsScreen, CandidatePickerScreen, SearchHistoryScreen, FreeDrivingScreen) into `auto/src/main/res/values/strings.xml` + German `values-de/`, converting to `carContext.getString` (spec: all user-facing text is translatable, phone/Auto label parity)
- [x] 3.2 Replace `PoiSearchScreen.CATEGORY_LABELS` hardcoded map with resource lookups (`categoryLabelRes`-style, fallback to English for unknown IDs) and add German `poi_category_*` translations to `auto` resources (spec: POI category names localized)
- [x] 3.3 Extract strings in auto template mappers and overlays (NavigationTemplateMapper, SearchScreenMapper, FavoritesScreenMapper, AddressBookScreenMapper, PreferencesScreenMapper, NavigationHintsOverlay, SurfaceIndicators, StreetNameLabel, MapStripActions, NavigationScreenActions, DeepLinkParser user-facing text) into resources (spec: all user-facing text is translatable)
- [x] 3.4 Verify `grep` for `setTitle("` / `addText("` in `auto/src/main/java` returns only logcat/debug literals (spec: all user-facing text is translatable)

## 4. Locale-aware formatting

- [x] 4.1 Add shared locale-aware distance formatter (pure function, explicit locale, returns numeric part only) in `util/` and unit tests for de/en decimal separators (spec: locale-aware number formatting)
- [x] 4.2 Migrate `DistanceFormat.formatDistanceKm` (currently `Locale.ROOT`), `NavigationArrowRenderer`, `NavigationHintsOverlay`, `MapManagerScreen` size display, and `PoiResultsScreen` "m away" to the shared formatter + resource unit suffixes (spec: locale-aware number formatting, units are localized resources)
- [x] 4.3 Add unit/`size` resource strings (`distance_unit_km`, `distance_unit_m`, `size_unit_mb`, `distance_away_m`) to both modules with German translations and verify formatting tests pass for de and en (spec: units are localized resources)

## 5. Plurals

- [x] 5.1 Add `plurals.xml` (one/other) to `app` and `auto` resources for count-dependent strings (results, favorites, contacts) with English + German forms and convert call sites to `pluralStringResource` / `getQuantityString` (spec: count-dependent strings use plurals)
- [x] 5.2 Add unit tests asserting singular/plural selection for English and German (spec: count-dependent strings use plurals)

## 6. Guideline update

- [x] 6.1 Add "Internationalisation / Localisation" section to `guidelines/UI.md` (resource-based strings, `stringResource`/`carContext.getString` patterns, locale-aware formatting helper, plurals, lint gate, phone/Auto label parity, RTL hygiene) and verify the section covers every spec requirement (spec: all requirements, config rule: guideline updated in same change)

## 7. Tests and verification

- [x] 7.1 Add resource-completeness test: every string key in `values/strings.xml` exists in `values-de/strings.xml` for both modules (spec: German translation completeness)
- [x] 7.2 Update existing unit/Robolectric/Compose tests that assert English literals (e.g. AboutDialogComposeTest, RoutePanelComposeTest, auto screen tests) to the new resource-based expectations (spec: all user-facing text is translatable)
- [x] 7.3 Add Robolectric tests with `@Config(qualifiers = "de")` verifying German rendering for representative phone and auto screens (spec: German is fully supported)
- [x] 7.4 Run `./gradlew test` and verify all existing and new tests pass (config rule: existing tests still pass)
- [x] 7.5 Run `./gradlew :app:assembleMobileDebug` and `./gradlew :app:assembleAutomotiveDebug` (all 3 ABIs) and verify builds compile without errors and without lint `HardcodedText` failures (config rule: build compiles, no warnings) — both flavors build; lint shows zero `HardcodedText` violations (3 pre-existing unrelated errors: MissingClass, NotificationPermission, AppLinkUrlError)
- [x] 7.6 On-device verification: set device/emulator locale to German, verify phone UI renders German (map screen, search, route panel, about/diagnostics dialog); verify Auto variant on emulator/head unit renders German templates with no truncation (config rule: on-device verification for UI and Auto changes) — verified by user on device
- [x] 7.7 Verify no regressions in unrelated areas: run full test suite and confirm only i18n-related changes (config rule: no regressions)
