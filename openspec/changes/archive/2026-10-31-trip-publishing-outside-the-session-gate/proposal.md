# Proposal

## Why

The session's trip collector (`auto/src/main/java/com/naviveylin/auto/NavigationSession.kt:727-733`) sends
`NavigationManager.updateTrip` while the car session is **not** started, and deliberately does not ask
`SessionHostGate.allowHostMutation()` the way the sibling navigation-state collector does (`:707`). The reason
is real: while the car app is backgrounded the vehicle cluster and heads-up display must keep showing
guidance, so the send is bounded by the *navigation* period (`navigating`, `NavigationManagerController.kt:69`,
`:101`) and not by the session start.

That deviation is currently stated nowhere a rule lives. It survives in one KDoc paragraph on
`SessionHostGate` and in the loop verdict of issue #71, while the shipped requirement
`openspec/specs/car-host-fault-isolation/spec.md:221-242` says a session that is not started "SHALL NOT …
change the host navigation state" — text a reader can only read as violated by the very next collector. Two
consequences measured on HEAD:

- **No test pins the deviation.** The trip cases (`NavigationManagerControllerTripTest.kt`,
  `NavigationManagerControllerTest.kt:144-161`) all run with a started session. Copying the sibling gate at
  `NavigationSession.kt:707` onto the trip collector — the "obvious" fix a reviewer will propose, exactly as
  issue #71 did — freezes the cluster while backgrounded and leaves every existing test green.
- **Issue #71 cannot be settled.** `status/on-hold`, `agent/eligible`, and two unresolved options (gate the
  send, or declare the exemption) with no measurement that separates them. This change settles it the way the
  spec's own bound already reads: the exemption is declared and asserted.

Why now: this is the closing move for #71 (`nvid: 56`) named in its loop verdict — "open a normal (non-loop)
change" — and it is cheap while the deviation is still only a KDoc.

## What Changes

- **Declare the bound.** `car-host-fault-isolation` gains a **new** requirement (ADDED, existing text
  untouched) that bounds trip metadata publishing by the navigation period, names the started/ended pair plus
  the screen and template mutations as what the session-start bound covers, and states that a stopped session
  neither defers nor drops a trip send.
- **Make the deviation a named decision.** The collector's decision is extracted as a pure seam
  `tripSendAllowed(navigating, sessionStarted)` in `NavigationSession.kt` (same pattern as
  `shouldRestoreFreeDriving`, `FreeDrivingRestoreGate`), and the trip collector is routed through it.
  **No host call changes**: the guard only skips a no-op (`tripFromState` returned `null` for a state that is
  not navigating). One internal delta, found by the independent review: the trip collector was the only
  *unconditional* creation site of the lazily built `navigationManagerController()`, so a session that never
  navigates no longer instantiates it and `onDestroy` skips the best-effort end/clear calls — no-ops on a host
  session that never started. The deviation itself is pinned by the two cases; the **call site** is not, because
  `NavigationSession` is not constructible in a unit test — `design.md` D2 "Residual gap" names both options.
- **Assert it.** One focused test class asserts both scenarios' THEN and AND lines: a stopped session + a
  displayed change ⇒ the update reaches the host (`updateTrip` called once, no start/end call, no deferred sync
  owed); navigation inactive ⇒ refused and nothing published, whether or not the session is started.
- **Pin the wiring as well.** No unit test can reach the collector's own line (`NavigationSession` needs a
  host-provided `CarContext`), so the change adds the source-level check the repo uses for rules tests cannot
  police: `:auto`'s `checkTripSendGate`, backed by the pure `buildSrc` object
  `com.naviveylin.build.testing.TripSendGateScanner` (unit-tested, no allowlist). It refuses a trip collector
  that asks `SessionHostGate.allowHostMutation()`, one that no longer takes its decision from
  `tripSendAllowed(...)`, and it fails loudly if no collector is found at all, so a renamed or moved collector
  cannot turn the rule off silently.
- **Guideline reference — deliberately not in this change.** `guidelines/Design.md` §8 already states
  "guidance updates and trip metadata keep flowing" on this base, so nothing needs changing for consistency. The
  section is being moved to `guidelines/DesignCrossVariant.md` §8a "The car host path and the surface" by the
  parallel `guideline-layers` change (uncommitted in the main tree at the time of writing), so editing
  `Design.md` here would collide with a split this change does not own. The sentence to add after that split
  lands, in the file that then owns the section:

  > trip metadata keep flowing (spec: `car-host-fault-isolation` — Trip metadata publishing is bounded by the
  > navigation period; the trip collector takes that decision from `tripSendAllowed`, and the `:auto` check
  > `checkTripSendGate` refuses a collector that asks `SessionHostGate.allowHostMutation()`).
- **Close the tracker item.** Issue #71 gets a comment naming the archived requirement and the test, and is
  closed as resolved-by-declaration rather than fixed-by-code — the code was already correct. The item's own
  first fix candidate (gate the send) stays rejected in `design.md` D1. Its stale
  `file:line` citations (`NavigationSession.kt:573-579`, `:664`, `:684-690`;
  `NavigationManagerController.kt:88`, `:101`; `NavigationTemplateMapper.kt:337-350`) belong to the resolver
  (`tools/issues/resolve-stale.sh`, report-only for an agent process). The item's `agent/eligible` label **stays**:
  it is the label `tools/issues/close.sh` gates the closure on (exit 3 without it), and a closed item is out of
  `--eligible-only` since 2026-10-27, so it costs no work slot. Lifting a loop hold and writing the closure are a
  person's or the loop's rows (`guidelines/Backlog.md` §5); this change prepares the closure text and its
  evidence and hands them over rather than writing them.

**Not in this change:** no session gate on the trip send, no change to `auto-navigation-hints` (it already
bounds publishing by the navigation period), no AVD run. Option B — gating the send — was considered and
rejected in `design.md`; choosing it would delete the shipped scenario "Guidance progresses while
backgrounded" and freeze the cluster, and its justification (host stability before the first rejection) is
unmeasured.

Additive change, no API or file-format break. **Rollback:** revert the change directory's implementation
commit — the spec delta disappears with the unarchived change, and the seam is a pure refactor, so nothing
downstream depends on it.

**Scope:** Android Auto / Android Automotive OS only (`:auto`). The phone variant neither sends nor owns a
host session and is untouched.

**Native/JNI:** none — no CMake, JNI bridge or submodule file is affected.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `car-host-fault-isolation`: one **ADDED** requirement, "Trip metadata publishing is bounded by the
  navigation period", with two scenarios (trip keeps updating while the session is not started; no trip send
  outside the navigation period). The capability's existing requirements and their scenarios are not modified.

## Impact

- **Code (`:auto`)** — `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt`: new pure seam
  `tripSendAllowed`; trip collector (`:727-733`) routed through it. `SessionHostGate.kt` KDoc gains the spec
  reference. No other production file changes.
- **Tests (`:auto` unit)** — new `auto/src/test/java/com/naviveylin/auto/NavigationSessionTripSendTest.kt`;
  existing suites unchanged and expected green (`NavigationManagerControllerTripTest`,
  `NavigationManagerControllerTest`, `SessionHostGateTest`).
- **Specs** — `openspec/specs/car-host-fault-isolation/spec.md` (after archive), plus this change's delta at
  `openspec/changes/trip-publishing-outside-the-session-gate/specs/car-host-fault-isolation/spec.md`.
- **Guidelines** — none edited: `guidelines/Design.md` §8 (the "while the car app is not the visible car app"
  bullet) is being moved to `DesignCrossVariant.md` §8a by the parallel `guideline-layers` change, so this change
  leaves it alone and records the sentence to add afterwards (see "What Changes").
- **Tracker** — `Framstag/NaviVeylin` issue #71: closure reason and evidence (the requirement name, the case
  name, and the archived change) prepared for the closing writer; stale-citation repair via
  `tools/issues/resolve-stale.sh`; `agent/eligible` stays as the closure gate's key.
- **No impact** — no Android manifest, resource, dependency, Gradle, CMake, vcpkg or ABI change; no phone-side
  behaviour; no log-line content change (`DiagnosticsLog` `HOST` line unchanged, still coordinate-free).
