# Design

## Context

`run_child` (`tools/loop/run.sh:1394`) starts every child of a run. It resolves the command in five lines
(`run.sh:1422-1427`): the project's command, then the role's key if it records anything. The role comes from
`child_role` (`run.sh:1382`), which reads the prompt file's name; the prompt file is the one thing every phase already
renders and the one thing a phase cannot silently change without being seen in the item's directory.

Three dimensions of the child are known before the fork and cannot reach those five lines:

| dimension | read today at | reachable today |
|---|---|---|
| the role | `child_role`, `run.sh:1382` | yes — the key is derived from it |
| the head role it belongs to (`reviewer-pre-gate` → `reviewer`) | nothing reads it | no |
| the item's class | the item loop's `class/<value>` label read, before any child | only into the prompt's placeholders |

## Goals / Non-Goals

**Goals.** One rule that resolves a child's command from the role, the role's head and the item's class, replacing
the role-only rule; the rework round as a role with its own two keys; the header naming the roles that do not run on
the project's command; one case per delta, each red on HEAD for its own reason.

**Non-Goals.** No change to what a role's *turn* bound means (it is read through the same `child_role`, so the new
role gets a bound key by the same transformation); no new configuration file, no new seam, no change to the prompt
artifacts the phases render; nothing that decides a model.

## Decisions

### D1 — one chain, most specific first

`child_agent_cmd_key <role> <class>` returns the first key that records a non-blank value:

```
LOOP_AGENT_CMD_<ROLE>_<CLASS>   the role, refined by the item's class
LOOP_AGENT_CMD_<ROLE>           the role
LOOP_AGENT_CMD_<HEAD>_<CLASS>   the head role, refined by the item's class
LOOP_AGENT_CMD_<HEAD>           the head role  — where REVIEWER_PRE_GATE inherits REVIEWER
LOOP_AGENT_CMD                  the project's one command
```

`<HEAD>` is the role up to its first hyphen. The transform is the one the engine already applied to a role
(`tr '[:lower:]-' '[:upper:]_'`, now inside `child_agent_cmd_key`, `run.sh:247`), extended to the class, so a key's
spelling is derived and not invented. `run_child` keeps its shape: `child_cmd="$LOOP_AGENT_CMD"`, then the if-line that
takes the resolved key — which is also the line the mutation harness quotes (`tools/loop-mutations.sh:772`), so no
existing record is invalidated.

### D2 — the rework round's role is read off the artifact the run renders

`child_role` gains one arm, `*round2*` → `worker-rework`, placed after `*-tail*` (a rework tail's prompt,
`worker-tail-round2-prompt.md`, is still the tail), `*pre-gate*` and `*gate*`, and before `worker-*`. The prompt the
round is handed is `worker-prompt-round2.md`; the arm reads the name the run already renders, so no artifact is
renamed.

### D3 — the class key refines the role it is spelled after

`LOOP_AGENT_CMD_WORKER_<CLASS>` is tried for the worker role and for the roles whose head is the worker (gate, tail,
rework), because the chain applies `<HEAD>_<CLASS>` at the head step. A role's own key still wins: a project that
records `LOOP_AGENT_CMD_WORKER_TAIL` keeps the tail on it whatever class the item carries.

### D4 — the header names effective roles, not recorded values

`agent-roles=` (`run.sh:267-272`) loops over the six roles and now compares each role's resolved key with
`LOOP_AGENT_CMD` instead of testing the role's own value. This project therefore prints
`agent-roles=reviewer,reviewer-pre-gate`. The class dimension cannot be printed: the header is written once per run,
before the first item's class is read, so a class-qualified command is named by the child's own transcript and the
header stays a claim about roles.

### D5 — the recorded bound for the new role is the archive's p95 for it

`LOOP_CHILD_TURNS_MAX_WORKER_REWORK=100`: 109 rework sessions in the archive, min 11, median 60, p95 100, max 197
(`evidence/round2-turns.log` of this change, and the item's own copy; taken 2026-11-07 with `jq`/`awk` over every
`events.jsonl`). The worker's 200 is not reused: it is another population's p95, and a bound copied from it would be a
number nobody took.

## Open decisions

none

The chain (D1), the new role (D2) and the class key's precedence (D3) are settled above; the alternatives beside them
are the ones the tree eliminated, and none of them is a choice this change leaves to its reader.

## Alternatives eliminated

1. **Inherit for `reviewer-*` only.** The defect names the reviewer family because that is where the measurement is,
   but a rule keyed on a role's name for one family is a second rule beside the mechanical one; the head rule covers
   both families with one expression and gives the gate child the worker's command for the same reason.
2. **Rename the rework prompt to `worker-prompt-rework.md`.** The role would then be carried by the artifact's name
   without an arm that reads a round. It realigns the nine lines `tools/loop-cases.sh` already spends opening
   `worker-prompt-round2.md` by name (`grep -c 'worker-prompt-round2' tools/loop-cases.sh`: 12 with this change's
   three, 9 before it), and the item's own directory naming, for no behaviour the arm does not already give.
3. **A separate class key shape** (`LOOP_AGENT_CLASS_CMD_<CLASS>`). A second key shape for one dimension, beside the
   one the role already uses; the suffix reuses both the key and the transform the engine already applies.
4. **Let the class key outrank a role's own key.** `WORKER_<CLASS>` above `WORKER_TAIL` would put the less specific
   key first, so a project's recorded tail command would be silently overridden by a class it never mentioned. The
   chain keeps the role's own key ahead of its head's class key.
5. **Copy the worker's 200-turn bound onto the rework role.** See D5.

## Blast radius

- `tools/loop/run.sh`: the header's role loop (267-272), `child_role` (one arm, 1382-1391), a new resolver beside the
  header (the role loop reads it, so it is defined above it, 247-262), and the key `run_child` reads (1425). No other
  phase changes.
- `tools/loop/loop.conf`: five lines (three command keys, one turn bound, plus the class-suffix comment).
- `tools/loop-cases.sh`: three cases added, one rewritten (`state#aRolesOwnCommandStartsOnlyThatRolesChild`, whose
  `agent-roles=reviewer` becomes `reviewer,reviewer-pre-gate`).
- `tools/loop-mutations.sh`: one record, for the resolution chain.
- `guidelines/BacklogTooling.md` "Where an agent runs": the table gains the role/class key and the rework role.
- Nothing else reads `LOOP_AGENT_CMD_<ROLE>`: `tools/look.sh 'LOOP_AGENT_CMD'` lists `run.sh`, `loop.conf`,
  `loop-cases.sh`, `tools/pi-child-events.sh` (a comment), `PORTING.md`, `AGENTS.md` and the foreign-project fixture,
  none of which resolve a command.

## Rollback

Revert the diff. The keys are empty or absent for a project that records none, so every child falls to
`LOOP_AGENT_CMD` exactly as before; the only behaviour a revert changes back is the pre-gate round's command in a
project that records `LOOP_AGENT_CMD_REVIEWER`, and the header's role list.

## Risks / Trade-offs

- **A role with a set head key inherits it.** A project that records `LOOP_AGENT_CMD_WORKER` now starts its gate and
  tail children on it, where they previously took `LOOP_AGENT_CMD`. That is the rule D1 states and the reason the
  header names both roles; this project records neither key, so its own children do not move.
- **The header is one line per run.** A class-qualified command is invisible in it; the transcript of the child is
  where it is named.
