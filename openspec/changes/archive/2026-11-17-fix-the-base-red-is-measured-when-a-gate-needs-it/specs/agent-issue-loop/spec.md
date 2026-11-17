# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: A step red on the run's base is subtracted from the item's tally and named

A run SHALL measure a gate step's state on the base the run starts from when an item's gate first needs it, and
record the failing case names it found there. A gate SHALL subtract exactly the pairs that record holds — a case it
fails now and the base failed leaves the tally, a case it fails only now stays — name what left as
`LOOP-GATE-BASERED:<step>:<n>`, and write the pairs to the run's record. A step whose base state no measurement could
take SHALL leave its red with the item.

#### Scenario: The pre-flight names each red step and the case that failed there, and writes no tally

- **WHEN** a base-red measurement is taken over a step set of the base whose steps are red
- **THEN** it prints one `LOOP-BASERED-STEP:<step>|<n>` line and one `LOOP-BASERED-CASE:<step>|<case>` line per red
  step and failing case, writes nothing into the gate's tally directory, holds no tally lock, and records the pairs
  as the run's own base-red record
- **Case**: `gate#aBaseRedStepIsSubtractedAndNamed`

#### Scenario: The measurement is narrowed to the step the gate found red

- **WHEN** a gate over an item's delta finds a step red and the run's record holds no state for that step
- **THEN** it measures that one step on the base in a clean tree at the base commit, prints one
  `LOOP-GATE-BASERED-MEASURE:<step>` line naming the tree and the output it keeps, writes nothing into the gate's tally
  directory, holds no tally lock, spends no `runs_max`, and records the failing step-and-case pairs as the run's
  base-red record with the commit it measured as that record's base — measuring none of the profile's other steps
- **Case**: `gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt`

#### Scenario: A step the record already holds costs no step

- **WHEN** a later gate finds red a step the run's base-red record holds
- **THEN** nothing is measured, no `LOOP-GATE-BASERED-MEASURE` line is printed, and the pairs the record holds are
  subtracted as they are for the gate that took the measurement
- **Case**: `gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt`

#### Scenario: A step red on the base and red for no new case is subtracted and named

- **WHEN** the gate over an item's delta runs a step the run's base-red record holds, and the cases that step fails now
  are the cases that record names
- **THEN** the gate exits 0 from that step, prints `LOOP-GATE-BASERED:<step>:<n>` on the console and in its log,
  quotes `— base-red: <step>:<n>` beside the verdict, and records the pairs it subtracted in the run's record
- **Case**: `gate#aBaseRedStepIsSubtractedAndNamed`

#### Scenario: A step red on the base and red for a case the base never had is still red

- **WHEN** a step the base-red record holds fails now for a case the record does not name beside it
- **THEN** only the pairs the record holds leave the tally, the step is still named in the verdict's failing list, and
  the subtraction is never widened to the step's whole count
- **Case**: `gate#aBaseRedStepIsSubtractedAndNamed`

#### Scenario: A red no pre-flight measured is charged to the item

- **WHEN** no measurement of a step's base state could be taken or written — the switch is off, the run records no base,
  the base is not a commit the tree can read, no clean tree at the base commit exists, the measurement failed to run, or
  it could not be written into the run's record
- **THEN** no step and no case is subtracted, where the measurement was attempted an
  `LOOP-GATE-BASERED-UNMEASURED:<step>` line names why, and every red the gate reports stays charged to the item
- **Case**: `gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt`

## ADDED Requirements

### Requirement: A worktree of the run's own repository carries the run's identity

The loop SHALL honour an inherited run state path that lies inside the main worktree of the repository the process
resolved, and SHALL ignore one outside it. A gate run in the run's own kept worktree SHALL read and write the run's
record.

#### Scenario: A gate in the item's worktree writes the run's record

- **WHEN** a gate run in a linked worktree of the project subtracts a base-red pair
- **THEN** the pair is written to the run's own record at the project's state path, and the record it read is the one the
  project holds
- **Case**: `gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt`

#### Scenario: A state path another tree names is still ignored

- **WHEN** a process inside a tree is handed a state path that lies outside that tree and outside the main worktree of
  the repository it resolved
- **THEN** the path is not written and the tree resolves a state file of its own
- **Case**: `state#inheritedStatePathOutsideTheTreeIsIgnored`
