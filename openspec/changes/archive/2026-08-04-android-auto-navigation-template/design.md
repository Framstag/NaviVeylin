## Context

Current state:
- `:auto` module exists with `CarAppService` stub throwing `UnsupportedOperationException`
- `NavigationViewModel` in `:app` emits full `NavigationState` via `StateFlow` (next turn, ETA, speed, lane, reroute)
- `NavigationState` has all fields `NavigationTemplate` needs
- No shared module exists — `NavigationState` is defined inside `:app`
- `androidx.car.app:app:1.7.0` already declared in `:auto/build.gradle.kts`

See proposal.md for motivation and scope.

## Goals / Non-Goals

**Goals:**
- Extract `NavigationState` and shared interfaces into a new `:core` module
- Implement `NavigationSession` returning `NavigationTemplate` during active navigation
- Wire all `NavigationState` fields into `NavigationTemplate` (next turn, ETA, distance, speed, lane, reroute, trip progress)
- Wire stop-navigation action back to `NavigationViewModel`
- Use `@HiltEntryPoint` for injection into Auto `Session`/`Screen`
- Lifecycle-safe observation (observe only when screen visible, clean up on destroy)

**Non-Goals:**
- Search, favorites, or map rendering on car (Phases 2-4)
- Voice guidance
- Any changes to `:app`'s Compose UI or navigation flow

## Decisions

### Decision 1: Extract `:core` module for shared state

**Chosen:** Extract `NavigationState`, `NavigationViewModel` interface, and Hilt entry points into a new `:core` module. Both `:app` and `:auto` depend on `:core`.

**Alternatives considered:**
1. `:auto` depends directly on `:app` — simpler but couples Auto to the entire app module (layouts, composables, resources). Violates separation of concerns.
2. In-process event bus — no compile-time safety, hard to reason about.
3. Bound service IPC — overengineered for same-process communication.

**Rationale:** `:core` is a thin module containing only the shared data types and interfaces. `:app` already has a clean ViewModel layer; extracting the state types is minimal refactoring. `:auto` gets only what it needs.

### Decision 2: `@HiltEntryPoint` for injection into Auto Session

**Chosen:** Define a `@HiltEntryPoint` interface in `:core` that exposes `NavigationViewModel`. `Session` accesses it via `CarContext` → `Application` → `EntryPointAccessors.fromApplication()`.

**Alternatives considered:**
1. `@AndroidEntryPoint` on `Session` — not supported; `Session` is created by Car App API, not by Hilt.
2. Manual service locator — works but bypasses Hilt's lifecycle management.
3. `CarViewModel` / `AndroidViewModel` — `Session` has `CarContext` but no `ViewModelStoreOwner` for standard ViewModels.

**Rationale:** `@HiltEntryPoint` is the documented pattern for non-`@AndroidEntryPoint` classes. `CarContext.getApplication()` returns the Hilt-enabled `Application`. No refactoring needed in `:app`.

### Decision 3: Observe `NavigationState` via `StateFlow` in Screen's lifecycle

**Chosen:** `NavigationScreen` collects `NavigationViewModel.state` using `coroutineScope` tied to screen lifecycle. Collection starts in `onCreate`, stops in `onDestroy`. Updates trigger `invalidate()` to re-render the `NavigationTemplate`.

**Alternatives considered:**
1. Polling — wasteful, no real-time updates.
2. Callback from `:app` — adds coupling, harder to reason about.
3. Shared `StateFlow` in `:core` — cleanest, already the pattern used in `:app`.

**Rationale:** `StateFlow` is already the mechanism. `NavigationScreen` just needs to observe it. `invalidate()` is the standard Car App API pattern for template updates.

### Decision 4: Store total route distance in `NavigationState`

**Chosen:** Add `totalDistance: Double` field to `NavigationState`. Set when `startNavigation()` is called, derived from `RouteEntry` or calculated from route geometry.

**Alternatives considered:**
1. Calculate from remaining distance at start — fragile, race condition on first update.
2. Query `RouteEntry` on demand — `NavigationViewModel` already has the `RouteEntry` at `startNavigation()` time.

**Rationale:** Simplest approach. `RouteEntry` has the route geometry; total distance can be computed by summing segment distances or read from the native route object.

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        :core module                          │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  NavigationState (data class)                           │  │
│  │  NavigationViewModel interface (observe + stop)         │  │
│  │  @HiltEntryPoint AutoEntryPoint { navVm() }            │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
         ▲                                    ▲
         │ depends                           │ depends
┌────────┴──────────┐              ┌─────────┴──────────┐
│     :app module    │              │    :auto module     │
│  NavigationViewModel│              │  NavigationSession  │
│  (implements iface)│              │  NavigationScreen   │
│  + totalDistance   │              │  (observes via EP)  │
└────────────────────┘              └────────────────────┘
```

**Data flow:**

```
NavigationViewModel.state (StateFlow)
        │
        ▼
NavigationScreen.onCreate()
  → coroutineScope.launch { navVm.state.collect { state ->
      template = NavigationTemplate.Builder()
        .setNavigationInfo(buildTrip(state))
        .setDestinationTravelEstimate(buildEstimate(state))
        .setNextTurn(buildTurn(state))
        .setLaneDirection(buildLanes(state))
        .setAction(stopAction)
        .build()
      invalidate()
    }}
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| `:core` module adds build complexity | Keep it minimal — one package, no resources, no Compose. ~5 files. |
| `NavigationViewModel` interface in `:core` may need frequent updates as Auto gains features | Design interface narrowly: only what Phase 1 needs. Extend later. |
| `NavigationTemplate` rendering differs across head unit manufacturers | Test on DHU (Desktop Head Unit) emulator. API 1.7+ behavior is standardized. |
| `invalidate()` called on every GPS tick (1Hz) may cause jank | Throttle: only invalidate when displayed fields actually change (compare with previous state). |
| Lane guidance `NavigationTemplate.Lane` API may not render on all head units | Graceful degradation — lanes are optional in the template. |

## Migration Plan

1. Create `:core` module with `NavigationState`, `NavigationViewModel` interface, `AutoEntryPoint`
2. Refactor `:app` to depend on `:core`, implement interface, add `totalDistance`
3. Update `:auto/build.gradle.kts` to depend on `:core`
4. Implement `NavigationSession` + `NavigationScreen`
5. Update `NaviVeylinCarAppService.onCreateSession()`
6. Test on DHU emulator
7. Verify no regressions in `:app` navigation

Rollback: revert `:auto` module changes. `:core` extraction is backward-compatible.

## Open Questions

- Does `NavigationTemplate.Lane` require Car App API 1.8+ for full lane guidance support? API 1.7 has basic lane support; verify on DHU.
- Should the `NavigationTemplate` show a "Search destination" action when not navigating (prep for Phase 2)? Defer to Phase 2.
