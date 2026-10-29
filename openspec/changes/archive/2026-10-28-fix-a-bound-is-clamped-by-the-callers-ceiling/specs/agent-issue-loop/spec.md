## ADDED Requirements

### Requirement: A bound is clamped by the ceiling the caller named

An invocation that applies a bound SHALL read the ceiling of the process that started it from
`LOOP_CALL_CEILING_SECONDS`, the caller naming it at the call site, and SHALL never let one of its bounds outlive
that ceiling. An absent key, or an empty, non-numeric or `0` value, SHALL mean no ceiling: every bound keeps its
recorded value, validated like the profile's other bounds, and the invocation starts no watcher.

#### Scenario: A suite that cannot finish inside the ceiling ends itself, and says what it ran

- **WHEN** a caller names a ceiling smaller than the time the selected cases need, and the ceiling is reached with
  cases still queued or running
- **THEN** the suite prints `LOOP-SUITE-CEILING:` naming the ceiling it ended by, how many of the selected cases
  started and how many never started; ends the cases still running together with what they
  started; removes its scratch tree and every watcher it started; and exits non-zero — a cut selection is never a
  suite that passed
- **Case**: `driver#aSuiteEndsInsideTheCeilingItsCallerNamed`

#### Scenario: A gate step's clock is the ceiling when the ceiling is smaller

- **WHEN** the gate runs with a ceiling named below `LOOP_GATE_STEP_MAX_SECONDS` and a step reaches it
- **THEN** `LOOP-GATE-STEP-BOUND:` on the console names the ceiling as the clock in force before the first step
  starts, the gate ends that step with everything it started, its console line reads `the step overran its bound of
  <n>s and was ended`, its tally carries the `LOOP-GATE-STEP:` line and an error, and the gate exits non-zero —
  instead of being killed mid-step with no line written
- **Case**: `gate#theStepBoundIsClampedByTheCeilingTheCallerNamed`

#### Scenario: A ceiling the selection fits inside changes nothing

- **WHEN** a caller names a ceiling the selected cases finish well inside
- **THEN** every case keeps its own verdict, the suite prints the summary it always printed, no
  `LOOP-SUITE-CEILING:` line appears, and the suite leaves no process behind
- **Case**: `driver#aCeilingTheSuiteFitsInsideChangesNothing`
