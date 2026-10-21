# Design — the change names its surface before its plan is fixed

## Root cause

The iteration protocol's first rule measures the **symptom** of a change — look at the artifact, add a
coordinate-free diagnostics line, measure with the `pixel-check` script (`guidelines/Build.md:121`) — and the
rules after it bound the **iteration** — focused suites and one gate (rule 2), batched questions (rule 3), one
builder per tree (rule 4), a reviewer that only reads (rule 5). None of them measures the change's own reach.
So the plan is fixed, the budget is set against the first file the plan names, and the cases and fixtures that
assert the behaviour being rewritten are discovered one red case at a time. That is the mechanism the item
records: a plan of "two functions" that realised 292 insertions over six files, ~60 % of it test surface.

## The single fix

One sentence added inside rule 1 of `guidelines/Build.md` §2, and the same pass stated in the loop's worker
task, so the two surfaces a change meets carry one rule:

1. `guidelines/Build.md` §2 rule 1 — after the symptom measurement: **count the surface** before the plan is
   fixed, by `grep -l` over the cases (`tools/*-cases.sh`), the engine, the prompts and the fixtures; the count,
   not the first file the plan names, is what the plan's estimate is set against; the proposal carries it as
   `surface: … — <command>` so the next reader repeats it rather than trusting it.
2. `tools/loop/prompts/worker.md` Phase A — a fifth step, "Count the surface before the plan is fixed", with the
   search, the `surface:` record and the #232 measurement as the reason it exists.
3. `openspec/specs/agent-issue-loop/spec.md` — the requirement "A change names its surface before its plan is
   fixed", with one scenario per surface and one case per scenario.
4. `tools/loop-cases.sh` — `prompt#workerNamesTheChangeSurfaceBeforeThePlan` (the rendered task) and
   `prompt#iterationProtocolNamesTheChangeSurface` (the guideline), both in the `prompt` group.
5. `tools/loop-mutations.sh` — `38.1`, which deletes the pass from the worker task; the first case must go red
   and green again on restore.

## Blast radius, measured before the plan was fixed

```
$ grep -rln 'iteration protocol\|Agent iteration protocol' tools/ .pi/skills/ AGENTS.md openspec/specs/ guidelines/
tools/loop-cases.sh, .pi/skills/run-tests/SKILL.md, AGENTS.md, openspec/specs/agent-issue-loop/spec.md, guidelines/Build.md
$ grep -rn 'rules 1-3\|rule 4' --include=*.md --include=*.sh tools/ .pi/skills/ AGENTS.md guidelines/ openspec/specs/
.pi/skills/mutation-runner/SKILL.md:98, AGENTS.md:185, AGENTS.md:186
$ grep -rn 'rule 2' --include=*.md openspec/changes/fix-mutation-harness-file-scope/
proposal.md:29, proposal.md:60, tasks.md:40
```

Files edited: 5 (`guidelines/Build.md`, `tools/loop/prompts/worker.md`, `tools/loop-cases.sh`,
`tools/loop-mutations.sh`, `openspec/specs/agent-issue-loop/spec.md`) plus this change's artifacts. Cases
added: 2. Cases rewritten: 0. Fixtures re-aligned: 0. Rule numbers changed: 0 — which is the point of the
measurement, because two documents and one open change cite a protocol rule by number (`AGENTS.md:185-186`
"rules 1-3" and "rule 4", `.pi/skills/mutation-runner/SKILL.md:98` "rule 4", and the open change
`fix-mutation-harness-file-scope`, which names "rule 2"), and a new number ahead of the cited rules would
renumber them with no case noticing.

## The alternatives the evidence eliminated

- **Appending the pass as rule 6.** It would keep every citation valid, but it puts a plan-time rule after the
  reviewer rule, so the protocol would read "gate first, then plan your change's surface". Rejected: the rule
  has to precede the plan, which rule 1 already governs.
- **A new guideline section of its own.** Rejected by the capability's own rule that a gate rule and a
  measurement have one documented home (`openspec/specs/build-test-gate/spec.md`, "A gate rule or measurement
  has one documented home"): the protocol is that home, and a second section would be the restated copy that
  requirement removes.
- **A helper script that prints the surface** (`tools/surface.sh`). Rejected as scope this item did not ask for
  and as a second thing to keep green: the rule is a measurement the planner takes with tools it already has,
  and the count is what changes the budget, not the formatting of the command that produced it.
- **A per-change checklist in the worker task only, without the guideline.** Rejected: a session outside the
  loop never reads the loop's task, and the #232 change was planned by such a session — the guideline is where
  `AGENTS.md` routes a session for the iteration loop.

## Rollback

Revert the five edited files. The change adds one sentence to a guideline, one step to a task template, two
cases and one mutation; no compiled path, no state, no runtime surface. A rollback leaves the project byte for
byte at `80df3a3` for every file it touched, and `tools/loop-selftest.sh` green throughout.

## Falsification

Mutation `38.1` (`tools/loop-mutations.sh --only 38.1`): the pass removed from the worker task →
`RED prompt#workerNamesTheChangeSurfaceBeforeThePlan`, restore → `green again`, `not-noticed: 0`,
`restore-failures: 0` (`evidence/mutation-38.1.log`).

The guideline half has no mutation, and that is a limit of the harness, not an omission: `tools/loop-mutations.sh`
resolves every record inside the engine (`rel="${file#tools/loop/}"`, so `$engine/$rel`), so a mutation cannot
reach `guidelines/Build.md`. Its load-bearing half is the red run recorded in `evidence/red-before-fix.log`:
with the sentence reverted from the tree by hand, `prompt#iterationProtocolNamesTheChangeSurface` fails on
exactly that assertion and passes again after the byte-identical restore.
