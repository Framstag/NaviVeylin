# Proposal

## Why

An installed map database can be older than the stylesheet the app ships, and nothing tells the user. Measured
2026-11-13 on `emulator-5554` (Pixel_8, x86_64, mobile debug build of this tree), same APK and stylesheets,
only the data changed:

- the stale install (`andorra` plus a format-26 basemap) reported **196** `Unknown types in '<file>': N` names
  per style load over 8 style files — `include/amenity.oss` **103**, `shop.oss` 40, `natural.oss` 33 — because
  its `types.dat` carried **514** of the **638** names `stylesheets/map.ost` declares (225 absent,
  `amenity_theatre` among them). With those types unimported, the free-text search answered
  `Search results: 0 for 'Mensa'` while the POI index listed `Mensa` as `amenity_restaurant`.
- the same device with `europe/germany/nordrhein-westfalen` v27 downloaded from the map source reported **2**
  by-design names per load and `searchLocations: query='Mensa', … candidates=60`.

The user-visible half is the gap this change closes. The only signal today is a developer line in
`adb logcat -s NaviVeylin`, and the map manager's update check **cannot** produce one: it compares the
published `typeConfigVersion` with the installed one, and both are **27** — it answered *No updates available*
while the stale set was installed. So a user sees a map whose POIs are missing from search and from the
renderer, with no way to learn that a re-download is the remedy.

Provenance: issue #26 (§91), closed 2026-11-13 as a data finding after this measurement; the requirement it
leaves behind is that the *app* should say so. Related, deliberately not in scope: `fix-compound-name-matching`
(queries matching a name's middle token, e.g. `Hilpert` against `Heinz-Hilpert-Theater`, still return nothing).

## What Changes

- **A detector in `:core`**: compare the type names an installed database carries in its `types.dat` with the
  names the bundled `assets/stylesheets/map.ost` declares, and answer the missing set. The names ship with the
  app (`assets/stylesheets/map.ost`, 362 KB, packaged by the same asset copy as the stylesheets), so no new
  packaging and no network are needed.
- **A phone surface**: the map manager names an installed map whose data predates the stylesheet, and the
  download that replaces it is the offered action. No new screen; the installed-map row gains a state.
- **A refusal**: a `types.dat` whose first `uint32` is not this build's `FILE_FORMAT_VERSION` is **not**
  judged — the detector answers "unknown", never a shortfall, so a format bump cannot produce a false report.
  This is the shape `BasemapDataFormat.admit` already uses for the basemap.
- **Measurement**: the detector's answer for both installs of 2026-11-13 (stale = 225 missing, fresh = 0) is a
  recorded number, not a hope, and the row's text is fixed by a case rather than by eyeballing.

Not in scope: the stylesheet content (the submodule `stylesheets/` is untouched), the native condensed report
(unchanged — the log line stays as the developer-facing evidence), the search matcher, and the car surface
(there is no map manager on the car; see the platform constraint in Impact).

## Capabilities

### New Capabilities

_None._ The behaviour extends the capability that already owns "an installed map is in step with what the app
ships" (`map-updates`); a new capability would duplicate it.

### Modified Capabilities

- `map-updates`: adds one requirement — **an installed database that predates the shipped stylesheet is
  reported**. Its existing requirements (the daily repository check, what an update shows, when a replacement
  is installed) are unchanged: this is a second, *content*-shaped staleness beside the repository's
  version-shaped one, and the two must not be conflated — the detector's answer is not an update, because no
  newer `typeConfigVersion` exists.

## Impact

**Scope: phone only.** `MapManagerScreen` is a `:app` surface and the car has no map manager, so no label or
hierarchy parity question arises. The detector itself lives in `:core` and is surface-agnostic, so a later car
surface could read the same answer without a second rule.

Affected code:

- `core/src/main/java/com/naviveylin/core/mapsource/` — the detector: a `types.dat` name reader beside
  `DatabaseMetadata`/`BasemapDataFormat`, plus the pure comparison against the declared names.
- `app/src/main/java/com/naviveylin/ui/mapmanager/MapManagerViewModel.kt` and `MapManagerScreen.kt` — the
  installed-map row's state, computed off the main thread, and the string resources for it.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml` — the notice's text.
- Tests: `core/src/test/java/com/naviveylin/core/mapsource/` (the detector and the refusal),
  `app/src/test/java/com/naviveylin/ui/mapmanager/` (the row's state), with a golden `types.dat` fixture cut
  from a real installation.

**Additive**, and nothing is persisted: the detector recomputes from files on disk at every read, so the
rollback is a revert of this change — no migration, no stored flag, and no data the app cannot rebuild.

Guidelines the change touches: `guidelines/MapStyling.md` §16b (the on-device stylesheet copy the detector
reads its declared names from), `guidelines/UI.why.md` (the parity table's map-manager entry, where the
phone-only notice is recorded — no `guidelines/UIPhone.md` section owns the map manager surface), and
`guidelines/BuildNative.recipes.md` §10e (the recipe that reads the log line this change retires as the *only*
signal).

surface: 3 scenarios in 1 delta file, 1 detector in `:core`, 1 view-model seam, 1 map-manager row state,
2 string tables — `grep -c '#### Scenario:' openspec/changes/report-a-stale-map-install/specs/map-updates/spec.md`
