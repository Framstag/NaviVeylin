# Tasks — fix-client-java-coverage-floor

Each task names the command that shows it done. The module's own suite and report are the focused loop; the
gate runs once at the end and is not a retry.

1. **Read the module's own number on the base tree.**
   `./gradlew --offline :osmscout-client-java:jacocoTestReport`, then the last root `INSTRUCTION` counter of
   `osmscout-client-java/build/reports/jacoco/test/jacocoTestReport.xml`.
   Verification: `covered=1567 missed=2643 → 37.2 %`, `CLASS covered=13 missed=21`.

2. **Add the case for the class with the largest uncovered surface — the POI table.**
   `PoiCategoriesTest.everyCategoryMapsToAtLeastOneOsmType` plus its clone, its unmodifiable-map and its
   unknown-category cases.
   Verification: `./gradlew --offline :osmscout-client-java:test --tests
   'com.framstag.libosmscout.client.PoiCategoriesTest'`.

3. **Add the case for each remaining data carrier** — `LaneTurnTest`, `NavigationPositionTest`,
   `CurrentRoadInfoTest`, `RoadInfoTest`, `ObjectDescriptionTest`, `ClientValueTypesTest`,
   `TurnTypeAndInstructionTest`, `AvailableMapEntryTest`, `NavigationControllerTest`.
   Verification: the same `--tests` invocation over the nine classes; each class leaves the report at
   `INSTRUCTION 100 %` except `TurnType`/`RouteInstruction`, whose remaining misses are the JNI-only
   constructor paths.

4. **Add the case for each behavioural gap the base suite left** — `InstalledMapsTest` (the shared scan),
   `BasemapManagerInstalledTest` (`BasemapInfo`, size, installed test, delete, the no-update shortcut),
   `MapDownloadManagerListingTest` (listing URL, empty answer on a failing provider, a directory entry
   refused, a dying worker reported) and the five cases added to `BasemapManagerTest` (a successful install,
   a wrapped archive flattened, the listing probe, its failure and unreachable branches, the version-date
   names that encode no date).
   Verification: `./gradlew --offline :osmscout-client-java:test` → `106` cases, `0` failures (the base tree's
   tally is `48`).

5. **Measure the delta with the row's own arithmetic.**
   `./gradlew --offline :osmscout-client-java:test :osmscout-client-java:jacocoTestReport`, then
   `bash .pi/issue-loop/items/320/coverage-floor-probe.sh . <out.xml> "the module instruction coverage reaches
   the 70% floor"`.
   Verification: `LOOP-COVERAGE module=osmscout-client-java covered=3124 missed=1086 percentage=74.2
   floor=70 verdict=passed` (`evidence/green-on-change.xml`), against the base tree's
   `covered=1567 missed=2643 percentage=37.2 verdict=failed` (`evidence/red-on-base.xml`).

6. **Check the cited coordinates mechanically.**
   `bash tools/loop/evidence-check.sh openspec/changes/<this change> --root .`
   Verification: no dangling `file:line`, `tests=`, `/tmp` pointer or `§N`; the run's output is retained as
   `evidence/evidence-check.log`.

7. **Run the project's gate once.**
   `./tools/loop/gate.sh`
   Verification (round 1, before the corrections): one `LOOP-GATE: tests=703 failures=1 errors=0` line —
   the compiled step green (251 s, this change's module suite included) and one failing case that was the
   previous landing's, not this delta's: `adapter#theChildIsStartedWithTheResultBound` failed because a loop
   child carried `LOOP_TOOL_RESULT_TRIM_RECORD` in its environment and the case unset only
   `LOOP_CHILD_EVENTS` (`tools/loop-cases.sh:6668`). Attributed and reproduced alone (`1 of 1 cases failed`,
   the same case in the suite: `1 of 247`): `evidence/gate-attribution.md`, `evidence/selftest-rerun.log`.

## Corrections after review round 1

The review refused the change with three blocking findings (the report is quoted in
`review-round-1-requested.md`; `items/320/reviewer-report-1.md` is the same report). Each is answered here and
none of them adds a claim to the change.

8. **Narrow each delta scenario to what its named case asserts.** Six scenarios' THENs carried conjuncts
   whose only support was a sibling case the scenario did not name. The shipped requirement
   (`openspec/specs/test-coverage/spec.md`, the delta merged) now states, per scenario, exactly the
   assertions the named case makes — e.g. "A downloaded basemap is installed" no longer claims the
   wrapped-archive flattening that `testDownloadOfAWrappedArchiveFlattensTheSubdirectory` alone asserts.
   Verification: each of the nine scenarios names one case, and the six narrowed THENs name only what that
   case asserts (`openspec validate test-coverage --strict`, `tools/check-openspec-hygiene.sh` in the gate).

9. **State the requirement's contract, not the tools that measure it.** The body named
   `tools/build-inputs.sh`, `tools/sensors/sensors.json` and `tools/sensors/metrics/coverage.sh` and ran to
   851 characters, against the 500-character limit the shipped `documentation-ownership` requirement
   states. It now reads 418 characters and states the invariant only; the arithmetic stays documented where
   it already lives (`guidelines/Build.md`, "Code coverage").
   Verification: `openspec/specs/test-coverage/spec.md`, requirement "The pure-Java module's suite covers the
   classes it ships" — body 418 characters (its four body lines with their newlines), its scenarios excluded.

10. **Unset the trim record beside the stream in the hand-started child case.**
    `tools/loop-cases.sh`: `env -u LOOP_CHILD_EVENTS -u LOOP_TOOL_RESULT_TRIM_RECORD …`, so the case measures
    the adapter's silence rather than the record path the run exports into every child.
    Verification: the gate's own loop suite, `evidence/gate/tools_loop-selftest.sh_--from-git_4a4b3a95….xml`
    (`tests="247" failures="0"`), where round 1 had `1 of 247` fail.

11. **Re-run the project's gate once, after the corrections.**
    `./tools/loop/gate.sh`
    Verification: `LOOP-GATE: tests=703 failures=0 errors=0` (`evidence/gate-summary.log`, sum in
    `evidence/gate-summary.xml`; the compiled step 248 s). No second run.

12. **Carry the module's per-case results into the change.** The 21 `TEST-*.xml` the gate's compiled step
    wrote are copied to `evidence/module-test-results/` and the tally `evidence/module-suite-tally.txt` is
    derived from them, so the `106` cases, `0` failures and their timestamps survive the build directory.
    Verification: `grep -h '<testsuite ' evidence/module-test-results/*.xml` sums to `tests="106"
    failures="0"` over 21 files.

## Corrections after review round 2

14. **Record the harness hunk as part of the change, and carry the delta to the shipped text.** Round 2 found
    two blocking findings: the change's 14th file (`tools/loop-cases.sh`) stood in no recorded surface, and
    the archived delta still carried the 846-character tool-prescribing body and the six wide THENs round 1
    refused, while task 8 called the merged text "the delta merged". The owner decided to keep the hunk
    (option a): `proposal.md`'s surface names it, `design.md`'s blast radius and rollback count it, and the
    delta's requirement now reads exactly as the shipped one — deletions only, no claim, case or assertion
    added.
    Verification: the delta's requirement block and the shipped one are byte-identical (a `diff` of the two
    blocks is empty); `openspec validate test-coverage --strict`; `bash tools/loop/evidence-check.sh
    openspec/changes/archive/2026-10-27-fix-client-java-coverage-floor --root .`. Docs-only, so no build and no
    gate run (`guidelines/Build.md` §2a: an `openspec/**` delta compiles nothing), and the round-2 gate's
    `tests=703 failures=0` still describes this tree, whose only change since is prose.
