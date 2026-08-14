## 1. vcpkg dependency (spec: osmscout-native)

- [x] 1.1 Verify marisa-trie port builds for Android: `./vcpkg install marisa-trie:arm64-android` succeeds (if port fails, create overlay port under `vcpkg-overlays/`)
- [x] 1.2 Install marisa-trie for remaining triplets: `marisa-trie:arm-neon-android`, `marisa-trie:x64-android`
- [x] 1.3 Add `marisa-trie` to `DEPS` array in `setup-vcpkg.sh`
- [x] 1.4 Add `[marisa-trie]=marisa` entry to `PKG_MAP` in `setup-vcpkg.sh` (pkg-config check)

## 2. CMake wiring (spec: osmscout-native)

- [x] 2.1 In `app/src/main/cpp/CMakeLists.txt`, inside `if(VCPKG)`, pre-set `MARISA_FOUND=1`, `MARISA_INCLUDE_DIRS="${VCPKG}/include"`, `MARISA_LIBRARIES="${VCPKG}/lib/libmarisa.a"` cache vars (pattern: Cairo/zlib pre-sets)
- [x] 2.2 Confirm `find_package(Marisa)` resolves: CMake configure log shows marisa support enabled (`- marisa support: 1`)
- [x] 2.3 Confirm `OSMSCOUT_HAVE_LIB_MARISA` defined in generated `CoreFeatures.h` (client-java compile path)

## 3. Build verification (spec: osmscout-native)

- [x] 3.1 Build single ABI: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` compiles without errors
- [x] 3.2 Verify `libosmscout_client_java.so` links marisa: `nm -C` on the .so contains `TextSearchIndex`/marisa symbols (arm64)
- [x] 3.3 Full build all ABIs: `./gradlew :app:assembleDebug` succeeds for arm64-v8a, armeabi-v7a, x86_64
- [x] 3.4 Run existing unit tests: `./gradlew test` passes (JNI stub unaffected, no regressions)
- [x] 3.5 Add unit tests for free-text result flow: `MapCanvasViewModelFreeTextSearchTest` (POI results surface, order preserved, query/limit/handle contract, empty fallback) + `FakeOSMScoutClient` configurable results

## 4. Runtime verification (spec: search-free-text, location-search)

- [x] 4.1 Manual: search POI name not matching any address (e.g. "cafe central") on a map with text index → POI appears in results with coordinates
- [x] 4.2 Manual: search on a database without text index → structured results still returned, no crash (native warning logged)
- [x] 4.3 Manual: duplicate suppression — query matching both structured and free-text index returns object once
- [x] 4.4 Manual: result limit respected — query with many hits returns at most the requested limit
- [x] 4.5 Manual: basemap loaded → no results sourced from basemap database
- [x] 4.6 Manual: search from map screen, route panel, and Android Auto all return free-text matches (existing call paths unchanged)
