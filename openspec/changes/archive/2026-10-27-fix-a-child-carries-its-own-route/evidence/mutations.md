# Revert-check — the two records this change adds

A shell gate has no build cache to force, so the mutation harness is the falsification: one record per invariant,
the named case must go red, the restore must go green again (`guidelines/Build.md` §1, `.pi/skills/revert-check`).

Command (from the change root):

```
bash tools/loop-mutations.sh --only "66.1 66.2"
```

Output (the harness's own scratch-directory line omitted):

```
loop-mutations: selected 2 of 182 records (labels: 66.1 66.2)
RED    66.1  adapter#theChildIsStartedWithoutContextFiles   (the context switch that keeps the project's own context files out of a child's prompt (issue #661))
restore 66.1 adapter#theChildIsStartedWithoutContextFiles green again
RED    66.2  prompt#thePhasePromptsCarryTheirOwnRoute   (where the capabilities live, in the route a child with no context files is given (issue #661))
restore 66.2 prompt#thePhasePromptsCarryTheirOwnRoute green again

red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0
```

What each record removes:

- **66.1** — `context=(--no-context-files)` becomes `context=()` in `tools/pi-child-events.sh`: the child is
  handed the project's `AGENTS.md` again, and the case notices because its assertion is the argv.
- **66.2** — `openspec/specs/` becomes `openspec/spec/` in `tools/loop/prompts/worker.md`: the route a child with
  no context files is given names a tree that does not exist, which is exactly what the case's path sweep reads.
