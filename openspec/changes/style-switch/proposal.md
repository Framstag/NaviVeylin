# Proposal: style-switch

## Why

The app currently renders every map with the single `standard.oss` stylesheet.
libosmscout ships several alternative top-level styles (`cycle.oss`,
`winter-sports.oss`, `motorways.oss`, ...) suited to different use cases, but
they are invisible to the user: the APK already contains them (the
`syncSubmoduleStylesheets` task copies the whole submodule stylesheet
directory), yet there is no way to select one.

The native side already supports dynamic switching — `OSMScoutClient.SetStyle`
loads a stylesheet by name on the database thread with rollback to the
previous style on parse errors, and `getAvailableStyleSheets()` enumerates the
top-level `*.oss` files. What is missing is the plumbing and UI: a persisted
setting, a settings entry on the phone, a settings entry in the Android Auto
car app, and applying the selection to the renderer.

## What Changes

- **Bundle all top-level `*.oss` stylesheets.** Already true via
  `syncSubmoduleStylesheets` (whole-directory copy) + `AssetCopier`; add a
  guard/test so a future submodule bump cannot silently shrink the bundled
  set. All top-level `*.oss` files are included in addition to `standard.oss`.
- **Persist the selected style.** New `styleSheet` field in `AppSettings`
  (default `standard`), stored via the existing `SettingsStorage`.
- **Phone settings entry.** A map-style picker in the phone settings UI listing
  every bundled style; display name is the file name without the `.oss`
  postfix (e.g. `cycle.oss` → `cycle`).
- **Android Auto settings entry.** The same picker in the car app's
  `PreferencesScreen`, backed by the shared persisted setting (via
  `AutoSettingsMapping`).
- **Apply the selection.** On app start and whenever the setting changes,
  call `SetStyle(name)` on the renderer; failed loads keep the previous style
  (existing native behavior) and surface the error.
- **Apply on AA session start.** The car app applies the persisted style when
  a navigation session begins, so a phone-side change takes effect in the car
  on the next session.

## Capabilities

### New Capabilities

- `map-styles`: user-selectable map stylesheet — enumeration of bundled styles,
  persisted selection, application to the renderer, and settings entries on
  phone and Android Auto.

### Modified Capabilities

- (none — no existing capability covers stylesheet selection)

## Impact

- **App settings**: `AppSettings` / `SettingsStorage.kt` gain `styleSheet`;
  `AutoSettingsMapping.kt` maps it to the car-side settings.
- **App UI**: new phone settings entry (settings UI currently has no map-style
  picker); `MapCanvasViewModel` applies the style via `OSMScoutClient.SetStyle`
  and observes changes.
- **Android Auto**: `PreferencesScreen` in `:auto` gains the picker; car
  session start applies the stored style.
- **Native**: no changes required — `SetStyle`, `getAvailableStyleSheets` and
  error rollback already exist in the JNI bridge.
- **Build**: `syncSubmoduleStylesheets` unchanged (already bundles everything);
  add verification that all top-level `*.oss` are packaged.
- **Tests**: `AppSettings`/`SettingsStorage` round-trip, `AutoSettingsMapping`
  coverage, picker rendering on phone and AA, style application on the fake
  JNI client.

## Open Questions

- `basemap-render.oss` drives the basemap layer rendering. Decide during design
  whether it belongs in the user-facing picker or stays internal (the
  requirement lists all top-level `*.oss`, but switching the basemap layer
  style may be undesirable).
- Some stylesheets may require stylesheet flags (e.g. `daylight`) for the
  intended look; whether the picker needs to imply flags is deferred to design.
