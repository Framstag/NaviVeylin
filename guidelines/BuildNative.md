# Build Native Guidelines — on-device evidence and the native build

The rules for on-device evidence for Android Auto / AAOS, the route-length rule, the route-row census, and
the native build facts. This document was cut from `Build.md`; the numbers did not change, only the document
that carries them.

> The device recipes for §10a–§10e and §10g–§10i are `guidelines/BuildNative.recipes.md`; the rationale and
> the dated provenance are `guidelines/BuildNative.why.md`; the vcpkg commands are `guidelines/BuildNative.recipes.md`.

---

## 10. On-device evidence for Android Auto / AAOS (host-crash triage)

The car host (templates host / car UI) is a **different process** from the app, so a host failure is not
visible in the app's own crash log. Every sub-section is one recipe family; read the one that matches the
evidence you need. §10a "Booting the AAOS AVD and driving the car UI", §10b "Judging the car surface and the
render loop", §10c "Footprint, the app's host sends and route numbers", §10d "Rejected mutations, the error
notice and database registration", §10e "Expectation baselines and the car search scope", §10f "Two checks
that need evidence before a behaviour is specified", §10g "Start-map selection on the phone", §10h
"Stylesheet load count per start", §10i "Device recipes that cost a round every time", §10j "A calculated
route's length — which number is a route length", §10k "The route-row census — what the route lists may show".

## 10a. Booting the AAOS AVD and driving the car UI

The recipe is `guidelines/BuildNative.recipes.md` §10a.

## 10b. Judging the car surface and the render loop

The recipe is `guidelines/BuildNative.recipes.md` §10b.

## 10c. Footprint, the app's host sends and route numbers

The footprint rule and its recipe are `guidelines/BuildNative.recipes.md` §10c.

## 10d. Rejected mutations, the error notice and database registration

The recipe is `guidelines/BuildNative.recipes.md` §10d.

## 10e. Expectation baselines and the car search scope

The recipe is `guidelines/BuildNative.recipes.md` §10e.

## 10f. Two checks that need evidence before a behaviour is specified

**Rule: two checks are run for evidence before any behaviour is specified** (the template-rate one is
recorded in `TODO.md` §64):

- A car-only session that starts the ongoing notification is checked with
  `adb logcat -d | grep ForegroundServiceDidNotStartInTime` — if a *refused* `startForeground` followed by
  `stopSelf()` still trips the platform's foreground-start deadline, the degrade path itself kills the
  process (and that death takes the car host down).
- The template rebuild rate is counted before a policy is read off it: with navigation and lane hints active,
  count the host-visible template activity over a minute (`Diag/HOST` lines, plus the lane-image allocation
  when logging is verbose). The distance values are bucketed to the host's own rounding and the lane image is
  reused; the residual rate follows the arrival estimate's update rate.

(The `TODO.md` §64 provenance is `guidelines/BuildNative.why.md` §10f.)

## 10g. Start-map selection on the phone

The recipe is `guidelines/BuildNative.recipes.md` §10g.

## 10h. Stylesheet load count per start

The recipe is `guidelines/BuildNative.recipes.md` §10h.

## 10i. Device recipes that cost a round every time

The recipes are `guidelines/BuildNative.recipes.md` §10i.

## 10j. A calculated route's length — which number is a route length

- **Rule: the route's length is the description's own total, else the drawn polyline's length.** The bridge
  publishes `RouteEntry.distance` from the description branch
  (`libosmscout-client-java/src/OSMScoutClient.cpp`: the sum of the published per-step legs, else the
  description's last-node distance) and, where a route's description produced nothing, from the great-circle
  length of the polyline it just built. A surface reads a length through `RouteStepValues.routeLengthMeters`,
  which prefers the sum of the step legs.
- **Rule: `AbstractRoutingService::GetOverallDistance()` is NOT a route length.** It is the start/target
  air-line estimate (`AbstractRoutingService.cpp:1088-1094`), used for the router's own cost limit and
  progress denominator, and upstream prints it as "Air-line distance" (`Demos/src/Routing.cpp`). Never publish
  it as a length.
- **Rule: the freshly built *test* APK is installed, not the one under `outputs/`.** With
  `-Pandroid.injected.build.abi=…` AGP writes the variant APKs to `app/build/intermediates/apk/<flavor>/<type>/`,
  while `app/build/outputs/apk/androidTest/…/app-…-androidTest.apk` can still be the previous build's file.
  Verify the artifact you install (`unzip -p <apk> classes*.dex | grep -ac '<a string only the new case has>'`)
  and always read the case's own log lines, never just the runner's `OK (N tests)`.
- **Rule: the guard is the case, not a log reader** — one instrumented run, no UI: build the x86_64 debug APK
  (`-Pandroid.injected.build.abi=x86_64`, `testOnly` → `adb install -r -t`), then
  `./gradlew :app:connectedMobileDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.naviveylin.route.RouteInstructionPositionDeviceTest`
  and read the per-candidate `RouteDeviceTest` line: `routerTotalM` / `descriptionTotalM` / `polylineM` /
  `routerOverPoly` / `descriptionOverPoly` / `descriptionOverRouter`.
  `routeLengthsAreMeasuredForALongAndAShortRoute` fails when the published length leaves
  `publishedOverPoly 0.95…1.05` or differs from the description's total by more than 2 % (the app's own
  divergence threshold), so a regression is a red case. The map data must carry the candidate chain (NRW);
  on the AAOS AVD the basemap can be a format version behind, which the app refuses with a
  `Basemap not registered: …` line — re-download it before comparing basemap-dependent numbers.

(The measured ratios and the issue references are `guidelines/BuildNative.why.md` §10j.)

## 10k. The route-row census — what the route lists may show

- **Rule: one line per calculated route, on the `ROUTE` tag, coordinate-free.** The view model logs
  `route census:` with `steps=` / `rows=` and counts per step kind (`turn`, `straightOn`, `nameChange`,
  `roundabout`, `motorway`, `info`, `start`, `target`), the kilometres each kind carries, the leg quantiles
  (`leg_m median= p90= under20=`), the straight-on run lengths (`so_runs`, `so_len_median`, `so_len_max`),
  the name-change statistics (`nc_total`, `nc_restated`, `nc_runs2plus`, `nc_empty`, `nc_leg_median`) and
  `info_name_only`. Metres and counts only — a coordinate, and a coordinate *word*, never reach the line
  (spec `auto-diagnostics`, `checkNoCoordinatesInLogs`).
- **Rule: measure the census before changing the row policy.** Three numbers cannot be settled from the
  code — whether an `informative`/`POIAtRoute`/`via` step earns a row, and the `MinRowLegMeters` floor
  (`app/ui/route/RouteRows.kt`). The census supplies them, and the filter keeps **one** predicate
  (`informativeEarnsRow()`) so the answer changes in one place.
- **Rule: both route lists read the same census** (`share-route-rows-in-core`). The rows are derived once, in
  `core/RouteRows.kt`, and the phone route panel and the car route description both list them — so `rows=`
  is the phone list's length *and* the car list's, while `steps=` is what a car list would show if it went
  back to one row per published step. A car line whose `rows` equals `steps` means the car list is listing
  published steps again, and a `rows=` that contradicts the panel's is a second row policy in the tree.
- **Rule: take one city route and one motorway route before reading the policy off it, and record both lines
  verbatim.** The census is written when a route is applied to the panel's state; read it with
  `adb logcat -d -s ROUTE:* NaviVeylin:* | grep 'route census'`, and read the car list's own line with
  `adb logcat -d -s NaviVeylin:* | grep 'route description rows='`. The numbers, not a summary of them, are
  the evidence; the neighbouring `route analysis:` line reports the row sum against the route's total and
  `maxErrM`.

## 12. Native build and vcpkg

The native facts have no rule status — they are what the build is made of — so they live next to the rest of
the build documentation. The vcpkg commands are `guidelines/BuildNative.recipes.md` §12.

## 12a. Architecture

- Core libosmscout libs (OSMScout, OSMScoutMap, OSMScoutClient) built from source via
  `add_subdirectory(libosmscout)`.
- Cairo backend (OSMScoutMapCairo) links against vcpkg-installed cairo + deps.
- CMakeLists.txt uses ABI-aware vcpkg triplet selection: `arm64-v8a` → `arm64-android`, `armeabi-v7a` →
  `arm-neon-android`, `x86_64` → `x64-android`.
- vcpkg overlay triplet at `vcpkg-overlays/triplets/arm64-android.cmake` uses API 26.
- iconv stub for Android API < 28.

## 12b. Configured feature headers

libosmscout renders four feature headers — `CoreFeatures.h`, `MapFeatures.h`, `MapCairoFeatures.h`,
`ClientFeatures.h` — from the tracked `*.cmake` templates beside them. Their values are the build
configuration's feature set, so they belong to a build tree and never to the submodule's source tree:

- `cmake/features.cmake` computes the values, `cmake/ProjectConfig.cmake` writes each header into the
  module's own CMake build tree (`<build>/libosmscout-*/include/<package>/<Name>Features.h`) and puts that
  directory on the module's include path. `app/src/main/cpp/CMakeLists.txt` renders nothing.
- **The Android configure's values are therefore `features.cmake`'s**: `OSMSCOUT_HAVE_LIB_MARISA`,
  `OSMSCOUT_MAP_HAVE_LIB_FREETYPE`, `OSMSCOUT_MAP_CAIRO_HAVE_LIB_FREETYPE` and
  `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` defined; `OSMSCOUT_MAP_CAIRO_HAVE_LIB_PANGO` and
  `OSMSCOUT_MAP_CAIRO_HAVE_LIB_FONTCONFIG` not — their detection does not resolve under the NDK although the
  three Android vcpkg triplets install pango, harfbuzz, fribidi and fontconfig.
- Two cache entries are cleared before `add_subdirectory(libosmscout)`, because they would otherwise outlive
  the configuration that wrote them: `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` and `OSMSCOUT_HAVE_LIB_MARISA`.
- `libosmscout-map` links `Freetype::Freetype` as soon as the library found FreeType, and the app adds the
  static port's closure behind it on the link line (libpng, zlib, bzip2, brotli) — a static archive does not
  carry its dependencies. `libosmscout_map_cairo` gets the same entries.
- **A copy in the submodule source tree wins over the header a build generates** — every module's target
  lists its own source tree **before** its binary include directory (`cmake/ProjectConfig.cmake`) — in
  *every* build of that working tree, a host build included. `:app:checkSubmoduleFeatureHeaders` (buildSrc
  `com.naviveylin.build.native`, in `preBuild`) **removes** such a copy and fails once, naming it; its scan
  reads the modules' `include` roots only.
- Two submodule fixes make a source-tree copy unnecessary (a pristine checkout configures again):
  `cmake/ProjectConfig.cmake` uses `get_filename_component(… DIRECTORY)` instead of
  `cmake_path(GET … PARENT_PATH …)`, and the core library lists its feature header in the build tree
  (`libosmscout/CMakeLists.txt`).

(The removed-block history and the issue references are `guidelines/BuildNative.why.md` §12b.)

## 12c. vcpkg Dependencies

- Cairo, pixman, fontconfig, freetype, libpng, expat, brotli, bzip2.
- Pango, harfbuzz, fribidi, glib, libffi, pcre2, gettext, libiconv, libuuid, pthreads.
- zlib, libxml2, protobuf, abseil.
- All installed for 3 Android triplets: arm64-android, arm-neon-android, x64-android.

## 12d. Rebuilding vcpkg packages

The command is `guidelines/BuildNative.recipes.md` §12d.

## 12e. vcpkg usage pattern (CI)

- **Classic mode, no manifest**: the dependency list is hardcoded in `setup-vcpkg.sh` (`DEPS=(...)`), not in
  a `vcpkg.json` manifest. Overlay ports (`vcpkg-overlays/`, e.g. marisa-trie) and overlay triplets are
  passed via `--overlay-ports`/`--overlay-triplets`.
- **CI pins vcpkg**: `.github/workflows/build.yml` sets `VCPKG_COMMIT` to a specific commit and fetches it
  shallowly; pinning keeps the vcpkg tool ABI-stable.
- **CI binary cache = NuGet feed**: `VCPKG_BINARY_SOURCES=clear;nuget,…` stores each compiled package as a
  NuGet entry versioned by its vcpkg ABI hash (shared org feed). Per-package ABI versions mean a
  runner-image/toolchain update causes exactly one rebuild, then the cache self-heals. The job runs with
  `permissions: packages: write`; mono-complete is installed for `nuget.exe`. Fork PRs restore but skip
  pushes (read-only token).
- **The old files-provider design is not reintroduced** — it restored stale archives on an exact key hit,
  vcpkg rejected them all, and the post-step never re-saved; do not reintroduce it.
- **Adding/removing a dependency**: edit the `DEPS` list in `setup-vcpkg.sh`; for an unregistered port, add an
  overlay port under `vcpkg-overlays/`.
- **Refreshing ports in CI**: bump `VCPKG_COMMIT` in the workflow.
- **`setup-vcpkg.sh` tolerates install failures** (`|| echo`), so CI runs an explicit `.pc` verification gate
  after it.

## 12f. Stylesheet and icon packaging

The submodule is the single source of truth for the map style sheets and the raster icons
(`guidelines/MapStyling.md` §16 and §16a own the runtime contract).

- `app/build.gradle.kts` copies `stylesheets/` into `build/generated/assets/stylesheets`
  (`syncSubmoduleStylesheets`, wired into `preBuild` and all `merge*Assets` tasks) — there is **no committed
  snapshot** in `app/src/main/assets/`, so a submodule bump changes the next APK with no manual step.
- `checkSubmoduleStylesheets` (in `preBuild`) fails with an actionable message when the submodule is not
  initialized (fresh clone: `git submodule update --init --recursive`).
- `syncSubmoduleIcons` packages `libosmscout/data/icons/14x14/standard/` as `assets/icons/14x14/standard`,
  and `checkSubmoduleIcons` fails when the submodule or that raster leaf is missing.
- `PackagedPoiIconsTest` fails the build when a stylesheet asks for an icon (a `name:` without an inline
  `symbol:`) that the packaged set does not carry.
