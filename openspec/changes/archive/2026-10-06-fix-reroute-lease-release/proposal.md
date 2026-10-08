# Proposal: fix-reroute-lease-release

## Why

A reroute that fails silently takes the running navigation's location updates with it. The engine takes one
`NAV_ENGINE` location lease (`LocationService.acquire(LocationConsumers.NAV_ENGINE)`, `NavigationEngine.kt:665-670`)
and releases it from exactly five places; two of them are the *failure* paths of any route attempt —
`calculateAndStart`'s `onError` (`:481`) and its synchronous-catch twin (`:507`) — and they release the lease
unconditionally, regardless of who owns it:

```
onError(message)                      // NavigationEngine.kt:476-485
    if (!endCalculation(token, ERROR)) return@launch
    releaseNavLease()                 // <-- drops the running navigation's lease
```

`confirmReroute` (`:900-907`) reroutes through the same `calculateAndStart(..., fromReroute = true)` *while
`isNavigating` is true* — it is only ever reached from an off-route report of the live session — and the lease
at that moment belongs to the running navigation, taken by `startInternal` (`:301`). When the reroute fails
(a disconnected map edge, a missing destination node, a thin map set), `LocationService` sees the last lease go
and stops device updates (`location-updates-lease` — "Updates stop with the last release"), so guidance keeps
rendering and the native engine keeps extrapolating from the last fix, with no position updates and no error
that would explain it. The cancel path already avoids this: `cancelAcquisition` guards its release with
`val ownsLease = !_state.value.isNavigating` (`:543-558`) and its comment states the reason ("A reroute reuses
the lease the running navigation holds — releasing it would starve live guidance"). The failure path was left
behind, recorded as `TODO.md` §126 while implementing `show-route-calculation-progress` (task 2.4).

The failure path has a device symptom already in the record: `TODO.md` §106's AAOS AVD reproduced route
failures with exactly such an error message (`No routable node near destination`).

## What Changes

- **The failure path releases only the lease the attempt took.** `calculateAndStart` captures whether the
  attempt owns the lease (`!_state.value.isNavigating`, read on the main thread where every entry point runs)
  and both failure handlers release the lease only for an owning attempt. A reroute's failure keeps the
  navigation's lease; a surface-less acquisition's failure still releases its own.
- **The failure is still published.** The error message and its `SurfaceOrigin.ENGINE` are unchanged — the
  driver must still be told the reroute failed. Only the lease release is gated.
- **Nothing else changes.** No new state, no new engine entry point, no UI, no persistence, no native/JNI
  change, no submodule patch, no `:osmscout-client-java` override. The ownership rule matches the one the
  cancel path already uses, so there is one rule for "an attempt that ends without starting navigation" in
  the engine, not two.

## Capabilities

Scope is **both surfaces**: the defect is in the process-scoped `NavigationEngine` (`:app`), which the phone
map and the car session share, so the lease loss affects free-driving navigation on the phone and guidance on
the car alike. No surface-specific behaviour is added.

### New Capabilities

None.

### Modified Capabilities

- `navigation-engine` — a new requirement, **"A failed route attempt releases only the lease it took"**, with
  its two scenarios: a failed reroute keeps the running navigation's lease (and still reports the failure), and
  a failed surface-less acquisition releases its own. The existing "Engine lifecycle and threading" requirement
  ("SHALL NOT retain location updates while not navigating") still holds and is not restated.

`location-updates-lease` is **not modified**: its "One consumer's release never silences another" is about
*different* consumers, and the defect is the engine releasing its own lease while it still needs it. The
change makes the engine obey the lease contract, it does not change it.

`route-calculation-feedback` is **not modified**: the in-flight change `show-route-calculation-progress`
already adds `navigation-engine`'s "Surface-less route acquisition is cancellable" requirement, whose
"Aborted acquisition releases the location lease" scenario is scoped to *a surface-less acquisition* — the
owning case this change keeps. The two changes add distinct requirements to `navigation-engine`; they do not
conflict in either archiving order.

## Impact

- **Code** (`:app`, one file):
  - `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` — `calculateAndStart` (`:436-513`):
    capture the attempt's lease ownership before the compute job, and use it at the two failure sites
    (`:481`, `:507`). `cancelAcquisition` (`:541-560`) and `acquireNavLease`/`releaseNavLease`
    (`:665-677`) are reference, not targets.
- **Tests**:
  - `app/src/test/java/com/naviveylin/navigation/NavigationEngineRerouteTest.kt` — a case driving the **real**
    reroute entry (`client.navigationListener.onRerouteRequest`, the same seam its other cases use) with the
    reroute's calculation held and then failed, asserting via `LocationService.heldLeaseConsumers()` that the
    navigation's lease survives, the engine stays navigating, and the failure is published. This case fails on
    the pre-change code.
  - `app/src/test/java/com/naviveylin/navigation/NavigationEngineCalculationCancelTest.kt` — the positive
    control that a failed surface-less acquisition still releases its own lease (the boundary that keeps the
    new rule from being "never release on failure").
  - Both classes already run under the default Robolectric sandbox (the `AGENTS.md` classloader rule) and
    already use the `heldLeaseConsumers()` seam — no new harness.
- **Modules / build**: `:app` only. No Gradle, manifest, DI, resource or asset change. **No native change**
  — no libosmscout submodule patch, no `:osmscout-client-java` override.
- **Guidelines**: `guidelines/Design.md` §4 (threading/lifecycle of the engine's attempts) and §12
  (one source of truth — the ownership rule is shared with the cancel path, not copied a third time). No
  `UI.md`/`MapRendering.md` rule is affected.
- **Specs of record**: `navigation-engine` (delta file). Cross-referenced, unchanged: `location-updates-lease`,
  `route-calculation-feedback`, `reroute-trigger`.
- **Backlog**: closes `TODO.md` §126 when archived.

## Additive or breaking, rollback

Additive: a location lease survives a failure it today loses, so nothing that works now stops working, and no
surface reads different state. The observable change is confined to an AVD/log line (`location lease release`
no longer logged during a failed reroute) and to guidance continuing to move. Rollback: remove the captured
ownership and release unconditionally again — the previous (starved) behaviour returns unchanged; the delta
requirement would then be false, so a rollback must also revert the spec.

## Decision

**Chosen: capture the attempt's lease ownership in `calculateAndStart` and gate both failure releases on it**
(same shape as `cancelAcquisition`). Rationale and the rejected alternatives are in `design.md` D1.
