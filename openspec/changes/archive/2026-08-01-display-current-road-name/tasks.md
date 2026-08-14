## 1. ViewModel — road info state + lookup

- [x] 1.1 Add `currentRoadInfo` field to `NavigationState` data class (type: `CurrentRoadInfo?`, default: `null`)
- [x] 1.2 Add `updateRoadInfoFromPosition(lat, lon)` method to `NavigationViewModel` with throttle logic (2s cooldown, ~50m distance skip)
- [x] 1.3 Implement road info lookup: call `client.getDescription(lat, lon, 15)` on `Dispatchers.IO`, parse "General" section entries for "NameRef", "Type", "Name" labels, construct `CurrentRoadInfo`
- [x] 1.4 Wire `updateRoadInfoFromPosition()` call from `onPositionEstimate` callback in `NavigationListener`
- [x] 1.5 Clear `currentRoadInfo` in `stopNavigation()` (reset to null)

## 2. UI — display current road in NavigationStateOverlay

- [x] 2.1 Add `currentRoadInfo: CurrentRoadInfo?` parameter to `NavigationStateOverlay` composable
- [x] 2.2 Add road info row at top of `NavigationStateOverlay` card: show `currentRoadInfo.toDisplayString()` when `hasInfo()` is true, show "Offroad" when null/empty during navigation
- [x] 2.3 Pass `navState.currentRoadInfo` to `NavigationStateOverlay` in `MapCanvasScreen`

## 3. Verify

- [x] 3.1 Build debug APK (`./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`) — no compilation errors
- [x] 3.2 Run unit tests (`./gradlew test`) — all pass
