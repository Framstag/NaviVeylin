# Design

## Context

Motivation is in `proposal.md` (Why). The state that shapes the approach:

- The engine holds **one** location lease for `LocationConsumers.NAV_ENGINE` (`NavigationEngine.kt:665-670`,
  `acquireNavLease`), and releases it from five sites:
  - `stopNavigation` (`:644`) — navigation ends, correct
  - `navigateTo`'s "no GPS position" early return (`:403`) — surface-less attempt, correct
  - `cancelAcquisition` (`:558`) — guarded by `ownsLease = !_state.value.isNavigating` (`:543`), correct
  - `calculateAndStart`'s `onError` (`:481`) — **unguarded, the defect**
  - `calculateAndStart`'s synchronous `catch` (`:507`) — **unguarded, the defect**
- Two callers take the lease for an attempt:
  - `navigateTo` (`:391`) — the surface-less acquisition (car deep link, favourite): takes the lease *before*
    `acquire(...)` because the start position must come from a fix.
  - `startInternal` (`:301`) — the running navigation takes the lease once a route starts.
- Three entry points reach `calculateAndStart` (`:436`), all on the main thread:
  - `navigateTo` → `acquire` (`fromReroute = false`), after it leased at `:391`
  - a surface's explicit `acquire` (`:428-431`) — takes no lease of its own
  - `confirmReroute` (`:906`, `fromReroute = true`) — called inside `scope.launch(Dispatchers.Main)`
    (`:840`, `:872-881`) from the native `onRerouteRequest`, always while `isNavigating` is true
- Both failure handlers already run on `Dispatchers.Main` (`scope.launch(Dispatchers.Main)`, `:478`, `:504`),
  and both first call `endCalculation(token, …)`, which returns `false` for a superseded attempt — a late
  failure of a superseded calculation therefore never reaches the release at all.
- `confirmReroute` is drivable in tests: `NavigationEngineRerouteTest` starts a session and calls
  `client.navigationListener!!.onRerouteRequest(...)` (`NavigationEngineRerouteTest.kt:109-131`), and
  `FakeOSMScoutClient` can hold a calculation (`holdRouteDelivery`) and then fail it through
  `pendingRouteCallbacks` (`FakeOSMScoutClient.kt:383-419`). `LocationService.heldLeaseConsumers()`
  (`:651`) is the assertion seam.

## Goals / Non-Goals

**Goals:**
- A route attempt releases a lease only when the attempt took it, expressed once and shared with the cancel
  path's existing rule.
- A failed reroute leaves live guidance on the air (position updates keep arriving) and still reports the
  failure.
- A failed surface-less acquisition still releases the lease the attempt took.

**Non-Goals:**
- Changing `LocationService` or the lease semantics: the spec is already right; the engine violated it.
- The reroute UX (whether a failed reroute should be a notice, a toast or a silent retry) — unchanged.
- Supersession/token semantics (`endCalculation`) — unchanged, and it is what makes a late failure of a
  superseded attempt harmless.
- Any native/JNI or submodule change.
- `TODO.md` §122/§140 (stop-button reachability) — different control, different change.

## Decisions

### 1. Ownership is captured at attempt start in `calculateAndStart` (chosen) over lease-creation tracking

**Chosen:** read `val ownsLease = !_state.value.isNavigating` immediately before `beginCalculation`/the compute
job in `calculateAndStart`, carry it into the closure, and use it in both failure handlers:

```
onError(message)  ->  if (ownsLease) releaseNavLease();  publish error
catch (e)         ->  if (ownsLease) releaseNavLease();  publish error
```

This is exactly the rule `cancelAcquisition` already applies (`ownsLease = !_state.value.isNavigating`), so the
engine has one ownership notion for "an attempt that ends without starting navigation", not two.

**Alternatives considered:**

- **B — make `acquireNavLease()` return whether it created the lease, and thread that Boolean from `navigateTo`
  through `acquire` into `calculateAndStart`.** More literally "did this attempt take it", but it changes the
  signatures of two internal functions and of `calculateAndStart` to carry a value that is only ever used by
  the failure handlers, and it still has to answer the same question for a reroute (there, `startInternal`
  took the lease, not the attempt). Rejected as more churn for the same predicate.
- **C — on failure, release and then re-acquire when navigating.** Removes the predicate but opens a real
  window: `LocationService` sees the last lease go and stops device updates, then restarts them — a race with
  the location feed and a visible gap in a live navigation, plus a second `location lease acquire/release`
  pair in the diagnostics for every failed reroute. Rejected: a failure path must not blink the subscription.
- **D — do not release on failure at all.** Simplest, but it breaks the surface-less case: a car deep link to
  an unreachable destination would hold the lease for a navigation that never starts, exactly the
  "No location retention while idle" requirement. Rejected — this is why scenario 2 of the delta exists.

**Consequence accepted:** ownership is read at attempt start, so if navigation *starts* by another route
between the attempt's launch and its failure the lease is not released by this failure. That is the desired
direction (never starve live guidance), and the late-failure path is already gated by `endCalculation`'s
supersession check.

### 2. The error publication is kept, only the release is gated (chosen) over suppressing the error during navigation

**Chosen:** keep `_state.update { it.copy(errorMessage = …, errorOrigin = SurfaceOrigin.ENGINE) }` on both
failure paths exactly as today. The driver/session must still learn the reroute failed.

**Alternatives considered:**

- **E — suppress the error while navigating** (a failed reroute is "just another failed attempt"): rejected —
  this change's premise is that the failure is invisible today; making it more invisible is the wrong
  direction, and `TODO.md` §126 already states the failure UX must survive.
- **F — raise a dedicated reroute-failure notice in the car session**: out of scope; `show-route-calculation-progress`
  owns the calculation-wait surface and this change must not add a second one.

### 3. The reroute-failure case drives the real reroute entry, not a stand-in `acquire` while navigating

**Chosen:** the new case lives in `NavigationEngineRerouteTest` and calls
`client.navigationListener!!.onRerouteRequest(...)` — the production entry the defect is reached through — with
`holdRouteDelivery = true`, then fails the held callback and asserts `heldLeaseConsumers()`. This is a true
guard of the requirement, not of a proxy path.

**Alternatives considered:**

- **G — assert only through `engine.acquire(...)` while navigating** (the existing cancel test's "what a reroute
  looks like"): cheaper, but it is a stand-in — if `confirmReroute` ever stopped routing through
  `calculateAndStart`, the case would stay green while the defect returned. Rejected as the *only* case; the
  surface-less control in `NavigationEngineCalculationCancelTest` covers the same code path's other half.
- **H — a device-only verification**: rejected as primary evidence — `TODO.md` §106/§40.45 record that the AAOS
  AVD is not reliably usable for headless device steps, and a reroute that *fails* needs a thin map set, so the
  fix would be unverifiable for long stretches. The unit case is the evidence of record; a device task is kept
  as a bonus (tasks 4.1) with its blocker stated.

**Delta scenario → case mapping (tasks 2.4):**

| Delta scenario | Case |
|---|---|
| Failed reroute keeps the running navigation's lease | `NavigationEngineRerouteTest.aFailedRerouteKeepsTheRunningNavigationLease` (production reroute entry) |
| Failed surface-less acquisition releases its own lease | `NavigationEngineCalculationCancelTest.aFailedSurfaceLessAcquisitionReleasesItsOwnLease` |

## Risks / Trade-offs

- **Risk: the ownership predicate mis-reads a path.** Mitigation: the predicate is copied from the cancel path,
  and task 2.2 pins the opposite direction (surface-less failure still releases) so the rule cannot degrade into
  "never release on failure".
- **Risk: a future attempt path takes its own lease while navigating** (e.g. a new surface-less entry that
  allows navigation to be active). Then `ownsLease` would be `false` and the attempt's own lease would leak.
  Mitigation: the delta's requirement names ownership, not `isNavigating`; task 5.2 records the rule in
  `guidelines/Design.md` §4 so a new entry path has to answer the ownership question. The leak would be a
  follow-up entry, not a silent one (the diagnostics line `location lease acquire … (held=N)` stays visible).
- **Risk: the device half is unproven.** Accepted and stated in task 4.1 rather than implied.
- **Trade-off:** one more Boolean in the `calculateAndStart` closure (three call sites pass through it) in
  exchange for one shared ownership rule.

## Verification

- **Unit (primary evidence)**: `NavigationEngineRerouteTest` — a real reroute whose calculation fails leaves
  `heldLeaseConsumers()` containing `LocationConsumers.NAV_ENGINE`, `state.isNavigating` true, and
  `state.errorMessage` set; `NavigationEngineCalculationCancelTest` — a surface-less acquisition that fails
  releases the lease (`heldLeaseConsumers()` empty). Both under the default Robolectric sandbox
  (`AGENTS.md` classloader rule), no wall-clock waits (drain the injected schedulers, per
  `unit-test-suite-runtime`).
- **Revert-check (one mutation)**: restore the unconditional `releaseNavLease()` at `:481` (and `:507`) → the
  reroute-failure case must fail with `heldLeaseConsumers()` no longer holding `nav-engine`; restore, then
  force green with `-PforceTests --no-build-cache` (`revert-check` skill; a restored tree answers `UP-TO-DATE`).
- **Focused suites**: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.*"` and the
  both-flavor gate once (`./gradlew test -PforceTests --no-build-cache`).
- **On-device (bonus, blocker stated)**: on the AAOS AVD force a reroute that fails (thin map set / an
  unreachable destination) and read `adb logcat -s NaviVeylin`: no `location lease release: nav-engine` in the
  window while guidance is live, and no gap in the position feed. The reroute's own device setup is the §106
  AVD recipe; if the AVD is not usable in the session, tasks 4.1 is completed as "not run, blocker recorded"
  and the unit case carries the verification.
