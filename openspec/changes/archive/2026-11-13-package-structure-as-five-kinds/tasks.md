# Tasks

## 1. The architecture module and its inputs

- [ ] 1.1 Add `include(":architecture")` to `settings.gradle.kts` and a `kotlin("jvm")` module at `architecture/` whose only dependencies are `com.lemonappdev:konsist` and `junit:junit`, with no Android plugin and no dependency on `:app`, `:auto` or `:core`. Parent spec: `architecture-test-suite` — The structure rules run as a unit test. Verify: `./gradlew :architecture:dependencies --configuration testRuntimeClasspath` lists konsist and junit and no Android artifact, and `:architecture:compileTestKotlin` succeeds.

- [ ] 1.2 Write `architecture/structure.tsv` as a 1:1 declaration over today's packages (module, package, kind, feature, may_import), classifying each of the 27 current packages into one of the five kinds as they stand — no file moves. Parent spec: `package-structure` — A source file belongs to exactly one kind. Verify: the row count equals the package count reported by `find app/src/main/java auto/src/main/java core/src/main/java -type d` minus the three module roots, and the file records the date of its measurement.

- [ ] 1.3 Implement the declaration reader and kind resolution in `:architecture`, including the case that a row names a package the tree no longer has. Parent spec: `package-structure` — The structure declaration is the single machine-readable description. Case `structureCheckFailsOnRowWithoutPackage` fails first on a row for `com.naviveylin.gone` and passes once the row is removed.

- [ ] 1.4 Add the default-deny, the duplicate-row case and the unclassified-file case. Parent spec: `package-structure` — An undeclared package fails by default. Cases: `defaultDenyFailsForANewPackage` (a temporary package with one file fails, passes after a row claims it), `structureCheckFailsOnDuplicateRow`, `structureCheckFailsOnUndeclaredPackage`.

- [ ] 1.5 Add the layer rules for `concept` and `port`. Parent spec: `package-structure` — concept is self-contained, and — port holds abstraction only. Cases: `conceptImportsNothingOfOurs`, `conceptImportsNoAndroidType`, `portImportsNoAndroidType`, `portImportsOnlyConcept`. Note: `core.ManeuverSymbols` (imports `android.graphics.Bitmap/Canvas/Paint/Path`) and `core.CarSurfaceHost` are declared `platform`, not `concept` or `port`, because of this rule.

- [ ] 1.6 Add the feature rules: disjunction, Android-free, and the upward-import refusal. Parent spec: `package-structure` — features are mutually disjunct, and — a feature is Android-free. Cases: `featureDisjunctionViolationNamesTheImport`, `featureImportingShellFails`, `featurePurityViolationNamesTheImport`, `featureImportingPortPasses`. The two measured `:app` disjunctions (`NavigationEngine` → `ui.route.routeLengthMeters`, `NavigationViewModel` → `ui.route.RoutePanelViewModel`) enter the baseline in 1.7, not the rule.

- [ ] 1.7 Add the `platform` mirror rule, the `shell` hosting rule and the baseline file with its two refusals. Parent spec: `package-structure` — a platform package mirrors a feature, and — shell is the only kind that hosts several features; `architecture-test-suite` — The baseline is shrink-only. Cases: `platformImportingSiblingPlatformFails`, `platformWithoutFeatureFails`, `shellMayHostSeveralFeatures`, `featureHostingTwoFeaturesFails`, `staleExemptionFails`, `exemptionWithoutChangeFails`. Baseline entries carry the file, the rule and the owning change; the 24 `:app`-only symbols leaving `:core` are not exemptions because no rule forbids them yet.

- [ ] 1.8 Add the green-run figures and the empty-declaration refusal. Parent spec: `architecture-test-suite` — A green run reports what it verified. Cases: `greenRunPrintsFigures` (declaration rows, classified files, outstanding exemptions, rules evaluated), `emptyDeclarationFails`.

- [ ] 1.9 Make the structure check a prerequisite of every module's unit-test task (`:app`, `:auto`, `:core`), so a focused run cannot pass on a violating tree. Parent spec: `architecture-test-suite` — Running a module's tests runs the structure check. Cases: `focusedTestRunOnViolatingTreeFails` (one `:app` test class by `--tests`, with an injected violation, fails at `:architecture:test` and the class reports no pass), `satisfyingTreeRunsTestsNormally` (`:core`). Verify: `./gradlew :app:testMobileDebugUnitTest --tests "*FavoritesViewModelTest*"` with the violation in place fails, and without it the class's own result stands.

- [ ] 1.10 Refuse in-code suppression and make the baseline the only exemption. Parent spec: `architecture-test-suite` — The baseline is the only exemption. Cases: `suppressionAnnotationDoesNotExempt` (a `@Suppress` naming the check, violation still reported), `baselinedFileIsCountedNotReported`.

- [ ] 1.11 Make the check fail closed on a missing, unparsable or empty declaration, and on an unknown kind. Parent spec: `architecture-test-suite` — The check fails closed. Cases: `missingDeclarationFails`, `unknownKindFails`.

## 2. The completeness of the report

- [ ] 2.1 Make every reported violation name the file, the import, the file's declared kind and the contradicted rule or row. Parent spec: `architecture-test-suite` — A violation names the file, the import and the rule. Case: `violationNamesFileImportAndRule` asserts all four fragments in the message of one disjunction violation.

- [ ] 2.2 Cover the test source sets and the application module. Parent spec: `architecture-test-suite` — The check reads every source set of the three modules. Cases: `violationInTestSourceIsReported` (a temporary violating file under `app/src/test`), `violationInAppModuleIsReported`, `suiteRunsWithoutDeviceOrAppBuild` (a run with no device and no `:app` build).

## 3. Falsification — one mutation per rule

- [ ] 3.1 Revert-check the concept rule: add `import com.naviveylin.core.DiagnosticsLog` to a `concept` file, confirm `conceptImportsNothingOfOurs` fails naming file, import and rule, restore, re-run green with `-PforceTests --no-build-cache`. Parent spec: `package-structure` — concept is self-contained.

- [ ] 3.2 Revert-check the purity rule: add `import android.content.Context` to a `feature` file, confirm `featurePurityViolationNamesTheImport` fails, restore, forced green. Parent spec: `package-structure` — a feature is Android-free.

- [ ] 3.3 Revert-check the disjunction rule: add a `feature/route` import to a `feature/navigation` file, confirm `featureDisjunctionViolationNamesTheImport` fails, restore, forced green. Parent spec: `package-structure` — features are mutually disjunct.

- [ ] 3.4 Revert-check the default-deny: delete one declaration row, confirm the package's files are reported, restore, forced green. Parent spec: `package-structure` — An undeclared package fails by default.

- [ ] 3.5 Revert-check the stale exemption: move one baselined file into its target package, confirm `staleExemptionFails` fails naming the entry, restore, forced green. Parent spec: `architecture-test-suite` — The baseline is shrink-only.

- [ ] 3.6 Revert-check the test-task hook — this is the one that proves "no code passes the tests". With one disjunction violation in the tree, run a single named `:app` test class; the invocation must fail at `:architecture:test` and the class must report no pass. Restore, then confirm the same command is green. Parent spec: `architecture-test-suite` — Running a module's tests runs the structure check.

- [ ] 3.7 Revert-check the one-escape-hatch rule: add a suppression annotation naming the check to a violating file and confirm the violation is still reported; then delete `architecture/structure.tsv` and confirm the check fails closed instead of reporting no violations. Restore both, forced green. Parent spec: `architecture-test-suite` — The baseline is the only exemption, and — The check fails closed.

## 4. The guideline that owns the structure

- [ ] 4.1 Create `guidelines/Structure.md`: `## 1` the five kinds and what each holds, `## 2` the rule set, `## 3` adding a package (the declaration is edited, not a rule), `## 4` the ratchet baseline and how it shrinks, `## 5` migrating one feature, `## 6` what the suite cannot prove. Every rule is written as `**Rule: <name>** <statement>.` with a name unique across `guidelines/*.md`, and each sits beside the measurement that proved it. Parent spec: `package-structure` — The structure declaration is the single machine-readable description. Verify: `tools/look.sh '^## ' --path guidelines/Structure.md` lists the six sections and `tools/check-doc-routes.sh` resolves each.

- [ ] 4.2 Create the rationale companion `guidelines/Structure.why.md` carrying the measurements (the seven figures from `proposal.md`), the ArchUnit elimination, the `map` ↔ `route` cycle and the `core.core` 491-of-839 figure, with `## §N <heading>` anchors into `Structure.md`. Parent spec: `package-structure` — The structure declaration is the single machine-readable description. Verify: `tools/check-doc-routes.sh` confirms every anchor names a section number the base document carries.

- [ ] 4.3 Add the `AGENTS.md` Documentation Map row that routes the structure topic to `guidelines/Structure.md`, and add the `guidelines/Structure*` route to `tools/doc-routes.json` carrying `Structure.md`'s sections and its named rules. Parent spec: `package-structure` — The structure declaration is the single machine-readable description. Verify: `tools/check-doc-routes.sh` reports no unrouted document and `tools/check-rule-routes.sh` reports no rule under `guidelines/` reached by no glob.

- [ ] 4.4 Add the code-path routes in `tools/doc-routes.json`: `Structure.md`'s rule-owner sections and its named rules on the existing `app/src/main/java/**`, `auto/**` and `core/**` routes, and an `architecture/**` route added only after the module and the declaration are tracked, so its glob matches a tracked path. Parent spec: `package-structure` — An undeclared package fails by default. Verify: `tools/check-rule-routes.sh` passes all four invariants — no glob matching no tracked path, every named rule reached, every normative document matched, no guidelines route carrying a document it does not reach.

## 5. The gate

- [ ] 5.1 Add `:architecture:test` to the gate's suite list (`guidelines/BuildEvidence.md` §4b) and to the verification workflow so it runs on every change. Parent spec: `architecture-test-suite` — The check is part of a gate run. Cases: `gateSuiteListIncludesArchitecture`, `gateFailsAtStructureCheck`, `violationFailsTheTestTask`.

- [ ] 5.2 Make the structure check a required status check on the main branch, so the `-x`/`--exclude-task` escape cannot merge. Parent spec: `architecture-test-suite` — The check is part of a gate run. Verify: a deliberately violating branch cannot be merged — the required check is red and names the violation; record the setting's name and the observed red run in the change's evidence.

## 6. The documents this change supersedes

- [ ] 6.1 Rewrite `guidelines/Design.md` §1 so it routes to `architecture/structure.tsv` and `Structure.md` and stops describing `:core` as "shared, Android-lean logic" — the wording 11 files contradicted. Parent spec: `package-structure` — The structure declaration is the single machine-readable description. Verify: `tools/check-doc-routes.sh` and `tools/check-rule-routes.sh` pass, and `docNamesDeclarationAndListsNoPackages` holds — §1 enumerates no packages.

- [ ] 6.2 Update the `AGENTS.md` module-structure block so the five kinds replace the module list's role descriptions. Parent spec: `package-structure` — The structure declaration is the single machine-readable description. Verify: `tools/check-doc-routes.sh` passes and the block names `Structure.md` rather than the package set.

- [ ] 6.3 Update `guidelines/Metrics.md` (the report's layer rows and the declaration) and `guidelines/UI.md` §8d (the parity-package symmetry `platform/phone` and `platform/car` make checkable). Parent spec: `package-structure` — a platform package mirrors a feature. Verify: both sections name the declaration and `tools/check-doc-routes.sh` passes.

## 7. The ports the pilot needs

- [x] 7.1 Add `port/FavouritesStore` and `port/Resources`, both Android-free and importing `concept` only, with `platform/favourites` implementing `FavouritesStore` by delegating to today's `FavoriteRepository` behind the existing write lock. Parent spec: `package-structure` — port holds abstraction only, and — a feature is Android-free. Verify: probe `tools/look.sh '^import ' --path core/src/main/java/com/naviveylin/core/port` = 3 imports / 0 android / 0 of ours (`items/873/port-imports.log`); `portImportsNoAndroidType`/`portImportsOnlyConcept` land with task 1.1; the favourite write-serialisation rule in `AGENTS.md` is preserved, and `FavoriteRepository`'s tests stay green.

## 8. Pilot — the favourites feature, end to end

- [ ] 8.1 Move `FavoriteOrder`, `GroupOrder`, `StarredOrder` and `core.GroupOrdering` to `feature/favourites`; move `FavoritesSheet` and `FavoritesViewModel` to `platform/favourites`; update every import. Parent spec: `package-structure` — A source file belongs to exactly one kind. Verify: `:app:compileMobileDebugKotlin` and `:app:compileAutomotiveDebugKotlin` succeed, and the feature holds no `android.*` import (`featurePurityViolationNamesTheImport` passes with no exemption).

- [ ] 8.2 Delete the now-satisfied baseline entries for the moved favourites files. Parent spec: `architecture-test-suite` — The baseline is shrink-only. Verify: the green run's outstanding-exemption figure drops by the number of entries removed, and `staleExemptionFails` passes.

- [ ] 8.3 Run the suites the move can affect and record the tallies. Parent spec: `architecture-test-suite` — The check is part of a gate run. Verify: `:app`, `:core` and `:auto` unit tests plus `:architecture:test` pass with `-PforceTests --no-build-cache`; name any exclusion.

## 9. The closing gate

- [ ] 9.1 Run the full gate over both flavors with `:architecture:test` in its suite list. Parent spec: `architecture-test-suite` — The check is part of a gate run. Verify: a green both-flavor run whose record names `:architecture` among the suites it covered.

- [ ] 9.2 Record the run's numbers in the change: declaration rows, classified files, outstanding exemptions, rules evaluated, and the baseline's drop over the pilot. Parent spec: `architecture-test-suite` — A green run reports what it verified. Verify: the figures appear in the change's evidence with the date of the run.
