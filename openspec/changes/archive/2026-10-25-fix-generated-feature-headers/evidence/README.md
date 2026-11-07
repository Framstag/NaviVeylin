# Evidence

Raw logs of the runs this change quotes. Every timestamp is the run's own; paths are relative to the repository
root (`app/src/main/cpp/libosmscout/build` is the host build tree used for the red/green pair, configured once with
`-DCMAKE_DISABLE_FIND_PACKAGE_Pango=ON`).

| file | what it shows |
|---|---|
| `host-nopango-red.log` | the defect: with the Android-generated `MapFeatures.h`/`MapCairoFeatures.h` copied into the submodule source tree, the host case fails — `REQUIRE(differs)`, 218.154 vs 218.154, 7 cases / 1 failed |
| `host-nopango-green.log` | the same tree and configure after the fix — `All tests passed (167 assertions in 7 test cases)` |
| `cmake-path-version-check.log` (+ `.cmake`) | `cmake_path(GET … PARENT_PATH …)` is a no-op on the CMake 3.22.1 the AGP ships (`c/MapFeatures.h.cmake`) and applies on 4.4.4 (`c`) |
| `gate-precedes-native-configure.log` | `:app:configureCMakeDebug` runs `preBuild` first, so a gate that only refused would block the configure that writes the build-tree headers |
| `gate-red.log`, `gate-green.log` | the gate on a planted generated header — removes it and fails naming it; the next run is green |
| `gate-revert-check.log`, `gate-revert-check-restored.log` | the removal's revert-check: with `delete()` disabled the file survives; restored, it is removed |
| `submodule-patch-revert-check.log` | the submodule patch reverted in a fresh `.cxx` tree — `Cannot find source file: include/osmscout/lib/CoreFeatures.h`, `No SOURCES given to target: OSMScout` |
| `submodule-patch-restored.log` | the patch restored — the same configure is green |
| `submodule-patch.diff`, `submodule-patch.stat.txt` | the submodule patch (2 files, 7 insertions, 2 deletions) |
| `native-configure-arm64.log` | the final tree: `:app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` green, with the corrected template directory in the CMake output |
| `assembleDebug-all-abis.log` | `:app:assembleDebug` — both flavors, all three ABIs |
| `jvm-gate.log` | the whole JVM gate, green (`:app` 2109/0/0 both flavors, `:auto` 880/0/0, `:core` 646/0/0, `:osmscout-client-java` 48/0/0) |
| `jvm-gate-stacktrace.log`, `jvm-gate-single-class.log`, `jvm-gate-retry.log`, `core-test.log` | the `java.io.EOFException` attribution: `Test.getPreviousFailedTestClasses`, a truncated `results-generic.bin` from a worker killed under memory pressure; green after `--stop` and clearing `test-results` |
| `configuration-cache.log` | `--configuration-cache :app:preBuild` — 5 script-level problems, none of them the new gate |
