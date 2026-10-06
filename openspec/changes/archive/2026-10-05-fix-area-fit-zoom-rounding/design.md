# Design

## Context

Motivation is in `proposal.md` (Why / Decision). The current state that shapes the approach:

- `computeAreaZoom` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:4204-4252`) solves the
  meters→magnification formula, **rounds to whole levels** (`Math.round`, `:4247`) and clamps
  `coerceIn(minZoom, MAX_MAG)`. Its default `minZoom` is `MIN_AREA_ZOOM = 14.0` (`:4177`), documented as the
  *area-favorite* floor.
- Four fit sites exist; exactly one is verified:
  - route overview — `:3641-3647`, `minZoom = MIN_MAG`, plus the projection check `routeFitsVisibleArea`
    (`:3674-3699`)
  - area favorite — `:2219` (in `onFavoriteSelected`, camera centered by `updateCenter(fav.lat, fav.lon)`)
  - POI click on the main map — `poiFitMagnification` (`:2530-2547`, called `:2489`), camera centered by
    `updateCenter(entry.lat, entry.lon)` (`:2488`)
  - embedded result map — `SearchDialog.poiFitMagnification` (`SearchDialog.kt:944-1000`, called `:809`)
- The three unverified sites center the camera on a point that is generally **not** the bbox midpoint, while
  `computeAreaZoom` solves "the bbox fits in 80 % *when the bbox midpoint is the camera center*". Clipping is
  therefore not only a rounding effect: an off-centre camera clips at an exact fit too.
- The POI fit inherits the area-favorite floor, so at mag 14 (≈440 m visible on a ~420 dpi phone) a POI beyond
  a few hundred metres cannot be shown together with the current location at all.
- Constraints: every fit runs on the main dispatcher (ViewModel paths) or as a pure computation inside a
  `remember` (embedded map); the embedded map is **north-locked** (`MiniMap.kt:107`) and owns its own renderer
  and scope; `guidelines/MapRendering.md:137-138` makes `viewport.center` ("the geo position at the screen
  center") the shared contract the fit helpers must use.

## Goals / Non-Goals

**Goals:**
- One shared, unit-testable seam that answers "does this bbox stay in the visible band at this magnification,
  around this camera center?" — used by every fit site.
- Make `fav-auto-zoom`'s and `poi-search`'s "the fitted content is visible" requirements true for whole-level
  rounding, a rotated viewport, and an off-centre camera.
- Let the POI fit reach below the area-favorites floor so "both positions visible" holds at any distance.
- Leave `computeAreaZoom`'s signature, rounding, clamp and DPI semantics untouched.

**Non-Goals:**
- Sheet-aware insets for the favorite/POI fits: they keep fitting against the full canvas. Only the route fit
  consumes the panel's covered height. The helper takes the visible band as a parameter, which is the seam a
  follow-up passes it through.
- The car surface's own fit (`DetailsScreen.fitZoom`) — untouched.
- `computeAreaZoom`'s "80 % margin" arithmetic: a fit is allowed to land further out; the margin stays a
  starting point, not a guarantee.
- The area-favorites floor itself (14 stays for area favorites, for the reason `:4176-4177` records).
- Making the embedded mini map's *later* pan/zoom keep results visible: only the magnification it is first
  shown with is pinned.

## Decisions

### 1. Verify by projection at every fit site, not by rounding differently (option B)

Chosen in `proposal.md` (Decision): extract the route path's corner projection + step-out loop and use it
everywhere. Alternatives considered there: **A** round up for fit callers (leaves the rotated case and the
off-centre camera unverified), **C** both (two load-bearing mechanisms for one rule).

Consequence accepted: the fitted magnification may land a level or two further out than today — a strictly
wider view, never a clipped one.

### 2. The helper is parameterized by the camera center and the visible band, and lives beside its callers

`routeFitsVisibleArea` assumes the camera center *is* the bbox midpoint, which is only true for the route fit.
The extracted helper takes the camera center explicitly:

```
internal fun fitsVisibleArea(
    bbox: DoubleArray,           // minLat, maxLat, minLon, maxLon
    cameraLat: Double, cameraLon: Double,   // the geo point that will sit at the canvas center
    mag: Double, width: Int, height: Int, dpi: Double,
    angleRad: Double = 0.0,      // 0 for the north-locked embedded map
    coveredPx: Int = 0           // the band the route panel covers; 0 for the other fits
): Boolean
```

It builds the projection with `ProjectionUtils.viewport(cameraLat, cameraLon, mag, width, height, dpi, angleRad)`
and requires every bbox corner to be inside `[0, width] × [coveredPx/2, coveredPx/2 + height − coveredPx]` —
the current band arithmetic (`:3687-3697`) with the camera center generalized.

Alternatives:
- *Keep it a private `MapCanvasViewModel` method and copy it into `SearchDialog`.* Rejected: a fourth copy of
  one rule, and the reason the check exists is that copies drift (`guidelines/Design.md` §12).
- *Put the predicate on `ProjectionUtils`.* Rejected: `ProjectionUtils` owns projection construction; a fit
  policy ("which corners must be inside which band") is a different concern, and `SearchDialog` would then
  depend on geo internals rather than on one named rule.
- *New top-level `internal` in `app/src/main/java/com/naviveylin/ui/map/FitVerification.kt`.* **Chosen:**
  both call sites are already in that package (`MapCanvasViewModel` and `SearchDialog`), so no new dependency
  edge is introduced, and the rule is one importable, directly testable function.

### 3. One verified-fit entry point, so no call site re-implements the loop

```
internal fun verifiedAreaFit(
    bbox: DoubleArray, cameraLat: Double, cameraLon: Double,
    width: Int, height: Int, dpi: Double,
    minZoom: Double, angleRad: Double = 0.0, coveredPx: Int = 0
): Double      // computeAreaZoom, then step one level out while !fitsVisibleArea(...) and mag > minZoom
```

Alternatives:
- *Export only `fitsVisibleArea` and let each site write the 4-line loop.* Rejected: four copies of the same
  loop, and a site can silently forget the `mag > minZoom` guard (an infinite/negative walk).
- *One `verifiedAreaFit` plus the exported predicate* — **chosen.** Call sites stay one expression each, the
  loop has one home, and tests can drive the predicate directly (including the rotated and off-centre cases
  that no ViewModel test can reach cheaply).

### 4. Step-out floors: `MIN_MAG` everywhere except the area-favorite fit (option B2)

| fit site | floor passed | why |
|---|---|---|
| route overview | `MIN_MAG` (4.0) | unchanged (`:3643`) |
| POI click (main map) | `MIN_MAG` (4.0) | **new** — the requirement must hold at any POI distance |
| embedded result map | `MIN_MAG` (4.0) | **new** — no area-favorite floor intent applies |
| area favorite | `MIN_AREA_ZOOM` (14.0) | unchanged: `:4176-4177` documents the floor as deliberate |

Alternatives: *B1 — keep 14 for the POI fit* (the `poi-search` requirement could then only be met within the
floor's reach; the user chose against recording that as accepted), and *uniform `MIN_MAG` including area
favorites* (would drop a documented, deliberate area-favorite floor and over-zoom-out small objects).

### 5. Threading and lifecycle: unchanged, and nothing new is stateful

- `MapCanvasViewModel` fits run in the existing `viewModelScope` main-dispatcher flow, exactly as today
  (`guidelines/Design.md` §4 — viewport mutations stay on the main dispatcher).
- The embedded map's fit stays a pure computation inside its `remember(…)` block (`SearchDialog.kt:808`)
  during composition.
- The loop is pure arithmetic: no suspension, no IO, no native or renderer call, no allocation per step
  (it projects four corners per level).
- No new coroutine, job, flow or field. The one-shot semantics stay as they are: the favorite/POI fits are
  single-shot already, the route fit keeps its settle-delay one-shot (`:3641` region).
- The embedded map's fit never touches the main map's renderer or viewport (spec: embedded map independence).

### 6. Verification

- **Unit, pure:** `FitVerificationTest` — a bbox that is clipped at the rounded magnification returns one level
  out; a bbox that already fits is returned unchanged; the floor is returned when even the floor does not fit;
  a rotated viewport (`angleRad ≠ 0`) that needs a larger hull; an **off-centre camera** (the real favorite/POI
  shape: bbox not centered on the camera) which is the case `computeAreaZoom` cannot serve.
- **Unit, ViewModel:** cases in `MapCanvasViewModelRouteFitTest.kt` — a POI click whose distance forces the fit
  below the area-favorites floor (mirroring the existing `longTrip_zoomsBelowAreaFloor_bothEndpointsFit`, `:221`)
  and an area-favorite selection whose bbox is off-centre from the favorite coordinate.
- **Regression:** the existing route-fit cases must stay green **without edits** — that is the evidence the
  extraction is behaviour-preserving. `favoriteSelectedWhileFollowing_disablesFollowAndFits` (`:342`) may
  legitimately change its expected magnification; if so it is reported, not silently re-baselined.
- **Revert-checks (one mutation each):** remove the step-out loop → the clipped case fails; pass
  `MIN_AREA_ZOOM` as the POI fit's floor → the distant-POI case fails.
- **Suite gate:** `:app:testMobileDebugUnitTest` and `:app:testAutomotiveDebugUnitTest` with executed counts
  and elapsed time read from `test-results/*.xml` (a 5-second "BUILD SUCCESSFUL" proves nothing —
  `guidelines/Build.md` §6, `TODO.md` §17); `--rerun-tasks` or a cleared `test-results/<flavor>` when a cached
  verdict is suspected.
- **On-device (device-gated, recorded not gating):** click a POI a few kilometres away with a fix and confirm
  both markers are on screen, then select an area favorite while the map is rotated; `guidelines/Build.md` §10
  for the logcat/`Diag/MAP` recipe. No device is attached as of 2026-10-02 (`adb devices` empty), so this is a
  recorded step for the next device session.

## Risks / Trade-offs

- [An off-centre camera may need more than one step out] → the loop steps until the band contains the bbox or
  the floor is reached; visibility wins over tightness, and the wider view is the intended outcome.
- [The step-out changes an existing fit expectation elsewhere in the suite] → report the case and its new
  value; do not re-baseline silently. The route cases staying green unchanged is the extraction's proof.
- [A large rotated bbox at low magnification walks all the way to the floor] → bounded by `MIN_MAG`/14; a
  floor-reached fit is the accepted result and is one of the pure test cases.
- [Lowering the POI floor is user-visible: a distant POI click zooms far out] → intended (B2) and precedented
  by `route-map-overview`'s "Long trip zooms out below the area-favorites floor"; the POI stays centered and
  the result list stays open beside the map.
- [The open sheet covers the lower canvas during a POI click or favorite selection, and those fits ignore it]
  → named Non-Goal; the helper's `coveredPx` parameter is the seam a follow-up passes it through.
- [The embedded map's own pan/zoom after the fit can still move results off-screen] → Non-Goal: only the
  magnification the map is first shown with is pinned.
- [Performance] → ≤ (MAX_MAG − floor) iterations of four corner projections, each a handful of float
  operations; the route path already pays exactly this. No per-step allocation.

## Migration Plan

Nothing to migrate: no persistence, wire format, DI key, screen argument or host API changes, and no native
code. Deployment is a normal phone-surface release.

Rollback: revert the four call sites to the raw `computeAreaZoom` result and delete `FitVerification.kt` — the
previous unverified behaviour returns unchanged.

## Open Questions

- Should the favorite and POI fits become sheet-aware (consume the open sheet's covered height the way the
  route fit consumes the panel's)? Deferred: it needs its own spec scenarios and a UI decision about how much
  of the map a raised sheet should leave visible; the helper's visible-band parameter is the seam for it.
- `fav-auto-zoom`'s "Magnitude clamped to valid range" scenario says 4–18 while the code clamps to 14–20
  (`MIN_AREA_ZOOM` … `MAX_MAG`). This is a pre-existing spec/code divergence, **not** this change's scope —
  recorded here so it is not lost, with no decision taken.
