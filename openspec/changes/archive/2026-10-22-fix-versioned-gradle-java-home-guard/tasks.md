# Tasks — fix-versioned-gradle-java-home-guard

## 1. The red case

- [x] 1.1 Add `app/src/test/java/com/naviveylin/build/VersionedGradleJavaHomeTest.kt` with three cases: "the
  versioned property source names no Java home" (scan of `gradle.properties`, comment-aware, printing
  `propertySource=`/`lines=`/`findings=`), "the pipeline refuses a reintroduced line" (the workflow's step
  "Check daemon JVM criteria" must read the property source and refuse the key) and "the scan is still
  detectable" (indented and `=`-padded settings are reported; a comment and another property are not).
  Verification: on the base tree,
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.VersionedGradleJavaHomeTest" -PforceTests`
  fails with `VersionedGradleJavaHomeTest > the pipeline refuses a reintroduced line FAILED`, JUnit XML
  `tests=3 failures=1`, timestamp `2026-10-22T08:48:01.161Z` (`evidence/red-pipeline-case.xml`).
  Parent: `build-jvm-toolchain`, "The versioned property source names no Java home".

## 2. The guard

- [x] 2.1 `.github/workflows/build.yml`, step "Check daemon JVM criteria": after the `toolchainVendor` check,
  refuse an `org.gradle.java.home` line in `gradle.properties` —
  `grep -nE '^[[:space:]]*org\.gradle\.java\.home[[:space:]]*='` with `::error` and `exit 1`. Four lines, no
  other step touched. Verification: the step body executed against this tree
  (`yq -r '.jobs.build.steps[] | select(.name=="Check daemon JVM criteria") | .run' … > /tmp/loop248/guard.sh &&
  bash /tmp/loop248/guard.sh`) prints `daemon JVM criteria: version 21, no vendor, no package id.` and exits 0;
  the new predicate applied to the #91 line
  (`grep -nE '^[[:space:]]*org\.gradle\.java\.home[[:space:]]*=' <(printf 'org.gradle.java.home=/usr/lib/jvm/java-17-openjdk\n')`)
  prints `1:org.gradle.java.home=/usr/lib/jvm/java-17-openjdk`, so the check would fail that job
  (`evidence/ci-step-green.log`).
- [x] 2.2 `guidelines/Build.md` §2, "The build JVM (daemon JVM criteria)": the paragraph that states the
  absence now names both surfaces (the CI step and the case, issue #248), and the checks block carries the
  focused command. Verification: `bash tools/check-doc-routes.sh` after the edits (task 3.4).

## 3. Verification

- [x] 3.1 The red case is green on the fixed tree:
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.build.VersionedGradleJavaHomeTest" -PforceTests`
  — JUnit XML `tests=3 failures=0`, timestamp `2026-10-22T08:48:19.405Z`, `propertySource=gradle.properties
  lines=29 findings=0` (`evidence/green-focused.xml`).
- [x] 3.2 Falsification (revert-check): the four CI lines removed again makes the named case red —
  `the pipeline refuses a reintroduced line FAILED`, XML `tests=3 failures=1`, timestamp
  `2026-10-22T08:48:41.782Z` — and the byte-identical restore (`md5sum` `e41120b509d7a21fd5992100c67236d3`,
  unchanged before and after) is green again, XML `tests=3 failures=0`, timestamp
  `2026-10-22T08:48:47.716Z` (`evidence/revert-check.txt`, `evidence/revert-red.xml`).
- [x] 3.3 The change's declared cases select the new class: `bash tools/declared-cases.sh
  fix-versioned-gradle-java-home-guard` — rules `declared 2 · touched 1 · mention 0` select
  `com.naviveylin.build.VersionedGradleJavaHomeTest` (0.218 s) and, because the change names it,
  `com.naviveylin.build.GradleScriptDeprecationTest` (0.136 s) in `app` (`evidence/declared-cases.txt`).
- [x] 3.4 Structural checks: `bash tools/check-doc-routes.sh` → `all 120 section references resolve, all 5 skill
  references resolve`; `openspec validate --specs` → `Totals: 177 passed, 0 failed`; the workflow still parses
  with 40 steps in `jobs.build` (`evidence/structural-checks.txt`).
- [x] 3.5 The project's gate, once: `./tools/loop/gate.sh` — exit 0,
  `LOOP-GATE: tests=477 failures=0` over the nine commands this pathspec owes, and from the XMLs the run
  left: app mobile `tests=2101 failures=0`, app automotive `tests=2101 failures=0`, auto `tests=876
  failures=0`, core `tests=642 failures=0`, osmscout-client-java `tests=48 failures=0` — `6245` is
  `477` (the shell suites the gate's own line counts) + `5768` (the five module tallies from the XMLs), `0
  failures, 0 errors`; the gate's `tests=477` therefore counts the shell suites only and the module tests are
  evidenced by the XMLs, not by that number; the new class ran in both app flavours (timestamps `2026-10-22T08:50:12.730Z` and
  `2026-10-22T08:51:54.115Z`), 3 tests each, 0 failures (`evidence/gate.log`, `evidence/gate-tallies.txt`,
  `evidence/gate-mobile-VersionedGradleJavaHomeTest.xml`, `evidence/gate-automotive-VersionedGradleJavaHomeTest.xml`).

## 4. Round 2 — the review's corrections

- [x] 4.1 `proposal.md` Surface: replace the wrong `grep -c` sentence ("9 after the change (5 pre-existing, 4
  added)") with the measured result — 10 matching lines, 9 pre-existing (66, 71, 462, 468, 475, 482, 491, 504,
  525) plus the added `::error` at :345, the predicate at :344 being escaped so the quoted grep does not match
  it — and put the block's conclusion on one `^surface:` line. Verification: `grep -c`/`grep -n` on
  `.github/workflows/build.yml` (quoted in `evidence/corrections-round2.md`) and
  `./tools/loop/measure.sh --change 2026-10-22-fix-versioned-gradle-java-home-guard` printing
  `LOOP-MEASURE-SURFACE surface: 1 case class added (3 cases), …` instead of `none`.
- [x] 4.2 `tasks.md` 3.5: state the `6245` total as `477` shell cases + `5768` module tests. Verification: the
  eight shell tallies in `evidence/gate-tallies.txt` sum to 477 and the five module tallies to 5768.
- [x] 4.3 Record the round and its measurements: `evidence/corrections-round2.md`, plus a round-2 section in
  `evidence/structural-checks.txt`. Verification: `bash tools/check-doc-routes.sh` → `all 120 section
  references resolve, all 5 skill references resolve`; `openspec validate --specs` → `Totals: 177 passed, 0
  failed`. No gate re-run: this round touches no compiled file.
