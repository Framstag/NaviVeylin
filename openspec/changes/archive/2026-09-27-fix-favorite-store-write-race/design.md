# Design

## Context

See `proposal.md` — Why for the two defects. What shapes the approach:

- **The repository write path is one unsynchronised triple.**
  `FavoriteRepository` (`app/src/main/java/com/naviveylin/data/FavoriteRepository.kt`)
  implements every write as `native mutate → refreshState() → persist()` per
  operation (`addGroup`, `deleteGroup`, `renameGroup`, `addFavorite`,
  `deleteFavorite`, `renameFavorite`, `moveFavorite`, `setGroupColor`,
  `setFavoriteStarred`). All of them run under
  `withContext(defaultDispatcher)` where `defaultDispatcher` is
  `Dispatchers.Default` — a multi-threaded pool, so two operations genuinely
  overlap. Each is launched by its own `viewModelScope.launch` in
  `FavoritesViewModel` (10 launch sites), so nothing above the repository
  serialises them either. `moveFavorite` is the one exception, guarded higher up
  by `FavoritesViewModel.reorderInFlight` (`fav-management-ui` — "Reorder
  commits are serialised").
- **`persist()` is a whole-store replacement, not an append.**
  `client.saveFavoriteLocations(path, groups)` is implemented in the submodule by
  **destroying and re-creating** the native service and rebuilding it from the
  array it was handed: `delete data->favService; data->favService = new
  osmscout::FavoriteLocationService(pathCStr); service->ClearAll();` followed by
  `AddGroup()` / `AddFavorite()` per entry
  (`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:6739-6754`).
  `loadFavoriteLocations` replaces the instance the same way (`:6688-6689`).
  `persist()` re-reads `client.favoriteGroups` itself, so the array it passes is
  a *snapshot* of a shared, mutable store.
- **The service pointer is unguarded.** `ClientData`
  (`OSMScoutClient.cpp:423-459`) carries `routingMutex`,
  `routeDescriptionMutex`, `gpsMarkerMutex` and `adminRegionMutex`, and no mutex
  for `favService` — a raw owned pointer. Every favorite entry point
  (`:6830-7080`) dereferences it after a null check alone; there is no lock in
  that range. The service's own `shared_mutex` protects its data per call, which
  means a read that lands between `ClearAll()` and the rebuild sees a partially
  rebuilt store, and a `delete` that lands during a call is a use-after-free.
- **The guideline already asks for the native half.**
  `guidelines/Design.md` §5 (Native boundary) states: "Serialize writes in
  native shared services", "keep submodule patches minimal and upstreamable", and
  "Android-specific deviations live as local overrides in a bridge module, never
  patched into the submodule". A concurrency-safety mutex is not an
  Android-specific deviation and not a change to the JNI method contracts, so it
  belongs in the submodule; the repository-side serialisation is app code.
- **Constraint on the app half:** `addFavorite` calls `addGroup` internally when
  the target group does not exist yet, so any mutual exclusion added to the
  repository's write operations must survive that nesting.

## Goals / Non-Goals

**Goals:**

- Two overlapping writes both survive, in the exposed state and in the file.
- No favorite call ever runs against a destroyed or replaced service instance,
  and no store replacement is ever observed half-applied.
- Keep every existing per-write behavior: one native save per successful write,
  nothing persisted on failure, issue order preserved, no UI or wording change.

**Non-Goals:**

- Changing the `saveFavoriteLocations` / `loadFavoriteLocations` method
  signatures or their "replace the store from the data I was given" semantics —
  the native half makes the replacement *safe*, not incremental.
- Removing `FavoritesViewModel.reorderInFlight`: its "second reorder commit is
  dropped" behavior is specified by `fav-management-ui` and stays.
- A favourites file-format change, migration, or any new user-visible surface.
- Making other shared services (`routingMutex`, `gpsMarkerMutex`, …) consistent
  with the new one; they are already guarded and out of scope.

## Decisions

### D1 — Fix both halves (option C), not one

Chosen: the repository `Mutex` **and** the native `favMutex`.

- **Alternative A (app-only):** a repository `Mutex` alone removes the lost
  update for the app's writers, but leaves `delete data->favService` racing an
  in-flight call — the use-after-free, and the partially-rebuilt-store read,
  survive. Rejected: leaves a crash class in place.
- **Alternative B (native-only):** a `favMutex` alone makes the swap safe, but
  the lost update survives, because a single repository write performs *two*
  separate bridge calls (`GetGroups` inside `refreshState()`, then
  `saveFavoriteLocations` inside `persist()`), so another write can commit
  between them. Rejected: leaves silent data loss in place.
- Rationale for C: each half closes exactly one defect, and neither closes the
  other's. Both are small.

### D2 — App-side serialisation lives in `FavoriteRepository`, not in the ViewModel

Chosen: one `Mutex` in `FavoriteRepository` guarding its write operations.

- **Alternative (ViewModel-level):** serialise in `FavoritesViewModel`. Rejected
  because the repository is a `@Singleton` shared by several callers — the
  favourites sheet, the details sheet add/remove path, and the Android Auto
  favourites screen — so a ViewModel-local lock would still allow two surfaces to
  race. The invariant belongs to the store's owner, which matches
  `guidelines/Design.md` §4 ("One source of truth per data signal").
- **Alternative (single-slot channel / actor):** a serialising channel with one
  consumer coroutine. Rejected as more machinery than needed for user-paced
  writes, and it would make write results harder to return per caller.

### D3 — One critical section spanning mutate, refresh and persist

Chosen: acquire the `Mutex` once per write and hold it across the native
mutation, `refreshState()` and `persist()`, so the persisted array can never be
older than the last committed mutation.

- **Alternative (lock only the persist):** narrower and cheaper, but does not fix
  the defect — the stale snapshot is taken in `refreshState()`, before the
  persist. Rejected.
- **Alternative (`limitedParallelism(1)` on the write dispatcher):** avoids an
  explicit lock but is a whole-dispatcher property, invisible at the call site,
  and would also serialise unrelated repository work. Rejected as implicit.
- Note: acquiring the `Mutex` *outside* `withContext` and doing the whole triple
  inside it keeps the lock off the caller's thread and keeps the native call on
  the background dispatcher (`Design.md` §4: never native on the main thread).

### D4 — Internal group creation goes through a private, non-locking helper

Chosen: extract the body of `addGroup` into a private `addGroupLocked(...)` (and
the same for any other future nested call) that assumes the lock is held;
`addGroup` becomes the locking wrapper.

- **Alternative (reentrant lock):** `kotlinx.coroutines.sync.Mutex` is not
  reentrant; using a `ReentrantLock` or a reentrant wrapper would work but hides
  the nesting and would deadlock across suspension points. Rejected.
- **Alternative (restructure `addFavorite` to never call `addGroup`):**
  duplicates the create-group native call and the success/refresh/persist
  handling in a second place. Rejected as duplication of the write protocol.

### D5 — Native half: a JNI-free `FavoriteStore` holder in `libosmscout-client`

Chosen: a new class `osmscout::FavoriteStore`
(`libosmscout-client/include/osmscoutclient/FavoriteStore.h`,
`src/osmscoutclient/FavoriteStore.cpp`) owning the `FavoriteLocationService`
behind one mutex. Its CRUD methods delegate under the lock, and the two
wholesale replacements are single calls: `ReplaceByPath(filePath)` (load) and
`ReplaceAndSave(filePath, groups)` (rebuild from the caller's snapshot + persist).
The JNI bridge holds a `FavoriteStore` by value in `ClientData` and delegates
all 15 favorite entry points to it; `close()` calls `Shutdown()`.

- **Alternative (implemented first, then superseded — a `std::mutex favMutex` in
  `ClientData` with `std::scoped_lock` in every JNI entry point):** it worked,
  but has no test seam. The guard would live in the JNI translation unit, which
  the native test project neither builds nor can drive (entry points need a
  `JNIEnv`, and the JNI target is added only under
  `OSMSCOUT_BUILD_CLIENT_JAVA`, which `Tests/` does not set), and a test at the
  service level cannot see it either — so the fix could not be revert-checked.
  Rejected for that reason.
- **Alternative (`std::shared_ptr<FavoriteLocationService>` + atomic pointer
  swap):** avoids holding the lock across the file I/O in `Save()`, and is the
  more modern shape, but it changes the ownership model of `ClientData` and
  every reader, and is a larger upstream patch for a service with user-paced
  writes. Rejected for this change; recorded as a possible follow-up.
- Rationale for the holder: it puts the guard where the data lives — the client
  library — which is what the guideline line "Serialize writes in native shared
  services" asks for, keeps the JNI method contracts unchanged, and is natively
  testable, so the fix is revert-checkable (`Tests/src/FavoriteStoreTest.cpp`).

### D6 — Guard the existing swap; do not eliminate it

Chosen: `ReplaceByPath`/`ReplaceAndSave` keep the service's `delete` + `new`
swap and make it atomic under the holder's mutex. The window in the old save
path — where `data->favService` was deleted and only republished at the end, so
every concurrent call saw a dangling pointer — no longer exists: the swap happens
inside one locked call.

- **Alternative (add a `SetFilePath()` / `Reload()` to
  `FavoriteLocationService` so the instance is never destroyed):** cleaner
  lifetime, but it changes the service's own API and the JNI methods' semantics,
  for a benefit the lock already delivers. Rejected for this change — it is the
  better long-term shape and is left as a possible upstream follow-up.
- **Alternative (an atomic `ReplaceAll(groups)` inside the service):** would fix
  the partial-store half at the service level and makes that half testable, but
  leaves the pointer-lifetime half (the use-after-free) with no test seam.
  Rejected in favour of the holder, which covers both halves at one seam.

### D7 — Threading model and lifecycle

- **App half:** the `Mutex` serialises `suspend` writes that already run on
  `Dispatchers.Default` via `withContext(defaultDispatcher)`. The lock is taken
  before the `withContext` and released after the persist completes, so callers
  on any dispatcher (all current callers are `viewModelScope`, i.e. main) simply
  suspend; no main-thread native call is introduced. Cancellation: a cancelled
  caller releases the lock through normal structured-concurrency unwinding; no
  detached coroutine, channel or thread is added, so there is no new lifecycle
  to tear down, and the existing `@VisibleForTesting defaultDispatcher` hook
  remains the only test seam.
- **Native half:** the lock is a plain blocking `std::mutex` held only for the
  duration of one JNI favourite call. No thread is created, no callback changes
  thread, and the existing "native callbacks arrive on native threads; Kotlin
  marshals state to the main thread" rule is untouched.

### D8 — Verification

- **Kotlin unit test** (`app/src/test/java/com/naviveylin/data/FavoriteRepositoryTest.kt`,
  extended): drive two overlapping writes into the repository with the
  `@VisibleForTesting internal var defaultDispatcher` hook pointed at a
  test-controlled dispatcher that forces the interleaving (a barrier between one
  write's refresh and its persist), then assert that the exposed state **and**
  the file both contain the effect of both writes; plus a sequential case
  asserting one save per write, and a failure case asserting nothing is
  persisted. Class must run under `@RunWith(RobolectricTestRunner::class)` with
  the default sandbox if it touches `OSMScoutClient`'s class load (see
  `guidelines/Build.md` §6).
- **Native test** (`app/src/main/cpp/libosmscout/Tests/src/FavoriteStoreTest.cpp`,
  run with `ninja -C hostbuild Tests/FavoriteStoreTest` then
  `meson test -C hostbuild "Check FavoriteStore" --print-errorlogs`): concurrent
  reads against repeated replacements must never observe a partial store;
  mutations running during replacements must neither fault nor survive a later
  replacement half-applied; a shut-down store must report no store and stay
  usable; a replacement must persist exactly the supplied content, including
  shrinking it. Revert-checked by removing the mutual exclusion on purpose (two
  cases fail). Note: this host build is a meson build with no
  `CTestTestfile.cmake`, so `ctest` finds no tests there — use `meson test`
  (recorded in `TODO.md` §40.48).
- **Build gates:** `./gradlew test` for the affected module, plus a native build
  for all three ABIs (`:app:assembleMobileDebug` for arm64-v8a at minimum, and
  the full-ABI debug build before the gate) so the submodule change compiles
  everywhere it ships.
- **On-device smoke check (no new UI, but the change is in a user-facing write
  path):** on the phone, add a favourite, star it, rename it and delete it in
  quick succession, then force-stop and relaunch the app and confirm the
  favourites survive exactly as performed; watch `adb logcat -s NaviVeylin` for
  native faults. Repeat once on the car favourites screen (or the AAOS AVD where
  usable, see TODO §40.45 for the harness limits).

## Risks / Trade-offs

- **Holding the repository lock across the file write** → favourite writes are
  user-paced and the file is small; the only effect is that a second tap waits
  for the first. Acceptable, and strictly better than losing it.
- **Holding `favMutex` across `Save()`'s file I/O** → other favourite calls on
  other threads block for the duration of a small JSON write. Accepted; the
  pointer-swap alternative (D5) would avoid it and is left as a follow-up if
  contention ever shows up.
- **The native half does not fix the lost update** → two separate bridge calls
  remain in one repository write; this is why D1 chooses both halves. Recorded so
  no later reader expects the mutex alone to fix data loss.
- **`Mutex` vs `reorderInFlight` interaction** → the UI guard still drops a
  second reorder while one is in flight (specified behavior); the new lock sits
  below it and must not be removed together with it. The reorder test suite
  (`FavoritesSheetReorderComposeTest`, `FavoriteOrderTest`) must stay green
  unchanged.
- **Submodule hygiene** → the native change must be committed on
  `naviveylin-local` and the main repo gitlink bumped, or CI and fresh clones
  build without it; the submodule must be left clean (AGENTS.md — Native
  Integration).
- **A test that only asserts "no exception"** would pass on the pre-change code
  for the native half → each new test needs a revert-check (mutate the fix,
  confirm the test fails) as coverage evidence, consistent with the project's
  practice and the Kover/Robolectric attribution gap (TODO §15).

## Migration Plan

- **Order:** native half first (submodule commit on `naviveylin-local`, then
  gitlink bump), then the app half. The app half is independently safe if it
  lands first, and the native half is safe if it lands alone; only both together
  deliver both guarantees.
- **Deployment:** no data migration, no file-format change, no user action. An
  upgraded install keeps its favourites file untouched.
- **Rollback:** revert the app commit and the gitlink bump together. The
  submodule commit may stay on the branch unused; because neither half changes
  the on-disk format, a rollback needs no cleanup.

## Open Questions

- Should `saveFavoriteLocations` eventually become incremental (append/update a
  single favorite) instead of replacing the whole store from a caller-supplied
  snapshot? Deferrable: with both halves in place the current contract is safe,
  and the change would be a separate native proposal.
- Should the reorder path lose `reorderInFlight` now that the repository queues
  writes? Deferrable and spec-bound: `fav-management-ui` specifies that a second
  reorder commit is dropped, so removing it would be a spec change of its own.
