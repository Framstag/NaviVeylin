## ADDED Requirements

### Requirement: The `too-wide` verdict is a counted surface, not a judgement

The worker's eligibility gate SHALL decide whether an item's change is wider than one child by a count the child can
take from the surface pass it has already run, and not by an estimate: the census of the rule the change touches
(`tools/look.sh '<the rule>'`) names the sites and the files the delta must change, one site is ten turns of a child
— the read it owes, the edit, its own case and the correction a red case asks for — and the change fits only while
that count is no more than a tenth of the turns the worker role is given (`LOOP_CHILD_TURNS_MAX_WORKER` in
`tools/loop/loop.conf`). A surface past that bound SHALL be refused `too-wide`, and the refusal SHALL carry the count
and the split it proposes, so the item is refiled as children under an epic rather than re-attempted whole by the
next run.

#### Scenario: A surface past the child's turns is refused with the split it proposes

- **WHEN** the census the worker took names more sites than a tenth of the turns the worker role is given
- **THEN** the item is skipped `too-wide`, and the skip's record carries `surface: <n> sites over <m> files against
  <t> turns` beside the command that printed the count
- **AND** the record carries the split the child proposes — each child named, the sites of the census it takes, and
  the epic the children are filed under — so a later run refiles the item as those children instead of paying for
  the same refusal again
- **Case**: `prompt#theTooWideRowCountsTheSurfaceBeforeTheVerdict`
