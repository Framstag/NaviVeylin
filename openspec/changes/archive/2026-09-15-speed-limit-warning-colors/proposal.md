## Why

The overspeed state of the speed badge is hard to read at a glance: on the phone the red warning text sits on the light overlay card, on Android Auto it sits on the near-black surface badge (red on dark). A solid red background with white bold text — the standard overspeed idiom used by automotive HUDs and nav apps — reads instantly without needing to separate text color from card color. The semi-transparent overlay treatment stays in both variants.

## What Changes

- **Phone badge (map speed widget)**: over the limit, the badge container changes from the theme surface card to the theme error color at the same 0.92 alpha and 12dp rounded corners; the text changes from error-red to white. Normal state unchanged: light surface card, dark `onSurface` text.
- **Android Auto badge (navigation + free driving)**: over the limit, the badge background changes from `0xCC1C1B1F` (near-black, 80% alpha) to red-600 `0xCCE53935` (same 80% alpha so the semi-transparent property is retained); the text stays white (it already is white in the normal state — only the background flips to red in the warning state). Normal state unchanged.
- **Spec amendments** (requirement-level behavior changes):
  - `map-speed-widget`: the "standard overlay card container" rule gains an overspeed exception (red background replaces the surface card while over the limit); the overspeed requirement is restated as red background + white foreground instead of warning-colored text on the standard card.
  - `auto-map-layout`: the "Limit exceeded warning" scenario is made concrete — warning color = red background with white text on the speed badge.
  - `auto/free-driving`: the free-driving speed readout inherits the same overspeed visual (scenario added to the "Current driving speed shown" requirement).
- **Guideline update** (`guidelines/UI.md` §8): the speed-badge card rule and its contrast rationale are rewritten to reflect the overspeed state replacing the card with the red warning background.
- **Test updates**: `SpeedWidgetTest` gains assertions that the over-limit state renders red background + white text; normal state assertions are preserved.

Additive — no API, permission, or native/submodule changes; existing APKs unaffected.

## Capabilities

### New Capabilities

None — the behavior change extends existing capabilities; no new surface is introduced.

### Modified Capabilities

- `map-speed-widget`: The phone speed badge gains an overspeed color state — over the limit, the badge SHALL show red background (theme error at 0.92 alpha, 12dp rounded) with white text, replacing the standard overlay card; the standard-card rule SHALL not apply while over the limit. Normal-state colors are unchanged.
- `auto-map-layout`: The Android Auto speed badge's "limit exceeded warning" SHALL be a red badge background (red-600) at the standard semi-transparent alpha with white text, replacing the current red-text-on-dark-background warning; normal-state colors unchanged.
- `auto/free-driving`: The free-driving speed readout (same badge component as navigation) SHALL use the identical overspeed visual — red background with white text — so free driving and navigation stay visually consistent.

## Impact

| Area | Files |
|------|-------|
| Kotlin – phone widget | `app/src/main/java/com/naviveylin/ui/map/SpeedWidget.kt` — `speedBadgeContainerColor(overLimit)` and `speedBadgeTextColor(overLimit)` signatures/rules, badge composition |
| Kotlin – AA indicators | `auto/src/main/java/com/naviveylin/auto/SurfaceIndicators.kt` — `BADGE_WARN` reuse as background (semi-transparent red), `drawSpeedBadge` fill/foreground selection |
| Specs | `openspec/specs/map-speed-widget/spec.md`, `openspec/specs/auto-map-layout/spec.md`, `openspec/specs/auto/free-driving/spec.md` |
| Guideline | `guidelines/UI.md` §8 (phone navigation overlay sizing / speed badge card rule) |
| Tests | `app/src/test/java/com/naviveylin/ui/map/SpeedWidgetTest.kt` (color-state assertions); AA indicator color check via existing indicator test surface if any (instrumented) |

- **Additive**, no breaking API changes; rollback = revert the two Kotlin edits and the spec/guideline deltas (existing APKs unaffected).
- Scope: **general** — both phone and Android Auto (navigation + free driving) share the visual; same change, no asymmetry.
- Dependencies: none new.

Threshold unchanged: the over-limit predicate (`isSpeedOverLimit` phone; displayed-value comparison AA) is untouched — this change only replaces the rendering of the already-detected state. The known phone/AA threshold drift (5+ km/h vs rounded exceed) is out of scope and tracked separately.
