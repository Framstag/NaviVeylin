# Design — Fix contact address resolution

## Context

`AddressBookResolver` (`:app`, `com.naviveylin.data`) turns one `ContactPostalAddress` into a ranked `List<LocationEntry>`, used by the phone Contacts search mode and, through the shared `AddressBookSearchProvider`, by the Android Auto address book screen.

Current behaviour, as verified in the tree:

- `resolveAddress` runs the structured form search (`OSMScoutClient.searchLocationByForm`) and **returns immediately** on a non-empty result set (`AddressBookResolver.kt:66-69`, and again for the postal-less retry at `:73-78`).
- Otherwise it walks `buildQueries(...)` and **returns on the first query that yields a non-empty result set** (`:81-88`).
- `ranked(...)` then drops every entry whose label lacks one of the address's street tokens (`AddressRanker.hasStreetEvidence`) and returns `emptyList()` when none survive (`:104-112`).
- Native search now runs with `partialMatch = true` in both entry points (`OSMScoutClient.cpp:3380` form, `:3577` string, commit `a4c309421`). With partial matching, `SearchForLocationByForm` adds an admin-region entry when the region matched but the street/house did not (`LocationService.cpp:2150-2157`), `SearchForPostalAreaForRegion` adds a postal-area entry under the same condition (`:1665-1673`), and `SearchForLocationByString` does the same for region candidates (`:1946-1971`). Such a fallback entry never carries street evidence.

Consequence: every candidate "succeeds" with entries the gate must reject, so the resolver returns empty and the UI shows the red "address not found" banner, while the phone search box resolves the same address because it merges raw results and renders them as a list without a gate (`MapCanvasViewModel.mergeSearchResults`, `StructuredAddressSearch`).

Two further defects from the same report: `ContactPostalAddress.queryText` and `AddressBookResolver` ignore `ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS` entirely, so a contact with empty structured components produces no query at all; and `ContactsRepository.queryPostalAddresses` does not deduplicate, so an aggregated contact with the same address in two accounts yields two identical addresses — which forces the multi-address step, duplicates the list row text, and feeds duplicate keys to the picker's `LazyColumn` (`AddressBookSheet.kt:191`, `key = { it.hashCode() }`).

Relevant guidelines: `guidelines/Design.md` §4 (threading), §5 (native boundary — "Native method contracts are API; fix behavioral issues in Kotlin"), §9 ("search never crashes the process"; external data access is read-on-demand, never persisted); `guidelines/UI.md` §1 (cross-variant parity) and §6a (phone search surface).

## Goals / Non-Goals

**Goals:**

- Contact address resolution succeeds whenever the phone search box can find the same address, and never reports not-found while a street-evidenced candidate exists.
- Contacts with only a formatted address, or with partially filled structured components, resolve like fully componentised contacts.
- A contact holding the same address in two synchronized address books behaves like a contact with one address (no forced selection step, no duplicated row text, no picker crash).
- Fix stays inside the app module: no submodule patch, no JNI surface change, no native rebuild semantics change.

**Non-Goals:**

- Changing `location-search` / search-box behaviour or the native `partialMatch` contract.
- Changing the ranking weights, the form-search parameter mapping, or map index/data issues.
- Persisting contacts or caching resolutions (Design.md §9: read-on-demand).
- Fuzzy street-name matching (abbreviation expansion such as "Str." → "Straße") — a separate concern if it turns out to be needed; the search-box parity scenario is the acceptance bar here.

## Decisions

### D1 — Fix in Kotlin, leave native `partialMatch` alone

- **Chosen**: Kotlin-only change in `AddressBookResolver` / `AddressParser` / `ContactsRepository`. `partialMatch = true` is the correct contract for the search box (spec `location-search`: postal code inside the query must not zero out the result set) and it stays as committed in `a4c309421`.
- **Alternative A — per-call native flag**: add a `partialMatch` parameter to `DoSearchLocations` / `DoSearchLocationByForm` and let the resolver request exact matching. Rejected: widens the JNI surface, needs a submodule patch plus gitlink bump plus a 3-ABI rebuild, and changes an API for behaviour that Kotlin can express locally. `guidelines/Design.md` §5 explicitly wants behavioural fixes in Kotlin.
- **Alternative B — revert `a4c309421`**: restores the old chain semantics but regresses the `location-search` requirements (postal code inside the query drops the whole result set). Rejected.
- **Risk**: a map index that genuinely has no postal areas for a region still produces region-only form results. Mitigated, not eliminated: the chain now continues to the string queries, which is exactly the path the working search box uses.

### D2 — Acceptance criterion: street evidence per candidate

- **Chosen**: a candidate is accepted only when it yields at least one entry with street-token evidence; otherwise it is a miss and the chain advances. Not-found is reported only after every candidate has been tried. Ranking and the existing street-evidence gate stay as they are — they already implement the spec's "wrong-location free-text result not selected".
- **Alternative A — score threshold**: accept a candidate when the top ranked entry scores above a numeric threshold. Rejected: needs a magic constant tuned per map, and `openspec/config.yaml` (archive guidance) forbids magic numbers.
- **Alternative B — `objectType` whitelist**: accept only `address`/`place`/`street` object types instead of token evidence. Rejected: couples Kotlin to the native type vocabulary and does not filter free-text POI hits whose type is a place but whose label lacks the street (the bus-stop case in the spec).
- **Risk**: an address whose street name differs from the index (abbreviation, extra suffix) now walks the whole chain and still reports not-found. That is a correct outcome under the spec (free-text noise must not be selected) and identical to the search box, which would also show no matching street.

### D3 — One component-merge helper in `AddressParser`

- **Chosen**: extend `AddressParser` (`:app`, `com.naviveylin.data`) with a helper that produces the effective `(street, houseNumber, postalCode, city)` from the structured fields, parsing `FORMATTED_ADDRESS` when a component is missing (street or city) and keeping the existing `normalizeStreetField` rules (embedded postal code is never the house number). `AddressBookResolver` consumes only the merged tuple, so both the form search and the string chain see the same components.
- **Alternative A — fill the gaps in `ContactsRepository`**: parse `formatted` while reading `StructuredPostal` and store merged components in `ContactPostalAddress`. Rejected: it makes the reader an interpreter, loses the distinction between stored and derived components that `displayText` relies on, and would silently change what the list shows.
- **Alternative B — use `formatted` only when every component is blank**: Rejected: leaves the "street missing, city present" case broken, which is one of the shapes in the report (component-filled Google contact plus formatted-only second account).
- **Risk**: `FormattedAddress` layouts vary by locale and provider. Mitigated by parse-only-as-fallback (structured components always win) and by keeping `displayText` unchanged.

### D4 — Candidate chain: add the queries the search box actually sends

- **Chosen**: keep the ordered chain (form search first, then progressively looser string queries) but extend it with the full formatted address query (`street house PLZ city`) and, when the parsed formatted text supplies a city, the corresponding component combinations. First candidate with street evidence wins; the remaining candidates stay untried.
- **Alternative A — single pass, merge, then rank**: run every candidate, merge by object file offset, rank once. Rejected: multiplies native searches per resolution (up to ~8) on the main-device hot path with no additional correctness, since the gate already rejects the partial-match noise.
- **Alternative B — no chain, only the full address query**: matches the search box literally but breaks the loosening cases the current chain covers (house number absent from the index, unknown postal code).
- **Risk**: chain length grows to roughly 7-8 candidates; each is a bounded native call (`FORM_LIMIT = 20`, `RESULT_LIMIT = 50`) executed off the main thread inside one `withContext`, and the common case exits after the first or second candidate.

### D5 — Resolver keeps its own chain instead of routing through `StructuredAddressSearch`

- **Chosen**: `AddressBookResolver` remains the shared provider (`AddressBookSearchProvider`, phone + Auto) and builds its own candidates through the shared `AddressParser` helper. Parity with the phone is enforced by a test that pins the resolver's candidate list to the query the dialog would build for the same address.
- **Alternative A — reuse `StructuredAddressSearch.resolve(query)` per candidate**, mirroring `MapCanvasViewModel.mergeSearchResults`. Attractive (one implementation), but `StructuredAddressSearch.resolve` returns early when the parsed query has no city or no street, has no notion of `formatted`, and is specified against `location-search`; extending it would widen this change into that spec.
- **Alternative B — extract a new shared "address candidate → results" component** used by dialog and resolver. Best long-term shape, largest blast radius (dialog path, `auto-search`, ranking); deferred.
- **Risk**: the two paths can drift apart again. Mitigation: the parity test in the tasks and the explicit scenario in the spec delta.

### D6 — Deduplicate in the repository; make the picker key duplicate-safe

- **Chosen**: collapse identical addresses in `ContactsRepository` before the `ContactAddressBookEntry` is built, using a normalized comparison key (trimmed, lower-cased, components joined; the formatted text when components are absent). Because duplicate rows already exist in the Data table, dedup at the read boundary is the single point every consumer (phone list, phone picker, Auto screens) sees. The picker's `LazyColumn` key becomes the normalized address string, which is unique after dedup.
- **Alternative A — dedup in `AddressBookViewModel`**: Rejected: Auto screens consume the provider directly (`AddressBookContactsProvider`), duplicates would survive there, and `AddressBookViewModel.resolve` state would carry dead entries.
- **Alternative B — hide duplicates in the composable only** (`distinctBy` plus index keys). Rejected: does not fix the forced multi-address step, which is decided from `addresses.size` in the view model, and violates "one source of truth per data signal" (Design.md §4).
- **Risk**: two genuinely different addresses whose components normalize equal (case-only difference) collapse into one. Accepted: identical after normalization is exactly the duplicate case the report describes.

### D7 — Threading and lifecycle (unchanged shape)

- No new components, no new dispatchers. `AddressBookViewModel.loadContacts` and `resolve` keep running on `viewModelScope` with `withContext(defaultDispatcher)` (`Dispatchers.Default`, swappable in tests via `@VisibleForTesting internal var defaultDispatcher`), so contact reads and the whole candidate chain (native calls included) stay off the main thread (Design.md §4, §9).
- Cancellation: the chain lives inside the single `withContext` block of one `resolve` call; leaving the search sheet cancels `viewModelScope`, which cancels the native search loop at its next suspension point. No native handles are held across calls — `LocationEntry` values are plain data.
- Contact data stays read-on-demand and is never persisted (Design.md §9); the dedup happens in memory per load.
- State machine is untouched: `isResolving` while the chain runs, `resolvedEntry` on success, `resolutionError` only when the chain is exhausted (spec: banner transient, cleared by query edit / reload / next attempt).

## Risks / Trade-offs

| Risk | Assessment | Mitigation |
|------|-----------|------------|
| Longer chain increases resolution latency on large maps | Low — bounded candidates, limits unchanged, off main thread | Early exit on first street-evidenced candidate; per-candidate debug log for on-device verification |
| Street-evidenced free-text noise now wins where the gate used to refuse everything | Medium — the gate admits any label containing a street token | Keep ranking (address > place > poi), keep the "wrong-location free-text result not selected" scenario and its test |
| `formatted` layouts differ per provider/locale | Medium — parsing is heuristic | Structured components always win; parse only fills gaps; `displayText` unchanged |
| Dedup collapses a genuine case-only-different address pair | Low | Normalized comparison documented; distinct addresses keep both entries (spec scenario) |
| Two unarchived changes modify `address-book-search` (`fix-address-lookup-accuracy`, this one) | Medium — archive order matters | Archive `fix-address-lookup-accuracy` first; its task 15 stays the on-device evidence for the regression this change fixes |
| Not fixing native means index-level data problems still surface as region-only results | Low — out of scope | Chain now reaches the string queries; residual not-found is the honest outcome |

## Verification

**Unit tests** (`:app`, host, Robolectric where `FakeOSMScoutClient` is instantiated — default sandbox config per `AGENTS.md`):

- `AddressBookResolverTest` additions: street-less form result does not abort the chain; street-less string result does not abort the chain; chain exhausts before not-found; formatted-only address resolves; partial components completed from `formatted`; candidate list contains the full formatted address query; `resolveAddress` returns a street-evidenced entry when a later candidate hits.
- `AddressParserTest` additions: component/formatted merge, formatted parse reuse of `normalizeStreetField`.
- New `ContactsRepositoryTest`: identical postal rows from two accounts collapse to one address; distinct addresses stay; blank addresses still dropped.
- `AddressBookViewModelTest` / Compose test: contact with two identical addresses resolves without the picker step; picker with two distinct addresses renders and does not throw on duplicate keys.

**Build/ABI checks** (via the `build-app` / `run-tests` skills, `guidelines/Build.md`): `./gradlew :app:test`, `./gradlew :app:assembleMobileDebug`, `./gradlew :app:assembleAutomotiveDebug`; 3-ABI matrix is CI-only and unaffected in structure (no native changes, but the verification stays in the task list since AGENTS.md requires it for release builds).

**On-device** (emulator with the NRW map, `adb logcat -s AddressBookResolver NaviVeylin`):

1. The reported failing contact resolves with the new build and shows the same object kind/location as the search box for the same address.
2. A contact with the same address in two synchronized address books shows one address, resolves without a selection step.
3. A contact whose structured components are empty resolves from the formatted address.
4. A genuinely unknown address still shows the transient banner, and editing the query or picking another contact recovers.
5. Android Auto address book screen (emulator/head unit or the `:auto` test harness) resolves the same contact to the same object — parity.
6. GPX replay is not applicable (no routing or position involvement).

**Logging**: per-candidate `Log.d` (query, result count, street-evidenced count) under the existing `AddressBookResolver` tag, so a future regression is diagnosable from logcat alone.
