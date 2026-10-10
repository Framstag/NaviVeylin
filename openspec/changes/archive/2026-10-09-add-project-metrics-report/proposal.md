# Proposal

## Why

The only thing in this repository that answers *how large is which part of the project, and where does it sit* is an untracked scratch script (`tools/count-loc.sh`, written 2026-10-08). It has proven its value — it exposed the per-package distribution of the 89 721 Kotlin code lines and the 3.4x external mass in the vendored submodule — but its foundation is wrong for the job it is used for.

It derives its input from `git ls-files`, so it cannot run where git is absent, and it measures a different subject depending on VCS state. One of its rows is already proven wrong: it printed 147 feature directories while the project has 155 (`openspec list --specs --json`). It violates a documented rule — `guidelines/Build.md` §4 states that OpenSpec interaction always goes through the CLI's own `--json` output, never a script. It has no self-test, so a silent miscount cannot be caught. And nothing in its output says which state was measured, while the Kotlin count moved 89 681 → 89 721 lines within one session at an unchanged file count (615).

Making the tool permanent means defining its contract first: one deterministic input rule, a stated header, a complete report, and a test that fails when the counting rules break.

## What Changes

- **New tool** `tools/project-metrics-report.sh`: reads the project **on disk** (no git, no VCS state as subject), prints a **complete** text report to stdout for manual inspection, takes positional scope paths (`tools/project-metrics-report.sh app core`), and offers only `--help` besides them. No `--json`, no snapshots, no baselines, no thresholds, no gate semantics.
- **Header**: run date, scan scope, number of files measured, and the excluded classes, so a printed report is attributable without a commit id.
- **Input rule — classify, do not drop**: a filesystem walk pruned at directory level for build outputs and caches (`build/`, `hostbuild/`, `.gradle/`, `.kotlin/`, `.cxx/`, `vcpkg/`, VCS internals). The vendored submodule `app/src/main/cpp/libosmscout` is classified at its root **without descending** and stated in the header rather than counted. No filter is silent.
- **Shape check**: a source file whose path does not match the expected layout (`<module>/src/<sourceSet>/{java,kotlin}/<package>/…`) is reported with its path in an unclassified bucket, and a tree whose root shape is unrecognizable is refused with exit code 2 instead of producing numbers (`guidelines/Build.md` §4: "A tool that infers a directory layout needs a shape check").
- **Parameters** (v1): size lines and file counts per language; per module; per full package path; a first-level layer index; files and lines per package as a distribution; package depth; every package ranked; every file ranked; main / test / androidTest / docs split; ratios (comment share, test share, docs share, average lines per file); platform share; and OpenSpec counts (open changes, archived changes, feature directories) sourced from `openspec list --json` / `openspec list --specs --json`.
- **Determinism**: locale-independent sorting with a deterministic tie-break by name, so two runs over the same tree print the same order.
- **New guideline** `guidelines/Metrics.md` owning the metric catalogue, each metric's definition, the exclusion rule and the invocation; one new row in the `AGENTS.md` documentation map routes to it.
- **New internal capability** `project-metrics-report` (area `build-tooling`, `userVisible: false`), classified in `tools/feature-list/specs.json` **at archive time** — the entry is required so the feature-list run is not left failing by a shipped spec id the classification does not name (`guidelines/FeatureList.md` §4), and it must not be added earlier, because the gate rejects an entry for a spec that does not exist yet as a stale entry.
- **New self-test** `tools/project-metrics-report-selftest.sh`: builds a fixture tree (nested packages, a file with no package path, a foreign package, `src/main/kotlin`, comment styles, an excluded `build/` tree, a fake vendored tree) and asserts the numbers, the exclusion statements and the shape-check refusal, with no device and no network.
- **Dropped**: the scratch `tools/count-loc.sh` is not promoted and is deleted once the new tool reports the same numbers.

No behaviour of the application changes. Not breaking.

## Capabilities

### New Capabilities

- `project-metrics-report`: what one run of the project metrics report measures and guarantees — the input rule, the header, the parameter set, the shape check, the exit-code contract and the completeness of the printed report.

### Modified Capabilities

- None. Checked against the inventory rather than assumed: `kover-aggregate-report` is coverage-only (root merge, aggregated report, no deprecation regression), and `build-test-gate` governs the build/test gate and its timings, which this tool is not. `documentation-ownership` already requires that every guideline document is covered by the route check, so a new guideline needs no delta there — it needs the `AGENTS.md` row the requirement already demands.

## Impact

- **Created**: `tools/project-metrics-report.sh`, `tools/project-metrics-report-selftest.sh`, `guidelines/Metrics.md`, and the change's own `specs/project-metrics-report/spec.md` (capability delta, published by archiving).
- **Changed**: `AGENTS.md` (one documentation-map row), `tools/feature-list/specs.json` (one classification entry, added at archive time).
- **Deleted**: `tools/count-loc.sh` (untracked scratch, no history affected).
- **Unaffected**: no application code, no Gradle configuration, no dependency, no native/JNI surface — no submodule patch and no `:osmscout-client-java` override, and the vendored submodule is read at its root only.
- **Enforcement points that make this change fail loudly rather than silently**: CI runs `bash tools/check-doc-routes.sh` (`.github/workflows/build.yml:131`), so a new guideline without its `AGENTS.md` row fails the build; `tools/gen-feature-list.sh --check-classification` fails on a shipped spec id missing from `tools/feature-list/specs.json`.
- **Rollback**: delete the created files, revert the two one-line changes, delete `tools/count-loc.sh` — nothing depends on the tool at runtime and no application behaviour is involved.
- **Scope**: developer tooling only. It reaches neither the phone UI nor the car surface, so it carries no phone/car parity requirement.
