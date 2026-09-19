# Design: fav-order-in-group

## Context

See `proposal.md` — Why. Storage already keeps order: `FavoriteLocationService` holds `FavLocationGroup::favorites` as a `std::vector<FavLocation>`, `Save()` writes it as a JSON array in vector order and `Load()` `push_back`s in array order. The gap is purely a mutator, plus the layers above it:

- `FavoriteLocationService` (`app/src/main/cpp/libosmscout/libosmscout-client/`) has `AddFavorite` (append), `DeleteFavorite`, `RenameFavorite`, `SetStarred`, `SetGroupColor` — no positional mutator.
- `OSMScoutClient.cpp` (submodule, JNI) forwards those operations one-to-one; `FakeOSMScoutClient` mirrors them for host tests.
- `FavoriteRepository` (`app/src/main/java/com/naviveylin/data/FavoriteRepository.kt`) exposes them as suspend functions and re-emits `StateFlow<Map<String, List<FavoriteLocation>>>` after each successful write; the map is a `LinkedHashMap` and the inner lists keep native order.
- `FavoritesSheet` group detail renders the list with `LazyColumn` + `items(favs, key = "fav_${groupName}_${it.name}")`; `StarredChipBar` renders `FavoritesViewModel.starredFavorites`, which `getAllStarredFavorites()` builds by walking groups in map order and favorites in stored order.

Constraints that shape the design: `guidelines/Design.md` §4 (never native on the main thread), §5 (minimal upstreamable submodule patch, app-owned bridge for platform deviations, native contract is API), §3 (presentational state lives in the composable, not the ViewModel), §11 (Robolectric classloader rule for tests that touch the JNI stub), §12 (reuse over reinvention, survive restarts, additive changes); `guidelines/UI.md` §1 (parity: deviations need a platform reason).

## Goals / Non-Goals

**Goals**
- A favorite's position inside its group is user-settable on the phone, persisted, and restored on restart.
- One committed reorder = one JNI call = one file write.
- Every listing surface (group detail, chip bar, AA place list) shows the same stored order.
- No favorites JSON format change; existing files keep their current visible order.

**Non-Goals**
- Group order (blocked by `std::map<std::string, FavLocationGroup>` group storage plus JSON-object group serialization — separate change).
- A chip-specific order independent of favorite order.
- A reorder interaction on Android Auto/AAOS or in the desktop `JavaScout` dialog (API is available to them; UI is out of scope).
- Undo/redo, multi-select, or cross-group moves (a move that changes the group is a delete + add).

## Decisions

### D1: One native positional mutator — `MoveFavorite(groupName, favName, newIndex)`

```cpp
bool MoveFavorite(const std::string &groupName,
                  const std::string &favName,
                  size_t newIndex);
```

Semantics: exclusive lock; look up the group (fail if absent), find the favorite (fail if absent), `erase` it, clamp `newIndex` to `[0, size()]` **after** removal and `insert` at that index; return `true`. Moving to the current index is a successful no-op. Clamping lives in C++ so every caller (Android, desktop, future bindings) gets identical bounds behavior, matching `guidelines/Design.md` §5 "native method contracts are API".

- **Alternative — `MoveFavoriteBefore(group, fav, beforeFav)`**: rejected: needs a second name lookup and an error case for "reference favorite not found" that callers must interpret; the UI already knows the target index from the drop position.
- **Alternative — `SetFavoriteOrder(group, std::vector<std::string> names)`**: rejected: reorders wholesale, so a concurrent client change (desktop dialog writing the same file) can silently drop favorites absent from the caller's list; a single-item move cannot lose data.
- **Alternative — `MoveFavoriteUp/Down`**: rejected: two more native entry points for something the caller computes with `index ± 1`; native surface stays minimal (§5).
- **Alternative — do it in Kotlin (delete + re-add all favorites of the group)**: rejected: O(n) JNI round trips, a non-atomic intermediate state (a crash mid-way loses favorites), and n+1 file writes — contrary to D5's single-write contract. The native store owns order + locking, so the mutation belongs there.

### D2: Order stays in the existing vector / JSON array — no `attributes["order"]`, no schema version

The visible order *is* the serialization order today, so nothing needs to be stored: `Save()` keeps writing the array in vector order. An explicit `order` attribute would require reindexing on every add/delete/rename, a migration for existing files, a tie-break rule for duplicates, and it would still have to be sorted on read — more state, same result.

- **Alternative — integer `attributes["order"]` per favorite**: rejected as above; also collides with the JNI save path, which copies favorite attributes verbatim but rewrites the array from the Java list, so the two order sources could disagree.
- **Alternative — a separate `order` array in the group JSON**: rejected: two representations of one truth (Design.md §12 single source of truth); the favorites array already is that truth.
- **Consequence**: nothing changes for other readers of the file (`JavaScout` desktop dialog round-trips `getFavoriteGroups` → `saveFavoriteLocations`, preserving array order), and old builds read files written by the new one without loss.

### D3: Phone interaction = drag and drop via `sh.calvin.reorderable:reorderable:3.1.0` (user decision)

The group detail `LazyColumn` is replaced by the library's reorderable list with a long-press drag on the row body. Rationale: a proven, maintained Apache-2.0 (permitted shipped) implementation rather than ~120 lines of bespoke pointer/offset arithmetic, aligning with Design.md §12 "reuse over reinvention". Library version is pinned inline in `app/build.gradle.kts` (this repo has no version catalog).

- **Alternative — hand-rolled drag `Modifier`**: rejected by the user; also the riskiest option (variable row height, edge auto-scroll, drag/scroll gesture arbitration) for a list whose rows differ in height once coordinates wrap.
- **Alternative — "Move up"/"Move down" row actions**: rejected by the user (tedious for longer groups).
- **Alternative — separate "Edit order" screen**: rejected by the user; extra surface, and Design.md §12's "separate screen over mode flag" does not apply because the drag needs the real list, not a different lifecycle.
- **Compatibility risk**: the library's 3.x line may require a newer Compose than the pinned BOM `2024.12.01`. Verification step in tasks: if the newest 3.x does not resolve against the BOM, pin the latest 2.5.x that does and record the exact version in `app/build.gradle.kts` and this document.
- **Consequence**: one new shipped dependency → license assets/SBOM/NOTICE regeneration and `checkLicensePolicy` must pass for both flavors.

### D4: Only the released position is committed

During a drag the composable keeps its own working copy of the group's list (a `remember`ed list backed by `mutableStateOf`). On drag end the resulting index is passed to `FavoritesViewModel.moveFavorite(groupName, favName, newIndex)` **once**; the flow-driven state re-emits the authoritative order afterwards. Nothing is sent to the store while the finger moves.

**Drag-end semantics (updated after inspecting the library):** `sh.calvin.reorderable` reports a system-cancelled gesture through the same `onDragStopped` callback as a released drop (`draggable.kt` calls `onDragStopped()` in both `onDragEnd` and `onDragCancel`), so cancel and drop cannot be told apart. The change therefore specifies WYSIWYG semantics: the order shown when the drag ends is the order that gets committed. The cases that must not write are expressed without needing to see the cancel path:

- the same index as before the drag → no commit;
- the drag never started (tap, long-press on the non-draggable header) → no commit;
- the sheet is dismissed mid-drag → the drop is not committed, because the drag end only *records* a pending commit (`remember { mutableStateOf<Reorder?> }`) that a `LaunchedEffect` in the still-composed sheet performs; disposing the sheet cancels that effect before it runs;
- the dragged favorite disappears mid-drag (deleted or renamed) → the pending commit finds no such favorite and is dropped.

- **Alternative — persist on every hover/position change**: rejected: a file write per drag frame, JNI on a hot gesture path, and a file that reflects an abandoned drag.
- **Alternative — optimistic state in the ViewModel during the drag**: rejected: the in-flight order is presentation state (Design.md §3 — don't push purely presentational state into the ViewModel), and it would fight the flow-driven `groups` state on every emission.
- **Alternative — hand-rolled gesture to detect cancellation**: rejected by the user (option A): it would duplicate the library's gesture handling to observe a path the library does not expose, and "cancelled" would then have to be defined and tested by us; the visible-order-committed rule needs no such definition.
- **Alternative — commit from `onMove` (the library's swap callback)**: rejected: `onMove` fires for every hover swap, so it would persist intermediate arrangements, and it does not distinguish the end of the gesture at all.

### D5: One write per committed move

`FavoriteRepository.moveFavorite` follows the existing write pattern: `if (!loaded) return false`, call the native mutator on `defaultDispatcher`, on success `refreshState()` + `persist()` (a single `saveFavoriteLocations` call), on failure return `false` without touching state or disk. The native side never writes the file itself (the JNI mutators only mutate the in-memory service; `Save()` is invoked through `saveFavoriteLocations`).

- **Alternative — native `MoveFavorite` also calls `Save()`**: rejected: two writers for one write path, and the app already persists atomically through the existing JNI save call; a failed save would then be invisible to the caller.
- **Verification**: the fake client counts `saveFavoriteLocations` invocations (spec `fav-ordering` — one write per committed reorder; `fav-service` — move persists once).

### D6: Serialise reorder commits in the ViewModel; document the general write race

`FavoritesViewModel` holds an `isReordering` flag (StateFlow-independent, e.g. a `@Volatile` Boolean or a `MutableStateFlow<Boolean>`); a reorder commit arriving while a move is in flight is dropped (spec `fav-management-ui` — reorder commits are serialised). The native service is mutex-protected, so no corruption is possible either way; the guard protects the *visible* order from an out-of-order refresh.

- **Alternative — a `Mutex` around all repository writes**: rejected for this change: it is a behaviour change for unrelated operations (rename/star/delete), and those already share the same pre-existing interleaving window. Recorded as a `TODO.md` entry instead (see tasks 3.4).
- **Threading model**: drag state updates on the main thread (Compose); the JNI mutation + file write run on the repository's `defaultDispatcher` (`Dispatchers.Default`); the resulting state emission is observed by `FavoritesViewModel`'s `viewModelScope` collector on main. No native call touches the main thread (Design.md §4).

### D7: Chip bar order is derived, not stored

`getAllStarredFavorites()` already walks groups in map order and favorites in stored order, so the chip bar follows the new order with no new state. This change pins that as a requirement (`fav-starred-chip-bar`) and adds a test so a future refactor cannot silently sort chips.

- **Alternative — stored chip order (`starredOrder` array)**: rejected for this change: a second order source, stale entries whenever a favorite is renamed/deleted/unstarred (names are the identity), and the user's request ("order of the start favorites") is satisfied by the single order they now control.
- **Risk**: chip order for groups is still alphabetical (group ordering is a separate change) — chips reorder *within* a group's block, not across blocks. Documented in the proposal's non-goals.

### D8: AA and desktop — read-only parity

The AA `PlaceListTemplate` (`auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt`) already iterates the repository's ordered map and list; no code change is needed, but the requirement is stated explicitly (`auto-favorites`) and covered by a test. Car App Library templates have no drag gesture, so no reorder affordance is offered there — a platform-forced deviation, recorded in `guidelines/UI.md` §1 with the shared order as the parity guarantee.

- **Alternative — an AA "move up/down" action row**: rejected: adds action rows to a driver-facing list for a rare management task, and the phone is the management surface (same split as favorite rename/color, which are phone-only today).

### D9: Failure surfaces are user-actionable, no silent loss

`moveFavorite` returning `false` (or a failed persist) produces a snackbar in the existing `FavoritesUiState.snackbarMessage` channel: "Failed to reorder favorite" / persisting failure. The composable drops its working copy and re-renders from state, so the list never shows an order that was not stored.

- **Alternative — silent revert**: rejected: the user's drag would appear to be undone for no stated reason (Design.md §3 — every async operation has an explicit error state).

## Risks / Trade-offs

- **[R1] New shipped dependency** — the SBOM/license pipeline gains `sh.calvin.reorderable:reorderable`; Apache-2.0 is already permitted, but the generated license assets and `NOTICE` must be regenerated and the gate must pass for both flavors. → Tasks 4.1/7.4; if the gate fails, the version pin is the first thing to revise.
- **[R2] Compose BOM compatibility** — a 3.x library may need a newer Compose than BOM `2024.12.01`. → Resolve first, pin the newest compatible version (fallback 2.5.x), and note the chosen version here.
- **[R3] Item keys during drag** — rows are keyed `fav_<group>_<name>`; identity is the name, so a rename during a drag would break the key. → The drag is abandoned when the dragged favorite disappears (spec scenario); Compose key changes then simply re-render from state.
- **[R4] Order silently lost by a future change to the JNI save path** — `saveFavoriteLocations` rebuilds the service from the Java array; if it ever sorted or grouped differently, order would be lost without touching `MoveFavorite`. → Catch2 + fake-client regression tests assert order survives a full save/load round trip (tasks 1.3, 3.2).
- **[R5] Variable row heights** — drag offset math in the library assumes uniform-ish rows; rows grow when a long name wraps. → Visual verification on device with long names (tasks 9.5).
- **[R6] Drag vs. sheet scroll arbitration** — a long-press drag must not fight the sheet's vertical scrolling or the sheet's swipe-to-dismiss. → On-device verification (task 9.2) plus the library's documented gesture handling; drop the drag if it conflicts.
- **[R7] Desktop client rewrites the file** — `JavaScout` writes through the same JNI save path and preserves array order; a future desktop feature that sorts groups would not affect *favorite* order. → No action; noted so the assumption is explicit.

## Migration Plan

No data migration: the file format is unchanged and the visible order of existing favorites is preserved (insertion order stays the stored array order).

Implementation order:

1. Submodule: header + implementation + Catch2 tests; commit with a minimal, upstreamable message; bump the submodule pointer in the app repo.
2. App-owned bridge: `moveFavorite` declaration in `osmscout-client-java`; `FakeOSMScoutClient` support.
3. Repository + ViewModel wiring, with unit tests.
4. Dependency + license assets, then the reorderable group detail list and Compose tests.
5. Chip-bar/AA order tests, guidelines, on-device verification.

Rollback: revert submodule pointer, Java declaration, Kotlin wiring, and the dependency. Files written by the new build remain valid for the old build (same schema), so no data fix-up is needed.

## Verification

- **Native**: `Tests/src/FavoriteLocationServiceTest.cpp` — move to first/last/middle, index clamping (negative, beyond end), unknown group/favorite, single-favorite no-op, order independent per group with same-named favorites, rename/delete/star keep order, order survives `Save()` + reload; run via the submodule's CTest/`FavoriteLocationServiceTest`.
- **Bridge**: `FakeOSMScoutClient` records the last move arguments and returns a configurable result; existing fake-based suites keep passing under the Robolectric classloader rule (Design.md §11 — do not add `@Config(sdk=…)`/`@GraphicsMode` to classes that load the stub).
- **App**: `FavoriteRepositoryTest` (state re-emission order, one `saveFavoriteLocations` call, failure path writes nothing, not-initialised path), `FavoritesViewModel` reorder guard test, Compose tests for the drag commit, the non-draggable header, a cancelled drag, and that star/rename/delete/tap still work; chip-bar order test; `auto` `FavoritesScreenTest` order assertion.
- **Build gates**: `./gradlew :app:assembleMobileDebug` for all three ABIs (native patch compiles, no Android APIs added → CI Android-free gate), full `./gradlew test`, `checkLicensePolicy` + license-asset generation for both flavors, `openspec validate fav-order-in-group`.
- **On-device (phone)**: drag a favorite to the top, kill and relaunch the app, confirm the order and that `favorites.json` (via `adb shell run-as`) lists the favorites in the new array order; drag inside a group that feeds the chip bar and confirm the chip sequence; check `adb logcat` for absence of main-thread JNI warnings and no StrictMode violations during the write.
- **On-device (car)**: AA/AAOS emulator — favorites place list shows the reordered sequence after a phone reorder, has no drag affordance, and selection still starts the destination-picker flow.

## Guideline review (apply pass, 2026-09-19)

- `guidelines/UI.md` §1: updated — the parity table gains the reorder row and a
  subsection recording why the reorder *interaction* is phone-only while the
  *order* is shared with the car list.
- `guidelines/Design.md`: no supersession. §4 (threading) is followed — every
  JNI call runs on the repository's background dispatcher, drag state stays on
  the main thread; §5 (native boundary) is followed — the mutator is a minimal,
  upstreamable submodule patch with the app-owned declaration in the bridge
  module; §10 (JNI) and §11 (testing) are followed — the index/commit rules live
  in the pure `FavoriteOrder` helper with unit tests, and the JNI stub classloader
  rule is respected (default Robolectric sandbox on every test that touches
  `FakeOSMScoutClient`); §12 (reuse over reinvention, additive changes) is what
  chose the library over a hand-rolled gesture. §11's "extract gesture handlers
  into reusable Modifier factories" targets *our own* gesture code; here the
  gesture belongs to the library and the testable part (the reorder decision) is
  extracted instead, so the principle's intent holds without an edit.
- `guidelines/MapRendering.md`: not affected (no render-pipeline change).
- `README.md`, `AGENTS.md`: no update required — neither documents the favorites
  interaction beyond "JSON persistence via JNI + `FavoriteRepository`", which is
  unchanged; the new dependency is recorded by the generated license inventory and
  `NOTICE` (verified: `reorderable` 3.1.0, Apache-2.0, scope `shipped`).
- Dev note for whoever tests this later: the drag gesture needs the whole
  gesture in one `performTouchInput` block (down → `advanceEventTime` past the
  long-press timeout → moves → up), and a `clickable` **ancestor** of the drag
  handle cancels the drag (it must be a sibling/child, see `FavoriteItem`).
- Two behavior notes from the implementation, both intentional:
  - **Row tap target narrowed**: the favorite row's `clickable` moved from the
    whole row to its content area (name + coordinates), because a clickable
    ancestor cancels the drag handle's long press. Tapping the empty space right
    of the star/rename/delete buttons no longer opens the favorite; no spec
    states the tap target is the full row.
  - **Failure snackbar text**: `FavoritesViewModel.moveFavorite` uses a literal
    English string like its eight sibling snackbars in the same file (the class
    has no `Context`); moving that whole set into string resources is the wider
    cleanup already tracked by `TODO.md` #30, not part of a reorder change.
  - **Test-double fidelity fix**: `FakeOSMScoutClient.getFavoriteGroups()` now
    returns fresh objects per call (like the JNI bridge). Reusing the same
    `FavoriteLocation` instances made an attribute-only change (starring) produce
    an equal map, so the repository's `StateFlow` collapsed the emission and the
    UI never saw it — a fake-only artifact that would have hidden real bugs.

## Open Questions

None blocking. The only implementation-time decision is the library version pin (D3/R2), resolved by resolving the dependency against the pinned Compose BOM before wiring the UI.
