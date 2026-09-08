## 1. Native submodule patch (libosmscout)

Specs: basemap-loading (all scenarios). Design: D1.

- [x] 1.1 Add `DBThread::SetBasemapLookupDirectory(const std::string&)` to `libosmscout-client/include/osmscoutclient/DBThread.h` and `libosmscout-client/src/osmscoutclient/DBThread.cpp` — schedules one async job taking `WriteLock(latch)` that updates the member and calls the existing `LoadBasemap()` (empty string = unload); verify it mirrors the existing `RemoveLookupDirectory` rescan pattern and adds no Android dependencies
- [x] 1.2 Add JNI method `OSMScoutClient.setBasemapLookupDirectory(String)` in `libosmscout-client-java/src/OSMScoutClient.cpp` (null/`basemapLookupDirectory` field read like `reloadBasemap`; call `data->dbThread->SetBasemapLookupDirectory(...)`) and verify a build of the submodule targets succeeds
- [x] 1.3 Commit the submodule change with a minimal, upstreamable message and verify `git status` of `app/src/main/cpp/libosmscout` is clean afterwards (except the intentional submodule pointer bump in the app repo)

## 2. JNI bridge Java API

Specs: basemap-loading. Design: D1.

- [x] 2.1 Declare native `setBasemapLookupDirectory(String)` on `com.framstag.libosmscout.client.OSMScoutClient` (java/) matching the JNI symbol from 1.2 and verify JNI name mapping compiles (build of the app's native targets for all ABIs)
- [x] 2.2 Update `FakeOSMScoutClient` in `app/src/test/java/com/framstag/libosmscout/client/` with the new method (record last-set directory) and verify existing fake-based tests still pass

## 3. Renderer invalidation hooks (phone + auto)

Specs: basemap-loading "current view re-renders" scenarios. Design: D2.

- [x] 3.1 Add `MapRenderer.invalidateData()` (`app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt`): `epoch.incrementAndGet()` + `tileCache.clear()` + `submitDebounced(..., forceFullRender = true)`; verify it behaves like `invalidateStyle()` (forced render survives the pan-blit shortcut, `MapRenderer.kt:455-463`)
- [x] 3.2 Add `AutoMapRenderer.invalidateData()` (`auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`): `blitEligible = false` + `requestRender()`, mirroring its `invalidateStyle()`; verify no tile cache exists to clear (overrun blit only)
- [x] 3.3 Add unit tests: phone — `invalidateData()` bumps epoch, clears tile cache, and enqueues a forced full render; auto — `invalidateData()` invalidates blit eligibility and requests a re-render (extend `AutoMapRendererTest.kt` / existing renderer test infra)

## 4. Reload notifier + wiring

Specs: basemap-loading. Design: D3, D4.

- [x] 4.1 Create `@Singleton` `BasemapReloadNotifier` (Hilt, e.g. `com.naviveylin.di` or `core`): `MutableStateFlow<Long>` counter + `bump()`; register provision and verify Hilt graph resolves it
- [x] 4.2 Wire `BasemapViewModel`: on download `onComplete` call `client.setBasemapLookupDirectory(installDir)` + `client.reloadBasemap()` + `notifier.bump()`; on `delete()` call `client.setBasemapLookupDirectory("")` + `client.reloadBasemap()` + `notifier.bump()`; keep `refresh()` unchanged (spec: download, first-time install, delete scenarios)
- [x] 4.3 Collect the notifier in `MapCanvasViewModel` (viewModelScope) and forward to `mapRenderer.invalidateData()`; verify a download flow triggers exactly one forced re-render
- [x] 4.4 Collect the notifier in the four Auto screens (`MapScreen`, `NavigationScreen`, `DetailsScreen`, `FreeDrivingScreen`) → `mapRenderer.invalidateData()`; verify phone/Auto parity wiring compiles

## 5. Unit tests for the new Kotlin code

Specs: basemap-loading. Design: D3, D4.

- [x] 5.1 Add `BasemapViewModel` tests: download-complete and delete flows call `setBasemapLookupDirectory` + `reloadBasemap` + `bump()` (Robolectric default sandbox + `FakeOSMScoutClient`, per AGENTS.md classloader rule) and verify state transitions (`isDownloading`, `installedInfo`)
- [x] 5.2 Add notifier tests: `bump()` increments the counter and collectors observe the latest value after coalescing
- [x] 5.3 Verify `./gradlew test` passes (existing suite + new tests)

## 6. Build verification

- [x] 6.1 Run a debug build for all three ABIs (`./gradlew :app:assembleMobileDebug`, then with `-Pandroid.injected.build.abi` per arm64-v8a/armeabi-v7a/x86_64) and verify no compile errors, no new warnings
- [x] 6.2 Build the auto module consumers compile (`./gradlew :app:assembleMobileDebug` covers `:auto`) and verify the Android-free CI gate on libosmscout still passes (`Check libosmscout Android-free outside Android/`)
- [x] 6.3 Verify existing tests still pass after the submodule bump (full `./gradlew test`)

## 7. On-device verification (phone + car)

Specs: basemap-loading scenarios. Design: D2, D4.

- [ ] 7.1 Fresh install (no basemap): download basemap from map manager → return to map → basemap visible WITHOUT restart; verify `adb logcat -s NaviVeylin` shows "Basemap loaded from ..." and tile re-render logs after the download completes
- [ ] 7.2 Delete basemap while app runs → map re-renders without basemap overlay, no restart
- [ ] 7.3 Update basemap while app runs → new version visible after re-render, no restart
- [ ] 7.4 Region-with-no-map viewport shows basemap borders/country names/coastlines after live install; regional map renders on top where covered (spec basemap-loading overlay requirements)
- [ ] 7.5 Android Auto emulator/head-unit: repeat 7.1–7.3 on the car surface (MapScreen/NavigationScreen), verify surface refreshes after download (surface lifecycle per guidelines/UI.md parity requirement)

## 8. Documentation and finalization

- [x] 8.1 Update `openspec/specs/basemap-loading/spec.md` main spec with the archived MODIFIED requirement (fresh-install + re-render scenarios)
- [x] 8.2 Check `guidelines/MapRendering.md` and `guidelines/Design.md` for invalidation-trigger lists and add basemap data-set changes if the docs enumerate such triggers (no change = note "no guideline update required")
- [x] 8.3 Confirm `openspec validate fix-basemap-live-reload` passes and all spec scenarios map to tasks
