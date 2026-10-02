# Proposal

## Why

When the Android Auto session and the phone UI share a process (always the case for projection), they
share four process-global resources whose ownership was never arbitrated. `LocationService` has no
subscription refcount, so one surface's stop kills the other's fixes (phone `ON_PAUSE` at
`app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt:821` vs the car's
`AutoLocationProvider.stop()` at `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt:230`);
`SettingsStorage` does a read-modify-write without a lock
(`app/src/main/java/com/naviveylin/data/SettingsStorage.kt:91`), so a car preferences save and a phone
save can lose an update; and the native tile-data cache capacity is client-global with a
first-writer-wins policy (`core/src/main/java/com/naviveylin/core/NativeTileDataCache.kt`), so a car
session opening databases first silently degrades the phone to 128 tiles per database while the phone
first leaves the car on 512. None of these needs the phone UI to be locked — they need explicit
ownership rules.

## What Changes

- **Location updates become refcounted leases.** `LocationService` exposes an acquire/release API keyed
  by consumer (`phone-map`, `phone-nav`, `car-session`, `car-nav`); updates run while at least one
  consumer holds a lease and stop when the last one releases. Unlike `DrivingModeProvider`, a lease is
  **released when its owner dies** (a dead surface must not hold GPS forever). Each acquire/release is
  logged with the consumer and the resulting count. **BREAKING** for the internal start/stop API used
  by four call sites today.
- **Settings writes become serialized transactions.** `SettingsStorage` gains an `update { … }`
  read-modify-write under one lock; the car's preferences save keeps patching only its owned subset,
  so phone-only fields stay intact and no update is lost to an interleave.
- **The native tile-data cache capacity becomes ordering-independent.** The per-client decision raises
  the capacity to the highest value any surface requested in the process and never lowers it, so a car
  session opening first no longer degrades the phone; a car-only process still runs on the car value.
- **A car-session-presence signal reaches the phone UI**: a process-wide flag published by the car
  session start/end, used for an advisory indicator on the phone ("navigation is running on the car").
  The functional half of the original idea — preventing competing start/stop commands — is **not**
  captured here: the sibling change `one-navigation-engine` removes the second engine entirely, so a
  competing command can no longer exist. If the indicator is wanted purely as information, it stays in
  this change; if not, it can be dropped without affecting the rest.
- **The phone UI is deliberately not locked.** Browsing the map, setting a destination and running
  navigation on the phone stay available while a car session is live; `one-navigation-engine` makes the
  session single-owner, so no lock is required for correctness.
- Not in scope: no native/JNI change, no rendering change, no UI redesign beyond the advisory
  indicator, no settings schema or migration change, no manifest change.

## Capabilities

### New Capabilities

- `location-updates-lease`: how GPS subscriptions are shared by several consumers in one process —
  acquire/release by consumer identity, updates only while leased, release on owner death, and
  diagnostics that name the consumer.
- `settings-persistence`: the persistence contract for the shared settings file — writer-serialized
  read-modify-write, per-writer field ownership, phone-only fields preserved by a car write, and no
  lost updates when two writers act concurrently.
- `car-session-presence`: a process-wide signal that a car session is live, and what the phone surface
  does with it (advisory only).

### Modified Capabilities

- `native-tile-data-cache`: the client-global capacity is no longer first-writer-wins — it is raised to
  the highest capacity requested by any surface in the process and never lowered, so the decision no
  longer depends on which surface opened databases first.

## Impact

Modules and files:

- `:app` — `app/src/main/java/com/naviveylin/location/LocationService.kt` (lease API replacing the bare
  `startLocationUpdates`/`stopLocationUpdates` pair), and its four start / three stop call sites:
  `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (`:759`, `:782`, `:821`, `:828`, `:840`),
  `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (`:3242`, `:3245`),
  `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` (`:76`) and
  `app/src/main/java/com/naviveylin/navigation/AANavigationController.kt` (`:97`) — note that the
  sibling change `one-navigation-engine` deletes the last two, so this change targets the surviving
  consumers (`MapCanvasScreen`, `MapCanvasViewModel`, the car session/`AutoServiceModule`).
- `:app` — `app/src/main/java/com/naviveylin/data/SettingsStorage.kt` (transaction API) and its callers
  `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` (`:242`, `:252`) plus the phone settings
  writers.
- `:core` — `core/src/main/java/com/naviveylin/core/NativeTileDataCache.kt` (raise-never-lower policy)
  and the new `car-session-presence` seam; `core/src/main/java/com/naviveylin/core/AutoEntryPoint.kt`
  (the presence publisher is resolved by the car session, the consumer by the phone).
- `:auto` — `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` (publish presence on start /
  clear on destroy) and the phone side of the indicator in `:app` UI.
- Android components: no manifest or service change. `AutoLocationProvider` keeps its `start()`/`stop()`
  contract but becomes a lease holder instead of a raw starter/stopper.
- Tests: `app/src/test/java/com/naviveylin/location/LocationServiceTest.kt` (6 existing cases use the
  bare start API), `app/src/test/java/com/naviveylin/data/SettingsStorageTest*`,
  `core/src/test/java/com/naviveylin/core/NativeTileDataCacheTest.kt`, and a new presence-seam test.

Guidelines affected: `guidelines/Design.md` §3 (single owner per piece of state — the lease is the
owner rule for the location subscription) and §12 if it enumerates single sources of truth;
`guidelines/UI.md` for the advisory indicator's wording and its phone/car parity statement.
`guidelines/MapRendering.md` is not affected.

Scope: **general** (the arbitration is needed wherever two surfaces share the process; on AAOS
standalone the same seams simply have one consumer).

Additive/breaking: additive for user-visible behaviour, **breaking** for the internal
`LocationService` start/stop API and for the tile-cache policy's observable outcome (the phone's
capacity is no longer reduced by a car-first ordering).

Rollback: revert the change as one commit; the four call sites return to start/stop, the settings
transaction returns to the plain save, and the cache policy returns to first-writer-wins. No data
migration is involved.

## Decisions and Open Questions

### Decided

1. **Arbitrate, do not lock the phone UI.** The wrong-output defects (killed GPS, lost settings
   update, degraded cache) come from unarbitrated shared resources, not from the phone being usable;
   locking would remove legitimate passenger browsing and destination entry for no correctness gain.
2. **The lease is release-on-death, unlike `DrivingModeProvider`'s retain-on-death.** The two seams
   answer opposite questions ("is driving active?" keeps the last claim; "who wants GPS?" must not
   outlive its owner), and both semantics are documented with that contrast.
3. **The tile-cache policy raises, never lowers.** With the phone in the process the 512-tile budget is
   already spent, so honouring it costs nothing extra, while first-writer-wins made the phone's
   degradation depend on session order. A car-only process is untouched and keeps the car value.
4. **The session-presence indicator is informational.** Its original functional purpose (preventing a
   competing start/stop) is subsumed by `one-navigation-engine`'s single engine.
5. **No native change**: the cache capacity call and the location APIs are unchanged at the JNI layer —
   no submodule patch and no `:osmscout-client-java` override.

### Open

- Whether the advisory indicator should be captured at all (see Decided 4); dropping it reduces this
  change to the three resource seams and does not affect the other capabilities.
- Whether `AutoLocationProvider` keeps its `start()`/`stop()` surface as a thin lease wrapper or the car
  screens hold leases directly. Implementation detail for `design.md`.
- Ordering with `one-navigation-engine`: the lease should land first or with it, since the engine is a
  lease consumer and must not reintroduce the unbalanced start (recorded in that change's design).
