# Tasks — fix-map-canvas-view-model-file-size

No requirement changes, so no task changes a spec; each task names the shipped requirement that holds the
claim it verifies. Parent requirements: `backlog-sensors` — "A source file's size is a declared check" (the
bound and the fingerprint the item closes under) and `unit-test-suite-runtime` — "the module's test classes
execute" (the roster of members the split must keep whole). Guideline: `guidelines/Design.md` §3
("ViewModel & state" — one ViewModel, its state in one `StateFlow`), `guidelines/Build.md` §2a (iteration
protocol) and §4 (warnings).

## 1. Before the split — the number the delta must move, and the invariant it must keep

- [x] 1.1 Take the red number with the row's own measurement rather than a second copy of it:
  `bash evidence/source-size-probe.sh .` on HEAD's tree (the 5 096-line file restored in place, clean
  `git status`) → `"measurement":"5096","verdict":"violation"` under fingerprint `44a78d48…`, the entry's own
  (`evidence/red-probe-head.log`, taken 2026-10-24T21:42:03Z at `c5b58332`, 0 changed paths). Parent:
  `backlog-sensors` — the row files one item per path, over the bound, and closes it when the path drops
  under.
- [x] 1.2 Record HEAD's member roster as the invariant the split keeps: 147 member functions of
  `MapCanvasViewModel` (`git show HEAD:…/MapCanvasViewModel.kt` over the class body, `onCleared` excluded —
  it is the one member that must stay a member, being an `override`) — `evidence/member-roster.log`'s
  "members before" line. Parent: `unit-test-suite-runtime` — every test class of the module executes, and no
  behaviour may vanish into a move.
- [x] 1.3 Count the surface before planning: `grep -rl MapCanvasViewModel app/src` → **80 files, 261
  mentions**, of which exactly one call site lives outside `com.naviveylin.ui.map` and calls a method the
  split moves (`CrossSurfaceSearchOrderTest.kt`, in `com.naviveylin.di`). That number, not the first file
  the plan names, is what task 2.5 is sized against.

## 2. The split

- [x] 2.1 Move the state types out unchanged: `MapCanvasUiState.kt` (232 lines) holds `GpsFixQuality`,
  `SearchMode`, `MapMode` and the `MapCanvasUiState` data class, byte-identical bodies. Verified by 2.5.
- [x] 2.2 Keep in `MapCanvasViewModel.kt` what cannot move: the constructor's injected collaborators, the 97
  state holders (a property cannot become an extension — it has a backing field), the `init` block, `onCleared`,
  the `companion object` and the three top-level angle helpers. 5 096 → **842 lines**. Verified by 2.5.
- [x] 2.3 Move every other member — 147 functions — to the six topic files named in `design.md`
  (`MapCanvasViewModelMapInit.kt` 330, `…Search.kt` 1 139, `…Route.kt` 678, `…Follow.kt` 1 248, `…Camera.kt`
  419, `…Settings.kt` 446) as `internal fun MapCanvasViewModel.<name>`, bodies and KDoc unchanged. Verified by
  2.5, 3.1 and 3.2.
- [x] 2.4 Turn the `init` block's 634 lines into 15 named `wire*` functions, each declared beside the code it
  starts and called from `init` in the order its statements ran — the initialisation order the block depends
  on. Verified by 3.1 (the collectors' cases: car session, dark mode, fix quality, free-driving publish) and
  by 3.2 (every statement accounted for).
- [x] 2.5 Widen the visibility the move forces: the class's `private` members become `internal` (54 of the 147
  functions were `private`, 97 state holders, 20 companion constants), the companion members reached from
  another file are written `MapCanvasViewModel.X`, and the two shadowing names the split forces are renamed
  (`poiEntryFitMagnification`; the local in `initMap` renamed `scope`). No public signature changes: a `public
  fun` stays `public`, an `internal fun` stays `internal`. Verified by the compile in 2.6 and by 3.2.
- [x] 2.6 Compile both sides of the delta:
  `./gradlew :app:compileMobileDebugUnitTestKotlin -PjvmTestsOnly` → `BUILD SUCCESSFUL`, **no `e:` and no `w:`
  line**, so the split adds no warning the warning rule would owe a fix for (`guidelines/Build.md` §4,
  "Warnings").
- [x] 2.7 Add the two imports the one out-of-package call site needs
  (`app/src/test/java/com/naviveylin/di/CrossSurfaceSearchOrderTest.kt`: `cancelScopeForTest`,
  `mergeSearchResults`) — the consequence 1.3 measured. Verified by 2.6 and 3.1.

## 3. Verification — scoped by what changed (production Kotlin, plus one test file)

- [x] 3.1 Run the affected cases: `./gradlew :app:testMobileDebugUnitTest -PjvmTestsOnly -PforceTests
  --tests 'com.naviveylin.ui.map.*' --tests 'com.naviveylin.di.CrossSurfaceSearchOrderTest'` →
  `BUILD SUCCESSFUL` in 1 min 11 s, **838 cases in 115 XMLs, `failures=0 errors=0`**
  (`evidence/focused-tally.log`). The eight XMLs the seams need are copied whole (`evidence/focused-TEST-*.xml`):
  `MapCanvasViewModelFollowModeTest` 24, `MapCanvasViewModelRouteFitTest` 21, `MapCanvasViewModelDarkModeTest`
  9, `MapCanvasViewModelFixQualityTest` 9, `MapCanvasViewModelCarSessionTest` 8, `MapCanvasViewModelFreeTextSearchTest`
  6, `MapCanvasViewModelInitThreadingTest` 4, `CrossSurfaceSearchOrderTest` 1 — all `failures=0 errors=0`.
  Parent: `unit-test-suite-runtime`.
- [x] 3.2 The roster check: the six new files' extension names against 1.2's 147 → `diff` empty, plus the 15
  `wire*` functions the `init` block became (`evidence/member-roster.log`). Parent:
  `unit-test-suite-runtime`.
- [x] 3.3 The statement accounting, which is what makes the move a move: the old file's code lines and the new
  files' are the same multiset once the split's own transformations are normalised away — **2 514 before,
  2 514 after, `diff` empty** (`evidence/line-accounting.log`). Parent: `unit-test-suite-runtime`.
- [x] 3.4 Take the green number with the same probe as 1.1 → `"measurement":"842","verdict":"clear"` under the
  same fingerprint (`evidence/green-probe.log`): the row's clearing pass is what closes the item. Parent:
  `backlog-sensors`.
- [x] 3.5 Run the project's gate once (`./tools/loop/gate.sh`): the loop profile counts `*/src/*` as a compiled
  path, so the gate owes `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` plus the suites the
  `openspec/changes/**` paths pull in. Quoted, not retried. Parent: `build-test-gate`.

## 4. Evidence and archive

- [x] 4.1 Copy every cited XML into `openspec/changes/2026-10-24-fix-map-canvas-view-model-file-size/evidence/`
  and the run's `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/297`, and quote the gate's tallies in
  `evidence/evidence.md`, each beside the `gate-*.xml` that carries it. Parent: `build-test-gate` (a green claim
  has a test result in the tree).
- [x] 4.2 Pointers mechanically: `bash .pi/skills/fix-loop/scripts/evidence-check.sh
  openspec/changes/2026-10-24-fix-map-canvas-view-model-file-size --root .` → see `evidence/evidence.md` §6
  for the report.
- [x] 4.3 `openspec validate 2026-10-24-fix-map-canvas-view-model-file-size --strict` → "is valid";
  `.openspec.yaml` declares `skip_specs: true` with the reason in `design.md` ("Why no delta": no requirement
  changes, so no delta is invented).
- [x] 4.4 Archive the change and confirm the directory exists under `openspec/changes/archive/` — archived as
  `2026-10-24-fix-map-canvas-view-model-file-size`, `skip_specs: true`, so no capability spec is touched and
  there is nothing to merge into one.
- [x] 4.5 Do not commit, do not push, do not touch the tracker: the run's script owns git and the labels.
