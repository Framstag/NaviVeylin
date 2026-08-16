## 1. Icon size and shadow alignment

- [x] 1.1 Shrink compass button to match other overlay buttons: 40dp `FilledTonalIconButton`-style sizing with 24dp icon area (spec: "Compass icon matches overlay button sizing")
- [x] 1.2 Verify compass shadow matches other overlay buttons (`shadow(3.dp, RoundedCornerShape(16.dp))`) (spec: "Compass icon matches overlay button sizing")

## 2. GPS fix quality via button fill color

- [x] 2.1 Replace the outer GPS fix status ring with the button fill (background) color: light red (no fix), light yellow (poor, >50m), light green (good, ≤50m) (spec: "GPS fix status fill color")
- [x] 2.2 Remove the ring stroke drawing code (`drawCompassRing`) (spec: "GPS fix status fill color")

## 3. Follow-direction triangle needle

- [x] 3.1 Replace the follow-direction line-and-arrowhead indicator with a compass-needle-like triangle pointing in the travel direction, base line smaller than height (spec: "Compass visually differentiates orientation modes")

## 4. Tests

- [x] 4.1 Add Compose unit test for `CompassButton` covering short-press (re-center) and long-press (toggle orientation) callbacks (spec: compass-button)
- [x] 4.2 Run unit tests: `./gradlew test`

## 5. Build verification

- [x] 5.1 Build debug APK: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`
- [x] 5.2 Manual visual check on device/emulator: icon size matches menu/search buttons, fill color reflects GPS quality, follow-direction triangle readable
