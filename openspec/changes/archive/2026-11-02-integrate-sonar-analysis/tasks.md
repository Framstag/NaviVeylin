# Tasks

All tasks reference the capability delta `specs/sonar-analysis/spec.md` unless stated otherwise. Nothing in
this change touches a compiled file, so no build and no test suite is owed (`guidelines/Build.md` §2, the
scope block); the structural checks named here stand in, and the first scan on `main` is the acceptance
measurement.

## 1. Preconditions (owner, outside the repository)

- [ ] 1.1 Turn **Automatic Analysis off** for the SonarQube Cloud project `Framstag_NaviVeylin`
      (Project → Administration → Analysis Method). Evidence: a screenshot or the settings page state, recorded
      in the change. Without it the first scan fails with the server's refusal
      (spec `sonar-analysis`: "The analysis is refused while the project has automatic analysis enabled").
- [ ] 1.2 Add a repository secret `SONAR_TOKEN` scoped to analysis for that one project. Evidence: the secret
      listed by name in Settings → Secrets (`gh secret list`), never its value.

## 2. The scan workflow

- [x] 2.1 Write `.github/workflows/sonar.yml`: `name`, `on: push: branches: [main]` + `workflow_dispatch`,
      job-level `if: github.ref == 'refs/heads/main'` (the `sensors.yml` shape), `permissions: contents: read`,
      and no `pull_request` or `workflow_run` trigger
      (spec `sonar-analysis`: "The scan is produced by CI from the main branch"). Verify:
      `grep -nE 'pull_request|workflow_run' .github/workflows/sonar.yml` finds nothing;
      `grep -n 'github.ref ==' .github/workflows/sonar.yml` names `refs/heads/main`.
- [x] 2.2 Add the preamble the scanner needs: checkout with `submodules: recursive` and `fetch-depth: 0`,
      JDK 21 (temurin), `local.properties` with `sdk.dir`/`org.gradle.java.home`, the Android SDK cache +
      install copied from `build.yml` steps "Cache Android SDK"/"Install Android SDK", and the Gradle cache
      step — no NDK, no vcpkg, no host build dependencies. Verify: the workflow's step list contains no
      `vcpkg` and no `ANDROID_NDK_HOME` reference.
- [x] 2.3 Add the measurement steps:
      `./gradlew -Dorg.gradle.java.home="$JAVA_HOME" test -PforceTests -PjvmTestsOnly --no-build-cache`
      followed by
      `./gradlew … :koverXmlReport :app:koverXmlReport :auto:koverXmlReport :core:koverXmlReport :osmscout-client-java:jacocoTestReport`
      (spec `sonar-analysis`: "The scan reads the coverage the test run already measures"). Verify: the report
      task list contains no `:osmscout-client-java:jacocoVendoredTestReport` and no bare `:koverHtmlReport`.
- [x] 2.4 Add the scan step `uses: SonarSource/sonarqube-scan-action@v7` with `env: SONAR_TOKEN:
      ${{ secrets.SONAR_TOKEN }}` and `with: args: -Dsonar.qualitygate.wait=false` absent — i.e. the property
      is never set (spec `sonar-analysis`: "The quality gate colours the project and does not fail the build").
      Verify: `grep -c 'qualitygate.wait' .github/workflows/sonar.yml` is `0`, and the step is not wrapped in
      `continue-on-error` (a scanner failure must fail the run).

## 3. The declared boundary

- [x] 3.1 Write the root `sonar-project.properties`: `sonar.projectKey=Framstag_NaviVeylin`,
      `sonar.organization=framstag`, `sonar.host.url=https://sonarcloud.io`, `sonar.sourceEncoding=UTF-8`
      (spec `sonar-analysis`: "The badge the README shows is this project's measure").
- [x] 3.2 Declare the source boundary: `sonar.modules=app,auto,core,buildSrc,osmscout-client-java` with each
      module's `sonar.<module>.sonar.sources` and `sonar.<module>.sonar.tests` pointing at its `src/main` and
      `src/test` trees (design D5) — `app`'s `src/main/cpp` is not among them.
- [x] 3.3 Declare the exclusions: `app/src/main/cpp/**`, the submodule's Java client path,
      `tools/**`, `openspec/**`, `guidelines/**`, `licenses/**`, `vcpkg/**`, `**/build/**`, `**/.gradle/**`,
      `**/.cxx/**`, and the generated-code patterns `**/BuildConfig.*`, `**/R.java`, `**/R$*.java`,
      `dagger/hilt/**`, `**/Hilt_*`, `**/*_Hilt*`, `**/Dagger*Component*`
      (spec `sonar-analysis`: "The analysis measures this repository's sources and nothing else"). Verify by
      `grep -c` on the property file for each of the six required path groups, and by
      `git check-ignore`-style path inspection that each pattern matches a path that exists in the tree.
- [x] 3.4 Declare the coverage inputs per module: `sonar.coverage.jacoco.xmlReportPaths` per module, with
      `app`/`auto`/`core` reading their Kover XML and `osmscout-client-java` reading its `jacocoTestReport.xml`
      only (spec `sonar-analysis`: "The scan reads the coverage the test run already measures"). Verify:
      the property file names four report paths and no path containing `vendored` or `kover/merged`.
- [x] 3.5 Declare `sonar.java.binaries` for the Java module (design D5, the SonarJava bytecode requirement).
      Verify: the property names a binaries path, and the workflow's Gradle invocation compiles that module
      before the scan step runs.

## 4. The pin is recorded where every pin is

- [x] 4.1 Add the floor row to `tools/check-action-runtimes.sh`'s table:
      `SonarSource/sonarqube-scan-action 7 https://raw.githubusercontent.com/SonarSource/sonarqube-scan-action/v7/action.yml`
      (spec `sonar-analysis`: "The scanner action's runtime is recorded where every action's is"). Verify:
      `bash tools/check-action-runtimes.sh` exits 0 and prints its pin count including the new action; without
      the row it exits 1 naming the action as unrecorded.
- [x] 4.2 Run `bash tools/check-action-runtimes-selftest.sh` — its case 5 runs the checker against this
      repository's own workflows, which is the case the new pin has to pass. Verify: exit 0.

## 5. The workflow's own structural check

- [x] 5.1 Add `tools/check-sonar-workflow.sh`: assert that `.github/workflows/sonar.yml`'s triggers are exactly
      `push` (branch `main`) and `workflow_dispatch`; that no `pull_request`/`workflow_run` trigger exists;
      that the dispatch is guarded to `refs/heads/main`; that the scanner pin is a released major; and that
      `sonar-project.properties` carries each exclusion group from 3.3
      (spec `sonar-analysis`: "The scan is produced by CI from the main branch" and "The analysis measures this
      repository's sources and nothing else"). Verify: exit 0 on the landed files, exit 1 per broken assertion.
      Place the file at `tools/`; if `separate-tooling-test-code` has archived first, the test driver goes to
      `tools/tests/` per that change's layout.
- [x] 5.2 Add `tools/check-sonar-workflow-selftest.sh` with one case per assertion of 5.1, named as the delta's
      `**Case**:` lines name them — `sonar-workflow#theNativeTreeIsExcluded`,
      `sonar-workflow#generatedCodeIsExcluded`, `sonar-workflow#theTriggerSetIsPushAndDispatch`,
      `sonar-workflow#aPullRequestTriggerIsReported`, `sonar-workflow#theDispatchIsGuardedToMain`,
      `sonar-workflow#theVendoredReportIsNotListed`, `sonar-workflow#theGateIsNotWaitedFor`,
      `sonar-workflow#theScanStepIsNotContinueOnError` — each planting a broken workflow or property file in a
      throwaway tree and requiring the checker to fail naming file and line. Verify:
      `bash tools/check-sonar-workflow-selftest.sh` exits 0, and each case's mutation makes the checker exit 1.
      For the two pin scenarios the cases already exist: `action-runtimes#theRepositorysOwnPinsPass`
      (`check-action-runtimes-selftest.sh`, the case that runs the checker against this repository) and
      `action-runtimes#anActionWithNoRecordedRuntimeFails` (its `actions/never-heard-of@v1` case, which 8.1's
      mutation of the scanner row is the real instance of).
- [x] 5.3 Wire both into the pre-build structural checks in `.github/workflows/build.yml`, beside
      "Check action runtimes". Verify: the new step appears in the workflow, and the full step list still
      parses (the runner is the parser on the next push).

## 6. Documentation

- [x] 6.1 `guidelines/BuildEvidence.md` §7: add one rule naming the Sonar consumer — what it reads (the
      per-module reports), that it is report-only with `sonar.qualitygate.wait` unset, that it runs from `main`
      only, and that it measures this repository's Kotlin and Java with the native tree excluded. Verify:
      `bash tools/check-doc-routes.sh` and `bash tools/check-rule-routes.sh` exit 0.
- [x] 6.2 `guidelines/Build.md` §3: add the command row for the scan (the workflow's own command line), so the
      scan is reproducible by hand. Verify: the command in the guideline matches the workflow step verbatim.
- [x] 6.3 `AGENTS.md`: add a routing row for "sonar / code-quality analysis" → `guidelines/BuildEvidence.md` §7.
      Verify: `bash tools/check-doc-routes.sh` exits 0 (a row that prices a section must state a figure the
      section's measurement gives — this row names a section that already has one).
- [x] 6.4 `README.md`: commit the quality-gate, coverage and lines-of-code badge lines (spec `sonar-analysis`:
      "The badge the README shows is this project's measure"). Verify: each of the three badge URLs carries
      `project=Framstag_NaviVeylin` and links to that project's summary.
- [x] 6.5 `tools/feature-list/specs.json`: classify the capability this change introduces (`sonar-analysis`,
      area `build-tooling`, not user-visible), the line the classification gate owes before the change
      archives. Verify: `bash tools/gen-feature-list.sh --check-classification` names no unclassified id this
      change introduces (the ids it still names belong to other open changes).

## 7. Build and suite obligations (the scope block)

- [x] 7.1 No build is owed: the change touches no compiled file — no Gradle file, no module source, no test
      source. Verify: `git diff --name-only` against the base lists only the files in section 8's list, and
      `git diff --name-only | grep -E '\.(kt|java|cpp|c|h|kts)$'` is empty.
- [x] 7.2 No unit test is owed, for the same reason: there is no production behaviour to case. This is the
      named exclusion `guidelines/Build.md` §2's scope block asks for. Verify: stated in the change's
      completion report, with the file list as evidence.
- [x] 7.3 Existing suites: unaffected — no test source, no test configuration and no Gradle file changes.
      Verify: `bash tools/check-jvm-test-graph-selftest.sh` and `bash tools/check-dex-gate-graph-selftest.sh`
      exit 0 (the graph the gate reads is unchanged).

## 8. Revert-check (the invariant this change adds)

- [x] 8.1 **Mutation**: delete the `SonarSource/sonarqube-scan-action` row from
      `tools/check-action-runtimes.sh`'s floor table. **Case that must fail**: `bash tools/check-action-runtimes.sh`
      → exit 1, naming the action as carrying no recorded Node runtime. Restore, then
      `bash tools/check-action-runtimes.sh` → exit 0. Evidence: both runs' output in the change.
- [x] 8.2 **Mutation**: add a `pull_request:` trigger to `.github/workflows/sonar.yml`. **Case that must fail**:
      `bash tools/check-sonar-workflow.sh` → exit 1, naming the trigger. Restore → exit 0. Evidence: both runs'
      output.
- [x] 8.3 **Mutation**: remove `app/src/main/cpp/**` from `sonar-project.properties`'s exclusions. **Case that
      must fail**: `bash tools/check-sonar-workflow.sh` → exit 1, naming the missing exclusion group. Restore →
      exit 0. Evidence: both runs' output.
- [x] 8.4 **Mutation**: set `sonar.qualitygate.wait=true` in `sonar-project.properties` or the scan step's
      `args`. **Case that must fail**: `bash tools/check-sonar-workflow.sh` → exit 1, naming the gate wait.
      Restore → exit 0. Evidence: both runs' output.

## 9. Acceptance measurement (after the merge, on `main`)

- [ ] 9.1 First scan completes on `main`. Record: the run URL, the analysis date, and the failure text if the
      owner's setting from 1.1 was missed (spec `sonar-analysis`: "The setting reads as the failure's cause").
- [ ] 9.2 Read the analyse file list for `app/src/main/cpp/libosmscout` and for the submodule's Java client —
      neither may appear — and for all five in-scope modules — each must appear with a non-zero line count
      (spec `sonar-analysis` scenarios "An upstream file is outside the analysis" and "An owned module is
      inside the analysis"). Record: the file counts and the total `ncloc`.
- [ ] 9.3 Read the generated-code classes in the analyse file list — `BuildConfig`, `R`, Dagger/Hilt — and
      record that none appears (spec `sonar-analysis`: "Generated code is outside the analysis").
- [ ] 9.4 Compare per-module coverage against `tools/sensors/metrics/coverage.sh`'s summary for the same
      commit (`app/build/reports/jacoco/coverage.json`, from that commit's `sensor-inputs` artifact). Record
      both figures per module (spec `sonar-analysis` scenarios "Coverage reaches the module it was measured
      in" and "Vendored coverage is not attributed to this project"). A module materially below its summary is
      design D5's risk realised — record it and take the fallback named there as its own change.
- [ ] 9.5 Read the quality-gate badge and the project's quality gate result for that commit. If the gate is
      failing, the run must still be green (spec `sonar-analysis`: "A red gate leaves the workflow green").
      Record: gate result and workflow conclusion.
- [ ] 9.6 Record one push to `main` and one pull request against `main` in the workflow's run list: the push
      ran, the pull request produced no run (spec `sonar-analysis` scenarios "A push to main produces a scan"
      and "A pull request produces no scan"). Record: the run ids.

## 10. Scenario traceability

| Scenario (spec `sonar-analysis`) | Exercised by |
|---|---|
| An upstream file is outside the analysis | 5.1 (structural), 9.2 (real scan) |
| An owned module is inside the analysis | 9.2 |
| Generated code is outside the analysis | 5.1 (structural), 9.3 |
| A push to main produces a scan | 2.1, 9.6 |
| A pull request produces no scan | 2.1, 5.1, 8.2, 9.6 |
| A dispatch of another branch is refused | 2.1, 5.1 |
| Coverage reaches the module it was measured in | 2.3, 3.4, 9.4 |
| Vendored coverage is not attributed to this project | 3.4, 9.4 |
| A missing report is reported rather than ignored | 2.3 (the step fails when a report task did not run), 9.1 |
| A red gate leaves the workflow green | 2.4, 8.4, 9.5 |
| A scan that could not run fails the workflow | 2.4 (no `continue-on-error`), 9.1 |
| The setting reads as the failure's cause | 1.1, 9.1 |
| The new pin passes the runtime check | 4.1, 4.2 |
| An unrecorded scanner pin is reported | 4.1, 8.1 |
| Every badge names the analysed project | 3.1, 6.4 |
