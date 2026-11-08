# Design

## Root cause

Both presence publishes were inline `carSessionPresence().setActive(...)` calls inside
`NavigationSession`'s `onStart` and `onDestroy` lifecycle callbacks. `NavigationSession` needs a
host-provided `CarContext` and cannot be constructed under Robolectric, so neither callback was
reachable from a unit case.

The first correction extracted one generic seam with a `live` boolean. Review round 1 found that the
four cases then proved only the helper's two modes: swapping `live = true` and `live = false` at the
production call sites left every case green. The gap was therefore not helper semantics but
lifecycle-to-direction wiring.

## The single fix

Give each lifecycle phase its own host-free seam and bind the production callbacks to those seams:

- `publishCarSessionPresenceOnStart(presence)` publishes live.
- `publishCarSessionPresenceOnDestroy(presence)` clears the signal.

Both delegate to one private helper, `publishCarSessionPresence(presence, live)`, which retains the
lazy provider resolution inside `guardedHostCall` and the existing publish/clear rejection names.
The production `onStart` callback selects the start seam; `onDestroy` selects the destroy seam.

The runtime cases prove each seam's direction and fault confinement. One source-level case reads
`NavigationSession.kt`, extracts the two callback bodies, and requires each to select only its
matching seam. This is the minimum extra reach needed because the session class cannot be
constructed in a JVM test. It makes the call-site swap fail instead of remaining inspection-only.

## Alternatives the evidence eliminated

- **One generic seam with a `live` boolean.** Rejected by review round 1: swapping the production
  arguments left all four cases green, so the helper semantics did not close the named gap.
- **An androidTest that owns a real session.** The item names it, but the project has no instrumented
  car-session harness and the direction is host-free.
- **Build `NavigationSession` under Robolectric with a fake `CarContext`.** The host supplies
  `CarContext`; the existing session tests state that this is why their decisions are seams.
- **Extract the whole callback body.** That pulls the surface host, host gate, navigation manager,
  location provider, view model and session scope behind a new interface. The gap concerns only the
  two presence directions, so the broader extraction would add surface without adding an assertion.
- **Change `CarSessionPresenceImpl` or its indicator.** Those cover the signal itself, not which
  lifecycle callback selects it.

## Blast radius

- `NavigationSession.kt`: two internal seam functions, two production call sites and one private
  helper. No host API, coroutine, state, protocol, native or DI change.
- `SessionPresencePublishTest.kt`: four existing seam cases plus one production-wiring case.
- No compiled cross-module surface: the seams remain `internal` to `:auto`.

## Threading and lifecycle

The seams run at the same lifecycle points as the inline calls: synchronously inside the car-app
library's main-thread lifecycle dispatch. They own no scope and start no work. Resolution and publish
remain inside `guardedHostCall`, so a rejected publish is a logged no-op that never escapes the
dispatch.

## Verification

- Red on the pre-correction tree:
  `SessionPresencePublishTest#sessionCallbacksSelectMatchingPublishDirection` — XML
  `tests=1 failures=1 errors=0`, timestamp `2026-11-08T15:37:28.251Z`
  (`evidence/review-round-2-red.xml`).
- Focused green suite: `SessionPresencePublishTest` — XML `tests=5 failures=0 errors=0`, timestamp
  `2026-11-08T15:40:27.155Z` after the final restore (`evidence/revert-check-green.xml`).
- Revert-check: one production mutation changed the `onStart` callback to the destroy seam. The
  wiring case failed with `the onStart callback selects the live publish`; XML
  `tests=5 failures=1 errors=0`. Restoring the call produced XML `tests=5 failures=0 errors=0`
  (`evidence/revert-check-red.xml`, `evidence/revert-check-green.xml`). The post-restore production
  diff and marker check are in `evidence/production-diff.txt` and `evidence/surface.txt`.
- The fresh gate owns the full-suite result for this corrected tree; its `LOOP-GATE` line is carried
  whole by `evidence/gate.log`. Its own tally for this class is `tests=5 failures=0 errors=0`, timestamp
  `2026-11-08T15:41:50.604Z` (`evidence/gate-SessionPresencePublishTest.xml`), and it carries the
  wiring case. The earlier gate record measured a different tree and is not reused.

## Rollback

Restore the two inline guarded publishes, remove the two lifecycle-specific seams and return the
test to its prior four cases. The publishes are again device-only; no persisted data or observable
behaviour changes.

## Open decisions

none
