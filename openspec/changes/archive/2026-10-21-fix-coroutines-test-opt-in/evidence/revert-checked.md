# Falsification of the new invariant (task 3.1)

Invariant: `unit-test-suite-runtime` — "A test source declares the opt-in its experimental time control
needs" (case `CoroutinesTestOptInGateTest.everyTestSourceThatUsesTheExperimentalTimeControlDeclaresTheOptIn`).

- **Mutation**: delete `@OptIn(ExperimentalCoroutinesApi::class)` from
  `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelRoadInfoTest.kt`, leaving its
  `import kotlinx.coroutines.ExperimentalCoroutinesApi` in place — the one spelling a marker-string check
  would miss.
- **Run**: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.test.CoroutinesTestOptInGateTest"`
  → `FAILED`, `expected:<[]> but was:<[app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelRoadInfoTest.kt]>`,
  `tests="6" failures="1"`, `2026-10-20T22:07:18.365Z` (`evidence/revert-check-mutated.xml`).
- **Restore**: the annotation back in place, same filtered run → `BUILD SUCCESSFUL`, `tests="6" failures="0"`,
  `scanned=441 missingOptIn=0` (`evidence/green-run-gate-test.xml`).

## What the first attempts taught (they are why the guard is shaped as it is)

- The **first** revert-check removed the annotation and the run **passed**: the detector then asked
  `source.contains("ExperimentalCoroutinesApi")`, which the leftover import satisfies. The marker test is now
  the applied annotation `@(?:file:)?OptIn\s*\([^)]*ExperimentalCoroutinesApi`, and
  `aTestSourceThatOnlyImportsTheMarkerIsStillNamed` asserts that case.
- The independent review then showed the marker scan ran on raw text, so an annotation mentioned in a comment
  or a fixture string would satisfy it. The detector now masks comments and string literals
  (`maskCommentsAndStrings`) and `aCommentedOutAnnotationDoesNotSatisfyTheRule` asserts it; the review also
  asked for the wildcard case, hence `aWildcardImportOfTheTestPackageIsNamed`.
