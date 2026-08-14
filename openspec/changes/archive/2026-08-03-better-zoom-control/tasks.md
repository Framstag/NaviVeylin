## 1. Rewrite ZoomControls composable

- [x] 1.1 Replace `Column` layout with `Row` inside a pill-shaped `Surface` container
- [x] 1.2 Add `+` button (FilledIconButton with Add icon) at start of row, greyed when `canZoomIn = false`
- [x] 1.3 Add centered zoom level `Text` between buttons using same surface color
- [x] 1.4 Add `-` button (FilledIconButton with Remove icon) at end of row, greyed when `canZoomOut = false`
- [x] 1.5 Apply `shadow` modifier to the pill container (3.dp elevation, RoundedCornerShape)
- [x] 1.6 Use `surfaceContainerHigh` for pill background, matching existing zoom control colors

## 2. Verify build and tests

- [x] 2.1 Run `./gradlew :app:assembleDebug` and confirm no compilation errors
- [x] 2.2 Run `./gradlew test` and confirm all existing tests pass
