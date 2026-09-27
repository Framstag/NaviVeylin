# Proposal

## Why

The phone compass button is the only themed map control whose **fill** bypasses the
day/night presentation: it uses three fixed *light* pastels (`#FFCDD2` / `#FFF9C4` /
`#C8E6C9`) while its needle and border come from the Material theme. In dark
presentation the theme's `on*` roles flip to light tones by design, so a light needle
is drawn on a light fill — measured contrast ≈ **1.04:1**, i.e. the needle and its "N"
label become unreadable — and the bright pastel glares against the dark map. This is
deterministic in every dark configuration (no wallpaper/dynamic-color luck involved)
and it violates the existing `dark-mode` requirement that all app controls render with
the dark color scheme.

The Android Auto compass rose has the mirror-image problem: `SurfaceIndicators` draws
it with one fixed dark chip (`0xCC1C1B1F` + white + red north pointer) regardless of
the host's day/night state, so it never follows the presentation the rest of the
surfaced map already follows.

## What Changes

- Compass fix-status fill gets a **per-presentation palette** (light pair and dark pair
  per GPS fix quality: no fix / poor / good), mirroring the palette-branches-on-dark
  precedent already used by `VehicleMarkerGeometry`.
- The compass needle, its "N" label and the rim stop being read from the dynamic
  Material scheme and become part of the same per-presentation palette (`onFill` +
  rim), so fill/needle contrast is guaranteed by construction instead of depending on
  a wallpaper-derived role.
- The phone compass reads the app's **resolved dark presentation**
  (`MapCanvasUiState.isDarkPresentation`). It SHALL NOT read `isSystemInDarkTheme()`
  directly — that would bypass the three-state On/Off override of the dark-mode
  preference.- The Android Auto compass rose gets the same treatment: a per-presentation rose
  palette driven by the same *resolved* presentation that drives the surface's
  stylesheet daylight flag on that screen (dark-mode preference × host day/night
  signal via `RendererGate.setDarkPresentation` / `resolveCarDark`), and never the
  host signal or a phone's system night mode alone.
- Amends the `compass-button` spec, which currently *prescribes* the defect by
  requiring the fill to use "light colors".
- Scope boundary on Android Auto: only the **compass rose** gains a presentation
  palette. The speed badge keeps its fixed dark-chip convention and the speed-limit
  sign keeps the standard-mandated white circle with red ring — a legally standard
  traffic sign must not change appearance with presentation.
- Additive, no behavior change in light presentation. Rollback: revert the palette
  branch (single commit scope).

## Capabilities

### New Capabilities

None — day/night behavior of both compass widgets belongs to capabilities that already
exist.

### Modified Capabilities

- `compass-button`: the "GPS fix status fill color" requirement drops the "light
  colors" mandate and gains a day/night palette plus scenarios for both presentations;
  the needle/rim color requirement moves from theme-derived to palette-derived.
- `dark-mode`: the "Dark presentation applies to UI controls" requirement gains a
  scenario for controls that carry status by a fixed hue family (compass fill, overspeed
  red) — those dim by their own dark palette rather than by Material theme roles.
- `auto-map-layout`: the compass-rose requirements gain the day/night palette of the
  app-drawn rose, so the rose follows the host day/night state like the rest of the
  surface.

## Impact

Scope: **both platforms** — phone (`:app`) and Android Auto / AAOS (`:auto`).

Affected code:

- `app/src/main/java/com/naviveylin/ui/map/CompassButton.kt` — palette functions, new
  `isDarkPresentation` parameter, palette-driven needle/rim/"N" color.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — `MapCompassBlock` /
  `MapRightWidgetColumn` signatures and their call sites (both orientations).
- `auto/src/main/java/com/naviveylin/auto/SurfaceIndicators.kt` — rose palette,
  `draw()` gains the presentation flag.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt`,
  `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — pass the host
  day/night state into `SurfaceIndicators.draw`.
- Tests: `app/src/test/java/com/naviveylin/ui/map/CompassButtonComposeTest.kt`,
  new `CompassPaletteTest.kt`, `auto/src/test/java/com/naviveylin/auto/SurfaceIndicatorsTest.kt`.

No manifest, Gradle, dependency, persistence or i18n impact.

Native/JNI: **none** — no libosmscout submodule patch and no local bridge-module
override; both compass widgets are app-drawn (Compose Canvas / `android.graphics`
Canvas).

Guidelines affected:

- `guidelines/UI.md` §8 (compass button, phone overlay styling) and §9 (dark mode) — a
  task in this change updates both; the guideline is not edited from the planning phase.
- `guidelines/Design.md` — no new architecture or threading: the flag is already
  delivered through existing state flows and the renderer's existing
  `setDarkPresentation` path.
- `guidelines/MapRendering.md` — only the stylesheet `daylight` flag is documented
  there; unchanged (this change touches app-drawn overlays, not the style sheet).

Previous specifications changed by this proposal: `compass-button`, `dark-mode`,
`auto-map-layout` (all listed above; `auto/navigation-view` and `auto/free-driving`
reference the rose but their requirements are unchanged).

Unresolved before design: whether the dark fills keep an opaque fill (status color stays
predictable) or adopt the 0.92-alpha overlay convention used by the speed badge — the
proposal assumes **opaque**, since an alpha fill mixes the status color with the map
underneath.
