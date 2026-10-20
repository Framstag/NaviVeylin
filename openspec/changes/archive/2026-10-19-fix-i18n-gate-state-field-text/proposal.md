# Proposal

## Why

The i18n build gate (`buildSrc` `HardcodedStringScanner`, wired into every module's `preBuild`) enumerates
text positions by **name** — `Text(`, `text =`, `title =`, `.setTitle(`, … — so display text that reaches a
surface through a name outside that list carries no matched position and is invisible. The tree holds the
proof: `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:2452`
(`error = "Could not open map database"`), `…:3563` (`snackbarMessage = "Group \"$groupName\" already
exists"`), `ui/favorites/FavoritesViewModel.kt:104` (`snackbarMessage = if (success) "Group '$name'
created" …`), `ui/mapmanager/MapManagerViewModel.kt:636` (`statusText = "Starting"`),
`BasemapViewModel.kt:351`, and `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt:673`
(`reportError("No matching location found for \"$query\"", …)`) — each a sentence a user reads, each
shipped untranslatable, none seen by the gate. The requirement it contradicts is
`i18n-l10n` — All user-facing text is translatable ("error messages … and count text" are named).

Root cause, in one sentence: **the scanner asks whether a text sink is *called*; a rendered state field is
*assigned*, and an assignment is matched only for the seven names the scanner happens to list.**

The red test, on HEAD (`5d65687`, worktree `.pi/worktrees/loop`):

```
HardcodedStringScannerTest > stateFieldIsFlagged FAILED
HardcodedStringScannerTest > prefixedStateFieldIsFlagged FAILED
HardcodedStringScannerTest > reportCallMessageIsFlagged FAILED
126 tests completed, 3 failed
```

`buildSrc/build/test-results/test/TEST-com.naviveylin.build.i18n.HardcodedStringScannerTest.xml`:
`tests="19" skipped="0" failures="3" errors="0" timestamp="2026-10-19T19:22:05.995Z"` (copied to the
change's `evidence/` and to `.pi/issue-loop/items/126/`).

Owning capability: `i18n-l10n`. Owning guideline section: `guidelines/UI.md` §10
"Internationalisation / Localisation", its **Lint + scan gate** bullet.

## What Changes

- **The gate learns the display sinks it was blind to.** `HardcodedStringScanner` gains
  - `DISPLAY_FIELD` — an assignment whose name *ends* in a display word (`text`, `title`, `label`,
    `description`, `hint`, `placeholder`, `message`, `error`), so `error =`, `poiSearchError =`,
    `snackbarMessage =`, `statusText =` are text positions, and
  - `reportError(` — a report call's message argument is display text;
  and it stops matching a position that sits *inside* an already-open string literal (the concatenated
  diagnostics message at `MainActivity.kt:168` was reported as a literal that is not one).
- **The literals the widened gate exposes move to their module's resources** — `:app` and `:auto`
  `values/strings.xml` + `values-de/strings.xml` — through the seam each file already uses
  (`context.getString` / `application.getString` / `carContext.getString`); the two failure sentences in
  `PoiResultsScreen` and `NavigationSession` use the `:core` failure resources the phone already ships, so
  the wording keeps one home (UI.md §10).
- **The car attribution notice becomes a resource.** `SurfaceAttribution.TEXT` is display text drawn on the
  surface; `:auto` already ships `osm_licence_statement` and `:app` already ships `osm_attribution`, so the
  drawer takes the text as a parameter and both car screens pass their module's string.
- `guidelines/UI.md` §10's scan-gate bullet lists the new positions.
- Additive to behaviour, breaking only for a source that hardcodes display text in a rendered field.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `i18n-l10n`: gains one requirement beside *All user-facing text is translatable* — the sink shapes the
  build scan must cover, a display-named field assignment and a report call's message — with one scenario
  per test case that holds a shape.

## Impact

- `buildSrc/src/main/kotlin/com/naviveylin/build/i18n/HardcodedStringScanner.kt` and its test.
- `app`: `ui/favorites/FavoritesViewModel.kt`, `ui/mapmanager/MapManagerViewModel.kt`,
  `ui/mapmanager/BasemapViewModel.kt`, `ui/map/MapCanvasViewModel.kt`,
  `res/values/strings.xml`, `res/values-de/strings.xml`.
- `auto`: `NavigationSession.kt`, `PoiResultsScreen.kt`, `SurfaceAttribution.kt`, `NavigationScreen.kt`,
  `FreeDrivingScreen.kt`, `res/values/strings.xml`, `res/values-de/strings.xml`, `SurfaceAttributionTest.kt`.
- `guidelines/UI.md` §10.
- No native, manifest, dependency or DI change: every touched class already holds the `Context` it needs.
- Rollback: revert the scanner; the resource-ised literals stay correct, so the rollback is the gate rule
  alone.
