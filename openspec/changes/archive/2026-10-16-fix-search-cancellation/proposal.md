# Proposal

## Why

A location search on this install measures **993–4316 ms** per native call — `MapCanvasViewModel.kt:2139`
logs `searchLocations: query='…', candidates=60, limit=60, nativeMs=…`, with recorded values of
`993`, `1606` and `4310` ms (`changes/fix-compound-name-matching/tasks.md:37`,
`archive/2026-10-05-fix-search-history-chip-replay/tasks.md:141,146`). Against a 300 ms debounce that makes
superseded searches the normal case, and today a superseded search cannot be stopped from Kotlin at all.
Three defects follow from that, plus one missing primitive:

1. **An abandoned search keeps running.** Nothing on the Kotlin side reaches the native breaker.
   `MapCanvasViewModel.kt:1303` `.filter { it.query.length >= 2 || it.query.isEmpty() }` drops a one-character
   query emission entirely, so the in-flight search is neither superseded nor cancelled and its results are
   published to a list under the new field text; `clearSearch` (`MapCanvasViewModel.kt:4061`),
   `RoutePanelViewModel.setActiveField` (`:321`) and the car's blank-query branch (`SearchScreen.kt:295`)
   cancel a `Job` only. A `Job` cancel cannot interrupt a JNI frame: `RunSynchronousJob` runs inline on the
   caller thread (`libosmscout-client/src/osmscoutclient/DBThread.cpp:574-582`) and the call has no
   suspension point, so the native work continues after the surface stopped wanting it. The one native
   cancellation entry point that exists, `cancelSearch` (`libosmscout-client-java/src/OSMScoutClient.cpp:4694`),
   is not declared on the compiled bridge override (`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`
   declares only the 3-argument `searchLocations`, `:265`), so no caller can reach it (TODO.md §174).
2. **One global breaker slot is shared by every surface.** `OSMScoutClient.cpp:137-138` holds a single
   file-static `g_searchMutex` / `g_currentBreaker`, and every search breaks it before installing its own
   (`:4141-4152`). Because searches run inline and in parallel, a projection session's car search and a phone
   search are live at once, and the newer one kills the other regardless of which surface asked for it.
3. **Two passes are not abortable at all.** The structured form search never sets the breaker its parameter
   supports (`OSMScoutClient.cpp:3984-4099`, `LocationService.h:83`), and the Marisa free-text pass takes no
   breaker (`OSMScoutClient.cpp:4307`).
4. **The car search screen outlives its own work.** `SearchScreen` never cancels its scope (`SearchScreen.kt:63`
   is the only reference; no `onDestroy`), while its coroutine calls `invalidate()` unguarded
   (`SearchScreen.kt:90,:174,:299`) — a late completion makes a host call from a screen whose started period
   has ended, which `auto/screen-observation` ("A stopped screen performs no renderer or host work", its
   template-invalidation clause) already forbids.

**Root cause and evidence.** Four independent defects, each with file:line above, plus one missing primitive
(a cancelling entry point that cannot be misused). There is no single red-on-HEAD case and no single fix, so
this change is **not `fix-loop` eligible** and is deliberately not shaped as one: it lands a shared primitive
first, then three behavioural repairs on top of it. The specs the defects contradict are
`auto/screen-observation` (defect 4), the search requirements named under Capabilities, and the contract that
does not exist yet — no requirement in the repository mentions cancelling a search, stopping an abandoned one,
or a minimum query length; `location-search:29` and `auto-search:22` pin debounce only.

## What Changes

- **A search-request lifecycle with generations.** A search request gets a generation; the abort predicate is
  "my generation is no longer current **or** my breaker is aborted", so supersede and explicit cancel are one
  mechanism and a late `cancel` of a superseded generation is a no-op by construction.
- **One search slot per surface** (`places`, `poi`, `route`, `car`), so supersede is scoped to the surface that
  asked: a car search no longer kills a phone search, and vice versa.
- **`beginSearch(slot)` / `searchLocations(query, limit, region, generation)` / `cancelSearch(generation)`** on the
  JNI bridge, replacing the parameterless `cancelSearch` and the 3-argument `searchLocations`
  (**BREAKING** for the bridge's own callers — four in-tree call sites, no external consumer).
- **An aborted search returns an empty array** (fail-closed), so a caller that forgets the generation cannot
  display partial results; the existing `nativeMs` line gains `slot=<s> gen=<n> aborted=<bool>`.
- **Abort coverage for the two passes that have none**: the structured form search sets the breaker its
  parameter supports and polls per database; the free-text pass polls before and after each database's pass.
- **One shared search gate in `:core`** owning debounce, minimum query length, generation and abandon, consumed
  by the phone places search, the phone POI search, the route panel and the car `SearchScreen` — replacing the
  three duplicated debounce mechanisms (state-flow operators on the phone map, `Job` + `delay` in the route
  panel and on the car).
- **Abandon wired at every point where nobody wants the result**: the phone search dialog closing, the POI mode
  switch, the route field being cleared or switched, the car's blank query, and the car search screen's
  teardown (which also gains the `onDestroy` that cancels its scope).
- **One minimum query length for every surface (2 characters)**: the car runs 1-character native searches today
  (`SearchScreen.kt:295` gates blankness only) while the phone and the route panel require two — an undocumented
  parity deviation.

Additive at the behaviour level (nothing existing is removed); the bridge API change is breaking for in-tree
callers only.

## Capabilities

### New Capabilities

- `search-request-lifecycle`: the cross-surface contract for one search request — the slot that serialises
  supersede per surface, the generation that identifies a request, the abort predicate that unifies supersede
  and cancel, the rule that a superseded or abandoned search stops promptly and publishes nothing, the
  fail-closed result of an aborted search, the minimum query length, and what the diagnostics line carries. One
  normative home for the rule; the surface specs reference it instead of restating it.

### Modified Capabilities

- `location-search`: "Suggestions-while-type" gains the supersede rule (a request for newer text stops the
  previous one) and the below-minimum behaviour (the results clear instead of holding the previous query's).
- `search-dialog`: "Places mode suggestions" gains what happens below the minimum length and on dialog dismissal
  — the typed list clears and the running search is abandoned, instead of showing results for a query the field
  no longer holds.
- `poi-search`: the POI search runs in its own slot and is abandoned when the POI view is left or the mode
  switches, rather than being cancelled only where a newer POI search replaces it.
- `auto-search`: parity with the phone for the minimum query length and for the cancel/abandon contract on the
  car `SearchTemplate`, including the screen's own teardown.
- `osmscout-jni`: the search API contract — `beginSearch` / generation-carrying `searchLocations` /
  `cancelSearch(generation)`, the abort predicate, the fail-closed empty result, and per-slot isolation.

## Impact

**Native (submodule patch, upstreamable, minimal — `guidelines/Design.md` §5 "patch in one place, never both"):**

- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/search_breaker.h` — new, host-testable policy
  (the `search_scope.h` / `frame_pixel_layout.h` precedent), holding the slot registry, generations and the
  abort predicate.
- `…/libosmscout-client-java/src/OSMScoutClient.cpp` — the registry replaces `g_searchMutex`/`g_currentBreaker`
  (`:137-138`), the new JNI entry points, the form-search breaker, the free-text polls, the fail-closed return
  and the extended log line.
- `…/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java` — the three declarations.
- `app/src/main/cpp/libosmscout/Tests/src/SearchBreakerTest.cpp` — new host cases (newest-wins within a slot,
  cancel-of-current aborts, cancel-of-stale is a no-op, slot isolation, superseded generation reads aborted).
- Submodule commit + gitlink bump (`guidelines/Design.md` §5: pinned SHA bumped after every submodule commit;
  one session owns the submodule and its pushes).

**Bridge override (local override in the `:osmscout-client-java` module, never patched into the submodule):**

- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` — the three
  declarations join the six deliberate deviations TODO.md §174 lists; that entry's blanket parity gate stays
  open and is not part of this change.

**Kotlin:**

- `core/src/main/java/com/naviveylin/core/search/` — the new shared search gate.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (`_searchRequest` pipeline `:1299-1318`,
  `onSearchQueryChanged :2538`, `clearSearch :4061`, POI search `:2820`),
  `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` (`:321-364`),
  `app/src/main/java/com/naviveylin/data/StructuredAddressSearch.kt` (form calls carry the generation),
  `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` (the car search provider's slot),
  `auto/src/main/java/com/naviveylin/auto/SearchScreen.kt` (the gate, the teardown).
- Test doubles and cases: `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` (generation
  and cancel recording), the `:core` gate suite, the phone search suites, `:auto` `SearchScreenTest`.

**Docs:** `guidelines/UI.md` §6a/§6b (the abandon rule per surface), `guidelines/Logging.md` (the new fields on
the existing search line), `guidelines/Design.md` §5 (the bridge API list).

**Follow-up after archive:** the new capability needs its classification in the feature-list inputs
(`guidelines/FeatureList.md` §3/§4) when that report is regenerated; TODO.md §174 stays open.

**Rollback:** revert the Kotlin wiring and the spec deltas — the app returns to debounce-only behaviour. The
native commit is additive (new entry points, no signature of an existing one changed in a way another submodule
consumer uses), so the gitlink can be reverted independently. No data, migration or persisted state is touched.
