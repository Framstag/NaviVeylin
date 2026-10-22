## MODIFIED Requirements

### Requirement: A modified requirement carries the scenarios its spec still has

A change's MODIFIED block SHALL carry the requirement it modifies whole — the description and every scenario
the shipped spec still has — because that block replaces the requirement when the change archives. A block
that omits a shipped scenario SHALL be reported as an error naming the omitted scenario, and the change SHALL
stay unarchivable until the block is completed. Every scenario SHALL keep its shipped title: a renamed
scenario is a removal plus an addition.

#### Scenario: The change set is validated by command

- **WHEN** `openspec validate --changes` runs over the open change set and a change's MODIFIED block omits a
  scenario its shipped spec still has
- **THEN** the command exits non-zero and its error names the change, the requirement and the omitted scenario
- **AND** the same command exits 0 once the omitted scenarios are restored into the block
- **Case**: `probe#theChangeSetIsValidatedByACommand`

#### Scenario: A completed block keeps the requirement whole

- **WHEN** `openspec validate <change> --type change` runs after a block has been completed from its shipped
  requirement
- **THEN** the command reports the change valid, and comparing the block against the shipped requirement names
  no shipped text that the block dropped
- **Case**: `probe#theCompletedBlockKeepsTheRequirementWhole`

#### Scenario: A renamed scenario is an omission plus an addition

- **WHEN** a MODIFIED block carries a shipped scenario under a new title and changes nothing else about it
- **THEN** `openspec validate <change> --strict` exits non-zero and names the shipped scenario's title as
  omitted, because a title is how the validator pairs the block's scenarios with the shipped ones
- **Case**: `probe#aRenamedScenarioIsReportedAsAnOmission`

## ADDED Requirements

### Requirement: The validator's own rules are read before the first write

The entry document's OpenSpec workflow section SHALL state the two artifact rules below and name the command
that fails on them: a `MODIFIED` requirement repeats every shipped scenario under their own titles, and a
requirement's text stays within the 500 characters the validator reports. A plain `openspec validate` SHALL
report both as warnings and exit 0, where `openspec validate <change> --strict` fails on them. Both SHALL be
read before the artifact is written, not after it.

#### Scenario: The section states the scenario rule

- **WHEN** an author opens the entry document before writing a change's delta
- **THEN** its OpenSpec workflow section states that a `MODIFIED` requirement repeats every scenario its spec
  still has, verbatim and under their own titles, so that the omission the validator reports is read first
- **Case**: `probe#theWorkflowSectionStatesTheScenarioRule`

#### Scenario: The section states the text bound

- **WHEN** an author opens the entry document before writing a requirement's text
- **THEN** its OpenSpec workflow section states the bound the validator reports, 500 characters of requirement
  body rather than of its scenarios, and says that an example belongs in a scenario
- **Case**: `probe#theWorkflowSectionStatesTheTextBound`

#### Scenario: The strict command is named with the rules

- **WHEN** an author reads the two validator rules in the entry document
- **THEN** the same place names `openspec validate <change> --strict` as the command that fails on them, saying
  that a plain `openspec validate` only warns, so the check is run before the artifact is finished rather than
  at the archive, when the rule is met for the first time
- **Case**: `probe#theStrictCommandIsNamedBesideTheRules`

#### Scenario: An over-long requirement is a warning until the strict command

- **WHEN** a requirement's text exceeds the bound the entry document states
- **THEN** a plain `openspec validate` reports that requirement's text as a warning and exits 0, while
  `openspec validate <change> --strict` exits non-zero, so the stated number and the number the command
  enforces are the same one
- **Case**: `probe#anOverLongRequirementIsReported`
