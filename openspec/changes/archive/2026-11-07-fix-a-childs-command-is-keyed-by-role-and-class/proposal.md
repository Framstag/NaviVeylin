# A child's command is keyed by its role and by the item's class

## Why

`run.sh` derives a child's role from the prompt file's name (`child_role`, `tools/loop/run.sh:1382`) and, until this
change, resolved the command that starts it from that role's key alone (`run_child`, `tools/loop/run.sh:1394`:
`LOOP_AGENT_CMD_<ROLE>`, else `LOOP_AGENT_CMD`). Three properties of the loop were therefore not configurable, and each
one is measured or counted in the tree:

1. **A role has no head.** The run's header loop read the five roles the engine starts — `WORKER WORKER_GATE
   WORKER_TAIL REVIEWER REVIEWER_PRE_GATE` (`run.sh:237` before this change) — while the block `A command per role` in
   `tools/loop/loop.conf` recorded three keys (`WORKER`, `WORKER_TAIL`, `REVIEWER`). The round before the gate is
   `reviewer-pre-gate` (the `*pre-gate*` arm of `child_role`) and took `LOOP_AGENT_CMD`, never
   `LOOP_AGENT_CMD_REVIEWER`: the round that reads the largest diff of an item ran on the baseline command while the
   round after it ran on the recorded cheaper one.
2. **The rework round is the worker.** The round a refusal buys is rendered from `prompts/worker.md` into
   `worker-prompt-round2.md` (the round-2 render, `run.sh:2679`), whose name resolved to role `worker`
   (`child_role`'s `worker-*` arm) — so a pass that answers a findings list and plans nothing was priced and bounded as
   a first pass (`LOOP_CHILD_TURNS_MAX_WORKER=200`, `tools/loop/loop.conf:162`). Measured over the archive: 109 rework
   sessions, min 11 turns, median 60, p95 100, max 197 (this change's `evidence/round2-turns.log`).
3. **Nothing but the name is read.** The item's class is read from its `class/<value>` label before any child starts,
   and it reached the worker's prompt (`CLASS` placeholder) and no configuration key.

**Root cause, in one sentence:** the command that starts a child is read from a key derived from one string — the
prompt's name — while the three dimensions that decide what the child costs (the role it plays, the class of item it
works, the head role it belongs to) are known to the run before it forks and none of them can reach the key.

**Why it is a defect and not a preference:** `tools/loop-cases.sh:1728` pins the current shape
(`state#aRolesOwnCommandStartsOnlyThatRolesChild`): the reviewer's key is set, and the assertion "every other child
went through the project's one" is what makes the pre-gate round's command invisible. A project cannot put the round
that reads its largest diff on its recorded model without recording the key twice, and the run's own header
(`agent-roles=`, the role loop at `run.sh:267-272` now and `run.sh:237` before this change) named only the roles whose
key was recorded, so a role that inherited was named nowhere.

**The red test, before any production code:** three cases fail on HEAD for the reason each one names, and the rewritten
fourth fails on the header it asserts — the logs are in this change's `evidence/`, one per case, taken 2026-11-07:

- `state#aRolesChildInheritsTheCommandOfTheRoleItBelongsTo` — "the pre-gate round ran on the command of the role it
  belongs to, recording no key of its own: expected '1', got '0'"
  (`red-on-HEAD-state-aRolesChildInheritsTheCommandOfTheRoleItBelongsTo.log`).
- `state#theReworkRoundIsARoleOfItsOwn` — "the rework round was started on its own role's command: expected '1', got
  '0'" (`red-on-HEAD-state-theReworkRoundIsARoleOfItsOwn.log`).
- `state#anItemsClassSelectsTheWorkerCommand` — "the bug item's worker pass ran on the command its class records:
  expected '1', got '0'" (`red-on-HEAD-state-anItemsClassSelectsTheWorkerCommand.log`).
- `state#aRolesOwnCommandStartsOnlyThatRolesChild` — "'agent-roles=reviewer,reviewer-pre-gate' not found in: …
  agent-roles=reviewer" (`red-on-HEAD-state-aRolesOwnCommandStartsOnlyThatRolesChild.log`).

Owning capability: `agent-issue-loop`. Owning guideline section: `guidelines/BacklogTooling.md` "Where an agent
runs" — its table records `LOOP_AGENT_CMD` and the five per-role turn bounds and no per-role command key at all.

surface: 3 cases added to the `state` group, 1 case rewritten, 1 requirement added with 4 scenarios, 4 sites in
`tools/loop/run.sh` (the header's role loop, `child_role`, the command resolution, and the new key resolver), 5
configuration keys for this project (`LOOP_AGENT_CMD_WORKER_GATE`, `LOOP_AGENT_CMD_WORKER_REWORK`,
`LOOP_AGENT_CMD_REVIEWER_PRE_GATE`, `LOOP_CHILD_TURNS_MAX_WORKER_REWORK`, plus the class suffix the keys gain), 1
mutation record, 1 guideline table. Counted with `tools/look.sh 'LOOP_AGENT_CMD'` and
`bash tools/loop-selftest.sh --list | cut -d'#' -f1 | sort | uniq -c`.

## What Changes

1. **A role's key, its head's key, then the project's.** `child_agent_cmd_key <role> <class>` walks four candidates
   in order and returns the first one that records a command: `<ROLE>_<CLASS>`, `<ROLE>`, `<HEAD>_<CLASS>`, `<HEAD>`,
   and `LOOP_AGENT_CMD` when none does. `<HEAD>` is the role's name up to its first hyphen (`reviewer-pre-gate` →
   `reviewer`, `worker-gate` → `worker`), so a `REVIEWER_*` role inherits the reviewer's command by one rule and not
   by a second mechanism. `run_child`'s existing resolution keeps its shape (`run.sh:1385-1389`) — only the key it
   reads changes.
2. **The rework round is a role of its own.** `worker-rework`, read off the artifact the run already renders
   (`*round2*`), with `LOOP_AGENT_CMD_WORKER_REWORK` and `LOOP_CHILD_TURNS_MAX_WORKER_REWORK`. Its head is `worker`,
   so a project that records nothing for it keeps the worker's inheritance and the project's command.
3. **The item's class can select the worker's command.** `LOOP_AGENT_CMD_WORKER_<CLASS>` is tried first for the
   worker role, with the class read exactly as the prompt already carries it (`run.sh:1845`). The chain keeps the
   most specific key winning: a recorded `LOOP_AGENT_CMD_WORKER_TAIL` still beats `LOOP_AGENT_CMD_WORKER_<CLASS>` for
   the tail, because the class key is a refinement of the *role* it is spelled after.
4. **The header names the roles that do not run on the project's command.** `agent-roles=` is computed from each
   role's effective key rather than from the recorded value, so `agent-roles=reviewer,reviewer-pre-gate` says which
   children a run starts on something other than `LOOP_AGENT_CMD`. The class dimension is per item and the header is
   per run; the class key is therefore named by the child's own transcript and not by the header.

## Impact

- Engine: `tools/loop/run.sh` only (one new resolver, one new `child_role` arm, one arm added to the header loop, one
  line of `run_child`).
- Configuration: `tools/loop/loop.conf` gains three role keys and one turn bound; no key changes value for this
  project except the pre-gate round, which moves to the reviewer's recorded command because it inherits it.
- Cases: 3 added (`state` group), 1 rewritten (`state#aRolesOwnCommandStartsOnlyThatRolesChild`, whose "every other
  child takes the project's one" is no longer the whole truth).
- Guideline: `guidelines/BacklogTooling.md` "Where an agent runs" table, one row for the role/class keys and the rework role in the
  turn-bound row.
- No new seam: the resolution stays one function, and a project that records nothing still runs every child through
  `LOOP_AGENT_CMD`.
