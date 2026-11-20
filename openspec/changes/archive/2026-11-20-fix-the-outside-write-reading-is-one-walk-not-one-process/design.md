# Design: fix-the-outside-write-reading-is-one-walk-not-one-process

## Root cause

`outside_write_seen` (`tools/loop/run.sh:1611`) reads the child's own tree first and the host's standing processes
second, which is the order issue #288 put there; the second half is `outside_write_pids` → `loop_pids_under`
(`tools/loop/lib.sh:717`), and that answered it with a `readlink` of every `/proc/<pid>/cwd` on the machine — one
fork per process. At a pool of forty instances the host carried 821 to 966 processes, and the walk cost 12 206 ms to
14 968 ms (`.pi/issue-loop/items/950/reader-cost-at-pool40.txt`). The case's writer stands eight seconds. So the
field the case asserts on (`LOOP-OUTSIDE-WRITE-ALIVE:`) is printed twelve to fifteen seconds after the difference
was read, and it answered `(none)` — not because no process stood there, but because the one that did had exited
before the walk reached its entry in `/proc`.

The item names the other candidate — a case asserting a pid it learned before the reading ran — and the measurement
refuses it: the case reads the pid the staged shell wrote *after* the run ended
(`tools/loop/tests/loop-cases.sh:9756`) and compares it with a transcript line the run wrote while the shell lived,
so the pid is the same process either way. What moved was the reading, not the pid.

## The single fix

1. **One walk.** `loop_pids_under` asks `find /proc -maxdepth 2 -name cwd -lname <pattern>` for the whole host in one
   process: `-lname` matches the link's target, which is the string `readlink` would have printed — for the directory
   itself, for a path under it, and for both of those with the kernel's ` (deleted)` suffix, which is what a killed
   suite's leftovers print. Measured: 163 ms to 428 ms for the same 821–966 processes
   (`.pi/issue-loop/items/950/reader-cost-at-pool40.txt`).
2. **Literal names.** `find`'s `-lname` takes a pattern, so a directory whose name carries `*`, `?`, `[`, `]` or `\`
   would match something else and the tree's processes would be reported by nothing. `loop_glob_escaped` escapes
   those five characters; the guard has teeth, measured: the raw pattern finds `(none)` while the escaped pattern and
   `loop_pids_under` both find the staged pid (`.pi/issue-loop/items/950/glob-escape-probe.txt`).
3. **The attribution's own forks.** `pid_ppid` parsed `/proc/<pid>/stat` with a `sed` per hop, and
   `pid_left_the_worktree` asked for an ancestor's `cwd` with a `readlink` per hop. Both are gone: the `stat` line is
   read by the shell's own `read` and split in the shell, and the ancestors' answer is the set of pids standing in
   the worktree, which the reading already took in its first call. That half is `attributed_ms` in the new line:
   3 823 ms to 4 691 ms at the same pool.
4. **What it cost, printed.** `outside_write_seen` prints one `LOOP-OUTSIDE-WRITE-COST: procs=<n> attributed_ms=<n>
   standers_ms=<n>` line beside the answer: the host's process count (`loop_proc_count`, a glob expansion and no
   fork) and the milliseconds each half took (`loop_now_ms`, `date +%s%3N` with a whole-second fallback for a host
   whose `date` lacks the field). A transcript that prints `(none)` then says how far from the difference its own
   answer arrived, which is the distinction the item's case could not make.
5. **The records the delta invalidates.** `tools/loop/tests/loop-mutations.sh` record 12.11 quoted
   `pid_left_the_worktree "$pid" "$LOOP_WORKTREE" || continue`, which step 3 replaces with `"$in_worktree"`: a
   record whose pattern no longer matches is `MUTATION-PATTERN-STALE`, and the gate of the previous attempt failed on
   exactly that (`tests=732 failures=1 errors=1`, not retained — its log is
   `.pi/issue-loop/items/950/gate-output.txt`). The record is re-quoted
   at the line as it now stands, and its case is untouched — `--check` reports every selected record applying
   (`all 430 cases passed — every selected record applies to the file it names`, 2026-11-20), and `--only 12.11`
   goes red for its own reason and restores green
   (`.pi/issue-loop/items/950/mutation-12.11-b63de90b.log`).
6. **The escaping's own case.** The delta's third clause — a directory whose name carries a metacharacter is still
   read — had no case: the item's case stages no such tree, and a scenario that names a case which does not assert
   it is the finding a reader writes next. `clean#aTreeWhoseNameIsAPatternIsStillRead` stages a process in `a[1]*x`,
   asks the reader for it, and asks `find` for it with the name unescaped — which finds nothing, so the case fails on
   the clause and not on the reader (`.pi/issue-loop/items/950/focused-cases-b63de90b.log`).

## Blast radius

`loop_pids_under` has three callers besides the reading — `loop_end_pids_under` (`lib.sh`, the cap's sweep of a
child's leftovers), `outside_write_pids` (`run.sh`, the note's list of other standers) and the cases that call it
directly — and all of them asked the same question and get the same answer: a pid per line, self and parent
excluded, order not promised (the walk's `sort` fixes an order no caller reads). The
`pid_left_the_worktree` signature gains an argument and has one caller. `pid_ppid` keeps its output contract, one
parent pid per line, and answers nothing when the file is gone or unparseable. No configuration, no schema, no
archive format moves.

Beside the engine, three records move with it: a case is added to `tools/loop/tests/loop-cases.sh` (and the item's own
case gains one assertion and one declared path), mutation record 12.11 is re-quoted, and the guideline paragraph that
states the old mechanism is corrected (`guidelines/Backlog.recipes.md` section 7a). Nothing else in the suite names the
reader's cost.

## Rollback

Revert the five files (`tools/loop/lib.sh`, `tools/loop/run.sh`, `tools/loop/tests/loop-cases.sh`,
`tools/loop/tests/loop-mutations.sh`, `guidelines/Backlog.recipes.md`). The reading's fields go back to two lines,
`LOOP-OUTSIDE-WRITE-COST:` disappears, and the case's two new assertions are what fails first — which is the same red
the item records, so a revert is read as "the walk is slow again" rather than silently.

## Alternatives the evidence eliminated

- **A longer-lived writer in the case** (raise the staged shell's `sleep` from 8 s). It hides a twelve-second walk
  behind a longer stage, makes every instance of the case slower, and leaves the run's own reading late for the real
  writers it exists to name; the reading is the defect, not the pin.
- **Reading the sweep before the child's tree.** Issue #288 measured this direction and refused it: the sweep is the
  expensive half, and a difference read after it records what the run *can* place as what it cannot (see the archive
  `2026-10-24-fix-loop-attribution-is-read-before-the-sweep`).
- **Keeping `readlink` per process and only printing its cost.** Then the field still says `(none)` about a past
  moment, and the line would state a cost nobody acts on — the item's symptom with a number beside it.
- **A `pgrep`-per-tree sweep instead of a `/proc` walk.** Issue #288 measured a host-wide sweep at 1.0 s for 711
  processes on an idle host, which is the same order as this change's 163–428 ms under load; nothing is bought by
  trading one walk for another reader of the same question.

## What stays, and why

`attributed_ms` is 3 823–4 691 ms at that pool and is **not** reduced here: the half that printed `(none)` is the
second one, and the attribution walks only the child's own tree — its cost is `loop_tree_pids`' `pgrep` per level,
a separate reader with a separate fix. It is printed rather than fixed so the next item can price it; naming it here
keeps this change to one mechanism.

One guideline section states the reading's cost and its walk, and this change updates it in the same pass:
`guidelines/Backlog.recipes.md` section 7a's paragraph carried the mechanism this diff replaces — a `readlink` for every
process on the host, 1.0 s for 731 with #288's measurement, the writer gone by the reading's end — and now states
the tree's own: one `find` pass, 163–428 ms for 821–966 processes
(`.pi/issue-loop/items/950/reader-cost-at-pool40.txt`). `guidelines/Backlog.md` section 7 indexes the loop's
procedure, and `guidelines/Backlog.recipes.md` sections 7a–7e carry the child, the bounds, the gate, the refusals
and the leftovers — the sweep and its cost are named in section 7a, and section 7b names `loop_pids_under` as the cap's
leftover test. The requirement that owns this rule is the spec's, so the delta below and that section 7a paragraph are
the whole of the documentation this change owes.
