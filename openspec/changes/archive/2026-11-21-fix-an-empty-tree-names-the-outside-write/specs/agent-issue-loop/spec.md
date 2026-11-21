# agent-issue-loop delta

## ADDED Requirements

### Requirement: An empty tree names the difference the run recorded

The reason and the step the run writes for a tree still at the run's base SHALL name the difference the run recorded
in the project's own tree while that child ran, when the item's record carries one, and SHALL say that if that
difference is the item's work it is in the caller's tree and not on the item's branch. The clause SHALL qualify the
arm's own sentence and SHALL place the difference on no child. An item whose record carries no reading SHALL get none.

#### Scenario: An empty tree with a recorded write names the paths

- **WHEN** a child reports success, leaves the item's tree at the run's base, and the run's outside-write reading
  recorded paths changed in the project's own tree while that child ran
- **THEN** the item is still blocked `uncommitted` on the sentence the arm writes, the reason adds that if the
  difference is the item's work it is in the caller's tree and not on the item's branch, and it names the paths the
  reading recorded — while the verdict and the placement of the difference stay what the requirement for an
  unattributable difference makes them, because this clause is a sentence and not evidence
- **Case**: `run#anEmptyTreeWithAnOutsideWriteNamesThePaths`

#### Scenario: A record with no reading keeps the sentence

- **WHEN** the same tree at the base is read for a child that wrote nothing into the project's own tree
- **THEN** the reason is the arm's own sentence, with no clause about the caller's tree
- **Case**: `run#childClaimWithoutCommitIsBlocked`
