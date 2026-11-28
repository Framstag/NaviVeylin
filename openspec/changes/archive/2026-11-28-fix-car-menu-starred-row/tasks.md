# Tasks — the starred entry moves from the menu to the group level

## 1. The group level carries the starred entry

- [x] 1.1 `LevelRow` gains a `Starred(count)` kind, `levelRows` prepends it to the ordered sequence it hands
      `FavoritesScreenMapper.page` while `starredOrder` is non-empty, and `rowFor` dispatches it to a new
      `starredRow`. Verify: `./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.FavoritesScreenTest"`
      — `theGroupLevelCarriesTheStarredEntryFirst`, `noStarredEntryWhileNothingIsStarred` and
      `theStarredEntryOpensTheStarredLevel` pass; the same three cases fail with `FavoritesScreen.kt` at the base
      commit (`evidence/red-on-HEAD-favorites.xml`, `tests="41" skipped="0" failures="2" errors="0"`).
- [x] 1.2 The row reuses the group rows' count wording (`plurals/favorite_count`) and pushes the starred level
      through `armScreenPush`, so a failing push stays a logged no-op. Verify: `theStarredEntryOpensTheStarredLevel`
      asserts the pushed screen's header and rows.

## 2. The menu loses the row

- [x] 2.1 `MapTemplateFactory.buildMenuContent` drops the starred `Row` and the `onStarredFavorites` parameter;
      `MapScreen` drops the callback body. Verify: `./gradlew :auto:testDebugUnitTest --tests
      "com.naviveylin.auto.MapTemplateFactoryTest"` — `theMenuCarriesSixRowsWithNoStarredEntry` and
      `theDegradedNoticeIsADisabledFirstRow` pass, and both fail once the row is re-added
      (`evidence/red-on-HEAD-menu.xml`, `tests="10" skipped="0" failures="2" errors="0"`).

## 3. The specs and the feature list follow

- [x] 3.1 `auto-map-layout` — "Content box acts as the app menu" names six entries, recarries its shipped
      scenarios and adds the row count as a scenario. Verify: `tools/loop/evidence-check.sh
      openspec/changes/fix-car-menu-starred-row --root .` passes C1–C6.
- [x] 3.2 `auto-favorites` — "Favorites grouped by category" carries the starred entry as the group level's first
      row, with the three cases above. Verify: same evidence check.
- [x] 3.3 `tools/gen-feature-list.sh`, because `FEATURES.md` carries the capability key
      `auto-map-layout#Content box acts as the app menu`. Verify: `FEATURES.md` names the six-entry menu and
      `git diff --stat` shows only that file plus this change.

## 4. The gate

- [ ] 4.1 `./tools/loop/gate.sh` once, from the change root, after the change directory is complete and archived.
      Verify: the log's last line is a `LOOP-GATE:` tally, quoted in the worker's report block.
