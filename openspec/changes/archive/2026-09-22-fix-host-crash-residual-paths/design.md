# Design

## Context

Follow-up to the 2026-09-21 host-crash triage (`TODO.md` §50/§51) and to the in-flight changes
`fix-aaos-host-crash` and `fix-car-screen-observer-leak`. The mechanism is fixed: an app-process
death/ANR, or host-side heap churn fed by the app, precedes the templates-host failure. This design
closes the residual paths found in the same code, keeping the car-host rules the in-flight change
established (`guidelines/Design.md` §AA, `guidelines/Build.md` §10).

## D1 — Surface destroy is scoped to the surface instance

**Problem.** `SessionCarSurfaceHost.onSurfaceDestroyed`
(`auto/src/main/java/com/naviveylin/auto/SessionCarSurfaceHost.kt:112`) clears `active` and calls
`release(current)` unconditionally, then releases a *different* delivered instance if
`destroyed !== current`. Sequence that breaks it: host delivers A (adopted) → host delivers B
(`onSurfaceAvailable` releases A, `active = B`) → host reports the destroy of A → `current = B` is
cleared and released while the host still composites through it. That is precisely the failure the
in-flight change removed from the renderer (`AutoMapRenderer.onSurfaceCreated` no longer releases),
and it contradicts the scenario "Registration superseded".

```
host:  A available ──► B available ──► A destroyed
app:   active=A        release(A)      current=B
                       active=B        active=null          <-- B forgotten
                                       release(B)            <-- host's live queue
```

**Alternatives**

1. **Identity-scoped destroy (chosen).** Clear/release only when `destroyed == null ||
   destroyed === current`; otherwise release the named (superseded) instance only, keeping the live
   one. Matches the API contract (a destroy names the container being destroyed), keeps the single
   release site, and needs no extra bookkeeping. Risk: low — a host that destroys without ever
   having delivered that instance still gets exactly one release (the existing `released` identity
   set remains the double-release guard).
2. **Delivery-generation tokens** (a monotonically increasing token per delivery, destroy matched on
   token) — equivalent behavior, more state; no benefit while the identity comparison is exact.
3. **Move surface lifetime back to the screens** (each screen releases on stop) — this is the design
   the in-flight change deliberately replaced: the library starts the incoming screen before it
   stops the outgoing one, so `onStop` releases a surface the incoming screen is drawing through.
   Rejected.

**Keep in sync:** the `released` identity set stays as the exactly-once guard, so a duplicate destroy
of the same instance remains a no-op (existing test
`auto/src/test/java/com/naviveylin/auto/SessionCarSurfaceHostTest.kt`).

## D2 — One guard for every screen's template build

**Problem.** `RemoteUtils.dispatchCallFromHost` rethrows an app exception on the main thread
(TODO §51), so a throw in any `onGetTemplate` kills the process and takes the host down. Today only
`MapScreen`, `NavigationScreen`, `FreeDrivingScreen`, `RouteDescriptionScreen` guard their build;
11 screens and the session's inline error screen do not.

**Alternatives**

1. **One shared guard used by each screen (chosen).** A small helper (in `SafeScreen.kt`, e.g.
   `guardedTemplate(tag) { … }` returning `SafeScreen.errorTemplate` on failure) called by every
   `onGetTemplate`. Minimal diff per screen, keeps the per-screen return type, keeps the existing
   `DiagnosticsLog` template tag, and is directly unit-testable per screen. Risk: low; a missed
   screen is visible as a missing call site (test per screen).
2. **Make `SafeScreen` the base class** (`class FooScreen(...) : SafeScreen(carContext, { … })`) —
   rejected: stateful screens register lifecycle observers in `init`, which a wrapper's `init`
   ordering cannot preserve (documented in the `SafeScreen` KDoc), and it would change every
   constructor.
3. **Contain the throw at the process boundary** (default `UncaughtExceptionHandler`, or catching in
   the library callback) — the library rethrows synchronously on the main thread; a handler cannot
   resume the session, only log the death, and the host is already gone. Rejected as a fix (keep
   `DiagnosticsLog.installCrashHandler` as the forensics path only).

**Scope note:** the session's error screen (`NavigationSession.showError`, an inline
`object : Screen`) is included — it runs when the app is already degraded, and its message comes from
arbitrary state text.

## D3 — Idempotent free-driving restore

**Problem.** `NavigationSession.kt:282` (`onCreateScreen`) and `:442` (`onWarmupComplete`) both call
`restoreDrivingMode()` (`:667`); the free-driving flag is retained across session destroy
(`FreeDrivingScreen.kt:354`), so a host/session restart while free driving is active pushes two
`FreeDrivingScreen`s — each with its own `AutoMapRenderer` (scope, three loops, overrun bitmap), and
one ghost view left under the top one.

**Alternatives**

1. **One-shot flag in the session (chosen).** `private var freeDrivingRestored = false`, set when
   the push happens; both call sites go through one method. Mirrors the existing `navScreenPushed`
   pattern for the navigation screen, so the session has one idiom for "already on the stack".
   Reset on `retryStartup()`/session end. Risk: low.
2. **Remove the `onCreateScreen` call** (restore only from the warmup-complete/sync path) — almost
   equivalent and fewer lines, but it silently depends on warmup always completing after
   `onCreateScreen`; a session whose warmup is already complete at `onCreateScreen` (fast restart)
   would then never restore. Not chosen as the only change, but the two call sites are unified
   either way.
3. **Ask the screen stack whether a free-driving view is up** — `ScreenManager` exposes no
   top-screen query (already documented for `navScreenPushed`). Rejected.

## D4 — Lane image reuse and a rebuild bound

**Problem.** Each template build calls `ManeuverGlyphs.lanesImage`
(`auto/src/main/java/com/naviveylin/auto/ManeuverGlyphs.kt:38`) which allocates a fresh `Bitmap` +
`CarIcon`, and the build runs on every navigation-state emission
(`NavigationScreen.onNavigationState` → `hasStateChanged`, which compares `nextInstruction.distanceTo`,
i.e. ~1/s while driving). Every rebuild ships a new bitmap over IPC to the host; the manoeuvre icons
are already cached per `TurnType`, the lane strip is not. This is the strongest app-controlled
candidate for host-side heap churn ending in a host crash after seconds-to-minutes.

**Alternatives**

1. **Memoize by lane state + bucket the rebuild (chosen).** Cache the lane image keyed by the lane
   list and the suggested range (last state wins: the host panel shows one step's lanes at a time),
   and rebuild the template only when the displayed content changes materially — step or manoeuvre,
   street name, lane state, or the displayed distance bucket, using the existing display rounding
   (`roundDistanceMeters`: raw below 50 m, 50 m steps below 1 km, 100 m above) for both the
   distance-to-turn and the remaining route distance. That rounding is what the host panel and the
   ETA card already show, so no displayed number changes; it only stops the template being rebuilt
   for a change the driver cannot read. Risk: the arrival estimate keeps its own per-second
   comparison (a shifted estimate is displayed content), so the residual template rate is bounded
   by the estimate's update rate, not by the distance buckets — measured on device (task 4.4) with
   the estimate bucketed to the displayed minute as the next lever if that rate turns out to matter.
2. **Cache only the image** — removes the per-second bitmap allocation, keeps the 1 Hz template
   rebuild (host re-renders per template). Cheaper, does not address the host churn.
3. **Let the host count the distance down** (stop sending distance each second, rely on the trip /
   ETA) — the host renders the number it was given for the current step; the panel would freeze
   between steps. Rejected as the primary mechanism (bucketing is the compromise).

**Interaction:** `NavigationTemplateMapper.hasTripChanged` (trip publishing, ~1 Hz) and the
notification post are separately deduplicated (`hostVisibleContentChanged`); this decision only
tightens the *template* path.

## D5 — One notification-identity source

**Problem.** `NavigationNotificationService.NOTIFICATION_ID = 1002` and
`CarStyleLoadNotifier.NOTIFICATION_ID = 1002`, both posted with a null tag from one process, so the
style notice takes the identity of the ongoing notification — which is also the foreground-service
notification and the car rail-widget hint carrier (`TODO.md` §60).

**Alternatives**

1. **A shared `NotificationIds` source, consumed by both callers (chosen).** Values:
   download 1001, navigation 1002, map style 1003; a unit test asserts distinctness and that the
   style notice never posts on the navigation id. It lives in `:core`, not `:app` as first
   planned: the module graph is `:app` -> `:auto` -> `:core` and `CarStyleLoadNotifier` (the
   style notice) is in `:auto`, so `:auto` cannot see an `:app` constant.
2. **Only move the style notice to a free id** — fixes the collision, leaves two unowned constants
   that can collide again.
3. **Use distinguishable tags instead of distinct ids** — the platform key is (tag, id); tags would
   work, but the notification-identity intent is clearer with distinct ids and the existing code has
   no tag convention. Rejected.

## D6 — Tap path off the host thread

**Problem.** `MapScreen.onLocationSelected` (`MapScreen.kt:688`) calls
`entryPoint.autoClientProvider().client()` synchronously from the surface-click callback (the client's
first touch builds it: stylesheet sync, `dlopen`, native setup), and creates a fresh
`CoroutineScope(SupervisorJob() + Dispatchers.Main)` per tap that is never cancelled.

**Alternatives**

1. **Resolve inside the existing background block, use the screen's scope (chosen).** One-line
   move plus `scope.launch`; the scope is cancelled in `onDestroy`, so no per-tap retention.
   Risk: low (`mag`/`viewportState` reads stay on the main thread before the switch).
2. **Pre-resolve the client at screen start** — pulls the native build into a host callback path
   (constructor/warmup) and duplicates the renderer-init logic. Rejected.
3. **Keep the per-tap scope, cancel it in `onDestroy`** — bounded, but still allocates a scope and a
   `SupervisorJob` per tap for no reason. Rejected.

## Threading model

- Screen lifecycle callbacks, gesture callbacks, surface callbacks, `onGetTemplate`, `invalidate()`
  and every `ScreenManager` call stay main-thread (unchanged).
- Native work added or moved by this change runs on `Dispatchers.Default` (`MapScreen` tap path,
  existing renderer init) and returns to the main thread to touch host state.
- The guard helper and the lane-image cache are stateless/thread-confined: the guard runs inside
  `onGetTemplate` (main), the cache is only read from the template build (main) — no new locks.
- The restore flag is session-confined, written from main-thread paths only
  (`onCreateScreen`, `onWarmupComplete`, `retryStartup`).
- No new threads, no new coroutine scopes; the change removes one scope per tap.

## Risk assessment

| Change | Risk | Mitigation |
| --- | --- | --- |
| D1 destroy scoping | Low — release semantics | Extended `SessionCarSurfaceHostTest` (superseded-instance destroy, duplicate destroy, unadopted destroy) |
| D2 template guard | Low — wrapper only | Per-screen test that a throwing build returns the error template; `:auto` suite green |
| D3 restore flag | Low — stack composition | Test seam for the restore decision (pure function) + session-level test of one push |
| D4 lane cache + rebuild bound | Medium — visible distance cadence in the host panel | Cache test + bucket-boundary test; on-device check that the instruction panel still counts down and the template rate drops (~1/s → per bucket) |
| D5 notification ids | Low | Uniqueness test + existing notification builder tests |
| D6 tap path | Low | Existing click-path tests; test that no client is resolved on the host thread |

## Verification

**Unit tests (`./gradlew :auto:testDebugUnitTest :app:testDebugUnitTest`, plus `:core` unchanged):**

- `SessionCarSurfaceHostTest`: destroy of a superseded instance keeps the live surface and releases
  only the named one, exactly once; duplicate destroy unchanged; unadopted destroy unchanged.
- Template guard: one test per screen with `onGetTemplate` (a throwing build yields the error
  template, logs `TEMPLATE`, and does not propagate).
- Restore: `shouldRestoreFreeDriving` unchanged plus a session-level test that a second restore does
  not push.
- Lane image: same lane state → same image instance; changed lanes → different image.
- Rebuild bound: unchanged displayed content (same bucket) → no `invalidate`; bucket crossed or step
  changed → one `invalidate`.
- Notification ids: all three distinct; the style notice never posts on the navigation id.
- Tap path: fake client recording the calling thread — no native client resolution on the main
  thread from the click path.

**On-device (automotive AVD, `guidelines/Build.md` §10 recipe; install first, never over a live
session):**

```
L=$(adb -s emulator-5556 logcat -d | grep -E 'AutoMapRenderer|CarSurfaceHost')
lock OK / surface created / releases / failures / drops   # baseline per Build.md §10
adb logcat -d | grep -E 'Diag/HOST'                       # NOTIF/trip cadence
adb logcat -b crash -d                                    # host stack, if any
```

- Browse → navigate → HOME → return: same surface id on re-entry, no `releasing session surface`
  between, `lock OK` continuing, 0 `surface invalid`/`lockCanvas failed`.
- Free-driving restart: session restart with free driving active shows **one** free-driving view
  (`Diag/SESSION Push FreeDrivingScreen (restore)` once per session); BACK once returns to the map
  root.
- Template cadence: with navigation active, the `HOST`-correlated template/notification rate drops
  from ~1/s to the bucket rate; the host instruction panel still shows a plausible countdown.
- No host crash for ≥10 minutes of driving with lane hints enabled.

**Verification-only checks (evidence first, no behavior specified yet):**

1. A *refused* `startForeground` followed by `stopSelf()`
   (`NavigationNotificationService.kt:73`) — check `adb logcat -d | grep
   ForegroundServiceDidNotStartInTime` after a car-only session; if the platform's deadline still
   fires, the degrade path itself kills the process (record as a `TODO.md` finding or fold into this
   change).
2. `TODO.md` §47 — the diagnostics file is never created on the automotive build: log the resolved
   file in `DiagnosticsLog.init` and check `run-as … ls -la files/` plus
   `logcat -d | grep 'append failed'`; the missing file is what makes automotive triage logcat-only.

**Guideline updates:** `guidelines/Design.md` (car-host section: identity-scoped surface destroy,
the "every screen's template build is guarded" rule), `guidelines/Build.md` §10 (the two checks
above, and the free-driving-restore baseline line).
