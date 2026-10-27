# Tasks — fix-the-scan-skips-sources-under-a-directory-named-build

## 1. The red case

- [x] 1.1 `app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt`: add the case "the scan reads
  the sources a directory named build holds" — the scan's file set contains
  `app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt` and
  `buildSrc/src/main/kotlin/com/naviveylin/build/testing/BuildFlags.kt`, and, in a probe tree the case builds,
  it prunes the `build` directories a Gradle marker stands beside and reads the ones no marker stands beside.
  Verification: on base `3f972aec`,
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.build.ComposeApiDeprecationTest"
  -PforceTests --no-build-cache` fails with `read 768, missing [app/src/test/java/com/naviveylin/build/
  ComposeApiDeprecationTest.kt, buildSrc/src/main/kotlin/com/naviveylin/build/testing/BuildFlags.kt]`, JUnit
  XML `tests=3 failures=1`, timestamp `2026-10-27T16:36:14.835Z`
  (`evidence/TEST-ComposeApiDeprecationTest-red.xml`). Parent: `build-jvm-toolchain`, "The project's own
  Kotlin sources call no deprecated quadratic path builder".

## 2. The fix

- [x] 2.1 Same file: prune a `build` directory only when its parent carries `build.gradle.kts`/`build.gradle`
  (`isGradleOutput()`), instead of listing `build` among the skipped directory names. Verification: task 1.1's
  case turns green and the existing case prints `files=805 findings=0`
  (`evidence/TEST-ComposeApiDeprecationTest-green.xml`).
- [x] 2.2 Same file: assemble the probe literals and the failure message from parts
  (`DEPRECATED = "quadratic" + "Bezier" + "To"`), so the scan — which now reads this file — reports no finding
  for the guard's own source. Verification: the existing case stays green with `findings=0`, and the
  detectability case still refuses both deprecated spellings (`evidence/TEST-ComposeApiDeprecationTest-green.xml`).
- [x] 2.3 Falsification (revert-check): restore the name-based prune, the new case must go red, restore, forced
  green — the byte-identical restore named by its md5 (`evidence/revert-check.txt`).

## 3. Guideline and spec

- [x] 3.1 `guidelines/Build.md`, the "Code coverage" section, its "Kotlin deprecation baseline" paragraph: the count becomes the number
  the scan prints (`805` files) and the paragraph says what a `build` directory is pruned by. Verification: the
  paragraph names the printed count and the marker; `bash tools/check-doc-routes.sh` exits 0
  (`evidence/structural-checks.txt`).
- [x] 3.2 `openspec/specs/build-jvm-toolchain/spec.md`: the requirement gains the scenario "The sources a
  package directory named build holds are read", naming the new case, and its text says a `build` directory is
  pruned by the Gradle marker beside it. Verification: `openspec validate --specs` passes, and the merged
  requirement is the change's delta plus the previous text (`evidence/structural-checks.txt`).

## 4. Verification

- [x] 4.1 The three cases are green on the fixed tree:
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.build.ComposeApiDeprecationTest"
  -PforceTests --no-build-cache` — JUnit XML `tests=3 failures=0`
  (`evidence/TEST-ComposeApiDeprecationTest-green.xml`).
- [x] 4.2 The change's declared cases select the class: `bash tools/declared-cases.sh
  fix-the-scan-skips-sources-under-a-directory-named-build`; its output is in `evidence/structural-checks.txt`
  (`selected 1 of 443 test classes … com.naviveylin.build.ComposeApiDeprecationTest`).
- [x] 4.3 Structural checks for the text half of the delta: `openspec validate
  fix-the-scan-skips-sources-under-a-directory-named-build --strict`, `openspec validate --specs`,
  `bash tools/check-doc-routes.sh`, `tools/check-openspec-hygiene.sh`
  (`evidence/structural-checks.txt`).
- [x] 4.4 The documented gate, once: `./tools/loop/gate.sh` → exit 0, `LOOP-GATE: tests=645 failures=0
  errors=0` over 23 suites, each suite's time on the same line (`evidence/gate.log`, `evidence/gate-summary.xml`).
- [x] 4.5 Copy every cited XML into `evidence/` of this change and check the pointers:
  `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-27-fix-the-scan-skips-sources-under-a-directory-named-build
  --root .` (`evidence/evidence-check.txt`).
- [x] 4.6 Archive the change and confirm the requirement merged into
  `openspec/specs/build-jvm-toolchain/spec.md`.

## 5. Review round 1 corrections

- [x] 5.1 The prune half of the case was vacuous on the real tree — `scanned.none { it.startsWith("app/build/") }`
  with `0` `.kt` files under the output directories, so an `isGradleOutput()` that pruned nothing still passed.
  The case now builds a probe tree with the two kinds of `build` directory (marker beside `build/` and
  `app/build/`; no marker beside `lib/build/` and `src/main/java/com/naviveylin/build/`) and asserts the exact
  three sources walked, so the prune half fails when the prune stops. Verification: one mutation
  (`onEnter` without `isGradleOutput()`, marked) reddens the case with the probe's own list
  (`evidence/TEST-ComposeApiDeprecationTest-prune-red.xml`, `tests=3 failures=1`, `2026-10-27T17:01:48.246Z`),
  restore byte-identical (`md5sum` `11628c2552877985149f7c32f582240e`), forced green after the restore
  (`evidence/TEST-ComposeApiDeprecationTest-green.xml`, `2026-10-27T17:01:57.965Z`, `files=805 findings=0`,
  `scanned=805`, `prune-probe walked=[… 3 paths …]`); `evidence/revert-check.txt` steps 6–11.
- [x] 5.2 `evidence/TEST-ComposeApiDeprecationTest-red.xml` was cited by this change and carried only in the
  item's working directory. Copied into the change's own `evidence/` (`tests=3 failures=1`,
  `2026-10-27T16:36:14.835Z`, `read 768`); `evidence-check` C2 pairs the red XML by its timestamp now.
- [x] 5.3 The three `NOTE:`s: task 4.2 points at `evidence/structural-checks.txt`; `LOOP-MEASURE-GATE
  seconds=1120 suites=23` is dropped from the proposal and from task 4.4 (`measure.sh` prints `seconds=-` for
  this tree, and the gate's own line carries the times); the surface line's count is `3 files`, not `2`, and
  the contradictory "So: … 1 case extended" sentence is gone.
