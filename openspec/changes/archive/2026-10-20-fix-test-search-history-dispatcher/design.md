# Design

## Root cause

`SearchHistoryRepository` publishes its `history` state **inside** the I/O hop
(`SearchHistoryRepository.kt:125`: `mutex.withLock { withContext(defaultDispatcher) { … _history.value = updated
… persist(updated) } }`), and the hop's dispatcher is an opt-in test seam whose production value is
`Dispatchers.Default` (`:60`). A test that constructs the repository and never assigns the seam therefore leaves
a real thread between the call and its state: `advanceUntilIdle()` drains the test scheduler only, so the
assertion that followed a result selection read the pre-selection state (`MapCanvasViewModel.kt:2692-2710`
records before it publishes). The five classes that wire the seam are green; the other 65 are not, and the
failure surfaces as a flake that mimics a defect in whatever change is under test.

## The single fix

Wire the existing seam at **every** test construction (96 sites, 65 classes) and keep it wired with one host
case that reads the test sources. `mainDispatcherRule.dispatcher` at the 75 sites whose class carries a
`MainDispatcherRule` — sharing the scheduler with its `runTest`, which inherits `Dispatchers.Main`'s scheduler
when it is given no argument; `Dispatchers.Unconfined` at the 21 sites in the 8 classes that carry no rule
(`SearchHistoryRepositoryTest` and the seven Compose classes that drain no scheduler), the form
`FavoritesSheetReorderComposeTest.kt:247` and `HttpUrlFetcherTest.kt:56` already use for the same kind of
seam. Production keeps `Dispatchers.Default`; the production ordering stays exactly as it is.

The guard (`app/src/test/java/com/naviveylin/test/SearchHistoryDispatcherRuleTest.kt`) counts construction sites
whose expression never names the seam and fails with file and line. It accepts the two forms the tree writes —
a chained `.also { … }`/`.apply { … }` on the construction, and a construction assigned to a property that the
same file points at the seam later — and refuses the third.

## Alternatives the evidence eliminated

- **Make the dispatcher a constructor obligation** (the item's fix candidate 1). `guidelines/Design.md` §4
  records that production code earns a seam wherever a test must control time; the seam is earned, and
  `internal var … = Dispatchers.Default` is the shape of the five sibling seams a test already points at
  (`FavoriteRepository.kt:101`, `MapCanvasViewModel.kt:431`, `RoutePanelViewModel.kt:228`,
  `AddressBookViewModel.kt:62`, `MemoryPressureResponder.kt:87`). Making one of the six a constructor argument
  adds a `CoroutineDispatcher` binding to the Hilt graph to repair a test harness and makes that one component
  spell its seam differently from its five siblings — and it does not shrink the sweep by one site: all 96
  constructions change anyway (an argument instead of a wiring). Strictly more work, strictly more production
  surface.
- **Await the observable state per test** (fix candidate 3: `uiState.first { it.showDetailsSheet }`). Which
  assertion sits behind a repository path is not visible to grep, so the sites cannot be enumerated and the
  change cannot name one case per scenario; and it repairs only the assertions that happen to await, leaving
  the rest of the 96 exposed. The item's claim is the pattern, not one assertion.
- **Record after the publish in `onSearchResultSelected`** (the fourth candidate the pre-screen added). It
  repairs the one `MapCanvasViewModel` path and leaves `RoutePanelViewModel.kt:427` and every favourite-then-state
  path exposed, and it changes production ordering — the search recorded before the details sheet opens — that
  no recorded decision asks to change (`fix-search-history-chip-replay` states only that `record()` prepends).

## Blast radius

- 65 test classes in `:app` gain one wiring each (96 sites); no test method body changes.
- One new test class, `SearchHistoryDispatcherRuleTest` (5 cases: the rule, the roots control, the two allowed
  forms, the refused form).
- `guidelines/Build.md` §6 "Test-scheduling traps" gains one sentence naming the guard.
- Nothing else: no main source, no build script, no dependency, no manifest, no native file, no `:auto`/`:core`
  change.

Risk carried by the sweep: a class whose `runTest` and repository dispatcher do not share a scheduler would
hang instead of failing. `MainDispatcherRule` installs `Dispatchers.Main` as a `TestDispatcher`, and `runTest`
without an argument takes its scheduler from `Dispatchers.Main` — which is why most of these classes already
pass `mainDispatcherRule.dispatcher` to `runTest` and mix both spellings. The eight classes without a rule get
`Dispatchers.Unconfined`, which executes inline and needs no scheduler at all. Every swept class ran in this
change's verification (168 classes, 1 230 cases, 0 failures), so a hang would have been visible rather than
latent.

## What the change does not cover (stated, not implied)

- The scan reads `*.kt` under a module's `src/test/java`. A repository built by a helper that lives in another
  module, or one constructed by reflection, is outside it.
- The guard lives in `:app`; a `./gradlew :auto:test` run enforces nothing by itself.
- The production ordering `record()`-then-publish is untouched. This change makes the *tests* decide from
  controlled time; it does not claim the production sequence is atomic, and a future test that must know the
  record finished still awaits the observable state.

## Rollback

Delete `SearchHistoryDispatcherRuleTest.kt` and revert the sweep: the suite returns to the previous
discipline-only state and stays green, because the sweep only assigns a hook the repository already publishes.
No data, no schema, no user-visible behaviour and no production code is touched.
