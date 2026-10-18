# Falsification — the portability mutation set (13 records)

`bash tools/mutate.sh --mutations tools/portability-mutations.json`, re-run on 2026-10-18T20:45:32+02:00 by the change's finishing pass
(the previous pass cited this file without leaving it). One mutation per invariant, the named case must go red with
the mutation in place and green after the exact restore; the runner restores byte-identically (checked below).

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
5.3b	discovery#fromAWorktree	…and a linked worktree resolves its own profile, not one read from $HOME (the same mutation, the task's second case)
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
6.1	portability#plantedValueInEveryNamespaceFails	every namespace's value set is read and forbidden, not the category's alone
  RED(exit 1)  1 occurrence(s) replaced in tools/check-portability.sh
  GREEN(exit 0)  restored, and the case passes again
6.2	portability#plantedSubmodulePathFails	the submodule path is read from loop.conf, not named in the engine
  RED(exit 1)  1 occurrence(s) replaced in tools/check-portability.sh
  GREEN(exit 0)  restored, and the case passes again
6.3	portability#authorisationLabelIsTheOneException	the reserved label is asserted as the one read-only exception, not tolerated
  RED(exit 1)  1 occurrence(s) replaced in tools/check-portability.sh
  GREEN(exit 0)  restored, and the case passes again
6.4	preconditions#refusesOutsideAGitTree	the suite refuses when its root is not a git working tree
  RED(exit 1)  1 occurrence(s) replaced in tools/check-portability-selftest.sh
  GREEN(exit 0)  restored, and the case passes again
6.5	preconditions#refusesUnderMachineLocalState	the suite refuses when its root is machine-local state
  RED(exit 1)  1 occurrence(s) replaced in tools/check-portability-selftest.sh
  GREEN(exit 0)  restored, and the case passes again

mutate: 13 mutation(s) run, 13 went red and restored green, 0 problem(s)
mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore
```

`git status --porcelain` after the run is unchanged from before it: the runner left no residue.
