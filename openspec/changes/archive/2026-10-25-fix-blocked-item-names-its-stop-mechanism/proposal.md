# Proposal — fix-blocked-item-names-its-stop-mechanism

## Why

An item that ends `blocked` records **one** word for two different mechanisms: a review round at the cap that
refused the change, and a branch the base would not take. The two are told apart only by the prose of the item's
`note` — `tools/loop/run.sh:1467` (`the change failed its last review round …`) and
`tools/loop/run.sh:1500` (`the branch … could not be merged onto the base …`) — while both write the same
`verdict: blocked` and the same `result: blocked` (`tools/loop/run.sh:1646`, `tools/loop/state.sh:436`).

**Root cause (one).** `state.sh done` has no field for the mechanism, so the record keeps the note's prose as the
only place the two blocks differ, and every figure derived from the state can only total `blocked`.

Measured over the archive on 2026-10-23 (`measurement.txt`, `probe-tokens.py`, re-measured against every run since
the item was filed): 142 attempts, 128 with usage, 1154.9 M tokens — 10 refusing rounds cost **198.8 M** (17.2 %,
mean 19.88 M, 2724 s) and 1 unmergeable attempt cost **22.9 M** (2.0 %, 2840 s), against a closure's 738.7 M /
15.08 M mean. A refusal block costs 32 % more than a closure, and the run cannot say so from its own state: the
attribution the item asks for exists only inside `note` strings, which is why the probe that produced those
numbers parses them by substring and why `#42` (`blocked`, 22.91 M) and `#116` (`blocked`, 27.01 M) read alike
until a person reads the prose.

The decision recorded on the item (`ITEM_DECISION`, 2026-10-24T17:06:19Z, option 1) settles the arm this change
takes, and settles it *instead of* the three arms that were offered beside it: keep both stop rules and only
record a distinct stop reason per mechanism (`refused` / `unmergeable`), so the 221.7 M becomes attributable —
"no closure lost, no requirement reversed, the ~200 M stays". Ending the item at the refusing round (option 2),
replaying a branch whose base moved (option 3) and bounding the round after a refusal (option 4) are **not**
this change: 36 of the 49 closures (73 %) arrived in the round option 2 would remove, option 3 reverses
`worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom` (`openspec/specs/agent-issue-loop/spec.md:1272-1278`),
and option 4 cuts a legitimately slow correction. No stop rule moves here; what moves is what the record says
about one.

**Red case — taken on HEAD `85d02943` with the tree otherwise clean, 2026-10-24T23:34Z** (`evidence/red-cases-head.log`,
re-runnable as `bash evidence/red-cases-280.sh`), each case one named selection of `tools/loop-selftest.sh`:

```
=== state#aBlockedItemNamesTheMechanismThatStoppedIt
loop: done: unknown argument --stop-reason
FAIL state#aBlockedItemNamesTheMechanismThatStoppedIt
loop-selftest: 1 of 1 cases failed
=== run#reviewRoundsCapAtTwo
  and the record names the refusing round as the mechanism that stopped it (issue #280): expected 'refused', got 'null'
FAIL run#reviewRoundsCapAtTwo
loop-selftest: 1 of 1 cases failed
=== run#doesNotCloseOnAMergeTheBaseCannotTake
  and the record names the base's refusal as the mechanism that stopped it (issue #280): expected 'unmergeable', got 'null'
FAIL run#doesNotCloseOnAMergeTheBaseCannotTake
loop-selftest: 1 of 1 cases failed
=== report#wallTimeAndReasonInTheTable
loop: done: unknown argument --stop-reason
  and one for the mechanism a block stopped on (issue #280): '| stop |' not found in: | issue | class | verdict |
  change | root cause | red test | gate | review | wall | tools | tokens | context | outcome | reason |
FAIL report#wallTimeAndReasonInTheTable
loop-selftest: 1 of 1 cases failed
```

Four cases, each `1 of 1 cases failed` on HEAD. The first drives `state.sh` directly (a refused and an
unmergeable block, a block with neither, a token outside the vocabulary, a stop reason on a closure); the two
`run#` cases are the two mechanisms end to end through the engine; the fourth is the report's table, whose header
has no column for the mechanism.

**The owning requirement.** `openspec/specs/agent-issue-loop/spec.md` gains one requirement — *A block records the
mechanism that stopped it* — beside *A block asks its own question* (the same subject: what a block leaves
behind for a later reader). The guideline section is `guidelines/Backlog.md` §7 "The loop", whose paragraph on
the report (`guidelines/Backlog.md:496-508`) says what the per-item table answers; that paragraph gains the
mechanism beside the spend.

**The surface, counted** (`grep -rln 'stop-reason\|stopReason\|failed its last review round\|could not be merged
onto the base' tools .pi/skills openspec/specs guidelines` → 3 files today, `tools/loop/{run.sh,loop-cases.sh,
loop-mutations.sh}`, all of them the loop's own; `tools/loop/state.sh` is the fourth a field must land in, and it
holds no such text yet):

```
surface: 1 case added, 3 cases extended, 1 state field + 1 flag + 2 engine call sites, 1 report column,
3 mutation records, 1 guideline paragraph — 5 files hold the shape (tools/loop/{state.sh,run.sh,loop-cases.sh,
loop-mutations.sh}, guidelines/Backlog.md); command: grep -rln 'stop-reason|stopReason|failed its last review
round|could not be merged onto the base' tools .pi/skills openspec/specs guidelines
```

No fixture moves (the field is written, not read, by the fixtures) and no prompt under `tools/loop/prompts/` names
the mechanism.

## What Changes

- **`tools/loop/state.sh`** — `done` accepts `--stop-reason refused|unmergeable` (a closed vocabulary, refused
  outside it and on a result that is not a block) and records it as `.items[].stopReason`; the item table gains
  the mechanism as a column of its own, appended after `reason` so no earlier column moves.
- **`tools/loop/run.sh`** — the two sites that block on a mechanism name it: `refused` where the last review
  round refused the change, `unmergeable` where the base would not take the branch, and the blocked `done` call
  hands the value through. Every other block passes nothing and records nothing.
- **`tools/loop-cases.sh`** — one case, `state#aBlockedItemNamesTheMechanismThatStoppedIt`, and three cases
  extended to the mechanism their own block stopped on (`run#reviewRoundsCapAtTwo`,
  `run#doesNotCloseOnAMergeTheBaseCannotTake`, `report#wallTimeAndReasonInTheTable`, whose column count moves
  14 → 15).
- **`tools/loop-mutations.sh`** — three records (58.1–58.3), one per new invariant: the refusal site, the merge
  site and the state write.
- **`guidelines/Backlog.md`** §7 — the report paragraph says the table names the mechanism a block stopped on,
  beside the spend that mechanism cost.

**Additive, no breaking change.** A state written before this change carries no `stopReason`; every reader
(`state.sh report`, `report.sh`, `report.sh --json`, the pre-screen) either prints `-` for it or ignores it, so an
archived run reports exactly as it did. No stop rule changes: the two mechanisms block at the same lines, with
the same notes, the same escalation and the same closure paths — only the record gains the fact. Rollback:
revert this change's commit and the field disappears with its column; the four red cases reproduce
`1 of 1 cases failed` each.

**Not a native/JNI change, not an app change**: no `:app` source, no Kotlin, no resource, no build logic.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `agent-issue-loop` — one added requirement, *A block records the mechanism that stopped it*, with four
  scenarios (`specs/agent-issue-loop/spec.md`). No shipped scenario is modified or removed, so the delta is
  `ADDED` only.

## Impact

Production sources (tooling)
- `tools/loop/state.sh` — `--stop-reason` on `done` (parse, vocabulary, record) and the `stop` column
- `tools/loop/run.sh` — `stop_reason` at the two mechanism sites and on the blocked `done` call

Test sources (tooling)
- `tools/loop-cases.sh` — one case (list, dispatch, body), three cases extended
- `tools/loop-mutations.sh` — three records

Docs / specs
- `openspec/specs/agent-issue-loop/spec.md` (delta), `guidelines/Backlog.md` §7 (one clause)

Verification (each task's own step, `tasks.md`)
- the four red cases on HEAD and green on the tree (`evidence/red-cases-head.log`, `evidence/green-cases.log`)
- the three mutation records noticed by their named cases, restore verified (`evidence/revert-check.log`)
- `./tools/loop/gate.sh` once, its tallies copied into `evidence/gate/`
