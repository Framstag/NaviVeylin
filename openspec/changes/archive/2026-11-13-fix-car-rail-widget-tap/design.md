# Design

## Context

Motivation and scope: see `proposal.md`. Requirements: see
`specs/navigation-ongoing-notification/spec.md`.

Current state that shapes the approach:

- `app/src/main/java/com/naviveylin/service/NavigationNotificationService.kt` builds the ongoing
  notification in `buildNotification()` (`:162-183`): one content intent
  (`PendingIntent.getActivity`, `:166-172`) targeting `openTargetActivity()` (`:196-202`), which
  returns `CarAppActivity` on automotive hardware and `MainActivity` otherwise. `buildNotification()`
  is evaluated inside `runGuardedNotification` in both `onCreate` (FGS contract, `startForeground`)
  and `render()` (content-changed post), so a throwing build degrades the post instead of killing the
  driving process.
- `app/src/main/java/com/naviveylin/service/NavigationNotificationBuilder.kt` is documented as
  "Pure assembly — the caller supplies the pending intents": `build()` (`:104`) takes
  `openIntent`/`stopIntent`, `carExtender()` (`:143-158`) adds the car text roles, the manoeuvre large
  icon and the stop action — no `setContentIntent`.
- Car-app 1.7.0 contract: `CarAppExtender.Builder.setContentIntent` — "If not set, the notification's
  content intent will be used. In the case of navigation notifications in the rail widget, this intent
  will be sent when the user taps on the rail widget."
  `androidx.car.app.notification.CarPendingIntent.getCarApp(context, requestCode, intent, flags)`
  takes an intent whose component is the app's `CarAppService`, validates it
  (`PackageManager.getServiceInfo`), unsets `FLAG_IMMUTABLE` (the host adds extras), then branches:
  projection → broadcast to `androidx.car.app.notification.CarAppNotificationBroadcastReceiver`
  (merged from the `app` AAR; the host stamps its `IStartCarApp` binder, the receiver calls
  `host.startCarApp(intent)`), AAOS → activity intent with the component rewritten to
  `CarAppActivity`.
- Manifest state is already sufficient: `NaviVeylinCarAppService` (`androidx.car.app.CarAppService`,
  category NAVIGATION), exported `androidx.car.app.activity.CarAppActivity` with
  `distractionOptimized`, and the `queries` entries for the host packages.
- The car session treats an incoming intent as a deep link only:
  `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` (`onCreateScreen` `:296`,
  `onNewIntent` `:333` → `handleDeepLink`); a bare `ACTION_VIEW` yields no destination, the root map
  screen stays, and the navigation screen is pushed by the session's existing state observer. Each
  session event is logged (`SessionLog`, diagnostics tag `SESSION`).
- Layering/threading rules: `guidelines/Design.md` §1 (module layering) and §8 (host-facing paths
  fault-confined, host traffic bounded); notification surfaces and parity: `guidelines/UI.md` §1.

## Goals / Non-Goals

**Goals:**

- The rail-widget turn hint returns the app to the car foreground on projection and on AAOS.
- One car tap path for both car platforms; the phone tap path stays exactly as it is.
- No new manifest, resource, dependency or `:auto`/`:core` code change.

**Non-Goals:**

- Carrying a destination or navigation payload in the car tap intent.
- Any car surface for free driving (no hint, no tap target).
- Changing phone notification content, channels or the `Stop` action path.
- Making the tap resume a session that the host never started (host behavior, not ours).
- Any native/JNI change.

## Decisions

### D1 — Car tap target is `CarPendingIntent.getCarApp` with the `CarAppService` component

`CarAppExtender.Builder.setContentIntent(CarPendingIntent.getCarApp(context, 0,
Intent(ACTION_VIEW).setComponent(ComponentName(context, NaviVeylinCarAppService::class.java)), 0))`.

- Chosen: it is the documented way to make a notification tap start a car app, and it encodes the
  per-platform mechanism itself (projection → host-mediated `startCarApp`; AAOS → the
  `CarAppActivity` launch), so the app does not have to know which car platform it is on. The
  `CarAppService` component is what its validator requires (a service of our own package).
- Alternative A — keep the plain activity intent for AAOS and use the phone intent elsewhere
  (today's behavior): rejected, it is exactly the reported defect on projection.
- Alternative B — `PendingIntent.getActivity(Intent(context, MainActivity::class.java))` for both
  surfaces: rejected, a car host cannot show a phone activity; that is why the tap looks dead.
- Alternative C — own broadcast receiver plus app-side "start car app" logic: rejected, outside a
  car session there is no `CarContext` to start the app with, and `CarContext.startCarApp(Intent,
  Intent)` is deprecated and explicitly not supported on AAOS.
- Alternative D — `CarContext.startCarApp` from the session: rejected, the tap is precisely the case
  where no session exists; also AAOS-unsupported.

### D2 — Bare `ACTION_VIEW` intent, no payload

- Chosen: the user asked to return to the app, not to restart guidance. A payload intent would have
  to survive `handleDeepLink`'s parsing and could re-trigger a route computation mid-drive; the map /
  navigation screen is already selected by the session's state observer.
- Alternative A — `Intent(CarContext.ACTION_NAVIGATE, geo:…)`: also accepted by `CarPendingIntent`,
  but it re-enters the app as a new navigation request, which is a different feature (`auto-deep-links`).
- Alternative B — an intent extra naming the target screen: rejected, it would put screen selection
  into the notification path and duplicate the observer's job.

### D3 — One car path for projection and AAOS

The `isAutomotive` branch disappears from the car tap target: `openTargetActivity()` remains only for
the phone content intent and collapses to `MainActivity`. The notification service keeps its existing
`AutomotiveDevice.isAutomotive()` use for the channel choice.

- Chosen: one code path, no app-owned knowledge of the library's `CarAppActivity` component rewrite,
  and the AAOS branch gets the same host-mediated contract as projection.
- Alternative A — migrate only projection, leave AAOS on the hand-built `CarAppActivity` intent:
  rejected, two mechanisms for one behavior, and the hand-built one bypasses the library contract that
  the extender's tap target is meant to follow.
- Risk of the chosen option is covered under Risks (the component rewrite is library behavior verified
  in car-app 1.7.0 and re-checked on an AAOS target).

### D4 — The service builds both intents; the builder stays pure assembly

`NavigationNotificationBuilder.build(...)` gains a nullable `carOpenIntent: PendingIntent?`, passed
through to `carExtender(...)` where it becomes `setContentIntent`. The service constructs both
PendingIntents in `buildNotification()`.

- Chosen: matches the builder's documented contract ("the caller supplies the pending intents") and
  keeps the Car-App-library component validation out of the builder's unit tests; the service already
  owns every `PendingIntent` and its request codes.
- Alternative A — build the `CarPendingIntent` inside `carExtender(context, …)`: fewer parameters, but
  the builder would reference the `CarAppService` class and its "pure assembly" testability (Robolectric
  tests assert the extender's content, not its construction) would erode.
- Alternative B — inject a `(Context) -> PendingIntent` factory for testability: rejected as
  indirection without a second implementation; the builder test asserts the extender carries the
  intent it was given, and the service-level test covers construction.

## Risks / Trade-offs

- [AAOS behavior change: the car tap no longer targets `CarAppActivity` directly] → the library
  rewrites the component for an intent whose component is a service of our package
  (`CarPendingIntent.createForAutomotive`); re-checked on the automotive flavor, and the rollback is
  a revert of two files.
- [`CarPendingIntent.getCarApp` throws (`InvalidParameterException`, `SecurityException`) if the
  component or the manifest declaration is wrong] → the call sits inside `buildNotification()`, which
  both `onCreate` and `render()` already run inside `runGuardedNotification`; the failure mode is the
  previous behavior (no car tap) plus a `W` log, never a dead driving process
  (`guidelines/Design.md` §8).
- [The phone tap regresses while the car tap is added] → the two intents are separate objects; the
  builder test asserts the phone content intent still targets `MainActivity` and differs from the car
  one.
- [An extra `PackageManager` lookup on the main thread per post] → the lookup happens only when the
  host-visible content changed (the same cadence as the post itself), and the existing build already
  creates two PendingIntents per post; the alternative (caching the intent) would have to be
  invalidated on session changes for no measurable gain.
- [Host session restart after the tap is slow (native warmup)] → unchanged from any other car launch:
  the session serves its root screen immediately and the warmup path is already diagnosed under the
  `WARMUP`/`SESSION` tags.
- [A tap while the app is already the visible car app may deliver nothing (host decision)] → no
  requirement depends on it; recorded as an open question, not a task.

## Migration Plan

Single-step change, no data migration:

1. Builder: add the nullable car intent parameter and the `setContentIntent` call.
2. Service: construct the car tap target with `CarPendingIntent.getCarApp`, pass it, drop the
   `isAutomotive` branch of `openTargetActivity()`.
3. Tests: extend `NavigationNotificationBuilderTest` (car intent present and distinct from the phone
   intent; phone intent unchanged; no hint → no car intent).
4. Docs: `guidelines/UI.md` §1 tap-target bullet; on-device verification.

Rollback: revert the two Kotlin files plus the spec and guideline deltas — the notification returns to
its previous phone-only tap target with no other behavior change.

## Verification

- Unit (`:app`, Robolectric): the car extender of a NAVIGATION notification carries a content intent
  distinct from the phone one; a free-driving notification is not extended at all; the phone content
  intent still targets `MainActivity`; existing notification tests stay green.
- Unit (`:app`, Robolectric, service-level): the car tap target the service builds does not throw and
  is the app's own intent (broadcast on projection), i.e. the `CarAppService` component passes the
  library's validation with the merged test manifest.
- On device, phone projecting to a head unit: start navigation, switch to another car app, tap the
  rail widget → the car screen returns to the app with guidance still running;
  `adb logcat -s Diag/SESSION Diag/HOST` shows the session reacting to the incoming intent and no
  `HOST` rejection.
- On device, AAOS emulator/head unit (automotive flavor): same tap returns to the app (D3 re-check).
- Phone-only regression check: tapping the notification in the phone shade still opens the phone UI.

## Open Questions

- Whether a tap on the rail widget while the app is already the visible car app delivers anything to
  the app is host-defined and unverified; no requirement or task depends on it.
