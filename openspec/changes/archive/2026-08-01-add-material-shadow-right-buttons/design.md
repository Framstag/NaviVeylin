## Context

Right-side map overlay buttons currently use `IconButton`, `FilledTonalIconButton`, and `FilledIconButton` with zero elevation. The map canvas has no surface-level visual separation between the rendered map and overlay controls. See proposal.md for motivation and specs/button-elevation/spec.md for requirements.

## Goals / Non-Goals

**Goals:**
- Add Material 3 elevation shadow to all 6 right-side overlay buttons
- Use standard `IconButtonDefaults` elevation API — no custom shadow composables
- Maintain existing `surfaceContainerHigh` container colors
- Pressed state reduces elevation for tactile feedback

**Non-Goals:**
- No changes to button size, shape, color, or position
- No changes to left-side or bottom overlays
- No changes to non-button elements (magnification label, dropdown menu)

## Decisions

### Elevation values
- **Resting elevation**: 3.dp (Material 3 level 1) — standard for tonal/filled icon buttons on surfaces
- **Pressed elevation**: 0.dp — button appears to depress into the surface
- Rationale: 3.dp provides clear visual separation from the map without excessive shadow. 0.dp pressed state matches Material 3 convention where pressed = flush with surface.

### API choice: `Modifier.shadow()` (fallback from `IconButtonDefaults`)
- Initial approach used `IconButtonDefaults.filledIconButtonElevation()` / `filledTonalIconButtonElevation()` via the button's `elevation` parameter
- Build failed: `elevation` parameter not available on `FilledIconButton`/`FilledTonalIconButton` in Compose BOM 2024.12.01
- Fallback: `Modifier.shadow(3.dp, RoundedCornerShape(16.dp))` on each button's modifier
- `Modifier.shadow()` is a Compose UI API available in all Compose versions
- Trade-off: static shadow only — no press-state elevation animation
- Menu button changed from `IconButton` to `FilledTonalIconButton` for consistent shadow support

### No `Surface` wrapper needed
- Elevation is applied directly via `Modifier.shadow()` on each button's modifier
- No need to wrap buttons in `Surface` composables

## Risks / Trade-offs

- [Low] Shadow rendering cost is negligible — Compose handles elevation via precomputed shadow geometry
- [Low] `IconButtonDefaults.iconButtonElevation()` may not exist in older Material 3 versions — verify at compile time. Fallback: use `FilledTonalIconButton` for menu button instead
- [Low] `Modifier.shadow()` is static — no press-state elevation animation. Acceptable trade-off for current Compose BOM version
