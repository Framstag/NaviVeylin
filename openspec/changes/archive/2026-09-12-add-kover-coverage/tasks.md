# Tasks: add-kover-coverage

Parent spec: `test-coverage` (openspec/changes/add-kover-coverage/spec.md)

## 1. Kover plugin wiring

- [x] **T1.1 Declare Kover in root build** [test-coverage: R1]
      `org.jetbrains.kotlinx.kover` 0.9.8 applied at root (merging module);
      verified with `./gradlew help` (configuration succeeds).
- [x] **T1.2 Apply Kover to :app** [test-coverage: R1]
      Plugin applied; per-variant + total kover task groups registered;
      `:app:koverXmlReport` produced 462-class report.
- [x] **T1.3 Apply Kover to :auto** [test-coverage: R1]
      Same; report produced (162 classes).
- [x] **T1.4 Apply Kover to :core** [test-coverage: R1]
      Same; report produced (38 classes).
- [x] **T1.5 Measure :osmscout-client-java via JaCoCo (fallback)** [test-coverage: R1]
      Kover yields an empty (0-counter) report on the Kotlin-plugin-free
      `java-library` module — documented in ki_processing_failures.log. Used the
      T1.5 fallback: standard Gradle `jacoco` plugin; `jacocoTestReport`
      (HTML+XML) non-empty (LINE 5 covered / 1 missed). Engine note lands in
      `guidelines/Build.md` (T5.1).

## 2. Report configuration

- [x] **T2.1 Exclude generated code** [test-coverage: R4]
      Kover report filters in `app`, `auto`, `core` AND at the root merging
      module exclude `*.BuildConfig`, `*.R`, `*.R$*`, `dagger.hilt.*`,
      `hilt_aggregated_deps.*`, `*.Hilt_*`, `*_Hilt*`, `*.Dagger*Component*`.
      Verified: merged report contains 0 Hilt/Dagger/BuildConfig/R entries
      (69 leaked classes pre-filter, all matched).
- [x] **T2.2 Root aggregation + formats** [test-coverage: R2, R3]
      Root `kover` configuration merges `:app`/`:auto`/`:core`;
      `:koverHtmlReport` + `:koverXmlReport` produce HTML + XML; merged
      report 662 classes (= 462+162+38, no double counting), LINE
      8839/14805 (59.7%) covered, per-module: app 58.9%, auto 53.8%, core
      67.3%.

## 3. Verification (local)

- [x] **T3.1 Full build compiles** [test-coverage: R1]
      `./gradlew :app:assembleDebug` succeeds — BUILD SUCCESSFUL, 143 tasks,
      no new warnings from our scripts (pre-existing Kotlin opt-in warning in
      MapCanvasViewModelRoadInfoTest tracked in TODO.md).
- [x] **T3.2 Existing tests still pass** [test-coverage: R1–R5 regression]
      `./gradlew test` passes for all modules incl. Robolectric Compose UI
      tests under instrumentation, plus a plain (uninstrumented) `test` run.
- [x] **T3.3 Report content checks** [test-coverage: R1, R2, R3, R4]
      (a) HTML+XML exist: root merged, per-module (app/auto/core), and
      `osmscout-client-java` JaCoCo; (b) non-zero line coverage everywhere;
      (c) no `BuildConfig`/`R`/Hilt/Dagger classes in metrics; (d) union-style
      merge (merged 662 = sum of per-module classes, overlapping classes not
      double counted). Baseline recorded: merged 59.7% line / 58.2%
      instruction; app 58.9%, auto 53.8%, core 67.3%.

## 4. CI integration

- [x] **T4.1 Coverage step in workflow** [test-coverage: R5]
      `.github/workflows/build.yml` gains "Generate coverage reports"
      (`:koverHtmlReport :koverXmlReport :osmscout-client-java:jacocoTestReport`)
      after "Run unit tests" + "Upload coverage reports" artifact step
      (`build/reports/kover/**` + JaCoCo tree, `if-no-files-found: error`).
- [x] **T4.2 CI run green + artifact** [test-coverage: R5]
      NEEDS PUSH: push a branch, confirm the workflow succeeds and the
      coverage artifact downloads with HTML+XML inside; confirm the debug-APK
      step output is unchanged from before the change. Cannot be verified
      without a remote run.

## 5. Documentation

- [x] **T5.1 Update guidelines/Build.md** [project rule: guidelines sync]
      New section 7 "Code coverage": engines (Kover 0.9.8 for Kotlin modules,
      JaCoCo for osmscout-client-java + why), commands, report locations,
      generated-code exclusion rationale, report-only policy (`koverVerify`
      reserved for a future change), baseline numbers, CI step, upstream
      Gradle-deprecation note. AGENTS.md build-commands block gains the
      coverage command; TODO.md gains the pre-existing opt-in warning + the
      Kover/Gradle-10 deprecation note.

## 6. Final regression check

- [x] **T6.1 Green build + clean state** [test-coverage: R1–R5 regression]
      `./gradlew test` and `./gradlew :app:assembleDebug` pass (final state,
      BUILD SUCCESSFUL); `openspec validate add-kover-coverage` valid;
      `git status` shows only intended build/CI/doc changes from this change
      (other modified files belong to unrelated in-flight OpenSpec changes).
