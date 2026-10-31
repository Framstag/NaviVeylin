# Design

## Root cause and the single fix

No presentation branch and no shared carrier exist for the app-drawn overlay palette. The vehicle marker
branches through `core/VehicleMarkerGeometry` and the compass through its own fixed palette
(`CompassButton.kt:41-61`), but the accuracy ring (phone and car) and the mini-map current-position marker are
literals, and the mini-map dot is additionally the theme's own `primary` value — which is why it collides with
the primary object marker.

The single fix: one `:core` palette object holds those colours per presentation, and the three draw sites read
it. Nothing else follows from the root cause.

## The recorded decision this implements

Issue #34 records the owner's decisions (2026-10-31, the item's answer comment):

- **D1 scope** — the mini-map dot **and** both accuracy rings branch, and the two rings take **one shared
  value**; the `#4A90D9` / `#2196F3` divergence is the defect, not a platform difference.
- **D2 direction** — the deciding question for an element is *how essential is it to see in the dark*. Dark
  presentation darkens the map and reduces the number of visually loud elements; an element keeps a clearly
  visible tone only when it carries navigation-essential information (the vehicle marker, the active route),
  and every other element dims to a dark tone of its own hue family. The exempt set stays small. Under that rule
  the accuracy fill/border, the mini-map halo and the white casing rings dim (not essential), and the
  current-position dot stays visible (it carries the user's own position).
- **D3 carrier** — the palette lives in `:core`; Android Auto may override an element whose colour the AA chrome
  dictates, so the carrier keeps a per-variant override seam. The ring is not AA chrome and takes the shared
  value.
- **D4 light presentation** — the dot takes a hue the object-marker roles never use, and the distinctness is a
  scenario of `mini-map`.

## Alternatives the evidence eliminated

- **Dot only (issue #34's original scope).** Eliminated: the car ring is `#2196F3` against the phone's
  `#4A90D9` with no platform constraint between them, so a dot-only fix leaves a `cross-variant-ui-parity`
  violation in place and a second defect of the same class open.
- **Brighten every overlay in dark (the vehicle-marker convention applied wholesale).** Eliminated: it inverts
  the dim rule for elements that carry no navigation-essential information and grows the set of loud elements
  on the dark map, which is the property D2 exists to bound. The conflict the item recorded is not between two
  authorities but between two questions — the rule names the question.
- **Dim every overlay, the dot included (the `dark-mode:64` rule applied wholesale).** Eliminated: a dot at the
  dark end of the blue family over a dark mini-map stops reading as "you are here", and the mini-map has no
  second cue for the user's own position.
- **A theme role for the dot.** Eliminated by measurement: `primary`/`secondary`/`tertiary` are already the
  object/additional/selected marker colours (`MiniMap.kt:88-90`), and `NaviVeylinTheme` defaults to
  `dynamicColor = true` (`MainActivity.kt:114`), so the roles are wallpaper-derived and can collide with any
  hue the dot picks. `CompassButton.kt:74` states the same rule for the compass.
- **A phone-local palette with the car keeping its own pair.** Eliminated: it is the divergence, not a fix for
  it, and it leaves the shared-value constraint unenforced.
- **Re-colour the mini-map's object markers instead of the dot (D4a).** Eliminated: larger blast radius
  (every mini-map marker), and it does not remove the collision by construction while the scheme stays dynamic.
- **Do nothing about light-presentation distinctness (D4c).** Eliminated:
  `openspec/specs/mini-map/spec.md:89` already requires
  a "distinct style", and with the fixed scheme the dot is literally `PrimaryLight`, i.e. the specification is
  violated today — a change that adds a dark branch and leaves that in place trades one defect for another.
- **A new capability for the overlay palette.** Eliminated: the elements already have owners
  (`gps-location-marker`, `mini-map`) and the rule has one (`dark-mode`); a fourth capability would state the
  same behaviour twice.

### The presentation reaches the mini map (added scope, decided during apply)

The plan's wording assumed `MiniMap` "already resolves" the presentation through the theme. It did not: measured
before task 3.3, the widget read no day/night input at all (`grep -n 'DAYLIGHT\|dark\|isDark'
app/src/main/java/com/naviveylin/ui/map/MiniMap.kt` found only `MaterialTheme.colorScheme` at `:87-90`), while the
resolved flag lives in `MapCanvasUiState.isDarkPresentation` and reaches the compass as an explicit parameter.

Three routes were weighed, and the owner chose the first:

1. **An explicit, no-default parameter, plumbed to the mini map** (chosen). Matching `CompassButton`, the value is
   the *resolved* presentation from `MapCanvasUiState`, so a manual On/Off preference still wins. Cost: the
   parameter crosses `SearchDialog` → `PoiResultsWithMap` → `MiniMap` and `LocationDetailsDialog` → `MiniMap` (3
   main files + 2 call sites) and 16 `:app` test call sites in 11 files pass it. A forgotten call site is a
   compile error, which is the property the compass parameter has.
2. **A `LocalDarkPresentation` provided by `NaviVeylinTheme`.** Eliminated: it would give the overlays a value the
   theme derives — and the theme's own default is `isSystemInDarkTheme()`, the source the compass rule forbids
   for exactly these palettes (`CompassButton.kt:84-86`).
3. **Narrow the change to the accuracy ring and wire the dot later.** Eliminated: the recorded decision (D1, and
   its answer on the dot) covers the dot, so narrowing it would reverse a settled decision instead of applying it;
   no option silently narrows a spec'd behaviour.

The parameter is also the natural input for the defect filed as issue #729: `MiniMap`'s own renderer never pushes
its stylesheet's daylight flag, so its bitmap stays the day style in dark presentation. Fixing that is a separate
item, and the presentation it needs is now a parameter of the widget.

## Where the code goes

| file | change |
|---|---|
| `core/src/main/java/com/naviveylin/core/GpsMarkerPalette.kt` | new object; pure functions of `dark: Boolean`: `accuracyFill`, `accuracyBorder`, `positionDot`, `positionDotCore`, `positionDotHalo`, `positionDotCasing`, plus `values(dark)` for the branch case |
| `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` | `drawAccuracyCircle(center, radiusPx, colors)`; the two module-level constants removed; the call site (`:87`) passes the palette for `dark` |
| `app/src/main/java/com/naviveylin/ui/map/MiniMap.kt` | the four `drawCircle` calls (`:242-249`) take dot/core/halo/casing from the palette; the widget gains a required `isDarkPresentation: Boolean` (it had no presentation input at all — measured during apply, see below); `gpsMarkerColor` removed |
| `app/src/main/java/com/naviveylin/ui/map/SearchDialog.kt` | `SearchDialog` and `PoiResultsWithMap` gain `isDarkPresentation` (no default) and pass it to the mini map |
| `app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt` | `LocationDetailsDialog` gains `isDarkPresentation` (no default) and passes it to the mini map |
| `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreenModalBands.kt` | both call sites pass `state.isDarkPresentation` (the resolved value the compass already reads) |
| `auto/src/main/java/com/naviveylin/auto/AutoMapRendererMarkers.kt` | the two paints (`:212-221`) take the palette values for the object's own `dark` field |

The car renderer keeps drawing its ring from its own paint objects — that *is* the override seam D3 names: a
surface whose chrome dictates a colour supplies its own, and today both surfaces supply the shared palette.

**Values.** Light keeps the shipped phone pair (`#4A90D9` at 10 % and 40 %) so light presentation does not
change for the phone; dark is a dark tone of the same hue family, chosen for legibility on dark land without
becoming a loud element (the exact bytes are the implementer's, pinned by the cases: darker tone, same hue
family within 20°, and the dot brighter than its halo in dark). The car's `#2196F3` pair is dropped — the
shared value is the point of D1.

## Threading and lifecycle

No change. Both overlays draw in Compose `Canvas` on the main thread; `GpsMarkerPalette` is a pure value object
with no state, no dispatcher and no allocation beyond the colours it returns, so the marker hot path
(`MapRendering.md` §8, "position updates on every fix") is unaffected. The car renderer's palette objects keep
their existing rebuild trigger (surface bounds, density, presentation), which is where a palette change already
lands.

## Risk

| risk | mitigation |
|---|---|
| light presentation drifts while adding the dark branch | the phone's light ring keeps its shipped bytes; `LocationMarkerOverlayPaletteTest`'s light case pins them, and the dot's light value moves only where D4 requires distinctness |
| the dark ring becomes a loud element on dark land | the rule's own bound: the ring is translucent and dims; the case asserts a darker tone of the same hue family, not a brighter one |
| the palette drifts from the car → parity lost again | both surfaces read one object; the car case asserts its paints equal that object's values |
| the mini-map dot is still indistinct under dynamic colour | named as a limit, not hidden: the scenario asserts distinctness against the fixed scheme, and the dynamic case is out of scope here (a separate item) |

## Verification

- **Host (the proof).** `core: GpsMarkerPaletteTest` (values, hue family, dot ≠ object-marker role, dot brighter
  than its halo); `app: LocationMarkerOverlayPaletteTest` and `app: MiniMapCurrentPositionPaletteTest` rasterize
  the production composables under Robolectric `@GraphicsMode(NATIVE)` after `CompassNeedleStrokeTest`'s harness
  and read the drawn pixels; `auto: AutoMapRendererMarkerDrawCacheTest` reads the two ring paints. Every scenario
  of the three deltas names one of these cases.
- **The red case on HEAD.** `auto: AutoMapRendererMarkerDrawCacheTest` "the accuracy ring branches on the dark
  presentation" and `app: LocationMarkerOverlayPaletteTest` "the drawn accuracy ring changes with the
  presentation": both fail on HEAD (identical values in both presentations) without any new API, so the red run
  is recordable before the palette exists.
- **Device (the measurement, not the proof).** Phone: screenshot with dark presentation on a poor fix
  (accuracy > threshold) and the measured ring pixel via the `pixel-check` skill; the mini-map with a current
  position, same measurement. Car: the AAOS AVD (`guidelines/Build.md` §10a) and the ring over the dark surface.
  A stationary emulator can produce both overlays (they are drawn from published state, not from motion), so
  the task states the numbers it records.
- **Not device-only.** Nothing here needs a real GPS fix: accuracy, bearing and position are inputs.

## Open decisions

none

## Open Questions

- **The dynamic-colour case for the dot.** With `dynamicColor = true` the object-marker roles are
  wallpaper-derived, so "distinct hue" cannot be asserted there. Recorded as a limit of the `mini-map` scenario;
  re-colouring the object markers is deliberately not part of this change.
