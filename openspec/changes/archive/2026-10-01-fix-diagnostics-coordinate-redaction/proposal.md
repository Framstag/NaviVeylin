# Proposal — fix-diagnostics-coordinate-redaction

## Why

`TODO.md` §68 / `guidelines/Regulatory.md` §9: the app writes **6-decimal** coordinates
(≈ 0.1 m) into the debug stream from ~25 call sites and into the persisted diagnostics file from
three (`MapCanvasViewModel.kt:2425` `LONGPRESS`, `auto/MapScreen.kt:652` and
`auto/MapPanHandler.kt:71` `onScroll`). The file is personal data **at rest** — it lives in
`filesDir/diagnostics/app.log`, is bounded only by *size* (`DiagnosticsLog.MAX_BYTES` × 2 files,
`DiagnosticsLog.kt:57-59`) with **no age bound**, and `DiagnosticsLog.exportTextAsync()` hands its
raw lines to the share sheet (`ui/about/AboutDialog.kt:196`). GDPR/ePrivacy need a stated purpose
and a retention bound for that; the Play Data safety form must match it; India's DPDP Rules
additionally prescribe a minimum retention then erasure (`Regulatory.md` §3).

The diagnostics *purpose* does not need coordinates: the entries exist to correlate car-host
failures, template builds, leases, cache sizes and screen→geo mapping — all of which are
identifiable by map database, map file, object identity, magnification and screen pixel.

**Decision (owner, 2026-09-26):** no coordinates in diagnostics at all — neither in the file nor
in logcat; retention 7 days; identity fields (raw object ids, database name) instead; a disclaimer
on export stating what the file may contain.

## What Changes

- **No coordinates in the diagnostic stream.** Every log/diagnostics line that carries a position
  is rewritten to carry precision-free identity instead: object label/id, map database or map file
  name, magnification, screen pixel, accuracy/bearing/timestamps (not personal data on their own),
  and the resolved admin-region name where one exists. The three file-backed sites are included —
  the `LONGPRESS` entry keeps its purpose (screen→geo mapping) through pixel + magnification +
  the resolved object identity, which is what a reviewer compares.
- **Interpolated values are in scope too** (`TODO.md` §87, found while applying this change). The
  first pass left the *logcat* coordinate sources that arrive through interpolation instead of a
  coordinate identifier: `DeepLinkActivity.kt:13` logs the raw intent data (`geo:51.5142,7.4653`),
  `MainActivity.kt:136` logs the whole `SharedLocationRequest` (whose `toString()` prints `lat`,
  `lon` and the label), `MapCanvasViewModel.kt:2512` logs `request.label`, which the parser
  synthesizes as a coordinate pair whenever the sharing app sends no subject, and the car path does
  the same in `auto/NavigationSession.kt:579` (`$destination`). These lines carry the input's origin
  and shape (URI scheme, action, request kind, magnification) instead, and the parser stops
  synthesizing a coordinate-pair label so one cannot reach a log line at all. The extended gate then
  found two more of the same class that §87 had not listed — `auto/SessionLog.kt:29`/`:38` wrote the
  session-start intent's URI into the **file-backed** diagnostics on every session create and
  onNewIntent, which is the worst variant of the defect (personal data at rest) and is covered here
  as well.
- **Age bound.** `DiagnosticsLog` prunes entries older than **7 days** in its own worker (no caller
  ever touches the filesystem — the existing contract), on the first flush of a process and on day
  rollover. The byte cap stays as the size backstop; the rotated file is pruned by the same rule.
- **Disclosure on export.** The shared/exported text carries a disclaimer header naming what the
  file may contain (app/session events, map database and object identifiers, no coordinates, kept
  at most 7 days), and both diagnostics viewers (phone About dialog, car `DiagnosticsScreen`) show
  the same statement as a caption. Strings in `values/` + `values-de/`.
- **A gate, not a habit.** A build-time task `checkNoCoordinatesInLogs` (sibling of the existing
  `checkHardcodedStrings`, `app/build.gradle.kts:329`) fails the build when a `Log.*` or
  `DiagnosticsLog.log` line interpolates a coordinate-shaped value — and, after `TODO.md` §87, also
  when it interpolates a whole position-carrying value (a request/location/fix object, an `Intent`'s
  data, or received share text) — so the rule survives the next log line someone adds.
- **Docs.** `guidelines/Regulatory.md` §3/§9 (what is retained, for how long, and what is *not*
  logged) and §10 (cadence row), plus the `AGENTS.md` logging section (the rule for new sites).
- **Not in scope:** user-facing coordinate displays and the locale-stable formatter (owned by
  `:core/CoordinateFormat` and `fix-comma-decimal-coordinate-entry`), favourites/search-history
  files, `ViewportStorage` (the user's own saved viewport, not diagnostics), and the car
  `HOST`/`SESSION`/`TEMPLATE` diagnostics tags, whose content does not depend on coordinates.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `auto-diagnostics`: "Log storage is bounded" — the bound becomes **size *and* age** (entries
  older than 7 days are pruned); new requirement "Diagnostics carry no coordinates" (the stream,
  the file and the export are free of position data and carry identity instead, including where the
  value arrives through an interpolated object or an `Intent`'s data); new requirement
  "Export and viewers disclose what the log contains". The existing requirements (crash capture,
  session lifecycle, viewer/export, non-blocking logging, bounded buffer) are unchanged.

## Impact

`:core`
- `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt` — retention (7 days) + export preface seam
- `core/src/test/java/com/naviveylin/core/DiagnosticsLogTest.kt` — pruning cases + existing suite

`:app`
- `app/src/main/java/com/naviveylin/DeepLinkActivity.kt` — `13` (deep-link action + URI scheme, not the data)
- `app/src/main/java/com/naviveylin/MainActivity.kt` — `136` (request shape, not the request object)
- `app/src/main/java/com/naviveylin/share/SharedLocationParser.kt` — the label is the share-subject hint only (no synthesized coordinate pair); `SharedLocationParserTest` updated with it
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — sites at `1059` (GPS fix),
  `1470` (`resolveAdminRegion`), `2019` (favorite), `2102` (search result), `2311` (POI),
  `2420` + `2425` (long-press: logcat **and** the file-backed `LONGPRESS` entry), `2512` (shared
  location label origin), `2524` (shared-location query shape, not its text), `3287` (address book),
  `3481` (route fit centre)
- `buildSrc/src/main/kotlin/com/naviveylin/build/diagnostics/CoordinateLogScanner.kt` + its test — the
  interpolation rules of design D6
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — `272` (request render), `451` (prepare viewport)
- `app/src/main/java/com/naviveylin/location/LocationService.kt:671` (fallback fix)
- `app/src/main/java/com/naviveylin/navigation/AANavigationController.kt:465` (reroute from → to)
- `app/src/main/java/com/naviveylin/ui/about/AboutDialog.kt` — export disclaimer + retention caption
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml` — new strings
- `app/build.gradle.kts` — `checkNoCoordinatesInLogs` gate wired into `preBuild`

`:auto`
- `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` — `579` (destination shape, not the object); new pure seam `deepLinkLogMessage`
- `auto/src/main/java/com/naviveylin/auto/SessionLog.kt` — `29`, `38` (session-start intent action + scheme, not its URI)
- `auto/src/main/java/com/naviveylin/auto/MapScreen.kt` (`268`, `652`, `680`, `694`),
  `AutoInitialViewport.kt` (`37`, `62`, `92`), `DetailsScreen.kt` (`263`, `268`),
  `SearchScreen.kt` (`212`), `MapPanHandler.kt` (`71`), `DiagnosticsScreen.kt` (caption)

Guidelines: `guidelines/Regulatory.md` §3/§9/§10; `AGENTS.md` (Logging — Kotlin);
`guidelines/Build.md` §9-adjacent (the new build gate). `guidelines/UI.md` only if the caption
needs a UI rule (checked in design).

Specs: `openspec/specs/auto-diagnostics/spec.md` (one modified requirement, two added).

Change class: **additive / behavioural** — no storage-format change (the log file keeps its line
format), no public API change, no manifest or permission change. Both surfaces (phone + car) are
affected in the same way; no platform deviation. No native/JNI change → no submodule patch and no
gitlink bump. Rollback: revert the touched files and the spec delta; logging returns to carrying
coordinates with a size-only bound.
