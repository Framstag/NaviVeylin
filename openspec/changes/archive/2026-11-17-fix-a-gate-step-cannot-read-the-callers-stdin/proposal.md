# Proposal: fix-a-gate-step-cannot-read-the-callers-stdin

## Why

A gate step inherits the gate's stdin, so one command that reads it can freeze the whole pre-flight (issue #947).

- **Observed** ✗: `start_step` launched every step as
  `( $cmd; printf '%s\n' "$?" > "$status_file" ) >"$stream" 2>&1 &` (`tools/loop/gate.sh:575` before this change) — stdout
  and stderr redirected, stdin not. A run started from a shell from `pts/3` on 2026-11-16T12:14:49Z had its base-red's step
  `tools/tests/check-dex-gate-graph-selftest.sh` found in state `T` (`do_signal_stop`) at 13:03:30Z with its whole subtree
  — the selftest script, the `gate.sh --base-red` it nested, and a `./gradlew … --dry-run` client. `ps` showed
  `TPGID 3770326` (the run's group) against `PGID 1952741` (the step's): a *background* group on that tty. `kill -CONT`
  twice, then `kill -CONT` again after `stty -F /dev/pts/3 -tostop`, left all three in `do_signal_stop` within two seconds.
  The step was ended only by its bound at 13:33:31Z — 30 minutes later — and the run's first item started at 13:36Z.
  *(Every figure in this section is the issue's own Observed and Evidence lines; the run's log is the source it names.)*
- **Root cause** ⊕: the step's stdin is the gate's stdin, and the process group `set -m` gives a step
  (`tools/loop/gate.sh:581-584`) is a background group on a terminal with job control, so a `read` from it raises
  `SIGTTIN`. The stop is the terminal's, not the step's: no `SIGCONT` undoes it while the read stands, and nothing in the
  engine can see it — a stopped child writes nothing, so the gate looks like a slow step until the bound answers it.
  The engine's own child launch already does the opposite: `tools/loop/run.sh:2125` starts every child
  `… 2>&1 < /dev/null &`. The gate was the one start line without it.
- **Consequence** ⊨: any environment that hands the loop a terminal with job control can freeze a pre-flight, and the
  cost is the step's whole bound — **75 minutes of a 600-minute budget** on 2026-11-16, 89 further minutes lost to a quota
  hold beside it. The same class reaches an item's gate inside a child session, where the cost is the item's wall clock.
- **The one fix** ➜: give every step its own stdin, `… >"$stream" 2>&1 < /dev/null &` (`tools/loop/gate.sh:582`), so no
  command a step starts can read the console and no `SIGTTIN` can arise from a gate at all. The follower's own reads are
  `wc` and `tail` over the step's file (`follow_stream`, `tools/loop/gate.sh:176-192`) and take no stdin.
- **Requirement added**: `openspec/specs/agent-issue-loop/spec.md`, "A gate step is given a stdin of its own" — no
  shipped requirement says anything about a step's stdin, which is why the start line could be written without one.

## What Changes

1. **The step start line redirects stdin** (`tools/loop/gate.sh:582`): `2>&1 < /dev/null` beside the stream and the status
   file, with the rule in the comment above it — the `set -m` group, the `SIGTTIN` measurement, and the bound it cost.
   This is one redirect on the one line a step is started from; nothing else in the gate reads stdin.
2. **A case holds it** (`tools/loop/tests/loop-cases.sh`, `gate#aStepCannotReadTheCallersStdin`): a caller whose stdin
   delivers nothing — a fifo whose writer side the case holds read-write, the idiom `driver#noFixtureFifoIsOpenedWriteOnly`
   already requires — and a suite whose first act is a `read`. A step given an empty stdin reads end-of-file at once and
   prints its summary; a step that inherits the caller's blocks until its bound, so the case is red on HEAD by
   construction, in ten seconds, with no terminal in the harness.
3. **A mutation record holds the redirect** (`tools/loop/tests/loop-mutations.sh`, `947.1`): it removes
   `< /dev/null` from the start line, and `gate#aStepCannotReadTheCallersStdin` must go red. `63.1` quoted the same line
   and is moved onto the new one: it reads the command half as `.*` and ends on the whole tail, so the two records name
   different halves of one line, and an edit to any part of that line moves both (which is why `63.1` had to move here).
4. **The spec adds the requirement** (`agent-issue-loop`), with the one scenario the case exercises.

Not changed: `set -m` and the process-group handle the bound ends a step with (issue #801, `63.2`), the follower and the
stream the tally is read from (issue #269, `315`), the bound itself (issue #311), and the engine's other child launch
(`tools/loop/run.sh:2125`, which already carries the redirect).

## Impact

- Files: `tools/loop/gate.sh` (one line and its comment), `tools/loop/tests/loop-cases.sh` (one case, its declaration and
  its dispatch row), `tools/loop/tests/loop-mutations.sh` (one record added, one moved), and this change's specs.
  Surface: 1 case added, 0 rewritten, 1 fixture (`suite.sh`, written inside the case) — `tools/look.sh
  'status_file" \) >"\$stream"' - files` → 5 files: `tools/loop/gate.sh`, `tools/loop/tests/loop-mutations.sh`, an archived
  gate log, and this change's own `proposal.md` and `design.md`, which quote the line.
- Modules: none — no Kotlin, Java, C++ or Gradle file is touched, so no build and no Android suite is owed
  (`guidelines/Build.md` §2, the scope block). The shell suite that owns `tools/loop/**` and the mutation record set
  stand in for the gate.
- Spec changed: `agent-issue-loop`, requirement "A gate step is given a stdin of its own" (ADDED).
- Additive, not breaking: a step that reads stdin gets end-of-file instead of the caller's input. No step in the profile
  reads stdin, and no caller of `gate.sh` pipes anything into it.
- Rollback: revert the three files. A step then inherits the caller's stdin again and a run started from an interactive
  shell can freeze its pre-flight until the step's bound, which is the defect this change removes.
