# Tasks — fix-gradle10-script-deprecations

## 1. The red case

- [x] 1.1 Add `app/src/test/java/com/naviveylin/build/GradleScriptDeprecationTest.kt` with the two cases:
  "no project build script uses a removed Gradle construct" (scan of every `*.gradle.kts` the project owns,
  asserts zero findings and prints `scripts=`/`findings=`) and "the removal is still detectable" (the scan
  reports a deprecated line and ignores the named form beside a comment naming the delegate). Verification: on
  the base commit `648d2bc` —
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.GradleScriptDeprecationTest" -PforceTests`
  fails with `expected:<[]> but was:<[:377: …]`, JUnit XML `tests=2 failures=1`, timestamp
  `2026-10-20T12:14:18.550Z`, `scripts=8 findings=10` (`evidence/TEST-GradleScriptDeprecationTest-red.xml`).
  Parent: `build-jvm-toolchain`, "The build's scripts use no construct Gradle 10 removes".

## 2. The fix

- [x] 2.1 In `app/build.gradle.kts`, rewrite the six registrations at `:377` (`checkHardcodedStrings`), `:406`
  (`checkNoCoordinatesInLogs`), `:444` (`checkCleartextTransportPolicy`), `:474` (`checkNoWallClockWaits`),
  `:606` (`downloadSbomCli`) and `:734` (`mergeNativeSbom`) from `val <name> by tasks.registering {` to
  `val <name> = tasks.register("<name>") {`. The configure block and the task name are unchanged.
  Verification: `grep -c "by tasks.registering" app/build.gradle.kts` prints `0`.
- [x] 2.2 Same rewrite in `auto/build.gradle.kts` (`:118`, `:138`) and `core/build.gradle.kts` (`:104`, `:125`).
  Verification: `grep -rn --include="*.gradle.kts" "by tasks.registering" .` finds nothing (exit 1); the guard
  case above turns green (`scripts=8 findings=0`).
- [x] 2.3 `guidelines/Build.md` §7: record the sweep beside the existing deprecation paragraphs — the
  project-owned delegate family is gone (with the command that measures it), the Kover plugin's Project-object
  warning is measured as plugin-owned and stays, and the `:app` case is named as the guard.
  Verification: the paragraph names the command, the two outcomes and the case; `bash tools/check-doc-routes.sh`
  — `all 89 section references resolve`, exit 0.

## 3. Verification

- [x] 3.1 The guard is not vacuous and the tree is clean of the family:
  `./gradlew --offline --warning-mode all help` reports `0` matches for `val name by registering` and still
  reports the Kover plugin's one (`Project object as a dependency`: count `1`) —
  `evidence/fixed-warning-mode-all.txt`, against `evidence/head-warning-mode-all.txt` (ten).
- [x] 3.2 The ten tasks still exist under their names and run:
  `./gradlew --offline --warning-mode all :app:checkHardcodedStrings :app:checkNoCoordinatesInLogs
  :app:checkCleartextTransportPolicy :app:checkNoWallClockWaits :auto:checkHardcodedStrings
  :auto:checkNoWallClockWaits :core:checkHardcodedStrings :core:checkNoWallClockWaits` — every task line
  executed, `BUILD SUCCESSFUL in 1s`, the only warning the Kover one (`evidence/fixed-checks.log`); and
  `./gradlew --offline :app:tasks --all` still lists `checkHardcodedStrings`, `checkNoCoordinatesInLogs`,
  `checkCleartextTransportPolicy`, `checkNoWallClockWaits`, `downloadSbomCli`, `mergeNativeSbom`
  (`evidence/app-tasks-all.log`).
- [x] 3.3 The red case is green on the fixed tree:
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.GradleScriptDeprecationTest"
  -PforceTests` — JUnit XML `tests=2 failures=0`, timestamp `2026-10-20T12:15:36.723Z`,
  `scripts=8 findings=0` (`evidence/TEST-GradleScriptDeprecationTest-green.xml`).
- [x] 3.4 Falsification (revert-check): `app/build.gradle.kts:377` restored to the delegate form makes the
  guard red — `expected:<[]> but was:<[:377: val checkHardcodedStrings by tasks.registering {]>`, exit 1 —
  and the byte-identical restore (`md5 29f1ed11af62b11fb6af63e2ebd9ba54`, unchanged before and after) is
  green again (`evidence/revert-check.txt`, `evidence/revert-check-red.log`).
- [x] 3.5 The documented gate, once: `./gradlew test -PforceTests --no-build-cache` — `BUILD SUCCESSFUL in
  4m 10s`; per module: app mobile `tests=2064 failures=0`, app automotive `tests=2064 failures=0`, auto
  `tests=866 failures=0`, core `tests=642 failures=0`, osmscout-client-java `tests=48 failures=0` — total
  `tests=5684 failures=0` (`evidence/gate-tallies.txt`, `evidence/gate.log`, and the class's XML as the gate
  produced it in both flavors).
- [x] 3.6 The change's declared cases select the new class: `bash tools/declared-cases.sh
  fix-gradle10-script-deprecations` — rule `declared` selects 1 class, `com.naviveylin.build.GradleScriptDeprecationTest`,
  in `app` (`evidence/declared-cases.txt`).

## 4. Round-2 corrections (review of round 1)

Two blocking findings of the first review, both subtractive: a scenario promised more than the artifacts
carry. No assertion, no fix and no case changed, so the red and green runs of tasks 1.1 and 3.3 and the gate of
task 3.5 keep their results; only the case's two comments changed, and one focused run re-confirms them green.

- [x] 4.1 Scenario "A project script registers its tasks by name" no longer promises the `dependsOn` edge —
  nothing in this change exercises that edge (`fixed-checks.log` invokes the eight tasks by name, so no edge
  resolves). Its THEN keeps the name half: "every task keeps the name its CI invocation already uses". The same
  unproven phrase is gone from `proposal.md`, `design.md`'s equivalence sentence and from
  `guidelines/Build.md` §7. Verification:
  `grep -rn dependsOn openspec/changes/archive/2026-10-20-fix-gradle10-script-deprecations/proposal.md
  openspec/changes/archive/2026-10-20-fix-gradle10-script-deprecations/design.md
  openspec/changes/archive/2026-10-20-fix-gradle10-script-deprecations/specs
  openspec/specs/build-jvm-toolchain/spec.md` finds nothing (exit 1); §7's paragraph still names the sweep, the
  command and the guard case; `bash tools/check-doc-routes.sh` → `all 89 section references resolve`, exit 0.
- [x] 4.2 Scenario "The removal is still detectable" promises what the scan prints — "each offending line's
  number and text" — in the applied spec, the archived delta, the class KDoc and the `findingsIn` comment, so no
  document claims a path the finding does not carry (the red XML's `system-out` and failure message both show
  `:<line>: <text>`). Verification: `grep -rn "by file\|names its file"` over the two spec files, the
  change's `proposal.md` and `design.md`, and the case finds nothing (exit 1).
- [x] 4.3 The edited case still compiles and both cases stay green:
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.GradleScriptDeprecationTest"
  -PforceTests` — JUnit XML `tests=2 failures=0`, timestamp `2026-10-20T12:25:15.470Z`, `scripts=8 findings=0`
  (`evidence/round2-TEST-GradleScriptDeprecationTest.xml`, log `evidence/round2-focused.log`). The gate of task
  3.5 is not re-run: a comment-only edit changes no behaviour, and a re-run is what destroys its evidence.
- [x] 4.4 The applied requirement and the delta are still the same text: the requirement region of
  `openspec/specs/build-jvm-toolchain/spec.md` diffs clean against the archived delta (`diff` → no output).
  Structural checks after the correction: `openspec validate --specs` → `Totals: 176 passed, 0 failed`,
  `bash tools/check-doc-routes.sh` → 89 resolve, `bash tools/check-todo-references.sh` → 62 citations resolve
  (`evidence/round2-structural-checks.txt`).
