# Tasks: GPS provider strict fallback

Spec: `gps-provider-selection` (specs/gps-provider-selection/spec.md)
Design: design.md

## 1. Implementation

- [x] 1.1 Gate `startManagerUpdates()` behind `!useFusedProvider` in `app/src/main/java/com/naviveylin/location/LocationService.kt` so Fused and LocationManager never run simultaneously (spec: "No parallel provider operation"); verify `./gradlew :app:compileMobileDebugKotlin` compiles
- [x] 1.2 Add injectable seam for the Play Services availability decision (constructor param `playServicesAvailable: Boolean` defaulting to the runtime `GoogleApiAvailability` check) so both provider branches are unit-testable (design: Decision 3); verify `./gradlew :app:compileMobileDebugKotlin` compiles
- [x] 1.3 Keep `shouldEmit` dedupe unchanged and confirm it still guards the LocationManager-only path where GPS/NETWORK/PASSIVE run in parallel (spec: "Duplicate fixes filtered on fallback path"); verify no diff beyond the gating change

## 2. Unit Tests

- [x] 2.1 Add Robolectric test: with Play Services available, `startLocationUpdates()` requests updates from the Fused client and never calls `LocationManager.requestLocationUpdates` (spec: "Fused provider preferred when Play Services available"); verify test passes
- [x] 2.2 Add Robolectric test: without Play Services, `startLocationUpdates()` requests updates from LocationManager GPS/NETWORK/PASSIVE providers and never initializes the Fused client (spec: "LocationManager strict fallback without Play Services"); verify test passes
- [x] 2.3 Add Robolectric test: `shouldEmit` drops a duplicate fix (same timestamp + position) and passes distinct fixes through (spec: "Duplicate fixes filtered on fallback path"); verify test passes
- [x] 2.4 Run `./gradlew test` and verify the full unit-test suite passes (existing tests unaffected)

## 3. Build & Device Verification

- [x] 3.1 Run `./gradlew :app:assembleMobileDebug` and verify the build compiles without errors
- [x] 3.2 On a GMS phone: `adb logcat -s LocationService` shows Fused requested and no `LocationManager onLocationChanged` lines; verify marker no longer jumps between raw and smoothed fixes in free driving mode
- [x] 3.3 On a GMS-less device (or emulator without Play Services): `adb logcat -s LocationService` shows LocationManager providers requested and fixes flowing; verify no regression
