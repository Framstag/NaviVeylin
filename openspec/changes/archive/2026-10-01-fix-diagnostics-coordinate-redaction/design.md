# Design — fix-diagnostics-coordinate-redaction

## Context

See `proposal.md` — Why. Current state that shapes the approach (all verified in the tree):

- `DiagnosticsLog` (`core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt`) is the only file-backed
  sink. Every line it writes is prefixed by `timestamped(...)` with `SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)`
  (`:119`, `:429-430`), including the synchronous crash-handler line (`:333-336`) — so **every** line in the
  file is age-parseable, and the format is locale-stable by construction.
- Bound today is size only: `MAX_BYTES = 256 KiB`, `ROTATED_FILE = app.log.1`, rotation on append
  (`:411-435`). There is no age bound anywhere.
- The worker (`workerLoop`, `:349-407`) already owns every filesystem operation and drains a batch per
  flush; readers wait for the drain (`awaitDrained`, `:291-301`). A retention pass fits that thread
  without changing any caller contract.
- Only three file-backed lines carry coordinates today: `MapCanvasViewModel.kt:2425` (`LONGPRESS`),
  `auto/MapScreen.kt:652` and `auto/MapPanHandler.kt:71` (`onScroll`, both file-backed). The remaining
  ~22 sites are `android.util.Log` only (logcat), which is still in scope per the owner decision.
- The logcat leak the first pass missed (`TODO.md` §87) is the *interpolation* class: the value is
  handed over whole, so no coordinate identifier appears in the source text. Measured in the tree,
  exactly four lines do that — `DeepLinkActivity.kt:13` (`data=${original?.data}`),
  `MainActivity.kt:136` (`$request`), `auto/NavigationSession.kt:579` (`$destination`, the car
  parity of the same defect) and `MapCanvasViewModel.kt:2512` (`label=${request.label}`).
  `SharedLocationParser.kt:52-56` synthesizes that label as a coordinate pair
  (`String.format(Locale.US, "%.5f, %.5f", …)`) whenever a share carries no subject — i.e. in the
  common case — and `SharedLocationParserTest:23` pins the synthesized value; the only other
  consumers of `request.label` are the display fallback at `MapCanvasViewModel.kt:2518` and the log
  line itself.
- Both carriers are data classes whose `toString()` prints the coordinates:
  `SharedLocationRequest(lat, lon, label, query)` and `DeepLinkDestination(lat, lon, query)`.
- The i18n gate (`checkHardcodedStrings`, `app/build.gradle.kts:329-368`) is the precedent for a
  source-scanning build gate, and `:core` owns the shared user-facing strings both surfaces use
  (`core/src/main/res/values/strings.xml`, e.g. `nav_hint_neutral`).

## Goals / Non-Goals

**Goals**

- No diagnostics line — file or logcat — carries a device position.
- A real age bound on the file, enforced without blocking any caller and without changing the file
  format.
- The user (and whoever receives a shared log) is told what the file holds.
- The rule is enforced mechanically, so it survives new log sites.

**Non-Goals**

- Changing user-facing coordinate display or the locale-stable formatter (`:core/CoordinateFormat`).
- Redacting anything else (object labels, database names, session ids are diagnostics *identity* and
  stay).
- A retention setting, a "delete my log" button, or an export that drops the newest entries.
- Touching the car `HOST`/`SESSION`/`TEMPLATE` diagnostics tags' content beyond coordinate removal.

## Decisions

### D1 — Enforcement: call-site identity replacement + a build gate (chosen)

Replace the coordinate with precision-free identity at every log site, and fail the build when a new
log call interpolates a coordinate.

*Alternatives:* **(B)** a logger-level redaction filter that strips coordinate-shaped substrings inside
`DiagnosticsLog.log`/`logThrowable`. Rejected: prose payloads cannot be parsed reliably (a regex either
misses the concatenated forms at `MapCanvasViewModel.kt:1059` or eats object labels such as `Heidenoldendorf 51`),
and a silent rewrite makes the file disagree with what the caller logged — a diagnosis trap.
**(C)** rounding to 3 decimals at the call sites. Rejected by the owner decision (no coordinates at
all); it also leaves a ~110 m fix in a file that is exported.
**(D)** no gate, rely on review. Rejected: the sites spread over 3 modules and 6 packages, and the
existing log lines were added one change at a time.

### D2 — Retention mechanics: timestamp pruning inside the existing worker (chosen)

On the first flush of a process (and once when the date rolls over) the worker prunes: it reads the
active and the rotated file, keeps lines younger than `RETENTION_MS = 7 days`, rewrites a file only
when something was dropped, and records one line when it dropped anything (count + oldest surviving
timestamp).

*Alternatives:* **(B)** daily-rotated files (`app.log.2026-09-26`) deleted by name — clean semantics but
changes file naming, the reader paths, the car screen's file handling and every existing test; the
retention rule would then live in the reader, not the writer. **(C)** truncate the log at process start
— loses exactly the crash trail the file exists for. **(D)** keep pruning in `init()` on the calling
thread — violates "logging never blocks the caller" (a template build calls it on the car's main
thread).

Pruning is bounded: ≤ 2 × 256 KiB read, and a rewrite only when entries expire. The window is
overridable through an `internal` test hook, next to the existing `maxBytes`/`flushIntervalMs` seams.

### D3 — Un-ageable lines are dropped (chosen)

A line the pass cannot parse cannot be aged, so it must not defeat the bound. It is removed like an
expired one and the removal is counted in the same single report line. *Alternative:* keep them
(privacy-unsafe for no benefit, since every writer path timestamps).

### D4 — What replaces the coordinate at each site family (chosen)

| Site family | Replaced by |
|---|---|
| Fix received (`MapCanvasViewModel.kt:1059`, `LocationService.kt:671`) | accuracy, bearing, speed, fix counter + provider name |
| Admin region resolve (`MapCanvasViewModel.kt:1470`) | resolved handle + region name (identity of the answer, not the input) |
| Favourite / search / POI / address-book selection (`:2019`, `:2102`, `:2311`, `:3287`) | object label, object id where available, selection source |
| Long press (`:2420` logcat, `:2425` file) | screen pixel, magnification, resolved object id/label, map database name |
| Shared location (`:2512`, `:2524`) | request shape (coordinates vs query), the label's *origin* (share subject vs synthesized — never the text), magnification, map database name |
| Deep link received (`DeepLinkActivity.kt:13`) | intent action, URI scheme, whether a text/query extra is present — never the URI data |
| Share parsed (`MainActivity.kt:136`) | request shape flags (coordinates/query present), label origin — never the request object |
| Car deep link (`auto/NavigationSession.kt:579`) | destination shape (coordinates vs query) — never the destination object |
| Car session log (`auto/SessionLog.kt:29`, `:38`) | intent action + URI scheme, warmup/timing/thread — never the session-start URI (file-backed, so this one was the worst of the set) |
| Viewport prepare / render request (`MapRenderer.kt:272`, `:451`) | magnification, angle, viewport pixel size |
| Route fit (`MapCanvasViewModel.kt:3481`) | magnification, route distance, step count |
| Reroute (`AANavigationController.kt:465`) | destination name, step index, magnification |
| Car viewport init (`auto/AutoInitialViewport.kt:37`, `:62`, `:92`) | map file/directory name, magnification, restore source (saved viewport vs map) |
| Car pan / scale / click (`MapScreen.kt:268`, `:652`, `:680`, `:694`, `MapPanHandler.kt:71`) | deltas, magnification, screen pixel |
| Car details / search (`DetailsScreen.kt:263`, `:268`, `SearchScreen.kt:212`) | destination/entry label, magnification |

Where the resolved answer is what matters (admin region, object description, long-press object), the
identity of the *answer* is logged — that is what a reviewer compares, and it is what §68 recorded the
file-backed `LONGPRESS` entry exists for.

### D5 — The disclosure is a resource composed by the UI (chosen)

The statement lives once in `:core` (`core/src/main/res/values/strings.xml` +
`values-de/strings.xml`) — the same module and the same idiom as the shared `nav_hint_neutral`
wording, so phone and car cannot drift. The phone About dialog prepends it to the shared text and
shows it as a caption next to the entries; the car `DiagnosticsScreen` shows it as a caption.
`DiagnosticsLog` (`:core`, no `Context`) stays free of user-facing text.

*Alternatives:* **(B)** `exportText(disclaimer: String)` in `DiagnosticsLog` — keeps the composition in
`:core` but still needs the UI to supply the text, so it adds an API without removing the resource
dependency. **(C)** a hardcoded English constant in `:core` — rejected: violates `i18n-l10n` and is
invisible to `checkHardcodedStrings` (the §80 lesson: the gate only scans `:app` Compose code).

### D6 — The gate is a paren-balanced scan, implemented and tested in `buildSrc` (chosen)

A line-based regex cannot see `Log.d(TAG, "GPS loc=${"%.6f".format(fix.lat)}," + … )` (the message
spans three lines), so the scanner walks each file and, at every `Log.<level>(` / `DiagnosticsLog.log(`
call, collects the argument text up to the matching closing parenthesis, then flags it when that text
interpolates an identifier matching `\b(lat|lon|latitude|longitude|destLat|destLon)\b` or a
coordinate-shaped format string (`%.[4-7]f` on such an identifier).

**Interpolated values (`TODO.md` §87).** Those two rules cannot see a value that arrives *whole*, so
three more shapes are flagged: **(3)** an interpolation that hands a whole value to the message — a
bare `$name`/`${name}` with no property access, call or format — where `name` is either declared with
a position-carrying type in the same file (`SharedLocationRequest`, `DeepLinkDestination`, `Intent`,
`Uri`, `Location`, `GpsFix`) or is one of the carrier names this repo uses for share/deep-link input
(`request`, `req`, `intent`, `original`, `fix`, `location`, `loc`, `dest`, `destination`, `pair`);
**(4)** a **whole-URI hand-over** — an interpolation containing a `.data`/`.dataString` access that is
not followed by a property read (`${original?.data}`, `${intent?.data ?: "-"}`, `.data.toString()`),
while a scalar read off the URI stays legal (`${intent?.data?.scheme}` — identity, not a position).
`uri`/`url` are deliberately *not* carrier names: a download URL is legitimate diagnostics content,
and rule (4) covers the URI class precisely where it matters.

Blast radius measured in the tree (2026-09-27): of 460 `Log.*`/`DiagnosticsLog.log` calls in the three
scanned source roots, the new rules flag exactly **five** lines, and every one is a real leak —
`DeepLinkActivity.kt:13` (`intent data 'original?.data'`), `MainActivity.kt:136`
(`position-carrying object 'request'`), `SessionLog.kt:29` and `:38` (`intent data 'intent?.data'`, the
session-start URI written to the **file** on every session create/onNewIntent) and
`NavigationSession.kt:579` (`position-carrying object 'destination'`). The two `SessionLog` lines were
found by the rule rather than by `TODO.md` §87 — the gate paid for itself on its first run. The other
carrier-named interpolations in the tree are property accesses that stay legal
(`${location.accuracy}`, `${location.time}` in `LocationService.kt:704`/`:815`, `${intent?.action}` in
`NavigationNotificationService.kt:90`), so the check ships with a **non-empty but entirely correct**
find set — and a false positive is still possible later (a local that merely shares a carrier name),
so the documented escape stays: reword the message or rename the local.

The label case is deliberately *not* a gate rule: `label=${request.label}` is a `String?`, statically
indistinguishable from the map-object labels the rule permits (`${entry.label}`), so its invariant is
established at the source instead — decision D7.

Lives in `buildSrc` beside the license logic (which is the module's established purpose and already
has a unit-test harness), wired as `checkNoCoordinatesInLogs` into `app/build.gradle.kts` `preBuild`,
scanning `app/src/main/java`, `auto/src/main/java`, `core/src/main/java`. *Alternatives:* a
`:app` unit test (runs too late — after compilation, and only if someone runs the suite), or a
line-based regex (known false negatives).

### D7 — The share subject is not identity: the parser stops synthesizing a coordinate-pair label (chosen)

`SharedLocationParser.parse` sets `label = EXTRA_SUBJECT?.takeIf { it.isNotBlank() } ?:
String.format(Locale.US, "%.5f, %.5f", lat, lon)`, so `request.label` *is* a coordinate pair whenever
the sharing app sends no subject — which is what D4's shared-location row then sent to the log. The
label becomes the share-subject hint only (nullable); the display fallback is composed where it is
displayed, which the map screen already does (`MapCanvasViewModel.kt:2518`), and the log line carries
the label's origin (`subject` vs `none`) plus magnification and the map database name.

*Alternatives:* **(B)** keep the synthesized label and log a kind flag — smallest diff, but the
invariant then rests on that flag staying correct at every future site, which is the failure mode this
amendment exists to close. **(C)** keep the label and let the gate catch it — the gate cannot (D6).
**(D)** log the label only when a subject was present — the parser still synthesizes a pair for other
consumers, and a *subject* can itself be a pasted coordinate pair, which origin-only logging avoids.

Cost: `SharedLocationParserTest:23` pins the synthesized value and is updated to the subject-only
contract; every other consumer is unaffected (the display fallback already handles a null label).

## Risks / Trade-offs

- **[Debugging loses exact positions]** → The identity fields (pixel + magnification + resolved
  object/database) are what the screen→geo and car-pan investigations compare; if a future
  investigation genuinely needs a position, it can be reproduced on a device with a temporary local
  edit, rather than shipped in every user's log.
- **[Pruning cost at startup]** → ≤ 512 KiB read, rewrite only when entries expire, once per process,
  on the existing daemon worker; readers keep waiting for the drain, so no ordering change.
- **[Pruning races the append]** → Both run on the same thread under `fileLock`; `awaitDrained`
  already ties readers to a completed batch.
- **[The gate blocks a legitimate message]** → Flag only identifier/format interpolation, not the
  words `lat`/`lon` in prose; a false positive is fixed by rewording the message (documented in
  `guidelines/Build.md` next to the i18n gate).
- **[The new interpolation rules block a legitimate line]** → Measured against the 460 existing log
  calls, the carrier-name and `.data` rules flag only the four known leaks (no current false
  positive); the escape (reword, or rename the local) is the same one the identifier rules document.
- **[Dropping the synthesized label changes a displayed value]** → The rendered label is unchanged
  (the display site composes its own fallback at `MapCanvasViewModel.kt:2518`); the parser-side value
  was pinned only by `SharedLocationParserTest`, which D7 updates.
- **[Retention deletes evidence of an undiagnosed crash]** → 7 days is the owner-set window; the
  crash line is written synchronously and the window is a single named constant if it ever needs to
  move.

## Migration Plan

No data migration: the file format is unchanged, so an existing `app.log` keeps working and is pruned
on the first flush after the update. Rollback = revert the touched files and the spec delta; the
retention bound and the identity fields disappear, the coordinate logging returns.

## Open Questions

None that change the specs, the approach or the task breakdown. The exact wording of the disclosure is
a wording decision inside the implementation (both locales, reviewed in the task).
