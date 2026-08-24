## 1. Shared resolver (core)

- [x] 1.1 Create `core/src/main/java/com/naviveylin/core/details/DetailsInput.kt` (label, adminRegionHierarchy, postalArea, description, resolvedAddress) and `DetailsResolver.kt` with `resolveTitle`, `resolveAddress`, `resolveArea`, `resolveDestinationName` — logic lifted verbatim from `LocationDetailsSheet.kt` derivation block (incl. `COORDINATE_LABEL_REGEX`, digit-street label detection, reverse-lookup fallbacks, IsIn chain, address suffix composition) — verify `./gradlew :core:compileDebugKotlin` succeeds
- [x] 1.2 Add `core/src/test/java/com/naviveylin/core/details/DetailsResolverTest.kt` covering delta spec scenarios (full address "Hauptstraße 12, 44339 Dortmund", street+house only, reverse-lookup street, digit label as address, title precedence name→address→label→generic, coordinate-label exclusion, area hierarchy→region→IsIn→postal→none) — verify `./gradlew :core:testDebugUnitTest` passes
- [x] 1.3 Verify resolver is a superset of current phone behavior: capture existing phone derivation outputs as test fixtures, assert resolver returns identical values — verify fixtures pass

## 2. AA details list view

- [x] 2.1 Rewire `auto/.../DetailsScreen.kt` address/area/title resolution via `DetailsResolver` (Coordinates → Address incl. postal+city → Area chain), street/address dedup — verify `./gradlew :auto:compileDebugKotlin` succeeds
- [x] 2.2 Rewire `resolveTitle` (name → full address → address-like label → plain label → nameHint → "Location") and `resolveDestinationName` to call the resolver (spec: title fallback scenarios) — verify AA compile succeeds
- [x] 2.3 Replace the pane row builder with `buildAttributeList`: Coordinates → Address → Area → ALL description entries in native order (no cap, host pages; dedup `Location/Address` + `Location/Location`, blank rows skipped) (spec: "All description attributes shown", "Opening hours shown", "Long description paged by host") — verify `./gradlew :auto:compileDebugKotlin` succeeds
- [x] 2.4 Rewire `onGetTemplate`: `MapWithContentTemplate` + `ListTemplate` content (header = resolved title + BACK); actions "Navigate here" + "Show" as clickable list rows — `ListTemplate.addAction` is FAB-icon-only (`maxCustomTitles=0`, verified in car-app 1.7.0 bytecode) and crashed with "Action list exceeded max number of 0 actions with custom titles" on the AAOS emulator; remove `buildDetailsPane`/`MAX_PANE_ROWS`/Pane imports — verify AA compile succeeds
- [x] 2.5 Update `auto/src/test/.../DetailsScreenTest.kt`: all attributes listed (incl. opening hours/phone), no row cap, dedup, labeled rows, title precedence, actions still invoke callbacks — verify `./gradlew :auto:testDebugUnitTest` passes

## 3. Phone rewiring (behavior unchanged)

- [x] 3.1 Replace `LocationDetailsSheet.kt` inline derivation with `DetailsInput` mapping + resolver calls (title/address/area), keeping `displayEntries` merge/dedup — verify `./gradlew :app:compileDebugKotlin` succeeds
- [x] 3.2 Run existing phone tests (`LocationDetailsSheet`/`MapCanvasScreen` related suites) and add mapping-only tests if coverage gap — verify `./gradlew :app:testDebugUnitTest` passes with no behavioral regressions

## 4. Integration verification

- [x] 4.1 Full test pass: `./gradlew test` — verify all modules green (serialized: `./gradlew test --max-workers=1`; parallel runs hit pre-existing UncompletedCoroutinesError flakes in MapCanvasViewModel suite — pass isolated, unrelated to this change)
- [x] 4.2 Smoke build single ABI: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — verify APK assembles (includes AA module compile via `:auto`)
- [x] 4.3 Manual parity check on car display: open same POI in phone details dialog and AA details list — verify identical address/area/title data AND all description attributes (opening hours, phone) visible on both (phone lead); report whether list-over-map (option 2) is viable or switch to option 1 drill-down — verified, option 2 confirmed viable
