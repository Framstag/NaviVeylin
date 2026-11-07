# Evidence — `fix-loop-attribution-is-read-before-the-sweep` (issue #288)

Base: main's tip `0f366176`, branch `fix/issue-288-the-writer-is-read-before-the-sweep`, worktree
`.pi/worktrees/i288-final` (isolated — a peer session was writing the project tree at the time), 2026-10-24.

## Red on the base

- `red-HEAD.log` — the case the item was filed on, in a detached worktree at main's tip `9e77cd69`, clean:
  `bash tools/loop-selftest.sh --case run#aWriterTheRunCanPlaceBlocksTheItem` → **3 of 3 red**
  (`a write the run can place in the child's own tree decides the item: expected 'blocked', got 'done'`).
  The item reported a flip with the engine tree's own dirt; measured here with the untracked probe file the item
  used (`tools/issues/zz-dirty-probe`), the case is red **4 of 4** with and without it — the flip is the margin,
  not a second cause (`sweeps.log`, `timestamps.log`).

## Why, measured

- `sweeps.log` — the base's two sweeps, instrumented once and reverted. Sweep 1 (the reading, tick+0.07 s) lists
  nine pids whose working directory is under the project; sweep 2 (1.1 s later, the one the attribution tested)
  lists **none** of them. `attributed` was therefore empty whatever the timing: the mechanism never fired.
- `timestamps.log` — where the second went: the difference was read 0.99 s after the write, the answer 2.19 s
  after it, and the writer (a `sleep 2` the child started) died in between.
- `sweep-cost.sh`, `sweep-cost.log` — the reason the order matters: `loop_pids_under` 0.96 s for 731 processes,
  1.04 s through the command substitution the attribution used.
- `mark.log` — what the narrow rule marks, per candidate, in a passing run of the writer's case: the child's own
  shell (cwd = the project, nothing above it in the worktree) is **not** marked; the child's `sleep 2`
  (`stands=yes left-worktree=yes`) **is**. `MARKED 3509271`.
- `peer-probe.log` — why the rule has to be narrow: with record 12.11 applied (the "left the worktree" test
  dropped), `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` fails — the run's own child chain stands in
  the project by construction, so a peer's write decides an item whose work was never touched.

## Green on this tree

- `green-case.log` — the writer's case, 5 of 5.
- `green-peer-case.log` — the peer case, 5 of 5.
- `green-neighbours.log` — `run#childWriteOutsideTheWorktreeIsNoted`, `run#strictOutsideWriteBlocksTheItem`,
  `run#strictOutsideWriteDiscardsTheReviewRound`, `run#dirtyBaseRefusesTheMergeAndNamesThePath`, 4 of 4.
- `revert-check.log`, `revert-check.md` — `bash tools/loop-mutations.sh --only "12.3 12.4 12.9 12.10 12.11 12.12"`:
  `red-when-mutated: 6 · not-noticed: 0 · restore-failures: 0`.

## The gate, run twice, and its two reds attributed

`bash tools/loop/gate.sh --base 0f366176` was run twice on this tree. The machine carried four other loop runs
while they ran (load average 5.3 / 7.6 / 11.7 on 16 cores), and each run was red exactly where the other run was
green:

| suite | `gate.log` (run 1) | `gate-attempt2.log` (run 2) | alone |
|---|---|---|---|
| `tools/loop-selftest.sh --from-git 0f366176` | `all 210 cases passed` | `2 of 210 cases failed` | the two, one at a time below |
| `bash tools/loop-mutations.sh --check` | `all 134 cases passed` | `all 134 cases passed` | — |
| `tools/loop-mutations-selftest.sh` | `2 of 34 cases failed` | `all 34 cases passed` | `all 34 cases passed` |
| `tools/check-doc-routes.sh`, `tools/check-doc-routes-selftest.sh`, `tools/check-todo-references.sh`, `tools/mutate.sh … --list` | passed | passed | — |

The per-suite tallies the gate sums are copied beside this file (`bash_tools_loop-mutations.sh_--check.xml`,
`tools_loop-mutations-selftest.sh.xml`, `tools_loop-selftest.sh_--from-git_0f366176.xml`, …); the gate's own sum
line, `LOOP-GATE: tests=378 failures=2 errors=0`, is **not retained** as an artifact of its own — it is the sum of
those tallies, not a suite.

**Each red, attributed:**

- `loop-mutations-selftest.sh`, run 1: the two checks `a file set runs the records its file owns` and `a git diff
  names the records its file owns` (each `expected [0] got [1]`, i.e. the harness run under a copy exited 1). Green
  in run 2 and alone (`loop-mutations-selftest-standalone.log`); the same file-set run reproduced by hand on this
  tree ends `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0`. Load-dependent, and its subject is the
  harness's own guard, not this delta.
- `loop-selftest.sh`, run 2: `run#prescreenAnnouncesItselfBeforeItReturns` and
  `run#childOutlastingTheWallClockIsBlocked` (`flakes-attributed.log`). The first is the subject of its own open
  item, #287 ("the pre-screen announces no phase") — it fails alone on main's tip too. The second is green alone on
  this tree. Neither is this delta's path, and run 1 has both green.

## What is not asserted

- **A child that writes into the project's tree from its own standing process** (nothing above it in the
  worktree) is recorded and does not decide the item — the price of the narrow rule, stated in `proposal.md`
  ("Not in this change"). No case pins it; the safe direction is `--strict-outside-write yes`.
- **The peer case's own race**: a peer that writes once and waits for the run to record it lands outside the
  window in about 1 of 40 runs (the write must fall after `before=$(project_state)` and before a heartbeat tick).
  The case writes until the run has recorded the difference, which is documented in the case itself and is what a
  peer rewriting a tree looks like from the run's side.
- No device and no emulator: the engine has no Android component and nothing here renders.
- No Gradle suite: no compiled path is in this delta (`*/src/*`, `*.gradle.kts`, `*.toml`, `CMakeLists.txt` all
  untouched), so `LOOP_GATE_COMPILED_CMD` was not owed.
