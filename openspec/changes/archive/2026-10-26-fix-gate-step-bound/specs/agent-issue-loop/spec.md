# Spec Delta

## ADDED Requirements

### Requirement: A gate step is bounded, and the step that overran is named

A gate SHALL bound every step it runs with `LOOP_GATE_STEP_MAX_SECONDS` (default `1800`), measured from the moment the
step starts, and SHALL end a step that reaches it together with everything it started. The step SHALL then be the
gate's failure: named on the console and in a `LOOP-GATE-STEP:` line of its own tally, counted as an error, and
invented as no case. A step that returns inside the bound SHALL keep its own status and numbers.

#### Scenario: A step that does not return is ended and named

- **WHEN** a step's command does not return within `LOOP_GATE_STEP_MAX_SECONDS`, the bound being a recorded profile
  value
- **THEN** the gate ends that step with the processes it started, its console line says the step overran the bound,
  its tally carries the `LOOP-GATE-STEP:` line naming the command and `errors="1"` with no case invented, and the
  gate exits non-zero naming the step — so a worker reads which suite overran instead of being ended with its budget
  spent
- **Case**: `gate#aStepIsBoundedAndTheOverrunNamesIt`

#### Scenario: A step that returns inside the bound keeps its own status

- **WHEN** a step's command ends before the bound, passing or failing, and writes its own summary
- **THEN** the step's exit status is the verdict, its case and failure counts are the summary's, its tally carries no
  error, and its console line is the plain `gate: <command> — <n>s` the console already carried
- **Case**: `gate#aFailingSuiteIsReadFromTheSameStream`
