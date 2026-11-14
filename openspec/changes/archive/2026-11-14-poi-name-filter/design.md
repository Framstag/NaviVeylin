# Design — a name filter on the POI result list, and the candidate set it needs

## D1 — The filter runs in Kotlin, not in the native walk

`osmscout::POIService` offers no name predicate: `GetPOIsInRadius` takes three `TypeInfoSet`s and returns
`NodeRef`/`WayRef`/`AreaRef` vectors (`libosmscout/include/osmscout/poi/POIService.h`), and our JNI
(`libosmscout-client-java/src/OSMScoutClient.cpp:9712`) builds `PoiEntry` from those objects.
`PoiEntry` already carries `label`, `operator` and `brand` (`PoiEntry.java`), so nothing has to be added to
the boundary.

Filtering in C++ would need a folding implementation beside `core/search/SearchFolding.kt`. That file's own
contract says a difference from the backend's transliteration may cost a *marking*, never a *result* — and a
filter mismatch costs a result. So there is exactly one folding implementation, in Kotlin, and the filter is
`foldForSearchMatch(term)` against `foldForSearchMatch(label|operator|brand)`. The matcher is a substring
test on the folded strings, which is what makes `rewe` match `REWE Markt GmbH` and `muller` match `Müller`.

## D2 — What widens: the walk's requested size

Today the native side truncates *after* `GetPOIsInRadius` has loaded every object in the radius
(`OSMScoutClient.cpp:9872` `entries.resize(limit)`), which means the expensive half of the search — the index
reads and the object loads — is already paid at the full radius. Widening the request to a candidate cap
therefore costs marshalling of the extra `PoiEntry`s and a sort, not a second walk. That is why the
candidate set is widened rather than filtering what the display limit returned.

The cap is a shared constant (`POI_CANDIDATE_CAP = 2000`) in `:core`, so the two variants cannot disagree,
and it is declared as `max(2000, 2 × host row capacity)` on the car: the shipped `auto-search` "Search result
limit" requires the car POI walk be asked for at least twice the host's capacity, and 2000 alone would break
that rule for a host reporting more than 1000 rows.

## D3 — Phone: one widened fetch per (category, radius, centre)

The gate re-runs unconditionally on every `request(query)` (`core/search/SearchGate.kt:79` has no equality
check), so a filter keystroke must never reach it. The rule is:

```
press Search                 fetch at (filter blank ? display limit : candidate cap)
first non-blank filter       candidates were narrow  -> one widened fetch, then local filtering
                             candidates were already wide -> local filtering only
further keystrokes           nothing; re-derive from the cached candidates
filter cleared               nothing; the full cached list returns
category / radius / centre   wide flag cleared; the next Search or filter widens again
```

The filter never travels inside the request: it lives in `MapCanvasUiState` and is applied when the displayed
list is derived, so a keystroke that lands while the widened fetch is in flight cannot be mis-keyed — the
arriving set is filtered by the term current at that moment.

Guards this owes, each one a case:

1. one widened fetch per (category, radius, centre) — not one per keystroke;
2. no spinner over a readable list: a widen is a widen, not a search;
3. a failed widen keeps the previous candidates and reports the failure;
4. "no POIs of this category in range" and "no match for *term*" are different states;
5. leaving the view abandons an in-flight widen (the existing gate path);
6. a filter with no category runs nothing (spec: `no-preloaded-results`).

## D4 — State: candidates and display are separate fields

`poiResults` stays what it is today — the list the sheet renders, the mini map fits and the modal bands
read — and `poiCandidates`/`poiFilter`/`poiWidened` join it. The derivation *candidates + term → displayed*
lives in `:core` (`PoiNameFilter.kt`) so the car screen and the ViewModel share one implementation, exactly
as `ResultRowText.kt` is shared for place rows.

## D5 — Car: one results screen, one filter screen, constant stack depth

`PoiResultsScreen` keeps its own `filter`, offers a "Filter by name" row above the results, and pushes a
`SearchTemplate` prefilled with the current term (`setInitialSearchText`, the call `SearchScreen.kt:172` and
`AddressBookScreen.kt:137` already make). Submitting applies the term and pops, guarded by
`guardedHostCall("pop (poi filter)")`; the precedent is `OverspeedDeltaPickerScreen.kt:146` and
`VehicleAnchorPickerScreen.kt:163`, both pickers that apply a choice and pop. The alternative — push a new
results screen per refinement — was rejected because the back stack grows by two screens per refinement and
BACK then walks search history instead of leaving the list.

## D6 — The row title is one composer

`poiDisplayLabel` is private in `SearchDialog.kt:916` and implements a rule `poi-search` already states. It
moves to `core/search/PoiRowText.kt` and the car row uses it, which is the move `search-result-row-content`
made for place rows (`rowTitle` in `ResultRowText.kt`). Without it a row that survived a filter on the
operator cannot show the operator, and the car would keep a title rule of its own.

## D7 — Strings and both locales

Two labels per locale: the filter field's label and the no-match sentence. `i18n-l10n` gates that both
`values/` and `values-de/` carry them; `UI.md`'s category-name rule ("names come from resources and match
across surfaces") applies to the labels too, so phone and car use the same resource.

## D8 — The cap census is a follow-up, not part of this item

The `counted=`/`cap=` extension to the search log line lives in `libosmscout-client-java/src/OSMScoutClient.cpp`
— inside the submodule, which `tools/loop/run.sh` may not commit to. Keeping it in this change would make the
item one the pre-screen must park (an item whose fix lands in the submodule), so this change ships the cap as
a declared 2000 and the census is delivered separately: a person patches the submodule, bumps the gitlink,
and re-reads the value. If the census says otherwise, the constant moves in a small follow-up change.

## Reversal

Remove the filter field, the filter row and the filter screen, drop `poiCandidates`/`poiFilter`/`poiWidened`,
and restore `poiDisplayLabel` to private in `SearchDialog.kt`. Nothing is persisted, so no migration is
involved and the reversal is one revert.

## Alternatives rejected

- **Filter inside the native walk** (D1): a second folding implementation, and a submodule patch per change
  instead of none.
- **No candidate cap, `Int.MAX_VALUE`** (D2): a 100 km radius in a metro would marshal tens of thousands of
  entries transiently.
- **Lazy filter on the display window only** (D2): silently misses a match past the nearest 100, at exactly
  the radius the feature exists for.
- **Push a results screen per refinement on the car** (D5): back-stack growth per keystroke-submit.

## Open decisions

none
