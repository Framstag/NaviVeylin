# Design — fix-diagnostics-stale-coordinate-purge

## Context

Motivation is in `proposal.md`. Constraints read from the tree 2026-09-28:

- `core/src/main/java/com/naviveylin/core/DiagnosticsLog.kt` (579 lines) owns the file. Its retention
  pass is `pruneExpiredLocked(file)` (`:477-514`), driven by `pruneIfDue()` (`:545-551`) which runs
  **before the worker's first wait** (`:375-378`) and then once per day. The pass rewrites the active
  file and the rotated one through a temp file + rename (`rewriteLocked` `:519-534`), only when
  something was dropped, and reports one summary line (`:505-513`). Its only predicate is
  `parseTimestamp` (`:556-566`) — the pass is age-only, which is exactly `TODO.md` §88.
- The reader path is already safe to rely on: `readEntries():233` waits for the worker
  (`awaitDrained():315`), and the worker runs the pass before its first wait, so the viewer/export path
  sees a pruned file whenever the worker exists (`ensureWorkerLocked():457`, started by the first
  logged entry — the app logs at startup via the `WARMUP`/stylesheet-sync entries).
- The rule for **new** writes lives in the still-unarchived change
  `fix-diagnostics-coordinate-redaction` and in `buildSrc/src/main/kotlin/com/naviveylin/build/
  diagnostics/CoordinateLogScanner.kt`, a **build-time** scan over source text wired into `preBuild`.
  It cannot run on a device, and it analyses Kotlin call sites, not log lines — so the file-level
  predicate here is a different input with different fixtures, not a duplicate gate.
- The evidence that found §88 is the recipe's own grep (`TODO.md` §88):
  `grep -E '[0-9]{1,3}\.[0-9]{4,}' files/diagnostics/app.log` → the pre-change line
  `[2026-09-25 21:34:34.085] LONGPRESS lat=51.513298135108705 lon=7.474341597216892 mag=16.0`, while
  everything the current build wrote is clean. Note that the observed corpus contains **two** shapes:
  a field-named pair (`lat=… lon=…`) and the car render's unnamed pair
  (`render center=51.60987926464756,7.621644390462239 mag=17.0`).
- `guidelines/Regulatory.md` §9 states the coordinate-free rule for log and diagnostics lines and §3
  records that the file is personal data at rest; the exported/shared text leads with
  `diagnostics_disclosure`, which already claims the file carries no coordinates.

## Goals / Non-Goals

**Goals**

- A position written by an earlier build does not survive for the rest of the retention window: the
  file the user exports matches the documented and disclosed claim within one pass of installing.
- The purge adds no new mechanism: same pass, same worker, same single rewrite, same reporting shape,
  no new file format and no persisted state.
- The predicate is pure and fixture-pinned, with its false-positive profile written down (the identity
  lines the project writes today must all survive).

**Non-Goals**

- No change to `buildSrc`/`CoordinateLogScanner` (source-level, build-time) and no change to what is
  logged at runtime.
- No one-shot "migrated" flag, no settings/persistence addition, no partial redaction of a line in
  place (a line is kept or dropped whole).
- No wording change to `diagnostics_disclosure` — the change makes the existing claim true rather than
  restating it.
- No attempt to purge anything outside the diagnostics files (logcat, tombstones).

## Decisions

### D1 — Where the purge lives

| | Approach | Consequence / risk |
|---|---|---|
| A | One-shot migration on the first start after an update, guarded by a persisted marker | Cheapest steady state, but adds persistent state that a restored backup or a re-created file can invalidate (the marker says "done" while an old file is back), and a second rewrite path with its own tests. Rejected: solves a one-time problem with permanent state |
| B | **Recommended** — the coordinate predicate joins `pruneExpiredLocked`: age and coordinates become two independent drop reasons in the pass that already reads every line, covers the active and rotated file, rewrites once, reports once and runs on the worker | Steady-state cost is one predicate call per line in a pass that already walks them (the observed install's pass processed 1617 entries). Risk: the pass is the only trigger, so its schedule bounds how fast a stale line goes |
| C | No purge; state the cutoff in the exported text ("entries before `<date>` predate coordinate-free logging") | Fixes how the recipe reads, not the property: the file still carries the position and the documented claim stays false. Rejected — kept only as the fallback if a purge fixture turns out to be unsafe |

### D2 — How the spec expresses the change

| | Approach | Consequence / risk |
|---|---|---|
| A | `MODIFIED` the requirement "Diagnostics carry no coordinates" | Semantically the right home, but that requirement exists only in the **unarchived** delta of `fix-diagnostics-coordinate-redaction`. A MODIFIED delta against a requirement the main spec does not yet have fails or is dropped at archive time, and whichever change archives second would overwrite the other's full requirement text |
| B | `MODIFIED` "Log storage is bounded" | That requirement is *being modified* by the same in-flight change (age retention), so the same archive-order overwrite applies — now to the very requirement the other change rewrites |
| C | **Recommended** — `ADDED` a self-contained requirement, "Coordinate-carrying entries do not survive the retention pass" | No dependency on the other change's archive order; after both archives the main spec carries both halves — one requirement for what may be written, one for what a pass removes. Cost: a small conceptual overlap between two requirements, which the texts disambiguate (new writes vs file history) |

### D3 — What counts as a coordinate-carrying line

| | Approach | Consequence / risk |
|---|---|---|
| A | The recipe's shape alone: a decimal with ≥4 fraction digits anywhere in the line (`[0-9]{1,3}\.[0-9]{4,}`) | Matches the verification grep exactly, but the app's identity lines can carry such numbers (a magnification printed with many digits, an accuracy, a scale) — it would delete legitimate diagnostics |
| B | **Recommended** — token **or** pair: drop a line when it names a coordinate field (`lat`, `lon`, `latitude`, `longitude`, case-insensitive, delimited) **and** carries a ≥4-fraction-digit number, *or* when it carries **two** ≥4-fraction-digit numbers **written as one comma-separated pair** (an unnamed lat/lon pair, the car render shape) | Covers both observed shapes; the token half has no false positives on the current corpus, the pair half is the deliberately coarse one — a legitimate line with two high-precision decimals *separated by a comma* would be dropped, and the pass's count report makes that visible. Fixtures pin both halves |
| C | Reuse `CoordinateLogScanner`'s identifier enumeration in `:core` | Rejected: `buildSrc` is a build-only classpath and `:core` does not (and should not) depend on it; the enumeration lists Kotlin *source* names (`frameLat`, `gpsLon`), not log fields |

### D4 — How the removal is reported

| | Approach | Consequence / risk |
|---|---|---|
| A | One entry per removed line, quoting the timestamp | An audit trail, but it grows the log by one entry per dropped line, and quoting the line re-introduces the position it just removed. Rejected |
| B | **Recommended** — one count per pass, folded into the existing retention summary line (`retention: dropped N entries older than 168h (oldest kept: …)`, extended with the coordinate count), emitted only when something was dropped and naming counts only | Keeps the single-rewrite/single-report property, stays greppable (`coordinate-carrying`), never repeats a position, and the existing test that pins "the drop is reported once" covers the shape |
| C | A separate `coordinate purge: dropped N` line next to the retention line | More obvious marker for the on-device recipe, but two entries for one pass and another line for the tests to pin. Deferred: if the on-device recipe wants a distinct marker later, that is a wording change with no spec impact |

## Risks / Trade-offs

- **[A legitimate line is dropped by the pair half of the predicate]** → the pair half requires the
  comma (a comma-separated pair of high-precision numbers), so the case that actually loses a line is
  narrow; the report counts every coordinate drop per pass, so an unexpected drop is visible in the log,
  and the fixtures pin the keep-list (magnification, DPI, accuracy, bearing, screen pixel, map
  database/file name, object label/id) including the two-raw-`Double`s-without-a-comma shape that the
  narrower rule keeps.
- **[A position survives the purge because it carries no token and fewer than 4 fraction digits]** →
  residual, documented in `guidelines/Regulatory.md` §9 as part of the task list: the age prune still
  bounds it, and new writes are the build gate's concern. The verification grep is *stricter* than the
  purge, so a 0-hit grep implies the purge caught everything the recipe can see.
- **[A read happens before the pass has ever run in the process]** → the worker runs the pass before
  its first wait (`:375-378`) and readers wait for the worker (`:234`); the app logs during startup, so
  a user cannot reach the viewer first. Residual: a process that logged nothing at all would read the
  file unpruned — impossible today, and the next pass covers it.
- **[Purge cost on a large log]** → the same pass, the same single rewrite, one predicate per line on
  a daemon worker thread; the observed install's pass already dropped 1617 entries without a reported
  cost issue.
- **[A dropped line was the evidence for an unrelated bug]** → accepted trade-off: the rule is
  documented and disclosed, the identity fields (magnification, pixels, map name, labels) are what the
  recipes read, and the count report shows that a drop happened.

## Migration Plan

- No data migration and no flag: the first retention pass after installing this build removes the stale
  coordinate entries; identity lines are untouched and the age window is unchanged.
- Rollback: revert the predicate call (and the report suffix) in `pruneExpiredLocked`. Nothing outside
  the diagnostics files persists, and no other behaviour depends on the purge.

## Open Questions

- Whether the purge deserves its own report line (D4 alternative C) once the on-device recipe is
  written — deferred, and it changes neither the spec nor the task breakdown.
- Whether a *future* field name for a position belongs in the purge's token list or only in the build
  gate. The purge deliberately covers the shapes observed in the log corpus; a new source name is the
  gate's job, so this stays a note for whoever adds one.
