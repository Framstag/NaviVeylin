# The car map menu carries six rows: the starred entry becomes the first row of the group level

## Why

The car map menu spends one of its seven rows on a second entry point to the starred level. On the base commit
`856269d9`, `auto/src/main/java/com/naviveylin/auto/MapTemplateFactory.kt:71` builds the "Starred favorites" row,
`auto/src/main/java/com/naviveylin/auto/MapTemplateFactory.kt:38` takes its callback, and
`auto/src/main/java/com/naviveylin/auto/MapScreen.kt:553` pushes `FavoritesScreen(starredOnly = true)` from
it, while the all-favorites group level that already pages
(`auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt:279`,
`auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt:243`) reaches the same level in one tap. The menu
therefore carries seven rows for six destinations (issue #1111).

- **Root cause**: the starred level's car entry point lives in the menu instead of in the group level, so the
  menu holds two rows for one destination family.
- **Red case**: `auto: FavoritesScreenTest.theGroupLevelCarriesTheStarredEntryFirst` and
  `auto: FavoritesScreenTest.theStarredEntryOpensTheStarredLevel` fail on the base commit `856269d9` — XML
  `tests="41" skipped="0" failures="2" errors="0"` (`evidence/red-on-HEAD-favorites.xml`);
  `auto: MapTemplateFactoryTest.theMenuCarriesSixRowsWithNoStarredEntry` fails as soon as the menu carries the
  starred row again — XML `tests="10" skipped="0" failures="2" errors="0"` (`evidence/red-on-HEAD-menu.xml`).
- **Owning requirements**: `auto-map-layout` — "Content box acts as the app menu" (modified); `auto-favorites`
  — "The group level carries the starred entry first" (added, beside the shipped "Favorites grouped by
  category").
- **Route**: the car list's row rules own this change (titled actions are clickable list rows, a list does not
  overfill its host):

- LOOP-ROUTE: guidelines/UICar.md §3

## What changes

- The starred row leaves the menu. `MapTemplateFactory.buildMenuContent` loses the row and the
  `onStarredFavorites` parameter; `MapScreen` loses the callback it wired to the push.
- The starred level's car entry point becomes the first row of the all-favorites group level, inside the
  ordered sequence `FavoritesScreenMapper.page` already partitions, so it pages with the groups and stays
  reachable on every page. `LevelRow` gains a `Starred` kind and `rowFor` renders it with the count wording
  the group rows use (`plurals/favorite_count`) and the push to the starred level.
- The row is omitted while nothing is starred, and the starred level keeps its own screen, its own stored
  order and its own "nothing starred" hint.

Out of scope: the menu's row-capacity read and the page that keeps an overflow reachable — that is the
container's other child, the change `fix-car-menu-row-capacity`.

## Impact

- Affected capability: `auto-map-layout` (the menu's entries), `auto-favorites` (the group level's rows).
- Affected code: `auto/src/main/java/com/naviveylin/auto/MapTemplateFactory.kt`,
  `auto/src/main/java/com/naviveylin/auto/MapScreen.kt`,
  `auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt`, and their two test classes.
- No new dependency, no schema, no migration; no settings key moves.
