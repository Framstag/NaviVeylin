## 1. NavigationState — Add isRerouting field

- [x] 1.1 Add `isRerouting: Boolean = false` to `NavigationState` data class in `NavigationViewModel.kt`
- [x] 1.2 In `onRerouteRequest()` callback, set `_state.value = _state.value.copy(isRerouting = true)`
- [x] 1.3 In `onRouteInstructions()` callback, set `_state.value = _state.value.copy(isRerouting = false)`
- [x] 1.4 In `stopNavigation()`, ensure `isRerouting` resets to `false` (already covered by `NavigationState()` reset)

## 2. NavigationStateOverlay — Animated border

- [x] 2.1 Add `isRerouting: Boolean = false` parameter to `NavigationStateOverlay` composable
- [x] 2.2 Add `rememberInfiniteTransition` with `animateFloat` for border alpha pulse (0.3 → 1.0 → 0.3, ~1.5s cycle) when `isRerouting` is true
- [x] 2.3 Add `Modifier.drawBehind {}` on the Card that draws a rounded-rect border stroke with animated alpha when `isRerouting` is true, using `MaterialTheme.colorScheme.primary`
- [x] 2.4 Ensure border respects the Card's existing `RoundedCornerShape(12.dp)`

## 3. Wire in MapCanvasScreen

- [x] 3.1 Pass `navState.isRerouting` to `NavigationStateOverlay` in `MapCanvasScreen.kt`

## 4. Verify

- [x] 4.1 Build debug APK (`./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`)
- [x] 4.2 Run existing unit tests (`./gradlew test`)
