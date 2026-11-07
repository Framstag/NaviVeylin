# A finding is filed in a shape, and the writer refuses an incomplete one

## Why

`tools/issues/create.sh` already refuses everything it can decide about a finding: a duplicate fingerprint (exit 4),
a reserved label, a value outside the closed vocabulary, a `class`/`category` pair that contradicts the labels it
carries, a body that cites a path nothing resolves. What it does not know is **shape** — whether the item, as filed,
leaves an unattended worker something to decide.

That is the gap the tracker measures. Of 44 items on `status/on-hold`, five carry `agent/needs-decision` with a stated
question, and **39 carry a bare hold**: a verdict word (`unspecifiable`, `too-wide`, `not-spec-shaped`,
`needs-diagnosis`) that names no question and therefore cannot be answered (`tools/loop/prompts/worker.md`, gate 3;
`guidelines/Backlog.md` §5, "A hold is not a decision"). A worker discovered each of those gaps after it was
started, out of an item that was complete as far as the filing path was concerned.

**The absence.** `grep -n 'tier\|Open decisions\|check-change-readiness' tools/issues/*.sh
tools/issues/core/*.java` returns nothing: no field, no marker and no call in the filing path asks what a worker
would need. The declaration and the checker that read it are `change-readiness-gate` (C1), which has no caller.

**Acceptance scenario.** A finding that names no cause and no change is refused with a sentence naming the missing
half; a finding that carries a `file:line` and a red case is filed as tier A; a finding whose change exists and
passes the readiness checker is filed as tier B with its answered forks on the item as one comment, before any
state label moves. In each case the item the tracker holds is one a worker can act on without asking.

**Owning guideline.** `guidelines/Backlog.md` §6 "How an item becomes work" — the intake paragraph that says *"the
intake step is a write, so it is the filing path's job to make an item exist in a selector's queue: a finding
states its `class` and its `category`"*. This change extends that sentence to the shape. §6 is not reachable from
the routing table today, so `AGENTS.md`'s backlog row gains it.

**Surface.** Measured with `grep -rn 'tier\b\|change:' tools/issues/ guidelines/Backlog.md` (no hit for a tier
field), `ls tools/issue-tooling-cases.sh .pi/skills/create-issue/`:

surface: 1 case file extended, 0 rewritten, 4 code sites, 1 guideline section extended, 2 fixtures realigned —
`grep -rn 'tier\b\|check-change-readiness' tools/issues/ .pi/skills/create-issue/ guidelines/Backlog.md`

## What Changes

- **`tools/issues/create.sh`** — a required shape per tier, refused before any `gh issue create` is issued:
  - a finding carrying `cause` (file, line, case, tallies) is **tier A**, and its `file:line` must resolve — the
    citation gate the body already passes through;
  - a finding carrying `change` is **tier B**, and it is refused unless the change directory exists, the readiness
    checker reports `ok` for it, and the finding carries its answered `questions`;
  - a finding carrying neither is refused with `shape-missing`: the filing path names the two shapes and what each
    needs, because "it looked easy" is not a shape.
- **`tools/issues/lib.sh`** — two body-record keys through the existing `issues_body_set` writer, so writer and reader
  keep one spelling: `<!-- tier: a|b -->` and `<!-- change: <name> -->`. A reader is added beside them
  (`issues_tier_of`, `issues_change_of`).
- **`tools/issues/create.sh`** — the answered forks are written as **comments, before the label**: one comment
  carrying `**The decision:**` and its answer, then `agent/needs-decision` is not written at all (the item is not
  awaiting anything), and the record the worker reads (`<<<ITEM_DECISION`) is the one `tools/issues/answer.sh`
  already produces.
- **`tools/tooling.json`** — a `checks.changeReady` path key. An **empty** value refuses the tier-B shape rather
  than filing it unchecked, the convention `LOOP_UNTRACKED_RECORD_DIR` already uses for an empty path.
- **`.pi/skills/file-finding/`** — the thin skill the sessions call: derive the shape from what the session
  produced, call `create.sh`, report the refusal verbatim. It decides nothing the writer can decide.
- **`guidelines/Backlog.md`** §6 and **`AGENTS.md`**'s backlog routing row.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `backlog-source-of-truth`: four added requirements — the two shapes and the writer's refusal of an incomplete one,
  what each shape owes, the record of the answered forks, and the configuration path through which the readiness
  gate is reached. No shipped requirement's text changes: the added requirements constrain *what the filing path
  accepts*, which no existing requirement in this capability states.

## Impact

| path | what changes |
|---|---|
| `tools/issues/create.sh` | the shape refusal, the cause/change/decision fields, the gate call, the exemption for the reconciler |
| `tools/issues/lib.sh` | `tier` and `change` body-record keys, their readers, `issues_changes_dir` |
| `tools/issues/core/Profile.java` | a `checks.<key>` reader, so the gate's path is a profile value and not engine text |
| `tools/issues/import.sh` | files with `--import`: a historical record's exemption, declared on the item |
| `tools/tooling.json` | `checks.changeReady` |
| `.pi/skills/file-finding/SKILL.md` | new — the session's filing verb |
| `tools/issue-tooling-cases.sh` | new cases: a refused shape, a filed tier A, a filed tier B, an empty gate path, the importer's exemption |
| `tools/issues/metadata-mutations.sh` | one record: the shape key is written by the record's one writer |
| `tools/fixtures/foreign-project/` | the fixture findings carry a cause, and the foreign profile declares `checks` |
| `guidelines/Backlog.md` | §6, the intake sentence extended to the shape |
| `AGENTS.md` | the backlog routing row names §6 |
| `openspec/specs/backlog-source-of-truth/spec.md` | four added requirements, after archive |

Additive for a caller that passes a shape; **breaking for a caller that passes none**, which is the point: an
existing caller of `create.sh` that supplies neither a cause nor a change now exits 3 instead of creating an
item. Rollback path: revert the change; the four cases go red, and the filing path accepts shapeless findings
again. The tracker needs no data migration — no filed item is rewritten by this change.

Scope: **general process tooling** — no `:app`, `:auto`, `:core`, `buildSrc`, Gradle, manifest, resource or native
path. No submodule patch, no bridge override.

## Not in this change

- **The readiness checker itself** — `change-readiness-gate` (C1), which this change calls and does not modify.
- **What the loop does with a tier-B item** — `loop-consumes-a-prepared-change` (C3). Until C3 lands, a tier-B item
  is filed correctly and the worker still plans its own change; the recorded decision keeps it from re-deriving
  the fork, which is the half of the behaviour that exists today.
- **The shaping inbox** — `shaping-inbox` (C4), for the tier-A item a worker later promotes.
- **Re-shaping the 39 bare holds.** They were filed before the shapes existed and stay as they are; C4 gives their
  reader a queue, and re-diagnosing them is `cleanup-todo`'s work.
- **Rewriting the reconciler.** `tools/issues/import.sh` keeps working through the declared exemption (its findings
  are historical records, so the item says `exempt: import <sourceId>`); a rewrite for a project adopting the
  tooling is a later change. Retiring it is not free: `tooling-portability` states that the per-project files are
  "the source adapter that reads this project's own record format", so it owes that capability a delta.
