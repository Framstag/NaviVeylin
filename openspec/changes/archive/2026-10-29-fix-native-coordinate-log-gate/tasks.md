# Tasks — fix-native-coordinate-log-gate

## 1. Red case

- [x] 1.1 Wire the native half of the gate first and run it with the submodule patch reverted (the tree's
      seven coordinate lines restored). Verify: `./gradlew :app:checkNoCoordinatesInLogs` fails and names all
      seven statements — `OSMScoutClient.cpp:6296`, `:7024`, `:7033`, `:7893`, `:7901`, `:7911`, `:7919`, each
      as `position accessor 'GetDisplayText'` (`evidence/red-gate-on-unpatched-submodule.log`).
      *(spec: `auto-diagnostics` — Diagnostics carry no coordinates)*
- [x] 1.2 Unit fixtures for the scanner: the four legacy trace lines and their reported line numbers, the box
      line, `GetLat`/`GetLon`, a position-free statement, a commented-out statement, and a message containing
      `);` and `//`. Verify: `NativeCoordinateLogScannerTest`, 6 cases.
      *(spec: `auto-diagnostics` — A new coordinate in a log line fails the build)*

## 2. The submodule patch

- [x] 2.1 Remove the position from the seven `osmscout::log` statements, keeping precision-free identity:
      database path (`:6296`), search radius (`:7024`, `:7033`), route end (`:7893`, `:7901`, `:7911`,
      `:7919`). Verify: `grep -n GetDisplayText OSMScoutClient.cpp` reports no match; commit `fab852ff9` on
      `naviveylin-local`, pushed (`git ls-remote origin naviveylin-local` = `fab852ff9`).
      *(spec: `osmscout-jni` — The start is published without a coordinate in the diagnostics)*

## 3. The gate

- [x] 3.1 `NativeCoordinateLogScanner` in `buildSrc` (`com.naviveylin.build.diagnostics`): statement-bounded,
      comment- and string-aware, accessor list `GetDisplayText|GetLat|GetLon|GetLatitude|GetLongitude`, no
      allowlist. Verify: `./gradlew -p buildSrc test --tests '*NativeCoordinateLogScannerTest*'` → 6/0.
- [x] 3.2 `checkNoCoordinatesInLogs` scans the app's own `src/main/cpp/*.cpp|h` and the JNI translation unit
      directory, named rather than walked (the submodule is a sibling of those files). Verify: the gate is
      green after the submodule patch, and red when 1.1 reverted it.
- [x] 3.3 `guidelines/Logging.md` §1 states the rule as gated, names its scope and its two residuals.
      Verify: `bash tools/check-doc-routes.sh` green.

## 4. Verification and archive

- [x] 4.1 `./gradlew -p buildSrc test --tests '*CoordinateLogScannerTest*' --tests
      '*NativeCoordinateLogScannerTest*'` and `./gradlew :app:checkNoCoordinatesInLogs` green after the
      patch; both recorded in `evidence/`.
- [x] 4.2 `openspec validate fix-native-coordinate-log-gate --strict` exits 0
      (`evidence/openspec-validate.log`).
- [x] 4.3 Archive the change under `openspec/changes/archive/2026-10-29-fix-native-coordinate-log-gate/`.
      Verify: `openspec/changes/fix-native-coordinate-log-gate` no longer exists.
