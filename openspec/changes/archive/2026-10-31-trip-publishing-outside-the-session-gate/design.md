# Design

## Context

Issue #71 (`nvid: 56`, `Framstag/NaviVeylin`) left one fork: gate the trip send on the session being started, or
declare the exemption and leave the code as it is. The tree on HEAD already decides the second:

- `NavigationSession.kt:727-733` — the trip collector calls `publishTrip` for every state emission, without
  `hostGate.allowHostMutation()`; the sibling navigation-state collector asks it at `:707`.
- `NavigationManagerController.kt:100-101` — `publishTrip` returns unless `navigating`, which
  `onNavigationStarted :69` sets and `onNavigationEnded :81`, `onDestroy :127` and a rejected `updateTrip :109`
  clear. So the send is bounded by the **navigation period**, and stops at the first host rejection.
- `SessionHostGate` KDoc — already states the intent: "The session's observers must keep running while the app
  is backgrounded (guidance and the cluster/HUD trip keep updating)."
- `openspec/specs/car-host-fault-isolation/spec.md:221-242` — the same requirement bounds screen pushes, template
  refreshes and "the host navigation state", and its scenario *Guidance progresses while backgrounded* requires
  the trip metadata to be updated while backgrounded. The clause at `:226` is the text a reader cannot square
  with the collector, and it is the only place the rule is missing.
- `guidelines/Design.md:588-598` — the MUST bullet already ends "guidance updates and trip metadata keep flowing".

`NavigationSession` cannot be constructed in a unit test (host-provided `CarContext`; see the KDoc of
`NavigationSessionRestoreTest`), so session-level decisions in this module are tested through a pure seam.

## D1 — Which resolution

**A: declare the exemption and assert it (chosen).** The bound stays the navigation period; the spec states
which host sends the session-start bound covers; the deviation becomes a pure, named, tested decision.

**B: gate the trip send on the session being started (rejected).** It deletes the shipped scenario *Guidance
progresses while backgrounded* and contradicts `auto-navigation-hints` (which bounds publishing by the
navigation period: "SHALL stop publishing when navigation ends") and `Design.md:592`. Driver-visible cost: the
cluster and heads-up display freeze whenever another car app is in front, which is the normal case in a car with
a factory cluster. Its only stated justification — host stability "in the window before the first rejection" —
is unmeasured, and it asks a decision to be taken against the measurement rather than from it. The library's own
contract makes the send legal: the active-navigation app is declared by `navigationStarted`, not by session
visibility.

**C: take the second fix candidate alone (already shipped — nothing to do).** `NavigationManagerController.kt:109`
stops publishing at the first rejected `updateTrip`, asserted by
`NavigationManagerControllerTest.aRejectedTripUpdateEndsTripPublishing` (`:144-161`). This change does not
re-open it.

**D: close the item with no artifact (rejected).** Nothing then pins the deviation. The measured consequence:
copying the gate from `:707` onto `:727-733` freezes the cluster and leaves **every existing test green**
(`NavigationManagerControllerTripTest`, `NavigationManagerControllerTest`, `SessionHostGateTest` all run with a
started session).

**AVD run (not required).** The loop verdict asked for one "counting `HOST` trip lines against host stability".
It would answer whether the host is stable while trip lines flow — a question this change does not have, because
option B is excluded on the spec and driver-visible grounds above, not on a stability count. If a host-stability
report ever arrives, the run in `guidelines/Build.md` §10c is the recipe and would then be a new item.

## D2 — How the deviation is asserted

**A: a pure seam plus a unit test (chosen).** `internal fun tripSendAllowed(navigating: Boolean, sessionStarted:
Boolean): Boolean`, called by the trip collector, which passes `hostGate.started`. The test drives both inputs
independently, so the direction the deviation protects — `sessionStarted = false` does not withdraw the send — is
asserted, and an author who adds `&& sessionStarted` turns the case red.

**B: an instrumented or session-level test (rejected).** `NavigationSession` needs a host `CarContext` and is not
constructible under Robolectric; the alternative is an AAOS AVD run, which cannot deterministically reproduce a
session-stop edge under a live host, and a timing-dependent case is the kind of test this module's conventions
exclude.

**C: spec text only (rejected).** Leaves the regression above reachable — the exact failure #71 nearly caused.

**D: assert it on `NavigationManagerController` (rejected).** The controller deliberately has no session
knowledge (KDoc `:25-33`: publishing happens only between `onNavigationStarted` and `onNavigationEnded`);
handing it session state to be testable would widen its contract to serve a test.

**E: put the seam on `SessionHostGate` as `allowTripPublish()` (rejected).** A gate method that must always
answer true asserts nothing, and it puts the wrong fix one edit away: the next author reads `SessionHostGate` as
the place host traffic is decided and writes `started` into it. The seam belongs beside the decision that reads
`hostGate.started`, in `NavigationSession.kt`.

### Residual gap after the independent review (finding 7) — closed by option F

The cases pin the **decision**, not the **call site**: replacing `tripSendAllowed(...)` at
`NavigationSession.kt:732` with `hostGate.allowHostMutation()` leaves every unit test green, because
`NavigationSession` cannot be constructed under Robolectric — the same constraint that produced the seam.
Two options were weighed:

- **F: a source-level check — chosen.** `:auto` gains `checkTripSendGate` (wired into `preBuild`, like
  `checkNoWallClockWaits`), backed by the pure buildSrc object
  `com.naviveylin.build.testing.TripSendGateScanner` with its own unit cases. It reads the trip collector's
  lambda and refuses (a) a collector that asks `allowHostMutation`, (b) one that no longer calls
  `tripSendAllowed`, (c) a collector whose shape it cannot read, and (d) a tree that holds no collector at all —
  the last two so a rename cannot switch the rule off quietly. No allowlist.
- **G: accept and document (rejected).** The seam KDoc, the `SessionHostGate` KDoc and this section name the
  wrong fix; a mistake then depends on a reviewer noticing it. That is exactly what the review's finding 7
  measured as insufficient.

What holds now: the mutation the revert-check performs fails a named case, and the collector's line is policed
by the build — both halves of the deviation are enforced, one by a test, one by a check.

## D3 — Where the seam lives and how it is shaped

- Top-level `internal fun tripSendAllowed(...)` in `NavigationSession.kt`, next to the file's existing seam
  cluster (`shouldRestoreFreeDriving :1352`, `shouldEndNavigationOnSessionEnd :1364`,
  `endNavigationAfterArrival :1374`) — the established place for session decisions, reachable from the test
  source set.
- Two explicit parameters, both real inputs of the call site, so no unused parameter appears in the production
  signature. `sessionStarted` is deliberately **not** consulted; the KDoc says so and names the spec requirement,
  so the exemption is stated where the next author will read it.
- The collector keeps its shape (`scope.launch(CoroutineName("trip"))`), with the seam consulted before
  `publishTrip`. Because `publishTrip` already returns when navigation is inactive, the wiring can only skip a
  call the controller would also have skipped: no message is ever added, so the change cannot increase host
  traffic.

## Threading and lifecycle

No new dispatcher, coroutine or lifecycle hook. The seam is pure and stateless; it runs on the session's main
thread inside the existing `trip` collector, and reading `hostGate.started` (`@Volatile`, `SessionHostGate.kt`)
there is safe. No native work, no blocking call, nothing new on the host path.

## Files touched

| File | Change |
|---|---|
| `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` | `tripSendAllowed` seam; trip collector consults it (`:727-733`) |
| `auto/src/main/java/com/naviveylin/auto/SessionHostGate.kt` | KDoc names the new spec requirement beside the existing "cluster/HUD trip keep updating" sentence |
| `auto/src/test/java/com/naviveylin/auto/NavigationSessionTripSendTest.kt` | new: the two scenario cases |
| `auto/build.gradle.kts` | `checkTripSendGate`, wired into `preBuild` (option F: the call site no unit test can reach) |
| `buildSrc/src/main/kotlin/com/naviveylin/build/testing/TripSendGateScanner.kt` | new: the pure scanner behind that check |
| `buildSrc/src/test/kotlin/com/naviveylin/build/testing/TripSendGateScannerTest.kt` | new: its 10 cases, including the shipped shape and the shape-change guards |
| `guidelines/Design.md` | **not touched** — its §8 is being moved to `DesignCrossVariant.md` §8a by the parallel `guideline-layers` change, so this change leaves the guideline alone and records the sentence to add afterwards (`proposal.md`, "Guideline reference") |
| `openspec/changes/…/specs/car-host-fault-isolation/spec.md` | the ADDED requirement (this change) |

No manifest, resource, Gradle, CMake, vcpkg, dependency or ABI change. No phone-side code path is touched.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The seam reads as decorative (a function that "always allows") | Medium | KDoc names the requirement and the rejected alternative; the test drives `sessionStarted` both ways; the wiring makes the parameter a used input |
| `navState.isNavigating` and the controller's `navigating` diverge after a host rejection | Low | The wiring can only skip what `publishTrip` would skip; the controller's rejection path (`:109`) still owns the stop, unchanged and already tested |
| A future author "fixes" the collector anyway | **Medium — measured** | The source check `checkTripSendGate` fails the build and names file:line, the call and the rule (review finding 7; the unit cases alone did **not** catch it) |
| The check's own shape guard misfires after a rename | Low | The scanner fails loudly ("no trip collector found … cannot be checked") instead of passing silently; its unit cases cover that and the unclosed-lambda shape |
| Spec drift from `Design.md` / `DesignCrossVariant.md` | Low | No guideline text changes here; the reference sentence is recorded in `proposal.md` for whoever lands after the split, and the spec requirement is the contract either way |

## Verification

- **Unit**: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.NavigationSessionTripSendTest"` for
  the new cases; the affected existing suites (`…NavigationManagerControllerTripTest`, `…NavigationManagerControllerTest`,
  `…SessionHostGateTest`) re-run in the same command.
- **Revert-check** (new invariant): mutate `tripSendAllowed` to `navigating && sessionStarted` →
  `tripKeepsUpdatingWhileTheSessionIsNotStarted` must fail; restore; re-run forced green
  (`-PforceTests --no-build-cache`).
- **Gate**: one full both-flavor run before the change is complete (`guidelines/Build.md` §2, §4).
- **Structural**: `openspec validate trip-publishing-outside-the-session-gate --strict`.
- **No device measurement is claimed.** This change produces no pixels, no rendering behaviour, no diagnostic
  line content and no host-traffic volume change; there is nothing for an AVD run to measure, and a stationary
  emulator could not produce the backgrounded-navigation state deterministically anyway.
