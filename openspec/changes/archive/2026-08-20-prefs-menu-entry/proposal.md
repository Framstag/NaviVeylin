## Why

Android Auto users cannot adjust navigation preferences (follow mode, auto-zoom, dark mode, lane hints, render mode) from the car. The phone app persists these in `AppSettings` via `SettingsStorage`, but the AA root screen offers no way to view or change them — settings are phone-only today.

## What Changes

- Add a "Preferences" menu entry to the Android Auto `RootScreen` (alongside Map, Search, Favorites, Diagnostics, About).
- Add a new `PreferencesScreen` in `:auto` that lists the settings from `AppSettings` and lets the driver toggle them.
- Expose settings to the AA process via a new `AutoSettingsProvider` interface in `:core`, added to `AutoEntryPoint`, with a Hilt implementation in `:app` wrapping the existing `SettingsStorage` (same pattern as `AutoFavoritesProvider`/`AutoSearchProvider`).
- Settings changes made in the car persist to the same `settings.json` file the phone app reads, so preferences stay in sync across phone and car.
- Only settings that make sense on the car are shown (e.g., follow mode, auto-zoom, north-up, dark mode, lane hints, render mode); phone-only settings (e.g., keep-screen-on) are excluded.

## Capabilities

### New Capabilities
- `auto/preferences`: Android Auto preferences screen — a "Preferences" entry on the AA root screen that displays and edits shared `AppSettings` values, persisted through the same `SettingsStorage` the phone app uses.

### Modified Capabilities
- `auto`: the AA root screen gains a Preferences entry; the AA session can now read/write shared settings. (Requirement-level change to the existing `auto` spec's root-screen behavior.)

## Impact

- `:core`: new `AutoSettingsProvider` interface; `AutoEntryPoint` gains `autoSettingsProvider()`.
- `:app`: `AutoServiceModule` provides the `AutoSettingsProvider` implementation backed by `SettingsStorage`.
- `:auto`: new `PreferencesScreen`; `RootScreen` gains a Preferences row; new unit tests for the screen and provider mapping.
- No changes to `SettingsStorage`/`AppSettings` schema — existing JSON file format reused.
- No new dependencies.
