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
concept exists — `tools/loop/prompts/worker.md:80` on the base commit, `:88` in the tree after this change's
fifth step — asks for it as a section of `design.md`, i.e. after the
plan's files are already fixed.

**The consequence is the item's own observation.** The #232 change was planned as "one attribution fix plus one
verdict split" — two functions in `tools/loop/worktree.sh` — and landed **292 insertions over six files**,
~60 % of it test surface: two cases added, two rewritten, one renamed, and four existing fixtures re-aligned
(`(#21)` in the adopt cases' commit subjects, an archived change in
`prescreen#escalatedItemKeepsItsBranchAsEvidence` and `worktree#refusesABranchCarryingAPreviousAttempt`, an
archive-less branch in the run case). ≈55 min planned against ≈100 min realised, a factor of ~2. The estimate
was made before anyone asked how many places assert the old rule; the answer was one `grep -l` away.

**Root cause, one sentence:** the iteration protocol measures a change's symptom and its iteration levers but
never its surface, so the plan — and the estimate it is made against — is fixed before anyone counts the
cases and fixtures that assert the behaviour being changed.

**Red tests on HEAD, before the fix.** Two cases in `tools/loop-cases.sh`, run one at a time with
`bash tools/loop-selftest.sh --case '<case>'`; both fail on HEAD because the text they assert is absent:

```
$ bash tools/loop-selftest.sh --case 'prompt#workerNamesTheChangeSurfaceBeforeThePlan'
  the worker counts the surface before it plans: 'count the surface' not found in: # Worker — one item, phases A–D
FAIL prompt#workerNamesTheChangeSurfaceBeforeThePlan
EXIT=1                       # evidence/red-before-fix.log, run copy written 2026-10-22T00:44:20+02:00

$ bash tools/loop-selftest.sh --case 'prompt#iterationProtocolNamesTheChangeSurface'
  the iteration protocol does not state the surface measurement
FAIL prompt#iterationProtocolNamesTheChangeSurface
EXIT=1                       # same log, same run
```

Both are green after the fix (`evidence/green-after-fix.log`, 2026-10-22T00:44:35+02:00), together with the eight
other cases of the prompt group the changed text sits beside.

**The gate was run, whole, once, and the owed suites after the final edit.** `guidelines/Build.md:109`'s "no
build and no suite"
is the build's own gate for a delta that compiles nothing; inside a run the gate is `./tools/loop/gate.sh`
(`LOOP_GATE_CMD`, `tools/loop/loop.conf`), which maps the same `tools/**` and `*.md` paths at the shell suites the
pipeline runs (`LOOP_GATE_SUITES`). The two do not collide here: no Gradle suite is owed by this delta's own
files, and the suites the mapping names are the verification — run whole, because `Build.md:137` says a
declared-cases pass is never gate evidence. Run 2026-10-22T00:51:54+02:00 → 01:05:33+02:00
(`evidence/gate.log`):

```
LOOP-GATE: tests=195 failures=0 (./gradlew test -PforceTests --no-build-cache, tools/loop-selftest.sh, tools/sensor-selftest.sh, tools/check-doc-routes.sh)
EXIT=0
```

`tools/loop-selftest.sh` ran whole — `loop-selftest: all 150 cases passed`
(`evidence/gate-loop-selftest.xml`); `tools/sensor-selftest.sh`, `all 45 cases passed`
(`evidence/gate-sensor-selftest.xml`), owed because `tools/sensors/*` and `.github/workflows/sensors.yml` are in
this worktree's diff from a sibling change; `check-doc-routes: all 120 section references resolve, all 5 skill
references resolve` (`evidence/gate-doc-routes.xml`). The Gradle gate also ran — `BUILD SUCCESSFUL in 3m 28s`
(`evidence/gate-gradle.xml`) — because the compiled pattern set includes `*.xml` and the archived JUnit evidence
of this and neighbouring changes matches it; that is the gate's mapping, not this delta's roster, and its tally
carries `tests="0"` because `gate.sh` derives numbers only from a suite's shell summary line. That run ended at
01:05:33, before the last writes to the guideline (round 2's paragraph at 01:07:07, re-wrapped at 01:09:37), so
the two suites the delta owes were re-run
after the final edit: `tools/loop-selftest.sh` `all 150 cases passed` and `tools/check-doc-routes.sh` `all 120
section references resolve, all 5 skill references resolve`, `EXIT=0` both, 2026-10-22T01:09:48+02:00 →
01:19:31+02:00 (`evidence/suites-after-corrections.log`, tallies `evidence/tools_loop-selftest.sh.xml` 150 cases
and `evidence/tools_check-doc-routes.sh.xml`).

**Surface — the record the new rule asks for, measured before this plan was fixed:**

```
surface: 2 cases added, 0 rewritten, 0 fixtures realigned — 1 rule sentence added in one place, 2 surfaces
$ grep -rln 'iteration protocol\|Agent iteration protocol' tools/ .pi/skills/ AGENTS.md openspec/specs/ guidelines/
tools/loop-cases.sh
.pi/skills/run-tests/SKILL.md
AGENTS.md
openspec/specs/agent-issue-loop/spec.md
guidelines/Build.md
$ grep -rn 'rules 1-3\|rule 4' --include=*.md --include=*.sh tools/ .pi/skills/ AGENTS.md guidelines/ openspec/specs/
.pi/skills/mutation-runner/SKILL.md:98   … `guidelines/Build.md` §2, rule 4
AGENTS.md:185                            … rules 1-3
AGENTS.md:186                            … rule 4
$ grep -rn 'rule 2' --include=*.md openspec/changes/fix-mutation-harness-file-scope/
openspec/changes/fix-mutation-harness-file-scope/proposal.md:29
openspec/changes/fix-mutation-harness-file-scope/proposal.md:60
openspec/changes/fix-mutation-harness-file-scope/tasks.md:40
```

Five files assert the protocol's text; three sites cite a protocol rule **by number** — two documents
(`.pi/skills/mutation-runner/SKILL.md:98` "rule 4", `AGENTS.md:185-186` "rules 1-3" and "rule 4") and one open
change (`fix-mutation-harness-file-scope` naming "rule 2" at `proposal.md:29`, `:60` and `tasks.md:40`). The first
search's path set does not cover `openspec/changes/`, so the open change is looked up by its own command
rather than counted as a sixth hit of the first. That count is what the fix's shape was chosen against: the step is
written **inside rule 1**, because rule 1 already governs measuring before code changes, a new number ahead of
the cited rules would renumber what the two documents cite, and a rule appended after rule 5 would put a
plan-time rule behind the reviewer rule. Two cases were added (one per surface) and none rewritten.

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

No compiled file changed by this delta: no Gradle suite is owed by its own roster, no native build, no device.
`bash tools/loop/gate.sh --plan --base HEAD` names the two structural suites the delta owes
(`tools/loop-selftest.sh`, `tools/check-doc-routes.sh`) and no Gradle task (`evidence/gate-plan.log`); the gate
run itself is `evidence/gate.log`. Protocol rule numbers are unchanged — two documents and one open change cite
one by number — so no other document is edited. Five source files touched plus this change's own artifacts, 2
cases added, 0 rewritten, 0 fixtures re-aligned; rollback is the revert of those
five files.

**Owning capability:** `agent-issue-loop` — this delta adds the requirement "A change names its surface before
its plan is fixed" to `openspec/specs/agent-issue-loop/spec.md`, the capability that already owns the loop's
worker and reviewer task texts and the protocol rule a session hands over. **Owning guideline section:**
`guidelines/Build.md` §2 "Agent iteration protocol", rule 1 (`Build.md:121`), routed by `AGENTS.md`'s
Documentation Map ("build, run, force a rerun, read a gate result → `Build.md` §2 — its 'Agent iteration
protocol' subsection is the iteration loop") and by `AGENTS.md`'s "Agent iteration loop (measure first)"
paragraph, which states the loop's rules as `Build.md` §2 owns them.
