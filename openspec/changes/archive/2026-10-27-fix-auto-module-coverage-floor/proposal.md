## Why

Item #319 (`tools/sensors/sensors.json:59-70`, the `coverage` row: floor 70, `comparison: below-floor`,
fingerprint field `module`) filed the condition *"the auto module's coverage is below its floor"* with the
measurement `69.2`. Verified on this tree (base commit `6f61cffe4f82`), with the row's own arithmetic
(`tools/build-inputs.sh:91-114` — the *last* `INSTRUCTION` counter of the module's Kover report, which is the
input `tools/sensors/metrics/coverage.sh` reads through `app/build/reports/jacoco/coverage.json`):

- `./gradlew --offline :auto:koverXmlReport -PforceTests` on the base tree wrote
  `auto/build/reports/kover/report.xml` with `<counter type="INSTRUCTION" missed="10643" covered="23923"/>` —
  **23923 / 34566 = 69.2 %** (`evidence/base-auto-kover.log`, `evidence/base-auto-kover-report.xml`). The
  condition still holds: the module is below its 70 % floor, at the number the row reported.
- The red case, `coverage-floor-probe#theAutoModulesInstructionCoverageReachesTheFloor`, fails on that report:
  `XML tests=1 failures=1`, `covered=23923 missed=10643 percentage=69.2 floor=70 verdict=fail`
  (`evidence/red-on-base.xml`, produced by `evidence/coverage-floor-probe.sh`).

**Root cause, one mechanism:** the module's suite never constructs three of the screens the module compiles, so
every instruction of them is counted as missed by the report the row reads. Per class, on the base report:

| class | missed | covered |
|---|---|---|
| `com/naviveylin/auto/NavigationSession` | 1685 | 0 |
| `com/naviveylin/auto/NavigationSession$2` | 230 | 0 |
| `com/naviveylin/auto/NavigationScreen` | 1163 | 0 |
| `com/naviveylin/auto/FreeDrivingScreen` | 863 | 0 |
| `com/naviveylin/auto/FreeDrivingScreen$1` | 80 | 0 |

4021 of the module's 10643 missed instructions sit in those three screens and their inner classes, and
`grep -rl 'FreeDrivingScreen(\|NavigationScreen(\|NavigationSession(' auto/src/test
--exclude='FreeDrivingScreenHostTest.kt'` finds no case that constructs any of them. The helpers those screens
were split into *are* covered (`FreeDrivingScreenObservations$start$…` reports 0 missed), so the gap is the screen
classes themselves and not their wiring.

The owning capability is `test-coverage` (its purpose: the reports "give the project visibility into test
coverage" — the row reads them). The owning guideline section is `guidelines/Build.md` section 7 "Code coverage" (the
per-module report, the opt-out that deletes execution data, the baseline); a case that builds a screen with
long-lived background work owes `guidelines/Build.md` section 6b's teardown discipline.

## Surface

surface: 1 test class added, 5 cases added, 0 production files, 0 fixtures, 0 build files — measured before
the plan was fixed:

- `find auto/src/test -name '*Test.kt' | wc -l` → **92** test classes in the module: the change adds one, and
  none of them constructed `FreeDrivingScreen`, `NavigationScreen` or `NavigationSession`
  (`grep -rl … --exclude='FreeDrivingScreenHostTest.kt' | wc -l` → **0**).
- `grep -c '^#### Scenario:' openspec/specs/test-coverage/spec.md` → **12** existing scenarios, none of which
  asserts a module's suite reaching the row's floor.
- `find auto/src/main -name '*.kt' | wc -l` → **77** production files; the three unconstructed screens are 3452
  lines of them (`FreeDrivingScreen.kt` 963, `NavigationScreen.kt` 1082, `NavigationSession.kt` 1407), so the
  delta is bounded to the *test* side and no production file moves.

## What Changes

- **Added** a `test-coverage` requirement: a module's suite reaches the classes a host test can reach, so the
  instruction coverage the coverage row reads stays at or above that row's floor.
- **Added** `auto/src/test/java/com/naviveylin/auto/FreeDrivingScreenHostTest.kt`: five cases that construct the
  free-driving screen and assert the template it builds, the session surface it takes while started and releases
  when stopped, the fixes and map-data revisions it observes, the free-driving vote it casts on entry, and the
  road status it drops on destroy.

Measured on the change, same command, same arithmetic: `INSTRUCTION missed="9502" covered="25064"` —
**25064 / 34566 = 72.5 %** (`evidence/change-auto-kover.log`, `evidence/change-auto-kover-report.xml`), and the
probe green (`evidence/green-on-change.xml`, `XML tests=1 failures=0`). `FreeDrivingScreen` itself goes from
0/863 to 583/863 covered.

## Impact

- Affected spec: `test-coverage` (one added requirement, six scenarios — the probe and the five cases).
- Affected code: `auto/src/test/java/com/naviveylin/auto/FreeDrivingScreenHostTest.kt` only. No production file,
  no fixture, no build script, no gradle property, no ledger entry.
- The module suite: 92 classes / 885 cases / 0 failures (`python3 evidence/tally.py` over
  `auto/build/test-results/testDebugUnitTest/TEST-*.xml`, `evidence/module-suite-tally.txt`); the change adds one
  class of five cases.
