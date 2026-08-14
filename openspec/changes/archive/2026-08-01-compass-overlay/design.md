## Context

Current `LocationOptionsOverlay` is a `DropdownMenu` triggered by a settings icon button near zoom controls. It contains a "Map follows position" toggle and an "Auto zoom" toggle (nav only). Settings are persisted via `AppSettings` (JSON file in `filesDir/maps/settings.json`) with fields `followMode` and `autoZoomEnabled`. Map angle is tracked in `MapCanvasUiState.mapAngle` (Double, radians) but there is no user-facing orientation control.

See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Replace dropdown with Material 3 `ModalBottomSheet` (full-width, drag-dismissable)
- Add per-mode orientation: "North up" vs "Follow direction" for free-form and navigation modes
- Persist orientation settings in `AppSettings` JSON
- Wire orientation into map rendering angle

**Non-Goals:**
- No compass rose UI element on the map (future enhancement)
- No animation for rotation transitions (acceptable snap)
- No changes to the native rendering pipeline — angle is already passed to `MapRenderer`

## Decisions

### Decision: ModalBottomSheet over DropdownMenu

**Chosen:** `ModalBottomSheet` from Material 3 Compose.

**Alternatives considered:**
- `DropdownMenu` (current) — too small for multiple controls, not full-width, not Material 3 standard for settings
- `AlertDialog` — modal, blocks map interaction, wrong pattern for settings
- Custom `AnimatedVisibility` panel — more work, no built-in drag-to-dismiss

**Rationale:** `ModalBottomSheet` is the Material 3 standard for settings panels. It provides drag-to-dismiss, full-width layout, and proper insets handling. It matches the pattern already used by `FavoritesSheet` and `LocationDetailsSheet`.

### Decision: Per-mode orientation stored as two booleans

**Chosen:** Two `Boolean` fields in `AppSettings`: `freeFormNorthUp` (default true) and `navNorthUp` (default false).

**Alternatives considered:**
- Single enum (`OrientationMode`) with per-mode map — over-engineered for two booleans
- String-based storage — type-unsafe, no serialization benefit

**Rationale:** Simple, type-safe with `kotlinx.serialization`, easy to extend if more modes appear. Defaults: free-form north-up (traditional map behavior), navigation follow-direction (driving direction up).

### Decision: Orientation applied in ViewModel, not renderer

**Chosen:** `MapCanvasViewModel` computes the effective map angle from current mode + orientation setting + GPS/nav bearing, and writes it to `MapCanvasUiState.mapAngle`. The renderer reads `mapAngle` as it already does.

**Alternatives considered:**
- Pass orientation to native renderer — unnecessary, angle computation is trivial
- Apply in composable layer — wrong layer, angle drives render requests

**Rationale:** Zero changes to native code. The existing `mapAngle` field in `MapCanvasUiState` already flows through to `MapRenderer.render()`. The ViewModel simply sets it based on the new logic.

### Decision: Settings saved on every change (not debounced)

**Chosen:** Save to `AppSettings` JSON immediately when user toggles orientation in the bottom sheet.

**Alternatives considered:**
- Debounce writes — unnecessary for infrequent manual toggles
- Save only on lifecycle pause — risk of data loss if app crashes

**Rationale:** Settings changes are rare (user taps, not continuous). Immediate write is simple and safe. The existing `settingsStorage.save()` already runs on `Dispatchers.IO`.

## Risks / Trade-offs

- **[Risk] Bottom sheet overlaps map** → The sheet uses `ModalBottomSheet` with `sheetState.dismiss()` on background tap. Map remains visible behind the sheet at peek height. Acceptable — standard Material 3 behavior.
- **[Risk] Orientation snap instead of animation** → Current implementation snaps to new angle. Smooth rotation animation would require interpolating angle over multiple render frames. Deferred — snap is functional and simple.
- **[Trade-off] JSON file persistence** → `AppSettings` uses a JSON file, not DataStore. This is the existing pattern and avoids adding a new dependency. Acceptable for small, infrequently written settings.
