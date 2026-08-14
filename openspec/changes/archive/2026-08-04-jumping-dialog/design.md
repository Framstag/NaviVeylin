## Context

Search panel uses `ModalBottomSheet` with `skipPartiallyExpanded = true`. Sheet height = content height. Content switches between empty, loading spinner, results list, and no-results text. Each transition changes content height → sheet resizes → visible jump.

See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Eliminate sheet height change during search lifecycle
- Keep sheet compact (not full-screen)
- Content overflow scrolls within stable bounds

**Non-Goals:**
- Changing search panel behavior or API
- Changing other bottom sheets (RoutePanel, LocationDetailsSheet, FavoritesSheet)
- Animation polish beyond the jump fix

## Decisions

### D1: Minimum height on outer Column vs pre-allocated LazyColumn

**Chosen:** `Modifier.heightIn(min = 280.dp)` on the outer `Column`.

**Alternatives considered:**
- **Pre-allocate LazyColumn height** — Always render LazyColumn with fixed height even when empty. Works but requires restructuring the `when` block to always emit a LazyColumn. More invasive.
- **`skipPartiallyExpanded = false` + lock expanded** — Sheet always full height. Takes too much screen for a search panel.
- **`confirmValueChange` on SheetState** — Lock sheet at current height. Complex, fights the framework.

**Rationale:** Minimum height on the outer container is the simplest change — one modifier addition. The sheet opens at 280dp, content fills that space, and the `LazyColumn` (already capped at `max=320.dp`) scrolls within it. No layout restructuring needed.

### D2: Minimum height value

**Chosen:** 280dp.

**Rationale:** Search input field (~56dp) + padding + 2-3 result items (~72dp each) ≈ 280dp. Tall enough to show meaningful results, short enough to leave map visible behind the sheet.

## Risks / Trade-offs

- **[Low] Empty state shows blank space** — When no query typed, the sheet shows 280dp of mostly empty space. Acceptable — the search field is always visible and focused.
- **[Low] Very short result sets** — 1-2 results won't fill the space. Sheet doesn't shrink. Acceptable — stability trumps tight-fitting.
