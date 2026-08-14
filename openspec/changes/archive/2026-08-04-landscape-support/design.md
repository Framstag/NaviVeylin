## Context

Current `MapCanvasScreen.kt` uses a single `Column` aligned `TopEnd` with `statusBarsPadding()` for all overlay controls. `ZoomControls.kt` is a vertical `Column` pill. No orientation awareness exists — the app does not handle landscape.

Constraints:
- Min SDK 26, no Google Play Services
- Compose `BoxWithConstraints` available from Compose 1.0+
- `statusBarsPadding()` uses `WindowInsets` — works in both orientations but pushes content into the camera notch area in landscape
- All overlay buttons are `FilledTonalIconButton` with `shadow(3.dp, RoundedCornerShape(16.dp))`

## Goals / Non-Goals

**Goals:**
- Orientation-aware layout switching in `MapCanvasScreen` using `BoxWithConstraints`
- Landscape layout: menu, compass at top-right; search+favorites side-by-side below; location options, zoom, mylocation at bottom-right
- Portrait layout: unchanged (top-right vertical column)
- `ZoomControls` accepts orientation parameter to render horizontal or vertical
- No buttons at the very top edge in landscape (8dp minimum padding)

**Non-Goals:**
- No foldable-specific posture handling (hinge, tabletop) — future change
- No tablet-specific layout variants — landscape layout serves both phone-landscape and tablet
- No animation between layout transitions — simple recomposition is sufficient

## Decisions

### Decision: BoxWithConstraints over Configuration.orientation

**Choice:** `BoxWithConstraints` (`maxWidth > maxHeight`)

**Rationale:** `Configuration.orientation` is deprecated in API 36+. `BoxWithConstraints` is the Compose-native approach, works correctly in multi-window and foldable split-screen, and recomposes automatically on configuration change without activity restart.

**Alternatives considered:**
- `LocalConfiguration.current.orientation` — deprecated, doesn't handle multi-window
- `LocalView.current.rootWindowInsets` — more verbose, no advantage

### Decision: All controls on right side in landscape

**Choice:** Menu, compass, search, favorites at top-right; location options, zoom, mylocation at bottom-right. Left side reserved for navigation hints.

**Rationale:** Navigation state overlay (`NextTurnOverlay`, `NavigationStateOverlay`) renders on the left during routing. Putting buttons on the left would cause overlap. Right-side placement keeps all controls accessible without conflicting with nav UI. Favorites grouped with search as related action buttons.

**Alternatives considered:**
- Left-side stack for secondary controls — conflicts with nav hints during routing
- Bottom bar — too crowded, loses visual hierarchy

### Decision: ZoomControls takes `isLandscape: Boolean` parameter

**Choice:** Add `isLandscape` param to `ZoomControls` composable; render `Row` when true, `Column` when false

**Rationale:** Single composable, minimal duplication. The internal structure (buttons, label, styling) is identical — only the layout direction changes.

**Alternatives considered:**
- Separate `HorizontalZoomControls` composable — code duplication
- `orientation` enum parameter — same thing, Boolean sufficient

### Decision: No animation for layout transitions

**Choice:** Simple recomposition on orientation change

**Rationale:** Map screen already re-renders on configuration change. Adding `animateContentSize()` or `AnimatedVisibility` would add complexity with minimal UX benefit — orientation changes are infrequent and instant.

## Risks / Trade-offs

- **Risk:** `BoxWithConstraints` recomposes on every size change (including soft keyboard) → **Mitigation:** Layout is cheap (just visibility + alignment flags), no measurable perf impact
- **Risk:** Landscape on devices with side-mounted buttons could conflict with bottom-right cluster → **Mitigation:** Controls are inset from edge by 8dp padding, same as portrait
- **Trade-off:** Left-side stack in landscape may be hard to reach on large tablets → Acceptable for now; tablet-specific layout is a non-goal
