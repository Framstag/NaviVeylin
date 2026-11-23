# Design — the two readings of a round the run cut twice are kept

## Open decisions

none

## Context

The run's review loop names every attempt of a round by a suffix on the round's name
(`<round>.md`, `<round>-retry.md`, `<round>-restart.md`, `<round>-restart-retry.md`). #918's restart hands the
restart the cut round's `BLOCKING:`/`NOTE:` lines *as prompt text*; the restart's own report is the only artefact
either reading leaves. When the restart is ended at a bound as well, the item is `cut` and the state's note — the
text `report.sh` prints on the report's `resume #<item>` line — names the bound and nothing about either reading.

The decisions below are the ones the implementation had to make; the alternatives are recorded so a later change
does not re-open them by accident.

## D1 — The predicate is the report's own name, not a new state field

`review_findings_for_double_cut <item dir> <the reading's report>` recognises "this reading is the second of two" by
the report's **name**: a report whose name carries a `-restart` suffix is one whose round the run had already cut
once, and the reading it replaced is that name with the suffix removed.

- The alternatives were a new state variable set by the restart arm (`review_restarted=1`) or a flag file. Both add
  a fact that has to be written, reset per item and kept in step with the four arms; the name is already written by
  the loop, already distinguishes all four shapes the loop produces, and needs no reset.
- The consequence to accept: a report renamed by a later change would stop matching. That is bounded — `run.sh`
  writing `<round>-restart.md` is asserted by `run#aBoundCutRoundIsRestartedWithItsFindings` — and the failure is a
  missing file, not a wrong one.
- A once-cut round is untouched: `*) return 0` leaves every other name writing nothing and adding no clause.

## D2 — The file is bounded by an existing cap, not a new knob

`review_findings_part` quotes one reading to `LOOP_REVIEW_INPUT_MAX_BYTES` (`loop_conf_value`, non-numeric treated as
`0` = unbounded) and marks the cut with `[LOOP-CUT: the findings above are quoted to N bytes; the whole of them
stands at <report>]`. A new configuration key would be a second way to say "a round's text is bounded", and a second
marker would be a second way to say "this text was cut"; the existing marker already means exactly that, and its
sentence is edited only in what it names (the report rather than a cut index) because a findings file is not a
round's input.

## D3 — One file beside the item's reports, named by the item's own note

`review_findings_evidence` writes `<item dir>/review-findings.md`, and `run.sh`'s `cut)` arm adds one clause to
`cut_note`: `· the findings of both readings of the round the run cut twice are kept at <path>`. `cut_note` becomes
the state's `note` (`tools/loop/state.sh`), and `report.sh:1132` prints the note inside the item's one
`resume #<item>` line, so the file is reachable from the only line a caller is given without adding a report line
(one item is one line — issue #850) and without a new `metrics.*` field whose reader does not exist yet.

## D4 — Degrade silently

When neither reading wrote a `BLOCKING:` or `NOTE:` line there is nothing to keep: `review_findings_evidence` writes
no file (`` [ -n "$a$b" ] || return 0 ``), `review_findings_for_double_cut` prints nothing, `review_findings_file`
stays empty and the `cut)` arm adds no clause. The `[ -s "$dest" ] || return 0` guard keeps a file that a failed
write left empty from being named. A double cut with no findings therefore reads exactly as it did before this
change.

## D5 — The write order and the failure mode are the ones the arm already had

The call sits where the arm already decides the cut, before the label write; nothing is reordered, so a run that
fails to write its labels still records the cut first. A write that fails (unwritable item directory) returns
nothing, so the arm keeps the note it had — the same shape as `leftover_patch`, whose clause is conditional on the
same kind of value.

## D6 — One reading's section names the report its whole stands in

Each section's heading is `## The first reading (<report basename>)` / `## The restart (<report basename>)`, and the
`[LOOP-CUT: …]` marker repeats the report name. A quote without the name of the file it was taken from is a claim a
reader cannot check, and the restart's report is the only one of the two that still exists by the time the item is
cut.

## What this does not touch

The bound itself, the restart's prompt, `LOOP_REVIEW_RESTART_MAX`, the four arms' cut decisions, `metrics.*`, the
report's action lists, and the archived #918 refusal recorded beside `LOOP_CHILD_TURNS_MAX_REVIEWER`.
