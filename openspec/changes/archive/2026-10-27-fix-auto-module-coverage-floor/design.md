## Why

Item #319 filed the `auto` module's coverage condition against the sensor registry's `coverage` row
(`tools/sensors/sensors.json:59-70`: floor 70, `below-floor`, fingerprint `module`). The row reads
`app/build/reports/jacoco/coverage.json`, which `tools/build-inputs.sh` writes from each module's own Kover XML
(`auto/build/reports/kover/report.xml`) with the arithmetic in `tools/build-inputs.sh:91-114` — the *last*
instruction counter of the document. `guidelines/Build.md` section 7 owns that report: its per-module outputs, the agent
opt-out that deletes execution data, and the fact that coverage is report-only in the build.

## Root cause

One mechanism: the module's suite never constructs three of the screens the module compiles, so the report counts
every instruction of them as missed. `NavigationSession` (1685 missed / 0 covered), `NavigationScreen` (1163 / 0)
and `FreeDrivingScreen` (863 / 0), plus their inner classes (`NavigationSession$2` 230 / 0,
`FreeDrivingScreen$1` 80 / 0), hold 4021 of the module's 10643 missed instructions, and no case in the module's
92 test classes constructs any of them (`grep -rl 'FreeDrivingScreen(\|NavigationScreen(\|NavigationSession('
auto/src/test --exclude='FreeDrivingScreenHostTest.kt'` → 0 files). The parts those screens were split into are
covered already (`FreeDrivingScreenObservations$start$1$1`: 0 missed / 45 covered), which is why the gap sat in
the screens and not in the helpers.

## The single fix

Construct the smallest of the three screens and assert the behaviours it owns, in one new test class
(`auto/src/test/java/com/naviveylin/auto/FreeDrivingScreenHostTest.kt`, five cases). The screen is the smallest
of the three (863 instructions against 1163 and 1685) and its collaborators are already faked for the module
(`FakeMapScreenClient`, `testCarContext()`, `MainDispatcherRule`, a mocked `AutoEntryPoint` through
`EntryPoints.get` — the pattern `DetailsScreenTest` established), so no fixture is added.

Measured: `FreeDrivingScreen` 0 → 583 covered, module 23923 → 25064 of 34566 (69.2 % → 72.5 %), the probe green on
the change and red on the base (`evidence/red-on-base.xml`, `evidence/green-on-change.xml`). The margin above the
floor is 2.5 points — 867 instructions — so the next uncovered regression still has room before the row files
again.

## Blast radius

One test file. No production file, no fixture, no build script, no gradle property, no mutation record, no ledger
entry. The module suite grows by one class and five cases (91 → 92 classes, 880 → 885 cases, 0 failures).

## Alternatives the evidence eliminated

- **Lower the row's floor to the measurement.** The floor is the project's declared bound and the report's
  baseline lives in `guidelines/Build.md` section 7; fitting the bound to the number inverts the check and needs a
  registry change plus its mutations. Eliminated as a fix for the module's coverage.
- **Cover `NavigationSession` instead, because it is the largest gap.** 1685 uncovered instructions against 863,
  and it needs a session harness (CarContext, screen stack, Hilt entry points, coroutine state machine) that no
  case in the module has. Eliminated on cost, not on principle — it is the natural next step if the screen margin
  is ever spent.
- **Wait for the renderer and assert the drawn frame.** The renderer is built off the main thread
  (`Dispatchers.Default`), so a case that awaits it measures the host's thread pool rather than the screen, and
  the module's own wall-clock rule forbids the wait. The screen's host-facing behaviour is asserted instead, and
  the renderer keeps its own cases (`AutoMapRenderer*Test`).
- **Test the screens only through the extracted helpers.** Already true of the module: the helpers report 0
  missed while the screens report 3711 missed. Eliminated by the report itself.
- **Add a `koverVerify` threshold gate.** `guidelines/Build.md` section 7 says coverage is report-only and the
  `test-coverage` capability's "CI stays green without thresholds" scenario pins it; a gate is a different change
  and a design fork this item cannot take.

## Rollback

`git rm auto/src/test/java/com/naviveylin/auto/FreeDrivingScreenHostTest.kt`. Nothing else moved: the shipped spec
gains the requirement, the change's own record and its evidence are the only other additions.
