# Tasks — fix-auto-module-coverage-floor

The change touches test sources only, so the scope is the `auto` module's own suite and the coverage row's
arithmetic; the project's gate is nonetheless owed (`LOOP_GATE_COMPILED_PATTERNS` matches `*/src/*`) and is run
once in task 7.

- [x] 1. Measure the base tree's module coverage with the row's own arithmetic.
  Verification: `./gradlew --offline :auto:koverXmlReport -PforceTests` →
  `INSTRUCTION missed="10643" covered="23923"` = **69.2 %** (`evidence/base-auto-kover.log`,
  `evidence/base-auto-kover-report.xml`).

- [x] 2. Run the red case on the base tree.
  Verification: `bash evidence/coverage-floor-probe.sh evidence/base-auto-kover-report.xml auto 70
  evidence/red-on-base.xml` → exit 1, `covered=23923 missed=10643 percentage=69.2 floor=70 verdict=fail`;
  `evidence/red-on-base.xml` carries `tests="1" failures="1"` for
  `coverage-floor-probe#theAutoModulesInstructionCoverageReachesTheFloor`.

- [x] 3. Locate the missed instructions per class, not per module.
  Verification: the per-class instruction counters of `evidence/base-auto-kover-report.xml` (`NavigationSession`
  1685/0, `NavigationScreen` 1163/0, `FreeDrivingScreen` 863/0, `NavigationSession$2` 230/0,
  `FreeDrivingScreen$1` 80/0) — 4021 of 10643 missed instructions sit in three unconstructed screens;
  `evidence/kover-classes.py` is the command that printed that list.

- [x] 4. Construct the free-driving screen and assert the behaviours it owns.
  Verification: `./gradlew --offline :auto:testDebugUnitTest -PforceTests -PnoCoverage --tests
  "com.naviveylin.auto.FreeDrivingScreenHostTest"` → `BUILD SUCCESSFUL`, the class's XML
  `tests="5" skipped="0" failures="0" errors="0"` (`evidence/TEST-com.naviveylin.auto.FreeDrivingScreenHostTest.xml`).

- [x] 5. Re-measure the module with the same command and the same arithmetic.
  Verification: `./gradlew --offline :auto:koverXmlReport -PforceTests` →
  `INSTRUCTION missed="9502" covered="25064"` = **72.5 %** (`evidence/change-auto-kover.log`,
  `evidence/change-auto-kover-report.xml`); `FreeDrivingScreen` 0 → 583 covered; the probe green —
  `bash evidence/coverage-floor-probe.sh evidence/change-auto-kover-report.xml auto 70
  evidence/green-on-change.xml` → exit 0, `tests="1" failures="0"`.

- [x] 6. Count the module suite the change leaves behind.
  Verification: `python3 tally.py` over `auto/build/test-results/testDebugUnitTest/TEST-*.xml` →
  `classes=92 tests=885 failures=0 errors=0 skipped=0` (`evidence/module-suite-tally.txt`).

- [x] 7. Run the project's gate once and copy its tallies into `evidence/`.
  Verification: `./tools/loop/gate.sh` from the change root; its aggregate line and each step's tally are copied
  into `evidence/gate.log` and `evidence/gate-tallies/`.

- [x] 8. Copy every cited XML into the change's `evidence/` and check the pointers mechanically.
  Verification: `bash tools/loop/evidence-check.sh
  openspec/changes/2026-10-27-fix-auto-module-coverage-floor --root .` → `PASS`.

- [x] 9. Archive the change: moved under
  `openspec/changes/archive/2026-10-27-fix-auto-module-coverage-floor/` with its delta merged into
  `openspec/specs/test-coverage/spec.md`.
