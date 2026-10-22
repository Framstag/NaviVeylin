# Tasks — fix-workflow-section-names-strict-rules

## 1. The red cases, before the statement exists

- [x] 1.1 Write `evidence/probe.sh` — seven cases, each runnable by id, three reading `AGENTS.md`'s
  `## OpenSpec Workflow` region and four driving `openspec validate --strict` on this change's own delta and
  restoring it — and record the red line.
  **Verify**: `evidence/red-HEAD.txt`, `probe: 0 passed, 7 failed`, exit 1. The method, as that file's header
  states: the probe was run at 2026-10-22T02:34:42Z on `80df3a3`'s `AGENTS.md` with this change's delta moved
  aside, i.e. by removing the fix rather than before writing it — 9 s after `green.txt`, the same measurement as
  the revert-check. Round 1's probe path (`openspec/changes/<name>/`) no longer applies after the archive; see
  task 5.1.
- [x] 1.2 Count the surface before fixing the plan: the rule's text and its command over the suites, the tools,
  the prompts and the fixtures.
  **Verify**: `evidence/surface.txt` — 0 files name `--strict`, 0 suite cases assert either rule's statement, so
  0 cases are rewritten and 0 fixtures realigned; 3 live files state the rule's subject and 1 capability owns it.

## 2. The statement an author reads

- [x] 2.1 `AGENTS.md` §"OpenSpec Workflow": two bullets beside the three CI-gated ones, the line naming
  `openspec validate <change> --strict` as the command that fails on them where a plain validate only warns, and
  the name of the requirement that owns both.
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
- [x] 3.2 ADD `The validator's own rules are read before the first write`: the entry document states the two
  rules the strict command fails on and names that command, and the bound it states is the bound the command
  enforces.
  **Verify**: `bash evidence/probe.sh --case anOverLongRequirementIsReported` prints `PASS` and records
  `Requirement text is very long (>500 characters)` from `--strict` and the same finding as a `[WARNING]` from a
  plain run (`evidence/green.txt`); every `#### Scenario:` in the delta carries a `- **Case**:` naming one of the
  seven probe cases — `LOOP-MEASURE-CHANGE 2026-10-22-fix-workflow-section-names-strict-rules scenarios=7
  cases=7 unnamed=0`.

## 4. Verification of the change as a whole

- [x] 4.1 The probe is green on the changed tree, all seven cases in one run.
  **Verify**: `evidence/green.txt` — `probe: 7 passed, 0 failed`, exit 0.
- [x] 4.2 Falsify the invariant: remove the added block from `AGENTS.md` and the three read cases must fail;
  restore it and they must pass (`revert-check`).
  **Verify**: `evidence/revert-check.txt` — without the block `probe: 4 passed, 3 failed`, with it restored
  `probe: 7 passed, 0 failed`.
- [x] 4.3 The delta passes the validator in strict mode, which is the rule this change documents.
  **Verify**: `openspec validate fix-workflow-section-names-strict-rules --strict` → `Change
  'fix-workflow-section-names-strict-rules' is valid` (`evidence/openspec-validate-green.txt`, round 1, while the
  change was open). After the archive the change is no longer in the open change set, so round 2 drives a
  temporary copy of the archived directory: `Change 'probe-round2-validate' is valid`, exit 0
  (`evidence/round2-checks.log`).
- [x] 4.4 No compiled file changed, so the build is not owed: `guidelines/Build.md` §2's scope rule. The gate
  the profile maps for `AGENTS.md` and for `openspec/*` is owed and run once — `tools/check-doc-routes.sh` and
  `bash tools/gen-feature-list.sh --check-classification` — and the run's own gate command was used unchanged,
  which also ran the build and the four shell suites because other changes in this shared worktree have
  compiled paths since the base.
  **Verify**: `evidence/gate.log` — `LOOP-GATE: tests=325 failures=0 (./gradlew test
  -PforceTests --no-build-cache, tools/loop-selftest.sh, tools/sensor-selftest.sh,
  tools/check-action-runtimes-selftest.sh, tools/gen-feature-list-selftest.sh,
  bash tools/gen-feature-list.sh --check-classification, tools/check-doc-routes.sh)`. That line is the exit
  status: `tools/loop/gate.sh` prints it in its success path (`gate.sh:142`), after the failing suites have
  already been named and exited 1 (`gate.sh:139-140`), so a tally with no `— failing:` clause is a gate that
  reached the end and exited 0. The 325 are the five shell suites' own summary counts (155 + 45 + 10 + 115 + 0);
  the build's own case count is not in the tally, because the gate reads a suite's `all N cases passed` line and
  Gradle prints no such line — its tally is `evidence/gradle-suite-tally.xml`, `tests=0`, written from a run
  that exited 0.
- [x] 4.5 Evidence copied into this change's `evidence/` directory (the versioned half) and into the run's item
  directory.
- [x] 4.6 `openspec archive fix-workflow-section-names-strict-rules`, confirming the requirement merged into
  `openspec/specs/documentation-ownership/spec.md`.
  **Verify**: `evidence/post-archive.txt` — the archived directory exists and the capability spec carries both
  requirements.

## 5. The round-2 corrections

- [x] 5.1 `evidence/probe.sh` resolves the change open **or** archived and drives a temporary
  `openspec/changes/probe-<name>` copy of it, removed by the exit trap; its header states seven cases and four
  validator cases.
  **Verify**: `evidence/round2-checks.log` — after the archive, `bash evidence/probe.sh` → `probe: 7 passed, 0
  failed` from `openspec/changes/archive/2026-10-22-…/evidence/`, and `openspec/changes/` holds no `probe-`
  directory afterwards.
- [x] 5.2 Report-versus-fail wording corrected in `AGENTS.md`, in the ADDED requirement and in the retitled
  scenario, whose case now asserts both a plain exit 0 with `[WARNING]` and `--strict` exit 1.
  **Verify**: `evidence/round2-checks.log` — `anOverLongRequirementIsReported` PASS; mutation `M2` restores the
  round-1 sentence and `theStrictCommandIsNamedBesideTheRules` is the only case red (`6 passed, 1 failed`).
- [x] 5.3 The ADDED requirement's universal narrowed to the two rules the entry document states.
  **Verify**: its body is 483 characters, `openspec validate documentation-ownership --type spec` exits 0 and
  warns only about `requirements[11]`/`[13]` — the capability's pre-existing 724- and 529-character bodies,
  which this change does not touch (`evidence/round2-checks.log`).
- [x] 5.4 Each read case asserts every clause of its THEN, and each clause has a mutation that fails it.
  **Verify**: `evidence/round2-checks.log` — `M3` (drop `under their own titles`) reds
  `theWorkflowSectionStatesTheScenarioRule` alone, `M4` (drop `An example belongs in a scenario`) reds
  `theWorkflowSectionStatesTheTextBound` alone, and the change-set case greps the requirement's name.
- [x] 5.5 No compiled file in the correction either, so no gate is owed or re-run: `guidelines/Build.md` §2's
  scope rule. The structural checks it does owe were run on the corrected tree.
  **Verify**: `evidence/round2-checks.log` — `check-doc-routes: all 120 section references resolve, all 5 skill
  references resolve`, exit 0, and `Change 'probe-round2-validate' is valid` in strict mode.

## Verification results (quoted, not promised)

| what | line |
|---|---|
| red on base `80df3a3` | `probe: 0 passed, 7 failed`, exit 1, `2026-10-22T02:34:42Z` |
| green | `probe: 7 passed, 0 failed`, exit 0 |
| falsification | block removed: `probe: 4 passed, 3 failed`; restored: `probe: 7 passed, 0 failed` |
| validator | `Change 'fix-workflow-section-names-strict-rules' is valid` with `--strict` |
| gate | `LOOP-GATE: tests=325 failures=0 (7 commands)`, exit 0 |
| surface | 0 files name `--strict`, 0 cases assert the rule, 0 fixtures realigned |
| round 2, four mutations | `M1` block removed `4 passed, 3 failed`; `M2` round-1 wording `6/1`; `M3` `6/1`; `M4` `6/1`; restored `7 passed, 0 failed` (`evidence/round2-checks.log`, 2026-10-22T02:56:09Z) |
| round 2, spec and structure | `Specification 'documentation-ownership' is valid` exit 0; corrected body 483 chars; `check-doc-routes` exit 0; archived delta validates from a copy, exit 0 |
