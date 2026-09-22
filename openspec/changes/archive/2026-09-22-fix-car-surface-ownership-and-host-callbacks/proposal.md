# Proposal

## Why

The AA/AAOS host crashes reported on the driver's seat ("AA crashed after seconds up to a few
minutes, the app did not") have a proven input side: an app process that dies, hangs, or talks to a
dead host session makes the templates host apply a queued operation against an invalidated
`CarHost`, and the host dies with `IllegalStateException: Accessed the car host after it became
invalidated` (TODO.md §50/§51). The fault-isolation work landed in `fix-aaos-host-crash` and
`fix-host-crash-residual-paths` hardened the *answering* side of that path. Five holes on the
**input** side remain open, and four of them were introduced or exposed by the single-owner surface
refactor that landed with them:

- **Two renderers draw one surface.** Every car screen owns its own `RendererGate` +
  `AutoMapRenderer`, and since the surface moved to the session all of them draw the *same*
  `Surface`. The car-app library starts the incoming screen before it stops the outgoing one, so
  during every push/pop and every screen re-entry two renderers can `lockCanvas` one surface
  concurrently. `pause()` does not clear the surface and `RendererGate.detachSurface()` — the method
  that would — has no caller, so a stopped screen's renderer keeps a live surface reference and
  draws through it again on the next start.
- **A released surface can be adopted.** `SessionCarSurfaceHost` keeps an identity set of released
  surfaces, but `onSurfaceAvailable` adopts a delivered instance without consulting it. The car-app
  contract is per *delivery*, not per instance: a re-delivered instance is a new delivery, and a
  released instance must not be locked or drawn again (the same requirement's own scenario).
- **A session can keep talking to a dead host.** `provideCarSurfaceHost()` is `@Singleton` and
  `startSession()` returns early while `registered`, so a session whose `endSession()` never ran
  (the host dropped the connection) leaves the next session holding the previous `CarContext` and
  never re-registers — the app then calls host APIs on an invalidated host, which is exactly the
  host-crash input above.
- **An observation fault kills the process.** `CarScreenObservations.observe()` launches into a
  supervisor scope with no `CoroutineExceptionHandler`, so an exception from a collector body
  reaches the main thread's uncaught handler: the process dies, not the observation.
- **Host callbacks still run native work.** The surface-delivery callback pushes the day/night
  stylesheet flag (a native `setStyleSheetFlag`, which schedules a stylesheet reload on the DB
  thread), and the surface-tap path resolves the native client before entering its coroutine.
  Both are on the host's answering path, where a block is an ANR and therefore an app-process kill.

## What Changes

- Each car screen detaches its renderer from the session surface when it stops (after `pause()`), so
  a stopped renderer holds no surface reference and no overrun buffer, and at most one renderer
  locks the session surface at a time. `resume()` re-establishes the surface through the session's
  retained delivery before the first frame.
- Surface bookkeeping in `SessionCarSurfaceHost` becomes per **delivery**: a re-delivered instance is
  treated as a new delivery (released state cleared for it), a released instance is never adopted for
  drawing, and every delivered instance is released exactly once.
- `SessionCarSurfaceHost` re-registers when the session's `CarContext` differs from the registered
  one, so a session that follows a dropped connection registers its own callback and never calls host
  APIs through a stale context.
- `CarScreenObservations.observe()` confines a fault to the observation that raised it: the failure is
  logged, the sibling observations keep running, and the process survives. This gives the screen seam
  the same guarantee `SessionCarSurfaceHost.dispatch` already gives host callbacks.
- The surface-delivery callback stops pushing the native day/night stylesheet flag; the resolved flag
  is published to state and applied by a background collector (the `rendererGate.surfaceDpi`
  pattern). The surface-tap path resolves the native client inside its existing background block
  instead of on the host thread.
- **Additive, AA/AAOS only.** No phone behaviour, no template content, and no label or hierarchy
  changes; the phone and car UIs keep their current parity. No native/JNI change: all edits are
  Kotlin in `:auto` and `:core` (no libosmscout submodule patch and no `:osmscout-client-java`
  override). Rollback path: revert the commit; the five edits are independent of each other and of
  any data or persisted state, so a revert restores the previous behaviour without migration.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `car-host-fault-isolation` (introduced by the in-flight change `fix-aaos-host-crash`, extended by
  `fix-host-crash-residual-paths`): "Single-owner car surface" gains the per-delivery release rule,
  the "never adopt a released instance" rule and the "a stopped renderer holds no surface" rule;
  "Host callbacks answer promptly" gains the surface-delivery callback (no native stylesheet-flag
  push) alongside the existing surface-tap scenario; a new requirement "Session registration follows
  the host session" covers the re-registration of a session after a dropped connection.
- `auto/screen-observation` (introduced by the in-flight change `fix-car-screen-observer-leak`): a
  new requirement that a fault inside an observation is confined to that observation.
- `auto-map-renderer`: a new requirement that a renderer whose screen is not started holds no car
  surface reference and no overrun frame buffer, and re-acquires the surface and re-renders a full
  frame before its first frame after a start.

**Ordering dependency:** `car-host-fault-isolation` and `auto/screen-observation` exist only inside
their in-flight changes. Archive `fix-aaos-host-crash`, `fix-host-crash-residual-paths` and
`fix-car-screen-observer-leak` before archiving this change; the Kotlin implementation does not
depend on that order (it edits the same files those changes already touched).

**Affected guidelines:** `guidelines/Design.md` §Android Auto / car-host rules (the surface-release
rule becomes per delivery, and a stopped screen's renderer holds no surface), `guidelines/UI.md`
(car surface and lifecycle rules), `guidelines/MapRendering.md` (the renderer's surface-handoff and
overrun-buffer lifetime), `guidelines/Build.md` §10 (the host-crash triage counters gain the
"released instance adopted" and "renderer detached on stop" checks).

## Impact

Modules and files:

- `:auto` — `SessionCarSurfaceHost.kt` (per-delivery release bookkeeping, re-registration),
  `CarScreenObservations.kt` (fault confinement), `RendererGate.kt` (`detachSurface()` on the stop
  path), `AutoMapRenderer.kt` (surface/overrun-buffer release semantics of `pause()`/`detachSurface()`),
  `MapScreen.kt` / `NavigationScreen.kt` / `FreeDrivingScreen.kt` / `DetailsScreen.kt` (stop path
  calls `detachSurface()`; `onCarSurfaceAvailable` no longer pushes the stylesheet flag; the tap path
  resolves the client inside its background block), `CarDaylightApplier.kt` (the flag push becomes a
  published state), `TemplateRefresh.kt` (used by the background applier), plus the existing test
  suites `SessionCarSurfaceHostTest`, `CarScreenObservationsTest`, `RendererGateTest`,
  `AutoMapRendererSurfaceOwnershipTest`, `MapScreenTest`.
- `:core` — `CarSurfaceHost.kt` / `CarSurfaceOwner.kt` (the interface contract for the delivered
  surface and the ownership rules the session host must satisfy), if the re-registration needs a
  contract statement rather than only an implementation change.
- `:app` — `di/AutoServiceModule.kt` (`provideCarSurfaceHost()` scope and its per-session state), if
  the re-registration is fixed here rather than in the host implementation.
- Android components: no manifest, resource, permission or flavor change; `NaviVeylinCarAppService`,
  `NavigationSession` and the car screen stack keep their structure. No new dependency.
- Tests: `:auto` and `:core` unit suites (Robolectric where a `Surface`/`CarContext` is involved);
  the JNI stub and the classloader rule in `AGENTS.md` apply to any test touching `OSMScoutClient`.
- Verification: on-device (AAOS AVD, automotive debug) per `guidelines/Build.md` §10, using the
  `Surface created` / `releasing session surface` / `lockCanvas failed` / `drops` counters across a
  push/pop and a background round trip, plus `dumpsys meminfo` for the overrun-buffer release.
