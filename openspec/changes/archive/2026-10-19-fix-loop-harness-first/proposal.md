# fix-loop-harness-first

## Why

The pre-screen ordered the queue by two keys only: the class's place in the profile's `workedClasses`
(`tools/tooling.json:41` → `bug`, `improvement`) and then the issue number — `sort_by([._rank, .issue])` inside
`tools/loop/prescreen.sh:330-341`. The category a defect sits in was read, recorded and printed, and never ranked
on — so a harness improvement queued behind every app bug, and a harness bug behind every app bug older than
itself.

Measured 2026-10-19 from `tools/loop/run.sh`'s own state (`.pi/issue-loop/run.json`, produced by the dry run this
change's evidence quotes): **19** eligible `category/build-and-harness` items — four bugs at ranks 1–4 (#126, #189,
#190, #198) and 15 improvements — and **every app item after them**, the oldest app bug #131 at rank 37 and the
app improvement #7 at rank 38. A harness defect is not one defect: it is paid again by **every** item after it,
and a defect in `prescreen.sh`, `labels.sh`, `state.sh`, `close.sh` or a sensor can make the run's own report, and
therefore the run's own closure decision, false. The ordering was written when the loop was younger than its own
backlog; the backlog has since grown a tooling layer the loop depends on.

## What changed

- A new profile role, `firstCategories`, under `tools/tooling.json` → `roles`: the category values whose items
  lead the queue. This project declares `build-and-harness`, `verification`, `specs-and-process`; a foreign
  project declares its own (`tools/fixtures/foreign-project/tools/tooling.json` → `infra`), or an empty list to
  keep the old order.
- `tools/loop/prescreen.sh` sorts on the new key **first**: `[category leads, class order, issue number]`, so a
  harness bug, then a harness improvement, then an app bug, then an app improvement, oldest first inside each
  group. The role is read through `loop_role` like every other decision (`tools/loop/lib.sh`), so no engine
  spells a project value and `tools/check-portability.sh` keeps proving it.
- A new fixture scenario `categories` and the cases that assert the order, plus the case that asserts the new key
  is a **rank**, not a gate: a harness item that is on hold or without the opt-in is still excluded.
- The two places that state the order in prose (`.pi/skills/fix-loop/SKILL.md`, `REFERENCE.md`) name the category
  key beside the class key.

## Spec

`openspec/specs/agent-issue-loop/spec.md` — one ADDED requirement, "The queue repairs the tooling the run
depends on first", under the existing capability; the requirement this change sharpens ("The run acts only on
authorised, eligible items") is not rewritten, because the gate is unchanged — only the order is.

## Cases and evidence

Red on HEAD: `prescreen#harnessItemsLeadTheQueue` — the harness bug is ranked last on HEAD and first on the fix.
Green: `prescreen#priorityCategoriesAreReadFromTheProfile` (a profile with an empty list keeps the class order),
`prescreen#priorityDoesNotBypassTheGate` (a harness item on hold is still excluded), and the existing
`prescreen#ranksBugsFirstAndDropsFeatures`, which must stay green: the key is added *before* the class key, not
instead of it. Mutation 22.1 drops the category key from the sort and must fail the first of them.

- `evidence/red-on-head.txt`, `evidence/green-on-fix.txt`, `evidence/revert-check.txt`, `evidence/gate.txt`

## Impact

- `tools/loop/prescreen.sh` (the sort), `tools/loop/lib.sh` (`LOOP_ROLE_KEYS`), `tools/tooling.json` (the role),
  `tools/fixtures/foreign-project/tools/tooling.json` (the foreign value)
- `tools/loop/fixtures/scenarios/categories/issues.json` (new), `tools/loop-cases.sh`, `tools/loop-mutations.sh`
- `.pi/skills/fix-loop/SKILL.md`, `.pi/skills/fix-loop/REFERENCE.md`
- `openspec/specs/agent-issue-loop/spec.md`

Additive, not breaking: the sort gains a key, no verdict, label, field or gate changes, and the state file's
`rank` is re-derived by every pre-screen rather than carried, so a run in flight needs no migration. Rollback is
a revert of the one commit; a queue already built by the new order keeps its ranks until the next pre-screen,
which recomputes them.

Out of scope, deliberately: the `prio/*` bands. They are a second ordering signal (`tools/issues/prioritise.sh`)
that the loop has never read, and mixing two orderings in one change would leave neither falsifiable. The loop
orders by what it can read from the labels it already reads.
