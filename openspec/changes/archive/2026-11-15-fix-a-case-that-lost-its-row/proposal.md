# Proposal — a case that loses its row is refused

## Why

**Root cause.** `tools/loop/tests/loop-selftest.sh:455` derives the declared case set as
`shell_cases=$(loop_shell_cases)`, so the selection list *is* the suite: `LOOP_SHELL_CASES` is the only place a case's
name is enumerated. Case bodies (`^case_` functions, `tools/loop/tests/loop-cases.sh`) and dispatch arms
(`'<group>#<Name>') fn=case_…`) are never read, and nothing compares the declared set against them — so a case that
loses its row parses, has an arm, has a body, and is reachable by nothing. Every refusal the driver already carries —
`no such shell case` (`tools/loop/tests/loop-cases.sh:1242`), `no case carries the group`, the empty-file-set refusal
— is a filter *over* the declared set, so a vanished row leaves no trace: the suite simply gets smaller.

**Evidence, measured on 2026-11-15 while five item branches were merged into `main` (issue #929).** Branch 922's own
commit `1eddc98c` *replaced* a selection row instead of inserting one:

```
@@ -372,7 +372,7 @@ LOOP_SHELL_CASES=(
-  'state#aCutRecordsTheFilesItsPatchCarries'
+  'report#theArchivesClassesAreDerivedNotInvented'
```

The case kept its arm and its body (today `tools/loop/tests/loop-cases.sh:1241` and `:13672`, which is the shape the
missing check has to see), so `case_state_aCutRecordsTheFilesItsPatchCarries` existed and no name could reach it, and
the change stayed green
through five merges and every focused run by its author. Counts could not see it: `^case_` bodies 453 → 459 (exactly
the six bodies the five branches added), mutation ids 384 = 384, registered names 449 → 454 while dispatch arms went
449 → 455 — the one-row gap is visible only when the three sets are compared name by name. Restored on `main` in
`cd45d567`; the finding is the missing check, not the one row.

**The surface.** Before this change, `tools/look.sh 'LOOP_SHELL_CASES'` → 114 matches in 74 files, 36 of them in
`tools/loop/tests/loop-cases.sh` and one in `tools/loop/tests/loop-selftest.sh`: the rule has one reader (the driver)
and one declaration (the array), and no guideline states it (`tools/look.sh 'LOOP_SHELL_CASES' --path guidelines` → 0
matches). After it, `tools/look.sh 'LOOP_SHELL_CASES'` → 143 matches in 81 files, the difference being this change's
own comment, case and delta.

surface: 1 case added, 0 rewritten, 0 fixtures realigned — `tools/look.sh 'LOOP_SHELL_CASES'` → 114 matches in 74
files, and `--group driver`'s 32 cases green with the check in place

**The red test.** `driver#aCaseWithNoSelectionRowIsRefused` (`tools/loop/tests/loop-cases.sh`), red on the base
`d794277b` with the base's driver: the case builds a nested suite of two bodies, two arms and one row — the shape
`1eddc98c` wrote — and the base reads it as a whole suite (`PASS stub#kept`, exit 0 where the case asserts exit 1).
`evidence/red-on-base.log`, JUnit carrier `evidence/red-on-base.xml` (`tests=1 failures=1`).

**The requirement the item corrects.** `openspec/specs/agent-issue-loop/spec.md:2109`, "A suite run selects what it
was asked for and the gate records what it cost", states what a selection is made *against* the declared set and
nothing about how the declared set relates to the file it describes
(`tools/look.sh 'LOOP_SHELL_CASES|selection row|dispatch arm' --path openspec/specs/agent-issue-loop/spec.md` → 0
matches): the delta adds one requirement and its scenario, and moves no existing text.

## What changes

1. `tools/loop/tests/loop-selftest.sh`: the driver derives the bodies and the arms of the cases file it sourced, and
   refuses a file the declared set disagrees with — a body with no row, a row with no body or no arm, an arm with no
   row, an arm whose function is not the body its name derives — before it lists, selects or runs anything.
2. `tools/loop/tests/loop-cases.sh`: the case `driver#aCaseWithNoSelectionRowIsRefused`, registered in
   `LOOP_SHELL_CASES` and in the dispatcher arm, holding the refusal to the shape the commit wrote.
3. The delta spec under `specs/agent-issue-loop/spec.md`, and no guideline edit: `guidelines/Backlog.recipes.md:873`
   ("The gate and its suite") owns what the suite is and how its numbers are read, and carries no sentence about a
   case's three sets — so the rule lands in the spec the guideline points at, `agent-issue-loop`.

No engine script changes: the driver is the suite's own gate, and the case that names the refusal is the suite's own
case.
