## ADDED Requirements

### Requirement: The gate measures the diff from a base this tree resolves

The loop gate SHALL resolve the base it measures the diff from to the first of these that is a commit this tree knows and an ancestor of HEAD: its `--base` argument, the base the run recorded, the merge-base with the run's base branch, the merge-base with `origin/main`, `HEAD~1`. The base branch's current tip SHALL beat a recorded base. It SHALL print the commit and the source it took it from before its first step, and SHALL refuse to measure when none resolves.

#### Scenario: A stale remote is not the base

- **WHEN** a gate runs over a tree whose `origin/main` stands behind the branch the run landed on, and the run recorded a base that is an ancestor of HEAD
- **THEN** the gate measures HEAD against the base it resolved, prints `LOOP-GATE-BASE: <sha> (from <source>)` before its first step, and the suites the base already carries are not selected
- **Case**: `gate#theBaseIsReadFromTheRunNotFromTheRemote`

### Requirement: An answer of 0 bytes is read wherever the tree stands

A final report that exists, is 0 bytes and carries no verdict, `LOOP-TIMEOUT` or `LOOP-DEADLINE` SHALL spend one retry of the run's recorded budget over a tree that already carries the item's work, re-running the same child with the same prompt, and SHALL name the class `answer-empty` in the printed line and in the item's step. A second empty answer SHALL stop the run with both attempts and their byte counts; a report carrying its own verdict SHALL NOT be re-run.

#### Scenario: An empty answer over work is asked again

- **WHEN** a child of the run exits 0 with a 0-byte final report over a tree that already carries the item's work, the report carries no verdict, timeout or deadline, and the class's retry is unspent
- **THEN** the run waits the delay the configuration records, re-runs the same child with the same prompt, records the step `answer-empty`, and the answer that arrives decides the item — the first silence neither blocks the item nor asks its owner a question
- **Case**: `run#anEmptyAnswerOverWorkIsRetriedBeforeTheStop`