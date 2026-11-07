# The Java module's coverage is two provenances, not one denominator

surface: 2 test classes + 1 fake added to `:osmscout-client-java`'s suite, 1 case added and 1 case rewritten in
`tools/build-inputs-cases.sh` (with its `module_reports` fixture growing one report), 1 module build script, 1
producer (`tools/build-inputs.sh`), 1 CI step, 1 test's KDoc, 3 sections of `guidelines/Build.md` (§6a, §6h and
"Code coverage"),
2 committed stub files, and one `test-coverage` requirement — `ls
osmscout-client-java/src/test/java/com/framstag/libosmscout/client/*.java | wc -l` (21 → 24 files), the module's
tally (`106` → `116` cases, 0 failures in both), and `bash tools/build-inputs-selftest.sh --list | wc -l`
(`11` → `12` cases). The count is the tail the change pays for, not the first file the plan names: the split is
one source set and one report task, and the floor the own key has to clear is what costs the two test classes.

## Why

Item **#678** (fingerprint `ad8b0c03a37dd836`, class `improvement`, source `ci`) records that
`osmscout-client-java`'s coverage report is one number for two provenances — the project's own six local sources
and the 23 vendored `libosmscout` Java client sources compiled beside them.

- **The condition, verified in the tree today.** `osmscout-client-java/build.gradle.kts` compiled
  `src/main/java` and the submodule's `clientJavaDir` (minus the five overridden files) into **one** source set,
  so `jacocoTestReport` measured both into one counter — re-measured on the base commit: INSTRUCTION
  `covered=3124 missed=1086` (74.2 %), of which the module's own sources are `1782/2854` (**62.4 %**, below the
  70 % floor) and the vendored sources `1342/1356` (**99.0 %**). `tools/build-inputs.sh` published that one
  counter as one key and `tools/sensors/metrics/coverage.sh` applied the row's floor to it, so a vendored-code
  gain paid for an own-code deficit and the emitted condition could not name whose code moved.
- **Root cause, one mechanism.** The provenance is a property of the **source set**, not of the class name: one
  source set means one classes directory, one JaCoCo report and one row key. Splitting the source set splits the
  report, and with it the key — no class-name list to keep in step with a submodule bump (the alternative #678
  rejected).
- **Red on HEAD** — `bash tools/build-inputs-selftest.sh --case inputs#splitsTheJavaModulesCoverageByProvenance`
  fails with `the vendored key reads the vendored report, so a pin bump moves it alone: expected '95.0', got
  'null'`, and the rewritten `inputs#convertsPerModuleCoverage` reads four keys where five are required
  (`evidence/red-on-head.log`, `evidence/red-on-head-converts.log`).
- **Green on the change** — the same two cases pass. Own code needs more than a split though: the floor is what
  the own key is compared against, so the second half of the delta makes the module's JNI surface reachable from
  a host case (the stub in `osmscout-client-java/src/test/jniLibs`, a fake beside the suite) and the own key
  reads **2099/2854 = 73.5 %**; the vendored key reads **1342/1356 = 99.0 %** (`evidence/jacoco-own.xml`,
  `evidence/jacoco-vendored.xml`, module tally `23` classes / `116` cases / 0 failures).

**Owning requirement**: `test-coverage` — "The pure-Java module's suite covers the classes it ships"; the
sentence that one mixed floor paid for is replaced by two keys, one per provenance. **Owning guideline**:
`guidelines/Build.md` "Code coverage" (the two report tasks and the two row keys), §6h (the stub's third home)
and §6a (why the classloader rule does not reach a plain-JUnit module).

## What the change does

1. **Two source sets, two reports.** `main` stays `src/main/java`; a new `vendored` source set carries
   `clientJavaDir` with the same five exclusions; `compileOnly`/`testImplementation` give the overrides their
   compile classpath and the jar still ships both (`from(sourceSets["vendored"].output)`), so `:app` consumes one
   artifact as before. `jacocoVendoredTestReport` reads the one `test.exec` of the suite.
2. **Two row keys.** `tools/build-inputs.sh` writes `osmscout-client-java` from the own report and
   `osmscout-client-java-vendored` from the vendored one, and refuses to publish the summary when the vendored
   report is missing instead of reading the mixed number.
3. **The own key clears the floor** by measuring real project code: the committed host stub is on this module's
   test `java.library.path`, `FakeOSMScoutClient` answers the native entry points, and two test classes exercise
   `OSMScoutClient`'s convenience methods (97.4 % of its instructions) and `OSMScoutClientBuilder`'s setters
   (96.9 %) — the 682 instructions #678 named as unreachable, 24 % of the own figure.

## Out of scope

- The 13 test classes #320 added for **vendored** classes stay where they are. Moving them upstream is a
  submodule change (a person lands it and the gitlink bump follows), and this change does not need it: the
  vendored key measures what the pin ships, and it is at 99.0 %.
- `MapDownloadManager`'s remaining uncovered instructions (330). The own key clears the floor without them; they
  are the next measurement, not this delta.
