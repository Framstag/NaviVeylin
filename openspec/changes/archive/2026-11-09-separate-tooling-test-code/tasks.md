# Tasks — separate-tooling-test-code

No compiled file is touched, so every task's verification is a tooling check (`guidelines/Build.md` §2, the
scope block): the moved self-tests, `tools/loop/tests/loop-selftest.sh`, `tools/tests/check-portability-selftest.sh`
and `tools/check-rule-routes.sh`. No Gradle target, no device, no emulator.

## 1. The move

- [x] 1.1 Create the four test roots and move the drivers, mutation sets and fixtures with `git mv`:
      `tools/loop/tests/`, `tools/issues/tests/`, `tools/sensors/tests/`, `tools/tests/`. Spec
      `tooling-test-layout`, "Tooling test code sits under the owning group's tests directory".
      Verification: `git mv` records the renames; `git status` shows no copy+delete pair for a production tool.
      **Result:** `git log --diff-filter=R` over the change records 67 renames under `tools/`; no production
      tool appears as a copy+delete pair.
- [x] 1.2 Confirm no production tool moved: `tools/declared-cases.sh`, `tools/mutate.sh` and every
      `tools/*.sh`/`tools/*.py` without a test suffix stay at their path. Spec
      `tooling-test-layout`, "A production tool is not moved for its name". Verification:
      `tools/declared-cases.sh` still runs (`bash tools/declared-cases.sh --help`).
      **Result:** `bash tools/declared-cases.sh --help` prints its usage; the rename set names neither it nor
      `tools/mutate.sh`.
- [x] 1.3 Move the fixture trees: shared `tools/fixtures/` → `tools/tests/fixtures/`; local
      `tools/<group>/fixtures/` → `tools/<group>/tests/fixtures/`. Spec `tooling-test-layout`, "Tooling test
      code sits under the owning group's tests directory".
      **Result:** `git ls-files 'tools/**' | grep fixtures/` names no fixture outside a `tests/fixtures/`.
- [x] 1.4 Verify the file names are unchanged after the move. Spec `tooling-test-layout`, "A moved test file
      keeps its name". Verification: for every moved file, the base name is the same as before the move.
      **Result:** the rename list holds no pair whose base names differ (0 rows).

## 2. Depth-independent root resolution

- [x] 2.1 Replace each moved driver's `root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)` with a
      tree-resolving form (`git rev-parse --show-toplevel`, keeping the `${BASH_SOURCE[0]}` fallback only for
      a run outside a git tree). Spec `tooling-test-layout`, "A moved test resolves the tree without assuming
      its depth". Verification: `bash tools/loop/tests/loop-selftest.sh --list` and each moved selftest's
      `--list` resolve the tree from a working directory that is not the repository root.
      **Result:** run from `.pi-subagents/tmp740/cwd` (three levels below the root), `--list` exits 0 and
      prints its case list: `loop-selftest.sh` 382 lines, `loop-mutations-selftest.sh` 35,
      `issue-tooling-selftest.sh` 164, `sensor-selftest.sh` 85, `check-change-readiness-selftest.sh` 13,
      `mutate-selftest.sh` 39. Every moved driver carries the `git -C … rev-parse --show-toplevel` form.

## 3. Cross-references inside the tests

- [x] 3.1 Rewrite every reference a test makes to another test, a fixture or a mutation set to its post-move
      path. Spec `tooling-test-layout`, "A cross-reference names the new path". Verification:
      `grep -rn 'tools/[a-z-]*-selftest\|tools/loop/fixtures\|tools/issues/fixtures\|tools/sensors/fixtures\|tools/fixtures/' tools/`
      returns only post-move paths. `tools/loop/loop.conf` carries no moved test path — confirm and state it.
      **Result:** the grep over `tools/` returns no pre-move path; `tools/loop/loop.conf` names only
      post-move paths (`LOOP_SUITE_CASES_CMD="tools/loop/tests/loop-selftest.sh --list"` and the prose that
      quotes them), and its one bare `loop-cases.sh` is issue provenance (#788), not a path.
- [x] 3.2 Rewrite each mutation set's `case-cmd` and every path in its `comment` to the new driver path
      (`close-`, `create-`, `duplicates-`, `status-`, `decision-question-`, `resolve-stale-`,
      `check-change-readiness-`, `check-doc-routes-`, `check-rule-routes-`, `portability-mutations.json`).
      Spec `tooling-test-layout`, "Live references follow the tests". Verification:
      `bash tools/mutate.sh --mutations tools/issues/tests/close-mutations.json --list` resolves its case.
      **Result:** `--list` prints the A1.2–A1.6 records with their descriptions; every `case-cmd` in the
      collected sets names a `tools/…/tests/` driver.

## 4. The portability check

- [x] 4.1 Change `tools/check-portability.sh` to derive its engine file set from production tool directories
      and skip `tests/` by directory, replacing the `*-selftest.sh`/`*-cases.sh`/`*-mutations.sh` suffix
      filter. Spec `tooling-test-layout`, "The portability check excludes tests by location". Verification:
      `bash tools/check-portability-selftest.sh` green over the synthetic foreign project.
      **Result:** `tools/tests/check-portability-selftest.sh` — all 31 cases passed, offline, no Gradle.
- [x] 4.2 Revert-check the new invariant: one mutation that makes the check read a file under `tools/tests/`
      must turn the named portability case red; restore, re-run green. Spec `tooling-test-layout`, "The
      portability check excludes tests by location". Verification: the mutation red case and the restored
      green run are both recorded with their timestamps.
      **Result:** `evidence/revert-check.txt` — record 4.1 of `tools/tests/portability-mutations.json`,
      `RED(exit 1)` then `GREEN(exit 0)`, run 2026-11-09T05:18:52Z.

## 5. CI and live documentation

- [x] 5.1 Update every call in `.github/workflows/build.yml` (~30) to the new driver path. Spec
      `tooling-test-layout`, "Live references follow the tests". Verification: `grep -n 'selftest\|mutations\|-cases'
      .github/workflows/build.yml` names only post-move paths.
      **Result:** 32 calls name a `tools/…/tests/` path; the only remaining matches are prose comments.
- [x] 5.2 Update the guidelines that name a moved driver — `guidelines/Build.md`,
      `guidelines/BuildGate.md`, `guidelines/BuildEvidence.md`, `guidelines/BuildEvidence.why.md`,
      `guidelines/BuildGate.why.md`, `guidelines/Metrics.md`, `guidelines/Metrics.recipes.md`,
      `guidelines/Backlog.md`, `guidelines/Backlog.recipes.md`, `guidelines/DesignNative.md`. Spec
      `tooling-test-layout`, "Live references follow the tests".
      **Result:** all ten edited; `bash tools/check-doc-routes.sh` resolves 329 section and 7 skill references.
- [x] 5.3 Update the skills that name a moved driver — `.pi/skills/revert-check/SKILL.md` (20),
      `.pi/skills/mutation-runner/SKILL.md` (9), `.pi/skills/portability-check/SKILL.md` (7),
      `.pi/skills/loop-spend/SKILL.md` (3), and the one-reference skills (`list-issues`, `update-issue`,
      `pixel-check`, `close-issue`, `create-issue`, `run-tests`, `cleanup-todo`). Spec
      `tooling-test-layout`, "Live references follow the tests".
      **Result:** the twelve skills edited, plus `analyse-loop-run`.
- [x] 5.4 Update `PORTING.md`'s "What to copy" list, its prerequisites table and its command examples, and
      `AGENTS.md`'s reference. Spec `tooling-test-layout`, "Live references follow the tests". Verification:
      `bash tools/check-portability.sh --quiet` green (the recipe's commands resolve).
      **Result:** `check-portability.sh` green, and its self-test's porting-recipe cases pass (31/31).
- [x] 5.5 Confirm no live file still names an old path. Spec `tooling-test-layout`, "Live references follow
      the tests". Verification:
      `grep -rn 'tools/[a-z0-9-]*-selftest\.sh\|tools/loop-cases\.sh\|tools/issues/metadata-mutations\.sh' --include='*.md' --include='*.yml' --include='*.sh' --include='*.json' . | grep -v 'openspec/changes/archive'`
      returns nothing.
      **Result:** the same grep over the tracked tree, archive and this change excluded, returns nothing.

## 6. Specs

- [x] 6.1 Land the five MODIFIED deltas and the new `tooling-test-layout` capability. Spec
      `tooling-test-layout` and the five modified capabilities. Verification:
      `openspec validate separate-tooling-test-code --strict` exits 0.
      **Result:** `Change 'separate-tooling-test-code' is valid`.
- [x] 6.2 Edit the shipped `openspec/specs/highlight-measurement/spec.md` Purpose line (which names
      `tools/measure-highlight-selftest.sh`) to the new path — a delta carries no Purpose for an existing
      capability, so this is a direct edit. Spec `tooling-test-layout`, "Live references follow the tests".
      **Result:** the Purpose names `tools/tests/measure-highlight-selftest.sh`.
- [x] 6.3 Leave the archived evidence under `openspec/changes/archive/**` byte-identical. Spec
      `tooling-test-layout`, "Archived evidence is not rewritten". Verification: `git diff --stat` names no
      file under `openspec/changes/archive/`.
      **Result:** `git diff --stat main..HEAD -- openspec/changes/archive` is empty.

## 7. Verification

- [x] 7.1 Run the loop self-test at its new path: `bash tools/loop/tests/loop-selftest.sh`. Spec
      `agent-issue-loop` (the two modified requirements) and `tooling-test-layout`. Verification: exit 0, and
      the two modified requirements' named cases are among the cases run.
      **Result:** all 382 cases passed — 447.58 s, JDK 27, offline, no token, no sandbox. The five named
      cases of the two modified requirements (`the scratch tree is the project's, not the host's tmpfs`,
      `a file set lists exactly the records its file owns`, `a file set runs the records its file owns`,
      `a file set no record owns exits 2`, `a git diff names the records its file owns`) are cases of
      `loop-mutations-selftest.sh`, run by 7.2.
- [x] 7.2 Run `bash tools/loop/tests/loop-mutations-selftest.sh`. Spec `agent-issue-loop`, the two modified
      requirements. Verification: exit 0.
      **Result:** all 34 cases passed — the harness reports an unapplied mutation, an unnoticed one and a real
      red, restores what it touches, backs up under the project and bounds a run by the files a change touched.
- [x] 7.3 Run every moved `*-selftest.sh` and `bash tools/tests/check-portability-selftest.sh`; run
      `bash tools/check-rule-routes.sh` and `bash tools/check-doc-routes.sh` for the doc-path rewrites. Specs
      `build-sbom`, `build-test-gate`, `highlight-measurement`, `test-coverage`, `tooling-test-layout`.
      Verification: all exit 0.
      **Result:** every moved selftest is green. The gate's own steps cover eleven of them — `loop-selftest`
      (382), `loop-mutations-selftest` (34), `issue-tooling-selftest`, `sensor-selftest`,
      `check-portability-selftest` (31), `check-action-runtimes-selftest`, `check-change-readiness-selftest`,
      `check-doc-routes-selftest`, `check-openspec-hygiene-selftest`, `check-sonar-workflow-selftest`,
      `ci-vcpkg-cache-selftest` and `mutate-selftest` — and the 18 it does not were run here, each exiting 0:
      `check-metadata` (10 cases), `resolve-stale` (36), `loop-spend` (27), `build-inputs` (14),
      `check-compose-sandbox-rule`, `check-rule-routes` (13), `check-submodule-pin`, `check-todo-references`,
      `declared-cases`, `gate-timings`, `gen-feature-list`, `look` (6), `measure-highlight` (12),
      `project-metrics-report` (52), `prune-native-configs`, `check-dex-gate-graph` (6),
      `check-jvm-test-graph` (3), `check-gate-artifacts` (3). `check-rule-routes.sh` reaches all 585 named
      rules; `check-doc-routes.sh` resolves 329 section and 7 skill references.
- [x] 7.4 State the exclusions. Spec `tooling-test-layout`. Verification: the summary names the changesets
      that no moved test is expected to reach, and any check skipped for a reason.
      **Result:** no Gradle module test and no device check is owed — the change touches no compiled file, so
      `:app`/`:auto` suites and the instrumented recipes reach none of it; the gate's compiled step is its
      own declaration, not a claim this change makes about an Android change. No check was skipped.

## 8. Archive

- [x] 8.1 Verify every `tooling-test-layout` scenario maps to a case or a named check, that every task traces
      to a requirement, and that no task references a deleted or renamed spec. Spec `tooling-test-layout`,
      all requirements. Verification: the scenario→case table in this change is complete.
      **Result:** all 10 scenarios are named below, 2 of them by a case and 8 by the check the task that
      owns the scenario runs; every task traces to one of the six capabilities (the trace is the row below),
      and the only spec names the tasks use are the six this change lands or modifies.

      | requirement | scenario | case or check | result |
      |---|---|---|---|
      | test code sits under the owning group's tests directory | a grouped tool's test moves beside its tool | the rename set, task 1.1 | ok |
      | test code sits under the owning group's tests directory | a root-level tool's test lands in tools/tests/ | the rename set, task 1.1 | ok |
      | test code sits under the owning group's tests directory | a production tool is not moved for its name | `bash tools/declared-cases.sh --help`, task 1.2 | ok |
      | a moved test resolves the tree without assuming its depth | a driver run from the new depth finds the tree | six `--list` runs from a directory below the root, task 2.1 | ok |
      | a moved test resolves the tree without assuming its depth | a cross-reference names the new path | the grep over `tools/`, task 3.1 | ok |
      | the portability check excludes tests by location | a test directory is not scanned | `portability#testsDirectoryIsNotScanned` | PASS |
      | the portability check excludes tests by location | a production engine is still scanned | `portability#productionEnginesAreStillScanned` | PASS |
      | live references follow the tests, archived evidence does not | a live reference names the new path | the grep over live files, task 5.5 | ok |
      | live references follow the tests, archived evidence does not | archived evidence is not rewritten | `git diff --stat main..HEAD -- openspec/changes/archive`, task 6.3 | ok |
      | a moved test file keeps its name | a reference changes only the directory | the rename base-name comparison, task 1.4 | ok |

      The task → requirement trace: §1 → `tooling-test-layout` (the layout requirement and the naming one);
      §2 → the depth requirement; §3 → the depth requirement's cross-reference clause and the live-reference
      requirement; §4 → the portability requirement; §5 → the live-reference requirement; §6 →
      `tooling-test-layout` plus `agent-issue-loop`, `build-sbom`, `build-test-gate`, `highlight-measurement`
      and `test-coverage`; §7 → `agent-issue-loop` (both modified requirements) and `tooling-test-layout`;
      §8 → `tooling-test-layout`, all requirements.
- [x] 8.2 Archive the change once 7.1–7.3 are green and 8.1 holds. Verification:
      `openspec validate --changes --strict` exits 0, then `openspec archive separate-tooling-test-code`.
      **Result:** archived as `2026-11-09-separate-tooling-test-code`; the five MODIFIED deltas and the new
      `tooling-test-layout` capability applied (5 added, 2 modified). The one red gate step,
      `hotspot#theBoundIsAboveOneChangedFile`, is waived as pre-existing on `main` at `f11595fa` — evidence and
      the base run are `evidence/gate.txt`, "The waiver".
