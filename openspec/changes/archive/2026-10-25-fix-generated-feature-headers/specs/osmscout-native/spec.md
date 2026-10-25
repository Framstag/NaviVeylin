## ADDED Requirements

### Requirement: Configured feature headers live in the build tree
The native build SHALL render libosmscout's four `*Features.h` into the app's CMake build tree and prepend each
module's include root to the include path, so those libraries compile with this configuration's values, and no
generated feature header exists below `app/src/main/cpp/libosmscout/`.

#### Scenario: The configure leaves no generated feature header in the submodule source tree
- **WHEN** `./gradlew :app:assembleMobileDebug` configures the native build
- **THEN** the four headers exist under `osmscout-features/` in the app's CMake build tree (`OSMSCOUT_FEATURES_DIR`)
- **AND** no generated `*Features.h` exists below `app/src/main/cpp/libosmscout/` (the two tracked copies under `Android/OsmScoutLib/jni/include/config/` are not generated — no template sits beside them)

#### Scenario: A generated feature header in the source tree is removed by the build gate
- **WHEN** a generated `*Features.h` is present in the submodule source tree as `preBuild` runs
- **THEN** `:app:checkSubmoduleFeatureHeaders` removes it and fails, naming the path and §202
- **AND** the following build finds none there and succeeds

#### Scenario: A host build in the same working tree measures its own feature set
- **WHEN** a working tree that also ran an Android configure is built for the host with `-DCMAKE_DISABLE_FIND_PACKAGE_Pango=ON` and the case "Cairo measures a configured font file as the face of that file" runs
- **THEN** the case passes — 7 cases, 167 assertions — because `libosmscout-map` was compiled with that build's `MapFeatures.h`
