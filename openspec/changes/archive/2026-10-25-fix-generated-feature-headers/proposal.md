# Proposal

## Why

A host build of the libosmscout submodule compiles against the **Android** configuration's feature macros when the
working tree has also run an Android configure, so tests keyed on those macros report false failures — and, worse,
false passes (§202, issue #136).

**Root cause, two layers, both proven in this tree.**

1. `app/src/main/cpp/CMakeLists.txt` pre-generated libosmscout's four feature headers — `CoreFeatures.h`,
   `MapFeatures.h`, `MapCairoFeatures.h`, `ClientFeatures.h` — **into the submodule source tree**
   (`file(REMOVE "${_out}")` + `configure_file("${_tpl}" "${_out}")`, `_out` under
   `app/src/main/cpp/libosmscout/`). Their content is what that file sees *before*
   `add_subdirectory(libosmscout)` — the app's own pre-set cache values, and nothing else.
2. The submodule's include order keeps such a copy winning: `cmake/ProjectConfig.cmake` (`_includes`:
   `include`, `${PROJECT_SOURCE_DIR}/include`, `${CMAKE_CURRENT_BINARY_DIR}/include`, `…/privateinclude`,
   `${CMAKE_CURRENT_SOURCE_DIR}/include`) puts the bare relative `include` — the *source* tree — first, which the
   generated `build.ninja` confirms for both builds (`-I…/libosmscout-map/include` **before**
   `-I…/<buildTree>/libosmscout-map/include`, `app/.cxx/Debug/4z1b2r3y/arm64-v8a/build.ninja` and
   `app/src/main/cpp/libosmscout/build/build.ninja`, read 2026-10-26).

**Red on HEAD** (recorded before the fix, `evidence/host-nopango-red.log`): with the Android-generated
`MapFeatures.h`/`MapCairoFeatures.h` in the source tree and a host build configured
`-DCMAKE_DISABLE_FIND_PACKAGE_Pango=ON`, the pre-existing case *"Cairo measures a configured font file as the face
of that file"* (`Tests/src/TextMetricsCairoTest.cpp:272`, `REQUIRE(differs)`) failed — `label width for the
configured file: 218.154, for the substituted family: 218.154`, with `The configured font file '…' is used as
configured, this build cannot read the family it holds` — **7 cases, 6 passed, 1 failed**; repro:
`cd app/src/main/cpp/libosmscout && cmake -B build -DCMAKE_DISABLE_FIND_PACKAGE_Pango=ON && cmake --build build
--target TextMetricsCairoTest && cd build && ctest -R "^TextMetricsCairoTest$" --output-on-failure`. The host
build's own, correctly computed `MapFeatures.h` had `#define OSMSCOUT_MAP_HAVE_LIB_FREETYPE` all along.

**The second layer is why the first could not simply be deleted.** The submodule's core library lists
`include/osmscout/lib/CoreFeatures.h` — a *source-tree* path — in `HEADER_FILES_LIB`
(`libosmscout/libosmscout/CMakeLists.txt:24-26`), and the fork untracked the four generated headers
(`4516ea96d`, 2026-08-21: "Untrack build-generated Feature headers"). So a checkout without them cannot configure
at all (`CMake Error … Cannot find source file: include/osmscout/lib/CoreFeatures.h`, measured while preparing
this change, `evidence/gate-precedes-native-configure.log`), and the pre-generation was the thing that satisfied
it. Underneath, the CMake 3.22.1 that the Android Gradle plugin ships
(`~/Android/Sdk/cmake/3.22.1`) makes `cmake_path(GET <var> PARENT_PATH <var>)` a **no-op** — verified against
CMake 4.4.4, which does apply it — so for every module that names no `FEATURE` the header was written to
`<buildTree>/…/include/<package>/<Name>Features.h.cmake/<Name>Features.h`, a path no include and no install reads.
The source-tree copy was therefore not a convenience but the only header that resolved.

Spec: `osmscout-native` — the capability's subject ("CMake build via Android NDK", "CMakeLists.txt integration")
is that the Android CMake build defines the feature set its libraries compile with; the defect made a *different*
configuration's macros compile into those libraries. The requirement that states the invariant is added by this
change (it was never specified).

## What Changes

- **The app renders the four headers into its own CMake build tree** (`OSMSCOUT_FEATURES_DIR`,
  `<app>/.cxx/<buildType>/<hash>/<abi>/osmscout-features/`) and prepends each module's `include` root
  (`include_directories(BEFORE …)`), so the app's values win for every translation unit of those libraries and
  nothing is written under `app/src/main/cpp/libosmscout/`. The values are unchanged (behaviour-preserving: the
  Android feature set stays the app's — marisa on, FreeType/Fontconfig/cairo-ft/Pango off).
- **`:app:checkSubmoduleFeatureHeaders` (new, buildSrc `com.naviveylin.build.native`, in `preBuild`) removes a
  generated feature header it finds in the submodule source tree and fails the build naming it, once.** It
  removes rather than only refusing because `preBuild` runs before the native configure: a refusing gate would
  block the very build that writes the build-tree headers (measured,
  `evidence/gate-precedes-native-configure.log`). Its scan reads the modules' `include` roots only, and it treats
  a header as generated exactly when its own `.cmake` template is its sibling — which never matches a tracked
  header (the submodule's `Android/OsmScoutLib/jni/include/config/` copies sit beside no template).
- **Two submodule fixes make a source-tree copy unnecessary** (minimal, upstreamable; 2 files, 7 insertions):
  `cmake/ProjectConfig.cmake` uses `get_filename_component(… DIRECTORY)` instead of the no-op `cmake_path(GET …)`,
  and the core library names its feature header in the build tree (`${CMAKE_CURRENT_BINARY_DIR}/include/…`) and
  passes it as `FEATURE`, the idiom `libosmscout-map` and `libosmscout-client` already use. A pristine checkout
  configures and builds its host tests again.
- `guidelines/Build.md` §12 gains the fact (where the headers live, why they must not sit in the source tree, what
  the gate does, the two submodule fixes).

**Explicit non-goals** (recorded so they are not re-litigated during apply):

- The Android feature set itself is **not** changed. Issue #305 owns the mismatch between it and the vcpkg
  triplets (which do install Pango, FreeType, Fontconfig for Android) and `guidelines/MapRendering.md` §20's
  wrong reason for the Pango-less wrapping; that is a decision about rendering behaviour with on-device
  measurement, not this fix.
- The submodule's `Android/OsmScoutLib/jni/include/config/` copies are not touched: they are tracked, belong to
  the submodule's own Android app, and sit beside no template.

Additive and behaviour-preserving on every shipped surface: no Kotlin/Java source changes, no JNI signature
change, no change to the Android feature macros, and the two AAB flavors build as before.
**Rollback**: revert this change's commit and its submodule commit (the gitlink bump goes with it); the tree then
pre-generates into the source tree again and the §202 trap with it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `osmscout-native` — **added** requirement "Configured feature headers live in the build tree" (three scenarios:
  the configure leaves none in the source tree; `:app:checkSubmoduleFeatureHeaders` removes one and fails naming
  it; a host build in the same working tree measures its own feature set). No shipped requirement is rewritten.

## Impact

- `app/src/main/cpp/CMakeLists.txt` — the generation loop: output path, include order, no source-tree write.
- `app/build.gradle.kts` — `checkSubmoduleFeatureHeaders` registered as a typed task and wired into `preBuild`
  (typed, so it adds none of the configuration-cache problems `guidelines/Build.md` §7 records: `preBuild` shows
  the same 5 script-level problems, `evidence/configuration-cache.log`).
- `buildSrc/src/main/kotlin/com/naviveylin/build/native/GeneratedFeatureHeaders.kt` — the scanner
  (`generated`/`find`/`report`) and the task; `buildSrc/src/test/…/GeneratedFeatureHeadersTest.kt` — 6 cases.
- `app/src/main/cpp/libosmscout/cmake/ProjectConfig.cmake`,
  `app/src/main/cpp/libosmscout/libosmscout/CMakeLists.txt` — submodule patch (commit on `naviveylin-local`,
  gitlink bump in this repo; `evidence/submodule-patch.diff`).
- `guidelines/Build.md` §12 — the fact. `AGENTS.md` is untouched (the native build's facts live in §12).
- No change to `:auto`, `:core`, `:osmscout-client-java`, resources, screens or the JNI bridge.

**Verification** — the host red/green pair quoted above; `:app:assembleDebug` (both flavors, all three ABIs); the
whole JVM gate; the `preBuild` gate red (removes + fails naming the path) and green; the buildSrc cases. Details
in `design.md`, steps in `tasks.md`, the raw logs in `evidence/`.
