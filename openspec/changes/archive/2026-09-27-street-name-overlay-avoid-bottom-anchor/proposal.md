## Why

With the vehicle-anchor presets (`vehicle-position-presets`), `BOTTOM_CENTER` keeps the vehicle marker at the bottom-center of the map. Both Android Auto and the phone draw the current-street-name label at bottom-center as well, so the label covers the vehicle marker precisely when that preset is active. Cheap, predictable fix: move the label to top-center in that case.

## What Changes

- `StreetNameLabel` (auto module) gains a placement switch (`TOP` / `BOTTOM`, default `BOTTOM`): top placement anchors the label to the top edge of the host's stable/visible area instead of the bottom edge; horizontal centering, width cap, and styling unchanged.
- Android Auto: `NavigationScreen` uses top placement while its routing anchor is `BOTTOM_CENTER`; `FreeDrivingScreen` does the same for the free-driving anchor.
- Phone: `StreetNamePill` in `MapCanvasScreen` aligns `TopCenter` (instead of `BottomCenter`) while the active follow anchor is `BOTTOM_CENTER`, free-driving case only.
- Specs updated: bottom positioning becomes conditional ("bottom, or top-center when the anchor preset is bottom-center") in the three affected capabilities.
- Additive. No breaking changes. Rollback: revert the placement rules (both surfaces return to bottom-center labels, as before the anchor feature).

## Capabilities

### New Capabilities
- none

### Modified Capabilities
- `auto/free-driving`: "Current street name shown" — position becomes conditional on the free-driving anchor preset.
- `auto/navigation-view`: "Current street name shown during navigation" — position becomes conditional on the routing anchor preset.
- `current-road-info`: phone free-driving street label — position rule + scenario for the bottom-center anchor case.

## Impact

- `auto/src/main/java/com/naviveylin/auto/StreetNameLabel.kt` — placement switch + top-edge resolution (new pure-canvas API; existing bottom path untouched).
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — label placement from `routingAnchor`.
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — label placement from `freeDrivingAnchor`.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — pill alignment from `state.activeFollowAnchor`.
- Tests: `auto/src/test/java/com/naviveylin/auto/StreetNameLabelTest.kt` (top geometry), AA screen wiring tests, phone Compose test for the pill alignment.
- Guidelines: `guidelines/UI.md` — parity statement: identical rule/labels on phone and AA (rule is surface-agnostic; only the layout container differs — Compose align vs Canvas anchor), consistent with the existing parity rule.
- Scope: both surfaces (AA + phone). Pure rendering/UI behavior; no native, JNI, or settings model changes.

Rollback path: revert the three call sites and the `StreetNameLabel` placement API stays additive; the specs' conditional wording covers both states.

## Superseded (2026-09-17) — street-name-host-views

This change's Android Auto deltas (top placement for `BOTTOM_CENTER`,
`isMapLabelSafe`/`setTripText` fallback) are SUPERSEDED by the in-flight
change `street-name-host-views`: routing moves the street name into the host
ETA card unconditionally, and free driving/browse/phone use a row rule (whole
bottom row → top, top row → bottom). The phone (Compose pill) deltas of this
change fold into that change's extended row rule wherever they overlap. The
remaining on-device tasks (5.3/5.4) verify behavior the superseding change
replaces; do not close them from the pre-refactor build.
