# Tasks — fix-ci-action-node-runtime

Parent spec: `openspec/changes/fix-ci-action-node-runtime/specs/ci-action-runtimes/spec.md`, "A workflow pin
names a major that declares Node 24" and "An action the check has no recorded runtime for is a defect".

## 1. The red case, before the fix

- [x] 1.1 `tools/check-action-runtimes.sh`: the check and its recorded floor table (action, first Node 24 major,
  the `action.yml` URL the declaration was read from). Verification: `bash tools/check-action-runtimes.sh` on
  HEAD → exit 1, six `::error` lines naming `.github/workflows/build.yml:506,529,540,547,557` and
  `.github/workflows/sensors.yml:59` (`evidence/red-check.log`, 2026-10-22T01:34:27+02:00, run against the
  base commit's workflows as `git show 80df3a3:.github/workflows/<file>`).
- [x] 1.2 `tools/check-action-runtimes-selftest.sh`: ten cases — the repository's own pins, the floor's two
  sides, a subpath, an unrecorded action, an unverifiable ref, a skipped container or local action, a workflow
  with no pin, and a missing workflow directory. Verification: `bash tools/check-action-runtimes-selftest.sh` on
  HEAD → `1 of 10 cases failed`, `EXIT=1`, the failing case being `this repository's workflows pin a Node 24
  major` (`evidence/red-check-action-runtimes-selftest.log`,
  `evidence/red-check-action-runtimes-selftest.xml` `tests=10 failures=1`, 2026-10-22T01:33:31+02:00).

## 2. The pins

- [x] 2.1 `.github/workflows/build.yml` — five `actions/upload-artifact@v5` → `@v7`. Verification: the same
  check exits 0 and prints `action runtimes: 10 action pins across 2 workflow files target Node 24.`
- [x] 2.2 `.github/workflows/sensors.yml` — `actions/checkout@v4` → `@v5`, the major `build.yml` uses.
  Verification: the same run's count (10 pins) and its zero exit; `evidence/green-check.log`.
- [x] 2.3 The pins' inputs are unchanged between the old and the new major: `name`, `path`,
  `if-no-files-found` on every upload step; `v7` adds `archive` with default `'true'`. Verification: each
  step's `with:` block is untouched by the version edit — `git diff .github/workflows/` shows version lines
  only.

## 3. The gate for the class

- [x] 3.1 `.github/workflows/build.yml` — the hygiene step `Check action runtimes` (checker + self-test), beside
  `Check Compose sandbox rule`. Verification: `yq -r '.jobs.build.steps[] | select(.name=="Check action
  runtimes") | .run' .github/workflows/build.yml` prints both commands; the workflow parses
  (`python3 -c 'import yaml,sys; yaml.safe_load(open(".github/workflows/build.yml"))'`).
- [x] 3.2 `tools/loop/loop.conf` — `LOOP_GATE_SUITES` gains `tools/check-action-runtimes*` and
  `.github/workflows/*`, both mapping to `tools/check-action-runtimes-selftest.sh`. Verification:
  `LOOP_CONF=<worktree>/tools/loop/loop.conf bash tools/loop/gate.sh --plan --base 80df3a3` lists that suite
  (`evidence/gate-plan-worktree-config.log`). The caveat is recorded rather than assumed: this run exports
  `LOOP_CONF` pointing at the *project* tree's copy of the same file, which predates the change, so the run's
  own gate run below does not see the new entry — the mapping takes effect for the next run, and the checker's
  cases were run focused here.

## 4. The rule and its route

- [x] 4.1 `guidelines/Build.md` §2 — the subsection "The actions' Node runtime": the pin decides the runtime,
  the two rules (pin the first Node 24 major or later; record a new action in the floor table), the CI step, and
  why the check fetches nothing. Verification: `bash tools/check-doc-routes.sh` exits 0.
- [x] 4.2 `AGENTS.md` — the routing-table row that owns `Build.md` §2 names the new subsection. Verification:
  the same run of `tools/check-doc-routes.sh`.
- [x] 4.3 `openspec/changes/fix-ci-action-node-runtime/specs/ci-action-runtimes/spec.md` — the capability, with
  its `## Purpose` written here rather than left to `openspec archive`. Verification: `openspec validate
  fix-ci-action-node-runtime --strict` → `is valid`, `EXIT=0` (`evidence/openspec-validate.log`);
  `openspec doctor` → `OpenSpec root: ok`.

- [x] 4.4 `tools/feature-list/specs.json` — classify the new capability (`build-tooling`, internal, no surface),
  because the pipeline's `Check capability classification` fails on a shipped spec with no entry (found by
  running that check after the archive, not by reading it). Verification:
  `bash tools/gen-feature-list.sh --check-classification` → `classified: 177, unclassified: 0`, `EXIT=0`, and
  `--list` prints the three requirements as internal (`evidence/classification.log`); the generator's own cases
  `108 passed, 0 failed` (same log).

## 5. Verification of a delta that compiles nothing

- [x] 5.1 `bash tools/loop/gate.sh --plan --base 80df3a3` names what this delta owes. Verification:
  `evidence/gate-plan.log` (the run's own configuration: Gradle pulled in by the evidence `.xml`, plus
  `tools/loop-selftest.sh`, `tools/sensor-selftest.sh`, `tools/check-doc-routes.sh`; the new self-test joins
  them only under the worktree's config, task 3.2).
- [x] 5.2 `bash tools/check-action-runtimes-selftest.sh` → `all 10 cases passed`, `EXIT=0`
  (`evidence/green-check-action-runtimes-selftest.log`); a focused run is not gate evidence.
- [x] 5.3 `bash tools/check-doc-routes.sh` → exit 0 (`evidence/doc-routes.log`).
- [x] 5.4 The gate, run once, exactly as the run's configuration states it (`./tools/loop/gate.sh`, 2026-10-22T01:35:13+02:00 → 01:48:46+02:00): `LOOP-GATE: tests=195 failures=0 (./gradlew test -PforceTests --no-build-cache, tools/loop-selftest.sh, tools/sensor-selftest.sh, tools/check-doc-routes.sh)`,
  `EXIT=0` (`evidence/gate.log`). Its per-suite tallies are `evidence/gate-tools_loop-selftest.sh.xml`
  (150 cases), `evidence/gate-tools_sensor-selftest.sh.xml` (45) and
  `evidence/gate-tools_check-doc-routes.sh.xml` (0 cases — the check prints a reference count, not cases). The
  Gradle leg carries no `cases` line of its own, so its 0 in the 195 is a counting property of the harness and
  not a skipped run: Gradle's own JUnit XMLs under `app/build/test-results/test{Mobile,Automotive}DebugUnitTest`
  were written inside the gate's window (01:37 and 01:38), each 286 suites / **2096 tests / 0 failures / 0
  errors** (`evidence/gradle-test-results-summary.log`).
- [x] 5.5 The focused case the delta's own rule adds was run beside the gate, because the run's `LOOP_CONF`
  points at the project tree's copy — task 3.2's caveat: `bash tools/check-action-runtimes-selftest.sh` → `all 10
  cases passed`, `EXIT=0` (`evidence/green-check-action-runtimes-selftest.log`).
- [x] 5.6 Three edits landed after the gate completed at 01:48:46: the archive move, this file, and
  `tools/feature-list/specs.json` (task 4.4). The suites that cover them were re-run focused —
  `tools/check-doc-routes.sh` (120 section references, 5 skill paths), `tools/check-action-runtimes.sh` (10 pins
  across 2 files) and its self-test (10 of 10), `tools/gen-feature-list.sh --check-classification` and the
  generator's own cases (108 passed, 0 failed): `evidence/suites-after-archive.log`,
  `evidence/classification.log`. Excluded from the re-run, with the reason: the Gradle leg, `tools/loop-selftest.sh`
  and `tools/sensor-selftest.sh` — no post-gate edit touches a compiled path, `tools/loop/loop.conf` (unchanged
  since the gate) or `.github/workflows/sensors.yml` (unchanged since the gate).

## 6. Evidence and archive

- [x] 6.1 `evidence/` carries the red case and its XML (`red-check.log`,
  `red-check-action-runtimes-selftest.{log,xml}`), the green halves, the gate plan under both configurations,
  the gate log with its three tallies and the Gradle summary, the doc-routes run and the OpenSpec validation;
  the run's own copy lives under `.pi/issue-loop/items/240/`.
- [x] 6.2 Archive the change; confirm the delta merged into `openspec/specs/ci-action-runtimes/spec.md` with the
  authored `## Purpose`.
