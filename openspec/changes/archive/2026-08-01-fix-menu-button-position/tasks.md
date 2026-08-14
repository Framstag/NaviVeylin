## 1. Restructure MapCanvasScreen Layout

- [x] 1.1 Remove `Scaffold` wrapper from `MapCanvasScreen.kt` — replace with plain `Box`
- [x] 1.2 Move `SnackbarHost` into the Box with `Modifier.align(Alignment.BottomCenter)`
- [x] 1.3 Remove `.padding(scaffoldPadding)` from the Box wrapping Canvas — Canvas uses `Modifier.fillMaxSize()` directly
- [x] 1.4 Keep `statusBarsPadding()` on overlay Column (menu/search/favorites/zoom) — single correct inset

## 2. Verify

- [x] 2.1 Build debug APK — `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`
- [x] 2.2 Run unit tests — `./gradlew test`
