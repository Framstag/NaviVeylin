# Red baseline (defect reproduced on HEAD, before any production change)

Recorded 2026-10-11, HEAD without the fix (`git stash`-clean tree, only the test added).

**Case:** `com.naviveylin.ui.map.MapCanvasViewModelBrowseReCenterTest.offCenterStartAtStandstillShowsTheButtonWithoutAnyFurtherEvent`

**Command:**

```bash
./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModelBrowseReCenterTest" -PnoCoverage
```

**Result:** `BUILD FAILED` — assertion failure at
`MapCanvasViewModelBrowseReCenterTest.kt:271` ("the dwell elapsing on its own must show the button
without a further fix or interaction").

**XML tally** (`app/build/test-results/testMobileDebugUnitTest/TEST-com.naviveylin.ui.map.MapCanvasViewModelBrowseReCenterTest.xml`):

```
tests="16" skipped="0" failures="1" errors="0"
```

| expected | actual |
|---|---|
| button visible after the dwell elapses with no further fix or viewport change | `browseReCenterVisible == false`, and it stays false because nothing re-evaluates the rule |

This is the defect `proposal.md` describes: the dwell is only ever evaluated when the UI state emits,
and the standstill case has no later emission.

## The mutations used for the two `revert-check` rounds (recorded verbatim — a mutant log alone cannot be re-checked)

**Mutation 1 — task 4.2, the clock path itself.** In `MapCanvasViewModel.kt`, inside the shared tick
loop, the publication guard was neutralised:

```kotlin
// before (fixed)
if (armedAtMs != 0L && timeSource.nowMillis() - armedAtMs >= RECENTER_DWELL_MS) {
// mutated
if (false && armedAtMs != 0L && timeSource.nowMillis() - armedAtMs >= RECENTER_DWELL_MS) {
```

Result: 6 of 22 in `MapCanvasViewModelBrowseReCenterTest` failed — `agedOutFixSuppressesThePendingDwell`,
`pressingReCenterDiscardsThePendingDwell`, `aSingleFlickPanRevealsTheButtonWithoutAFollowUpEvent`,
`leavingBrowseDiscardsTheDwellAndReturningReArmsIt`,
`offCenterStartAtStandstillShowsTheButtonWithoutAnyFurtherEvent`, `theDwellBoundaryIsDecidedByElapsedTime`
(`/tmp/recenter-mutant.log`, 2026-10-11 12:54:24Z). Restored, forced green 22/22
(`/tmp/recenter-restored.log`, 12:54:57Z).

**Mutation 2 — task 5.1, the tier in the guard (independent review finding P2-1).** The fix-unusable
tier was dropped from the same guard:

```kotlin
// before (fixed)
if (armedAtMs != 0L && quality != GpsFixQuality.NONE &&
    timeSource.nowMillis() - armedAtMs >= RECENTER_DWELL_MS
) {
// mutated
if (armedAtMs != 0L &&
    timeSource.nowMillis() - armedAtMs >= RECENTER_DWELL_MS
) {
```

Result: 1 of 23 failed — `anAgingFixDoesNotLetTheClockRevealTheButton`, i.e. the case written for that
gap is red exactly when the guard stops reading the derived tier (`/tmp/p2-mutant.log`). Restored, forced
green 23/23 (`/tmp/p2-green.log`).

## Device evidence artefact

The logcat excerpt of the device run on the revision that carries both fixes is
`device-logcat.txt` in this directory (task 5.6).
