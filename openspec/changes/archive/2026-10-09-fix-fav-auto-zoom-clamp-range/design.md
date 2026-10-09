# Design

## Context

`fav-auto-zoom`'s requirement "Bounding box zoom calculation" owns the area-favorite fit. Its last scenario,
"Magnitude clamped to valid range", claimed a valid range of **4–18**. The fit it describes does not clamp
there: `onFavoriteSelected` (`MapCanvasViewModel.kt:2359`) passes `minZoom = MIN_AREA_ZOOM` (14.0, `:4615`) into
`verifiedAreaFit`, which starts from `computeAreaZoom` — whose default `minZoom` is that same 14.0 (`:4646`) and
which ends `mag.toDouble().coerceIn(minZoom, MAX_MAG)` (`:4689`) with `MAX_MAG = 20.0` (`:4584`). The enforced
range is the area-favorites floor to the maximum magnification, 14–20.

Nothing compared the sentence to the code, and no single artefact is wrong on its own: the fit cases
(`FitVerificationTest`, `AreaFitViewModelTest`) pin the *behaviour* through floor-passing parameters, the sibling
specs pin the *app-wide* range as 4–20, and the 4–18 sentence had no reader. It survived from the initial import,
where the code already read `coerceIn(MIN_AREA_ZOOM, MAX_MAG)` — so this is a documentation defect that is
detectable only by comparing two artifacts.

## Goals / Non-Goals

**Goals**

- The scenario states the range the area-favorite fit applies.
- A host case that fails on the pre-fix tree and passes after, and that keeps failing if either side moves again.
- The other four scenarios of the requirement, and its prose, unchanged — byte for byte.

**Non-Goals**

- No change to `computeAreaZoom`'s bounds, to `MIN_AREA_ZOOM`, to `MAX_MAG` or to any fit caller.
- No change to the app-wide clamp scenarios (`map-pan-zoom`, `map-rotation-gesture`), which already state 4–20
  correctly, and no restatement of the route-overview / POI floors in this file (their owning capabilities state
  them).
- No guideline edit: `guidelines/MapRendering.md` §3 describes the area-favorites floor as a concept and names
  no number, so nothing there contradicts the corrected scenario.

## Decisions

### D1: correct the spec sentence, and add the comparison that was missing

**Chosen**: the scenario names the two bounds of the fit it describes — "below the area-favorites magnification
floor (14) or above the maximum magnification (20)" — and
`app/src/test/java/com/naviveylin/ui/map/FavAutoZoomClampRangeTest.kt` derives both bounds *from the code* and
compares them to the numbers the scenario states, so neither artefact is written down twice and a movement on
either side fails the case.

*Alternatives eliminated by the evidence*

- **Move the code to 4–18** (make the spec right): impossible — the ceiling 18 contradicts `MAX_MAG = 20`, which
  `map-pan-zoom` and `map-rotation-gesture` state as the application limit and which the zoom control, the
  gesture clamp, the viewport restore and `MiniMap` all use; and a floor of 4 contradicts this requirement's own
  "The area-favorites floor bounds the fit" plus the `poi-search` / `route-map-overview` scenarios that exist to
  let *their* fits go below the favorites floor. The scenario is the only artefact that disagrees, so there is no
  reading of the tree in which the code is the stale side.
- **Split the scenario per caller** (the second candidate `TODO.md` §118 names): impossible without duplicating
  ownership — the non-favorite callers' floors are already specified where they belong (`poi-search` "Distant POI
  zooms out below the area-favorites floor" with the render-stability minimum, `route-map-overview` "Long trip
  zooms out below the area-favorites floor"), and this requirement is the area-favorite fit. Restating them here
  would put one bound in two capabilities.
- **Leave the sentence and record the divergence as prose** (what `fix-area-fit-zoom-rounding`'s design did as an
  Open Question, producing `TODO.md` §118): impossible as an end state — the file's own scenarios are written
  against the floor, so the stale sentence is a contradiction inside one requirement, and the entry exists
  precisely to close it. A test cannot fail on a divergence that no case compares.
- **Have the new case pin the numbers as constants instead of reading the spec**: it would assert the code against
  itself and pass on HEAD — no red, and the next stale sentence would be invisible again.

## Risks / Trade-offs

- **The case reads a file by relative path** (`../openspec/specs/fav-auto-zoom/spec.md`), the way
  `MapMenuBackOrderComposeTest` reads `src/main/java/...` for the band order: an `:app` unit test's working
  directory is the module directory. A future source-set change that moves the working directory fails the case
  with the resolved absolute path in the message (`the spec file must exist at …`) instead of silently.
- **A scenario rewording touches the case**: the case parses the scenario's `- **WHEN**` line and requires exactly
  two numbers on it; a reworded line that keeps the two bounds passes, one that drops or adds a number fails with
  the line quoted. That is the guard's point — the scenario is the claim about a two-ended range.
- **The red run's XML is not versioned**: `app/build/` is gitignored, so the copies of the red and the two
  mutation runs under `app/build/loop118-evidence/` survive only until a `clean`; they are there for the review, and
  `tasks.md` §4 names each copy and says so. The second half of the fix (18 → 20) is proven by the ceiling mutation
  rather than by the HEAD red alone, because the red run stops at its first assertion.

## Verification

- Host: `FavAutoZoomClampRangeTest` — red on HEAD (`tests="1" failures="1"`,
  `2026-10-09T18:21:03.694Z`), green after the scenario edit; two revert-check mutations (one per bound) fail the
  case on the bound they move.
- Gate: `./gradlew test -PforceTests --no-build-cache` (both flavors) — the new case runs in the mobile and the
  automotive variant, `failures=0` in both.
- Device: none required — the claim is a comparison of two files in the tree, and the fit's own device-level
  behaviour is covered by the existing fit cases and the device task that `fix-area-fit-zoom-rounding` left open.
