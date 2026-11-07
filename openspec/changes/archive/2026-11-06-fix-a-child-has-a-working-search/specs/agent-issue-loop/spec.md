## ADDED Requirements

### Requirement: A child is started with a tree search that runs

A process the run starts SHALL be started with a search call it can make: the directory this project's agent keeps
its tools in SHALL be on the child's `PATH`, the project's tree-search wrapper SHALL be able to scope its walk to one
document, and every phase prompt SHALL name that wrapper rather than a shell command the child's own policy refuses.

#### Scenario: A child inherits the agent's tool directory

- **WHEN** the adapter the run's configuration names starts a child, and this project's agent keeps its tools in its
  own directory
- **THEN** that directory is the first entry of the child's `PATH`, the `PATH` the child inherited stands behind it,
  and a directory that does not exist changes nothing
- **Case**: `adapter#theChildIsStartedWithItsToolDirectoryOnPath`

#### Scenario: A document's section list is one scoped call

- **WHEN** a child asks the wrapper for the section list of one document
- **THEN** one call answers it — the matches with their line numbers, no other document searched, and the answer's
  first line naming what was walked — and a path the root does not hold is a refusal and not an empty answer
- **Case**: `look#aPathScopesTheWalkToIt`

#### Scenario: A phase prompt names a call its own policy allows

- **WHEN** a phase prompt tells a child how to read a document's section list
- **THEN** it names the wrapper's scoped call and not the shell `grep` the policy that every child is started with
  refuses
- **Case**: `prompt#thePhasePromptsCarryTheirOwnRoute`
