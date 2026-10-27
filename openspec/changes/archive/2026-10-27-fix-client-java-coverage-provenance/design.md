# Design — the Java module's coverage is two provenances

## Root cause

`osmscout-client-java/build.gradle.kts` built one `main` source set out of two provenances: `src/main/java` (the
project's six local overrides) plus a `source(fileTree(clientJavaDir))` addition of the submodule's Java client
sources. JaCoCo measures a source set's classes directory, the producer publishes its root counter, and the
coverage row keys on what the producer wrote — so exactly one report, one percentage and one row key existed for
both provenances. The measurement on the base commit: `covered=3124 missed=1086` (74.2 %) for the mixture,
`1782/2854` (62.4 %) for the own half and `1342/1356` (99.0 %) for the vendored half.

## The single fix

The provenance is the source set. Split it, and the report, the percentage and the row key split with it:

- `main` keeps `src/main/java`; a new `vendored` source set carries `clientJavaDir` with the five exclusions the
  `compileJava` wiring used to carry. `compileOnly(sourceSets["vendored"].output)` gives the overrides their
  compile classpath, `testImplementation(...)` gives the suite both provenances, and
  `tasks.jar { from(sourceSets["vendored"].output) }` keeps the published artifact one jar — `:app` and `:auto`
  consume it unchanged.
- `jacocoTestReport` is narrowed to `main`'s classes directory and `jacocoVendoredTestReport` measures the
  vendored one; both read the one `build/jacoco/test.exec` the suite produced.
- `tools/build-inputs.sh` reads the two XMLs into two keys, `osmscout-client-java` and
  `osmscout-client-java-vendored`, and refuses the summary when the vendored report is missing.
  `tools/sensors/metrics/coverage.sh` needs no change: it applies the row's floor per key and fingerprints by
  key, which is exactly the provenance the report now carries.

The floor is the second half. A split alone would have published `osmscout-client-java` at 62.4 %, below the
floor — the report would name the own-code deficit instead of hiding it, but the row would still fire. The own key
clears the floor by measuring real project code: the host stub is put on this module's test `java.library.path`
(`systemProperty`, since the module has no AGP), a `FakeOSMScoutClient` overrides the native entry points the
convenience methods call, and two test classes exercise `OSMScoutClient` (97.4 %) and `OSMScoutClientBuilder`
(96.9 %). Own reads 73.5 %.

## Blast radius

- **Published artifact**: unchanged. The jar still carries 34 classes (`unzip -l` over
  `osmscout-client-java/build/libs/libosmscoutclientjava-1.0.0.jar`).
- **`:app` and `:auto`**: no source change. `native-parity.txt` and `checkNativeParity` read directories, not
  source sets.
- **Coverage inputs**: `app/build/reports/jacoco/coverage.json` gains a key; the row applies its floor to every
  key, so the registry (`tools/sensors/sensors.json`) is untouched.
- **CI**: the report step gains `:osmscout-client-java:jacocoVendoredTestReport` and the artifact path widens to
  `osmscout-client-java/build/reports/jacoco/**`, so a missing vendored report still fails the upload
  (`if-no-files-found: error`).
- **The stub's third home**: `osmscout-client-java/src/test/jniLibs`, re-included in `.gitignore`, wired by the
  module's own `java.library.path`. The classloader rule of `guidelines/Build.md` §6a does not reach it: that
  rule's premise is Robolectric's per-test classloader and this module's suite is plain JUnit 5.

## Alternatives the evidence eliminated

- **A class-name exclusion list in `jacocoTestReport`** (23 names in `classDirectories`): the list has to be kept
  in step with every submodule bump, and a new upstream class silently re-enters the denominator. The source set
  carries the same five exclusions the compile wiring already had.
- **A name filter in `tools/build-inputs.sh`**: the same list in a shell script, where no reader of the report can
  see it.
- **Shrinking the denominator** (excluding the vendored classes from the module's only report): the report would
  stop measuring what the pin ships, and a pin bump that adds an unreached upstream class would be invisible.
- **Moving the vendored classes' tests upstream** (the mechanism the item's escalation named): a submodule commit
  plus a gitlink bump, which this change does not need — the vendored key measures the pin at 99.0 % either way.

## Rollback

Revert the commit. The `vendored` source set and `jacocoVendoredTestReport` go with it, the producer stops
writing the second key, and the module's report returns to the mixed number. The stub and the two test classes are
additive and can stay — leaving them keeps the own-code gain, so a partial rollback is the split alone.
