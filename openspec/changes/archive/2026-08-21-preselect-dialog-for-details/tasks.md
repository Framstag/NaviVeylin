## 1. Submodule — upstream preselection feature

- [x] 1.1 Cherry-pick `ace943087` onto `naviveylin-local`; resolve conflicts in `MainController.java`, `SearchOverlay.java`, `DescriptionEntry.java`, `OSMScoutClient.java`, `ObjectDescription.java`, `OSMScoutClient.cpp`, `PoiCategories.java` — keep NaviVeylin's local ranking constants (150 m radius, visibility scoring, `VERY_CLOSE`/`MAX_SMALL_AREA_SIZE`) (Spec: `long-press-details`, `long-press-candidate-picker`)
- [x] 1.2 Verify `ObjectDescription` has identity fields (`objectRefType`, `objectTypeName`, `objectFileOffset`) and `OSMScoutClient` declares `native List<ObjectDescription> getDescriptionCandidates(double, double, int)` (Spec: `long-press-details`)
- [x] 1.3 Verify native build compiles: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` (Spec: `long-press-details`)

> **Note (1.1 follow-up)**: cherry-pick resolution had dropped the upstream POI search pieces (`PoiEntry.java`, `PoiCategories.java`, `searchPOIsByTypes` JNI impl) that the root `osmscout-client-java` module depends on — restored in `0c0a70dc0` + `353954497` (submodule). Without this the app module did not compile.

## 2. JNI — getDescriptionCandidates verification

- [x] 2.1 If cherry-pick conflict resolution is too messy: port manually — extract candidate collection/ranking helper in `OSMScoutClient.cpp`, implement `getDescriptionCandidates` marshaling the full ranked list with identity fields, keep `getDescription` as top-ranked wrapper (Spec: `long-press-details`) — **N/A**: cherry-pick resolution succeeded; `CollectDescriptionCandidates` helper + `getDescriptionCandidates` marshaling already in place
- [x] 2.2 Extend `ObjectDescriptionTest` (JavaScout) for identity fields: refType/typeName/fileOffset populated, default null/0 (Spec: `long-press-details`) — **already covered** by upstream test (`testObjectIdentityFields`, `testLegacyConstructorsDefaultIdentity`)
- [x] 2.3 Verify `getDescription` still returns single best for existing callers (search, POI, favorites) — no behavior change (Spec: `long-press-details`) — verified: `getDescription` returns `candidates.front()` of the same ranked list; search/POI/favorites callers untouched

## 3. Phone — ViewModel + state

- [x] 3.1 Add `candidateDescriptions: List<ObjectDescription>` and `showCandidatePicker: Boolean` to `MapCanvasUiState` (Spec: `long-press-candidate-picker`)
- [x] 3.2 `onLongPress(lat, lon)`: call `client.getDescriptionCandidates(lat, lon, mag)` on `Dispatchers.Default`; empty list → no picker; non-empty → `showCandidatePicker = true` (Spec: `long-press-details`, `long-press-candidate-picker`)
- [x] 3.3 Add `onCandidateSelected(desc)`: set `objectDescription`, `showDetailsSheet = true`, `isLongPress = true`, hide picker; marker at `objectLat`/`objectLon` with fallback to press point on NaN (Spec: `long-press-candidate-picker`, `long-press-details`)
- [x] 3.4 Add `dismissCandidatePicker()`: clear candidates, hide picker, no details (Spec: `long-press-candidate-picker`)
- [x] 3.5 Verify search/POI/favorites flows still use `getDescription` — no regression (Spec: `long-press-details`) — verified: lines 1283/1370/1543 unchanged

## 4. Phone — CandidatePickerSheet UI

- [x] 4.1 Create `CandidatePickerSheet` composable (ModalBottomSheet, `LocationDetailsSheet` pattern): candidate rows in ranking order, each showing name + type + description snippet in search-result format (Spec: `long-press-candidate-picker`)
- [x] 4.2 Wire into `MapCanvasScreen`: long-press → picker; row tap → details sheet; dismiss (swipe/outside/back) → no details (Spec: `long-press-candidate-picker`) — note: details UI is `LocationDetailsDialog` (full-screen), not a sheet; picker is a ModalBottomSheet per design D3

## 5. Phone — tests

- [x] 5.1 Update `MapCanvasLongPressTest` for candidate flow (fake client returns candidate list; empty → no picker) (Spec: `long-press-details`)
- [x] 5.2 New ViewModel tests: candidates state on long-press, `onCandidateSelected` opens details + marker, `dismissCandidatePicker` clears (Spec: `long-press-candidate-picker`) — `MapCanvasViewModelCandidatePickerTest`
- [x] 5.3 New Compose test for `CandidatePickerSheet`: rows rendered in order, select opens details, dismiss shows nothing (Spec: `long-press-candidate-picker`) — `CandidatePickerSheetComposeTest`
- [x] 5.4 Verify existing tests pass: `./gradlew test` (Spec: all)

## 6. AA — tap → candidates + picker screen

- [x] 6.1 `MapScreen.onClick`: call `getDescriptionCandidates(lat, lon, mag)` off-main; size > 1 → push `CandidatePickerScreen`; size == 1 → details screen directly; size == 0 → details screen with coordinates (Spec: `auto-map-destination-picker`)
- [x] 6.2 Create `CandidatePickerScreen` (ListTemplate): candidates in ranking order; selecting one pushes the details screen with the passed `ObjectDescription` (no re-query) (Spec: `auto-map-destination-picker`)
- [x] 6.3 AA tests: `MapScreenTest` tap routing (multi/single/zero) + new picker screen test (Spec: `auto-map-destination-picker`) — `CandidatePickerScreenTest` (mapper + `shouldShowCandidatePicker` routing)

## 7. Build + full verification

- [x] 7.1 Full build all ABIs: `./gradlew :app:assembleDebug` (Spec: all)
- [x] 7.2 Full test suite: `./gradlew test` (Spec: all)
