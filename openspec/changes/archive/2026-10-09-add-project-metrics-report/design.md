# Design

## Context

The tool that would become permanent exists only as scratch: `tools/count-loc.sh`, untracked, `git ls-files`
based, ~110 lines of `bash` + `awk`. It is the direct predecessor of `tools/project-metrics-report.sh`, and the
numbers it prints today are the reconciliation baseline for this change (Kotlin 89 721 code lines over 615
files; module `app` 57 918, `auto` 20 829, `core` 8 176).

Constraints that shape everything below, from `guidelines/Build.md` and `guidelines/FeatureList.md`:

- §4 of `Build.md`: OpenSpec interaction always goes through the CLI's own `--json` output, never a script; and
  "a tool that infers a directory layout needs a shape check" (the failure that made `tools/prune-native-configs.sh`
  delete ABI directories inside hash trees).
- §4 of `Build.md`: `jq`/`grep`/`sed`/`awk` are the house style for text work; `python3` is reserved for what
  shell genuinely cannot express.
- The house template for a companion tool is `tools/gen-feature-list.sh`: usage block in its own header printed
  by `--help` via `sed -n '3,30p'`, exit status `0 ok / 1 gate failed / 2 usage or environment error`, and a
  device-free self-test (`tools/gen-feature-list-selftest.sh`).
- §4 of `FeatureList.md`: `tools/feature-list/specs.json` must name every shipped spec id, with the internal
  tooling specs classified `{"area": "build-tooling", "userVisible": false, "surfaces": []}`.
- CI runs `bash tools/check-doc-routes.sh` (`.github/workflows/build.yml:131`); it enumerates `guidelines/*.md`
  at run time and fails when `AGENTS.md` does not route to a document, so the new guideline and its `AGENTS.md`
  row are one unit.

See `proposal.md` — Why for the motivation and the proven defects.

## Goals / Non-Goals

**Goals**

- One deterministic input rule that produces the same figures in a dirty worktree, a clean checkout, and an
  export with no version-control metadata.
- A complete, attributable, locale-stable text report for manual inspection.
- Counting rules that fail loudly (shape check, exit 2) or visibly (unclassified bucket) rather than silently.
- A device-free self-test that names the rule each case holds.

**Non-Goals**

- No gate: no thresholds, no exit status driven by figures, no CI wiring.
- No machine-readable output, no stored snapshots, no run-over-run deltas (see trade-offs).
- No code-quality metrics — complexity, duplication, coverage, dependency graphs — and no attempt to count
  lines as a semantics-aware tokenizer would.
- No application, Gradle, native or JNI change of any kind; the tool is read-only over the source tree.

## Decisions

### D1 — Input is a pruned filesystem walk, not a VCS query

Walk the scan scope, pruning at directory level: any directory named `build`, `hostbuild`, `.gradle`, `.kotlin`,
`.cxx`, `vcpkg`, `.git`, plus the vendored submodule root `app/src/main/cpp/libosmscout` (classified, not
descended). The rule is applied before reading anything inside a pruned directory, so the 3.3 GB submodule and
the local build trees cost nothing.

*Alternatives.* (a) `git ls-files`, the current behaviour — rejected: it makes version-control state the
subject, dies where git is absent, and is the source of the mixed-subject defect. (b) Parse `.gitignore` — rejected:
it re-implements ignore semantics, still implies git's model, and drifts from the actual ignore rules. (c) Hybrid
(git when present, walk otherwise) — rejected: two code paths, two numbers for one tree, and the CI/local
difference the requirement forbids.

### D2 — The exclusion rule lives in the tool and in the guideline, printed in the header

The class list is an array inside the script, printed in the report header as class + path, and its normative
description lives in `guidelines/Metrics.md`.

*Alternatives.* (a) A data file (`tools/feature-list/`-style directory) — rejected: one artifact per rule adds a
file to parse for no review benefit at this size. (b) Derive from `.gitignore` — rejected with D1. (c) Gradle-
provided exclusions — rejected: needs a build; the report must run without one.

### D3 — Source layout is discovered, and an unrecognized root is refused

Discover source roots by matching `<module>/src/<sourceSet>/{java,kotlin}/<package>/…`. A source file outside
every discovered root is listed with its path under an unclassified bucket and attributed to no module, package
or layer figure. A scope in which no root is discovered is refused with exit 2 and the offending path named.

*Alternatives.* (a) A fixed path template per module — rejected: a layout shift silently moves everything into
one bucket; this is the exact defect `Build.md` §4 records. (b) Ask Gradle for its source sets — rejected: it
needs a JVM, a Gradle run and a working build, none of which the report may depend on. (c) Regex-only detection
without refusal — rejected: it cannot distinguish "no sources here" from "shape changed".

### D4 — `bash` + `awk` + `jq`, OpenSpec through the CLI

One script, no build step, no interpreted-runtime dependency beyond what the repo already requires (the CLI's
`--json` output parsed with `jq`).

*Alternatives.* (a) `python3` — rejected: allowed by `Build.md` §4 but explicitly reserved for what shell cannot
express; text aggregation per language, module, package and file is what `awk` is for. (b) A Kotlin/Gradle task
— rejected: it needs the build to work before a report can be produced, and the report is not part of the build.
(c) Extend `tools/gate-timing-report.sh` — rejected: that tool observes the gate; a gate-shaped home for a
non-gate measurement would violate the one-normative-home rule.

### D5 — Surface attribution is layout plus naming convention

Car code is the `auto` module plus paths in the app module that name the car or auto surface (`*Car*`, `*Auto*`
in `di/`, `data/`, `ui/map/`, `navigation/`, the app-root entry points `NaviVeylinCarAppService.kt` and
`AutomotiveDevice.kt`, and their test counterparts). Everything else in the app module is phone code.

*Alternatives.* (a) Module only — rejected: the nine car main files and sixteen car test files inside `app` would
be reported as phone code. (b) Import-based (`androidx.car.app.*`) — rejected: it reattributes dual-purpose files
wholesale (`MainActivity.kt`, `service/NavigationNotificationBuilder.kt`,
`service/NavigationNotificationService.kt` serve both surfaces), needs source parsing, and reads like a code
audit rather than a size report. A hand-maintained file list is excluded by the spec itself.

*Risk accepted:* a car file not named for the car surface is counted as phone; the report mitigates this by
printing the module-root and unclassified figures rather than hiding them, and the convention is stated in
`guidelines/Metrics.md` so a rename is the fix.

### D6 — Fixed sections, complete lists, no options

Sections print in a fixed order (header, language, module, package, layer index, distribution, ranked packages,
ranked files, source-set split, ratios, platform share, OpenSpec counts, excluded classes), each list complete.
Beside scan scope paths, the only interface is `--help`, which prints the usage block from the script's own
header.

*Alternatives.* (a) `--top N` / `--only <section>` — rejected by the owner: the report must always be complete.
(b) JSON alongside the text — rejected by the owner: manual inspection only. (c) Truncate long lists to a screen
— rejected: an unread part of the project would read as a small project.

### D7 — Determinism is explicit

Ordering is by figure, ties by name, with `LC_ALL=C` so collation cannot reorder lists between environments;
the only variable part of the output is the header's date.

*Alternatives.* (a) Rely on the input order — rejected: `awk`'s key iteration order is unspecified, which is why
the predecessor can print the same ties in different orders. (b) Leave collation to the environment — rejected:
a report compared between a German dev machine and CI would reorder.

### D8 — OpenSpec counts come from the CLI's JSON

Open changes, archived changes and feature directories are read from `openspec list --json` and
`openspec list --specs --json`. An unavailable CLI prints the section as unavailable; the run still exits 0.

*Alternatives.* (a) Directory globs — rejected: this is the predecessor's proven defect (it printed 147 feature
directories against the CLI's 155, because seven capabilities live at a nested path and the `auto` namespace
directory owns no `spec.md`), and it violates `Build.md` §4. (b) Parse the store's files — rejected: same rule,
plus a second parser to maintain.

### D9 — Counting definitions are simple, stated and additive

A line is code, comment or blank; full-line `//`, `#`, `/* … */` and `<!-- … -->` are comments, trailing
comments count as code. `.kts` counts as Kotlin. Markdown counts as documentation. Source sets come from the
source-root directory (`main`, `test`, `androidTest`) and documentation is its own kind. Ratios are comment
share, test share, documentation share and average lines per file, each printed with its operands.

*Alternatives.* (a) A per-language tokenizer — rejected: the report exists for comparison between trees, not for
a code audit; the cost is real and the benefit is not. (b) Separate rows for `.kts` — rejected: it splits one
language across two rows for no decision it would change. The normative statement of these definitions is
`guidelines/Metrics.md`; this document records only why they are shaped this way.

### D10 — Verification is a fixture tree, not the live repository

`tools/project-metrics-report-selftest.sh` builds a temporary tree covering the layout variants (nested package,
file with no package path, foreign package, `src/main/kotlin`), the exclusion classes (a `build/` tree holding a
countable file, a fake vendored tree), the comment styles, the refusal paths, and the additivity invariants, then
asserts figures, statements and exit statuses.

*Alternatives.* (a) Assert against the live repository — rejected: figures drift within a session (the Kotlin
count moved 89 681 → 89 721 while this change was being explored), so cases would fail for unrelated reasons.
(b) Golden output files — rejected: brittle against formatting, and they hide which rule broke. (c) Only
invariant checks (additivity) — rejected: they hold even when everything is misclassified, so layout and refusal
outcomes need explicit cases.

## Risks / Trade-offs

- **A car file unnamed for the car surface is counted as phone** → the convention is documented, the module-root
  and unclassified figures stay visible in the report, and a rename is the correction.
- **Heuristic comment counting misclassifies an unusual file** → the definition is documented and printable, the
  line-kind figures are additive so totals stay exact, and the self-test covers `//`, `#`, block and HTML
  comments including a multi-line block.
- **A new generated directory name outside the class list would be walked** → the class list is documented,
  reviewable and printed with each report; the header's file count makes an unexpected mass visible; the fixture
  includes an unrecognized generated directory.
- **No machine-readable output, therefore no trend tracking** → accepted by the owner; it is a requirement change,
  not a design unknown, so it would return as its own change.
- **The vendored submodule contributes nothing, so a vendored regression is invisible here** → deliberate: the
  header names the class and its root path, and the number exists in the submodule's own tooling.
- **A long report (~2 300 file rows)** → sections are grouped and figure-bearing columns are stable so output can
  be piped to `less` or matched with `grep`.
- **The classification entry is a two-sided gate, and the change sits in the middle of it** → the entry must
  land at archive time, not before: an early entry is rejected as stale. Sequence step 4 accordingly, and treat
  `gen-feature-list.sh --check-classification` as the check for both directions.
- **`guidelines/Metrics.md` without an `AGENTS.md` row fails CI** → they are implemented as one unit, verified by
  running `tools/check-doc-routes.sh`.

## Migration Plan

1. Write `tools/project-metrics-report.sh` and run it beside the scratch predecessor, reconciling language,
   module and layer figures and documenting the one expected difference (the OpenSpec row: 147 → 155).
2. Write `tools/project-metrics-report-selftest.sh`; its cases become the change's regression surface.
3. Add `guidelines/Metrics.md` and the `AGENTS.md` documentation-map row together, then prove the route with
   `bash tools/check-doc-routes.sh`.
4. Add the classification entry for `project-metrics-report` to `tools/feature-list/specs.json` (mirroring
   `build-test-gate` and `kover-aggregate-report`) **when the capability ships with the archive**, then prove it
   with `bash tools/gen-feature-list.sh --check-classification`. Adding the entry earlier fails the same gate in
   the other direction — measured 2026-10-08: an entry for a spec that does not yet exist is reported as
   `stale classification entry for a spec that no longer exists` and exits 1 (`specs read: 155`, `classified:
   154`).
5. Delete `tools/count-loc.sh` once step 1 reconciles.

**Rollback.** Delete the two new tools and the guideline, revert the one-line `AGENTS.md` row and the
classification entry. Nothing at runtime depends on any of it.

## Open Questions

- How far the documentation kind should be subdivided later (guidelines versus OpenSpec store versus release
  notes). Deferrable: the report already separates documentation from code, which is what any share claim needs.
- Whether the unrecognized-layout bucket deserves its own reported total per source set or only the path list it
  has now. Deferrable: the bucket is visible either way, and the additivity requirement holds.
- Whether `guidelines/FeatureList.md:8`'s three named behaviour contracts (`spec-feature-index`,
  `feature-list-generation`, `release-notes-generation`, none of which exist on disk) are repaired here or in
  their own change. Deferrable and independent: this change does not rely on them.
