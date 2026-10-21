# fix-iteration-protocol-surface-step

## Why

Issue #238. Verified in the loop's worktree on 2026-10-22, on the run's base commit `80df3a3`.

**The claim holds, at one line and in one absence.** `guidelines/Build.md:121` opens the iteration protocol
with rule 1, "Look, then measure, before changing code" (`Build.md:98` is the subsection heading, rule 1 at
`:121`): it measures the **symptom** — a pixel artifact, a coordinate-free diagnostics line, the `pixel-check`
script's numbers — and the rules after it measure the **iteration levers** (rule 2 focused suites, rule 3
batched questions, rule 4 one builder per tree, rule 5 a reviewer that reads). No rule measures the change's own
**reach**: which cases, fixtures and prompt or engine files assert the rule being rewritten. Before this change
`grep -n 'count the surface\|blast radius\|grep -l' guidelines/Build.md` returned nothing, and the one place the
concept exists — `tools/loop/prompts/worker.md:80` — asks for it as a section of `design.md`, i.e. after the
plan's files are already fixed.

**The consequence is the item's own observation.** The #232 change was planned as "one attribution fix plus one
verdict split" — two functions in `tools/loop/worktree.sh` — and landed **292 insertions over six files**,
~60 % of it test surface: two cases added, two rewritten, one renamed, and four existing fixtures re-aligned
(`(#21)` in the adopt cases' commit subjects, an archived change in
`prescreen#escalatedItemKeepsItsBranchAsEvidence` and `worktree#refusesABranchCarryingAPreviousAttempt`, an
archive-less branch in the run case). ≈55 min planned against ≈100 min realised, a factor of ~2. The estimate
was made before anyone asked how many places assert the old rule; the answer was one `grep -l` away.

**Root cause, one sentence:** the iteration protocol measures a change's symptom and its iteration levers but
never its surface, so the plan — and with it the budget, the review scope and the loop's floor — is fixed before
anyone counts the cases and fixtures that assert the behaviour being changed.

**Red tests on HEAD, before the fix.** Two cases in `tools/loop-cases.sh`, run one at a time with
`bash tools/loop-selftest.sh --case '<case>'`; both fail on HEAD because the text they assert is absent:

```
$ bash tools/loop-selftest.sh --case 'prompt#workerNamesTheChangeSurfaceBeforeThePlan'
  the worker counts the surface before it plans: 'count the surface' not found in: # Worker — one item, phases A–D
FAIL prompt#workerNamesTheChangeSurfaceBeforeThePlan
EXIT=1                       # evidence/red-before-fix.log, run copy written 2026-10-22T00:42:41+02:00

$ bash tools/loop-selftest.sh --case 'prompt#iterationProtocolNamesTheChangeSurface'
  the iteration protocol does not state the surface measurement
FAIL prompt#iterationProtocolNamesTheChangeSurface
EXIT=1                       # same log, same run
```

Both are green after the fix (`evidence/green-after-fix.log`, 2026-10-22T00:43:12+02:00), together with the two
neighbouring prompt cases the changed text sits beside.

**The project's gate produces no JUnit XML for this delta, and none is owed.** No compiled file changed: the
delta touches `guidelines/Build.md`, `tools/loop/prompts/worker.md`, `tools/loop-cases.sh`,
`tools/loop-mutations.sh` and `openspec/**`, all of them in the protocol's "no compiled file" class
(`Build.md:109`). `bash tools/loop/gate.sh --plan --base HEAD` names exactly the two structural suites the
change owes — `tools/loop-selftest.sh` and `tools/check-doc-routes.sh` — and no Gradle task
(`evidence/gate-plan.log`). Their own summary lines are the numbers quoted; `tools/loop-selftest.sh` is a shell
harness that prints one `PASS`/`FAIL` per case, so a JUnit-shaped tally is derived from its summary exactly as
`tools/loop/gate.sh` derives one (`evidence/loop-selftest.xml`, derivation recorded in its `system-out`).

**Surface — the record the new rule asks for, measured before this plan was fixed:**

```
surface: 2 cases added, 0 rewritten, 0 fixtures realigned — 1 rule sentence added in one place, 2 surfaces
$ grep -rln 'iteration protocol\|Agent iteration protocol' tools/ .pi/skills/ AGENTS.md openspec/specs/
tools/loop-cases.sh
.pi/skills/run-tests/SKILL.md
AGENTS.md
openspec/specs/agent-issue-loop/spec.md
$ grep -rn '§2, rule\|§2 rule\|rules 1-3\|rule 4' --include=*.md --include=*.sh tools/ .pi/skills/ AGENTS.md guidelines/ openspec/specs/ | wc -l
3
```

Four files assert the protocol's text; three places cite a protocol rule **by number** (`AGENTS.md:185`
"rules 1-3", `.pi/skills/mutation-runner/SKILL.md:98` "rule 4", and the open change
`fix-mutation-harness-file-scope` naming "rule 2"). That count is what the fix's shape was chosen against: the
step is written **inside rule 1** rather than appended as rule 6, because a plan-time rule appended after the
reviewer rule would put the plan after the gate, and inserting a new number before rule 5 would falsify the two
live "rule N" citations. Two cases were added (one per surface) and none rewritten.

## What changes

- `guidelines/Build.md` §2 rule 1 — the surface measurement, inside the rule that already governs measuring before
  code changes: `grep -l` the rule's text and its symbol over the cases, the engine, the prompts and the fixtures,
  and record the count as `surface: … — <command>` in the change's proposal. One home for the rule's text.
- `tools/loop/prompts/worker.md` Phase A — a fifth step, "Count the surface before the plan is fixed", so the
  loop's worker takes the measurement before it writes the plan.
- `openspec/specs/agent-issue-loop/spec.md` — the requirement "A change names its surface before its plan is
  fixed", its two scenarios, and one case per scenario.
- `tools/loop-cases.sh` — `prompt#workerNamesTheChangeSurfaceBeforeThePlan` (the rendered worker task) and
  `prompt#iterationProtocolNamesTheChangeSurface` (the guideline), both added to the `prompt` group.
- `tools/loop-mutations.sh` — `38.1`, the pass removed from the worker task, which must turn the first case red
  and green again on restore.

## Impact

No compiled file changed: no Gradle task, no native build, no device. `bash tools/loop/gate.sh --plan --base HEAD`
names the two structural suites the delta owes (`tools/loop-selftest.sh`, `tools/check-doc-routes.sh`) and no
Gradle task. Protocol rule numbers are unchanged — three live citations name one by number — so no other document
is edited. Five files touched, 2 cases added, 0 rewritten, 0 fixtures re-aligned; rollback is the revert of those
five files.

**Owning capability:** `agent-issue-loop` — this delta adds the requirement "A change names its surface before
its plan is fixed" to `openspec/specs/agent-issue-loop/spec.md`, the capability that already owns the loop's
worker and reviewer task texts and the protocol rule a session hands over. **Owning guideline section:**
`guidelines/Build.md` §2 "Agent iteration protocol", rule 1 (`Build.md:121`), routed by `AGENTS.md`'s
Documentation Map ("build, run, force a rerun, read a gate result → `Build.md` §2 — its 'Agent iteration
protocol' subsection is the iteration loop") and by `AGENTS.md`'s "Agent iteration loop (measure first)"
paragraph, which states the loop's rules as `Build.md` §2 owns them.
