# Native slice verification (tasks 2.1-2.7, 2.9, 2.11-2.13)

Session: apply pass on 2026-10-14, worktree shared with an active second builder (see "State of the tree").
No submodule commit was made and the gitlink was not bumped: task 2.10 is blocked until the tree has one owner.

## Builds

| what | command | result |
|---|---|---|
| JNI translation unit (host, CMake) | `ninja -C build osmscout_client_java` | `OSMScoutClient.cpp.o` built, `libosmscout_client_javad.so` linked, no error/warning lines |
| submodule Java + JAR | `ninja -C build osmscout_client_java java_jar` | `Compiling Java sources and generating JNI headers` + `Packaging Java classes into jar` — the new `beginSearch` / `cancelSearch(long)` / generation-carrying natives compile |
| host policy test | `meson compile -C hostbuild SearchBreakerTest` then `meson test -C hostbuild "Check search breaker" --verbose` | `All tests passed (29 assertions in 8 test cases)`, `Ok: 1  Fail: 0` |
| neighbour header test (regression) | `meson test -C hostbuild "Check search scope" --verbose` | `All tests passed (50 assertions in 9 test cases)` — unchanged |

Note: `libosmscout_client_javad.so` builds on the host with `OSMSCOUT_BUILD_CLIENT_JAVA=ON` and
`Java_JAVAC_EXECUTABLE=/usr/bin/javac`, i.e. the JNI TU *is* host-compilable in the CMake tree
(`build/`). That contradicts `TODO.md` §66's "the host build cannot compile the JNI translation unit
(`jni.h` is configured against a JDK that is not installed)" — worth correcting when that entry is
next touched.

## App-side compile and existing behaviour

`./gradlew :app:testMobileDebugUnitTest --tests MapCanvasViewModelSearchCancellationTest
--tests MapCanvasViewModelFreeTextSearchTest --tests PoiSearchViewModelTest` →
`22 tests completed, 2 failed`; the 2 failures are the new red cases
(`oneCharacterQueryShowsNoResults`, `shorteningTheQueryToOneCharacterClearsAndAborts`), i.e. still red
until the gate lands them (group 3). `MapCanvasViewModelFreeTextSearchTest` and
`PoiSearchViewModelTest` pass, so the Java-API change kept the existing search behaviour intact.

`FakeOSMScoutClient` had to be updated in the same step (`searchPOIsByTypes` gained the generation
parameter, plus `beginSearch`/`cancelSearch` stubs): without it the app's test sources did not compile,
because the fake overrode the old native signature. The file is one of the second builder's dirty files
— see "State of the tree".

## Revert-checks (red then green, evidence files alongside)

| task | mutation | red | green after restore |
|---|---|---|---|
| 2.11 | `Begin` no longer drops the slot's predecessor (the supersede term) | `SearchBreakerTest.cpp:40,52,75` failed, `test cases: 8 / 3 failed` | `All tests passed (29 assertions in 8 test cases)` |
| 2.12 | `Cancel(gen)` ignores its argument | `SearchBreakerTest.cpp:63,75,89,105,142` failed, `test cases: 8 / 5 failed` | `All tests passed (29 assertions in 8 test cases)` |
| 2.13 | `Begin` keys every surface to one shared slot | `SearchBreakerTest.cpp:101` failed, `test cases: 8 / 1 failed` | `All tests passed (29 assertions in 8 test cases)` |

Files: `revert-check-2.11-*.txt`, `revert-check-2.12-*.txt`, `revert-check-2.13-*.txt`. No mutation
marker is left in the tree (checked with `grep -rn 'REVERT-CHECK MUTATION'`).

## Not verified here

- **JavaScout port** (`JavaScout/src/main/java/com/framstag/libosmscout/SearchOverlay.java`): neither the
  CMake host build nor Gradle builds JavaScout in this repository, so the port of its two call sites
  (`cancelSearch()` → `cancelSearch(generation)`, the boolean form → the generation form, plus a
  `volatile long searchGeneration` field) is verified by inspection only: every method it calls
  (`beginSearch`, `searchLocations(String,int,String,long)`, `cancelSearch(long)`) exists in the
  submodule's Java with those signatures.
- **A JNI symbol mismatch** would not be caught by any build here: the natives are hand-written
  `extern "C"` symbols and the app's tests go through `FakeOSMScoutClient`. The mangled names
  (`…_searchLocations__Ljava_lang_String_2IJJ`, `…_searchLocations__Ljava_lang_String_2ILjava_lang_String_2J`,
  `…_searchLocationByForm__Ljava_lang_String_2Ljava_lang_String_2Ljava_lang_String_2Ljava_lang_String_2IJ`,
  plus the unmangled `beginSearch`, `cancelSearch__J`, `searchPOIsByTypes`) are therefore device-gated
  (task 5.6), which is why the transitional forms were moved into Java instead of adding more natives.

## State of the tree (why 2.10 is blocked)

The worktree carries a second, active builder (`distinguish-routing-failures`, just archived
`fix-roundabout-exit-number`): the parent repo has ~80 dirty files, the submodule has an uncommitted
commit `3b29204e3` plus dirty files, and the parent gitlink is dirty. Committing the submodule here would
sweep their work into this change's commit. My writes are confined to regions their diff does not touch
(`OSMScoutClient.cpp` search functions and the removed globals; `Tests/CMakeLists.txt`,
`Tests/meson.build`; the two Java API files; `search_breaker.h` and `SearchBreakerTest.cpp` as new
files) with one exception: `FakeOSMScoutClient.kt` and `auto/.../SearchScreenTest.kt` are files they also
have dirty, touched here because leaving them stale breaks the build.
