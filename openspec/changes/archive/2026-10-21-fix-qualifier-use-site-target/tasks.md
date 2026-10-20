# Tasks — fix-qualifier-use-site-target

## 1. The guard, written first

- [x] 1.1 Write `app/src/test/java/com/naviveylin/build/QualifierUseSiteTargetTest.kt`: the detector
      (`@(ApplicationContext|ActivityContext|Named)` applied to a `val`/`var` with no `:target`, comments
      skipped), the real-tree case `no qualifier on an injected property relies on the implicit target` over
      the six Kotlin source roots of `app`, `auto` and `core`, and the detector case
      `the implicit target is still detectable` — spec `build-test-gate`, "An injected property states its
      qualifier's use-site target", scenarios 1 and 2.
      *Verification*: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.build.QualifierUseSiteTargetTest" -PforceTests --no-build-cache` → the real-tree case
      fails naming `NavigationNotificationController.kt:36` with `sources=716 findings=1`, the detector case
      passes (`tests=2 failures=1`, XML `evidence/revert-check-mutated.xml` — the revert check's mutation
      restores the HEAD line, so it is the red run of the final revision; log `items/220/red-run-final.log`).

- [x] 1.2 Record the warning the red case stands in for: compile both `:app` main source sets with warnings on
      and count the family.
      *Verification*: `./gradlew :app:compileMobileDebugKotlin :app:compileAutomotiveDebugKotlin
      --rerun-tasks --no-build-cache -Pkotlin.incremental=false` → exactly 2
      `value parameter only … also be applied to field` lines, both at
      `NavigationNotificationController.kt:36:5` (log `evidence/head-compile-warnings.log`).

## 2. The statement

- [x] 2.1 `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt:36`:
      `@ApplicationContext` → `@param:ApplicationContext`.
      *Verification*: the same filtered case turns green (`tests=2 failures=0`, `sources=716 findings=0`,
      `evidence/green-run-test.xml`), and the compile of both `:app` flavors prints 0 lines of the family
      (log `items/220/green-run.log` and the gate log `evidence/gate/gate-console.log`).

- [x] 2.2 Prove the annotation still has the effect it had: `javap -v -p` the compiled class in both states.
      *Verification*: `RuntimeVisibleParameterAnnotations: parameter 0:
      dagger.hilt.android.qualifiers.ApplicationContext` in both, and `diff` of the two `javap` dumps after
      the classfile header is empty (`evidence/javap-constructor-bare.txt`,
      `evidence/javap-constructor-param.txt`, `evidence/javap-diff.txt`).

## 3. Falsification of the new invariant

- [x] 3.1 Revert-check (`revert-check` skill): restore the bare `@ApplicationContext`, run the filtered case
      — it must fail and name the file — then restore `@param:` and re-run forced green.
      *Verification*: the mutated run prints the failure naming
      `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt`, XML
      `evidence/revert-check-mutated.xml` (`tests=2 failures=1`, `2026-10-20T22:46:46.741Z`); the restored
      run's XML is `evidence/green-run-test.xml` (`tests=2 failures=0`, `2026-10-20T22:46:53.028Z`).

## 4. Owning documents

- [x] 4.1 `guidelines/Build.md` §4 "Result evaluation": the "Warnings" bullet records the measured site, the
      2-lines-from-2-flavors count, and names `QualifierUseSiteTargetTest` as the guard.
      *Verification*: the bullet names the case and the count; `./gradlew test` is unaffected (docs only).

- [x] 4.2 The spec delta `openspec/changes/2026-10-21-fix-qualifier-use-site-target/specs/build-test-gate/spec.md`
      carries both scenarios, each naming exactly one case of task 1.1.
      *Verification*: `openspec validate 2026-10-21-fix-qualifier-use-site-target --strict`.

## 5. The gate, once

- [x] 5.1 Run the project's gate exactly as the run's configuration states it, once.
      *Verification*: `./gradlew test -PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 3m 40s`,
      189 tasks (25 executed), `tests=5704 failures=0 errors=0` over the five suites — per-module tally in
      `evidence/gate/gate-tally.txt`, the guard case's two flavor XMLs in `evidence/gate/`. (An earlier gate
      run of the same command on 2026-10-20 passed at 3m 39s, `tests=5704 failures=0`; it is superseded
      because the guard's scan surface was narrowed from every `*.kt` under `app/`, `auto/`, `core/` to the
      six source sets after it, so this run is the one that matches the archived tree.)
