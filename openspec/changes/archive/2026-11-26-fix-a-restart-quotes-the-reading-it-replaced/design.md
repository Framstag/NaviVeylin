# Design — a restart quotes the reading it replaced

## Open decisions

none

## Context

`review_findings_for_double_cut` was written for issue #987 to answer one question: is this reading the second of
two? Its answer was the report's name, and the same name was then used to derive **which** reading this one stands
in place of — `*-restart.md` → `<round>.md`, `*-restart-retry.md` → `<round>.md`. The name answers the first
question and cannot answer the second, because `run.sh` reuses one restart name for whichever attempt it restarted,
and because a round whose answer was cut on the way to the file (issue #224's retry) is asked again before it is
restarted at all.

## D1 — The caller hands the replaced reading in, the name keeps only its first job

`review_findings_for_double_cut <item dir> <the reading's report> <the reading it replaced>`
(`tools/loop/evidence.sh:811`) returns nothing unless the report's name says this reading is a restart, and then
quotes the two reports it was handed. The name still decides *whether* this is a restart — that is a fact about the
form of the name and it is the name's to carry; *which* reading was replaced is a fact about the run's own history,
and the run holds it at the moment it names the next attempt.

Rejected: keeping the derivation and adding a second name suffix (a name per replaced attempt) — that puts the run's
history into a filename a later reader has to decode, and the run already has the report in a variable at the site.

Rejected: reading the replaced report off the filesystem (the newest `*retry*.md` beside the item) — mtime order is
not the run's order, and a resumed item's directory carries an earlier run's attempts.

## D2 — Four capture sites, one per place an attempt is named

An attempt replaces another in exactly two places in each review phase: the transport-cut retry
(`tools/loop/run.sh:1643`, `:2328`) and the restart (`tools/loop/run.sh:1664`, `:2358`). Each capture is one
assignment of the report the run is about to stop reading, placed immediately before the report's name is
reassigned, so the capture and the naming cannot drift apart.

Rejected: one capture at the restart only — the retry of a restart replaces the restart, and that is the second
shape of the bug this item names.

## D3 — Nothing to quote writes nothing, and a report never quotes itself

The predicate returns nothing when the replaced reading is empty (no attempt replaced another, so no restart was
started) and when it is the same path as the reading itself. The second case is the run's own name reuse: a round
restarted twice names its second restart `<round>-restart.md` again, and quoting that file as both readings would
state a history that did not happen.

Rejected: falling back to the name derivation when the argument is empty — that is the guess this change removes,
and it would keep the bug reachable from a path nobody tests.

## D4 — The declarations are unchanged

The three functions keep their names, their files and their callers, so `tools/loop/layout.tsv`, `shared.tsv` and
`events.tsv` need no row: a signature is not a declaration the tables carry. The arity change is stated where the
run reads it — the function's own argument comment and its four call sites.

## Evidence

- Red: `evidence/red-on-HEAD-aRestartQuotesTheRetriedReadingItReplaced.log`,
  `evidence/red-on-HEAD-aRetriedRestartQuotesTheReadingItRetried.log` — the two cases against the base
  `251e7abe` with the engine unmodified.
- Green: `evidence/green-aRestartQuotesTheRetriedReadingItReplaced.log`,
  `evidence/green-aRetriedRestartQuotesTheReadingItRetried.log`, and
  `evidence/green-declaration.log` (`driver#everyCaseDeclaresTheFilesItCanReach`).
- Falsified: `evidence/mutations.log` — `1045.1` reverts the predicate to the name derivation and reddens
  `run#aRestartQuotesTheRetriedReadingItReplaced`; `1045.2` drops the retry's capture and reddens
  `run#aRetriedRestartQuotesTheReadingItRetried`. Both restores are green again.
