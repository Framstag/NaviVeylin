# Proposal — the run emits one typed record of what it did, and the report reads it

## Why

`run.sh` prints. Measured 2026-11-14 on the committed tree: 230 `printf` calls, of which **131 are lines that
only print** — no state write, no branch, nothing a caller can read back. Each of them is a fact the run knows
and then drops on stderr.

The same facts exist a second time elsewhere. `report.sh` re-derives the run's counts from the state file and
from git (47 `jq` call sites), the item's table is rebuilt from `.items[]`, and the run's spend figures are
re-summed from the child's event stream. So one fact has two homes — a printed line and a re-derived figure —
and nothing checks that they agree. `run.sh`'s own history says the same thing in its comments: 204 mentions of
`issue #`, 128 distinct issue numbers, and 210 guard/refusal sites, added one incident at a time. A new
observation is cheap to print and expensive to read back, so every incident adds a print and a branch, never a
record.

That is the growth this change attacks. Measured in git over four weeks (2026-10-17 → 2026-11-14):
`run.sh` went 410 → 3793 lines, +3380 in 28 days (+120/day), with no plateau. The language is not the cause;
the missing thing is a *record* — a declared, typed statement of what a run did, which the human lines, the
report and the cases all read instead of each re-deriving it.

## What Changes

- **One `emit`**, in `tools/loop/lib.sh`, appends one JSON object per line to the run's own record
  (`$LOOP_RUN_EVENTS`, one file for every run of the project, beside the state file). The record is the run's, written by the run —
  never by a child, and never trusted from a child (D11 is unchanged).
- **A declared vocabulary.** `tools/loop/events.tsv` names each event kind, the fields it must carry, and the
  human line it renders to. `emit` refuses an undeclared kind and a declared kind missing a required field.
  The vocabulary is one file, so a new observation is a row rather than a `printf` plus a comment.
- **The 131 print-only sites are what this change makes cheap to convert, and converts one of them**: the run's
  header. `emit` is the only printer for the kinds it owns — the vocabulary's format column renders the line from
  the event's own values — so one fact has one home and a line and its record cannot disagree. The remaining sites
  are `the-print-sites-are-converted`, which stands on this writer.
- **The record is bounded**, like the child's event stream: `LOOP_RUN_EVENTS_MAX_BYTES`, with a rotation that
  is itself an event, so a reader knows what the file no longer holds and reads it as the lower bound it is.
- **No event carries a coordinate or a secret.** An event names an issue, a phase, a path inside the project and
  a number; identity instead of position (`guidelines/Logging.md` §2, `guidelines/Regulatory.md` §9).
- **Scope**: the loop's engine and its self-test only (`tools/loop/**`). No app code, no native code, no
  dependency, no Gradle change, no persisted user data. Additive: every existing human line keeps its text, and
  a run that writes no record is not refused by it (the vocabulary case is the check, not the run).
- **Follow-up, not this change**: the report's cross-check — `report.sh --from-events` recomputing the run-level
  counts from the record and naming a difference beside the state's figures — is `the-print-sites-are-converted`'s
  requirement, together with the conversion of the remaining sites it names; and the report reading the record
  *instead of* the state file, the state file becoming a projection of the record, is a third change. Both need
  this vocabulary to exist first.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. Every requirement this change adds is new, so no shipped requirement is replaced and no shipped scenario
name has to be repeated verbatim. `agent-issue-loop` gains four:

- the record and its one-write rule (a fact printed once is recorded once);
- the declared vocabulary, and the refusal of an undeclared kind or a missing field;
- the bound and the named rotation;
- the coordinate rule applied to an event.

The cross-check between the record and the report's derived figures is `the-print-sites-are-converted`'s
requirement, and its shipped requirements (`A report reports the shape a run spent its budget in`, `The report says
which phase and which role spent the run's clock`, `The report states whether the loop is getting better`) are
**not** modified here at all: this change adds no reader.

## Impact

Code:

- `tools/loop/lib.sh` — `loop_emit`, the vocabulary reader, the rotation and the bound; `LOOP_RUN_EVENTS`
  resolution beside the state file.
- `tools/loop/events.tsv` (new) — one row per kind: `kind`, required fields, the human format, and the phase
  that owns it.
- `tools/loop/run.sh` — the header is one event and its line is rendered from it; the run's record path and the
  bound it keeps are named in that line. The remaining print-only sites are `the-print-sites-are-converted`'s.
- `tools/loop/layout.tsv` — `lib.sh`'s bound, restated for the lines the writer adds, with its reason in the
  row's note.
- `tools/loop/loop.conf` — `LOOP_RUN_EVENTS_MAX_BYTES` recorded with its default and the reason the bound exists.
- `tools/loop/shared.tsv` — `loop_emit` and `LOOP_EVENT_DEFAULT_MAX_BYTES` declared as what `lib.sh` shares with
  `run.sh`.
- `tools/loop/tests/loop-cases.sh` — the cases below; the `# files:` declarations of the cases this touches.

Guidelines and documents that must follow the code:

- `guidelines/Logging.md` §2 — the record is a log line's shape and follows the same rules; it is the first
  structured one, so the section gains the rule that an engine's record is JSON-lines with no coordinate.
- `guidelines/Backlog.md` section 7 (the loop's procedure) — the run's own record named beside the state file.
- `guidelines/BuildEvidence.md` §4f — what a run leaves as evidence now includes the record; the artifact list
  follows.

Rollback: revert the commit. The record is a file a run leaves in its own state directory; nothing persists it,
no database artefact and no stored value depends on it, and a run started from the reverted tree writes the
record it always did — nothing.

Costs this change does not measure, and so does not claim: the record's size per run, and how rarely it rotates.
Both are read from the runs that follow this landing — `wc -c` on the record and on the run's transcript — rather
than assumed here (`guidelines/BuildEvidence.md` section 11).

surface: 4 requirements and 8 scenarios added to 1 delta file, 0 requirement bodies rewritten, 1 new declared
vocabulary file of 105 lines, 1 print-only site converted (the header, the one `loop_emit` call site in `run.sh`),
3 guideline sections updated (`Logging.md` §2, `Backlog.md` section 7, `BuildEvidence.md` §4f) — counted with
`grep -c '^#### Scenario:' openspec/changes/run-event-stream-is-typed/specs/agent-issue-loop/spec.md` → 8,
`grep -c '^### Requirement:'` on the same file → 4, `wc -l tools/loop/events.tsv` → 105 and
`grep -c 'loop_emit' tools/loop/run.sh` → 1
