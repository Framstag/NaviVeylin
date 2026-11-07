# Evidence — `route-steps-live-guidance`

Measured on this tree, 2026-10-28. Device items are named at the end and are **not** claimed.

## The native change (tasks 1.1–1.4)

- Fork commit `de37a2bd13c60706bb12cda01ee1f92ea473d293` on `naviveylin-local`, pushed:
  `fcfd3efca..de37a2bd1  naviveylin-local -> naviveylin-local`. App-side pin bumped and staged.
- `NavigationListener.onRouteSteps(RouteStep[])` / `onNextRouteStep(RouteStep)`; method ids cached with
  the same guard (`GetMethodOrClear`), so a descriptor mismatch leaves a null id and no emission.
- Engine: `RouteInstructionAgent<osmscout::client::RouteStep, RouteStepBuilder>`. The submodule's own
  `Tests/src/RouteStepModelTest.cpp` instantiates exactly this pair (its `static_assert` case), which is
  the contract evidence; the case itself was **not run** here (the submodule's host build is a separate
  `meson` configure).
- Dispatch marshals `CreateJavaRouteStep` per element, drops unbuildable ones, and sends **no list** when
  none was built (the non-null guarantee, re-pointed at steps).
- `[JNI] guidance dispatch: list|next steps=N us=M` — one coordinate-free line per publication
  (task 1.3; the device read of it is 5.1).
- Prose machinery deleted: `JavaRouteInstruction`, `JavaRouteInstructionBuilder`, `CollectCallback`,
  `MoveToDescription`/`MoveToTurnType`, `CreateJavaRouteInstruction`, the two instruction dispatchers
  (−654 lines net).

## Build and gate (tasks 7.1, 7.2)

- `build-both-flavors.log` — `:app:assembleMobileDebug :app:assembleAutomotiveDebug`: **BUILD SUCCESSFUL**,
  no `w:`/`e:` line, all three ABIs of the JNI bridge built against the new pin.
- `gate.log` — `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache --continue`:
  **BUILD SUCCESSFUL** (`:osmscout-client-java`, `:core`, `:auto`, both `:app` flavors, forced).
- `:app:compileMobileDebugAndroidTestKotlin` green (the two instruction device tests re-pointed at
  steps and `RouteStepLiveGuidanceDeviceTest`'s siblings).

## Revert-checks (tasks 2.5, 3.6)

| mutation | rule mutated | case that went red |
|---|---|---|
| `revert-step-matching-distance-only.log` | `nextStepIndex`: distance only, the `kind` comparison removed | `StepIndexSelectionTest` "a dual node binds to the step with the same kind" — 1 of 6 red |
| `revert-next-next-hint.log` | `NextTurnOverlay`: the step's nested `nextNext` replaced by `null` | `NextTurnOverlayTest` "the next-next hint renders from the nested step" (+ the icon-size and render cases) — 3 of 10 red |
| `revert-checks-restored-green.log` | none (both files restored byte-identically, `diff -q`) | green, forced |

## Test conversion (the bulk of this session's work)

The suites did not compile when the listener changed: every fixture built `RouteInstruction(...)`.
Converted with two module-local fixtures (`app/src/test/java/com/naviveylin/test/InstructionStepFixtures.kt`
and `auto/src/test/java/com/naviveylin/auto/StepFixtures.kt`) that mirror the retired constructor and
ignore its description strings — the wording is now built from values, so a case asserts
`ManoeuvreLabels`' output for the manoeuvre the fixture describes. Assertions that named native English
were replaced by the builder's wording, `streetName` reads became `manoeuvreFromStep(step).displayName`,
and the two roundabout device tests now read `kind`/`exitOrdinal`/`roundaboutLeave.clockwise`.

## Open — needs a device, not claimed

1. **5.1 / 5.2 — the dispatch cost.** The timing line exists and is emitted per publication; no device
   run recorded it, so no cache decision was taken (design "Performance" says: measure first, cache only
   if the number demands it). Recipe: `adb logcat -s NaviVeylin | grep "guidance dispatch"` while
   navigating a 19-manoeuvre route, before and after the pin bump.
2. **7.3 — `RouteStepLiveGuidanceDeviceTest` is not written.** The step-ahead publication is covered by
   `RouteStepModelBridgeTest` (calculated route) and by the unit-level engine cases only.
3. **7.4 — the AAOS AVD.** `emulator` is not on this host's `PATH` (only `adb`), so the car surface
   could not be booted or judged (`guidelines/Build.md` §10a/§10b).
4. **7.5 — the overlay pixels.** No screenshot judged with `pixel-check`; the kind-based icon claim is
   covered by `NextTurnOverlayTest`'s symbol assertions.

A phone run is also blocked at the packaging level by issue **#691** (the debug APK from this tree cannot
start; it needs the temporary source-copy workaround that `route-panel-from-steps` used and removed).

## Device run, 2026-10-28 (phone emulator `emulator-5554`, Andorra data)

The app was installed with the same temporary packaging workaround issue #691 needs (the submodule's Java
client sources copied into `app/src/main/java/com/framstag/libosmscout/client/`, removed again afterwards),
and every run used `adb install -r -t` + `am instrument` — never `connectedAndroidTest`.

- `device-live-guidance-run.log` — `com.naviveylin.route.RouteStepLiveGuidanceDeviceTest`: **OK (3 tests)**.
  `live step list: published=330 calculated=330` (the live walk publishes exactly the calculated route's
  steps), `live step ahead: kind=INFORMATIVE remaining=4.4 leg=4.4`, and a nested hint was observed on the
  later vertices.
- `device-dispatch-cost.log` — the timing line the change added, task 5.1:

  ```
  [JNI] guidance dispatch: list steps=330 us=7503      # first full list
  [JNI] guidance dispatch: list steps=329 us=6816      # window re-emission
  [JNI] guidance dispatch: next steps=1  us=81         # per position update
  [JNI] guidance dispatch: next steps=1  us=53
  [JNI] guidance dispatch: next steps=1  us=70
  ```

  **Task 5.2's decision: no cache.** The path the design worried about is `next` — the per-position
  publication — and it measures **53–85 µs** (0.05–0.09 ms) on this device, three orders of magnitude
  below a one-second fix interval; the 5.7–7.5 ms list publication happens on a route change or a window
  step, not per fix. No `currentNodeIndex` cache is added. The "before" number (the instruction agent's
  dispatch) is not reproducible from this tree: the instruction projection no longer exists and it never
  emitted a timing line.
- `device-overlay-navigation.png` — the live next-turn overlay during navigation (German device), looked at
  in a throwaway child session: `0 m`, the road name twice, `31 m`, `Leicht rechts abbiegen`, a large
  straight-ahead glyph in row 1 and a smaller glyph for the hint row.

### Finding from that frame (blocks 7.5's claim)

The overlay's **primary row shows a facts-only step**: the engine's walk lands on an `INFORMATIVE` node on
every described road node, so `nextStep` can name a road-name/max-speed node rather than the manoeuvre, and
the card then reads `0 m` + the street name with a straight-ahead arrow while the real turn (`Leicht rechts
abbiegen`, 31 m) is demoted to the hint row. The route *list* filters such steps (`routeRows`), the live
state does not. The fix is a state-level rule — announce the next manoeuvre rather than the next described
node, and carry the distance to it — which changes `NavigationState`/every surface, so it is not a silent
patch inside 7.5.

## The announced-step fix (7.5's finding)

`core/RouteStepGuidance.kt` — `guidanceStep(published, route)` returns the published step when it is a
manoeuvre, and otherwise the route's next manoeuvre with its distance recomputed from the vehicle's progress
(`manoeuvre.distanceFromStart - (published.distanceFromStart - published.remainingMeters)`), as a copy that
carries the fields a guidance surface reads. `NavigationEngine.onNextRouteStep` announces that step, so the
overlay, the notification, the car hint and the car trip all get the manoeuvre instead of a facts-only node.
`core/ManoeuvreAnnouncementTest` pins the rule (a manoeuvre announced unchanged, a facts-only node resolved to
the next manoeuvre 31 m ahead, the distance shrinking with progress, no manoeuvre ahead keeping the published
step).

The pixel re-check of the same frame is **open**: the phone emulator was no longer attached to `adb` after
the automotive emulator was started (`adb devices` empty), so no new navigation frame could be captured. The
screenshot in this directory is the *pre-fix* frame that found the defect.

## Car surface on the AAOS AVD (task 7.4), 2026-10-28

- Automotive APK built (same temporary #691 packaging workaround, removed again) and installed with
  `adb install -r -t`: `Success`. `CarAppActivity` top-resumed, template host renders the car map
  `SurfaceView` (`template_view_surface`, 1080x600), stylesheets load with the expected `Unknown types`
  warnings only, `logcat -b crash` empty.
- `device-car-surface.png` (looked at in a throwaway child session): the car app on screen, panel header
  `NaviVeylin`, entries `Freie Fahrt`, `Markierte Favoriten`, `Alle Favoriten`, `POIs suchen`,
  `Suchverlauf`, the Dortmund map with the position marker, the host status bar (`12:51`, `Driver`) and the
  climate strip. **No route line and no guidance card** — nothing was navigated in that frame.
- So the car *path* runs against the step-based guidance code without a fault; the instruction-panel
  scenario (cue, kind-based glyph, hint) still needs a navigation session driven on the car, which this
  session did not do.

## Car run (task 7.4), 2026-10-28 on the automotive AVD

Drive: car app → search `Hauptbahnhof` → result → `▶ Navigation starten` → GPS fixes advanced along the
route. Two frames in this directory: `device-car-surface.png` (panel/map before navigation) and
`device-car-guidance.png` (guidance running).

Host diagnostics, in order (`Diag/HOST`, tags from `guidelines/Design.md` §8):

```
calculation notice push pct=null cancellable=true
calculation notice update pct=0
surface release 12241048
navigationStarted
trip update remaining=815m eta=0
NOTIF post … text='in -- · arrive --:-- · -- · 800 m' hint='Navigation aktiv'
surface adopt 168077915 1080x600@120.0dpi
NOTIF post … text='Start'        big='Start | in -- …'              hint='Start hintText='-- · --:--''
NOTIF post … text='Links abbiegen in Gerberstraße'
           big='Links abbiegen in Gerberstraße | in 50 m · arrive --:-- · -- · 800 m'
           hint='Links abbiegen hintText='50 m · --:--''
```

What each part proves:

- **wait notice** — pushed with a null percentage, then updated; no surface while it is up.
- **one surface, one owner** — the search screen's surface is released, `navigationStarted`, then the
  navigation surface is adopted: 1080x600 @ 120 dpi. No second adopt, no stray release.
- **cue** — the manoeuvre reaches the host in the same wording the phone overlay uses (`ManoeuvreLabels`:
  `Links abbiegen in Gerberstraße`), and the distance-to-turn is the step's `remainingMeters` (`in 50 m`),
  not the trip's remaining distance.
- **hint** — the car hint carries the same instruction (`Links abbiegen`) plus `50 m · --:--`.
- **step list is not stale** — the first post is the fallback (`in --`, no fix yet), then the live steps
  arrive (`[JNI] guidance dispatch: next steps=1 us=88…161`, 0.09–0.16 ms per dispatch on this AVD).
- **no fault** — `logcat -b crash` empty for the whole car run; the app exits the session on back.

Two gaps the run exposed, both recorded rather than fixed here:

1. **`arrive --:--` / `trip update … eta=0`** — the route carries no remaining time on this AVD (the route
   card reads `0 min` as well), so the arrival estimate is absent. That is a route-time gap above this
   change, not a step-model one.
2. **`in --` for a 0 m distance** — `formatDistanceCompact` renders `<= 0 m` as `--`: a vehicle standing at
   the announced node reads "in --" instead of "jetzt". Filed as issue #694.

The instrumented `RouteStepLiveGuidanceDeviceTest` could not run on this AVD (it is the automotive image and
the test drives the phone surface); the phone-side live-guidance run of 7.3 (3 tests OK) stands.

`recordNotificationPost` now also logs the expanded lines (`big=`) and the hint's own text (`hintText=`),
because the compact text omits the distance whenever a manoeuvre is known — without that, a car run cannot
be judged from the log at all (`guidelines/UI.md` §3: the car's panel is host-drawn).

### The car instruction panel itself (frame `device-car-guidance.png`)

Read in a throwaway child session (pixels never enter the session holding the work):

```
top-left black card:   <-  50 m
                       Links abbiegen
                       ^   Gerberstrasse
bottom-left card:      Kampstrasse
                       01:00 · 800 m
right:                 0 km/h, speed-limit disc 30
```

- the panel's glyph points **left**, matching `Links abbiegen` — the kind-based icon claim holds on the car
  surface too, not only on the phone overlay;
- the third line `^ Gerberstrasse` is the nested "next next" hint, the same field the phone's secondary row
  uses (`nextNextGapMeters`), drawn by the app, not by the host;
- `50 m` is the step's `remainingMeters`, distinct from the trip's `800 m`.

Correction to the gap list above: the app's own card **does** show a time (`01:00`), so the route carries a
duration; what is missing is the wall-clock arrival (`trip ... eta=0`, `arrive --:--`), i.e. `state.etaMillis`
stays 0 during a car session. Separate from the step model.

## The live overlay frame after the announced-step fix (task 7.5)

Phone AVD, 2026-10-28: search `Escaldes-Engordany` → `Route berechnen` (8,4 km · 10 min) → `Navigation
starten` → four GPS fixes advanced along the route. Frame `device-overlay-step-cue.png`, read in a throwaway
child session:

```
card x 0..1079, y 167..455
  50 m                            <- the step's remainingMeters, navy
  Rechts abbiegen                 <- the manoeuvre, the phone overlay's own wording
  Carrer Ciutat de Sabadell       <- the road turned onto
glyph x 57..169, y 263..296: a plain horizontal right arrow
bottom sheet: CG-2 Vial de l'Obac, arrival 14:10, 8 min, 7,7 km
```

and the matching host post at the same moment:

```
Diag/HOST: NOTIF post text='Rechts abbiegen in Carrer Ciutat de Sabadell'
           big='Rechts abbiegen in Carrer Ciutat de Sabadell | in 50 m · arrive 14:10 · 8 min · 7,7 km'
           hint='Rechts abbiegen' hintText='50 m · 14:10'
```

The defect this task was open for is gone: the primary row carries the announced manoeuvre's own distance
(`50 m`, not `0 m`), the wording names the turn, and the glyph is a right arrow — the three agree. The frame
that found the defect (`device-overlay-navigation.png`) had `0 m` + a street name + a straight-ahead glyph
while the real turn was 31 m away and sat in the hint row.

No nested "then" row appears in this frame: the follower is outside `NextNextWindowMeters`, which is the
rule the unit cases pin (`NextTurnOverlayTest`, the `nextNext` revert-check log).
