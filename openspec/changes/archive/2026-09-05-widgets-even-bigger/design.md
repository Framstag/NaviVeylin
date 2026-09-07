# Design: widgets-even-bigger

## Context

See `proposal.md` — Why. Current sizes: phone max-speed sign 56dp/5dp border/22sp digits (`SpeedWidget.kt`), phone compass 48dp layout/40dp visual (`CompassButton.kt`), AA rose + limit sign 48dp with 20sp digits (`SurfaceIndicators.kt`). All three widgets are pure size constants — no layout algorithm, data flow, or interaction change. Specs: `map-speed-widget`, `compass-button`, `auto-map-layout` deltas.

## Goals / Non-Goals

**Goals:**
- Bump max-speed sign + compass sizes on phone and AA to the new spec minimums.
- Keep the reserved-slot/placeholder footprint in sync with the visible sign so the stable-layout behavior (spec `map-speed-widget` — Stable widget layout) is preserved.

**Non-Goals:**
- No change to the speed badge (current-speed text) on either platform — only the max-speed sign grows.
- No change to other overlay buttons (menu, search, location options) — they stay 48dp.
- No interaction changes (compass press/long-press, overspeed rule, AA placement rules).

## Decisions

**D1 — Phone compass becomes larger than sibling overlay buttons (56dp/48dp vs 48dp/40dp).**
Rationale: the compass is the primary glanceable orientation widget; the prior "same size as other buttons" rule (spec `compass-button`) was a consistency constraint, not a usability requirement. Growing only the compass creates a deliberate visual hierarchy. Alternative considered: grow all overlay buttons uniformly — rejected, out of scope (user asked for compass + max speed only) and larger menu/search buttons add no glance value.
Implementation: `CompassButton.kt` — `.size(48.dp)` → `.size(56.dp)`, inner `Canvas` `40.dp` → `48.dp`; needle stays 70% of the visual (auto-scales), "N" label 9sp → 11sp to stay proportional.

**D2 — Phone sign grows with its placeholder together.**
The placeholder (`speedLimitPlaceholder`) and the visible sign share the same `56.dp` constant in `SpeedWidget.kt`; bump both to `64.dp` in the same edit so the reserved slot always matches the visible footprint (spec scenario "Reserved slot still matches"). Border 5dp → 6dp; digits `titleLarge` (22sp) → `headlineMedium` (28sp) via `speedLimitDigitsStyle()`.

**D3 — AA sizes are constants in `SurfaceIndicators.kt`; badge unchanged.**
`ROSE_DIAMETER_DP` 48f → 56f, `LIMIT_DIAMETER_DP` 48f → 56f, `LIMIT_RING_DP` 6f → 7f, `LIMIT_TEXT_DP` 20f → 24f. The AA speed badge (128×52, 20sp) stays — the spec delta only raises the sign minimums. Geometry math (`geometry()`) is size-agnostic (all derived from the constants), so no layout code changes. Alternative considered: bump badge text too — rejected, user scoped to max speed + compass.

**D4 — No shared size abstraction between phone and AA.**
Phone uses Compose `dp`/`sp` + Material typography; AA uses raw Canvas `Paint` constants. The two platforms already diverge (phone sign 64dp vs AA 56dp per spec). A shared constants file would couple unrelated rendering stacks for no behavioral gain. Alternative considered: unify — rejected, cross-variant parity is label-based, not pixel-based (see `cross-variant-ui-parity`).

## Risks / Trade-offs

- [Larger AA rose/sign may overlap the host's right-edge action strip or street-name label] → `SurfaceLayoutTest.kt` asserts no overlap; geometry keeps the same right margin and centering, only diameters grow; verify with the existing layout test.
- [Phone compass at 56dp may crowd the speed widget below it in the right view column] → column is bottom-anchored with fixed order (compass → speed widget → location options → zoom); the compass grows upward from its anchor, so the cluster height grows but the map stays visible; visual check on device.
- [28sp digits in a 64dp sign may overflow for 3-digit limits (e.g. 120)] → `headlineMedium` at 28sp fits 3 digits in 64dp (sign ≈ 2.3× text height); if a locale/limit combination overflows, the sign is a fixed circle — mitigation would be a smaller digit style, tracked as an open question, not blocking.

## Migration Plan

- Deploy: normal app update (phone + AA ship in the same APK/AABs). No data migration, no native change.
- Rollback: revert the three Kotlin files + test updates; sizes return to current values. Additive, non-breaking.

## Open Questions

- None blocking. (3-digit limit rendering inside the 64dp sign is verified by the existing `SpeedWidgetTest` width-stability coverage; if a real overflow appears on device, adjust the digit style — spec minimums are floors, not exact values.)
