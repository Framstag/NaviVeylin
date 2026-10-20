# Design — test sources declare the experimental coroutines-test opt-in

## Root cause

At `kotlinx-coroutines-test:1.9.0` (`app/build.gradle.kts:1542`, `auto/build.gradle.kts:170`,
`core/build.gradle.kts:158`) the `TestScope` extensions `advanceUntilIdle`, `advanceTimeBy` and `runCurrent`
are annotated `@ExperimentalCoroutinesApi`, while the same-named members of `TestCoroutineScheduler` are
stable. Nine test classes import the extensions and call them inside `runTest` bodies without any
`@OptIn(ExperimentalCoroutinesApi::class)` in the file, so Kotlin reports the missing opt-in once per call
site: 91 warnings over those nine files, out of 109 in the four unit-test compile tasks
(`logs/head-compile-warnings.log`).

One mechanism, not two. The same build's other 18 warnings are a different family — deprecated Robolectric
shadows, an unchecked cast, an inferred-nullability mismatch — and are tracked by their own items (#219, #221,
#222); nothing in them shares this cause.

Why the other files that name the same extension are silent, measured: 79 test files import
`kotlinx.coroutines.test.advanceUntilIdle`, and 70 of them already declare the opt-in. The files that reach
the time control through the *stable* member instead — `mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()`
in `MapCanvasViewModelStyleTest`, `LicenseInventoryTest`, `MapCanvasViewModelSpeedWidgetTest` and others —
import nothing experimental, so they neither warn nor need the opt-in.

## The single fix

Two mechanical edits and one case:

1. In each of the nine classes, add `import kotlinx.coroutines.ExperimentalCoroutinesApi` and
   `@OptIn(ExperimentalCoroutinesApi::class)` on the class — the shape 70 files in the same source sets
   already use. No per-call-site judgement, no `@Suppress`.
2. `app/src/test/java/com/naviveylin/test/CoroutinesTestOptInGateTest.kt` — six cases:
   - `everyTestSourceThatUsesTheExperimentalTimeControlDeclaresTheOptIn()` walks
     `app/src/test/java`, `auto/src/test/java`, `core/src/test/java` and asserts the findings are empty. The
     finding set before the fix is exactly the nine files, measured (red run, `evidence/`).
   - `aTestSourceThatUsesTheExperimentalTimeControlWithoutTheOptInIsNamed()` runs the same detector over a
     fixture source string and asserts the file is named, so the guard cannot pass by detecting nothing.
   - `aTestSourceThatDeclaresTheOptInIsNotNamed()` asserts the marker satisfies the rule, so a marker spelling
     change cannot make the guard vacuous.
   - `aTestSourceThatOnlyImportsTheMarkerIsStillNamed()` asserts an *import* of `ExperimentalCoroutinesApi` is
     not the annotation — the spelling a marker-string check misses.
   - `aCommentedOutAnnotationDoesNotSatisfyTheRule()` asserts a comment that mentions the annotation does not
     either; the detector masks comments and string literals before scanning.
   - `aWildcardImportOfTheTestPackageIsNamed()` asserts `import kotlinx.coroutines.test.*` is a finding, because
     an import-level rule cannot see which names a wildcard brings in.
3. The red run is the real-tree case only: the other five cases are the guard's own falsification, and they
   pass before and after the nine declarations.

The detector's rule, and why it is import-level: a file that imports a `kotlinx.coroutines.test` declaration
outside the stable set `{runTest, TestScope, TestDispatcher, StandardTestDispatcher, TestCoroutineScheduler}`
must contain `ExperimentalCoroutinesApi` (its `@OptIn` or `@file:OptIn`). Import level is chosen because the
experimental and the stable declaration share the name `advanceUntilIdle`, so a call-shape rule would have to
resolve receivers in a regex. The rule is monotone in the safe direction: a declaration the pinned library
*stabilises* later makes the case demand an annotation that is no longer needed (a visible, one-line fix),
while a *new* experimental declaration is caught, because an unknown name is not in the stable set.

## Blast radius

Nine test files (one annotation and one import each), one new test file, the spec delta. No production
code, no build file, no manifest, no native source, no tracker write. No threading, lifecycle or dispatcher
change: a class-level opt-in asserts at compile time only, and the guard reads files on the calling thread
before any coroutine runs.

## Rollback

Revert the change commit. The only requirement touched is ADDED, so the previous contract is restored without
a compensating delta.

## What the evidence eliminated

- **Turn this family into a compile error** (`allWarningsAsErrors`, or a per-diagnostic error): impossible
  without taking the other families too. The same four tasks print 18 other warnings (deprecated
  `ShadowSensorManager`, `isBroadcastIntent`, `field defaults`, `Unchecked cast`, `Condition is always
  'true'`, the final-type parameter, `quadraticBezierTo`, the annotation-target migration, and a nullable
  receiver), and Kotlin offers no selection of a single diagnostic to promote. Those families are open items
  #219, #221 and #222, not this one, so promoting them here would either fail the build on warnings this
  change does not fix or expand the change to four families.
- **Have the guard read the compiler's warning output**: impossible from a JUnit case, and the run's gate
  (`./gradlew test -PforceTests --no-build-cache`) captures no warning log — a guard that reads a log file
  written by some other invocation is the "warning nobody reads" the issue rejects.
- **Put the opt-in once into a shared test rule the nine files already share**: measured — they share none.
  `:auto`'s files use `com.naviveylin.auto.MainDispatcherRule`, `:app`'s use
  `com.naviveylin.test.MainDispatcherRule`, and neither type's scope encloses a `runTest` body, so a rule
  cannot carry the opt-in for `advanceTimeBy`/`runCurrent` calls made in the test method. The two rules are
  also in different modules, so "one shared rule" would be two edits and still not cover the call sites.
- **Guard the call shape** (`advanceUntilIdle(` not preceded by `.scheduler`): impossible to make correct
  without receiver resolution — `TestCoroutineScheduler.advanceUntilIdle()` is stable and
  `TestScope.advanceUntilIdle()` is experimental, same name — and it would have to model `runTest`'s receiver
  chain, which the import already states. The tree has both spellings (`MapCanvasViewModelStyleTest` calls the
  scheduler member at 27 sites and must stay unflagged), so the call-shape rule would need a receiver model,
  while the import names the declaration unambiguously.
- **`@Suppress("OPT_IN_USAGE")` instead of the declaration**: eliminated — it silences the contract rather
  than satisfying it, and 70 files in these source sets show the project's convention.
- **Fix the nine files and leave the recurrence**: eliminated by the issue's own acceptance — without a
  case, the tenth file is silent again, which is how 91 call sites accumulated.
- **A buildSrc scanner plus a `preBuild`-wired Gradle check** (the mechanism the wall-clock requirement uses):
  eliminated for *this* requirement, because a case that must fail on HEAD cannot depend on a class that does
  not exist yet, and because the run's gate task does not execute a `preBuild`-wired task, so the guard's red
  and green runs would both be unobservable. The precedent that does fit exists and is in this repository:
  `ShippedSpecClassificationTest` reads repo artefacts from an `:app` case with private helpers.

## Verification

- Red on HEAD-with-test-only: `everyTestSourceThatUsesTheExperimentalTimeControlDeclaresTheOptIn` fails
  naming the nine files, `tests="6" failures="1"`, `scanned=441 missingOptIn=9` (XML in `evidence/`).
- Green after the fix, with the same six cases: `tests="6" failures="0"`, `scanned=441 missingOptIn=0`.
- Recompile the four unit-test compile tasks: the opt-in family drops from 91 to 0 while the total drops from
  109 to 18 (`evidence/`), so the fix removed exactly its family and touched no other.
- Revert-check: remove the `@OptIn` line from `MapCanvasViewModelRoadInfoTest.kt`, the real-tree case must
  fail and name it; restore; forced green run.
- The gate `./gradlew test -PforceTests --no-build-cache`, once: 2076/2076 `:app` mobile, 2076/2076 `:app`
  automotive, 866 `:auto`, 642 `:core`, 48 `:osmscout-client-java`, 0 failures and 0 errors everywhere. The
  gate ran while the guard had four cases; the review that followed added two self-test cases and
  comment/string masking to the guard *file* only, and that revision is covered by the filtered runs
  `tests="6" failures="0"` — no compiled production source changed after the gate.
