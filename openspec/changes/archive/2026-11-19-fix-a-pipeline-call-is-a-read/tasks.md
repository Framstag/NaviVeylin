# Tasks — the list separator that opens a command

The base is `0b62e579692b2a3b1e02382c191f2d67ca0dfad9`. The gate is not this child's to run: the run hands this item's
one gate to the child after it, so the tasks that run the full selection, write the change's pointers and archive it
are that child's.

## 1. The red case first

- [x] 1.1 `driver#aCallInPipelinePositionIsARead` in `tools/loop/tests/loop-cases.sh`: `a.sh` defines `shared_fn`, and
      `b.sh` calls it as a pipeline stage, as the second operand of `&&` and as the second operand of `||` — each read
      must be the row `shared_fn	a.sh	b.sh`, and the declaration holding that row must survive
      `layout_shared_refusals` in both directions. Registered in `LOOP_SHELL_CASES` and in the case dispatch, with its
      `# files:` declaration. Verify (the cheapest check a case-writing delta owes, and the plan's first line):
      `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`
      (`evidence/declaration-case.log`).
- [x] 1.2 Red against the reading the base ships: it is `awk: gsub(/[;|&(){}[\]\t"']/, " ", line)` where the fix puts
      the separator back as a word, so record `963.1` puts it back and the case reports
      `a call in pipeline position is a read of the name: expected 'shared_fn	a.sh	b.sh', got ''` →
      `RED 963.1`, `restore … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (`evidence/mutation-963.1.log`), transcribed as `evidence/red-on-HEAD-pipeline-call.xml`
      (`tests=1 failures=1 errors=0`, `2026-11-19T01:49:28Z`).

## 2. The derivation

- [x] 2.1 `is_call` in `SHARED_AWK`: the normalisation class keeps `;` and `|` and `&` out of the whitespace and stands
      `SEP` in for them, and `SEP` joins the predecessors that open a command. Verify: the red case passes
      (`evidence/focused-pipeline-case.log`); and with the declaration untouched the census case now names exactly
      three disagreements — `loop_drop_ignorable_writes` and `loop_shaping_verdicts` with no row, and `loop_die`
      declared with a reader list the derivation does not have (`evidence/census-undeclared-reads.log`).
- [x] 2.2 The same fix in the second copy of the helper, `CALLS_AWK`, where the source-order check asks its question;
      and two fixtures in `driver#aCallToALaterFilesFunctionFails` — a pipeline call and a `&&` call to a function
      only a later file defines. Verify: `bash tools/loop/tests/loop-selftest.sh --case
      'driver#aCallToALaterFilesFunctionFails'` → `PASS`, the engine's own reading still empty
      (`evidence/focused-later-file-case.log`).

## 3. The declaration

- [x] 3.1 Three lines of `tools/loop/shared.tsv`: a row for `loop_drop_ignorable_writes` (`lib.sh`, read by
      `worktree.sh run.sh`), one for `loop_shaping_verdicts` (`lib.sh`, read by `labels.sh`), and `loop_die`'s reader
      list completed with `measure.sh phase.sh report.sh`. Verify: `bash tools/loop/tests/loop-selftest.sh --case
      'driver#theSharedNamesAreDeclared'` → `PASS` (`evidence/census-green.log`).

## 4. The record that keeps the reading

- [x] 4.1 `963.1` in `tools/loop/tests/loop-mutations.sh`, putting the eliminated reading back (`" SEP "` →
      `" XSEP "`, a marker no predecessor rule accepts). Verify: `bash tools/loop/tests/loop-mutations.sh --check
      --only 963.1` → `all 1 cases passed — every selected record applies to the file it names`, then `--only 963.1`
      → `RED 963.1`, `restore 963.1 … green again` (`evidence/mutation-963.1.log`).

## 5. The cases the delta reaches

- [x] 5.1 `tools/loop/tests/loop-cases.sh` is a core file, so the selection a delta over it owes is every case:
      `bash tools/loop/tests/loop-selftest.sh --list --from-git 0b62e579…` → 491 cases. That run is the gate's, and
      the gate child takes it. Verify here, while iterating: `bash tools/loop/tests/loop-selftest.sh --group driver`
      → every case of the group passes (`evidence/focused-driver-group.log`).

## 6. The documents

- [x] 6.1 The capability delta in this change: two requirements MODIFIED, each with the scenario its case names.
      Verify — this delta edits no compiled file and no guideline, so the structural checks of the artifacts it does
      edit stand in for a build it owes none of: `openspec validate fix-a-pipeline-call-is-a-read --strict` and
      `tools/check-openspec-hygiene.sh` both exit 0 (`evidence/doc-checks.log`).
