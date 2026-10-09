# Design

## Context

`drawCompassNeedle` (private `DrawScope` extension, `CompassButton.kt:203`) draws the north half and the
neutral south half of the needle with `drawLine(...)`, passing `strokeWidth = 3f` to both. Everything else the
widget draws is converted at draw time: `needleLength = 10.dp.toPx()` (`:218`), the rim
`Stroke(width = 1.dp.toPx())` (`:144`), the canvas `Modifier.size(48.dp)`. See `proposal.md` — Why for the
defect and its red-on-HEAD evidence.

The stroke is a draw-time value: it has no semantics node, so nothing in the existing suite can read it. The
existing host harness has three observation channels, and none of them carries a drawn width today:
`getBoundsInRoot()` on a semantics node (what `CompassButtonComposeTest#buttonLargerThanOtherOverlayButtons`
and `RoutePanelActionBandScaleTest` measure), an extracted `internal` decision function called directly (what
`CompassNeedleTargetTest` and `CompassPaletteTest` do), and the rasterized pixels themselves (used nowhere in
`app/src/test` yet). Compose's own `captureToImage()` is not available here — probed on this tree, on both a
node and the root, it times out with `ComposeTimeoutException: Condition still not satisfied after 2000 ms`
(recorded from that probe run; the probe file was a throwaway spike, not kept).

## Goals / Non-Goals

**Goals**

- The needle's drawn width is density-independent, and keeps the width it has today on a 1x screen.
- The invariant is decidable on the host from what the needle actually draws, without a production seam added
  for the test.
- The measurement stays readable after the run that produced it (JUnit XML `system-out`).

**Non-Goals**

- Not changing the needle's length, hub, cap, palette or rotation convention, or the widget's layout sizes.
- Not touching the Android Auto compass rose (a different renderer, spec `auto-map-renderer`).
- Not asserting a specific dp number as the requirement: the invariant is density-independence, so the case
  compares 1x against 4x instead of re-pinning `3.dp`.
- No device/emulator work: the invariant is fully host-decidable.

## Decisions

**1. The fix: one density-aware value, used by both strokes.**
`drawCompassNeedle` gains `val needleStrokeWidth = 3.dp.toPx()` beside `needleLength`, and both `drawLine` calls
pass it. This is the only fix that follows from the root cause: `3.dp.toPx()` is the same expression the sibling
in the same file already uses, the drawn result on a 1x screen is byte-identical to today's (3 px), and it makes
the *dp* width constant across densities. A local `val` rather than `3.dp.toPx()` twice keeps the two halves one
concept (one spelling per concept).

**2. Observation: measure the drawn pixels, not the source, and add no production seam.**
The rasterized view hierarchy under Robolectric's native graphics is the channel that observes what the needle
draws without changing production code: the case composes the production `CompassButton` at two densities in one
tree (`LocalDensity provides Density(1f)` / `Density(4f)`), draws the decor view into a `Bitmap`, and measures
the run of needle-colored pixels across the needle at its centre column. `SemanticsNode.boundsInRoot` gives each
needle's centre in bitmap pixels (the device density stays the Robolectric default, mdpi).
Alternatives the evidence eliminates:
- *A test-only seam* — widening `drawCompassNeedle` to `internal` so a case could call it with a recording
  canvas, or extracting the stroke width into an `internal fun needleStrokeWidth(density)`. Both change
  production visibility or structure for the test alone, and neither is needed: the pixels are already
  observable (measured `1x=4px, 4x=4px` on HEAD in a two-density tree within one ~7 s class run — recorded from
  the calibration spike, whose /tmp log is not retained; the retained figure is the gate's after row above).
- *Asserting a source-level absence of `3f`* — a source-scanning case would test the text, not the drawing, and
  would pass on any other raw-pixel stroke that produced the same symptom.
- *Compiling the case against a value that only exists after the fix* — the red evidence would be a compile
  error rather than a failing case, which is not a run the gate can quote.

**3. The assertion compares two densities rather than one magic dp value.**
The requirement is density-independence, so the case asserts (a) `|dp(4x) − dp(1x)| ≤ 1.5 dp`, the tolerance
the ±1 px antialiased edge needs (1.0 dp at 1x, 0.25 dp at 4x) and — measured — the figure the raw-pixel stroke
exceeds (4.0 dp vs 1.0 dp on HEAD), and (b) `px(4x) ≥ 2 · px(1x)`, which fails for any stroke that does not grow
with density. A case that pinned `12 px at 4x` would pass the defect's opposite (a constant px count) if the
number were ever re-derived from the implementation.

## Risks / Trade-offs

- **`@GraphicsMode(GraphicsMode.Mode.NATIVE)` is a second Robolectric sandbox config.** The repository's rule
  (`guidelines/Build.md` §6) forbids `@Config(sdk=…)` / `@GraphicsMode(…)` for classes that trigger the JNI
  stub's `System.loadLibrary`; this class does not, and it loads no native library of its own. Probed before
  the change was planned (recorded: with this class in the run, `com.naviveylin.ui.map.*` executed 103 classes /
  777 tests with its one expected red and zero `already loaded in another classloader` lines), and confirmed by
  the retained forced gate (task 4.2): 228 classes / 1752 tests in each `:app` flavor, 0 failures, and no
  classloader line in the log.
- **Antialiasing makes the measured width a pixel-quantized value.** Mitigation: the case reads the run at the
  needle's hub (1 px above centre, where only the north half is drawn and the "N" glyph is not), counts an
  antialiased edge pixel once, prints both measurements, and compares dp across densities with a tolerance whose
  cause is written down.
- **The case rasterizes the whole decor view**, so it depends on the Compose tree being laid out and drawn:
  `waitForIdle()` precedes the draw. If a future Compose version stops drawing into a software `Canvas` under
  Robolectric, the case fails loudly rather than silently passing (the measured run would be 0 px, tripping
  assertion (b)).
- **The rule lives only in the guideline's compass/drawn-size wording.** `guidelines/UI.md` §8 says
  "density-aware (same visual size on every screen)" for the vehicle marker and sizes the compass in dp; the
  change adds one sentence to that section so the next drawn value has an explicit rule to follow.
