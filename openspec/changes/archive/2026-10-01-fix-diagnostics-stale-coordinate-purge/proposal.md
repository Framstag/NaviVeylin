# Proposal — fix-diagnostics-stale-coordinate-purge

## Why

`fix-diagnostics-coordinate-redaction` stopped *new* coordinates from reaching the diagnostics stream,
but the file an earlier build already wrote still holds positions, and the 7-day retention bound keeps
them for the whole window after an update. The redaction change's own device recipe (recorded as
`TODO.md` §88) hits exactly one such entry on the test install:

```
[2026-09-25 21:34:34.085] LONGPRESS lat=51.513298135108705 lon=7.474341597216892 mag=16.0
```

The app now states that guarantee as a shipping property — `guidelines/Regulatory.md` §9 ("no
latitude/longitude pair reaches logcat or the file"), the build gate `checkNoCoordinatesInLogs`, and
the disclosure that leads the exported text and both viewers — so for up to 7 days after every update
the documented and disclosed claim is false for the file the user exports. The prune already rewrites
the file line by line, so this needs a predicate, not new machinery.

## What Changes

- **The retention pass also drops coordinate-carrying lines, regardless of age.** The age window and
  the coordinate rule become two independent reasons to drop a line; a coordinate entry written by a
  pre-redaction build is younger than the window and would otherwise survive it.
- **A named, unit-testable predicate defines "coordinate-carrying line"** for the log file: a
  coordinate token (`lat`, `lon`, `latitude`, `longitude`, case-insensitive, delimited) together with
  a number carrying four or more fraction digits. Every identity line the project writes today —
  magnification, screen pixel (`x=`, `y=`), `map=…`, accuracy, bearing, object label/id — stays.
- **The drop is reported once per pass**, through the existing retention diagnostics line, so a reader
  can tell a purged log from a complete one. No per-line entries: the log must not become a coordinate
  index, and the report must not itself name the dropped position.
- **The purge keeps the logging contract**: it runs in the retention pass on the logging worker, in
  the same bounded rewrite, never on the caller's thread. The active file and the rotated file are
  both covered.
- **No format change, no runtime logging change.** What gets logged is still owned by the build gate;
  this change only removes history that contradicts it.
- **Non-goals**: no one-shot migration flag (the pass already runs on the first flush of a process and
  on day rollover), no change to the retention window or byte cap, no change to what the app logs or
  displays, no `:app`/`:auto` UI change, no native/JNI change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- **`auto-diagnostics`** — the file-history half of "Diagnostics carry no coordinates": an existing
  line that carries a coordinate is removed by the retention pass even when it is younger than the
  retention window, and the removal is reported without naming the position. The delta is `ADDED`
  (new requirement "Coordinate-carrying entries do not survive the retention pass"), not `MODIFIED`,
  because the coordinate-free requirement it complements is still in flight in
  `fix-diagnostics-coordinate-redaction` and an archive-order overwrite would fight it — see
  `design.md` D2.

## Impact

- **Code**: `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt` (the retention pass and the new
  predicate), `core/src/test/java/com/naviveylin/core/DiagnosticsLogRetentionTest.kt` (purge cases),
  plus a focused predicate test class next to it if the predicate gets its own file.
- **Docs**: `guidelines/Regulatory.md` §9 (state that the rule covers the file's history, not only new
  writes), `TODO.md` §88 (removed when this change is archived, per the entry's own condition).
- **Spec**: `auto-diagnostics` gains one requirement; no other capability is touched. The disclosure
  string (`diagnostics_disclosure`) needs no wording change — it already claims no coordinates, which
  this change makes true for history as well.
- **Guidelines affected**: `guidelines/Regulatory.md` §3 (the file is personal data at rest) and §9.
  `guidelines/UI.md`, `guidelines/MapRendering.md` and `guidelines/Design.md` are unaffected.
- **Scope**: shared code, so both surfaces (phone and Android Auto/AAOS) are covered by the one change;
  there is no phone/car parity question. No manifest, resource or Hilt change.
- **Additive, not breaking**: a log that carries no coordinate behaves exactly as today (same pass,
  same rewrite trigger, same reporting shape). Rollback: revert the predicate call in the retention
  pass; nothing outside the log file persists, and a rolled-back build simply keeps old entries until
  the age prune removes them.
- **Tests/verification**: unit tests in `:core` (no device needed) — the purge of a young
  coordinate line in both the active and the rotated file, the keep-cases for the identity lines the
  project writes, the single report, and the caller-never-blocks assertion. The on-device half is a
  re-run of the `TODO.md` §88 recipe (`guidelines/Build.md` §10) on an install that ran a pre-redaction
  build, which needs a device or emulator.
