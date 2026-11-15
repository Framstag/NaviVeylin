# agent-issue-loop delta

## ADDED Requirements

### Requirement: A step red on the run's base is subtracted from the item's tally and named

A run SHALL measure, before it works anything, which of the gate's steps the base it starts from is already red at,
and record the failing step-and-case pairs. A gate SHALL subtract exactly the pairs that record holds — a case it
fails now and the base failed leaves the tally, a case it fails only now stays — print `LOOP-GATE-BASERED:<step>:<n>`
on its console, log and verdict, and write the pairs to the run's record. A pre-flight it cannot take SHALL leave
every red with the item.

#### Scenario: The pre-flight names each red step and the case that failed there, and writes no tally

- **WHEN** the run takes the pre-flight over a base whose steps are red, on a step set the profile owes
- **THEN** it prints one `LOOP-BASERED-STEP:<step>|<n>` line and one `LOOP-BASERED-CASE:<step>|<case>` line per red
  step and failing case, writes nothing into the gate's tally directory, holds no tally lock, and records the pairs
  as the run's own base-red record
- **Case**: `gate#aBaseRedStepIsSubtractedAndNamed`

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

- **WHEN** the run takes no pre-flight — the switch is off, the run records no base, the base is not a commit the tree
  can read, or the tree carries uncommitted work — and the base could not be measured
- **THEN** no step and no case is subtracted, and every red the gate reports stays charged to the item
- **Case**: `gate#aBaseRedStepIsSubtractedAndNamed`