## Context

Current favorites system uses `FavoriteRepository` wrapping JNI `OSMScoutClient` for CRUD. Groups and favorites both have `attributes: Map<String, String>` for extensible fields — no JNI changes needed. UI is a full-screen Compose sheet with group grid and group detail views.

## Goals / Non-Goals

**Goals:**
- Store group color and favorite star state using existing `attributes` maps
- Add color picker dialog with predefined swatches
- Render group card with tinted background when color assigned
- Add star toggle to favorite item rows
- Add horizontal scrollable chip bar of starred favorites at top of main view
- Chip click navigates to group detail and scrolls to target favorite

**Non-Goals:**
- No new external dependencies
- No changes to map marker rendering for starred favs
- No drag-reorder of chips

## Decisions

### Decision: Use `attributes` map instead of new JNI fields
Both `FavoriteLocationGroup` and `FavoriteLocation` already have `Map<String, String> attributes`. Storing `color` and `starred` as string entries avoids adding new JNI methods. However, the JNI bridge required fixes: `toJavaFavLocation()` and `toJavaFavGroup()` did not copy attributes from C++ to Java, and `saveFavoriteLocations()` did not read attributes from Java to C++. These three JNI functions were fixed to wire the `attributes` field through. The C++ `FavoriteLocationService` already had `SetGroupColor`, `GetGroupColor`, `SetStarred`, `IsStarred` methods — these were already exposed via JNI and required no changes.

### Decision: Predefined color palette instead of free-form color picker
A set of 8-12 predefined color swatches (Material colors) is simpler to implement and guarantees good contrast against card backgrounds. Free-form hex/HSV picker adds complexity with marginal benefit for this use case.

### Decision: Chip bar uses `LazyRow` with `FlowChip` or `AssistChip`
Material 3 `LazyRow` with `FilterChip` or plain `SuggestionChip` composables. Each chip shows favorite name + optional group subtitle. Click handler calls `selectGroup()` then scrolls via `LazyListState.animateScrollToItem()`.

### Decision: Star state as `attributes["starred"]` = `"true"` / absent
Using string `"true"` / absent (null) rather than `"false"` to keep the common case (unstarred) clean — no entry means not starred. The repository method `setFavoriteStarred()` writes/removes the attribute key.

### Decision: Color stored as hex string `attributes["color"]` = `"#FF0000"`
Hex string format (e.g., `"#FF5722"`) is human-readable, easy to parse in Compose via `Color(android.graphics.Color.parseColor(hex))`, and serializes cleanly through the existing JSON persistence.

## Risks / Trade-offs

- **Risk:** `attributes` map serialization may not survive a round-trip through JNI if the C++ side drops unknown keys. → **Mitigation:** Verify by testing add/save/reload cycle. The existing `FavoriteLocationService` JSON serialization already handles the attributes map.
- **Risk:** Chip bar with many starred favorites could push group grid too far down. → **Mitigation:** Chip bar is compact (single row, scrollable). If >20 starred favs, bar height stays constant.
- **Trade-off:** Using `attributes` map means color/star are opaque strings to the C++ layer — no native-side queries possible. Acceptable since all filtering is UI-side.
