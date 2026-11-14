# Proposal

## Why

A POI search is narrowed by category and radius only, so asking for "Lebensmittelgeschäft" answers with
every supermarket, discounter, kiosk and bakery in the chosen radius — the name, operator and brand the
native side already carries per entry (`PoiEntry.label`, `.operator`, `.brand`) narrow nothing. A driver
looking for "REWE" has to read the whole list, and on the car the row does not even show the operator, so a
match on it would be invisible.

The second half of the problem is where the list is cut. The native walk already loads every object in the
radius, then truncates to the display limit (phone: the nearest 100; car: twice the host's row capacity)
before Kotlin sees anything. Filtering that truncated list would silently miss a "REWE" that is not among
the nearest hundred of the category — exactly at the wide radii the feature is for (up to 100 km). The
candidate set therefore has to widen when a filter is in effect, which is a decision this change makes
explicit rather than leaving to whichever layer happens to filter.

## What Changes

- **A name filter narrows a POI result list**, on the phone and on the car. A term matches when it is a
  substring of the entry's name, operator or brand, compared with the search's own folding
  (`core/search/SearchFolding.kt`: case, diacritics, `ß`→`ss`, punctuation, whitespace) — the same folding
  the ranker uses, so a filter can never disagree with the search that produced the candidates.
  An empty filter is today's list.
- **The candidate set widens when a filter is in effect.** The native walk is asked for a candidate cap
  (2000, provisionally) instead of the display limit, the filter narrows that set, and the display limits
  stay as they are — so a filtered list becomes *the nearest N matches in the radius*, not *the filter
  applied to the nearest N of anything*.
- **Phone: the filter narrows without a new search.** Category or radius changes still require the
  explicit Search button (unchanged, `search-dialog` "POIs mode search flow"), but a filter keystroke
  never re-runs a search. The first non-empty filter widens the fetch exactly once per
  (category, radius, centre); the remaining keystrokes filter the cached candidates locally. Clearing the
  filter brings the unfiltered list back from the same cache.
- **Phone: two empty states.** "No POIs of this category in range" and "no match for *term*" are different
  answers and read differently.
- **Car: a "Filter by name" row above the results.** It opens a search-template screen prefilled with the
  current filter; submitting it applies the term to the results screen and pops back to it (one results
  screen, one filter screen, constant back-stack depth — the pattern `OverspeedDeltaPickerScreen` and
  `VehicleAnchorPickerScreen` already use). The results header carries the active filter.
- **Car: the result row shows the operator or brand.** Both variants compose the row title through one
  shared composer (`poiDisplayLabel`, today private in `SearchDialog.kt`), mirroring the move
  `search-result-row-content` made for place rows — otherwise a row that survived the filter cannot show
  why it did.
- **The cap ships as a declared value (2000), and its census is a follow-up.** The `counted=<n>`/`cap=<n>`
  extension of the search log line lives inside the submodule, which the tooling may not commit to, so it is
  not part of this change's fix (design D8): a person patches it, bumps the gitlink, and the number is
  confirmed or corrected in a small follow-up.
- **Scope**: both variants — phone (`:app`, unified search dialog, POIs mode) and Android Auto/AAOS
  (`:auto`). Additive and presentational: no persistence, no settings, no schema, no new dependency, no
  stylesheet change. Not a `fix-*` item — there is no defective behaviour to prove, so the
  `agent-issue-loop` root-cause gate does not apply.

## Capabilities

### New Capabilities

None. The filter belongs to the surfaces that already own POI search and its result rows; a new capability
would be a near-duplicate of `poi-search` and `auto-search`. Consequence: no `FEATURES.md` classification
row is added, because no new capability id appears (`guidelines/FeatureList.md` §3).

### Modified Capabilities

None, deliberately. Every requirement this change adds is new, so no shipped requirement is replaced and no
shipped scenario name has to be repeated verbatim:

- `poi-search` gains two: the filter's behaviour (matched attributes, folding, narrow-without-a-search, the
  two empty states) and the candidate-set rule (the walk is asked for the cap when a filter is in effect,
  and the displayed list is the nearest display-max matches). The shipped "POI results list" is untouched —
  its label rule is what the car row now follows.
- `search-dialog` gains one: the filter field as a control of POIs mode. Left out entirely, that
  capability's "POIs mode search flow" would enumerate the mode's controls and omit the new one, which is
  why it is added here rather than deferred.
- `auto-search` gains two: the car filter entry (row, screen, re-search, header, guarded pop) and the row
  title rule. Its "Search result limit" is **not** modified: the filtered car walk asks for
  `max(2000, 2 × host capacity)`, so the shipped "at least twice the capacity" floor still holds.

## Impact

Code:

- `core/src/main/java/com/naviveylin/core/search/PoiNameFilter.kt` (new) — the folded match over
  label/operator/brand, the candidate cap constant, and the derivation *candidates + term → displayed
  list*, shared by both variants so the two surfaces cannot diverge.
- `core/src/main/java/com/naviveylin/core/search/PoiRowText.kt` (new) — `poiDisplayLabel` moved out of
  `app/.../ui/map/SearchDialog.kt:916` (precedent: `rowTitle`/detail moved into `:core` by
  `search-result-row-content`).
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasUiState.kt`,
  `MapCanvasViewModelSearch.kt`, `SearchDialog.kt` — the filter state, the widen-once rule and its guards
  (one wide fetch per category/radius/centre, no spinner over a readable list, a failed widen keeps the
  previous candidates, empty-candidates versus empty-after-filter, no request without a category), the
  filter field, and the two empty states.
- `auto/src/main/java/com/naviveylin/auto/PoiResultsScreen.kt` — the filter row, the header, the fetch
  width, and the shared row title; `auto/src/main/java/com/naviveylin/auto/PoiFilterScreen.kt` (new) — a
  `SearchTemplate` prefilled with the current term whose submit applies it and pops (guarded host call).
- **No native change in this change.** The POI walk needs no new entry point, no new parameter and no
  change to `PoiEntry` — `label`, `operator` and `brand` are already there and the filter runs in Kotlin. The
  only native work the feature wants is the `counted=`/`cap=` census, which is the submodule follow-up
  below; keeping it out is what makes this item deliverable by the loop rather than parked for a person.
- No new dependency, no manifest change. Two new string resources per locale (`filter by name`, the
  no-match message) — `i18n-l10n` requires both locales.

Guidelines and documents that must follow the code:

- `guidelines/UIPhone.md` §6a (phone search surface, POIs mode) and `guidelines/UICar.md` §6b (car search
  surface), §3c (a wait the driver can see and leave).
- `guidelines/Logging.md` §2 — the search request line's format, which this change extends.
- `guidelines/UI.md` §9/§10 (the new strings in both locales) and the category-name parity rule (§ line
  "POI category names come from resources and match across surfaces") — the filter label follows it.
- `guidelines/BuildNative.md` §10k (the recipe style the cap census copies) and `guidelines/BuildEvidence.md`
  §11 (a UI finding is measured before code moves).

Rollback: revert the commit. Nothing is persisted — the filter lives in UI state and dies with the
surface — so no stored value, no database artefact and no settings migration depends on it.

Costs to prove by measurement rather than assume:

- the widened walk's cost on the phone, from the existing `nativeMs` field: the claim is that the walk is
  unchanged and only the marshalling grows, and a device check at 5 km and at 100 km proves or falsifies
  it (`guidelines/BuildEvidence.md` §11);
- the cap's value, from the follow-up's `counted=` field over a dense city and a motorway radius: 2000 is
  kept or corrected, and `poi-search` states the number it lands on.

Follow-up, not this change: `counted=`/`cap=` on the POI search log line
(`libosmscout-client-java/src/OSMScoutClient.cpp`, a submodule patch plus a gitlink bump) and the constant
adjustment the census may force.

surface: 5 requirements and 34 scenarios added across 3 delta files, 0 requirement bodies rewritten, 1 new
shared composer plus 1 new shared filter file, 1 new car screen — counted with `grep -hc '^#### Scenario:'
openspec/changes/poi-name-filter/specs/*/spec.md` and `grep -h '^### Requirement:'
openspec/changes/poi-name-filter/specs/*/spec.md`
