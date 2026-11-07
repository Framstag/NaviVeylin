# The run consumes the change an item names, and never plans it again

## Why

A tier-B finding (`finding-shape-at-filing`) names an OpenSpec change that already exists, passed the readiness
gate, and answers the forks its item records. The run cannot see it. Measured on the tree:

```
$ git check-ignore -v openspec/changes/fix-start-map-selection
.gitignore:76:openspec/changes/*/   openspec/changes/fix-start-map-selection
```

An unarchived change is gitignored, and the run cuts each item's worktree from the base
(`tools/loop/worktree.sh`), so `openspec/changes/<name>/` is **not** in the tree the worker gets. `loop.conf` states
the same fact from the other side: the one gitignored directory a stopped run copies into its leftover patch is
`openspec/changes`, because *"an unarchived change is exactly the work a killed worker leaves behind"* (issue #308).

So a change written before the run arrives nowhere, and the worker plans a second one from the item's prose. Two
consequences, both measured in the tree's own record: the recorded decision the item carries is then a *constraint
on a plan the worker re-derives* rather than the plan itself (`tools/loop/prompts/worker.md`, "A recorded decision is
settled"), and the #196 class of defect — a fork re-derived with a fresh option list, so a recorded `2` means
something else — reappears in the one place the tree has been careful to close it.

**The absence.** `grep -rn 'ITEM_CHANGE\|openspec/changes' tools/loop/prompts/worker.md
tools/issues/core/WorkPackage.java tools/loop/prescreen.sh` returns nothing: no phase reads a change the item
names, no package block carries one, and the pre-screen does not know a tier exists.

**Acceptance scenario.** An item whose body records `<!-- tier: b -->` and `<!-- change: add-x -->` has that change
copied into its worktree before the worker starts; the worker's package carries the change's name, its path and the
readiness verdict; the worker applies that change and writes no new one; and an item whose named change is gone or
no longer ready is refused before any child is started.

**Owning guideline.** `guidelines/Backlog.md`, section 7 "The loop" — the phase table and the paragraph on what a child is
started with; the copy is the one mechanical act of the item's preparation that the table does not yet have.
`guidelines/Build.md` §4f owns the artifact and evidence rules a consumed change still owes.

**Surface.** Measured with `grep -rn 'ITEM_CHANGE\|tier\b' tools/loop/ tools/issues/core/ tools/loop/prompts/`
(no hit), `ls tools/loop-cases.sh` (the case suite), `grep -c 'phase' tools/loop/prompts/worker.md` (4 phases).

surface: 12 cases added, 1 rewritten, 6 code sites, 3 guideline places extended, 2 fixtures (1 added, 1 realigned) —
`grep -rln 'ITEM_CHANGE\|change-not-ready\|changeCopies' tools/loop/ tools/issues/core/ tools/loop-cases.sh guidelines/Backlog.md guidelines/Backlog.recipes.md`

## What Changes

- **`tools/loop/worktree.sh`** — at the start of an item whose body records a change, copy
  `openspec/changes/<name>/` from the project's tree into the item's worktree, whole, and record the copy in the
  run's state and in the item's evidence directory. A change the tree does not hold is a refusal (below), never an
  empty copy.
- **`tools/loop/prescreen.sh`** — read `tier` and `change` from the item's body record; for tier B check that the
  change directory exists and that the profile's readiness check still reports `ok` for it, and refuse the item
  before any child starts when either fails. The refusal is written on the item as a finding with the checker's
  verdict line, and it is **not** a bare hold.
- **`tools/issues/core/WorkPackage.java`** — a third delimited block `<<<ITEM_CHANGE` / `ITEM_CHANGE` carrying the
  change's name, its worktree path, the readiness verdict line and the recorded decisions, beside the existing
  `<<<ITEM_TEXT` and `<<<ITEM_DECISION` blocks, so a comment can open or close no block it does not own.
- **`tools/loop/prompts/worker.md`** — Phase A re-verifies the change against the tree (`file:line` claims still
  hold, the red case is still red on the base) and Phase C becomes *do not re-plan*: a fork the change does not
  answer is gate 3, naming that fork; a change that no longer fits the tree is `needs-diagnosis`, naming what moved.
  The propose-first path (gate 7, `roles.proposeClass`) is unchanged for an item that names no change.
- **`tools/loop/loop-clean.sh`** — the consumed change's source directory in the project's tree is reported by the
  clean command and removed by `--apply`, with the recorded worktree build paths; the run itself writes nothing in
  the project's own tree.
- **`guidelines/Backlog.md`** §6 and its section "The loop", and **`tools/loop/loop.conf`** where the copy's bounds belong.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None at the level of shipped text. Four requirements are **added** to `agent-issue-loop`: the named change is handed
to the item's worktree and its source is cleaned afterwards; a tier-B item is never planned again; an item whose
change is absent or no longer ready is refused before a child starts; and the package carries the change beside the
item's text and its decision. Each states a behaviour the shipped requirements do not state, and none of them
contradicts one — in particular this change does **not** modify *"Each item is worked by a fresh process, reviewed
by a different one"*: a package that carries a change is still a prepared package, which is why C3's delta is
`ADDED` rather than a 14-scenario `MODIFIED` block.

## Impact

| path | what changes |
|---|---|
| `tools/loop/worktree.sh` | copy the named change into the item's worktree; record it |
| `tools/loop/state.sh` | the copy's record (source, file count, bytes) under the item |
| `tools/loop/prescreen.sh` | tier-B refusal before a child starts |
| `tools/issues/core/WorkPackage.java` | the `ITEM_CHANGE` block |
| `tools/loop/prompts/worker.md` | Phase A re-verify, Phase C "do not re-plan" |
| `tools/loop/loop-clean.sh` | the consumed source directory in the report and in `--apply` |
| `tools/loop-cases.sh` | the cases; `tools/loop-mutations.sh` one record per new refusal |
| `guidelines/Backlog.md` | §6 and "The loop" |
| `openspec/specs/agent-issue-loop/spec.md` | four added requirements, after archive |

**BREAKING for a tier-B item only.** A tier-A item is untouched: no copy, no pre-screen branch, no package block
beyond `recorded: none`. Rollback path: revert the change; the four cases go red and a tier-B item is worked as
today, i.e. planned by the worker. No shipped behaviour, no flag, no default and no existing refusal message
changes; no tracked file's meaning changes.

Scope: **general process tooling** — the loop engine, the package renderer, the prompt and the guideline. No
`:app`, `:auto`, `:core`, `buildSrc`, Gradle, manifest, resource or native path. No submodule patch, no bridge
override.

## Not in this change

- **The gate and the filing shape** — `change-readiness-gate` (C1) and `finding-shape-at-filing` (C2), which this
  change reads and does not modify.
- **A change split across items.** One item names one change, and one change belongs to one item; a feature needing
  three changes is three items. Nothing here records a relation between them.
- **A change edited while its item is in flight.** The copy taken at item start is what the run works, and a peer's
  edit to the source afterwards is recorded by the existing outside-write reading, not merged
  (`run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`).
- **The shaping inbox** — `shaping-inbox` (C4), which gives the tier-B refusal a queue to land in.
