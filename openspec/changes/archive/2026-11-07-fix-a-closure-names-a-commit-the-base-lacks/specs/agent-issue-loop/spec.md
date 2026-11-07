## ADDED Requirements

### Requirement: The loop's closure is refused when the base does not carry the commit

The loop's `close` verdict SHALL refuse a closure whose commit the base branch does not carry, and SHALL write nothing
when it does, so that `status/fixed-by` and "the fix is in the base branch" are one
claim on the loop's own path rather than two claims a refused call can leave disagreeing.

#### Scenario: A closure on a commit the base does not carry writes nothing

- **WHEN** the loop's `close` verdict is handed the commit the landing made (`--landed`) and the base branch the
  profile names does not carry that commit
- **THEN** the call exits 3 with the reason `not-on-base`, naming the commit and the base branch, and writes nothing:
  no `status/fixed-by`, no comment, no close, so the item is not left settled on a fix the tree does not have
- **AND** the same verdict handed a commit the base carries records the settled state and closes the item
- **Case**: `labels#closeRefusesACommitTheBaseDoesNotCarry`
