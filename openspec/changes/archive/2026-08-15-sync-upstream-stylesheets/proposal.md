# Sync Upstream Stylesheets (Build-Wired)

## Why

libosmscout upstream regularly updates its style sheets (`*.ost`, `*.oss`). NaviVeylin did not use upstream stylesheets at runtime: it bundled a static snapshot at `app/src/main/assets/stylesheets/`, copied it to internal storage on first launch (`AssetCopier`), and handed that directory to the native client via `withStyleSheetDirectory` in `MapDownloadModule`. Three defects hid upstream changes:

1. **No build-time link** — `:app` packaged the committed `assets/stylesheets/` snapshot; the submodule was ignored by the build. `./gradlew build` succeeding did NOT assure upstream styles were used. Submodule bumps without a manual snapshot refresh shipped stale styles.
2. **Stale, divergent snapshot** — the bundled copy was from ~29 Jul; upstream 12 Aug commit `cd273c581` ("Cleanup an additions to style sheets") restructured the stylesheets: deleted `include/symbols.oss` (274 lines), further split `standard.oss`, cleaned `winter-sports.oss`. 17 files diverged. The snapshot referenced `MODULE "include/symbols"` in `standard.oss`/`cycle.oss`/`winter-sports.oss`; current upstream removed those references.
3. **One-time copy on device** — `AssetCopier.ensureStylesheets()` skipped copying when the destination directory already existed. App updates preserve `filesDir`, so even a rebuilt APK left existing installs on the old internal-storage copy until data clearing or reinstall.

## What Changes

- **Source stylesheets from the submodule at build time**: wire the submodule stylesheet directory into the `:app` main source set as an assets root (`android.sourceSets["main"].assets.srcDir(...)` pointing at `src/main/cpp/libosmscout/stylesheets`). Every build packages the stylesheets exactly as they exist in the current submodule checkout — no committed snapshot, no drift, no sync step to forget.
- **Delete the committed snapshot** `app/src/main/assets/stylesheets/` — the submodule becomes the single source of truth. Nothing stale can linger.
- **Fail fast when the submodule stylesheets are missing** (fresh clone without submodule init): a `checkSubmoduleStylesheets` task wired into `preBuild` aborts the build with an actionable message (`git submodule update --init --recursive`).
- **Make `AssetCopier` refresh-aware**: on every app start, compare each bundled asset against the internal-storage copy (size + content hash) and copy changed files; delete files present in internal storage but no longer bundled. First-launch full copy behavior stays intact. This delivers submodule-sourced styles to existing installs on APK update.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `map-render`: Add requirement that the stylesheets packaged into the APK SHALL be sourced from the libosmscout submodule stylesheet directory as it exists at build time (current submodule state) — not from a committed snapshot — and that the internal-storage copy SHALL be refreshed on app start so existing installs receive style changes without clearing data. Add scenarios for build-time sourcing, missing-submodule failure, update-refresh, and no-change no-op.
- `basemap-loading`: Add requirement that the stylesheet directory passed to `withStyleSheetDirectory` SHALL always contain the full, current set of stylesheets (populated/refreshed by `AssetCopier` before the native client loads them).

## Impact

- `app/build.gradle.kts` — add `assets.srcDir("src/main/cpp/libosmscout/stylesheets")` to the main source set; add `checkSubmoduleStylesheets` task wired into `preBuild`
- `app/src/main/assets/stylesheets/` — **deleted** (snapshot replaced by submodule wiring)
- `app/src/main/cpp/libosmscout/stylesheets/` — now the single source of truth, read at build time
- `app/src/main/java/com/naviveylin/data/AssetCopier.kt` — replace existence check with per-file compare + refresh; delete stale dest files; keep returning stylesheet dir path
- `app/src/main/java/com/naviveylin/di/MapDownloadModule.kt` — no change expected (already reads `ensureStylesheets()` result)
- Tests: new `AssetCopier` unit tests (Robolectric, JNI-free)
- Docs: `AGENTS.md` — stylesheet sourcing note (submodule = source of truth; build packages it)
- No new dependencies; no JNI/native changes
