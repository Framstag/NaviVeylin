# A restart quotes the reading it replaced

LOOP-ROUTE: guidelines/Backlog.md §7d

## Why

A round the run cut **twice** keeps both readings' `BLOCKING:`/`NOTE:` lines in one file beside the item's reports
(issue #987). The file names two readings — the one a restart stands in place of, and the restart — and issue #987
decided which reading the first of the two is by **name**: `*-restart.md` resolves to `<round>.md` and
`*-restart-retry.md` resolves to `<round>.md` (`tools/loop/evidence.sh:811` before this change).

That derivation cannot carry the distinction. `run.sh` names the restart `<round>-restart.md` whichever attempt it
restarted (`tools/loop/run.sh:2358`), and a round whose answer was cut on the way to the file is asked again as
`<round>-retry.md` (`tools/loop/run.sh:2328`) before it is ever restarted. So for the sequence

1. `<round>.md` — asked, answer cut on the way to the file (no `VERDICT:` line),
2. `<round>-retry.md` — asked again, ended at the reviewer's turn bound, and its findings are the reading the
   restart is handed,
3. `<round>-restart.md` — the restart, ended at a bound as well, so the item is `cut`,

the file quotes step 1's empty report above the restart's findings, and step 2's findings — the ones the run paid a
session for and the ones the restart was handed — stand in no record the item keeps. The second shape is the same
bug one attempt further in: a restart whose own answer was cut is asked again as `<round>-restart-retry.md`, whose
first section resolves past the restart it retried to `<round>.md`.

## The red cases

Two, one per shape, both in `tools/loop/tests/loop-cases.sh` (`:11536` and `:11575`):

- `run#aRestartQuotesTheRetriedReadingItReplaced` — the round before the gate: first attempt cut on the way to the
  file, retry ended at the reviewer's turn bound, restart ended at the same bound. The case asserts the item is
  `cut`, the first section quotes the **retry's** `NOTE:` line and names `pre-gate-review-retry.md`, the restart's
  own reading stands beside it, and no reading that wrote nothing is named.
- `run#aRetriedRestartQuotesTheReadingItRetried` — after the gate: round 1 ended at the turn bound, its restart cut
  on the way to the file, the retry of that restart ended at the same bound. The case asserts the first section
  quotes the **restart's** `NOTE:` line and names `reviewer-report-1-restart.md`, the retry's reading stands beside
  it, and the round's first answer — a reading the retry was never handed — is not quoted in its place.

On the base `251e7abe` with the two cases added and the engine unmodified, both fail —
`evidence/red-on-HEAD-aRestartQuotesTheRetriedReadingItReplaced.log` and
`evidence/red-on-HEAD-aRetriedRestartQuotesTheReadingItRetried.log`.

## The fix

The reading a restart replaced is the report the run had in hand a line before it named the next attempt, and only
the caller holds it. So the caller hands it in (`tools/loop/run.sh:1643`, `:1664`, `:2328`, `:2358`) and the
predicate takes it as its third argument (`tools/loop/evidence.sh:811`), keeping its other job — telling a restart
apart from a first answer — and guessing nothing. The four bound-cut arms pass it on (`tools/loop/run.sh:1696`,
`:1709`, `:2406`, `:2433`).

A round the run cut once keeps exactly the shape it had: no attempt replaced another, the argument is empty, and the
predicate returns nothing as it did before.

surface: 2 cases added (`run#aRestartQuotesTheRetriedReadingItReplaced`, `run#aRetriedRestartQuotesTheReadingItRetried`)
with 4 registry rows, 1 predicate re-signatured in `tools/loop/evidence.sh`, 12 captured-report lines and 4 arms
re-pointed in `tools/loop/run.sh`, 2 mutation records (`1045.1`, `1045.2`), 1 requirement modified — the engine side
counted with `tools/look.sh 'review_replaced_report|pre_gate_replaced_report' --files`, which names `run.sh` with 11
lines, the mutation record and this change's own two artifacts.
