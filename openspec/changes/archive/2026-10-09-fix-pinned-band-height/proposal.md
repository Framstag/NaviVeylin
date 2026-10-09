# Proposal

## Why

Root cause: the phone card hands its pinned action band a **fixed** reservation instead of the band's own
height — `RoutePanel.kt:767` capped the scrolling content at `heightIn(max = cardCapDp - ACTIONS_BAND_DP)`
with `ACTIONS_BAND_DP = 120f` (`:115`) — so once the system font scale grows the band's content past that
constant, the band receives less height than it needs and its last child, the labelled End action, is
squeezed (measured on the host: 42.67 dp where its own content needs 53.33 dp; `TODO.md` §138 measured the
same shortfall on the AVD at font scale 2.0, where the action left the card and reached no UI dump at all).

Evidence: `com.naviveylin.ui.route.RoutePanelActionBandScaleTest#the labelled End action keeps its tap target inside the card at font scale 2` fails on HEAD — XML `tests="1" failures="1"` (`java.lang.AssertionError: Actual height is 42.666687.dp, expected at least 48.0.dp (tolerance: 0.5.dp)`), `timestamp="2026-10-09T17:08:46.480Z"`.

Repro: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.route.RoutePanelActionBandScaleTest"` (window `w411dp-h891dp-420dpi`, font scale 2.0 through `LocalDensity`, max card on a calculated route). Measured geometry, HEAD: card `[489.9, 890.67]` dp (= its 45 % cap of `[0, 890.67]`), action row `[770.67, 824.0]`, labelled End action `[824.0, 866.67]` = **42.67 dp**. After the fix: card `[489.9, 890.67]` (unchanged cap), band `[752.0, 890.67]` = 138.67 dp, End action `[805.33, 858.67]` = **53.33 dp** — the band grew by the 18.67 dp it was short, and the scrolling step list yielded exactly that amount instead. The case prints each measurement (`BandGeometry …`) into the JUnit XML's `system-out` before it asserts, so the rows above are checkable without a probe: `app/build/test-results/testMobileDebugUnitTest/TEST-com.naviveylin.ui.route.RoutePanelActionBandScaleTest.xml` (round-2 gate ts `2026-10-09T17:48:01.013Z`, automotive `17:45:52.997Z`) carries the after rows and the font-scale-1.0 row. The pre-fix row is **not retained**: `tasks.md` 4.1's mutation run printed it into its own red XML (ts `2026-10-09T17:32:12.243Z`), which the later green runs overwrote, and Gradle copies no test stdout into its console log — `/tmp/loop-138-fix1-revert.log` keeps that run's failure text (`Actual height is 42.666687.dp`, BUILD FAILED in 57s) only — so the row above stands as recorded prose from that run, re-creatable only by re-running the mutation.

Spec: `route-planning-session` / Planning card content and its pinned actions (its pinned actions "SHALL stay reachable without scrolling")   Guideline: `guidelines/UI.md` §7 (phone layout and the pinned action band), §8 (phone overlay tap targets)

## What Changes

- **`app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt`**: the max card's scrolling region takes what the pinned band leaves — `Modifier.weight(1f, fill = false)` replaces `heightIn(max = cardCapDp - ACTIONS_BAND_DP)` — so the band is measured at the height its own content needs at the *current* font scale and the content above it gives the space up. `ACTIONS_BAND_DP` is deleted (nothing else reads it).
- **The card's other properties are untouched**: the 45 % cap still bounds the card, the card still hugs its content when the content is shorter than the cap (`fill = false`), and the band stays pinned at the card's bottom edge.
- **A new host case measures the geometry the host can measure**: `RoutePanelActionBandScaleTest` composes the production `RoutePanel` at the AVD's density (`420 dpi`) and font scale 2.0, asserts the card stays within its 45 % share, that the labelled End action lies inside the card, and that it keeps its 48 dp tap target — and has a font-scale-1.0 case for the hug-content property at the default scale. Both print their geometry, so the numbers quoted here stay checkable from the JUnit XML.
- **`guidelines/UI.md` §7** records the rule with its measurement: the pinned band's height is the band's own, never a constant, because the band grows with the font scale and the map area is capped.
- **The device measurement stays a follow-up**: `TODO.md` §138's containment claim (the action's bounds inside the card at font scale 1.0 / 1.3 / 2.0 on the AVD) needs the emulator; `tasks.md` states it as pending, not done.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `route-planning-session`: the pinned action band's height comes from its own content at the current font scale, and the card's scrolling content yields that space, so the labelled actions stay reachable and keep their tap target.

## Impact

- `app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt` (the max card's layout; one constant deleted, one modifier changed).
- `app/src/test/java/com/naviveylin/ui/route/RoutePanelActionBandScaleTest.kt` (new).
- `guidelines/UI.md` §7 (one sentence, with the measurement).
- No API, JNI, manifest, flavor, dependency or native change: both flavors compile the same Kotlin, so the automotive variant is unaffected beyond the forced gate.
- No test expectation moves: no test asserts `ACTIONS_BAND_DP` or the reservation width (grep over `app/src/test` and `app/src/main` finds none), and the neighbouring geometry expectations are "at most 45 %" bounds the fix keeps.
