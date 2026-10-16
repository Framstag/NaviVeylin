# Design — label-word-wrapping

## Context

The render path is: Kotlin `MapRenderer` → `OSMScoutClient.renderWithRouteAndPois`/`renderInto` →
`renderMapIntoPixels` (builds `MapParameter`, one `MapPainterCairo`) → `DrawMap` → label registration
→ `MapPainterCairo::Layout(projection, parameter, text, fontSize, objectWidth, enableWrapping,
contourLabel)` per label → `LabelLayouter` places the measured `CairoLabel`. Everything happens inside
one native render job on the DB job thread; nothing new is threaded.

Two facts constrain the design: the Android build has **no Pango**, and a point label is registered
with `ScreenBox::EMPTY`, i.e. `objectWidth == 0` (`MapPainter.cpp:896-899`).

## D1 — Where the wrapping is implemented

**A (chosen): implement wrapping in the Pango-less Cairo backend, and state the bounds in the render.
** The backend owns text measurement, so only it can turn the parameter bounds into lines. This keeps
the dependency-free native build the project already maintains (the pinned submodule commit *"map-cairo:
draw the face of a configured font file without Pango too"* exists precisely to support that build) and
it is a change a submodule patch can carry.

- *B — enable Pango for the Android native build.* Pango 1.58 static libraries are already installed
  in all three vcpkg Android triplets, and the Pango variant of the painter wraps. **Eliminated**: it
  would switch the whole text path on device (font resolution, metrics, and therefore every label
  position) from the path this app has validated, for a wrapping feature; it needs `PANGO_*`/`GLIB2_*`
  /`PANGOCAIRO_*` cache presets plus a long static link list in `app/src/main/cpp/CMakeLists.txt`
  (the pattern already needed for Cairo); and it grows the APK. It is also the opposite direction of
  the submodule work that made this build Pango-less on purpose.
- *C — set the map parameters only.* **Eliminated by evidence, not by preference**: with the parameters
  set and the Pango-less backend unchanged, the text still draws on one line, because `Layout` ignored
  `objectWidth` and `enableWrapping`. This is the state the change was opened from.

**Risk of A**: the painted label now changes shape on device (two or three lines where there was one),
so a zoom level that used to show one long line now shows a block. Mitigation: the defaults are the Qt
plane renderer's, chosen by that project for exactly this map style (`PlaneMapRenderer.cpp:376-379`),
and the character bound keeps ordinary one- and two-word names on one line.

## D2 — Where the bounds come from

**A (chosen): the existing `MapParameter` values, read through `MapPainter::GetProposedLabelWidth`.**
The values exist, are documented as the wrapping contract, and the Qt and Skia backends already read
them exactly this way, so the Cairo backend only has to join its siblings — one call site, no new
parameter, and the wrapping stays configurable per render call (the app already sets the font size and
prefer-symbol flag there).

- *B — a new `MapParameter` (e.g. `LabelWrapWidthPixels`) set by the app.* **Eliminated**: it would be
  a fourth spelling of the same three knobs, and upstream would reject a patch that adds an API instead
  of using the one it has.
- *C — wrap inside the label layouter rather than the backend.* **Eliminated**: `LabelLayouter` cannot
  measure — average character width and ink extents are backend knowledge — which is why
  `MapPainter::GetProposedLabelWidth` takes `averageCharWidth` as an argument and why the layouter's
  `MeasureLabel` only forwards `objectWidth` and `enableWrapping`.

## D3 — The break rule

**A (chosen): word boundaries only; a word wider than the line gets a line of its own.** This is what
the request asks for ("one-word labels must not wrap") and it matches `PANGO_WRAP_WORD`, so the two
Cairo variants and the other backends agree. It is also the only rule that does not need a hyphenation
source.

- *B — `PANGO_WRAP_CHAR`-style breaking of an over-wide word.* **Eliminated**: it changes long single
  words (`Escaldes-Engordany`, compound German names) into two fragments with no hyphen, which reads as
  two words; the request explicitly excludes it.
- *C — ellipsize instead of wrapping.* **Eliminated**: it loses text rather than making the map more
  readable.

## D4 — How the behaviour becomes testable

**A (chosen): a `MeasureText(…, objectWidth, enableWrapping)` overload on `MapPainterCairo`.** The
existing four-argument `MeasureText` is the backend's public measurement entry point already used by
`Tests/src/TextMetricsCairoTest.cpp`; the overload lets a host test measure exactly what the renderer
lays out, including the wrap, without a device. It is a thin delegation to the same `MeasureLabel` +
`Layout` pair as the four-argument form.

- *B — drive `DrawMap` with synthetic `MapData` and assert pixels.* **Eliminated**: the label stage is
  the layouter's, and a pixel assertion on a rasterised label is fragile against host font differences
  (`TextMetricsCrossBackendTest` already carries tolerances for that), while it would not name which
  *line* the text took.
- *C — no new entry point; test only on device.* **Eliminated**: the wrapping rule (never break a word,
  respect the minimum character count) would then have no case that can be run in CI or on a host, and
  the defect this change fixes would be reproducible only by eye.

**Risk of A**: a new public method on the backend class. Mitigation: it is measurement, documented in
the header, and the four-argument form is unchanged; the new overload is not called from any production
path.

## Verification

- **Host, Pango-less (the shipped configuration):**
  `cmake -S app/src/main/cpp/libosmscout -B <dir> -DCMAKE_DISABLE_FIND_PACKAGE_Pango=ON
  -DOSMSCOUT_BUILD_TESTS=ON …` then
  `cmake --build <dir> --target TextMetricsCairoTest -j16 && ctest -R TextMetricsCairo`.
  Cases: `Cairo wraps a multi-word label into the proposed width` (multi-line, narrower, stacked) and
  `Cairo never breaks a word and keeps a short label on one line` (one baseline for an over-wide word
  and for a label under the minimum character count).
- **Falsification (`revert-check`)**: force `proposedWidth = -1` in the Pango-less `Layout`; the wrap
  case must fail; restore and re-run green.
- **Build**: `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=<abi>` per shipped ABI
  compiles the modified backend in the shipped (Pango-less) configuration — this is the only build that
  compiles that variant, so it is the compile gate, not the host test.
- **Gate**: `./gradlew test -PforceTests --no-build-cache` plus both flavors' assemble, on a tree whose
  other in-flight changes are committed or absent, because the shared tree makes the result unreadable.
- **Device measurement (owed, no `view_image` in this harness):** with the Andorra map installed
  (`emulator-5554`), a known multi-word place name (`Andorra la Vella`) is drawn on more than one line
  and a known one-word name (`Escaldes-Engordany`) on one — recorded either as a screenshot inspected
  by the owner or as a temporary, coordinate-free diagnostics line naming the line count of a label
  (numbers only, `Logging.md` §1/§3), removed after the measurement.

## Threading and lifecycle

None of the change touches scheduling. `Layout` runs on the DB job thread inside the existing render
job, under the same render serialisation as before; the new state is per-`CairoLabel` (lines, extents,
advance) and dies with the frame's label. The only new shared state is the new public method's
signature. No new allocation in the hot path beyond the vectors a wrapped label needs, and a label that
does not wrap allocates the same single-entry vector it needs today.
