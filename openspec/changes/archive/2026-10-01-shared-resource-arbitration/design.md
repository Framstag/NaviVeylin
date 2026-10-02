# Design

## Context

See `proposal.md` — Why. What shapes the approach:

- `LocationService` (`app/src/main/java/com/naviveylin/location/LocationService.kt:382`) is an `:app`
  `@Singleton` with a bare `startLocationUpdates()` (`:501`) / `stopLocationUpdates()` (`:626`) pair.
  `startFusedUpdates` is idempotent (`:519` returns when a callback is registered) and
  `stopLocationUpdates` removes **both** provider paths unconditionally (`:626-639`). Four call sites
  start (phone `NavigationViewModel:76`, `AANavigationController:97`, `AutoServiceModule:209`, and the
  phone map screen at `:759`/`:782`/`:828`) and three stop (`MapCanvasScreen:821`/`:840`,
  `AutoServiceModule:230`) — none of them knows about the others, and the phone's own start/stop pair is
  already unbalanced (it starts in `init` and never stops).
- The car side already keeps a *surface-local* refcount: `NavigationSession.locationStarted` guards its
  own start/stop (`auto/src/main/java/com/naviveylin/auto/NavigationSession.kt`). The seam generalizes
  that idea to the process.
- `DrivingModeProvider` (`core/src/main/java/com/naviveylin/core/DrivingModeProvider.kt`) is the existing
  precedent for a surface-keyed shared flag, and it deliberately **retains on death** ("destroying a
  surface must never claim driving stopped"). The location lease answers the opposite question and must
  therefore release on death — the contrast is intentional and documented.
- `SettingsStorage` (`app/src/main/java/com/naviveylin/data/SettingsStorage.kt:91`) writes the whole
  `AppSettings` document; the car path
  (`app/src/main/java/com/naviveylin/di/AutoServiceModule.kt:242`, `:252`) does
  `load() -> patch owned subset -> save()`, so the read-modify-write span is unprotected.
- `NativeTileDataCache` (`core/src/main/java/com/naviveylin/core/NativeTileDataCache.kt`) stores one
  capacity per client identity in a `WeakHashMap` and rejects any later, different value
  (`TileCacheConfig.REJECTED`). The native side re-applies the stored value to every open database on
  every render (`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:1411-1429`,
  only when `> 0`), so a raise is cheap and a process-wide value is the real cost driver
  (`TODO.md` §63/§65).
- No spec currently covers location-subscription lifetime, settings persistence or session presence;
  `native-tile-data-cache` covers the capacity but not the multi-surface ordering.
- Constraints: `guidelines/Design.md` §3 (one owner per piece of state) and §4 (threading, lifecycle);
  `guidelines/UI.md` for the indicator wording and the phone/car parity statement. No native change and
  no Android component change is involved.

## Goals / Non-Goals

**Goals:**

- One owner rule per shared process-global resource, with the rule testable in a host JVM test.
- Survive every surface order: car-first, phone-first, both live, either one dying.
- Keep the phone fully usable while a car session is live.

**Non-Goals:**

- Not locking or gating phone UI actions (`one-navigation-engine` removes the competing command that a
  lock would have been for).
- Not making the app multi-process safe: both the settings file and the native client are single-process
  resources today (projection shares the process, AAOS standalone has one), so an in-process lock is the
  correct scope.
- Not re-tuning the two cache capacities; only the ordering rule changes.
- Not persisting the session-presence flag beyond the process.

## Decisions

### D1 — A named lease with per-consumer idempotency

Chosen: `LocationService.acquire(consumer: String): LocationLease` / `LocationLease.release()`, where the
service keeps a set of consumer names; the first acquire starts the provider path, any acquire while a
set entry with that name exists is a no-op for the device and for the set, and the last release stops
it. `release()` is idempotent per lease object, so a double release cannot stop another consumer's
updates.

Alternatives:

- *Plain refcount + start/stop*: no identity, so a release cannot be attributed, the diagnostics
  requirement is unsatisfiable, and a surface that starts twice leaks a count. Rejected.
- *Each surface gets its own provider client*: two Fused clients / two `LocationManager` requests
  violate the "exactly one provider path" rule (`gps-provider-selection`), double the battery cost, and
  give two independently smoothed fix streams. Rejected.
- *A distinct token per `acquire()` call*: makes an unbalanced caller leak a lease forever (today's
  defect class) instead of collapsing it. Rejected in favour of per-consumer idempotency.

### D2 — The lease releases when its owner ends

Chosen: release points are the owning component's end of use — phone map `ON_PAUSE`/`onDispose`
(`MapCanvasScreen.kt:821`/`:840`), the navigation engine's stop/arrival path
(`one-navigation-engine`), the car session's `onStop`/`onDestroy` via `AutoLocationProvider.stop()`.
A destroyed surface releases; a dead process takes the OS registration with it.

Alternatives:

- *Retain-on-death like `DrivingModeProvider`*: leaves the provider registered after a surface dies —
  exactly the leak the arbitration is for, and the reason the two seams differ. Rejected.
- *Lease tied to the foreground service*: couples GPS to the notification's lifetime, which exists for
  navigation *and* free driving and can outlive both. Rejected.

### D3 — `AutoLocationProvider` becomes a thin lease wrapper

Chosen: keep its `start()`/`stop()` contract for the car screens and the session, implemented as
acquire/release of the `car` consumer, so the car-side call sites do not change.

Alternatives:

- *Car screens hold leases directly*: touches `NavigationSession` plus four screens and their
  `CarScreenObservations` classes for no behavioural gain. Rejected.
- *Delete `AutoLocationProvider`*: the car screens consume its `position()` flow for the map and search
  distance reference; the flow stays useful. Rejected.

### D4 — `SettingsStorage.update { }` under one lock

Chosen: add `suspend fun update(transform: (AppSettings) -> AppSettings)` that reads, transforms and
writes while holding a `Mutex` (or a single permit), and route every writer through it: the car's
`save(settings)` and `saveCarAnchor(…)` (`AutoServiceModule.kt:242`, `:252`) and the phone's settings
writers. `save(settings)` stays for whole-document writers, implemented on top of `update { settings }`
if that preserves its semantics.

Alternatives:

- *Serialize through a single writer actor/channel*: same ordering guarantee with more machinery
  (queue, backpressure, cancellation semantics) than a `Mutex` around three statements. Rejected.
- *File-level lock (`FileChannel.lock`)*: only needed for multiple processes; the app is single-process
  today, and the added failure mode (lock not supported / released by process death) buys nothing.
  Rejected, with the single-process assumption recorded as a constraint.

### D5 — Raise the client capacity, never lower it

Chosen: `NativeTileDataCache.applyTo` keeps the per-client map but stores the **maximum** requested
value: an equal value reports `UNCHANGED`, a higher value applies and reports `APPLIED`, a lower value
reports `REJECTED` without touching the client. Consequently the phone's 512 wins whenever the phone is
in the process (its memory is already committed), and a car-only process keeps 128.

Alternatives:

- *Always request the larger value regardless of surface*: removes the car RAM tuning that
  `fix-car-tile-data-cache` added for the head-unit budget (`TODO.md` §51/§65). Rejected.
- *Purge/re-create the native client per surface*: two DB threads and two tile-data sets per database —
  the option `fix-client-dpi-surface-leak` already rejected on the car RAM budget. Rejected.

### D6 — Session presence as its own `:core` seam

Chosen: a `:core` interface (`CarSessionPresence`) with an observable `StateFlow<Boolean>` and a
`setActive(…)` setter, implemented as an `:app` `@Singleton`, published by `NavigationSession.onStart`
/ `onDestroy`, resolved by the phone UI through Hilt. In-memory only — never persisted, so a process
restart starts clear.

Alternatives:

- *Derive it from `DrivingModeProvider.freeDrivingActive`*: only free driving is published, and
  navigation on the car (the case the indicator names) is not. Rejected.
- *Derive it from `NavigationState.isNavigating`*: session liveness ≠ navigation; a car session that is
  merely browsing would not show, and a phone session would be indistinguishable. Rejected.
- *Reuse `SessionHostGate`/session state from `:auto`*: not reachable from the phone module and not
  session-lifecycle-complete (`:auto` owns its own gate). Rejected.

## Threading and lifecycle (`guidelines/Design.md` §4)

- **Lease state** is a small set guarded by a lock on the service (the same shape as
  `NativeTileDataCache`'s `synchronized` map), because acquire/release can arrive from the main thread
  (Compose lifecycle, session callbacks) and from background work. Provider registration itself stays on
  the main thread exactly as today (`Looper.getMainLooper()` for `LocationManager`; Fused callbacks).
- **Settings transaction** is suspend-based: `Mutex.withLock { load(); patch; save(); }` on the existing
  `ioDispatcher`, so no caller blocks a thread and the existing `withContext(ioDispatcher)` behaviour is
  preserved.
- **Presence** is written on the main thread by the session lifecycle and read by Compose through
  `StateFlow`; no dispatcher is introduced.
- **Lifecycle**: every lease owner releases at its own end (D2); the settings lock lives for the process;
  the presence flag is cleared on session destroy and is not persisted.

## Risks / Trade-offs

- [A caller forgets to release, so GPS runs after its user is gone] → per-consumer idempotency (D1)
  makes a repeated acquire harmless, the diagnostics line names the consumer that started the leak, and
  a test pins "destroyed surface releases". The phone's current unbalanced `init` start
  (`NavigationViewModel:76`) disappears with `one-navigation-engine`.
- [Two components accidentally use the same consumer name, so one release stops the other's use] →
  consumer names are constants in the lease seam (a phone role and a car role), and a test pins that two
  distinct consumers coexist and that releasing one leaves the other's updates running.
- [The settings `Mutex` cannot help if the two surfaces ever live in different processes] → the
  single-process assumption is stated in the capability's context; a future multi-process split needs
  file-level serialization, recorded as a follow-up note rather than built now.
- [Raising the cache capacity mid-session triggers native re-configuration] → the native layer re-applies
  the stored value per render to every open database, so a raise is a capacity change only; the device
  check watches for a render spike and for the diagnostics line asserting the raised value.
- [The presence indicator is noise for users who never look at the phone while driving] → it is
  informational, blocks nothing, and is one row; its removal is an open question, not a risk to the
  other capabilities.
- [Removing the bare start/stop pair breaks the six existing `LocationServiceTest` cases] → the test
  class is updated in the same change, and new cases cover the lease semantics rather than the toggle.

## Migration Plan

1. `LocationService` lease API + the surviving call sites (phone map screen, `MapCanvasViewModel`, car
   via `AutoLocationProvider`) + updated and new tests. Land this **first or together with**
   `one-navigation-engine`, which consumes the lease.
2. `SettingsStorage.update { }` + both car call sites + phone writers + a concurrent-writer test.
3. `NativeTileDataCache` raise-never-lower + tests (car-first, phone-first, car-only, equal value).
4. Presence seam + session publish/clear + phone indicator + tests.
5. Docs: `guidelines/Design.md` §3 (the lease as the location owner rule, with the retain-on-death
   contrast) and §12 if it lists single sources of truth; `guidelines/UI.md` for the indicator wording
   and the parity statement (the car has no counterpart — platform constraint).
6. Gates: both flavor builds, full unit suite, and on-device checks — phone paused during a car drive
   keeps car fixes; car session end leaves phone fixes running; a car and a phone settings change in the
   same minute both persist; `adb logcat -s NaviVeylin` shows the lease acquire/release lines and the
   raised cache value.
7. Rollback: revert the single commit — call sites return to start/stop, the settings transaction
   returns to plain save, the cache policy returns to first-writer-wins, and the presence flag
   disappears with its seam. No data migration.

## Open Questions

- Whether the informational indicator ships with this change or is deferred (proposal Open 1); deferring
  it removes capability `car-session-presence` from the change and leaves the three resource seams.
- Whether `SettingsStorage.save(settings)` should remain a public whole-document writer or be folded
  into `update { }` for every caller; both preserve the capabilities, and the choice depends on how many
  call sites write a whole document rather than patch fields.
