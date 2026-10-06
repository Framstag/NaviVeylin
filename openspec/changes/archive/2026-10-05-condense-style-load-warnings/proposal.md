# Proposal

## Why

The map data and the shipped stylesheet have drifted apart, and the drift itself is a data problem
that has to be fixed where the maps are generated (server side) — it is `TODO.md` §89/§90/§91 and
this change does not close it (TODO §90 basemap rejected, `types.dat` 26 vs expected 27; §89/§91 the
loaded type config lacks types the stylesheet declares). What *is* ours, ships today and is a defect
on its own is the diagnostics cost of that drift: **one stylesheet load emits one
`W NaviVeylin: Unknown type '<name>'` line per occurrence**, because the same missing type is
referenced by many rules across the `MODULE` includes.

The chain is in `libosmscout-map`:

```
StyleConfig::LoadContent (StyleConfig.cpp:1786) -> oss::Parser::Parse
  STYLEFILTER_TYPE  Parser.cpp:1109, 1130   "Unknown type '<name>'"
  WAYGROUP          Parser.cpp:417,  439    "Unknown way type '<name>'"
    -> SemWarning -> Errors::Warning(line, col, s)   Parser.cpp:2685
         -> log.Warn() << ...            one formatted line per occurrence  <- noise
         -> errors.push_back(...)        structured list for callers        <- keep
```

Measured per startup: 3078 lines on the car, 9159 on the phone (`TODO.md` §12). Why now: the
warning is real evidence and must stay — the map is rendering without the types the style asks for —
but in this shape it drowns `adb logcat -s NaviVeylin`, makes "logcat free of warnings" unverifiable
for unrelated on-device checks (`TODO.md` §85; `compass-day-night-palette` task 7.3), and pays a
formatted log line per occurrence inside the style-load path. The aggregation belongs where the
knowledge is — the parser — so every consumer of libosmscout gets the quiet log, not just this app.

## What Changes

- `libosmscout-map` collects unknown-type findings per style load, **deduplicates them by name**, and
  reports **one** `log.Warn()` line per load: the style file, the exact number of distinct unknown
  types and a bounded sample (`Unknown types in '<file>': 42 (amenity_theatre, amenity_cinema, …,
  34 more)`).
- The same treatment for the sibling way-type site (`Unknown way type`), so the two paths cannot
  disagree about how a stale type config is reported.
- **The signal does not disappear**: it stays at `Warn` level, one line per style load, and the full
  per-name list stays available to callers programmatically.
- **No API change** (explicit constraint): `Errors`, `Parser` and `StyleConfig` signatures,
  `StyleConfig::warnings`, `errors`, `hasErrors` and everything derived from them (including
  `wasLastStyleLoadSuccessful()` and the style-switch behaviour) keep their present meaning. Only the
  number of log lines changes.
- Native **submodule patch, minimal and upstreamable**: `osmscout::log` only, no Android dependency
  (the CI gate "Check libosmscout Android-free outside Android/" stays green).
- Docs: `guidelines/Build.md` §10 and `AGENTS.md`'s native-logging section record that one aggregate
  line per load is the expected on-device output for stale map data, so a later session reads it as
  evidence instead of a defect.
- **Not in scope**: regenerating/re-importing the published map and basemap packages (server side;
  `TODO.md` §89/§90/§91 stay open), the nested `files/maps/basemap/basemap` layout question, and any
  app-side "map data out of date" state or prompt.

## Capabilities

### New Capabilities

- `style-load-diagnostics`: how the native style/type-config load reports what it could not resolve —
  one deduplicated report per load, at a level that keeps the finding visible while keeping the log
  usable, with the structured per-name findings preserved for callers and repeated loads behaving
  identically.

### Modified Capabilities

- none. No phone-, Android-Auto- or Automotive-OS-specific behaviour changes, and the app's own
  file-backed diagnostics contract (`auto-diagnostics`, including "diagnostics carry no coordinates")
  is untouched — the aggregate line carries a style file name and type names, never a position.

Guidelines touched: `Design.md` §12 (the single source of truth for the aggregation is the parser,
not each caller), `MapRendering.md` §16a area (stylesheet mechanics — a stale type config no longer
produces a per-rule warning stream), `Build.md` §10 (the on-device recipe's expected output).

## Impact

- `app/src/main/cpp/libosmscout/libosmscout-map/src/osmscoutmap/oss/Parser.cpp` — `STYLEFILTER_TYPE`
  (`:1109`, `:1130`) and `WAYGROUP` (`:417`, `:439`) collect into a dedupe set instead of calling
  `SemWarning` per occurrence; `Errors` (`:2685`) gains the aggregate emitter;
  `libosmscout-map/include/osmscoutmap/oss/Parser.h` (`:80-81`) if an overload is needed.
  Submodule commit on `naviveylin-local` (upstream candidate: the same file on `master`) plus the
  **gitlink bump** in this repo — the two must land together.
- `app/src/main/cpp/libosmscout/libosmscout-map/src/osmscoutmap/StyleConfig.cpp` (`:1786-1805`) —
  only if the aggregate is emitted at the end of the load instead of by the parser; the
  `errors`/`warnings` translation there must keep producing exactly the entries it produces today.
- Submodule `Tests/` — a parser/style-load case pinning: N distinct unknown types produce exactly one
  log line and N structured warnings, a clean stylesheet produces zero, and a second load behaves
  identically (no hidden global state).
- `guidelines/Build.md` §10, `AGENTS.md` — expected-output note for the aggregate line.
- **No** change in `:app`, `:auto`, `:core` or `:osmscout-client-java`; no JNI signature change
  (`:osmscout-client-java` override untouched, so the `TODO.md` §79 drift question is not involved);
  no manifest, Gradle or dependency change. The app benefits without an edit because
  `native_log_bridge.cpp`/`NativeLogBridge` already forward `osmscout::log` to Logcat under the
  `NaviVeylin` tag.

**Nature**: additive — nothing removed, no persisted state, no API touched, and the change is
independent of the map regeneration. **Rollback**: revert the gitlink bump (or the submodule commit);
the app returns to the per-occurrence lines and nothing else changes.

**Verification**: the native case above plus an on-device count — `adb logcat -s NaviVeylin |
grep -c "Unknown type"` across one startup, expected to drop from 3078 (car) / 9159 (phone) to one
line per style load, with the style still loading and rendering as before.
