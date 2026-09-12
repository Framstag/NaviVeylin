## Context

See proposal.md — Why. Current state: `FavoritesScreen` (auto) reads the favorites `StateFlow` once via `.first()`; the AA session warmup (`NavigationSession.startWarmup`) initializes favorites as its last step, after `openMapDatabases` (seconds with multiple installed maps). All other favorites consumers — `MapScreen`, `DetailsScreen`, phone `FavoritePickerDialog`/`FavoritesViewModel` — collect the flow reactively. The favorites JNI path is fast (small JSON parse); the latency is ordering + one-shot read, not the data path.

## Goals / Non-Goals

**Goals:**
- Favorites appear on the AA favorites screen without re-entering, even when the screen opens before the store is ready.
- Favorites become available as early as possible in the session warmup.

**Non-Goals:**
- Main-thread hygiene for `MapScreen.mapRenderer` lazy init (startup jank) — tracked in TODO.md §7, separate change.
- Reordering the phone app's `initMap` favorites step — the phone collects reactively and has no bug; parity reorder is optional future work.
- Exposing an `initialized` flag from `FavoriteRepository` — deferrable polish (see Risks).

## Decisions

### D1: FavoritesScreen collects the flow instead of reading once

`FavoritesScreen` replaces `favoritesProvider.favoriteLocations().first()` with `.collect { ... }`, mirroring `MapScreen`/`DetailsScreen`. The screen keeps its `loaded` flag: show "Loading" until the first emission, then render the list (or empty state) and re-render on every subsequent emission.

- **Why**: the flow is a `StateFlow` — `.collect` emits the current value immediately, then every update. The screen self-heals when the store finishes loading; no re-entry needed. This is the exact pattern already proven in `MapScreen.kt:577` and `DetailsScreen.kt:134`.
- **Alternatives considered**:
  - *Poll/retry `.first()`* — re-reads on a timer. Wasteful, racy, no reactive guarantee. Rejected.
  - *`initialized` flag on the repository* — screen shows "Loading" until init completes, then collects. Nicer UX (no "no favorites saved" flash) but adds API surface to `FavoriteRepository`/`AutoFavoritesProvider` and touches the phone path. Deferred (see Risks); D2 shrinks the flash window enough that it is acceptable.

**Threading**: collection runs on the screen's `Dispatchers.Main` scope; `StateFlow` emissions are delivered on the collector's context, so no extra marshalling needed (same as existing consumers).

**Lifecycle**: the screen's `scope` is currently never cancelled (no lifecycle observer). With `.first()` the coroutine completed quickly; with `.collect` it would run until cancelled — leaking one collector per screen instance across open/close cycles. Add a `DefaultLifecycleObserver` that cancels the scope in `onDestroy` (pattern already used by `MapScreen`).

### D2: Warmup initializes favorites before opening map databases

`NavigationSession.startWarmup` moves the favorites init step ahead of `openMapDatabases`:

```
entry point -> native client build -> favorites init -> nav controller -> openMapDatabases
```

- **Why**: favorites depend only on the native client (JNI `loadFavoriteLocations` + JSON file I/O), not on map databases. Verified: `FavoriteLocationService` constructor reads the JSON file; `favoriteGroups` returns in-memory groups. No DB dependency. Favorites become ready as soon as the client is built — seconds earlier.
- **Alternatives considered**:
  - *Parallelize warmup steps* — favorites init and `openMapDatabases` on separate coroutines. Slightly faster but adds concurrency complexity to a deliberately sequential, time-boxed warmup. Rejected for now.
  - *Keep order, rely on D1 alone* — the screen would self-heal, but favorites would still be unavailable for seconds. D1 fixes the symptom, D2 fixes the latency. Both are cheap; do both.

The step stays best-effort (try/catch + log, as today); moving it earlier does not change failure behavior.

## Risks / Trade-offs

- ["No favorites saved" flash if the screen opens before init completes] → D2 shrinks the window to the client-build duration; the collect self-heals once data lands. If the flash still bothers users, add the `initialized` flag (D1 alternative) as a follow-up.
- [Collector leak per screen instance (scope never cancelled)] → cancel the screen scope in `onDestroy` (D1 lifecycle note).
- [Warmup reorder changes failure timing] → favorites init failure is already caught and logged; no user-visible behavior change on failure.
- [Spec says `PlaceListTemplate`, code uses `ListTemplate`] → pre-existing spec/code mismatch, out of scope for this change; noted so it is not silently "fixed" here.

## Migration Plan

Additive behavior change. Deploy: normal build. Rollback: revert the two files (`FavoritesScreen.kt`, `NavigationSession.kt`). No data migration, no native changes.

## Open Questions

None — the deferrable `initialized`-flag polish is recorded in Risks, not blocking.
