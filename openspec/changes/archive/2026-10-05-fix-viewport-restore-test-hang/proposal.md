# Proposal

## Why

`MapCanvasViewModelViewportRestoreTest` can suspend a coroutine forever and take a whole `:app` test
task with it: on 2026-10-02 both flavors' full unit-test runs stopped producing any verdict (the
automotive task ran >20 minutes and the mobile task >20 minutes, zero result XML, no timeout firing),
while the same suites are green as soon as this one class is left out. The cause is the class's own
gating helper: `FirstDispatchGatedDispatcher` holds *only the first* dispatch and nothing guarantees
that the first dispatch is the one the test means to hold, so when the intended load arrives after
`release()` it is queued with nothing left to drain it — and `runTest`'s timeout cannot fire, because
the test thread is parked in `runBlocking` while Robolectric's main thread spins.

This is a **test-only** defect (no product behaviour is wrong), but it is a verification defect: every
change whose gate is "the `:app` suites are green" loses its verdict on this machine, and the only
workaround is enumerating classes to exclude one. `TODO.md` §117 carries the observation, the `jstack`
evidence and the fact that it is independent of every change that ran into it.

## What Changes

- **The gate no longer swallows a post-release dispatch.** `FirstDispatchGatedDispatcher` records that
  it has been released; every dispatch after that runs inline, so no coroutine can be left queued with
  no drainer.
- **The affected cases assert the window they depend on** instead of assuming the scheduler (or a real
  dispatcher) already got them there: a bounded `awaitHeldBlock` poll waits — and fails with a message
  if it times out — for the gate to actually hold a block, for the restore to be applied, and for
  `saveViewport` to persist.
- **Two more cases in the same class lose their real-time assumptions**, which is the same defect in a
  milder form: the render wait had a 5 s deadline that the first render reaches at ~5 s on a loaded
  host (observed failing with `expected:<1> but was:<0>`), and the save case dereferenced a file that
  is only written once the restore had applied (observed `NullPointerException`).
- **No production code changes**, and nothing about the behaviour under test changes: the cases keep
  their falsifying power (two revert-checks below).

## Capabilities

None. This change modifies only a test file: no requirement's behaviour changes, so the change marks
`skip_specs: true` in its `.openspec.yaml` instead of inventing a spec delta. The behaviour these
cases pin — the viewport restore window and its save guard — is already specified in `viewport-persist`
and `map-render`; the fix makes that proof stop hanging, not stronger or weaker in a way a spec could
express.

## Impact

- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelViewportRestoreTest.kt` — the gate
  (`GatedDispatcher`, `FirstDispatchGatedDispatcher`), the shared bounded waiter, and four cases
  (`screen size during viewport restore does not clobber restored center`, `saveViewport after restore
  persists current viewport`, `re-entry while init suspended keeps the new map viewport`,
  `saveViewport during re-entry window keeps persisted viewport`).
- No other file: no production source, no build script, no dependency, no resource, no spec.
- Consumers: the `run-tests` / `build-app` skills and every change that gates on the `:app` suites.
