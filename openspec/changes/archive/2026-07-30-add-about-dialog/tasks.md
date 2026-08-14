## 1. Create About Dialog Composable

- [x] 1.1 Create `app/src/main/java/com/naviveylin/ui/about/AboutDialog.kt` with Material 3 `AlertDialog` composable
- [x] 1.2 Display app name "NaviVeylin" and version from `BuildConfig.VERSION_NAME`
- [x] 1.3 Add brief app description text explaining it's an Android navigation app built on libosmscout
- [x] 1.4 Add link to project repository (github.com/Framstag/libosmscout) for source code and license information
- [x] 1.5 Add dismiss/close button and support back press dismissal

## 2. Wire Menu Item in MapCanvasScreen

- [x] 2.1 Add `showAboutDialog` state variable to `MapCanvasScreen`
- [x] 2.2 Add "About" `DropdownMenuItem` to the existing overflow menu (⋮)
- [x] 2.3 Wire menu item click to set `showAboutDialog = true`
- [x] 2.4 Conditionally compose `AboutDialog` when `showAboutDialog` is true, passing dismiss callback

## 3. Verify Build

- [x] 3.1 Run `./gradlew :app:assembleDebug` and confirm compilation succeeds
- [x] 3.2 Run `./gradlew test` and confirm existing tests pass
