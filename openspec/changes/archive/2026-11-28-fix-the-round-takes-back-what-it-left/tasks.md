# Tasks — #1098, a round takes back the difference it left

## 1. The restore (`tools/loop/leftovers.sh`)

- [x] 1.1 `restore_project_difference <report file> <before> <after>` resolves the paths — the round's kept patch
      under `outside-write/` where one exists (#1092), `project_state_diff` otherwise — and removes each of them
      against `$LOOP_ROOT`: `git clean -qfd --` for a path the after-reading carries as `??`, `git checkout --`
      for anything else. Verification: `tools/loop/tests/loop-selftest.sh --case
      'run#childWriteOutsideTheWorktreeIsNoted'` asserts `0` paths a child wrote are left in the caller's tree,
      where the base commit leaves `3` (`evidence/red-on-base.xml`, `evidence/green-on-change.xml`).
- [x] 1.2 the run's own state directory is left alone, by the exclusion `tools/loop/run.sh:649` draws — nothing is
      removed of a path equal to it, beneath it or holding it. Verification: the same case, whose run writes its
      state under the project (`state_file`) and reads it back after every phase — a removal there leaves no
      state to read and the case fails before its own arms.
- [x] 1.3 a path the caller had already changed before the round began is left where it stands and named
      `LOOP-OUTSIDE-WRITE-NOT-OURS:`. Verification: read against the function, and the case's run cannot reach
      the arm — `tools/loop/run.sh:655` refuses to start on a dirty tree, so the arm is a live-only state. Named
      in `design.md` rather than claimed by a case.
- [x] 1.4 what the tree still reads as a difference after the removal is named `LOOP-OUTSIDE-WRITE-KEPT:` on the
      child's transcript and the function returns non-zero. Verification: the call site discards the code
      (`|| true`) so a kept path is a record, not a verdict, and the case's transcript assertions read the lines
      the function writes.

## 2. The call site (`tools/loop/child.sh`)

- [x] 2.1 the restore runs where the difference is recorded, after the record line is appended, so the item keeps
      what the round took back. Verification: the same case reads `LOOP-OUTSIDE-WRITE:` and
      `LOOP-OUTSIDE-WRITE-RESTORED: demo-owned.txt` on the worker's own transcript, and `demo-owned.txt` is gone
      from the tree at the same time.
- [x] 2.2 the call is inside the branch that found a difference, so a round that wrote nothing costs nothing.
      Verification: `run#aPhaseThatWroteNothingIsAskedOnceMore` and
      `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` still pass in the same suite run.

## 3. The structural tables

- [x] 3.1 `tools/loop/layout.tsv`'s `leftovers.sh` row names the new function in definition order and describes
      what the file now does. Verification: the suite's layout cases
      (`tools/loop/tests/loop-cases.sh`, the `driver#` group) read the table's order and the entry's sourcing;
      no compiled file, so no build stands in for it.

## 4. The red case (`tools/loop/tests/loop-cases.sh`)

- [x] 4.1 `run#childWriteOutsideTheWorktreeIsNoted` gains three arms — no path a child wrote is left in the
      caller's tree, the worker's shell-written file is gone, and the removal is named on the transcript — and
      its stub's review round writes a path of its own instead of deleting the two earlier ones, which the
      restore now takes back before that round begins. Verification: the case fails on the base commit
      (`evidence/red-on-base.log`, `1 of 1 cases failed`) and passes on the change
      (`evidence/green-on-change.log`).
- [x] 4.2 its `# files:` declaration still names every file the case can reach: `tools/loop/child.sh`,
      `tools/loop/evidence.sh`, `tools/loop/leftovers.sh`, `tools/loop/run.sh` and the fixtures. Verification: the
      declaration is unchanged by this delta, and the change touches no path outside it.

## 5. The guideline section the rule lives in (`guidelines/Backlog.recipes.md`, section `7a`)

- [x] 5.1 the "What it does **not** claim" paragraph states that the difference is taken back — where the record
      is written, what is removed, the two paths left where they stand, and that the verdict rules beside it are
      unchanged. Verification: `tools/loop/evidence-check.sh` C6 resolves the route line against the section's
      own heading (`evidence/evidence-check.log`).

## 6. Evidence and the archive

- [x] 6.1 the case is run once on the base commit's `tools/loop/child.sh` and `tools/loop/leftovers.sh` (the tree
      put back afterwards) and once on the change, and both are kept: `evidence/red-on-base.log`,
      `evidence/red-on-base.xml`, `evidence/green-on-change.log`, `evidence/green-on-change.xml`.
- [x] 6.2 `bash tools/loop/evidence-check.sh <this change> --root .` passes, and its log is kept as
      `evidence/evidence-check.log`.
- [x] 6.3 the change is archived with its delta (`openspec archive --yes`) before the gate runs, so the tree the
      gate measures is the tree a round reads.
