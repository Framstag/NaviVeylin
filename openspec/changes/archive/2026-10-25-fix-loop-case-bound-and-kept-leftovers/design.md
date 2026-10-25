# Design — the loop bounds a case, and keeps what a killed child left

Four defects, four decisions. Each decision lists the alternatives the root-cause evidence eliminated and why it
is impossible, as `guidelines/Build.md` §2a and the change's own config require for a fix-shaped change.

## D1 — Where the case bound lives

**Chosen**: the pool in `tools/loop-selftest.sh` starts every case inside its own process group (it already runs
each case as a subshell, `loop-selftest.sh:425`) and records `started=$(date +%s)` beside it. While the pool waits
for that case's `status` file it compares the clock, and a case past `LOOP_CASE_MAX_SECONDS` is ended with
`loop_end_group` (`tools/loop/lib.sh`, the engine's own helper: `TERM` to the group, a 5 s grace, then `KILL`),
recorded `FAIL <case> (exceeded <n> s)`, and the pool moves to the next case. The bound is a whole-second clock
read on a wake-up the pool already performs — it adds no poll of its own.

**Alternatives eliminated**:

- `timeout(1)` around the case's subshell: GNU `timeout` signals the *process* it started, not its group. The
  hang this change fixes came from a case that had started a whole loop engine; a process-only bound would leave
  that engine and the case's tree alive, which is the second half of the incident (the boundary between a case's
  processes is a group, and the engine already has exactly one helper for that).
- A bound on the suite as a whole: it says nothing about *which* case hung, and it turns a bound failure into
  "the suite timed out" — precisely the fact that took a session to reconstruct. Measured cost of not knowing:
  56 min of a 480 min budget, then another 56 min re-diagnosing it.
- Letting each case bound itself (`timeout … bash -c 'case …'` inside the case): a case's own body is what hangs;
  a bound a case can fail to reach is not a bound. The pool is the only layer that exists before the case runs.
- A profile-free hard-coded `900`: rejected for the same reason the other bounds are configurable — the suite that
  measures slow hardware is the one that needs to say so. The default is validated like `LOOP_CHILD_MAX_SECONDS`
  (empty, non-numeric or `0` → the default).

**Risk**: a case whose *legitimate* work exceeds the bound would be failed. Measured: the suite's slowest case is
`run#recordsAFailedReviewOnTheBranch` at 9–15 s, and the whole suite ran in 273 s for 223 cases on 2026-10-25
(the pool is not the bound's consumer, and `--jobs` is what shortens the total). 900 s is 60× the slowest case,
and a case that needs more says so by raising the profile key. Second risk: ending a case's group ends the loop
engine that case started — which is what the suite's own sweep already does for a case's leftovers
(`driver#aCaseThatLeavesAProcessBehindIsSweptWithIt`), so no new contract is created.

## D2 — How a running case's output reaches the suite's log

**Chosen**: the pool relays the case's own stdout/stderr through one prefixed writer: each line is appended to the
case's `<dir>/out` **unmodified** and printed to the suite's own output as `<case>| <line>`. The case's exit
status still travels through `<dir>/status`, written by the case's subshell as today, so the pool's "ended by its
own record" rule and the bound's pid both keep working. The relay is started as a member of the case's process
group and is therefore ended by D1's `loop_end_group`.

The shape is a **fifo and a foreground case**, not a pipeline: the suite opens `$dir/stream` (a fifo of the case's
own), the relay is the fifo's reader, and the case runs in the foreground of its subshell so its `status` is
written the moment it returns. A pipeline (`( case ) | relay`) makes the subshell wait for the relay to reach EOF,
and a case's own descendants inherit that pipe — measured while building this change, the whole `driver#*` group
stood at its 900 s bound because each driver case runs a nested suite whose cases left the outer relay's pipe open.
The case's own stdout is the suite's stdout only through fd 3, which the relay alone writes, and the bound watcher
of D1 gets `/dev/null` for all three standard descriptors: it outlives every case until it fires or is ended, so it
must not hold a suite's stdout either — a suite captured in `$( … )` (three of the driver's own cases) never saw
EOF while a watcher held the write end of that pipe.

The relay is given a bounded grace **before the case's status is written**, inside the case's own subshell: the case
writes its transcript into the fifo, and a relay killed first loses whatever had not yet been read — measured while
building this change, a failed case's message never reached the log and its `out` file was empty, so the failure was
a `FAIL` line with nothing under it. The grace is 1 s and lives in the subshell and not in `finish_case`, because a
case whose descendants hold the fifo open (the sweep case leaves exactly one) never gives the relay EOF, and a wait
in `finish_case` would sit between a case's end and the sweep that ends what it left — measured, that made
`driver#aCaseThatLeavesAProcessBehindIsSweptWithIt` see its leaked process still running.

**Alternatives eliminated**:

- Keeping the buffered `<dir>/out` and printing it when the case ends (today's shape, `loop-selftest.sh:449`): the
  incident's own measurement — 222 `PASS` lines, then ~50 min of nothing, with the hung case's name nowhere in
  the log.
- `tail -f` on `<dir>/out` from the suite: introduces a wake-up poll the project's rules dislike, doubles the
  writes, and races at the case's end (the file is unlinked between the sweep and the read).
- A second "live view" command a person runs beside the suite: the log the *gate* tees is what a reader has
  after the fact, and the incident's hang was read from that file hours later — an opt-in view would not have
  named it.

**Risk**, named because the fix must not reintroduce the hang: a case's descendant that inherits the relay's pipe
keeps it open, so the relay's `read` never sees EOF. The pool must therefore never wait for the relay to finish:
it waits on the status file (or ends the group), exactly as it waits today. A relay that outlives its case is
swept with the case's own leftover processes, which the suite already asserts.

## D3 — Ending the heartbeat with the run

**Chosen**: `run_child` sets a global `active_hb` when it starts the heartbeat subshell and clears it after
`wait "$hb"`; `on_exit` (`tools/loop/run.sh:343-357`) ends it with `loop_end_pid` before it unlocks the state.
The pid is a number, and `loop_end_pid` already treats a gone pid as nothing to do, so the trap path and the
normal path can both call it.

**Alternatives eliminated**:

- Creating the wait's `$stop` file in `on_exit` and letting the heartbeat see it: `$stop` is a `run_child` local
  derived from the phase's report path, so the trap path would need a global for it anyway — the same change, with
  the extra dependence on the heartbeat's own 0.05 s poll. Ending the pid is immediate and needs nothing of the
  heartbeat's loop.
- Sending `TERM` to the run's own process group: the run's shell is in it, and the run must still record the
  interrupted item, remove the label and unlock the state.
- Relying on the suite's sweep of the case tree (`loop_pids_under`): that is what ended yesterday's orphan, and it
  only runs for a case that *ended*, in a suite, in a tree; a run ended by an operator's `kill` has none of those.
  Measured: one orphan from a self-test of the previous day was still polling the next afternoon.

**Risk**: a heartbeat pid reused by another process between the clear and the trap would be ended wrongly. The
window is the case's own wait (the pid is cleared immediately after `wait "$hb"` returns), and `loop_end_pid`
sends `TERM` to a single pid, not a group; the risk is accepted and noted, and the alternative (a pipe the run
reads) is a larger change than this defect justifies.

## D4 — How the leftovers of a stopped run are kept

**Chosen**: the patch `.pi/issue-loop/items/<n>/leftover-from-killed-run.patch`, written by `run.sh` in both
paths that stop on a dirty tree: the cap- or deadline-ended child (`run.sh:975-990`, before the next item) and
the refused start (`run.sh:1125-1143`, before the run ends). The patch carries `git diff` for the tracked
changes, and one `diff --no-index /dev/null <file>` hunk per untracked file. Ignored files under the profile's
`LOOP_UNTRACKED_RECORD_DIR` (new key, default empty; this project sets `openspec/changes`, which is gitignored)
are included too — an unarchived change directory is exactly what the incident lost — and every other ignored path
in the tree is *named* in the patch's header instead of copied, so the patch never picks up a build directory; a
file above 256 KiB is named too. The tracked half is `git diff HEAD` and not `git diff`: a refused `end-item`
leaves its work **staged** (the commit is what failed), and `git diff` shows neither a staged addition nor a
staged deletion — measured on `run#aRefusedStartKeepsTheLeftoversItFound`, whose `demo-fix.txt` was staged and
absent from the first patch. The child's transcript carries the `LOOP-LEFTOVERS:` line naming the file, beside the
`LOOP-TIMEOUT` and `LOOP-ENDED` lines the run already appends there, and the stop reason names it on the refused
start's path.

**Alternatives eliminated**:

- Committing the leftovers on the item's branch: impossible, the shipped requirement "A child the run ended
  itself leaves no commit" says the branch is kept as it stood, and a commit would make a killed attempt read as
  earned work in the landing accounting.
- `git stash`: it names nothing in the item's evidence, is not attributable after the run's own record is
  replaced, and the incident showed the real cost — a session reconstructing a killed worker's work by hand from
  `git status` and a diff. A previous session had already done that by hand for #260, which is where the file
  name comes from.
- `git diff` alone: the incident's change directory was *untracked* (`openspec/changes/*/` is gitignored), and
  `--reset-worktree` would have discarded it unseen. A patch of the tracked diff only would have kept the fix and
  lost every artifact that proves it.
- `.pi/issue-loop/items/<n>/` as the only home, with the tree left as is and no patch: the item's directory is
  per item, and the refused-start path may find leftovers of an item whose directory this run never touched — the
  patch is written into *that* item's directory, which is why the branch marker's item is resolved first.
- An engine-side hard-coded `openspec/changes`: the engine names no project fact of its own (the suite's own case
  for that rule refuses it), so the record glob is a profile key.

**Risk**: a very large change root inflates the patch. Bounded by the record glob and by `git status`'s own list;
the untracked files a worker leaves are text the run already reads, and a patch is plain text a reader may
`--exclude` from review if it is large.

## Process model

No threads and no new long-lived process. The pool owns one process group per case (D1) and one relay inside it
(D2); the engine owns the heartbeat (D3) and writes files (D4). Each bound is a whole-second clock read on a
wake-up that already happens, and each end is the engine's existing `loop_end_group` / `loop_end_pid`, so a
process the loop cannot reach is not introduced.

## Verification

- Six cases, each red on HEAD before its fix and green after (the one exception is the bound's guard, green on
  HEAD by construction and red under its mutation): `driver#aCaseThatBlocksIsEndedByItsOwnBound`,
  `driver#aCaseInsideItsBoundKeepsItsOwnVerdict`, `driver#aRunningCasePrintsItsOwnLines`,
  `run#noProcessOfASignalledRunOutlivesIt`, `run#treeOfACapEndedChildIsKeptAsAPatch`,
  `run#aRefusedStartKeepsTheLeftoversItFound`.
- Six mutation records `61.1`–`61.6` in `tools/loop-mutations.sh`: one per invariant (the bound, the verdict of a
  case inside its bound, the streamed line, the heartbeat's end, the cap-ended patch, the refused-start patch),
  each red under mutation and green after restore.
- The stub-readiness wait of the cases that block a stub child is a **host** fact with a wide margin
  (`wait_for_stub_child`, 60 s), not the 20 s four of them carried: two Gradle compiles of a second session on
  this host pushed an engine's first child past 20 s and `run#childOutlastingTheWallClockIsBlocked` read that as
  "the stub child never started" — a host's slowness reported as a defect in the engine, which is the same class
  of error this change is about.
- Structural checks, because no compiled file is touched: `bash -n` on the edited shell, `tools/loop-mutations.sh
  --check`, `tools/loop-selftest.sh --from-git <base>`, `tools/check-portability.sh`, `tools/check-portability-selftest.sh`,
  `tools/check-doc-routes.sh`, `tools/check-todo-references.sh`, `tools/check-openspec-hygiene.sh`.
- The gate of the change itself (`./tools/loop/gate.sh`), whose loop suites the delta pulls in, plus its tallies.
- No on-device measurement: no visual or user-visible behaviour changes — the deliverables are the suite's own
  log, the engine's process lifetime and a file beside the item.
