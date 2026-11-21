# agent-issue-loop delta

## ADDED Requirements

### Requirement: A reason that names one child carries that child's reading

A reason that names one child SHALL carry that child's own reading, and no other's: an earlier child's reading SHALL
NOT stand under it, and an empty reading for the child it names SHALL be no clause at all. A clause this run appends to
a reason that names no child SHALL keep reading the item's readings in order, as the requirement for an empty tree
states.

#### Scenario: A stop that names a child reads that child

- **WHEN** the run's own "could not start" stop is taken over a tree at the base, its sentence names the child that
  ran last, another child of the item wrote into the project's own tree earlier, and the run's reading for the named
  child records paths of its own
- **THEN** the clause appended to the stop carries the reading taken around the child the sentence names and its
  paths, and never the earlier child's reading or its paths
- **Case**: `run#aStopThatNamesAChildReadsThatChildsOwnWrite`

#### Scenario: A named child that wrote nothing gets no clause

- **WHEN** the same stop is taken for a child whose own reading is empty, while an earlier child of the item wrote into
  the project's own tree
- **THEN** the stop is the arm's own sentence, with no clause about the caller's tree and none of the earlier child's
  paths
- **Case**: `run#aStopWhoseChildWroteNothingGetsNoClause`
