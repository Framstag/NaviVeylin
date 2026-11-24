## ADDED Requirements

### Requirement: A change that edits a baselined file migrates it

A change that edits a file the baseline names SHALL either migrate that file in the same change or state in its
proposal why it does not, and the baseline entry keeps its owner until it does. The rule is judged by review: the
diff's baselined files are read against the proposal's statement, because a working-tree run has no base ref to
diff against and the diff against the base is what a reviewer already reads.

#### Scenario: A change that edits a baselined file migrates it

- **WHEN** a change's diff touches a file that `architecture/baseline.tsv` names
- **THEN** the change moves that file to the package the entry's rule requires, deletes the entry, and runs the
  forced suite
- **Case**: none — a review convention: no automated case can read the base ref a working-tree run lacks

#### Scenario: A change that leaves a baselined file in place names why

- **WHEN** a change's diff touches a baselined file and the change does not move it
- **THEN** the proposal states the reason and names the change that still owns the entry, so the debt keeps a
  reader
- **Case**: none — a review convention: the statement is the proposal's own text, read by the reviewer
