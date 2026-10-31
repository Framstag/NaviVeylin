# Build Native — why

Rationale and dated provenance for `guidelines/BuildNative.md`. Read beside the section it anchors to.

## §10f Two checks that need evidence before a behaviour is specified

The template-rate check is recorded in `TODO.md` §64.

## §10j A calculated route's length

- **`AbstractRoutingService::GetOverallDistance()` is not a route length.** Measured 2026-10-05 against the
  drawn polyline it was 0.748× on a ~70 km route (72 771 m published vs 97 283 m drawn), 0.795× on a 20 km
  one and 0.552× on a 1.5 km one, while the description tracked the polyline to 1.0014 / 1.0021 / 0.9964 —
  and the real road distance Dortmund Hbf → Cologne Hbf is ~95-100 km, i.e. the polyline's number, not the
  published one.
- The AAOS AVD's basemap can be a format version behind, which the app refuses with a
  `Basemap not registered: … names data format N, this build reads M` line (issue #23) — re-download it
  before comparing basemap-dependent numbers (`TODO.md` §99).
- The freshly built **test** APK trap was measured 2026-10-07: a revert-check “passed” by installing the
  stale `outputs/` APK until the intermediate APK was installed; the same trap as the stale asset APK in
  `TODO.md` §17, one layer down. The class may live in `classes3.dex`.

## §12b Configured feature headers

`app/src/main/cpp/CMakeLists.txt` *used* to render the same four headers from the cache values that stood
before `add_subdirectory(libosmscout)` (marisa on, every other feature off) and prepend that directory, so the
Android build compiled against that block rather than against what the library computed (§305; the
include-order mechanism that let it win is §202, issue #136). Removed 2026-10-26.

The Android configure's values are `features.cmake`'s, measured on the arm64 build tree 2026-10-26.

The include-order trap: a copy in the submodule source tree wins over the generated header in *every* build
of that working tree, a host build included — that host build then compiled `libosmscout-map*` against the
Android configuration's macros; with Pango disabled, the Cairo text case measured the substituted family as
the configured font file and failed `REQUIRE(differs)` (§202, issue #136).

The two submodule fixes: the CMake 3.22 the Android Gradle plugin ships makes `cmake_path(GET … PARENT_PATH …)`
a no-op, which wrote a module's header into `<Name>Features.h.cmake/<Name>Features.h`; the core library used
to need its feature header in the source tree.
