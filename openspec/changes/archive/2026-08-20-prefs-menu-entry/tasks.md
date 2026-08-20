## 1. Shared settings model (:core)

- [x] 1.1 Define `AutoSettings` data class in `:core` mirroring the car-relevant `AppSettings` fields (`followMode`, `autoZoomEnabled`, `freeFormNorthUp`, `navNorthUp`, `darkMode`, `laneHintsEnabled`, `renderMode`; `darkMode`/`renderMode` as `String`)
- [x] 1.2 Define `AutoSettingsProvider` interface in `:core` with `suspend fun load(): AutoSettings` and `suspend fun save(settings: AutoSettings)`
- [x] 1.3 Add `autoSettingsProvider(): AutoSettingsProvider` to `AutoEntryPoint`

## 2. Hilt provider (:app)

- [x] 2.1 Implement `AutoSettingsProvider` in `AutoServiceModule` backed by `SettingsStorage` (load/save `AppSettings`, map to/from `AutoSettings`)
- [x] 2.2 Unit test `AppSettings` ↔ `AutoSettings` mapping (all fields + defaults)

## 3. Preferences screen (:auto)

- [x] 3.1 Create `PreferencesScreenMapper` mapping `AutoSettings` → rows (title + current value text, e.g. "Follow mode: On")
- [x] 3.2 Create `PreferencesScreen` extending `Screen`: resolve `AutoEntryPoint`, load settings in a coroutine, render `SectionedItemTemplate`, back navigation, "Preferences" header
- [x] 3.3 Row tap toggles the value → save via provider → `invalidate()`
- [x] 3.4 Add "Preferences" row to `RootScreen` (alongside Map, Search, POI, Favorites, Diagnostics, About) and update `RootScreenTest` expected item count
- [x] 3.5 Unit tests: mapper (row per setting, current values, `keepScreenOn` absent), screen template builds without throwing, toggle calls `save()` with flipped value

## 4. Build verification

- [x] 4.1 `./gradlew :core:assembleDebug` compiles
- [x] 4.2 `./gradlew :auto:assembleDebug` compiles
- [x] 4.3 `./gradlew :app:assembleDebug` compiles
- [x] 4.4 `./gradlew :auto:testDebugUnitTest :app:testDebugUnitTest` — new tests pass, existing tests pass
