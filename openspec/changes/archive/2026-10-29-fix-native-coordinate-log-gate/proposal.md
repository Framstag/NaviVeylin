# Proposal — fix-native-coordinate-log-gate

## Why

**The condition, verified in the tree at base `2ab6a238` (2026-10-29).** The rule that a log line carries
no position is stated twice — `guidelines/Logging.md` §3 for Kotlin, §1 for native code — but only the
Kotlin half is a gate: `checkNoCoordinatesInLogs` (`app/build.gradle.kts:410`) walks `*.kt` in three source
directories and hands each file to `CoordinateLogScanner`. Nothing reads a `*.cpp`, so a native line that
streams a coordinate neither fails a build nor is noticed.

That is not hypothetical. The JNI translation unit carried seven such lines: the bounding box a description
search checked, the coordinate a `GetClosestRoutableNode` lookup succeeded at (1 km and 5 km), and the start
and destination of the legacy route entry, on the resolving line and again on the failure line —
`osmscout::log.Warn() << "calculateRouteAsync: resolving start at " << start.GetDisplayText()`. A `GeoCoord`
or `GeoBox` display text is a coordinate pair, and those lines reach Logcat under the app's own tag, so each
one is a coordinate log line by the letter of the rule (`guidelines/Logging.md` §1–2,
`guidelines/Regulatory.md` §9).

Issue #125 recorded the seven lines while implementing `distinguish-routing-failures` (task 2.2) and left the
decision open: land the deletions alone, land them with a native gate, or declare the gate Kotlin-only. The
owner chose the second — delete, and close the gap that let the lines live.

## What Changes

- **Submodule commit `fab852ff9`** (branch `naviveylin-local`, pushed): the seven statements log identity
  instead of a position — the database path for the box check, the search radius for the coordinate lookup,
  the route end for the calculation. No behaviour change; the entry is the legacy one the Android app never
  calls, and the information removed was the position itself.
- **`buildSrc` `NativeCoordinateLogScanner`** — a sibling of `CoordinateLogScanner`, not a reuse: native
  logging is a stream (`osmscout::log.Warn() << …;`), so the statement ends at a `;` and tracks parenthesis
  depth, comments and string literals instead of balancing a call's closing parenthesis, and the position
  arrives through an accessor (`GetDisplayText`, `GetLat`, `GetLon`, `GetLatitude`, `GetLongitude`) instead
  of a Kotlin identifier. No allowlist, matching the Kotlin gate's own rule.
- **`checkNoCoordinatesInLogs`** gains the native half, scanning the files this project writes: the app's
  own `src/main/cpp/*.cpp|h` and the JNI translation unit's directory. It cannot walk `src/main/cpp`
  recursively — the libosmscout submodule lives there, 1 127 files of upstream logging habits the fork does
  not own — so the roots are named rather than walked.
- **`buildSrc` test** `NativeCoordinateLogScannerTest`: six cases, the fixtures being the four legacy trace
  lines (with their reported line numbers), the box line, component accessors, a position-free statement, a
  commented-out statement, and a message that contains `);` and `//` — the characters a naive scanner would
  mistake for the statement's end.
- **`guidelines/Logging.md` §1** states the native rule as gated, names the gate's scope and its two
  residuals (accessor-shaped list, so a `TileId` display text is reworded rather than allowlisted; a position
  copied into a local before the statement is not followed), and says upstream libosmscout is not scanned.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- None at spec level, so `.openspec.yaml` declares `skip_specs: true`. The shipped text already covers
  native code and already names this very case: `auto-diagnostics` — *Diagnostics carry no coordinates* says
  "a build-time gate SHALL fail the build when a log or diagnostics call interpolates a coordinate value"
  (no language restriction), and the `osmscout-jni` scenario *The start is published without a coordinate in
  the diagnostics* names its case as "`checkNoCoordinatesInLogs` build gate over `OSMScoutClient.cpp`". This
  change makes that case true instead of aspirational; it states no new behaviour.

## Impact

- `app/src/main/cpp/libosmscout` — gitlink to `fab852ff9`.
- `buildSrc/src/main/kotlin/com/naviveylin/build/diagnostics/NativeCoordinateLogScanner.kt` (new),
  `buildSrc/src/test/kotlin/com/naviveylin/build/diagnostics/NativeCoordinateLogScannerTest.kt` (new).
- `app/build.gradle.kts` — the `checkNoCoordinatesInLogs` task and its comment.
- `guidelines/Logging.md` §1 — one bullet.
- No Kotlin source, resource, API or dependency changes; the gate runs in `preBuild`, which every assemble
  already depends on.
