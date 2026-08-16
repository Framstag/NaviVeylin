# Design — sync-upstream-stylesheets

## D1: Build-time stylesheet sourcing

### Alternative A: Sync task + generated assets root (chosen)

A `syncSubmoduleStylesheets` `Sync` task copies the submodule stylesheet directory into a generated assets root; the main source set adds that root:

```kotlin
// app/build.gradle.kts
tasks.register<Sync>("syncSubmoduleStylesheets") {
    from("src/main/cpp/libosmscout/stylesheets")
    into(layout.buildDirectory.dir("generated/assets/stylesheets"))
}
sourceSets {
    getByName("main") {
        assets.srcDir("build/generated/assets")
    }
}
// wired into preBuild + all merge*Assets tasks
```

`mergeAssets` copies the files into the APK on every build. The APK's `assets/stylesheets/` matches the submodule exactly (verified: 38 files, no stale `symbols.oss`). Delete `app/src/main/assets/stylesheets/` so no committed snapshot can shadow the submodule content. A `checkSubmoduleStylesheets` task (wired into `preBuild`) fails fast with an actionable message when the directory is missing.

Why a copy task and not a direct `assets.srcDir(".../stylesheets")`: pointing the source set at the stylesheet directory itself would merge its files at the APK assets root (`assets/map.ost`), breaking the `stylesheets/` prefix contract that `AssetCopier`/`MapDownloadModule` rely on. The copy preserves the prefix. (A plain `assets.srcDir` also cannot take a Gradle Provider in current AGP — `android.sourceset.disallowProvider=true` — so task ordering must be wired explicitly, which the merge-task `dependsOn` does.)

- **Pros**: build output is, by construction, the current submodule stylesheet state — no copy step, no snapshot to forget, no drift possible; git status stays clean (copy goes to `build/`); submodule is pinned by the parent commit, so builds are deterministic for a given parent commit; incremental (Sync task re-runs only when submodule files change)
- **Cons**: build depends on the submodule being checked out (mitigated by the fail-fast check); a dirty submodule working tree with local stylesheet edits would be packaged (accepted — "current submodule state" is exactly the requirement; today only JNI files are dirty, stylesheets untouched)
- **Risk**: low. Deterministic per parent commit; fail-fast covers the fresh-clone case.

### Alternative B: Manual snapshot sync (status quo, rejected)

Keep a committed copy in `app/src/main/assets/stylesheets/`; update it by hand after each submodule bump (checklist).

- **Pros**: build independent of submodule checkout
- **Cons**: human step → drift returns (the original defect); snapshot was already 17 files out of sync; no build-time guarantee. Rejected: does not satisfy "assured during build".

### Alternative C: Gradle sync/verify task pair

A `:app:syncStylesheets` task copies submodule → assets and a `verifyStylesheets` task fails the build when they differ.

- **Pros**: build-time guarantee via failure
- **Cons**: copy mutates the working tree (dirty `git status` mid-build, rebuild churn); verify fails on the *first* build after a bump, forcing an extra manual step before anything works; still conceptually a snapshot. Rejected: A achieves the same guarantee with less machinery.

### Decision

Source-set wiring (A). It is the smallest change that makes "current submodule state" structurally true.

## D2: AssetCopier refresh strategy

### Alternative A: Per-file compare, copy changed, delete stale (chosen)

On every `ensureStylesheets()` call: list bundled assets; for each file compare size then content hash (SHA-256) against the internal-storage copy; copy when different or missing; delete internal-storage files not bundled. Files are small (< 65 KB), so hashing is negligible on the app-start path.

- **Pros**: self-healing with zero manual version bumps; mirrors the bundle exactly; idempotent; no-op fast path
- **Cons**: slightly more code than alternatives; hash read touches every asset each start (trivial at these sizes)
- **Risk**: low. Ordering: refresh runs on the main-thread app-start path today; keep it synchronous and cheap. I/O failure already handled (catch → log → continue with existing copy).

### Alternative B: Version marker file

Bundle a version file; copy the whole directory only when the version differs.

- **Pros**: simpler compare; single copy burst on change
- **Cons**: manual version bump per upstream sync → same human-drift problem D1-A eliminates; whole-dir copy rewrites unchanged files. Rejected.

### Alternative C: Always overwrite every start

- **Pros**: simplest possible
- **Cons**: rewrites all files every launch; still needs stale-deletion logic. Rejected on cost grounds.

### Decision

Per-file compare (A). Hash compare is exact (catches same-size edits); mirror semantics keep the directory converged; no manual versioning.

## Threading / lifecycle notes

- `ensureStylesheets()` is already called from `MapCanvasViewModel.initMap` and `MapDownloadModule` (app-start path, main thread). Keep the function synchronous and cheap; hashing ~15 small files is well under a frame budget. If it ever shows up in startup traces, move the copy loop to `Dispatchers.IO` while preserving the call order (refresh completes before `withStyleSheetDirectory` consumers read the dir).
- Configuration changes / process death: refresh is idempotent, so recomposition-safe; no state beyond the directory path returned per call.
- Robolectric note: unit tests see the merged assets (including the submodule-sourced `stylesheets/` root), which also exercises the D1 wiring. Keep `AssetCopierTest` JNI-free (no `FakeOSMScoutClient`/`OSMScoutClient` touch) so the classloader rule does not apply.
- No new dependencies; no native/JNI changes.

## Files

- `app/build.gradle.kts` — `syncSubmoduleStylesheets` Sync task + generated assets root + `checkSubmoduleStylesheets` preBuild task (D1-A)
- `app/src/main/assets/stylesheets/` — **deleted** (D1-A)
- `app/src/main/cpp/libosmscout/stylesheets/` — single source of truth
- `app/src/main/java/com/naviveylin/data/AssetCopier.kt` — refresh-aware copy (D2-A); traversal tolerant of flattened `AssetManager.list()` output (Robolectric returns `include/roads.oss` as a top-level entry; real Android returns only direct children)
- `app/src/test/java/com/naviveylin/data/AssetCopierTest.kt` — new (Robolectric, JNI-free, no `@Config`)
- `AGENTS.md` — stylesheet sourcing note
