## 1. Extract `:core` shared module

- [x] 1.1 Create `core/` directory with `build.gradle.kts` (Android library, no Compose, no resources)
- [x] 1.2 Move `NavigationState` data class from `:app` to `:core` module
- [x] 1.3 Create `NavigationViewModel` interface in `:core` exposing `state: StateFlow<NavigationState>` and `stopNavigation()`
- [x] 1.4 Create `@HiltEntryPoint` interface `AutoEntryPoint` in `:core` exposing `NavigationViewModel`
- [x] 1.5 Add `:core` to `settings.gradle.kts`
- [x] 1.6 Update `:app/build.gradle.kts` to depend on `:core`
- [x] 1.7 Make `NavigationViewModel` in `:app` implement the `:core` interface
- [x] 1.8 Add `totalDistance: Double` field to `NavigationState`, set in `NavigationViewModel.startNavigation()`
- [x] 1.9 Verify `:app` compiles and existing tests pass after refactor

## 2. Update `:auto` module dependencies

- [x] 2.1 Add `implementation(project(":core"))` to `:auto/build.gradle.kts`
- [x] 2.2 Add `implementation("com.google.dagger:hilt-android")` to `:auto/build.gradle.kts` (for `@HiltEntryPoint` access)
- [x] 2.3 Verify `:auto` module compiles

## 3. Implement NavigationSession

- [x] 3.1 Create `NavigationSession` extending `Session` in `:auto`
- [x] 3.2 Implement `onCreateScreen()` returning `NavigationScreen` when navigating, fallback screen otherwise
- [x] 3.3 Wire `@HiltEntryPoint` to inject `NavigationViewModel` via `CarContext.getApplication()`
- [x] 3.4 Handle screen lifecycle — start/stop state observation in `onCreate`/`onDestroy`

## 4. Implement NavigationScreen with NavigationTemplate

- [x] 4.1 Create `NavigationScreen` extending `Screen` in `:auto`
- [x] 4.2 Collect `NavigationViewModel.state` in screen's coroutine scope
- [x] 4.3 Build `NavigationTemplate` with next turn instruction + distance
- [x] 4.4 Add `Trip` with progress bar (remaining / total distance)
- [x] 4.5 Add `DestinationTravelEstimate` with ETA, remaining distance, remaining time
- [x] 4.6 Add current speed display
- [x] 4.7 Add lane guidance via `NavigationTemplate.Lane` builder
- [x] 4.8 Add rerouting indicator (show when `NavigationState.isRerouting` is true)
- [x] 4.9 Add stop navigation `Action` wired to `NavigationViewModel.stopNavigation()`
- [x] 4.10 Throttle `invalidate()` calls — only re-render when displayed fields actually change
- [x] 4.11 Create fallback `Screen` for non-navigating state (simple message or empty)

## 5. Update CarAppService

- [x] 5.1 Replace `NaviVeylinCarAppService.onCreateSession()` to return `NavigationSession`
- [x] 5.2 Verify manifest still correctly declares `CarAppService` intent filter

## 6. Testing

- [x] 6.1 Write unit tests for `NavigationScreen` state mapping (NavigationState → NavigationTemplate fields)
- [x] 6.2 Write unit test verifying `totalDistance` is set on `startNavigation()`
- [x] 6.3 Write unit test verifying lifecycle cleanup (observers stop on `onDestroy`)
- [ ] 6.4 Test on Android Auto Desktop Head Unit emulator — verify turn-by-turn appears during phone navigation
- [ ] 6.5 Test connect/disconnect mid-navigation — verify no crash, no leak
- [x] 6.6 Verify `:app` navigation still works correctly (no regressions)
- [x] 6.7 Verify `./gradlew :app:assembleDebug` compiles without errors
- [x] 6.8 Verify `./gradlew :auto:assembleDebug` compiles without errors
