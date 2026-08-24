## Context

See `proposal.md` — Why. The phone details dialog (`LocationDetailsDialog`, `app/…/ui/map/LocationDetailsSheet.kt`) and the AA details screen (`DetailsScreen`, `auto/…/auto/DetailsScreen.kt`) each implement their own address/area/title resolution from the same raw inputs (`getAddressAt` array, `ObjectDescription` entries, search label). The AA version resolves less and its pane caps (MAX_PANE_ROWS = 4, a host constraint of `PaneTemplate`) let the fixed rows starve description entries.

Both modules already depend on `:core`, which depends on `:osmscout-client-java` — so `ObjectDescription`, `LocationEntry`, and `DescriptionEntry` are all reachable from core. The shared resolver can live there without new dependencies.

## Goals / Non-Goals

**Goals:**
- Single pure resolution source for address, area, title, destination name — used by both views, so the AA view can never lag the phone view again.
- AA details pane shows the richest subset under the 4-row host cap: coordinates (1) + address (1) + area (1) fixed rows, description entries fill the rest in native order, duplicates removed.
- Phone behavior byte-for-byte unchanged (verified by existing phone tests).

**Non-Goals:**
- No changes to PaneTemplate or MAX_PANE_ROWS — host caps are out of scope.
- No native/JNI changes (`getAddressAt`/`getDescription` outputs stay the contract).
- No change to phone spec (`enhanced-details-sheet`) — it stays the lead definition.

## Decisions

### 1. Shared resolver in core, phone logic extracted verbatim
`core/src/main/java/com/naviveylin/core/details/DetailsResolver.kt` — pure functions over a small input model, with the phone's exact fallback chains lifted from `LocationDetailsSheet.kt` (digit-street label detection, `COORDINATE_LABEL_REGEX` move, reverse-lookup fallbacks, IsIn/admin-hierarchy/postal area chain, address suffix composition).

```kotlin
data class DetailsInput(
    val label: String?,                // search/long-press label
    val adminRegionHierarchy: String?, // LocationEntry.adminRegionHierarchy
    val postalArea: String?,           // LocationEntry.postalArea
    val description: ObjectDescription?,
    val resolvedAddress: Array<String>?, // [street, houseNumber, adminRegion, postalArea]
)

fun resolveTitle(input: DetailsInput, nameHint: String? = null): String
fun resolveAddress(input: DetailsInput): String?          // "Hauptstraße 12, 44339 Dortmund"
fun resolveArea(input: DetailsInput): String?             // hierarchy → region → IsIn → postal
fun resolveDestinationName(input: DetailsInput, nameHint: String? = null): String?
```

Rationale: copy-once, shared forever. The phone composable's `remember` block becomes a thin `LocationEntry + ObjectDescription → DetailsInput` mapping; AA's `buildDetailsRows`/`resolveTitle` become thin adapters over the same functions.

Alternatives considered:
- *Duplicate phone logic into AA file* — rejected: this is exactly the drift the change exists to kill.
- *Shared module-level only, keep phone inline* — rejected: phone stays the lead and must stay authoritative; two copies of truth still drift.

### 2. AA details view = map preview + full attribute list (option 2 — list replaces pane)
`onGetTemplate` returns `MapWithContentTemplate` (map preview with destination/favorite/GPS markers, unchanged renderer) whose content template is a `ListTemplate` — the same pattern the map menu already uses (`MapTemplateFactory.buildTemplate`). The list contains "Navigate here" and "Show" as the first two clickable rows, marked with unicode glyphs (▶ / ◎) so they read as actions distinct from attribute rows (spec: "Details actions visually marked"), then Coordinates → Address → Area → every description entry in native order (dedup of `Location/Address` + `Location/Location` against the merged Address row, blank rows skipped). No row cap: `ListTemplate` scrolls and the host pages when the list exceeds one page, so all attributes (opening hours, phone, …) are reachable.

**Action constraint (crash fix)**: `ListTemplate.Builder.addAction` validates against `ACTIONS_CONSTRAINTS_FAB` — icon-only, `maxCustomTitles = 0` (verified in car-app 1.7.0 bytecode: the FAB block never calls `setMaxCustomTitles`). Adding titled actions ("Navigate here", "Show") throws `IllegalArgumentException: Action list exceeded max number of 0 actions with custom titles` on `onGetTemplate` — the exact crash seen on the AAOS emulator. The actions are therefore rendered as clickable list rows (`Row.setTitle().setOnClickListener()`), the pattern the map menu already uses. `buildDetailsPane`/`MAX_PANE_ROWS`/list actions are all removed.

Fallback: if list-over-map proves not viable on the car display (option 2 test), switch to option 1 — keep a pane overview and push a drill-down `ListTemplate` screen with the full attribute list.

### 3. Title resolution precedence stays phone-identical
Name (description `General/Name`, else `LocationEntry.name`) → full address → address-like label (digit-bearing, non-coordinate, non-name) → plain label → nameHint → generic `"Location"`. Phone's `titleLarge` header and AA's `ListTemplate` header title both render this single result.

### 4. Tests move with the logic
- `core/src/test/…/details/DetailsResolverTest.kt` — pure JVM tests (no Robolectric): every fallback chain + scenario from the delta spec (full address with postal+city, reverse street, digit label, IsIn area, no-area, title precedence, coordinate-label exclusion).
- `auto/…/DetailsScreenTest.kt` — updated to assert rows via the resolver-backed builder (address composition, dedup, entries-fill-remaining, cap behavior).
- Phone: existing tests (if any) keep passing unchanged; add a thin mapping test only if a gap exists.

## Risks / Trade-offs

- [Long attribute lists render as many host pages] → `ListTemplate` host paging is the contract; rows are order-stable, so pagination never loses attributes.
- [List actions are FAB-icon-only (custom-title actions crash on ListTemplate)] → Confirmed at runtime + verified in car-app 1.7.0 `ActionsConstraints` (FAB: maxCustomTitles 0). "Navigate here"/"Show" render as clickable list rows instead — same pattern as the map menu. Revisit via option 1 if UX suffers.
- [Phone refactor risk: shared resolver changes phone rendering] → Phone mapping is pure delegation; existing phone tests act as regression net; run `:app:test` before and after.
- [ObjectDescription shape drift (native changes)] → Resolver takes `description.entries` only; native output changes surface as phone/AA parity tests, not silent divergence.

## Migration Plan

1. Add `DetailsResolver` + core tests (pure, no build impact).
2. Rewire AA `DetailsScreen` to the list view: `buildAttributeList` (all entries), `onGetTemplate` → `MapWithContentTemplate` + `ListTemplate`, `resolveTitle`/`resolveDestinationName` via resolver; update `DetailsScreenTest`.
3. Rewire phone `LocationDetailsSheet.kt` derivation to resolver; run full `:app:test` + `:auto:test`.
4. Smoke: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` (single ABI).
5. Rollback: revert the two rewiring commits — resolver stays, nothing else depends on it yet.

## Open Questions

None — the delta spec fixes all behavior; remaining unknowns (host-specific row rendering) are covered by the cap contract.
