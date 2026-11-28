# The run's report prints the share of items whose route resolves

Item #974's third arm, the parent's own "Measure after". #1080 landed the resolution (C6 in
`tools/loop/evidence-check.sh:185`) and #1081 landed the line the resolution reads (`LOOP-ROUTE:` in
`tools/loop/prompts/worker.md:245`). Neither prints a figure: `tools/loop/report.sh` names no route at all — a
`grep -i route` over its 1210 lines answers nothing — and C6's counts (`evidence-check.sh:238`, `ok C6 route: 2 of 2
route line(s) resolved`) are written to a log the run never reads back.

**Root cause, in one sentence:** the resolution exists and its counts are printed only into the round's own log, so
the loop cannot say what share of its landed work reached its guideline, which is the number `#974` exists to move
from 55 % to 100 %.

**The red test:** `report#theRouteShareIsPrinted` reads a state file whose three items name one change with a route
that resolves, one with a route naming `§99` its document does not carry, and one naming no change; on the base
commit the report prints no `route:` line at all, so the case's first assertion fails on the absent line rather than
on the arithmetic.

**The owning requirement:** `agent-issue-loop` — the requirement "The run's report prints the share of items whose
route resolves", added by this change's delta. The owning guideline section is the loop's procedure in
`guidelines/Backlog.md`, whose index states what the run reports, and the recipes section this change's route names
carries the commands and the sample line.

**The route this change names:**

- LOOP-ROUTE: guidelines/Backlog.recipes.md §7e

surface: 3 functions added to tools/loop/lib.sh, 1 printed line added to tools/loop/report.sh, 1 call site in
  tools/loop/evidence-check.sh restated onto them, 3 cases added to tools/loop/tests/loop-cases.sh, 1 mutation record
  added — the functions are counted by `tools/look.sh '^loop_route' --path tools/loop/lib.sh`, the printed line by
  `bash tools/loop/report.sh --state <a run's state> | grep '^route:'`, the cases by
  `bash tools/loop/tests/loop-selftest.sh --list | grep '^report#'`.`

## What Changes

- **The resolution moves into `tools/loop/lib.sh`**, where both readers reach it: `loop_route_lines <dir>` returns
  the anchored `LOOP-ROUTE:` lines of a change's markdown, `loop_route_ids <doc>` the ids the document's own `## `
  headings carry, and `loop_route_resolves <root> <doc> <id>` the comparison between them. C6 keeps its verdicts and
  its messages and loses its second copy of the rule, so a gate that resolves and a report that counts cannot
  disagree.
- **One line of the run's report** (`tools/loop/report.sh`, beside the `bound:` line at `report.sh:393`): the number
  of the run's items whose record names a change carrying at least one route line, the number of those whose every
  route line resolves, the share through `bound_share` (`report.sh:328`) and the archive's same figure beside it.
  The change directory is found through `loop_change_path` / `loop_change_archive_dir` (`lib.sh:684`), so a landed
  change is counted from its archived copy and an item whose change never landed is counted from the live one.
- **The counted set is the items that wrote a report**, which is the population `#974`'s 55 % baseline counts: an
  item whose record names no change, and a change carrying no `LOOP-ROUTE:` line, are outside the denominator
  rather than counted as a miss. No record field is added, so a state written before this lands answers.
- **No configuration key.** The denominator rule is fixed by the requirement, not a project's judgement, so nothing
  new lands in `tools/loop/loop.conf`.
