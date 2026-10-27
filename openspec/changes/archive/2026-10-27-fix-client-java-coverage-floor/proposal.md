# fix-client-java-coverage-floor

surface: 13 test classes added, 5 cases added to 1 existing test class, 0 production files, 0 fixtures, 0
build files — `ls osmscout-client-java/src/test/java/com/framstag/libosmscout/client/*.java | wc -l` (8 → 21
test classes), the module's own tally (`48` → `106` cases, 0 failures in both), and the base report's class
counter (`CLASS covered=13 missed=21`, i.e. 21 of the module's 34 compiled classes had no covered
instruction). The count is the tail the change pays for, not the first file the plan names: the module
compiles the submodule's client API beside its five local overrides, so reaching the floor is one case per
untested class — nine of them data carriers, four behavioural (the POI table, the installed basemap, the
download worker, the installed-map scan) — not one new test file.

## Why

The coverage row reads the module's instruction coverage out of the JaCoCo report and files an issue below
its floor; the module sat at 37.2 %, so the floor was a bound nothing in the tree answered for.

- **The condition, verified in the tree today.** `tools/sensors/sensors.json:59-70` declares the `coverage`
  row: `"cmd": "tools/sensors/metrics/coverage.sh"`, `"threshold": 70`, `"comparison": "below-floor"`, input
  `app/build/reports/jacoco/coverage.json`, fingerprint field `module`.
  `tools/sensors/metrics/coverage.sh:38-41` compares each module's percentage against that floor and emits
  the violation the item was filed from. The number itself is written by
  `tools/build-inputs.sh:91-114` (`xml_instruction_percentage`, the last root
  `<counter type="INSTRUCTION">` of `osmscout-client-java/build/reports/jacoco/test/jacocoTestReport.xml`),
  and on the base commit it read **covered=1567 missed=2643 → 37.2 %**.
- **Root cause, one mechanism.** The `:osmscout-client-java` module compiles 34 classes — its five local
  overrides plus the submodule's Java client API (`osmscout-client-java/build.gradle.kts:20-37`,
  `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/`) — and its
  suite held 8 test classes / 48 cases that exercise 13 of them; the other 21 classes (1421 of the module's
  4210 instructions: `BasemapManager$BasemapInfo`, `CurrentRoadInfo`, `DescriptionEntry`, `FavoriteLocation`,
  `FavoriteLocationGroup`, `InstalledMaps`, `LaneTurn`, `NavigationController`, `NavigationListener`,
  `NavigationPosition`, `NavigationState`, `ObjectDescription`, `OSMScoutClient`, `OSMScoutClientBuilder`,
  `PoiCategories`, `PoiEntry`, `RoadInfo`, `RouteCallback`, `RouteEntry`, `StarredFavoriteLocation`,
  `TrackPoint`) held no covered instruction. Eighteen of them are reachable from the host JVM and each got a
  case; the three that are not — `OSMScoutClient`, `OSMScoutClientBuilder` and `RouteCallback`, which load
  the native library in a static initializer — are the module's JNI surface, whose stub lives in
  `app/src/test/jniLibs` and `auto/src/test/jniLibs` only (`guidelines/Build.md`, "The JNI stub for unit
  tests"), and they are not the delta.
- **Red case.** `coverage-floor-probe.sh <tree> <out.xml>` reads the module's JaCoCo report and asserts its
  instruction coverage against the row's floor of 70 % — the same arithmetic as
  `tools/build-inputs.sh:91-114`, so the case measures what the sensor measures. On a copy of the base tree
  whose test sources are the base's own (the change's cases removed), it fails:
  `LOOP-COVERAGE module=osmscout-client-java covered=1567 missed=2643 percentage=37.2 floor=70
  verdict=failed at=2026-10-27T00:55:43Z`; the tally is `evidence/red-on-base.xml`
  (`tests="1" failures="1"`). On the change it passes at 74.2 % (`evidence/green-on-change.xml`,
  `tests="1" failures="0"`, 2026-10-27T00:55:46Z).
- **Owning requirement.** `openspec/specs/test-coverage/spec.md`, "All test-bearing modules produce coverage
  metrics" — the requirement that says the module's metrics exist. What the item adds is the other half:
  that the suite the metrics are taken from covers the classes the module ships, and the scenario that
  measures the number against the row's floor.
- **Owning guideline section.** `guidelines/Build.md`, "Code coverage" — the two-engine table (JaCoCo for
  this module), the report-only rule, and the baseline line that read "`osmscout-client-java`: LINE 5
  covered / 1 missed" before the module grew its overrides.

## What changes

1. **Thirteen test classes under `osmscout-client-java/src/test/java/com/framstag/libosmscout/client/`** —
   one per class the module compiled without a case: `PoiCategoriesTest` (the category table, its clone and
   its unmodifiable map), `LaneTurnTest` (the ids against the native enum), `NavigationPositionTest`,
   `CurrentRoadInfoTest`, `RoadInfoTest`, `ObjectDescriptionTest`, `ClientValueTypesTest` (track point,
   favourite, group, starred favourite, POI entry, description entry, route entry and the two listener
   interfaces' default methods), `TurnTypeAndInstructionTest` (both spellings the bridge sends, and the
   instruction constructors), `AvailableMapEntryTest` (the list entry, its search, the archive label and the
   provider), `InstalledMapsTest` (the `types.dat` scan and its exclusion), `BasemapManagerInstalledTest`
   (`BasemapInfo`, size, installed test, delete), `MapDownloadManagerListingTest` (the listing URL, the empty
   answer on a failing provider, the refusal of a directory entry, a worker that dies being reported) and
   `NavigationControllerTest` (the substitutable controller).
2. **Five cases in the one test class that already exercises `BasemapManager`** — a
   successful download that installs the basemap, an archive that wraps its content in one directory being
   flattened, the listing probe reading the server's directory listing, the probe yielding nothing when the
   server fails or is unreachable, and the invalid version-date names that encode no date.
3. **No production file changes.** The delta is a suite, not a behaviour: no `src/main` file, no build file,
   no fixture and no guideline sentence moved.
