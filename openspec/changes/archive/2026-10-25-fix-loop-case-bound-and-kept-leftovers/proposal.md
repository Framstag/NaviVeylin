# Proposal — the loop bounds a case, and keeps what a killed child left

## Why

**Tracker item**: #308 — the loop's self-test holds a case with no clock, keeps a running case's output to itself,
and leaves what a stopped run found unwritten (`tools/issues/list.sh --id 308`; filed 2026-10-25 from this
incident). The citation in the code, in `loop.conf` and in `guidelines/Backlog.md` is `#308`.

The loop's run of **2026-10-24/25** stopped 1 h 56 min before its own deadline, with 15 of its 26 queued items
unworked, and its worktree carried four uncommitted files that only `--reset-worktree` could clear. Four separate
defects produced that:

1. **A self-test case has no bound.** The gate of item **#259** sat inside
   `labels#trapRemovesInProgressOnSignal` for **56 min** and returned nothing; the case's own `wait "$first"`
   never came back, and the suite has no wall clock of its own (`grep -n 'timeout ' tools/loop-selftest.sh` → no
   hit). The worker that ran that gate was then ended by its own `LOOP_CHILD_MAX_SECONDS=2700`
   (`piExit 124`, `LOOP-TIMEOUT`, 2026-10-25T02:10:50Z) — with its change uncommitted.
2. **A case's output is invisible while it runs.** Case output is written to `<scratch>/cases/<n>/out` and
   `cat`-ed only when the case ends (`tools/loop-selftest.sh:425,449`), so the gate's log held 222 `PASS` lines
   and then **~50 min of silence** (`evidence` of the archived
   `2026-10-25-fix-loop-wait-poll-granularity`): the hang named neither its case nor what it was doing.
3. **A signalled run leaves its heartbeat behind.** `on_exit` ends the child's group but nothing ends the
   heartbeat subshell `run_child` started, and the `$stop` file it loops on is only created after the wait loop
   returns — a run ended by a signal therefore leaves a subshell polling its tree for ever (measured: an
   orphan `bash tools/loop/run.sh --items 1 …` still printing `running 56m00s` long after its run had exited,
   and a second one from an older self-test still alive the next day).
4. **The leftovers a stopped run reports are lost.** `run.sh:1125-1143` detects the dirty tree, attributes it to
   the item whose branch marker it finds, records the item blocked and ends the run — and names **only**
   `--reset-worktree` as the repair. The work a killed worker left (here: the poll fix, its two cases, its two
   mutation records, its change directory) existed only as a dirty tree, and another session had to reconstruct
   it by hand from `git status` plus a `tools/loop-cases.sh` diff. A previous session had already done the same
   thing by hand for #260 (`.pi/issue-loop/items/260/leftover-from-killed-run.patch` — no tooling writes that
   name).

Root cause per defect, with its evidence:

- (1) the pool starts a case without a clock (`loop-selftest.sh:425`) — no bound exists, so the fix is the bound,
  not the case's bug. **Red on HEAD**: a nested suite whose stub case blocks
  (`sleep 600`) against `LOOP_CASE_MAX_SECONDS=2` never returns; the case to be written,
  `driver#aCaseThatBlocksIsEndedByItsOwnBound`, hangs HEAD instead of reporting.
- (2) the case's output is buffered into `cases/<n>/out` and printed after its status file appears — the
  requirement "The gate streams a suite's output while it runs" streams the *suite*, not the case inside it.
  **Red on HEAD**: `driver#aRunningCasePrintsItsOwnLines` (stub case prints, then blocks) finds no line of the
  running case on the suite's own output.
- (3) the wait's heartbeat is joined only on the normal path (`run.sh:995` `: > "$stop"; wait "$hb"`), while
  `on_exit` (`run.sh:343-357`) knows only `active_child`. **Red on HEAD**:
  `run#noProcessOfASignalledRunOutlivesIt` finds a process whose cwd is under the run's tree after the run is
  gone (the existing `labels#trapRemovesInProgressOnSignal` covers the label and the state, not the process).
- (4) the refusal and the cap-ended-child paths record *that* the tree is dirty and never write the diff
  anywhere. **Red on HEAD**: `run#treeOfACapEndedChildIsKeptAsAPatch` and
  `run#aRefusedStartKeepsTheLeftoversItFound` find no patch.

Owning requirements: `agent-issue-loop` — "The self-test runs its cases in a bounded pool, and what a case
leaves ends with it" (the pool the bound belongs in), "The gate streams a suite's output while it runs" (the
streaming the case output is missing), "A child the run ended itself leaves no commit" and "A start the worktree
refuses mid-run names the item whose work it holds" (the two paths that must keep the leftovers). Owning
guideline section: `guidelines/Backlog.md`, section "7. The loop".

- **Root cause, one mechanism each**: an absent per-case clock, a case's output written to a file instead of the
  suite's stream, `on_exit` knowing the child but not the heartbeat, and a dirty tree recorded as text rather
  than kept as a patch.
- **Fix shape**: the pool bounds each case and ends its group; a running case's lines reach the suite's output
  with the case's name in front; `on_exit` ends the heartbeat by pid; the two dirty-tree paths write
  `<item dir>/leftover-from-killed-run.patch` (tracked changes plus untracked files) and name the path.
- **Scope note**: the four defects were bundled into one change by the operator's decision. Each keeps its own
  root cause, its own case and its own mutation record, so a later split is mechanical; the loop's rule "one fix
  per item" is not weakened for anything that enters the loop.
- **Detected by**: the archived change `2026-10-25-fix-loop-wait-poll-granularity` and this session's repair,
  both recorded in `ki_processing_failures.log` (2026-10-25 entry).

## What Changes

- `tools/loop-selftest.sh` — the pool bounds every case with a watcher started beside it: `LOOP_CASE_MAX_SECONDS`
  (default `900`, validated like the profile's other bounds), measured from the case's own start, and a case that
  exceeds it has its process group ended (the engine's own `loop_end_group`) and is answered by the bound —
  `FAIL <case> (exceeded <n> s)`, with the case's own log kept — while the suite runs the cases queued behind it.
  The watcher is a job of the suite's shell and not a member of the case's group (it ends that group, so it cannot
  be in it), and all three of its standard descriptors are `/dev/null`: it outlives every case until it fires, so it
  must not hold a stdout a caller captured in `$( … )`.
- `tools/loop-selftest.sh` — a running case's output is relayed to the suite's own output as it arrives, each line
  prefixed with the case name, and appended unaltered to the case's `out` file (printed again, with a header, only
  when the case failed). The case runs in the **foreground** of its subshell and its status is written the moment it
  returns; the relay reads a `fifo` of the case's own: a pipeline would make the subshell wait for the relay's EOF,
  which a case's own descendants can hold open for ever.
- `tools/loop/run.sh` — `run_child` sets a global for the heartbeat's pid and clears it when the wait joins it;
  `on_exit` ends it with `loop_end_pid`, so a run that takes `INT`/`TERM` leaves no process polling its tree.
- `tools/loop/run.sh` — both dirty-tree paths (the cap/deadline-ended child, and the refused start) write
  `<item dir>/leftover-from-killed-run.patch` before the run moves on: `git diff HEAD` (staged and unstaged — a
  refused `end-item` leaves its work staged), a `diff --no-index` hunk per untracked file, and the ignored files
  under `LOOP_UNTRACKED_RECORD_DIR` (new profile key; this project sets `openspec/changes`, whose unarchived
  changes are exactly what the incident lost). Every other ignored path, and any file above 256 KiB, is *named* in
  the patch's header instead of copied. The child's transcript carries a `LOOP-LEFTOVERS:` line naming the file,
  and the refused start's stop reason names it too. Nothing is committed and nothing is discarded: the tree is
  still kept exactly as the child left it.
- `tools/loop-cases.sh` — the wait for a stub child to appear is the **host's** bound, not a 20 s property of the
  loop: one helper (`wait_for_stub_child`, 60 s at 0.1 s per tick) replaces the eleven copies of that wait, four of
  which a loaded host made read as "the stub child never started" on 2026-10-25 (two Gradle compiles of a second
  session on this host) — the same class of error this change is about, a host fact reported as a defect.
- `guidelines/Backlog.md`, section "7. The loop" — the case bound, the streamed case line, the heartbeat that
  ends with the run, and the patch a stopped run keeps.

Additive in behaviour, one bound and one artifact added: no requirement is removed, no on-disk format changes,
no run's contract changes. Rollback is reverting the four edits in `tools/loop-selftest.sh` and
`tools/loop/run.sh`; the written patches are ordinary files a reader may delete.

Surface, counted before the plan is fixed:
`grep -rn 'cases/.*/out\|loop_run_shell_case\|active_child\|leftover\|LOOP_CASE_MAX\|fifo.ready' tools/loop-selftest.sh
tools/loop/run.sh tools/loop/lib.sh tools/loop/loop.conf tools/loop-cases.sh` → 5 call sites in 2 engine files
(`loop-selftest.sh:425,449`, `run.sh:343-357,995,1125-1143`) plus 11 stub-readiness waits in the cases. Cases
rewritten by nothing: the three cases that watch the pool (`driver#theSuiteRunsItsCasesInAPool`,
`driver#aCaseThatLeavesAProcessBehindIsSweptWithIt`, `driver#aSecondSuiteInOneRepositoryIsRefused`) keep their
assertions, and the eleven readiness waits keep their assertions and lose only their host-tight bound.

surface: 0 cases rewritten, 6 cases added, 1 case helper added, 11 host-tight waits widened, 6 mutation records
added (`62.1`–`62.6`), 1 guideline section updated, 1 profile key added.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop`: three requirements added — "A self-test case is held to a bound of its own", "A case's own
  output reaches the suite's log while it runs", "A run that takes a signal takes its heartbeat with it" — and
  two modified — "A child the run ended itself leaves no commit" and "A start the worktree refuses mid-run names
  the item whose work it holds" — each with the patch it must keep added and its shipped scenarios repeated
  verbatim.

## Impact

- `tools/loop-selftest.sh` — the pool's case launch and output handling.
- `tools/loop/run.sh` — `on_exit`/`on_signal` and `run_child`'s heartbeat, plus the two dirty-tree paths.
- `tools/loop/loop.conf` — `LOOP_CASE_MAX_SECONDS`, with the profile's other bounds.
- `tools/loop-cases.sh` — one helper added (`wait_for_stub_child`) and six cases, each named above with its
  declaration in the first line of its body:
  `driver#aCaseThatBlocksIsEndedByItsOwnBound`, `driver#aCaseInsideItsBoundKeepsItsOwnVerdict`,
  `driver#aRunningCasePrintsItsOwnLines`, `run#noProcessOfASignalledRunOutlivesIt`,
  `run#treeOfACapEndedChildIsKeptAsAPatch`, `run#aRefusedStartKeepsTheLeftoversItFound`.
- `tools/loop-mutations.sh` — records `62.1`–`62.6`, one per new invariant (the bound, the verdict inside the
  bound, the streamed line, the heartbeat's end, the cap-ended patch, the refused-start patch).
- `PORTING.md` — the two new profile keys named in the list of files a foreign project edits.
- `guidelines/Backlog.md` — section "7. The loop".
- `openspec/specs/agent-issue-loop/spec.md` — the five deltas, merged at archive.
- No Android module, no Gradle build, no native code, no on-disk format of the run's state is touched: the delta
  is the loop's shell and its own cases, so the gate owes no compiled suite for it.
