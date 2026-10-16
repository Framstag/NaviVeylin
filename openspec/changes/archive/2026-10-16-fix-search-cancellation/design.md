# Design

## Context

See `proposal.md` — Why for the four defects and their file:line evidence. What shapes the approach:

- **The search is synchronous and inline.** `DoSearchLocations` (`libosmscout-client-java/src/OSMScoutClient.cpp:4101`)
  runs its walk inside `data->dbThread->RunSynchronousJob(...)` (`:4185`), and `RunSynchronousJob`
  (`libosmscout-client/src/osmscoutclient/DBThread.cpp:574-582`) holds a `ReadLock` and invokes the job **on the
  caller's thread**. There is no worker queue to drain and no suspension point, which is why a Kotlin `Job`
  cancel cannot reach the work.
- **The cancel machinery half exists.** `g_searchMutex` / `g_currentBreaker` (`OSMScoutClient.cpp:137-138`) with
  "break the predecessor, install mine" at `:4141-4152`; a parameterless `Java_..._cancelSearch` at
  `:4694-4701`; a boolean `cancel` parameter on the name-form overload (`:3268-3280`). The boolean's real
  meaning is "break the predecessor and do **not** install a breaker for me" — a cancel mechanism that cannot
  name the request it cancels, which is the defect this design removes.
- **The bridge's Java side is two files.** The app compiles the override
  (`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`), which declares only
  the 3-argument `searchLocations` (`:265`) and its own six deviations including `searchLocationByForm` (`:283`).
  The submodule's own copy
  (`libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java`) is what the in-tree
  JavaScout demo compiles against and declares the parameterless `cancelSearch` (`:309`) and the boolean form
  (`:287`). Both files are in the submodule's build, so a signature change touches both plus the demo's two call
  sites (`JavaScout/src/main/java/com/framstag/libosmscout/SearchOverlay.java:181,:546`).
- **Native policy lives in small host-testable headers** in the same directory (`search_scope.h`,
  `frame_pixel_layout.h` + `Tests/src/FramePixelLayoutTest.cpp`) — the precedent this design follows, and what
  makes the new rule decidable without a device.
- **The car surface's own rules already forbid the current teardown behaviour**: `auto/screen-observation` —
  "A stopped screen performs no renderer or host work" names template invalidation explicitly.

## Goals / Non-Goals

**Goals:**

- One abort primitive that is correct under concurrency and cannot be misused by a late caller.
- Supersede scoped per surface; abandon possible with no successor.
- Every pass of a search abortable at a bounded granularity.
- One Kotlin seam for debounce, minimum length, generation and abandon across all four surfaces.
- The rule testable on the host JVM / host build, not only on a device.

**Non-Goals:**

- Speeding up the search itself (index structure, caching, prefix reuse, candidate-budget tuning) — out of
  scope, see `proposal.md` — Why for the measured cost that motivates but does not cause this change.
- The blanket native-parity gate of `TODO.md` §174; the new declarations join that entry's deviation list.
- Any change to the search *result* pipeline (ranking, marking, rows) or to result ordering.
- Reworking the route-calculation breaker (`ClientData::breaker`, `OSMScoutClient.cpp:455-456`), which is a
  separate mechanism with a separate mutex.

## Decisions

### D1 — A generation per request, not a flag and not a parameterless cancel

The abort predicate is `aborted(gen) = gen != current(slot) || breaker(gen)->IsAborted()`. `cancel(gen)` breaks
only when `gen == current(slot)`.

Alternatives the evidence eliminates:

| option | why it is impossible |
|---|---|
| Keep the parameterless `cancelSearch()` and call it from the Kotlin `Job`'s cancellation handler | The cancellation handler has no suspension point to run at: it executes only after the blocking JNI frame returns, i.e. after the successor search has installed its breaker. With `search A` superseded by `B`, A's handler then calls a parameterless cancel and **breaks B** — the live search, mid-flight. A cancel that cannot name its request is unsound by construction, not by misuse. |
| A boolean "cancel requested" flag reset at the start of every search | Same failure: the late writer cannot be distinguished from the current one; and it cannot express "I am superseded", so the two cases need two mechanisms again. The existing boolean parameter (`:3268-3280`) is this option already implemented, and the demo's use of it (`SearchOverlay.java:546`) shows the result: no request identity anywhere. |
| A `CancelableFuture` through `AsyncWorker` (`libosmscout/include/osmscout/async/AsyncWorker.h:115`) instead of a registry | Structurally sound — the worker registers a breaker per job and breaks it on shutdown (`AsyncWorker.h:120-141`) — but it converts the search to a callback-based asynchronous job, changing the JNI surface (a Java callback per search), the phone pipeline and the car provider at once, for the same abort semantics. Out of proportion to the defect; recorded, not chosen. |
| Generation-stamped (chosen) | Identity is explicit, ordering is decided by one mutex, and "cancel" and "supersede" collapse into one predicate. A late `cancel(oldGen)` is a no-op *by definition*, which is what makes `finally { cancel(gen) }` safe. |

### D2 — One slot per surface, keyed `places` / `poi` / `route` / `car` (and, added during apply, `car-poi` for the
car's POI results screen, which the `poi-search` wording covers as "a POI surface" and which is the one car search
a person can drive by taps alone)

The registry holds one `{generation, breaker}` entry per slot; supersede is `Begin(slot)`.

Alternatives the evidence eliminates:

| option | why it is impossible |
|---|---|
| Keep the single file-static `g_currentBreaker` (`:137-138`) | `RunSynchronousJob` runs inline on the caller's thread (`DBThread.cpp:574-582`), so a projection session's car search and a phone search are genuinely concurrent — measured by construction, not hypothesis: nothing serialises them, and the breaker is one slot. Today the newer search kills the other regardless of surface; stated as intended it would mean the driver's result list dies when the passenger types. |
| A registry per `OSMScoutClient` instance with one unkeyed entry | Identical to the status quo in this app (one client), so it fixes nothing while looking like a fix. |
| Keyed per surface (chosen) | Four keys, one map, no extra concurrency: a slot still aborts its own predecessor at the next poll. The car and the phone cannot disturb each other, which is also the parity statement the specs make. |

### D3 — An aborted search returns an empty array (fail-closed)

Alternatives: return the partial result and document "the caller must check the generation" — eliminated
because that is precisely the failure mode observed: a caller that keeps the result (the abandoned-search case,
where the Kotlin job was cancelled and the publish skipped only because `withContext` rethrows) leaves a stale
list, and a future caller has no forcing function. Returning `[]` makes the safe path the default path, and the
generation in the log line keeps an aborted search distinguishable from a genuine no-match.

### D4 — One shared search gate in `:core`, not per-site wiring

The gate owns debounce, minimum length, the current generation per surface, and `abandon()`. Callers supply a
`CoroutineScope` (the ViewModel's, the route panel's, the car screen's) and a search function; the gate returns
nothing but state.

Alternatives the evidence eliminates:

| option | why it is impossible |
|---|---|
| Wire `beginSearch`/`cancel` into the current three pipelines | There are three debounce mechanisms today (state-flow operators at `MapCanvasViewModel.kt:1299-1318`, `Job` + `delay` at `RoutePanelViewModel.kt:337-338`, `Job` + `delay` at `SearchScreen.kt:131-133`) and four places that must remember to abandon. The one-character defect is a *pipeline* defect (`:1303`) that would then have to be fixed per pipeline, and the next surface re-introduces the gap. The spec states one behaviour for every surface; three implementations make that unverifiable. |
| Put the gate in `:app` and let `:auto` depend on it | `:auto` does not depend on `:app` (module layering, `guidelines/Design.md` §1); the gate belongs next to the other shared search code in `:core` (`com.naviveylin.core.search`), beside `SearchResultRanker`, `FavoriteSearchMerger` and `SearchQueryParser`. |
| One gate per surface (chosen as one class, four instances) | A single instance shared across surfaces would collapse the four slots back into one (D2's defect) and one surface's abandon would cancel another's search. |

### D5 — Abort coverage: the form search gets the breaker its parameter already supports; the free-text pass polls per database

`DoSearchLocationByForm` (`:3984-4099`) never calls `SetBreaker`, although `LocationFormSearchParameter` has
`SetBreaker`/`GetBreaker`/`IsAborted` (`libosmscout/src/osmscout/location/LocationService.cpp:176-188`) and the
string search already uses them (`:318`, and the abort checks in the search flow at `:1934-2087`).
`TextSearchIndex::Search` (`:4307`) takes no breaker at all.

Alternatives: leave both (eliminated — the form pass is entered exactly for the slow, address-like queries
(`StructuredAddressSearch.kt:29-31`) and is a second full walk; the free-text pass is the last thing a slow
query does); wrap the free-text pass in a separately cancelable job (eliminated — the index API has no breaker
hook, so the only honest bound is "between databases", and that is what is specified).

### D6 — The registry lives in `ClientData`, not in a file static

Alternatives: keep the file-static `g_searchMutex`/`g_currentBreaker` (smallest diff, but it is the shared global
that makes D2 impossible, and it survives `close()`); per-`ClientData` member mutex + map (chosen): the slot
state has the same lifetime as the search it belongs to, there is no hidden global, and the JNI functions
already fetch `ClientData` at their top.

### D7 — Kotlin obtains the generation *before* the blocking call

`beginSearch(slot)` returns the generation; the search call carries it. Alternative: return the generation
together with the result — eliminated: a cancel must be possible while the call is running, so the caller has to
know the identity before it starts.

### D8 — `finally { cancel(gen) }` is allowed only because of D1

The gate starts a search, holds the generation, and cancels it in the search's `finally` — covering the
"superseded while a native call was still returning" tail without a race, because `cancel(oldGen)` is a no-op
once a successor owns the slot. Alternative: cancel only from explicit abandon points (eliminated — the tail
would then depend on every call site remembering, which is D4's rejected option again); alternative: cancel
from a `Job.invokeOnCompletion` handler (same object as the `finally`, less readable about ordering).

### D9 — The minimum length lives in the gate, not in the native API

One rule (2 characters) for all four surfaces, enforced where the query arrives. Alternatives: enforce it in
`DoSearchLocations` — eliminated: the native search's contract is "search what you are given" (it returns an
empty array for an empty query already, `:4119-4123`), and the minimum is an interaction rule; enforcing it
natively would hide the surface difference the spec forbids without expressing it. Leaving the car's blank-only
gate (`SearchScreen.kt:295`) — eliminated: it is the parity defect itself.

### D10 — The car screen cancels its scope in `onDestroy`

`SearchScreen` gains the `onDestroy` every other stateful car screen has (`RootScreen.kt:39-40`,
`PoiSearchScreen.kt:44-45`). Alternatives: wrap `invalidate()` in `guardedHostCall` — eliminated: that confines
the fault while still making the host call from a screen whose started period ended, which
`auto/screen-observation` forbids; and it leaves the native search running. Cancelling the scope covers both.

### D11 — The submodule's Java API and the demo change together, in one submodule commit

`beginSearch(String slot)`, `searchLocations(query, limit, adminRegionHandle|defaultRegion, generation)`,
`searchLocationByForm(…, generation)` (app deviation, override + C++ only), and
`cancelSearch(long generation)` replacing the parameterless form. `JavaScout/SearchOverlay.java` is updated in
the same commit (`:181`, `:546`), so the submodule's own sources stay consistent and the patch stays
upstreamable. Alternatives: keep the parameterless `cancelSearch()` beside the new one (eliminated — two
competing cancel mechanisms, one of them unsound, is the debt being removed); deprecate the boolean overload
only (eliminated — it leaves the demo unported and the boolean reachable).

**Transitional overloads — as built (apply, tasks 2.5/2.6).** The two forms the app still
calls are kept on the **Java** side, not as JNI overloads: the override's
`searchLocations(query, limit, adminRegionHandle)` and `searchLocationByForm(…, 5 args)` (and
the submodule's `searchLocations(query, limit[, defaultRegion])` and `searchPOIs(...)`)
begin a request, call the generation-carrying native and end it in a `finally`. Reason the
JNI-overload variant was dropped during apply: a second native with the same Java name
requires a hand-written mangled symbol (`…__Ljava_lang_String_2IJ` and friends), and no
build in this environment checks that name — the app's tests go through
`FakeOSMScoutClient`, so a wrong mangling would surface only as a device-time
`UnsatisfiedLinkError`. A Java wrapper is checked by `javac` on every build. The C++
therefore keeps exactly one native per entry point, and the transitional forms disappear
when the app owns the request (task 5.1). Their semantics stay the design's: beginning a
request supersedes the predecessor, so today’s behaviour is preserved while every
intermediate state builds.

## Threading model and lifecycle

| component | thread / dispatcher | lifecycle |
|---|---|---|
| `SearchBreakerRegistry` (native) | any caller thread; its own `std::mutex` is taken for `Begin`/`Cancel`/`IsAborted` only — **never across the search walk**, which is what keeps the poll cheap | one per `ClientData`, destroyed with the client |
| the search walk | unchanged: inline on the caller's thread inside `RunSynchronousJob` | bounded by the polls (per database, per scope region, per form pass, around each free-text pass) |
| `SearchGate` (`:core`) | constructed with the caller's `CoroutineScope`; the native call stays on `defaultDispatcher` (phone) / `ioDispatcher` (car) exactly as today; state publication stays on the caller's dispatcher (main for the car screen's fields) | `abandon()` is callable from any thread — it cancels the `Job` and calls `cancelSearch(gen)`; the ViewModel's gate ends with `onCleared`, the car screen's with its new `onDestroy` |
| car `SearchScreen` | unchanged: `carScreenScope` (main dispatcher + fault handler) | + `onDestroy` cancelling the scope (D10), which also stops a late `invalidate()` |
| phone POI | unchanged: `viewModelScope` + `defaultDispatcher` | its own slot; abandoned by the gate on mode switch / view close |

## Risks / Trade-offs

- **Concurrent searches against one `DBInstance`'s location index.** [Risk] Slot isolation makes parallel
  searches *normal* rather than accidental. [Mitigation] They already happen today — `RunSynchronousJob` does not
  serialise and an abort is not instantaneous — so this change does not introduce the concurrency; if the index
  turns out not to be read-safe, the fix is one mutex per slot inside the registry's `Begin`, which changes
  neither the specs nor the API (recorded as an open question, verified in slice 2).
- **Four slots, more live work.** [Risk] A superseded search in another slot is no longer aborted as a side
  effect. [Mitigation] Supersede within a slot still aborts immediately; at most two slots can be live in a
  projection session, and the car's teardown now abandons its own.
- **Fail-closed empties look like "No results found".** [Risk] A UI could show the no-results state for an
  aborted search. [Mitigation] The gate publishes nothing for a request that is no longer current, so the
  no-results state is only reachable for a request that is still current; the log line carries `aborted=true`
  for the device check.
- **Submodule commit and pin.** [Risk] A fork commit is required and CI builds only committed submodule state
  (`guidelines/Design.md` §5). [Mitigation] Minimal, upstreamable patch; one session owns the submodule; revert
  the gitlink to roll back.
- **A stale generation reaching the bridge after `close()`.** [Risk] A cancel or search after the client is gone
  is undefined. [Mitigation] Unchanged from today: every JNI function checks `getClientData` at its top and
  returns early (`:4107-4110`).

## Migration Plan

Four landable slices, in dependency order, each with its own cases. The order is native-first because the
behavioural repairs all end in the same Kotlin seam: fixing the one-character defect in the current pipeline
first would write that code twice.

1. **Native primitive — submodule commit + gitlink bump, app builds unchanged** through the transitional
   overloads (D1, D2, D3, D5, D6, D11). Green: the new host case plus the app's existing suites, untouched.
2. **The `:core` gate and the phone places surface** — minimum length, supersede, abandon through the new API,
   replacing the `_searchRequest` pipeline (D4, D7, D8, D9). This is where the one-character defect is fixed.
3. **The remaining three consumers** — route panel, POI search, car `SearchScreen` — each with its own slot, plus
   the car's minimum length and its `onDestroy` (D2, D9, D10).
4. **Transitional overloads removed** once no caller uses them; then the integration checks and the on-device
   read of the search line.

Rollback: slice 1 reverts by restoring the gitlink (the app is unaffected either way); slices 2–4 revert
independently. Reverting slice 2–4 alone leaves the app on the transitional overloads — today's behaviour,
debounce only — which is the change's rollback path. Nothing persistent is touched.

Verification: host cases in the submodule's test target (`Tests/src/SearchBreakerTest.cpp`, run per
`guidelines/Build.md` §6 — build the target, then `ctest -R SearchBreakerTest`), `:core` gate cases, the phone
search suites against `FakeOSMScoutClient` extended with generations and cancel recording, `:auto`
`SearchScreenTest`, then the `:app`/`:auto` gates. Red-on-HEAD cases before the fix: the stale list under a
one-character query, the car's one-character search, the car screen's late template refresh, no abort without a
successor, the unabortable form pass. On device (not required for correctness, but the observable check):
`adb logcat -s NaviVeylin` shows the search line carrying `slot=`, `gen=` and `aborted=` with a small
`nativeMs` after a typing burst or after leaving the car search screen mid-search — coordinate-free by
construction, which is the measurement that settles this change on a device. Revert-check: make `Cancel(gen)`
ignore the generation, and the stale-cancel case must fail (`guidelines/Build.md` §2, `TODO.md` §113).

## Open Questions

- ~~Are concurrent `LocationService` searches on one `DBInstance` read-safe?~~ **Answered (apply, task 2.1): yes — by
design, so no per-slot mutex is added.** The location index's read path shares nothing unprotected: the scanner
pool is mutex-guarded (`libosmscout/include/osmscout/util/ObjectPool.h:37,78-80`), every visit API is `const`, each
visit borrows its own scanner from that pool (`libosmscout/src/osmscout/db/LocationIndex.cpp:36,579,641,668,699,729,760,787`),
and the index's shared cache is flushed only when the last `ScopeCacheCleaner` goes out of scope — a
reference-counted lifetime (`libosmscout/include/osmscout/db/LocationIndex.h:56-70`, `FlushCache() const` `:270`),
with the cleaners held per operation by the services that use the index (`LocationDescriptionService.cpp` and
friends). A search's own state is per-call locals, and the one shared structure it touches, `ClientData::adminRegions`,
is already guarded by `data->adminRegionMutex` (`OSMScoutClient.cpp:4168`). Concurrency therefore predates this
change and stays safe; if a future pass finds a mutable read-path cache, the fix is one mutex per slot inside
`Begin`, which changes no spec, API or task.
- The exact submodule host-test invocation for the new case (configure flags, target name) — a build detail
  owned by `guidelines/Build.md` §6/§12, resolved when slice 2 runs.
