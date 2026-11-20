# Tasks

## 1. The reader answers in one walk

- [x] 1.1 `tools/loop/lib.sh`: `loop_pids_under` asks `find /proc -maxdepth 2 -name cwd -lname …` for the whole host
      in one process, keeping the contract it had (a pid per line, `$$` and `$PPID` excluded, the ` (deleted)` target
      matched) and dropping the `readlink` per process. — done: the case `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`
      passes focused, and at a pool of forty instances it is **40 of 40** on this tree against **0 of 40** on base
      `b63de90b` (`.pi/issue-loop/items/950/pool-fix-40-b63de90b.txt`,
      `.pi/issue-loop/items/950/pool-HEAD-40-b63de90b-verified.txt`, 2026-11-20).
- [x] 1.2 `tools/loop/lib.sh`: `loop_glob_escaped` escapes `*`, `?`, `[`, `]` and `\` in the directory before it
      becomes a `-lname` pattern, so a tree whose name carries one is still matched. — done: the case
      `clean#aTreeWhoseNameIsAPatternIsStillRead` stages a process in a directory named `a[1]*x`, asserts the reader
      names it, and asserts the raw pattern names nothing
      (`.pi/issue-loop/items/950/focused-cases-b63de90b.log`).

## 2. The attribution stops forking per hop

- [x] 2.1 `tools/loop/run.sh`: `pid_ppid` reads `/proc/<pid>/stat` with the shell's own `read` and splits the fields
      after the last `") "`, instead of running a `sed` per hop; it answers nothing when the file is gone or the line
      is not the kernel's shape. — done: `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` passes
      (`.pi/issue-loop/items/950/focused-cases-b63de90b.log`), and mutation 12.11 — which quotes the line this task
      changed beside it — goes red for its own reason and restores green
      (`.pi/issue-loop/items/950/mutation-12.11-b63de90b.log`).
- [x] 2.2 `tools/loop/run.sh`: `pid_left_the_worktree` is handed the pids standing in the worktree, which
      `outside_write_seen` takes in its first call, instead of a `readlink` per ancestor per candidate; a gone
      ancestor is simply not in the set, which is the answer the kernel gave for it before. — done: the same case
      passes, and at a pool of forty the reading's answer is 40 of 40
      (`.pi/issue-loop/items/950/pool-fix-40-b63de90b.txt`).

## 3. The reading states its own cost

- [x] 3.1 `tools/loop/run.sh`: `outside_write_seen` prints one
      `LOOP-OUTSIDE-WRITE-COST: procs=<n> attributed_ms=<n> standers_ms=<n>` line beside the answer, with
      `loop_now_ms` (a millisecond clock with a whole-second fallback) and `loop_proc_count` (a glob expansion, no
      fork) as the only new readers. — done: the case's assertion holds it to exactly one line of that shape in the
      transcript the run writes (`.pi/issue-loop/items/950/focused-cases-b63de90b.log`).
- [x] 3.2 `tools/loop/tests/loop-cases.sh`: the item's case asserts exactly one cost line, in the shape the reading
      prints, and declares `tools/loop/lib.sh` among the paths it can reach. — done: `bash
      tools/loop/tests/loop-selftest.sh --case 'run#theWorkersOwnShellWriteIsRecordedAndNotPlaced'` → `PASS`
      (`.pi/issue-loop/items/950/focused-cases-b63de90b.log`), and `driver#everyCaseDeclaresTheFilesItCanReach`
      passes on that declaration (same file).

## 4. The records this delta invalidates

- [x] 4.1 `tools/loop/tests/loop-mutations.sh`: record 12.11 quoted its line as `pid_left_the_worktree "$pid"
      "$LOOP_WORKTREE" || continue`, and task 2.2 hands that argument `"$in_worktree"` instead — a record whose
      pattern no longer matches is `MUTATION-PATTERN-STALE` and a red gate. The record is re-quoted at the line as it
      now stands, and its case is unchanged. — done: `bash tools/loop/tests/loop-mutations.sh --check` → `all 430
      cases passed — every selected record applies to the file it names`
      (`.pi/issue-loop/items/950/structure-checks-b63de90b.log`), and `--only 12.11` → `RED 12.11
      run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` for its own reason, `restore 12.11 … green again`,
      `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (`.pi/issue-loop/items/950/mutation-12.11-b63de90b.log`).

## 5. The red case, and the change's own record

- [x] 5.1 The red reading on the tree before the fix: the item's case at a pool of forty instances on base
      `b63de90b`, measured on a pristine clone of that commit in the item's scratch — **0 of 40** passed, every
      instance on `the shape was found: ' … ' not found in: LOOP-OUTSIDE-WRITE-ALIVE: (none) · attributed=none`
      (`.pi/issue-loop/items/950/pool-HEAD-40-b63de90b-verified.txt`, transcribed as
      `.pi/issue-loop/items/950/red-on-HEAD-950.xml`). Green on this tree at the same pool: **40 of 40**
      (`.pi/issue-loop/items/950/pool-fix-40-b63de90b.txt`).
- [x] 5.2 `openspec/changes/fix-the-outside-write-reading-is-one-walk-not-one-process/specs/agent-issue-loop/spec.md`:
      MODIFIED requirement "The reading that places a write has its own cadence", carrying its shipped scenario
      unchanged plus the two this change adds, each naming the one case that fails when the clause is removed. —
      done: `openspec validate fix-the-outside-write-reading-is-one-walk-not-one-process --strict` → `Change
      'fix-the-outside-write-reading-is-one-walk-not-one-process' is valid`
      (`.pi/issue-loop/items/950/openspec-validate-b63de90b.txt`, 2026-11-20), and `measure.sh --base b63de90b
      --change …` counts `scenarios=3 cases=3 unnamed=0`
      (`.pi/issue-loop/items/950/measure-950.txt`).

## 6. Structural checks, since nothing here compiles

- [x] 6.1 The five files this change edits are shell, case and guideline sources, so the structural check stands in
      for a build: `bash -n` on each of `tools/loop/lib.sh`, `tools/loop/run.sh`, `tools/loop/tests/loop-cases.sh` and
      `tools/loop/tests/loop-mutations.sh`. — done, no output
      (`.pi/issue-loop/items/950/structure-checks-b63de90b.log`).
- [x] 6.2 The declarations, tables and bounds the delta writes into: `bash tools/loop/tests/loop-selftest.sh
      --group driver` → **37 of 37 cases passed**, which carries `driver#everyCaseDeclaresTheFilesItCanReach` (the
      `# files:` line this delta narrows) and `driver#theCaseBoundScalesWithThePool`
      (`.pi/issue-loop/items/950/driver-group-b63de90b.log`, 60.59 s).
- [x] 6.3 The guideline that owns the mechanism: `guidelines/Backlog.recipes.md` section 7a states the reading's own walk
      and the order the sweep keeps, with the figures the record carries
      (`.pi/issue-loop/items/950/reader-cost-at-pool40.txt`: 163–428 ms for 821–966 processes). — done: the paragraph
      is in the diff this change lands.
