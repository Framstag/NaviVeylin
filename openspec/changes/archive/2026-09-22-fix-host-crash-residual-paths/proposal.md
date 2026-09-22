# Proposal

## Why

On 2026-09-21 the Android Auto / AAOS **host** (templates host, `renderer_service`) crashed
repeatedly after seconds to minutes of driving, while the app itself stayed up long enough to be
observed; a few days earlier the same session was stable. `TODO.md` §50/§51 established the
mechanism: a host crash is *downstream* of the app process dying/ANRing (the car-app library
rethrows an app exception on the main thread, and the host's queued template operation then runs
against an invalidated `CarHost`), or of host-side heap churn fed by the app. The in-flight changes
`fix-aaos-host-crash`, `fix-car-screen-observer-leak` and `fix-diagnostics-log-host-path-io` closed
the paths known at the time; a re-review of the same code (2026-09-21, after the report) found
**further** paths in the same class that are still open, and one of them violates a rule the
in-flight change itself specifies.

## What Changes

- **Surface ownership: identity-scoped destroy.** `SessionCarSurfaceHost.onSurfaceDestroyed`
  currently clears the session's active surface and releases it unconditionally, even when the host
  reports the destroy of a *superseded* surface instance (a new surface was delivered first, and
  AAOS does re-deliver surfaces after transitions). The session then forgets the live surface and
  calls `Surface.release()` on the buffer queue the host is still compositing through. The destroy
  SHALL be scoped to the surface instance it names.
- **Template-build fault isolation for every car screen.** Only `MapScreen`, `NavigationScreen`,
  `FreeDrivingScreen` and `RouteDescriptionScreen` (plus `SafeScreen`/`ErrorScreen` helpers) guard
  the template build today; 11 other screens build a template in `onGetTemplate` unguarded, and a
  throwing library validator there kills the process → host crash. Every car screen's template build
  SHALL degrade to the error template.
- **Idempotent free-driving restore.** `NavigationSession.restoreDrivingMode()` runs from both
  `onCreateScreen` and `onWarmupComplete` with no pushed-guard, so a session restart while free
  driving is active pushes **two** `FreeDrivingScreen`s (two `AutoMapRenderer`s, two native render
  loops, two buffers) and leaves a ghost screen after one BACK. Restore SHALL happen at most once
  per session.
- **Host traffic bound to displayed change.** The template is rebuilt on every navigation-state
  emission (~1 Hz while driving) and each rebuild allocates a **new** lane-guidance bitmap +
  `CarIcon` that is sent to the host over IPC (`ManeuverGlyphs.lanesImage`,
  `NavigationTemplateMapper.routingInfoFromState`). The lane image SHALL be reused while the lane
  state is unchanged, and a template SHALL only be rebuilt when displayed content changes
  materially (step, manoeuvre, street, or a distance bucket), not on every metre.
- **Tap path off the host thread.** `MapScreen.onLocationSelected` resolves
  `entryPoint.autoClientProvider().client()` on the host callback thread and, per tap, creates a
  `CoroutineScope(SupervisorJob() + Dispatchers.Main)` that is never cancelled. The client SHALL be
  resolved inside the existing background block, and the screen's own scope SHALL be used.
- **One notification-identity source.** The ongoing navigation notification (which is also the
  car rail-widget turn hint via its `CarAppExtender`) and the car map-style failure notice both use
  id 1002 with a null tag, so a style notice replaces the foreground-service notification
  (`TODO.md` §60). The system SHALL post them under distinct identities from one shared source.
- **Verification-only (no spec change):** whether a *refused* `startForeground` followed by
  `stopSelf()` still trips the platform's foreground-start deadline
  (`ForegroundServiceDidNotStartInTimeException` → process death), and why the file-backed
  diagnostics log is never created on the automotive AVD (`TODO.md` §47). Both are on-device checks
  in `tasks.md`; they need evidence before any behavior is specified.

Not in scope (tracked in `TODO.md`, each needs its own change): §48 unsynchronised
`ClientData::knownPaths` (native, submodule patch), §49 render-buffer reuse, §57 `DetailsScreen`
observers while invisible, §59 observation fault confinement, §62 overrun buffer on `pause`, §63 car
tile-cache capacity.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `car-host-fault-isolation` (introduced by the in-flight change `fix-aaos-host-crash`): the
  "Single-owner car surface" requirement gains the identity-scoped destroy rule; "No fault escapes
  into the host path" is extended from "building a template" to *every* car screen's template build;
  "Bounded host-facing traffic while not visible" gains the template-rebuild bound and the stable
  lane image; "Host callbacks answer promptly" gains the surface-tap path (no native-client
  resolution, no per-tap scope).
- `auto/free-driving`: a new requirement that a restored free-driving session pushes at most one
  free-driving view.
- `navigation-ongoing-notification` (introduced by the in-flight change
  `background-navigation-notification`): a new requirement that the ongoing navigation notification
  and the car map-style failure notice are posted under distinct notification identities.
- Affected guideline: `guidelines/Design.md` §AA surface/car-host rules (the car-host section
  already states "the surface released exactly once: on the host's destroy signal or at session
  end" — the identity scope is the missing half). `guidelines/Build.md` §10 (host-crash triage)
  gains the two verification checks.

**Ordering dependency:** `car-host-fault-isolation` and `navigation-ongoing-notification` exist only
inside their in-flight changes. Archive `fix-aaos-host-crash` and
`background-navigation-notification` before archiving this change; implementation (the Kotlin
changes) does not depend on that order.

## Impact

Additive and behavior-preserving for correct runs: no spec'd scenario is removed, no user-visible
label changes, no API/permission/manifest change.

- `auto/src/main/java/com/naviveylin/auto/SessionCarSurfaceHost.kt` — destroy scoping (+ test
  `SessionCarSurfaceHostTest`)
- `auto/src/main/java/com/naviveylin/auto/` — every `*Screen.kt` with `onGetTemplate`
  (`AboutScreen`, `AddressBookScreen`, `AddressBookAddressPickerScreen`, `CandidatePickerScreen`,
  `DetailsScreen`, `DiagnosticsScreen`, `FavoritesScreen`, `OverspeedDeltaPickerScreen`,
  `PoiResultsScreen`, `PoiSearchScreen`, `PreferencesScreen`, `RootScreen`, `SearchHistoryScreen`,
  `SearchScreen`, `VehicleAnchorPickerScreen`) + one shared guard seam (extend `SafeScreen.kt`)
- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — restore guard; also the inline
  error `Screen` in `showError`
- `auto/src/main/java/com/naviveylin/auto/ManeuverGlyphs.kt`,
  `NavigationTemplateMapper.kt`, `NavigationScreen.kt` — lane-image reuse + rebuild bound
- `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` — tap path (background client resolution,
  screen scope)
- `app/src/main/java/com/naviveylin/service/NavigationNotificationService.kt`,
  `NavigationNotificationBuilder.kt`, new shared notification-id source in `:app`;
  `auto/src/main/java/com/naviveylin/auto/CarStyleLoadNotifier.kt` consumes it
- Tests: `auto/src/test/...` (surface host, template guard for each screen, lane-image reuse,
  rebuild bound, restore idempotence), `app/src/test/...` (notification-id uniqueness)
- Modules: `:auto`, `:app` (notification ids). `:core` unchanged. **No native/JNI change**, no
  submodule patch, no bridge-module override.

## Rollback

Revert the change: the surface-host scoping, the template guard wrapper, the restore flag and the
lane-image cache are independent commits; each can be reverted alone. Rollback restores today's
behavior (including the residual crash paths) and needs no data migration.
