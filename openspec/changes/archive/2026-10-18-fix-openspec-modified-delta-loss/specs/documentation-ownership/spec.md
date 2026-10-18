# Spec Delta

## ADDED Requirements

### Requirement: A modified requirement carries the scenarios its spec still has

A change's MODIFIED block SHALL carry the requirement it modifies whole — the description and every scenario
the shipped spec still has — because that block replaces the requirement when the change archives. A block
that omits a shipped scenario SHALL be reported as an error naming the omitted scenario, and the change SHALL
stay unarchivable until the block is completed.

#### Scenario: The change set is validated by command

- **WHEN** `openspec validate --changes` runs over the open change set and a change's MODIFIED block omits a
  scenario its shipped spec still has
- **THEN** the command exits non-zero and its error names the change, the requirement and the omitted scenario
- **AND** the same command exits 0 once the omitted scenarios are restored into the block

#### Scenario: A completed block keeps the requirement whole

- **WHEN** `openspec validate <change> --type change` runs after a block has been completed from its shipped
  requirement
- **THEN** the command reports the change valid, and comparing the block against the shipped requirement names
  no shipped text that the block dropped
