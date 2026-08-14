## 1. Stable search sheet height

- [x] 1.1 Add `Modifier.heightIn(min = 280.dp)` to the outer `Column` in `SearchPanel.kt` — prevents sheet from resizing when results/loading/empty states change
- [x] 1.2 Verify `LazyColumn` with `heightIn(max = 320.dp)` still scrolls correctly within the stable 280dp minimum

## 2. Verify

- [x] 2.1 Run `./gradlew :app:assembleDebug` and confirm no compilation errors
- [x] 2.2 Run `./gradlew test` and confirm existing tests pass
- [x] 2.3 Manual test: open search, type query, verify sheet height does not change when results appear
- [x] 2.4 Manual test: clear query, verify sheet does not shrink
- [x] 2.5 Manual test: search with many results, verify list scrolls within stable sheet
