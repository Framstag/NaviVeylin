# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A path is compiled because of the tree it sits in, not its name

The gate SHALL decide that a path is compiled from the tree it sits in — a module's `src/` tree, its `lint.xml`,
or a build script — and never from a file name alone, so an archived artefact, a fixture that mimics build output
or a tool beside the build owes no compiled command. It SHALL compare the profile's patterns as patterns, never
expanded against the directory the gate runs in.

#### Scenario: An archived artefact owes no compiled command

- **WHEN** a delta's only paths are an archived change's evidence tally, and then a gate fixture that mimics a test
  report, and the gate is asked what each delta owes without running anything
- **THEN** the plan names no compiled command for either, and still names the checks the delta does owe, so a delta
  that touches no compiled file is not made to run the build
- **Case**: `gate#anArchivedArtefactOwesNoBuild`

#### Scenario: A module's source tree and its lint configuration still owe the build

- **WHEN** a delta touches a resource in a module's `src/` tree, and then that module's `lint.xml`, in a tree that
  holds the directory the pattern names
- **THEN** the plan names the compiled command for each, because a pattern is matched as a pattern and a module's
  sources and lint configuration are what the build reads
- **Case**: `gate#aModuleResourceOwesTheBuild`
