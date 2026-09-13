# Tasks

## 1. Shared geometry

- [x] 1.1 Add shared normalized geometry/palette constants for the unified marker (e.g. `MarkerGeometry` in `:core`, or mirrored constants + parity test if module layering forbids): footprint 32 dp, tip/tail proportions, casing width, rim width, gradient stops (#42A5F5 → #0D47A1), shadow blur/offset. Both renderers multiply the normalized values by their own dp→px density.

## 2. Phone marker

- [x] 2.1 `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt`: replace the hard-offset shadow with a soft blurred shadow (Compose shadow / blur), add the white casing ring, dark accent rim, and vertical gradient core (`Brush.linearGradient`) to `drawCompassArrowWithShadow`; round the tip; keep the tail triangle.
- [x] 2.2 Change the size constant from 56.dp to 32.dp (density-aware via `dp`).

## 3. Android Auto marker

- [x] 3.1 `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` `drawGpsMarker`: size to `32f * density` (was `14f * density`); add tail triangle and rounded tip; add casing path + dark rim; replace hard shadow offset with `BlurMaskFilter` soft shadow.
- [x] 3.2 Add gradient via `LinearGradient` shader (arrow-local coordinates, vertical, light-from-above).

## 4. Tests

- [x] 4.1 Unit tests for vertex/shape math against the shared constants (both modules) — e.g. projected tip/tail positions for sample bearings, casing ≥ core footprint, gradient stop order.
- [x] 4.2 Parity test: phone and AA geometry constants produce the same normalized shape (or same rendered path coordinates) for the same bearing/size.
- [x] 4.3 Existing `AutoMapRendererTest` and phone marker tests keep passing (projection, gliding, off-screen logic unchanged).

## 5. Verification

- [x] 5.1 `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` and `:auto` test/compile via build-app skill (see guidelines/Build.md).
- [x] 5.2 Run unit suites: `./gradlew test` (app + auto) via run-tests skill.
- [x] 5.3 On-device: check marker size/legibility in AA daylight and dark host modes, phone light/dark (projection or AAOS head unit; verify at highway zoom the arrow stays crisp).

## 6. Guidelines

- [x] 6.1 Update `guidelines/UI.md` §8/§9 marker mentions (compass/speed badge sections) with the unified marker style + 32 dp density rule, and note the parity guarantee (specs `gps-location-marker`, `auto-map-renderer`, `cross-variant-ui-parity`).
