# Tasks — fix-deprecated-quadratic-path-builder

## 1. The red case

- [x] 1.1 Add `app/src/test/java/com/naviveylin/build/ComposeApiDeprecationTest.kt` with the two cases:
  "no project Kotlin source calls a deprecated quadratic path builder" (scan of every `*.kt` the project owns,
  asserts zero deprecated spellings and prints `files=`/`findings=`) and "the scan is still detectable" (the
  scan reports each deprecated spelling and ignores the kept spelling and a comment that names the deprecated
  one). Verification: on the base commit `3c1ecc78` —
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.build.ComposeApiDeprecationTest" -PforceTests --no-build-cache`
  fails with `expected:<[]> but was:<[app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:165: …]>`,
  JUnit XML `tests=2 failures=1`, timestamp `2026-10-20T13:20:05.766Z`, `files=731 findings=1`, and the same
  run prints `w: …LocationMarkerOverlay.kt:165:14 'fun quadraticBezierTo(…)' is deprecated`
  (`evidence/TEST-ComposeApiDeprecationTest-red.xml`, `evidence/head-red-run.log`).
  Parent: `build-jvm-toolchain`, "The project's own Kotlin sources call no deprecated quadratic path builder".

## 2. The fix

- [x] 2.1 `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:165`: replace
  `path.quadraticBezierTo(0f, -hPx, x1 * hPx, y1 * hPx)` with
  `path.quadraticTo(0f, -hPx, x1 * hPx, y1 * hPx)` — the same control point and end point, so the outline is
  unchanged. Verification: the guard case above turns green (`files=731 findings=0`), and the compile prints no
  `w:` line for the file (`evidence/fixed-focused.log`).

## 3. Guideline and spec

- [x] 3.1 `guidelines/Build.md` §7: record the Kotlin-side deprecation baseline beside the Gradle one — the one
  project-owned call site is gone, the compile line that measured it is quoted, no warning-as-error flag is set
  and why (109 `w:` lines on HEAD), and `ComposeApiDeprecationTest` is named as the guard.
  Verification: the paragraph names the file, the case and the measurement; `bash tools/check-doc-routes.sh`
  exits 0.
- [x] 3.2 `openspec/specs/build-jvm-toolchain/spec.md`: the Purpose gains the Kotlin-source half of the same
  rule so the new requirement is not wider than the capability that carries it.
  Verification: `openspec validate --specs` passes for the capability; the Purpose sentence names the Kotlin
  sources.

## 4. Verification

- [x] 4.1 The red case is green on the fixed tree:
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.build.ComposeApiDeprecationTest" -PforceTests --no-build-cache`
  — JUnit XML `tests=2 failures=0` (`evidence/TEST-ComposeApiDeprecationTest-green.xml`).
- [x] 4.2 Falsification (revert-check): restore the deprecated spelling in the one call site, the guard case
  must go red (`expected:<[]> but was:<[…:165: path.quadraticBezierTo…]>`), restore, forced green — the byte
  identical restore named by its md5 (`evidence/revert-check.txt`).
- [x] 4.3 The change's declared cases select the new class: `bash tools/declared-cases.sh
  fix-deprecated-quadratic-path-builder` selects `com.naviveylin.build.ComposeApiDeprecationTest` in `app`
  (`evidence/declared-cases.txt`).
- [x] 4.4 Structural checks for the text half of the delta: `openspec validate fix-deprecated-quadratic-path-builder
  --strict` → `Change 'fix-deprecated-quadratic-path-builder' is valid`; `openspec validate --specs` →
  `Totals: 176 passed, 0 failed`; `bash tools/check-doc-routes.sh` → `all 89 section references resolve`
  (`evidence/structural-checks.txt`).
- [x] 4.5 The documented gate, once: `./gradlew test -PforceTests --no-build-cache` (2026-10-20T13:21-13:23Z) —
  `:app:testMobileDebugUnitTest` reported `2068 tests completed, 1 failed`, `BUILD FAILED in 2m`, the failing
  case being `MapManagerUpdatesQueueTest > updateOneItemRunsOnlyThatOne`
  (`ComparisonFailure: expected:<[new types]> but was:<[installed]>`, `MapManagerUpdatesQueueTest.kt:202`,
  `evidence/gate.log:286-311`). That case is not this delta's: the change's one production line is the marker
  path builder, the case never touches it, and it passes in isolation twice
  (`evidence/mapmanager-focused-1.log`, `-2.log`) and beside the new guard case
  (`evidence/mapmanager-pair-with-new-case.log`). The gate stopped before the remaining suites, which were then
  measured with the same flags without re-running the mobile suite (the one-gate rule): automotive 2068/0,
  auto 866/0, core 642/0, osmscout-client-java 48/0 (`evidence/gate-remaining-suites.log`) — the automotive
  suite runs the same 2068 `:app` cases from the same sources, and in it the failing case passes
  (`evidence/TEST-MapManagerUpdatesQueueTest-automotive-gate.xml`) while the guard is green in both flavors
  (`files=731 findings=0`). Tallies and the cross-check: `evidence/gate-tallies.txt`.
- [x] 4.6 No Kotlin deprecation for the file in any run: `grep -c '^w: .*LocationMarkerOverlay'
  evidence/gate.log evidence/gate-remaining-suites.log` → `0`, `0` (HEAD's focused run printed
  `1`, `evidence/head-red-run.log`).
- [x] 4.7 Archive the change and confirm the requirement merged into
  `openspec/specs/build-jvm-toolchain/spec.md`.
