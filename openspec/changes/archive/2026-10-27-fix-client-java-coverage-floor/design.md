# Design — fix-client-java-coverage-floor

## Root cause

The module's suite was written for the five local overrides and for three of the submodule's classes, so 21
of the 34 classes the module compiles were loaded by no case: the module's instruction coverage was
`1567/4210 = 37.2 %` while the coverage row's floor is 70 %.

## The single fix

One case per reachable class, in the class's own test class, asserting the behaviour the class exists for —
not a getter walk: the POI table's clone and its unmodifiable view, the lane-turn ids against the native
enum, the empty-string substitution the UI reads without a null check, the display string's join rule, the
`hasInfo` decision, the unmodifiable entry list, the favourites' empty collections, the two spellings the
bridge sends, the entry search, the `types.dat` scan, the installed basemap's description and delete, the
listing URL's placeholders, the empty answer a failing provider yields, and a dying worker being reported
through the listener. The classes the host JVM cannot reach — `OSMScoutClient`, `OSMScoutClientBuilder` and
`RouteCallback`, which load the native library in a static initializer, and the JNI half of
`MapDownloadManager` — stay uncovered: the stub the project keeps for them lives in `app/src/test/jniLibs` and
`auto/src/test/jniLibs` (`guidelines/Build.md`, "The JNI stub for unit tests"), and reaching it from this
module's test JVM would move
that rule rather than test the module. The cases that touch that surface assert a declaration or a
delegation instead of executing native code, as `NavigationControllerTest` does with the substitutable
controller's superclass, exactly as `MapDownloadManagerRegisterTest` and
`OSMScoutClientFavoriteFileFormatApiTest` already did.

## Blast radius

- Test sources only: 13 new files under `osmscout-client-java/src/test/java/com/framstag/libosmscout/client/`
  and 5 cases added to `BasemapManagerTest`; nothing under `src/main`, no build file, no fixture, no
  guideline sentence.
- The module's suite grows from 48 to 106 cases (`evidence/module-suite-tally.txt`, derived from the 21
  `evidence/module-test-results/TEST-*.xml` the gate wrote; the base's 48 is the round-1 review's count of the
  base copy's 8 class results, `items/320/reviewer-report-1.md`); the four cases that
  wait for a worker are bounded by a `CountDownLatch` await of 10 s and none sleeps or polls a clock
  (`guidelines/Build.md`, "Wall-clock waits and the conversion measured"), exactly as the suite's existing
  download cases do.
- The compiled gate (`./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`, `*/src/*` is a compiled
  pattern in `tools/loop/loop.conf`) runs the module's task too, so the change is verified by the gate the
  project declares, not by a private command.

## Rollback

Revert the change's diff: 13 test files and two hunks of `BasemapManagerTest` disappear, and the module's
coverage returns to 37.2 %. Nothing else in the tree depends on them — no production file, no spec that
names a case other than the scenarios this change adds.

## Alternatives the evidence eliminated

- **Lower the floor, or narrow the report to the module's five local overrides.** Eliminated by measurement:
  the overrides alone carry `1075/2558 = 42 %` of their own instructions, so excluding the submodule's
  classes leaves the module below the same floor. The alternative is not cheaper — it is the same 37 % with
  a smaller denominator, and it would hide the submodule's classes the app does call — `NavigationState`,
  `NavigationPosition`, `RouteInstruction` and `TurnType` are on the navigation path — behind an exclusion
  that no reader of the report can see.
- **Attach the committed JNI stub to this module's test JVM and instantiate `OSMScoutClient`.** Eliminated by
  the rule, not by cost: that section places the stub in the app and auto test source sets because AGP puts each
  module's own `src/test/jniLibs` on `java.library.path`; a plain `java-library` module reaching into
  `app/src/test/jniLibs` moves that boundary and duplicates `FakeOSMScoutClient`'s job one module down. The
  classes it would reach are 326 of 4210 instructions (7.7 %), and the delta does not need them: the module
  reaches the floor without them.
- **A Gradle coverage gate (`jacocoTestCoverageVerification`).** Eliminated by a recorded project decision:
  `guidelines/Build.md`, "Code coverage" states coverage is report-only, and the CI scenario in
  `openspec/specs/test-coverage/spec.md` ("CI stays green without thresholds") makes a failing build on a
  percentage a spec violation. The floor is enforced where it already lives — the sensor row that filed the
  item — and the red case reproduces that row's arithmetic rather than adding a second gate.
- **A JUnit case inside the module that asserts its own coverage.** Impossible, not merely unchosen: JaCoCo
  writes its execution data when the test JVM exits and `jacocoTestReport` reads it afterwards, so no case in
  the suite can read the coverage of the run it belongs to. The case is therefore the probe
  (`evidence/coverage-floor-probe.sh`) over the report, and the report it reads is copied into `evidence/`.
