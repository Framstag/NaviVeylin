# Traceability — `fix-router-overall-distance`

## Delta scenarios → the case or observation that exercises them

| # | Requirement → Scenario | Exercised by | State |
|---|---|---|---|
| 1 | *A published length tracks the route that was drawn* | task 3.1's assertions in `RouteInstructionPositionDeviceTest.routeLengthsAreMeasuredForALongAndAShortRoute` | **executed on `emulator-5554` (Pixel_8, NRW data)**: green at 1.00137 / 1.00214 / 0.99636, and **red** under the task 3.2 mutation with the expected assertion (see below) |
| 2 | *A long intercity route carries a real length* | same case, Dortmund Hbf → Cologne Hbf | **measured**: `routerTotalM=97416 descriptionTotalM=97416 polylineM=97283` (before the change: 72 771 m against 97 283 m) |
| 3 | *A route without a description still carries a route length* | task 3.3's mutation (the branch has no producer in the tree) | **measured**: with the description suppressed, `descriptionTotalM=0` and the published total is the polyline's length — 97 446 / 97 283, 21 022 / 20 966, 1 496 / 1 493 (1.0017 / 1.0027 / 1.0017) where the estimate (72 771 / 16 677 / 824) used to be; case green |
| 4 | *No route field states the estimate as a distance* | task 1.4 + 2.2: `grep -n 'GetOverallDistance' OSMScoutClient.cpp` leaves one *use* — the async success log, now `air-line estimate=` (`:6868`) — the other two hits are the corrected comments; no field assignment reads it | ✓ code + grep |
| 5 | *A consumer that reads the length gets a length* | task 2.1's enumeration: every length consumer goes through the one seam — `RoutePanel.kt:235`, `RouteSummary.kt:72`, `NavigationEngine.kt:1064` (progress denominator + car trip state) and `RouteStepValues.kt:63` (the fallback itself); the divergence diagnostic (`RoutePanelViewModel.kt:681/683`) reads the published total, which is now a length on both paths; `SearchDialog.kt:922/929` reads a *search result's* distance, not a route length | ✓ enumerated; host coverage in `RouteStepValuesTest` |

Both surfaces read the same `RouteEntry` in one process (`NavigationEngine.kt:1064` is the shared progress/trip
source), so row 5 has no separate car case: the case is the seam's host case plus task 5.1's numbers, recorded
here as the substitute rather than as a car run.

## Evidence collected in this session

| Item | Evidence |
|---|---|
| The estimate is no longer published | `totalDistance` assignments after the change: init `0.0` (`:6053`, `:6727`), description total (`:6399`/`:6402`, `:7157`/`:7160`), new polyline fallback (`:6515`, `:7278`) — no assignment reads `GetOverallDistance()` |
| The fix compiles in the native TU | `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` → BUILD SUCCESSFUL 27s, `configureCMakeDebug[arm64-v8a]` + `buildCMakeDebug[arm64-v8a]` executed (not up-to-date), `.ninja_log` shows `OSMScoutClient.cpp.o` compiled and the obj relinked at that build's epoch |
| Both flavors link it, and the device case compiles | `:app:assembleMobileDebug :app:assembleAutomotiveDebug :app:assembleMobileDebugAndroidTest -Pandroid.injected.build.abi=arm64-v8a` → BUILD SUCCESSFUL 32s, 55 tasks executed |
| Existing suites still pass | `:app:testMobileDebugUnitTest :auto:testDebugUnitTest :core:testDebugUnitTest -PforceTests --no-build-cache -PnoCoverage` → BUILD SUCCESSFUL 2m37s, 29 executed; tallies `:app` mobile 221 classes / 1725 tests / 0 failures, `:auto` 76 / 782 / 0, `:core` 41 / 447 / 0. The tree included the concurrent session's uncommitted edits, so the tallies are the tree's, not HEAD's alone |
| Submodule patch landed | commit `29db6870c` on `naviveylin-local` ("Publish a route length on the path without a description"); `git ls-remote origin naviveylin-local` read immediately before the push returned the pre-change `570a9372c`, and the remote now reads `29db6870c`; the submodule tree is clean and the main repo's gitlink is bumped `570a937` → `29db687` |
| Rule recorded | `guidelines/Build.md` §10, subsection "A calculated route's length — which number is a route length", with the estimate's three jobs, the measured ratios, the instrumented recipe **and the stale-test-APK trap** the device session hit |
| Main repo commits | `c085d73` (gitlink bump + device-case assertions + the `guidelines/Build.md` §10 rule) and `78a796e` (the stale-test-APK trap note), pushed to `upstream main` as `cf513f7..78a796e`; CI run `37673100702` triggered by it |
| Completion gate | both flavors built with all three ABIs (arm64-v8a, armeabi-v7a, x86_64 in both APKs); `:app` mobile 221 classes / 1725 tests / 0 failures, automotive 221 / 1725 / 0 on the re-run (first run red on two `MapCanvasViewModelModeTest` cases — `Dispatchers.Main` set/reset race, both green solo, recorded in `TODO.md` §148) |

## Device session (2026-10-07, `emulator-5554`, Pixel_8, `north-rhine-westphalia` data)

One boot, four APK installs, never uninstalled (the skill's rule), maps intact afterwards.

| Run | Build | Result |
|---|---|---|
| class run (first) | fix | `OK (6 tests)` — but the installed test APK was the **stale `outputs/`** one, so this was a false green: it ran the *old* assertions. Recorded as the reason for the trap note in `guidelines/Build.md` §10 |
| my case | fix + **mutation R** (estimate republished *after* the description branch in path A) | **FAILED** — `the published length must track the drawn route (publishedM=72771 descriptionM=97416 polylineM=97283 publishedOverPoly=0.7480366378706278 …)`, the pre-fix numbers reproduced exactly |
| my case | restored fix | `OK (1 test)` — 1.00137 / 1.00214 / 0.99636 |
| my case | fix + **mutation D** (description branch suppressed) | `OK (1 test)`, `descriptionTotalM=0`, published = polyline length on all three routes |
| class run (last) | restored fix | `OK (6 tests)` — 97 416 / 21 011 / 1 487 published, all within 0.4 % of the drawn polyline |

Two findings the session produced, both recorded rather than smoothed over: the **task 3.2 mutation had to change** (re-adding
 the estimate at its old spot is overwritten by the description branch and proves nothing — the honest mutation
 republishes it after that branch), and the earlier green was **void** until the freshly built test APK was
 installed (an ABI-injected build leaves `outputs/apk/androidTest/…` from a previous build; the class's dex
 lives in `classes3.dex`, so a naive `classes.dex` grep also misleads).

## TODO handover

`TODO.md` **§129** (user-visible half) and **§139** (native half) are both addressed by this change and are
removed when it archives — the removal belongs to `cleanup-todo`, not to this change. §129's "34 % apart"
wording also predates `fix-route-length-disagreement`, which is why the design records that the divergence
diagnostic now compares two numbers from one source.

## Open, device-gated

None — tasks 3.2, 3.3 and 5.1 ran in the session recorded above. What remains is the routine gate for the
completed change (both flavors once, per `build-test-gate`) and the archive itself.
