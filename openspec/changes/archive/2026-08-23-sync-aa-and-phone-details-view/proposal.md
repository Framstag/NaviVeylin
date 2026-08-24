## Why

The Android Auto details screen (`DetailsScreen`) shows less information about the same object than the phone details dialog (`LocationDetailsDialog`): address rows lack postal code and city, area resolution misses the admin-hierarchy/IsIn fallbacks, title resolution misses label-based fallbacks, and the pane's fixed row cap lets coordinates/address/area starve the object-description rows entirely. Users get a degraded destination view on the car display. The phone UI is the lead — it shows more information — so the AA view SHALL be aligned to its data and resolution rules.

## What Changes

- **Address composition**: AA `Address` row now uses the phone's full composition — street + house number + postal code + city (`"Hauptstraße 12, 44339 Dortmund"`), with the same fallback chain (description street → reverse-lookup street → digit-bearing address label → house number alone; postal from reverse lookup or entry; city from reverse region, deepest hierarchy segment, or IsIn).
- **Area resolution**: AA "Area" row adopts the phone's fallback chain — admin region hierarchy → reverse-lookup region → description `IsIn` (admin level) → postal area — instead of admin-region/postal only.
- **Title resolution**: AA title adopts phone logic — object name → full address (incl. postal+city) → address-like search label → generic fallback; digit-bearing address labels treated as streets, coordinate labels excluded.
- **All attributes listed (option 2 — list replaces pane)**: the AA details view stops using `PaneTemplate` entirely. The details screen shows every attribute returned by the Description API (opening hours, phone, website, …) as labeled rows in a `ListTemplate` content list over the map preview (`MapWithContentTemplate`) — no row cap; the host pages when the list exceeds one page. Phone already shows all attributes (scrollable sections); AA now does too, so both views display the complete `ObjectDescription`.
- **Row priority**: the list orders Coordinates → Address → Area → all description entries in native order, skipping the merged street/address duplicates. Address/area/title resolution is shared with the phone dialog via the resolver (phone = lead).
- **Shared resolution source (implementation)**: address/title/area resolution extracted into a shared, pure, tested helper used by both AA and phone so the two views cannot drift again. Phone behavior unchanged.

## Capabilities

### New Capabilities
- none

### Modified Capabilities
- `auto-destination-details`: requirement changes — full address composition with postal code + city, area fallback chain incl. description `IsIn`, title fallback to street-like search labels, and ALL Description API attributes displayed as a scrollable/paged list (opening hours, phone, …), with street/address dedup matching the phone spec.

## Impact

- `auto/src/main/java/com/naviveylin/auto/DetailsScreen.kt` — `buildDetailsRows` → all-attribute list builder, `resolveTitle`/`resolveDestinationName`, `onGetTemplate` → `MapWithContentTemplate` + `ListTemplate` content (pane removed).
- `auto/src/test/java/com/naviveylin/auto/DetailsScreenTest.kt` — updated/extended row tests (address composition, area fallbacks, title scenarios, cap priority).
- Phone: `app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt` — behavior unchanged; may switch to shared helper.
- Shared helper: new file (likely `core/src/main/java/com/naviveylin/core/details/…`) with unit tests, used by both views.
- No native/JNI changes, no manifest changes.
