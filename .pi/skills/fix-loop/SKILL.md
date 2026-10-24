---
name: fix-loop
description: 'Reference for the budgeted fix loop (`tools/loop/run.sh`): where the loop''s contract, the worker''s contract and the eligibility gate live, and the script that checks a change''s evidence pointers. The entry is the script, not this file.'
---

# fix-loop

A **reference, not the entry**. The loop is a script — `tools/loop/run.sh --items N --minutes M`
(`--help` lists every flag) — and its contract is the spec `agent-issue-loop`
(`openspec/specs/agent-issue-loop/spec.md`).

| what | home |
|---|---|
| the run's requirements — budget, stop conditions, closure, the report, the worktree, the leftovers | the spec `agent-issue-loop` |
| what a child is told, **including the eligibility gate and the evidence rules** | `tools/loop/prompts/worker.md`, `tools/loop/prompts/reviewer.md` |
| the mechanics | `tools/loop/` |
| this project's values — gate command, worker skills, paths, budgets | `tools/loop/loop.conf`, `tools/tooling.json` |
| the mechanical check of a change's evidence pointers | `scripts/evidence-check.sh` |
| measured costs, the field notes, and what changed when the loop became a script | `REFERENCE.md` |

`scripts/evidence-check.sh` resolves every `file:line`, every quoted `tests=`/`failures=`, every `/tmp`
pointer and every `§N` in a change's markdown against the recorded `§ → issue` table, and checks that no
`REVERT-CHECK MUTATION` marker survived in the tree. Run it before the gate and again before archive. The run
copies this skill into its worktree (`tools/loop/worktree.sh`), so a child can run it there.

**Retired 2026-10-22** — the forwarder `scripts/loop-state.sh` and its self-test are gone; the only writer of
the run's state is `tools/loop/state.sh`. `REFERENCE.md` records what changed and why.
