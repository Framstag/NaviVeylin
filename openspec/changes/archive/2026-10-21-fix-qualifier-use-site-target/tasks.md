# Tasks — fix-qualifier-use-site-target

Round 1 ran on 2026-10-20 and was refused: its red/green/gate XMLs came from a guard whose scan surface
excluded the package it lives in, and its two `javap` dumps were one classfile taken twice. Every task below
states the evidence of the final revision; where a round-1 artefact was superseded it says so.

## 1. The guard, written first

- [x] 1.1 Write `app/src/test/java/com/naviveylin/build/QualifierUseSiteTargetTest.kt`: the detector
      (`@(ApplicationContext|ActivityContext|Named)` applied to a `val`/`var` with no `:target`, comments
      skipped), the real-tree case `no qualifier on an injected property relies on the implicit target` over
      the six Kotlin source roots of `app`, `auto` and `core`, and the detector case
      `the implicit target is still detectable` — spec `build-test-gate`, "An injected property states its
      qualifier's use-site target", scenarios 1 and 2.
      *Verification*: red state = the revert check's mutation (task 3.1), because the guard is written after
      the fix it holds: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.build.QualifierUseSiteTargetTest" -PforceTests --no-build-cache` →
      `sources=719 findings=1`, the finding naming
      `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt:36`
      (`evidence/red-run-round2.xml`, `tests=2 failures=1`, `2026-10-20T23:02:07.005Z`, log
      `items/220/red-run-round2.log`).

- [x] 1.2 Make the guard's surface the six source-set roots instead of a repository walk pruned by directory
      name, and assemble its own fixtures from parts. The first version pruned every directory named `build`
      — which is the project's own guard package — so it scanned 716 of the 719 Kotlin files and could not
      see itself; a whole fixture inside that surface is a finding of the guard it feeds.
      *Verification*: `python3 evidence/probe-guard-surface.py .` → `sources=719 findings=0`, and the fixtures
      still match (`implicit fixtures hits: [1, 1, 1]`, `explicit fixtures hits: [0, 0, 0, 0, 0]`,
      `evidence/probe-guard-surface.txt`); the case asserts each root exists, so a lost root cannot shrink
      the surface silently.

- [x] 1.3 Record the warning the red case stands in for: compile both `:app` main source sets with warnings on
      and count the family on HEAD (before the fix).
      *Verification*: `./gradlew :app:compileMobileDebugKotlin :app:compileAutomotiveDebugKotlin
      --rerun-tasks --no-build-cache -Pkotlin.incremental=false` → exactly 2
      `value parameter only … also be applied to field` lines, both at
      `NavigationNotificationController.kt:36:5` (log `evidence/head-compile-warnings.log`).

## 2. The statement

- [x] 2.1 `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt:36`:
      `@ApplicationContext` → `@param:ApplicationContext`.
      *Verification*: the filtered case on the committed tree → `tests=2 failures=0`, `sources=719
      findings=0` (`evidence/green-run-round2.xml`, `2026-10-20T23:03:45.120Z`); and a forced recompile of
      both flavors prints 0 lines of the family while the tree's other warnings still arrive — 2 lines, the
      `LocationMarkerOverlay.kt:165` deprecation, one per flavor (`evidence/post-fix-compile-warnings.log`,
      `BUILD SUCCESSFUL in 18s`, both compile tasks executed). The gate's own console cannot carry this
      measurement: its compiles are `UP-TO-DATE` when this task runs them, and an up-to-date task prints no
      warnings either way.

- [x] 2.2 Prove the annotation still has the effect it had: compile the class from the bare form, capture it,
      compile it from the `@param:` form, capture it, and compare.
      *Verification*: two compilations — classfile `Last modified` `22:56:32` and `22:56:50`, each recorded
      with the source line at capture time in `evidence/classfile-state-bare.txt` /
      `evidence/classfile-state-param.txt` — produce the same 6225 bytes and the same SHA-256
      (`evidence/classfile-bare.class`, `evidence/classfile-param.class`), so `diff` of the two `javap -v -p`
      dumps is empty (`evidence/javap-diff-round2.txt`, 0 bytes) and both carry
      `RuntimeVisibleParameterAnnotations: parameter 0:
      dagger.hilt.android.qualifiers.ApplicationContext`.

## 3. Falsification of the new invariant

- [x] 3.1 Revert-check (`revert-check` skill): restore the bare `@ApplicationContext`, run the filtered case
      — it must fail and name the file — then restore `@param:` and re-run forced green.
      *Verification*: the mutated run prints `expected:<[]> but was:<[:36: @ApplicationContext private val
      context: Context, in app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt]>`
      with `sources=719 findings=1` (`evidence/red-run-round2.xml`, `tests=2 failures=1`,
      `2026-10-20T23:02:07.005Z`, quoted in `evidence/revert-checked.md`); the restored run is
      `evidence/green-run-round2.xml` (`tests=2 failures=0`, `2026-10-20T23:03:45.120Z`), and line 36 reads
      `@param:ApplicationContext private val context: Context,` in the tree the gate ran on.

## 4. Owning documents

- [x] 4.1 `guidelines/Build.md` §4 "Result evaluation": the "Warnings" bullet records the measured site, the
      2-lines-from-2-flavors count, the post-fix compile, and names `QualifierUseSiteTargetTest` as the guard.
      *Verification*: the bullet names the case, the count and the guard's surface rule; the change touches no
      compiled file there, so this task runs no build.

- [x] 4.2 The spec delta `openspec/changes/archive/2026-10-21-fix-qualifier-use-site-target/specs/build-test-gate/spec.md`
      carries both scenarios, each naming exactly one case of task 1.1, with each `THEN` limited to what that
      case asserts.
      *Verification*: `openspec validate build-test-gate --strict` → `Specification 'build-test-gate' is
      valid` (the CLI validates active specs, not archived changes, so the merged capability spec is what is
      checked; the delta's requirement and both scenarios are byte-identical to it), and the delta is merged
      into `openspec/specs/build-test-gate/spec.md` under its "An injected property states its qualifier's
      use-site target" heading.

## 5. The gate, once

- [x] 5.1 Run the project's gate exactly as the run's configuration states it, once, on the tree the change
      leaves behind.
      *Verification*: `./gradlew test -PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 3m 36s`,
      189 tasks (26 executed, 0 `FROM-CACHE`), `tests=5704 failures=0 errors=0` over the five suites
      (`evidence/gate/gate-tally.txt`, produced by `evidence/gate/gate-tally.py` from the task XMLs it names;
      the per-module XMLs themselves are build output and are not archived, so the tally is the record);
      the guard inside the two `:app` flavor suites: `tests="2" failures="0"`, `sources=719 findings=0`
      (`evidence/gate/gate-app-mobile-QualifierUseSiteTargetTest.xml`, `2026-10-20T23:04:03.678Z`;
      `evidence/gate/gate-app-automotive-QualifierUseSiteTargetTest.xml`, `2026-10-20T23:05:51.651Z`).

      (An earlier gate run of the same command in round 1 — `BUILD SUCCESSFUL in 3m 40s`, same
      `tests=5704 failures=0` — is superseded twice over: its guard XMLs came from the 716-file surface, and
      later still from a test source that predates this revision. A run of the same command in round 2 on the
      pre-correction test source was discarded for the same reason and left no cited artefact: the gate quoted
      above is the only one this change claims.)
