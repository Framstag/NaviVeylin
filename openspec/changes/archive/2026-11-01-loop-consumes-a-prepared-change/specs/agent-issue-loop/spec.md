# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A change the item names is handed to its worktree

When an item's body records a change, the run SHALL copy that change's directory from the project's tree into the
item's worktree before the worker starts, whole, and SHALL record the copy. A change the tree does not hold SHALL
refuse the item, never produce an empty directory, because the run cuts the worktree from the base and the change
is gitignored, so nothing else would carry it.

#### Scenario: The named change is in the worktree the worker gets

- **WHEN** an item's body records `<!-- change: add-x -->` and the project's tree holds `openspec/changes/add-x/`
- **THEN** the item's worktree carries that directory with every file it had, before the worker process starts, and
  the run records the source path and the copy's file count
- **Case**: `worktree#theNamedChangeIsCopiedIntoTheItem`

#### Scenario: A change the tree does not hold refuses the item

- **WHEN** an item names a change no directory of that name holds
- **THEN** the item is refused with the path the run looked for, before a branch or a child exists, and no empty
  change directory is created in the worktree
- **Case**: `worktree#aMissingChangeRefusesTheItem`

#### Scenario: A change that is not ready refuses the item

- **WHEN** the profile's readiness check reports any refusal for the named change at the moment the item is selected
- **THEN** the item is left open with the checker's verdict line recorded on it, and no child is started
- **Case**: `prescreen#aChangeThatIsNoLongerReadyIsRefused`

### Requirement: A tier-B item is verified, never planned again

A worker handed a change SHALL verify it against the tree and SHALL NOT write a second change for the item. A fork
the change does not answer SHALL be reported as a decision naming that fork, and a change whose claims no longer
hold SHALL be reported with what moved, so no worker can silently replace the plan a person authorised.

#### Scenario: The worker applies the change it was handed

- **WHEN** the package carries a change and the change still fits the tree
- **THEN** the change's tasks are applied in order and no second change directory is created for the item
- **Case**: `run#aTierBItemWritesNoSecondChange`

#### Scenario: A fork the change does not answer is named

- **WHEN** applying the change requires a decision its `## Open decisions` declaration does not settle
- **THEN** the item is skipped with `needs-decision`, and the question names the fork and the change's line, not a
  re-derived option list
- **Case**: `run#aForkTheChangeDoesNotAnswerIsNamed`

#### Scenario: A change whose premise moved is not re-planned

- **WHEN** a `file:line` the change cites no longer shows what it claimed
- **THEN** the item is skipped with `needs-diagnosis`, naming the path that moved, and the worker writes no
  replacement change
- **Case**: `run#aChangeWhosePremiseMovedIsNotRePlanned`

### Requirement: The package carries the change beside the item's text and its decision

The package a worker receives SHALL carry the change as a delimited data block of its own — its name, its path in
the worktree, the readiness verdict line and the recorded decisions — and SHALL keep that block distinct from the
item's own text and from its decision, so that no field of one can close a block it does not own.

#### Scenario: The change reaches the worker as its own block

- **WHEN** a worker is started for a tier-B item
- **THEN** the package contains exactly one `<<<ITEM_CHANGE` opening and one `ITEM_CHANGE` closing marker, the
  change's name and worktree path inside them, and the block is the same text the evidence-check reads
- **Case**: `prompt#thePackageCarriesTheChange`

#### Scenario: A tier-A item's package carries no change block

- **WHEN** a worker is started for an item that records no change
- **THEN** the package's change block is present and states `change: none`, rather than being absent, because an
  absent block is a half-prepared package
- **Case**: `prompt#aTierAItemSaysSo`

#### Scenario: A comment cannot close a block it does not own

- **WHEN** an item's body or one of its comments contains the text `ITEM_CHANGE` or its opening marker
- **THEN** the rendered package carries exactly one opening and one closing marker for that block, and the item's
  text cannot end it
- **Case**: `prompt#inlineInjectionCannotReachTheInstructionPosition`

### Requirement: The consumed change is cleaned from the project's tree, and the run does not write it there

The source directory of a change an item consumed SHALL be reported by the loop's clean command and removed by its
`--apply`, together with the worktree's recorded build paths, because the run writes nothing in the project's own
tree and a consumed change left behind can be read as work still owed. The item's closure, not the leftover, SHALL
be what prevents a second consume.

#### Scenario: A consumed change is reported before it is removed

- **WHEN** the clean command runs without `--apply` after an item whose change was copied landed
- **THEN** it names the source change directory and its size and removes nothing
- **Case**: `clean#aConsumedChangeIsReported`

#### Scenario: The second run finds nothing to remove

- **WHEN** the clean command runs with `--apply` and is then run again
- **THEN** the first run removes exactly the paths it reported and the second finds nothing of them
- **Case**: `clean#aSecondRunFindsNothing`

#### Scenario: A closed item cannot consume its change twice

- **WHEN** a change's source directory is still present and the item that consumed it carries the settled state
- **THEN** the run does not select that item again, and the leftover is only disk hygiene
- **Case**: `prescreen#aClosedItemIsNotSelected`
