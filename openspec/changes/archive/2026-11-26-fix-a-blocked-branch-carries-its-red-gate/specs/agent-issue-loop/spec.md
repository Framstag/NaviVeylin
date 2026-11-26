# Delta — a branch whose run ended on a red gate is not adopted

## ADDED Requirements

### Requirement: A branch whose run ended on a red gate carries that verdict and is not adopted

A run whose gate did not pass SHALL carry the gate's verdict onto the branch, in the archived change's `evidence/`
directory and in the form that directory already gives a verdict, and `branch-state` SHALL answer `gate-red` for such
a branch and name the record it read — before it answers `archived-not-green`, because a red gate is the reason the
verification is incomplete. `adopt-item` SHALL refuse that branch with the red gate as its reason rather than merge
it, so the item is its owner's and is not closed on a gate its own run refused. A gate that passed SHALL be carried
exactly as it is carried today.

#### Scenario: A blocked branch carries its red gate and is refused

- **WHEN** an item's branch carries one archived change, one case's own log, and a gate that did not pass
  (`LOOP-GATE:` naming a non-zero `failures=`)
- **THEN** the verdict is carried onto the branch as `evidence/gate-red.log`, `branch-state` answers `gate-red` and
  names that record, `adopt-item` exits `5` with `ended on a red gate`, and the change is not merged onto the base
- **AND** the case is `worktree#aBranchWhoseRunEndedBlockedIsNotAdoptable`

#### Scenario: A gate that passed is carried as it was

- **WHEN** the gate passed and the archived change carries only its red-on-HEAD record
- **THEN** the tally and the gate's log travel into `evidence/` as today, the state is `adoptable`, and a change that
  already answers for its own gate is left untouched
- **AND** the case is `worktree#theGatesTallyTravelsWithTheChangeItMeasured`
