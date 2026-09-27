# Proposal

## Why

While NaviVeylin navigates and the driver leaves the navigation view (for example a
podcast player is full screen on the head unit), the car shows nothing: the lower car
chrome — the rail widget on Android Auto and its Android Automotive OS (AAOS)
equivalent — stays empty, and the instrument cluster / heads-up display never receive
turn metadata. The phone-shade notification added by the in-flight change
`background-navigation-notification` does not reach those surfaces: a car host only
treats a notification as a turn-by-turn (TBT) notification when the notification is
extended with a `CarAppExtender`, and cluster/HUD data comes from
`NavigationManager.updateTrip()`, which the app never calls.

## What Changes

- Extend the ongoing NAVIGATION notification with `CarAppExtender` so the host
  classifies it as a TBT navigation notification and renders it in the rail widget at
  the bottom of the car screen while the app is in the background.
  `setOngoing(true)` and `setCategory(CATEGORY_NAVIGATION)` are already present, so the
  notification gains the missing third contract element only.
- Add car-only content overrides through the extender: car title = manoeuvre
  instruction, car text = distance-to-turn and ETA, car large icon = the manoeuvre
  arrow bitmap, car action "Exit navigation" with a real drawable icon (the phone
  action currently passes resource id `0`). The phone notification content is
  unchanged.
- Introduce a per-surface importance policy: the phone channel stays
  `IMPORTANCE_LOW` (silent, no badge, no heads-up); the `automotive` flavor uses a car
  notification channel at `IMPORTANCE_DEFAULT` or above, because AAOS does not
  represent foreground-service notifications with importance `LOW` or below at all,
  regardless of category. Heads-up notifications stay off: hints appear in the rail
  widget only.
- Record that free driving gets **no** car surface: in free driving the app is not the
  active navigation app, the host suppresses TBT notifications, and
  `NavigationManager.updateTrip` rejects calls issued without
  `navigationStarted()`. Free-driving street/speed content therefore remains
  phone-only — a documented platform deviation, not a defect.
- Publish cluster / heads-up metadata with `NavigationManager.updateTrip(Trip)`: the
  current step (manoeuvre icon, cue, road), its `TravelEstimate` (remaining distance,
  arrival time, remaining time) and the destination, rebuilt only when the displayed
  values change, `setLoading(true)` while rerouting, and stopped together with
  `navigationEnded()`.
- Correct the car-surface claims of the in-flight change
  `background-navigation-notification` (spec `navigation-ongoing-notification` R5/R8,
  tasks 4.6 and 4.7): a TBT notification is deliberately excluded from the car
  Notification Center, so the car surfaces are the rail widget and the optional
  heads-up notification, not a shade relay.
- Update `guidelines/UI.md` (ongoing navigation notification section): label/guidance
  parity stays the rule, the car renders it through car-only extender overrides, and
  the free-driving deviation is stated.

Not breaking: the phone notification, the navigation flow, the navigation template and
all existing car screens keep their current behavior. Everything here is additive and
gated on an active navigation session.

## Capabilities

### New Capabilities

- `auto-navigation-hints`: car-side turn-by-turn hints outside the navigation view —
  rail-widget presence through the TBT notification contract (`CarAppExtender`), the
  car-only hint content, the per-surface importance policy, and cluster/HUD trip
  metadata through `NavigationManager.updateTrip`.

### Modified Capabilities

- `navigation-ongoing-notification`: introduced by the still-open change
  `background-navigation-notification` and therefore not yet present under
  `openspec/specs/`, so it cannot carry an archived delta file. Its car-surface
  requirements (R5 free-driving content on car surfaces, R8 three-surface parity
  "phone shade → head-unit shade relay") are corrected by this change: this change's
  tasks revise that change's spec delta, and the resulting car behavior is specified by
  the new `auto-navigation-hints` capability. No archived capability needs a delta:
  `auto`, `auto/navigation-view`, `auto/free-driving` and `android-automotive-os` keep
  their requirements (none of them mention notifications or car chrome), which was
  verified by reading each of them.

## Impact

Scope: the Android Auto / Android Automotive OS integration plus the shared
notification service. Not a phone-UI feature and not a native change.

Modules and files:

- `app/src/main/java/com/naviveylin/service/NavigationNotificationService.kt` — extender
  plus car content overrides, automotive channel selection, car exit action.
- `app/src/main/java/com/naviveylin/service/NavigationNotificationContent.kt` — car
  variant of the formatted content (manoeuvre title, distance/ETA text, no phone
  changes).
- `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt` —
  channel/importance decision passed to the service.
- `app/src/main/res/drawable/` — action icon for the car exit action.
- `app/src/main/res/values/strings.xml` — car notification channel name/description if
  a second channel is introduced.
- `auto/src/main/java/com/naviveylin/auto/NavigationManagerController.kt` — trip
  publishing lifecycle next to `navigationStarted()` / `navigationEnded()`.
- `auto/src/main/java/com/naviveylin/auto/NavigationTemplateMapper.kt` — `Trip`
  mapping (step, travel estimate, destination) reusing the existing step/manoeuvre
  mapping and the existing change throttle.
- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — drives the trip
  publisher from the existing navigation-state observer.
- `auto/src/main/java/com/naviveylin/auto/ManeuverGlyphs.kt` — expose the manoeuvre
  arrow as a `Bitmap` for the car large icon.
- Modules: `:app`, `:auto` (`:core` only if the glyph renderer is lifted there).
- Gradle: none; `androidx.car.app:app:1.7.0` already provides `CarAppExtender` and
  `updateTrip`.
- Manifest: unchanged (`androidx.car.app.category.NAVIGATION`,
  `NAVIGATION_TEMPLATES`, `FOREGROUND_SERVICE_LOCATION` already declared).
- Native/JNI: none.
- Guidelines: `guidelines/UI.md` (ongoing navigation notification section).

Rollback: delete the `.extend(CarAppExtender…)` call and the trip publisher; the phone
notification and all car screens return to their previous behavior with no data or
format migration.
