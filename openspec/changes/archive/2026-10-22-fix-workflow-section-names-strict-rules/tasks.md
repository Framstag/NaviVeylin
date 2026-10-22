# Tasks — fix-workflow-section-names-strict-rules

## 1. The red cases, before the statement exists

- [x] 1.1 Write `evidence/probe.sh` — seven cases, each runnable by id, three reading `AGENTS.md`'s
  `## OpenSpec Workflow` region and four driving `openspec validate --strict` on this change's own delta and
  restoring it — and run it on HEAD.
  **Verify**: `bash openspec/changes/fix-workflow-section-names-strict-rules/evidence/probe.sh` on base
  `80df3a3` at 2026-10-22T02:34:42Z → `probe: 0 passed, 7 failed`, exit 1 (`evidence/red-HEAD.txt`).
- [x] 1.2 Count the surface before fixing the plan: the rule's text and its command over the suites, the tools,
  the prompts and the fixtures.
  **Verify**: `evidence/surface.txt` — 0 files name `--strict`, 0 suite cases assert either rule's statement, so
  0 cases are rewritten and 0 fixtures realigned; 3 live files state the rule's subject and 1 capability owns it.

## 2. The statement an author reads

- [x] 2.1 `AGENTS.md` §"OpenSpec Workflow": two bullets beside the three CI-gated ones, the line naming
  `openspec validate <change> --strict`, and the name of the requirement that owns both.
  **Verify**: `bash evidence/probe.sh --case theWorkflowSectionStatesTheScenarioRule`,
  `--case theWorkflowSectionStatesTheTextBound` and `--case theStrictCommandIsNamedBesideTheRules` each print
  `PASS` (`evidence/green.txt`); `bash tools/check-doc-routes.sh` still resolves every reference, because the new
  block adds no `§`-reference and no skill path outside the routing table's region.

## 3. The same two properties, owned by a requirement

- [x] 3.1 MODIFY `documentation-ownership`'s *A modified requirement carries the scenarios its spec still has*:
  the block's scenarios keep their shipped titles, and a renamed scenario is a removal plus an addition — with
  the scenario that proves it on this change's own delta.
  **Verify**: `openspec validate fix-workflow-section-names-strict-rules --strict` exits 0, which it can only do
  while the MODIFIED block carries every shipped scenario verbatim; `bash evidence/probe.sh --case
  theCompletedBlockKeepsTheRequirementWhole` prints `PASS` having compared the block against the shipped
  requirement line by line, `--case theChangeSetIsValidatedByACommand` prints `PASS` on the omitted-scenario
  failure through `--changes --strict`, and `--case aRenamedScenarioIsReportedAsAnOmission` prints `PASS` and
  records the validator's omission error (`evidence/green.txt`).
- [x] 3.2 ADD `The validator's own rules are read before the first write`: the entry document states the rules
  and names the command, and the bound it states is the bound the command enforces.
  **Verify**: `bash evidence/probe.sh --case anOverLongRequirementIsReported` prints `PASS` and records
  `Requirement text is very long (>500 characters)` (`evidence/green.txt`); every `#### Scenario:` in the delta
  carries a `- **Case**:` naming one of the seven probe cases — `LOOP-MEASURE-CHANGE
  fix-workflow-section-names-strict-rules scenarios=7 cases=7 unnamed=0`.

## 4. Verification of the change as a whole

- [x] 4.1 The probe is green on the changed tree, all seven cases in one run.
  **Verify**: `evidence/green.txt` — `probe: 7 passed, 0 failed`, exit 0.
- [x] 4.2 Falsify the invariant: remove the added block from `AGENTS.md` and the three read cases must fail;
  restore it and they must pass (`revert-check`).
  **Verify**: `evidence/revert-check.txt` — without the block `probe: 4 passed, 3 failed`, with it restored
  `probe: 7 passed, 0 failed`.
- [x] 4.3 The delta passes the validator in strict mode, which is the rule this change documents.
  **Verify**: `openspec validate fix-workflow-section-names-strict-rules --strict` → `Change
  'fix-workflow-section-names-strict-rules' is valid` (`evidence/openspec-validate-green.txt`).
- [x] 4.4 No compiled file changed, so the build is not owed: `guidelines/Build.md` §2's scope rule. The gate
  the profile maps for `AGENTS.md` and for `openspec/*` is owed and run once — `tools/check-doc-routes.sh` and
  `bash tools/gen-feature-list.sh --check-classification` — and the run's own gate command was used unchanged,
  which also ran the build and the four shell suites because other changes in this shared worktree have
  compiled paths since the base.
  **Verify**: `evidence/gate.log` — exit 0 and `LOOP-GATE: tests=325 failures=0 (./gradlew test
  -PforceTests --no-build-cache, tools/loop-selftest.sh, tools/sensor-selftest.sh,
  tools/check-action-runtimes-selftest.sh, tools/gen-feature-list-selftest.sh,
  bash tools/gen-feature-list.sh --check-classification, tools/check-doc-routes.sh)`. The 325 are the five shell
  suites' own summary counts (155 + 45 + 10 + 115 + 0); the build's own case count is not in the tally, because
  the gate reads a suite's `all N cases passed` line and Gradle prints no such line — its tally is
  `evidence/gradle-suite-tally.xml`, `tests=0`, written from a run that exited 0.
- [x] 4.5 Evidence copied into this change's `evidence/` directory (the versioned half) and into the run's item
  directory.
- [x] 4.6 `openspec archive fix-workflow-section-names-strict-rules`, confirming the requirement merged into
  `openspec/specs/documentation-ownership/spec.md`.
  **Verify**: `evidence/post-archive.txt` — the archived directory exists and the capability spec carries both
  requirements.

## Verification results (quoted, not promised)

| what | line |
|---|---|
| red on base `80df3a3` | `probe: 0 passed, 7 failed`, exit 1, `2026-10-22T02:34:42Z` |
| green | `probe: 7 passed, 0 failed`, exit 0 |
| falsification | block removed: `probe: 4 passed, 3 failed`; restored: `probe: 7 passed, 0 failed` |
| validator | `Change 'fix-workflow-section-names-strict-rules' is valid` with `--strict` |
| gate | `LOOP-GATE: tests=325 failures=0 (7 commands)`, exit 0 |
| surface | 0 files name `--strict`, 0 cases assert the rule, 0 fixtures realigned |
