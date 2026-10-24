# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A path is compiled by the tree it sits in or a build input's convention

The gate SHALL decide that a path is compiled from the tree it sits in — a module's `src/` tree or its `lint.xml` —
or from a name that is the convention for a build input (a `.gradle.kts` or `.gradle` script, a `.toml`, `CMakeLists.txt`), which classify wherever they sit; an extension that names anything else — `*.xml`, `*.kt`, `*.java`, `*.c`, `*.h` — SHALL NOT be enough. Every pattern SHALL be compared as a pattern, not expanded against the directory it runs in.

#### Scenario: An archived artefact owes no compiled command

- **WHEN** a delta's only paths are an archived change's evidence tally, and then a gate fixture that mimics a test
  report, and the gate is asked what each delta owes without running anything
- **THEN** the plan names no compiled command for either, and names the checks each delta does owe — the archived
  tally's delta owes `check-classification` — so a delta that touches no compiled file is not made to run the build
- **Case**: `gate#anArchivedArtefactOwesNoBuild`

#### Scenario: A module's source tree and its lint configuration still owe the build

- **WHEN** a delta touches a resource in a module's `src/` tree, and then that module's `lint.xml`, in a tree that
  holds the directory the pattern names
- **THEN** the plan names the compiled command for each, because a pattern is matched as a pattern and a module's
  sources and lint configuration are what the build reads
- **Case**: `gate#aModuleResourceOwesTheBuild`
