## Verification Report: android-project-setup

### Summary
| Dimension | Status |
|---|---|
| **Completeness** | 32/44 tasks (73%) — 12 incomplete |
| **Correctness** | 10/15 requirements covered (67%) |
| **Coherence** | 1 design deviation, otherwise consistent |

---

### CRITICAL Issues

#### 1. Incomplete tasks — Jetpack Architecture (Section 3)

| Task | Description |
|---|---|
| 3.2 | Room database — no `AppDatabase.kt`, no entities |
| 3.3 | DAO interfaces — none created |
| 3.4 | `AppViewModel.kt` — no base ViewModel |
| 3.5 | `NavGraph.kt` — no route definitions file |
| 3.6 | `WindowSizeClass` detection — not in `MainActivity` |
| 3.7 | Tablet layouts — no `sw600dp`/`sw840dp` composables |

**Recommendation:** These are genuine incomplete tasks. Either implement them or mark as deferred to a future change.

#### 2. Incomplete tasks — JNI Bridge (Section 5)

| Task | Description | Status |
|---|---|---|
| 5.3 | C++ JNI wrapper functions | **Moot** — upstream provides them |
| 5.4 | JNI bridge for map loading | **Moot** — upstream `OSMScoutClient.openDatabase()` |
| 5.5 | JNI bridge for coordinate queries | **Moot** — upstream `searchLocations()` |
| 5.6 | JNI bridge for routing | **Moot** — upstream `calculateRouteAsync()` |
| 5.7 | C++ exception → Java exception | **Moot** — upstream handles this |
| 5.9 | AAR build verification | **Moot** — `:osmscout-jni` module removed |

**Recommendation:** Mark tasks 5.3-5.7, 5.9 as **superseded** — upstream `libosmscout-client-java` provides the complete JNI bridge. Update `tasks.md` to reflect this.

#### 3. Spec requirement not met — Room persistence

**Requirement:** "The system SHALL use Room for local storage of map metadata, favorites, and search history."
**Status:** ❌ Not implemented. No `AppDatabase.kt`, no entities, no DAOs, no `DatabaseModule.kt`.

**Recommendation:** Implement Room database or defer to a future change with explicit spec update.

#### 4. Spec requirement not met — Adaptive layouts

**Requirement:** "The system SHALL support phone, foldable, and tablet form factors using Compose adaptive APIs and WindowSizeClass."
**Status:** ❌ Not implemented. No `WindowSizeClass` detection, no tablet layouts.

**Recommendation:** Implement adaptive layout support or defer with spec update.

---

### WARNING Issues

#### 1. Design decision D3 not followed

**Design:** "`osmscout-jni` as Android Library module producing an AAR."
**Reality:** `:osmscout-jni` module was removed. Upstream `libosmscout-client-java` Java classes are copied directly into `app/src/main/java/com/framstag/libosmscout/client/`.

**Rationale for deviation:** Upstream already provides the complete JNI bridge. Creating a separate AAR module added unnecessary indirection. The native `.so` is linked directly via CMake.

**Recommendation:** Update `design.md` to document this decision change. The current approach is valid but the design artifact is stale.

#### 2. Spec requirement partially met — ViewModel

**Requirement:** "The system SHALL use Jetpack ViewModel with Kotlin StateFlow for UI state management."
**Status:** ⚠️ Hilt is wired and `NativeModule.kt` provides `OSMScoutClient`, but no ViewModel exists yet. The DI infrastructure is ready but unused.

**Recommendation:** Create `MapViewModel` or `AppViewModel` when map rendering UI is built.

#### 3. Spec scenario not covered — Room database created

**Scenario:** "WHEN app first launches THEN Room creates the database schema with all defined entities."
**Status:** ❌ No Room database exists.

**Recommendation:** Implement Room or mark scenario as deferred.

#### 4. Spec scenario not covered — Tablet/Foldable layouts

**Scenarios:** Tablet layout on large screen, Foldable layout on unfolded screen.
**Status:** ❌ Not implemented.

**Recommendation:** Implement or defer with spec update.

---

### SUGGESTION Issues

#### 1. `MapDownloadManager.java` removed

`MapDownloadManager.java` was removed because it uses `java.net.http` (Java 11+ API not available on Android). The `getMapDownloadManager()` method in `OSMScoutClient.java` was stubbed with `UnsupportedOperationException`.

**Recommendation:** If map download is needed later, implement an Android-compatible HTTP client (e.g., OkHttp) replacement.

#### 2. `NativeModule.kt` returns client without map data

`NativeModule.kt` creates an `OSMScoutClient` via `OSMScoutClientBuilder().build()` but no map database is opened. The client will be initialized but `openDatabase()` hasn't been called yet.

**Recommendation:** This is expected for the scaffold phase. Document that `openDatabase()` must be called before rendering.

#### 3. No `data/` package directory

The spec mentions a `data/` package but it doesn't exist yet. Only `di/` and `ui/` packages exist.

**Recommendation:** Create `data/` package when Room entities and repositories are implemented.

---

### Final Assessment

**3 critical issues, 4 warnings, 3 suggestions.**

**Critical issues breakdown:**
- 6 tasks genuinely incomplete (Section 3 — Jetpack Architecture)
- 6 tasks superseded by upstream JNI bridge (Section 5)
- 2 spec requirements not met (Room, adaptive layouts)

**Ready for archive?** ⚠️ **Not yet.** The 6 incomplete Section 3 tasks and 2 unmet spec requirements need resolution. Options:

1. **Implement remaining tasks** — complete Room, ViewModel, NavGraph, adaptive layouts
2. **Defer explicitly** — update `tasks.md` to mark Section 3 tasks as deferred to a future change, update specs to note deferred requirements
3. **Split scope** — archive this change as "project scaffold + native integration" and create a new change for "Jetpack architecture + Room"

**Recommendation:** Option 3 — the completed work (scaffold, native build, JNI bridge) is solid and independently verifiable. The Jetpack architecture tasks are a natural next change.
