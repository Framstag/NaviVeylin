# Item #780 — evidence

Change: `fix-settled-escalation-files-an-item` (change root `/home/tim/projects/NaviVeylin/.pi/worktrees/780`,
base `ec58cb91177f2d1c4621b87cdd26a9c7fced5117`). The defect: an escalation past the recurrence bound on an
issue the profile declares settled was refused by `tools/issues/update.sh` and reported as landed, so a red
main filed no item at all.

Every line below is a command's own output, kept beside this file.

## The red case, on the base

`bash tools/sensor-selftest.sh --case 'run#anEscalationOnASettledIssueFilesANewItem'`, `2026-11-05T07:52:12Z`,
`FAIL` (`evidence/red-on-HEAD.log`, tally `evidence/red-on-HEAD.xml`):

```
  the returned condition is filed as a new item: status-settled: #98 carries status/fixed-by, a settled state of the project profile — pass --displace-settled to name the transition
fixture-emit: fingerprint=48a09a835ee92d32ebcf0d2132fdfc6048aa30639ce1644360e52f3086ddbc51 verdict=violation measurement=5 issue=98 action=escalated (past 3 recurrences): expected '1', got '0'
FAIL run#anEscalationOnASettledIssueFilesANewItem
```

## The suite, on the round-2 tree

`bash tools/sensor-selftest.sh` — `sensor-selftest: all 84 cases passed (JDK 27, offline, no token)`, exit 0,
`2026-11-05T19:31:34Z` → `2026-11-05T19:37:00Z` (`evidence/sensor-suite.log`, 84 `PASS` lines, 0 `FAIL`, the
log carrying its own start and end).

## The gate, on the round-2 tree

`./tools/loop/gate.sh --base ec58cb91`, `2026-11-05T20:04:49Z` → `2026-11-05T20:18:49Z`, exit 0 —
`LOOP-GATE: tests=309 failures=0 errors=0` (`evidence/gate.log` is the console stream, `evidence/gate-summary.txt`
the verdict and its scope, the 19 tallies are beside them, and `red-on-HEAD.xml` is the red case's own).
`tools/sensor-selftest.sh` inside that run: `tests="84" failures="0" time="452"`.

## The revert-checks, both recorded

`evidence/revert-check.log`, each mutation planted, run, restored and re-run:

1. `sensors_escalate`'s `return "$code"` → `return 0  # REVERT-CHECK MUTATION` (credit a write that did not
   land): `run#anEscalationOnASettledIssueFilesANewItem` `FAIL` at `2026-11-05T19:30:47Z`, `expected '1', got
   '0'` on `the returned condition is filed as a new item` — the red run's own assertion; restored,
   `PASS` at `19:30:57Z`.
2. `sensors_issue_for_fingerprint`'s `then .[0]` → `then .[-1]  # REVERT-CHECK MUTATION` (read the oldest item,
   so every tick files another duplicate): `run#theFiledConditionIsOpenWorkOnTheNextTick` `FAIL` at
   `2026-11-05T19:31:09Z`, `expected '0', got '1'` on `the returned condition files no second duplicate while
   its own item is open`; restored, `PASS` at `19:31:25Z`.

`grep -c 'REVERT-CHECK MUTATION' tools/sensors/run.sh tools/sensors/lib.sh` = 0, 0; `git status --porcelain`
over `tools/` empty after each restore.

## The change's own numbers

`bash tools/loop/measure.sh --base ec58cb91 --change 2026-11-05-fix-settled-escalation-files-an-item`
(`evidence/measure.txt`), the lines that are not per-path:

```
LOOP-MEASURE-SUITE cases=314 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=843 suites=19
LOOP-MEASURE-CHANGE 2026-11-05-fix-settled-escalation-files-an-item scenarios=6 cases=6 unnamed=0
LOOP-MEASURE-SURFACE none — the proposal carries no surface: line
```

## The reads that are not runs

- `bash tools/check-openspec-hygiene.sh` — exit 0, inside the gate and again on its own after the spec
  requirement's text was shortened (`docs-route` and `rule-route` checks likewise exit 0 in the gate).
- `openspec validate backlog-sensors --type spec --strict` — `Specification 'backlog-sensors' is valid`; the one
  remaining warning is `requirements[11]` (a requirement the change does not touch, 564 characters on the base
  too). The requirement this change modifies reads 457 characters after the round-2 shortening.

## The diff under review

`git diff ec58cb91...HEAD`, evidence tree excluded: `tools/sensors/run.sh` (two functions and the `sensors_file`
flags), `tools/sensor-cases.sh` (three cases), `guidelines/BacklogTooling.md` §9 (one clause in each of two
rules), `openspec/specs/backlog-sensors/spec.md` and the archived change.
