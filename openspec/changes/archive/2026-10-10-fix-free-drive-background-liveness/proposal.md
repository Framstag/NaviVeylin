# Proposal: fix-free-drive-background-liveness

## Why

The FREE_DRIVE notification promises live street/ref and speed (`navigation-ongoing-notification` — "Free
driving content") and background process survival "so GPS updates and guidance continue". Neither holds in a
running build:

- **No producer.** `NavigationState.currentRoadInfo` / `currentSpeedKmH` are written only by `NavigationEngine`
  (`NavigationEngine.kt:860`, `:972`, `:1000`), which runs only during navigation. In free driving the phone's
  road/speed live in `MapCanvasUiState` (`MapCanvasViewModel.kt:1871`, `:1270`) and the car's in
  `FreeDrivingScreen` (`FreeDrivingScreen.kt:669`) — neither is published anywhere the notification reads.
  Measured on `emulator-5554` with a GOOD 5 m fix, free drive active: `android.title = "Freie Fahrt"`,
  `android.text = "Abseits der Straße · "` — fallback road, no speed, dangling separator, never refreshed.
- **No background liveness.** `PHONE_MAP` is released on `ON_PAUSE` (`MapCanvasScreen.kt:989-991`) and no
  consumer leases for FREE_DRIVE, so a backgrounded free drive runs a `location`-typed foreground service with
  `ProviderRequest[OFF]`: process survival and the notification are real, the "GPS keeps updating" half is not.
  Measured: `lease release: phone-map (held=0)` → `stopLocationUpdates: stopped` → `ProviderRequest[OFF]`, then
  20 s with zero fixes and zero renders, while the service stayed `isForeground=true types=0x8`.

Both were shipped as done: archived `background-navigation-notification` task 4.2 is checked with no evidence
line (4.1 records a failed device run), and its design's assurance "no new GPS load — location keeps streaming
from the existing `LocationService`" stopped being true when the lease mechanism landed
(`shared-resource-arbitration`). No TODO entry and no in-flight change owns the gap.

## What Changes

- **One free-driving status, published once per fix.** The road (ref + name + type) and speed that the phone
  label, the car label and the ongoing notification show SHALL come from **one** resolution per fix, published
  into the shared state the notification reads — no second lookup for the notification, no per-surface private
  copy. This is what makes the notification's content requirement satisfiable at all.
- **The background liveness claim is dropped (owner decision C).** FREE_DRIVE in the background keeps its
  notification and its process protection and holds **no** location lease; the requirement text stops implying
  that fixes continue there. The deliberate no-lease state is pinned in `location-updates-lease` with its own
  requirement and cases, so a later reader cannot re-add always-on fixes by accident. No GPS policy changes in
  code; the car surface is unaffected (its session lease keeps fixes flowing while the notification shows).
- **Notification content rules made testable.** State the data source, the freshness rule (content reflects the
  latest fix **while fixes arrive**) and the formatting rule (an unknown speed SHALL NOT leave a separator or
  an empty fragment).
- **Supersede the "no new GPS load" assurance** in `guidelines/Design.md` §13 "Superseded decisions", because
  the app now deliberately streams 1 Hz high-accuracy fixes for a parked, backgrounded free drive.
- **BREAKING: none.** No API removal, no persistence change, no data migration. Behaviour change: with follow
  mode left on, the device streams high-accuracy fixes while the app is backgrounded (previously it did not).

**Decided by the owner on 2026-10-07: option C.** FREE_DRIVE holds no leased location updates while it is
invisible and not navigating. The notification and the process protection remain (they are the mode's visible
promise), the content requirement is reduced to "reflects the latest fix while fixes arrive", and the battery
question is closed in the app's favour: backgrounded free driving requests nothing from the device.

| option | consequence |
|---|---|
| A — whole mode lifetime (rejected) | spec-conformant live notification, but 1 Hz HIGH_ACCURACY fixes for as long as follow mode is left on, parked or not |
| B — bounded after last user interaction (rejected) | bounded battery cost, but a second written exception plus a staleness rule |
| **C — drop the background claim (chosen)** | no device request in the background; requirement text shrinks to what holds; the free-driving content fix still applies wherever fixes do arrive (phone foreground, car free drive) |

## Capabilities

### New Capabilities

- none — the fix extends capabilities that already own this behaviour.

### Modified Capabilities

- `navigation-ongoing-notification`: "Free-driving content" gains a data-source rule (the shared free-driving
  status, not navigation-engine fields) and a no-empty-fragment rule; "Process survival during background
  driving" states what actually continues (fixes at the lease cadence) instead of implying it.
- `location-updates-lease`: pins the deliberate case — an invisible, non-navigating FREE_DRIVE holds no lease
  and its notification keeps showing the last known road/speed — with cases for backgrounding, returning to
  the foreground (fixes resume and the content refreshes) and navigation taking over the lease.
- `current-road-info`: states that the free-driving road/speed status is resolved once per fix and is the
  single source for every surface and the notification (phone label, car label, shade), so the two cannot
  disagree.

## Impact

- `app/src/main/java/com/naviveylin/location/LocationService.kt` — **no new consumer role**; the existing
  `PHONE_MAP` release on `ON_PAUSE` is the intended state for a backgrounded free drive, now pinned by a
  requirement and a case instead of only by code.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — publish the resolved road/speed as the
  free-driving status instead of keeping it private to the screen state.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — `ON_PAUSE` keeps dropping the `PHONE_MAP`
  lease (unchanged behaviour, now covered by a case); the screen observes the shared free-driving status for
  its label instead of a private copy.
- `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt`, `core/src/main/java/com/naviveylin/core/`
  (`NavigationState` / `NavigationViewModel`) — the shared free-driving status the notification reads.
- `app/src/main/java/com/naviveylin/service/NavigationNotificationContent.kt`,
  `NavigationNotificationService.kt`, `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt`
  — read the status, refresh while the mode is active, format without empty fragments.
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — publish its street/speed into the same status
  instead of keeping a screen-local pill source (cross-variant parity, `guidelines/UI.md` §1).
- Guidelines: `guidelines/Design.md` §13 (supersede the "no new GPS load" assurance; §4 for the lease owner's
  lifecycle), `guidelines/UI.md` §1/§7 (notification label parity across the two variants), and
  `guidelines/MapRendering.md` (road/speed resolution cadence, §3 "GPS Deduplication" neighbours).
- Tests: `LocationServiceTest`, `NavigationNotificationContentFormatterTest`, phone ViewModel publish cases,
  a car-screen publish case, and one case per delta scenario below.
- Native/JNI: **none** — no submodule patch, no bridge override, no CMake change.
- Rollback: restore the surface-scoped lease and the navigation-state-only content source; no persisted state
  is involved, so the rollback is a source revert.

## Evidence carried into the design

Device run `emulator-5554` (API 37, `com.framstag.naviveylin` code 19, NRW map), free drive in both phases:
per-UID GPS on-time `+124.9 s / 125 s` with the request active and `+8.0 s / 127 s` with it off; per-UID power
model `+0.0096 mAh` vs `+0.0006 mAh`; FGS alive in both. GNSS mA cannot be produced on the emulator (no
radio, no per-UID `gps=` term) — the design's verification section names the physical-device step for it.
