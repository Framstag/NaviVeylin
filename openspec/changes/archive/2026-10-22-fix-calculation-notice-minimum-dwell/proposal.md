# fix-calculation-notice-minimum-dwell

## Why

Issue #84 (filed as §133). Verified in the tree today: `NavigationSession.applyCalculationNotice()`
removes the notice the moment the calculation state clears — on HEAD `NavigationSession.kt:937` called
`removeCalculationNotice()` directly — so the navigation view's push (the `isNavigating` edge,
`NavigationSession.kt:771-791`) is followed immediately by the removal that reveals it.

**Root cause, one sentence:** the notice's removal is driven by the calculation state alone, so a route
that arrives shortly after `CALCULATION_NOTICE_DELAY_MS` (400 ms, `NavigationSession.kt:1167`; the push
follows the `delay` at `NavigationSession.kt:971`) has already replaced a screen the driver saw for only
the remainder of that band — measured 2026-10-05 on the AAOS AVD (automotive `x86_64`, North
Rhine-Westphalia): an 847 ms town route pushed the notice at 18:10:38.668 and the navigation view
replaced it at 18:10:39.111, **443 ms** of notice.

The archived change that introduced the delay deferred exactly this fix on one condition — its
`design.md:54-55`: "No minimum-visible dwell for the notice (declined during exploration; revisit only if
the measurement shows a populated 400–800 ms band)" — and its own Risks section recorded that the band
*is* populated and filed this item rather than building it (`design.md:213-216`, in
`openspec/changes/archive/2026-10-09-show-route-calculation-progress/`). The condition is met, so the
dwell is the recorded next step rather than a reversal of a decision.

**The red test** (`auto/src/test/java/com/naviveylin/auto/CalculationNoticeDwellTest.kt`), three cases:
`aRouteArrivingInsideTheDwellKeepsTheNoticeUp`, `theDriversOwnAnswerIsNeverDelayedByTheDwell`,
`aNoticeThatOutlastedItsDwellIsReplacedAtOnce`.

- HEAD: `tests="3" failures="3"`, timestamp `2026-10-22T10:59:03.025Z`
  (`evidence/red-HEAD-focused.log`), each failing with
  `java.lang.AssertionError: the calculation notice's removal consults no minimum visible dwell:
  NavigationSession keeps no calculationNoticeDwellRemainingMs(shownForMs, replacedByNavigation) …`.
  The case is reached by reflection for exactly this reason: on HEAD the seam does not exist, and a
  direct call would fail the *compile* — a run with no result to compare. `evidence/red-seam-absent.xml`
  is the same three failures reproduced on the fixed tree with the seam's name withheld, so the red
  artifact is reproducible after the fix exists.
- After the change: `evidence/green-CalculationNoticeDwellTest.xml`, `tests="3" failures="0"`,
  timestamp `2026-10-22T11:00:25.208Z` (the post-restore green run of the falsification below; the first
  green run, `2026-10-22T10:59:21.068Z`, is quoted in `evidence/green-focused.log`); the decision cases
  that already existed stay green (`evidence/green-CalculationNoticeDecisionTest.xml`, `tests="8"
  failures="0"`) and so does the notice's bookkeeping (`evidence/green-SessionScreenStackTest.xml`,
  `tests="16" failures="0"`).

**Falsification (revert-check), two mutations, each restored to the byte-identical file
(`md5 b803e5e1fb7a23dfa5117c7b11edc2e3`):**

- the seam's name withheld → the same three failures as HEAD (`evidence/red-seam-absent.xml`,
  `tests="3" failures="3"`, `2026-10-22T11:00:16.405Z`) — the red artifact is reproducible after the
  fix exists;
- `CALCULATION_NOTICE_MIN_DWELL_MS = 0L` (the pre-fix behaviour: no dwell) → `expected:<257> but was:<0>`
  and `expected:<1> but was:<0>` (`evidence/mutation-dwell-zero.xml`, `tests="3" failures="2"`,
  `2026-10-22T11:00:21.781Z`). The third case passes under this mutation by design — it asserts that a
  cancellation owes nothing, which a zero dwell also answers; that is why the band case, not it, is the
  mutation's target.

**Owning capability:** `route-calculation-feedback` — a new requirement, "A notice the navigation view
replaces stays visible for a minimum dwell" (the shipped requirement "The car notice never outlives its
calculation" and its five scenarios are unchanged: the navigation view still replaces the notice, one
dwell later).

**Owning guideline section:** `guidelines/UI.md` §3c "Car wait notices (a wait the driver can see and
leave)", its "Never outlives its work" bullet (`UI.md:433-435`).

**Surface, counted before the plan was fixed** (all `git grep … HEAD`, i.e. the tree the change
starts from):

- `git grep -c -e noticeIsStale -e removeCalculationNotice -e noticeArmJob HEAD -- auto/src app/src
  core/src` → 14 hits in 2 files: 11 in `NavigationSession.kt` (the seam and its 3 call sites — the
  sync, `applyCalculationNotice`, and the deferred-dismissal path) and 3 in
  `CalculationNoticeDecisionTest.kt`; nowhere else removes the notice.
- `git grep -l -e CalculationNotice -e "calculation notice" HEAD -- auto/src/test` → 4 case files:
  `CalculationNoticeDecisionTest` (the decision seams, 8 cases), `SessionScreenStackTest` (the notice's
  bookkeeping), `GermanRenderingTest` and `StartupScreensTest` (the notice's wording / the retired
  screen's class). None of them asserts the removal's timing, and `git grep -l -e "calculation notice"
  HEAD -- tools` → none.
- `git grep -l -e "car notice" -e "calculation notice" HEAD -- openspec/specs guidelines` → 4 specs
  (`route-calculation-feedback`, `auto/navigation-view`, `rerouting-visual-feedback`,
  `routing-failure-reporting`) and 0 guideline files by that spelling; the guideline section that owns
  the rule is `UI.md` §3c, found through `AGENTS.md`'s Documentation Map.

So: **3 cases added, 0 rewritten, 0 fixtures realigned** — 1 new case file, 1 production seam plus its
wiring in `NavigationSession.kt`, 1 guideline bullet, 1 capability delta. No resource, no build script,
no native code.

surface: 3 cases added, 0 rewritten, 0 fixtures realigned — `CalculationNoticeDwellTest` (new, 3 cases),
`NavigationSession.kt` (seam + wiring), `guidelines/UI.md` §3c, one capability delta — re-read by
`./tools/loop/measure.sh --change fix-calculation-notice-minimum-dwell --base e1c9b74`

## What changes

- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt`:
  `CALCULATION_NOTICE_MIN_DWELL_MS = 700L` and the pure seam
  `calculationNoticeDwellRemainingMs(shownForMs, replacedByNavigation)`; the session records when the
  notice landed (`calculationNoticeShownAtMs`, monotonic), replaces the immediate removal in
  `applyCalculationNotice()` with `scheduleCalculationNoticeRemoval()` (a dwell job that removes by
  identity when the navigation view is what replaces the notice), cancels that job when a live
  calculation takes the notice over, clears both when the notice leaves, and treats the root pop as the
  owner of the notice's departure.
- `auto/src/test/java/com/naviveylin/auto/CalculationNoticeDwellTest.kt` (new): the three cases above.
- `guidelines/UI.md` §3c: the dwell rule, its number and the measurement it comes from.
- `openspec/specs/route-calculation-feedback/spec.md` (delta): one added requirement with three
  scenarios, each naming its case.

No dependency, no manifest, no native code, no build file, no resource. **Rollback:** delete the seam, the
two fields, the dwell job and the record in `pushCalculationNotice()`, restore the direct
`removeCalculationNotice()` call in `applyCalculationNotice()` — the notice leaves on the state change
again and nothing else moves.

## Out of scope, and why

The dwell does **not** apply to a cancellation or a failure: the driver's own tap and the error notice are
answers, not a screen the wait owes, and delaying them would tax the one interaction the notice exists to
offer. Reroute notices are covered by the same rule (the navigation view is already live under them, so
the dwell is the only thing that changes); the phone surface has no wait notice and is untouched.
