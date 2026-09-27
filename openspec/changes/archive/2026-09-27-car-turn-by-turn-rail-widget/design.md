# Design

## Context

Motivation and scope: see `proposal.md`. Requirements: see
`specs/auto-navigation-hints/spec.md`.

Current state that shapes the approach:

- The ongoing navigation notification is built in
  `app/src/main/java/com/naviveylin/service/NavigationNotificationService.kt`
  (`setOngoing`, `setCategory(CATEGORY_NAVIGATION)`, channel `IMPORTANCE_LOW`) and
  posted with `startForeground` plus `notify` for updates. It is never extended with a
  `CarAppExtender`, which is the car host's gate for the turn-by-turn (TBT) contract
  (Android for Cars: "notifications only show up in the car screen if it is extended
  with `CarAppExtender`, even if the extender does not override any properties";
  `androidx.car.app.notification.CarAppExtender`, car-app 1.7.0).
- `auto/src/main/java/com/naviveylin/auto/NavigationManagerController.kt` owns
  `NavigationManager` for the car session: callback registration, `navigationStarted`,
  `navigationEnded`, best-effort cleanup. `updateTrip` is not called anywhere, so the
  host has no step/estimate data for the cluster or heads-up display.
- The car session already observes the shared navigation state on the main thread
  (`NavigationSession.startObserving`), and `:auto` reaches the app through
  `AutoEntryPoint` (`NavigationViewModel` interface lives in `:core`).
- Layout and dependency rules: `guidelines/Design.md` §1 (`:auto` never depends on
  `:app`; `:core` holds shared logic) and §4 (threading); parity/deviation rules:
  `guidelines/UI.md` §1; car-host constraints: `guidelines/UI.md` §3.
- `:app` already depends on `:auto`; `:core` is an Android library (graphics allowed).

## Goals / Non-Goals

**Goals:**

- The car host classifies the ongoing notification as a TBT navigation notification and
  can render the current manoeuvre in the rail widget while the app is in the
  background, on both Android Auto and Android Automotive OS.
- The car surfaces use the same guidance wording as the phone and on-screen displays,
  with car-appropriate text roles (instruction first) and a manoeuvre arrow icon.
- The cluster / heads-up display receives trip metadata (current step, travel estimate,
  destination) at a host-friendly cadence.
- One source of truth for the manoeuvre glyph artwork used by the car template and the
  notification.

**Non-Goals:**

- Heads-up notifications for turn hints (rail only; the door is left open as a one-line
  change).
- Any free-driving car surface (platform constraint, see the spec).
- Rendering a map in the instrument cluster (`FEATURE_CLUSTER` category,
  `SessionInfo.DISPLAY_TYPE_CLUSTER` sessions) — metadata only.
- Changing phone notification content, labels or the phone channel.
- Any native/JNI change.

## Decisions

### D1 — Car hint content produced by the existing formatter (in `:app`)

Add a car variant to `NavigationNotificationContentFormatter` (pure function: state →
car title/text/large-icon turn type), instead of a new formatter in `:core` or
content assembly in `:auto`.

- Chosen: one formatter object already unit-tested without Robolectric; the car variant
  is a second pure mapping and needs no Android types.
- Alternative A — `CarNotificationContentFormatter` in `:core`: rejected, the car text
  roles are notification-rendering concerns with no second consumer; `:core` would grow
  a module-level concept for one caller.
- Alternative B — assemble car content in `:auto`: rejected, it inverts the layering
  (`:auto` must never depend on `:app`, and the notification service lives in `:app`);
  the car session has no business building notifications.

### D2 — Lift the manoeuvre glyph renderer into `:core`

Move the bitmap drawing currently inside `auto/.../ManeuverGlyphs.kt` into a
`:core` renderer that returns a `Bitmap` per `TurnType`; `ManeuverGlyphs` becomes a thin
wrapper that caches `CarIcon.Builder(IconCompat.createWithBitmap(...))` around it, and
the notification service uses the `:core` renderer for the car large icon.

- Chosen: `:core` is the documented home for shared logic; the artwork (arrow shapes,
  size, colour) then has exactly one definition and the host-approved car artwork is
  reused unchanged for the rail widget.
- Alternative A — expose a `bitmapForTurnType()` accessor on `ManeuverGlyphs` and let
  the app notification service call into `:auto`: smaller diff, but `:app` would depend
  on an Android Auto-specific glyph type for a surface that is not the template, and the
  drawing code stays in the wrong module. Fallback only if the `:core` renderer turns
  out to need car-app types (it does not — plain `Canvas`/`Bitmap`).
- Alternative B — a separate notification-only arrow drawable: rejected, two artworks
  for the same manoeuvre drift apart and the notification would not match the car
  template arrows.

### D3 — Per-surface channel, chosen at notification build time

On automotive the ongoing notification uses a car channel of
`IMPORTANCE_DEFAULT`; on phone it keeps the existing `IMPORTANCE_LOW` channel. The
channel is selected in `buildNotification` via the existing
`AutomotiveDevice.isAutomotive(context)` helper.

- Chosen: the notification is posted by `startForeground`, which cannot go through
  `CarNotificationManager` (that helper rewrites the notification for the car but does
  not post a foreground-service notification), so the channel must be right at build
  time. AAOS does not represent foreground-service notifications with importance `LOW`
  or below at all, regardless of category — hence `IMPORTANCE_DEFAULT` there. Both
  channels are created in `onCreate`; the automotive channel is created only on
  automotive devices.
- Alternative A — `CarAppExtender.Builder.setChannelId(...)` / `setImportance(...)`:
  rejected as the primary mechanism because those values are applied by
  `CarNotificationManager` and by Android Auto respectively; they stay unused to avoid
  two competing sources for the same decision.
- Alternative B — one channel raised to `IMPORTANCE_DEFAULT` everywhere: rejected, it
  breaks the phone design decision that the ongoing notification is silent and
  badge-free.
- Alternative C — `IMPORTANCE_HIGH` plus `setImportance` for a heads-up in Android
  Auto: rejected, repeated heads-up notifications distract the driver; rail-only is the
  design decision (one-line change if we ever want it).

### D4 — Trip publishing owned by the car session controller

Extend `NavigationManagerController` with trip publication, fed by the session's
existing main-thread state observer (`NavigationSession.startObserving`).

- Chosen: the controller already owns the `NavigationManager` lifecycle and its
  ordering rules (callback registered before `navigationStarted`, cleared only after
  `navigationEnded`); the session already has the state flow and its `scope`.
- Alternative A — publish from `:app`'s `NavigationViewModel`: rejected, the
  `NavigationManager` comes from the car session's `CarContext`, which the app module
  does not have.
- Alternative B — publish from `NavigationScreen`: rejected, the screen lifecycle is not
  the navigation lifecycle (the screen can be popped while navigation continues, and a
  recreated session would lose the publisher).
- Alternative C — a separate Hilt-injected publisher singleton: rejected as unnecessary
  indirection; the controller is already the single owner of car-side navigation
  notifications to the host, and a second component would need its own ordering
  guarantees.

### D5 — Cadence predicate dedicated to trip content

Add a pure `hasTripChanged(old, new)` predicate (manoeuvre, rounded distance, remaining
time, loading/rerouting flag) next to the existing `NavigationTemplateMapper`
mapping/throttle code, and publish only when it reports a change.

- Chosen: the existing `NavigationTemplateMapper.hasStateChanged` also reacts to speed
  and lane data, which the trip does not carry; reusing it would publish trips whose
  displayed values did not change and violate the cadence requirement.
- Alternative A — reuse `hasStateChanged`: rejected for the reason above; also it is
  tuned for template invalidation, not host metadata.
- Alternative B — publish on every state emission: rejected, it floods the host at the
  position-update rate; the `updateTrip` contract expects updates roughly per rounded
  distance change.
- Alternative C — debounce by time window: rejected, distance/time-based predicates are
  deterministic and testable without fake clocks.

### D6 — One new action drawable for both stop paths

Add a mono vector drawable for the end-navigation action and use it for the car extender
action and for the phone notification action (which currently passes resource id `0`).

- Chosen: car actions require a valid drawable resource id, and passing the same icon to
  `NotificationCompat.Builder.addAction` removes the current icon-less phone action —
  small, additive, and keeps one artwork for one action.
- Alternative — keep the phone action icon-less and add the drawable only for the car:
  rejected, the same action would render differently on the two surfaces for no reason.

### D7 — Trip mapping reuses the template step mapping

The trip's steps are built with the existing
`NavigationTemplateMapper.stepForInstruction(...)` (manoeuvre type, icon, cue, road,
lanes); the destination and travel estimates come from the same state.

- Chosen: one mapping for "instruction → host step" means the rail card, the cluster
  step and the notification hint cannot disagree about wording or arrow.
- Alternative — a separate trip mapper: rejected, duplication with drift risk and no
  added coverage.
- Consequence: a trip must be well formed — every step needs a `TravelEstimate`
  (non-null distance and arrival time), so the mapper publishes a loading or neutral
  trip when the arrival time or distance is not yet known instead of omitting the
  estimate.

## Threading model and lifecycle

- Notification rendering keeps its current model: `Dispatchers.Main` combine collector
  in the service; the notification is built on the main thread. Glyph bitmaps are
  created by the `:core` renderer and cached per `TurnType` (the current
  `ManeuverGlyphs` already caches per type); bitmap creation is small, bounded and does
  not touch native/JNI, so the `startForeground` five-second budget is safe. The car
  icon is produced before `startForeground` in `onCreate` only if a manoeuvre is already
  known — otherwise the neutral hint is posted first and the icon arrives with the next
  render.
- Trip publication runs on the main thread (the `NavigationManager` contract), driven by
  the session's existing main-thread observer — no new dispatcher, no new scope. All
  host calls are wrapped best-effort (`runCatching`), matching the existing controller
  behavior when the host connection is gone.
- Ordering: on the first `isNavigating = true` emission the observer already calls
  `setNavigationManagerCallback` then `navigationStarted`; trip publication is appended
  to that sequence so no `updateTrip` can precede `navigationStarted`. On
  `isNavigating = false` publication stops before `navigationEnded`. `onDestroy` keeps
  its best-effort cleanup and stops publication first.
- Session recreate while navigating: the publisher seeds from
  `navigationViewModel.state.value` when it is created, mirroring the existing restore
  behavior for the free-driving view.

## Risks / Trade-offs

- [The rail widget may still not appear on Android Auto after adding the extender — the
  documented gate could be insufficient in practice] → verify on the DHU as the first
  on-device task of the change; the fallback lever is car importance
  (`IMPORTANCE_DEFAULT`, one line), which the spec permits for Android Auto because it
  only constrains Android Automotive OS and the phone.
- [Expectation mismatch: TBT notifications are excluded from the car Notification Center
  by design, so a user looking for a shade entry on the car still sees none] → the spec
  and `guidelines/UI.md` state the actual car surface (rail widget, optional heads-up),
  and the sibling change's tasks are corrected.
- [TBT hints are suppressed while the app shows its routing card] → expected host
  behavior; the app must not treat a missing rail widget as an error (spec scenario
  covers it).
- [`updateTrip` throws when called outside an active navigation session] → publication
  is gated by the same state transition that calls `navigationStarted` /
  `navigationEnded`, plus best-effort wrapping.
- [Malformed trip: a step without a travel estimate or an unknown arrival time] → the
  mapper publishes a loading/neutral trip when the values are not yet available and
  rejects publishing otherwise; unit tests cover the null-ETA and rerouting cases.
- [Two channels on Android Automotive OS could surface two entries] → the ongoing
  notification is always posted on the car channel on automotive devices; the phone
  channel is created there too but is unused while hints are live (documented
  trade-off).
- [Manoeuvre bitmap on the main thread in the FGS start path] → bitmaps are small,
  cached per turn type, and the neutral hint is posted first when no manoeuvre is known
  yet.
- [Publishing from a recreated session] → seeding from the current state value on
  creation, so a session created mid-navigation publishes immediately.

## Migration Plan

No data migration; the change ships in a normal in-place app update (both flavors, same
applicationId). Rollback: remove the extender call and the trip publication wiring — the
phone notification, navigation flow and all car screens then behave exactly as before.

## Open Questions

- Does the Android Auto rail widget require `IMPORTANCE_DEFAULT` as well (answerable by
  DHU experiment)? If yes, the car-side channel choice extends to the Android Auto
  flavor; the specs already constrain only Android Automotive OS and the phone, so no
  spec change is needed.
- Exact neutral car hint text when no manoeuvre is available yet (for example while
  rerouting) — reuse the existing neutral navigation wording; no spec change follows
  from the choice.
