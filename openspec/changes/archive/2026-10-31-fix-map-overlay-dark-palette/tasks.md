# Tasks

Scope: `:core`, `:app`, `:auto` and two guideline paragraphs. No native, JNI, Gradle, manifest or resource
change, and no `:app` flavor-specific task — both flavors compose the same overlays.

## 1. The red cases, before any production change

- [x] 1.1 `auto: AutoMapRendererMarkerDrawCacheTest` — add `theAccuracyRingBranchesOnTheDarkPresentation`:
      build two `MarkerDrawObjects` (as `aDarkPresentationChangeRebuildsThePalette` already does) and assert the
      accuracy fill paints of the light and the dark object differ. Red on HEAD — both are `0x1A2196F3`
      (`AutoMapRendererMarkers.kt:212-221`).
      Parent: `gps-location-marker` — "Accuracy circle follows the resolved presentation".
      Verification: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.AutoMapRendererMarkerDrawCacheTest"`
      red, and the JUnit XML's `tests=` / `failures=` recorded in the change's `evidence/`.
- [x] 1.2 `app: LocationMarkerOverlayPaletteTest` (new) — add
      `theDrawnAccuracyRingChangesWithThePresentation`: compose `LocationMarkerOverlay` twice with a poor accuracy
      and the same viewport (`dark = true` / `dark = false`, the composable's existing parameter,
      `LocationMarkerOverlay.kt:54`), rasterize each frame under Robolectric `@GraphicsMode(NATIVE)` after
      `CompassNeedleStrokeTest`'s harness, and assert the ring pixels differ. Red on HEAD — the ring is drawn
      from the module-level constants `LocationMarkerOverlay.kt:239-240` in both.
      Parent: `gps-location-marker` — "Accuracy circle follows the resolved presentation".
      Verification: focused run of the class red (`:app:testMobileDebugUnitTest` — `:app` has two flavors, so the
      unscoped `testDebugUnitTest` is ambiguous), XML tally recorded in `evidence/`.

## 2. The palette (`:core`)

- [x] 2.1 `core/src/main/java/com/naviveylin/core/GpsMarkerPalette.kt` (new): pure functions of the dark flag —
      `accuracyFill`, `accuracyBorder` (phone light bytes `0x4A90D9` at 10 % / 40 % kept), `positionDot`,
      `positionDotCore`, `positionDotHalo`, `positionDotCasing`, plus `values(dark)` for the branch case. Light
      bytes unchanged except where distinctness requires; dark is a darker tone of the same hue family. The ring's
      stroke width stays a per-surface geometry value, not a palette value.
      Parent: `dark-mode` — "Which app-drawn elements stay visible in dark presentation"; D3 in `design.md`.
      Verification: compiles with `./gradlew :core:compileDebugKotlin`.
- [x] 2.2 `core: GpsMarkerPaletteTest` (new) — four cases, each named in a scenario:
      `everyOverlayColourBranchesOnThePresentation`, `theDarkRingIsADarkerToneOfTheSameHueFamily`,
      `theLightDotIsNotAnObjectMarkerColour`, `theDotIsBrighterThanItsHaloInDarkPresentation`. Plain JUnit after
      `CompassPaletteTest` (pure functions, no Robolectric).
      Parent: `dark-mode` (scenario 3), `mini-map` (scenarios 1 and 2), `gps-location-marker` (scenario 1).
      Verification: `./gradlew :core:testDebugUnitTest --tests "com.naviveylin.core.GpsMarkerPaletteTest"` green,
      and 1.1 is green afterwards.

## 3. The phone draw sites

- [x] 3.1 `LocationMarkerOverlay.kt`: `drawAccuracyCircle` takes the ring colours (fill and border) as input and
      the call site (`:87`) passes `GpsMarkerPalette` for the `dark` flag; the two module-level constants go.
      Geometry (radius, stroke width, `MIN_RADIUS_DP`, the `POOR_ACCURACY_THRESHOLD_PX` gate) is untouched.
      Parent: `gps-location-marker` — "Accuracy circle follows the resolved presentation".
      Verification: 1.2 green and 3.2 green.
- [x] 3.2 `app: LocationMarkerOverlayPaletteTest`: complete the class with
      `theDrawnAccuracyRingUsesADarkerToneOfTheSameHueFamily`,
      `theRingRadiusAndStrokeWidthAreIdenticalInBothPresentations` and `theLightRingKeepsItsShippedTone` (the
      regression guard for light presentation).
      Parent: `gps-location-marker` (scenarios 1, 2, 3).
      Verification: focused run green; the light case's drawn pixels equal the shipped `#4A90D9` tones.
- [x] 3.3 `MiniMap.kt`: the current-position marker's four `drawCircle` calls read the dot, the inner highlight,
      the halo and the casing from `GpsMarkerPalette`; `gpsMarkerColor` goes. The object-marker roles (`:88-90`)
      are untouched.
      **Added scope (the plan's wording was wrong):** `MiniMap` resolved no presentation at all, so it gains a
      required `isDarkPresentation: Boolean` — the option the owner chose 2026-10-31 (explicit parameter, no
      default, matching `CompassButton`) — plumbed through `PoiResultsWithMap` and `SearchDialog`
      (`SearchDialog.kt`) and through `LocationDetailsDialog` (`LocationDetailsSheet.kt`); the two main call
      sites in `MapCanvasScreenModalBands.kt` (and, in `:app` tests, 16 call sites in 11 files) pass it. The
      parameter is not only palette input: `MiniMap`'s own renderer still never pushes the daylight style flag,
      filed as issue #729.
      Parent: `mini-map` — "Current-position marker palette and distinctness".
      Verification: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MiniMap*" --tests
      "com.naviveylin.ui.map.LocationDetails*" --tests "com.naviveylin.ui.map.PoiSearchPanel*" --tests
      "com.naviveylin.ui.map.SearchPanel*" --tests "com.naviveylin.ui.map.SearchDialog*" --tests
      "com.naviveylin.ui.map.SearchHistorySheet*" --tests "com.naviveylin.ui.map.SearchResultMarking*" --tests
      "com.naviveylin.ui.map.BackGesture*" --tests "com.naviveylin.ui.map.MapLayerStack*"` — 84 tests, 0 failures
      (`evidence/app-plumbing/`); plus 3.4.
- [x] 3.4 `app: MiniMapCurrentPositionPaletteTest` (new) — `theDrawnDotChangesWithThePresentation` and
      `theDrawnDotIsNotTheObjectMarkerPrimary`, rasterizing `MiniMap` with a current position through the
      `FakeOSMScoutClient` the existing `MiniMapComposeTest` uses and reading the dot's pixels.
      Parent: `mini-map` (scenarios 1 and 2).
      Verification: focused run green; the second case's failure message would print the colliding `#1A73E8` if
      the palette regressed.

## 4. The car draw site

- [x] 4.1 `AutoMapRendererMarkers.kt`: `accuracyFillPaint` and `accuracyBorderPaint` (`:212-221`) take
      `GpsMarkerPalette` values for the object's own `dark` field; the `0x2196F3` pair goes.
      Parent: `gps-location-marker` — "Accuracy circle follows the resolved presentation" (phone/car parity is the
      reason: spec `cross-variant-ui-parity`).
      Verification: 1.1 green and 4.2 green.
- [x] 4.2 `auto: AutoMapRendererMarkerDrawCacheTest` — add `theRingPaintsTakeTheSharedPaletteValues` (the paints'
      `color` equals the palette's ARGB values in both presentations).
      Parent: `gps-location-marker` — scenario "Phone and car draw the same ring".
      Verification: focused run green.

## 5. Guidelines

- [x] 5.1 `guidelines/UI.md`§8 — replace the "The accuracy circle is untouched" sentence (pre-split `:1072-1073`) with
      the ring's palette branch and the shared-value rule. After another in-flight change split that document during
      this one, the marker entry sits at `:103-109`, the ring's rule at `:108-110`.
      Parent: `gps-location-marker` — the requirement added by this change.
      Verification: `grep -n 'is untouched' guidelines/UI.md` finds nothing and the §8 entry names the branch;
      `tools/check-doc-routes.sh` reports all 169 section references resolving.
- [x] 5.2 `guidelines/UI.md` §9 — a new entry after "Status colors live outside the theme" states
      the visibility rule: which app-drawn elements keep a clearly visible tone in dark presentation (the vehicle
      marker, the active route, the mini map's current-position dot), that everything else dims to a dark tone of
      its own hue family, and that the exempt set is small and enumerated.
      Parent: `dark-mode` — "Which app-drawn elements stay visible in dark presentation".
      Verification: the entry names the exempt elements, the dim rule and the two palette owners —
      `guidelines/UI.md:170-182` after the split (it was `:1188-1202` when it was written).

## 6. Verification

- [x] 6.1 Focused suites for what the change can affect:
      `:core:testDebugUnitTest --tests "com.naviveylin.core.GpsMarkerPaletteTest" --tests "com.naviveylin.core.VehicleMarkerGeometryTest"`,
      `:app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.LocationMarkerOverlay*" --tests "com.naviveylin.ui.map.MiniMap*" --tests "com.naviveylin.ui.map.Compass*"` (the mobile flavor; the automotive flavor runs the same phone UI), 
      `:auto:testDebugUnitTest --tests "com.naviveylin.auto.AutoMapRendererMarkerDrawCacheTest"` — all green.
      Excluded and named: the native/`libosmscout` suites (no native file is touched) and the instrumentation
      suites (no device-only behaviour is added).
      Verification: the four runs' XML tallies recorded in `evidence/`.
- [x] 6.2 The scoped gate this session ran (chosen 2026-10-31, because this tree also carries a second, unfinished
      change): `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` — BUILD SUCCESSFUL in 4m53s, `:app`
      mobileDebug 2219 tests + `:app` automotiveDebug 2219 + `:auto` 910 + `:core` 653 = 6001 tests, 0 failures
      (the run's per-module tallies are in `evidence/verification-record.md` §4 — the 781 XMLs themselves are
      not versioned, being 3.3 MB), plus the checks this change's own paths owe
      (`tools/check-compose-sandbox-rule.sh` 73 classes, `tools/check-openspec-hygiene.sh`, `bash
      tools/gen-feature-list.sh --check-classification`).
      **Exclusion, named:** `tools/loop/gate.sh` itself — its pathspec comes from the whole tree (34 suites,
      including the other change's new `check-rule-routes*` and the workflow/sensor sets), so its verdict would be
      about two changes at once; it stays owed by the loop's wrapper, whose worktree holds only this change. The
      two red runs this session produced are attributed in the same record.
      Verification: `evidence/verification-record.md` §4 (the run) and §5 (the attribution: one red was mine and
      fixed, one was an unrelated flake under load).
- [x] 6.3 Revert-check for the invariant this change introduces (one mutation, the named case must fail):
      mark the mutation site with `// REVERT-CHECK MUTATION (task 6.3)` and make
      `GpsMarkerPalette.accuracyFill(dark)` return the light value for both presentations, run
      `:auto:testDebugUnitTest --tests "com.naviveylin.auto.AutoMapRendererMarkerDrawCacheTest"` — 1.1's case
      must fail; restore the source **and remove the marker** (the gate's `evidence-check` C4 fails on a
      left-over marker under `app/src`); re-run green forced with `-PforceTests --no-build-cache`. `revert-check`
      skill holds the evidence format.
      Verification: red-then-green record in `evidence/`, naming the case and both tallies, and
      `bash tools/loop/evidence-check.sh openspec/changes/fix-map-overlay-dark-palette --root .` reporting
      `ok   C4 mutations: no REVERT-CHECK MUTATION marker`.
- [x] 6.4 Phone measurement (the numbers, not the proof) — `Pixel_8`, Android 15, 1080x2400, the mobileDebug build;
      `adb logcat -s MapCanvasVM` reports `GPS fix acc=5.0` and `renderMap mag=20.0`.
      **Measured:** the mini map's dot, one screen in both presentations — light frame `#0B57D0` 599 px,
      `#4DA3FF` 0 px; dark frame `#4DA3FF` 594 px, interior highlight `#E8F1FF` 132 px, `#0B57D0` 0 px; the
      pre-change literal `#1A73E8` **0 px in both**. The main map's marker casing `#0E1622` 27 px.
      **The ring's device measurement could not be taken, stated rather than implied:** at mag 20 the emulator's
      fixed 5 m accuracy projects to ~2.6 px, an order of magnitude below the 20 px draw threshold, and the
      accuracy cannot be widened from the host (`--accuracy` on a test provider is a class in `[1,2]`;
      `set-test-provider-location --accuracy` needs `MOCK_LOCATION`, refused for uid 2000). The ring's proof stays
      the host raster cases. The independent look reported no ring on the frame, consistent with the threshold.
      Parent: `mini-map` (scenarios 1, 2), `gps-location-marker` (scenario 1 — host proof).
      Verification: `device-evidence.md` (the counts, the two captures, the look, and the limit with its reasons).
- [x] 6.5 Car measurement — **not run; the task's named host substitute stands, chosen by the owner 2026-10-31.**
      The AAOS AVD was not booted: the car surface's ring is proven equal to the shared palette in both
      presentations by `auto: AutoMapRendererMarkerDrawCacheTest` "theRingPaintsTakeTheSharedPaletteValues"
      (`evidence/green-auto-accuracy-ring.xml`, `tests=6 failures=0`), and 6.4's limitation applies to the car
      surface too — an injected fix reports 5 m there as well, so the ring would not be drawn on the car either.
      Parent: `gps-location-marker` — scenario "Phone and car draw the same ring".
      Verification: the host case above, and this line naming why the AVD run would add no measurement.
- [x] 6.6 Evidence, traceability and archive — **archived at the owner's explicit instruction (2026-10-31), after a
      scoped gate rather than the full one.** What this task owes and how it was met:
      `bash tools/loop/evidence-check.sh openspec/changes/fix-map-overlay-dark-palette --root .` — PASS
      (C1 §references, C2 tallies, C3 `/tmp` pointers, C4 no left-over mutation marker, C5 every `file:line`
      resolving); `openspec validate … --strict` — valid; the scenario→case table below.
      **What is weaker than the rule asks, stated rather than glossed:** the independent review a `fix-*` change
      archives on (`openspec/config.yaml`, archive guidance) was not performed by a second, read-only process —
      the same session wrote and archived; and the gate was the JVM suite + this change's own path checks, not
      `tools/loop/gate.sh`, whose pathspec this shared tree (holding another change) could not isolate.
      Verification: `openspec/changes/archive/2026-10-31-fix-map-overlay-dark-palette/` and the three ADDED
      requirements now in `openspec/specs/dark-mode/spec.md`, `gps-location-marker/spec.md`, `mini-map/spec.md`.
---

## Traceability — every scenario names the case that exercises it

| spec | scenario | case | evidence ||---|---|---|---|
| `dark-mode` | The navigation-essential elements keep their dark tone | `auto: AutoMapRendererMarkerDrawCacheTest` `aDarkPresentationChangeRebuildsThePalette` | `green-auto-accuracy-ring.xml` |
| `dark-mode` | An overlay that carries no nav-essential information dims | `app: LocationMarkerOverlayPaletteTest` `theDrawnAccuracyRingUsesADarkerToneOfTheSameHueFamily`; `auto: …` `theAccuracyRingBranchesOnTheDarkPresentation` | `green-app-accuracy-ring.xml`, `green-auto-accuracy-ring.xml` |
| `dark-mode` | No overlay keeps a light literal while dark presentation is active | `core: GpsMarkerPaletteTest` `everyOverlayColourBranchesOnThePresentation` | `green-core-palette.xml` |
| `gps-location-marker` | Dark presentation draws the dark ring | `app: …` `theDrawnAccuracyRingChangesWithThePresentation`; `auto: …` `theAccuracyRingBranchesOnTheDarkPresentation` | `red-head-*.xml` → `green-*.xml` |
| `gps-location-marker` | Phone and car draw the same ring | `auto: …` `theRingPaintsTakeTheSharedPaletteValues`; `app: …` `theLightRingKeepsItsShippedTone` | `green-auto-accuracy-ring.xml`, `green-app-accuracy-ring.xml` |
| `gps-location-marker` | The accuracy circle's geometry does not branch | `app: …` `theRingRadiusAndStrokeWidthAreIdenticalInBothPresentations` | `green-app-accuracy-ring.xml` (radius 60.0 px, thickness 2 px) |
| `mini-map` | The dot's hue is not an object-marker colour | `app: …` `theDrawnDotIsNotTheObjectMarkerPrimary`; `core: …` `theLightDotIsNotAnObjectMarkerColour` | `green-app-minimap-dot.xml`, `green-core-palette.xml`, `device-evidence.md` (`#1A73E8` 0 px) |
| `mini-map` | The dot stays visible in dark presentation | `core: …` `theDotIsBrighterThanItsHaloInDarkPresentation`; `app: …` `theDrawnDotChangesWithThePresentation` | `green-core-palette.xml`, `green-app-minimap-dot.xml`, `device-evidence.md` |
| `mini-map` | The halo and the casing dim in dark presentation | `core: …` `everyOverlayColourBranchesOnThePresentation` | `green-core-palette.xml` |
| scenario outside a delta | *(`gps-location-marker`'s ring on device)* | **could not be produced** — stated in `device-evidence.md` with its three reasons; host raster cases stand as the proof | `device-evidence.md` |
- [x] 6.7 File anything found but not owned by this change as a tracker item (`tools/issues/create.sh`, or the
      `create-issue` skill) — for example if the light-presentation dot needs a second look on device.
      Found and filed: **#729** "Mini map renders the day stylesheet in dark presentation — its renderer never
      pushes the daylight flag" (`class/bug`, `category/map-rendering`, `source/agent`, fingerprint
      `9167c0c536c2f440be9cc6210b259188dd864f57ae8d43f796ab6b3104961a82`). Nothing else was found that this
      change does not own.
      Verification: `gh issue view 729 --repo Framstag/NaviVeylin`.
