## ADDED Requirements

### Requirement: A consumed source is read from the record that outlives the run

The clean command SHALL name the source directory a landed item consumed from the records that outlive the run — the
item's own `change-copy.json` and its result in the run's event record — not from the run state's `changeCopies`
alone, which the next run's `init` replaces. A source two records name SHALL be reported once, and a source whose
`tasks.md` carries a checked task SHALL be kept and named with that reason: the archived twin of a change worked in
slices is the earlier slice's.

#### Scenario: The source a past run consumed is still found, once

- **WHEN** an item's change was copied, the item landed, and the next run's `init` replaced the state that named the
  copy, so that only the item's own `change-copy.json` and the run's `item-result` line name it
- **THEN** the clean command names that source with its size and the item that consumed it, and names it once rather
  than once per record that holds it
- **Case**: `clean#aConsumedSourceIsReadDurably`

#### Scenario: A change worked in slices keeps its source

- **WHEN** the source of a landed item's change carries a checked task, so the archived twin is an earlier slice's
  and a later one is still prepared from it
- **THEN** the clean command keeps the source and says which reading kept it, and removes nothing of it
- **Case**: `clean#aChangeWorkedInSlicesIsKept`
