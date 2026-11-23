# Proposal — the two readings of a round the run cut twice are kept

## Why

The run restarts a round a bound ended and hands the restart what the cut round wrote **in its prompt**
(`review_restart_scope`, `tools/loop/evidence.sh`; issue #918). When the restart is ended at a bound as well — the
item is recorded `cut`, `stopReason: bound` — the run has paid for two readings of one change and puts neither
down. What the first reading wrote lives only in the prompt string of one attempt that already exited, the restart's
report is the only file left, and the item's own note names the bound and nothing else. A person who picks the item
up from the report's `resume #<item>` line — the one line a `cut` item gets (`tools/loop/report.sh:1132`, which
prints the state's note verbatim) — resumes a branch knowing that two rounds read the change and neither found out
what they said.

Measured over the runs that carry `LOOP_REVIEW_RESTART_MAX=1` after #918 landed: 57 passes, 13 `done`, 11 `cut`, 12
restarts started, of which 8 were ended at a bound a second time — so two thirds of the restarts the run paid for
left no account. #892's single round is the shape in numbers: `metrics.reviewInput` recorded
`1 of 2 parts over the 32000 B cap · 502773 of 558533 B cut (90 %)`, i.e. the round was reading a tenth of its diff
before a bound ended it either way, and #1005's restart was ended at the same `LOOP_CHILD_TURNS_MAX_REVIEWER` after
reading about a third of its input.

## What changes

Three layers, one file each:

1. **`tools/loop/evidence.sh`** — `review_findings_for_double_cut` reads the reading's own report name
   (`<round>.md`, `<round>-retry.md`, `<round>-restart.md`, `<round>-restart-retry.md`, the names `run.sh`'s review
   loop already writes): a name carrying a restart suffix is a reading whose round the run had already cut once, and
   the reading it replaced is the same name with the suffix taken off. `review_findings_evidence` writes one file,
   `review-findings.md`, beside the item's reports: a header, then the `BLOCKING:`/`NOTE:` lines of the first
   reading and of the restart, each under the name of the report the whole of it stands in. `review_findings_part`
   quotes one reading to `LOOP_REVIEW_INPUT_MAX_BYTES` with the `[LOOP-CUT: …]` marker a cut part already carries,
   so the file is bounded by an existing knob rather than a new one.
2. **`tools/loop/run.sh`** — the four arms that record a bound-cut round (the pre-gate deadline and bound arms, the
   post-gate review-deadline and bound arms) call it with the report they already hold, and the `cut)` arm's note
   gains one clause naming the file when one was written, so the file travels on the report's resume line. The four
   arms keep the write order they had.
3. **the declarations** — `tools/loop/layout.tsv` (the three functions are `evidence.sh`'s), `tools/loop/shared.tsv`
   (`review_findings_for_double_cut` is assigned by `evidence.sh` and read by `run.sh`) and `tools/loop/events.tsv`
   (a composer prints a value a caller or a file takes).

Nothing in `run.sh` changes which bound is a cut or which arm cuts: the fix records what the run already knows at
the moment it cuts.

## Root cause

The restart's findings were a **value of one attempt's prompt** and never a value of the item's record. #918 chose
that shape deliberately and refused its alternative: deriving a smaller input from the first reading "is a constant
in other clothes" (`tools/loop/loop.conf`, the recorded refusal beside `LOOP_CHILD_TURNS_MAX_REVIEWER`). This change
does not revisit that refusal — it does not re-derive any bound, and it does not hand either reading more input. It
writes down what the two readings had already produced, which is a fact about the item and not a new budget.

## Red case

`run#aRoundCutTwiceKeepsBothReadingsFindings` (new) drives the round before the gate into a bound, lets the run
restart it, ends the restart at the same bound, and asserts the item is still `cut`, the file exists, both readings'
`BLOCKING:` lines stand in it under their own report names, the item's note names the file, and the report's resume
line carries that note. On the base commit the run writes no such file: `evidence/red-on-HEAD.log`, 1 of 1 cases
failed, 2026-11-23T16:04:16Z, `the two readings' findings were not kept as the item's evidence`.

## Surface

surface: 3 functions added in `tools/loop/evidence.sh`, 1 state line, 4 call sites and 1 note clause in
`tools/loop/run.sh`, 3 declaration rows (`tools/loop/layout.tsv`, `tools/loop/shared.tsv`,
`tools/loop/events.tsv`), 1 case added and 3 mutation records — the surface is the loop's own engine, re-taken with
`tools/loop/tests/loop-selftest.sh --case 'run#aRoundCutTwiceKeepsBothReadingsFindings'` and
`tools/loop/tests/loop-mutations.sh --only '987.1 987.2 987.3'`.

## Not the fix

Re-deriving the reviewer's turn bound or its context cap from the size of the diff (Arm 1): refused by #918's
recorded decision, and a re-measurement of a constant the profile already owns. A new configuration key or a new
marker for the findings file: `LOOP_REVIEW_INPUT_MAX_BYTES` already bounds a round's input and `[LOOP-CUT: …]`
already names a cut, so a second knob would be a second way to say the same thing. A new report line: the resume
line already carries the note, and one item stays one line (issue #850).
