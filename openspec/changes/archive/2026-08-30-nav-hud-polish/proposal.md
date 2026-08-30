# Proposal — nav-hud-polish

## Why

On-device review of the on-map speed widget (change `move-max-speed-on-map`) found five layout/typography issues in the navigation HUD:

1. During navigation the compass sits at the top-right while the speed widget sits at the bottom-right (above the routing status) — the two indicators are far apart instead of grouped.
2. When the max-speed sign appears/disappears, the bottom-anchored speed widget shrinks/grows and the badge (and compass) shift position.
3. The next-turn hint card has asymmetric padding (`start = 0, end = 16dp`) — a visible gap on the right instead of full width.
4. The routing status card does not reach the bottom of the window (nav-bar inset + rounded bottom corners leave a gap).
5. Next-turn text is too small for glanceability while driving.

## What Changes

- **Compass above speed widget (navigation)**: during active navigation the compass moves down to sit directly above the speed widget, both bottom-anchored above the routing status card (right visualisation region). Follow-mode placement (compass above speed, top-right) is unchanged.
- **Centered indicator cluster**: compass, current-speed badge, and max-speed sign share a common center axis in follow mode and during navigation (the compass is no longer right-aligned relative to the badge).
- **Stable widget layout**: the speed widget gains a `reserveLimitSpace` option — when set, the sign slot below the badge is always reserved (invisible when no limit is known), so the badge and compass stay in place when the limit sign appears or disappears. Used for the bottom-anchored navigation placement; follow mode (top-anchored) is already stable and keeps the default. The badge additionally reserves the width of the widest speed value ("999 km/h") so it does not resize when the value changes or the source switches (follow mode ↔ navigation). The navigation placement also reserves the whole widget slot when no speed data is available (`reserveSlotWhenHidden`), so the compass does not shift when the widget appears or disappears.
- **Full-width turn hints**: `NextTurnOverlay` drops the asymmetric right padding — the card spans the full display width (top area is free during navigation: action columns are hidden, compass cluster is at the bottom).
- **Routing status covers the bottom**: the routing status card gets square bottom corners and its content is padded above the system navigation bar; the navigation overlay column no longer applies `navigationBarsPadding` below the card, so the card extends to the bottom edge of the window.
- **Larger next-turn typography**: next-turn distance `titleLarge` → `headlineSmall`, instruction/destination `titleMedium` → `titleMedium` at 18sp, next-next row `bodyMedium` → `bodyMedium` at 16sp. Next-next stays smaller than next-turn (spec `next-turn-overlay`).

## Capabilities

### Modified Capabilities
- `map-speed-widget`: navigation placement groups compass directly above the speed widget; sign slot is reserved in that placement so the badge does not shift when the limit sign is hidden.
- `nav-hints-layout`: next-turn hints span the full display width (no right gap); routing status card covers the bottom of the window.
- `next-turn-overlay`: next-turn and next-next typography increased (distance `headlineSmall`, instruction 18sp, next-next 16sp); next-next remains smaller than next-turn.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/SpeedWidget.kt` — add `reserveLimitSpace` param + invisible sign-slot placeholder.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — navigation layout: compass directly above speed widget (bottom-anchored), pass `reserveLimitSpace = true`; remove `navigationBarsPadding` from the navigation overlay column.
- `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` — square bottom corners, content `navigationBarsPadding`, off-route clip shape updated.
- `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt` — remove asymmetric right padding; bump typography.
- Tests: `SpeedWidgetTest` — placeholder rendered when `reserveLimitSpace` and no limit, sign rendered when limit known; existing tests unchanged.
- No native, JNI, or dependency changes. AA behavior unchanged.

## Rollback

Pure UI change. Revert the five edits; no data or versioning impact.
