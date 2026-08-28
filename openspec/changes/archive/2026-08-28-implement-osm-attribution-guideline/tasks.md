## 1. String resources

- [x] 1.1 Add string resources for attribution text ("© OpenStreetMap contributors"), ODbL statement, "(i)" content description, and licence dialog title/link label to `app/src/main/res/values/strings.xml` and verify `./gradlew :app:assembleMobileDebug` compiles

## 2. Attribution overlay

- [x] 2.1 Create `OsmAttributionOverlay` composable in `app/src/main/java/com/naviveylin/ui/` showing the attribution text in the bottom-right corner of the map canvas with a semi-transparent scrim, and verify it renders in a Compose preview
- [x] 2.2 Wire the overlay into the map canvas screen (`MapCanvasScreen`) so it is visible on map load, and verify via a Compose UI test that the attribution text is displayed
- [x] 2.3 Make the attribution text tappable to open https://www.openstreetmap.org/copyright via an `ACTION_VIEW` intent, and verify with a unit test that the intent is launched with the correct URI

## 3. Collapse behaviour

- [x] 3.1 Implement auto-hide: fade the attribution out after 5 seconds of no map interaction, and verify with a Compose UI test using a test clock that the notice transitions to hidden
- [x] 3.2 Re-show the attribution on map pan/zoom interaction (reset the timer), and verify with a Compose UI test that interaction makes the notice visible again

## 4. Licence access

- [x] 4.1 Add an "(i)" icon button next to the attribution that opens a licence dialog showing the attribution text, the ODbL statement, and a link to https://www.openstreetmap.org/copyright, and verify with a Compose UI test that the dialog opens and shows the licence text
- [x] 4.2 Verify the licence info is reachable when the attribution is hidden (the "(i)" button remains visible), via a Compose UI test

## 5. About dialog

- [x] 5.1 Add an OSM data licence link (openstreetmap.org/copyright + ODbL statement) to the About dialog, and verify with a Compose UI test that the link is shown and launches the correct URI

## 6. Verification

- [x] 6.1 Run `./gradlew :app:test` and confirm all new attribution/licence tests pass
- [x] 6.2 Build both flavors (`./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`) and confirm the attribution renders on the map in both

## 7. Android Auto variant

- [x] 7.1 Create `SurfaceAttribution` drawer in `:auto` drawing "© OpenStreetMap contributors" at the bottom-right of the map surface, and verify with a geometry unit test
- [x] 7.2 Wire the attribution into `FreeDrivingScreen` and `NavigationScreen` overlayDrawer, and verify via screen tests that the drawer is invoked
- [x] 7.3 Add OSM licence section (ODbL statement + link action) to the car `AboutScreen`, and verify with a screen test that the licence rows/action are present
- [x] 7.4 Add an "(i)" info action to the map action strips opening the About screen, and verify with `MapStripActionsTest`
- [x] 7.5 Run `:auto` unit tests and build both flavors
