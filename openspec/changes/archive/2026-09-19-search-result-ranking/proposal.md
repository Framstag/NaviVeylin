# Proposal

## Why

Searching for `Waltrop` returns a list of `Waltroper Straße` hits with the exact match `Waltrop` last, on both the phone search dialog and the Android Auto search template. The app performs no ordering of its own: results reach the UI in native order, which is decided by an upstream sort key that compares the *matched admin region* before the matched name (`LocationService.cpp:413-460`), by appending free-text hits after structured ones, and by concatenating per-database results without a global sort. On top of that, the app passes a GPS-derived search scope, and every entry built inside that scope is stamped with region and postal-area quality `match` although the query never named them — so "sits in my scope" outranks "name matches exactly".

Nothing in the result list tells the user which entry is the exact match, and the search fetches exactly the 20 entries it displays, so a perfect match that native truncated is unrecoverable before any ordering could promote it.

## What Changes

- Define a **match tier** for every search result: a result is a *perfect match* when every attribute the query named matches exactly and the entry carries no extra criterion-class attribute; otherwise it is a *close match*. Admin region and postal area are *context*: they are ignored unless the query names them.
- **BREAKING (visible behavior)**: order results as perfect matches first (nearest first), then close matches by match quality and distance. This replaces the native ordering as the order the user sees.
- **BREAKING (visible behavior)**: compute the displayed distance and the ranking distance from the last known GPS fix, falling back to the map center only when no fix exists. Two existing requirements currently specify the map center.
- Fetch a **larger candidate set** from native instead of exactly the displayed count, then rank and truncate to the displayed count in the app.
- Extend the JNI result type with the **per-attribute match qualities** native already computes but currently collapses into one lossy `matchQuality` string.
- Mark perfect matches with a **composite glyph** (heart / check / heart+check) in the result row on both surfaces, so the favorite marking stays intact.
- Apply the same tiering, ordering, distance reference and marking on the phone search dialog, the route-panel start/destination picker, and the Android Auto search template.

## Capabilities

### New Capabilities

- `search-result-ranking`: how a query's named attributes are determined, what makes a result a perfect match, that admin region and postal area are context, the tier order (perfect by distance, then close matches by quality and distance), the distance reference rule (last known fix, else map center), the candidate-set versus displayed-limit contract, and how a result with unknown quality degrades.

### Modified Capabilities

- `location-search`: ordering requirements ("Structured matches above free-text for address queries" is superseded by the tier order), the fetch limit in the suggestions-while-type scenario (20 fetched vs 20 displayed), the result-item display (perfect-match marker), and `Result distance display` (reference changes from the current map center to the last known fix with map-center fallback).
- `search-free-text`: the merge and truncation rule — free-text hits no longer form an appended tail beyond the structured results; they compete in one ranked candidate set and are ordered by the same tier rule.
- `favorite-search`: how favorite hits interact with the tiers (favorite hits are currently unconditionally listed above native results, which conflicts with a perfect native match outranking them), and the marking rule now that a row can be both favorite and perfect match.
- `auto-search`: car-surface parity — the same ordering and marking as the phone, and a distance reference on the car, which has no map center and must take it from `AutoLocationProvider`.

Not modified: `search-dialog` (its Places-mode requirement already defers the favorite-hit ordering to `favorite-search` and does not describe the result-row contents, so the phone row contract belongs in `location-search`) and `poi-search` (see Open Decision 7 — if the POI reference moves, a `poi-search` delta is added).

## Impact

Affected modules and files (expected; the design artifact confirms them):

- **Native bridge (submodule patch, minimal and upstreamable — NOT a `:osmscout-client-java` local override)**: `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/LocationEntry.java` (new quality fields) and `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` (write them; `SerializeStructuredEntries` around line 3260 currently collapses five qualities into one string, free-text hits around line 3787 are stamped `"match"` unconditionally, and the truncation at line 3731 happens before the app sees anything). Requires a gitlink bump in the main repo. `LocationEntry` is **not** one of the six files overridden under `osmscout-client-java/src/main/java`, so the field must be added on the submodule side only — patching both sides is exactly the trap `AGENTS.md` forbids.
- **`:core`**: new pure ranker (`core/src/main/java/com/naviveylin/core/search/`) plus unit tests; `AutoSearchProvider` gains the reference point and the candidate/display limits; `AutoLocationProvider` becomes the car-side reference source.
- **`:app`**: `ui/map/MapCanvasViewModel.kt` (`searchLocations`, `mergeSearchResults` — fetch limit, last known fix as reference), `ui/map/SearchDialog.kt` (marker, distance reference at line 733, row builder at line 674), `ui/route/RoutePanelViewModel.kt` and `ui/route/RoutePanel.kt` (distance reference at line 521), `data/AddressParser.kt` / `data/StructuredAddressSearch.kt` (query attribute extraction; note `AddressParser.kt:258` consumes the lossy `matchQuality` for address scoring), `data/AddressBookResolver.kt` (scope of the change), `di/AutoServiceModule.kt` (`provideAutoSearchProvider`, line 52).
- **`:auto`**: `auto/SearchScreen.kt` and `auto/SearchScreenMapper.kt` (`buildResultRow` uses the single `Row` image slot for the favorite heart; `Row` carries at most two text lines), distance line on the car surface.
- **Guidelines**: `guidelines/UI.md` §6a (phone search surface) and §6b (Android Auto search surface) — result-row decoration, the favorite-heart marking rule and the perfect-match marking.
- **Previous specifications changed**: `location-search`, `search-free-text`, `favorite-search`, `auto-search` (see Modified Capabilities); new capability `search-result-ranking`.
- **Android components referenced**: Compose result rows (`SearchResultItem`), Material 3 badge/icon usage, Car App Library `SearchTemplate` + `Row.Builder` (single image slot, max two text lines), `androidx.car.app` glyph bitmaps as already drawn by `CarGlyphs`, and the `AutoLocationProvider` GPS flow for the car surface.

Additive or breaking: the new capability and the bridge fields are additive (a new field cannot break an older reader). Two existing visible behaviors change — result order and the distance reference — which is breaking for those requirements and for any test asserting the old order or the map-center distance. Rollback path: revert the change; the old code ignores the new bridge fields, and no persisted state, schema or dataset is touched, so a revert needs no migration. Compatibility: an app build against an older native library receives no quality data, and the ranker must degrade to the close-match tier without crashing.

Scope: a general feature, not car-only — the same ranker serves the phone search dialog, the route-panel start/destination picker and the Android Auto search template.

## Open Decisions

Decisions that need a call before implementation; each is carried into the design artifact.

1. **Favorites versus tiers.** Favorite hits carry no attributes, so under the perfect-match rule they can only ever be close matches (unless the rule is applied to their name). Existing `favorite-search` behavior lists them unconditionally first. Options: keep favorites first (and let a perfect native match sit below a non-matching favorite), or rank favorites by the same tier rule and keep the heart as the only distinguishing mark. Consequence: option two changes `favorite-search` visible behavior.
2. **Address book resolution.** `AddressBookResolver` uses the same search to *resolve* one address rather than to list results. Distance-based re-ranking there could change which address is picked. Options: exclude it (quality-only, no distance), or include it (distance may change resolution outcomes). Requires an explicit answer.
3. **Free-text hits.** They carry no component quality and are stamped `"match"` unconditionally today. Options: never perfect (simplest, exact POI names are not marked), or have the bridge tag their provenance so the app can treat an exact name as a perfect match.
4. **Bridge field shape and limits.** Five per-attribute qualities exposed verbatim versus one computed field for the label-supplying component (the address case makes `label` `location + house number`); the candidate-set limit value; and where the final truncation to the displayed 20 lands.
5. **Car surface without a fix.** `SearchScreen` has no map center, so the no-fix fallback needs a source on the car (last rendered viewport center, last saved viewport) or a documented quality-only fallback.
6. **Fate of the lossy `matchQuality`.** Keep it for `AddressParser`'s +25 address scoring, or split the new data into separate fields and leave the old one untouched.
7. **POI distance reference.** `poi-search` displays and ranks by the search center, which on the phone is the map center, while POI search itself takes that center as a required parameter and uses it as the radius origin. Applying the GPS-else-center rule to POI results would also change the *result set*, not just the order. Options: leave `poi-search` untouched (then POI rows and place rows in the same dialog can show different references), or move the POI search center to GPS-else-center and accept the result-set change. This proposal does not modify `poi-search`; if the second option is chosen, a `poi-search` delta is added.
