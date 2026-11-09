# Design — one line, two halves, and the denominator that must not lie

## The root cause

Every figure a run prints about a bound is a figure about **one item**: the table's `context` column is one item's
largest context, the token band names the items above it, the cut line names the items the bound ended. What a run
never states is how many of its items that was **out of what it attempted** — the only shape in which two runs can be
compared, and the shape the item's own table is written in (2 of 11 items). So a run at five times the archive's rate
prints the same three lines as a healthy one, and the 36 Mtok it spent with nothing landed is a number nobody can
act on because nothing says it is unusual.

## The single fix

One line, printed beside the token band (the other run-level figure about a bound), built from two facts the state
already records:

1. **The class** is `stopReason: bound` — the word `state.sh done` refuses to carry on any other result than a block or
   a bound cut, and the word every arm that ends a child at a bound the run enforces writes (`run.sh`'s turn bound,
   child clock, deadline and context bound all reach the same arm). No vocabulary is added for the context bound
   alone: a `context` value beside `bound` would be a second mechanism for one fact, and the archive could not be
   re-counted into it without reading notes.
2. **The denominator** is the records that can answer. 160 of the archive's 407 item records were written before
   `stopReason` existed. Counting them below the line makes the archive's rate read *lower* than it is (2.7 %
   instead of 4.5 %), and a rate that reads too low is exactly the measurement the item was written about. So each
   side is counted over records that carry the field, and the line says so, because a denominator that is not the
   whole population has to be named or the two rates are not comparable.

The second half is what makes the figure actionable: an item the bound ended is named with the turns it spent and the
files its leftover patch carries. `metrics.turns` was already recorded; the file count was not, so
`state.sh done --leftover` derives `leftoverFiles` from the patch the caller names — read from the file, not passed in
beside it, because the patch is the only account of what it holds and a second account can disagree with it. A path
that does not resolve records nothing, because `0 files` would read as a patch that was measured and was empty.

## Where the line goes, and why it is not in the table

Columns are append-only in this report for a reason (`state.sh report`: every index a reader already quotes keeps its
number), and a per-item column cannot carry a rate — the rate is a property of the run. The line therefore sits with
the run-level figures, directly after `over the item token bound`, which is the figure a bound is already read
against. The item's own words ask for it beside that line.

## Blast radius

| file | what changes |
|---|---|
| `tools/loop/report.sh` | `bound_share` (the one-decimal share, `-` when the population is empty), the archive read, the line |
| `tools/loop/state.sh` | `leftoverFiles` on the item record, read from the patch `done --leftover` names |
| `tools/loop/tests/loop-cases.sh` | `report#theBoundRateIsNamed`, `state#aCutRecordsTheFilesItsPatchCarries` |
| `tools/loop/tests/loop-mutations.sh` | `265.1` (the share), `265.2` (the file count) |
| `guidelines/Backlog.recipes.md` §7b | the paragraph: the line, its denominators, its measurement, what it is not |
| `openspec/specs/agent-issue-loop/spec.md` | one ADDED requirement, two scenarios, one case each |

Nothing compiled, nothing shipped, no Gradle task, no device: the loop's own self-test is the gate.

## Alternatives the evidence eliminated

- **A new mechanism value (`context`) beside `bound`** — eliminated because it splits one fact into two vocabularies:
  `state.sh next` reads the pair `(result, stopReason)` to keep a bound cut out of the refusal streak, `prescreen.sh`
  reads it to queue the item, and every arm of `run.sh` writes `bound`. A finer word would have to be threaded
  through all of them to say what the transcript's `LOOP-TIMEOUT` line already says, and the archive's older records
  would stay uncountable either way.
- **The archive over all its item records** — eliminated by arithmetic: the 160 fieldless records would sit in the
  denominator and never in the numerator, so the archive would print 2.7 % where its own records say 4.5 %, and the
  line would understate the very rate it was added to expose.
- **A stored rate on the run** — eliminated because it is derived: two runs of one state would disagree the moment a
  record was added, and the state already holds the facts (`result`, `stopReason`, `metrics.turns`, `leftoverFiles`).
  Recording a derived number is the defect `state.sh`'s `--gate-waived` exists to avoid elsewhere.
- **Reading the leftover's files at report time instead of at `done`** — eliminated because `loop-clean` releases an
  item directory after the retention period: the patch a reader would count can be gone, while the fact it carried
  belongs to the run's record.
- **Starting a wide item with the split shape** (the item's third candidate) — out of scope, not eliminated: no
  measurement here fixes the N. See the proposal's "Not in this change".

## Verification

- Both cases red on the base and green on the change (`evidence/red-on-HEAD.log`, `evidence/green-on-change.log`),
  each named by exactly one scenario of the delta.
- One mutation per invariant (`265.1`, `265.2`): the named case goes red for the named reason and green again on the
  restore (`evidence/mutation-265.log`).
- Every record the change's paths select still applies: `--check` (`evidence/mutation-check.log`).
- The surrounding cases the line's own rule touches — the token band, the cut line, the mechanism, the record's
  fields — together with the two new ones (`evidence/focused-cases.log`).
- The structural checks of the shell files (`bash -n`), the documentation route the §7b edit owes and the change's
  measure block (`evidence/structural-checks.log`, `evidence/measure.txt`).
- The gate itself — `tools/loop/gate.sh --base main` — run by this item's finalize step, which is the child that owns
  it.

## Rollback

Revert the four files' hunks. The two cases return to red, `tools/loop/tests/loop-mutations.sh --check` reports
`265.1` and `265.2` as `MUTATION-DID-NOT-APPLY`, and the §7b paragraph is the only prose left to delete with them. A
state written by this version carries one more field and every reader tolerates its absence.

## Open decisions

none

The item's `ITEM_DECISION` block records `none`, and the direction is the item's own stated one: the rate is measured
and what a cap-hit kept is priced; the bound itself is not moved.
