## Context

See proposal.md — Why. Current state relevant to the approach:

- Only existing notification code: `MapDownloadService` (FGS, `foregroundServiceType="dataSync"`, `START_NOT_STICKY`) and its permission flow (`ui/mapmanager/NotificationPermission.kt`). No navigation notification exists.
- `NavigationStateProvider` (app-module `@Singleton`, core `NavigationViewModel` interface) already mirrors live nav state (`isNavigating`, `nextInstruction.description`, `remainingDistance`, `etaMillis`, `destinationName`, `currentRoadInfo`, `currentSpeedKmH`) for both surfaces.
- Map mode derivation (`app/.../map/MapCanvasViewModel.kt:mode`): NAVIGATION = `isNavigating`; FREE_DRIVE = `followMode || driveSuspended`; else BROWSE. Phone free-driving lives only in the phone ViewModel; AA free-driving lives only in the auto session's screen stack (`FreeDrivingScreen` pushed from `MapScreen`). Both die with their owner — neither is visible to an app-module controller, and neither survives a surface death.
- GPS: `LocationService` (app-module singleton) keeps streaming fixes regardless of which surface drives; `OSMScoutClient` singleton stays alive with the process. These keep notification content fresh under a foreground service without new location machinery.
- `:auto` depends on `:core`; `:app` depends on `:auto`. App-module singletons are unreachable from auto code — shared plumbing must go through `:core` interfaces resolved via the existing `AutoEntryPoint` entry-point pattern (same as auto-favorites/auto-settings).

## Goals / Non-Goals

**Goals:**
- One mechanism (foreground service + ongoing notification) covering phone, AA projection, AAOS.
- Notification rendered from the existing `NavigationState` flow; no second navigation data path.
- Free-driving trigger that does not depend on which surface is alive (phone VM or auto session).
- Content formatting and lifecycle decisions pure and unit-testable.

**Non-Goals:**
- Moving navigation out of Activity/ViewModel scope. If the Activity is destroyed in the background, its VMs (and with them the native controller) are cleared — navigation stops today; this change does not resurrect it (the FGS keeps the process alive, which makes Activity destruction under memory pressure far less likely, but does not bind navigation to the process).
- Restoring a *routed* navigation after process death (route + native controller are in-memory only; re-route would need persisted route state — separate change).
- Turn-by-turn *audio* announcements (not requested; notification is silent by design).
- A "free driving" stop action in the notification (exiting follow mode from the shade is cross-VM and ambiguous on a route-less drive; tap-to-open suffices).

## Decisions

### D1: Foreground service of type `location` (vs plain notification / other FGS types)

Chosen: dedicated `NavigationNotificationService`, `foregroundServiceType="location"`, started while NAVIGATION or FREE_DRIVE is active, `START_NOT_STICKY`, declared in the main manifest (applies to both `mobile` and `automotive` flavors).

- **Alternative: plain notification, no service** — trivial, but the process stays killable in the background, so the notification silently disappears *and* navigation silently dies (the very problem). Rejected.
- **Alternative: reuse `MapDownloadService` / `dataSync` type** — wrong semantics: Play review flags `dataSync` for navigation, and it does not legitimise background location continuation. Rejected.
- **Alternative: `specialUse` FGS type** — requires manual Play review and justification, heavier process. Rejected; `location` is the honest fit for a navigation app.

Rationale: navigation is the canonical `location` FGS (Google Maps/Waze pattern); `FOREGROUND_SERVICE_LOCATION` is a normal permission (no runtime prompt); the type also keeps "while-in-use" background location flowing (no background GPS throttling while driving).

Constraints verified against platform rules:
- API 31+/34+: an FGS can only be **started** while the app is in the foreground. Driving modes only begin while a surface is visible (nav start / follow toggle on phone, free-driving entry on the head unit) — satisfied. Deep-link-started navigation from the car: the car session is the foreground surface on AAOS; projection keeps the phone in the "in use" state via the host binding. Android 14+ car apps may start a location FGS while their session is foreground — verify on device (task).
- `START_NOT_STICKY`: Android may restart a killed FGS; with no in-memory navigation state that restart must not post a lying "navigating" notification. The service self-stops if its observed driving state is gone (spec: "No lying notification after a kill").

### D2: Notification visible whenever a driving mode is active (foreground included)

Chosen: notification shown during NAVIGATION/FREE_DRIVE regardless of app visibility; removed only when the mode ends.

- **Alternative: background-only** — matches the letter of the complaint but pops in/out on every app switch and gates process protection on lifecycle detection. Rejected (also: lifecycle-based show/hide races with state-driven FGS).
- **Alternative: split (nav always-on, free-drive background-only)** — adds a second trigger condition for a marginal UX gain. Rejected for v1; trivial to revisit later via the same controller.

Trade-off accepted: an ongoing notification is visible while free-driving even when parked (Maps shows nothing there). It is silent and low-importance; the free-driving content (street + speed) is truthful.

### D3: Content pipeline — controller observes state, service is a rendering shell

Chosen: `NavigationNotificationController` (`@Singleton`, app module) combines `NavigationStateProvider.state` + the free-driving flag; a pure formatter maps `(mode, NavigationState)` → notification fields; on driving start the controller starts the service (state snapshot in the start intent); the service (Hilt `@AndroidEntryPoint`) injects the same flows itself, re-renders on every state emission, and calls `stopSelf()` when the driving state clears (belt-and-braces with the controller's `stopService`).

- **Alternative: service collects nothing; controller pushes every update via `startService` intents** — more moving parts, intent marshalling of structured data, harder to keep consistent with the source of truth. Rejected. The service observing the flows keeps it a thin renderer of the single source of truth.
- **Alternative: controller owns both start/stop and all rendering, service fully dumb** — fine too; the difference is where formatting lives. Formatter is a standalone pure object either way so unit tests don't need Robolectric.

Threading/lifecycle (Design.md §4): all collections on `Dispatchers.Main` (state emission rate is fix-cadence, ~1 Hz — negligible); formatting is pure and allocation-light; no work queue, no wake locks (location FGS needs neither). The controller and service are `@Singleton`/service-scoped — independent of Activity lifetime, so deep-link-started (car-only) navigation still starts the FGS.

### D4: Surface-independent free-driving flag

Chosen: `DrivingModeProvider` — a `:core` interface + app-module `@Singleton` implementation with a `StateFlow<Boolean> freeDrivingActive`, published by the phone `MapCanvasViewModel` (`mode == FREE_DRIVE`) and by the auto session (when `FreeDrivingScreen` is on the stack / until explicitly exited). Exposed to auto via the existing `AutoEntryPoint`.

- **Alternative: controller observes `MapCanvasViewModel` directly** — phone-only; AA free-driving unreachable (its state dies with the session). Rejected.
- **Alternative: extend `NavigationStateProvider`/`NavigationState` with the flag** — muddles the nav-mirror contract (`NavigationState` is the AA bridge for route guidance); free-driving is not navigation state. Rejected.
- **Alternative: session-local auto flag + no phone sharing** — breaks the exact AA free-driving-in-background scenario the user asked for. Rejected.
- **Semantics: OR-combined, retained on surface death.** A surface publishes its *current* follow/free-driving state; when it dies mid-drive (Activity destroyed, session destroyed) the last value is retained — destroying a surface must not claim "driving stopped". The only lingering-false case (phone opens in BROWSE while the car free-drives) is actually correct: the car is still free-driving.

### D5: Auto session restore on recreate while the flag is active

Chosen: when a (re)created auto session finds `freeDrivingActive == true` (i.e. it died mid-free-drive), `NavigationSession` re-enters the free-driving view instead of the map (extend `initialScreen()`; nav restore already exists for `isNavigating`).

- **Alternative: clear the flag on session destroy** — kills background continuation: the head-unit shade would lose the notification the moment the user switched apps. Rejected.
- **Alternativе: persist free-driving into settings** — overkill; the in-process flag survives under the FGS anyway.

### D6: Notification permission degradation

Chosen: reuse the existing `NotificationPermission.kt` request flow for the new `navigation` channel. On denial: FGS still runs (process protection and guidance continue), notification may be hidden from the shade; no crash.

- Expected exemption on API 33+: foreground-service notifications are exempt from the `POST_NOTIFICATIONS` gate and still appear while the service runs — verify on device; if the head unit/host suppresses them, the service still provides silent process protection.

### D7: Small icon

Chosen: new mono vector `res/drawable/ic_nav_notification.xml` (simple direction/arrow glyph, flat). Alternatives rejected: reuse launcher foreground (colored/scaled — Android rejects non-mono small icons), stock `android.R.drawable` (style mismatch with M3).

### D8: Stop commands broadcast instead of routing to one source (2026-09-20, defect correction from on-device task 4.1)

Chosen: an "end navigation" request broadcasts through the `:core` seam `NavigationStopRequests` (`stopRequests` flow + `requestStop()`), which the shared `NavigationStateProvider` implements. Emitters are the foreground service (notification stop action / car hint action) and the provider's `stopNavigation()` facade the car host calls; handlers are the surface controllers — the phone `NavigationViewModel` and the car `AANavigationController` collect it and stop their own navigation, and stopping an already-idle controller is a no-op. A controller's own `stopNavigation()` must not emit (that would loop through its own collector).

- **Alternative: the original single callback slot** — rejected after the on-device failure (`TODO.md` §46): `NavigationStateProvider.observe()` overwrote one `stopCallback` per registration, and `AANavigationController` is instantiated on every car-session warmup (`NavigationSession` — "Activating navigation controller"), so a car session starting after the phone app owned the slot and the phone notification's stop action stopped only the idle car controller.
- **Alternative: fan out to every registered source's `stopNavigation()` inside the provider** — same effect, but the provider would keep owning commands and could not separate emitters from handlers; the flow is testable without Android and leaves the provider a mirror plus a seam.

Same correction fixes the mirror half of the defect: the provider keeps a per-source registry and publishes the **navigating** source's state (an idle registrant's empty `NavigationState` could blank live navigation and trip the notification's self-stop gate). A source unregisters when its owner dies (phone `ViewModel.onCleared`), so a dead surface cannot keep a driving state — or the notification — alive, consistent with the "no lying notification" limit in the proposal.

Residual, recorded not fixed: `navigateTo` / `reportError` are still mirrored to the last registrant (`TODO.md` §46) — the same last-wins shape, but their mis-routing does not drop a user action (both controllers can route with the same client), so the stop path was corrected first.

## Risks / Trade-offs

- [Battery: notification + FGS while driving] → No new GPS load (location already streams); FGS overhead is minimal; notification is silent and low-importance. Free-drive notifications while parked are the accepted trade-off of the always-on choice (D2).
- [Activity destroyed in background clears VMs → navigation stops even with FGS alive] → Pre-existing limitation, documented in the proposal; FGS makes it rare. Full fix (navigation owned by a process-scoped scope) is a separate change.
- [Process killed → navigation dead, no notification] → `START_NOT_STICKY` + self-stop = truthfulness (spec: "No lying notification"). This matches today's behavior, just without the lie.
- [Two surfaces sharing one free-driving flag could desync on edge sequences (open phone BROWSE while car free-drives)] → OR-combined semantics is correct by construction; the only visible effect is the notification staying while the car free-drives.
- [API 34+ location-FGS start restrictions / car-app exemption] → Verify on emulator/head unit in the task list; all start paths begin in the foreground on some surface, so a documented exemption is not required for the happy path.
- [POST_NOTIFICATIONS denial hides the indicator] → Accepted degradation; FGS still protects. Re-request handled by the existing permission flow.

## Migration Plan

- Fully additive: new files (`NavigationNotificationService`, `NavigationNotificationController`, `DrivingModeProvider` interface + impl, formatter, icon), manifest additions (`FOREGROUND_SERVICE_LOCATION`, service declaration, channel), publishing hooks in `MapCanvasViewModel` and auto session, restore branch in `NavigationSession.initialScreen()`.
- No existing spec or behavior changes; no data migration.
- Rollback: revert the change; the service/permission disappear, app returns to today's behavior. No stored state to clean up.

## Open Questions

- None blocking. Two verify-on-device items (car-app location-FGS start; FGS notification exemption under denied permission) are explicit tasks, not design inputs.
