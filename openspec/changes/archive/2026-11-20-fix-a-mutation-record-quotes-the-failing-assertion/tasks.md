# Tasks — the assertion a mutation record quotes

The base is `6f32fdb005aef825bd10d821f0e5bd27ec98f7b8`. The gate is not this child's to run: the run hands this item's
one gate to the child after it, so the tasks that run the full selection, write the change's pointers and archive it
are that child's.

## 1. The red leg first

- [x] 1.1 `tools/loop/tests/loop-mutations-selftest.sh`: a leg after the `--only 9.8` one, running
      `--only 29.8 --engine "$copy_dir"` — the record whose case prints three fixture lines before it fails — and
      asserting that the harness's `RED` line quotes `the merge was refused on a build directory`
      (`tools/loop/tests/loop-cases.sh:10554`), that the reason names a log line after the case's first, and that the
      copy is still byte-identical to the pristine engine. Verify: the same script on the base engine ends
      `1 of 39 cases failed` with this leg's `FAIL` (the harness still quotes the fixture line) —
      `evidence/red-on-base-selftest.log`.
- [x] 1.2 The record itself on the base, before any code changes: `bash tools/loop/tests/loop-mutations.sh --only 29.8`
      → `RED 29.8 … — Bereite Arbeitsverzeichnis vor (neuer Branch 'loop-test')` (`2026-11-20T13:24:29Z`) in
      `evidence/red-on-base-29.8.log`, with the case's own log kept as `evidence/red-on-base-29.8-case.log`.

## 2. The reading

- [x] 2.1 `case_reason` in `tools/loop/tests/loop-mutations.sh`: the first line of the last run of two-space indented
      prefixed lines, prefix and indent stripped, `cut` to 240 characters, printed with the case's log line
      (`case log line <n>: <text>`); a prefixed line with one space remembered as the case's last printed line, so a
      case that printed no assertion is answered with it and said to be that; and the comment above the function
      rewritten to state why the first prefixed line is the fixture's and that the dump precedes `FAIL`. Verify: 1.1's
      leg passes (`evidence/green-selftest.log`, `all 39 cases passed`) and the record prints
      `— case log line 4: the merge was refused on a build directory: …` (`evidence/green-29.8.log`).
- [x] 2.2 `.pi/skills/revert-check/SKILL.md` §"When the gate is a shell self-test, not Gradle": the `RED` line quotes
      the assertion and the line of the case's log it was read from, because a case signals failure with `fail` and not
      with the dump the suite repeats before its `FAIL` line. Verify: `tools/check-doc-values.sh`,
      `tools/check-doc-routes.sh` and `tools/check-rule-routes.sh --diff 6f32fdb0…` are green
      (`evidence/structural-checks.log`, `evidence/rule-routes.log`).
- [x] 2.3 The fallback branch, probed and not cased: `evidence/case-reason-probe.log` — the harness's own `awk`,
      extracted from `case_reason`, over a log with no indented line prints `case log line 3, the last line the case
      printed: ?? architecture/bin/Thing.class`, and over record `29.8`'s real log prints the assertion on line 4;
      the earlier round's `awk` prints nothing over the first of those logs.

## 3. What the delta owes

- [x] 3.1 Structural checks of the text-only files it edits (no build, no suite of its own beyond 1.1):
      `openspec validate fix-a-mutation-record-quotes-the-failing-assertion --strict` (`exit=0`),
      `tools/check-doc-routes.sh`, `tools/tests/check-doc-routes-selftest.sh`, `tools/check-doc-values.sh`,
      `tools/tests/check-doc-values-selftest.sh`, `tools/check-todo-references.sh` (all `exit=0`) — and
      `tools/check-openspec-hygiene.sh`, `exit=1` on four *other* open changes whose `.openspec.yaml` carries no
      `x-issue:` (`a-case-name-resolves-to-one-harness`, `settings-taxonomy-and-surface`,
      `the-bounds-spend-and-budget-are-typed`, `the-print-sites-are-converted`) and not on this one. All in
      `evidence/structural-checks.log`.
- [x] 3.2 The two pathspec checks this delta owes, run by the gate child, are cited from the measure block rather than
      run here: `bash tools/loop/tests/loop-mutations.sh --check` and `tools/loop/tests/loop-mutations-selftest.sh`
      (`LOOP-MEASURE-CHECK` lines). The checks this child runs are the ones its delta reaches: the leg of 1.1
      (`evidence/green-selftest.log`, `all 39 cases passed`), the record set (`evidence/mutations-check.log`, `all 430
      cases passed`), and the self-test cases that declare the two files it edits
      (`tools/loop/tests/loop-selftest.sh --from-git 6f32fdb0…`, `evidence/from-git-selftest.log`, `all 60 cases
      passed`). The change is handed over on the diff (`LOOP-DIFF-READY`), unarchived.
