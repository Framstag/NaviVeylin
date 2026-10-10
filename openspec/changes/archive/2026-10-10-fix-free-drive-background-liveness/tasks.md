# Tasks

Specs: `openspec/changes/fix-free-drive-background-liveness/specs/` (`navigation-ongoing-notification`,
`current-road-info`, `location-updates-lease`). Approach and decisions: `design.md` (D1–D5).

## 1. Shared free-driving status (`:core`) — spec: current-road-info

- [x] 1.1 Add `FreeDrivingStatus` + `@Singleton FreeDrivingStatusProvider` in
      `core/src/main/java/com/naviveylin/core/FreeDrivingStatusProvider.kt` (`publish(surface, road, speedKmH)`,
      `clear(surface)`, `status: StateFlow<FreeDrivingStatus?>`, per-surface keys, retain-on-death, values
      guarded like `DrivingModeProvider`) and bind it in the app's DI modules.
      **Verify:** `FreeDrivingStatusProviderTest` green with cases `publishPerSurface`, `lastResolutionWins`,
      `clearRemovesOneSurface`, `retainOnSurfaceDeathIsNotBlanked`, `emptyBeforeFirstFix` (spec
      `current-road-info` — status empty before the first fix).
- [x] 1.2 Revert-check for the no-stale-status predicate (spec `current-road-info` — status clears when the
      road is lost): mutate `clear()` to a no-op, run `FreeDrivingStatusProviderTest#clearRemovesOneSurface`
      and confirm it FAILS, restore, re-run the class forced green (`--rerun-tasks`), record both outputs.
- [x] 1.3 Add the coordinate-free diagnostics line for the status (one line on publish, `free-driving status:
      source=<phone|car> road=<ref|name|none> speed=<n|none>`; no position, spec `auto-diagnostics`).
      **Verify:** `:app:checkNoCoordinatesInLogs` passes and the line appears once per published change in
      `adb logcat -s NaviVeylin` during 1.4.

## 2. Phone publication and label — spec: current-road-info, location-updates-lease

- [x] 2.1 Publish from `MapCanvasViewModel` after `resolveCurrentRoad`/speed resolution and clear the
      free-driving status on mode exit (spec `current-road-info` — one resolution feeds every consumer).
      **Verify:** `MapCanvasViewModelFreeDrivingStatusTest` green with cases `publishesRoadAndSpeedOnFix`,
      `clearsStatusOnFreeDriveExit`, `noStatusBeforeFirstFix`, and the case that a street change publishes the
      new road (`streetChangePublishesNewRoad`).
- [x] 2.2 Point the on-screen street label at the shared status instead of the private copy; keep the label's
      anchor and visibility rules untouched.
      **Verify:** existing `FreeDrivingStreetPillComposeTest` and
      `MapCanvasViewModelVehicleAnchorTest` still green; `MapCanvasViewModelFreeDrivingStatusTest#labelShowsPublishedRoad`.
- [x] 2.3 Case that the notification path never triggers a road lookup of its own (spec `current-road-info` —
      one lookup per fix, not one per consumer): inject a counting fake client, publish a status, render the
      notification content, assert the lookup count is unchanged.
      **Verify:** `MapCanvasViewModelFreeDrivingStatusTest#notificationDoesNotTriggerRoadLookup` green.
- [x] 2.4 Case that an invisible, non-navigating free drive holds no lease and a returning surface acquires it
      again (spec `location-updates-lease` — pinned no-lease case): drive the screen's `ON_PAUSE`/`ON_RESUME`
      path against a real `LocationService`, assert `heldLeaseConsumers()` is empty after pause and contains
      `PHONE_MAP` after resume, and that the diagnostics stream shows `held=0` then re-acquire.
      **Verify:** `FreeDrivingBackgroundLeaseTest` green; no production code change in `LocationService`. If
      the assertion fails because something *does* hold a lease, stop and report instead of weakening the case.
- [x] 2.5 Revert-check for the publication invariant (spec `navigation-ongoing-notification` — shade and label
      agree): remove the `publish` call, run `MapCanvasViewModelFreeDrivingStatusTest#publishesRoadAndSpeedOnFix`
      and confirm it FAILS, restore, re-run forced green, record both outputs.

## 3. Car publication (parity) — spec: current-road-info, location-updates-lease

- [x] 3.1 Publish the car's resolved street/speed from `FreeDrivingScreen` into the same status and clear it on
      screen stop/destroy and on free-driving exit (spec `current-road-info` — car publishes its own
      resolution).
      **Verify:** `FreeDrivingScreenStatusTest` green with cases `carFreeDrivingPublishesItsRoad`,
      `clearOnScreenDestroyLeavesNoStaleRoad`.
      **Deviation (stop vs destroy):** the clear runs on screen **destroy** and on free-driving exit, not on
      screen **stop**. `onStop` bounds the screen's observations (spec `auto/screen-observation`), but the live
      car session keeps its lease through a stop, and a backgrounded free drive must keep showing its last known
      road/speed (spec `location-updates-lease` — an invisible, non-navigating mode keeps the last known road) —
      clearing on stop would blank the shade to the neutral title instead. Retain-on-death is the provider's
      stated semantics; `clear` is reserved for the surface actually ending.
- [x] 3.2 Case that the car's session lease is the one keeping fixes alive while another car app is foreground
      (spec `location-updates-lease` — the car keeps its session lease).
      **Verify:** `NavigationSessionLeaseTest` (auto) green with `carSessionLeaseHeldWhileAnotherCarAppForegrounds`.
      **Location deviation:** the test lives in `:app` (`app/src/test/java/com/naviveylin/di/NavigationSessionLeaseTest.kt`)
      because the lease owner (`AutoServiceModule.provideAutoLocationProvider`) and `LocationService` are
      `:app` types — `:auto` has no lease owner of its own; the class and case name are as specified.
- [x] 3.3 Revert-check for the car's clear-on-destroy (spec `current-road-info` — no road from an earlier
      session): mutate the clear to a no-op, run `FreeDrivingScreenStatusTest#clearOnScreenDestroyLeavesNoStaleRoad`
      and confirm it FAILS, restore, re-run forced green, record both outputs.

## 4. Notification content and refresh — spec: navigation-ongoing-notification

- [x] 4.1 Make `NavigationNotificationContentFormatter` render the free-driving line from the shared status and
      omit absent values (no empty fragment, no stray separator, fallback/neutral title otherwise).
      **Verify:** `NavigationNotificationContentFormatterTest` green with
      `freeDrivingShowsStatusRoadAndSpeed`, `unknownSpeedLeavesNoSeparator`, `unknownRoadShowsFallback`,
      `noFixYetShowsNeutralTitle` (one case per delta scenario).
- [x] 4.2 Combine the status flow into `NavigationNotificationService.observeDrivingState` so the content
      refreshes with the next fix and posts only when the host-visible content changed; leave the controller's
      mode-driven start/stop trigger untouched.
      **Verify:** `NavigationNotificationServiceStatusTest` green with `statusChangeRepostsContent` and
      `unchangedStatusDoesNotRepost`; existing `NavigationNotificationServiceActionTest` and
      `NotificationHostSendLoggingTest` still green.
- [x] 4.3 Revert-check for the formatting predicate (spec `navigation-ongoing-notification` — unknown values
      leave no empty fragment): restore the previous `joinToString` behaviour, run
      `NavigationNotificationContentFormatterTest#unknownSpeedLeavesNoSeparator` and confirm it FAILS, restore,
      re-run forced green, record both outputs.

## 5. Spec-honest documentation — spec: navigation-ongoing-notification, location-updates-lease

- [x] 5.1 Supersede the archived assurance in `guidelines/Design.md` §13 "Superseded decisions": the
      `background-navigation-notification` claim "no new GPS load — location keeps streaming" is false under the
      lease mechanism, and the deliberate policy is now "an invisible, non-navigating free drive holds no
      lease"; add the matching `TODO.md` entry that records the defect, its device evidence and its closure.
      **Verify:** the §13 entry names the archived change and the replaced assurance; the TODO entry exists and
      links this change; `openspec doctor` still clean.
- [x] 5.2 Check `guidelines/UI.md` §1 (cross-variant parity) / §7 (phone map modes) for the free-driving
      notification content convention (street/ref + speed, no empty fragment) and add it if missing.
      **Verify:** the convention is found in UI.md or added; the documented wording matches the spec text.

## 6. Integration gates and device evidence — spec: all three

- [x] 6.1 Build both flavors and the car module through the `build-app` skill:
      `:app:assembleMobileDebug`, `:app:assembleAutomotiveDebug` — no errors, no new warnings.
      **Verify:** both tasks succeed and the build log carries no new warning lines.
      **Result:** `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug :auto:assembleDebug` →
      `BUILD SUCCESSFUL in 46s`, 175 actionable tasks; the only warnings are the pre-existing Gradle
      deprecation notices and the pre-existing `ExperimentalCoroutinesApi` opt-in warnings (no new lines).
- [x] 6.2 Full gate through the `run-tests` skill with `--rerun-tasks`: both `:app` flavors, `:auto`, `:core`,
      `:osmscout-client-java`, plus `./gradlew :koverXmlReport` — all green, tallies recorded in the change.
      **Verify:** return code 0 and the recorded test tallies; any failure blocks the change.
      **Result:** `./gradlew test -PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 5m 47s`;
      `./gradlew :koverXmlReport` → `BUILD SUCCESSFUL in 43s`. Tallies (from `build/test-results/*.xml`, all
      0 failures / 0 errors): `:core` 532, `:auto` 792, `:app` mobileDebug 1862, `:app` automotiveDebug 1862,
      `:osmscout-client-java` 33.
- [x] 6.3 On-device phone measurement (emulator or phone, `device-check` skill), free drive with a feed of
      fixes: record the notification extras (`dumpsys notification --noredact` → `android.text`) next to the
      diagnostics status line, then press HOME and record `lease release: phone-map (held=0)`,
      `gps provider service: ProviderRequest[OFF]` and that the notification is unchanged.
      **Verify:** the recorded numbers show road equality between the label's status line and the shade text, a
      non-empty speed only when a speed is known (no trailing separator), and lease count zero in the
      background. Numbers go into the change, not a verbal "works".
      **Result** (emulator-5554, API 37, Andorra map, `com.framstag.naviveylin` mobile debug): diagnostics
      `Diag/FREEDRIVE: free-driving status: source=phone road=Cap del Carrer speed=0` next to
      `dumpsys notification --noredact` → `android.title=Freie Fahrt`, `android.text=Cap del Carrer · 0 km/h`
      (road equality with the label; speed present; no dangling separator). After `KEYCODE_HOME`:
      `LocationService: lease release: phone-map (held=0)` + `stopLocationUpdates: stopped`,
      `dumpsys location` shows the service state `ProviderRequest[OFF]`, and the notification is unchanged
      (`Freie Fahrt` / `Cap del Carrer · 0 km/h`).
- [x] 6.4 Pixel/geometry record for the map-side half of the claim: screenshot the free-driving map and read the
      street label's bounds from a UI dump in the same moment. State explicitly that the shade row is text-only
      (no pixel claim applies to it) and that a stationary emulator cannot produce a *changed* speed — the road
      equality is the measured property, the speed is measured as "present and equal to the status line".
      **Verify:** screenshot + dump stored under `.pi/logs/`, the label's road text equals the status line's
      road, and the limitation sentence is in the change.
      **Result:** `.pi/logs/device-check-163/free-drive-map.png` + `.pi/logs/device-check-163/ui-freedrive.xml`;
      the label node is `text="Cap del Carrer"` at `bounds="[401,2274][679,2337]"` (bottom-center), equal to
      the status line's road. Limitation: the shade row is text-only — no pixel claim applies to it — and the
      stationary emulator cannot produce a changed speed (`adb emu geo fix` supplies no speed), so the measured
      property is the road equality and the speed is measured as "present and equal to the status line".
- [x] 6.5 On-device car verification (device-gated): on the AAOS AVD (or a head unit), free driving with another
      car app foreground — the session lease keeps fixes flowing, so the notification must show live road/speed.
      **Verify:** notification extras change with the fix feed while the car app is not in front; if no car unit
      is available, record the task as device-gated with the exact reason instead of implying coverage.
      **Result:** device-gated — the only attached device is `emulator-5554`, a phone AVD (API 37); no AAOS AVD
      or head unit is available. The car-side publish path is covered by `FreeDrivingScreenStatusTest` and the
      session lease by `NavigationSessionLeaseTest` (`:app`); the device run remains open.
- [x] 6.6 Device-gated power measurement (physical phone only): the emulator cannot produce GNSS mA (no radio,
      no per-UID `gps=` term in API 37's power model). On a phone, record `Sensor GPS:` on-time and the per-UID
      power-model delta for a backgrounded free drive, as the counterpart to the 2026-10-07 baseline
      (`+124.9 s / 125 s`, `+0.0096 mAh` with the request active).
      **Verify:** either the numbers, or an explicit "device-gated: no phone available" note in the change —
      never an implied measurement.
      **Result:** device-gated — no physical phone is attached (`adb devices` shows only `emulator-5554`), and
      the emulator has no GNSS radio / no per-UID `gps=` term. The measured replacement (device run 6.3) is
      `ProviderRequest[OFF]` while the free drive is backgrounded — the device requests nothing.

## Workflow follow-up

- Implement this change with `/openspec-apply-change` (apply guidance in `openspec/config.yaml` governs the run).
- Archive after the review requirements are met; verify the archived result and that the three delta specs landed
  in `openspec/specs/`.
- TODO.md §46-style follow-up: none expected — 5.1 records the closure in the backlog.
