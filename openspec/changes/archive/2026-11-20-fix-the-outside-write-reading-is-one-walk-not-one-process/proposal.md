# Proposal: fix-the-outside-write-reading-is-one-walk-not-one-process

## Why

**Root cause.** The reader that names who stands in a tree, `loop_pids_under` (`tools/loop/lib.sh:717`), answered
the question with a `readlink` of every `/proc/<pid>/cwd` on the host — a fork per process — and
`outside_write_seen` (`tools/loop/run.sh:1611`) takes its `LOOP-OUTSIDE-WRITE-ALIVE:` field from that walk **after**
the child's own tree has been read. The field is therefore an answer about the moment the walk *ends*, and at the
pool the suite actually uses that moment is twelve to fifteen seconds after the difference was read: the writer the
difference named had exited before the walk reached it, so the field printed `(none)` while a shell of the child's
tree stood in the project for eight seconds. The reading was honest and useless, and the case that pins it read as
"the shell was never placed" — with `attributed=none` beside it, the one shape the item's own case cannot tell from
an arm that is broken.

**Evidence** ⊨:

- At base `0b62e579` (2026-11-19), `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced` at a pool of forty instances:
  **0 of 40 passed, 40 of 40 failed**, every instance on `the shape was found: ' 1586171 ' not found in:
  LOOP-OUTSIDE-WRITE-ALIVE: (none) · attributed=none` (`.pi/issue-loop/items/950/pool-HEAD-40-0b62e579.txt`).
  The same case passes focused on the same tree, which is the shape of a load-sensitive read and not of a broken arm
  — exactly what the item records.
- The earlier pool, at the base this change was first measured against (`7ecab8e6`, 2026-11-17): **0 of 40 passed,
  40 of 40 failed**, every instance on the same failure text (`.pi/issue-loop/items/950/pool-HEAD-40.txt`; the
  instance logs are `.pi/issue-loop/items/950/scratch/head-pool40/`).
- The walk's own cost, timed on this host while a pool of forty instances ran (three readings): **821 to 966
  processes** carried, a `readlink` per process **12 206 ms to 14 968 ms**, one `find` pass over `/proc` **163 ms to
  428 ms** (`.pi/issue-loop/items/950/reader-cost-at-pool40.txt`, probe
  `.pi/issue-loop/items/950/scratch/probe-reader-cost.sh`). The writer the case stages stands eight seconds, so a
  twelve-second walk answers about a moment that has passed.
- With the fix, the same case at the same pool on base `0b62e579`: **40 of 40** passed twice
  (`.pi/issue-loop/items/950/pool-fix-40-0b62e579.txt`), and 40 of 40 and 16 of 16 on the tree of the first attempt at
  its own base (`.pi/issue-loop/items/950/pool-after-40.txt`). The reading now states its own cost in the transcript —
  `LOOP-OUTSIDE-WRITE-COST: procs=1176 attributed_ms=4142 standers_ms=505` — so a transcript that prints `(none)`
  says how far from the difference its answer arrived (`.pi/issue-loop/items/950/pool-after-40.txt`).
- The item's own line is that field: a full fourteen-case-pool leg recorded `the reading names the shell that wrote
  among the processes standing in the project — the shape was found: ' 2330843 ' not found in:
  LOOP-OUTSIDE-WRITE-ALIVE: (none) · attributed=none` (issue #950's body), identical to the pool's failure text.

**Consequence** ⊨: a difference is recorded as one the run can place only when a process of the child's tree stood
beside it (`outside_write_decides`, `tools/loop/run.sh:1596`), and that process is read out of this field
(`tools/loop/run.sh:2466`). An enumeration that answers `(none)` because its own walk outlived the writer therefore
attributes a write the child made to nobody: the arm records the difference and blocks nothing, and the item's
evidence reads as an arm that cannot find a process rather than as a walk that arrived late.

## What changes

One mechanism, in five places — one walk of `/proc` per reader, and no reader that walks it twice:

1. `tools/loop/lib.sh`: `loop_pids_under` answers in **one pass** over `/proc` — `find /proc -maxdepth 2 -name cwd
   -lname …` — instead of a `readlink` per process, and matches the directory by its **literal** path
   (`loop_glob_escaped`), because `find`'s `-lname` reads `*`, `?`, `[`, `]` and `\` as its own metacharacters and a
   tree whose name carries one would be answered by nothing at all.
2. `tools/loop/run.sh`: `pid_ppid` reads `/proc/<pid>/stat` with the shell's own `read` instead of a `sed` per hop;
   `pid_left_the_worktree` is handed the pids standing in the worktree, which the reading already took in its first
   pass, instead of asking the kernel for an ancestor's `cwd` once per hop; and `outside_write_seen` prints one
   `LOOP-OUTSIDE-WRITE-COST:` line, carrying the host's process count and what each half of the reading cost, from a
   millisecond clock taken around each half (`loop_now_ms`, `loop_proc_count`).
3. `tools/loop/tests/loop-cases.sh`: the case that pins the reading asserts the cost line, and a new case,
   `clean#aTreeWhoseNameIsAPatternIsStillRead`, stages a process in a directory named `a[1]*x` and asserts that the
   reader names it while the unescaped pattern names nothing
   (`.pi/issue-loop/items/950/glob-escape-probe.txt`: the raw pattern finds `(none)` while the escaped one and
   `loop_pids_under` both find the pid).
4. `tools/loop/tests/loop-mutations.sh`: record 12.11 quoted the line task 2 changes (`pid_left_the_worktree "$pid"
   "$LOOP_WORKTREE"`), so it is re-quoted at the line as it now stands (`"$in_worktree"`); a record that no longer
   applies is `MUTATION-PATTERN-STALE` and a red gate
   (`.pi/issue-loop/items/950/mutation-12.11-b63de90b.log`).
5. `guidelines/Backlog.recipes.md` section 7a: the paragraph that stated the mechanism this diff replaces — the sweep as a
   `readlink` for every process on the host, the writer gone by the reading's end — states the tree's own: one
   `find /proc` pass, the sweep as the expensive half the order is kept for, with the figures the record carries
   (`.pi/issue-loop/items/950/reader-cost-at-pool40.txt`: 163–428 ms for 821–966 processes).

surface: one case extended (`run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`, +1 assertion, +1 declared path), one case added
(`clean#aTreeWhoseNameIsAPatternIsStillRead`, 3 assertions), no case rewritten; one mutation record re-quoted (12.11);
one guideline paragraph corrected (`guidelines/Backlog.recipes.md` section 7a); no table row, no bound. — the reading's own callers are `lib.sh`'s `loop_end_pids_under`,
`run.sh`'s `outside_write_pids` and `outside_write_seen`, and the cases that call it directly. `tools/loop/shared.tsv` needs no row: `loop_pids_under
lib.sh run.sh` is the row the reader already had, and the three names this change adds are assigned and read inside
one file each. `tools/loop/layout.tsv`'s line bounds still hold — `driver#noEngineFileExceedsItsBound` passes
(`.pi/issue-loop/items/950/driver-group-b63de90b.log`).

## Open decisions

None. The item names both halves as candidates and the measurement decides between them: the enumeration's own walk
outlived the writer (12 206–14 968 ms against an eight-second shell), so the case's assertion was right and the
reading was late. The case is unchanged in what it pins.
