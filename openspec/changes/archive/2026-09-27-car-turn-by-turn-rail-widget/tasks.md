# Tasks

Spec: `specs/auto-navigation-hints/spec.md`. Design decisions D1–D7 in `design.md`.

## 1. Shared maneuver glyph source (spec: Car hint content; design D2)

- [x] 1.1 Move the maneuver arrow drawing out of `auto/src/main/java/com/naviveylin/auto/ManeuverGlyphs.kt` into a `:core` renderer that returns a `Bitmap` per `TurnType` (Canvas/Paint only, no car-app types, cached per turn type). Verify: new `:core` unit test renders every `TurnType` and asserts a non-null bitmap of the expected size plus cache identity (same instance on the second call).
- [x] 1.2 Rewire `ManeuverGlyphs` to wrap the `:core` renderer (`CarIcon.Builder(IconCompat.createWithBitmap(...))`) with the existing per-type `CarIcon` cache, so the car template artwork is unchanged. Verify: `:auto` compiles and the existing `ManeuverGlyphs`/`NavigationTemplateMapper` tests pass (`./gradlew :auto:test`).

## 2. Car hint content (spec: Car hint content; design D1)

- [x] 2.1 Extend `NavigationNotificationContentFormatter` with a car variant of `(state) → (car title, car text, turn type for the large icon)` using the instruction wording of the on-screen next-turn display, the distance to the maneuver and the arrival time. Verify: unit tests cover a normal maneuver, a maneuver without a street name, and the neutral fallback while rerouting or before the first instruction.
- [x] 2.2 Verify label parity between the car hint title and the on-screen next-turn text for the same navigation state. Verify: parity unit test comparing both outputs for identical state (spec: "Car hint content" — instruction wording scenario).
- [x] 2.3 Verify the phone notification content is unchanged. Verify: existing `NavigationNotificationContent` unit tests still pass without edits.

## 3. Notification: TBT contract, channel policy, action (spec: Turn-by-turn notification contract, Notification importance per surface, End navigation from the car hint; design D3, D6)

- [x] 3.1 Add a mono vector drawable for the end-navigation action and use it for the car extender action and the phone notification action (currently resource id `0`). Verify: `:app:assembleMobileDebug` accepts the drawable (`aapt2`), and existing notification action-intent tests pass with the icon set.
- [x] 3.2 Add the automotive car notification channel (`IMPORTANCE_DEFAULT`) next to the existing phone channel (`IMPORTANCE_LOW`, silent, no badge) and select it in `buildNotification` via `AutomotiveDevice.isAutomotive`. Verify: unit tests assert the phone path uses the low-importance channel and the automotive path the default-importance channel, and that no `IMPORTANCE_HIGH` is ever requested (spec: "No heads-up notification for turn hints").
- [x] 3.3 Extend the NAVIGATION notification with `CarAppExtender` (car title, car text, large icon from the `:core` renderer, end-navigation action). Verify: Robolectric test asserts `CarAppExtender.isExtended(notification)` and that the car title/text/action are the car variant while the phone title stays the destination name.
- [x] 3.4 Verify free driving posts no car hint contract. Verify: unit test asserts the free-driving notification is not extended and carries no car action (spec: "No hint contract outside navigation", "Free driving produces no car hint").

## 4. Trip metadata publishing (spec: Trip metadata for cluster and heads-up display, Trip publishing cadence, Hint teardown without host connection; design D4, D5, D7)

- [x] 4.1 Add `hasTripChanged(previous, current)` (maneuver, rounded distance, remaining time, rerouting/loading) and a `tripFromState(...)` mapping next to the existing `NavigationTemplateMapper` code, reusing `stepForInstruction` for the current step and building the `TravelEstimate` and `Destination` from the same state. Verify: unit tests assert step/estimate/destination content, a loading trip without steps while rerouting, and the neutral/loading fallback when the arrival time is unknown.
- [x] 4.2 Verify the cadence predicate against unchanged values. Verify: unit tests assert no publish when only speed or position changes and exactly one publish when the rounded distance or remaining time changes (spec: "Trip publishing cadence").
- [x] 4.3 Wire publication into the car session: publish from the existing main-thread state observer in `NavigationSession` through `NavigationManagerController`, after `navigationStarted` and stopping before `navigationEnded`, seeding from the current state value on session creation, all host calls best-effort. Verify: unit/session tests with a fake `NavigationManager` assert call order (`setNavigationManagerCallback` → `navigationStarted` → `updateTrip`), no `updateTrip` after `navigationEnded`, immediate publication for a session created mid-navigation, and no exception on mid-navigation destroy (spec: "Hint teardown without host connection").
- [x] 4.4 Verify existing `NavigationManagerController` behavior (stop callback, destroy cleanup) is unaffected. Verify: `NavigationManagerControllerTest` passes unchanged.

## 5. Documentation and sibling-change correction (spec: No car surface for free driving — deviation scenario; design risks)

- [x] 5.1 Update the ongoing-navigation section in `guidelines/UI.md`: label/guidance parity stays the rule, the car renders hints through car-only extender overrides, the car surfaces are the rail widget plus optional heads-up (never the car Notification Center), and free driving stays phone-only. Verify: the section states all three points and `guidelines/UI.md` matches the implemented behavior.
- [x] 5.2 Correct the in-flight change `background-navigation-notification`: spec delta `navigation-ongoing-notification` R5/R8 and tasks 4.6/4.7 must state the rail-widget/heads-up car surfaces and the free-driving deviation instead of a car shade relay. Verify: `openspec validate background-navigation-notification` passes and the corrected text no longer claims a car shade relay.
- [x] 5.3 Check `AGENTS.md` and `README.md` for stale statements about the notification/car surfaces and update them if the new behavior makes them wrong. Verify: no remaining claim that the ongoing notification reaches car surfaces through the shade.

## 6. Build and test gates

- [x] 6.1 Full unit-test suite passes with the new tests (use the `run-tests` skill). Verify: `./gradlew test` green, no regressions; watch the AGENTS.md Robolectric/JNI-stub classloader rule for the new Robolectric tests.
- [x] 6.2 Both flavors build without errors or new warnings (use the `build-app` skill). Verify: `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`.
- [x] 6.3 Verify no manifest change was needed and the car-app declarations are untouched. Verify: merged manifests for both flavors contain exactly one `location` foreground service and no new permissions (`:app:processMobileDebugManifest`, `:app:processAutomotiveDebugManifest`).

## 7. On-device verification (Android Auto via DHU, Android Automotive OS via emulator/head unit)

- [x] 7.1 Android Auto (DHU): start navigation, switch to another app so the nav view is not shown → the rail widget at the bottom shows the current maneuver with distance and updates as the vehicle moves. Verify: DHU observation with `adb logcat -s NaviVeylin` for the render cycle.
- [x] 7.2 Android Auto (DHU): resolve design Open Question 1 — test the rail widget with the phone (`IMPORTANCE_LOW`) channel versus a default-importance channel and record the observed behavior; if default importance is required for Android Auto, raise the car-side channel for that flavor too and update the design note. Verify: recorded observation plus the code change if required.
- [x] 7.3 Android Auto (DHU): instrument-cluster emulation shows the current step and remaining distance from the published trip, and shows the loading state during a reroute. Verify: DHU cluster emulation observation (spec: "Trip metadata for cluster and heads-up display").
- [x] 7.4 Android Auto (DHU) + Android Automotive OS (emulator/head unit): invoking the end-navigation action from the rail widget ends navigation and withdraws the hint; the phone stop action still works. Verify: navigation ends on both surfaces, hint disappears, logcat shows the stop path (spec: "End navigation from the car hint"). **Blocked 2026-09-20:** the phone leg of this check fails today — tapping `Stop` on the phone shade does not end navigation (see `TODO.md` §46 and `background-navigation-notification` task 6.1); re-run this task after that defect is fixed.
- [x] 7.5 Android Automotive OS (emulator/head unit): while navigating and after switching to another car app the hint is present (car channel represented), and free driving offers no car hint. Verify: observation on the head unit for both modes (spec: "Notification importance per surface", "No car surface for free driving").
- [x] 7.6 Android Auto (DHU) + Android Automotive OS: TBT hints are suppressed while the app displays its routing card, and the app does not treat the suppression as an error. Verify: observation with the nav view foreground versus another app foreground; no error state in the app or logcat (spec: "No car hints while the routing card is shown").
- [x] 7.7 Host-connection loss: destroy the car session mid-navigation (unplug the DHU connection / stop the AAOS session) → no crash, no further trip or hint updates. Verify: logcat shows clean teardown and no repeated host-call failures (spec: "Hint teardown without host connection").
