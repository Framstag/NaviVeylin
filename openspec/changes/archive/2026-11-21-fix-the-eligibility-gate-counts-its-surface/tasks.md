## Tasks

- [x] 1. Add `prompt#theTooWideRowCountsTheSurfaceBeforeTheVerdict` to `tools/loop/tests/loop-cases.sh` — the case body
  with its `# files:` declaration, its `LOOP_SHELL_CASES` row and its dispatch arm — and take it red on the base,
  before the row it reads exists: `bash tools/loop/tests/loop-selftest.sh --case
  'prompt#theTooWideRowCountsTheSurfaceBeforeTheVerdict'`. Result on `8e3861c4`, 2026-11-21T03:54:29Z:
  `tests=1 failures=1 errors=0`, the failure being the case's first assertion (`'counted, not judged' not found`).
  `evidence/red-on-HEAD.xml` is that tally and `evidence/red-on-HEAD-8e3861c4.log` its log.

- [x] 2. Rewrite row 5 of `tools/loop/prompts/worker.md` (the eligibility gate's table) so the row reads the census
  Phase A already took, compares it with a tenth of `LOOP_CHILD_TURNS_MAX_WORKER`, and records
  `surface: <n> sites over <m> files against <t> turns` with the split it proposes. Verification — the case, green:
  `bash tools/loop/tests/loop-selftest.sh --case 'prompt#theTooWideRowCountsTheSurfaceBeforeTheVerdict'` →
  `PASS prompt#theTooWideRowCountsTheSurfaceBeforeTheVerdict` (`evidence/green-on-change.log`).

- [x] 3. Add the paragraph to `guidelines/Backlog.recipes.md`, section 7b, beside the paragraph that prices the gate against
  the pre-screen: the row is a count, with the 2026-11-17T10:58Z figures (8.29 Mtok for six refusals against 78.7
  Mtok for the four the row admitted) and the record a `too-wide` skip carries. Verification: the prompt's own route
  case still reads — `bash tools/loop/tests/loop-selftest.sh --case
  'prompt#thePhasePromptsCarryTheirOwnRoute'` → `PASS`, which is also the case that holds every path the phase
  prompts name against the project root.

- [x] 4. Add the mutation record `959.1` to `tools/loop/tests/loop-mutations.sh`: it removes the bound the count is
  read against. Verification: `bash tools/loop/tests/loop-mutations.sh --only 959.1` →
  `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`, and the whole set still applies —
  `bash tools/loop/tests/loop-mutations.sh --check` (via the case `mutation#everyDeclaredRecordApplies`, `PASS`,
  `evidence/records-check.log`).

- [x] 5. The structural checks in place of a build, because this change compiles nothing: the delta's own case and
  declarations are held by `driver#everyCaseDeclaresTheFilesItCanReach` (`PASS`), the guideline edit by
  `./tools/check-doc-routes.sh` (`all 356 section references resolve, all 8 skill references resolve`), the numbers
  the guideline quotes by `./tools/check-doc-values.sh` (`LOOP-CHILD-TURNS-MAX: 6 roles, prose matches config`), and
  the change directory by
  `./tools/check-openspec-hygiene.sh` — which is **red on the base and not on this change**: its five errors name
  `the-print-sites-are-converted`, `a-case-name-resolves-to-one-harness`, `run-event-stream-is-typed`,
  `settings-taxonomy-and-surface` and `the-bounds-spend-and-budget-are-typed`, five other open changes, and no line
  names this one (`evidence/structural-checks.log`).

- [x] 6. Take the change's own numbers with `./tools/loop/measure.sh --change
  fix-the-eligibility-gate-counts-its-surface`: `LOOP-MEASURE-CHANGE … scenarios=1 cases=1 unnamed=0`, the declared
  suite `LOOP-MEASURE-SUITE cases=508 cmd=tools/loop/tests/loop-selftest.sh --list`.

- [x] 7. The gate child's, not this phase's: complete the change — its evidence, its pointers and its archive — before
  the gate runs once over the completed tree. This phase ends at the diff (`LOOP-DIFF-READY`).
