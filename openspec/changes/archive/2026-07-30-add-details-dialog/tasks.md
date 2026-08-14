## 1. JNI — Implement `getDescription()` in OSMScoutClient.cpp

- [x] 1.1 Add `getDescription` JNI function to `OSMScoutClient.cpp`: accept `(JNIEnv*, jobject, jdouble lat, jdouble lon)`, return `jobject` (ObjectDescription)
- [x] 1.2 Implement candidate collection: get DBThread databases via `RunSynchronousJob`, query objects (nodes/ways/areas) in ~50m bounding box around (lat, lon)
- [x] 1.3 Implement candidate ranking: score by (has DescriptionService data, visible at zoom, proximity), tiebreak by type priority (node > way > area)
- [x] 1.4 Call `DescriptionService::GetDescription()` on best candidate, marshal `DescriptionEntry` list to Java `ArrayList<DescriptionEntry>`
- [x] 1.5 Return empty `ObjectDescription` (no entries) when no suitable object found
- [x] 1.6 Verify `osmscout` library target is linked to `osmscout_client_java` in `CMakeLists.txt` (DescriptionService dependency)
- [x] 1.7 Build native code and verify no compile errors: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`

## 2. ViewModel — Wire long-press and description state

- [x] 2.1 Add `objectDescription: ObjectDescription?` and `isLongPress: Boolean` fields to `MapCanvasUiState` data class
- [x] 2.2 Add `onLongPress(lat: Double, lon: Double)` method to `MapCanvasViewModel`: call `client.getDescription(lat, lon)` on `Dispatchers.Default`, update state with result
- [x] 2.3 Set `isLongPress = true` when description comes from long-press (distinct from search result selection)
- [x] 2.4 Clear `objectDescription` when `dismissDetailsSheet()` is called or a new search result is selected
- [x] 2.5 Verify existing tests still pass: `./gradlew test`

## 3. MapCanvasScreen — Add long-press gesture detection

- [x] 3.1 Add `pointerInput` modifier for long-press: on `PointerEventType.Press` start 500ms coroutine delay, cancel on `Release` or `Move` > 3px threshold
- [x] 3.2 On long-press fire, convert screen coords to geo using current viewport (same Mercator formula as pan handler)
- [x] 3.3 Call `viewModel.onLongPress(lat, lon)` with converted coordinates
- [x] 3.4 Ensure long-press does not interfere with existing pan/zoom gestures (3px drag threshold cancels)
- [x] 3.5 Verify build compiles: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`

## 4. LocationDetailsSheet — Enhance with structured description

- [x] 4.1 Add optional `ObjectDescription?` parameter to `LocationDetailsSheet` composable
- [x] 4.2 Group `DescriptionEntry` list by `sectionKey`, render section headers (bold, `titleSmall` style)
- [x] 4.3 Render subsection headers indented under parent section when `subsectionKey` is non-empty
- [x] 4.4 Render label/value rows under each (sub)section header
- [x] 4.5 Show existing basic info (label, region, coords, fav controls) below description sections
- [x] 4.6 Fall back to current behavior (no sections) when `ObjectDescription` is null or empty
- [x] 4.7 Make sheet draggable: change `rememberModalBottomSheetState(skipPartiallyExpanded = true)` to `skipPartiallyExpanded = false`
- [x] 4.8 Add visible drag handle composable at top of sheet content
- [x] 4.9 Wire `ObjectDescription` from ViewModel state to `LocationDetailsSheet` in `MapCanvasScreen.kt`
- [x] 4.10 Verify build compiles: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`

## 5. Integration & Verification

- [x] 5.1 Run full build: `./gradlew :app:assembleDebug`
- [x] 5.2 Run unit tests: `./gradlew test`
- [x] 5.3 Verify long-press on map opens details sheet with structured description (manual test on device/emulator)
- [x] 5.4 Verify search result selection still opens details sheet with basic info (no description sections)
- [x] 5.5 Verify details sheet is draggable to dismiss
- [x] 5.6 Verify add/remove favorites from details sheet still works
- [x] 5.7 Verify pan/zoom gestures still work (no regression from long-press addition)
