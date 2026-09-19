# Design

## Context

See `proposal.md` — Why. Current state that constrains the approach:

```
core/src/main/java/com/naviveylin/core/VehicleMarkerGeometry.kt
  SIZE_DP = 32f                      single size source for phone + AA
  COLOR_GRADIENT_TOP/BOTTOM          shared by both presentations
  COLOR_CASING / COLOR_CASING_DARK   the ONLY presentation-dependent constant
  outlineVertices()                  shared by both renderers

app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt
  Compose overlay, reads the constants, takes dark: Boolean

auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt
  surface renderer, reads the same constants via setDarkPresentation(dark)

app/src/main/cpp/libosmscout/stylesheets/include/route.oss    (submodule)
  COLOR routeColor = #ff000088
  [TYPE _route] WAY#outline { color: #ffffff; displayWidth: 2.2mm; priority: 99; }
  [TYPE _route] WAY        { color: @routeColor; displayWidth: 1.5mm; priority: 100; }
  no IF daylight branch -> day and night route are identical today

day stylesheet road hues: motorway #4440ec, trunk #7674ec, primary #ec4044,
secondary #fdac44, tertiary #fef271, road/residential #ffffff
other overlays: GPX track #0088ff, favorites #ff8800, search #ff00ff

daylight flag: phone pushes it in MapCanvasViewModel.pushDarkPresentation(),
AA pushes it in MapScreen / NavigationScreen via CarDaylightApplier
```

Submodule state: clean, HEAD `bf7d488d8` on branch `naviveylin-local`; `route.oss` already carries a NaviVeylin-local comment block, so a local patch to it has precedent.

## Goals / Non-Goals

Goals:

- Make the three reported appearance defects fixable by changing data, not by adding rendering code.
- Keep one source of truth per visual: one size constant, one palette constant set, one stylesheet.

Non-Goals:

- No new render pass, no marker drawing in native code, no route drawing in Kotlin.
- No user-facing setting for route color or marker size (this change fixes defaults only).
- No change to the dark-presentation route, as explicitly requested.
- No change to route geometry, width, priority, or the start/end node symbols.

## Decisions

### D1 — Marker size: bump the shared constant, not a per-surface override

`SIZE_DP` 32f to 38f in `VehicleMarkerGeometry`. Both renderers multiply it by their own density, so both surfaces grow by the same ratio.

- Alternative A (rejected): phone-only size parameter, AA stays 32 dp. Rejected because specs `gps-location-marker` and `auto-map-renderer` both define marker-size parity plus `guidelines/UI.md`; a deviation would need justification in two specs for a purely cosmetic change, and the car marker benefits from the same legibility gain.
- Alternative B (rejected): keep 32 dp and scale by a factor at each call site. Rejected: two call sites, two chances to drift, and the size constant stops being the single source.
- Consequence accepted: existing tests that measure the marker against AA assertions keep passing because they derive from the constant; only the constant assertion in `VehicleMarkerGeometryTest` changes.

### D2 — Dark marker palette: add dark-specific color constants, keep the geometry untouched

Add `COLOR_GRADIENT_TOP_DARK = #BBDEFB` and `COLOR_GRADIENT_BOTTOM_DARK = #1E88E5` next to the existing `COLOR_CASING_DARK`, and let each renderer select the palette pair by its `dark` flag. Rim and casing keep their current values, so the deliberate removal of the white ring (change `unified-vehicle-marker`) is not reversed.

- Alternative A (rejected): lighten the shared gradient for both presentations. Rejected: it changes the daylight look, which is not reported as a problem.
- Alternative B (rejected): compute the dark palette at runtime by lightening the day colors. Rejected: implicit math makes the palette untestable as a contract and hides the actual color values from review.
- Alternative C (rejected): restore a light casing ring in dark presentation. Rejected: that is exactly the stencil halo the earlier change removed; the user's constraint is "lighter, but not white".
- Consequence accepted: the "only the casing branches" rule in both specs and in the `VehicleMarkerGeometry` KDoc is superseded — the palette branches, the geometry does not.

### D3 — Day route colors: opaque violet fill with a dark violet casing

```
IF daylight {
  COLOR routeColor       = #7b1fa2;   opaque violet
  COLOR routeCasingColor = #311b92;   dark violet
}
ELSE {
  COLOR routeColor       = #ff000088; red (unchanged)
  COLOR routeCasingColor = #ffffff;   (unchanged)
}
[TYPE _route] WAY#outline { color: @routeCasingColor; ... }   hardcoded white becomes a constant
```

Hex literals in a stylesheet SHALL stay lowercase: `osmscout::Color::FromHexString` documents "@param hexString (lowercase)" and `Color::GetHexValue` (`libosmscout/src/osmscout/util/Color.cpp:68`) accepts only `0-9` and `a-f`, asserting on anything else. The first iteration of this change used `#7B1FA2` and crashed the app on the emulator (see Risks); `app/src/test/java/com/naviveylin/data/StylesheetHexColorCaseTest.kt` now guards every packaged stylesheet against uppercase literals.

Violet is absent from the day road palette and from the other overlays, and a dark casing stays visible on white residential roads where a white casing disappears.

- Alternative A (rejected): green fill. Absent from roads too, but close in tone to `grassColor #cfeca8` on the light map; less separation from land cover.
- Alternative B (rejected): keep the red family (#D32F2F with a near-black casing). Fixes only the alpha blending; the hue still equals the primary-road color, which is the reported symptom.
- Alternative C (rejected): teal. Collides with `waterColor` on water-heavy views.
- Consequence accepted: the violet route sits in the same hue region as the magenta search marker `#ff00ff`. Different roles (wide polyline versus small node dot) and different lightness; verified on-device in the verification tasks, and the hue can be darkened later without a spec change since the spec names the contract (distinguishable, not the exact hex) — the hex values are recorded here and in the stylesheet.

### D4 — Route colors live in the stylesheet, not in Kotlin

The route is rendered natively from the `_route` way through the active stylesheet; the Kotlin layers only hand geometry to the renderer. The colors therefore belong in `stylesheets/include/route.oss`, driven by the `daylight` flag both surfaces already push.

- Alternative A (rejected): override the route color from Kotlin per surface. Rejected: introduces a second route palette and breaks the both-surfaces-same-color requirement; also no existing API paints the route outside the native render.
- Alternative B (rejected): ship a second route stylesheet for night. Rejected: duplicates the whole stylesheet tree and the selection logic for two color values; the `daylight` flag already exists for exactly this.
- Consequence accepted: an `IF daylight` branch is added to a file that currently has none, so a syntax error there would affect route rendering only (it does not gate the rest of the stylesheet).

### D5 — Submodule patch, minimal and upstreamable

`route.oss` and `cycle.oss` are changed inside the libosmscout submodule on the `naviveylin-local` branch (one presentation branch plus one casing color reference, and one include replacing a style-local route rule), and the main repo bumps the gitlink in the same change.

- Alternative A (rejected): leave the submodule change uncommitted. Rejected by project rules — uncommitted submodule state is not built by CI or fresh clones.
- Alternative B (rejected): a Java-side override in `:osmscout-client-java`. Rejected: stylesheets are data assets, not bridge code; the override list is for Android ports of Java sources.
- Alternative C (rejected): a committed stylesheet snapshot in `app/src/main/assets`. Rejected: the project deliberately has no snapshot; `syncSubmoduleStylesheets` copies the submodule tree at build time.

### D6 — Per-style route rules are replaced by the shared include

`cycle.oss` carried its own route color and rule, which would have kept a semi-transparent single-color route in that selectable style while every other style switched to the new palette. Its `COLOR routeColor` and its `[TYPE _route] WAY` rule are removed and `MODULE "include/route"` is added, matching `standard.oss` and `winter-sports.oss`.

- Alternative A (rejected): duplicate the `IF daylight` color block into `cycle.oss`. Rejected: two places to keep in sync, and the casing rule would have to be duplicated too.
- Alternative B (rejected): narrow the spec to "stylesheets using the shared include" and leave `cycle.oss` alone. Rejected by the user: a user picking the cycle style would still see a washed-out route without a casing.
- Alternative C (rejected): change only the route color in `cycle.oss`, keeping its own rule. Rejected: the semi-transparent fill and the missing casing remain, so the contrast contract still fails there.
- Consequence accepted (scope growth, user-approved): the cycle style now also draws the route start/end, favorite, search and GPX track node symbols, because those come with the shared include. That closes a gap for favorites and the search selection in that style; it is a behavior change beyond the literal three cosmetic asks and is recorded here and in the proposal.

## Risks / Trade-offs

- [A 38 dp marker covers more of the route line and may reach the next-turn card at city zoom] → on-device check in the verification tasks (follow mode, city zoom, next-turn card visible); the marker is a per-frame overlay, so shrinking later is a one-constant change.
- [The lighter dark core could read as a white blob at the tip] → the tip end is `#BBDEFB`, not white, and the dark casing stays around it; `VehicleMarkerGeometryTest` asserts the dark core is lighter than the dark casing but below a white threshold.
- [Violet route could be confused with the magenta search marker or the blue GPX track] → on-device check with route + GPX track + search selection visible together; hue separation is wide (violet 285° vs magenta 300° vs blue 210°) but lightness is asserted visually.
- [A stylesheet syntax error in the new `IF daylight` branch or in the cycle include breaks route rendering] → build + startup check in the verification tasks; the app logs stylesheet load failures through the existing native log bridge (`adb logcat -s NaviVeylin`). The cycle style is the higher-risk case because it gains an include: the include pattern matches `standard.oss`/`winter-sports.oss` exactly, `cycle.oss` defines no symbol or color name that `route.oss` also defines (verified by grep before the edit), and it is checked on-device alongside the default style.
- [Case of a hex color literal — a crash, not a cosmetic slip] → happened on the first device run: uppercase `#7B1FA2`/`#311B92` make `Color::GetHexValue` assert, `route.oss` fails to load, `standard.oss` is rejected, and the renderer then crashes natively (`StyleConfig::HasNodeTextStyles`, SIGSEGV) instead of degrading. Mitigation: all stylesheet hex literals are lowercase (as upstream already was), the reason is documented in `route.oss` itself, and `StylesheetHexColorCaseTest` scans every packaged stylesheet for uppercase literals and was verified to fail on the uppercase spelling before the fix was restored.
- [The cycle style gains node symbols it did not draw before (favorites, search selection, GPX track, route end points)] → intended and user-approved as the cost of sharing the route rule; on-device check of the cycle style with a favorite, a search selection and a route visible.
- [public-transport.oss has no `_route` rule, so an active route is invisible in that style] → pre-existing, out of scope for this cosmetic change; recorded in `TODO.md` instead of expanding the change.
- [Gitlink bump forgotten: CI builds the old stylesheet] → an explicit task bumps and verifies `git submodule status` shows the new commit, and the on-device check distinguishes violet from red, which fails loudly if the stylesheet did not ship.
- [Both surfaces change at once, so a regression is twice as visible] → accepted: parity is a stated project rule, and the change is colors plus one dp constant.

## Threading / Lifecycle

No new component, thread, dispatcher, or lifecycle owner is introduced. The marker overlay keeps drawing per frame on the existing Compose draw scope (phone) and the existing surface renderer off the main thread (AA); the palette selection is a plain value read of the existing `dark` flag. The route color change needs no invalidation logic of its own: the existing stylesheet-change path already forces a full re-render (the `daylight` flag push), and the stylesheet contents are fixed at build time, so no runtime reload is added.

## Migration Plan

No data, API, or preference migration. The change ships with the next build/APK.

Rollback: revert the `VehicleMarkerGeometry` constant change and the submodule `route.oss` commit (plus the gitlink bump) and restore the replaced spec and guideline lines. Nothing is persisted, so no cleanup is required on devices; the next app start copies the previous stylesheets back only if the APK carries them.

## Verification

Unit tests:

- `core/src/test/java/com/naviveylin/core/VehicleMarkerGeometryTest.kt` — footprint is 38 dp; dark core is lighter than the dark casing; dark core is below a white threshold; day palette values unchanged; geometry (vertices, casing scale, rim width, shadow) identical for both palettes.
- `app/src/test/java/com/naviveylin/data/StylesheetHexColorCaseTest.kt` — every packaged stylesheet's hex color literals are lowercase (the failure mode that crashed the emulator run), and the shared route include carries the `IF daylight` branch with `#7b1fa2` / `#311b92` plus the `@routeCasingColor` reference.
- Existing marker-palette consumers: phone overlay and AA renderer tests still pass (they derive from the constants).

On-device smoke check performed during apply (phone emulator, `adb logcat -s NaviVeylin`): after the lowercase fix the app starts, `standard.oss` and `include/route.oss` load with no "Style error" / "Cannot load module" lines, no `Fatal signal`, and the map renders.

Build: `build-app` skill for the debug APK for both flavors, and the native stylesheet copy path (`syncSubmoduleStylesheets`) must pick up the changed `route.oss`; `run-tests` skill for the unit-test suites.

On-device checks (phone, follow + browse):

- Day: route over a primary road, over a residential street, with the GPX track visible, with a search selection visible.
- Night: route unchanged (red fill, white casing); marker visibly lighter than before on dark land; no white ring around the marker.
- Marker size compared against the previous build at the same zoom.
- `adb logcat -s NaviVeylin` clean of stylesheet load errors after the switch of the `daylight` flag.

On-device checks (Android Auto emulator / head unit):

- Day and night map: route color matches the phone for the same presentation; night route still red with a white casing.
- Marker: bigger, and in night mode lighter, with the same geometry.
- Surface lifecycle unaffected (map screen and navigation screen start/stop).

## Open Questions

None remaining; the three user decisions (38 dp shared, violet day route, lighter-but-not-white dark core) are settled and reflected in the specs.
