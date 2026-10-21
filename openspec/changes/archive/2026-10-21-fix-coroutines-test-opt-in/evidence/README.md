# Evidence — fix-coroutines-test-opt-in

Every file here was produced in the change root (`.pi/worktrees/loop`) on base commit
`3211d4d8b395c61041dfccff9330e754dfb3499b`, on 2026-10-20/21.

| file | what it is |
|---|---|
| `head-compile-warnings.log` | the four unit-test compile tasks on HEAD: 109 `^w: ` lines, 91 of the opt-in family, per-file counts as the issue states them |
| `red-run-gate-test.xml` | the guard case on HEAD (the nine files unannotated, guard test only): `tests="6" failures="1"`, failure names the nine files, `scanned=441 missingOptIn=9`, `2026-10-20T22:07:09.446Z` |
| `green-run-gate-test.xml` | the same six cases after the nine declarations: `tests="6" failures="0"`, `scanned=441 missingOptIn=0` |
| `revert-check-mutated.xml` | the revert-check's mutation (`@OptIn` removed from `MapCanvasViewModelRoadInfoTest.kt`, import left in place): `tests="4" failures="1"`, the file named |
| `revert-checked.md` | the revert-check record, including the first attempt that passed and what it changed |
| `post-fix-compile-warnings.log` | the same four compile tasks after the fix: 18 `^w: ` lines, 0 of the opt-in family |
| `gate/gate-tally.txt` | the gate run's per-module tally |
| `gate/gate-app-mobile-CoroutinesTestOptInGateTest.xml`, `gate/gate-app-automotive-CoroutinesTestOptInGateTest.xml` | the guard case inside the two `:app` flavor suites of the gate run (`tests="4"`: the revision that existed when the gate ran; the review that followed added two self-test cases and comment/string masking to that file only, and the final revision is the `tests="6"` XMLs above) |

The four self-test cases (`aTestSourceThatDeclaresTheOptInIsNotNamed`,
`aTestSourceThatOnlyImportsTheMarkerIsStillNamed`, `aCommentedOutAnnotationDoesNotSatisfyTheRule`,
`aWildcardImportOfTheTestPackageIsNamed`) pass in the red run too: they falsify the detector, not the tree.
Only `everyTestSourceThatUsesTheExperimentalTimeControlDeclaresTheOptIn` moves from red to green.

The red and green runs of the guard are the filtered invocation
`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.test.CoroutinesTestOptInGateTest"`, which is a
pre-gate run, not gate evidence; the gate is `./gradlew test -PforceTests --no-build-cache`, run once.
