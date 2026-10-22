# fix-car-search-reference-without-a-map

## Why

Issue #42, verified in the tree today. The spec's fallback distance reference on the car is "the current car
map viewport center" (`openspec/specs/search-result-ranking/spec.md`, requirement "Distance reference for
ordering and display"; mirrored in `openspec/specs/auto-search/spec.md`, scenario "Car uses the same reference
rule as the phone"), and only the screen that owns a renderer could supply it: `MapScreen.kt:607` passes
`viewportCenter = { rendererGate.renderer.value?.markerViewport() }`, while `RootScreen.kt:132` and
`SearchHistoryScreen.kt:142` push `SearchScreen(carContext, navigationViewModel)` — the constructor default is
`viewportCenter: () -> Pair<Double, Double>? = { null }` (`SearchScreen.kt:63`). Their results are therefore
ordered by tier and quality with **no distance at all** (`SearchScreen.kt:187-200`: no fix and no center
resolves to `null`), spec-legal only because a search screen with no map screen behind it has no center to
read. Issue #34 recorded it as "a driver comparing results from the root list has no proximity signal".

**Root cause, one sentence:** the car map viewport center exists only inside the renderer of the screen that
drew it, so a `SearchScreen` constructed by a screen that never held that renderer — the root list, the
history list, and the history tap inside `SearchScreen` itself — has no fallback reference and shows no
distance, although the session did have a car map a moment earlier.

**The red test** (`auto/src/test/java/com/naviveylin/auto/SearchScreenTest.kt`),
`aSearchWithoutAMapUsesTheLastDrawnCarViewportCenter`: no GPS fix, the car surface's last-frame center
published as `52.52, 13.405`, the screen constructed exactly as `RootScreen` and `SearchHistoryScreen` construct
it, one search typed — the reference handed to `AutoSearchProvider.searchLocations` must be
`SearchReference(52.52, 13.405)`.

- HEAD (`evidence/red-HEAD-SearchScreenTest.xml`, `evidence/red-HEAD-focused.log`): `tests="17" failures="1"`
  (17 because the case asserting the specified "neither exists" boundary was added after this run),
  timestamp `2026-10-22T03:37:48.362Z`, the assertion message naming the mechanism —
  `[2]: argument: null, matcher: eq(SearchReference(lat=52.52, lon=13.405)), result: -`.
- after the change (`evidence/green-SearchScreenTest.xml`): `tests="18" failures="0"`, timestamp
  `2026-10-22T03:41:18.185Z` — the post-restore green run. The `03:40:25.791Z` execution's XML was overwritten by
  that run and no longer exists; the timestamp carried by the artifact is the one quoted (`evidence.md`).

**Owning capability:** `search-result-ranking` — the requirement "Distance reference for ordering and display",
modified (the fallback center is now a value of the car surface, not of the screen that happens to hold the
renderer). **Owning guideline section:** `guidelines/UI.md` §3b "Search over the shared history store" — the
car search bullet that stated the old behaviour ("screens opened from the root/history have no map, so their
results are ordered by tier and quality without distances", `UI.md:607-610`).

**Surface, counted before the plan was fixed:** `grep -rn "viewportCenter" auto/src app/src core/src` → 5 hits
in 2 files (3 production lines in `SearchScreen.kt`, 1 in `MapScreen.kt`, 1 case in `SearchScreenTest.kt`);
`grep -rln "viewport center" openspec/specs/` → 10 capability specs mention one, of which 2 state this rule
(`search-result-ranking`, `auto-search`); `grep -rn "viewportCenter\|viewport center" tools/*-cases.sh` → none;
`grep -rn "markerViewport" auto/src` → 1 production call site (`MapScreen.kt`), 1 helper
(`AutoMapRenderer.kt`), 5 case references in `AutoMapRendererTest`. So: **5 cases added, 1 rewritten, 1 fixture
realigned** (the `AutoEntryPoint` stub of `DetailsScreenTest`, whose screen now publishes into the provider on
stop), 4 map screens gaining one publish call each, 1 guideline bullet, 1 capability delta. Review round 1
added 2 of those cases (the stop-path publish and the no-committed-frame publish), added 1 accessor case in
`AutoMapRendererTest`, and strengthened 1 (`historyTapPushesScreenWithQuery`). No resource, no build script, no
native code.

surface: 10 cases added, 1 rewritten, 1 fixture realigned — 3 in `SearchScreenTest`, the 5 of the new
`CarMapViewportTest`, 1 each in `DetailsScreenTest` and `AutoMapRendererTest`; re-read by
`./tools/loop/measure.sh --change 2026-10-22-fix-car-search-reference-without-a-map`

## What changes

- `core/src/main/java/com/naviveylin/core/CarMapViewportProvider.kt` (new): the car surface's last drawn frame
  center, a `:core` singleton in the shape of `DrivingModeProvider`/`FreeDrivingStatusProvider` — retain-on-stop,
  and a non-finite coordinate is dropped.
- `core/.../AutoEntryPoint.kt`: `carMapViewportProvider()`, so the car screens reach the one instance.
- `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt`: the `@Singleton` provider function.
- `auto/src/main/java/com/naviveylin/auto/CarMapViewport.kt` (new): `publishCarViewportCenter(renderer, provider)`,
  the one place the publish rule lives; it reads `AutoMapRenderer.committedViewportCenter()` (new), the center of
  the frame actually on the surface, and never the pending render target `markerViewport()` answers with.
- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`: `committedViewportCenter()`, null until a frame
  has committed.
- `MapScreen.kt`, `DetailsScreen.kt`, `FreeDrivingScreen.kt`, `NavigationScreen.kt`: each publishes the center of
  the frame it stops drawing, as the first act of `onStop` — while that frame is still the displayed one.
- `SearchScreen.kt`: the reference is the screen's own live center when it has one, else the published car
  center; and the history tap passes its own center on to the prefilled screen it pushes (it dropped it).
- `guidelines/UI.md` §3b: the bullet now states the car-wide rule, and that the spec's "neither exists" case
  needs a session whose car map has not drawn a frame.
- `openspec/specs/search-result-ranking/spec.md` (delta): the modified requirement, one added scenario, and the
  `- **Case**:` line of all four.

No dependency, no manifest, no native code, no build file. **Rollback:** delete the provider, the helper, the
four publish calls and the fallback in `searchReference()`; the screens return to reading only their own
renderer, and nothing else moves.

## Out of scope, and why

Distance for a car search on a session that has **never** drawn a map frame stays absent — that is the spec's
specified "neither exists" case, and inventing a reference (the phone's saved viewport, the first installed
map's centre) would show a distance from a place the driver never saw. Freshness of the published center is not
bounded either: the map screen publishes as it stops drawing, which is exactly the moment before the search
opens, so the value cannot be older than the last frame the driver looked at while this screen stack was on top.
