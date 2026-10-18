# Tasks

## 1. Reproduce the defects (red on HEAD)

- [x] 1.1 Write the two readings of #172 into cases: `preconditions#refusesOutsideAGitTree` and
  `preconditions#refusesUnderMachineLocalState` in `tools/portability-cases.sh` — a copy of the suite (the
  driver, its cases and the engines) outside a git working tree, and one under a `.pi/` path inside a
  throwaway repository. Parent spec: `tooling-portability` → "Discovery is exercised where it breaks" →
  scenarios `preconditions#refusesOutsideAGitTree`, `preconditions#refusesUnderMachineLocalState`. Verify:
  each asserts exit 2, a message naming the reason, no `FAIL` line, and (for the first) the header naming both
  preconditions.
- [x] 1.2 Write the value-set cases of #162: `portability#plantedValueInEveryNamespaceFails` (one value per
  declared namespace, each asserted to be reported with its namespace as source),
  `portability#plantedSubmodulePathFails` (the path `loop.conf` must declare, and the worktree that must not
  name it) and `portability#authorisationLabelIsTheOneException` (the real tree's single read-only
  declaration, and a planted second one). Parent spec: `tooling-portability` → "The check's value set is the
  project's own". Verify: the check fails for each planted value and names its source.
- [x] 1.3 Write the loop-side cases: `prescreen#readsTheVerdictsFromTheProfile` (rename `roles.deviceAgent` in
  the throwaway profile; the device verdict must change with it) and
  `worktree#checksOutTheSubmoduleTheLoopConfigNames` (a `.gitmodules` declaring a path the configuration
  names). Parent spec: `tooling-portability` → "The check's value set is the project's own". Verify:
  `bash tools/loop-selftest.sh --case <case>` is red on HEAD.
- [x] 1.4 Record the red-on-HEAD run: `bash tools/check-portability-selftest.sh` → `5 of 26 cases failed`,
  `bash tools/loop-selftest.sh` → `2 of 55 cases failed`, plus the two #172 readings verbatim from a
  `git archive HEAD` export, saved as `evidence/red-on-head.log`. Verify: the file names each case and the
  tally.

## 2. Implement the widened value set

- [x] 2.1 `tools/tooling.json`: add the `roles` section (the eleven role keys, `comment` included). Parent
  spec: `tooling-portability` → "The check's value set is the project's own". Verify: `jq -e .roles
  tools/tooling.json`.
- [x] 2.2 `tools/loop/loop.conf`: add `LOOP_SUBMODULE_PATH` to the project-keys block. Verify: the check
  reports it as a forbidden value's source (`portability#plantedSubmodulePathFails`).
- [x] 2.3 `tools/check-portability.sh`: forbid every namespace's values (`word`) and label forms (`literal`),
  the `roles` values (`word`) and `LOOP_SUBMODULE_PATH`; keep a value that is also a namespace name out of the
  bare set; dedupe the value list by value. Verify: `portability#plantedValueInEveryNamespaceFails` passes.
- [x] 2.4 `tools/check-portability.sh`: make the word mode's flag-argument rule precise (a token of its own,
  not glued into a flag's name and not a `name=value` pair), and document it in the header. Parent spec: same
  requirement. Verify: `portability#plantedCategoryFails` and `portability#plantedRepoNameFails` stay green.
- [x] 2.5 `tools/check-portability.sh`: the reserved labels become the one asserted exception (one read-only
  declaration, printed in the header; a second or any other occurrence is a leak). Verify:
  `portability#authorisationLabelIsTheOneException`.
- [x] 2.6 `tools/check-portability.sh`: complete the source adapter's exclusion (`TodoAdapter.java`,
  `TodoSections.java`) and name all three files in the printed exclusion list. Verify:
  `portability#excludesTheAdapterAndFixtures` and `passesOnTheRealTree` stay green.

## 3. Implement the engine fixes the widened rule demands

- [x] 3.1 `tools/loop/lib.sh`: `loop_roles_load` (section present, every role declared) and `loop_role`.
  Verify: `bash tools/loop-selftest.sh --case prescreen#readsTheVerdictsFromTheProfile`.
- [x] 3.2 `tools/loop/prescreen.sh`: the class/status/device roles and the queued verdict come from the
  profile; the ranking is `workedClasses`. Parent spec: `tooling-portability` → "The check's value set is the
  project's own". Verify: `prescreen#ranksBugsFirstAndDropsFeatures`, `prescreen#settledItemIsDropped`,
  `prescreen#deviceItemIsAVerdictNotWork`.
- [x] 3.3 `tools/loop/labels.sh`: the verdict→label mapping reads `holdStatus`, `closeStatus`,
  `inProgressAgent`, `deviceAgent`, `decisionAgent`. Verify: `labels#verdictToLabelMatrix`,
  `labels#neverWritesReservedLabel`.
- [x] 3.4 `tools/loop/run.sh`, `report.sh`, `state.sh`: the decision label and the queued verdict come from
  the roles. Verify: `run#decisionCapRefusesToStart`, `run#needsDecisionIsEscalatedWithTheWork`,
  `state#projectKeysAreReadFromLoopConf`.
- [x] 3.5 `tools/loop/worktree.sh`: read `LOOP_SUBMODULE_PATH`. Verify:
  `worktree#checksOutTheSubmoduleTheLoopConfigNames`, `worktree#seedsAllFourInputs`.
- [x] 3.6 `tools/issues/resolve-stale.sh`: the parking status reads `roles.holdStatus`; the redundant
  `--state open` goes (the same filter is `gh`'s default). Verify: `bash tools/resolve-stale-selftest.sh`.
- [x] 3.7 `tools/sensors/conditions/pr-conflicts.sh`: the same `--state open` removal. Verify:
  `bash tools/sensor-selftest.sh`.
- [x] 3.8 `tools/fixtures/foreign-project/tools/tooling.json`: the fixture's own `roles` section, with
  `deviceStatus`/`deviceAgent` empty. Verify: `foreign#emptyGateCommandStillRunsThePhases`,
  `foreign#toolchainRunsEndToEnd`, `porting#everyCommandInTheRecipeRuns`.

## 4. Make the suite refuse its preconditions

- [x] 4.1 `tools/check-portability-selftest.sh`: refuse with a named reason and exit 2 when the root is not
  inside a git working tree, and when the root path contains `/.pi/`; state both in the header (and keep
  `-h` printing them). Parent spec: `tooling-portability` → "Discovery is exercised where it breaks". Verify:
  the two `preconditions#…` cases.

## 5. Falsify the new invariants

- [x] 5.1 `tools/portability-mutations.json`: re-point 5.1 at the widened value set, fix 5.2's search text,
  and add 6.1–6.5 (the namespace value set, the submodule path, the asserted exception, the two
  preconditions). Verify: `bash tools/mutate.sh --mutations tools/portability-mutations.json` → every
  mutation red, restored green.
- [x] 5.2 `bash tools/mutate.sh --mutations tools/portability-mutations.json --list` exits 0 (already
  registered in the "Check portability" CI step). Verify: the command's exit status.

## 6. Documents

- [x] 6.1 `PORTING.md`: the profile's `roles` section and `loop.conf`'s `LOOP_SUBMODULE_PATH`; what the check
  forbids and what the one exception is. Verify: `porting#namesTheAdaptationPoint`,
  `porting#recipeDoesNotAssumeOurBuild`.
- [x] 6.2 `guidelines/Backlog.md` §10: the adaptation table's rows and what "names a value" means. Verify:
  the section reads as one boundary statement (structural check: the code block and the prose agree with
  `tools/check-portability.sh --values`).
- [x] 6.3 `AGENTS.md` and `.pi/skills/portability-check/SKILL.md`: the case count (26), the exception, the two
  preconditions. Verify: `grep -n '26 cases' AGENTS.md .pi/skills/portability-check/SKILL.md`.

## 7. Verify, land, close

- [x] 7.1 Run the owning self-tests in full: `bash tools/check-portability-selftest.sh` → all 26 cases pass;
  `bash tools/loop-selftest.sh` → all 55 cases pass (re-run in the finishing pass over the frozen `tools/loop/**`
  and `tools/tooling.json` diff, with `tools/loop-mutations-selftest.sh`, `tools/issue-tooling-selftest.sh`,
  `tools/declared-cases-selftest.sh`, `tools/resolve-stale-selftest.sh`, `tools/sensor-selftest.sh` and the
  13-record mutation set; the tails are `evidence/*.log` and `evidence/revert-checks.md`). Parent spec: `tooling-portability` (the whole
  capability). Exclusion: no Gradle and no device — this change touches no compiled file (`guidelines/Build.md`
  §2, the scope block); the other offline suites that drive the touched tooling (`tools/resolve-stale-selftest.sh`,
  `tools/sensor-selftest.sh`, `tools/issue-tooling-selftest.sh`, `tools/loop-selftest.sh`) are run as well.
- [x] 7.2 Run `openspec validate fix-portability-value-set-and-preconditions --type change` → exit 0. Verify:
  the command's exit status. Re-run in the finishing pass (2026-10-18) after the spec delta was merged into
  `openspec/specs/tooling-portability/spec.md`: `Change 'fix-portability-value-set-and-preconditions' is
  valid`, exit 0.
- [x] 7.3 Archive the change with the `openspec-archive-change` skill into
  `openspec/changes/archive/<YYYY-MM-DD>-fix-portability-value-set-and-preconditions/` (move, not copy).
  Verify: the archive directory exists and the change directory is gone.
- [x] 7.4 One commit: `fix: fix-portability-value-set-and-preconditions (#162, #172)`, adding every touched
  file. Verify: `git status --porcelain` is clean afterwards (bar the untracked
  `Agentisches-Coding-NaviVeylin.md`).
- [x] 7.5 Mark issues #162 and #172 `status/fixed-by` with a comment citing the archived change, the selftest
  and the `file:line` that now makes the defect impossible — via `tools/issues/update.sh`. Verify: each item
  carries exactly one `status/*` label and the comment cites the archive. Done in the finishing pass:
  `update.sh 162 --status fixed-by --comment-file evidence/issue-162-close.md` and the same for 172, both
  exit 0. **#162** carries `agent/eligible`, so `close.sh 162 --reason … --evidence …` closed it
  (`closed #162 (completed)`); **#172** does not, so its closure is **owed to the owner**
  (`close.sh` would refuse with `missing-eligibility-label`) and the item stays open under
  `status/fixed-by`.
