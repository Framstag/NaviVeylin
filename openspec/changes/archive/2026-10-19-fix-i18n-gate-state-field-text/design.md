# Design

## Root cause

`HardcodedStringScanner.TEXT_POSITION` is a list of **names**: `Text(`, `text =`, `label =`, `title =`,
`contentDescription =`, `placeholder =`, `hint =`, `.setTitle(`, `.addText(`, `.setText(`,
`.setContentTitle(`, `.setContentText(`, `description =`. A rendered state field is *assigned*, not called:
`error = "Could not open map database"` (`MapCanvasViewModel.kt:2452`) contains no listed name, so
`lineFindings` finds no position on the line and the literal is never examined. The same hole swallows a
report call whose message is a literal (`NavigationSession.kt:673`), and it is why the two examples the
tracker entry names (`errorMessage = …`, `reportError(…)`) were invisible while they shipped.

Two secondary defects fall out of widening the name set, and both are fixed here because the widened rule
would otherwise fire on them:

1. The scan has no notion of "inside a string literal". `MainActivity.kt:168` builds a *diagnostics* message
   with `" labelHint=" + (if (…) "present" else "none")`; a case-insensitive `hint =` position matches inside
   the literal and reports `" + (if (outcome.request.label != null) "` as if it were a literal.
2. A field name that merely *ends* in a display word is a display field whatever its qualifier:
   `snackbarMessage`, `poiSearchError`, `statusText`. Matching only the bare name would have hidden most of
   the live literals — so the position is a suffix match, not an equality match.

## The single fix

`HardcodedStringScanner` gains two positions and one guard:

- `DISPLAY_FIELD = Regex("(?i)(?:text|title|label|description|hint|placeholder|message|error)\\s*=")` — the
  assignment arm, replacing the seven `… =` entries that were spelled out in `TEXT_POSITION`.
- `reportError\\(` joins `TEXT_POSITION` — a report call's message is display text.
- `lineFindings` skips a position that starts inside an already-open literal on the line.

`detail` is deliberately **not** a display word: it is `MemoryPressureResponder`'s diagnostics payload
(`detail = "avail=512MB threshold=256MB"`), and logcat-only wording is exempt by the requirement. The case
`logPayloadFieldIsNotFlagged` holds that line as the exemption's witness.

Nothing else about the scanner changes: the exemptions (`%`-format, symbol-only, camelCase identifier, pure
template), the conditional handling, the notification-channel arm and the report text are untouched, so the
false-positive surface stays where it was.

## Blast radius — the literals the widened gate exposes

Every one of them is a sentence a user reads, and every file already holds the `Context` it needs
(`context` / `application` / `carContext`), several already resolve a sibling message from a resource
(`FavoritesViewModel.kt:219` `snackbarMessage = context.getString(R.string.group_move_failed, groupName)`,
`MapCanvasViewModel.kt:3178` `showSnackbar(context.getString(R.string.shared_link_unreadable))`; line numbers as
of this change) — so the sweep is the completion of a seam that exists, not a new one.

| file | line | literal |
|---|---|---|
| `ui/favorites/FavoritesViewModel.kt` | 104–149 | 12 snackbar sentences (`"Group '$name' created"`, `"Failed to rename"`, …) |
| | 169 | `"Failed to reorder '$favName'"` |
| `ui/mapmanager/BasemapViewModel.kt` | 351 | `"Failed to delete basemap"` |
| `ui/mapmanager/MapManagerViewModel.kt` | 527 | `"Failed to refresh installed maps: …"` (+ `"unknown error"`) |
| | 636 | `"Starting"` |
| | 720 | `"Cancelled"`, `"Error: $msg"` |
| | 750, 755 | `"Failed to delete map: …"` (+ `"unknown error"`) |
| | 1027 | `"The repository's region index could not be read"` |
| `ui/map/MapCanvasViewModel.kt` | 2452 | `"Could not open map database"` |
| | 2923 | `"POI search failed"` |
| | 3563, 3569, 3581 | `"Group \"$groupName\" already exists"`, `"Added to …"`/`"Failed to add favorite"`, `"Removed from favorites"`/`"Failed to remove"` |
| `auto/NavigationSession.kt` | 673 | `"No matching location found for \"$query\""` |
| `auto/PoiResultsScreen.kt` | 143 | `"GPS signal required. Please wait for a fix."` |
| | 158 | `"POI search failed: …"` (+ `"unknown error"`) |
| `auto/SurfaceAttribution.kt` | 21 | `const val TEXT = "© OpenStreetMap contributors"` |

The two `auto` failure sentences take the `:core` resources the phone already renders
(`navigation_failure_gps_signal_missing`, so both surfaces show one wording with one home — UI.md §10);
everything else gains a key in its own module's `values/` **and** `values-de/`, gated by
`GermanTranslationCompletenessTest`.

`SurfaceAttribution` is a pure Canvas drawer with no `Context`, so `geometry(doc)`/`draw(doc)` take the text
as a parameter and its two call sites (`NavigationScreen.kt:319`, `FreeDrivingScreen.kt:448`, both holding
`carContext`) pass `carContext.getString(R.string.osm_attribution)`. That also removes the only Kotlin copy
of wording the phone already ships as a resource (UI.md §10, phone/car parity).

## Residual, deliberately not closed here

The scanner is line-based: a *position* and its literal must share a line (`reportError(\n    "…"` is
invisible, and so is `Text(\n  "…")` today). Closing that means paren-balanced scanning across lines for
every position — a change of its own with a false-positive surface of its own. It is recorded here and in
`guidelines/UI.md` §10 so the next author reads it instead of rediscovering it. The three live multi-line
cases (`NavigationSession.kt:673`, `PoiResultsScreen.kt:143`, `:158`) are fixed by the sweep, so the
requirement holds for those three literals; a *future* multi-line positional message would still be invisible
to the gate, and closing that needs a multi-line sweep (paren-balanced scanning) that this change does not
carry.

## Alternatives the evidence eliminated

- **Invert the rule: any literal carrying a sentence is display text unless it is a log argument.** The
  scanner cannot tell a `Log.d(` continuation line from a text position — the three source roots carry many
  multi-word literals that are log payloads whose `Log.` call sits on an earlier line
  (`MapRenderer.kt:811`, `AutoServiceModule.kt:188`, `MapCanvasViewModel.kt:1498`). It would also fire on
  `MemoryPressureResponder.kt:120`'s `detail = "avail=…"`. This is not a viable option with a line scanner;
  it is the reason the name list exists.
- **Widen only to the two names the tracker entry quoted (`errorMessage`, `surfaceMessage`).** The live
  literals are named `snackbarMessage`, `poiSearchError` or `statusText`, so an equality-match list would close
  the entry's example and leave the hole open.
- **Move each literal to the surface (composable) instead of the ViewModel.** Not available: the message is
  produced inside a coroutine that owns the repository result, and the surface receives only a `String`.

## Risks

- A legitimately untranslated assignment whose name ends in a display word now fails the build. That is the
  rule; the escape is a resource with `translatable="false"`, not an allowlist.
- The suffix match is case-insensitive, so a name like `…Error =` matches; the gate is over-inclusive by
  design (it has no allowlist), and the tree holds no such false positive after this change.
