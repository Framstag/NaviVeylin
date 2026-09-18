# Design — Street-name host views and row-based placement

## Context

See `proposal.md` for the Why. Current AA street-name rendering (verified in code):

- `StreetNameLabel` (pure Canvas pill) is drawn in the overlay drawers of `NavigationScreen` and `FreeDrivingScreen`; position anchors to the host's stable/visible-area rects (`min(bottoms)` / `max(tops)`), with `16 dp` margins and — navigation only — a `24 dp` bottom reserve.
- Navigation also gates a host-ETA-card fallback (`isMapLabelSafe` → `TravelEstimate.setTripText`) that in practice never fires on hosts that deliver a bottom-clearing area; `TOP` placement bypasses it by construction (design D3 of `street-name-overlay-avoid-bottom-anchor`).
- `MapScreen` (browse) has no street name, no lookup, no overlay drawer.
- Phone: `FreeDrivingStreetPill` (Compose) shows the pill only when no route is active; navigation shows the road inside the routing-status card (`NavigationStateOverlay`). Pill placement today: top only for the `BOTTOM_CENTER` preset, real-edge anchored (`statusBarsPadding()` + 16 dp top / 16 dp bottom) — the phone already has the "real-edge" property AA lacks.

Anchor grid (`core/VehicleAnchor.kt`): 15 presets, rows `TOP (fy=0.1)`, `MIDDLE (fy=0.5)`, `BOTTOM (fy=0.9)`; `DEFAULT = CENTER`. Per-screen presets: `routingAnchor`, `freeDrivingAnchor`; browse sets none (CENTER).

Constraints:

- `NavigationTemplate` is the only template for free driving that gives a full-bleed map (no content slot — `MapWithContentTemplate` showed a "Free driving" box, spec `auto/free-driving` pins this). It has no title either. The only host-rendered text slots are the ETA card (`TravelEstimate`) and the instruction panel (`RoutingInfo`).
- `RoutingInfo` has no free-text slot for the current road: `Step.road` is the next maneuver's *target* street; `cue` is the localized turn instruction. Fabricating the current street there would corrupt either semantics.
- Host chrome around the free-driving/browse maps is zero (no panel, no ETA card); the stable/visible-rect plumbing exists only to dodge the routing ETA card.

## Goals / Non-Goals

Goals:

- Routing: current street name always rendered in the host ETA card; never on the map surface.
- Free driving / browse: pill as the separate floating view, vertical position = opposite the vehicle anchor row, anchored to real surface edges with a small top padding when top-placed.
- Browse gains the street-name feature (lookup + pill) for the first time.
- Phone pill rule extended to the same row rule (parity with AA free driving).
- Host-geometry dependence removed from the street-name pill entirely.

Non-Goals:

- Changing the street-name *data sources* (route-way `currentRoadInfo` on route; throttled bearing-aware `getRoadAt` fallback off route) — unchanged, spec already pins them.
- Changing the compass/speed indicator anchoring (`SurfaceIndicators` keeps its `stableBounds` argument; untouched).
- Overhauling `auto-map-layout` or the browse content menu — the browse pill overlays the map only.
- New native code, JNI changes, settings/persistence changes.

## Decisions

### D1 — Routing: street name always in the host ETA card, never on the surface

Chosen: `buildTravelEstimate` sets `tripText = streetName` unconditionally; the overlay drawer loses the `StreetNameLabel` draw; `isMapLabelSafe`, `streetPlacement`, `streetNameOnSurface`, `bottomReserveDp`, and the conditional `tripTextFor` are deleted from `NavigationScreen`. Street-name change still triggers a template rebuild (the `invalidate()` on change becomes unconditional instead of `if (!streetNameOnSurface())`).

Alternatives:

- Status quo (surface pill + gated ETA-card fallback): host-dependent placement (observed ~70 % height), and the name rarely reaches the routing status view — the reported defect. Rejected.
- Synthesize the current road into the instruction panel (`RoutingInfo`): no clean API slot — `Step.road` is the target street of the upcoming maneuver; `cue` is the localized turn instruction. Rejected as semantics corruption.
- `setTripText` always: host positions the card, so the app cannot cover or be covered by it; matches phone behavior (`NavigationStateOverlay` road row). Chosen.

Threading/lifecycle: unchanged — `streetName` updates on the main thread from the state collector (route-way) or the `Dispatchers.Default` lookup job (off route); `invalidate()` schedules the template rebuild.

Risk: a host that renders a small/hidden ETA card could truncate trip text. → `setTripText` is the sanctioned supplementary-text API; text is short ("ref name", capped) and derived from the same data as before. Verify on AAOS emulator.

### D2 — Free driving: pill stays on the surface, placement by anchor row

Chosen: keep the pill (it is the only separate view this template allows) and derive placement from the vehicle anchor row:

| Anchor row (`fy`) | Pill |
|---|---|
| BOTTOM (0.9) | top edge |
| TOP (0.1) | bottom edge |
| MIDDLE (0.5) | bottom edge (today's default) |

Rationale behind the middle-row default: the vehicle sits mid-screen, the travel corridor (heading-up) is above it; the bottom edge is clear of the corridor and is where the phone shows the same pill — parity.

Alternatives:

- Switch free driving to `MapWithContentTemplate` with the street name as a content row: reserves screen space for the content slot and reintroduces the historical "Free driving" box this project deliberately removed (spec `auto/free-driving` pins the fullscreen `NavigationTemplate`). Rejected.
- Move to `MapTemplate` (deprecated) to use its `setTitle` bar: deprecated API + no equivalent on `NavigationTemplate`; browse would diverge from free driving. Rejected.
- Surface pill with row rule: no layout change, deterministic, testable. Chosen.

### D3 — Placement keyed to the anchor row, not live screen position

Chosen: `StreetNameLabel.placementFor(anchor)` → `TOP` when `anchor.fy == 0.9`, `BOTTOM` otherwise (covers top row and middle row together since both land bottom). Rationale: during follow the vehicle's screen position *is* the preset; during pan the pill staying on its side avoids flicker, and pan is a transient browse state.

Alternative: derive the live marker rect each frame and flip the pill when the vehicle crosses the vertical middle. Rejected: churn while panning, ties the pill to marker-pixel math, nondeterministic tests; the anchor row is the stable semantic input.

### D4 — Real-edge anchoring; host-rect math removed from the pill

Chosen: `StreetNameLabel.geometry` drops the stable/visible-rect parameters. `TOP` = `surfaceTop + 16 dp`; `BOTTOM` = `surfaceBottom − 16 dp − pillHeight`. Width cap (`MAX_WIDTH_DP`), ellipsize, styling, horizontal centering unchanged. The ~70 % artifact cannot occur — no host input in the vertical math.

Alternatives:

- Keep rect anchoring but clamp to a minimum top inset: still host-dependent (the 70 % case was *correct by construction*: the host really reserved that bottom region). Rejected.
- Keep rect math only for `NavigationScreen`: navigation no longer draws a pill at all; free driving/browse have no host chrome top/bottom. The rect plumbing would exist for nobody. Rejected.

Free driving and browse pass no `stableBounds`/`visibleBounds` for the pill; indicators keep their own rect usage.

**2026-09-17 correction (on-device finding, AAOS emulator `sdk_gcar_dd`):** this decision is superseded for the AA pill. The premise "free driving/browse have zero chrome" is falsified by the verification device: the emulator's *system* chrome — top status bar (~56 px at 1080x600 @ 120 dpi) and bottom task bar (~70 px) — covers the surface extremes, and the host reports those bands through the stable area exactly like the bottom band that already feeds the follow-anchor clamp. Raw real-surface-edge anchoring puts the TOP-placed pill entirely under the top band (bottom-row anchors) and the BOTTOM-placed pill entirely under the bottom band (top/middle-row anchors): no pill visible in any configuration. The follow anchor and the compass/speed indicators (both inset- or rect-aware) are visible; only the pill — the one overlay that lost its rect/inset input — is hidden. See D8.

### D5 — Browse (MapScreen): add street-name lookup + pill

Chosen: `MapScreen` gains the same machinery as `FreeDrivingScreen`: `StreetNameUpdater` throttle, `resolveStreetName(pos)` running `getRoadAt(lat, lon, bearing)` on `Dispatchers.Default` via a `streetJob`, `"ref name"` display text, and a pill draw in a new minimal overlay drawer (browse currently has none — the pill-only overlay is additive). Placement via the browse anchor (see D7).

Alternatives:

- No browse street name: leaves the reported asymmetry (browse = only mode without the street). Rejected.
- Reuse a shared lookup component across all three screens (extract `StreetNameLookup`): attractive, but screens already duplicate the pattern and each has different state/lifecycle; extraction is a refactor beyond this change's scope. Noted as follow-up.

Threading/lifecycle: mirrors `FreeDrivingScreen` — `@Volatile streetName`, `Dispatchers.Default` lookup job cancelled with the screen scope, throttle guards reentry (`shouldGeocode` + `streetJob?.isActive`).

### D6 — Phone parity: pill rule extended to the anchor row

Chosen: `FreeDrivingStreetPill` computes `atTop = anchor.fy == 0.9` (covers `BOTTOM_*`), `atBottom = anchor.fy == 0.1` (new mirror case, `TOP_*` presets), else bottom. Top keeps `Modifier.statusBarsPadding()` + inner 16 dp (camera/status-bar clearance, unchanged); bottom keeps 16 dp. `onPillInset` semantics unchanged (bottom pill reserves map space below the follow anchor; top pill zeroes the bottom inset — keyed on `pillAtTop` already).

Alternative: leave phone at the single `BOTTOM_CENTER` rule; parity with the new AA row rule would drift for `BOTTOM_LEFT`/`BOTTOM_RIGHT` (pill bottom-center under a bottom-left marker grazes the icon on wide names) and `TOP_*`. Extending is 2 lines and keeps spec/behavior parity. Chosen.

### D7 — Browse anchor

Chosen (recommended default, confirm during review): browse follows with the shared `freeDrivingAnchor` setting (`VehicleAnchorPosition.fromId(settings.freeDrivingAnchorId)`), so the vehicle-frame and the pill rule behave like free driving. `MapScreen` already reads `settingsProvider`; one more field. Alternative considered: fixed CENTER → pill always bottom; rejected as inconsistent with the other follow screens when the user configured a bottom-row preset. (Open in the proposal as Q2 — flagged for review.)

### D8 — AA pill anchors to the guaranteed-visible band (host-inset-aware real edge) [2026-09-17]

**The problem (measured):** with the row rule in place and the AAOS emulator's top status band (~56 px) + bottom task band (~70 px) covering the surface extremes, the pill is invisible in *every* placement: TOP (bottom-row anchors) = `y 12..51` at 120 dpi, entirely inside the top band `0..56`; BOTTOM (top/middle-row anchors) = `y 549..588`, entirely inside the bottom band `530..600`. The host reports these bands through the stable area — the same input that already clamps the follow anchor out of the bottom band (`hostBottomInsetPx`, design `anchor-per-surface-visible-area` AA vertical clamp).

**Chosen:** the AA pill anchors to the edge of the *guaranteed-visible band*, not the raw surface edge and not the transient visible rect:

- Both screens derive `bottomInset = surfaceHeight − stableArea.bottom` (0 when the stable area is empty or spans the full surface — the D4 behavior on hosts without chrome). `hostBottomInsetPx()` becomes `hostInsetsPx()` returning both; `AutoMapRenderer` gains `setHostTopInset(px)` mirroring `setHostBottomInset` so pill and follow anchor resolve the same band.
- `StreetNameLabel.geometry`/`draw` gain `topInset`/`bottomInset` (both default 0): `TOP` = `topInset + TOP_MARGIN_DP`; `BOTTOM` = `surfaceHeight − bottomInset − BOTTOM_MARGIN_DP − height`. Row rule (D3) unchanged; horizontal centering, width cap, ellipsize unchanged.
- Zero insets → byte-identical geometry to today: no behavior change on hosts whose stable area spans the full surface (the design's original target class).

**2026-09-17 revision (on-device padding report):** the TOP edge uses the *currently-visible* top, not the stable top: `topInset = HostInsets.topInset(visibleArea, stableArea)` (visible top, falling back to the stable top, then 0). The AAOS emulator's stable area over-reserves a top band the host never draws (stable top 138 px) while the real coverage — the visible-area top — collapses to ~66 px once the host's floating header slides away; anchoring to the stable top left the label ~87 px below the visible chrome (“spacing too high”). The visible top tracks the real coverage with no flicker on the free-driving/browse screens (no animated host chrome there; navigation draws no pill). The bottom edge stays stable-based (the reported band matches the real task bar). `TOP_MARGIN_DP` reduced 16 → 8 dp.

Why not the raw edge: measured invisible on the verification device; on a ~236-dpi head unit the same bands would only partially cover the pill (peeking sliver), i.e. the same defect class at any density. Why not the stable top for the pill's TOP edge: it over-reserves — the AAOS emulator's stable area keeps a 138 px top band that the host never actually draws once its floating header collapses, leaving the label ~87 px below the visible chrome (the 2026-09-17 padding report); the pill therefore follows the currently-visible top (D8 revision), which is static on the free-driving/browse screens (no animated host chrome; navigation draws no pill) — the flicker concern applies to the bottom/anchor paths, which keep the stable area. Why not a hardcoded bar height: density- and host-specific, and a second encoding of host geometry.

The D4 "~70 %" concern (a host-reserved band makes the pill look mid-screen) does not apply to the band rule: a host-reserved band is real chrome and the pill must stay inside the guaranteed-visible band; D4 already conceded this is *correct by construction* for the anchor clamp, and free driving/browse pass no ETA card, so their stable band shrinks only under genuine chrome.

**Follow-up (out of scope here; same plumbing, owned by `anchor-per-surface-visible-area`):** the follow anchor has no *top* clamp — a top-row preset (fy = 0.1) puts the vehicle marker under the top status band on this device class. `hostTopInset` makes the clamp a ~2-line addition to `resolvedFollowAnchor`.

## Risks / Trade-offs

- [ETA-card trip text truncated on some hosts] → Mitigation: D1 — sanctioned API, short text, verify on AAOS emulator (`adb logcat -s NaviVeylin` for template rebuilds).
- [The verified-on-emulator assumption "no host panel text around the free-driving map" missed the *system* chrome] → Falsified 2026-09-17: the AAOS emulator's status/task bars cover the surface extremes (top ~56 px, bottom ~70 px at 1080x600 @ 120 dpi) and the pill was invisible in every placement. Fixed by D8 (band anchoring); re-verified on-device in task 8.5.
- [Browse gains a surface overlay where none existed] → Draw-only (pill), no interactive elements — the "host never forwards surface gestures" constraint (spec `auto-map-layout`) is not touched.
- [Row rule differs from the user's live-position mental model] → Flagged: Q1 in the proposal; rule is deterministic and matches the preset grid; flip is an `if` change if review disagrees.
- [In-flight `street-name-overlay-avoid-bottom-anchor` overlap] → This change supersedes its AA deltas; its phone deltas fold in via D6. Apply order: finish/close that change's remaining tasks or mark superseded before this applies, so the `StreetNameLabel` refactor (D4) does not conflict mid-file.

## Migration Plan

- Code: edit `StreetNameLabel` (placement + geometry, D3/D4) → `NavigationScreen` (remove pill draw + gating, unconditional trip text, D1) → `FreeDrivingScreen` (D2) → `MapScreen` (D5) → `StreetNamePill` (D6) → **D8 inset-aware band anchoring** (`AutoMapRenderer.setHostTopInset` + `hostInsetsPx()` in both AA screens + `StreetNameLabel` top/bottom insets). All steps compile independently; no data or settings migration. See tasks §8.
- Specs: delta files for `auto/navigation-view`, `auto/free-driving`, `current-road-info`, new `auto/browse` in this change (pending Q1–Q3 confirmation in review).
- Rollback: revert the four AA call sites + phone pill; specs reverted by removing deltas. No native/JNI components involved.
- Verification: `:auto` unit tests (`StreetNameLabelTest`, `NavigationScreenTest`, new browse/row tests) + `:app` unit tests via run-tests skill; debug builds via build-app skill; on-device (AAOS emulator): navigate → street name in ETA card, no surface pill; free drive with `BOTTOM_CENTER` → pill at top with ~16 dp padding; `BOTTOM_LEFT`/`BOTTOM_RIGHT` → top too; `TOP_*`/default → bottom; browse → pill appears with the same rule; logcat tag `NaviVeylin`.

## Open Questions

Deferrable without changing specs/approach:

- Reported ~70 % placement — which mode and host class ("head unit vs AAOS emulator") it was seen on. Doesn't change the design (real-edge anchoring removes the input that caused it); recorded for the verification task only.
