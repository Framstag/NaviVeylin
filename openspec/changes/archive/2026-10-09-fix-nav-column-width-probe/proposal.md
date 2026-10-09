# Proposal

## Why

Root cause: the chrome band provides `LocalOverlayWidthProbe` around its **browse layout only**
(`app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt:1671-1673`), while the navigation branch
composes the same `MapRightWidgetColumn` outside that provider (`:2043`), so a chrome band that composes
while navigation is already active never publishes the column's measured width — `overlayRightInset`
stays 0, `setMapOverlayInsets(right = 0)` (`:2230-2235`) publishes no right band, and the follow anchor
resolves under the compass / speed / zoom column instead of beside it (spec `smooth-follow` — Right
anchor stays clear of the widget column only when covered).

Evidence: `com.naviveylin.ui.map.MapNavColumnWidthProbeTest#theNavigationWidgetColumnPublishesItsWidthSoTheRightAnchorStaysClearOfIt` fails on HEAD — XML `tests="2" failures="1"` (`java.lang.AssertionError: the navigation-time widget column must publish its width like the browsing one, so the right-edge preset stays clear of the column (navigatingFx=0.9 rawPresetFx=0.9)`), `timestamp="2026-10-09T20:05:03.852Z"` (XML copied to `evidence/TEST-MapNavColumnWidthProbeTest-red-on-HEAD.xml`).

Repro: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapNavColumnWidthProbeTest"` (window `w411dp-h891dp`, both anchors `MIDDLE_FAR_RIGHT`, follow mode with a GPS fix).

**The reachable path, measured.** The entry `TODO.md` §151 recorded the mechanism as "`overlayRightInset`
stays 0 for the whole navigation". Measurement refines the *path*, not the mechanism: with the routing
anchor at `MIDDLE_FAR_RIGHT` the retained red run reads `browseFx=0.7773722627737226` (the browsing
column's own width, so the preset already sits left of it) and `navigatingFx=0.9` = the raw preset
(nothing measured). The browsing band's last width is still in `overlayRightInset` when a band starts
navigating after composing in browse mode; that row is the phase-A scratch probe and is **not retained**
(`design.md`, Context). The defect is therefore visible exactly where the band **composes
while navigation is already active**, and that path is reachable and documented: a live car session
disposes the phone canvas — `MapCanvasScreen.kt:246-252` returns before the band's `remember`ed insets
exist and `guidelines/MapRendering.md`, section 18/`guidelines/UI.md` §10a state that nothing of the canvas
survives the suspension — so the *Show map here* override recomposes the chrome band in its navigation
branch with no remembered inset at all. The driver's phone then frames the followed map for the rest of
the session with a right-edge preset sitting under the widget column. That is the framing change
`TODO.md` §151 filed rather than made inside a layering change, which is why the device check stays a
follow-up task here.

Spec: `smooth-follow` / Vehicle position anchor in follow mode   Guideline: `guidelines/MapRendering.md` §1.1, `guidelines/UI.md`, section 11

## What Changes

- **`app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`**: the chrome band's
  `CompositionLocalProvider(LocalOverlayWidthProbe provides …)` moves from around the browse
  `BoxWithConstraints` (`:1671-1673`) to around the whole chrome band's content, so the browse column
  (`:1707`, `:1847`) and the navigation column (`:2043`) are all inside the provider. Still one provider,
  one writer of `overlayRightInset`, and one composed column at a time — the three call sites are
  mutually exclusive branches of the band.
- **Nothing else changes**: the column composable, the inset publication (`:2230-2235`), the collision
  rule (`core/VehicleAnchor.kt`), the anchor presets, the browse layout, the band stack and the
  car-session suspension are untouched.
- **A new host case measures the wiring whole**: `MapNavColumnWidthProbeTest` composes the real
  `MapCanvasScreen` (three view models constructed directly, a real `CarSessionPresenceImpl`), drives
  the car-session suspension → navigation start → *Show map here* path, and asserts the resolved anchor
  fraction stays left of the widget column. It prints its measurements (`NavColumnProbe …`) into the
  JUnit XML's `system-out`. The screen runs a `while (isActive)` `withFrameNanos` loop
  (`MapCanvasScreen.kt:578`), so `waitForIdle` never returns; the case drives
  the Compose clock by hand (`mainClock.autoAdvance = false` + explicit frames) and has a second case
  that the browsing column keeps publishing its width.
- **`guidelines/MapRendering.md` §1.1** records the rule with this measurement: the phone's overlay
  regions are measured from the chrome the surface currently composes, so a widget column composed while
  navigating publishes its width like the browsing one. **`guidelines/UI.md`, section 11** records the band rule
  the defect violated: a contract the whole band shares is provided once around the band's content, never
  around one branch of it.
- **Device verification stays a follow-up**: whether the driver's followed-map framing (the anchor left
  of the column's pixels, at both orientations) is what the device shows is a `pixel-check` run, stated
  as pending in `tasks.md`, never implied done.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `smooth-follow`: the phone's own overlay regions are measured from the chrome the surface currently
  composes, so the right-side widget column publishes its width in navigation too — including on a
  surface whose chrome band composes while navigation is already active (the car-session resume) — and a
  right-edge preset therefore resolves left of the column in every composition of the band.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — the provider's scope moved (one production
  file, ~8 lines added / 4 removed; the band's content keeps its current indentation, the style the file
  already uses for the provider's body).
- `app/src/test/java/com/naviveylin/ui/map/MapNavColumnWidthProbeTest.kt` (new, two cases).
- `guidelines/MapRendering.md` §1.1 and `guidelines/UI.md`, section 11 (one rule with its measurement each).
- Observable behaviour: on a phone surface whose chrome band composes while navigating, the resolved
  follow anchor moves left of the widget column where it previously sat under it (followed-map framing).
  On a band that composed in browse mode first the inset follows whichever column the band composes;
  the phase-A scratch-probe rows for that path are not retained (`design.md`, Context).
- No API, JNI, manifest, flavor, dependency or native change: both flavors compile the same Kotlin.
  Rollback is reverting the provider move; no stored state, no migration and no test expectation depends
  on it (`grep` over `app/src/test` finds no assertion on the provider's scope or on a zero navigation
  right inset).
