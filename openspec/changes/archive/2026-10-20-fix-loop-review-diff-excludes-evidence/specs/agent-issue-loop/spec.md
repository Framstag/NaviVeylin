# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A review round reads the change, not the tree it sits in

The run SHALL hand a review round the change — source, the specification delta, the design, the tasks and the
change's own prose — and SHALL NOT hand it the generated output an item archives beside that change. The
evidence SHALL reach the reviewer by path, through the item's evidence directory, which the round's prompt
names. Which directories hold generated output SHALL be a recorded value of the project's configuration and
not a rule of the phase.

#### Scenario: The round's diff carries the change and not the generated evidence

- **WHEN** the run writes the diff a review round reads, and the item's commit carries both a source change and
  a generated artefact under a configured evidence tree
- **THEN** the diff carries the source file and its hunk, and carries neither the artefact nor any line of its
  content
- **AND** a file outside the excluded trees is still in the diff whatever its extension, so a `.txt` the
  reviewer has to read survives an artefact whose name looks like data
- **Case**: `run#reviewDiffExcludesTheEvidenceArchive`

#### Scenario: The exclusion is a recorded value, and an empty one is no exclusion

- **WHEN** a project configures no exclusion pathspec, or configures one
- **THEN** the round's diff is the whole change in the first case and the change without the excluded trees in
  the second, from the same function, with no path of the evidence convention written into the phase
- **Case**: `run#reviewDiffExcludesTheEvidenceArchive`

#### Scenario: The reviewer is told where the evidence went

- **WHEN** the reviewer's prompt is rendered
- **THEN** it names the evidence directory the change cites as the route to the evidence, and says that the
  diff excludes the generated trees, so the absence in the diff is not read as a missing artefact
- **Case**: `run#reviewDiffExcludesTheEvidenceArchive`
