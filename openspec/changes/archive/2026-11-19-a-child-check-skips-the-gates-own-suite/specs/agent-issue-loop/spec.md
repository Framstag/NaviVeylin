## MODIFIED Requirements

### Requirement: The checks a child runs by hand are bounded while it runs

A child's hand-run checks SHALL be counted while it runs, from the lines its stream has gained, and SHALL end it at
the recorded `LOOP_CHILD_CHECKS_MAX`: the `LOOP-TIMEOUT` line SHALL name that key and the count that reached it. A
counted call is one that **runs** a program the gate's own checks start, narrowed to one case, less the gate's own
run; a call that only names such a path, a `--plan` call, and a call that runs one of those programs whole SHALL NOT
count. No key, or `0`, SHALL bound nothing.

#### Scenario: A child past the recorded check bound is ended

- **WHEN** a child's stream gains four tool calls — two that only read the gate's own file and two that run programs
  the gate's map names — while `LOOP_CHILD_CHECKS_MAX` records 2 and the wall clock and the turn bound keep their
  recorded defaults
- **THEN** the run ends the child, the run's header names `child-checks-max=2`, the child's transcript carries
  `LOOP-TIMEOUT: the child reached the recorded check bound of 2 checks (LOOP_CHILD_CHECKS_MAX) after 2 check(s)`, and
  the item is recorded `cut` with `bound` as its stop reason, as every bound the run enforces ends one
- **Case**: `run#childPastItsCheckBoundIsCut`

#### Scenario: A call that only names a check's path is not a check

- **WHEN** that child's stream holds `sed -n 1,60p tools/loop/gate.sh` and `git show HEAD:tools/loop/gate.sh` beside
  the two checks it ran
- **THEN** the transcript's count reads `2 check(s)` and not `4`, so reading the gate's own file neither pays the
  gate's clock nor ends the child
- **Case**: `run#childPastItsCheckBoundIsCut`

## ADDED Requirements

### Requirement: A whole-suite run is the gate's work, not a child's check

A call that runs one of the gate's check programmes without narrowing it to a case SHALL be classified with the gate,
so a child's check budget counts the focused cases the iteration protocol hands it and not the gate's own suite run
by hand. A narrowed call SHALL stay a child's check.

#### Scenario: A whole-suite run by hand spends no check

- **WHEN** a child's stream shows `tools/loop/tests/loop-selftest.sh --from-git <base>` or a Gradle test task with no
  `--tests` filter in one of its turns
- **THEN** `child_check_calls` adds nothing to the child's count for that call, and the count is the one the run's
  check bound reads
- **Case**: `run#aChildCheckSkipsTheGatesOwnSuite`

#### Scenario: A focused case is still counted

- **WHEN** a child runs one case (`--case <name>`, or `--tests <filter>`)
- **THEN** the call is counted, and a child that reaches `LOOP_CHILD_CHECKS_MAX` such calls is still ended at the
  bound with the key named on its `LOOP-TIMEOUT` line
- **Case**: `run#aFocusedCaseIsStillAChildCheck`, `run#childPastItsCheckBoundIsCut`
