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
   Verification: no dangling `file:line`, `tests=`, `/tmp` pointer or `§N`.

7. **Run the project's gate once.**
   `./tools/loop/gate.sh`
   Verification: one `LOOP-GATE: tests=703 failures=1 errors=0` line — the compiled step green (251 s, this
   change's module suite included) and one failing case that is the previous landing's, not this delta's:
   `adapter#theChildIsStartedWithTheResultBound` fails because a loop child carries
   `LOOP_TOOL_RESULT_TRIM_RECORD` in its environment and that case unsets only `LOOP_CHILD_EVENTS`
   (`tools/loop-cases.sh:6668`). Attributed, reproduced alone (`1 of 1 cases failed`) and re-run as a suite
   for the attribution only (`1 of 247`, the same case): `evidence/gate-attribution.md`,
   `evidence/gate-loop-selftest-tally.xml`. No second gate run.
