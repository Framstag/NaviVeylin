# Proposal: Show Operator and Brand in POI Search Results

## Why

POI search results for ATMs (and most other categories) show "(unnamed)" because the
`operator` and `brand` OSM tags are dropped at database import time — the stylesheet
types do not carry the `Operator`/`Brand` features. Users cannot tell which bank runs
an ATM, which fuel brand a station belongs to, or which supermarket chain a shop is,
even though OSM has the data.

## What Changes

- **Stylesheet (`map.ost`)**: add `Operator, Brand` features to every POI-category
  type (ATM, fuel, bank, restaurants, fast food, grocery shops, hotels, parking,
  police, hospital, doctors, public transport, tourism, and their `_building`
  variants). `amenity_charging_station` already has both and is unchanged.
- **JNI bridge (submodule patch, minimal/upstreamable)**: extend `PoiEntry` (Java)
  with `operator` and `brand` fields; populate them in `BuildPoiEntry`
  (`OSMScoutClient.cpp`) and serialize them across JNI. The existing `label`
  fallback (name → operator → brand → ref) is kept for backward compatibility.
- **App UI (`PoiSearchPanel.kt`)**: the result list entry shows the name plus
  `(brand)` or `(operator)` when available — brand preferred, operator as fallback,
  no duplication when name equals brand/operator. "(unnamed)" only when nothing
  exists.
- **Spec**: update the `poi-search` "POI results list" requirement.
- **BREAKING (data)**: features are baked at import time — existing on-device
  databases must be re-imported/re-downloaded before operator/brand appears. No
  code-level break; old databases still work, they just lack the new fields.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `poi-search`: the "POI results list" requirement changes — entries show name plus
  operator/brand instead of name-only; the unnamed fallback only applies when no
  name, operator, or brand exists.

## Impact

- **Stylesheet**: `app/src/main/cpp/libosmscout/stylesheets/map.ost` — feature lists
  of ~40 POI types (additive; no render rules change, rendering still uses `Name`).
- **JNI bridge (submodule)**: `libosmscout-client-java/java/.../PoiEntry.java` (new
  fields), `libosmscout-client-java/src/OSMScoutClient.cpp` (`PoiEntry` struct,
  `BuildPoiEntry`, JNI field serialization). Submodule patch, minimal and
  upstreamable; no local override in the app bridge.
- **App**: `app/src/main/java/com/naviveylin/ui/map/PoiSearchPanel.kt`
  (`PoiResultItem` label composition).
- **Tests**: `JavaScout/.../PoiEntryTest.java` (new fields), app
  `PoiSearchPanelComposeTest.kt` (label composition), `PoiSearchViewModelTest.kt`
  (unchanged behavior).
- **Specs**: `openspec/specs/poi-search/spec.md` (delta in this change).
- **Guidelines**: none affected (UI.md parity rules unchanged; phone-only feature).
- **Scope**: phone POI search result list only. Android Auto POI search (if any) and
  the details sheet are out of scope.
- **Rollback**: revert the stylesheet feature additions and the JNI/UI changes; old
  databases remain compatible. No migration needed.
