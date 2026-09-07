# Design: Show Operator and Brand in POI Search Results

## Context

See proposal.md — Why. Current state: `PoiEntry` (Java) carries only `label`,
`objectType`, `lat`, `lon`, `distance`. The C++ `BuildPoiEntry`
(`OSMScoutClient.cpp:7090`) fills `label` with a name → operator → ref fallback,
mirroring upstream `POILookupModule.cpp`. The stylesheet `map.ost` assigns
`Operator`/`Brand` features to only one POI type (`amenity_charging_station`,
line 1134); every other POI-category type carries `Name` (+ `NameAlt`,
`OpeningHours`, …) but not `Operator`/`Brand`, so those OSM tags are dropped at
import time and `findValue<OperatorFeatureValue>()` returns null at runtime.
`TypeConfig` registers both features (TypeConfig.cpp:741-742), so the machinery
exists — the types just do not reference them.

## Goals / Non-Goals

**Goals:**
- POI result entries show name plus brand (preferred) or operator, per the
  updated `poi-search` spec.
- Minimal, upstreamable submodule patch: new data fields only, no behavior
  change for existing consumers (JavaScout keeps working).
- All POI-category types get `Operator`/`Brand` in one sweep.

**Non-Goals:**
- No change to map rendering (render rules still use `Name`; adding features
  does not alter `.oss` output).
- No change to the details sheet, Android Auto POI search, or phone search
  beyond the result list entry.
- No migration of existing databases (re-import is user-driven, see Migration).

## Decisions

### D1: Compose the display label in Kotlin, not C++

`PoiEntry` gains `operator` and `brand` fields populated by the JNI bridge; the
app's `PoiResultItem` composes the visible text from `label` + `operator` +
`brand`.

- **Alternative A (chosen)**: separate fields, Kotlin composes. The submodule
  patch stays additive and upstreamable; JavaScout and other consumers ignore
  the new fields; presentation logic lives where the UI lives.
- **Alternative B**: compose in C++ (label = "Name (Brand)"). Changes the
  meaning of `label` for every consumer, including JavaScout's POI lookup
  display; harder to upstream; mixes presentation into the bridge.
- **Alternative C**: single combined `displayName` field in addition to
  `label`. Redundant — Kotlin can compose from the three raw fields.

### D2: Brand preferred over operator

Display parenthetical = brand if present, else operator; no parenthetical when
it equals the primary text.

- **Alternative A (chosen)**: brand first. Brand is the consumer-facing name
  (Shell, McDonald's, REWE); operator is who runs it (often a subsidiary or
  public body). For fuel and fast food, brand is what users recognize.
- **Alternative B**: operator first. Wrong for fuel/fast food where operator
  is rarely the recognizable name.
- **Alternative C**: show both ("Name (Brand, Operator)"). Too long for a list
  row; the user asked for "operator/brand" (either), not both.

### D3: Keep the C++ label fallback unchanged

`label` stays name → operator → brand → ref. The app treats `label` as the
primary text and appends the parenthetical only when `brand`/`operator` differ
from it. This keeps the bridge behavior identical for existing callers and
makes the "(unnamed)" case fall out naturally (all three fields empty).

- **Alternative A (chosen)**: unchanged fallback + Kotlin dedup.
- **Alternative B**: label = name only. Breaks JavaScout's unnamed-POI display
  (it relies on the operator fallback) and changes existing behavior for no
  benefit.

### D4: Add `Operator, Brand` to every POI-category type in `map.ost`

All types listed in `PoiCategories.CATEGORY_TYPES` (and their `_building`
variants) gain `Operator, Brand` in their feature list. `amenity_charging_station`
already has both. `highway_street_lamp` (line 281) already has them but is not a
search category — untouched.

- **Alternative A (chosen)**: full sweep. One stylesheet change, one re-import
  cycle covers every category; the user asked for "all".
- **Alternative B**: ATM only. Cheaper but every later category needs another
  re-import; batching is strictly better since re-import is the expensive part.

### D5: No threading or lifecycle changes

`searchPOIs` already runs on `defaultDispatcher` inside `viewModelScope`
(`MapCanvasViewModel.performPoiSearch`). The change only adds data fields; the
existing coroutine and state flow are untouched.

## Risks / Trade-offs

- [Re-import required] → Features bake at import time; existing on-device
  databases show no operator/brand until re-downloaded. Mitigation: documented
  in proposal and tasks; the app's existing basemap download flow is the path;
  old databases remain fully functional (no code-level break).
- [Database size] → Adding two features to ~40 types adds feature bits and
  value-buffer slots per stored object. Mitigation: negligible for POI types
  (small fraction of objects); verified by comparing DB size before/after
  re-import during on-device verification.
- [Submodule patch drift] → `OSMScoutClient.cpp` is a single 7300-line
  translation unit; edits must stay minimal. Mitigation: only the `PoiEntry`
  struct, `BuildPoiEntry`, and the JNI field serialization block change; the
  patch is additive and upstreamable.
- [Null handling] → `operator`/`brand` are null for objects without the tags
  (and for all objects in old databases). Mitigation: Kotlin treats them as
  nullable; composition guards on null/empty.
- [Render regression] → Adding features must not change map output.
  Mitigation: render rules reference `Name` for these types (only
  `amenity_charging_station` uses `Operator.name` in `amenity.oss:570`);
  on-device check compares rendering before/after.

## Migration Plan

1. Apply stylesheet change; re-import a test database with the new `map.ost`.
2. Ship app with new bridge + UI. Old databases: app works, entries show
   "(unnamed)" as before (fields null) — no crash, no migration code.
3. Users re-download maps to see operator/brand. Rollback: revert stylesheet
   and code; old databases remain compatible either way.

## Verification

- Unit: `PoiEntryTest.java` (new fields default null, populated values);
  app `PoiSearchPanelComposeTest.kt` (name+brand, name+operator, brand
  preferred, no-name fallback, dedup, unnamed).
- Build: `./gradlew :app:assembleMobileDebug` (or build-app skill) compiles;
  `:auto` unaffected.
- On-device: re-import DB, search ATMs/fuel/restaurants, verify labels;
  verify old-DB install shows no crash and unchanged "(unnamed)" behavior;
  compare DB size and map rendering before/after.
