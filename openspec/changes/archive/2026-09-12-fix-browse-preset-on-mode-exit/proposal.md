## Why

After navigation ends (or free drive is exited) on the phone, the app lands back in BROWSE but the map representation keeps routing parameters: the map stays rotated at the last heading-up angle instead of going north-up, and the pre-navigation follow/suspension state is restored without the rest of the BROWSE preset. Users perceive "search/browse settings not applied, routing settings still active". The explicit drive-toggle exit (`exitFreeDrive`) already applies the BROWSE representation, but the navigation-end restore path (`setNavigationViewModel` nav-state collector) only flips `followMode`/`driveSuspended` and never applies orientation — a spec violation of `map-modes` "Navigation end restores prior mode".

## What Changes

- Navigation-end restore now applies the representation preset of the restored mode:
  - Restore to **BROWSE**: north-up (`freeFormNorthUp = true`, `viewport.angle = 0.0`), follow off, drive suspension cleared, browse-drift flag cleared — while **keeping the center and zoom** exactly where routing ended (per user decision: restore settings, not viewport/zoom).
  - Restore to **FREE_DRIVE**: standard drive representation (follow on), suspension cleared — center/zoom kept.
- The restore path re-renders after applying orientation so the north-up angle takes effect immediately.
- No viewport/zoom restoration, no change to viewport persistence (`onViewChanged`/`saveViewport` keep writing; map intentionally stays at the routing end position).
- Spec `map-modes` contract tightened: navigation end applies the prior mode's representation preset while retaining position and zoom.

Additive behavior change; no breaking changes. Rollback: revert the restore branch in `MapCanvasViewModel.setNavigationViewModel`; no persisted format changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `map-modes`: "Navigation end restores prior mode" — scenario is extended so that navigation end applies the BROWSE representation preset (north-up) / FREE_DRIVE representation (follow on) while keeping the viewport position and zoom at the routing end state. Currently created in the in-flight `browse-drive-modes` change; delta targets the same capability path `map-modes`.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — nav-state restore collector in `setNavigationViewModel` (~L2086-2102): apply BROWSE/FREE_DRIVE representation on navigation end, including angle reset + render.
- `app/src/test/java/com/naviveylin/ui/map/` — new unit tests for the restore behavior (BROWSE and FREE_DRIVE destinations, center/zoom retention).
- Phone scope only. The Android Auto path uses its own `AANavigationController`/MapController and is unaffected (car UI has no browse/search mode parity requirement here).
- Guidelines: no `guidelines/` doc changes needed — behavior now matches `guidelines/UI.md` browse/drive semantics.
- Previous related specs: `map-modes` (changed), `smooth-zoom` (auto-zoom, unchanged — auto-zoom flag intentionally untouched).
