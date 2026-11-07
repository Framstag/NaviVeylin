# Proposal — fix-map-canvas-view-model-file-size

## Why

`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` is **5 096 lines**, past the `source-size`
row's bound of 1 500 — item #297, class `improvement`, and the improvement is a non-defect: the row counts
lines per tracked file and files one item per path, and the item closes when that path drops under the bound
(`tools/sensors/sensors.json:70`, `"threshold": 1500, "comparison": "greater-than"`, owned by
`guidelines/Backlog.md` §9 "One row watches a source unit's size", over the owner's recorded decision of
2026-10-21: 1 500 lines per file, tests included).

**Root cause (one).** One file carries the whole phone map view model — its UI state, its sixty-odd state
holders *and* every method of every topic it serves (search, POI, route panel, follow mode, camera, render,
car-session suspension) — so the file's length is the sum of every topic's code, and each topic that grows
there grows that sum.

**Red case — the number the delta must move, taken with the row's own measurement.** A non-defect has no
failing JUnit case to offer unless the build grows a second copy of the row, so the red case is the row's
probe (`evidence/red-probe-head.log`):

```
$ SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[<the entry's condition>]' bash tools/sensors/metrics/source-size.sh
{"path":"app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt","fingerprint":"44a78d48…","measurement":"5096","verdict":"violation","baseline":"","title":"the source file is past the size bound","claim":"… is 5096 lines, past the row's bound of 1500"}
```

On HEAD `c5b58332` with a clean tree, 2026-10-24T21:42:03Z (the probe's own `git status --porcelain` line
reports 0 changed paths). After the split the same probe prints `"measurement":"842","verdict":"clear"`
under the same fingerprint (`evidence/green-probe.log`) — the row's `clear` finding is what closes the item
(`tools/sensors/metrics/source-size.sh`, the clearing pass over the open conditions).

**The invariant the split keeps, and how it is checked.** `evidence/member-roster.log`: 147 member functions
before, 147 extension functions after (`diff` empty, `poiEntryFitMagnification` mapped back to the name the
one forced rename replaces), plus the 15 `wire*` functions the `init` block's statement groups became.
`evidence/line-accounting.log`: **2 514 statements before and 2 514 after**, the multiset identical once the
split's own transformations are normalised away (declaration prefix, property/constructor visibility, a
qualified companion member, an explicit `this` label, the two renamed names). Nothing was retyped.

**The owning requirement.** `backlog-sensors` — "A source file's size is a declared check"
(`openspec/specs/backlog-sensors/spec.md:392`) states the row and the bound; `unit-test-suite-runtime` states
that every test class of the module executes, which is what the roster and the focused run keep whole. The
guideline section the delta answers to is `guidelines/Design.md` §3 "ViewModel & state": one ViewModel per
concern, its state in one `StateFlow` — which is exactly why the state stays in one object and only the
methods move. `guidelines/Build.md` §2a is the iteration protocol this change followed, §4 its warning rule.

## What Changes

The file becomes eight, each under the bound, and no statement changes:

- **`MapCanvasUiState.kt`** (new, 232 lines) — `GpsFixQuality`, `SearchMode`, `MapMode` and the
  `MapCanvasUiState` data class: the state the screen reads, unchanged.
- **`MapCanvasViewModel.kt`** (5 096 → **842 lines**) — the class itself: the constructor's injected
  collaborators, every state holder (a property cannot become an extension: it has a backing field), the
  `init` block (now one `wire*()` call per collector group), `onCleared`, the `companion object` and the three
  top-level angle helpers that already lived at the file's foot.
- **`MapCanvasViewModelMapInit.kt`** (new, 330 lines) — `initMap`, `initializeOffMain`, `MapInitResult`.
- **`MapCanvasViewModelSearch.kt`** (new, 1 139 lines) — the search panel and its request lifecycle, the POI
  sheet, the admin region, the candidate picker, the share-sheet flow, the address book and the favourites
  sheet.
- **`MapCanvasViewModelRoute.kt`** (new, 678 lines) — the route panel and navigation wiring, the route
  overview fit, the manoeuvre focus and camera framing, the details sheet, favourite add/remove.
- **`MapCanvasViewModelFollow.kt`** (new, 1 248 lines) — follow mode, free driving, the mode preset, GPS fix
  quality, the marker/speed/road resolvers, the anchors and orientation.
- **`MapCanvasViewModelCamera.kt`** (new, 419 lines) — viewport and insets, the zoom walk, rotation, the render
  entry points, the car-session suspension and the test hooks.
- **`MapCanvasViewModelSettings.kt`** (new, 446 lines) — the setting toggles, dark presentation and the
  stylesheet load/daylight/symbol-icon path.
- **`app/src/test/java/com/naviveylin/di/CrossSurfaceSearchOrderTest.kt`** — two imports. It calls
  `mergeSearchResults` and `cancelScopeForTest` from outside `com.naviveylin.ui.map`, and an extension
  function has to be imported where a member did not: the one call site in the tree that is not already in the
  package (measured: `grep -rl MapCanvasViewModel app/src` → 80 files, 261 mentions, one of them outside
  `com.naviveylin.ui.map` calls a moved function).

**The one visibility change the split forces.** A member function may see a `private` property, an extension
function may not, so the class's own members become `internal` — module-visible, still invisible outside
`:app` and outside the class's file package for anything but the extension functions themselves. No public
signature changes: a `fun` stays `public`, an `internal fun` stays `internal`.

**Additive, no breaking change.** No behaviour changes, no test changes beyond the two imports, no spec text,
no guideline, no build logic, no manifest, no resource. Rollback path: revert this change's commit — the seven
new files disappear, the 5 096-line file returns, and the red probe's `violation` at `measurement=5096`
reproduces.

**Not a native/JNI change**: no submodule patch, no `:osmscout-client-java` override, no CMake input.

**Scope**: phone, foldable, tablet, Android Auto and AAOS alike — the ViewModel is the phone map's, but the
file is compiled into both flavours, so both are built and both test sets run.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(none) — no requirement changes, so `.openspec.yaml` declares `skip_specs: true` (see `design.md`, "Why no
delta"). The rule this change satisfies is the `source-size` row's, which
`openspec/specs/backlog-sensors/spec.md` already states and which this change does not alter: the bound, the
unit, the scope and the fingerprint are untouched, and the finding that closes the item is the row's own
clearing pass.

## Impact

Production sources (`:app`, both flavours)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — from 5 096 to 842 lines
- `MapCanvasUiState.kt`, `MapCanvasViewModelMapInit.kt`, `MapCanvasViewModelSearch.kt`,
  `MapCanvasViewModelRoute.kt`, `MapCanvasViewModelFollow.kt`, `MapCanvasViewModelCamera.kt`,
  `MapCanvasViewModelSettings.kt` — new, 232 / 330 / 1 139 / 678 / 1 248 / 419 / 446 lines

Test sources (`:app`)
- `app/src/test/java/com/naviveylin/di/CrossSurfaceSearchOrderTest.kt` — two imports for the two moved
  extension functions it calls

Specs and guidelines — read, not edited
- `openspec/specs/backlog-sensors/spec.md` (the row's doctrine), `openspec/specs/unit-test-suite-runtime/spec.md`
  (every test class executes), `guidelines/Backlog.md` §9, `guidelines/Design.md` §3, `guidelines/Build.md`
  §2a and §4

Verification (each task's own step, `tasks.md`)
- the row's probe before and after (`evidence/red-probe-head.log`, `evidence/green-probe.log`)
- the member roster and the statement accounting (`evidence/member-roster.log`,
  `evidence/line-accounting.log`)
- `./gradlew :app:testMobileDebugUnitTest -PjvmTestsOnly -PforceTests --tests 'com.naviveylin.ui.map.*'
  --tests 'com.naviveylin.di.CrossSurfaceSearchOrderTest'` — 838 cases in 115 XMLs, none failing
- `./tools/loop/gate.sh` once, because the profile counts `*/src/*` as a compiled path
