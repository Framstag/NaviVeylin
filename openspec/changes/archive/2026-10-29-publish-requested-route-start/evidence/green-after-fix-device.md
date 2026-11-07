# Evidence — tasks 3.1–3.3: the bridge publishes the requested start (2026-10-29)

Both native entry points, one helper (`libosmscout-client-java/src/route_start.h`): the description's
first node takes the requested position and every later node's cumulative distance carries the approach
(`RouteStartNodeDistanceMeters`), and the published polyline gains the requested position as its first
vertex. `calculateRouteWithObjectsWithProfile` and `calculateRouteWithObjectsAsync` get the same two
blocks, so the two copies cannot publish different starts (design D4).

Order matters and is what the code does: the description is corrected **before** `routeStepCollector.Collect`
(the step list and its distances) and before the copy kept for live navigation; the polyline is prepended
**after** `TransformRouteDataToPoints`, so nothing downstream sees an uncorrected start.

## Before (HEAD, `evidence/red-on-head-device.log`)

```
RouteStartPosition: requested start vs published first point 80 m, vs first step 80 m,
                    first segment 120 m, total 8897 m
RouteStartPosition: lengths: total 8897 m, step legs 8897 m, last step from start 8897 m,
                    drawn polyline 8912 m
RouteStartPosition: object start 'Camí Ral de la Massana a Andorra' (boundary_administrative):
                    published first point 57 m from the object, vs first step 57 m, first segment 19 m
```

## After (emulator-5554, same Andorra map, same coordinates)

```
$ adb shell am instrument -w -e class com.naviveylin.route.RouteStartPositionDeviceTest \
    com.framstag.naviveylin.test/androidx.test.runner.AndroidJUnitRunner
OK (5 tests)

I RouteStartPosition: requested start vs published first point 0 m, vs first step 0 m,
                       first segment 80 m, total 8977 m
I RouteStartPosition: first step is 0 m from the nearest polyline vertex
I RouteStartPosition: lengths: total 8977 m, step legs 8977 m, last step from start 8977 m,
                       drawn polyline 8992 m
I RouteStartPosition: on-network start: published first point 0 m from the request, first segment 7 m
I RouteStartPosition: object start 'Camí Ral de la Massana a Andorra' (boundary_administrative):
                       published first point 0 m from the object, vs first step 0 m, first segment 57 m
```

What the numbers say, one line each:

- The two red cases are green: the published first point and `steps[0].coord` are the requested position
  (0 m), not the snapped node (80 m, and 57 m for the object start).
- The approach is in every number that must carry it: `total 8897 → 8977` (+80) and
  `drawn polyline 8912 → 8992` (+80), while the four length figures still agree with each other
  (8977 / 8977 / 8977 against 8992 drawn — 0.17 %, inside the case's 2 % bound). Nothing was counted twice
  and the route is still one length (design D2/D5).
- The on-network case is unchanged in shape: first point 0 m, first segment 7 m — no duplicate vertex was
  added to an ordinary route (design D3).

## The diagnostics line (task 3.3)

```
$ adb logcat -d -s NaviVeylin | grep 'Route start'
I NaviVeylin: Route start: the requested start lies 80 m from the snapped node (tolerance 1 m),
              so the approach is published as the first segment
I NaviVeylin: Route start: the requested start lies 57 m from the snapped node (tolerance 1 m),
              so the approach is published as the first segment
```

Metres, the tolerance and the decision — no coordinate, no coordinate word. The line is emitted from a
calculation that never touches the main thread, and `:app:checkNoCoordinatesInLogs` (a `preBuild`
dependency, so it ran in the assemble above) passes.

## Host side of the same change

- The consumer greps of task 3.4: only `RouteGeometry.kt:25` reads `polyline.first.first()`, and that is
  `adoptedRouteGeometry` — the ends of an adopted route, which after this change are the requested start
  and the routed target; `FakeOSMScoutClient` sets no polyline for a route and needs no fixture change.
- `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.route.*"`: 30 suites,
  `tests=227 skipped=0 failures=0 errors=0`.
- The JNI translation unit compiles clean against the host include set with
  `-std=c++20 -fsyntax-only -Wall -Wextra -Wpedantic` (no warning), and the real build is
  `BUILD SUCCESSFUL` for `:app:assembleMobileDebug` (all three ABIs packaged).
