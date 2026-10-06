# Design

## Context

See `proposal.md` — Why, for the motivation. The current state that shapes the approach:

- A style load runs `StyleConfig::LoadContent` (`libosmscout-map/src/osmscoutmap/StyleConfig.cpp:1786`)
  → `oss::Parser::Parse`. The parser is **generated** by coco/R from
  `libosmscout-map/parser/OSS/OSS.atg` with the frames `Parser.frame`/`Scanner.frame` via
  `libosmscout-map/parser/generate.sh`; both generated files are committed
  (`libosmscout-map/src/osmscoutmap/oss/Parser.cpp`,
  `libosmscout-map/include/osmscoutmap/oss/Parser.h`).
- The four unknown-type sites are grammar actions: `OSS.atg:289` and `:313` ("Unknown way type"),
  `:851` and `:873` ("Unknown type"). Each calls `SemWarning` → `Errors::Warning(line, col, s)`
  (`Parser.cpp:2685`), which **logs immediately** (`log.Warn() << line << "," << col << …`) and then
  appends to `errors` (`Errors::Err{Warning, line, column, text}`).
- `StyleConfig::LoadContent` (`:1802-1826`) translates that list: `Error`/`Symbol`/`Exception` →
  `StyleConfig::errors`, `Warning` → `StyleConfig::warnings` (`GetWarnings()`).
- Consumers of the recorded warnings: `Tests/src/OSTAndOSSTest.cpp:260-265` (counts them, and with
  `--warning-as-error` adds the count to its error count) and `StyleEditor/src/StyleAnalyser.cpp:103`
  (prints each one). The app's own style verdict does **not** consume warnings:
  `DBInstance::LoadStyle` fills its out-list from `GetErrors()` only
  (`libosmscout-client/src/osmscoutclient/DBInstance.cpp:55`), and `DBThread.cpp:492` computes
  `lastStyleLoadSucceeded = succeeded && styleErrors.empty()`.
- Tooling: `Coco`/`cococpp` is **not available** on this machine (`command -v Coco cococpp` → exit 1),
  and no CI step or build target regenerates the parser — `generate.sh` is a manual script.
- The app surfaces native lines through its own bridge (`app/src/main/cpp/native_log_bridge.cpp`,
  `com.naviveylin.NativeLogBridge`) under the Logcat tag `NaviVeylin`; libosmscout must stay
  Android-free outside its `Android/` directory (CI gate).
- Measured cost today: 3078 lines on the car, 9159 on the phone per startup (`TODO.md` §12).
- Constraint from the owner: no existing API may break, and the data mismatch itself is server-side
  and out of scope (`proposal.md` — What Changes, "Not in scope").

## Goals / Non-Goals

**Goals:**

- One warning line per style load for unresolved type names, with the exact distinct count and a
  bounded, deterministic sample.
- The recorded per-occurrence findings, the load outcome and the warning level are untouched.
- Grammar and generated parser stay in lockstep, so the change is upstreamable and survives a
  regeneration.
- The full per-name detail stays reachable: programmatically through the recorded findings, and in the
  log when debug logging is enabled.

**Non-Goals:**

- Repairing the map/basemap data or re-importing the published sets (server side; `TODO.md` §89/§90/§91).
- Any app, `:auto`, `:core` or `:osmscout-client-java` change, and any change to the app's file-backed
  diagnostics contract (`auto-diagnostics`).
- A general de-duplication of every repeated parser warning, and any change to hard errors or to the
  `Symbol`/`Error` paths.
- Changing the warning level or hiding the finding when the map data is stale.

## Decisions

### D1 — The condensation lives in the parser's unknown-type actions

Chosen: the per-load state (a set of reported names, per-kind counters) and the aggregate emitter are
added to the OSS parser, and the four unknown-type actions consult it. This is the only place that
knows it is looking at an unresolved *type name*, owns per-load state, and can suppress the line
**before** it is formatted.

Alternatives:

- **Filtering `Log` proxy passed into the load** (rejected): `oss::Errors` stores the logger by value
  (`Parser.h`: member `Log log;`, constructed from `const Log&`), so a derived logger would be sliced
  and lose the virtual dispatch — and the proxy would have to recognise messages by text.
- **Post-pass over the collected findings in `StyleConfig::LoadContent`** (rejected): too late; the
  lines are emitted during parsing.
- **De-duplicate inside `Errors::Warning` by message text** (rejected): it cannot distinguish an
  unresolved type from an unrelated warning that legitimately repeats at different positions, and it
  puts a semantic rule into the generated collector instead of the grammar that has the semantics.

### D2 — Only the log is condensed; the recorded findings stay per occurrence

Chosen: the unknown-type sites stop logging, and every occurrence is still handed to the collector
with its own line/column. So `GetWarnings()`, `StyleAnalyser` and `OSTAndOSSTest` see exactly what
they see today, the name is counted once per parse for the aggregate (D3), and the per-name detail
comes from the recorded findings and the gated debug line instead of from per-name log lines.

Correction to this section's first draft: it had the *first* occurrence of a name still emitting a
per-name line next to the aggregate. That contradicts the spec — *A style load reports unresolved type
names in one condensed line* says exactly one line, and its "Fifty distinct names … the load still
emits one line" scenario fails with 50 per-name lines — and it defeats the point of the change. The
spec wins; the artifacts were corrected here and in `tasks.md` task 2.1 before implementation.

Mechanism: the collector gets a **record-without-log** entry point, called by one parser helper that
records the finding and notes the name. Adding a method is API-additive; no existing signature changes
(the owner's constraint). The aggregate line itself is **log-only and is not recorded as a finding**,
precisely so `GetWarnings()`'s size does not change.

Alternative (rejected): de-duplicate the recorded findings to one per name. A shorter list, but it
silently changes `OSTAndOSSTest`'s `--warning-as-error` count and `StyleAnalyser`'s printout — a
behavior change outside the log for no gain.

### D3 — One `Warn` line per load, plus a gated full list

Chosen: at the end of a parse, if anything was unresolved, emit one line through the same collector
path's logger: the style file name, the exact number of distinct unresolved names (node/area and way
types together), and a bounded sorted sample (`Unknown types in '<file>': 42 (a, b, c, …, 34 more)`).
Level `Warn` — the finding must not disappear. The complete distinct list is emitted at `Debug` as
well, gated by the existing `Log::Debug(bool)`/`IsDebug()` switch (`Logger.h:373`/`:380`), which is the
switch `AGENTS.md` already documents for native debug output.

- Sample bound: a named constant, small (single digits) — the count is always exact, only the listing
  is bounded.
- Order: names sorted, so the line is deterministic across runs and loadable in a test assertion.

**Report scope — "one line per load" means per parsed style file.** `MODULE "include/route"` is not
inlined: the action calls `config.Load(moduleFileName, colorPostprocessor, true, errors->log)`
(`OSS.atg:182-185`), i.e. a nested stylesheet load with `submodule=true` that builds its **own**
parser. The state is per parser instance (D5), so each parsed file — the top-level stylesheet and every
include it pulls in — reports its own single line, and a file whose names all resolve reports nothing.
The spec's scenarios hold per file, and the module attribution is useful for the data-side fix.

Alternative (considered and rejected): one line for the whole top-level load. It would need a
cross-parser accumulator on `StyleConfig` (each nested parser writes into it, the top-level load emits
once) — new public state on the style configuration, cleared per load, to save a handful of lines and
lose the module attribution. Not worth it; the reduction is 3078 → tens either way.

Alternative (rejected): one line per distinct name instead of one line per file. More raw
diagnosability, but the requirement is condensation to a single report per file, and the detail is one
debug flag away.

### D4 — Grammar and generated files change together, hand-mirrored

Chosen: `OSS.atg` and the committed `Parser.cpp`/`Parser.h` change in the same commit, mechanically
mirrored: the new member and helper land in the header block the grammar's member section produces,
and the four action sites change identically in both files. The frames are fixed text templates, so
regeneration is a substitution; the commit message and the upstream PR state that the generated files
were mirrored by hand because the toolchain is unavailable on this machine, and that a regeneration is
expected to produce no diff.

**Regeneration-route record (2026-10-02, task 1.1).** The toolchain is absent on this machine:
`command -v Coco cococpp` exits 1 with no output, and `libosmscout-map/parser/generate.sh` prints
`No coco implementation found!` and exits 1. `OSS.atg`, `OSS/Parser.frame` and the generated
`Parser.h`/`Parser.cpp` are therefore mirrored by hand in one commit.

Alternatives:

- **Build/install `cococpp` and regenerate** (rejected for now): the correct route in principle, but it
  adds a toolchain/network dependency to a four-site edit for an identical resulting diff. It stays the
  preferred route whenever the tool is at hand (upstream CI or a maintainer regeneration).
- **Patch only the generated files** (rejected): the grammar — the source of truth — would keep
  describing the old behavior; the next regeneration silently reverts the fix.

### D5 — Per-load state on the parser instance; no new component, no new lifecycle

The dedupe set and counters are members of the parser instance; one parser exists per load (created in
`StyleConfig::LoadContent`), so nothing is shared between loads, between databases or between threads.
No component, scope or lifecycle is introduced, so no dispatcher question arises for new code: the load
stays where it runs today — the client's DB-thread worker (`DBThread::LoadStyleInternal`,
`DBThread.cpp:455-498`) or the render-side style reload — and never on the Android main thread for the
app's own style switches. Emitted lines travel `osmscout::log` → `native_log_bridge.cpp` →
`__android_log_print`, which is thread-safe; no new synchronization. No Android API enters libosmscout.

### D6 — Verification

- **Unit (submodule `Tests/`, catch2)** — extend `Tests/src/StyleLoadResilienceTest.cpp` (its
  `TESTS_TOP_DIR`/`WriteUnparsableStyleSheet()` pattern) or add a new file, which needs its own
  `osmscout_test_project(...)` line in `Tests/CMakeLists.txt` (registration there is explicit). A
  capturing `Logger` subclass is injected through `StyleConfig::Load(file, nullptr, false, log)`
  (`StyleConfig.h:918-921`, `Log& log = osmscout::log`) against a `TypeConfig` that lacks the
  referenced names. Cases, one per spec scenario: many references → one line; 200 distinct names →
  count exact, sample bounded, "more" marker; two loads → same order; all-resolvable → no line; both
  kinds → one line with the total; three occurrences of one name → three recorded findings; debug on →
  full list, debug off → not; a valid stylesheet with unresolved names still loads and is adopted; a
  stylesheet with a hard error still fails.
- **Native host build** — build and run the submodule tests (`meson test -C hostbuild "<name>"
  --print-errorlogs` is the route that works on this machine; `ctest -R` for a CMake build dir, per
  `TODO.md` §40.48).
- **App build** — after the gitlink bump: `:app:assembleMobileDebug` and `:app:assembleAutomotiveDebug`
  for all three ABIs, proving the pinned submodule still compiles the JNI library. The JNI surface is
  untouched.
- **On-device count (the acceptance evidence)** — `adb logcat -s NaviVeylin | grep -c "Unknown type"`
  over one startup on the stale install: expect one line per style load instead of 3078 (car) / 9159
  (phone), the style still loading and rendering (`Diag/MAP` render lines continue, no style-error
  entry), and `adb logcat -s NaviVeylin | grep "Unknown types in"` showing the count and sample. A
  device is required; `adb devices` is empty today, so this half is a pending documented step, not a
  claimed pass.
- **Gate check** — the CI gate "Check libosmscout Android-free outside Android/" stays green, and the
  diff touches no other generated file.

**Measured (2026-10-02, task 5.1 — phone AVD `emulator-5554`, debug build with the stale map data
installed).** One startup: `adb logcat -s NaviVeylin | grep -c "Unknown type"` = **104** (was 9159),
all of them condensed reports, no per-occurrence line left. The reports name 8 files
(`standard.oss` + `basemap.oss`, `place.oss`, `religious.oss`, `shop.oss`, `tourism.oss`, `natural.oss`,
`amenity.oss`) with exact counts (3, 2, 9, 40, 5, 33, 103, 1) and bounded samples
(`…, 95 more`), identical across repeats; 0 style errors; the stylesheet is adopted
(`I NaviVeylin: Created new style with …/standard.oss`), `MainActivity` stays resumed, no fatal
exception, and the map screen's controls are present in the accessibility dump.

Two readings follow from this and are recorded rather than fixed here: the count is **13 loads × 8
files**, i.e. the phone re-parses the stylesheet set ~13 times per startup (`Created new style` 12×) —
filed as `TODO.md` §116, since the report merely made the repetition visible; and the phone emits
**no** `Diag/MAP` render lines (only the car renderer does), so "renders continue" is evidenced here by
the adopted style, the clean log and the live map screen rather than by render counters.

## Risks / Trade-offs

- [Hand-mirrored generated files drift from the grammar] → edit both in one commit, keep the diff
  mechanical, state it in the commit and PR, and treat the new unit case (not a compile error) as the
  evidence; the `.atg` edit is the durable part that a regeneration reproduces.
- [A newly appearing unknown name hides inside a large set] → the count changes with the set, the
  sample is sorted and deterministic, the per-name detail is one debug flag away, and the recorded
  findings are unchanged.
- [Someone expects `StyleAnalyser`/`OSTAndOSSTest` to print fewer entries] → they do not: the recorded
  findings are deliberately unchanged (D2); only the library log is condensed. State this in the
  change's notes so it is not "fixed" later by accident.
- [Frequent style switches mean several aggregate lines per session] → that is by design (each load
  reports its own result, per the spec's independence requirement) and still orders of magnitude below
  today's count.
- [A future regeneration rewrites the generated files] → harmless: the behavior comes from the `.atg`,
  which carries the change.

## Migration Plan

1. Submodule `naviveylin-local`: one commit with `OSS.atg` + regenerated-by-hand `Parser.h`/`Parser.cpp`
   + the test case; submodule tests green on the host.
2. Parent repo: bump the gitlink in the same change; nothing else to migrate — no app code, Gradle,
   manifest, resource or persisted state is touched.
3. Offer the same commit as a focused upstream PR (the pattern used for the matcher work), including the
   note that the generated files were mirrored by hand. **Done (2026-10-02):** the commit was cherry-picked
   onto a fresh branch off `origin/master` (`condense-style-load-warnings`, commit `5f61763e7`, which built
   and passed the new cases plus `StyleLoadResilienceTest` standalone) and opened as
   https://github.com/Framstag/libosmscout/pull/1870. The app keeps pinning `77fdc4299` on
   `naviveylin-local`, so the PR and the pin are independent.
4. On-device: run the log-count recipe above when a phone/AAOS session is available.
5. Rollback: revert the gitlink bump (or the submodule commit). Behavior returns to one line per
   occurrence; no data migration and no user-visible state to clean.
