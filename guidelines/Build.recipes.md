# Build — recipes

Command transcripts, sample output and tallies for `guidelines/Build.md` and the documents cut from it. Read
beside the section its anchor names.

## §7 Code coverage

Usage — one command for all coverage (root Kover merge + both JaCoCo reports):

```bash
./gradlew :koverHtmlReport :koverXmlReport :osmscout-client-java:jacocoTestReport
./gradlew :osmscout-client-java:jacocoVendoredTestReport

# Per-module Kover report (HTML + XML)
./gradlew :app:koverHtmlReport :app:koverXmlReport
```

Engine/agent timings, measured 2026-10-04, one invocation per suite on the same tree, suite wall time from the
first and last result-XML `timestamp`:

| suite | agent attached | agent detached (`-PnoCoverage`) | classes | tests |
|---|---|---|---|---|
| `:app:testMobileDebugUnitTest` | 2m58s | 2m40s | 215 both | 1635 both |
| `:app:testAutomotiveDebugUnitTest` | 2m57s | 2m36s | 215 both | 1635 both |

The class-name sets and the tallies (1635 tests, 0 failures, 0 errors) are identical with and without the
agent.

## §4b Which flavors and suites a run must cover

A gate tally, quoted per module:

```
:core 379/0/0 · :auto 699/0/0 · :app mobile 1314/0/0 · :app automotive 1314/0/0
```

A forced iteration run's shape, measured 2026-10-04 on `:core`:

```
:core:compileDebugKotlin UP-TO-DATE
30 actionable tasks: 2 executed, 28 up-to-date
```

## §4f Artifacts, evidence rules and native verification

The packaged-asset path a Robolectric read uses:

```
app/build/intermediates/apk_for_local_test/<variant>/package<Variant>UnitTestForUnitTest/apk-for-local-test.ap_
```

## §6h The JNI stub for unit tests

The same tiny host-compiled stub (ELF, no symbols) ships under each test source set, named both
`libosmscout_client_java.so` and `libosmscout_client_javad.so`:

```
app/src/test/jniLibs/
auto/src/test/jniLibs/
osmscout-client-java/src/test/jniLibs/
```

AGP puts each Android module's own `src/test/jniLibs` on the unit-test `java.library.path`; the Java module
wires the same directory itself (`osmscout-client-java/build.gradle.kts`,
`systemProperty("java.library.path", …)`) because it has no AGP. Tests override the native methods through
fakes: `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` and the Java module's own
`FakeOSMScoutClient`.

## §4 Result evaluation

Test-report paths:

```
app/build/reports/tests/testMobileDebugUnitTest/index.html
app/build/test-results/testMobileDebugUnitTest/*.xml
```
