# Proposal

## Why

The queue currently ranks every item of a leading category (`build-and-harness`, `verification`,
`specs-and-process`) ahead of every app item, and the loop refuses `class/feature` outright
(`tools/tooling.json` → `roles.workedClasses`, `roles.refusedClasses`). That was the right policy while the
harness itself was being repaired: a broken `prescreen.sh` is paid again by every item after it. It is the
wrong policy now.

Measured on the profile as it stands:

```
$ jq '.roles.workedClasses, .roles.firstCategories, .roles.refusedClasses' tools/tooling.json
["bug","improvement"]
["build-and-harness","verification","specs-and-process"]
["feature"]
```

The consequence is that a harness **improvement** — a change that makes the tooling nicer but breaks nothing —
outranks an app bug, and a `class/feature` item can never be worked at all. The backlog owner wants the loop
back on app work: only harness defects should lead, harness improvements should be mixed in at a small rate,
and features should be planned and applied by the loop.

## What Changes

- `tools/tooling.json` → `roles`: `workedClasses` gains `feature`; `refusedClasses` is empty; two roles are
  added — `firstCategoryClasses: ["bug"]` (the leading categories lead on defects only) and
  `firstCategoryEvery: 4` (one leading-category item of another class after every four other items).
- `tools/loop/prescreen.sh`: the queue is ranked in three streams — leading-category defects first, then the
  remaining items in class order, with one leading-category non-defect mixed in after every
  `firstCategoryEvery` item.
- `tools/loop/run.sh`: the item's class is read from its labels and handed to the worker as `{{CLASS}}`.
- `tools/loop/prompts/worker.md`: a `feature` takes the propose-first path — an `add-<slug>` OpenSpec change
  (proposal, capability delta, design, tasks) whose acceptance scenario is the red case, before production
  code. The gate table is read per class; a feature names a missing capability rather than a root cause.
- `tools/loop/prompts/reviewer.md`: item 2 accepts a named missing capability as well as a root cause.
- `tools/loop/loop.conf`: the worker is told about `openspec-propose` as well as `openspec-apply-change`.
- `.pi/skills/fix-loop/SKILL.md`, `REFERENCE.md`: reduced to pointers — the eligibility table the worker
  applies is `tools/loop/prompts/worker.md`, the ranking rule is the spec delta below, and the skill keeps the
  measurements and the evidence script.
- `openspec/specs/agent-issue-loop`: the queue requirement is modified, and a feature requirement is added,
  each with its cases.
- `tools/loop-cases.sh` and its fixtures: the ranking, the mixing interval, a refused class, a feature's
  prompt path and the new role's absence are cases.

Not part of this change: the class order itself (`bug`, `improvement`, `feature`) and the branch prefix
`fix/issue-*`, which stays the run's branch name for every class.
