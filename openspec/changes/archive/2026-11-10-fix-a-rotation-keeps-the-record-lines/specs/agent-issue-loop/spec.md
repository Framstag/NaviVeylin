# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: A rotation keeps the record lines the spend is read from

A rotation of a child's event stream SHALL remove its streamed deltas and keep every record line: `message_update` and `tool_execution_update` go, `message_update` only where the stream holds a `turn_end` and every one of them carries a `message.usage`, and the oldest-half cut runs only on a stream still over `LOOP_CHILD_EVENTS_MAX_BYTES` afterwards. Its record keeps its shape, with a cumulative `dropped_turn_ends` and the `dropped_lines`/`dropped_bytes` it dropped.

#### Scenario: A rotated stream keeps every record the spend is read from

- **WHEN** a child's stream outgrows `LOOP_CHILD_EVENTS_MAX_BYTES` and every `turn_end` of it carries a `message.usage` —
  the archived shape: 747 of 747 turns over six streams, 746 byte-identical to the `message_update` before them
- **THEN** the rotation drops the `message_update` and `tool_execution_update` lines and keeps every record line — every
  `turn_end`, `tool_execution_end`, `session` and `message_end` in its own order — so no turn the child took is lost,
  and the record states the lines and bytes it removed: measured on `items/833/events.jsonl` at a 4 MiB bound,
  8 593 950 B → 3 590 953 B with all 90 `turn_end` records kept, where the line-position cut kept 57
- **Case**: `adapter#aRotatedStreamKeepsEveryTurnEnd`

#### Scenario: A turn whose update a rotation took still records what it spent

- **WHEN** an item's stream holds no `message_update` because a rotation dropped them, and its `turn_end` records carry
  `message.usage`
- **THEN** the item's `metrics.turns`, `metrics.tokens` and `metrics.context` are the figures the undelta'd stream
  records — one turn's usage is its last update's, or its own record's when no update survives it, whether or not the
  stream rotated — so a rotation changes no measurement of spend
- **Case**: `state#theSpendSurvivesTheLossOfItsDeltaLines`

#### Scenario: An update-only stream keeps the usage records it has

- **WHEN** a stream outgrows `LOOP_CHILD_EVENTS_MAX_BYTES` and holds no `turn_end` at all, so no record of it carries a
  usage for a turn to be read from
- **THEN** the rotation drops the `tool_execution_update` lines and keeps every `message_update` — the only usage record
  the stream has — instead of taking them on a precondition no `turn_end` can meet
- **Case**: `adapter#anUpdateOnlyStreamKeepsItsUpdates`

#### Scenario: A stream over its bound after the delta pass is cut, and its turns counted

- **WHEN** a stream's record lines alone outgrow the bound, so the delta pass leaves it over
- **THEN** the oldest half of what remains is cut as before, the rotation record's `dropped_turn_ends` names the turns
  that went cumulatively, and one record stands for every rotation the stream went through
- **Case**: `adapter#aStreamStillOverItsBoundAfterTheDeltaPassIsCutAndRecorded`

## MODIFIED Requirements

### Requirement: An item's kept event stream is bounded

The adapter that keeps a child's event stream SHALL bound the file it appends to at `LOOP_CHILD_EVENTS_MAX_BYTES`. When
the file outgrows its bound it SHALL rotate it in two passes: the streamed deltas SHALL be dropped, every record line
SHALL be kept, and only a stream still over its bound SHALL have its oldest half cut. The newest event SHALL survive.
`0`, or a project that records no key, SHALL keep every line.

#### Scenario: A stream past its bound is rotated and the rotation is recorded

- **WHEN** a child writes more events than `LOOP_CHILD_EVENTS_MAX_BYTES` allows
- **THEN** the file stops growing, its first line is a `stream_rotation` record naming the lines, bytes and
  `turn_end` events that were dropped, the newest event is still the last line, and the dropped plus the remaining
  turns are every turn the child took
- **Case**: `adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded`

#### Scenario: A stream under its bound is kept whole

- **WHEN** a child writes fewer events than the bound allows
- **THEN** every line is kept and no rotation record is written
- **Case**: `adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded`
