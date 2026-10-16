# Proposal — label-word-wrapping

## Why

Map labels are never word-wrapped on device, and the reason is two stacked omissions, not one
value that is set too high.

**1. The app set none of the wrapping bounds.** `renderMapIntoPixels` builds its `MapParameter`
without any label-line value (`libosmscout-client-java/src/OSMScoutClient.cpp:1590-1612`), so the
library defaults applied: minimum character count 5, maximum 15, fit-to-area `true`, fit-to-width
**8000 px** (`libosmscout-map/src/osmscoutmap/MapParameter.cpp:37-40`). The pixel bound can never bind:
`MapPainter::GetProposedLabelWidth` always caps the proposed width at
`LabelLineMaxCharCount * averageCharWidth` (`libosmscout-map/src/osmscoutmap/MapPainter.cpp:642-668`),
so the character count is the bound that wraps — and it was never stated for this app.

**2. The backend that this app builds drops the wrapping inputs.** Android builds the Cairo backend
without Pango: `app/.cxx/*/arm64-v8a/CMakeCache.txt` holds
`PANGO_LIBRARY:FILEPATH=PANGO_LIBRARY-NOTFOUND`, the generated `MapCairoFeatures.h` leaves
`OSMSCOUT_MAP_CAIRO_HAVE_LIB_PANGO` undefined, and no Android vcpkg triplet installs pango. So
`MapPainterCairo::Layout` is the FreeType/cairo variant, whose signature read
`double /*objectWidth*/, bool /*enableWrapping*/` and which measured the whole text with one
`cairo_scaled_font_text_extents` — **one line, always**. No value of the four map parameters could
reach it, so "set a reasonable value" alone changes nothing on device.

A point label makes the first omission visible even in a build that does honour the parameters: a
node label is registered with `ScreenBox::EMPTY` (`MapPainter.cpp:896-899`), so its object width is 0
and the pixel bound is the only bound that could apply to it.

**Evidence.**
- The wrapped case is red on HEAD in a Pango-less host configuration:
  `Cairo wraps a multi-word label into the proposed width` (`Tests/src/TextMetricsCairoTest.cpp:573`)
  fails with `31 == 33` glyphs and one baseline where the wrapped label carries three.
- On HEAD the app's own render sets no wrapping value: `OSMScoutClient.cpp:1590-1612` carries no
  `SetLabelLine*` call (`git grep -n 'SetLabelLine' -- app/src/main/cpp/libosmscout/libosmscout-client-java`
  is empty on HEAD).
- The Pango-less build is the shipped one (CMakeCache above), quoted from a real configure of
  `:app:assembleMobileDebug`.

**Why now.** The label layout is what makes a dense map readable, both surfaces draw through the same
native entry, and the fix is testable without a device in a Pango-less host configuration (which the
submodule already supports — the pinned commit is *"map-cairo: draw the face of a configured font file
without Pango too"*).

## What Changes

- **The render states the wrapping bounds.** `renderMapIntoPixels` sets `LabelLineMinCharCount` 5,
  `LabelLineMaxCharCount` 15, `LabelLineFitToArea` `true` and `LabelLineFitToWidth`
  `min(width, height)` of the render buffer — the bounds `libosmscout-client-qt`'s plane renderer uses
  (`PlaneMapRenderer.cpp:376-379`). Additive: the map parameter keeps its defaults for every other
  caller.
- **The Pango-less Cairo text layout wraps.** `MapPainterCairo::Layout` computes an average character
  width from the resolved cairo font, asks `MapPainter::GetProposedLabelWidth` for the line width, and
  breaks the text at word boundaries into the lines it measures. A word is never broken, so a one-word
  label stays on one line however wide it is — the rule Pango's `PANGO_WRAP_WORD` follows in the
  other variant.
- **A wrapped label reports the size it draws.** The Cairo label carries its lines, each line's ink
  extents and the baseline advance; `DrawLabel` (text style and shield style) and `ToGlyphs` emit one
  line per entry, centred in the label rectangle, and the label's width/height are the widest line and
  the stacked block, so the label layouter places, culls and breaks overlaps with the drawn size.
- **The wrapping is measurable from outside the painter**, so a host test can assert it:
  `MapPainterCairo::MeasureText(…, objectWidth, enableWrapping)` overloads the existing
  measurement entry point.
- **`guidelines/MapRendering.md` gains §20**, the routing table in `AGENTS.md` gains its row.

**Not changed.** The Pango variant of the Cairo painter (Android does not build it) and the AGG
backend; the Qt and Skia backends already wrap through `GetProposedLabelWidth`. Nothing on the Kotlin
side: the wrap happens entirely inside one native render job.

## Capabilities & specs

- `map-render` — **ADDED** requirement *"Labels wrap at word boundaries within the configured
  bounds"* (`specs/map-render/spec.md`). No existing requirement mentions label wrapping, so the delta
  is additive and touches no existing text.
- No new capability and no new spec id: the shipped spec classification
  (`tools/feature-list/specs.json`) does not change.

## Impact

| file | change |
|---|---|
| `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` | the four `SetLabelLine*` calls in `renderMapIntoPixels` |
| `…/libosmscout-map-cairo/src/osmscoutmapcairo/MapPainterCairo.cpp` | `WrapCairoText`/`CairoTextExtents` helpers; the Pango-less `Layout` wraps; `ToGlyphs` and `DrawLabel` draw every line; the two-argument `MeasureText` overload |
| `…/libosmscout-map-cairo/include/osmscoutmapcairo/MapPainterCairo.h` | `CairoNativeLabel` gains `lines`/`lineExtents`/`lineAdvance`; the `MeasureText` overload declared |
| `…/Tests/src/TextMetricsCairoTest.cpp` | two Pango-less cases and their helpers |
| `guidelines/MapRendering.md` | §20 "Label wrapping" |
| `AGENTS.md` | Documentation Map row for §20 |

Modules: `:app` native build (both flavors — the car session draws through the same native entry);
no `:core`/`:auto` Kotlin change. Scope: **both surfaces** (phone canvas and car surface call the same
`renderWithRouteAndPois`/`renderInto`).

**Native/JNI policy:** a **submodule patch, minimal and upstreamable** — the wrapping capability belongs
to `libosmscout-map-cairo` (the AGG and Pango variants are the precedent), so it is fixed there and not
worked around in Kotlin (`Design.md` §5, "A defect *inside* the library behind an unchanged JNI contract
is fixed upstream"). `OSMScoutClient.cpp` is the submodule's JNI bridge; it is patched in the same place
per `Design.md` §5 ("patch in one place, never both").

**Additive or breaking:** additive. No Java/JNI signature changes; the new C++ overload is a new method
on the backend class.

**Rollback:** `git checkout` the six files (four submodule, two documentation). The map parameter
defaults restore the previous behaviour for the render call, and the painter's previous single-line
`Layout` returns with the source file. No persisted state, no migration.

**Open questions.** None: the two defensible fixes (implement wrapping in the Pango-less backend vs
enable Pango for Android) were decided by the owner — see `design.md` D1, which records why the Pango
route was eliminated.
