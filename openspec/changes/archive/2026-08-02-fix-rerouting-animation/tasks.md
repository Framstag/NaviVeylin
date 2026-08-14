## 1. State Model — Add `isOffRoute` to NavigationState

- [x] 1.1 Add `isOffRoute: Boolean = false` field to `NavigationState` data class in `NavigationViewModel.kt`
- [x] 1.2 Set `isOffRoute = true` in `onRerouteRequest` alongside existing `isRerouting = true`
- [x] 1.3 Set `isOffRoute = false` in `onRouteInstructions` alongside existing `isRerouting = false`
- [x] 1.4 Set `isOffRoute = false` in `stopNavigation` alongside existing state reset
- [x] 1.5 Verify build compiles: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`

## 2. Animation Fix — Restructure `NavigationStateOverlay.kt`

- [x] 2.1 Remove `rememberInfiniteTransition`, `animateFloat`, and `drawBehind` border from `NavigationStateOverlay.kt`
- [x] 2.2 Replace animated border with solid red tint overlay (controlled by `isOffRoute`)
- [x] 2.3 Remove unused animation imports
- [x] 2.4 Verify build compiles: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`
- [x] 2.5 Verify build compiles: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`

## 3. Off-Route Red Tint + Auto-Reroute

- [x] 3.1 Add `routeCalculatedEvent` SharedFlow to `RoutePanelViewModel`
- [x] 3.2 Collect `routeCalculatedEvent` in `NavigationViewModel.setRoutePanelViewModel`; auto-start navigation on new route when reroute confirmed
- [x] 3.3 Pass `forceFollowMode=false` for reroute auto-start to avoid map rotation / snap jump
- [x] 3.4 Stop old native controller in `startNavigation()` before creating new one to prevent double callbacks / marker jumps
- [x] 3.5 Clear `isRerouting`/`isOffRoute` in `onRouteInstructions` when new route is active
- [x] 3.6 Verify build compiles: `./gradlew :app:assembleDebug`

## 4. Reroute Confirmation + Tunnel Guard

- [x] 4.1 Increase confirmation counter to 5 calls within 60s window
- [x] 4.2 Track `lastTunnelOrNoSignalTime` from `NavigationPosition.state`
- [x] 4.3 Ignore `onRerouteRequest` for 30s after `EstimateInTunnel` or `NoGpsSignal`
- [x] 4.4 Re-add `lastGpsAccuracy` and ignore reroute when accuracy > 100m
- [x] 4.5 Reset confirmation/tunnel/accuracy state in `startNavigation` and `stopNavigation`
- [x] 4.6 Remove verbose `onPositionEstimate` logging
- [x] 4.7 Remove render/per-frame logs from `MapRenderer`, `MapCanvasViewModel`, `MapCanvasScreen`, `LocationMarkerOverlay`, native `OSMScoutClient`
- [x] 4.8 Add minimum 30s off-route duration before reroute proceeds
- [x] 4.9 Run unit tests: `./gradlew test`

## 5. Verification

- [x] 5.1 Run unit tests: `./gradlew test`
- [x] 5.2 Run full debug build (all ABIs): `./gradlew :app:assembleDebug`
- [x] 5.3 Manual smoke test: GPX track through tunnel, verify no false reroute / marker jump
- [x] 5.4 Manual smoke test: deliberately leave route, verify auto-reroute after ~25s
