# Tasks — the assertion a mutation record quotes

The base is `20d4bbafdb8a5e59af66f81ddb506b72fe2e99c1`. The gate is not this child's to run: the run hands this item's
one gate to the child after it, so the tasks that run the full selection, write the change's pointers and archive it
are that child's.

## 1. The red leg first

- [x] 1.1 `tools/loop/tests/loop-mutations-selftest.sh`: a leg after the `--only 9.8` one, running
      `--only 29.8 --engine "$copy_dir"` — the record whose case prints three fixture lines before it fails — and
      asserting that the harness's `RED` line quotes `the merge was refused on a build directory`
      (`tools/loop/tests/loop-cases.sh:10443`), that the reason names a log line after the case's first, and that
      the copy is still byte-identical to the pristine engine. Verify: the same script on the base engine ends
      `M of N cases failed` with this leg's `FAIL` (the harness still quotes the fixture line) —
      `evidence/red-on-base-selftest.log`, transcribed as `evidence/red-on-base-selftest.xml`
      (`tests=39 failures=1 errors=0`).
- [x] 1.2 The record itself on the base, before any code changes: `bash tools/loop/tests/loop-mutations.sh
      --only 29.8 --out evidence/red-on-base-29.8.log` → `RED 29.8 … — Bereite Arbeitsverzeichnis vor (neuer Branch
      'loop-test')` (`2026-11-19T02:40:17Z`), transcribed as `evidence/red-on-base-29.8.xml`
      (`tests=1 failures=1 errors=0`), with the case's own log kept as `evidence/red-on-base-29.8-case.log`.

## 2. The reading

- [x] 2.1 `case_reason` in `tools/loop/tests/loop-mutations.sh`: the first line of the last run of two-space indented
      lines of the case's text, prefix and indent stripped, `cut` to 240 characters, printed with the case's log line
      (`case log line <n>: <text>`); the fallback for a case that printed no assertion, said to be its last line; and
      the comment above it rewritten to state why the first prefixed line is the fixture's and that the dump precedes
      `FAIL`. Verify: 1.1's leg passes (`evidence/green-selftest.log`) and the record prints
      `— case log line <n>: the merge was refused on a build directory: …`
      (`evidence/green-29.8.log`).
- [x] 2.2 `.pi/skills/revert-check/SKILL.md` §"When the gate is a shell self-test, not Gradle": the `RED` line quotes
      the assertion and the line of the case's log it was read from, because the suite repeats the case's text before
      its `FAIL` line. Verify: `tools/check-doc-values.sh` and `tools/check-doc-routes.sh` are green, and
      `bash tools/check-rule-routes.sh --diff 20d4bbaf… --change openspec/changes/fix-a-mutation-record-quotes-the-failing-assertion`
      names every derived section this change's artifacts name.

## 3. What the delta owes

- [x] 3.1 Structural checks of the text-only files it edits (no build, no suite of its own):
      `openspec validate fix-a-mutation-record-quotes-the-failing-assertion --strict` (the `--all` reading exits 1 on
      long-requirement warnings the tree already carried, none of them this change's), `tools/check-openspec-hygiene.sh`,
      `tools/check-doc-routes.sh`,
      `tools/tests/check-doc-routes-selftest.sh`, `tools/check-doc-values.sh`, `tools/tests/check-doc-values-selftest.sh`.
      All eight, with `tools/check-todo-references.sh`, in `evidence/structural-checks.log`, each `exit=0`.
- [x] 3.2 The two pathspec checks this delta owes, run by the gate child, are cited from the measure block rather than
      run here: `bash tools/loop/tests/loop-mutations.sh --check` and
      `tools/loop/tests/loop-mutations-selftest.sh` (`LOOP-MEASURE-CHECK` lines). The leg of 1.1 is the one check this
      child runs, because it is the change's own case — handed over on the diff (`LOOP-DIFF-READY`), not run here.
