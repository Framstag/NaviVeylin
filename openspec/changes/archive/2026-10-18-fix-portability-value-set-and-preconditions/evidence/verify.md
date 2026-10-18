# Verification evidence — fix-portability-value-set-and-preconditions

All commands offline: no token, no Gradle, no device. This change touches no compiled file, so no build and
no Android test is affected (`guidelines/Build.md` §2, the scope block); the exclusion is named here, and the
offline suites that drive the tooling this change touches were run instead of the gate.

## Red on HEAD

`evidence/red-on-head.log` holds the full readings:

- the two #172 readings, reproduced from `git archive HEAD` — `check-portability: not inside a git working
  tree — pass --root <dir>` (exit 2) and `FAIL portability#passesOnTheRealTree`, `1 of 21 cases failed`; then
  `FAIL foreign#fixtureIsVersioned`, `1 of 21 cases failed` from a copy under `.pi/`;
- the five new portability cases red on HEAD — `check-portability-selftest: 5 of 26 cases failed`;
- the two new loop cases red on HEAD — `loop-selftest: 2 of 55 cases failed`;
- the widened check over the untouched tree — `78 leak(s)` in 9 engine files on a tree the category-only
  check called clean.

## Green after the fix

```
$ bash tools/check-portability.sh
check-portability: OK — 40 engines and 2 templates name no project value (91 value(s) read from the project)
check-portability: the documented exception — the label that authorises work is declared read-only, once:
  tools/loop/lib.sh:44:LOOP_AUTHORISATION_LABEL="agent/eligible"
exit=0
```

```
$ bash tools/check-portability-selftest.sh
check-portability-selftest: all 26 cases passed (JDK 27, offline, no token, no Gradle)
exit=0
```

```
$ bash tools/loop-selftest.sh
loop-selftest: all 55 cases passed (JDK 27, offline, no token, no sandbox)
exit=0
```

The other offline suites that drive the touched tooling:

```
$ bash tools/resolve-stale-selftest.sh
resolve-stale-selftest: all 20 cases passed (JDK 27, offline, no token, no Gradle)
$ bash tools/issue-tooling-selftest.sh
issue-tooling-selftest: all 77 cases passed (JDK 27, offline, no token)
$ bash tools/sensor-selftest.sh
sensor-selftest: all 43 cases passed (JDK 27, offline, no token)
```

Exclusions: no Gradle build, no `./gradlew test`, no emulator and no device run — the change touches no
compiled file and produces no screen, so there is nothing to measure on a device (nothing that could not be
produced on a stationary emulator, either).

## Falsification

`evidence/revert-checks.md`: `bash tools/mutate.sh --mutations tools/portability-mutations.json` — 13
mutations, each the one pattern that falsifies its invariant, each case red with the mutation in place and
green after the exact restore.

## OpenSpec

`openspec validate fix-portability-value-set-and-preconditions --type change` →
`Change 'fix-portability-value-set-and-preconditions' is valid` (exit 0).

## Two measurements worth quoting

- The value set grew from the category namespace's 14 values to **91 values over the whole profile** (every
  namespace, every label form, the roles section, the registry's declared paths and labels, and `loop.conf`).
- The engines the check scans went from 42 to 40: the two Java files that *are* the source adapter
  (`TodoAdapter.java`, `TodoSections.java`) are now excluded with the shell wrapper `PORTING.md` already
  promised was excluded — completed, not widened.

## Re-measurement (finishing pass, 2026-10-18)

The first implementation pass was cut off by its own time budget before it ran the loop suites against its
final `tools/loop/**` and `tools/tooling.json` diff, and before it left the mutation transcript this file
cites. Every owning suite was therefore re-run over the frozen diff; the raw tails are the `evidence/*.log`
files, each headed by its command.

| suite | result | log |
|---|---|---|
| `bash tools/check-portability.sh` | `OK — 40 engines and 2 templates name no project value (91 value(s) read from the project)`, exit 0 | `check-portability.log` |
| `bash tools/check-portability-selftest.sh` | all **26** cases passed, exit 0 | `check-portability-selftest.log` |
| `bash tools/loop-selftest.sh` | all **55** cases passed, exit 0 | `loop-selftest.log` |
| `bash tools/loop-mutations-selftest.sh` | the harness's own guards green, exit 0 | `loop-mutations-selftest.log` |
| `bash tools/mutate.sh --mutations tools/portability-mutations.json` | **13** mutations, 13 red and restored green, 0 problems, exit 0 | `revert-checks.md` |
| `bash tools/issue-tooling-selftest.sh` | all **77** cases passed, exit 0 | `issue-tooling-selftest.log` |
| `bash tools/declared-cases-selftest.sh` | **12** passed, 0 failed, exit 0 | `declared-cases-selftest.log` |
| `bash tools/resolve-stale-selftest.sh` | all **20** cases passed, exit 0 | `resolve-stale-selftest.log` |
| `bash tools/sensor-selftest.sh` | all **43** cases passed, exit 0 | `sensor-selftest.log` |

`git status --porcelain` after the mutation run is unchanged from before it: the runner restored every file
byte-identically.

Two leftovers of that pass were finished before the re-runs (nothing else was touched):

- `tools/loop/worktree.sh:70` — the new comment block had lost its indentation and stood at column 1 in the
  `create` function.
- `.pi/skills/portability-check/SKILL.md` and `.pi/skills/mutation-runner/SKILL.md` — the mutation set is 13
  records now; both docs still said "seven revert-checks (eight records)".

The spec delta was merged into `openspec/specs/tooling-portability/spec.md` (the two MODIFIED requirements,
no other text), so the archive finds it already synced.

## Archive checks

- `openspec status --change fix-portability-value-set-and-preconditions --json` → all four artifacts `done`;
  `openspec list --json` → 29 of 29 tasks complete.
- `openspec validate fix-portability-value-set-and-preconditions --type change` →
  `Change 'fix-portability-value-set-and-preconditions' is valid` (exit 0, re-run in this pass).
- Scenario → case: every one of the delta's seven named scenarios resolves to a case that is registered in
  `tools/portability-cases.sh` / `tools/loop-cases.sh` and passed in the runs above
  (`portability#plantedValueInEveryNamespaceFails`, `portability#plantedSubmodulePathFails`,
  `portability#authorisationLabelIsTheOneException`, `preconditions#refusesOutsideAGitTree`,
  `preconditions#refusesUnderMachineLocalState`, `prescreen#readsTheVerdictsFromTheProfile`,
  `worktree#checksOutTheSubmoduleTheLoopConfigNames`).
- Tracker, after the commit: `tools/issues/update.sh 162|172 --status fixed-by --comment-file
evidence/issue-{162,172}-close.md` (both exit 0). #162 carries `agent/eligible`, so
  `tools/issues/close.sh 162 --reason … --evidence …` closed it (`closed #162 (completed)`); #172 does not, so
  its closure is owed to the owner and the item stays open under `status/fixed-by`.
- Not present, named rather than implied: a per-scenario review signed by a reviewer that did not write the
  diff. What stands in its place is that the finishing pass re-ran every owning suite over the frozen diff
  (the table above) and that the two cases that evolved a real question — the renamed-role verdict and the
  configured submodule — are the ones the profile-driven engines had to earn.
