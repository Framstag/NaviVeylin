# Design

## Context

See `proposal.md` — Why. The facts that shape the approach:

**The car-app library rethrows app exceptions on the main thread.** `RemoteUtils.dispatchCallFromHost`
(`androidx.car.app:app:1.7.0` sources, `utils/RemoteUtils.java:140-158`) wraps every host call in
`ThreadUtils.runOnMain`, sends the host a `FailureResponse` when the callback throws, and then
`throw new RuntimeException(e)` — on the app's main thread. A throw inside a template-row click
listener therefore kills the process *and* reports the failure to the host (TODO.md §51). The host's
own queued template work then runs against an invalidated `CarHost`, which is the host-side
`FATAL EXCEPTION` the driver sees as "Android Auto crashed".

**What the library itself does *not* throw on** (checked in the 1.7.0 sources): `ScreenManager.push`,
`remove`, `pop` and `popToRoot` are documented no-ops once the app lifecycle is `DESTROYED`, and throw
only on a non-main thread. The realistic throw sources are `CarContext.getCarService(...)` when the
host connection is gone, `ScreenManager.getTop()` on an empty stack, and any screen *construction*
that does work we moved off the host thread (entry-point resolution, provider first-touch) failing.

**What is already guarded** (all 2026-09-21, unarchived): the whole `SessionCarSurfaceHost` dispatch
wrapper, every car screen's template build (`car*Template`), `NavigationManagerController`,
`NavigationNotificationService`, the renderer frame draw, the surface lifetime, and the per-screen
observation seam (`CarScreenObservations` carries a `CoroutineExceptionHandler`).

**What is not** — the three paths this change closes:

```
click path (7 sites)        session coroutines                    session lifecycle
  setOnClickListener         observeJob / errorJob / tripJob        onStart / onDestroy
        |                          |                                    |
        v                          v                                    v
  ScreenManager.push        push / popToRoot / getCarService      surfaceHost().startSession()
  (library rethrows)        (no CoroutineExceptionHandler)        syncHostWithCurrentState()
        |                          |                                    |
        +--------------------------+------------------------------------+
                                   v
                    uncaught on main -> process death -> host FATAL
```

Scope constraints: Android Auto / AAOS only; module graph is `:app` → `:auto` → `:core`, and `:core`
has no car-app dependency, so the seam must live in `:auto`. No native change.

## Goals / Non-Goals

**Goals**

- A fault in *any* host mutation, wherever it runs (click callback, screen scope, session scope,
  session lifecycle), is confined to that piece of work and never reaches the process's main thread.
- The session's screen-stack bookkeeping reflects the mutations that actually succeeded.
- An error notice never removes the navigation view from the stack.
- Tests prove each of the above without a device; the device run confirms it in the real host.

**Non-Goals**

- Reducing host traffic or the app/host memory growth (the `TODO.md` §49/§56/§63/§64/§65 findings) —
  separate changes.
- The surface lifetime, template-build guards, trip/notification guards — already done.
- Moving the *native* work off the main thread in the click path — `fix-host-crash-residual-paths`
  task 6.1 did the map tap path; nothing here re-opens it.
- Phone behaviour, the native/JNI layer, the map render pipeline, spec or artefact upkeep outside the
  one capability named in the proposal.

## Decisions

### D1. Two mechanisms, not one: a guarded mutation seam *and* a scope fault handler

Both are needed, because the two failure surfaces are different:

- a **host callback** runs on the library's dispatch path, with no coroutine to confine — an
  exception there is rethrown by the library. Only a `try`/`catch` around the mutation confines it.
- a **coroutine body** (session observer, screen collector, deferred push) has no host callback to
  guard — an exception there goes to the scope's uncaught handler. Only a
  `CoroutineExceptionHandler` on that scope confines it.

Chosen: one seam `guardedHostCall(what) { … }` for every `ScreenManager`/`AppManager` mutation, plus
a fault handler on each scope that owns host-mutating work.

Alternatives considered:

- *Only `try`/`catch` at every call site* — rejected: hand-maintained per-site guards are exactly how
  `fix-car-screen-observer-leak`'s class of defect arose (the guard exists at the sites someone
  remembered). A seam makes "did the guard exist?" a grep-able property, and the on-device `HOST`
  line comes from one place.
- *Only fault handlers on the scopes* — rejected: does nothing for the click path, which is the
  library's rethrow path.
- *A `ScreenManager` delegate injected into every screen* — rejected: the library resolves
  `ScreenManager` from the `CarContext`, so a delegate would have to be threaded through every screen
  constructor; the seam is the same guarantee at a fraction of the churn.

### D2. The seam lives in `:auto`, next to `SafeScreen`, and returns an outcome

`auto/src/main/java/com/naviveylin/auto/SafeScreen.kt` already holds the module's shared car-facing
guards; the seam joins them (or a sibling file beside it, to keep `SafeScreen.kt` about templates).
It returns `Boolean` (mutated / rejected) so callers set bookkeeping from the outcome (D4).

Alternatives: `:core` (rejected — it would need a `Context`-shaped abstraction to reach
`getCarService`, an inversion for no gain); `:app` (rejected — the screens and the session both live
in `:auto`; a seam in `:app` could not be called from `:auto`).

### D3. One shared fault-handler factory for the session and screen scopes

`CarScreenObservations` builds its handler inline. This change extracts the shape into one small
factory (log with a name, mirror into `DiagnosticsLog` under the sibling tags) and uses it for the
session scope, each car screen's own scope, and the observation seam — so all of them log identically
and a new scope cannot forget the handler.

Alternatives: *wrap every collector body in `runCatching`* — rejected (the same per-site bookkeeping
problem as D1, and it also swallows nothing it should not: a handler ends only the failed child of a
`SupervisorJob` scope, which is the desired granularity); *a handler per class, written out* —
rejected as duplication that drifts (the reason `CarScreenObservations` has one).

### D4. Bookkeeping follows the mutation that succeeded

`NavigationSession` currently sets `navScreenPushed = true` **before** the push returns. A rejected
push therefore leaves the flag claiming a screen that is not there — `showNavigationScreen` early-
returns forever and the driver loses the navigation view. Chosen: the seam reports the outcome and the
session updates `navScreenPushed` / `navigationScreen` only on success, so a rejected push is retried
by the next state emission.

Alternatives: *derive the flags from `screenManager.getTop()` / `getScreenStack()`* — attractive (the
host becomes the source of truth) but it changes the session's model to polling host state on every
decision and couples the tests to library internals; kept as a test oracle instead (assert the stack
the session built). *Leave the optimistic flag and retry only on the next `isNavigating` edge* —
rejected: the edge may never come, which is the wedge being fixed.

### D5. The error notice is removed by identity, not by `popToRoot`

`showError` pushes an overlay and 4 s later calls `popToRoot()`, which pops the navigation screen too
while `navScreenPushed` stays `true`. Chosen: keep the pushed overlay instance, and on dismissal call
`ScreenManager.remove(overlay)` (a documented no-op when the screen is no longer in the stack) through
the seam, after re-checking that the session is still started and not destroyed. The local error state
is cleared regardless, so a skipped pop can never pin the error.

Alternatives: *`pop()`* — rejected, it pops whatever is on top now, and the driver may have pushed
something in the meantime; *`popToRoot()` after re-pushing the navigation screen* — rejected as a
two-mutation race for no benefit; *delete the auto-dismiss and leave it to the host's back gesture* —
recorded as an open question, not taken here because it changes the driver-visible timing.

### D6. "Show on map" is deferred out of the callback

`DetailsScreen`'s "Show on map" action does `popToRoot()` + `push(MapScreen(...))` inside the click
listener, constructing a whole screen — entry-point resolution, renderer gate, provider first-touch —
on the host's answering path. Chosen: the action records the intent and performs both mutations on the
main thread outside the callback (the screen's own scope), through the seam, so the callback returns
immediately and a construction failure degrades to a logged no-op instead of a process death.

Alternatives: *keep the mutation in the callback and only guard it* — rejected, it still violates
"host callbacks answer promptly" for a screen construction; *route it through the session (a
"show location" callback) so the session owns the stack* — architecturally cleaner but a larger
behavioural change (the session would have to know about a details-screen action), recorded as a
follow-up.

### D7. Threading and lifecycle model (per `guidelines/Design.md` §4)

- The seam does **no** work of its own: it runs on the caller's thread (always main, per the library's
  contract), calls the mutation, and logs a rejection through `DiagnosticsLog.log` (buffered, no
  filesystem work on the caller).
- No new threads, dispatchers or scopes. The fault handler is added to the **existing** session scope
  and the **existing** per-screen scopes; both are cancelled where they already are
  (`NavigationSession.onDestroy` → `scope.cancel()`; a screen's `onDestroy`).
- The delegated "Show on map" work runs in the screen's existing scope (main), i.e. main-thread
  mutation after the callback returned — never on a background dispatcher, because `ScreenManager`
  requires the main thread.
- The deferred error dismissal keeps using the session's scope (`delay` is cancelled by
  `scope.cancel()` on destroy) plus an explicit session-state check, because a host can end a session
  without the session's `onDestroy` running.

## Risks / Trade-offs

- **A swallowed fault hides a real defect** → every rejection logs under the existing `HOST`/`TEMPLATE`
  diagnostics tags with the mutation name; the on-device recipe (`guidelines/Build.md` §10) gains a
  grep for rejected mutations, and unit tests assert both the no-op and the log.
- **`remove(overlay)` is a no-op when the overlay already left the stack** → benign by design (the
  local error state is cleared anyway); the alternative (`pop()`) can remove the wrong screen, which
  is strictly worse.
- **A rejected push is retried on the next state emission** → an emission storm could mean a push
  attempt per emission. Bounded by the state emission rate and by `SessionHostGate` (no mutation while
  stopped); the rejection is logged, so a host that keeps refusing is visible in diagnostics rather
  than silent.
- **Deferring the "Show on map" push changes its timing** → the push stays on the main thread with no
  additional hop beyond the coroutine dispatch; the on-device run judges whether the driver perceives
  it, and D6's follow-up (session-owned navigation) is the alternative if it does.
- **Two unarchived sibling changes also modify `car-host-fault-isolation`** (and the same requirement
  text) → archive ordering is recorded in `tasks.md`; the delta here carries the union of their
  requirement text so that whoever archives last keeps every scenario.
- **The delta's MODIFIED operations cannot be applied until the capability exists** → `openspec
  validate fix-car-host-mutation-guards` is clean today and reports the archive precondition as INFO;
  `tasks.md` gates the archive on the siblings.

## Migration Plan

No data, settings or protocol migration. Ship as a normal build (both flavors); the change is additive
and the only driver-visible difference is that an error notice no longer removes the navigation view.

Rollback: revert the commit — the guards are pure fault confinement except D5, whose revert restores
the current behaviour (error notice removes the navigation view).

Archive order (must hold before `openspec archive fix-car-host-mutation-guards`):
`fix-aaos-host-crash` → `fix-host-crash-residual-paths` → `fix-car-surface-ownership-and-host-callbacks`
so `car-host-fault-isolation` exists in `openspec/specs/` with the requirement text this delta modifies.

## Verification

- **Unit tests** (`:auto`, plus `:app` where a session seam is shared): the seam returns false and logs
  when the mutation throws; a throwing click-path push leaves the process's main thread clean (asserted
  by the fake throwing and the test completing); a throwing session observer and a throwing `onStart`
  sync do not fail their siblings; a rejected push leaves `navScreenPushed` false; an error while
  navigating leaves the navigation view on the stack and the overlay is removed by identity; the
  delayed dismissal performs no host call when the session is destroyed or no longer started; the
  free-driving restore and the navigation push each happen at most once per started period.
- **Revert checks** for the two behavioural cores (D4's success-conditional bookkeeping, D5's
  identity-scoped removal): disable each and confirm exactly the new cases fail.
- **On device** (`guidelines/Build.md` §10, automotive AVD and/or a projection head unit): browse →
  navigate → force the error notice (a route to an unreachable destination, or the existing error
  path) → confirm the navigation view returns when the notice clears, `Diag/HOST` shows no rejected
  mutation in a healthy run, and the host crash buffer and app crash log stay empty across a
  background/return round trip.
- **Build**: `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug` (and x86_64 for the AVD)
  with no new warnings; full `:app` per flavor, `:auto`, `:core` suites green.

## Open Questions

1. Should a confined fault also be surfaced to the driver (a notice on the next template build)? Left
   deferrable: the default is a silent logged no-op, matching the template-build rule, and the
   decision depends on how often rejections actually occur on a real head unit (the `HOST` log gives
   the number).
2. Is the 4-second auto-dismiss of the error notice still wanted once the notice no longer displaces
   the navigation view, or should the host's own back gesture be the only way out? Deferrable; both
   satisfy D5's requirements, and only the delay constant changes.
