# Design — the starred entry moves from the menu to the group level

## Root cause

One mechanism: the starred level's car entry point was written into the map menu
(`auto/src/main/java/com/naviveylin/auto/MapTemplateFactory.kt:71`), where it duplicates a destination the
all-favorites group level already owns — the group level is one tap from the menu
(`auto/src/main/java/com/naviveylin/auto/MapTemplateFactory.kt:75` builds the "All favorites" row) and reaches
the starred level from there. Two rows, one destination family, so the menu holds seven rows for six
destinations.

## The single fix

Move the entry point instead of copying it:

1. **The group level gains the row.** `FavoritesScreen.levelRows` prepends a `LevelRow.Starred` carrying
   `starredOrder.size` when the stored starred order is non-empty. It is prepended to the list the pager
   receives, never added as a fixed header above the page: `FavoritesScreenMapper.page` partitions whatever
   sequence it is handed, so a header would be lost from page 2 onward. `rowFor` renders the kind through a
   new `starredRow`, which reuses the group rows' count wording (`plurals/favorite_count`) and pushes
   `FavoritesScreen(starredOnly = true)` through `armScreenPush`, the same guard every other car row uses.
2. **The menu loses it.** `MapTemplateFactory.buildMenuContent` drops the starred `Row` and the
   `onStarredFavorites` parameter, and `MapScreen` drops the callback body that pushed the starred screen. The
   menu keeps one row per destination it opens: free driving, all favorites, POI search, search history,
   diagnostics, about.

## Blast radius

- `MapTemplateFactory.buildMenuContent`'s signature loses one parameter; its only caller is
  `auto/src/main/java/com/naviveylin/auto/MapScreen.kt:544`, and its tests.
- `FavoritesScreen`'s group level gains one row; the starred level itself, its stored order, its header and its
  "nothing starred" hint are untouched, so the starred screen still behaves as
  `auto-favorites` — "Starred favorites render as one ordered list" states.
- The level's row count grows by one while something is starred, so a level that was already paging pages one
  row earlier. That is the intended consequence: the row is part of the partition, not an extra.

## Rollback

`git revert` of the change's single commit restores the seven-row menu and the group level without the starred
row; no data, setting or stored order is rewritten by either direction.

## Alternatives the evidence eliminated

- **Keep the menu row and drop the group level's group rows**: removed — the group level is what makes a large
  favorites store browsable under the host's row limit (`auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt:247`),
  and the issue names the starred row as the redundant one.
- **Keep both rows and rely on the menu's capacity read**: removed — the missing capacity read is the container's
  other child (`fix-car-menu-row-capacity`); this item's mechanism is the redundant destination, which a capacity
  read does not address.
- **Render the starred entry as a fixed header above the pages**: removed — it would vanish from page 2 and later,
  which is the "an item is unreachable" defect `car-list-row-limit` exists to prevent.

## Why the owning guideline section owes no edit

`guidelines/UICar.md` §3 carries the platform rules this change obeys: titled actions are clickable list rows
(`Row.setTitle().setOnClickListener()`), and a car list asks the host for its capacity and pages itself. Both
stay true — the new row is a clickable list row and it is part of the paged sequence — and §3 has no sentence
about which level owns a destination's entry point. The menu's own capacity read, the one rule of §3 this
surface still does not follow, is filed as `fix-car-menu-row-capacity` and is not touched here.
