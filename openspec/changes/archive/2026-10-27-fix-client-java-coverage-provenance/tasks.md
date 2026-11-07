# Tasks — the Java module's coverage is two provenances

## 1. The red case on the base tree

- [x] 1.1 Verify the item against the tree: the module built one `main` source set out of `src/main/java` plus
  `fileTree(clientJavaDir)` (`osmscout-client-java/build.gradle.kts`), published one root counter
  (`tools/build-inputs.sh`, `write_coverage_summary`) and the row applied its floor to that one key
  (`tools/sensors/metrics/coverage.sh`). Re-measured on the base commit: mixture `3124/4210` (74.2 %), own
  `1782/2854` (62.4 %), vendored `1342/1356` (99.0 %) — `evidence/jacoco-head.xml`.
- [x] 1.2 Write the red case: `inputs#splitsTheJavaModulesCoverageByProvenance` in `tools/build-inputs-cases.sh`
  (with `module_reports` growing the vendored report fixture), and rewrite `inputs#convertsPerModuleCoverage` for
  five keys. Verification: both fail on the base tree —
  `the vendored key reads the vendored report, so a pin bump moves it alone: expected '95.0', got 'null'` and
  `expected '{"app":90.0,…, "osmscout-client-java-vendored":95.0}', got '{…, "osmscout-client-java":60.0}'`
  (`evidence/red-on-head.log`, `evidence/red-on-head-converts.log`).
- [x] 1.3 Count the surface before the plan was fixed: 1 shell case added, 1 rewritten, 1 fixture helper grown,
  2 test classes and 1 fake in the module's suite, 1 module build script, 1 producer, 1 CI step, 3 guideline
  sections, 1 test KDoc, 2 stub files — the commands are in the proposal's `surface:` line.

## 2. The split

- [x] 2.1 `osmscout-client-java/build.gradle.kts`: a `vendored` source set from `clientJavaDir` with the same five
  exclusions, `compileOnly`/`testImplementation` on its output, `tasks.jar { from(...) }`, and
  `jacocoTestReport` narrowed to `main`. Verification: `./gradlew --offline :osmscout-client-java:jar` still
  produces 34 classes in the one jar, and the own report's counter equals `1782/2854` with the vendored report
  `1342/1356` — the same two numbers the base report decomposed into.
- [x] 2.2 Register `jacocoVendoredTestReport` on the same `build/jacoco/test.exec`, writing
  `build/reports/jacoco/vendored/jacocoVendoredTestReport.xml`. Verification: the file exists and its root
  counter is `covered=1342 missed=14`.
- [x] 2.3 `tools/build-inputs.sh`: `jacoco_vendored_xml`, the second key in `write_coverage_summary`, the check in
  `coverage_inputs_present`, and a `--write` refusal that names the missing report. Verification: the two red
  cases pass.
- [x] 2.4 `.github/workflows/build.yml`: the report step runs both JaCoCo tasks and the artifact path covers
  `osmscout-client-java/build/reports/jacoco/**`. Verification: `tools/check-action-runtimes.sh` and the gate's
  pipeline-suite run stay green; the workflow's step text names both tasks.

## 3. The own key clears the floor

- [x] 3.1 The host stub: `osmscout-client-java/src/test/jniLibs/libosmscout_client_java.so` and
  `libosmscout_client_javad.so` (copied from `app/src/test/jniLibs`, md5 `90fa0ff2…`), with
  `!osmscout-client-java/src/test/jniLibs/*.so` in `.gitignore` and `systemProperty("java.library.path", …)` on
  the module's test task. Verification: `git check-ignore -v` names the re-include rule, and a case that
  instantiates `OSMScoutClient` runs instead of failing the library load.
- [x] 3.2 `FakeOSMScoutClient` (Java, module test sources) answers `getStyleSheetDirectory`, `beginSearch`,
  `cancelSearch`, the two search entry points, `searchPOIsByTypes`, `calculateRouteWithObjectsWithProfile`,
  `startNavigationWithVehicle` and `renderWithRouteAndPois`, and records the delegation.
- [x] 3.3 `OSMScoutClientWrappersTest` (7 cases) and `OSMScoutClientBuilderTest` (2 cases). Verification: the
  module's suite reads 23 classes / 116 cases / 0 failures, and the own report's INSTRUCTION counter is
  `2099/2854` = 73.5 %, above the 70 % floor (`evidence/jacoco-own.xml`, `evidence/module-tally.txt`).

## 4. Documentation

- [x] 4.1 `guidelines/Build.md` "Code coverage": the engine table gains the two rows, the paragraph naming the two provenance
  keys and why the split exists, the usage commands, and the baseline paragraph's JNI-module sentence re-measured
  per provenance (`evidence/jacoco-own.xml`, `evidence/jacoco-vendored.xml`).
- [x] 4.2 `guidelines/Build.md` §6h names the stub's third home and the module's own `java.library.path` wiring;
  §6a states why the classloader rule does not reach a plain-JUnit module.
- [x] 4.3 `JniStubSandboxRuleTest`'s KDoc stops claiming "a module is scanned when it ships the stub" and names
  the two modules the rule reaches.

## 5. Verification and archive

- [x] 5.1 Structural check of the docs and artifacts: `bash tools/check-doc-routes.sh`, `bash
  tools/check-todo-references.sh`, `bash tools/check-openspec-hygiene.sh` and
  `openspec validate fix-client-java-coverage-provenance --strict`.
- [x] 5.2 The gate: `tools/loop/gate.sh` (compiled gate `./gradlew test -PforceTests -PjvmTestsOnly
  --no-build-cache` plus the shell suites the changed paths owe).
- [x] 5.3 Evidence: `jacoco-head.xml`, `jacoco-own.xml`, `jacoco-vendored.xml`, `module-tally.txt`,
  `red-on-head.log`, `red-on-head-converts.log`, `green-on-change.log`, `gate.log` in this change's `evidence/`,
  and `bash tools/loop/evidence-check.sh openspec/changes/fix-client-java-coverage-provenance --root .` PASS.
