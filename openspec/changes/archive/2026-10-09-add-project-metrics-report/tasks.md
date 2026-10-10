# Tasks

## 1. CLI contract and the self-test harness

- [x] 1.1 Write `tools/project-metrics-report.sh` with the house-style usage block in its own header and `--help` printing that block through `sed -n '3,30p'` (template: `tools/gen-feature-list.sh`); verification: `bash tools/project-metrics-report.sh --help` prints the usage block and exits 0 (spec scenario *An unsupported option is refused* needs that usage text to exist).
- [x] 1.2 Implement the exit-status contract — 0 for a printed report, 2 for an unsupported option, a missing scope path or a missing required tool, and never 1; verification: named cases assert all four statuses (spec scenarios *A printed report exits 0*, *A bad scope exits 2*, *Figures never decide the status*).
- [x] 1.3 Build the self-test harness `tools/project-metrics-report-selftest.sh`: a fixture tree under a temporary directory, one named case per rule, one line printed per case, exit 0 only when all pass; verification: the harness runs green and prints every case name.
- [x] 1.4 Case: the self-test needs no connected device, emulator, head unit or network — it must run with `PATH` restricted to the fixture stubs and must not call `adb`; verification: named case *No device and no network* passes (spec scenario of that name, and the requirement *The counting rules are covered without a device*).
- [x] 1.5 Revert-check: mutate the unsupported-option path so it continues instead of exiting 2; the case *An unsupported option is refused* must fail; restore it and re-run `bash tools/project-metrics-report-selftest.sh` green (the Gradle `-PforceTests` form of a forced green run does not apply to a shell self-test); verification: the failing run and the restored green run are recorded with their output. This task and its eight siblings are the evidence for the spec scenario *A broken rule fails a named case*.

## 2. Input rule: pruned walk, excluded classes, stated header

- [x] 2.1 Walk the scan scope (default: repository root) and prune at directory level every class of the documented list — `build`, `hostbuild`, `.gradle`, `.kotlin`, `.cxx`, `vcpkg`, `.git` — plus the vendored submodule root `app/src/main/cpp/libosmscout`, descending into none of them; verification: on this repository the run finishes in seconds with the 3.3 GB submodule present, and no path under a pruned directory appears in any list (spec scenario *Excluded files are never listed*).
- [x] 2.2 Print the header first: run date, scan scope, files measured, and every excluded class with its path, the vendored tree named as external; verification: the header is the first output and names all four items, and adding one in-scope file raises files-measured by exactly one (spec scenarios *Header names the subject*, *Files measured tracks the scope*, *An excluded class is stated*, *Vendored source is external*).
- [x] 2.3 Case: run with no version-control metadata and no `git` on `PATH` (fixture tree, restricted `PATH`) — the report is produced, the run exits 0, and a file unknown to version control is counted; verification: named cases *Uncommitted files are measured* and *No version-control tooling is needed* pass (spec scenarios of those names).
- [x] 2.4 Revert-check: mutate the prune so `build/` is descended; the case *Excluded files are never listed* must fail; restore it and re-run the self-test green; verification: both runs recorded.
- [x] 2.5 Revert-check: mutate the header so it stops naming the vendored class; the case *Vendored source is external* must fail; restore it and re-run the self-test green; verification: both runs recorded.

## 3. Layout discovery, shape check, unclassified bucket

- [x] 3.1 Discover source roots by matching `<module>/src/<sourceSet>/{java,kotlin}/…` and refuse a scope in which no root is discovered — exit 2, the offending path named, no figures printed; verification: named case *An unrecognizable root is refused* asserts the status, the message and the absence of metric output (spec scenario of that name).
- [x] 3.2 List every source file outside every discovered root in an unclassified bucket with its path, attributed to no module, package or layer figure; verification: named case *An unexpected file stays visible* passes and that file's lines appear in no module, package or layer sum (spec scenario of that name).
- [x] 3.3 Case coverage for the layout variants — nested package (`ui/map`), a file with no package path, a foreign package (`com.framstag`), a Kotlin source root (`src/main/kotlin`); verification: named case *Layout variants are covered* asserts all four outcomes (spec scenario of that name).
- [x] 3.4 Revert-check: mutate the shape check so an empty discovery is accepted; the case *An unrecognizable root is refused* must fail; restore it and re-run the self-test green; verification: both runs recorded.
- [x] 3.5 Revert-check: mutate the unclassified bucket so its files are attributed to the first module; the case *An unexpected file stays visible* must fail; restore it and re-run the self-test green; verification: both runs recorded.

## 4. Parameters: dimensions, additivity, source sets, ratios, platform share

- [x] 4.1 Print code, comment, blank and total lines with file counts per language, per module, per full package path, per first-level layer and per file; verification: named case *Line kinds sum to the total* passes and all five dimensions appear in the fixture report (spec scenario *Line kinds sum to the total*, requirement *Figures are per dimension and additive*).
- [x] 4.2 Print the architecture distribution — files and lines per package, package depth, and complete ranked lists of every package and every file; verification: named cases *Dimension sums agree* and *Every file appears* pass, the latter with N fixture files producing N rows (spec scenarios of those names).
- [x] 4.3 Separate production, unit-test, instrumented-test and documentation figures; verification: named cases *Test and production are split* and *Documentation is its own kind* pass (spec scenarios of those names).
- [x] 4.4 Print every derived ratio with its operands — comment share, test share, documentation share, average lines per file; verification: named cases *The operands are named* and *A printed ratio is reproducible* pass, the latter recomputing each ratio from the printed figures (spec scenarios of those names).
- [x] 4.5 Report the phone and car shares from the recognized layout using the design's convention (module `auto`, plus app-module paths naming the car or auto surface), printing both shares with the figures they came from and a zero row when a surface holds no code; verification: named cases *Both surfaces are named* and *An empty surface is not omitted* pass (spec scenarios of those names).
- [x] 4.6 Case: the report is complete — every package and file listed, no truncation, no option narrowing it; verification: named case *No truncation* passes over a fixture holding more packages than a screen, and every list's row count equals its dimension's count (spec scenario of that name, requirement *The report is complete*).
- [x] 4.7 Revert-check: mutate the split so test and production figures are merged into one row; the case *Test and production are split* must fail; restore it and re-run the self-test green; verification: both runs recorded.
- [x] 4.8 Revert-check: mutate the platform section to omit a surface holding no code; the case *An empty surface is not omitted* must fail; restore it and re-run the self-test green; verification: both runs recorded.
- [x] 4.9 Head every figure column of every per-dimension table, including the blank-line column, and assert it with the case *Every figure column is headed* (spec scenario of that name): the header must name FILES, CODE, COMMENT, BLANK and TOTAL and hold exactly one cell fewer than a data row's fields.
- [x] 4.10 Divide the test share by code lines outside documentation and print that operand, asserted by the case *Test share excludes documentation* (spec scenario of that name): the printed denominator must equal the sum of the non-documentation source-set code lines.
- [x] 4.11 Make the per-file lists source-only — documentation and OpenSpec store files are counted and aggregated with their own file and line counts printed beside the list — asserted by the cases *Every source file appears* and *Documentation is aggregated, not listed* (spec scenarios of those names).
- [x] 4.12 Revert-check: drop the blank-line header cell from the table header; the case *Every figure column is headed* must fail; restore it and re-run the self-test green; verification: both runs recorded.
- [x] 4.13 Revert-check: set the test-share denominator back to the total code lines; the case *Test share excludes documentation* must fail; restore it and re-run the self-test green; verification: both runs recorded.
- [x] 4.14 Revert-check: replace the source-only filter with an unconditional listing; the cases *Documentation is aggregated, not listed (no document row)* and *… (no store row)* must fail; restore and re-run the self-test green; verification: both runs recorded.

## 5. OpenSpec inventory from the CLI

- [x] 5.1 Read the open-change, archived-change and feature-directory counts from `openspec list --json` and `openspec list --specs --json` with `jq`, print them as their own section, and mark the section unavailable — with the run still exiting 0 — when the CLI is absent; verification: named cases *The CLI is the source* and *A missing CLI does not fail the run* pass (spec scenarios of those names).
- [x] 5.2 Verify the counts against this repository: the printed feature-directory count equals `openspec list --specs --json | jq '.specs|length'` in the same run (measured 155 on 2026-10-08, against the predecessor's 147); verification: the two figures agree (spec scenario *The CLI is the source*).
- [x] 5.3 Case: a second OpenSpec store inside the scope does not contribute to the counts; verification: named case *A nested store is not this project's inventory* passes against a fixture carrying a nested store (spec scenario of that name).
- [x] 5.4 Revert-check: mutate the inventory to read directories instead of the CLI; the case *The CLI is the source* must fail; restore it and re-run the self-test green; verification: both runs recorded.

## 6. Determinism and completeness

- [x] 6.1 Order every list by its figure, ties ordered by name, under `LC_ALL=C`; verification: named cases *Byte-identical apart from the date*, *Ties are ordered by name* and *Locale does not reorder* pass (two consecutive fixture runs, an artificial tie, and a run under a different locale) (spec scenarios of those names).
- [x] 6.2 Revert-check: mutate the comparison so the name tie-break is dropped; the case *Ties are ordered by name* must fail; restore it and re-run the self-test green; verification: both runs recorded.

## 7. Reconciliation with the predecessor and its removal

- [x] 7.1 Run `tools/count-loc.sh` and the new tool over this repository and reconcile the language, module and layer figures; verification: the figures agree except the OpenSpec row, whose 147-against-155 difference is explained by the CLI-versus-glob defect and recorded in the change.
- [x] 7.2 Delete `tools/count-loc.sh` once 7.1 reconciles; verification: the file is gone and the new tool still prints the reconciled report.

## 8. Documentation home

- [x] 8.1 Write `guidelines/Metrics.md` with the normative definitions — the input rule and exclusion classes, the layout and shape check, the counting definitions, the metric catalogue with one definition per metric, the platform convention, the invocation, the exit statuses; verification: every section and metric the tool prints has a definition in the document, checked by walking the report's section list against the document's headings.
- [x] 8.2 Add the `guidelines/Metrics.md` row to the `AGENTS.md` documentation map; verification: `bash tools/check-doc-routes.sh` exits 0.
- [x] 8.3 Revert-check: remove the `AGENTS.md` row again; `bash tools/check-doc-routes.sh` must fail naming `guidelines/Metrics.md`; restore the row and re-run green — this falsifies the CI gate at `.github/workflows/build.yml:131`; verification: both runs recorded.

## 9. Build, artifact hygiene and scenario coverage

- [x] 9.1 Verify the tree still builds and existing tests pass, scoped to what this change can affect: `./gradlew :core:testDebugUnitTest` — this change adds a shell tool and a Markdown guideline and changes no Gradle, Kotlin, Java, C++, JNI or resource file, so the both-flavor gate is not required, and that reasoning is part of the task's evidence.
- [x] 9.2 Verify the artifact hygiene the CI gate checks: `openspec doctor` and `openspec validate add-project-metrics-report --strict` both pass, and every tracked task in this file uses the `- [x]` / `- [ ]` marker.
- [x] 9.3 Verify scenario coverage: extract every `#### Scenario:` name from `openspec/changes/add-project-metrics-report/specs/project-metrics-report/spec.md` and confirm each is named by a task above or by a case in the self-test; verification: the comparison prints no missing scenario.

## Recorded evidence

Plain bullets: review material, not tracked task progress.

- **Revert-checks (2026-10-09, re-run against the final tool)** — each mutation was applied to `tools/project-metrics-report.sh`, the named case had to fail, the tool was restored, and the suite had to be green again (47/47):
  - 1.5 `usage >&2; exit 2 ;;` → `exit 0` → case *An unsupported option is refused* failed · restored green
  - 2.4 `-name build` → `-name build_never` → case *Excluded files are never listed* failed · restored green
  - 2.5 `printf 'vendored source…'` → `printf 'tooling state…'` → case *Vendored source is external (stated)* failed · restored green
  - 3.4 the shape-check guard replaced by `|| true` → case *An unrecognizable root is refused* failed · restored green
  - 3.5 unclassified files attributed to module `tools` → case *An unexpected file stays visible (unattributed)* failed · restored green
  - 4.7 `return "unit test"` → `return "production"` → case *Test and production are split* failed · restored green
  - 4.8 car row printed only when `CAR_CODE > 0` → case *An empty surface is not omitted* failed · restored green
  - 5.4 `--specs --json` → `--json` (so the feature count cannot come from the CLI) → case *The CLI is the source* failed · restored green
  - 6.2 tie key `-k2,2` → `-k2,2r` → case *Ties are ordered by name* failed · restored green
  - 8.3 the `AGENTS.md` row deleted → `bash tools/check-doc-routes.sh` exited 1 with `::error file=AGENTS.md::guidelines/Metrics.md — the routing table does not name this document` · restored: 82 section references resolve
- **Reconciliation (7.1)** — Kotlin: independent count over the same on-disk file set (documented rule, `find` + `awk`) = 650 files / 91 938 code / 23 941 comment / 14 150 blank / 130 029 total, identical to the report's row. Predecessor: 615 files / 89 722 code, and the delta closes exactly — 615 tracked − 8 tracked build scripts + 43 untracked `.kt` = 650. Language regrouping (predecessor's `XML/Gradle`, `Shell/AWK`, `Config` rows), the documentation comment definition (predecessor counted Markdown `#` headings as comments: 16 607 lines), the seven excluded classes and the untracked WIP files account for every remaining difference.
- **OpenSpec row** — report 155 feature directories in the same run as `openspec list --specs --json | jq '.specs|length'` = 155; the predecessor printed 147 from directory globs, which is the defect D1 of the proposal.
- **Input rule on this repository (2.1)** — 2904 counted paths, 0 of them from a pruned class, 0 build/hostbuild directories outside `/src/`, and the 11 files of the source package `buildSrc/src/main/kotlin/com/naviveylin/build/` present; a whole-tree run takes ~4 s, of which ~2.2 s is the two `openspec` CLI calls.
- **Verification** — `bash tools/project-metrics-report-selftest.sh` 52/52; `bash tools/check-doc-routes.sh` 82 references resolve; `openspec validate add-project-metrics-report --strict` valid; `openspec doctor` exit 0; `./gradlew :core:testDebugUnitTest` BUILD SUCCESSFUL in 24 s (the run prints a pre-existing Gradle 10 deprecation warning, unrelated to this change); scenario coverage complete — every scenario the delta states is named by a task or a case.
- **Second review pass (2026-10-09, owner review of the printed report)** — three defects, each fixed, guarded and falsified:
  - every per-dimension table printed four column headers for five figure columns: the blank-line column was unheaded, because the header helper spent its first argument on the label slot. Fixed in the helper; case *Every figure column is headed*.
  - the test share divided by total code lines, so 94 320 lines of documentation diluted it. It now divides by code lines outside documentation — `56206 / 112713` = **49.9%** in the reviewed run, against 27.1% before. Case *Test share excludes documentation*.
  - the per-file lists carried 1557 Markdown documents and 261 `openspec/**` store files (including ~250 two-line `.openspec.yaml` rows, which is why the YAML row reads 263 files but 957 lines). The lists are now source-only with the aggregates printed beside them: `not listed here: 1557 documentation file(s) (127356 lines), 261 OpenSpec store file(s) (654 lines)`. Cases *Every source file appears* and *Documentation is aggregated, not listed*. The spec's completeness requirement was reworded from "every file" to "every source file" with two new scenarios, and `guidelines/Metrics.md` §4, §5 and §7 now state that documentation is counted but never mixed into a source figure.
  - revert-checks 4.12, 4.13, 4.14: blank-line cell dropped → *Every figure column is headed* failed; denominator restored to the total → *Test share excludes documentation* failed; filter replaced by an unconditional listing → both aggregation cases failed. Each restored to green 52/52.
  - two of my own assertions were the weak part, not the tool: dropping the TOTAL header cell left BLANK in place, so a count-only check stayed green (the case now asserts the names and the cell count), and one case used different names on its pass and fail paths, which masked whether it had failed.
- **Five defects found while implementing, each fixed and now guarded by a case** — the external sort's separator was written with single quotes inside the single-quoted awk program, so the shell consumed them and sort used the letter `t`, leaving the tie key inert (ordering survived only on GNU sort's whole-line fallback); module attribution took the first path segment, which broke every absolute scope; `emit()`'s redirected rows were still buffered when `sort` read them, so tables sorted empty files; the archived-change count printed 0 because `openspec list --json` reports only active changes (fixed with `openspec list --all --json`, 23 open / 243 archived, cross-checked against `openspec list --archived --json`); and a count that could not be read printed as a count of 0 instead of stating that the inventory was unusable, which the per-count validation now prevents (`Unreadable CLI output is not a count of 0`).

## Workflow follow-up

- At archive time, add `"project-metrics-report": { "area": "build-tooling", "userVisible": false, "surfaces": [] }` to `tools/feature-list/specs.json` and prove both directions with `bash tools/gen-feature-list.sh --check-classification`. It must land with the archive, not before: an entry for a capability that has not shipped is rejected as `stale classification entry for a spec that no longer exists` (measured 2026-10-08, `specs read: 155`, `classified: 154`, exit 1).
- Archive the change once review is satisfied, then re-run `bash tools/gen-feature-list.sh --check-classification`.
- Pre-existing and outside this change: `map-repository-source` and `map-source-selection` are shipped spec ids without a classification entry, so the classification gate is red for reasons this change does not touch — record it in `TODO.md` or its own change.
- Pre-existing and outside this change: `guidelines/FeatureList.md:8` names three behaviour contracts that do not exist on disk (`spec-feature-index`, `feature-list-generation`, `release-notes-generation`).
