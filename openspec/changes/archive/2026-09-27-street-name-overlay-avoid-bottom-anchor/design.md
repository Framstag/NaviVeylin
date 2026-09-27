# Design — Street-name overlay vs bottom-center vehicle anchor

## Context

See proposal.md — Why. The street-name label (AA: `StreetNameLabel`, phone: `StreetNamePill`) is always drawn bottom-center, which collides with the vehicle marker when the user picks the `BOTTOM_CENTER` anchor preset (grid preset `fx=0.5, fy=0.9`, `core/VehicleAnchor.kt`). The fix moves the label to top-center for that preset on both surfaces. Existing bottom-anchoring already respects host geometry (stable/visible area bottoms, design D1/D5 in `vehicle-position-presets` + street-label work); the top placement must mirror that for host chrome (AA instruction panel, phone status bar).

Constraints from the codebase:

- AA label: pure Canvas in the existing overlay drawer (`NavigationScreen`, `FreeDrivingScreen`); position helpers in `StreetNameLabel` (geometry from stable/visible area bottoms; `isMapLabelSafe` gates the host-ETA-card fallback in navigation).
- Phone label: Compose `StreetNamePill` inside `MapCanvasScreen`'s Box, `Modifier.align(BottomCenter).padding(bottom=16.dp)`, shown only when no route is active; the active preset is already in UI state (`MapCanvasUiState.activeFollowAnchor`).
- Anchor presets are per-mode: `routingAnchor` (nav) vs `freeDrivingAnchor` (free driving), each screen reads its own.
- Top-center is clear on both surfaces: AA top-right holds compass/speed (`SurfaceIndicators`, right-aligned), host instruction panel is above the stable-area top; phone top-left holds the action column, widgets top-right, search opens as a sheet.

## Goals / Non-Goals

Goals:

- Street name never covers the vehicle when the active anchor preset is `BOTTOM_CENTER`, on AA (both screens) and phone (free driving).
- Keep bottom behavior byte-identical for every other preset (no regression; existing tests keep passing unchanged).
- Top placement respects host-guaranteed areas (mirror of the bottom logic).

Non-Goals:

- Generic overlap detection between label and marker rect (see D1).
- Moving the label for other bottom-row presets (`BOTTOM_LEFT`, `BOTTOM_RIGHT`, far-left/far-right) — vehicle and label are horizontally offset there; residual overlap is name-length dependent and marginal (see Risks).
- Any change to settings, persistence, or the anchor model.

## Decisions

### D1: Rule keyed on the preset, not on geometry overlap

Chosen: label moves to top-center if and only if the mode's active anchor preset is exactly `BOTTOM_CENTER`.

Why over the alternatives:

- Alternative A — geometry overlap detection (compute label rect vs marker rect): correct in the general case, but the label does not know the marker icon size, overlap becomes name-length dependent (short names never reach the marker, long ones do), and it reintroduces pixel-math coupling between two unrelated overlays. Overkill for a single preset in a fixed 5×3 grid.
- Alternative B — any bottom-row preset (all `fy == 0.9`): bottom-left/bottom-right markers are horizontally offset from the label; only very wide names could graze the icon edge. Moves the label pointlessly for most users of those presets. Rejected as over-broad.
- Alternative C — always draw top-center regardless of anchor: regresses the common case (center anchor) for no benefit. Rejected.

Deterministic, trivially testable, exactly matches the reported bug.

### D2: Top placement anchors to the top of the stable/visible area (mirror of bottom logic)

Chosen: `StreetNameLabel` gains a placement (`TOP` / `BOTTOM`, default `BOTTOM`). For `TOP`, the label's top edge resolves to the largest of the known stable/visible area tops plus a symmetric margin (mirror of today's `bottoms.min()`); when both areas are unknown, fall back to the raw surface top + margin (same fallback the bottom path already uses with the raw surface bottom). Horizontal centering, `MAX_WIDTH_DP` cap, ellipsize, styling unchanged.

Why over alternatives:

- Raw surface top: puts the label under the AA host instruction panel (navigation) and under the phone status bar. Rejected.
- Fixed top offset: breaks on head units with different chrome heights. Rejected.

`max(tops)` mirrors `min(bottoms)` semantics: conservative against the tallest host chrome and keeps both placements inside the area the host guarantees visible.

Phone equivalent of the stable/visible-area top: the phone surface is edge-to-edge (`MainActivity` `enableEdgeToEdge`), so the map Canvas renders under the system bars and the top-safe inset comes from `WindowInsets.statusBars` (status-bar height incl. display-cutout avoidance). The pill's TOP placement therefore applies `Modifier.statusBarsPadding()` as the outer modifier, with the existing 16.dp visual margin inside it (mirror of the bottom's 16.dp). This closes the "raw surface top — under the phone status bar" rejection above with real device values rather than a hardcoded offset (rejected alternative, same as the AA fixed-offset case). Compose-test/Robolectric environments report zero insets, so the padded geometry stays deterministic in tests.

### D3: `TOP` placement bypasses the ETA-card fallback in navigation

Chosen: when the routing anchor preset is `BOTTOM_CENTER`, the navigation screen always draws the map label at top-center; `isMapLabelSafe`/`setTripText` (host ETA-card fallback) applies only to bottom placement. Top-center is clear by construction (stable/visible area top excludes the instruction panel; the ETA card is bottom-left), so the fallback would only fight the fix.

Why over the alternative:

- Keep `isMapLabelSafe` gating for `TOP`: on a host that never delivers a bottom-clear area, the street name would land in the ETA card instead of moving to top-center — the reported bug would persist on exactly the host that triggers it. Rejected.

### D4: Parity via the same preset rule on both surfaces

AA: placement chosen per screen from that screen's anchor (`routingAnchor` / `freeDrivingAnchor`). Phone: `MapCanvasScreen` switches the pill's `Alignment` between `BottomCenter` and `TopCenter` from `state.activeFollowAnchor`. Same presets, same rule, same visible outcome (guidelines/UI.md parity rule); the only platform deviation is the layout container (Canvas anchor vs Compose alignment), which the parity rule already accommodates.

## Risks / Trade-offs

- [Top label under host chrome (AA navigation) when the host delivers no stable/visible area] → Both areas unknown falls back to surface top + margin; the existing spec scenario "Street name stays clear when host geometry is unknown" covers the bottom path and the same conservative top fallback is used. Verify on AAOS emulator with a host that reports areas (the emulator host delivers stable/visible areas; see D5 history in `vehicle-position-presets`).
- [Bottom-left/bottom-right presets keep a marginal, name-length-dependent chance of grazing the icon] → Out of scope for this change; the presets are horizontally offset and typical names clear. If observed, extend the rule to the bottom row in a follow-up (spec change required).
- [Regression risk for the common center anchor] → Placement default is `BOTTOM`; all existing `StreetNameLabel` tests exercise the bottom path unchanged; phone pill defaults to `BottomCenter`.
- [Parity drift between surfaces] → Single rule keyed on the shared enum; unit tests pin both call sites to the same mapping.

## Migration Plan

- Additive: extension of `StreetNameLabel` with a defaulted placement parameter; phone alignment switch defaults to existing behavior. No settings or data migration.
- Rollback: revert the three call sites (both AA screens + phone pill alignment). Specs' conditional wording then matches the reverted behavior.
- Verification: unit tests (below), `:auto` unit tests + `:app` unit tests via run-tests skill, debug build via build-app skill, then on-device: AAOS emulator — pick `BOTTOM_CENTER` in the AA vehicle-position picker (navigate + free drive) and confirm the label sits top-center and the vehicle is uncovered; phone emulator — same preset in the location-options sheet.

## Open Questions

None — the rule, margins, and fallbacks are concrete; anything discovered on device is implementation detail, not spec change.
