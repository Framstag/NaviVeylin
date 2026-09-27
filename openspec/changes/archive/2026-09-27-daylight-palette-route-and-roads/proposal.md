# Proposal

## Why

Two legibility defects in the daylight (light presentation) map, both measured on the
stylesheet the app ships, both fixed by colour data rather than by rendering code.

1. **Street labels are unreadable where the active route covers the road.** Way labels
   are drawn *after* way fills — `MapPainter` render step `DrawLabels` (22) runs after
   `DrawWays` (12) — so the label is painted *on top of* the route, and street
   `WAY.TEXT` carries no halo (`stylesheets/include/roads.oss:622` and `:640`,
   `color: @wayLabelColor` = `#000000`). On the daylight route's opaque `#7b1fa2` fill
   that is **2.56:1**, and **1.70:1** over the `#311b92` casing, against a 4.5:1
   target for text. The route does not hide the label; it removes its contrast.
2. **The dark-blue motorway fill is already below the same threshold:**
   `#4440ec` gives **3.19:1** for a black label. Trunk (5.48), primary (5.38) and
   secondary (11.16) all clear 4.5, so the motorway blue is the outlier.

## What Changes

- **Daylight route fill becomes translucent and lighter, with a dark opaque casing.**
  `stylesheets/include/route.oss`: fill `#7b1fa2` (opaque) becomes `#ba68c8d9`
  (Material Purple 300 at 85 % alpha); casing `#311b92` becomes `#6a1b9a`.
  The composited core is `#ae5dc1`, which lifts a black street label from 2.56:1 to
  **5.14:1** while the casing keeps its own border (**9.39:1** against a white
  residential road) and the edge stays readable (band-vs-core 1.70 → **2.30**).
  Transparency is only safe here because the casing stays **opaque and wider** than the
  fill: the composited core is then independent of the road underneath it, so a
  translucent fill cannot darken its own label backdrop. A translucent fill over a
  *dark* casing does exactly that and was rejected (measured 3.40:1).
- **Daylight road fills lighten one step.** `stylesheets/standard.oss` daylight branch:
  motorway `#4440ec` → `#7d7af5`, trunk `#7674ec` → `#a3a1f5`, primary `#ec4044` →
  `#f58b8b`, secondary `#fdac44` → `#fdd08a`, tertiary unchanged. Black-label contrast
  becomes 5.97 / 8.97 / 8.95 / 14.57.
- **Three constants that ride on the road colours are corrected**, because a lighter
  base makes them fail (`darken`/`lighten` in the stylesheet are simple lerps toward
  black/white, `libosmscout/include/osmscout/util/Color.h:140`):
  - shield colours become their own darker constants `darken(base, 0.45)`
    (`#454387` / `#5a5987` / `#874c4c`), keeping white shield text at 8.73 / 6.53 /
    6.63 instead of dropping to 3.52 / 2.34 / 2.35 — trunk and primary shields already
    fail today at 3.83 / 3.90, so this is also a latent defect repaired;
  - `thinXColor` uses `lighten(base, 0.2)` instead of `0.3`, keeping the low-zoom
    hairline visible on land (motorway 2.28 vs 1.99);
  - `motorwayJunctionLabelColor` uses `lighten(base, 0.3)` instead of `0.5`
    (on land 1.99 vs 1.53; it carries `style: emphasize`, a white glyph halo, so raw
    contrast understates its legibility).
- **Same two blues in `stylesheets/winter-sports.oss`.** Its warm colours already sit
  at the new values (deltas 1.04 / 1.03 / 1.05); only its blues lag. `cycle.oss` is
  out of scope — its daylight palette is deliberately desaturated (`motorwayColor`
  is `#bbbbbb`), not this blue/red/orange scheme.
- **The dark presentation is untouched.** Leaving it as it is widens the day/night
  spread (motorway day `#7d7af5` vs night `#302da5` = 2.92:1), and
  `route-appearance` pins the dark route as red with a white casing.
- **`route-appearance` changes one requirement.** Its daylight clause currently
  *requires* "The daylight fill SHALL be opaque: the road color underneath SHALL NOT
  blend through the route" plus a matching scenario. That clause dates from the era
  when the daylight fill was red and shared the primary road's hue; the violet fill now
  separates by hue, so the requirement is replaced by the invariant that actually keeps
  the design safe — an opaque casing of the shared fill, and a minimum contrast against
  the stylesheet's label colour.
- Additive, no breaking change: no API, data format, navigation behaviour, preference,
  or user-visible workflow changes. Only stylesheet colour data changes.
- **Rollback:** revert the three stylesheet files in the libosmscout submodule plus the
  main-repo gitlink bump, and the guard-test/guideline lines. Nothing is persisted on
  device that needs migration; `AssetCopier` refreshes the on-device stylesheet copy
  from the APK on the next app start.

## Capabilities

### New Capabilities

- `daylight-map-palette`: the daylight presentation's map colour contract — the road
  fill palette per road class plus the colours derived from it (highway shields, low-zoom
  thin rendering, motorway junction labels) and the minimum legibility each must keep
  against the black way label and the white shield text. No existing capability covers
  the daylight road palette (`route-appearance` covers only the route, `dark-mode` only
  the dark presentation, `map-styles` only stylesheet selection).

### Modified Capabilities

- `route-appearance`: the daylight requirement that the route fill be opaque is replaced
  by an opaque-casing invariant plus a minimum contrast of the composited fill against
  the daylight way-label colour; the matching "Route fill is opaque in daylight" scenario
  is replaced. The dark-presentation requirement, the both-surfaces parity requirement,
  the shared-rule-per-style requirement and the no-geometry-change requirement are
  unchanged.

## Impact

**Modified stylesheet assets (libosmscout submodule — one patch, minimal and upstreamable)**

- `app/src/main/cpp/libosmscout/stylesheets/include/route.oss` — daylight `routeColor`
  and `routeCasingColor`.
- `app/src/main/cpp/libosmscout/stylesheets/standard.oss` — daylight road palette,
  shield colours, `thinMotorwayColor`/`thinTrunkColor`/`thinPrimaryColor`,
  `motorwayJunctionLabelColor`.
- `app/src/main/cpp/libosmscout/stylesheets/winter-sports.oss` — the two blues.
- Committed on the submodule's `naviveylin-local` branch; the main repo bumps the gitlink
  in the same change. Precedent: the current route colour block in `route.oss` is
  already a local submodule commit. No C++/JNI code is touched, so this is **not** a
  bridge-module override, and there is no committed stylesheet snapshot to update
  (`syncSubmoduleStylesheets` copies the submodule tree at build time).

**Modified first-party code**

- `app/src/test/java/com/naviveylin/data/StylesheetHexColorCaseTest.kt` — the pinned
  route hexes `#7b1fa2` / `#311b92` and the dark-fill literal assertions move to the new
  values.
- Optional new guard, same module and same read-the-packaged-assets technique: compute
  the contrast invariants from the packaged stylesheets (black way label on the route
  core and on every daylight road fill, white shield text on every shield colour) so the
  new capability's thresholds are machine-checked instead of eyeballed.

**Android-specific components affected**

- None structurally. The change is stylesheet data consumed by the existing native render
  path (`OSMScoutClient` render, `_route` type, `include/roads.oss`) on both surfaces.
- Phone: `MapCanvasScreen` / `MapCanvasViewModel` push the `daylight` style flag; the
  `:app` debug and release assets carry the stylesheets.
- Android Auto / AAOS: `RendererGate.setDarkPresentation` / `CarDaylightApplier` push the
  same flag; the `:auto` surface renderer draws through the same stylesheet. Because both
  surfaces share one stylesheet and one flag, phone and car stay identical by
  construction — no per-surface palette is introduced.
- No manifest, permission, resource-string, or Gradle configuration change.

**Guidelines affected**

- `guidelines/UI.md:541-548` — the route paragraph prescribes "opaque violet fill
  `#7b1fa2` with a dark violet casing `#311b92`" and states the route "is opaque in
  daylight"; superseded by the new colours and the translucent-fill/opaque-casing rule.
- `guidelines/MapRendering.md` — reviewed; the route is still one native render, one
  pass, one stylesheet rule, so no statement becomes wrong. Confirm during apply.
- `guidelines/Design.md` — reviewed; data-only change, no architectural principle moves.

**Scope**

General: one stylesheet serves phone and Android Auto, and every bundled style that draws
a route includes the shared route rule, so both surfaces and every style change together.
Nothing in this change is phone-only or auto-only.

**Specs checked and deliberately not changed**

- `map-styles` (stylesheet selection, not colours), `map-render` (pipeline), `dark-mode`
  (dark presentation, untouched), `marker-render-accuracy` (marker alignment),
  `fav-group-color` (favourite group colours), `render-performance` (frame budget — this
  change alters no pass count, width or priority).
