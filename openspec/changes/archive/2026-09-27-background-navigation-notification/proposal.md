## Why

When NaviVeylin is moved into the background in NAVIGATION or FREE_DRIVE mode (another app foregrounded — on the phone, or on the AAOS head unit / Android Auto), there is no visible indication that driving guidance is still running. Nothing keeps the process alive either: without a foreground service the navigation can be silently killed by the OS in the background. Users lose both visibility and survival of active guidance.

## What Changes

- New foreground service (`NavigationNotificationService`, type `location`) that keeps the process alive while a driving mode is active and carries an ongoing, silent notification.
- New app-module singleton controller (`NavigationNotificationController`) that observes the shared navigation state plus a surface-independent free-driving flag and starts/stops the service on mode transitions (NAVIGATION or FREE_DRIVE active → started; BROWSE → stopped).
- Notification content rendered from `NavigationState`:
  - **NAVIGATION**: destination/"Navigation active" title, next manoeuvre instruction + street, "in `<distance>` · arrive `<ETA>` · `<remaining>` remaining"; a `Stop` action that ends navigation; tap opens the app.
  - **FREE_DRIVE**: current street/ref + speed; no destination-dependent guidance; tap opens the app.
- New `DrivingModeProvider`-style shared flag so FREE_DRIVE state survives when the source surface dies (phone ViewModel cleared / AA session destroyed) — the trigger must not depend on which surface started driving. (Exact placement: extend `NavigationStateProvider` vs separate singleton — see design.)
- Manifest: `FOREGROUND_SERVICE_LOCATION` permission, service declaration with `foregroundServiceType="location"` (main manifest applies to both `mobile` and `automotive` flavors), new `navigation` notification channel.
- New mono vector small icon for the notification; reuse of the existing notification-permission flow (`ui/mapmanager/NotificationPermission.kt`).
- Notifications are shown whenever a driving mode is active, foreground or background — Google Maps convention; no lifecycle pop-in/pop-out. This is additive; nothing existing changes behavior.

## Capabilities

### New Capabilities
- `navigation-ongoing-notification`: an ongoing foreground-service notification that stays visible while NAVIGATION or FREE_DRIVE is active on phone, Android Auto (projection), and Android Automotive OS; shows live guidance content, provides a stop action and app-return tap, and keeps the process alive in the background. (Parallel scope: phone + auto surfaces; label/hierarchy parity per guidelines/UI.md.)

### Modified Capabilities
- None — no existing requirement texts change. Mode derivation (`map-modes`), navigation state, and AA lifecycle specs stay untouched; this change adds a new consumer of existing state.

## Impact

- **App module**: new files `service/NavigationNotificationService.kt`, `navigation/NavigationNotificationController.kt` (or `data/`), shared free-driving flag wiring; `MainActivity`/`MapCanvasViewModel` untouched except follow-mode flag publication; `NavigationStateProvider` gains the free-driving mirror (surface-independent).
- **Auto module**: session (`MapScreen`/`FreeDrivingScreen`/`NavigationSession`) publishes follow/free-drive state into the shared flag instead of keeping it session-private.
- **Manifest** (`app/src/main/AndroidManifest.xml`): +`FOREGROUND_SERVICE_LOCATION`, +service decl (both flavors inherit via main manifest; the automotive overlay only overrides the automotive feature).
- **Resources**: new `res/drawable/ic_nav_notification.xml` (mono vector), new channel `navigation`.
- **Permission flow**: notification permission dialog reuse — channel-permission handling already patterned in `ui/mapmanager/NotificationPermission.kt`.
- **Battery**: no new GPS load — location keeps flowing from the existing `LocationService`; FGS `location` type additionally legitimises background "while-in-use" location continuation.
- **Guidelines**: `guidelines/Design.md` §9 already sanctions foreground services for long-running work; no contradiction. Check `guidelines/UI.md` for notification label conventions during design.
- **Existing specs touched**: none (verified against inventory — no notification/background capability exists).

## Out of Scope / Known Limits

- Activity destroyed in background → ViewModels cleared → navigation stops today; FGS does not fix that (moving navigation out of Activity scope is a separate, larger change).
- `POST_NOTIFICATIONS` denied (API 33+): shade may hide the notification; FGS exemption behaviour to verify in design.
- A killed process (`START_NOT_STICKY`) is never resurrected with a lying "still navigating" notification — navigation is already dead in that case.
