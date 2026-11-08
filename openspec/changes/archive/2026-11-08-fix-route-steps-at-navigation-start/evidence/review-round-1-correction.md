# Review round 1 — the correction applied

Review round 1 (`evidence/review-round-1-requested.md`, `reviewer-report-1.md` in the item directory) refused
this change on one blocking finding, and the run ended the rework at its context bound
(`evidence/review-round-1-rework-cut.md`). This file records the rework that finished it.

## The finding

The change extended the body of `navigation-engine` §"One navigation state shared by all surfaces" from 387
to 562 characters, over the project's 500-character limit (`AGENTS.md`, OpenSpec artifact hygiene). The
project's own validator reported it as a new warning on that requirement —
`⚠ [WARNING] requirements[1]: Requirement text is very long (>500 characters)` — while the base tree warned
only on `requirements[5]` ("Engine lifecycle and threading", 623 characters, untouched by this change).

## The correction

The added body clause duplicated what the two new scenarios already state in SHALL form, so the body is
shortened to a single sentence and the contract stays in the scenarios. The same two lines are corrected in
the shipped spec and in the delta copy:

- `openspec/specs/navigation-engine/spec.md:40-41`
- `openspec/changes/archive/2026-11-08-fix-route-steps-at-navigation-start/specs/navigation-engine/spec.md:11-12`

```
 every surface renders the same navigation session. The state's step list SHALL be part of that session
 from its start.
```

No scenario, no case, no production file and no other spec line is touched by this correction.

## Validation after the correction

`openspec validate navigation-engine --strict` (rc=1, the base tree's own exit code):

```
Specification 'navigation-engine' has issues
⚠ [WARNING] requirements[5]: Requirement text is very long (>500 characters). Move examples and edge cases into scenarios, or split it into separate requirements that each state one behavior.
```

`requirements[1]` no longer warns: the requirement body is 472 characters, under the 500-character limit.

## The focused suite re-run after the correction

```
./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.NavigationEngineStepIndexTest" \
  -PforceTests -PjvmTestsOnly --no-build-cache --rerun-tasks
```

`BUILD SUCCESSFUL in 1m 47s`; the case tally `tests="3" skipped="0" failures="0" errors="0"`,
`timestamp="2026-11-08T07:21:43.772Z"`, all three cases present
(`startNavigation_publishesTheRoutesStepsBeforeAnyPositionUpdate`,
`startNavigation_clearsStaleStepsFromPreviousRoute`,
`onNextStep_advancesIndexMonotonicallyThroughRepeatedManoeuvres`). The red-on-HEAD and green-focused runs
that prove the fix are retained as `evidence/TEST-NavigationEngineStepIndexTest-red-on-HEAD.xml`
(`tests="3" failures="2"`, `2026-11-08T01:50:03.391Z`) and
`evidence/TEST-NavigationEngineStepIndexTest-green-focused.xml` (`tests="3" failures="0"`,
`2026-11-08T01:52:14.074Z`).

## Not touched

`tools/check-rule-routes.sh` stays red at base (six `**Rule:` names reached by no glob, issue #826) and is
attributed, not repaired, in `evidence/gate-waived-check-rule-routes.md`. This correction touches only the two
spec files above, so it adds no tracked path and no `**Rule:` line and leaves that verdict unchanged.
