## 1. ZoomControls — Add elevation

- [x] 1.1 Add `elevation` parameter to both `FilledIconButton` calls in `ZoomControls.kt` using `IconButtonDefaults.filledIconButtonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp)`

## 2. LocationOptionsOverlay — Add elevation

- [x] 2.1 Add `elevation` parameter to `FilledTonalIconButton` in `LocationOptionsOverlay.kt` using `IconButtonDefaults.filledTonalIconButtonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp)`

## 3. MapCanvasScreen — Add elevation to top-right buttons

- [x] 3.1 Add `elevation` parameter to menu `IconButton` in `MapCanvasScreen.kt` using `IconButtonDefaults.iconButtonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp)`
- [x] 3.2 Add `elevation` parameter to search `FilledTonalIconButton` in `MapCanvasScreen.kt` using `IconButtonDefaults.filledTonalIconButtonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp)`
- [x] 3.3 Add `elevation` parameter to favorites `FilledTonalIconButton` in `MapCanvasScreen.kt` using `IconButtonDefaults.filledTonalIconButtonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp)`

## 4. Verify

- [x] 4.1 Build debug APK (`./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`) compiles without errors
- [x] 4.2 Run unit tests (`./gradlew test`) — all pass
