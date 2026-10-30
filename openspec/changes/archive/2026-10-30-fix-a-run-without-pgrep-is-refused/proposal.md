# A run on a host without `pgrep` refuses instead of placing nothing

## Why

`loop_tree_pids` (`tools/loop/run.sh:885`) reads the child's own process tree with two calls, and both swallow their
stderr:

```
tools/loop/run.sh:888:  frontier="$child $(pgrep -g "$child" 2>/dev/null | tr '\n' ' ')"
tools/loop/run.sh:895:      next="$next $(pgrep -P "$pid" 2>/dev/null | tr '\n' ' ')"
```

On a host without procps they return nothing, the walk prints only `$child`, and `outside_write_seen`'s two filters
drop that pid. Every difference in the project's own tree is then recorded and none is ever placed, with no line
saying so; `--strict-outside-write yes` still blocks, but the placement half of the rule never fires. The only
refusal today is `tools/loop-selftest.sh:71`'s required-tool list, which refuses the *suite* — a run started by
hand, or on a host that never runs the suite, degrades silently.

`pgrep` is the engine's only procps dependency since `fix-loop-attribution-is-read-before-the-sweep` removed
`pid_in_child_tree`'s `ps` call: `grep -rn 'ps -o\|pkill' tools/loop/*.sh` is empty, and that change's
`openspec/changes/archive/2026-10-24-fix-loop-attribution-is-read-before-the-sweep/design.md:48` justified `pgrep` as "Same procps package as `ps`, which the engine already uses" — a premise the
same change removed. The suite refuses the host it runs on; the run does not.

**Root cause.** The run's placement reading can fail closed (return nothing distinguishable from "no process") and
the run has no prerequisite check of its own, so an unreadable child tree is indistinguishable from a clean one and
the rule's deciding half silently does not apply.

**Red test.** `run#aHostWithoutThePlacementReadingIsRefused`, run on the base's production code by
`bash evidence/red-on-head.sh` (`evidence/red-on-head.xml`, `evidence/red-on-head.log`): `tests=1 failures=1`,
timestamp `2026-10-30T09:10:53Z`. With `pgrep` off `PATH` the run reaches the pre-screen and stops on an unrelated
refusal — `expected '2', got '3'` — instead of refusing with the usage error the case requires. The case itself is
the delta's only test change; it is the same case that must pass on the change's tree, and the gate's
`tools/loop-selftest.sh` tally is where that is read (`evidence/gate-tallies/`).

**Owning capability.** `agent-issue-loop`, requirement *Each item is worked by a fresh process, reviewed by a
different one* — its scenario *A difference is attributed while the writer is alive*
(`openspec/specs/agent-issue-loop/spec.md:433`) is the rule this defect leaves unenforced. The delta adds one
scenario to that requirement.

**Owning guideline.** `guidelines/Backlog.md`, section 7 "The loop" — the paragraph that states the placement reading and
recorded that "the **suite** is what refuses a host without it (#703)" (`guidelines/Backlog.md:326`).

**Surface.** The rule is asserted in two code sites, one guideline paragraph, and no case today. Measured with
`grep -rn 'pgrep' tools/loop/*.sh tools/loop-selftest.sh tools/loop-cases.sh guidelines/Backlog.md`: `run.sh` 2
lines (`:888`, `:895`), `loop-selftest.sh` 2 lines (the required-tool list at `:71`, which stays), `Backlog.md` 1
paragraph, no case, no fixture.

surface: 1 case added, 0 rewritten, 2 code sites, 1 guideline paragraph, 0 fixtures realigned — `grep -rn 'pgrep'
tools/loop/*.sh tools/loop-selftest.sh tools/loop-cases.sh guidelines/Backlog.md`

## What Changes

- `tools/loop/run.sh`: one prerequisite check beside the `LOOP_AGENT_CMD` check in the header — `command -v pgrep`
  absent is a `loop_die` naming the command and the reading it serves, before the worktree, the pre-screen, a
  branch or a child exists. The check is on the run, so a run started by hand refuses on such a host too.
- `tools/loop-cases.sh`: the red case, `run#aHostWithoutThePlacementReadingIsRefused` — it simulates the host with a
  `PATH` of symlinks that holds every executable except `pgrep`, and asserts the refusal's status and sentence, no
  worktree and no child.
- `tools/loop-mutations.sh`: one record, `12.13`, whose mutation deletes the check so the case must go red.
- `guidelines/Backlog.md`, section 7 "The loop": the paragraph now states that the run refuses such a host itself,
  and that the suite's refusal stays.
- `tools/loop-selftest.sh`: unchanged — the suite still refuses the host it runs on, and the case above is what
  covers a run started beside it.

## Capabilities affected

`agent-issue-loop` — one `MODIFIED` requirement, *Each item is worked by a fresh process, reviewed by a different
one*: one added scenario, *A host that cannot read the child's tree refuses the run*. Every other scenario of the
requirement is repeated verbatim; the requirement's body is unchanged.

Additive, no interface change: no flag, no recorded key, no default, no message of an existing refusal and no
landing rule is touched. Rollback path: revert the change; the case is red again, as it is on HEAD, and the run
degrades silently as it does today.

## Impact

| path | what changes |
|---|---|
| `tools/loop/run.sh` | one `command -v pgrep` prerequisite check in the header |
| `tools/loop-cases.sh` | the new case, its declaration and its dispatch |
| `tools/loop-mutations.sh` | record `12.13` |
| `guidelines/Backlog.md` | the placement-reading paragraph in section 7, "The loop" |
| `openspec/specs/agent-issue-loop/spec.md` | the added scenario, after archive |

No `:app`, `:auto`, `:core`, `buildSrc`, native or Gradle path is touched: the delta is the loop engine, its suite's
case list and the document that routes them. No submodule patch and no bridge override.

## Not in this change

- **A host without procfs but with procps** — `/proc/<pid>/cwd` and `/proc/<pid>/stat` are read directly and are not
  part of this defect: the engine already treats a missing `MemAvailable` as "no reading" rather than a refusal
  (`run#hostWithoutTheReadingPasses`), and a host that cannot read `/proc/<pid>` at all is a wider prerequisite the
  item does not name.
- **The archived change's
  `openspec/changes/archive/2026-10-24-fix-loop-attribution-is-read-before-the-sweep/design.md:48` premise** stays as
  written: an archived change is the record of what was
  believed and measured then, and the correction lives where a reader routes — `guidelines/Backlog.md`, section 7.
