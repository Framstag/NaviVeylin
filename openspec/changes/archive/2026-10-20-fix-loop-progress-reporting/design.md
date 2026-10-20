# Design — fix-loop-progress-reporting

## Root cause, and what it leaves to be decided

`run_child` starts every child through `LOOP_AGENT_CMD` and sends the child's stream to the phase's report
file:

```sh
# tools/loop/run.sh, before 1fa8077
run_child() {
  local before after rc
  before=$(project_state)
  ( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) > "$2" 2>&1
  rc=$?
  …
}
```

`LOOP_AGENT_CMD` is `pi --print` (`tools/loop/loop.conf`), and print mode writes the final assistant text
once, at exit. So the redirected stream is empty for the child's whole life. Measured, on a stand-in child
that holds its answer and writes it at exit — the stream shape of print mode — at one-second intervals:
`t=1s 0 B, t=2s 0 B, t=3s 0 B, t=4s 0 B`, then `16 B` at exit, identically for a redirected file and for a
pipe (`evidence/probe-output.txt`). The real command measured the same 16 bytes at exit and nothing before it
(the fix's own commit message, `evidence/commit-1fa8077-stat.txt`).

So the decision the root cause leaves is narrow: **where do the numbers come from?** Nothing the child says is
available while the child runs. Everything the run can report must therefore be something the run can observe
itself.

## The alternatives the evidence eliminated

- **Stream the child's own progress to the console** — impossible. Print mode emits only the final assistant
  text: a child that works for twelve minutes writes 0 bytes until it exits, and a `tee` of that stream would
  reproduce the silence. Not a matter of where the bytes go; there are no bytes
  (`evidence/probe-output.txt`, the four 0 B rows before exit; a pipe carries no more than a file).
- **Tail the report file and print its new lines** — impossible for the same reason, and measurably so: the
  file holds 0 bytes until the child exits (`wc -c` in the same probe). A tail loop would print nothing for the
  whole item and then print the entire final text at once, which is what redirecting already did.
- **A per-second spinner with no numbers** — it is printable, and it is useless: it moves whether the child is
  thinking, stuck on a locked file, or waiting for a credential, so it says nothing about whether the work is
  moving. It also cannot be asserted by a case that the child is alive but silent, which is the case the defect
  is about.
- **Change `LOOP_AGENT_CMD` to a command whose stdout streams** — not a fix of this defect: the run's mechanics
  are the project's configuration (`agent-issue-loop`, "The run's mechanics are the project's configuration,
  not its code"), so the loop must report the wait whatever command a project configures — the next project's
  command would reintroduce the silence. It is also outside a `fix-*` change's one root cause: the code that
  failed to report is `run_child`, and a non-print mode would additionally need a dialogue the run may not
  have.

## The chosen shape

Three lines per child, all printed by `run_child` itself:

```
loop: worker-prompt started (log .pi/issue-loop/items/126/worker-report.md)
loop: worker-prompt running 1m30s — log 0 B, worktree 4 changed file(s), 0 commit(s) since the base
loop: worker-prompt finished in 12m04s (exit 0, log 812 B)
```

- `started` is printed before the child is launched, so the phase's name and its transcript's path exist even
  if the child never writes anything.
- The periodic line is printed by a watchdog subshell that sleeps one second at a time and counts, and reports
  when the count reaches `LOOP_PROGRESS_SECONDS` (`${LOOP_PROGRESS_SECONDS:-30}`, falling back to 30 when the
  value is empty, non-numeric or zero — a cadence of zero would loop without pause). The elapsed time is
  formatted by `fmt_elapsed` (`37s`, `4m12s`).
- The periodic line's numbers are read, in this order, from the file system and git: `wc -c` of the report
  file, `git -C "$LOOP_WORKTREE" status --porcelain | wc -l`, and
  `git -C "$LOOP_WORKTREE" rev-list --count "${base:-HEAD}..HEAD"`. Every one of them changes when the child
  works — a worker that writes its change moves the changed-file count, a worker that commits moves the commit
  count — and none of them can be produced by a child that is not working.
- `finished` is printed after the child is reaped and after the watchdog is stopped through a stop file
  (`"$2.progress-stop"`), with the exit status the child returned, so the phase that follows can be read
  against what the previous one did.

## Why these three numbers, and not others

- **transcript size** is the only direct evidence that the child produced output; it is expected to stay 0
  until the child exits, and that expectation is what the skill now states — a reader who sees `log 0 B` for
  an hour is not seeing a broken measurement but print mode's contract.
- **changed files in the worktree** is the coarse measure of work in flight: a change being written always
  moves it.
- **commits since the base** is the measure that survives a child that stages and commits nothing until the
  end; together with the previous number it separates "writing" from "committed".

A percentage or an estimated remaining time would need a model of the child's cost that no measurement in this
repository supports (`.pi/skills/fix-loop/REFERENCE.md`'s measured costs are per item, not per phase step), so
none is printed.

## Threading, state and lifecycle

The loop is a shell script; there is no dispatcher. The watchdog runs as a background subshell of `run_child`,
writes to **stderr** (the phase lines on stdout stay in order), is stopped by a stop file so it never outlives
its child, and `wait`s on the parent's side so no orphan survives a child that exits early. It writes nothing
to the run's state file, no label and no branch: `agent-issue-loop`'s "State is written only between agent
processes" holds unchanged — the progress lines are console output, not state.

## Risk

- A watchdog that outlives its child would print stale lines into the next phase's output. Mitigated by the
  stop file plus `wait "$hb"` before the `finished` line, and observed by the case (its `finished` line is the
  last one printed).
- `git status` every 30 s in the worktree is a second-scale cost against a child that runs for minutes; on a
  worktree the size of this one it is milliseconds.
- A reader may believe `log 0 B` means failure. Addressed by documenting the expectation in the skill, in the
  section "What a run prints while it works".

## Blast radius

`tools/loop/run.sh` (`run_child` plus `fmt_elapsed`) and `tools/loop-cases.sh` (one case). No caller changes:
`run_child`'s arguments, return code and report-file contract are exactly as before, and the case that inspects
the child's stream (`run#childStartsInTheProjectRoot`) reads the prompt file, not the console.

## Verification

- Red on the unpatched script: `git checkout 1fa8077~1 -- tools/loop/run.sh`, then
  `bash tools/loop-selftest.sh --case 'run#childReportsWhileItWaits'` → `FAIL`, with `the wait is announced when
  the child starts: 'loop: worker-prompt started' not found in: … pick #21`
  (`evidence/case-red-unpatched.log`).
- Green with the fix: the same command → `PASS run#childReportsWhileItWaits`
  (`evidence/case-green.log`, `evidence/case-green-after-restore.log`).
- The rest of the suite: `bash tools/loop-selftest.sh` offline, no token, no network, no device — `loop-selftest:
  all 86 cases passed (JDK 27, offline, no token, no sandbox)` (`evidence/full-suite.log`). A first attempt had
  one case killed by the host rather than failing (`Getötet` on `run#mayCloseNoLeavesTheIssueOpen`, 0.9 GB free);
  that case passed in isolation and in the clean re-run (`evidence/full-suite-first-run-killed.log`).
- The probe that eliminates the alternatives: `bash
  openspec/changes/fix-loop-progress-reporting/evidence/probe-print-mode-bytes.sh`
  (`evidence/probe-output.txt`).
- No compiled file is touched, so no Gradle gate is run. The structural checks are `bash -n` on the two edited
  scripts and `openspec validate` / `openspec doctor` for the record.
