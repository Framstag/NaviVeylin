# Handover — the app-half patch for `fix-search-cancellation` (not yet emitted)

This change's Kotlin/Java half is **applied but uncommitted** in the shared worktree. The shared files also carry
three other workstreams' uncommitted lines, so the commit cannot be done by path. This file is the executable
spec for the patch that lands it; the next session's first act. Written 2026-10-15 at the end of an apply
session, deliberately *not* attempted then — a hand-enumerated artefact over ~20 files is the wrong thing to
produce in the last of a context window, and twice that day a fast artefact would have shipped a defect that a
careful one caught.

## Base

**`base: 3a1c241`** — the routing repair, append-only over the non-compiling `97fb38a`; gitlink `dff960e34`.
State the base SHA in the patch header. Never base on `97fb38a`.

## Scope — this change's lines only

| file | what is mine |
|---|---|
| `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` | the **only shared-hunk file**: my four `com.naviveylin.core.search.*` imports, the `searchGate` field, `onSearchStateChanged`, `searchOnce`, `RouteSearchResult`, and the abandoned `setActiveField` |
| `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` | the places and POI gates, the two state collectors, `searchOnce`/`searchPoisOnce`, `onPoiSearchStateChanged`, the generation-carrying `searchLocations`/`mergeSearchResults` overloads, `performPoiSearch`, `restorePoiViewport`, `setSearchMode` and `clearSearch` abandons |
| `app/src/main/java/com/naviveylin/data/StructuredAddressSearch.kt` | the generation parameter and the standalone wrapper |
| `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` | the provider object (`beginSearch`/`cancelSearch`/`searchLocations(gen)`) |
| `auto/src/main/java/com/naviveylin/auto/SearchScreen.kt` | the car gate, `searchOnce`, `onSearchStateChanged`, the `onDestroy` observer |
| `auto/src/main/java/com/naviveylin/auto/PoiResultsScreen.kt` | the `car-poi` gate, injectable dispatchers, the teardown observer |
| `core/src/main/java/com/naviveylin/core/AutoSearchProvider.kt` | the interface (`SearchRequestClient`) and the standalone default |
| `core/src/main/java/com/naviveylin/core/search/{SearchGate,SearchRequestClient}.kt` | new |
| `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` | the five search hunks (slots, `beginSearch`, `cancelSearch(long)`, the generation parameters, the standalone KDocs) |
| `app/src/test/java/...`, `auto/src/test/java/...` | the new suites and the extended `FakeOSMScoutClient` / `FakeMapScreenClient` |
| `guidelines/{UI,Design,Logging}.md` | the lifecycle paragraphs and the log-line fields |
| `openspec/changes/fix-search-cancellation/**` | the change artifacts and evidence |

**Excluded and must stay out:** `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` — its
uncommitted diff has **0** of my lines (17 added lines are the roundabout workstream's); a hunk for it is a
phantom and its presence is itself a finding. The same for the routing lines of `RoutePanelViewModel.kt`, which
are **committed** in `3a1c241` — quote them for the mirror check, never re-apply them.

## Where to apply and compile it

`.pi/tmp/verify-routing` — a detached worktree at `3a1c241` with the gitlink pinned at `dff960e34`, built by the
routing session. **Caveat:** it has no `local.properties`, so it needs `ANDROID_HOME` from the environment (the
main tree has the file and needs nothing). Compile there, never in the shared tree: a compile in the shared tree
is what made this afternoon expensive, and two concurrent Gradle builds in one project directory void both
tallies (`ki_processing_failures.log` 2026-10-08; `AGENTS.md`, `TODO.md` §40 item 4).

## The two post-conditions, on those two files and only those two

1. **Pre-image:** the uncommitted diff contains my hunk sets and **no routing line added**. Recorded before the
   patch: `RoutePanelViewModel.kt` 0 routing of 71 added lines / 15 search markers;
   `NavigationEngine.kt` 0 routing and 0 search of 21. The next session needs only the post-patch reading to
   have a before/after pair.
2. **Post-image:** the file's *text* carries the defaulted third parameter and the typed event pair — not
   merely "it compiles". A 2-argument and a 3-argument call site both compile against the post-image, so a green
   build cannot say which one is in the tree.

## The two rules this day cost four incidents to learn

- **A token filter cannot classify a hunk.** Two were tried; both classified an import or a signature line as
  zero lines of the other change (a constants-only pattern scored `import com.naviveylin.core.NavigationFailure`
  as routing-free, and my first pass dropped five hunks of my own work for the same reason).
- **Never rewrite history a branch another session is committing to.** Two `git reset --mixed` /
  `git commit --amend` incidents happened in this tree the same day; the append-only repair
  (`3a1c241` over a superseded `97fb38a`) is the answer, and squashing before publication is the operator's call.
