# Design — fix-free-drive-background-liveness

## Context

Motivation and evidence: see `proposal.md`. The deltas this design serves:
`specs/navigation-ongoing-notification`, `specs/current-road-info`, `specs/location-updates-lease`.

Current shape relevant to the approach:

- `LocationService` (`app/.../location/LocationService.kt`) hands out named leases
  (`PHONE_MAP`/`NAV_ENGINE`/`CAR_SESSION`); updates run while one is held. The phone's `PHONE_MAP` lease is
  acquired on `MapCanvasScreen` `ON_RESUME` and released on `ON_PAUSE` (`:989-991`).
- The notification service reads `NavigationViewModel.state` and `DrivingModeProvider.freeDrivingActive`
  (`navigation/NavigationNotificationController.kt:44-52`, `service/NavigationNotificationService.kt:105-113`)
  and formats with `NavigationNotificationContentFormatter` (`service/NavigationNotificationContent.kt:186-225`).
- Only `NavigationEngine` writes `NavigationState.currentRoadInfo`/`currentSpeedKmH`, and only while
  navigating. The phone resolves road/speed for its own label in `MapCanvasViewModel.resolveCurrentRoad`
  (`:1855-1875`, throttle 5 s / 50 m) and `resolveMaxSpeed`; the car resolves its own in
  `auto/FreeDrivingScreen.kt:652-675`.
- `DrivingModeProvider` (`:core`) is the existing pattern for "a surface publishes its own mode state, the
  process combines it": OR-combined, retain-on-death, lock-guarded set.

## Goals / Non-Goals

**Goals**

- Make the free-driving notification's street/ref and speed correct wherever fixes actually arrive (phone
  foreground free drive; car free drive, whose session lease keeps fixes flowing in the background).
- Make the two consumers (on-screen label, shade) unable to disagree, by construction rather than by
  coincidence.
- Record the deliberate "backgrounded free drive holds no lease" policy where the lease rules live.

**Non-Goals**

- No always-on background fixes for the phone (owner decision C, proposal table).
- No change to the lease mechanics, the provider choice (Fused vs `LocationManager`), the update cadence
  (1000 ms / 500 ms / 5 m), or the notification's identity, channel or FGS type.
- No unification of the two road-resolution code paths (phone VM vs car screen) beyond publication; the
  throttles stay as measured today.
- No new navigation-engine state, no `NavigationState` field, no native/JNI change.

## Decisions

### D1 — A dedicated free-driving status provider in `:core`, not fields on `NavigationState`

Chosen: new `@Singleton FreeDrivingStatusProvider` (`core/src/main/java/com/naviveylin/core/`) exposing
`status: StateFlow<FreeDrivingStatus?>` plus `publish(surface, road, speedKmH)` and `clear(surface)`.

Alternatives: (a) add `freeDrivingRoad`/`freeDrivingSpeedKmH` to `NavigationState` — rejected: that state is
the navigation contract, its fields are produced by the native controller while navigating, and
`isNavigating == false` plus a populated road would be ambiguous to every existing reader and test;
(b) keep the surfaces' private state and have the notification ask the surfaces — rejected: it re-introduces
the coupling the defect is made of, and a phone surface is exactly what is absent when the car drives.

Rationale: the status is a *mode* fact, not a navigation fact; it needs its own owner with the same
publication shape as `DrivingModeProvider`, which the codebase already tests and trusts.

### D2 — Per-surface publication, last resolution wins, single publisher at a time

Chosen: `publish(surface, …)` keyed by `DrivingModeProvider.SURFACE_PHONE` / `SURFACE_AUTO`; the exposed
status is the most recently published non-empty entry; `clear(surface)` removes it.

Alternatives: (a) one writer resolved once in a shared `:core` resolver used by both surfaces — rejected as a
larger refactor that would drag the two throttles and the two native-lookup call sites into one owner before
the defect is fixed; (b) the notification reading whichever surface currently holds `freeDrivingActive` —
rejected: a mode can be entered on both surfaces, and the notification is process-wide.

Rationale: today each surface already resolves road/speed at its own cadence for its own label; the contract
that matters is that the notification shows the same values as *the* surface that is driving, and that no
second lookup is triggered *for the notification*. The rare both-surfaces case resolves to "the most recent
resolution wins", which is stated in the spec's scenarios and is testable.

### D3 — The notification refreshes off the existing combine; no new push path

Chosen: `NavigationNotificationService.observeDrivingState` combines `navigationViewModel.state`,
`drivingModeProvider.freeDrivingActive` **and the new status flow**, and posts only when the host-visible
content changed (the existing `hostVisibleContentChanged` gate).

Alternatives: (a) the surface pushes a refresh into the service on every fix — rejected: it crosses the
surface/host boundary with a per-fix call (the car host path is explicitly guarded and must not carry native
or per-fix work) and duplicates throttling; (b) a periodic re-post — rejected: the notification is silent and
shade-visible content changes are the only reason to post.

### D4 — Formatting: absent values are omitted, never rendered

Chosen: `freeDriveContent` builds its line from present values only (road text, then speed) and falls back to
the neutral title; a `·` separator is inserted only between two present values.

Alternatives: (a) keep `joinToString` on a list that contains empty strings — the current defect
("Abseits der Straße · "); (b) render placeholders ("– km/h") — rejected: the spec forbids destination-like
and unknown-value content in the shade and placeholders read as a live value of zero.

### D5 — The lease policy stays surface-scoped (owner decision C)

Chosen: no lease for an invisible, non-navigating FREE_DRIVE. The FGS and notification continue; the content
is the last known road/speed.

Alternatives (both presented to and rejected by the owner, 2026-10-07): (A) lease for the whole mode lifetime
— spec-conformant live content in the background, cost 1 Hz HIGH_ACCURACY fixes for as long as follow mode is
left on; (B) bounded streaming after the last interaction — bounded cost, plus a second exception and a
staleness rule.

Measured support on `emulator-5554` (free drive in both phases; per-UID accounting): GPS on-time
`+124.9 s / 125 s` with the request active vs `+8.0 s / 127 s` with it off; per-UID power model
`+0.0096 mAh` vs `+0.0006 mAh` (≈16×). The GNSS receiver's own mA is not observable on the emulator (no
radio, no per-UID `gps=` term in API 37's power model) — see Verification for the physical-device step.

## Threading model and lifecycle (Design.md §4)

- `FreeDrivingStatusProvider` is a process-scoped `@Singleton` in `:core` holding a `MutableStateFlow`
  guarded by the same lock discipline as `DrivingModeProvider`. Writes come from the surfaces' main-thread
  fix paths: the phone publishes after its road lookup returns (the lookup itself stays on
  `defaultDispatcher` inside `withContext`; only the state write is on main), the car publishes from its
  screen scope on the main dispatcher. Reads: the notification controller/service collect on
  `Dispatchers.Main`, as today.
- Lifecycle: the provider is retained for the process lifetime, and the status is retained per surface until
  the surface clears it (mode exit, screen destroyed, session destroyed), matching `DrivingModeProvider`'s
  retain-on-death semantics — destroying a surface must not blank a live mode's content. Nothing is
  persisted; the app starts in BROWSE with an empty status.
- No new thread, no dispatcher of its own, no native call on the host thread, no work queue: publication is a
  `StateFlow` write on the caller's thread, which is main for both surfaces.

## Files and Gradle surface

| file | change |
|---|---|
| `core/src/main/java/com/naviveylin/core/FreeDrivingStatusProvider.kt` | new provider + `FreeDrivingStatus` |
| `core/src/main/java/com/naviveylin/core/DrivingModeProvider.kt` | unchanged (reference for the pattern) |
| `app/.../di/*Module.kt` | bind the new provider as a `@Singleton` |
| `app/.../ui/map/MapCanvasViewModel.kt` | publish on fix (road + speed), clear on mode exit |
| `app/.../ui/map/MapCanvasScreen.kt` | label reads the shared status; `ON_PAUSE` semantics unchanged |
| `app/.../service/NavigationNotificationContent.kt` | formatter reads the status; no empty fragments (D4) |
| `app/.../service/NavigationNotificationService.kt` | combine the status flow into the existing observation |
| `app/.../navigation/NavigationNotificationController.kt` | unchanged trigger (mode-driven) |
| `auto/.../FreeDrivingScreen.kt` | publish its resolved street/speed; clear on exit/destroy |
| tests | see Verification |

No Gradle configuration changes (no new dependency, no new module, no flavor split). One JVM-only change set
plus Robolectric cases; no native build is required.

## Verification

- **Unit (JVM/Robolectric), one case per delta scenario**: provider publication/retain/clear
  (`FreeDrivingStatusProviderTest`); phone publish-on-fix and clear-on-exit
  (`MapCanvasViewModelFreeDrivingStatusTest`, `MapCanvasViewModelFreeDrivingPublishTest` extended); car
  publish/clear (`FreeDrivingScreenStatusTest`); formatter cases for road+speed, unknown speed (no stray
  separator), unknown road, and the neutral title (`NavigationNotificationContentFormatterTest`); service
  re-post only on content change with the status in the combine
  (`NavigationNotificationServiceStatusTest`); lease cases stay as they are
  (`LocationServiceTest`) — the pinned no-lease-while-invisible behaviour is asserted here as an
  expectation, not as a new mechanism.
- **On-device, phone (coordinate-free diagnostics)**: add one diagnostics line through
  `DiagnosticsLog`/`com.naviveylin.core` from the publish path — `free-driving status: source=<phone|car>
  road=<ref|name|none> speed=<n|none>` (numbers and names only, never a position, spec `auto-diagnostics`).
  Then: enter free drive in a mapped area with a fix → `dumpsys notification --noredact` extras
  `android.text` equals the on-screen label's road (and carries the speed, no `·` when the speed is unknown)
  → press HOME → `location lease release: phone-map (held=0)` + `gps provider service: ProviderRequest[OFF]`
  in `dumpsys location` + the notification unchanged (the pinned deliberate state).
- **On-device, car (device-gated)**: AAOS AVD, free driving with another car app foreground — the session
  lease keeps fixes flowing, so the notification must show live road/speed. Not producible on the phone
  emulator used for the rest of this change; the task says so explicitly instead of implying coverage.
- **Device-gated measurement**: real GNSS mA needs a physical phone (no GNSS radio and no per-UID `gps=`
  term on the emulator); the surrogate measured today is per-UID `Sensor GPS:` on-time plus the per-UID
  power-model delta. The design does not claim a battery measurement of the chosen option — it claims the
  device requests nothing in the background, which `ProviderRequest[OFF]` shows directly.

## Risks / Trade-offs

- **Both surfaces in free driving at once** (phone mode toggle on while the car screen free-drives): the
  status follows the most recent resolution, so the shade may show the other surface's road →
  mitigated by the per-surface keys plus a case that asserts last-resolution-wins, and by the fact that the
  notification is process-wide and its content is the mode's, not a surface's.
- **A stale status after a surface dies mid-mode** → mitigated by retain-on-death plus `clear()` on mode exit
  and on screen/session destroy; a case covers "no road from an earlier session".
- **Requirement narrowing reads as a regression** to someone who remembers the old text ("live in the
  background") → mitigated by the explicit `location-updates-lease` requirement that pins the deliberate
  no-lease case, and by the Design.md §13 entry that supersedes the archived "no new GPS load" assurance.
- **A future contributor re-adds a mode-scoped lease** to "fix" the now-known-inert background content →
  mitigated by the pinned requirement and its scenario names, which a reviewer of such a change would see.
- **Car-side publication from a stopped screen** (screen stopped while the session keeps running): the
  screen's status must be cleared on `onStop`/`onDestroy` under the observation lifetime rules
  (`CarScreenObservations`) → covered by the car case; if it proves noisy, the fallback is to let the
  session own the publication instead of the screen.

## Migration Plan

Additive and in-process; no persisted state, no schema, no manifest change. Rollback is a source revert of
the change's commits: the notification returns to the navigation-state-only source (fallback text) and the
lease behaviour is untouched either way.

## Open Questions

None that change the specs, the approach or the task breakdown. The car-side measurement and the physical-GNSS
measurement are device-gated work items, captured as tasks with that limitation stated.
