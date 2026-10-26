## ADDED Requirements

### Requirement: The reading that places a write has its own cadence

A run SHALL read the project's own tree while a child runs at least once per whole second of that child's
life, whatever `LOOP_PROGRESS_SECONDS` is, because that reading is the only evidence that can place a transient
write and a reporting interval is not a resolution for evidence. A case that pins a placed write SHALL keep its
write standing for a wide multiple of that cadence, so the pin does not depend on the phase between the two.

#### Scenario: A transient write is placed whatever the reporting interval is

- **WHEN** a child writes the project's own tree, leaves a process of its own standing beside the write in it,
  and takes the write back before the child ends, while the configured reporting interval is far longer than the
  write's life
- **THEN** the run reads the tree inside the writer's life, marks the process that left the worktree as the one
  that made the difference, and records the item as blocked — without the reporting interval having been lowered
  for it, and with the write standing for a wide multiple of the reading cadence so the outcome does not depend
  on the phase between the reading and the write
- **Case**: `run#aWriterTheRunCanPlaceBlocksTheItem`
