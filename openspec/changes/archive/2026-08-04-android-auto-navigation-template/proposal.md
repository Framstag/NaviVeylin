# Proposal: Android Auto NavigationTemplate (Phase 1)

## Problem

NaviVeylin has a full turn-by-turn navigation engine — routing, GPS tracking, next turn display, ETA, speed, lane guidance, auto-rerouting — but it only works on the phone screen. When users connect to Android Auto, the `:auto` module throws `UnsupportedOperationException`. The car screen shows nothing.

## Goal

Surface existing navigation state to Android Auto via `NavigationTemplate`. When the user is navigating on the phone, the car head unit displays:

- Next turn instruction + distance
- ETA, remaining distance, remaining time
- Current road name
- Current speed
- Lane guidance
- Trip progress bar
- Rerouting indicator
- Stop navigation button

No new navigation features. No map rendering. Just wire existing `NavigationState` into Android Auto's template API.

## Why Now

All dependencies are in place:
- `:auto` module exists with `CarAppService` + manifest
- `androidx.car.app:app:1.7.0` already declared
- `NavigationViewModel` emits full `NavigationState` via `StateFlow`
- `NavigationState` contains every field `NavigationTemplate` needs

The gap is purely plumbing: create `Session` → `Screen` → `Template` and observe the existing state.

## Scope

**In scope:**
- `NaviVeylinCarAppService.onCreateSession()` returns real `Session`
- `NavigationSession` — lifecycle-aware session hosting Auto screens
- `NavigationScreen` — returns `NavigationTemplate` populated from `NavigationState`
- Wire stop-navigation action back to `NavigationViewModel`
- Wire lane guidance into `NavigationTemplate.Lane`
- Wire rerouting state into `NavigationTemplate` (rerouting indicator)
- Add `Trip` progress bar using remaining / total distance
- Handle `CarContext` lifecycle (start/stop observation on screen visible/hidden)
- Module dependency: `:auto` depends on `:app` (or shared state module)
- Hilt integration: inject `NavigationViewModel` into Auto `Session`/`Screen`

**Out of scope:**
- Search on car (Phase 2)
- Favorites on car (Phase 2)
- Map rendering on car (Phase 3)
- Deep linking / cross-device continuity (Phase 4)
- Voice guidance

## Impact

### Files to create

| File | Purpose |
|------|---------|
| `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` | `Session` subclass, manages screen stack + lifecycle |
| `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` | `Screen` returning `NavigationTemplate` |
| `auto/src/main/java/com/naviveylin/auto/di/AutoModule.kt` | Hilt module providing Auto dependencies |

### Files to modify

| File | Change |
|------|--------|
| `auto/src/main/java/com/naviveylin/auto/NaviVeylinCarAppService.kt` | Replace `onCreateSession()` stub with real `NavigationSession` creation |
| `auto/build.gradle.kts` | Add `implementation(project(":app"))` dependency |
| `auto/src/main/AndroidManifest.xml` | May need `meta-data` for Car App API (verify) |

### Files unchanged

- All `:app` ViewModels, services, repositories — consumed as-is
- `settings.gradle.kts` — `:auto` already included
- Native code — no changes needed

## Risks

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| `:auto` depending on `:app` creates circular dependency | Low | `:app` doesn't depend on `:auto`. One-way dependency is safe. |
| Hilt can't inject `NavigationViewModel` into Auto `Session` (not a Compose `ViewModelStoreOwner`) | Medium | Use `EntryPoint` accessor or `CarViewModel` pattern. `Session` has `CarContext` which can reach app-level singletons. |
| `NavigationTemplate` requires minimum Car App API version not met on some head units | Low | API 1.7+ supports `NavigationTemplate`. Target API 1.7 (already declared). |
| State observation in Auto `Screen` causes memory leak | Low | Use `Lifecycle`-scoped collection via `repeatOnLifecycle` in `Screen`'s `onCreate`/`onDestroy`. |

## Decisions

| Question | Decision |
|----------|----------|
| Module dependency strategy | Extract shared `:core` module. `:auto` and `:app` both depend on `:core`. Contains `NavigationState`, shared interfaces, and Hilt entry points. |
| Hilt injection into Session | Use `@HiltEntryPoint` pattern. `Session` accesses app-scoped singletons via `CarContext` → `Application` → `EntryPointAccessors`. |
| Trip progress total distance | Store total route distance in `NavigationState` when navigation starts. Derive from `RouteEntry` or calculate from route geometry. |

## Success Criteria

- Android Auto head unit emulator shows turn-by-turn navigation when phone is navigating
- Next turn instruction + distance updates in real-time
- ETA, remaining distance, speed display correctly
- Lane guidance appears when available
- Stop navigation button works
- Rerouting state shows indicator on car screen
- No crashes when car connects/disconnects mid-navigation
- No memory leaks (verified via `onDestroy` cleanup)
