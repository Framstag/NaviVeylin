# Design — fix-car-poi-results-cap

## Root cause

One number decided two things and belonged to neither of them: `PoiResultsScreen`'s `MAX_RESULTS = 20`
(`auto/src/main/java/com/naviveylin/auto/PoiResultsScreen.kt:239`, base-commit line numbers throughout this
section) was passed to the native POI walk as its candidate count (`:159`) and also bounded the rows rendered
into the single `ItemList` (`:198`), so the screen
never asked the host how many rows it displays and never asked the walk for more than it showed. Below a
20-row host the tail is dropped with no signal (spec `car-list-row-limit`; `guidelines/UICar.md` §3 — "the host
will ignore any items over that limit"), and the candidate rule — at least twice the displayed maximum
(`openspec/specs/auto-search/spec.md:62`) — was under-asked by the same constant.

## The decision this change implements

Issue #113's recorded decision (2026-11-06, `ITEM_DECISION` in the item's work package) answered the open pair
with **Option 2**, quoted there as: "the row cap becomes the host's reported capacity, the fetch budget twice
that, the remainder behind the page row on already-fetched rows as §177/§178 do — one capability delta on
`car-list-row-limit`, and the spec's 'display at most 20 results' line is amended". This change implements that
option and re-derives nothing: the three candidate counts the escalation offered are settled, and the two
questions the escalation named (the fetch budget's provenance, and whether the POI list inherits the
"at least twice" rule) are answered by the same option — host-derived, and yes.

## The fix

1. **The row cap is the host's answer.** `rowCapacity = CarListCapacityReader.read(carContext)` on the
   screen's own scope, off the host thread, sliced per page through the shipped
   `FavoritesScreenMapper.page(results, capacity = rowCapacity, page = page)` (`PoiResultsScreen.kt:114`,
   `:238`). The initial value is `LIBRARY_DEFAULT` so the first template the host asks for is already bounded.
2. **The fetch budget is twice that.** `fetchBudget = 2 * rowCapacity` (`:121`), which is why the capacity is
   read **before** the request is issued: the search starts after the host answers (`:145`), not in the
   constructor as it used to. The page and the page its row opens are therefore always inside the fetched set.
3. **The remainder is already-fetched rows.** The page row pushes another `PoiResultsScreen` carrying the
   opener's `results` as `prefetched` and `page + 1` (`:288`); that screen slices those rows and issues no
   request. Back navigation returns to the opening page because the host owns the screen stack (the mechanism
   `SearchHistoryScreen` and `AddressBookScreen` use), and the page costs no second native walk — which is what
   "already-fetched" in the decision means, and what the third case asserts
   (`client.poiSearchGenerations.size == 1`).
4. **The constant leaves.** `MAX_RESULTS` is deleted rather than narrowed; the screen carries no result count
   of its own. `SEARCH_RADIUS_M` is unrelated and stays.

## Blast radius

- One production file (`PoiResultsScreen.kt`), one new test file, two lines in the shared car test double.
- `PoiSearchScreen.kt:65` is the only production construction site; it passes four arguments, so the two new
  parameters take their defaults (first page, search afresh) and its behaviour is unchanged.
- `CarListCapacityReader`, `FavoritesScreenMapper`, the `armScreenPush` seam and the `More (n)` string are
  used as they are shipped; nothing in `:core`, `:app`, resources, the manifest or native code moves.
- The `:auto` suite's other cases reach the fake client's POI path only through
  `PoiResultsScreenSearchLifecycleTest`, whose single-row fixture is unaffected by the double's new cap; that
  class was re-run green (`evidence/TEST-PoiResultsScreenSearchLifecycleTest-green-focused.xml`).

## Alternatives the evidence eliminated

- **Row cap only, fetch left at 20** (the escalation's reading A). Eliminated by the decision, and by the
  screen's own arithmetic: with the cap at a host's 8 and 20 candidates fetched, the page row would count rows
  the walk never returned on a host below 10 — the entry's own "under-filled on a 100-row host" clause stays
  alive (issue #113's Impact, the item this change closes). It is also the reading the candidate rule
  (`openspec/specs/auto-search/spec.md:62`) still fails.
- **A fixed 40 candidate budget with the 20-row cap kept** (the escalation's option 1). Eliminated by the
  decision: 20 rows is a display number, and a car list's display number is the host's to report
  (`car-list-row-limit`).
- **Host capacity below 20 only, 20 otherwise, fixed 40 fetched** (option 3). Eliminated by the same clause:
  two rules for one list, and the host's answer would still not bound the rows on a large host.
- **Re-running the walk on the pushed page** (the shape the other paged screens use, each of which reloads
  local data). Eliminated by the decision's "already-fetched rows" and by cost: a native POI walk per page on
  a driving surface, for rows the first walk already returned. `prefetched` is the one addition this change
  makes to the shipped seam, and it is what makes the paging free of a second search.
- **Capping `PoiSearchScreen`'s category list in the same change.** Eliminated as scope: it is a second screen
  with a second list (`PoiSearchScreen.kt:54-75`), so it is a second delta; it is named in the report as a
  `NOTE:` rather than absorbed.

## Guideline and spec ownership

- **Owning guideline section:** `guidelines/UICar.md` §3 "Car-host template constraints (platform knowledge)",
  the `ListTemplate` bullet — it states the rule screen-generically ("A car list screen MUST ask the host for
  the capacity and present **at most** that many rows, ending with its own page row") and names the shipped
  implementation this change reuses. It carries the edit this diff owes: none — the rule is already true of
  every car list, and this change makes one more screen obey it. The change adds no platform knowledge, so a
  guideline paragraph would restate §3 rather than extend it. (The archived precedents cite `guidelines/UI.md`
  §3; the car-platform rules live in `UICar.md` §3 today — `UI.md`'s current sections carry no list-capacity or
  page-row rule: `./tools/look.sh 'capacity|page row' --path guidelines/UI.md` → 0 matches.)
- **The candidate rule's other statements.** `openspec/specs/search-result-ranking/spec.md:123` and
  `openspec/specs/location-search/spec.md:29` state the same "at least twice the displayed maximum" rule for
  the ranked query search on both surfaces; this change leaves them alone because the POI list is not that
  list — its order is the native walk's, with no tier ranking — and the surface they bound still carries its
  own maximum. The `auto-search` amendment names the POI list as the case where both numbers come from the
  host, which is the one place the two rules meet.

## Rollback

Revert `PoiResultsScreen.kt` to its HEAD content, drop `PoiResultsScreenTest.kt` and the fake client's two
added lines: the screen returns to one 20-row list with a 20-candidate walk and nothing else in the tree
depends on the new parameters (both default). The delta specs and this change's directory ride with the
change.
