## 1. Search Result Disambiguation

- [x] 1.1 Add duplicate-label grouping logic to `SearchPanel.kt` — compute `groupBy { it.label }` on results list, identify groups with count > 1
- [x] 1.2 Update `SearchResultItem` composable to accept `isDuplicate: Boolean` and `disambiguationDetail: String?` parameters
- [x] 1.3 Build disambiguation detail string from `LocationEntry.objectTypeName`, `postalArea`, and first `region` element — join non-empty fields with " · " separator
- [x] 1.4 Render disambiguation detail line (bodySmall, onSurfaceVariant color) below the label when `isDuplicate` is true
- [x] 1.5 Keep single-result items unchanged — no extra detail line when `isDuplicate` is false

## 2. Verification

- [x] 2.1 Build project with `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — verify compilation succeeds
- [x] 2.2 Run unit tests with `./gradlew test` — verify no regressions
