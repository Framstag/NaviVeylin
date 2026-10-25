# Design

## Root cause, and what the evidence eliminated

The defect is not a wrong value but a wrong **owner**: the feature headers that decide what `libosmscout-map` and
`libosmscout-map-cairo` compile were written into the submodule source tree, where the include order makes them win
over the header the build itself generates. Two further facts had to be measured before a fix could be chosen —
they are what made the obvious fixes impossible, and they are what this design is built on.

1. **The submodule's own configure needs one of those headers.** `libosmscout/libosmscout/CMakeLists.txt:24-26`
   lists `include/osmscout/lib/CoreFeatures.h` — a *source-tree* path — in the core library's source list, and the
   fork's commit `4516ea96d` ("Untrack build-generated Feature headers") removed the tracked copies. A CMake
   source list resolves a relative path against the source directory first and, only if it is not there, against
   the *binary* directory; so any configuration that fails to write the expected build-tree path fails the whole
   configure with `Cannot find source file: include/osmscout/lib/CoreFeatures.h` +
   `No SOURCES given to target: OSMScout` (measured: `evidence/submodule-patch-revert-check.log`, a fresh
   `.cxx` tree with only the submodule patch reverted).
2. **CMake 3.22.1 writes that header somewhere else.** `cmake/ProjectConfig.cmake` derived the module's directory
   with `cmake_path(GET <var> PARENT_PATH <var>)`. On the CMake 3.22.1 that the Android Gradle plugin ships that
   command is a **no-op** (verified against CMake 4.4.4, which applies it:
   `evidence/cmake-path-version-check.log`), so a module that names no `FEATURE` had its header written to
   `<buildTree>/…/include/<package>/<Name>Features.h.cmake/<Name>Features.h` — a path that no `#include` and no
   `install(FILES …)` reads. Under the 3.22.1 configure the source-tree copy was therefore the *only* header that
   resolved, and the pre-generation was load-bearing rather than cosmetic.

Alternatives this eliminated:

- **Fix the include order alone** (`cmake/ProjectConfig.cmake`, drop the bare relative `include` or move
  `${CMAKE_CURRENT_BINARY_DIR}/include` first) and delete the pre-generation. Impossible as the whole fix: the
  app pre-generates the four headers *with its own values* (marisa on; FreeType, Fontconfig, cairo-ft, Pango off),
  which is what the Android build compiles with today. Removing the files makes the library's own values win, and
  those are FreeType **on** and cairo-ft **on** for this configuration — a rendering-behaviour change (the cairo
  text path, and `libosmscout-map-cairo` would then need Pango's dependency closure, which the app's
  `CAIRO_LIBRARIES` override does not carry). That is issue #305's decision, with on-device measurement, not this
  fix.
- **Re-track the four headers in the submodule** (undo `4516ea96d`). Impossible: a tracked copy in the source
  tree is the poison itself — it wins by include order in a fresh clone too, which is a worse state than today's
  (today it needs an Android configure first).
- **A gate that only refuses.** Impossible: `preBuild` runs before the native configure, so a refusing gate blocks
  the build that writes the build-tree headers (measured: `evidence/gate-precedes-native-configure.log`).
- **A separate `sync…` task beside the check.** Rejected: Gradle does not order two `dependsOn` siblings, and
  ordering the check after the sync makes the check vacuous.

## Decisions

### D1 — The four headers are rendered into the app's CMake build tree and the include path picks them up

`app/src/main/cpp/CMakeLists.txt` renders each header to
`${CMAKE_CURRENT_BINARY_DIR}/osmscout-features/<module>/include/<package>/<Name>Features.h` and calls
`include_directories(BEFORE "${OSMSCOUT_FEATURES_DIR}/<module>/include")`, where the module include root is taken
from the path itself (`string(REGEX REPLACE "/include/.*$" "/include" …)`, which also covers the core library's
`libosmscout/include/osmscout/lib/…` layout). Directory-level includes are inherited before a target's own
`target_include_directories`, so the app's values win for every translation unit of those four libraries — verified
in the generated `build.ninja` (`app/.cxx/Debug/4z1b2r3y/arm64-v8a/build.ninja`, the four `osmscout-features/…`
entries first in `INCLUDES`). Nothing is written under `app/src/main/cpp/libosmscout/`, and the values are byte for
byte the ones the old source-tree copies carried, so the Android feature set is unchanged.

### D2 — Two minimal submodule fixes replace what the source-tree copy used to satisfy

Patch (2 files, 7 insertions; `evidence/submodule-patch.diff`):

- `cmake/ProjectConfig.cmake` — `get_filename_component(_template_dir "${_template_dir}" DIRECTORY)` instead of
  the no-op `cmake_path(GET …)`, so a module without `FEATURE` writes its header where the include order and
  `install(FILES …)` expect it.
- `libosmscout/CMakeLists.txt` — the core library lists its feature header in the build tree
  (`${CMAKE_CURRENT_BINARY_DIR}/include/osmscout/lib/CoreFeatures.h`) and names it with `FEATURE`, the idiom
  `libosmscout-map` and `libosmscout-client` already use.

With those two, no generated header has to exist in the source tree for any configuration to succeed (green half:
`evidence/submodule-patch-restored.log`, and the pristine-tree host configure). The patch is minimal and
upstreamable: it makes the core module consistent with the two modules that already pass `FEATURE`, and it fixes a
path computation that silently wrote an unreachable file on the CMake the Android toolchain ships. It is committed
on the submodule's `naviveylin-local` branch with a gitlink bump here, and it is an upstream PR candidate — if it
is not merged before the next submodule bump it has to be carried in
`.pi/skills/update-to-current-libosmscout-master/SKILL.md`.

### D3 — `:app:checkSubmoduleFeatureHeaders` removes what it finds, then fails once

The gate scans the modules' `include` roots for a `*Features.h` whose own `.cmake` template is its sibling — the
same rule the build uses to decide to render one, and one that never matches a header the submodule tracks (its
`Android/OsmScoutLib/jni/include/config/` copies sit beside no template). A hit makes the task delete the file and
throw with the paths, the §202 mechanism and "re-run the build"; the next invocation is green.

- Removing is deliberate. The files are this build's own generated artifacts, `preBuild` runs before the native
  configure, and the configure is what writes the build-tree headers — so a gate that only refused would leave a
  working tree configured before this change unable to build itself (measured). Delete-and-fail costs one failing
  build per stale tree, no manual `rm`, and keeps the alarm.
- The scan is bounded to `<module>/include` for each direct child of the submodule: the generated header is always
  the sibling of its template, and walking the submodule's `build/`/`hostbuild/` trees would fingerprint gigabytes
  for a hit that cannot happen.

### D4 — A typed task in `buildSrc`, not a script-level gate

`GeneratedFeatureHeaders` (scanner: `generated`/`find`/`report`) and `CheckSubmoduleFeatureHeaders`
(`@Internal val submoduleDir`, `@TaskAction`) live in `buildSrc`'s `com.naviveylin.build.native`, with 6 JUnit
cases. `submoduleDir` is `@Internal` on purpose: fingerprinting the vendored tree would hash its build directories
on every build, and a gate has no outputs.

The rule the other gates in `app/build.gradle.kts` follow — a script-level `doLast` closure — would add a
configuration-cache problem to the 5 that `--configuration-cache :app:preBuild` reports for the script-level ones.
A typed task with a property set from the script adds none, measured: the same 5 problems, and
`:app:checkSubmoduleFeatureHeaders` executes under `--configuration-cache`
(`evidence/configuration-cache.log`).

## Risks and what is not addressed

- **The pre-generated values are cache-dependent.** `#cmakedefine` reads whatever the CMake cache holds at that
  point, and `check_cxx_source_compiles` caches its probe, so `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` was
  `/* #undef */` in the copy generated on 2026-10-15 and `#define` in the one generated on 2026-10-26 from the
  same sources. This change moves where that content is written, not what it is; issue #305 owns the decision
  (keep the forced set and fix the guideline, or let `features.cmake` decide).
- **Nothing is validated about the values.** A header that disagrees with the configuration it belongs to is still
  possible (the app's forced set *is* such a disagreement). The gate checks *location*, not content, and says so.
- **Threading and lifecycle**: not applicable — every part of this change runs at configure time (CMake) or as a
  Gradle task. No runtime component, dispatcher, scope or native call is added.

## Verification

| claim | measurement |
|---|---|
| the defect is real and host-side | `evidence/host-nopango-red.log` — the case failed, 7 cases / 1 failed, `REQUIRE(differs)` 218.154 vs 218.154, with the `cannot read the family it holds` line (HEAD state: the Android-generated headers in the source tree) |
| the fix removes it | `evidence/host-nopango-green.log` — same tree, same configure, `All tests passed (167 assertions in 7 test cases)` |
| no header is left in the source tree, and the app's values still win | `evidence/native-configure-arm64.log` (full arm64 app build) + the generated `build.ninja` include order + the four `osmscout-features/…` files |
| the gate repairs and fails, then passes | `evidence/gate-red.log`, `evidence/gate-green.log`, `evidence/gate-revert-check.log` (mutation: removal disabled → the file survives), `evidence/gate-revert-check-restored.log` |
| the gate runs before the native configure (so it has to remove) | `evidence/gate-precedes-native-configure.log` |
| the submodule patch is load-bearing | `evidence/submodule-patch-revert-check.log` (patch reverted, fresh `.cxx`: `Cannot find source file` / `No SOURCES given to target: OSMScout`) vs `evidence/submodule-patch-restored.log` (patch restored: configure + build green) |
| the 3.22.1 path wart | `evidence/cmake-path-version-check.log` — `inplace_parent=c/MapFeatures.h.cmake` on 3.22.1, `c` on 4.4.4 |
| every shipped ABI and both flavors still build | `evidence/assembleDebug-all-abis.log` — `:app:assembleDebug` (mobile + automotive, arm64-v8a + armeabi-v7a + x86_64) |
| the JVM suites are unaffected | `evidence/jvm-gate.log` — `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`: `:app` automotive 2109/0/0, mobile 2109/0/0, `:auto` 880/0/0, `:core` 646/0/0, `:osmscout-client-java` 48/0/0 |
| the buildSrc rule | `buildSrc`'s `GeneratedFeatureHeadersTest` — 6 cases |
| the new gate adds no configuration-cache problem | `evidence/configuration-cache.log` — 5 problems, none of them this task |
