# Evidence — motorway-destination-hint

Every number below is carried by a JUnit XML in this directory; the gate's transcript is `gate.log` in the
same directory. Nothing here was re-derived in the tail: the gate ran once, in the gate child, and the
`LOOP-GATE` line it printed is quoted in the item's `.pi/issue-loop/items/806/worker-gate-report.md`.

## Part 1 — The gate, run once

`./tools/loop/gate.sh` in the change root, transcript `gate.log` (164 kB: the shell steps and the Gradle step in
one stream). Its verdict line summed 866 cases across the gate's suites and reported **no failures and one
error**; the error is the Gradle step `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`, whose own
tally is `gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`:

| step | tally | time |
|---|---|---|
| `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` | tests="0" failures="0" errors="1" | 267 s |

(Gradle's tally reports the step, not its cases — which is why the per-suite XMLs in part 3 are the numbers this
change is read on.)

Every other step of the gate passed: the loop self-tests, the mutation checks, the doc/hygiene/portability
steps (their names and durations are `gate.log`'s own `gate:` lines).

**The gate was not re-run, and no step of it was retried.**

## Part 2 — The one red, attributed to work this change does not touch

Read mechanically with `tools/gate-attribute.sh gate.log`:

```
step: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache
case: MapManagerSourceSwitchPlanTest > aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered
error: java.lang.NoClassDefFoundError: Could not initialize class sun.security.ssl.NamedGroup$SupportedGroups
flake: not recorded — this tree keeps no flake list
rerun: ./gradlew :app:testMobileDebugUnitTest --tests "MapManagerSourceSwitchPlanTest" -PforceTests -PjvmTestsOnly --no-build-cache
```

- **Not on this change's paths.** The change touches 14 files (`git show --stat b012c1ff`): `:core`'s
  `Manoeuvre.kt`, `ManoeuvreLabels.kt` and the two `strings.xml`, the app's `NextTurnOverlay.kt`,
  `RouteStepValues.kt` and `NavigationNotificationContent.kt`, the auto module's `NavigationTemplateMapper.kt`,
  and six test files. The red class
  `app/src/test/java/com/naviveylin/ui/mapmanager/MapManagerSourceSwitchPlanTest.kt` is none of them, and
  neither are the frames that raised it — `HttpUrlFetcher`, `RepositorySource.availableRegions`,
  `MapManagerViewModel.refreshRepositoryRegions`.
- **The mechanism is the JVM's TLS initialisation, not this change's code.** The throwable is an
  `NoClassDefFoundError` for `sun.security.ssl.NamedGroup$SupportedGroups`, raised while a real
  `HttpsURLConnection` initialises its `SSLConfiguration` on the class's network path
  (`RepositorySource.availableRegions`, the region-list fetch). The same case passes in the `automotive`
  flavour of the same run — `TEST-automotive-com.naviveylin.ui.mapmanager.MapManagerSourceSwitchPlanTest.xml`,
  tests="9" failures="0" — so the class's own logic is green; only the mobile flavour's environment fails to
  initialise that JDK class.
- **It was red before this work.** The same case with the same `NoClassDefFoundError` is recorded in three
  earlier archived changes of this tree:
  `openspec/changes/archive/2026-10-27-fix-the-tail-is-admitted-on-the-cheapest-measured-outcome/evidence/gate-attribution.md`
  (which also records that the class passed when rerun alone), the round-2 tally
  `openspec/changes/archive/2026-10-21-fix-follow-blit-anchor-call-site/evidence/gate/gate-tally-round2.txt`,
  and `openspec/changes/archive/2026-10-18-fix-parked-state-race/report.md`, which names it a local JDK
  artifact. It is a recurring residual of this tree's mobile unit-test run, owned by the map-source area.
- **It stays red after this work, for the same reason**: the diff adds no network path, no JDK class and no
  change to the map-source code, and the automotive flavour's green is the same class's logic on a run that
  did not take that initialisation path.

So the red is named here rather than repaired: the affected cases — every suite this change edits — are green
in the same gate run (part 3), and the residual belongs to the map-source area, not to this delta.

## Part 3 — The change's own results, as the gate produced them

Fresh from this gate run, in this directory:

| carrier | tally | timestamp |
|---|---|---|
| `TEST-com.naviveylin.service.CarHintContentTest.xml` (mobile) | tests="8" failures="0" | 2026-11-06T21:39:47.347Z |
| `TEST-automotive-com.naviveylin.service.CarHintContentTest.xml` | tests="8" failures="0" | 2026-11-06T21:37:39.533Z |
| `TEST-com.naviveylin.ui.navigation.NextTurnOverlayTest.xml` (mobile) | tests="13" failures="0" | 2026-11-06T21:40:48.318Z |
| `TEST-automotive-com.naviveylin.ui.navigation.NextTurnOverlayTest.xml` | tests="13" failures="0" | 2026-11-06T21:38:51.998Z |
| `TEST-com.naviveylin.ui.route.RoutePanelDiagnosticsTest.xml` (mobile) | tests="5" failures="0" | 2026-11-06T21:41:03.795Z |
| `TEST-com.naviveylin.core.ManeuverFromStepTest.xml` | tests="12" failures="0" | 2026-11-06T21:36:58.858Z |
| `TEST-com.naviveylin.core.ManeuverLabelTest.xml` | tests="17" failures="0" | 2026-11-06T21:36:58.877Z |
| `TEST-com.naviveylin.auto.NavigationTemplateMapperTest.xml` | tests="71" failures="0" | 2026-11-06T21:37:45.208Z |

The one red beside them: `TEST-com.naviveylin.ui.mapmanager.MapManagerSourceSwitchPlanTest.xml` (mobile),
tests="9" failures="1", 2026-11-06T21:40:56.699Z — part 2's case, kept here because it is what the gate's
verdict line counts as its single error.

## Part 4 — Revert checks (tasks 2.3 and 3.3)

Each mutation was reverted before the gate ran; the red half is the mutated tree, the green half is the same
guard restored, forced with `-PforceTests --no-build-cache`.

| check | red (mutated) | green (restored) |
|---|---|---|
| first-segment rule, task 2.3 | `revert-check-multivalue-red.xml`, tests="12" failures="1", 2026-11-06T21:26:17.231Z | `TEST-com.naviveylin.core.ManeuverFromStepTest.xml`, tests="12" failures="0", 2026-11-06T21:36:58.858Z |
| kind gate, task 3.3 | `revert-check-kindgate-red.xml`, tests="17" failures="1", 2026-11-06T21:26:27.432Z | `TEST-com.naviveylin.core.ManeuverLabelTest.xml`, tests="17" failures="0", 2026-11-06T21:36:58.877Z |

The failing cases are named in the red XMLs (`the manoeuvre carries the first segment of a multi-value
destination`, `a plain turn does not show a destination record it carries`) and in `tasks.md` 2.3 / 3.3. The
green halves are the gate's own forced run of those suites, later than both mutations — the restore, measured.

## Part 5 — The change's own red on HEAD (task 7.1)

`red-on-HEAD-carhint.xml`: `app: CarHintContentTest` run against the base's production code,
tests="8" failures="1", timestamp 2026-11-06T21:23:57.062Z, the failure being

```
org.junit.ComparisonFailure: expected:<450 m · 14:32[ · A8 towards München]> but was:<450 m · 14:32[]>
```

That is the case `aMotorwayChangesSecondaryTextCarriesTheSignpostedDestination` — red before the fix, green on
the change (part 3, the same suite's mobile carrier, tests="8" failures="0"). The companion case
`aMotorwayChangeWithoutADestinationKeepsTodaysSecondaryText` is in both carriers and green on the change.

## Part 6 — What this change does not carry, and why

- **Device steps, unchecked, no number invented**: task 1.2 (the route census line read on a real motorway
  route), task 4.3 (a rendered frame judged by the `pixel-check` host) and task 5.2 (the hand parity check on
  the car emulator). No device, emulator or `pixel-check` host is attached to an unattended loop child, so each
  task states what it did not measure; the layout half of 4.3 and the parity rule behind 5.2 are unit-asserted
  instead (part 3, `NextTurnOverlayTest` and `NavigationTemplateMapperTest`).
- **Task 6.2 stays unchecked**: the gate ran once (part 1) and did not pass — its single error is part 2's attributed
  residual. Ticking it would claim a green gate the tree does not have.
- The spoken voice instruction is out of scope by the proposal (it needs a submodule patch in
  `osmscout::VoiceInstructionAgent`); it and the tracker follow-up are named in `tasks.md`'s "Workflow
  follow-up".
