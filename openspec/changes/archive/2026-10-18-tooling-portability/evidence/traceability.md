# Scenario → case → task

Every scenario in `specs/tooling-portability/spec.md` with the case that exercises it and the task in
`tasks.md` that owns that case. The row count equals
`grep -c '^#### Scenario:' specs/tooling-portability/spec.md` (21).

The gate is `tools/check-portability-selftest.sh` (21 cases, offline, no token, no network, no Gradle);
`revert-checks.md` proves that seven of them fail when the invariant behind them is removed.

| # | requirement | scenario | case | task |
|---|---|---|---|---|
| 1 | An engine names no project value | A project value in an engine fails the check | `portability#plantedRepoNameFails`, `portability#plantedCategoryFails` | 1.1 |
| 2 | An engine names no project value | The check passes on the project it guards | `portability#passesOnTheRealTree` | 1.4 |
| 3 | An engine names no project value | The exclusions are deliberate and visible | `portability#excludesTheAdapterAndFixtures` | 1.2 |
| 4 | An engine names no project value | A change that leaks portability is caught where it is introduced | `portability#plantedRepoNameFails` (red) as run by the new `Check portability` CI step | 1.1, 1.6 |
| 5 | The check's value set is the project's own | A newly declared value is forbidden in engines | `portability#valueSetIsReadNotHardCoded` | 1.3 |
| 6 | The check's value set is the project's own | A value removed from the profile is no longer forbidden | `portability#valueSetIsReadNotHardCoded` | 1.3 |
| 7 | Discovery is exercised where it breaks | Invoked from a nested directory | `discovery#fromASubdirectory` | 2.1 |
| 8 | Discovery is exercised where it breaks | Invoked in a linked working tree | `discovery#fromAWorktree` | 2.2 |
| 9 | Discovery is exercised where it breaks | The environment contradicts the tree | `discovery#ignoresGithubRepositoryEnv` | 2.3 |
| 10 | Discovery is exercised where it breaks | Invoked outside a project | `discovery#outsideAGitTree` | 2.4 |
| 11 | A project with a different vocabulary runs the toolchain | The whole toolchain runs in the foreign project | `foreign#toolchainRunsEndToEnd`, `foreign#profileDiffersFromOurs` | 3.1, 3.4 |
| 12 | A project with a different vocabulary runs the toolchain | Identity is refused in the foreign project too | `foreign#identityIsRefusedToo` | 3.4 |
| 13 | A project with a different vocabulary runs the toolchain | A project with no gate command still runs | `foreign#emptyGateCommandStillRunsThePhases` | 3.6 |
| 14 | A project with a different vocabulary runs the toolchain | A duplicate is refused in the foreign project | `foreign#duplicateIsRefused` | 3.5 |
| 15 | The porting recipe is executable documentation | Every command in the recipe runs | `porting#everyCommandInTheRecipeRuns` | 4.2 |
| 16 | The porting recipe is executable documentation | The recipe assumes no build of this project | `porting#recipeDoesNotAssumeOurBuild` | 4.3 |
| 17 | The porting recipe is executable documentation | The adaptation point is named | `porting#namesTheAdaptationPoint` | 4.1 |
| 18 | The porting recipe is executable documentation | The cost of a copy is stated | `porting#statesTheCopyCost` | 4.4 |
| 19 | The synthetic project is a fixture, not a second project | The fixture is versioned, not local | `foreign#fixtureIsVersioned` | 3.4 |
| 20 | The synthetic project is a fixture, not a second project | The fixture's copy of the engines cannot drift | `foreign#fixtureIsVersioned`, `foreign#toolchainRunsEndToEnd` | 3.4 |
| 21 | The synthetic project is a fixture, not a second project | What the fixture does not model is written down | `foreign#statesWhatItDoesNotModel` | 3.2 |

Five cases were added by this traceability pass, because the table is what made the gap visible:
`foreign#identityIsRefusedToo` (scenario 12 — the foreign gate's refusal is a profile fact, and the case
first drives the stub's login as an outsider and then as the allowlisted author),
`foreign#fixtureIsVersioned` (19 and 20 — the fixture is inside the repository, is not gitignored, and
carries no engine of its own), `foreign#statesWhatItDoesNotModel` (21 — the README names at least three
differences it does not exercise), `porting#namesTheAdaptationPoint` (17 — the recipe names the profile,
the two per-tool configs and the adapter to delete) and `porting#statesTheCopyCost` (18 — re-copy, own the
merge, deferred extraction with the profile seam as the reason it stays cheap). None of them needed the
implementation to change: they are the assertions the five scenarios already demanded.

## Row count

```
$ grep -c '^#### Scenario:' openspec/changes/tooling-portability/specs/tooling-portability/spec.md
21
```

Rows in the table above (excluding the header and the separator):

```
21
```
