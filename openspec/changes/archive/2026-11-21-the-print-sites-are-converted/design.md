# Design — convert the sites, then let the report disagree out loud

## Context

See `proposal.md` — Why. Constraints:

- **`emit` already exists** (`run-event-stream-is-typed`), with the vocabulary in `tools/loop/events.tsv`, the bound
  and the rotation. This change adds no mechanism: it uses one.
- **The human line is the liveness signal** an operator reads during a run (`guidelines/BuildGate.md` §2b), so the
  line must keep its text and its stream; only its composition moves into `emit`'s render.
- **The report's source is shipped**: three requirements name the state file, and the state holds the arithmetic
  their figures depend on. A switch is a different change with its own red case.
- **The engine is five files by now** (`run-sh-is-split-along-the-stream`), so each conversion task edits one owner's
  file and the selection stays narrow.

## Goals / Non-Goals

Goals: no site prints a fact it does not record, and a difference between the record and the report is visible.
Non-goals: the report reading the record instead of the state; the state becoming a projection of the record;
converting lines that print something which is not a run fact (a fixture's own output, a child's answer).

## Decisions

### D1 — Convert by phase, one owner's file per task

Alternatives:

- **Convert alphabetically by event kind** (rejected): a kind is emitted from several phases, so one task would edit
  four files and the review would have no theme.
- **Convert the whole engine in one task** (rejected): 131 sites in one diff is a review nobody can do, and the
  suite's failure would name a phase boundary at best.

Chosen: one task per phase group (header, queue and stop arms, item, child wait, gate and review, tail), each ending
green on that owner's cases. The review then reads one theme per diff.

### D2 — A print-only site is *replaced*, never accompanied

Alternatives:

- **Emit and keep the `printf`** (rejected): two writes of one fact with nothing comparing them — the defect this
  change is about, reproduced with more ceremony.
- **Emit only, and let the report render what a person reads** (rejected): an operator watching a live run reads
  stderr, not a file; the heartbeat exists for exactly that reason.

Chosen: `emit <kind> field=…` writes the record and renders the vocabulary's format into the same stream the line went
to. One call site, one fact, one source.

### D3 — The cross-check prints beside the figures and never replaces them

Alternatives:

- **Switch the counts to the record** (rejected): three shipped requirements name the state as the source, and a
  switch would turn an incomplete record into a wrong report rather than a named difference.
- **No cross-check** (rejected): then nothing proves the record complete, which is the property this whole pair of
  changes rests on.
- **Fail the report on a difference** (rejected): a difference is evidence about the vocabulary, not a reason to
  refuse a person their report of a finished run.

Chosen: `--from-events` prints the recomputed counts beside the state's and adds one difference line when they
differ, plus an "absent" line when the record is missing. Exit 0 in every case.

### D4 — A difference names the item, not only the totals

Alternatives:

- **Totals only** (rejected): "4 closed vs 3" sends a reader through an item table to find which one, and the
  difference is nearly always one item whose events a rotation removed.
- **The whole item diff under `--json`** (rejected): the human line is the product; the list belongs in the JSON
  document a caller parses.

Chosen: the difference line names both numbers and the first item the record lacks; `--json` carries the whole list.

## Risks / Trade-offs

- **A converted site changes its text by accident** → the conversion is a line-for-line replacement against the
  vocabulary's format, and a case compares the emitted kinds against the printed lines for one run.
- **The `text` field escapes badly** (a stop reason with a quote or a newline) → `emit`'s escaping is the predecessor
  change's case; this change adds a case for a reason carrying both.
- **The cross-check is noisy** (many difference lines on a healthy run) → measured over two runs after landing; the
  claim is zero, and a number above it is a finding about the vocabulary rather than a reason to weaken the check.

## Migration Plan

1. The header and the queue/stop arms (the cheapest sites, and the ones a run of `--items 0 --minutes 0` exercises).
2. The item and the child wait, then the gate, the review and the tail.
3. `report.sh --from-events` last, when the record carries every kind the counts read.

Rollback: revert the commit. Each task's sites revert with it.

## Open decisions

none
