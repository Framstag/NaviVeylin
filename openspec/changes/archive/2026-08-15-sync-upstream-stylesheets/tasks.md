# Tasks — sync-upstream-stylesheets

## 1. Build-Time Sourcing from Submodule

- [x] 1.1 In `app/build.gradle.kts`, add `syncSubmoduleStylesheets` Sync task copying `src/main/cpp/libosmscout/stylesheets` into `build/generated/assets/stylesheets`; add the generated root as an assets source of the main source set (`assets.srcDir("build/generated/assets")`); wire the task into `preBuild` and all `merge*Assets` tasks (spec: map-render — "Stylesheets sourced from submodule at build time"; design D1-A)
- [x] 1.2 Delete the committed snapshot `app/src/main/assets/stylesheets/` (all files) so no stale copy can shadow the submodule (spec: map-render — "No committed snapshot involved"; design D1-A)
- [x] 1.3 Add `checkSubmoduleStylesheets` task wired into `preBuild`: fail with `git submodule update --init --recursive` instruction when `app/src/main/cpp/libosmscout/stylesheets` is missing (spec: map-render — "Missing submodule fails the build")
- [x] 1.4 Verify merged assets: `./gradlew :app:mergeDebugAssets` then inspect merged assets and the packaged APK — `assets/stylesheets/` must match submodule `stylesheets/` exactly (38 files, no `symbols.oss`) (spec: map-render — "Submodule bump changes APK content")

## 2. Refresh-Aware AssetCopier

- [x] 2.1 Replace the `destDir.exists()` early-return in `AssetCopier.ensureStylesheets()` with per-file refresh: list bundled assets; for each, compare size + SHA-256 against the internal-storage copy; copy when different or missing; delete internal-storage files no longer bundled; keep returning the stylesheet dir path. Traversal must handle both listing styles (real Android: direct children; Robolectric: flattened nested paths like `include/roads.oss`) (spec: map-render — "Stylesheet refresh on device"; design D2-A)
- [x] 2.2 Keep failure handling: copy errors logged, app continues with existing internal-storage stylesheets, no crash (spec: basemap-loading — "Refresh failure degrades gracefully")
- [x] 2.3 Grep other `ensureStylesheets` callers (`MapCanvasViewModel`, `MapDownloadModule`) and confirm the refresh ordering contract holds: refresh completes before `withStyleSheetDirectory` consumers read the dir (spec: basemap-loading — "Stylesheet directory fully populated before load")

## 3. Tests

- [x] 3.1 Add `AssetCopierTest` (Robolectric, JNI-free, no `@Config`): first launch copies full set; update with changed content refreshes only changed files; no-change start rewrites nothing and returns existing path; removed bundle file deletes internal-storage copy; `symbols.oss` absent from the bundled set (spec: map-render scenarios "Existing install receives upstream changes" / "No-change startup is a no-op" / "Stylesheet file removed upstream")
- [x] 3.2 Run `./gradlew test` — full suite green (spec: map-render; apply rule: all tests pass)

## 4. Build & Device Verification

- [x] 4.1 Build debug APK: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`; confirmed `assets/stylesheets/` inside the APK matches submodule (38 files, unzip -l, no `symbols.oss`) (spec: map-render — "Submodule bump changes APK content"; apply rule: build compiles, run full 3-ABI build once at the end)
- [ ] 4.2 Device — fresh install: map renders with current upstream styles (restructured `standard.oss`, no `symbols.oss` reference errors); day/night flag switch reflects stylesheet changes (spec: map-render — "Existing install receives upstream changes")
- [ ] 4.3 Device — update path: install a build from the old snapshot, let it copy stylesheets, then install the new build; verify styles change after restart **without** clearing app data or reinstalling (spec: map-render — "Existing install receives upstream changes"; design D2-A)
- [ ] 4.4 Device — bump submodule to a newer commit, rebuild, reinstall: verify the new styles appear (spec: map-render — "Submodule bump changes APK content")

## 5. Finalize

- [x] 5.1 Add stylesheet sourcing note to `AGENTS.md` (Native Integration section): submodule `stylesheets/` is the single source of truth, copied at build time by `syncSubmoduleStylesheets`; no snapshot to sync; submodule must be initialized before building; `AssetCopier` refreshes device copies on app start (spec: map-render — "Stylesheets sourced from submodule at build time")
- [x] 5.2 Verify no docs reference the old snapshot path or one-time-copy behavior — only the openspec change docs mention `assets/stylesheets`; no stale references in `docs/`, `README`, `AGENTS.md` (spec: map-render)
