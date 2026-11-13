# Proposal — fix-car-rail-widget-tap

## Why

While navigating, the driver switches to another car app on Android Auto (phone projecting to a head
unit). The current manoeuvre stays visible in the rail widget at the bottom of the car screen, but
tapping it does nothing — the app never comes back to the car foreground. Other apps behave
differently (a music player returns to the foreground), so the rail widget looks broken rather than
restricted. Reported on device: phone + head unit under Android Auto (projection), 2026-09-26.

Cause is visible in the code, no device instrumentation needed:

- `NavigationNotificationService.buildNotification()` (`:162-183`) sets exactly one content intent on
  the notification: `PendingIntent.getActivity(…, openTargetActivity())` (`:166-172`), and
  `openTargetActivity()` (`:196-202`) returns `CarAppActivity` **only** on automotive hardware
  (`AutomotiveDevice.isAutomotive(this)`), otherwise `MainActivity`.
- `NavigationNotificationBuilder.carExtender()` (`:143-158`) extends the notification with a
  `CarAppExtender` (title, text, large icon, stop action) but never calls
  `CarAppExtender.Builder.setContentIntent`.
- Library contract (car-app 1.7.0, `CarAppExtender.Builder#setContentIntent` javadoc): *"If not set,
  the notification's content intent will be used. In the case of navigation notifications in the rail
  widget, this intent will be sent when the user taps on the rail widget."*

So on projection the host fires `MainActivity` — a **phone** activity: it starts on the phone (which
is showing the Android Auto UI) and the car screen never switches. The car app itself is never asked
to come forward, which is why the tap looks dead. Music players differ because their notifications
are host-owned, not app-extended.

The in-flight capability text already claims this works: `navigation-ongoing-notification` — "Return
to the app from the notification" (change `background-navigation-notification`, not yet archived)
says tapping brings "the phone UI, **or the car navigation view** on an automotive device" back to
the front. For the car surface that is unimplemented today.

Doing this now: one small seam in the notification builder closes it for both car platforms, and the
change is a prerequisite for any car-side "return to guidance" flow.

## What Changes

- **Give the car extender its own tap target.** `NavigationNotificationBuilder.carExtender()` SHALL
  set `CarAppExtender.Builder.setContentIntent(carOpenIntent)`; the phone notification keeps
  `MainActivity` as its content intent.
- **The car tap target SHALL be a `CarPendingIntent`** (`androidx.car.app.notification.CarPendingIntent.getCarApp`)
  built from a bare `Intent(ACTION_VIEW)` whose component is `NaviVeylinCarAppService`, not a plain
  activity intent and not the phone's. The library branches internally: projection → broadcast to
  `androidx.car.app.notification.CarAppNotificationBroadcastReceiver` (the host stamps its
  `IStartCarApp` binder, the receiver calls `host.startCarApp(intent)` → the car screen switches to
  the app); AAOS → activity intent with the component rewritten to `CarAppActivity`. `CarPendingIntent`
  **unsets `FLAG_IMMUTABLE`** because the host has to add extras to the delivered intent — it cannot
  be used for the phone tap.
- **One car path for both car platforms.** The `isAutomotive` branch of `openTargetActivity()`
  disappears from the car tap target: projection and AAOS both go through `CarPendingIntent`. Phone
  keeps `MainActivity`, so `openTargetActivity()` reduces to the phone case.
- **Bare intent, no payload.** The delivered intent carries `ACTION_VIEW` and the CarAppService
  component only. `NavigationSession.onCreateScreen`/`onNewIntent` route it through
  `handleDeepLink`, which finds no `geo:`/share data and leaves the root map screen in place; the
  navigation-screen push is already driven by the session's state observer, so no session-side change
  is needed.
- **Fault isolation stays as is**: `buildNotification()` is evaluated inside
  `runGuardedNotification` in both `onCreate` and `render`, so a throwing `CarPendingIntent`
  (`InvalidParameterException` for a bad component, `SecurityException`) degrades the post instead of
  killing the driving process.
- **Spec delta**: `navigation-ongoing-notification` gains the car-surface requirement (ADDED
  requirement "Car rail-widget tap returns to the app"); no existing requirement text changes.
- **Guideline**: `guidelines/UI.md` §1 records the tap target per surface (phone → app UI, car → the
  car app), keeping the surfaces/parity section honest.
- Scope: **Android Auto integration only** (projection + AAOS). The phone notification, its content
  and its tap target are untouched; free driving has no car surface and therefore no car tap target.
- **Additive** (no API, storage, JNI or manifest change). Rollback: revert the two Kotlin files and
  the spec/guideline deltas; the notification then keeps its previous phone-only tap target.

## Decisions (made)

1. **Bare `ACTION_VIEW` intent, no navigation payload.** Chosen over a `geo:`/`CarContext.ACTION_NAVIGATE`
   intent (which `CarPendingIntent` also validates) because the user asked to return to the app, not to
   restart guidance; a payload intent would additionally have to survive `handleDeepLink`'s parsing and
   could re-trigger a route computation. The map/navigation screen is chosen by the session's existing
   state observer.
2. **Migrate the AAOS branch to `CarPendingIntent` too.** Chosen over keeping the hand-built
   `CarAppActivity` activity intent: one code path for both car platforms, the host-mediated contract
   the library documents (`setContentIntent` → "start a car app"), and no app-owned knowledge of
   `CarAppActivity`'s component rewriting. Consequence: AAOS behaviour depends on the library's
   component rewrite (`CarAppService` component → `CarAppActivity`), which is exactly what
   `CarPendingIntent` guarantees for an intent whose component is a service of our own package.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `navigation-ongoing-notification`: ADDED requirement — tapping the car rail-widget hint returns the
  app to the car screen (projection and AAOS), while the phone shade keeps returning to the phone UI.
  Complements the capability's in-flight "Return to the app from the notification" requirement from
  change `background-navigation-notification` and the TBT contract in `auto-navigation-hints` (change
  `car-turn-by-turn-rail-widget`); neither change's requirement text is modified by this one.

## Impact

Phone/car notification (`:app`):
- `app/src/main/java/com/naviveylin/service/NavigationNotificationService.kt` — build the car tap
  target (`CarPendingIntent`), pass it to the builder; `openTargetActivity()` (`:196-202`) loses the
  `isAutomotive` branch for the car path (`buildNotification()` `:162-183`, `openIntent` `:166-172`).
- `app/src/main/java/com/naviveylin/service/NavigationNotificationBuilder.kt` — `build()` (`:104`)
  and `carExtender()` (`:143-158`) gain the car content intent parameter and the
  `setContentIntent` call; KDoc updated to state the per-surface tap target.
- Tests: `app/src/test/java/com/naviveylin/service/NavigationNotificationBuilderTest.kt` —
  `CarAppExtender(notification).contentIntent` is non-null, differs from the phone content intent, and
  the phone content intent still targets `MainActivity`; a free-driving (no hint) notification carries
  no car extender and therefore no car tap target.
- No manifest change (`NaviVeylinCarAppService`, `CarAppActivity`, the `queries` block and the merged
  `androidx.car.app.notification.CarAppNotificationBroadcastReceiver` are already in place), no
  resource change, no new dependency (`androidx.car.app:app-automotive:1.7.0` already provides
  `CarPendingIntent`).
- `:auto` / `:core`: no code change. Session intent handling (`NavigationSession.onCreateScreen`,
  `onNewIntent` → `handleDeepLink`) already covers a bare `ACTION_VIEW` intent.

Specs: `openspec/specs/navigation-ongoing-notification/spec.md` (delta only, in this change).

Guidelines: `guidelines/UI.md` §1 (notification surfaces — add the per-surface tap target); no
`Design.md` change needed (§8 already requires host-facing notification paths to be fault-confined,
which this change preserves), no `MapRendering.md` or `Build.md` impact.

Native/JNI: none — no submodule patch, no bridge change.

Verification: unit tests (builder test above) plus an on-device check on a phone projecting to a head
unit (start navigation, switch to another car app, tap the rail widget → the car screen returns to the
app; `adb logcat -s Diag/SESSION Diag/HOST` shows the session being (re)created for the app and no
`HOST` rejection).
