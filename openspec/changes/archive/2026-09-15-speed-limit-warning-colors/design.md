## Context

Two independent render paths draw the speed badge (see proposal.md — Why for motivation):

- **Phone** — `app/src/main/java/com/naviveylin/ui/map/SpeedWidget.kt`, Compose. Normal state: theme `surface` at 0.92 alpha, 12dp rounded; text `onSurface`. Over limit: same card, text `error` (red).
- **Android Auto** — `auto/src/main/java/com/naviveylin/auto/SurfaceIndicators.kt`, Canvas paints (no Compose theme available). Normal: `0xCC1C1B1F` (near-black, 0xCC ≈ 80 % alpha), text white. Over limit: same dark background, text `0xFFE53935` (red-600).

Both already compute an over-limit predicate; only the *rendering* of the detected state changes. `drawSpeedBadge` is shared by navigation and free driving, so the AA flip covers both views automatically.

**Constraint discovered during design**: the phone uses Material 3 theming. M3 dark-scheme `error` is a light pink (`#F2B8B5`) — white text on it is unreadable (≈ 1.9:1). A naive "theme error background" would break dark mode. The warning state therefore needs a fixed red that works in both color schemes.

## Goals / Non-Goals

**Goals:**
- One warning visual across phone, AA navigation, and AA free driving: red badge background + white bold text.
- Semi-transparency retained at the existing alpha values (0.92 phone, 0xCC AA).
- Normal state pixel-identical to today.
- Keep color resolvers small, pure, and unit-testable (existing pattern: `speedBadgeTextColor` / `speedBadgeContainerColor` exposed for tests).

**Non-Goals:**
- Changing the over-limit threshold or closing the known phone (5+ km/h) vs AA (displayed rounded exceed) drift — separate change, documented in proposal.md.
- Darkening the warning red for extra contrast margin — deferred to apply-time QA (see Risks).
- Any new state, threading, or lifecycle: colors are resolved at composition (phone) and at draw time on the existing AA surface-renderer path; no component lifecycle changes.

## Decisions

### 1. Warning red = fixed `#E53935` on the phone too (not theme error)

**Chosen**: `Color(0xFFE53935)` — the exact red already used for the phone's limit-sign ring (`SpeedWidget.kt` speedLimitSign border) and the AA badge warning/rose north. Apply at 0.92 alpha / 12dp rounded on the phone, 0xCC alpha / same rect on AA.

Rationale: white on `#E53935` ≈ 3.1:1 — meets WCAG AA large-text (≥ 3:1); badge text is ≥ 24sp bold (phone) / 20sp bold (AA), both large. A fixed red is the only option satisfying "white foreground" in *both* color schemes, because theme `error` is light in dark mode. It also matches the sign-ring red, so "over limit" and the limit sign share one visual language, and it is pixel-identical to the AA warning hue the app already ships.

**Alternative A — theme `error` background + `onError` text**: theme-correct pair (light mode: `#B3261E` + white; dark mode: `#F2B8B5` + `#601410`). Rejected: dark mode renders dark text on a light-red background — not the "white foreground" requested, and visually inconsistent with AA.

**Alternative B — theme `error` background + white text**: rejected above — unreadable in dark mode (white on `#F2B8B5`).

**Alternative C — keep red text, add a red background tint/ring instead**: rejected — user explicitly wants filled red background + white text; a ring does not give the at-a-glance pop.

Trade-off of the fixed red: theme-unaware by design in exactly one state. Acceptable — the normal-state card stays theme `surface`; only the alert state is pinned, same as AA today. The old UI.md rationale ("NEVER a fixed dark color… guarantees the overspeed warning color contrasts with the badge background") concerned the *card* under red *text*; it no longer applies to the alert fill and is rewritten in this change.

### 2. Keep existing alpha for the warning fill

**Chosen**: phone `0.92f` on the red (same as normal card), AA `0xCC` (same 0xCC as normal badge). "Semi-transparent effect stays" per requirement.

**Alternative**: full-opacity red for a stronger alert. Rejected — user requirement; additionally, 0xCC/0.92 red over the map is near-solid while keeping map context visible, and white text keeps its edge.

### 3. Phone API: parametrize the container by over-limit

**Chosen**: change `speedBadgeContainerColor()` → `speedBadgeContainerColor(overLimit: Boolean)` returning `Color(0xFFE53935).copy(alpha = 0.92f)` when over, else `surface.copy(alpha = 0.92f)`; `speedBadgeTextColor(overLimit)` returns `Color.White` when over, else `onSurface`. `SpeedWidget` passes the existing `isSpeedOverLimit(...)` result to both. Both resolvers stay `@Composable`, pure, exposed for tests — mirrors the current seam, minimal diff.

**Alternative**: overlay a red scrim behind the badge (transparent→red gradient). Rejected — extra compositing, no benefit, harder to assert in tests.

### 4. AA: warning red becomes the background

**Chosen**: in `drawSpeedBadge`, `bg.color = if (overLimit) BADGE_WARN_BG else BADGE_BG` where `BADGE_WARN_BG = 0xCCE53935` (reuses the existing red constant's hue at the badge's own alpha); text paint always `BADGE_FG` (white). `BADGE_WARN` (currently a text accent) is repurposed/replaced by the background constant. Shape and geometry untouched; free driving inherits via the shared function — no free-driving-specific change needed.

**Alternative**: separate free-driving drawing path with its own colors. Rejected — duplicates logic; the shared badge is the point.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| White on `#E53935` is 3.1:1 — below 4.5:1 normal-text AA | Badge text is bold and large (≥ 20sp AA, ≥ 24sp phone) → WCAG large-text pass; if device QA disagrees, darken both platforms to `#D32F2F` (4.1:1) in one line each — pure apply-time tweak, no spec/design churn |
| Dark-mode phone: fixed red clashes with theme in one state | Deliberate; normal-state card remains theme `surface`; alert states are conventionally non-theme (cf. AA). Verified on-device in both schemes |
| AA Canvas has no direct unit-test seam | SurfaceIndicators is verified on emulator/head unit (per guidelines Build.md) at over-limit with fixed `maxKmH`; phone behavior covered by Compose unit tests |
| Test churn in `SpeedWidgetTest.badgeTextColorDarkOnLightCard` | Normal-state assertions (dark text on light card) kept verbatim; over-limit assertions extended to container red + white text via the existing `composeRule` + test-tag pattern |

## Migration Plan

No data, preference, or API migration. Rollback = revert `SpeedWidget.kt` + `SurfaceIndicators.kt` and drop the spec deltas; existing APKs unaffected (additive). Specs `map-speed-widget`, `auto-map-layout`, `auto/free-driving` and `guidelines/UI.md` §8 updated in the same change per the config rule (a change that supersedes a guideline updates it).

Verification:
- Unit: `SpeedWidgetTest` — extended color-state tests; existing structure/placement tests must stay green.
- On-device: phone light + dark scheme over-limit screenshot (red fill visible, white text readable); AA emulator — navigation with over-limit fix and free driving, both show the red badge; normal state visually unchanged.
- Threshold behavior is unchanged by design (`isSpeedOverLimit` phone; rounded-value comparison AA) — existing threshold tests remain as regression guard.

## Open Questions

None blocking. The only deferred item is the optional red darkening risk column — decided during apply from on-device QA, without spec impact.
