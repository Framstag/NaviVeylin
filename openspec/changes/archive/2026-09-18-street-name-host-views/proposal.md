# Proposal

## Motivation

Street-name presentation on Android Auto is inconsistent across the three map modes and, in routing, fights the host UI:

- **Routing** draws the current street name as a pill **on the map surface** with a host-ETA-card fallback that only fires when the host geometry is "unsafe" — so in normal operation the name never appears in the host routing status view. The phone app, by contrast, always shows the current road inside its routing-status card during navigation (parity rule in `guidelines/UI.md`).
- **Free driving** places the pill by a single-anchor rule (top only when the anchor preset is `BOTTOM_CENTER`) and anchors it to the host's stable/visible-area rects. On hosts that reserve the bottom ~30% of the surface, the pill lands at ~70% of the screen height — floating in the corridor ahead of the vehicle — instead of hugging a real screen edge.
- **Browse** (`MapScreen`) shows no street name at all.

Goal: street name placement becomes deterministic and mode-appropriate:

```
 ROUTING       -> street name ALWAYS in the host ETA card (TravelEstimate.setTripText);
                  nothing drawn on the map surface.
 FREE DRIVING  -> pill (the only separate view available: NavigationTemplate has no
                  content/title slot) anchored to the real surface edges opposite the
                  vehicle row; small top padding when top-placed.
 BROWSE        -> pill added (same rule, bearing-aware lookup).
 PHONE         -> free-driving pill rule extended to the same row rule (parity).
```

## What Changes

1. **Routing (NavigationScreen)**: the current street name moves off the map surface into the host travel-estimate card via `setTripText`, unconditionally. Remove the surface draw, `isMapLabelSafe`, `streetPlacement`, `streetNameOnSurface`, `bottomReserveDp`, and the conditional `tripTextFor` gating. Data sources stay unchanged: route-way `currentRoadInfo` when on route, throttled bearing-aware `getRoadAt` fallback when off route.
2. **Free driving (FreeDrivingScreen)**: pill placement keyed to the vehicle anchor row — bottom row (`fy=0.9`) → top edge, top row (`fy=0.1`) → bottom edge, middle row (`fy=0.5`) → bottom (today's default). Anchoring uses the real surface top/bottom with fixed paddings (16 dp top margin, mirror at bottom), dropping the stable/visible-rect math that produced mid-screen placement.
3. **Browse (MapScreen)**: add the same pill — throttled bearing-aware `getRoadAt` at the GPS position, `"ref name"` display text, same row rule — drawn on the browse surface (additive; browse currently has no overlays and no street lookup).
4. **Phone (StreetNamePill / FreeDrivingStreetPill)**: extend the top/bottom rule to the full row rule for parity; phone already uses real paddings (`statusBarsPadding()` + 16 dp top / 16 dp bottom).

> **2026-09-17 finding (verify-time correction, design D8):** item 2's real-surface-edge anchoring is falsified on the AAOS emulator (`sdk_gcar_dd`): the device's *system* chrome — top status bar (~56 px) and bottom task bar (~70 px at 1080x600 @ 120 dpi) — covers the surface extremes, so the pill is invisible in every placement (both band-anchored placements land entirely inside a band). The AA pill gains inset-aware band anchoring: it anchors to the edge of the guaranteed-visible band (stable area top/bottom, falling back to the real surface edge when no insets are reported) instead of the raw surface edge. Phone placement (item 4) is unaffected — it already clears the status-bar/camera inset. Specs `auto/free-driving` + `auto/browse` and tasks §8 updated accordingly.

## Capabilities

### New Capabilities

- `auto/browse`: street name shown on the browse map — current street derived from a bearing-aware road lookup at the GPS position, displayed as a surface pill opposite the vehicle row, matching the free-driving rule.

### Modified Capabilities

- `auto/free-driving`: the "Current street name shown" requirement changes from "centered at the bottom of the view, within the host-guaranteed area" to "opposite the vehicle row (bottom-row anchor → top, top-row anchor → bottom, middle → bottom), anchored to the real surface edges with a small top padding when top-placed".
- `auto/navigation-view`: the "Current street name shown during navigation" requirement changes from "drawn on the map surface (ETA-card fallback when unsafe)" to "always rendered inside the host travel-estimate card via `setTripText`; never drawn on the map surface".
- `current-road-info`: the phone free-driving label requirement extends the top-placement rule from the `BOTTOM_CENTER` preset to the full row rule (bottom row → top, top row → bottom, middle → bottom), keeping the status-bar/camera clearance.

## Impact

### Affected code

- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — remove surface street-name drawing + geometry gating; always set trip text on the travel estimate.
- `auto/src/main/java/com/naviveylin/auto/StreetNameLabel.kt` — placement rule by anchor row; geometry anchored to real surface edges (drop/hardcode the stable/visible-rect parameters for the vertical position; keep width cap + ellipsize).
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — feed the new placement; drop the rect math.
- `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` — add street-name lookup (StreetNameUpdater + `getRoadAt`) and the pill draw.
- `app/src/main/java/com/naviveylin/ui/map/StreetNamePill.kt` and `MapCanvasScreen.kt` — row-based placement for the phone pill.
- Tests: `StreetNameLabelTest.kt`, `NavigationScreenTest.kt`, phone pill tests; new tests for browse lookup and the row rule.

### Guides / specs

- `guidelines/UI.md` — refresh the street-name parity paragraph (AA routing via ETA card, AA browse/free-driving + phone via row-rule pill).
- Specs above get delta files in this change.
- Native/JNI: **not applicable** — pure UI/template changes; no libosmscout or JNI bridge changes. The `getRoadAt` lookup already exists and is unchanged.

### Additive vs breaking

- Routing: breaking (behavior + spec wording change) — the street name visibly moves from the map surface to the host ETA card. Rollback: restore the pill draw + gating (revert the NavigationScreen/StreetNameLabel changes).
- Free driving / browse / phone: additive placement changes; rollback = revert the placement call sites.

### Supersedes

- This change supersedes the Android Auto parts of in-flight change `street-name-overlay-avoid-bottom-anchor` (11/13 tasks done): that change's AA label-placement deltas (top placement for `BOTTOM_CENTER`, `isMapLabelSafe`/`setTripText` fallback) are replaced by the ETA-card-always design and the row rule. The phone (Compose pill) deltas of that change are folded into the extended row rule here wherever they overlap.

## Open questions (recommended defaults in brackets)

1. **Row rule vs live position**: placement flips per anchor row (recommended, deterministic, matches the preset grid) or per the vehicle's *actual* screen position (flips mid-pan)? Default: row rule.
2. **Browse anchor**: browse follows with which preset? Default: reuse the shared `freeDrivingAnchor` setting; alternative: fixed center (pill always bottom).
3. **"Browse mode"** is the root `MapScreen` (content slot = menu/search) — to be confirmed against the user's intent.
4. **70%-height observation**: mode + host where it was seen (head unit / AAOS emulator) — recorded as an on-device verification task rather than a design input; root cause is the stable/visible-rect anchoring, which this change removes.
