## ADDED Requirements

### Requirement: A phase that exited 0 and wrote no answer is asked once more

A phase whose report carries none of the lines the item's arms read, whose child exited 0, and whose tree carries work
SHALL be relaunched once from its checkpoint prompt before the item is blocked, and the item SHALL record that it was
retried. A child the run itself ended at a bound SHALL NOT be relaunched, and neither SHALL one that left the tree at the
run's base with a clean status — that silence spent no phase and keeps the item-level retry it has.

#### Scenario: A silent change child is asked again

- **WHEN** `change_answered` answers false for a change report, the child exited 0, and the tree carries work
- **THEN** exactly one relaunch happens, and the item records the retry beside its phase's bounds
- **Case**: `run#aPhaseThatWroteNothingIsAskedOnceMore`

#### Scenario: A child the run ended is not asked again

- **WHEN** the report carries `LOOP-TIMEOUT` or the child did not exit 0
- **THEN** no relaunch happens, and the item blocks with the attempt count in its reason
- **Case**: `run#aChildTheRunEndedIsNotAskedAgain`

#### Scenario: A child that read nothing is retried at the item

- **WHEN** the report carries no line the run reads, the child exited 0, and the tree is at the run's base with a clean
  status
- **THEN** no relaunch happens inside the item, and the transient class the run already spends re-runs the item from the
  queue
- **Case**: `run#aChildThatNeverStartedIsRetriedBeforeTheRunStops`
