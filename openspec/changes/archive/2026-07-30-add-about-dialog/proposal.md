## Why

NaviVeylin lacks an About dialog. Users have no way to see app version, credits, or open-source licenses. Standard Android convention requires an accessible About screen.

## What Changes

- Add "About" item to existing overflow menu (⋮) in `MapCanvasScreen`
- Create `AboutDialog` composable — standard Android about dialog with app name, version, description, and licenses link
- Wire menu item to show/hide the dialog

## Capabilities

### New Capabilities
- `about-dialog`: Standard Android about dialog showing app name, version, description, and open-source licenses link, reachable from the map screen overflow menu

### Modified Capabilities

<!-- None — no existing spec changes -->

## Impact

- **New file**: `app/src/main/java/com/naviveylin/ui/about/AboutDialog.kt` — composable dialog
- **Modified file**: `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — add menu item + dialog state
- **No new dependencies** — uses Material 3 `AlertDialog` already in project
- Version info sourced from `BuildConfig` (versionName = "1.0.0", versionCode = 1)
