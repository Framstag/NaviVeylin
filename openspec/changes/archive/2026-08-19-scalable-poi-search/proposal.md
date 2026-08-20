# Scalable POI Search

## Why

The POI search sheet shows categories as `FilterChip`s in a horizontally scrollable row. With 14 categories already and more planned, the row becomes a long horizontal scroll: categories past the fold are hard to discover, there is no way to filter, and the layout does not scale with the category count.

## What Changes

- Replace the horizontal `FilterChip` row in the POI search sheet with a searchable dropdown (Material 3 `ExposedDropdownMenuBox` with a text filter field).
- The user can type into the field to filter the category list, then pick one category from the filtered results.
- The dropdown scales to any number of categories without layout changes; the sheet stays compact.
- Existing behavior is preserved: no category preselected, search disabled until a category is chosen, clicking the selected category clears the selection.
- Update the POI search panel Compose tests to interact with the dropdown instead of the chip row.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `poi-search`: The category selection requirement changes — categories are chosen via a searchable dropdown instead of a chip row, and the dropdown supports filtering by typed text.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/PoiSearchPanel.kt` — replace the chip `Row` (lines ~124–139) with an `ExposedDropdownMenuBox` + filter text field.
- `app/src/main/res/values/strings.xml` — new strings for the dropdown label, filter placeholder, and clear-selection action.
- `app/src/test/java/com/naviveylin/ui/map/PoiSearchPanelComposeTest.kt` — update chip-based interactions (`assertIsSelected`, `performClick` on category text) to dropdown interactions.
- No native/JNI changes. `PoiCategories` (category ids and type mapping) is unchanged.
