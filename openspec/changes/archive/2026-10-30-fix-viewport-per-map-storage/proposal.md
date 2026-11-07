# Proposal — fix-viewport-per-map-storage

## Why

**Observed (#9, verified in the tree at base `28a174a3`).** `openspec/specs/viewport-persist/spec.md:11`
and `:45` say the viewport is persisted in **one** file, `filesDir/maps/viewport.json`, while
`ViewportStorage.fileFor` (`app/src/main/java/com/naviveylin/data/ViewportStorage.kt:23-25`) writes
`filesDir/maps/viewport-<key>.json`, one file per map key, and every load path keys by the map's file
name (`MapCanvasViewModel.initMap` sets `currentMapKey = mapPath.substringAfterLast('/')` at
`MapCanvasViewModelMapInit.kt:177`, then loads with it at `:136`). The spec's only key sentence
(`:111`, "nor persist a viewport under the new map key") presupposes a key, so its single-file
sentence cannot describe the shipped storage.

**Consequence.** A reader implementing the storage contract from the spec builds a different layout —
the same class of divergence as §118 (`fix-fav-auto-zoom-clamp-range`): a spec pinned behaviour the
code never applied, and no test compared the two artefacts. It also leaves the un-keyed save without a
destination: `saveViewport` and the navigation-end save fall back to the literal `"default"`
(`MapCanvasViewModelCamera.kt:356`, `MapCanvasViewModelRoute.kt:83-84`), and no load path reads
`viewport-default.json` (issue #54, the residual half of §9).

**Root cause.** The per-map storage landed (`continuous-pinch-zoom`, archived 2026-08-29) without the
spec sentence following it; the two save-site fallbacks stayed at the pre-per-map literal. Nothing read
the spec against the code, so neither the sentence nor the orphaned fallback was seen.

**Red on HEAD.** `com.naviveylin.data.ViewportFileContractTest` fails: the spec states
`filesDir/maps/viewport.json`, the storage writes `viewport-germany.json`
(`tests="2" failures="2" errors="0"`), and
`com.naviveylin.ui.map.MapCanvasViewModelViewportPreInitSaveTest` fails because a save with no map key
writes `viewport-default.json` (`tests="1" failures="1" errors="0"`). Repro:

```
./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.data.ViewportFileContractTest" \
  --tests "com.naviveylin.ui.map.MapCanvasViewModelViewportPreInitSaveTest"
```

## What Changes

- **The two storage requirements describe the shipped per-map layout** (spec `viewport-persist`,
  MODIFIED "Save viewport state" and "Load viewport state"): the file pattern
  `filesDir/maps/viewport-<key>.json`, the key (the map database path's last segment), the escaping of
  characters outside `A-Za-z0-9._-` to `_`, and the skip when no key is known. Every existing scenario
  is carried; three scenarios are added for the key, the escaping and the skip.
- **One key derivation, one accessor.** `ViewportStorage.keyFor(mapPath)` owns the last-segment rule
  that `initMap` used to inline, `ViewportStorage.fileNameFor(key)` owns the file name, and
  `MapCanvasViewModel.viewportStorageKey()` is the one accessor the save sites consult. The
  navigation-end save and `saveViewport` write only when a key is known, so the `"default"` literal and
  its unreadable file disappear (decision (a) of #54; see `design.md`).
- **The view-change listener persists under the initialization's own captured key**, not the mutable
  `currentMapKey`, so a re-entry cannot retarget a renderer that is still shutting down.
- **Conformance and behaviour cases.** The contract test reads `openspec/specs/viewport-persist/spec.md`
  and compares its stated pattern, key derivation and escaping with what `ViewportStorage` produces;
  the pre-init case pins that a save without a key writes no file. `MapCanvasViewModelNavEndRestoreTest`
  names the map key it simulates instead of relying on the orphaned `"default"` bucket.

## Impact

Files:

- `openspec/specs/viewport-persist/spec.md` (shipped spec, via this change's delta)
- `app/src/main/java/com/naviveylin/data/ViewportStorage.kt`
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (one accessor)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelMapInit.kt` (derivation, load key, listener)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelCamera.kt` (`saveViewport`)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelRoute.kt` (navigation-end save)
- `app/src/test/java/com/naviveylin/data/ViewportFileContractTest.kt` (new)
- `app/src/test/java/com/naviveylin/data/ViewportStorageTest.kt` (key/file-name cases)
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelViewportPreInitSaveTest.kt` (new)
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelNavEndRestoreTest.kt` (one assertion realigned)

No guideline changes: `guidelines/Design.md` §9 owns "Data, persistence & search" but names no viewport
storage file, and no guideline restates the file layout, so none contradicts the corrected spec.

**Shape.** Additive to the spec's vocabulary (the per-map key), breaking for nothing: no persisted
file is renamed, and the only removed behaviour is a save that wrote a file no load path read.
Rollback is the revert of this change; files already on disk (`viewport-<key>.json`) are untouched.

## Out of scope

- Migrating or deleting a pre-existing `viewport-default.json` on a user's device: no load path reads
  it, so it is inert; a cleanup would need a device-evidence recipe of its own.
- Any other consumer of `currentMapKey` (search/diagnostics display).
