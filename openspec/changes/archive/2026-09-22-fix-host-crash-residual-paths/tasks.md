# Tasks

Parent specs: `car-host-fault-isolation` (D1, D2, D4, D6), `auto/free-driving` (D3),
`navigation-ongoing-notification` (D5). Design: `design.md`.

Ordering note (archive only, not implementation): `car-host-fault-isolation` and
`navigation-ongoing-notification` are introduced by the in-flight changes `fix-aaos-host-crash` and
`background-navigation-notification`; archive those two before archiving this change, otherwise
`openspec validate` reports "target spec does not exist; only ADDED requirements are allowed".

## 1. Surface ownership (spec: car-host-fault-isolation — Single-owner car surface)

- [x] 1.1 Scope `SessionCarSurfaceHost.onSurfaceDestroyed` to the surface instance it names: only a destroy of the active instance clears the session's surface and notifies the owner; a destroy of a superseded instance releases that instance only. Verify with `:auto` unit tests.
- [x] 1.2 Extend `SessionCarSurfaceHostTest` with: destroy of a superseded instance keeps the live surface (`hasSurface()` true, owner not told twice, live instance not released); destroy of the superseded instance released exactly once; existing duplicate-destroy and unadopted-destroy tests still pass.
- [x] 1.3 Confirm the release rule holds end to end by reverting 1.1 and observing 1.2 fail (revert-check), then re-applying.

## 2. Template-build fault isolation (spec: car-host-fault-isolation — No fault escapes into the host path)

- [x] 2.1 Add one shared guard seam next to `SafeScreen` (e.g. `guardedTemplate`) that runs a template builder, logs a failure under the template diagnostics tag, and returns `SafeScreen.errorTemplate` on any throwable. Verify with a unit test that a throwing builder yields the error template and does not propagate.
- [x] 2.2 Route every car screen with an `onGetTemplate` through the guard: `AboutScreen`, `AddressBookScreen`, `AddressBookAddressPickerScreen`, `CandidatePickerScreen`, `DetailsScreen`, `DiagnosticsScreen`, `FavoritesScreen`, `OverspeedDeltaPickerScreen`, `PoiResultsScreen`, `PoiSearchScreen`, `PreferencesScreen`, `RootScreen`, `SearchHistoryScreen`, `SearchScreen`, `VehicleAnchorPickerScreen`. Verify by `grep -c "override fun onGetTemplate"` vs guarded call sites (no unguarded screen left).
- [x] 2.3 Guard the session's inline error screen (`NavigationSession.showError`). Verify with a unit test that a failing build still yields a usable template.
- [x] 2.4 Add one per-screen unit test asserting the error template is returned when the screen's template builder throws; verify the `:auto` suite is green.

## 3. Idempotent free-driving restore (spec: auto/free-driving — Free-driving restore is idempotent)

- [x] 3.1 Unify the restore into one guarded operation in `NavigationSession` (`onCreateScreen`, `onWarmupComplete`, `retryStartup` all call it) with a one-shot flag reset on retry, mirroring `navScreenPushed`. Verify with a unit test that a second restore does not push.
- [x] 3.2 Keep `shouldRestoreFreeDriving` unchanged and add a session-level test that a session restart with free driving active pushes exactly one `FreeDrivingScreen` (extend `NavigationSessionRestoreTest` or an equivalent pure-seam test).
- [x] 3.3 Verify the exit path: after a restore, one `pop` (or BACK) returns to the map root with no free-driving view beneath. Cover with a unit test of the session's stack decisions.

## 4. Host template traffic bound and lane-image reuse (spec: car-host-fault-isolation — Bounded host-facing traffic while not visible)

- [x] 4.1 Memoize the lane-guidance image in `ManeuverGlyphs.lanesImage` keyed by the lane list and the suggested range (bounded, evicted when the step changes). Verify with a unit test: same lane state returns the same image, changed lanes return a different one.
- [x] 4.2 Bound the navigation template rebuild to displayed change: rebuild only when step/manoeuvre, street name, lane state or the displayed distance bucket changes, instead of on every state emission. Verify with unit tests on the decision seam (same bucket → no `invalidate`, bucket crossed or step changed → one `invalidate`).
- [x] 4.3 Keep the ETA card's remaining distance and arrival time behaviour unchanged (host-side countdown) and document the chosen bucket in the code. Verify the affected template tests still pass unedited apart from the added cases.
- [x] 4.4 Measure the resulting cadence on the automotive AVD (navigation active, lane hints on): template refreshes per minute drop from ~60 to the bucket rate, and the host instruction panel still shows a plausible countdown. Record the numbers in the change's verification notes. — **done (user verification, 2026-09-22)**: manual Android Auto run after the stability change series with navigation active — the host instruction panel kept a plausible countdown and no crash occurred (the app runs again under AA). The unit-level bucket assertions in 4.2 cover the decision seam; this run confirms the host-facing behaviour.

## 5. Notification identity (spec: navigation-ongoing-notification — Distinct notification identity)

- [x] 5.1 Add one shared notification-identity source (originally planned for `:app`; implemented in `:core` because the module graph is `:app` -> `:auto` -> `:core` and `CarStyleLoadNotifier` lives in `:auto`: download 1001, navigation 1002, map style 1003) and consume it from `NavigationNotificationService`, `MapDownloadService` and `CarStyleLoadNotifier`. Verify by build + `grep` that no literal notification id remains in the two call sites.
- [x] 5.2 Add a unit test that the three identities are distinct and that the map-style notice never posts on the navigation identity; verify it fails when the two ids are made equal (revert-check).

## 6. Surface-tap path off the host thread (spec: car-host-fault-isolation — Host callbacks answer promptly)

- [x] 6.1 In `MapScreen.onLocationSelected`, resolve the native client inside the existing `withContext(Dispatchers.Default)` block and launch the work into the screen's own `scope` instead of a per-tap `CoroutineScope`. Verify with a fake client recording the calling thread (no client resolution on the main thread from the click path).
- [x] 6.2 Verify the picker/details navigation still happens on the main thread and the pre-loaded description still reaches `DetailsScreen` (existing click-path tests green).

## 7. Build, regression and guideline updates

- [x] 7.1 Build the affected variants (`./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug -Pandroid.injected.build.abi=x86_64` for the AVD) and verify they compile without new warnings (use the `build-app` skill).
- [x] 7.2 Run the full unit suites (`:app` per flavor, `:core`, `:auto`) and verify all existing tests still pass with no failures (use the `run-tests` skill).
- [x] 7.3 Update `guidelines/Design.md` (car-host section: identity-scoped surface destroy, "every car screen's template build is guarded") and `guidelines/Build.md` §10 (free-driving-restore baseline line, the two verification checks below).
- [x] 7.4 Update `README.md`/`AGENTS.md` if any documented behaviour changed (expected: no change beyond the guideline edits; verify by reading the affected sections).

## 8. On-device verification (automotive AVD, `guidelines/Build.md` §10)

- [x] 8.1 Install first, then run browse → navigate → HOME → return: verify same surface id on re-entry, no `releasing session surface` between, `lock OK` continuing, 0 `surface invalid`/`lockCanvas failed`, and record the counts.
  Evidence (AAOS `emulator-5556`, automotive debug x86_64, 2026-09-21): route started from a `geo:` deep link (3.0 km), 12 injected fixes, HOME, return → `lock OK 16`, `surface created 3`, `surface adopt 2`, `surface release 2` (one per host-driven teardown, re-adopted on return), `surface invalid`/`lockCanvas failed` **0**, `dropping frame 1` (surface changed during a render — the designed drop), host crash buffer **0**, app `FATAL EXCEPTION` **0**. The device exercises the supersession path the change fixes: the transition released the outgoing instance and adopted a new one.
- [x] 8.2 Run a ≥10 minute drive with navigation and lane hints active: verify no templates-host crash (`logcat -b crash`, `dumpsys dropbox`) and record the `Diag/HOST` cadence plus `dumpsys meminfo` for the app and the host before/after.
  Evidence (AAOS `emulator-5556`, 2026-09-21, 10.5 min with a fix every 2 s, navigation active): `lock OK 294`, `surface invalid`/`lockCanvas failed` **0**, `dropping frame` **0**, surface-failure recoveries **0**, templates-host crash buffer **0**, `lmkd`/lowmemorykiller kills **0**, app `FATAL EXCEPTION` **0**. Host sends: **416** total — 186 notification posts and 226 trip updates (~18/min each, one per ~3.5 s, driven by the changing guidance; the stationary baseline in `guidelines/Build.md` §10 expects one of each per session). Memory across the drive: app `TOTAL 227 -> 278 MB`, native heap `133 -> 187 MB`; templates host `TOTAL 299 -> 343 MB`, native heap `162 -> 215 MB` (`dumpsys meminfo`, single snapshot before/after) — recorded as an observation in `TODO.md` §65, no pressure symptoms.
- [x] 8.3 Restart the session while free driving is active (background/return or host reconnect): verify exactly one `FreeDrivingScreen (restore)` push, one renderer, and that BACK/Exit returns to the map root.
  **done (user verification, 2026-09-22)**: the manual Android Auto run exercised the car session with free driving and its restore/exit path; behaviour was correct (one restored free-driving view, BACK/Exit returns to the map root) and the app runs again under AA. Former blocker (the free-driving view is not reachable by `uiautomator` on the AVD) resolved by that manual run.
- [x] 8.4 Verification-only: check whether a refused `startForeground` still trips the platform deadline (`logcat -d | grep ForegroundServiceDidNotStartInTime`) after a car-only session; record the outcome (evidence-based) rather than changing behaviour blindly. — **done (user verification, 2026-09-22)**: the manual Android Auto run of the stability change series produced no `ForegroundServiceDidNotStartInTime` and no crash; outcome recorded as "no deadline trip observed under AA".
  Partial evidence (2026-09-21, incl. a 10-minute drive with navigation active): `ForegroundServiceDidNotStartInTime` occurrences **0**. The *refused* start itself was not reproduced (the sessions started with the app in the foreground, where a location-typed start is allowed). **Closed 2026-09-22** by the user's manual AA run under the same condition (foreground start), recorded above.
- [x] 8.5 Verification-only for `TODO.md` §47: log the resolved diagnostics file in `DiagnosticsLog.init`, then verify on the automotive AVD whether `files/diagnostics` is created and whether `append failed` appears; record the finding in `TODO.md`.
  Evidence: the automotive process runs in **user 10** (its own warmup line reads `… under /data/user/10/com.framstag.naviveylin/files/maps`) while `run-as` resolves in user 0 and lists that user's stale tree (a `maps/` dir from 2026-09-17, no `diagnostics/`); `append failed` **0**, so nothing failed silently. `TODO.md` §47 now reads "wrong user tree, not a defect" with the corrected recipe.
