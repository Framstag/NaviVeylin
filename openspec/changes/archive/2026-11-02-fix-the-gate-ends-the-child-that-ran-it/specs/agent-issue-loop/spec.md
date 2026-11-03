# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The gate ends the child that ran it, and a fresh child writes the archive

The worker phase of a gated item SHALL be two fresh children. The first SHALL end when it reports its gate line, and
a second SHALL carry the gate's result, the change's `tasks.md` and the gate's own output into the evidence and
archive phases, so the tail is charged the project's baseline once instead of the first child's context per turn. An
item's recorded spend SHALL be the sum of its children's streams.

#### Scenario: The gate line ends the child that ran the gate

- **WHEN** an item's first child reports a `LOOP-GATE` line and was not ended by the run's own clocks
- **THEN** that child's session ends there, a second fresh child is started for the evidence and archive phases, and
  the run reads the item's verdict, change, gate line and decision from the second child's report — while a first
  child that reports `skipped`, `blocked`, `falsified` or no line at all starts no second child, because there is no
  archive to write
- **Case**: `run#childUsageIsRecordedPerItem`, `run#falsifiedSkipClosesWithItsMeasurement`

#### Scenario: The tail starts at the baseline, and the item pays for both children

- **WHEN** an item's two children each wrote an event stream
- **THEN** the item's recorded tokens, tools and cost are the sum of the two streams, and the largest context it
  records is the largest single session's — the second child's first turn is charged the project's baseline, not the
  context the first child had reached
- **Case**: `run#childUsageIsRecordedPerItem`

#### Scenario: The tail is handed the gate, the tasks and the gate's own output

- **WHEN** the second child is started
- **THEN** its prompt names the gate line the first child reported, the change and the `tasks.md` it planned, the root
  cause and red test the first child established, and a bounded extract of the gate it ran — the first child's own
  tool output, where the gate printed, and its closing answer after it — so it re-derives none of them and reads no
  path twice
- **Case**: `run#childUsageIsRecordedPerItem`

#### Scenario: A gate that did not pass is the tail's verdict, and the item is blocked with it

- **WHEN** the tail child finds that the gate did not pass on the change's own paths
- **THEN** it archives nothing and reports `blocked` with the gate line and the failing output it was handed, and the
  run records the item `blocked` with that verdict and the gate's own numbers in its reason — never as "no archived
  change appears in the run's diff", which is the opposite of what the tail reported and drops what the next attempt
  needs
- **Case**: `run#aRedGateIsBlockedByTheTail`

#### Scenario: The rework of a refusing round is split the same way

- **WHEN** a review round refuses a change and the repair worker the round buys reports its gate line
- **THEN** that worker ends there too and a fresh child carries its archive, because a repair round that paid the tail
  in-session would cost what the item did
- **Case**: `run#aBlockingRefusalStillTakesTheRoundTwoPath`

#### Scenario: A phase's checklist is the prompt of the child that is handed the phase

- **WHEN** the phase state is printed for a phase a second process performs
- **THEN** the checklist it prints is that process's own prompt section, and the call names the file it read it
  from, rather than a section of a prompt whose child has already ended
- **Case**: `phase#oneCallPrintsThePhaseState`
