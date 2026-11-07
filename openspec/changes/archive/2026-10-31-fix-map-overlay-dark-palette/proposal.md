# Proposal

## Why

Three map-overlay colours are literals that read no presentation flag and no shared palette, so dark
presentation leaves them at their light value — and two of them disagree about the same element.

**Root cause (one mechanism):** no presentation branch and no shared carrier exist for the app-drawn overlay
palette, so every overlay that is not the vehicle marker or the compass keeps its light literal in dark
presentation, and the phone/car accuracy ring pair diverged.

Evidence, measured on the tree (2026-10-31):

- `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt:239-240` — the phone accuracy ring is two
  module-level constants, `0x1A4A90D9` (fill) and `0x664A90D9` (border), and `drawAccuracyCircle` (`:135`)
  takes no presentation input although the sibling arrow draw (`:90`, `drawCompassArrowWithShadow(center,
  bearingDegrees, dark)`) already receives `dark: Boolean` from the composable's own parameter (`:54`).
- `auto/src/main/java/com/naviveylin/auto/AutoMapRendererMarkers.kt:212-221` — the car accuracy ring is a
  **different** pair, `0x1A2196F3` / `0x662196F3`, inside `MarkerDrawObjects`, whose KDoc (`:175-178`) says it
  is "rebuilt only when an input that defines the geometry or the palette changes — the surface bounds, the
  projection density, or the dark presentation". The ring's colours are the one thing that does not branch.
  No platform constraint forces `#4A90D9` ≠ `#2196F3`, so the divergence breaks `cross-variant-ui-parity`.
- `app/src/main/java/com/naviveylin/ui/map/MiniMap.kt:91` — the mini-map's current-position dot is
  `Color(0xFF1A73E8)`, which **is** `ui/theme/Color.kt:6 PrimaryLight`, i.e. `Theme.kt:18`'s `primary` role,
  i.e. the same value `MiniMap.kt:88` hands the primary object marker as `markerColor`; with the dynamic
  scheme off, the dot and the primary object marker are the same colour, which
  `openspec/specs/mini-map/spec.md:89`'s "distinct style" does not allow. Its two `Color.White` casing rings (`:247`, `:249`) never branch either.
- `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt:41-61` is the precedent the other overlays lack: a
  fixed, presentation-branched palette (`GpsFillNoFix 0xFFFFCDD2` → `CompassDarkFillNoFix 0xFF93000A`) plus the
  rule at `:74` that these values come from "a palette as the fill (never from a theme color role)".
- **The defect is not caught by any existing case.** Measured:
  `grep -rln 'ACCURACY_FILL_COLOR\|ACCURACY_BORDER_COLOR\|gpsMarkerColor\|accuracyBorderPaint\|accuracyFillPaint' app/src/test auto/src/test`
  prints nothing, and `grep -rn 'accuracy circle' guidelines/` found one paragraph that closed the vehicle-marker
  entry with "The accuracy circle is untouched" — it was `guidelines/UI.md` in the document's pre-split numbering,
  and a later change split that document while this one was open, so the paragraph this change writes in its
  place now sits at `:108-110`.
  which closes the vehicle-marker entry with "The accuracy circle is untouched."

**Red case on HEAD** (the smallest case that exercises the missing bound, no new API needed):
`auto: AutoMapRendererMarkerDrawCacheTest` "the accuracy ring branches on the dark presentation" — it builds
`MarkerDrawObjects` twice (`dark = false` / `dark = true`, the pattern the existing
`aDarkPresentationChangeRebuildsThePalette` case already uses) and asserts the two accuracy fill paints differ.
On HEAD both are `0x1A2196F3`, so it fails. The phone side gets the same falsifier as drawn pixels
(`app: LocationMarkerOverlayPaletteTest` "the drawn accuracy ring changes with the presentation", Robolectric
`@GraphicsMode(NATIVE)` after `CompassNeedleStrokeTest`'s harness).

**The single fix.** One `:core` palette object holds the ring and dot colours per presentation, and the three
draw sites read it: this is the change `status` from the recorded decision on issue #34 (D1 scope, D2 the
direction rule, D3 the carrier, D4 light-presentation distinctness). The delta is an **ADDED** requirement in
each owning capability, so no shipped requirement is rewritten and nothing else in those specs moves.

The requirement the defect contradicts: `dark-mode` (dark presentation applies to app-drawn elements), which
today states the rule only for controls that carry status through a fixed hue family
(`openspec/specs/dark-mode/spec.md:64`) and says nothing about the map-backed overlays. `Guidelines`:
`guidelines/UI.md` §9 ("Status colors live outside the theme"), whose rule is the one this change extends, and
`guidelines/UI.md` §8 (the "accuracy circle is untouched" sentence).

**Scope: phone and Android Auto** — the same two elements exist on both surfaces and the divergence between
them is half the defect.

## What Changes

- **`core/src/main/java/com/naviveylin/core/GpsMarkerPalette.kt`** (new) — the presentation-branched overlay
  palette: the accuracy ring's fill and border and the mini-map current-position marker's dot, halo and casing,
  each as a pure function of the dark-presentation flag. It sits beside `VehicleMarkerGeometry` because
  `guidelines/UI.md` already states the invariant "geometry and palette live in `:core`
  `VehicleMarkerGeometry`, shared by both renderers" (the marker's own entry, at `:103-109` after the document
  split described above). It carries an override seam (values are read from one
  object, so a surface whose chrome dictates a colour passes its own) — Android Auto may override, nothing
  overrides today.
- **`LocationMarkerOverlay.kt`** — `drawAccuracyCircle` takes the ring colours from `GpsMarkerPalette` for the
  `dark` flag the composable already receives; the ring's geometry (radius, stroke width, min-radius rule) keeps
  its single path.
- **`AutoMapRendererMarkers.kt`** — `MarkerDrawObjects.accuracyFillPaint` / `.accuracyBorderPaint` take the same
  `GpsMarkerPalette` values for the `dark` flag the object already carries, so the two surfaces draw the same
  ring. The `0x2196F3` pair disappears.
- **`MiniMap.kt`** — the current-position dot, its inner highlight, its halo and its casing read the palette
  (dot and casing brighter than the halo in dark; the halo dims because it carries no navigation-essential
  information); the dot's hue leaves the marker roles' neighbourhood, so it stops colliding with `markerColor`.
  The widget also gains a required `isDarkPresentation: Boolean` (it had no presentation input, measured during
  apply), plumbed through `SearchDialog`/`PoiResultsWithMap` and `LocationDetailsDialog` and fed from
  `MapCanvasUiState.isDarkPresentation`.
- **`guidelines/UI.md`** — §8: the "accuracy circle is untouched" sentence is replaced by the ring's palette
  branch; §9: the status-colours entry gains the rule that decides *which* app-drawn elements stay visible in
  dark presentation (nav-essential ones: the vehicle marker and the active route; everything else dims to a dark
  tone of its own hue family) and that the exempt set stays small.
- **Not in scope:** the vehicle marker's own palette (already correct), the compass fill (§9, already correct),
  the route colours (already correct), a light-presentation redesign of the mini-map's object markers, and any
  AA-chrome colour (host-rendered).

Additive in light presentation: the ring and the dot keep a light tone; the dot's light value moves off
`PrimaryLight` only where the recorded decision requires distinctness (D4). Not BREAKING: no API, no persisted
state, no user flow, no template. Rollback: revert the four code paths — the palette object becomes unused and
the three draw sites return to their literals; the new cases go red, which is the point.

surface: 12 cases added, 0 rewritten, 7 code files (6 changed + 1 new), 16 `:app` test call sites in 11 files
realigned for the new `isDarkPresentation` parameter, 2 guideline paragraphs, 0 fixtures realigned — `grep -rn 'ACCURACY_FILL_COLOR\|ACCURACY_BORDER_COLOR\|gpsMarkerColor' app/src/main | wc -l` (7),
`grep -rn 'accuracyFillPaint\|accuracyBorderPaint' auto/src/main | wc -l` (4),
`grep -rln 'ACCURACY_FILL_COLOR\|gpsMarkerColor\|accuracyBorderPaint' app/src/test auto/src/test` (no hit),
`grep -rn 'accuracy circle' guidelines/` (1 paragraph)

## Capabilities

### New Capabilities

None. Each element already belongs to a capability: the accuracy circle to `gps-location-marker`, the
current-position marker to `mini-map`, the dark-presentation rule to `dark-mode`.

### Modified Capabilities

None at the level of shipped text. Three requirements are **added**:

- `dark-mode`: which app-drawn elements keep a clearly visible tone in dark presentation, and the rule that the
  exempt set stays small — the tie-breaker between `dark-mode`'s dim rule and the marker's brighter-in-dark
  convention, which today read as a conflict (issue #34's loop verdict).
- `gps-location-marker`: the accuracy circle's fill and border follow the resolved presentation, from one shared
  value on both surfaces, while its geometry does not branch.
- `mini-map`: the current-position marker's dot, halo and casing follow the resolved presentation, the dot stays
  visible in dark because it carries the user's own position, and its hue stays distinct from the object-marker
  roles.

Specs deliberately left unchanged: `cross-variant-ui-parity` (no text change — its existing "same visual style
wherever the platform constraints allow" requirement is *why* the car ring moves, and the change satisfies it
instead of amending it), `auto-map-renderer` (it specifies that the marker's palette objects are rebuilt for a
new presentation (`:278`) but names no accuracy-ring colour, so the car ring's value is an implementation
detail of this palette), `auto-map-layout`, `compass-button` (its own palette is correct and untouched).

## Impact

| path | what changes |
|---|---|
| `core/src/main/java/com/naviveylin/core/GpsMarkerPalette.kt` | new — the ring and dot palette, per presentation |
| `core/src/test/java/com/naviveylin/core/GpsMarkerPaletteTest.kt` | new — 4 cases on the palette's values |
| `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` | `drawAccuracyCircle` reads the palette for `dark`; module-level ring constants removed |
| `app/src/main/java/com/naviveylin/ui/map/MiniMap.kt` | dot, inner highlight, halo and casing read the palette; `gpsMarkerColor` removed; gains a required `isDarkPresentation` (measured during apply: the widget had no presentation input) |
| `app/src/main/java/com/naviveylin/ui/map/SearchDialog.kt` | `SearchDialog` + `PoiResultsWithMap` gain `isDarkPresentation` and pass it to both mini maps |
| `app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt` | `LocationDetailsDialog` gains `isDarkPresentation` and passes it to its mini map |
| `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreenModalBands.kt` | both modal call sites pass `state.isDarkPresentation` |
| `app/src/test/java/com/naviveylin/ui/map/LocationMarkerOverlayPaletteTest.kt` | new — 4 raster cases on the drawn ring |
| `app/src/test/java/com/naviveylin/ui/map/MiniMapCurrentPositionPaletteTest.kt` | new — 2 raster cases on the drawn dot |
| `auto/src/main/java/com/naviveylin/auto/AutoMapRendererMarkers.kt` | the two ring paints read the palette; `0x2196F3` pair removed |
| `auto/src/test/java/com/naviveylin/auto/AutoMapRendererMarkerDrawCacheTest.kt` | 2 cases added — the ring branches, and it takes the shared values |
| `guidelines/UI.md` | §8's "untouched" sentence replaced; §9's status-colours entry gains the visibility rule |
| `openspec/specs/dark-mode/spec.md`, `gps-location-marker/spec.md`, `mini-map/spec.md` | one added requirement each, after archive |

Modules and Android components: `:core`, `:app` and `:auto`. Both `:app` flavors build (the automotive flavor
runs the same phone UI). No manifest, resource, Gradle, native, JNI, submodule or bridge change — the overlays
are Compose/Android `Canvas` code.

## Not in this change

- **The mini-map's object-marker colours.** They stay on the theme roles (`MiniMap.kt:88-90`); with
  `dynamicColor = true` those are wallpaper-derived, so the dot's distinctness is asserted against the fixed
  scheme only. Re-colouring the object markers is a separate item.
- **The car's accuracy-ring *geometry*** (stroke width, threshold). Only the colours move.
- **A device-only claim.** Both surfaces are Compose/`Canvas`, so the cases are host cases; the emulator task
  measures the pixels the same way but is not the proof.
