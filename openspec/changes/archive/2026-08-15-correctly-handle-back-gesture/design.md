# Design: Correctly handle back gesture

## Context

See proposal.md — Why. Current state: `MapCanvasScreen.kt` composes full-screen overlays (favorites sheet, search panel) from boolean state flags with zero `BackHandler` usage; `AlertDialog`/`ModalBottomSheet` components already dismiss via `onDismissRequest`. `androidx.activity:activity-compose:1.9.3` is present and `AndroidManifest.xml` already sets `android:enableOnBackInvokedCallback="true"`, so predictive-back plumbing exists — only the callback wiring is missing.

## Goals / Non-Goals

**Goals**
- Back gesture/button closes full-screen overlays (favorites sheet, search panel) instead of exiting the app.
- Favorites sheet group-detail sub-screen consumes back first (returns to main grid), then sheet close.
- Predictive back animation works on API 33+.

**Non-Goals**
- No change to `AlertDialog`/`ModalBottomSheet` dismissal — already correct via `onDismissRequest`.
- No change to base-map back behavior (default system handling).
- No navigation-graph changes; overlays stay flag-driven.

## Decisions

### D1: Overlays own their back handling
Each full-screen overlay registers its own `BackHandler` where it is composed: `FavoritesSheet` handles both levels (detail → main grid, main grid → `onDismiss`), `SearchPanel`/`RouteSummaryDialog`/dialogs already dismiss via `onDismissRequest`/own `BackHandler`. `MapCanvasScreen` registers one `BackHandler(enabled = navState.isNavigating)` that rejects back on the base map while navigation is active (snackbar feedback) — overlays' handlers register later and win, so they still dismiss first. When no overlay is composed and navigation is inactive, no handler is registered and default system behavior applies.

- **Why**: `BackHandler` composes with `OnBackPressedDispatcher` and predictive back automatically; co-locating the handler with the overlay that owns the state keeps dismissal logic testable at the component level (no Hilt needed) and avoids dead handlers.
- **Alternatives**: Single `BackHandler` in `MapCanvasScreen` — rejected: `FavoritesSheet` uses `hiltViewModel()` internally, so a full-screen compose test would require Hilt setup; also the sheet's internal sub-screen state would need lifting.

### D2: Nested `BackHandler` pair inside `FavoritesSheet`
`FavoritesSheet` owns its sub-screen state (`isDetailView`). Register `BackHandler(enabled = isDetailView)` that resets to the main grid, and `BackHandler(enabled = !isDetailView)` that calls `onDismiss()`. Innermost enabled callback wins (LIFO), so detail-view back returns to the grid before sheet close; dialogs inside the sheet register later and win over both.

- **Why**: No state lifting; both levels of back behavior live with the sheet and are directly testable.
- **Alternatives**: Lifting `isDetailView` to `MapCanvasScreen` — rejected, couples map screen to sheet-internal navigation.

### D3: Dialogs left as-is
About, route summary, favorite picker, permission rationale, details sheet, location options all use `AlertDialog`/`ModalBottomSheet` with `onDismissRequest` — back already dismisses them. Verify each during implementation; no code change expected.

### D4: Predictive back via existing manifest flag
`android:enableOnBackInvokedCallback="true"` already set; `BackHandler` callbacks integrate with the dispatcher, so API 33+ shows the system preview animation. No further config.

## Risks / Trade-offs

- [BackHandler ordering vs dialog dismissal] → Dialogs register their own callbacks; outer handler is disabled whenever a full-screen overlay flag is false, so no conflict.
- [Nested handler both active in detail view] → LIFO ordering guarantees sheet-internal handler wins; covered by test.
- [Regression: back exits app from sheet] → Compose test asserts sheet closes and app stays; manual check on device.

## Migration Plan

Feature-only, no data migration. Rollback: revert `MapCanvasScreen.kt`/`FavoritesSheet.kt` changes.

## Open Questions

None.
