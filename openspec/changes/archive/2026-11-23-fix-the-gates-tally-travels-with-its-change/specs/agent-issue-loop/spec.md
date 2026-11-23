## ADDED Requirements

### Requirement: The gate's own record travels with the change it measured

The run's landing step SHALL carry the gate's own record into the archived change it commits, because the directory
the gate writes that record to (`LOOP_GATE_TALLY_DIR`) is the project's build output and not the change. When the
gate's verdict is green and the archived change carries no green verification record of its own, `end-item` SHALL
copy the gate's tally (`*.xml`) and the gate's own log beside them into that change's `evidence/`, and SHALL NOT
replace a file the worker already wrote there, SHALL NOT write outside that directory, and SHALL copy nothing when
the gate's verdict is not green, so a change blocked on a red gate keeps the red record and gains no green one. The
record SHALL be judged green by one predicate, the one the branch reader uses, so a change the run wrote itself
reads back as the change it is.

#### Scenario: A change whose evidence carries the gate's own tally reads as verified

- **WHEN** an item's gate passes with its tally under `LOOP_GATE_TALLY_DIR` and its own log beside it, and the change
  the worker archived holds a red record and no green one
- **THEN** `end-item` copies the tally and the log into the change's `evidence/` before it commits, `branch-state`
  reads that branch's change `adoptable` and names the tally it read, a change that already answers for its own gate
  is left as it stands rather than given a second copy, and a gate whose verdict is not green leaves the change's
  evidence untouched, so the branch it blocked still reads `archived-not-green`
- **Case**: `worktree#theGatesTallyTravelsWithTheChangeItMeasured`
