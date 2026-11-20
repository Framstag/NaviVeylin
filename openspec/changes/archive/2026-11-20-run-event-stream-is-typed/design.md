# Design — one record the run writes, one vocabulary that renders it

## Context

See `proposal.md` — Why. The constraints this design works inside:

- **`run.sh` is the run's only writer** and its only git actor (its own header, D2/D3/D7/D12). The record is
  written by that process, and by nothing a child started.
- **The state file stays authoritative.** `state.sh next` owns the deadline, the streak and the budget
  arithmetic (invariant 3), and three shipped requirements name the state file as the report's source. This
  change adds a record; it does not move the arithmetic.
- **The suite selects cases by declared files** (`# files:`, `loop-selftest.sh --files`), because a `tools/loop/**`
  delta cannot afford all 436 cases (723 s for 190 of them, measured 2026-10-23).
- **`tools/loop/**` may name no project value** — the portability check reads every engine file, `*.java`
  included (`tools/check-portability.sh:222`). The vocabulary is engine, so it carries kinds and fields and no
  command, no path outside the project and no label value.
- **131 print-only sites** are the conversion surface — `the-print-sites-are-converted`'s, one site of which (the
  header) this change converts to prove the writer — and the human text they print today is the liveness signal
  an operator reads during a run (`guidelines/BuildGate.md` §2b).

## Goals / Non-Goals

Goals: one writer path for a run-visible fact; a vocabulary a new observation is one row of; a machine-readable
record a reader — a case, a person, and the report that will follow — can count without parsing prose; a bound that
is named when it fires.

Non-goals: replacing the state file; replacing the report's source; making a child's output typed (the child's
stream is already JSON and is read as it is); a record of anything the run does not print (an unprinted fact is
not a fact this change owes).

## Decisions

### D1 — The record is JSON-lines in its own file beside the state file

Alternatives:

- **An `events` array inside the state file** (rejected): `state.sh` rewrites the state whole — 37 `jq` sites,
  each a full read-modify-write — so a growing array makes every one of them more expensive, and the one
  property that made the state file safe (small, whole, atomically replaced) is spent on a transcript.
- **One file per item, per phase** (rejected): the run-level facts (the run's start, its deadline, its stop
  reason, a pause) have no item, and a reader would glob a directory to answer "what did the run do".
- **A database** (rejected): a dependency in a component that must stay copyable and dependency-free
  (`PORTING.md`, "Prerequisites").

Chosen because a JSON-lines file is append-only, one `printf` per event, readable by `jq -c` streaming, and it
sits beside the state file where the run's other evidence already lives, so both share one lifecycle and one
cleanup path.

### D2 — The vocabulary is a TSV read once, not code and not a schema

Alternatives:

- **A `case` block with every format inline** (rejected): that is today's shape — the format lives at the call
  site, which is why a new observation needs a `printf`, a branch and a comment.
- **A JSON Schema validated by `jq` per event** (rejected): 131 forks of `jq` per run on a hot path, and a schema
  language the engine does not otherwise use. The engine's own precedent is a declared table read once
  (`architecture/structure.tsv`, `tools/loop/events.tsv` follows it).
- **Generated from the call sites' comments** (rejected): a generator that reads prose is the mechanism that
  produced the drift this change is about.

Chosen: three columns — `kind`, required fields (`+`-separated, `name:type`), the human format — loaded once into
three associative arrays in `lib.sh`. A row is the whole cost of a new observation.

### D3 — The human line is rendered from the record, never printed beside it

Alternatives:

- **Keep the `printf` and add an `emit`** (rejected): two writes of one fact, nothing comparing them — the exact
  defect named in the proposal, reproduced with more ceremony.
- **JSON only; the report renders everything a person reads** (rejected): an operator watching a live run (and
  `guidelines/BuildGate.md` §2b's shell recipes) reads stderr, not a file; a run that prints nothing until it ends
  is a run nobody can watch, and the heartbeat exists for that reason.

Chosen: `emit <kind> <field=value>…` writes the record and then renders the vocabulary's format into the same
stream the line went to before. One call site, one fact, one source.

### D4 — An undeclared kind, or a declared kind missing a field, ends the run

Alternatives:

- **Fall back to printing the raw line** (rejected): a silent fallback is how a vocabulary stops being one.
- **Warn and continue** (rejected): the run is unattended; a warning is a line nobody reads, and the record would
  then be incomplete while the run reports success.

Chosen, and it follows the engine's shape for a missing configuration value (`LOOP_AGENT_CMD` empty stops the run
instead of starting nothing, D3/D15): the refusal names the kind and the field, exits 3, and no item is recorded.
The suite is what keeps this from being a surprise at run time — a case greps every `emit` call's kind against
the vocabulary.

### D5 — The report's cross-check is not this change

Alternatives:

- **Switch `report.sh` to the record now** (rejected): three shipped requirements name the state file, and the
  state file holds the arithmetic the report's figures depend on; a switch is a different change with its own
  red case, and it would make an incomplete record a wrong report rather than a named difference.
- **Add the cross-check here** (rejected): the check compares the record against figures the state derives, and
  this change converts one site — a report reading a record that carries one event per run would name a
  difference on every run it read, which is a case for the change that completes the conversion.

Chosen: the cross-check and the conversion it measures are `the-print-sites-are-converted`'s requirement `The
record and the report's figures agree, or the difference is named`. This change adds no reader: it adds the
writer that reading stands on, and what a reader of the record must find — one event per fact, a declared
vocabulary, a bound that names its rotation — is the whole of what is proven here.

### D6 — The record is bounded, and a rotation is an event

Alternatives:

- **Unbounded** (rejected): a file that grows with every heartbeat without bound; a 4-hour run with a 30 s heartbeat
  is a bounded case, an item that loops is not.
- **Bound without recording it** (rejected): a reader then cannot tell "nothing happened" from "the file was
  rotated", which is the failure `pi-child-events.sh` already records a rotation for.

Chosen: `LOOP_RUN_EVENTS_MAX_BYTES` (a recorded value, `loop.conf`), the same rotation shape as the child stream
(`<file>.1` kept, a `record-rotated` event first in the new file), so a reader reads what it holds as a lower
bound and the vocabulary owns the event that says so.

## Risks / Trade-offs

- **`emit` becomes a hot path** → one `printf` and two associative-array reads per call; a case emits 10 000
  events and bounds the wall clock, so a regression is red rather than felt.
- **The vocabulary drifts from the call sites** (a kind added in a branch) → `emit` refuses it at run time, so a
  run that emits a kind no row declares stops rather than writing an untyped line; the both-directions comparison
  of rows against call sites is `driver#everyEmittedKindHasARow`, the conversion change's case.
- **A free-text field breaks the line format** (a stop reason carrying a quote or a newline) → the type column
  marks `text`, the writer escapes, and a case emits a reason carrying both.
- **A reader mistakes the record for the authority** → the run header names the state file as the authority and the
  record as a cross-check, and the report's own lines keep their labels.

## Migration Plan

1. `lib.sh` gains `loop_emit`, the vocabulary loader and the bound; `events.tsv` carries the kinds the writer
   renders, so a new observation is one row.
2. The header is converted: one `run-start` event, and its line rendered from that row, naming the record's path
   and the bound it keeps. The remaining print-only sites stay as they are — their conversion is
   `the-print-sites-are-converted`'s, and this change converts nothing further.

The report's cross-check is not here: it needs a record that carries every kind its counts read, which is the
conversion change's tree. Rollback: revert the commit. A run started from the reverted tree writes no record and
prints the lines it always printed; nothing reads a record that a reverted engine cannot produce.

## Open decisions

none
