## 1. ZoomControls — Horizontal layout variant

- [x] 1.1 Add `isLandscape: Boolean = false` parameter to `ZoomControls` composable
- [x] 1.2 When `isLandscape` is true, render as `Row` (zoom in, mag label, zoom out) instead of `Column`
- [x] 1.3 Adjust `Surface` shape and padding for horizontal layout
- [x] 1.4 Verify build compiles with no errors

## 2. MapCanvasScreen — Orientation-aware layout

- [x] 2.1 Wrap overlay controls in `BoxWithConstraints` to detect landscape (`maxWidth > maxHeight`)
- [x] 2.2 In portrait: keep existing top-right `Column` with `statusBarsPadding()` — no change
- [x] 2.3 In landscape: remove top-right `Column` and `statusBarsPadding()` from overlay area
- [x] 2.4 In landscape: place menu, compass, search, favorites stacked at top-right (no statusBarsPadding)
- [x] 2.5 In landscape: place zoom controls (horizontal) at bottom-right
- [x] 2.6 In landscape: place MyLocation re-center button at bottom-right below zoom
- [x] 2.7 In landscape: place location options overlay at bottom-right above zoom
- [x] 2.8 Verify build compiles with no errors

## 3. Verification

- [x] 3.1 Run `./gradlew :app:assembleDebug` — build succeeds
- [x] 3.2 Run `./gradlew test` — existing unit tests pass
- [x] 3.3 Verify portrait layout is unchanged (regression check)
- [x] 3.4 Verify landscape layout has no controls in top 48dp safe zone
- [x] 3.5 Verify zoom controls render horizontally in landscape
- [x] 3.6 Verify zoom + favorites are side-by-side in landscape
