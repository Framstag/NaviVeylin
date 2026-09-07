# Design: Enlarge phone navigation overlays for driver-seat readability

See proposal.md for motivation and locked sizes. Requirements live in `specs/map-speed-widget/spec.md` and `specs/next-turn-overlay/spec.md`.

## Context

Three phone map overlays need bigger, more readable typography:
- `SpeedWidget.kt` — speed badge (16sp text on a fixed `0xCC1C1B1F` near-black box, theme-unaware) + 40dp round max-speed sign; badge is the ONLY overlay not using the standard card container.
- `NextTurnOverlay.kt` — next-turn card (distance 24sp `headlineSmall`, description/destination 18sp, next-next 16sp; arrows 48/28dp).

Every other overlay (turn card, routing status `NavigationStateOverlay`) uses `MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)` with a 12dp rounded shape. In light theme the fixed near-black badge makes the dark-red overspeed text low-contrast — the reported "hard to read above limit".

## Goals / Non-Goals

**Goals**
- All three complaints fixed: current speed (badge), turn instruction text, speed limit sign.
- Badge adopts the standard overlay card container so contrast works in both color schemes.
- Sizes expressed as spec minimums; implementation uses exact values (>= the minimums).

**Non-Goals**
- No behavior changes: overspeed rule (+5 km/h), hidden-without-speed, width stability, sign-slot reservation, wrap/ellipsis behavior, labels.
- No Android Auto changes — car surfaces have their own template scale (`SurfaceIndicators`); `cross-variant-ui-parity` is labels/hierarchy, not pixels.
- No changes to the routing status card or other overlays.

## Decisions

### D1 — Badge container: standard overlay card

**Chosen: `surface.copy(alpha = 0.92f)` + `RoundedCornerShape(12.dp)`**, same as the turn card and routing status. The badge keeps `contentAlignment = Center` and its padding grows with the larger text.

| Alternative | Assessment |
|---|---|
| **B: keep dark badge, switch overspeed text to a brighter error variant** | Fixes light-theme contrast but the badge stays invisible on dark maps (dark-on-dark) and still breaks the "same as other overlays" consistency the user asked for. |
| **C: theme-aware dark scrim color** | Still a distinct slab, not the card look; more code for marginal benefit. |

### D2 — Exact sizes (phone)

Chosen values (all >= spec minimums, using Material typography so weight/line-height stay consistent):

| element | style | value |
|---|---|---|
| badge speed text | `headlineSmall` (24sp) bold | was `titleMedium` 16sp |
| badge padding | `12.dp` horizontal / `6.dp` vertical | unchanged shell, text grows |
| limit sign circle | 56dp, border 5dp, `titleLarge` (22sp) bold digits | was 40dp/4dp/16sp |
| sign placeholder slot | 56dp footprint | was 40dp — must match new sign (spec: reserved slot same footprint) |
| turn distance | `headlineLarge` (32sp) bold | was 24sp |
| turn description/destination | `titleLarge` (22sp) | was 18sp |
| turn arrow | 64dp | was 48dp |
| next-next text | `titleMedium` (20sp? titleMedium is 16 — use `copy(fontSize = 20.sp)`) | was 16sp |
| next-next arrow | 36dp | was 28dp |
| next-next distance | 20sp | was 16sp |

Font-sizing approach: keep the existing `style = typography.X.copy(fontSize = N.sp)` pattern — matches the codebase, no new type system.

### D3 — Spec contract = minimums, implementation = exact values

Specs say "at least X sp" so future growth does not invalidate the spec; implementation locks the exact values in D2. Risk: drift between spec minimum and code value → mitigated by tests asserting the exact implemented sizes (which are >= minimums) and an on-device check (D5).

### D4 — No AA parity change

Car surfaces size text via the host template (`SurfaceIndicators`, `NavigationHintsOverlay` in `:auto`). Labels unchanged. No `:auto` file touched.

## Threading / Lifecycle

- Pure Compose rendering — all changes are static layout/typography on the main-thread composition. No new state, no dispatchers, no lifecycle changes.
- `SpeedWidget` signature unchanged (params `currentSpeedKmH`, `maxSpeedKmH`, modifiers) — callers (`MapCanvasScreen`, tests) untouched.

## Risks / Trade-offs

- [Bigger turn card covers more map] → Card is top-anchored, full-width; ~40% taller. NextTurnOverlay padding (12dp) and `maxLines = 2` keep it bounded; on-device check confirms map visibility (existing turn-overlay behavior already accepts the top bar).
- [Bigger badge/sign overflow the right widget column in nav mode] → Column is `TopEnd`-anchored, bottom-padded above the routing status; 56dp sign + larger badge fit, but verify in landscape + portrait on device (task 6.x).
- [`MAX_SPEED_TEXT` "999 km/h" reservation no longer bounds the badge] → Text at 24sp bold is wider; the reservation still prevents resize-on-change; verify width-stability test still passes (it asserts equality, not a specific width).
- [Sign placeholder footprint change] → Placeholder Box must grow with the sign (`size(56.dp)`), otherwise badge position shifts; covered by the existing slot-reservation test with the new size.

## Verification

- **Unit (Compose, Robolectric)**:
  - `SpeedWidgetTest` — extend: badge container assertion (background/semantics or testTag presence is insufficient — assert rendered background color via captureToImage OR add a testTag and assert no longer dark? Practical: assert font size of badge text >= 24sp and sign size == 56dp via `fetchSemanticsNode()`, and placeholder == 56dp). Keep existing cases (width stability, overspeed color rule, hidden state) green.
  - New `NextTurnOverlayTest` (Compose): font sizes of distance/description/next-next >= 32/22/20sp; icon sizes >= 64/36dp; wrap/ellipsis still applied on long text.
  - Full `./gradlew test` regression (mobile + automotive).
- **On-device (phone)**: navigate with realistic instruction set — verify distance/description readable at arm's length (driver-seat check), badge visible and readable on light map + dark map, sign legible, widget column not clipped in portrait + landscape, routing-status interaction unchanged (button not covered — already fixed).
