# Design

## Context

See `proposal.md` — Why and What Changes. Current state that shapes the approach:

- The rule to share already exists, inline in the phone:
  `MapCanvasViewModel.searchAdminRegionHandleForFix` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:1523-1582`)
  — accuracy gate `GPS_FIX_MAX_ACCURACY_M = 50f` (`:4088`), reuse window
  `ADMIN_REGION_MOVEMENT_THRESHOLD_M = 500.0` (`:4109`), no age cap, release on
  unusable fix, cache state (`searchAdminRegionHandle/Lat/Lon/Name`) plus the
  UI-state mirror `pushSearchAdminRegionState`.
- The native calls involved are all already declared in the bridge override and
  need no change: `resolveAdminRegion`, `getAdminRegionScopeName`,
  `getAdminRegionName`, `releaseAdminRegion`
  (`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java:277,292,302,284`).
- The car path is `AutoServiceModule.provideAutoSearchProvider`
  (`app/src/main/java/com/naviveylin/di/AutoServiceModule.kt:58-84`), a
  `fun interface` implementation in the app's Hilt graph; its consumer
  `SearchScreen.runSearch` (`auto/src/main/java/com/naviveylin/auto/SearchScreen.kt:141-166`)
  calls it inside `withContext(ioDispatcher)` and resolves the client lazily
  through an injected `Provider<OSMScoutClient>` (contract pinned by
  `app/src/test/java/com/naviveylin/di/AutoProviderLazinessTest.kt`).
- The shared distance helper is `:core` `haversineDistanceMeters`
  (`core/src/main/java/com/naviveylin/core/GeoDistance.kt`), already the single
  implementation for the app's distance comparisons.
- Behaviour-preservation evidence for the phone exists and is the yardstick:
  `MapCanvasViewModelAdminRegionTest` (20 cases: accuracy gate, reuse,
  re-resolution, release on lost fix, unconstrained without a fix, region-name
  UI state, eager panel-open resolution).

## Goals / Non-Goals

**Goals:**

- One implementation of the region-scoping rule, used by the phone search, the
  car search and the phone's search-panel region-name display.
- The car search passes a resolved admin region handle instead of
  `NO_ADMIN_REGION`, with today's behaviour preserved whenever no usable fix
  exists.
- Host-testable rule: no native call inside the rule itself.

**Non-Goals:**

- No viewport-centre region fallback (see Decision 2).
- No car-side scope-region-name surface (Decision 4 / spec deviation).
- No change to which maps are searched, no ranking change, no native change.
- No change to the phone's observable behaviour or its dispatcher usage.

## Decisions

### 1. The rule lives in `:core` and is shared (owner decision: option B)

New `core/src/main/java/com/naviveylin/core/search/` file with a state value
class plus a resolution function; `MapCanvasViewModel` delegates to it and the
car adapter calls the same function.

- **Alternatives**: (A) car-only duplicate rule — smallest diff, rejected
  because the accuracy/threshold rule would exist twice, the exact duplication
  `guidelines/Design.md` §12 forbids and the class that produced TODO.md §75
  (a phone-only rule the car never got). (C) pass the region through
  `AutoSearchProvider`'s signature and resolve in `:auto` — rejected: the car
  screen would need the native client and a cache, and three `:core`/`:auto`
  call sites plus their tests would change for no behavioural gain.

### 2. Region source: the position fix only, never the map viewport centre

The car resolves the region from its position fix under the same gate as the
phone. With no usable fix the search runs unconstrained.

- **Rationale**: parity is the point of the shared rule — the phone never scopes
  from the map centre, so a car that did would match different candidates for
  the same query. The viewport centre stays what `auto-search` already specifies
  it for: the *distance* reference fallback, a separate concern.
- **Alternatives**: fix-else-viewport-centre — needs a car-side holder of the
  last rendered viewport that does not exist today; that holder is the fix
  candidate of TODO.md §34 (car search from root/history has no distance
  reference), so it belongs to that sibling change. Native region-less bounded
  POI pass — walks the POI index of every loaded database inside the 500 ms
  debounce budget, unnecessary when a fix exists.

### 3. Rule shape: pure decision + caller-owned state

The rule takes the fix and the previous scope state and returns an outcome
(handle, region/scope name, handle-to-release); the caller owns the cache and
performs the injected native calls. `SearchRegionScope` carries the state
(handle, lat, lon, name) so both surfaces hold the same shape.

- **Rationale**: the rule stays host-testable with fakes and no Robolectric
  native stub; the phone keeps its existing state owner (the ViewModel) and its
  UI-state mirror unchanged; the car adapter holds one instance for the process.
- **Alternatives**: a stateful rule class instantiated per surface — better
  encapsulation, but it would move the ViewModel's cache and its eager
  panel-open path inside the class, i.e. restructure a verified path for no
  behavioural gain.

### 4. The car adapter reads the shared location source; no signature changes

`provideAutoSearchProvider` gets a lazily resolved region source backed by the
same `LocationService` the car surface already uses
(`provideAutoLocationProvider`, `AutoServiceModule.kt:202`), so the car process
needs no new location wiring and the car session's existing location lease keeps
producing fixes.

- **Alternatives**: resolve inside `:auto` `SearchScreen` (rejected, Decision 1
  C); a second location subscription for search (rejected: duplicates the lease,
  `location-updates-lease`).
- **Constraint to preserve**: the region source must be injected as
  `Provider`/`Lazy` so the client is still built on the caller's thread inside
  the search call — `AutoProviderLazinessTest` fails otherwise.

### 5. Diagnostics carry identity, never coordinates

The rule and its adapter log the resolved handle, the region/scope name and the
accuracy decision — never a latitude/longitude, and never a local assigned from
one (`checkNoCoordinatesInLogs`, buildSrc `CoordinateLogScanner`, spec
`auto-diagnostics`). The phone's existing log lines are kept as they are.

## Risks / Trade-offs

- **[Scope hides a correct match]** → Mitigation: the native search still matches
  fully qualified queries regardless of the default region (`location-search`),
  and `SearchResultRanker` keeps out-of-scope matches, demoted (in-flight
  `fix-cross-database-search-scope`).
- **[The extraction breaks the phone's verified region path]** → Mitigation:
  `MapCanvasViewModelAdminRegionTest` must pass **unmodified** (all 20 cases),
  plus a rule-level revert-check; the extraction lands as its own commit because
  this tree also carries in-flight search work
  (`fix-cross-database-search-scope`; one writer per tree, TODO.md §40.46).
- **[Car-only process has no fix when the driver searches]** → Accepted:
  behaviour is today's (unconstrained), and the case is specified; the
  viewport-centre fallback stays with TODO.md §34.
- **[Region resolution cost inside the search debounce]** → One native lookup per
  search at most (cached under the movement threshold), executed in the same
  background block as the search, so the 500 ms debounce budget is untouched.
- **[Device verification is data-blocked]** → The positive car case (a POI found
  by name) needs a map set whose type config carries the POI types — TODO.md
  §89/§91 report the installed set does not. The on-device task therefore records
  the blocker and proves what the data allows (street/address query parity,
  unconstrained-without-fix, the region diagnostics line).

## Migration Plan

No data, API or template migration. Ship order: (1) `:core` rule + its tests,
(2) phone delegation + `MapCanvasViewModelAdminRegionTest` green, (3) car adapter
+ provider wiring, (4) verification gates. Rollback: revert the commits — the
degraded path (no region) is today's behaviour, and the phone's own rule returns
with the revert (proposal.md — Rollback).

## Verification Results (2026-10-01)

- `:core` rule: `SearchRegionScopeTest` **14 cases / 0 failures**
  (`./gradlew :core:testDebugUnitTest --tests "com.naviveylin.core.search.SearchRegionScopeTest"`).
  Revert-check: neutering the movement-threshold branch → the two reuse cases
  failed (`tests=14 failures=2`), the other 12 passed.
- Phone unchanged: `MapCanvasViewModelAdminRegionTest` **20/0** with the test file
  unmodified. Suites: `:app:testMobileDebugUnitTest` **1462/0/0**;
  `:app:testAutomotiveDebugUnitTest` **1462/0/0** on the rerun (the first run
  failed `NavigationEngineTest.listenerCallbacksDriveTheSharedState`, the
  documented load-sensitive flake TODO.md §101 — the class passes alone 12/12,
  and the flake entry holds the new evidence).
- Car wiring: `AutoSearchProviderScopeTest` **6/0**; revert-check: hardcoding
  `OSMScoutClient.NO_ADMIN_REGION` again → the four scoped cases failed
  (`tests=6 failures=4`). Touched DI tests (`com.naviveylin.di.*`) **35/0/0**.
- Builds: `:app:assembleMobileDebug` and `:app:assembleAutomotiveDebug` green, and
  both debug APKs carry all three ABIs (`lib/<abi>/libosmscout_client_javad.so`);
  `:auto:testDebugUnitTest` **713/0/0**; `:core:assembleDebug` with **0 Kotlin
  warnings**; `:app:preBuild` (incl. `checkNoCoordinatesInLogs`) green on the
  final tree — the new code carries no position into any log line.
- Device-gated and still open: tasks 5.1 (car surface) and 5.2 (phone regression).
  No device was attached on 2026-10-01 (`adb devices` empty), and the POI-only
  positive case additionally waits on the data gap of TODO.md §89/§91. The steps
  are in `guidelines/Build.md` §10 with that caveat.
