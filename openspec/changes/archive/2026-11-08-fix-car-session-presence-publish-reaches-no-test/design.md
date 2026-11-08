# Design

## Root cause

Both presence publishes are inline `carSessionPresence().setActive(...)` calls inside
`NavigationSession`'s lifecycle callbacks (`onStart` at `NavigationSession.kt:267`, `onDestroy` at
`:309` on the base). `NavigationSession` needs a host-provided `CarContext` and cannot be constructed
under Robolectric — the constraint `NavigationSessionDarkModeTest` and `NavigationSessionRestoreTest`
state in their own KDoc. The publish's *direction* (active on start, cleared on end) therefore had no
host-free seam, while every sibling lifecycle step that does have one (`SessionHostGate`,
`SessionCarSurfaceHost`, `endNavigationAfterArrival`, `carSessionScope`) is unit-tested.

## The single fix

Extract the publish step to one `internal` top-level seam, `publishCarSessionPresence(presence, live)`,
that takes the presence as a provider and the direction as a flag, publishes through `guardedHostCall`
and returns whether it landed. Both callbacks call it. The provider keeps the lazy resolution
(`entryPoint.carSessionPresence()`) inside the confined block, so resolution and publish are confined
exactly as the inline step was, and the two diagnostics names ("publish car session presence" /
"clear car session presence") are preserved.

## Alternatives the evidence eliminated

- **An androidTest that owns a real session.** The item names it, and the project has paid for no
  instrumented car-session harness anywhere; it is a device test for a direction a pure case settles.
  The two existing session tests already record why the unit path is the project's pattern.
- **Build `NavigationSession` under Robolectric with a fake `CarContext`.** Impossible: `androidx.car.app.Session`
  and `CarContext` are supplied by the host and are the exact reason no `:auto` test constructs the
  class (`NavigationSessionDarkModeTest` KDoc, archive task 4.2).
- **Assert `CarSessionPresenceImpl` more.** Already covered (`CarSessionPresenceImplTest` 5/0); it says
  nothing about the direction the lifecycle publishes, which is the gap.
- **Extract the whole callback body** (the item's `onSessionStarted(presence, surfaceHost, hostGate)` /
  `onSessionDestroyed(...)` sketch). That signature pulls the surface host, host gate, navigation
  manager, location provider, view model and session scope into one seam; the ordering between those
  steps is design D4 and device-verified, and each collaborator is separately seam-tested. It is wider
  than the gap the item records and would put the surface lifecycle behind a new interface for no new
  assertion.
- **Enforce the seam with a structural scanner + Gradle task** (the `checkTripSendGate` shape). Not
  owed: the sibling lifecycle steps have no such scanner, the item accepts inspection-verified wiring
  for all of them, and the only new fact is the publish direction — which the seam case now asserts.

## Blast radius

- `NavigationSession.kt`: one seam; two call sites change shape. No host API, no coroutine, no state,
  no protocol changes.
- Tests: one new `:auto` class (4 cases); `SessionEndArrivalDecisionTest`, `CarHostGuardsTest`,
  `SessionHostGateTest` exercise the same `guardedHostCall` seam and are unaffected (the `:auto` module
  suite is green with the change).
- No compiled cross-module surface: the seam is `internal` to `:auto`.

## Threading and lifecycle

The seam runs at the same points the inline calls did: synchronously inside the car-app library's
main-thread lifecycle dispatch. It owns no scope and starts no work; confinement stays `guardedHostCall`
(a rejected publish is a logged no-op that never escapes the dispatch), unchanged from the inline step.

## Verification

- The red case: `SessionPresencePublishTest` — 4 cases, `tests=4 failures=0` with the change
  (`evidence/revert-check-green.xml`), compile-red on the base and `tests=4 failures=4` with the publish
  line mutated to a no-op (`evidence/red-on-HEAD.log`, `evidence/revert-check-red.xml`).
- The `:auto` unit suite (`:auto:testDebugUnitTest -PforceTests --no-build-cache`) is green with the
  change.
- No on-device step is owed: the seam moves the same call to the same point; the lifecycle ordering
  around it is inspection-verified exactly as every sibling step's is.

## Rollback

Inline the two calls again and delete the seam and its case. The publishes are again device-only, which
is the state this change repairs; no persisted data and no behaviour changes.

## Open decisions

none
