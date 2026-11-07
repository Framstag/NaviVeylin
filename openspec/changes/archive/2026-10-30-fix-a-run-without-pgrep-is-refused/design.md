# Design — the run refuses a host whose child tree it cannot read

## Root cause

`loop_tree_pids` (`tools/loop/run.sh:885`) asks `pgrep` twice per level of the child's tree — `pgrep -g "$child"`
for the group the child leads and `pgrep -P "$pid"` for every level below it (`tools/loop/run.sh:888`,
`tools/loop/run.sh:895`) — and both
calls redirect stderr to `/dev/null`. On a host without procps each call prints nothing and returns non-zero; the
walk prints only `$child`; `outside_write_seen`'s two filters drop that pid (it is the child, which the run itself
started in the project, and it never left the worktree). The result is indistinguishable from "the child wrote
nothing": every difference in the project's own tree is recorded and none is ever placed, and no line says so.

The one refusal today is `tools/loop-selftest.sh:71`'s required-tool list. It refuses the *suite*, and the run does
not consult the suite: a run started by hand on such a host degrades silently, and `--strict-outside-write yes`
still blocks while the placement half of the rule never fires.

`pgrep` is the engine's only procps dependency: `fix-loop-attribution-is-read-before-the-sweep` replaced
`pid_in_child_tree`'s per-pid `ps` with `pid_ppid`'s `/proc/<pid>/stat` read, so
`grep -rn 'ps -o\|pkill' tools/loop/*.sh` is empty while that change's
`openspec/changes/archive/2026-10-24-fix-loop-attribution-is-read-before-the-sweep/design.md:48` still justifies
`pgrep` as
part of a package the engine "already uses".

## The single fix

One prerequisite check in `tools/loop/run.sh`'s header, beside the `LOOP_AGENT_CMD` check that already refuses an
empty agent command (`tools/loop/run.sh:109`):

```sh
if ! command -v pgrep >/dev/null 2>&1; then
  loop_die "run: pgrep is required — the placement reading asks the kernel for the child's own process tree; a host without procps would record every difference and place none"
fi
```

It sits after `loop_resolve` (so `loop.conf` and the resolved paths are already read) and before the lock, the
worktree, the pre-screen, any branch and any child — the same place and the same `loop_die` (`LOOP_USAGE`, exit 2)
as the agent-command refusal. The suite's own required-tool list stays: it still refuses the host before a suite
runs 287 cases that all need the same reading, and it is what makes the refusal cheap on a host that runs the
suite.

The refusal names the *outcome* ("would record every difference and place none"), not a bare tool name, because a
person reading the line has to know why a run needs the tool at all — the shape `loop_resolve` already uses for the
`core.bare=true` refusal, which names the setting and the repair.

## Alternatives the evidence eliminated

- **Warn instead of refuse.** A log line is the one thing the defect already produces nothing of, but a warning is
  not a decision: with `--strict-outside-write no` — the default — the item still lands, and the rule's deciding
  half is still unenforced. The archived `fix-loop-attribution-is-read-before-the-sweep` already recorded that a
  reading that cannot attribute must not decide; a warning would leave the opposite half of the same scenario
  unmet. Eliminated.
- **Rely on the suite's required-tool list.** Eliminated by the item's evidence: the refusal has to reach a run
  started by hand, and `tools/loop/run.sh` is that entry point; a host that never runs the suite is exactly the
  case the issue reports.
- **Probe the read (`pgrep -P $$`) and treat a failure as missing.** Eliminated: `pgrep` returns 1 both for "no
  match" and for an error it was told to hide, and the two calls already swallow stderr, so the probe re-creates
  the ambiguity it was meant to remove. `command -v` is the only question with one answer.
- **Drop the dependency and read `/proc` directly.** A rewrite of the walk `fix-loop-attribution-is-read-before-the-
  sweep` paid for, and a second mechanism beside the missing prerequisite check. Two mechanisms is not this item;
  the item's "Smallest fix" names the check.
- **A recorded key for the tool's path.** No project varies here — the engine already calls `pgrep` by name — and a
  key would add a surface with no second caller. Eliminated as over-configuration.

Not a trade-off, so gate 3 holds: the issue's own text names the check as the smallest fix, and no recorded owner
decision on the item contradicts it (`recorded: none`).

## Why the neighbouring "no reading is not a refusal" rule does not apply

`run#hostWithoutTheReadingPasses` records that a host publishing no `MemAvailable` is *not* refused: the engine has
to run in a container without procfs, and a check that cannot be made is not a refusal. The difference is what the
reading decides. The memory reading gates one item's affordability and its absence is the conservative answer
(stop spending on an item that may not fit — the run records the free memory as `unknown` and the floor passes).
The process-tree reading is the rule's *evidence*: with it absent, the run's answer for "who wrote this" is the
wrong answer every time, not a missing number a reader can see. Absence is silent, so it must be a refusal.

## Blast radius

The delta touches one production file (`tools/loop/run.sh`, four lines plus a comment), the case list that proves
it (`tools/loop-cases.sh`), the mutation record (`tools/loop-mutations.sh`), and one guideline paragraph
(`guidelines/Backlog.md`, section 7). `tools/loop-selftest.sh` is unchanged, and every other engine script is untouched —
`grep -rn pgrep tools/loop/*.sh` still names `run.sh` only.

Effects on existing behaviour: none, on a host with `pgrep`. A host without it now exits 2 with one line instead
of running an item whose placement reading cannot fire. No flag, no recorded key, no exit status of any other path,
no message of an existing refusal, no landing rule and no child prompt changes.

## Rollback

Revert the change: the check and the case go, the guideline paragraph returns to "the suite is what refuses", and
the tree is the base's. The red case returns red, as it is on HEAD (`evidence/red-on-head.xml`).

## Verification

- The red case on the base's production code: `tests=1 failures=1` (2026-10-30T09:10:53Z,
  `evidence/red-on-head.xml`).
- The same case green on the change: the gate's `tools/loop-selftest.sh` tally
  (`evidence/gate-tallies/tools_loop-selftest.sh_--from-git_….xml`).
- The revert direction: mutation `12.13` deletes the check and the case must fail; restore, re-run green.
- The gate, once, for a `tools/loop/**` delta: `./tools/loop/gate.sh` — `tools/loop-selftest.sh` selected by the
  delta's pathspec, `tools/loop-mutations.sh --check`, and the structural checks — with the XMLs copied into
  `evidence/` at the moment they are produced.
- No on-device check: the delta reaches no compiled file, no UI and no surface.
