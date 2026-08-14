## 1. Native C++: Relax guard in RouteInstructionAgent

- [x] 1.1 Edit `libosmscout/include/osmscout/navigation/RouteInstructionAgent.h` — move position-state guard to only wrap per-update processing, emit `NextRouteInstructionsMessage` on route change regardless of state — move position-state guard (lines 83-87) to only wrap per-update instruction trimming (lines 98-115), not initial route instruction emission (lines 92-96)
- [x] 1.2 Build native libs and verify compilation: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`

## 2. Kotlin: Add showFirstInstructionOnStart flag to NavigationState

- [x] 2.1 Add `showFirstInstructionOnStart: Boolean = false` field to `NavigationState` data class in `NavigationViewModel.kt`
- [x] 2.2 In `startNavigation()`, set `showFirstInstructionOnStart = true` alongside `isNavigating = true`
- [x] 2.3 In `onPositionEstimate()` (first callback after start), set `showFirstInstructionOnStart = false`

## 3. Kotlin: Wire immediate instruction display

- [x] 3.1 Verify `onRouteInstructions()` in `NavigationViewModel` already sets `nextInstruction = instructions[0]` — no change needed if native fix fires `RouteInstructionsMessage` on route change
- [x] 3.2 Confirm `NextTurnOverlay` composable renders non-null `nextInstruction` correctly — no change needed

## 4. Testing

- [x] 4.1 Run existing unit tests: `./gradlew test`
- [x] 4.2 Run existing instrumented tests: `./gradlew connectedAndroidTest`
- [x] 4.3 Manual test: start navigation on a calculated route, verify first turn instruction appears immediately without GPS movement
- [x] 4.4 Manual test: verify subsequent turn instructions still appear at correct distance-based timing
