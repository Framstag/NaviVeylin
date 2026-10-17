# Scenario to case traceability

Every scenario of `specs/backlog-sensors/spec.md` (29 of them — the row count below is
`grep -c '^#### Scenario:' specs/backlog-sensors/spec.md`), the case that exercises it, and the task that
owns that case. Every case runs offline against the fixture registry, the fixture sensors and the stub `gh`
on PATH: `bash tools/sensor-selftest.sh --case <case>`. No device, no token and no network is involved
anywhere in this change.

| # | Scenario | Case | Task |
|---|---|---|---|
| 1 | A row is the whole definition | `Registry#everyRowIsComplete`, `run#selectsOnlyDueRows` | 2.1, 2.3 |
| 2 | A row without a bound is refused | `guard#refusesARowWithoutThresholdOrConsecutive`, `Registry#everyRowIsComplete` | 1.3, 2.1 |
| 3 | Every declared check names its own label | `sensors#usesTheReservedNamespacesAndAddsNone`, `create#sensorFingerprintIsTheAnchor` | 1.4, 3.5 |
| 4 | The condition is present | `run#dispatchMatrix`, `sensors#coverageRow` | 3.4, 5.4 |
| 5 | The condition has cleared | `run#hysteresisBothDirections`, `sensors#buildDurationRow` | 4.1, 5.1 |
| 6 | The check itself failed | `run#errorPostsNothingAndFails` | 2.4 |
| 7 | Failing is not clearing | `run#errorIsNeverTreatedAsClear` | 2.4 |
| 8 | The same condition returns | `run#dispatchMatrix`, `reopen#neverWritesEligibility` | 3.3, 3.4 |
| 9 | A different cause under the same check | `sensors#brokenMainFingerprintDistinguishesCauses`, `create#sensorFingerprintIsTheAnchor` | 3.5, 5.5 |
| 10 | The condition is still present | `run#dispatchMatrix` (the open issue is annotated, never refiled) | 3.4 |
| 11 | A completed fix recurs | `reopen#neverWritesEligibility`, `run#dispatchMatrix` | 3.3, 3.4 |
| 12 | A decision not to act recurs | `run#dispatchMatrixNotPlannedClosure`, `reopen#refusesANotPlannedClosure` | 3.3, 3.4 |
| 13 | The recurrence is visible as a count | `reopen#neverWritesEligibility` (`occurrence: 2` after one reopen), `report#printsACountingLinePerRow` | 2.6, 3.3 |
| 14 | The recurrence bound is reached | `run#escalatesPastKRecurrences` | 4.2 |
| 15 | Escalating does not authorise work | `run#escalationNeverSetsEligibility` | 4.3 |
| 16 | A flapping condition does not churn | `run#hysteresisBothDirections` (the alternating sequence closes nothing, and the counter that is not accumulating is reset) | 4.1 |
| 17 | Another check's issue is refused | `close#sensorScopeRefusesAForeignIssue` | 3.2 |
| 18 | A person's issue is out of reach | `close#sensorScopeRefusesAForeignIssue` (the second half: an issue with no ownership label at all) | 3.2 |
| 19 | No check can delete | `sensors#engineNamesNoProjectValue` (the engine names no deletion and no GraphQL); the tooling-wide `surface#noDeletePath` covers `tools/issues/reopen.sh` as well | 7.3 |
| 20 | Change validation is unaffected | `workflow#onlyTwoTriggers` (a file of its own, two triggers); the diff check of task 9.4 | 6.1, 9.4 |
| 21 | A dispatch from another branch cannot act | `guard#refusesOffMain` | 1.2 |
| 22 | The identity is a concrete person | `guard#eachCheckHasItsOwnReason` (the identity guard asks the tooling's own gate), `update#commentRefusesOnPolicyFailure` | 1.1, 3.1 |
| 23 | Two runs cannot race | `workflow#onlyTwoTriggers` (the `concurrency` group is asserted) | 6.1 |
| 24 | The pipeline's own changes are reviewed | `workflow#onlyTwoTriggers` (`.github/CODEOWNERS` exists and covers `/.github/workflows/`) | 6.4 |
| 25 | The baseline is the previous measurement | `sensors#buildDurationRow` (the recorded number is what the bound is computed from, and what the issue quotes) | 5.1 |
| 26 | Nothing is committed by a check | `run#noBaselineFileWritten` | 2.5 |
| 27 | Growth is distinguishable from a level | `report#printsACountingLinePerRow` (`trend=worse` against the issue's own previous number) | 2.6 |
| 28 | The row's classification is used | `run#dispatchMatrix` (the filed issue carries `class/bug` and `category/build-and-harness`), `Registry#everyRowIsComplete` (both values are inside the profile's vocabulary) | 2.1, 3.4 |
| 29 | Filing is not authorising | `run#dispatchMatrix` (a filed issue carries no `agent/eligible`), `run#escalationNeverSetsEligibility` | 3.4, 4.3 |

## The cases this change's own tasks name

Task 1.1, 5.2 and 7.3 name cases that are not scenarios of the spec delta but asserts about the tooling
itself: `guard#eachCheckHasItsOwnReason` (five guards, five reasons and five exit codes),
`hotspot#ranksBySizeTimesChanges` (the ranking, and that the bound comes from the row) and
`sensors#engineNamesNoProjectValue` (the engine names no project value). They are in `SENSOR_CASES`
alongside the rest.

## Falsification

Every mutation of group 8 and the case it must turn red is recorded in `report.md`. `revert-check`-style, each
was applied to the shipped script, run, and restored — nine of nine behaved, and the two cases that could not
be shown red at first were strengthened until they could.
