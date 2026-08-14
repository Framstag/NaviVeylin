## Why

Map downloads are unreliable and confusing. Screen may turn off or app may be hibernated mid-download, progress bar only updates on manual rerender, installed maps disappear after refresh, and refreshed maps bury installed entries — making deletion of unwanted maps unnecessarily difficult.

## What Changes

- **Screen-on + wake lock**: Acquire a wake lock and keep screen on during active map downloads to prevent interruption
- **Progress bar auto-update**: Fix progress bar so it updates reactively during download, not only on manual rerender
- **Installed maps always visible**: Show previously downloaded maps in the tree immediately on screen open, even without a provider refresh
- **Refreshed maps keep installed on top**: After refresh, installed maps remain visible at the top of the tree for easy access and deletion

## Capabilities

### New Capabilities
- `download-wake-lock`: Keep screen on and prevent app hibernation/sleep during active map downloads

### Modified Capabilities
- `map-download-ui`: Progress bar updates reactively during download; installed maps always visible without refresh; refreshed maps keep installed entries on top
- `map-download-infrastructure`: Add wake lock integration to download lifecycle (acquire on download start, release on complete/error/cancel)

## Impact

- `app/src/main/java/com/naviveylin/ui/mapmanager/MapManagerViewModel.kt` — Fix progress state propagation; ensure installed maps always populate tree; keep installed entries on top after refresh
- `app/src/main/java/com/naviveylin/ui/mapmanager/MapManagerScreen.kt` — Fix `LinearProgressIndicator` recomposition; reorder tree so installed maps appear on top
- `app/src/main/java/com/naviveylin/di/MapDownloadModule.kt` — May need wake lock dependency provision
- Android manifest — May need `WAKE_LOCK` permission if not already declared
- No new native/JNI changes required
