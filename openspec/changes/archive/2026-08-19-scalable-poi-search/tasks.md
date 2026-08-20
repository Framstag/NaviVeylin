## 1. UI Implementation (spec: poi-search — "Category and radius selection")

- [x] 1.1 Replace the horizontal `FilterChip` row in `PoiSearchPanel.kt` (lines ~124–139) with a Material 3 `ExposedDropdownMenuBox` containing an editable text field that filters the category list as the user types
- [x] 1.2 Show every supported category (`PoiCategories.getCategoryTypes().keys`) when the dropdown opens unfiltered
- [x] 1.3 Selecting a category calls `onCategorySelected(id)`; selecting the already-selected category calls `onCategorySelected(null)` to clear the selection
- [x] 1.4 Show a "no matching categories" state when the filter text matches nothing, and keep the search trigger disabled while no category is selected
- [x] 1.5 Keep the dropdown field label/placeholder consistent with the sheet's existing typography and spacing

## 2. Strings

- [x] 2.1 Add strings to `app/src/main/res/values/strings.xml` for the category dropdown label, filter placeholder, and clear-selection content description

## 3. Tests (spec: poi-search)

- [x] 3.1 Update `PoiSearchPanelComposeTest.kt` chip-based interactions (`assertIsSelected`/`performClick` on category text) to dropdown interactions: open dropdown, type filter, select category
- [x] 3.2 Add test: typing a filter narrows the dropdown to matching categories
- [x] 3.3 Add test: filter text matching no category shows no selectable categories
- [x] 3.4 Add test: selecting the already-selected category clears the selection and disables the search trigger
- [x] 3.5 Keep existing behavior tests passing: no category preselected, search disabled without a category, search enabled after selection

## 4. Verification

- [x] 4.1 Run `./gradlew :app:compileDebugKotlin` — build compiles without errors
- [x] 4.2 Run `./gradlew test` — existing unit and Compose tests pass
