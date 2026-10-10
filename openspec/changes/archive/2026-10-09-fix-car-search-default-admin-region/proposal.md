# Proposal

## Why

The car search runs unconstrained: `AutoServiceModule.provideAutoSearchProvider`
passes `OSMScoutClient.NO_ADMIN_REGION` (`app/src/main/java/com/naviveylin/di/AutoServiceModule.kt:78`),
while the phone resolves the admin region containing the current position and
passes it as the default (`MapCanvasViewModel.currentSearchAdminRegionHandle`,
`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:1516`; spec
`location-search` — "Search scoped by current admin region"). The native
structured search visits POIs only inside an admin region matched from the query
tokens, so a driver who names a POI without its city (`Hilpert Theater`,
`Theater Dortmund`) gets streets, garages or nothing at all, while the same query
on the phone resolves — a silent failure of a user action on the one surface
where the driver cannot retry by typing more (TODO.md §75, found during
`fix-compound-name-matching`).

## What Changes

- The car search path resolves the admin region for its position reference
  (last GPS fix, else the car map viewport center) and passes that handle to
  `OSMScoutClient.searchLocations(...)` instead of `NO_ADMIN_REGION`.
- The region is resolved and cached by the same rule the phone uses: a fix must
  be usable (accuracy ≤ 50 m), the handle is reused until the position moves
  more than the movement threshold (500 m), and any resolution failure yields
  `0` — the search then runs unconstrained, exactly as today.
- The rule becomes one shared implementation (**decided: Open Decision 1, option
  B**): `MapCanvasViewModel.searchAdminRegionHandleForFix` moves into a
  framework-free `:core` rule that the ViewModel and the car adapter both call,
  so the accuracy gate, the reuse window and the movement threshold cannot
diverge.
- A new requirement is added to `auto-search` stating the car's scope semantics
  and its parity with the phone, including the one platform deviation (the car
  `SearchTemplate` has no free-text label row, so the resolved scope region name
  is not shown on the car; `location-search` — "Search scope region name shown
  in search panel" stays phone-only).
- No signature, layout or template change in `:auto`: `AutoSearchProvider`,
  `SearchScreen` and its call sites are untouched.
- **Additive**, not breaking. No behaviour is removed, no data format or public
  API changes; a failed or absent resolution degrades to today's unconstrained
  search.

### Non-Goals

- **TODO.md §34** (car search opened from the root/history screens shows no
  distance reference) is *not* part of this change. A shared car-side "search
  reference" provider would close both §75 and §34; that is a separate scope
  decision (see Open Decisions).
- No region scoping for the car search *from the route panel* (the phone keeps
  that path unconstrained by spec; the car has no equivalent surface).
- No re-import or re-download of map data — the POI types missing from the
  installed map set (TODO.md §89/§91) stay a data-side issue.
- No **behaviour** change on the phone: its region resolution is refactored
  onto the shared rule and its existing region tests must stay green unmodified.
  No native/JNI change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto-search`: adds a requirement "Search scoped by the current car position's
  admin region" (WHEN/THEN scenarios for a scoped POI-only query, region reuse
  under the movement threshold, the unusable-fix fallback to the viewport
  center, and the unconstrained case when neither reference exists), and states
  the phone/car parity rule with its one platform deviation. No existing
  requirement of `auto-search` is removed or reworded.
- Cross-referenced, unchanged: `location-search` (the phone rule this mirrors,
  including the scope walk-up cap of level 5 and the "no usable fix →
  unconstrained" clause), `search-result-ranking` (the distance reference rule
  the region source follows; `LocationEntry.inSearchScope` demotion from
  `fix-cross-database-search-scope` then ranks in-scope matches first).

## Impact

Affected code and modules:

| Path | Change |
|---|---|
| `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` | `provideAutoSearchProvider` gains a lazily resolved region source (a second `javax.inject.Provider`, keeping the existing laziness contract) and passes the handle to `searchLocations` |
| `core/src/main/java/com/naviveylin/core/search/` (new file, e.g. `SearchRegionResolver.kt`) | framework-free resolution rule **extracted from the phone's `searchAdminRegionHandleForFix`**: usability gate, reuse window, movement threshold, failure → `0`; the native calls (`resolveAdminRegion`/`getAdminRegionScopeName`/`getAdminRegionName`) are injected as lambdas so it is host-testable |
| `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:1516-1580` | `currentSearchAdminRegionHandle` / `searchAdminRegionHandleForFix` delegate to the shared rule; the ViewModel keeps its state publication, its diagnostics and its release-on-unusable-fix behaviour |
| `app/src/main/java/com/naviveylin/di/` (new adapter, e.g. `CarSearchRegionSource.kt`) | app-side wiring: the car location source + a lazily resolved `OSMScoutClient` behind the resolver |
| `core/src/main/java/com/naviveylin/core/AutoSearchProvider.kt` | KDoc only (the scope note; signature unchanged) |
| `auto/src/main/java/com/naviveylin/auto/SearchScreen.kt` | unchanged — `runSearch` already runs the provider call in `withContext(ioDispatcher)`, which is where the region resolution must run (guidelines/Design.md §4: no native call on the car-app main thread) |
| `app/src/test/java/com/naviveylin/di/AutoSearchProviderRankingTest.kt`, `CrossSurfaceSearchOrderTest.kt`, `AutoProviderLazinessTest.kt` | extended fakes: assert the handle reaching `searchLocations`, and that laziness (client not resolved while the provider is built) is preserved |
| `app/src/test/java/com/naviveylin/ui/map/` (existing phone region tests) | must pass **unmodified** — they are the behaviour-preservation evidence for the extraction |
| new `core/src/test/.../SearchRegionResolverTest.kt` | the rule's own cases (usability gate, reuse inside the window, re-resolve past the threshold, failure → `0`, car viewport-center fallback) + revert-check |

Android components: `NaviVeylinCarAppService` / `:auto` `SearchScreen` (both
distribution flavors — the car path is the same code in the mobile and the
automotive build), `:app` Hilt `SingletonComponent` bindings, and the car
location source already bound for the car session (`AutoLocationProvider`,
backed by the shared `LocationService` lease `car-session`/`car-nav`).

Guidelines: `guidelines/Design.md` §4 (native work off the car-app main thread;
the extracted rule stays pure, the native calls live in the injected lambdas) and
§12 (single source of truth — the extraction is what removes the duplication);
`guidelines/UI.md`
(car search surface, no layout change); `guidelines/Build.md` §10 (on-device
recipe, which must also record the §89/§91 data blocker).

Native/JNI: **no submodule patch and no bridge-override change.** The three
natives the resolver needs are already declared in the Java override —
`resolveAdminRegion(double,double)`, `getAdminRegionName(long)`,
`getAdminRegionScopeName(long)` (`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java:277,292,302`)
— and implemented by the existing `libosmscout_client_java.so`. If a future
rebase is needed there, it is a submodule-side minimal patch, not this change.

Performance: one native region resolution per search at most, cached across
searches under the 500 m movement threshold, executed inside the existing
background search block so it stays inside the 500 ms search debounce.

Risk: the resolved scope can hide a correct match that lies outside it (the
`fix-cross-database-search-scope` failure mode). Mitigation: the native search
still matches fully qualified queries regardless of the default region
(`location-search`), and `SearchResultRanker` keeps out-of-scope matches in the
list, demoted.

Risk (from the chosen option B): the extraction edits `MapCanvasViewModel`'s
verified region path. Mitigation: the phone's existing region tests must pass
unmodified, and this tree already shares in-flight work
(`fix-cross-database-search-scope`) that touches the same search files — one
writer per tree (`guidelines/Build.md`, TODO.md §40.46), so the extraction lands
as its own commit before any other phone-side search work resumes.

## Open Decisions (owner)

1. **Where the resolution rule lives — DECIDED 2026-10-01 (owner): (B) shared
   resolver.** `searchAdminRegionHandleForFix` moves into one `:core` rule that
   `MapCanvasViewModel` and the car adapter both call. Rejected (A) app-side
   car-only rule — smallest diff, but the accuracy/movement rule would exist
   twice (a `guidelines/Design.md` §12 deviation). The cost accepted with (B) is
   the edit to the phone's verified path (see Risk) and phone-side regression
   evidence; the benefit is that the car cannot drift from the phone's rule.
2. **Region source — DECIDED 2026-10-01 (design.md): the position fix only,
   never the car map viewport center.** Parity decides it: the phone never scopes
   from the map center, so a car that did would match different candidates for
   the same query; the viewport center stays the *distance* reference fallback
   `auto-search` already specifies. Accepted consequence: a car search with no
   usable fix stays unconstrained (today's behaviour, no regression).
3. **TODO.md §34 — deferred, not in this change.** Its fix (a car-side holder of
   the last rendered viewport center) is what a viewport-center region fallback
   would need, so it stays the sibling change; design.md records the reasoning.

## Rollback

Revert the commit(s). Nothing is persisted and no interface changed, so a
rollback is a plain code revert; at runtime the degraded path (resolution
returns `0`) is today's behaviour, and the existing "unconstrained search" tests
continue to pass unchanged. The phone-side part is a behaviour-preserving
extraction, so reverting it restores the in-ViewModel rule with no data or spec
consequence.
