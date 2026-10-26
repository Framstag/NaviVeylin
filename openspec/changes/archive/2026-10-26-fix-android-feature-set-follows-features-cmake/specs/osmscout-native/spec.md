## MODIFIED Requirements

### Requirement: Configured feature headers live in the build tree
The native build SHALL compile libosmscout against the four `*Features.h` that `cmake/features.cmake` renders
for the configuration into the modules' own CMake build trees, and SHALL NOT render a feature-header directory
of its own or prepend one to the include path. No generated feature header exists below
`app/src/main/cpp/libosmscout/`.

#### Scenario: The configure leaves no generated feature header in the submodule source tree
- **WHEN** `./gradlew :app:assembleMobileDebug` configures the native build
- **THEN** each module's `*Features.h` exists in that module's own CMake build tree with `features.cmake`'s values
- **AND** no generated `*Features.h` exists below `app/src/main/cpp/libosmscout/` (the two tracked copies under `Android/OsmScoutLib/jni/include/config/` are not generated — no template sits beside them)
- **AND** no app-rendered feature-header directory is on the compile or link include path

#### Scenario: A generated feature header in the source tree is removed by the build gate
- **WHEN** a generated `*Features.h` is present in the submodule source tree as `preBuild` runs
- **THEN** `:app:checkSubmoduleFeatureHeaders` removes it and fails, naming the path and §202
- **AND** the following build finds none there and succeeds

#### Scenario: A host build in the same working tree measures its own feature set
- **WHEN** a working tree that also ran an Android configure is built for the host with `-DCMAKE_DISABLE_FIND_PACKAGE_Pango=ON` and the case "Cairo measures a configured font file as the face of that file" runs
- **THEN** the case passes — 7 cases, 167 assertions — because `libosmscout-map` was compiled with that build's `MapFeatures.h`

#### Scenario: The Android configure compiles the features its dependencies carry
- **WHEN** the arm64-v8a configure runs in a working tree whose Android vcpkg triplets are installed
- **THEN** `MapFeatures.h` defines `OSMSCOUT_MAP_HAVE_LIB_FREETYPE`, and `MapCairoFeatures.h` defines `OSMSCOUT_MAP_CAIRO_HAVE_LIB_FREETYPE` and `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT`
- **AND** `libosmscout_mapd.so` carries the FreeType entry points the map library calls

#### Scenario: A cached probe result does not outlive its configuration
- **WHEN** a build directory's cache holds an empty `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` entry and the configure runs
- **THEN** `MapCairoFeatures.h` carries the answer the probe gives for this configuration, not the cached one
