# Proposal

## Why

Favourite writes race. Every `FavoriteRepository` write is an unsynchronised
`mutate → refreshState() → persist()` triple, and `persist()` ends in
`saveFavoriteLocations`, which the JNI bridge implements by **deleting and
re-creating the whole native `FavoriteLocationService` and rebuilding it from
the Java snapshot it was handed** (`OSMScoutClient.cpp:6739-6754`). Two writes
that overlap — two quick star taps, or a rename while a delete is still
persisting — therefore cause two defects, not one:

1. **Silent data loss.** A snapshot taken before another writer's mutation erases
   that mutation from the live native store as well as from the file, so the
   `StateFlow`, the UI and the next read all lose the favourite.
2. **A native fault.** The pointer swap is unguarded — `ClientData` has mutexes
   for routing, route descriptions, the GPS marker and admin regions, and none
   for `favService` (a raw owned pointer). A `delete` can land while another
   thread is inside a call on that object, possibly while its `shared_mutex` is
   held: use-after-free / SIGSEGV.

The app makes both reachable from ordinary interaction: each UI action launches
its own `viewModelScope` coroutine onto `Dispatchers.Default`, and only reorder
commits carry a guard (`reorderInFlight`). `loadFavoriteLocations` performs the
same unguarded swap at startup.

## What Changes

- **`FavoriteRepository` (app):** one `Mutex` serialises each write operation as a
  single critical section covering the native mutation, the state refresh and the
  file persist, so a write can never be persisted from a snapshot that predates
  another write. `addFavorite`'s internal group auto-creation is routed through a
  private, non-locking helper so the non-reentrant `Mutex` cannot self-deadlock.
- **Native JNI bridge (submodule):** a `std::mutex favMutex` in `ClientData`,
  taken with `std::scoped_lock` by every favourite entry point, so the service
  pointer can neither be destroyed nor replaced while a favourite call is in
  flight; the `delete`/`new` swap in `loadFavoriteLocations` /
  `saveFavoriteLocations` moves inside that lock.
- **Tests:** a native test for concurrent swap-vs-mutate beside
  `Tests/src/FavoriteLocationServiceTest.cpp`, and a Kotlin test that forces the
  interleaving through the existing `@VisibleForTesting defaultDispatcher` hook
  and asserts both the exposed state and the file match the final state.
- **Not changed:** no UI, no public API, no file format, no user-visible wording.
  `FavoritesViewModel.reorderInFlight` stays — its "second commit is dropped"
  behaviour is specified (`fav-management-ui`) and this change sits below it.

**Decision to confirm (owner):** the two defects need different halves of the
bridge, and the options differ in blast radius:

- **(A) app-only** — Kotlin `Mutex` only. Removes the lost update for the app's
  own writers; leaves the native pointer swap unguarded (race 2 survives).
  Smallest change, no submodule movement.
- **(B) native-only** — `favMutex` only. Fixes the fault properly and is
  upstreamable, but the lost update survives: `GetGroups()` and the save are two
  separate lock acquisitions.
- **(C) both (recommended)** — A for snapshot→persist atomicity, B for swap
  safety. Only C closes both defects.

This proposal is written for **(C)**; design.md records the alternatives,
consequences and the risk of each.

## Capabilities

### New Capabilities

None — both halves extend capabilities the project already has.

### Modified Capabilities

- `fav-service`: adds a requirement that overlapping write operations are
  serialised, and that a successful write is never lost from either the exposed
  state or the persisted file. Changes the existing "Repository persists on every
  write" / "move method" behaviour from "persist after each write" to "persist
  after each write, with the mutate+refresh+persist triple atomic against other
  writers".
- `osmscout-jni`: adds a requirement that the favourite service handle is never
  destroyed or replaced while a favourite call is in flight, and that concurrent
  favourite calls neither fault nor discard each other's mutations. Changes the
  existing JNI-layer contract, which today says nothing about favourite-call
  concurrency.

## Impact

Affected modules, files and configuration:

- `app/src/main/java/com/naviveylin/data/FavoriteRepository.kt` — the `Mutex`
  and the private non-locking group-creation helper.
- `app/src/main/java/com/naviveylin/ui/favorites/FavoritesViewModel.kt` —
  unchanged behaviour; must keep `reorderInFlight` (spec `fav-management-ui`).
- Submodule `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`
  — `favMutex` in `ClientData` (`:423-459`) and the favourite entry points
  (`:6830-7080`), plus the swap sites (`:6688-6689`, `:6739-6740`, `:6829`).
- Submodule `app/src/main/cpp/libosmscout/Tests/src/` — new native test.
- Main repo gitlink for the `libosmscout` submodule — bumped after the
  submodule commit.
- `app/src/test/java/com/naviveylin/data/` — repository concurrency test.

Android components: no manifest, permission or resource change. The affected
surface is the shared `FavoriteRepository` used by the phone favourites sheet
(`FavoritesSheet`), the details sheet add/remove path and the Android Auto
favourites screen — the change is therefore **general for the app**, not
phone-specific and not Android-Auto-specific, and the AA favourites screen
inherits the guarantee without its own change.

Guidelines referenced: `guidelines/Design.md` (threading model and lifecycle,
§4 — the new critical section and the native lock are both threading decisions),
`guidelines/Build.md` (test constraints, §6 — the declared fork budgets and the
one-invocation-per-suite rule the new tests must respect; native verification),
`AGENTS.md` (bridge halves — C++ side is the submodule, Java side is
`:osmscout-client-java`; patch one, never both).

Change type: **additive**. No breaking API, behaviour or data change for users;
the only behavioural difference is that a previously-lost write now survives.
Rollback path: revert the app commit and the submodule gitlink bump together; the
submodule commit may remain on `naviveylin-local` unused, and no data migration
is needed because the file format is untouched.

Native/JNI form: a **submodule patch**, minimal and upstreamable — one mutex
member plus `scoped_lock` at the favourite entry points in a single JNI
translation unit. Not a local override in the `:osmscout-client-java` Gradle
module, which only overrides Java classes.

Previous specifications changed by this proposal: `fav-service` (its "Repository
persists on every write" and "Repository exposes a move method for favorites"
requirements) and `osmscout-jni` (its JNI-layer contract for the Java API).
