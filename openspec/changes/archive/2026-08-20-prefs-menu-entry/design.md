## Context

See proposal.md — Why.

- `:auto` depends only on `:core` and `:osmscout-client-java`; it cannot reference `:app`'s `SettingsStorage`/`AppSettings`.
- Existing bridge pattern: `AutoEntryPoint` (Hilt `@EntryPoint` in `:core`) exposes provider interfaces; `:app`'s `AutoServiceModule` provides implementations (`AutoFavoritesProvider`, `AutoSearchProvider`, `AutoLocationProvider`, `AutoClientProvider`, `AutoNavigationController`).
- AA screens resolve `AutoEntryPoint` via `EntryPointAccessors.fromApplication` and load data in a coroutine, then `invalidate()` (see `FavoritesScreen`).
- `AppSettings` (JSON at `filesDir/maps/settings.json`) holds `followMode`, `autoZoomEnabled`, `freeFormNorthUp`, `navNorthUp`, `keepScreenOn`, `darkMode`, `laneHintsEnabled`, `renderMode`. `SettingsStorage.load()`/`save()` are suspend functions.
- Car template constraint: `PaneTemplate` rows cannot carry click listeners; list-based templates (`SectionedItemTemplate` + `RowSection`) allow them (RootScreen regression history).

## Goals / Non-Goals

**Goals:**
- Expose shared settings to the AA process via a `:core` interface + `:app` Hilt implementation — no `:app` dependency in `:auto`.
- Preferences screen in AA: view current values, toggle, persist.
- Keep the `AppSettings` JSON schema and `SettingsStorage` unchanged.

**Non-Goals:**
- Wiring AA behavior (map follow mode, auto-zoom, dark mode, lane hints, render mode) to these settings — AA screens keep current behavior; consumption is a follow-up change.
- New settings or a settings UI in the phone app.
- `keepScreenOn` in the car (car screen is always on).

## Decisions

1. **`AutoSettingsProvider` in `:core`, impl in `:app`** — follows the existing `AutoFavoritesProvider`/`AutoSearchProvider` pattern; `SettingsStorage` needs Android `Context` + kotlinx-serialization, so `:core` stays lean and storage stays in `:app`. Alternative considered: move `AppSettings`/`SettingsStorage` to `:core` — rejected (churn, `:core` gains Android deps).
2. **`AutoSettings` mirror type in `:core`** — `:core` cannot reference `:app` types. `darkMode`/`renderMode` carried as `String` values, mapped to/from the app enums in the `:app` impl. Alternative: duplicate the enums in `:core` — rejected (more mapping surface).
3. **Shown settings: all except `keepScreenOn`** — `followMode`, `autoZoomEnabled`, `freeFormNorthUp`, `navNorthUp`, `darkMode`, `laneHintsEnabled`, `renderMode`. `keepScreenOn` is meaningless on a car screen.
4. **`SectionedItemTemplate` with value-text rows, tap toggles** — car templates have no native toggle row; matches `RootScreen`/`FavoritesScreen`; rows with click listeners require a list-based template.
5. **`PreferencesScreen` resolves `AutoEntryPoint` internally** (like `FavoritesScreen`); a pure `PreferencesScreenMapper` maps `AutoSettings` → rows for unit testing (like `FavoritesScreenMapper`).

## Risks / Trade-offs

- [Concurrent phone + car writes to `settings.json`] → last-write-wins; `SettingsStorage` writes are atomic (temp file + rename); acceptable for low-frequency settings changes.
- [Mirror type drift (`AutoSettings` vs `AppSettings`)] → keep the subset small; mapping unit-tested in `:app`.
- [Car template constraint violations] → list-based template only; mapper tests assert structure.
- [AA behavior not yet wired to the settings] → documented non-goal; follow-up change.

## Migration Plan

New feature — no migration. Rollback: remove `autoSettingsProvider()` from `AutoEntryPoint`, the `PreferencesScreen`, and the `RootScreen` row.

## Open Questions

None.
