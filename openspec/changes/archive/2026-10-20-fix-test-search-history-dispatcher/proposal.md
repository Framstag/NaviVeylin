# Proposal

## Why

Root cause: `SearchHistoryRepository`'s I/O hop is an **opt-in** seam, and nothing compared the rule "a test wires
it" to the test sources. `app/src/main/java/com/naviveylin/data/SearchHistoryRepository.kt:60` declares
`internal var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default` (`@VisibleForTesting`), and both
`load()` (`:67`) and `record()` (`:125`) run their whole body — **including the `history` publication** — inside
`withContext(defaultDispatcher)`. A test that builds the repository inline and never assigns the seam hands that
work to a real `Dispatchers.Default` thread, which `advanceUntilIdle()` cannot wait for; the assertion that
follows a result selection then reads the pre-selection state. `MapCanvasViewModel.onSearchResultSelected`
(`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:2692-2710`) records the query **before** it
publishes the selection, so it is the path that shows the race first.

The rule itself is recorded — `guidelines/Design.md` §4 ("**MUST**: a dispatcher hop inside a suspend accessor
needs its own test seam … never a test-side timeout or a longer advance") and `guidelines/Build.md` §4 "Result
evaluation", in its test-scheduling-traps bullet. What was missing is a check: the seam exists, seven test files
wire it — `MapCanvasViewModelSearchHistoryTest`, `MapCanvasViewModelSharedLocationTest`,
`MapCanvasViewModelFavoriteSearchTest`, `RoutePanelViewModelSearchHistoryTest`,
`RoutePanelViewModelSearchCancellationTest`, `MapCanvasViewModelFixQualityTest` and
`MapCanvasViewModelViewportRestoreTest` — and 65 of the 72 classes that construct the repository do not.

Evidence: `com.naviveylin.test.SearchHistoryDispatcherRuleTest#noTestLeavesTheSearchHistoryRepositoryIoHopOnAProductionDispatcher`
fails on the base commit `db1fe8e8` — XML `tests="5" skipped="0" failures="1" errors="0"`,
`timestamp="2026-10-20T19:06:42.161Z"`, `searchHistoryDispatcher roots=3 files=441 findings=96`: **96 construction
sites in 65 test classes** leave the seam unwired (`app/src/test/…/data/SearchHistoryRepositoryTest.kt:28`,
`…/di/CrossSurfaceSearchOrderTest.kt:60`, `…/navigation/NavigationEngineStopPathTest.kt:84`, and 93 more under
`ui/map/` and `ui/route/`). The red XML is retained at `.pi/issue-loop/items/78/red-searchhistorydispatcher.xml`
and copied into this change's `evidence/red/`.

Repro: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.test.SearchHistoryDispatcherRuleTest"`.

The item (§123) states 41 sites in `app/src/test/java/com/naviveylin/ui/map/`; the measured scale on this base is
96 sites in 65 classes across `data/`, `di/`, `navigation/`, `ui/map/` and `ui/route/` — true, and roughly twice
the stated size.

Spec: `unit-test-suite-runtime` / "A test that builds the search-history repository wires its I/O hop" and
"An unwired search-history hop is refused from the sources alone" (ADDED by this change)   Guideline:
`guidelines/Build.md` §4 "Result evaluation" (its test-scheduling-traps bullet),
`guidelines/Design.md` §4 (a dispatcher hop needs its own seam).

## What Changes

- **Every test construction of the repository wires the seam** (96 sites, 65 files): `.also { it.defaultDispatcher =
  … }` chained on the construction — `mainDispatcherRule.dispatcher` at 75 sites in the 57 classes that carry a
  `MainDispatcherRule`, and `Dispatchers.Unconfined` at 21 sites in 8 classes that drive no scheduler and drain
  none (`SearchHistoryRepositoryTest` and the seven Compose classes) — the form `FavoritesSheetReorderComposeTest`
  and `HttpUrlFetcherTest` already use for the same seam.
  No production file changes: the seam's production value stays `Dispatchers.Default`.
- **A host case holds the rule to the sources** (`app/src/test/java/com/naviveylin/test/SearchHistoryDispatcherRuleTest.kt`):
  it reads the test sources of every test-bearing module (`:app`, `:auto`, `:core`), reports every construction
  that leaves the seam unwired, and fails naming file and line. Both allowed forms have a control — a chained
  `.also { … }` and a property wired later in the same file — the refused form has a positive control, and a
  further case fails if a declared module root disappears.
- **`unit-test-suite-runtime` gains two requirements** with five scenarios, one per case above.
- **`guidelines/Build.md` §4 "Result evaluation" gains one sentence** in its test-scheduling-traps bullet naming
  the guard, so the rule the case enforces is discoverable from the guideline that owns it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `unit-test-suite-runtime` — two ADDED requirements, "A test that builds the search-history repository wires its
  I/O hop" and "An unwired search-history hop is refused from the sources alone", five scenarios in all. No
  existing requirement is rewritten, and both requirements are scoped to `SearchHistoryRepository` in their own
  text: the sibling dispatcher seams (`FavoriteRepository`, `MapCanvasViewModel`, `RoutePanelViewModel`,
  `AddressBookViewModel` — all `defaultDispatcher` — and `MemoryPressureResponder`, whose field is `dispatcher`)
  are named as outside this change.

## Impact

Additive, test-only: `app/src/test/java/**` (66 files: the new guard plus 65 swept classes) and one sentence in
`guidelines/Build.md` §4. No main source, no build script, no dependency, no manifest, no native file. `:auto`
and `:core` are scanned but contain no construction of this repository.

Rollback: `git revert` is a no-op for behaviour — the sweep only assigns a test hook the repository already
publishes; deleting the guard alone restores the previous (discipline-only) state and leaves the suite green.
