# Falsification — the seven revert-checks (tasks 5.1–5.7)

One mutation each, the named case must go red, the file is restored, and the case is green again.

**Reproduce the whole table in one command:**

```
bash tools/mutate.sh --mutations tools/portability-mutations.json --out evidence/portability-mutations.log
```

The mutations now live in `tools/portability-mutations.json` (eight records: the seven tasks, and 5.3 names two
cases), so they are versioned rather than recorded here alone — `.pi/skills/mutation-runner` is the recipe,
`.pi/skills/revert-check` the rule — and the CI `Check portability` step prints `--list` so a set that no
longer parses fails the pipeline instead of rotting. The transcript below was measured with that runner
(2026-10-18): eight mutations, eight red, eight restores green, exit 0.

```
5.1	portability#valueSetIsReadNotHardCoded	the value set is read from the profile, not hard-coded
  RED(exit 1)  1 occurrence(s) replaced in tools/check-portability.sh
  GREEN(exit 0)  restored, and the case passes again
5.2	portability#excludesTheAdapterAndFixtures	the source adapter is excluded from the engine set
  RED(exit 1)  1 occurrence(s) replaced in tools/check-portability.sh
  GREEN(exit 0)  restored, and the case passes again
5.3	discovery#fromASubdirectory	the profile is resolved from the working tree, not from $HOME
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/lib.sh
  GREEN(exit 0)  restored, and the case passes again
5.3b	discovery#fromAWorktree	…and a linked worktree resolves its own profile, not one read from $HOME
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/lib.sh
  GREEN(exit 0)  restored, and the case passes again
5.4	discovery#ignoresGithubRepositoryEnv	GITHUB_REPOSITORY does not override the working tree
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/lib.sh
  GREEN(exit 0)  restored, and the case passes again
5.5	foreign#profileDiffersFromOurs	the synthetic project's repository differs from this one
  RED(exit 1)  1 occurrence(s) replaced in tools/fixtures/foreign-project/tools/tooling.json
  GREEN(exit 0)  restored, and the case passes again
5.6	foreign#emptyGateCommandStillRunsThePhases	the prompt substitutes the project's gate command instead of owning one
  RED(exit 1)  2 occurrence(s) replaced in tools/loop/prompts/worker.md
  GREEN(exit 0)  restored, and the case passes again
5.7	porting#everyCommandInTheRecipeRuns	every command in the porting recipe runs
  RED(exit 1)  1 occurrence(s) replaced in PORTING.md
  GREEN(exit 0)  restored, and the case passes again

mutate: 8 mutation(s) run, 8 went red and restored green, 0 problem(s)
mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore
```

The runner verifies the restore by content **and** by permission bit (a mutated copy written by `awk` lands
with the process's default mode, so a restore that dropped the executable bit would leave a tree the gate was
never green on), and restores a killed run's file (EXIT trap); `git status --porcelain` over `tools/` and
`PORTING.md` is clean afterwards.

| task | mutation | case that must fail | measured |
|---|---|---|---|
| 5.1 | `tools/check-portability.sh`: read the category value set from a hard-coded `printf` instead of `jq -r '.namespaces.category[]?' "$profile"` | `portability#valueSetIsReadNotHardCoded` | **red, exit 1**; restored → exit 0 |
| 5.2 | `tools/check-portability.sh`: add `tools/issues/import/*.sh` to the engine set | `portability#excludesTheAdapterAndFixtures` | **red, exit 1**; restored → exit 0 |
| 5.3 | `tools/issues/lib.sh`: `issues_resolve` takes `root` from `$HOME` instead of `git rev-parse --show-toplevel` | `discovery#fromASubdirectory`, `discovery#fromAWorktree` (records 5.3 and 5.3b) | **red, exit 1** both; restored → exit 0 both |
| 5.4 | `tools/issues/lib.sh`: `ISSUES_REPO="${GITHUB_REPOSITORY:-$(issues_repo_of)}"` | `discovery#ignoresGithubRepositoryEnv` | **red, exit 1**; restored → exit 0 |
| 5.5 | `tools/fixtures/foreign-project/tools/tooling.json`: `"repo": "Framstag/NaviVeylin"` | `foreign#profileDiffersFromOurs` | **red, exit 1**; restored → exit 0 |
| 5.6 | `tools/loop/prompts/worker.md`: `{{GATE_CMD}}` → `./gradlew test` (2 occurrences) | `foreign#emptyGateCommandStillRunsThePhases` | **red, exit 1**; restored → exit 0 |
| 5.7 | `PORTING.md`: `bash tools/check-portability.sh --quiet` → `bash tools/check-portability.sh --nope` | `porting#everyCommandInTheRecipeRuns` | **red, exit 1**; restored → exit 0 |

Two notes on what the 5.6 mutation does and does not prove. The suite goes red on the assertion that the
rendered worker prompt carries no gate command of its own (`assert_not_contains … gradlew`) plus the
assertion that the skeleton still carries the `{{GATE_CMD}}` placeholder — not on the report's
`skipped (no gate configured)` tally, which is computed from `LOOP_GATE_CMD` and would therefore still say
"skipped" with a hard-coded gate in the prompt. Both halves are asserted in the same case, which is why the
mutation cannot pass it.

The runner's own four guards are falsified too, on a toy tree, by `tools/mutate-selftest.sh` (27 checks):
`MUTATION-DID-NOT-APPLY` (a search that matches nothing — the case is green and the run must not read as a
falsification), `STAYS-GREEN` (a mutation that lands and removes nothing the case asserts),
`MUTATION-FILE-MISSING`, an exact restore, and a pristine tree after every run.

The five cases added by the traceability pass (`foreign#identityIsRefusedToo`, `foreign#fixtureIsVersioned`,
`foreign#statesWhatItDoesNotModel`, `porting#namesTheAdaptationPoint`, `porting#statesTheCopyCost`) are
falsifiable in the same way — each fails when the fact it asserts is removed — but no mutation is recorded
for them here: tasks 5.1–5.7 name exactly the cases above, and adding mutations for the five would be scope
this change did not take.
