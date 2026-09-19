# Design — search-result-ranking

## Context

See `proposal.md` — Why. Facts the approach depends on, all verified in-tree:

- The app performs no ordering: `MapCanvasViewModel.mergeSearchResults` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:1569`) hands the native order to the UI; `FavoriteSearchMerger` only prepends favorite hits (`core/src/main/java/com/naviveylin/core/search/FavoriteSearchMerger.kt`).
- Native truncates before the app sees anything: `OSMScoutClient.cpp:3731` resizes the structured results to `limit` and clears the free-text list when they already fill it. All four call sites pass 20 (`MapCanvasViewModel.kt:1556`, `AutoServiceModule.kt:59`, `RoutePanelViewModel.kt:191`, `AddressBookResolver.kt:145`).
- The results of several databases are concatenated, not globally sorted (the per-database `LocationSearchResult` is sorted internally by upstream's `Entry::operator<` at `LocationService.cpp:413`, whose first key is the matched admin region).
- Five per-attribute qualities exist natively and are collapsed into one string at `OSMScoutClient.cpp:3260`; free-text hits are stamped `"match"` at `OSMScoutClient.cpp:3787`.
- `LocationEntry.java` lives in the submodule (`libosmscout-client-java/java/com/framstag/libosmscout/client/`) and is **not** one of the six files overridden under `osmscout-client-java/src/main/java`, so a new field must be added on the submodule side only.
- The phone derives the search scope from the GPS fix (`currentSearchAdminRegionHandle`), the car and the route-panel picker search unconstrained (`NO_ADMIN_REGION`).
- Existing precedents to follow: `searchPOIs` already sorts by distance before truncating in the JNI (`OSMScoutClient.cpp:7713`); `ManeuverSymbols` (`core/src/main/java/com/naviveylin/core/ManeuverSymbols.kt`) is the single source of shared canvas artwork consumed by both the car glyphs and the notification; `AutoLocationProvider` is started/stopped by `NavigationSession` (`auto/.../NavigationSession.kt:413`, `:177`) and read by other car screens (`PoiResultsScreen.kt:56`).
- Threading and module rules: `guidelines/Design.md` §4 (no JNI on the main thread, debounce, one source of truth), §8 (parity, deviate only as much as required), §9 (search never crashes the process).

## Goals / Non-Goals

Goals: one shared, unit-testable tier computation; a candidate set large enough that ranking is not confined to a pre-truncated page; a distance reference that matches what the rows display; a marking that composes with the favorite heart on both surfaces.

Non-goals: changing the search scope rules (GPS-derived region scoping stays as specified); changing which names a query matches; changing POI search semantics (see D10); re-ranking address-book resolution (see D9); touching upstream `LocationService` sort behavior (the upstream order stays as the backend's own order and is simply not the order the user sees).

## Decisions

### D1 — Tier computation in Kotlin `:core`, not in the native bridge

Chosen: a pure `SearchResultRanker` in `core/src/main/java/com/naviveylin/core/search/`, alongside `FavoriteSearchMerger`, consumed by phone, route-panel picker and car.

Alternatives: (a) rank in the JNI next to the existing POI distance sort, like `searchPOIs` — rejected: app unit tests never execute native code (they use `FakeOSMScoutClient`), so the project's "new unit tests for new code" rule would be unsatisfiable for the core rule; (b) rank in both places — rejected: one rule with two owners drifts, and the spec would have to name an owner anyway.

Rationale: the rule is query-text-driven and needs the parsed query (`AddressParser` already splits street/PLZ/house/city), which is app-side data.

### D2 — Bridge exports per-attribute qualities plus the matched name

Chosen: extend `LocationEntry.java` (submodule) with `adminRegionMatchQuality`, `postalAreaMatchQuality`, `locationMatchQuality`, `addressMatchQuality`, `poiMatchQuality` (each `"match"` / `"candidate"` / `"none"`), a `hasHouseNumber` flag, `matchedName` — the name of the component that supplied the displayed label — and `matchedComponent`, the id of that component (`"location"`, `"poi"`, `"adminRegion"`, `"address"`, `"freeText"`, `"coordinate"`). `OSMScoutClient.cpp` writes them in `SerializeStructuredEntries` (the data is already in scope at the point where the collapsed string is built) and in the free-text loop (empty qualities, `matchedName` = the hit label).

`matchedComponent` was added during implementation, beyond the original field list: the label and `objectType` are derived from *different* component precedences (`OSMScoutClient.cpp:3232` for the label, `:3249` for the type), so the ranker cannot tell from either which per-attribute quality field belongs to `matchedName`. Without it the ranker would have to guess the component from the presence of fields it does not receive.

Alternatives: (a) one computed `nameMatchQuality` field — rejected: it bakes in an answer for the address case (`label` is `location + house number` at `OSMScoutClient.cpp:3232`) and for region-only entries, hiding the distinction the ranker needs; (b) recompute everything app-side from `label` by folding — rejected: the label is a composite for address entries, and re-deriving "exact vs partial" app-side duplicates the native matcher's token and pattern rules.

Rationale: exposes exactly the native judgement and nothing else; keeps `label` semantics untouched.

### D3 — Candidate set of 60, displayed 20, ranked then truncated app-side

Chosen: a `CANDIDATE_LIMIT = 60` passed to native by every surface; the ranker sorts the full candidate set, then the display path takes the first 20.

Alternatives: (a) 40 (the spec's 2× floor) — cheaper, but a perfect match can sit deeper in a multi-database result set; (b) keep 20 and rely on the upstream order — rejected: it is the bug.

Rationale: 3× headroom over the displayed page while keeping the extra cost linear and measurable, and each source is capped at the candidate count (D8), so the worst case the app ranks is 2×60 entries. Cost: each structured candidate does a database object read plus an admin-region hierarchy resolution (`OSMScoutClient.cpp:3286`), so the native cost per query rises to roughly 3×, plus one extra `LocationEntry` per candidate across JNI; free-text candidates add serialization only. The per-query candidate count and native time are logged by `MapCanvasViewModel.searchLocations` for the device check.

### D4 — Distance reference supplied by the caller, no JNI signature change

Chosen: the ranker takes an explicit reference point (nullable). Phone: last known fix, else `viewport.centerLat/Lon`. Car: `AutoLocationProvider.position().value`, else the current car viewport center. Route-panel picker: same rule as the phone.

Alternatives: (a) pass the reference into `searchLocations` and let native rank, mirroring `searchPOIs` — rejected: it would move the tier rule into C++ (D1) purely to obtain the reference; (b) rank from the map center everywhere — rejected by the spec (fix first, center as fallback).

Rationale: keeps the native call site and signature unchanged, and makes the fallback per surface explicit. The same reference value feeds the displayed distance, so numbers and order cannot disagree.

### D5 — Composite marking glyph, one artwork source in `:core`

Chosen: `:core` gains a small marking bitmap factory covering the four combinations (none, favorite, perfect, favorite+perfect), drawn on canvas like `ManeuverSymbols`; the car wraps it in a `CarIcon`, the phone renders the same bitmap through Compose `Image`. The phone row also sets `contentDescription` from a string resource naming the facts ("Favorite", "Exact match").

Alternatives: (a) a text badge on the phone and text on the car — rejected by the chosen decision (one shared composite glyph); (b) two independent icons per surface — rejected: the artwork would drift between variants, which `ManeuverSymbols` was created to prevent; (c) a Material `Icon` pair on the phone only — rejected for the same reason.

Rationale: one artwork source, identical meaning on both surfaces, and the car's single image slot stays free of a conflict. Known limitation, accepted and specified: the car `Row` has no accessibility text channel for the marking, so on the car the fact is glyph-only.

### D6 — `matchQuality` stays, but address scoring stops depending on it

Chosen: keep the existing collapsed `matchQuality` field (other consumers set it: `NavigationViewModel`, `RoutePanelViewModel`), and have the address ranker read the **per-attribute** qualities instead — `AddressParser.matchedExactly` accepts `locationMatchQuality`/`addressMatchQuality`/`poiMatchQuality` being `match`, and falls back to the collapsed field only when a native library reports no per-attribute quality at all (D11).

Why the consumer moved: this change makes the collapsed field search-tier-specific — it no longer reports `match` for a text-index hit whose name merely contains the query (task 1.3/1.4), while it *does* count a GPS-scope-derived admin-region match that the query never named. Address resolution scoring `+25` on it would therefore have silently changed for free-text-derived candidates and rewarded scope-derived ones. Verified during the verification pass; the side effect was recorded as `TODO.md` #35 and is resolved by this decision.

Alternatives: (a) leave the address ranker on the collapsed field — rejected: an unintended cross-feature change, and it rewards the fabricated region match; (b) remove the collapsed field — rejected: four call sites set it and the removal is unrelated to this change.

### D7 — Favorites stay above native results

Chosen: `FavoriteSearchMerger` keeps prepending favorite hits, independent of tier (as the modified `favorite-search` delta now states explicitly), and the marking composes.

Alternatives: (a) favorites compete in the tiers, keeping only the heart — a cleaner single order, but it changes existing `favorite-search` behavior beyond what this change needs; if chosen, only the `favorite-search` delta changes, not the approach.

### D8 — Free-text hits are close matches and own their candidate budget

Chosen: free-text hits never carry the perfect marking (they report no per-attribute quality), and they are not a tail that structured results can crowd out — at the native boundary **each source gets its own budget** of `limit` candidates (structured ≤ limit, free-text ≤ limit, union ≤ 2×limit), the app ranks the union and truncates to the displayed maximum.

Why the budgets: the first implementation raised the limit but left the old truncation policy in place (`if (results.size() >= limit) freeTextEntries.clear()`), so a query with a full page of structured results returned **no** text-index hits at all — the verification pass flagged it as a spec/design mismatch and this is the chosen fix. Ranking happens app-side, so the native layer cannot decide which source's candidates are better; giving both the same budget is the only way to let the ranker see both.

Alternatives: (a) keep structured-first truncation and amend the spec/design to say so — rejected: a query with many structured matches would keep hiding POIs the user asked for; (b) rank natively to pick the best across sources — rejected in D1.

Alternatives: (a) have the bridge tag provenance and let an exactly-named free-text hit be perfect — deferred: it adds a provenance field and a folding comparison for a case the structured index usually covers; (b) leave them appended last — rejected: it contradicts the tier order and the modified `location-search` requirement.

### D9 — Address-book resolution excluded from distance ranking

Chosen: `AddressBookResolver` keeps its current quality-based selection; it receives the larger candidate set only if the shared provider makes that free, and is otherwise unchanged.

Alternatives: (a) rank there too — rejected: it resolves one address rather than listing results, so a distance-driven reorder can silently change which address a contact resolves to, which is not a search-ordering concern.

### D10 — POI search untouched

Chosen: `poi-search` keeps its center (the map center on the phone, passed as the radius origin) for both its result set and its distance.

Alternatives: (a) move the POI search center to GPS-else-center — rejected for this change: the center is also the radius origin, so it changes the *result set*, not just the order, and that is a separate behavior change with its own spec delta. Consequence, accepted for now: within the same dialog, POI rows and place rows can show distances against different references. If this is later flipped, `poi-search` gains a delta and the POI call site passes the same reference as the place search.

### D11 — Degradation on a missing quality field

Chosen: `null`/empty quality values make an entry a close match with `none`-level quality; the list still renders, ordered deterministically. This covers an app built against an older native library (fields absent → null).

Alternatives: (a) fall back to the legacy `matchQuality` string when the new fields are absent — rejected: the legacy string is the lossy value that caused the misclassification, and it would mark scope-derived matches as perfect.

### D12 — Threading, lifecycle and ownership

Chosen: the ranker is pure, allocation-light, no IO, no framework types — it runs inside the existing background search coroutine (phone: the `withContext(defaultDispatcher)` block in `searchLocations`/`mergeSearchResults`; car: `SearchScreen.runSearch`'s `ioDispatcher`). No new service, no new dispatcher, no JNI call added or moved. The car reference read is `AutoLocationProvider.position().value` inside the same coroutine, with the provider's lifecycle owned by `NavigationSession` as today (`start` at `:413`, `stop` at `:177`). Marking is rendering only: Compose recomposition on the phone, `Row.Builder` inside `onGetTemplate` on the car.

Alternatives: (a) a separate ranking flow with its own scope — rejected: one source of truth per signal (`guidelines/Design.md` §4) and the search already has a debounced job with cancellation; (b) ranking on the main thread at display time — rejected: violates §4's spirit and would re-sort on every recomposition.

## Risks / Trade-offs

- **Native cost triples per query** (60 candidates, each with a DB read and a hierarchy resolution) → mitigation: the ranking stays off the main thread behind the existing 300 ms debounce; measure per-query time with the existing logcat path (`searchLocations` debug lines plus a timing line around the native call) during the on-device task and drop `CANDIDATE_LIMIT` to 40 if the debounce-plus-search budget on a regional map is exceeded.
- **App-side folding can disagree with the native transliteration table** for exotic spellings (e.g. `ẞ`, already a known limitation recorded in `TODO.md` §24) → mitigation: the ranker's folding is applied to both the query criterion and the entry name, so a disagreement can only cost a marking, never a missing result or a wrong order; the spec's transliteration scenarios pin the common cases (ß/ss, diacritics, case).
- **Region name matching is heuristic** when the query names a city that appears in the entry's region hierarchy only as an ancestor or descendant → mitigation: compare against every name in the hierarchy (which the bridge already provides via `region[]`/`adminRegionHierarchy`), and treat a hierarchy-wide exact name match as the criterion matching; scenarios in `search-result-ranking` cover the named-region, unnamed-region and postal-code cases.
- **Car marking is glyph-only** → mitigation: this is specified as a platform constraint in `search-result-ranking` and `auto-search` rather than presented as parity; a future text channel can be added when the host allows it.
- **Two specs now describe ordering** (the new capability plus the per-surface deltas) → mitigation: the new capability owns the rule; the surface specs only state that they apply it, and the deltas say so explicitly.
- **Behavioral change to the distance requirement** affects existing tests that assert map-center distances → mitigation: the affected unit tests are updated in the same change, and the no-fix fallback keeps the map-center behavior for the no-GPS case they likely exercise.

## Migration Plan

No data migration: nothing is persisted by this change, no schema or dataset is touched, no native file format changes. Deployment order: submodule change (Java field + C++ writer) → gitlink bump in the main repo → app/core/auto changes. An old app build against the new library ignores the added fields; a new app build against an old library sees them as `null` and degrades per D11, which is why the app-side tolerance is implemented before the UI that depends on it. Rollback: revert the change; the old native order and the old distance reference return, and the added fields are simply unused. Version/ABI impact: none — the added JNI fields are additive and the native library keeps its name and ABI set (arm64-v8a, armeabi-v7a, x86_64).

## Verification notes

- **Host-testable**: the tier rule, the query split, the folding, the ordering, the marking decision, the artwork contract, the phone/route-panel/AA wiring and the cross-surface order (83 new tests: `:core` 53, `:app` 20, `:auto` 7, `:osmscout-client-java` 4 class-contract tests).
- **Not host-testable**: the JNI serialization itself — app unit tests never execute native code (they use `FakeOSMScoutClient`), so the *values* the bridge writes (five qualities, `hasHouseNumber`, `matchedName`, `matchedComponent`, the coordinate entry's `none` qualities) and the candidate-budget truncation are verified by code review plus the device pass (tasks 7.3/7.4), while the Java-side contract is pinned by `LocationEntryFieldsTest`. No C++ harness exists for the JNI translation unit; building one is not justified for five field writes (recorded as a known limitation rather than a task).
- **Measured on device**: ordering and marking on phone and AAOS (tasks 7.3/7.4), and the candidate-set cost budget (task 7.5 — judgement: acceptable at 60; the timing line added by task 8.4 makes the next pass quantitative).

## Open Questions

- The exact `CANDIDATE_LIMIT` (default 60) is tunable from the on-device measurement without changing the specs, the approach or the task breakdown — the spec only requires "at least twice the displayed maximum".
- Whether an exactly-named free-text hit should later become a perfect match (D8 alternative a) is deferrable: it adds a provenance field and changes no other requirement.
