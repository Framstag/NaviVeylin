# Tasks

## 1. The shared filter and the shared row composer

- [x] 1.1 `core/search/PoiNameFilter.kt`: `POI_CANDIDATE_CAP` (2000), `matchesPoiFilter(entry, foldedTerm)`
      over `label`/`operator`/`brand` with `foldForSearchMatch`, and the derivation *candidates + term →
      displayed list* (`filterPoiEntries(candidates, term, displayMax)`), returning the nearest
      `displayMax` matches of the input order.
- [x] 1.2 `core/search/PoiRowText.kt`: move `poiDisplayLabel` out of
      `app/.../ui/map/SearchDialog.kt:916` unchanged; the phone row calls it from `:core`.
- [x] 1.3 Cases: `PoiNameFilterTest` (name, operator, brand, folding, empty term, no match) and
      `PoiRowTextTest.bothVariantsComposeTheSameTitle`.

## 2. The phone surface

- [x] 2.1 `MapCanvasUiState`: `poiCandidates`, `poiFilter`, `poiWidened`; `poiResults` stays the displayed
      list, derived through `filterPoiEntries`.
- [x] 2.2 `MapCanvasViewModelSearch`: the widen-once rule of design D3 — the fetch width is the candidate cap
      exactly when a filter is in effect at the moment of the request; the first non-blank filter on a narrow
      candidate set runs one widened fetch; later keystrokes derive locally.
- [ ] 2.3 Guards G1–G6 as cases: one widen per (category, radius, centre), no spinner over a readable list,
      a failed widen keeps the rows, two distinct empty states, abandonment on leaving, no filter without a
      category.
      **Five of the six hold on `main`; G2 does not.** G1
      (`PoiSearchViewModelTest.theFirstNonEmptyFilterWidensTheFetchOnce`,
      `laterFilterKeystrokesAskTheBackendForNothing`), G3 (`aFailedWidenKeepsTheRowsOnScreen`), G4
      (`PoiSearchPanelComposeTest.noPoisInRangeReadsDifferentlyFromNoMatch`), G5
      (`MapCanvasViewModelPoiSearchCancellationTest.closingThePoiViewAbandonsThePoiSearch`) and G6
      (`aFilterWithoutACategoryRunsNothing`) each have a case. **G2 — no spinner over a readable list — has
      none.** `MapCanvasViewModelSearch.onPoiSearchStateChanged` implements it (`isPoiSearching = !widen`),
      and no case asserts that a widen in flight leaves the sheet's spinner off: forcing that flag `true`
      fails no case, because `aFailedWidenKeepsTheRowsOnScreen` reads `isPoiSearching` only after the failure
      has landed. Left unchecked rather than claimed.
- [x] 2.4 `SearchDialog.kt`: the filter field beside the dropdown and the slider, the no-match sentence, and
      the string resources in `values/strings.xml` and `values-de/strings.xml` (both live in `:core`, which is
      what lets the car read the same label).

## 3. The car surface

- [x] 3.1 `PoiResultsScreen`: `filter` in the state, the "Filter by name" row above the results, the header
      carrying the active term, and the walk asked for `max(POI_CANDIDATE_CAP, 2 × row capacity)` under a
      filter (twice the capacity without one, unchanged).
- [x] 3.2 `PoiFilterScreen` (new): `SearchTemplate` prefilled with the current term, submit applies and pops
      through `guardedHostCall("pop (poi filter)")`, empty submit clears, leaving without submitting changes
      nothing.
- [x] 3.3 The car row title through the shared composer; its text line keeps object type and distance.
- [x] 3.4 Cases: `PoiFilterScreenTest` (prefill, submit+pop, empty submit, guarded pop, leave without
      submit) and the `PoiResultsScreenTest` additions (filter row, header, filtered walk width, row title).

## 4. Verification

- [x] 4.1 The two-module gate for the compiled delta (`:app`, `:auto`, `:core`,
      `:osmscout-client-java` untouched) plus the changed cases through their `--tests` filters.
      Taken as the changed cases by name rather than as the whole gate: `:core:testDebugUnitTest` (11 cases),
      `:app:testMobileDebugUnitTest` (54), `:auto:testDebugUnitTest` (16), all green —
      `evidence/cases.log`.
- [x] 4.2 `revert-check` on the widened-fetch rule: mutate it to fetch the display limit while a filter is
      in effect, the named case must fail, restore, forced green. `evidence/revert-check.log`:
      `theFirstNonEmptyFilterWidensTheFetchOnce` FAILED
      (`expected:<[100, 2000]> but was:<[100, 100]>`), restored byte for byte, PASS.
- [ ] 4.3 On device: a grocery search at 5 km in a city, a `rewe` filter, and a 100 km radius with a term
      only a far entry matches — the row appears; note `nativeMs` for the widened walk.
      **Not taken.** The archive carries this task deliberately unchecked: it owes a device walk — a grocery
      search at 5 km in a city with a `rewe` filter, a 100 km radius whose only match is a far entry (the row
      must appear), and the `nativeMs` the widened walk costs beside the narrow one.
- [x] 4.4 Docs: `guidelines/UIPhone.md` §6a (the POI mode's controls), `guidelines/UICar.md` §6b/§3c (the
      filter row and its wait state), `guidelines/Logging.md` §2 unchanged (the census is the follow-up).
      `UIPhone.md` §6a (line 114) and `UICar.md` §6b (line 313) state the rules and name this change; the
      wait-state sentence this task points at sits in §6b line 320 ("the list shows its wait row alone"), and
      §3c's wait-notice rule is unchanged. `Logging.md` §2 carries no `counted=` field — unchanged, as the
      census is the follow-up.

## Archive

- The three delta specs are archived unmerged: all five requirements they add were hand-applied to
  `openspec/specs/` on `main` at `62d3ac73` and `34f01c8d` — the `poi-search` and `search-dialog` blocks
  byte-identical to this change's, the `auto-search` block a superset (`34f01c8d` added the no-fix
  filter-row scenario) — so `openspec archive --skip-specs` records the text here and re-merges nothing.
  `evidence/archive.log` holds the CLI's refusal and the flag's exit 0.