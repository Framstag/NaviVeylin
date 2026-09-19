# Tasks: fav-order-in-group

Specs: `fav-ordering` (new), `fav-service` (additive), `fav-management-ui` (additive), `fav-starred-chip-bar` (additive), `auto-favorites` (additive). Design: see design.md — D1 (native `MoveFavorite`), D2 (vector/array order, no schema change), D3 (drag via `sh.calvin.reorderable`), D4 (commit on drop only), D5 (single write), D6 (serialised commits + threading), D7 (derived chip order), D8 (read-only AA parity), D9 (error surfacing).

## 1. Native submodule patch (libosmscout)

Specs: fav-ordering (position, invalid/out-of-range targets, per-group scope, stability, single write). Design: D1, D2.

- [x] 1.1 Declare `bool MoveFavorite(const std::string &groupName, const std::string &favName, size_t newIndex);` in `app/src/main/cpp/libosmscout/libosmscout-client/include/osmscoutclient/FavoriteLocationService.h` with a doc comment stating the semantics (0-based target index after removal, clamping, `false` for unknown group/favorite, no-op success for the current index); verify the header still includes only standard headers (no Android APIs) and the client library compiles
- [x] 1.2 Implement `MoveFavorite` in `libosmscout-client/src/osmscoutclient/FavoriteLocationService.cpp`: exclusive lock, group lookup, favorite lookup by name, `erase` + clamped `insert`, `Save()` untouched (persistence stays with the caller); verify the file adds no Android dependency (CI Android-free gate)
- [x] 1.3 Extend `Tests/src/FavoriteLocationServiceTest.cpp` with Catch2 cases: move to first / middle / last; clamp for negative and beyond-end indices; unknown group; unknown favorite; single-favorite group no-op reporting success; same-named favorites in two groups keep independent order; `AddFavorite` appends; `DeleteFavorite` keeps relative order; `RenameFavorite` keeps position; `SetStarred` keeps position; order survives `Save()` + a new service loading the same file
- [x] 1.4 Run the native test target (`FavoriteLocationServiceTest` via the submodule's CMake/Meson test setup) and confirm all cases pass
- [x] 1.5 Commit the submodule change with a minimal, upstreamable message, verify `git status` in `app/src/main/cpp/libosmscout` is clean, and bump the submodule pointer in the app repo (gitlink)
  - Done 2026-09-19: submodule commits `b9fbe0b52` (service) and `b8ca7432f` (JNI entry point), app pointer bumped in `5c078aa` + `a7c08f8`. Neither commit is pushed yet. The submodule tree still shows modifications for the concurrent `client-style-load-resilience` session (`DBInstance`/`DBThread`/`Tests`,`OSMScoutClient.java`/`.cpp` style-load hunks) — those were deliberately left uncommitted and untouched; the `moveFavorite` hunks were staged selectively from the two shared files (see TODO.md #34)

## 2. JNI wrapper and Java API

Specs: fav-service (move delegation). Design: D1, D5.

- [x] 2.1 Implement `Java_com_framstag_libosmscout_client_OSMScoutClient_moveFavorite` in `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`: null-check `getClientData`/`favService`, convert the `jint` index (negative → `0`) to `size_t`, call `MoveFavorite`, release local refs, return the result; no Android APIs, no exceptions crossing the boundary
- [x] 2.2 Declare `public native boolean moveFavorite(String groupName, String favName, int newIndex);` in the submodule Java API (`libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java`) with a doc comment, and mirror the declaration in the app-owned override `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`; verify the override file still compiles into `libosmscoutclientjava.jar`
- [x] 2.3 Extend `FakeOSMScoutClient` (`app/src/test/java/com/framstag/libosmscout/client/`) with `moveFavorite` (record group/name/index, return a configurable result, apply the move to the fake's in-memory groups so reorder tests observe the new order); keep the class under the default Robolectric sandbox config (design R3 of `guidelines/Design.md` §11 — no `@Config(sdk=…)`/`@GraphicsMode`)
- [x] 2.4 Verify the native build for all three ABIs (`./gradlew :app:assembleMobileDebug` with the injected ABI flag for arm64-v8a, armeabi-v7a, x86_64) and confirm the JNI name mapping resolves for each

## 3. Repository and ViewModel wiring

Specs: fav-service (all), fav-ordering (single write, persistence, failure). Design: D5, D6, D9.

- [x] 3.1 Add `open suspend fun moveFavorite(groupName: String, favName: String, newIndex: Int): Boolean` to `app/src/main/java/com/naviveylin/data/FavoriteRepository.kt`: `if (!loaded) return false`; delegate to `client!!.moveFavorite(...)` on `defaultDispatcher`; on success `refreshState()` + `persist()` (one `saveFavoriteLocations` call); on failure touch neither state nor disk; KDoc the contract
- [x] 3.2 Extend the repository tests: state flow re-emits the new order (group list order equals the fake's order), `saveFavoriteLocations` called exactly once per successful move, failure leaves the previous order and performs no save, not-initialised returns `false` without any native call, and a save/load round trip through the fake preserves the order (design R4)
- [x] 3.3 Add `moveFavorite(groupName: String, favName: String, newIndex: Int)` to `app/src/main/java/com/naviveylin/ui/favorites/FavoritesViewModel.kt`: ignore the call while a move is in flight (single in-flight guard), otherwise launch in `viewModelScope`, and on failure set `snackbarMessage` to a user-actionable message (spec `fav-ordering` — persistence failure keeps the app usable); add unit tests for the guard (second commit while in flight is dropped) and for the failure snackbar
- [x] 3.4 Append the pre-existing, unrelated risk found while implementing this change — interleaved repository writes (read-modify-write in JNI + persist) for `renameFavorite`/`setFavoriteStarred`/`deleteFavorite` are not serialised — as a new numbered entry in `TODO.md` (do not fix it in this change)

## 4. Phone UI: reorderable group detail list

Specs: fav-management-ui (all), fav-ordering (position). Design: D3, D4, D9.

- [x] 4.1 Add the dependency to `app/build.gradle.kts` (`implementation("sh.calvin.reorderable:reorderable:<version>")`, inline — this repo has no version catalog); resolve it against the pinned Compose BOM `2024.12.01` and pin the newest version that resolves (fall back to the newest 2.5.x if 3.x requires a newer Compose); record the chosen version in design.md D3; verify Gradle sync/build compiles
- [x] 4.2 Regenerate the license assets for both flavors and run the license gate (`generateLicenseAssetsMobileDebug`/`generateLicenseAssetsAutomotiveDebug`, `checkLicensePolicy`) — the new Apache-2.0 component must appear in the SBOM-derived inventory and the `NOTICE`; verify the license screen still shows the validated inventory
- [x] 4.3 Convert the group detail list in `app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt` to the reorderable list: keep the "Add favorite" row as a non-draggable leading item outside the reordered range, hold the working order in composable state during the drag, and on drop call `viewModel.moveFavorite(groupName, favName, newIndex)` exactly once (design D4 — nothing persisted during the drag)
- [x] 4.4 Add the drag affordance and strings: a drag-handle icon on the favorite row with a content description in `app/src/main/res/values/strings.xml` **and** `values-de/strings.xml` (plus any reorder/failure text added in 3.3); verify the German translation completeness test passes
- [x] 4.5 Abandon an in-flight drag when the dragged favorite disappears (deleted or renamed mid-drag) and when the sheet is dismissed mid-drag (pending-commit + `LaunchedEffect`, design D4), re-rendering from state without committing; verify with a Compose test
- [x] 4.6 Verify the row actions still work after the conversion: star toggle, rename, delete, and row tap (center map + close sheet) keep their behavior, and the search-results list stays non-reorderable in stored order; cover with Compose tests
- [x] 4.7 Add a Compose test for the drag commit: after a simulated drag-and-drop the list shows the new order, `moveFavorite` is invoked once with the expected target index, and the working copy does not fight the flow-driven state (the composable re-renders the authoritative order)
- [x] 4.8 Verify the "Add favorite" header is excluded from the reorder range (dragging never places a favorite above it; holding the header does not start a drag) with a Compose test

## 5. Starred chip bar order

Specs: fav-starred-chip-bar (all). Design: D7.

- [x] 5.1 Add a unit test asserting `FavoriteRepository.getAllStarredFavorites()` returns starred favorites in group order and, within a group, in stored favorite order (guards against a future sort)
- [x] 5.2 Add a Compose test for `StarredChipBar`: after a reorder inside a group, the chip sequence of that group's block follows the new order without reopening the sheet; verify starring a further favorite appends its chip at the end of the group's block

## 6. Android Auto parity (read-only order)

Specs: auto-favorites (all). Design: D8.

- [x] 6.1 Verify `auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt` derives its section and row order from the repository (no sorting) and add a test in `auto/src/test/java/com/naviveylin/auto/FavoritesScreenTest.kt` asserting the group rows follow the stored order and update in place when the store emits a new order
- [x] 6.2 Confirm no reorder affordance exists on the car screen (no drag, no move action) and that favorite selection still starts the destination-picker flow; assert selection behavior in the existing screen test

## 7. Tests and build gates

Specs: all. Design: all.

- [x] 7.1 Run the native test suite for the client library and confirm all `FavoriteLocationServiceTest` cases pass
- [x] 7.2 Run `./gradlew test` (app + auto + core) and confirm the full suite passes, including the existing favorites, chip-bar and auto favorites tests plus the new ones
  - Verified 2026-09-19: `:core` and `:app` (mobileDebug + automotiveDebug) green, 0 failing classes. `:auto` overflowed the unit-test fork's 512 MB heap on the single-JVM run (pre-existing host limit, TODO.md #33 — 54 `OutOfMemoryError` failures, no per-class XMLs, plus one sandbox `ClassCastException` cascade in `FreeDrivingScreenTest`), so it was verified with the recorded workaround: 8 JVMs of 5–6 classes, `--no-build-cache`, **504 tests / 47 classes / 0 failures**
- [x] 7.3 Verify the native patch compiles for all target ABIs (`:app:assembleMobileDebug` without an injected ABI) and that the CI Android-free gate pattern check passes for the touched files
- [x] 7.4 Re-run the license gate for both flavors (`checkLicensePolicyMobileRelease`/`checkLicensePolicyAutomotiveRelease` or the aggregate task) and confirm zero policy findings and a regenerated `NOTICE`
- [x] 7.5 Confirm the build emits no new warnings for the touched Kotlin/Java/C++ files
- [x] 7.6 Generate the coverage report (`:koverHtmlReport :koverXmlReport`) and confirm the new repository/ViewModel/ordering logic is covered (repository move, guard, chip-order helper)
  - Verified 2026-09-19 via `:app:koverXmlReportMobileDebug` (the aggregate task re-runs `:auto` in one JVM and hits the same heap ceiling): `FavoriteOrder.kt` 80/83 lines (96%), `FavoriteRepository.kt` 63% (pre-existing file), `FavoritesViewModel.kt` 68%, `FavoritesSheet.kt` 60%
- [x] 7.7 Run `openspec validate fav-order-in-group` and confirm the change validates; verify every spec scenario maps to at least one task

## 8. Guidelines and documentation

Specs: fav-ordering. Design: D3, D8.

- [x] 8.1 Update `guidelines/UI.md` §1 (`Current parity decisions`) with the phone-only reorder interaction and the platform reason (Car App Library templates cannot drag), stating that the resulting order is shared with AA — parity of data, not of interaction
- [x] 8.2 Check `guidelines/Design.md` (§4 threading, §5 native boundary, §12 additive changes) against the implementation and record "no guideline update required" if nothing is superseded; update the document if the submodule-patch or persistence rules changed
- [x] 8.3 Check `README.md` and `AGENTS.md` for dependency/feature lists that need the new entry (AGENTS.md module/tech-stack tables list the UI stack and native integration; the favorites JSON mechanics are described there — update only if the described behavior changed)
- [x] 8.4 Confirm the spec deltas left the existing requirements untouched (additive-only change) and that no spec references a removed code path

## 9. On-device verification

Specs: fav-ordering, fav-management-ui, fav-starred-chip-bar, auto-favorites. Design: R2, R5, R6, D4, D5.

- [x] 9.1 Phone: open a group with several favorites, drag the last to the first position, confirm the list updates and the star/rename/delete buttons still work; no snackbar error
- [x] 9.2 Phone: repeat the drag with a long-press near the list edge so the list auto-scrolls, and confirm the drag does not fight sheet scrolling or the sheet's swipe-to-dismiss gesture (design R6)
- [x] 9.3 Phone: kill and relaunch the app, reopen the group and confirm the order persisted; inspect the favorites JSON (e.g. `adb shell run-as <applicationId> cat files/.../favorites.json` — use the path `FavoriteRepository.init` receives) and confirm the favorites array is in the new order
- [x] 9.4 Phone: reorder a group that contributes starred favorites and confirm the chip bar shows the new sequence immediately and after the restart
- [x] 9.5 Phone: verify with a long favorite name (wrapped row) and with a group of ~50 favorites that the dragged row follows the finger, rows are not mis-targeted, and the drop lands on the intended index (design R5)
- [x] 9.6 Phone: check `adb logcat` for main-thread I/O or JNI warnings during a reorder and confirm the write happens once per drop (no repeated saves while dragging)
- [x] 9.7 Car (AA/AAOS emulator or head unit): after a phone reorder, the favorites place list shows the new order, offers no drag affordance, and selection still starts the destination-picker navigation flow
- [x] 9.8 Verify the favorites JSON written by the new build still loads in the previous build (rollback path) by reinstalling the previous APK and confirming the group lists the reordered sequence

Verification basis: on-device pass by the user on 2026-09-19 (manual, phone + car). No logcat excerpts or screenshots were captured into this change — the evidence is the user-reported pass.
