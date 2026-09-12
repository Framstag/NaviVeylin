## Why

The Android Auto favorites screen shows stale or empty data when opened before the session's background warmup has finished initializing the favorites repository. Favorites init is the last warmup step — after the native client build and `openMapDatabases` (which opens every installed map database, taking seconds) — and `FavoritesScreen` reads the favorites flow exactly once (`.first()`), so it never updates when the data arrives. The user must leave and re-enter the screen to see their favorites.

## What Changes

- `FavoritesScreen` (auto) reads the favorites flow reactively (`.collect`) instead of one-shot (`.first()`), matching `MapScreen`, `DetailsScreen`, and the phone app's `FavoritePickerDialog`/`FavoritesViewModel`. The screen updates in place when favorites finish loading — no re-entry needed.
- The AA session warmup (`NavigationSession.startWarmup`) initializes favorites **before** `openMapDatabases`. Favorites depend only on the native client (JNI + JSON file), not on map databases, so they become available as soon as the client is built — seconds earlier.
- No native/JNI changes. No changes to the phone app.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto-favorites`: the favorites list must update reactively when the favorites store finishes loading after the screen is already open (new scenario: favorites appear without re-entering the screen). The warmup reorder is an implementation detail of the session startup sequence and does not change the `auto-startup-hardening` contract (first screen still returned immediately, heavy init still off the main thread).

## Impact

- `auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt` — `.first()` → `.collect`; loading state until first emission.
- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — move the favorites init warmup step before `openMapDatabases`.
- Tests: extend `FavoritesScreen` tests (Robolectric, default sandbox per AGENTS.md classloader rule) to cover a late favorites emission; verify warmup ordering in the session test if one exists.
- No guideline updates required (`guidelines/UI.md` parity rule already requires phone/AA favorites parity — this change restores it).
- Rollback: revert the two files; additive behavior change, no data migration.
