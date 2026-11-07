# Design — fix-viewport-per-map-storage

## Root cause

One artefact states a single `viewport.json`; the other writes `viewport-<key>.json`. The per-map
storage landed with `continuous-pinch-zoom` (archived 2026-08-29), the spec's "Save viewport state" and
"Load viewport state" requirements did not follow, and no case compared the two. The un-keyed save
fallback (`"default"`) is the same divergence seen from the code side: no load path reads the file it
writes.

## Decision 1 — which storage layout is canonical

**Alternatives the evidence eliminates**

- *Revert the code to one `viewport.json`*: impossible. Per-map files are required by the shipped
  re-entry requirement (`viewport-persist` "Re-entry supersedes an in-flight map init", scenario
  "Lifecycle save during the re-entry window") and by the existing restore cases
  (`MapCanvasViewModelViewportRestoreTest` seeds `viewport-testmap.json`, `viewport-mapA.json`,
  `viewport-mapB.json`); one file cannot hold two maps' viewports and would reopen map B at map A's
  center.
- *Let the spec keep the single-file sentence and treat the code as the defect*: same reason — the
  tested, shipped behaviour is per map.
- *Write both files (single plus per-map)*: adds a second writer with no reader and doubles the
  invalidation surface; nothing asks for a single file.

**Chosen.** The per-map layout is canonical, and the spec states it: pattern
`filesDir/maps/viewport-<key>.json`, key = the map database path's last segment, characters outside
`A-Za-z0-9._-` escaped to `_`. This is the layout the code has shipped and the existing tests pin.

## Decision 2 — what a save does while no map key is known

**Alternatives**

- **(a) Skip the save.** Chosen. It matches the guard that already exists for the restore window
  (`saveViewport` returns while `viewportRestored` is false) and removes the `"default"` literal, so no
  file is written that no load path reads.
- **(b) Keep a named bucket and consult it in `initMap`.** Rejected: there is no map to load it for.
  `initMap` sets the key synchronously before the load, so the bucket is reachable only from a caller
  that saves before any map exists — the navigation-end simulation in `MapCanvasViewModelNavEndRestoreTest`
  — and making `initMap` prefer an un-keyed bucket would let a stale navigation-end viewport override
  the map's own restored viewport on the next start.
- **(c) Unify the literals without a behaviour change.** Rejected: it leaves `viewport-default.json`
  unreadable under either literal and leaves the spec unable to state the pre-init behaviour truthfully.

**Chosen (a).** A save with no key is skipped and logged (`saveViewport: skipped, no map key yet`),
the same shape as the existing `viewportRestored` skip. `MapCanvasViewModelNavEndRestoreTest` names the
map key it simulates (`currentMapKey = "testmap"`) instead of relying on the orphaned bucket.

## Decision 3 — one derivation, one accessor

`ViewportStorage.keyFor(mapPath)` owns the last-segment rule (`substringAfterLast('/')`) and
`fileNameFor(key)` owns `viewport-<escaped key>.json`; `initMap` derives both its display key and its
storage key through `keyFor`, and `MapCanvasViewModel.viewportStorageKey()` is the one accessor the
save sites read. The view-change listener of one initialization uses the key it captured before its
first suspension, not the mutable `currentMapKey`, so a re-entry cannot retarget a renderer that is
still shutting down.

## Threading and lifecycle

No dispatch change: `ViewportStorage.save`/`load` keep `Dispatchers.IO` through `ioDispatcher`, and the
accessor only reads a `String?` on the caller's thread. No new lifecycle owner, no new native call.

## Verification

- `ViewportFileContractTest` reads `openspec/specs/viewport-persist/spec.md` from the module directory
  and compares the stated file pattern, key derivation and escaping with what `ViewportStorage`
  produces (the `FavAutoZoomClampRangeTest` / `MapMenuBackOrderComposeTest` precedent).
- `ViewportStorageTest` pins the file name for a key and the escaping of an unsafe key.
- `MapCanvasViewModelViewportPreInitSaveTest` pins that a save with no key writes nothing.
- `MapCanvasViewModelNavEndRestoreTest` keeps its navigation-end persistence claim, now under a key.
- No device measurement: the change alters no pixel and no on-device behaviour reachable without a
  pre-init save; the gate is the app unit suite over both flavors (`guidelines/Build.md` §2a scope).
