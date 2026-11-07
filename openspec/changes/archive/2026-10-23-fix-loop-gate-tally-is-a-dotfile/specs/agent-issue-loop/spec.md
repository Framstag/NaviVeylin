# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: A tally's name is a file a reader finds

The gate SHALL name each suite's tally so that no reader misses it: the name SHALL NOT begin with a dot, and the
`./` of a command run from the project root SHALL be dropped before the name is built. The rest of the name SHALL
stay the command that wrote it, so a reader can still match a tally to its suite.

#### Scenario: A compiled step's tally is not hidden

- **WHEN** the profile's compiled command is written with the `./` a command run from the project root needs, a
  compiled path owes that command, and the gate has written its tally
- **THEN** a plain `ls` of the configured tally directory prints the tally, and a `*.xml` glob over it matches the
  file named after the command without its `./` prefix
- **Case**: `gate#aCompiledStepsTallyIsNotHidden`
