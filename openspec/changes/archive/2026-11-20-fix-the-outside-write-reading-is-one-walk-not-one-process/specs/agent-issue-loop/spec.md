# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: The reading that places a write has its own cadence

A run SHALL read the project's own tree while a child runs at least once per whole second of that child's
life until the first difference is recorded, whatever `LOOP_PROGRESS_SECONDS` is, because that reading is the only
evidence that can place a transient write and a reporting interval is not a resolution for evidence. A case that
pins a placed write SHALL keep its write standing for a wide multiple of that cadence, so the pin does not depend
on the phase between the two.

The reader that names the processes standing in a tree SHALL answer with one walk of `/proc` rather than a call per
process on the host, because its answer is only worth reading while the processes it names are still alive, and a
walk that costs seconds answers about a moment that has passed. It SHALL match a directory by its literal path, so a
tree whose name carries one of the pattern metacharacters `*`, `?`, `[`, `]` or `\` is still read and not silently
reported as empty.

The reading that places a write SHALL state what it cost, on one line of its own beside the answer: the processes it
walked and the milliseconds each of its two halves took. A transcript that reports that nothing stood in the tree
SHALL then say how far from the difference its own answer arrived, so a reading that outlived its writer is told
apart from an arm that finds no process.

#### Scenario: A transient write is placed whatever the reporting interval is

- **WHEN** a child writes the project's own tree, leaves a process of its own standing beside the write in it,
  and takes the write back before the child ends, while the configured reporting interval is far longer than the
  write's life
- **THEN** the run reads the tree inside the writer's life, marks the process that left the worktree as the one
  that made the difference, and records the item as blocked — without the reporting interval having been lowered
  for it, and with the write standing for a wide multiple of the reading cadence so the outcome does not depend
  on the phase between the reading and the write
- **Case**: `run#aWriterTheRunCanPlaceBlocksTheItem`

#### Scenario: A reading states the processes it walked and what each half cost

- **WHEN** the heartbeat reads the project's own tree while a child runs and the difference is recorded
- **THEN** the transcript carries one `LOOP-OUTSIDE-WRITE-COST:` line naming the number of processes the host
  carried and the milliseconds each half of the reading took, beside the `LOOP-OUTSIDE-WRITE-ALIVE:` line it
  qualifies, so an empty enumeration is read as an answer taken at a stated moment rather than as a walk that found
  nothing
- **Case**: `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`

#### Scenario: A directory whose name carries a pattern metacharacter is still read

- **WHEN** the reader is asked for the processes standing in a directory whose path contains `*`, `?`, `[`, `]` or
  `\`, and one process stands there
- **THEN** it names that process, because the directory is matched as a literal path and not as the pattern the walk
  spells it with
- **Case**: `clean#aTreeWhoseNameIsAPatternIsStillRead`
