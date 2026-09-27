# Design

## Context

See `proposal.md` — Why. The current state that constrains the approach:

```
renderer order (libosmscout-map/src/osmscoutmap/MapPainter.cpp)
  12 DrawWays      route fill + casing, road fills
  22 DrawLabels    WAY.TEXT in the way label colour, no halo
  -> a street label lying on the route is painted ON TOP of the route fill.
     The route does not hide it; it removes its backdrop contrast.

way labels           stylesheets/include/roads.oss:622, :640   color: @wayLabelColor
daylight label       stylesheets/standard.oss:179             #000000
night label          stylesheets/standard.oss:192             #cccccc

route rule           stylesheets/include/route.oss
  IF daylight  COLOR routeColor = #7b1fa2        (opaque)
               COLOR routeCasingColor = #311b92
  [TYPE _route] WAY#outline { color: @routeCasingColor; displayWidth: 2.2mm; priority: 99; }
  [TYPE _route] WAY        { color: @routeColor;        displayWidth: 1.5mm; priority: 100; }
  -> casing is 1.467x the fill width and 0.35mm of it shows on each side

daylight road palette  stylesheets/standard.oss:168-205
  motorway #4440ec  trunk #7674ec  primary #ec4044  secondary #fdac44  tertiary #fef271
  motorwayShieldColor = @motorwayColor   trunkShieldColor = @trunkColor
  primaryShieldColor  = @primaryColor    + WAY.SHIELD text is #ffffff
  thinXColor = lighten(@x, 0.3)          motorwayJunctionLabelColor = lighten(@motorwayColor, 0.5)
  lanes = lighten(@x, 0.1)  outline = darken(@x, 0.4)

lighten/darken are simple lerps toward white/black and preserve alpha
  libosmscout/include/osmscout/util/Color.h:140,148

other daylight hues the route must stay away from
  water #9acffd  land #f1eee9  GPX track #0088ff  search marker #ff00ff
  cycleway #ec47c7  favourite #ff8800

stylesheets are a submodule patch surface, not app code
  app/src/main/cpp/libosmscout/stylesheets/** on branch naviveylin-local,
  copied into the APK by syncSubmoduleStylesheets, refreshed on device by AssetCopier
  (per-file size + SHA-256 compare)
```

## Goals / Non-Goals

**Goals:**

- Express both defects, and every knock-on, as colour data in the stylesheet — no renderer, no Kotlin, no JNI change.
- Make the route's translucent fill safe by construction, i.e. its composited centre colour independent of the road beneath it, so no future road or route colour can re-break the label.
- Keep the route's geometry contract: same rendered width, same fill-to-casing ratio, same priority, same single render pass.
- Keep phone and Android Auto identical by leaving the single shared stylesheet and the single `daylight` flag as the only source.

**Non-Goals:**

- No change to the dark presentation's road, route or casing colours.
- No new user setting for route or road colours.
- No change to road widths, priorities, label placement, label style (no halo added to `WAY.TEXT`), or the shield/lane geometry.
- No palette work for `cycle.oss`, whose daylight palette is deliberately desaturated and does not use this scheme.
- No fix for the pre-existing `public-transport.oss` gap (no `_route` rule at all) — still a `TODO.md` item, out of scope.

## Decisions

### D1 — A translucent fill is only safe under an opaque, wider casing

The composited centre is `alpha * fill + (1 - alpha) * casing`. Therefore the centre's
lightness tracks the **fill** and is set by the **casing**, and the road underneath can
only matter if the casing is not opaque.

```
  road -> casing (2.2mm, OPAQUE) -> fill (1.5mm, translucent) -> label
  the casing covers the whole fill footprint, so the centre never sees the road.
```

Alternatives:

- *A — keep the fill opaque and only lighten it.* Fixes the label (measured 6.00:1 for
  an opaque `#b56ad6`) and keeps every existing clause, but delivers no transparency,
  which is what the user asked for.
- *B — translucent fill with the existing dark `#311b92` casing.* Rejected: the fill lets
  the dark casing through, so the centre goes **darker**, not lighter — measured 3.40:1
  for `#ab47bc @75%` over `#311b92`, barely better than the 2.56:1 it replaces.
- *C — translucent fill with a much lighter casing.* Best label numbers (6.07:1 for
  `#ab47bc @75%` over `#e9d5f5`) but the casing drops to **1.37:1** against a white
  residential road, i.e. the border disappears, which the daylight requirement and its
  white-road scenario both forbid.
- *D — chosen: translucent fill over an opaque, darker magenta-leaning casing.* The
  centre stays lighter than the casing (edge **2.30:1**), the casing keeps a visible
  border (**9.39:1** on white), and the label gets a light backdrop (**5.14:1**).

Consequence accepted: the casing is no longer a *dark outline only* but part of a
two-tone route (light core, darker rim), the standard shape for a navigation route.

### D2 — Route values: fill `#ba68c8` at 85 %, casing `#6a1b9a`

All numbers are the black daylight way label against the composited core, measured over a
white residential road.

| option | fill / casing | label/core | core/white | casing/white | edge | hue gap to blue roads |
|---|---|---|---|---|---|---|
| current | `#7b1fa2` opaque / `#311b92` | **2.56** | 8.20 | 12.34 | 1.99 | 37 deg |
| B5 | `#ab47bc` @75 % / `#b39ddb` | 5.14 | 4.09 | **2.40** | 1.70 | **20 deg** |
| M1 | `#ab47bc` @75 % / `#995bb9` | 4.37 | 4.80 | 4.61 | **1.04** | 36 deg |
| L2 | `#ab47bc` @75 % / `#cd9cde` | 5.28 | 3.98 | 2.23 | 1.78 | 41 deg |
| **D4 chosen** | `#ba68c8` @85 % / `#6a1b9a` | **5.14** | 4.09 | **9.39** | 2.30 | 33 deg |
| D5 | `#ce93d8` @85 % / `#7b1fa2` | 7.40 | 2.84 | 8.20 | 2.89 | 38 deg |

Why the alternatives lost:

- *B5* keeps the fill the user first approved but its casing (`#b39ddb`, hue 261) is only
  **20 degrees** from the blue road family, and its border on a white road is 2.40:1,
  below the 3:1 the requirement wants.
- *M1 / the magenta casing at the same lightness.* The casing must sit at a different
  lightness from the fill for the band to read. The whole Material purple ramp from 900
  to 100 sits at hue 251-278, i.e. blue-violet; a low-lightness magenta casing lands on
  top of the composited core (edge **1.04:1**) and the band vanishes.
- *L2* is B5 with only the casing hue moved (20 → 41 degrees). It fixes the hue collision
  but leaves the border at 2.23:1, so the white-road clause would have to be relaxed
  rather than met.
- *D5* has the best label contrast (7.40) but the core drops to 2.84:1 against white, so
  the route itself gets pale on residential roads.

Why the casing goes **darker** and the fill **lighter** relative to B5: the core is
`0.85 * fill + 0.15 * casing`, so the pair has to bracket the target core lightness. A
casing at the fill's own lightness cannot produce an edge, and a casing lighter than the
fill forces the core down toward the casing.

The route's daylight contract is expressed in the spec as relationships (4.5:1 label,
3:1 casing on light backgrounds, ≥25 degrees hue, ≥1.25:1 edge), not as these hex values,
following the precedent set by `map-marker-route-contrast`. The hexes live here and in
`include/route.oss`.

### D3 — Road palette: the bigger step, `#7d7af5` / `#a3a1f5` / `#f58b8b` / `#fdd08a`

Only the **motorway** actually fails the label threshold today (3.19). Trunk (5.48),
primary (5.38) and secondary (11.16) already clear 4.5, so the lightening is primarily the
requested softer look, and the labels justify only the blue.

| step | motorway / trunk / primary / secondary | motorway label | trunk vs water | thin on land (mw/tr) |
|---|---|---|---|---|
| today | `#4440ec` / `#7674ec` / `#ec4044` / `#fdac44` | 3.19 | 2.32 | 3.09 / 2.10 |
| softer | `#6c68f3` / `#8f8df1` / `#f2706f` / `#fdc06b` | 4.91 | 1.75 | 2.27 / 1.76 |
| **bigger (chosen)** | `#7d7af5` / `#a3a1f5` / `#f58b8b` / `#fdd08a` | **5.97** | 1.42 | 1.99 / 1.53 |

Alternatives:

- *Softer step.* Half the collateral (water separation 1.75 vs 1.42) but a visibly smaller
  change; declined by the user after seeing both columns rendered.
- *Motorway only.* Fixes the labels with the least collateral, but the motorway would
  become lighter than the trunk, inverting the road hierarchy the palette encodes.
- *Indigo hue shift (hue 245-248).* Improves water separation to 2.87/1.85 at 37 degrees,
  but drops the motorway label to 4.44, just under the 4.5 target in the spec.

### D4 — Shields get their own darker constant, `darken(base, 0.45)`

A shield is white text on the road colour, so lightening a fill makes the shield text
worse. Measured white-on-fill: today 6.58 / 3.83 / 3.90, after lightening 3.52 / 2.34 /
2.35.

| shield derivation | motorway | trunk | primary |
|---|---|---|---|
| keep today's literal | 6.58 | 3.83 | 3.90 |
| darken(base, 0.35) | 7.07 | 5.08 | 5.14 |
| **darken(base, 0.45)** | **8.73** | **6.53** | **6.63** |
| darken(base, 0.55) | 10.83 | 8.62 | 8.59 |

Chosen `0.45`: clears 4.5 with margin, and the shield stays only 2.48-2.82:1 from the road
fill it labels, so it still reads as a badge rather than a hole. `0.35` clears the
threshold but with only 0.6 of margin on the trunk and primary. Keeping today's literals
is rejected: trunk and primary already fail at 3.83 / 3.90, so that path would ship a known
defect that this change is otherwise well placed to repair.

### D5 — `thinXColor` uses `lighten(base, 0.2)`, with the visibility floor restated

`thinXColor` is used only in the below-full-width branch (`roads.oss:174`, `SIZE ... <0.45mm:3px`,
so the thin branch matches only under 0.45 mm **and** under 3 px, `StyleConfig.cpp:138`), mutually
exclusive with the fill, so there is no on-screen collision — but it is drawn on light land as a
sub-millimetre hairline. Contrast on land `#f1eee9`:

| derivation | motorway | trunk | primary |
|---|---|---|---|
| today (`lighten(old fill, 0.3)`) | 3.09 | 2.10 | 2.33 |
| `lighten(base, 0.2)` chosen | 2.28 | **1.67** | **1.70** |
| `lighten(base, 0.1)` | 2.64 | 1.85 | 1.85 |
| `lighten(base, 0.05)` | 2.81 | 1.92 | 1.94 |
| no lightening (thin = fill) | 3.04 | 2.02 | 2.03 |
| the palette's own hairlines | 2.46 (`thinRoadColor` #999999) | 1.22 (`thinSecondaryColor`) | 1.08 (`thinTertiaryColor`) |

**The first draft of the spec set the floor at 2:1, and that requirement was unsatisfiable.** The new
trunk fill is only 2.02:1 against land and the primary fill 2.03:1, so any variant *lighter than the
fill* — which the requirement also demanded — is below 2.0 by construction, at every value of the
factor. The guard test caught this during apply. Three candidate resolutions were measured:

- *`lighten(base, 0.05)`, floor 1.9.* Keeps today's visibility (1.92 / 1.94) but the lightening is
  only 1.05-1.08:1 above the fill, i.e. a distinction without a difference; effectively the
  no-lightening case with extra constants.
- *No lightening (thin = fill), floor 2.0.* 2.02 / 2.03 clears 2.0 by 1%, so the next unrelated
  tweak trips it, and it deletes upstream's deliberate lighter low-zoom tint, a map-character change
  nobody asked for.
- *Chosen: keep `lighten(base, 0.2)`, restate the floor as 1.6:1 plus a 25-degree hue separation.*

Why the chosen one:

- 1.67 / 1.70 sits **inside** the palette's own convention, not below it — `thinSecondaryColor`
  ships at 1.22:1 and `thinTertiaryColor` at 1.08:1 — and the low-zoom hierarchy is preserved
  (trunk 1.67 still above secondary 1.22).
- Luminance contrast is the wrong sole metric for a hairline. The hairline keeps a saturated class
  hue (`#b5b4f7` is ~152 degrees from the land hue), so it reads as a line despite the low luminance
  ratio. The requirement therefore carries the hue clause, which is the property that actually makes
  it visible.
- It keeps the cartographic intent (the map reads lighter at small scales) instead of silently
  removing it.

Keeping today's literal values as a fixed `thinXColor` is rejected for the motorway specifically:
those literals were derived from the *old*, darker fills and would now be darker than the new fill,
inverting the tint.

### D6 — `motorwayJunctionLabelColor` uses `lighten(base, 0.3)` instead of `0.5`

On land: today 2.04, `0.5` gives 1.53, `0.3` gives 1.99, `0.2` gives 2.28. The label is
drawn with `style: emphasize` (`roads.oss:611`, Cairo strokes a white glyph halo,
`MapPainterCairo.cpp:1039-1053`), so raw contrast understates its legibility; the spec
therefore requires the halo plus 1.9:1. Chosen `0.3`.

### D7 — Secondary stays at `#fdd08a`, with the orange/yellow separation accepted

Lightening the secondary lightens it toward the yellow tertiary: the separation goes
1.63 → 1.25, while primary/secondary improves 1.25 → 1.63. The two warm separations swap.

Alternatives, all measured:

- `#fdc06b` balances both at ~1.4 — offered, declined by the user in favour of the
  demoed value.
- *Darken the tertiary to restore the gap.* Rejected on measurement: it makes things worse
  (`#fde94a` → 1.16, `#fbe03a` → 1.09, `#f9d92e` → 1.03), because both colours are already
  light and converge downward.
- *Shift the secondary hue warmer.* Increases the gap to the yellow but decreases it to the
  red primary; no net gain across the warm triad.

### D8 — Scope: `standard.oss` plus the two blues in `winter-sports.oss`, night untouched

`winter-sports.oss` has its own palette; its warm colours already sit at the new values
(deltas 1.04 / 1.03 / 1.05 against the new standard ones) and only its blues lag, at the
same 3.19 label contrast the change is fixing. Updating just those two blues makes the two
selectable styles consistent for the classes that share the scheme. `cycle.oss` is excluded:
its daylight motorway is `#bbbbbb`, a deliberately desaturated cycling palette that does not
use blue/red/orange at all.

The night branch stays as it is. It is written as `darken(<old literal>, 0.3)`, so leaving
it alone widens the day/night spread (motorway day `#7d7af5` vs night `#302da5` = 2.92:1),
which is the desired direction for a night map, and the `daylight-map-palette` spec requires
the night values to be unchanged.

### D9 — One submodule patch, no bridge-module override

The stylesheets live in the libosmscout submodule (`stylesheets/`), not in the app, and the
build copies them at build time (`syncSubmoduleStylesheets`), so the change is a submodule
patch committed on `naviveylin-local` plus a gitlink bump in the main repo.

Alternatives: a Java-side override in `:osmscout-client-java` (rejected — that list is for
Android ports of Java sources, not data assets); a committed snapshot under
`app/src/main/assets` (rejected — the project deliberately has no snapshot).

### D10 — Guard the contract in a test that reads the packaged stylesheets

`app/src/test/java/com/naviveylin/data/StylesheetHexColorCaseTest.kt` already reads the
merged assets, so the same technique can compute the contrast invariants from the real
packaged stylesheet instead of duplicating hex values:

- the way label colour against the daylight route's composited fill (4.5:1),
- the white shield text against each derived shield colour (4.5:1),
- each daylight road fill against the way label colour (4.5:1).

This needs the stylesheet's own `lighten`/`darken` semantics replicated in the test for the
derived constants; the test therefore evaluates the final colour expressions rather than
parsing them in general. The existing pinned-hex assertions in that file are updated to the
new values as well.

## Risks / Trade-offs

- *The route casing's visible band is only 0.35 mm per side, which at high map DPI can look
  thinner than intended* → accepted: the edge is 2.30:1, and the route's rendered width and
  the fill-to-casing ratio are explicitly held constant by the spec, so the band cannot be
  widened without a separate change.
- *Trunk vs water drops to 1.42:1, so on water-heavy views a lightened trunk can read as
  water* → on-device check with a large lake in view; if it reads badly, the fallback is the
  indigo-shifted softer pair, which the design records with numbers.
- *Secondary vs tertiary drops to 1.25:1* → accepted with the user's decision (D7); the
  balanced alternative is recorded. On-device check with both classes visible.
- *The route's violet sits near the magenta search marker `#ff00ff` and the pink cycleway
  `#ec47c7`* → different roles and sizes (wide polyline vs small node dot vs 0.8 mm route
  line style), and the hue is 33-36 degrees from blue but under 40 from magenta; on-device
  check with the route, a search selection and a GPX track visible together. This risk was
  already accepted in `map-marker-route-contrast` and is unchanged in kind.
- *An uppercase hex literal is a crash, not a cosmetic slip* — `osmscout::Color::GetHexValue`
  accepts only `0-9a-f` and asserts, the module load fails, `standard.oss` is rejected and the
  renderer then crashes natively (`StyleConfig::HasNodeTextStyles`, SIGSEGV). Every new
  literal here is lowercase, the reason is documented in `route.oss`, and
  `StylesheetHexColorCaseTest` scans every packaged stylesheet for uppercase literals.
- *Night presentation regresses because a shared constant moved* → the night branch is
  written from its own literals and is untouched; the on-device check includes a night
  comparison against the previous build.
- *`winter-sports.oss` diverges because only one style was updated* → either both styles get
  the two blues (the plan) or neither; the check compares the same road class in both styles.
- *The gitlink bump is forgotten and CI builds the old stylesheet* → an explicit task bumps
  it and verifies `git submodule status`; the on-device check is unambiguous because the old
  route is red-violet and opaque while the new one is lighter and shows its rim.
- *A stylesheet syntax error in the changed branches breaks rendering* → the build and
  startup checks plus `adb logcat -s NaviVeylin` for "Style error" / "Cannot load module".

## Threading / Lifecycle

No new component, thread, dispatcher or lifecycle owner. The change is stylesheet colour
data:

- the render still happens on the existing native render path (background thread on the
  phone through `MapRenderer`, surface thread on Android Auto through the renderer gate);
- the day/night variant still switches through the existing `daylight` stylesheet flag that
  both surfaces already push (`MapCanvasViewModel` and `CarDaylightApplier`), which already
  forces a full re-render;
- on-device the stylesheets are refreshed by `AssetCopier` from the APK on app start, by
  size + SHA-256, so an update propagates without clearing app data.

## Migration Plan

No data, preference, API or schema migration; the change ships with the next build.

Rollback: revert the three files in the libosmscout submodule (`stylesheets/include/route.oss`,
`stylesheets/standard.oss`, `stylesheets/winter-sports.oss`) and the main-repo gitlink bump,
plus the guard-test and guideline lines. Nothing is persisted on device that needs cleanup —
the next app start copies the previous stylesheets back from the older APK.

## Verification

Unit tests:

- `app/src/test/java/com/naviveylin/data/StylesheetHexColorCaseTest.kt` — every packaged hex
  literal lowercase; the pinned route and road hexes updated; the computed contrast
  invariants from `daylight-map-palette` and the route label requirement.
- Existing `MapCanvasViewModelDarkModeTest` and `MapCanvasViewModelStyleTest` must still
  pass unchanged: the `daylight` flag push and the stylesheet-reload path are not touched.

Build and tests: the `build-app` skill for a mobile debug APK (the stylesheet copy path
`syncSubmoduleStylesheets` must pick up the changed files) and the `run-tests` skill for the
unit-test suites. `checkSubmoduleStylesheets` must pass, which it also proves the submodule
tree is clean.

On-device checks (phone, day presentation, `adb logcat -s NaviVeylin`):

- a street label on the route over a residential road, over a primary road and over a
  motorway: readable in all three;
- the route's rim visible against a white residential road and against a light blue
  motorway;
- the route, a GPX track and a search selection visible together, distinguishable;
- the motorway/trunk/primary shields readable at the zoom where shields appear;
- the low-zoom hairline of every lightened class still visible on land;
- a motorway junction label readable;
- no "Style error" / "Cannot load module" lines and no `Fatal signal` in logcat;
- night presentation compared against the previous build: road fills, route fill and casing
  unchanged.

On-device checks (Android Auto emulator / head unit): the same day checks on the car surface,
confirming identical road and route colours, and no surface-lifecycle change.

## Open Questions

None that affect the specs, the approach or the task breakdown.
