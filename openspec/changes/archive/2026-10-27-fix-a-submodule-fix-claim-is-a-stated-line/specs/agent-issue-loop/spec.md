## ADDED Requirements

### Requirement: The pre-screen refuses only a fix stated to land in the submodule

The pre-screen SHALL refuse as a decision an item whose fix has to be committed inside the submodule — work
neither the run nor `worktree.sh end-item` may deliver — whenever the profile records a `LOOP_SUBMODULE_PATH`. The
signal SHALL be the author's stated claim: a line of its own beginning `The fix lands in the submodule`. The
submodule's path and its directory name SHALL NOT decide the verdict, because a body names them as evidence.

#### Scenario: A body that states the fix lands there is a person's patch

- **WHEN** an item's title or body carries a line beginning `The fix lands in the submodule`
- **THEN** the verdict is `needs-decision`, its reason names the configured submodule path and the route that remains
  (a person lands the patch there and the gitlink bump that follows), and the item does not reach the queue
- **Case**: `prescreen#aFixInTheSubmoduleIsAPersonsPatch`

#### Scenario: A body quoting the path or the marker is evidence, not the claim

- **WHEN** an item's body names the submodule's path or its directory name as the file a dirty tree was seen on, or
  quotes `The fix lands in the submodule` inline or in backticks
- **THEN** the verdict stays `eligible` and the item reaches the queue — the claim is a line of its own, so a
  quotation of the phrase and a path quoted as evidence are neither of them the claim
- **Case**: `prescreen#aQuotedSubmodulePathIsEvidenceNotAClaim`
