# Design — fix-map-canvas-view-model-file-size

## The root cause, in one sentence

`MapCanvasViewModel.kt` holds the ViewModel's state **and** every method of every topic it serves, so the
file's length is the sum of seven topics' code and each of them grows the sum.

## The one fix

The state stays in one object, because Kotlin has no partial class and because
`guidelines/Design.md` §3 puts the state in one `StateFlow` of one `MapCanvasUiState`: a property cannot
become an extension function (an extension property has no backing field). What *can* move is behaviour, so
every member function becomes an `internal fun MapCanvasViewModel.<name>(…)` extension in the sibling file
that owns its topic, and the `init` block's statement groups become named `wire*` functions declared beside
the code they start.

That is a move, not a rewrite. `evidence/line-accounting.log` compares the old file's 2 514 statements with
the new files' 2 514 and finds no line unaccounted for beyond the transformations listed below;
`evidence/member-roster.log` compares 147 member names with 147 extension names and an empty `diff`.

The transformations, complete:

| what | count | why it is forced |
|---|---|---|
| `private fun x` → `internal fun MapCanvasViewModel.x` | 147 declarations, 54 of them `private` | an extension function cannot see a `private` member |
| `private val/var x` → `internal val/var x` | 97 state holders, and the 15 injected constructor parameters | same rule: the moved bodies read them |
| `private const val X` in the companion → `internal` | 20 | the moved bodies read them, now through `MapCanvasViewModel.X` |
| `TAG` → `MapCanvasViewModel.TAG` (and the other companion members) | the companion references in moved code | a companion member is not in an extension's scope |
| `this@MapCanvasViewModel.rendererScope` → the local `rendererScope` renamed `scope` | 3 lines in `initMap` | the label existed only because a local shadowed the property; with the property assignment written plainly, the local needs a different name |
| `poiFitMagnification(entry)` → `poiEntryFitMagnification(entry)` | 1 declaration + 1 call | the package already has a top-level `internal fun poiFitMagnification(...)` in `SearchDialog.kt`; same name would be ambiguous at the 8-argument call sites |
| `init { … }` (634 lines) → 15 `wire*` functions | 15 declarations + 15 calls | the `init` block is the last 634 lines of the file and cannot leave the class any other way |

The `init` block is the one place where the shape (not the statements) changes: its groups of
`viewModelScope.launch { … }` statements become one function per collector group, called from `init` in the
order the statements ran. Initialization order is therefore unchanged, which is load-bearing here: the block
sits after every property declaration on purpose, and a moved statement that ran before another would change
what a collector first observes.

## Why no delta (`.openspec.yaml`: `skip_specs: true`)

No requirement changes. The bound the item names is the `source-size` row's, stated by
`backlog-sensors`'s "A source file's size is a declared check" and by `guidelines/Backlog.md` §9, and this
change does not move it, retune it, or restate it — it drops one path under it. The behaviour the file
implements is stated by the capabilities it already serves (`map-canvas-screen`, `map-modes`, `map-render`,
`search-request-lifecycle`, `poi-search`, `route-map-overview`, `gps-fix-quality`, `smooth-zoom`,
`map-styles`, `dark-mode`, …), none of whose text this change touches, and the cases that assert them keep
their names and their bodies. A delta would have to invent a requirement for "the file is smaller", which is
the row's job, not a capability's.

## Blast radius

- **Inside the module**: `private` becomes `internal`. That is the whole API-surface change. A `public fun`
  stays `public`; an `internal fun` stays `internal`; no signature, no parameter, no return type changes.
- **Inside the package** (`com.naviveylin.ui.map`): nothing to do — a top-level extension is in scope by
  package. The screen, the sheets and the 60 test classes in the package compile unchanged.
- **Outside the package**: an extension function must be imported where a member function did not. Measured
  over the tree (`grep -rl MapCanvasViewModel app/src` → 80 files, 261 mentions), exactly one such call site
  exists: `CrossSurfaceSearchOrderTest.kt` in `com.naviveylin.di`, which gains two imports
  (`cancelScopeForTest`, `mergeSearchResults`). `:auto` and `:core` reference the ViewModel only in comments.
- **The class file's own readers**: the type is still `MapCanvasViewModel` with the same members; only the
  file that declares a given function changed, and `design.md`/`proposal.md` name which.
- **The build**: the same sources compile into both flavours (`mobile`, `automotive`); no new source set, no
  new module, no Gradle change.

## The alternatives the evidence eliminated

1. **Extract the state into collaborator objects** (a `SearchController`, a `CameraController`, …). Eliminated
   on a measurement, not on taste: 61 test classes in `app/src/test/java/com/naviveylin/ui/map` construct the
   ViewModel and assert against its state and its collaborators' calls; splitting the state changes when each
   field is initialized, which is the ordering `init` deliberately depends on (see "The one fix"), and it puts
   a second copy of "who owns this field" beside `Design.md` §3's one-state rule. It is a redesign of the
   ViewModel, i.e. a second change, not the size fix this item asks for.
2. **Move only the pure functions** (the maths: `haversine`, `distanceMeters`, `filterSpeed`,
   `computeTurnBoost`, `findNearestCurve`, `smoothCenter`, `isAngleSame`, `segmentBBox`). Eliminated by
   arithmetic: those functions are 131 lines in total, against the 3 596 that have to leave the file.
3. **Raise the bound** in `tools/sensors/sensors.json`. Refused: the 1 500-line bound is the owner's recorded
   decision of 2026-10-21 (`guidelines/Backlog.md` §9), and the row's own doctrine says an item is one path
   whose number must drop, not a threshold to be retuned by whoever hits it.
4. **Leave the class in one file and delete dead code until it fits.** There is no dead code of that size:
   the file is 5 096 lines of live methods, each referenced from the screen, a sheet or a case.

## Rollback

Revert the change's commit: the seven new files disappear, `MapCanvasViewModel.kt` returns to 5 096 lines,
`CrossSurfaceSearchOrderTest.kt` loses its two imports, and the row's probe prints
`measurement=5096 verdict=violation` again under the same fingerprint. Nothing outside
`app/src/main/java/com/naviveylin/ui/map` and one test file is touched, so no other rollback step exists.

## What the change does not claim

- It does not make the ViewModel smaller in *design* terms: one object still holds the state and still owns
  every topic's behaviour. What shrinks is the file, which is what the row measures.
- It does not reduce coupling between topics; the extension functions call each other across files exactly
  as the members called each other.
- The `internal` widening is not an invitation: it exists because Kotlin has no package-private visibility,
  and it is the minimum that lets one object's behaviour live in more than one file.
