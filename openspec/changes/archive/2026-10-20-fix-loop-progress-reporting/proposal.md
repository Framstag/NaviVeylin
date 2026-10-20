# Proposal — fix-loop-progress-reporting

## Why

A loop run printed `pick #N` and then nothing until the item's verdict. The console for the worker phase, both
review rounds and the gate carried no line at all, so an item that was being worked and an item whose child had
hung were indistinguishable to the person watching, and to the person reading the log afterwards.

Root cause, and the one measurement that settles it: the run starts every child through the configured command
`LOOP_AGENT_CMD` (`tools/loop/loop.conf`), which is print mode (`pi --print`). Print mode writes one thing — the
child's final assistant text — when the child exits, and nothing before it. `run_child`
(`tools/loop/run.sh`, pre-fix) sent that whole stream to the report file and printed nothing of its own:

```sh
( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) > "$2" 2>&1
```

so the stream it redirected carried no bytes until the child was over: measured 16 bytes at exit, 0 before it,
on a redirected file and on a pipe (`evidence/probe-output.txt`, the 0 B rows for the child's whole life). The
run did not lose the child's output; there was never any output to lose while it mattered.

Failing case and its red run on the unpatched script (evidence, not assertion):

- case `run#childReportsWhileItWaits` (`tools/loop-cases.sh`) holds a stub child on a fifo — the child cannot
  have said anything, so whatever the run prints it did not learn from the child.
- On the pre-fix `run_child` (`git checkout 1fa8077~1 -- tools/loop/run.sh`):
  `FAIL run#childReportsWhileItWaits` — `the wait is announced when the child starts: 'loop: worker-prompt
  started' not found in: … pick #21` (`evidence/case-red-unpatched.log`).
- On the fixed script: `PASS run#childReportsWhileItWaits` (`evidence/case-green-after-restore.log`).

Owning requirement, and the gap the defect exposes: `agent-issue-loop` — "One command starts a budgeted run,
with no dialogue", whose scenario *A run is recorded* guarantees the run justifies itself with numbers **when
it ends**. Nothing in that specification said what a run reports *while* it works, and that is exactly the
window in which the run was silent. This change ADDs the missing requirement, "A run reports the wait it
cannot get from the child"; no existing requirement's text moves.

One fix follows from this root cause, and no second fix is defensible: the silence is created by the run's own
`run_child`, in one place, and the only thing that can be reported without the child is what the run can
observe itself. The alternatives the evidence eliminates are in `design.md`.

## What Changes

- `run_child` prints three kinds of line — `started`, a periodic `running`, and `finished`. The periodic line
  carries the elapsed time, the transcript's size on disk, the worktree's changed-file count and its commit
  count since the run's base; all four are read from the tree the child writes, never from the child.
- The cadence is `LOOP_PROGRESS_SECONDS` (default 30, a non-numeric or zero value falling back to 30), so a case
  can drive it in one second without a special mode in the script.
- The `finished` line carries the child's exit status and the final transcript size, so the three phases that
  follow know what the previous one did without opening its file.
- `.pi/skills/fix-loop/SKILL.md` gains the section "What a run prints while it works" — what the three lines
  mean, and which of the numbers is expected to stay at 0 until the child exits.

Additive: no flag, path, exit status, state file or verdict changes, and no phase's input changes. Rollback:
revert `tools/loop/run.sh`, `tools/loop-cases.sh` and `.pi/skills/fix-loop/SKILL.md`; a run then prints what it
printed before, and no consumer reads the new lines.

## Capabilities

### New Capabilities

None.

### Added Requirements

- `agent-issue-loop`: one new requirement, "A run reports the wait it cannot get from the child", with three
  scenarios — the wait is announced when the child starts, the running child is reported on a cadence whose
  numbers come from the tree, and the end is reported with the child's own exit status. Each is exercised by
  the single case `run#childReportsWhileItWaits`. No existing requirement is modified, deleted or reordered.

## Impact

- `tools/loop/run.sh` — `run_child` and one small helper, `fmt_elapsed`; the child-start indirection
  (`LOOP_AGENT_CMD`) and the prompt, the worktree and the state are untouched.
- `tools/loop-cases.sh` — the new shell case `run#childReportsWhileItWaits`, added to `LOOP_SHELL_CASES` and to
  the dispatcher in `loop_run_shell_case`.
- `.pi/skills/fix-loop/SKILL.md` — one new section, between the failure table and the report section.
- `openspec/specs/agent-issue-loop/spec.md` — gains the ADDED requirement at archive time; this change's delta
  is the only place it is written now.
- No app module, manifest, resource, stylesheet or native file is touched; no phone/car variant is affected, so
  the parity requirement of `guidelines/UI.md` §1 does not apply.
- Guidelines affected: none. `guidelines/Build.md` §2 owns the iteration protocol the loop runs, and it does
  not describe the loop's console output; `AGENTS.md` "Backlog maintenance" names the loop and its entry
  `tools/loop/run.sh`, and the loop's own operating document is the skill, which is updated here.

## Scope

Tooling only — the backlog loop that closes defect-shaped items. It is not a feature of the app, of the Android
Auto integration or of the phone UI.
