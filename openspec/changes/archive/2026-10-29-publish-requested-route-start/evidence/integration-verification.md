# Evidence — tasks 5.1–5.5: integration verification (2026-10-29)

## 5.1 The suites, each module run on its own (the aggregate run red, per the record below)

`-PforceTests --no-build-cache`, tallies read from each module's `test-results/*.xml`:

| module | run | tests | failures | errors |
|---|---|---|---|---|
| `:app` mobile | `:app:testMobileDebugUnitTest -PforceTests` | 2139 (297 classes) | 0 | 0 |
| `:app` automotive | `:app:testAutomotiveDebugUnitTest -PforceTests` | 2139 (297 classes) | **5 in the loaded run** | 0 |
| `:auto` | `:auto:testDebugUnitTest -PforceTests` | 887 (92 classes) | **32 in the loaded run** | 0 |
| `:core` | `:core:testDebugUnitTest -PforceTests` | 645 (65 classes) | 0 | 0 |
| `:osmscout-client-java` | `:osmscout-client-java:test -PforceTests` | 112 (23 classes) | 0 | 0 |
| `buildSrc` | `./gradlew test` in `buildSrc` | 152 (15 classes) | 0 | 0 |
| submodule host suite | `meson test -C hostbuild --timeout-multiplier 2` | 169 executables | 0 | — |

### The red runs are issue #93 (§148), the aggregate-run family — attributed, not waved away

The first attempt was the aggregate invocation
`:app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest
:core:testDebugUnitTest :osmscout-client-java:test`: `:auto` came back with **32 failures**, all
`java.lang.StackOverflowError` inside MockK's handler map (`TemplateFaultIsolationTest` 21/21,
`VehicleAnchorPickerScreenTest` 11), and the automotive flavor with **5** failures in two classes
(`RoutePanelViewModelVehicleSpeedTest` 2, `NavigationStatusRingRasterTest` 3).

Every one of those classes is green when its own module — or just the class — is run:

```
$ ./gradlew :auto:testDebugUnitTest -PforceTests
BUILD SUCCESSFUL in 1m 39s
auto: files=92 tests=887 skipped=0 failures=0 errors=0

$ ./gradlew :app:testAutomotiveDebugUnitTest -PforceTests \
    --tests "com.naviveylin.ui.route.RoutePanelViewModelVehicleSpeedTest" \
    --tests "com.naviveylin.ui.navigation.NavigationStatusRingRasterTest"
BUILD SUCCESSFUL in 29s
TEST-...RoutePanelViewModelVehicleSpeedTest       tests="3" skipped="0" failures="0"
TEST-...NavigationStatusRingRasterTest            tests="3" skipped="0" failures="0"
```

That is the shape issue #93 records ("every one of them green in a single-module or solo run"), extended by
one data point its own wording does not cover yet: a **single-module** automotive run of 2139 tests is
enough to redden them, so the load threshold is lower than "aggregate across modules". None of the failing
classes reads a path this change touches (no route geometry, no native call, no storage).

## 5.2 Warnings

```
$ ./gradlew :app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest :core:testDebugUnitTest \
    :auto:testDebugUnitTest --rerun-tasks 2>&1 | grep -c '^w: '
0
```

No warning at all in that run, and therefore none naming `route_start.h`, `OSMScoutClient.cpp`,
`RouteStartTest.cpp`, `RouteStartPositionDeviceTest.kt` or `RouteStepSegmentsTest.kt`.

## 5.4 Revert-check on the device (the new invariant, mutated)

Mutation — `ShouldPrependRouteStart` never prepends, so the bridge publishes the snapped start again:

```c++
inline bool ShouldPrependRouteStart(double approachMeters)
{
  // MUTATION (revert-check, task 5.4): never prepend.
  (void)approachMeters;
  return false;
}
```

Red, on `emulator-5554` with the Andorra map, rebuilt and reinstalled (`BUILD SUCCESSFUL in 1m 01s`):

```
$ adb shell am instrument -w -e class \
    com.naviveylin.route.RouteStartPositionDeviceTest#publishedStartIsTheRequestedStartPosition \
    com.framstag.naviveylin.test/androidx.test.runner.AndroidJUnitRunner
1) publishedStartIsTheRequestedStartPosition(...)
java.lang.AssertionError: the published polyline starts 80 m from the requested start
  (hillside above Andorra la Vella); it must start where the caller asked to depart
Tests run: 1,  Failures: 1
```

Mutation reverted, rebuilt (`BUILD SUCCESSFUL in 43s` — the target recompiled, so this is not an
`UP-TO-DATE` answer) and forced green:

```
$ adb shell am instrument -w -e class com.naviveylin.route.RouteStartPositionDeviceTest ...
OK (5 tests)
$ git -C app/src/main/cpp/libosmscout status --porcelain     # empty
```

## 5.5 The two surfaces receive the same geometry (read, not run)

- The car has **no** route calculation and no polyline construction of its own: `auto/src/main/java`
  contains no `calculateRoute*`, no `latitudes`/`routeLats` producer — only consumers
  (`NavigationScreen.kt:588-590` → `RendererGate.setRoute(state.routeLats, state.routeLons)`,
  `AutoMapRenderer.setRoute`, `RouteDescriptionScreen` reading `RouteStep`).
- The state it consumes is filled once, from the engine's own `RouteEntry`:
  `NavigationEngine.kt:402` `routeLats = routeEntry.latitudes`, carried in
  `core/.../NavigationState.kt:94-95` (`routeLats`/`routeLons`) and `:49` (`steps`).
- The phone draws from the same `RouteEntry` through `RouteGeometry.kt` (`adoptedRouteGeometry`,
  `calculatedRouteGeometry`).

So parity is by construction: one `RouteEntry`, whose first vertex is the requested start and whose
`steps[0].coord` is that same position, reaching both surfaces. **No car run was made** — the AAOS AVD was
not attached in this session; the claim above is a reading of the seam, and the device evidence for the
geometry itself is the phone run in `green-after-fix-device.md`.
