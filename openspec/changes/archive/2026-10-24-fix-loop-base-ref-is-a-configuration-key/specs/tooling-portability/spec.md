## MODIFIED Requirements

### Requirement: The synthetic project is a fixture, not a second project

The project used to prove portability SHALL live in the repository's fixtures, SHALL be built from a
throwaway working tree at test time, and SHALL be documented as modelling neither a second tracker, nor
another shell, nor another forge client.

#### Scenario: The fixture is versioned, not local

- **WHEN** the fixture is inspected
- **THEN** it is part of the repository's tracked content, so a fresh clone can run the proof

#### Scenario: The fixture's copy of the engines cannot drift

- **WHEN** the fixture runs the toolchain
- **THEN** it uses the engines in the repository rather than a copy of its own

#### Scenario: The fixture declares every budget the engine reads

- **WHEN** the loop's engine is asked for the budget defaults it resolves an omitted flag to (every
  `LOOP_*_DEFAULT` key it reads through `loop_conf_value`)
- **THEN** the fixture's `tools/loop/loop.conf` declares each one, so the foreign project is a complete,
  runnable configuration and a budget the engine gains fails this case rather than the porting recipe
- **Case**: `foreign#fixtureDeclaresEveryBudgetDefault`

#### Scenario: The fixture names its own base branch

- **WHEN** the foreign project's own configuration and a run started in its tree are read
- **THEN** the branch the fixture records is the branch its tree is created on and the commit the run records as
  the base, and it is not the engine's default
- **Case**: `foreign#baseBranchIsTheFixturesConfiguration`

#### Scenario: What the fixture does not model is written down

- **WHEN** a reader of the fixture asks whether their project is covered
- **THEN** the fixture states the assumptions it does not exercise
