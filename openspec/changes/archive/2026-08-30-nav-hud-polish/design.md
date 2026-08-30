# Design — nav-hud-polish

## Context

See proposal.md — Why. On-device review of `move-max-speed-on-map` found five issues in the navigation HUD: compass and speed widget separated, badge shifting when the limit sign toggles, next-turn card with a right gap, routing status not reaching the window bottom, and small next-turn typography.

Current layout (navigation): `MapCanvasScreen` renders a full-height Column — `NextTurnOverlay` (top, full width), a weighted Box with the right-side widget column (compass top-anchored, speed widget bottom-anchored via `Spacer(weight)`), and `NavigationStateOverlay` (bottom). The nav Column applies `statusBarsPadding` + `navigationBarsPadding`.

## Goals / Non-Goals

Goals:
- Group compass directly above the speed widget during navigation (bottom-anchored, above the routing status).
- Keep the badge/compass fixed when the limit sign appears or disappears.
- Full-width next-turn hints (no right gap).
- Routing status flush with the window bottom.
- Slightly larger next-turn typography.

Non-Goals:
- No change to follow-mode placement (compass above speed, top-right — already correct).
- No change to AA rendering.
- No data/native/JNI changes.

## Decisions

### 1. Navigation placement: compass directly above speed widget, bottom-anchored

The nav right-side column becomes: `Spacer(weight)` → compass → 8dp → speed widget. The cluster is bottom-anchored above the routing status, mirroring the AA right-visualisation-region grouping.

Alternatives considered:
- Keep compass at top, move speed widget up below it (AA rose-at-top pattern). Rejected — the user explicitly asked for the compass to move above the speed widget; the speed widget stays near the routing status where it was placed.
- Keep the current separated layout. Rejected — the two indicators read as unrelated.

### 2. Stable widget: reserved sign slot + reserved widget slot in bottom-anchored placements

`SpeedWidget` gains `reserveLimitSpace: Boolean = false` and `reserveSlotWhenHidden: Boolean = false`. When `reserveLimitSpace` is set and no limit is known, an invisible 40dp Box (same footprint as the sign, incl. 6dp top spacing) is drawn below the badge, so the badge and compass never shift. When `reserveSlotWhenHidden` is set and no current-speed data is available, the widget renders an invisible structure with the same footprint as the visible widget (badge + sign slot) instead of nothing, so the compass does not shift when the widget appears or disappears. The nav placement passes both; follow mode (top-anchored, badge already stable) keeps the defaults.

Alternatives considered:
- Always reserve the slot. Rejected — leaves a visible gap below the badge in follow mode when no limit exists.
- Bottom-align the widget so the badge sits at the bottom and the sign above it. Rejected — violates the spec'd "sign below badge" layout.
- Reserve only the sign slot, not the whole widget slot. Rejected — the compass still jumps at navigation start before the first speed callback.

### 3. Full-width next-turn hints

`NextTurnOverlay` drops the asymmetric `padding(start = 0.dp, end = 16.dp)` — the card spans edge to edge. During navigation the top area is free: the action button columns are hidden (`!navState.isNavigating`) and the compass/speed cluster is bottom-anchored, so nothing overlaps the hint card.

Alternatives considered:
- Symmetric 16dp padding. Rejected — the user asked for full width, and the top area is genuinely free during navigation.
- Keep the right gap. Rejected — visible asymmetry.

### 4. Routing status flush with the window bottom

The nav Column drops `navigationBarsPadding`; `NavigationStateOverlay` gets square bottom corners (`RoundedCornerShape(topStart = 12, topEnd = 12, bottomStart = 0, bottomEnd = 0)`) and its content Column gains `navigationBarsPadding` so the stop button/stat text stay clear of the gesture bar. The off-route overlay clip shape matches the new card shape.

Alternatives considered:
- Keep rounded corners + nav-bar padding. Rejected — leaves the reported gap below the card.
- Extend the card behind the nav bar without content padding. Rejected — the stop button would sit under the gesture bar.

### 5. Next-turn typography +2sp

Distance `titleLarge` (22sp) → `headlineSmall` (24sp); instruction/destination `titleMedium` (16sp) → 18sp via `copy(fontSize = 18.sp)`; next-next row `bodyMedium` (14sp) → 16sp via `copy(fontSize = 16.sp)`. Next-next stays smaller than next-turn (spec `next-turn-overlay`).

Alternatives considered:
- One full typography level up (titleMedium → titleLarge). Rejected — 22sp is too large for a two-line instruction; "slightly" means +2sp.
- Leave fonts unchanged. Rejected — user asked for larger.

### 6. Centered indicator cluster

The compass (fixed 48dp circle) is narrower than the speed badge, so right-aligning the column put the compass center right of the badge center. The right-side columns now use `horizontalAlignment = CenterHorizontally` (nav + follow-landscape), and the follow-portrait column wraps compass + speed widget in a centered inner column while the location/zoom block stays right-aligned. All three elements share one center axis.

Alternatives considered:
- Center the whole follow-portrait column including location/zoom. Rejected — the control cluster would drift left of the right edge.
- Keep right alignment. Rejected — the compass reads as detached from the badge.

### 7. Fixed badge width

The badge width varies with the speed value ("48 km/h" vs "120 km/h"), which shifts the centered cluster on value changes and on follow-mode ↔ navigation switches. The badge Box now renders an invisible `"999 km/h"` text (3-digit max) with `contentAlignment = Center`, reserving the widest width; the visible value is centered inside it. The max-speed sign is already a fixed 40dp circle, so it needs no reservation.

Alternatives considered:
- Fixed dp width. Rejected — font metrics vary; a hardcoded dp width is fragile across densities.
- `TextMeasurer`-based measurement. Rejected — more complex than the invisible-text pattern and needs Compose 1.5+ APIs.

## Risks / Trade-offs

- [Full-width hints overlap something in future layouts] → The top area is free today; if a top-right control returns, re-apply a right constraint (see `nav-hints-layout` spec).
- [Square bottom corners look odd on 3-button nav] → The card extends behind the opaque bar; content is padded above it, so nothing is obscured.

## Migration Plan

Pure UI change, no data migration. Rollback: revert the five edits. No versioning impact.

## Open Questions

None.
