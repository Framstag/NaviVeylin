# Evidence — the traceability baseline, and why it is not the issue's figure

Taken 2026-11-21 on the tree at commit `2db7087e` (plus this change's own edits). Every figure below is one
command's output, quoted as it printed.

## 1. The verdict line

```
$ bash tools/check-case-names.sh
CASE-NAMES: ok harnesses=41 lines=1524 named=1316 none=129 unnamed=79 unclaimed=5985
$ echo $?
0
```

Measured wall time 15.5 s, no network, no Gradle. The rows: `jq '.harnesses | length' tools/case-harnesses.json` → `41`.

`named=1316` counts the distinct `group#case` and `module: Class.method` names a `**Case**:` line carries;
`lines=1524` counts the lines themselves (a line may carry two names, and a name may appear in two specs);
`none=129` are the `**Case**: none — <reason>` lines, which the readiness checker owns; `unnamed=79` are the
scenarios with no `**Case**:` line at all.

## 2. The two halves of `unclaimed=5985`

The count is every name a declared harness runs that no shipped scenario names.

```
$ bash tools/check-case-names.sh --json | jq -r '.unclaimed[]' | grep -cE '^(app|auto|core|buildSrc|osmscout-client-java)(\(device\))?: '
5330
```

So 5330 of 5985 are test methods and test classes of the Gradle and Catch2 trees — a suite's own methods, which a
requirement rarely names one by one. The remaining 655 are the bash suites' cases (the largest group is the loop's
`run#*`, 29 unclaimed).

## 3. Why this differs from the issue's 1252 / 597 / 572 / 69

The issue measured one harness family — the loop suite's cases against the specs that name them — and a draft
registry of four rows. The tree cannot be resolved that way, which the 2026-11-17 comment on the item proves:

- four rows leave 176 of 779 `group#case` names unresolved, because the specs cite the issue tooling's Java cases,
  the glossary, portability, sonar, the readiness checker's own suite and a dozen other suites;
- about 100 names are Gradle forms (`Class#method`, `module: Class.method`) that no four-row grammar covers.

So every suite a shipped spec cites carries a row, and the check's counts are over the whole shipped spec set, not
over one suite's slice. `unresolved=0` and `duplicates=0` are the two figures that must be zero, and they are.

## 4. What the first green run found

Eight specs named cases that no harness ran under that name. Each was a stale or invented name, and each was
corrected in the spec to the case that actually runs (or to `**Case**: none — <reason>`):

| spec | named | corrected to |
|---|---|---|
| `osmscout-jni` | `app: RouteStepModelBridgeTest` ×5, `osmscout-client-java: …` | `app(device): RouteStepModelBridgeTest…` (the test is an instrumented one) |
| `architecture-test-suite` | `ArchitectureSuiteTest#suiteRunsWithoutDeviceOrAppBuild`, `StructureScopeTest#violationInAppModuleIsReported`, `ViolationReportTest#violationNamesFileImportAndRule` | `StructureScopeTest#suiteRunsWithoutDeviceOrAppBuild`, `StructureScopeTest#violationInTheAppModuleIsReported`, `FeatureRuleTest#violationNamesFileImportAndRule` |
| `turn-instruction-localization` | `TurnInstructionLocalizerTest`, `ManoeuvreLabelTest` | `core: ManeuverLabelTest` "…" — the test class is `ManeuverLabelTest` |
| `i18n-l10n` | `GermanTranslationCompletenessTest` (no module) | `app: GermanTranslationCompletenessTest.everyEnglishStringHasGermanTranslation` |
| `route-calculation-feedback` | the same class, in the `auto` tree | `auto: GermanTranslationCompletenessTest.retiredWaitNoticeStringsAreGone` |
| `map-render` | `MapCanvasViewModelTest` "a frame lands…", `MapCanvasViewModelZoomTest` | `app: MapCanvasViewModelFrameHarnessTest` "a frame lands through the frame collector", `app: MapCanvasViewModelZoomWalkTest` "a large change walks and lands exactly on the target" |
| `test-coverage` | `coverage-floor-probe#…` | `**Case**: none — the floor is arithmetic over the module's own Kover report…` |

One latent duplicate was renamed rather than declared: the sensors suite's `run#dryRunTouchesNothing` became
`run#aDryRunWritesNothingToTheTracker`, because two suites holding one identical full name is the one thing the
registry refuses.

## 5. The falsifications

```
$ bash tools/tests/check-case-names-selftest.sh
check-case-names-selftest: all 12 cases passed (throwaway trees under the loop's temporary root; no git, no build, no network)
  … 1.4 s wall

$ bash tools/mutate.sh --mutations tools/tests/check-case-names-mutations.json
… 4 red, 4 restored green, 0 problems  (3.8 s)
```

The four records mutate the four rules separately — the resolution refusal, the duplicate refusal, the absent-list
refusal and the no-refusal-for-an-unclaimed-case rule — and each names the case that must fail.

## 6. Two defects this work found in other tools' declarations

Both were red on the tree before this change touched anything, and both were repaired because a red structural
check cannot be left beside a green one:

- `tools/check-rule-routes.sh` reported three rules reached by no glob — one in `Backlog.md` §3 (left by
  `642533d5`, #938), one in `BuildEvidence.md` §4f and one in `Logging.md` §2 (both left by `f55c8d6b`, #894). The
  three quoted rules were added to their documents' own routes.
- `tools/check-openspec-hygiene.sh` reports 26 open changes with no `x-issue:` field. This change carries
  `x-issue: 896`; the other 26 belong to the changes that are open beside it and are filed as `#999`, the cause
  being the refusal at `tools/check-openspec-hygiene.sh:122`.