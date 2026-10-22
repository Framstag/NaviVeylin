# fix-run-state-names-its-item

## Why

The state a person opens after a run was killed names no item. It carries a phase and nothing else, so the
one question a killed run leaves — *which item was in flight* — is answerable only by resolving the worktree's
branch marker (`.pi/worktrees/loop/.pi/loop-branch`) by hand, a file the state does not point at.

**What happened, with the files.** `tools/loop/state.sh` already has the writer: `cmd_start`
(`tools/loop/state.sh:325`) validates `<issue>` and `--change` and patches
`.current = {issue:$i, change:$c, phase:"A"}` plus a `start` step (`tools/loop/state.sh:337`). Nothing calls
it — `grep -rn "loop_state start\|state\.sh\" start" tools/loop/*.sh | wc -l` prints **0** — so the three
places that read that field read what nobody wrote:

| place | file:line | what it does with `.current` |
|---|---|---|
| the live view | `tools/loop/state.sh:236-237` | prints `no item in progress`, or `#<issue> <change> · phase <phase>` |
| every phase step | `tools/loop/state.sh:381-382` | records `issue:(.current.issue // 0)` — so **every step of every item records `issue: 0`** |
| the item's end | `tools/loop/state.sh:436` | clears the mark when it names the finished item — a clear of a field nothing set |

`run.sh` writes its one step (`loop_state step A --name worker`, `tools/loop/run.sh:876`) and its item record
(`loop_state done`), and between picking an item and that step it never names the item in the state: the only
mark it writes there is the tracker's (`loop_labels pick`, `tools/loop/run.sh:829`, whose `in_progress`
variable at `tools/loop/run.sh:834` is the run's own shell state and dies with it).

The observed consequence is recorded in the item: the run that started at 18:46 on 2026-10-22 was killed at
19:19 while its round-2 reviewer for `#249` was answering, and the kept state
(`.pi/issue-loop/runs/2026-10-22T16:46:21Z.json`) says `current: {"phase":"A"}` — the item is recoverable only
from the worktree's branch marker.

**Root cause, one sentence:** `state.sh`'s `start` — the only writer of `.current.issue` — has no call site in
`run.sh`, so the field the live view prints and every step derives its `issue` from is never written, and each
step is attributed to issue `0`.

**Red test on HEAD, before the fix.** One case added with this change,
`run#stateNamesTheItemInProgress` in `tools/loop-cases.sh`, run against the engine at
`522d69a7ac21aebe8a542983671c5f57963ebf5a` — the transcript is `evidence/red-HEAD.log`, the result
`evidence/red-HEAD.xml`: **XML tests=1 failures=1** (2026-10-22T20:44:30Z), the assertion

```
the state names the item while it is being worked: expected '21', got 'null'
```

The case reads the state *while the worker child is blocked* — the state a killed run leaves behind — so the
assertion is the item's own complaint and not a proxy: after the fix the same read names `#21` and phase `A`;
once the item ends, `.current` is `null` again and every step recorded carries `21`, none `0`.

**Owning capability and guideline section.** `agent-issue-loop` — "The run's state names the item it is
working on" is added by this change; the requirement "The run survives interruption" owns the state the
scenario reasons about, and "The run names its phases and the live view into it" owns the reader
(`state.sh show`, the command the run's own header names). The guideline that owns the loop is
`guidelines/Backlog.md`, its heading `## 7. The loop`, whose paragraph on the lock and the state names the process a state
was written by and not the item it was working on.

**What is not fixed here.** The item's other half — "the item is only recoverable from the worktree's branch
marker, which is a file the state does not point at" — is answered only to the extent the item's number and
the run's own change name are enough to find that branch (`fix/issue-<issue>-<slug>`): no branch field is
added to the state. `done` already records the branch it merged, per item.

## What Changes

- `tools/loop/run.sh` — the item is recorded as in progress between picking it and starting its first child:
  `loop_state start "$issue" --change "<fix|add>-<slug>"`, guarded like the label write that precedes it, so a
  state that cannot name the item stops the run instead of working an item the state does not mention.
- `tools/loop-cases.sh` — the case above, with its registration and dispatch.
- `openspec/specs/agent-issue-loop` — the requirement above, naming exactly that case.
- `guidelines/Backlog.md`, heading `## 7. The loop` — the sentence about what the state names gains the item.

surface: 1 case added, 0 rewritten, 0 fixtures realigned — `grep -rn "\.current" tools/loop/state.sh` (5 hits: `tools/loop/state.sh:337` the writer, `tools/loop/state.sh:236-237` the live view's two branches, `tools/loop/state.sh:382` every step's fallback, `tools/loop/state.sh:436` the item's end) and `git show <base>:tools/loop-cases.sh | grep -c "\.current"` (0: no case asserted the mark before this one)

## What the fix does not decide, and why

The item names a choice — whether the in-progress mark belongs in the state's `.current` or in the item's own
directory, and what reads it while the run works. The tree already answers both halves and this change takes
the answer the tree carries rather than inventing one: `.current` is the state's field because `state.sh show`
(`tools/loop/state.sh:236`) prints it as its first line — the live view the run's header tells a person to open — and
because `cmd_done` (`tools/loop/state.sh:436`) already clears it when the item ends, which is an item-scoped mark by
construction; the item's own directory holds the reports a person reads, and nothing there is a *mark* that a
later run can clear.
