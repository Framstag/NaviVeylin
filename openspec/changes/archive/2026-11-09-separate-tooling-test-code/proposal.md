# Proposal

## Why

`tools/` is 46,624 lines and **60% of it is test code** (27,706 lines: the `*-selftest.sh` drivers, their
`*-cases.sh` companions, the `*-mutations.sh` / `*-mutations.json` falsification sets and the fixtures they
read). That test code sits in the same directories as the production tools it exercises — `tools/loop/run.sh`
beside `tools/loop-selftest.sh`, `tools/check-doc-routes.sh` beside `tools/check-doc-routes-selftest.sh`.
Nothing in the layout says which of two neighbouring files ships to a reader of the engine and which exists
only to test it, so a reader pays the test code's weight to find the engine and an editor of an engine open
the wrong file. The separation is free: the test code names its subjects by path, so a directory change
costs a rename and the reference updates, not a redesign.

This is a **structural** change. No tool's behaviour changes, no compiled file is touched, and the tooling's
own gates (`tools/loop-selftest.sh`, `tools/check-portability-selftest.sh`, the check selftests) are the
verification.

## What Changes

- **Test code moves under its group's `tests/` directory.** A grouped tool's tests go to
  `tools/<group>/tests/` (`loop`, `issues`, `sensors`); a tool that sits at the `tools/` root keeps its tests
  in `tools/tests/`. Production tools do not move — including `tools/declared-cases.sh`, a production
  selection tool whose name merely ends in `-cases.sh`.
- **Fixtures split shared from local.** The shared fixture tree `tools/fixtures/` (the synthetic foreign
  project and the mutation corpus) moves to `tools/tests/fixtures/`; each group's local fixtures move beside
  its tests (`tools/loop/fixtures/` → `tools/loop/tests/fixtures/`, and likewise for `issues`, `sensors`).
- **File names are unchanged.** Only the directory changes, so a reference update is a path edit and never a
  rename.
- **A moved driver no longer assumes its depth.** Every driver resolves the repository root from the tree it
  sits in (`git rev-parse --show-toplevel`) instead of `${BASH_SOURCE[0]}/..`, so `tools/<group>/tests/` and
  `tools/tests/` both find the tree.
- **The portability check excludes tests by location.** `tools/check-portability.sh` stops filtering its
  engine file set by a name suffix (`*-selftest.sh`, `*-cases.sh`, `*-mutations.sh`) and skips `tests/`
  directories instead — a new test file can no longer be scanned by accident, and a moved one cannot be
  missed by a filter that no longer matches its directory.
- **Cross-references are rewritten.** Every live reference — the CI workflow (~30 calls), the guidelines, the
  `.pi/skills/*` skills (46 refs), `PORTING.md`, `AGENTS.md`, the affected specs, and each mutation set's
  `case-cmd` / `comment` — names the new path. The archived evidence under `openspec/changes/archive/**` is
  history and stays byte-identical.

## Capabilities

### New Capabilities

- `tooling-test-layout`: where the tooling's test code lives — the `tests/` directory that owns a test driver,
  its mutation sets and its fixtures, the naming rule that keeps a move a path edit, the depth-independent
  root resolution a moved driver uses, the location-based exclusion the portability check applies, and the
  rule that live references are rewritten while archived evidence is not.

### Modified Capabilities

- `agent-issue-loop`: "The mutation harness leaves the tree exactly as it found it" and "A mutation run is
  scoped by the files the change touches" — the loop mutation harness and its self-test move to
  `tools/loop/tests/`, so the two requirements' scenario evidence names the new path.
- `build-sbom`: "A JVM-only test run does not require the native tree" — `check-jvm-test-graph-selftest.sh` is
  a `tools/`-root tool's test and moves to `tools/tests/`.
- `build-test-gate`: "The local-test asset APK follows its merged assets" — `check-gate-artifacts-selftest.sh`
  moves to `tools/tests/`.
- `highlight-measurement`: "A row qualifies by its longest colour run" — `measure-highlight-selftest.sh` moves
  to `tools/tests/`.
- `test-coverage`: "The dex gate waits for its variant's dex producer" — `check-dex-gate-graph-selftest.sh`
  moves to `tools/tests/`.

## Impact

**Moved — to `tools/tests/`** (a `tools/`-root tool's tests): `build-inputs-{cases,mutations,selftest}`,
`check-action-runtimes-selftest`, `check-change-readiness-{selftest,mutations.json}`,
`check-compose-sandbox-rule-selftest`, `check-dex-gate-graph-selftest`, `check-doc-routes-{selftest,mutations.json}`,
`check-gate-artifacts-selftest`, `check-jvm-test-graph-selftest`, `check-openspec-hygiene-selftest`,
`check-portability-selftest`, `check-rule-routes-{selftest,mutations.json}`, `check-submodule-pin-selftest`,
`check-todo-references-selftest`, `declared-cases-selftest`, `gate-timings-selftest`,
`gen-feature-list-selftest`, `look-selftest`, `measure-highlight-selftest`, `mutate-selftest`,
`portability-{cases,selftest,mutations.json}`, `project-metrics-report-selftest`,
`prune-native-configs-selftest`, plus the shared `tools/fixtures/` → `tools/tests/fixtures/`.

**Moved — to `tools/loop/tests/`**: `loop-cases.sh`, `loop-selftest.sh`, `loop-mutations.sh`,
`loop-mutations-selftest.sh`, `loop-spend-selftest.sh`, and `tools/loop/fixtures/` →
`tools/loop/tests/fixtures/`.

**Moved — to `tools/issues/tests/`**: `issue-tooling-{cases,selftest}`, `check-metadata-selftest`,
`metadata-mutations`, `resolve-stale-{cases,selftest}`, `resolve-stale-mutations.json`, and the mutation sets
whose `case-cmd` is the issue self-test — `close-`, `create-`, `duplicates-`, `status-`,
`decision-question-mutations.json` — and `tools/issues/fixtures/` → `tools/issues/tests/fixtures/`.

**Moved — to `tools/sensors/tests/`**: `sensor-{cases,selftest,mutations}` and `tools/sensors/fixtures/` →
`tools/sensors/tests/fixtures/`.

**Not moved**: `tools/declared-cases.sh` (production), `tools/mutate.sh` (production harness), and every
`tools/*.sh` / `tools/*.py` without a test suffix.

**Touched, not moved**: `.github/workflows/build.yml`; `guidelines/{Build,BuildGate,BuildEvidence,Metrics,Backlog}*.md`;
`.pi/skills/{revert-check,mutation-runner,portability-check,loop-spend,...}/SKILL.md`; `PORTING.md`;
`AGENTS.md`; `tools/check-portability.sh`; the six spec deltas above plus the shipped
`openspec/specs/highlight-measurement/spec.md` Purpose line.

**Classification**: additive to the repository layout, **behaviour-preserving** — no spec requirement's
*meaning* changes, only the path its evidence names. **Rollback**: revert the change's commit; no runtime
state, persistence or build input is affected, so no migration path is needed.

**Guidelines referenced**: `guidelines/BacklogTooling.md` §10 "Portability" (the copy list and the engine
boundary the check enforces) and `PORTING.md` (the "what to copy" list and the prerequisites table).

surface: 12 scenarios across 6 delta files — 12 added and 6 requirement bodies rewritten in place; 47 moved
test files and 97 live references, counted by `git ls-files 'tools/*' | grep -cE -- '-selftest\.sh$|-cases\.sh$|-mutations\.sh$|-mutations\.json$'`
and `grep -rhoE 'tools/[A-Za-z0-9/._-]*(selftest|cases|mutations)[A-Za-z0-9/._-]*' .github .pi/skills guidelines AGENTS.md PORTING.md | wc -l`.
