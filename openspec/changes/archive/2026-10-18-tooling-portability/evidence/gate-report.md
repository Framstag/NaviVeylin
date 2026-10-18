# Gate evidence — `tooling-portability` (tasks 6.1–6.4)

## 6.1 The full self-test, run and recorded

```
$ date -u +%Y-%m-%dT%H:%M:%SZ
2026-10-18T06:47:16Z
$ bash tools/check-portability-selftest.sh
PASS portability#plantedRepoNameFails
PASS portability#plantedCategoryFails
PASS portability#excludesTheAdapterAndFixtures
PASS portability#valueSetIsReadNotHardCoded
PASS portability#passesOnTheRealTree
PASS discovery#fromASubdirectory
PASS discovery#fromAWorktree
PASS discovery#ignoresGithubRepositoryEnv
PASS discovery#outsideAGitTree
PASS foreign#profileDiffersFromOurs
PASS foreign#noProjectNameInTheStub
PASS foreign#toolchainRunsEndToEnd
PASS foreign#duplicateIsRefused
PASS foreign#identityIsRefusedToo
PASS foreign#emptyGateCommandStillRunsThePhases
PASS foreign#fixtureIsVersioned
PASS foreign#statesWhatItDoesNotModel
PASS porting#everyCommandInTheRecipeRuns
PASS porting#recipeDoesNotAssumeOurBuild
PASS porting#namesTheAdaptationPoint
PASS porting#statesTheCopyCost
check-portability-selftest: all 21 cases passed (JDK 27, offline, no token, no Gradle)
$ echo $?
0
```

**21 of 21 cases, exit 0.** The run needs `bash`, `git`, `jq`, `grep`, `sed`, `awk`, `sort` and a JDK ≥ 17
(`javac`), and nothing else: no token, no network, no Gradle, no device, no screen. Each case builds its
throwaway git repository under the gitignored `.pi/tmp/`. The test-only delta rule of
`guidelines/Build.md` §2 applies — no compiled file of the app is involved.

**Addendum (after the tasks):** the seven revert-checks were given a versioned harness in the same pass —
`tools/mutate.sh` with `tools/portability-mutations.json` and its own `tools/mutate-selftest.sh` (27 of 27
checks, exit 0; 8 mutations, 8 red and 8 restores green — see `report.md`, "After the tasks", and
`revert-checks.md`). The CI `Check portability` step runs the runner's self-test and prints the set's `--list`.

## 6.2 The check on the real tree, green, with its own scope

```
$ bash tools/check-portability.sh
check-portability: engines checked (38 files):
  tools/issues/check-metadata.sh
  tools/issues/classify.sh
  tools/issues/close.sh
  tools/issues/core/Bands.java
  tools/issues/core/Fingerprint.java
  tools/issues/core/Json.java
  tools/issues/core/Policy.java
  tools/issues/core/Profile.java
  tools/issues/core/Taxonomy.java
  tools/issues/core/TodoAdapter.java
  tools/issues/core/TodoSections.java
  tools/issues/core/WorkPackage.java
  tools/issues/create.sh
  tools/issues/import.sh
  tools/issues/labels.sh
  tools/issues/lib.sh
  tools/issues/list.sh
  tools/issues/policy.sh
  tools/issues/prioritise.sh
  tools/issues/reopen.sh
  tools/issues/update.sh
  tools/loop/labels.sh
  tools/loop/lib.sh
  tools/loop/prescreen.sh
  tools/loop/report.sh
  tools/loop/run.sh
  tools/loop/state.sh
  tools/loop/worktree.sh
  tools/sensors/conditions/broken-main.sh
  tools/sensors/conditions/pr-conflicts.sh
  tools/sensors/guard.sh
  tools/sensors/hotspot-report.sh
  tools/sensors/lib.sh
  tools/sensors/metrics/build-duration.sh
  tools/sensors/metrics/coverage.sh
  tools/sensors/metrics/package-size.sh
  tools/sensors/report.sh
  tools/sensors/run.sh
check-portability: templates checked (2 files):
  tools/loop/prompts/reviewer.md
  tools/loop/prompts/worker.md
check-portability: excluded, printed so the proof names its own scope:
  tools/issues/import/todo-md.sh	the source adapter — reads this project's own record format, and is allowed to
  tools/issues/core/Cases.java	the case runner
  tools/issues/metadata-mutations.sh	the falsification harness
  tools/issues/check-metadata-selftest.sh	the self-test
  tools/sensors/fixtures/**	test data
  tools/issues/fixtures/**	test data
  tools/loop/fixtures/**	test data
  tools/fixtures/**	test data
  .pi/**	machine-local state: worktrees, captures, session logs
  openspec/**	the project's documents
  guidelines/**	the project's documents
  TODO.md	the project's document — the adapter reads it, nothing else does
check-portability: OK — 38 engines and 2 templates name no project value (132 value(s) read from the project)
$ echo $?
0
```

The printed engine set covers `tools/issues/**` (21 files, including the Java core), `tools/sensors/**`
(10 files, the runner, the guards, the report, the four measurements and the two conditions) and
`tools/loop/**` (7 files plus the two prompt templates). The excluded set names the `TODO.md` adapter, and
`portability#excludesTheAdapterAndFixtures` proves the exclusion is deliberate rather than silent.

The check also runs token-free with an emptied environment, which is what the CI step relies on:

```
$ env -i PATH="$PATH" bash tools/check-portability.sh --quiet
check-portability: OK — 38 engines and 2 templates name no project value (132 value(s) read from the project)
$ echo $?
0
```

## 6.3 The five preceding changes, and the check over their engines

| change (its design's portability promise) | engine files it contributed | check result |
|---|---|---|
| `github-issue-tooling` (D11) | `tools/issues/create.sh`, `close.sh`, `update.sh`, `list.sh`, `labels.sh`, `policy.sh`, `lib.sh`, and `core/{Fingerprint,Json,Policy,Profile,Taxonomy,WorkPackage}.java` | passes — all in the printed engine set, 0 leaks |
| `migrate-todo-to-github-issues` (D14) | `tools/issues/import.sh`, `core/{TodoAdapter,TodoSections}.java`, and the adapter `tools/issues/import/todo-md.sh` | passes — the reconciler and the two core classes are engines; the adapter is **excluded and printed** as the one component allowed to know this project's record format |
| `agent-issue-loop` (D16) | `tools/loop/{run,state,prescreen,worktree,labels,report,lib}.sh` and `tools/loop/prompts/{worker,reviewer}.md` | passes — 7 engines and 2 templates, 0 leaks (with the boundary recorded as issue **#162**, below) |
| `sensor-registry-and-metric-issues` (D12) | `tools/sensors/{run,lib,guard,report,hotspot-report}.sh`, `tools/sensors/metrics/{build-duration,coverage,package-size}.sh`, `tools/sensors/conditions/{broken-main,pr-conflicts}.sh` | passes — 10 engines, 0 leaks |
| `issue-metadata-loop` (D2) | `tools/issues/{prioritise,classify,check-metadata}.sh`, the `bands` section and the new readers in `lib.sh`/`list.sh`/`update.sh`, `core/Bands.java` | passes — all in the printed engine set, 0 leaks |

No engine is unaccounted for: the 38 printed engines plus the two templates are exactly the files the five
changes contributed, and the one excluded engine-shaped file is the adapter.

### What the check does not cover, and what was done about it

The value set is read from the project rather than hard-coded (D1), and it is exactly: the repository, the
allowlisted authors, the project name, the **category** vocabulary (its values and the label spellings every
namespace would give them), the commands and inputs `tools/sensors/sensors.json` declares, its escalation
labels, and `LOOP_GATE_CMD` plus `LOOP_WORKER_SKILLS` from `tools/loop/loop.conf`.

Three facts in the loop engine sit outside that boundary, measured in code (not comments) on 2026-10-18:

- `tools/loop/worktree.sh:71` names the project path `app/src/main/cpp/libosmscout` (reached only when the
  adopting project has a `.gitmodules`);
- `tools/loop/prescreen.sh:49-56` names the `class` and `status` values literally (`grep -x bug`, `grep -x
  in-flight`, …) to detect them after reading the profile;
- `tools/loop/labels.sh:55-97` names the `agent` and `status` values literally (`in_progress="agent…
  in-progress"`, `status_value="on-hold"`, `status_value="fixed-by"`).

Reading **every** namespace value set — as the spec's wording ("a value of the project's vocabulary") would
allow — would therefore report those lines, and task 6.4's scope guard ("no engine file was changed by this
change") forbids fixing them here. The measurement is recorded and filed in the tracker as **issue #162**
(`tools/loop/worktree.sh` reads the submodule path from `loop.conf`, and the loop's verdict→label mapping
reads its `class`/`status`/`agent` values from the profile, after which the check can be widened). This
change adds the proof; it does not silently narrow the rule. The `agent/eligible` constant in
`tools/issues/lib.sh` is the one documented exception: the loop reads the authorisation label and no code
path writes it.

## 6.4 The exclusions, confirmed

- **No Gradle build and no Gradle suite was run, and none is required.** The change edits shell, JSON,
  markdown and one CI step. The scope block of `guidelines/Build.md` §2 applies: a delta that touches no
  compiled file names the structural check it ran instead — here `tools/check-portability.sh` (the value
  set over the real tree) and `tools/check-portability-selftest.sh` (the 21 cases).
- **No engine file was changed by this change.** `git diff --name-only` lists
  `.github/workflows/build.yml`, `AGENTS.md`, `guidelines/Backlog.md` and `tools/feature-list/specs.json`
  (plus the new, untracked files of this change), and no file under `app/`, `auto/`, `core/`,
  `osmscout-client-java/`, `buildSrc/`, `tools/issues/`, `tools/sensors/` or `tools/loop/`. The seven
  revert-checks in `revert-checks.md` mutated three engine files and restored each of them.
- **The fixture lives under `tools/`, not in `.pi/`, so it is versioned** — and
  `foreign#fixtureIsVersioned` asserts it: the path is inside the repository, `git check-ignore` does not
  ignore it, and the fixture carries configuration and its own sensor rather than a copy of any engine.
- **The gate itself makes no network call and needs no token.** The check and the self-test read repository
  files and run throwaway git repositories. The one tracker write this session made is **issue #162** — the
  finding above, filed through `tools/issues/create.sh` because the operation guidance requires a problem
  this change detects but does not fix to be filed; it is not part of any case and no case reads it.
- **The CI step grants nothing.** A step grants no permissions of its own; the enclosing `build` job already
  carries `actions: write` and `packages: write` for the existing vcpkg/NuGet and cache steps, and the
  `Check portability` step needs none of them — it reads repository files and writes only under `$TMPDIR` and
  the job's checkout. `contents: read` is the whole of what it depends on.
