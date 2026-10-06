# Proposal

## Why

`computeAreaZoom` rounds its exact fit to **whole magnification levels** (`Math.round`,
`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:4247`), so an exact fit can land **up** on the
next whole level — 2^0.5 ≈ 1.41× more content than the helper solved for, i.e. ~13 % beyond the viewport its
80 % contract is measured against (a fit rounded *down* only wastes margin; it is the round-*up* direction that
clips, and the second cause below clips even at an exact fit). The route overview was protected against exactly
this when it landed (`2026-09-27-route-overview-fit`, design Decision 8: project the bbox corners, step one
level out while the route would not fit, `MapCanvasViewModel.kt:3645` + `routeFitsVisibleArea` `:3674-3699`),
but the other two fit callers were left unverified — and both park the camera on a point that is not the bbox
midpoint (the favorite coordinate, the POI), while the helper solves "fits in 80 % *when the bbox midpoint is
the camera center*". So an area favorite or a POI/radius fit can today clip the object or a result marker out
of the visible viewport — recorded as `TODO.md` §28.

## What Changes

- **Verify every fit caller's magnification instead of trusting the rounded one.** The route overview's
  "project the bbox corners, step one level out while clipped" check is extracted into one shared helper and
  used by all three fit sites, so whole-level rounding (and a rotated viewport, which needs a larger screen
  hull than the north-up bbox) can never clip the fitted content.
- The three phone fit call sites gain the verification:
  - area-favorite fit — `MapCanvasViewModel.onFavoriteSelected` (`MapCanvasViewModel.kt:2219`)
  - POI result fit on the main map — `MapCanvasViewModel.poiFitMagnification` (`:2546`)
  - POI/radius fit of the embedded search map — `SearchDialog` (`SearchDialog.kt:988` and `:996`)
- The route overview keeps its behaviour; it only stops owning a private copy of the check.
- `computeAreaZoom`'s signature and default rounding are **unchanged** (`minZoom`, `dpi` stay as
  `2026-09-27-route-overview-fit` left them), so pure-math callers and their existing expectations do not move.
- Nothing else changes: no new UI, no new gesture, no persistence, no native code, no car-screen behaviour.

## Capabilities

Scope is **phone only** (the car fits through its own `DetailsScreen.fitZoom` path, which is not touched here).

### New Capabilities

None. Every behaviour this change needs is already required; the change makes three callers meet the
requirement they already have.

### Modified Capabilities

- `fav-auto-zoom`: "Bounding box zoom calculation" — the 80 % contract is stated as a ceiling, but a
  whole-level round can breach it and clip the area object. The requirement is made explicit that the fitted
  bounding box SHALL stay inside the viewport, and the delta adds the scenario that pins it (an area favorite
  whose exact fit lands mid-level must still show the whole object, not an overflowed crop).
- `poi-search`: "Details via single click" — its "Zoom fits current location and POI with markers" scenario
  says both are "visible" but the same rounding can push one of them outside the viewport. The requirement is
  made explicit that both the current location and the selected POI (and the result set, for the embedded
  map) SHALL remain inside the visible area at the fitted magnification, with the delta scenarios for the
  main-map POI fit and for the embedded result map.

`route-map-overview` is **not** modified — its requirement already holds and its verification is preserved.

## Impact

- **Code** (all in `:app`, phone surface):
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — the shared fit-verification helper
    (extracted from `routeFitsVisibleArea`, `:3674-3699`) and its use at `:2219`, `:2546`, `:3645`
  - `app/src/main/java/com/naviveylin/ui/map/SearchDialog.kt` — the embedded result map's fit (`:988`, `:996`)
- **Tests**: `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelRouteFitTest.kt` (the route case stays
  green, proving the extraction is behaviour-preserving), plus cases for the area-favorite fit and the two POI
  fits that fail on the pre-change code. Existing favorites/POI/zoom tests are expected to stay green; a case
  that changes its expected magnification is a finding to report, not to re-baseline silently.
- **Modules / build**: `:app` only. No Gradle, manifest, DI, asset or resource change. No native change: no
  libosmscout submodule patch, no `:osmscout-client-java` override.
- **Guidelines**: `guidelines/MapRendering.md` (fit/DPI ground-resolution rules the helper follows) and
  `guidelines/Design.md` §12 (one source of truth — the reason the route's private check is extracted rather
  than copied a third time). No UI rule is affected.
- **Specs of record**: `fav-auto-zoom`, `poi-search` (both get delta files). `route-map-overview` is the
  precedent the delta cites.
- **Backlog**: closes `TODO.md` §28 when archived.

## Additive or breaking, rollback

Additive. The only user-visible effect is that a fitted bbox may sit up to one level further out than today,
so nothing that is visible now becomes invisible. Rollback: revert the call sites to passing `computeAreaZoom`'s
result straight through and drop the shared helper — the previous (unverified) behaviour returns unchanged.

## Decision (chosen 2026-10-02: option B)

**Chosen — B: share the route overview's projection check.** `routeFitsVisibleArea` is extracted into one
reusable helper — `fitsVisibleArea(bbox, midLat, midLon, mag, width, height, dpi, angle)` — and every fit site
starts from `computeAreaZoom`'s rounded result and steps one level out while the projected bbox leaves the
visible area. `computeAreaZoom`'s signature, default rounding and `minZoom`/`dpi` parameters are unchanged.

Why B over the alternatives:
- It makes the requirement true for the case the rounding rule cannot see at all: a **rotated** viewport needs a
  larger screen hull than the north-up bbox, so rounding alone would leave that clip live.
- It removes a duplicate rather than adding a third copy of the check — the route path already pays this loop.
- It leaves `computeAreaZoom`'s contract and its pinned rounding values untouched, so no pure-math caller or
  existing expectation has to move.

Rejected alternatives (kept for the record):
- **A — round *up* for fit callers** (`computeAreaZoom(..., rounding = Ceil)` or a `neverClip` flag): smallest
  change, but every fit lands up to one level further out than needed and the rotated case stays unverified.
- **C — both** (a `Ceil` starting point *plus* the projection loop): two mechanisms for one rule, so a later
  reader cannot tell which one is load-bearing.

Consequences B accepts: each fit site pays a loop of up to a few 4-corner projections (negligible, and bounded
by `MIN_MAG`/`MIN_AREA_ZOOM` as the step-out floor), and the fitted magnification may land one level further out
than today — a strictly wider view, never a clipped one.
