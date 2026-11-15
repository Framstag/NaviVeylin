# Tasks

This archive is the first slice of the change `package-structure-rules-and-suite`, the change that carries
what `package-structure-as-five-kinds` did not land on 2026-11-14 (see that archive for the plan, the rationale
and the design). The slice landed on 2026-11-15, carried by the tracker item #869: the three `feature` rules of
task 1.6, the completeness of a violation's message (2.1), and the falsifications 3.2 and 3.3. The rest of the
plan — the `platform` mirror and `shell` hosting rules of 1.7, the remaining rules' falsifications, the suite's
place in the build and the gate, `guidelines/Structure.md` and its routes, and the pilot migration — is still
open under that change. So the two delta files here carry only what landed: the two feature requirements, and
the one requirement about a violation's message. The requirements the open tasks will ship are not in this
archive, and `openspec/specs/package-structure/spec.md` gained the two feature requirements with this slice.

Two figures this slice measured, because the item's and the answering comment's own numbers are short:
the purity rule fires on **11** files, not 15 (the 4 files of `ui/navigation` sit in a `platform` row), and
the disjunction rule fires on **4** files, not 2 (`ui.route` reaches into `core.search`, and
`RoutePanelViewModel` into `navigation`). The upward-import refusal, which no earlier measurement counted,
fires on **8** files. The baseline therefore grew by **23** entries, 14 → 37, not by the 13 the item's answer
predicted. The derivation and the commands are `evidence/measurement-baseline-entries.md`.

## 1. The architecture module and its inputs

- [x] 1.6 Add the feature rules: disjunction, Android-free, and the upward-import refusal. Parent spec: `package-structure` — features are mutually disjunct, and — a feature is Android-free. Cases: `featureDisjunctionViolationNamesTheImport`, `featureImportingShellFails`, `featurePurityViolationNamesTheImport`, `featureImportingPortPasses`, `nestedRowOwnsTheImportItPrefixes`. The two measured `:app` disjunctions (`NavigationEngine` → `ui.route.routeLengthMeters`, `NavigationViewModel` → `ui.route.RoutePanelViewModel`) enter the baseline in 1.7, not the rule. Evidence: `evidence/red-on-HEAD-FeatureRuleTest.xml` — `tests="6" skipped="0" failures="5" errors="0" timestamp="2026-11-15T07:43:37.242Z"`, the six cases against the rules stubbed, so the five that assert a refusal fail and only the port case passes (a stub that refuses nothing satisfies it); `evidence/green-FeatureRuleTest.xml` — `tests="6" skipped="0" failures="0" errors="0" timestamp="2026-11-15T07:45:21.087Z"`; `evidence/revert-check-resolution-red-FeatureRuleTest.xml` — the row resolution mutated from the longest declared prefix to the shortest (`maxByOrNull` → `minByOrNull`), which no other case catches and `nestedRowOwnsTheImportItPrefixes` does: `tests="6" skipped="0" failures="1" errors="0"`, the failure named `the longer row prefixing the import owns it, so the disjunction rule applies`. The mutation matters in the real tree, not only in the case: `com.naviveylin.core` is a `concept` row and `com.naviveylin.core.search` a `feature` row, so the shorter row would make every `core.search` type read as a concept import. And the 23 baseline entries of `architecture/baseline.tsv`, derived in `evidence/measurement-baseline-entries.md`.

## 2. The completeness of the report

- [x] 2.1 Make every reported violation name the file, the import, the file's declared kind and the contradicted rule or row. Parent spec: `architecture-test-suite` — A violation names the file, the import and the rule. Case: `violationNamesFileImportAndRule` asserts all four fragments in the message of one disjunction violation. Evidence: `evidence/green-FeatureRuleTest.xml` — the case passes (`tests="6" skipped="0" failures="0"`); the message it asserts on is `app/src/main/java/com/naviveylin/feature/navigation/Navigation.kt is declared 'feature' and imports com.naviveylin.feature.route.RouteLength, which 'feature imports another feature' forbids`.

## 3. Falsification — one mutation per rule

The two mutations below break the **project's** tree, so the case that goes red is the one that checks the
project's tree — `StructureReportTest#greenRunPrintsFigures`, which asserts the tree passes — and not the five
`FeatureRuleTest` cases that assert a refusal, which hand the check a synthetic tree of their own and cannot
see this one.

- [x] 3.2 Revert-check the purity rule: add `import android.content.Context` to a `feature` file, confirm the check fails naming that file, that import and the purity rule, restore, forced green. Parent spec: `package-structure` — a feature is Android-free. Evidence: `evidence/revert-check-3.2-red.xml` — `tests="2" skipped="0" failures="1" errors="0"`, the single failure being `greenRunPrintsFigures` with the assertion `core/src/main/java/com/naviveylin/core/search/PoiNameFilter.kt is declared 'feature' and imports android.content.Context, which 'feature imports no Android type' forbids`; the restored run is `evidence/green-StructureReportTest.xml` — `tests="2" skipped="0" failures="0" errors="0"`.
- [x] 3.3 Revert-check the disjunction rule: add a `feature/route` import to a `feature` file, confirm the check fails naming that file, that import and the disjunction rule, restore, forced green. Parent spec: `package-structure` — features are mutually disjunct. Evidence: `evidence/revert-check-3.3-red.xml` — `tests="2" skipped="0" failures="1" errors="0"`, the failure being `greenRunPrintsFigures` with the assertion `core/src/main/java/com/naviveylin/core/search/PoiNameFilter.kt is declared 'feature' and imports com.naviveylin.ui.route.routeLengthMeters, which 'feature imports another feature' forbids`; the same restored run as 3.2 (`evidence/green-StructureReportTest.xml`) is green and `git checkout` restored the file byte for byte.

## 4. The requirement the corpus gained

- [x] 4.1 Merge the landed requirements into `openspec/specs/package-structure/spec.md`, so the rules cite a requirement a shipped spec states: the corpus gains "features are mutually disjunct" and "a feature is Android-free", with the four `FeatureRuleTest` cases they name. Evidence: `git diff openspec/specs/package-structure/spec.md` — six requirements where the corpus held three, and every scenario's `**Case**` resolves to a case this archive's green XMLs ran. The `architecture-test-suite` requirement ("A violation names the file, the import and the rule") is not merged: #867 deleted that capability from the corpus, so it lives in this archive's delta only, beside the sibling archive's requirements.