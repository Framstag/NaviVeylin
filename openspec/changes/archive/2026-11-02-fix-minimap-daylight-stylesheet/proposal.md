# The mini map pushes the daylight stylesheet variant

## Why

`MiniMap` owns its own `MapRenderer` (`app/src/main/java/com/naviveylin/ui/map/MiniMap.kt:99-102`) and requests every
frame through `renderer.requestRender(lat, lon, mag, 0.0)` (`:117-121`, the pan path `:151`, the zoom buttons
`:268` and `:286`), but it never pushes the stylesheet `daylight` flag: the file's only day/night input is the
`isDarkPresentation` parameter added for the current-position palette (`:65-66`, `:85`, `:238`), and nothing hands
it to the renderer's client. Measured on the run's base `e190d8729eda946da6de579151552a5203055c61`:

```
$ grep -n 'DAYLIGHT\|daylight\|setStyleSheetFlag' app/src/main/java/com/naviveylin/ui/map/MiniMap.kt
(no output)
$ grep -n 'setStyleSheetFlag' app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt
246:        client.setStyleSheetFlag(MapCanvasViewModel.STYLE_FLAG_DAYLIGHT, !daylight)
```

The main map is the only writer of that flag (`resolvedDaylight()` at `MapCanvasViewModelSettings.kt:211`), and
the mini map has its own renderer, its own tile cache and its own front buffer, so a variant change invalidates
the main map's cache (`invalidateStyle()`, `MapRenderer.kt:484-489`) and leaves the mini map's bitmap at the
variant it last rendered — a day-stylesheet bitmap inside a dialog whose canvas follows the theme
(`drawRect(surfaceColor)`, `MiniMap.kt:163`). The widget's own KDoc already states the value it must follow:
"@param isDarkPresentation resolved dark presentation … the widget's map bitmap must follow the same value"
(`MiniMap.kt:65-69`); the renderer never received it.

**Root cause, one sentence.** `MiniMap` resolves the presentation for its overlay palette but never applies it to
the renderer that produces its bitmap, so its frame follows whatever variant the shared native client happened to
carry and is never re-rendered when that variant changes.

**Red case on HEAD.** Three cases in `MiniMapComposeTest`, run against the base with only the test file changed:

| case | result |
|---|---|
| `darkPresentationPushesTheDarkDaylightFlag` | `expected:<[(daylight, false)]> but was:<[]>` |
| `lightPresentationPushesTheDaylightFlag` | `expected:<[(daylight, true)]> but was:<[]>` |
| `presentationChangeReRendersTheMiniMap` | `TimeoutCancellationException` — no render follows the flip |

XML `tests="10" failures="3" errors="0"`, timestamp `2026-11-02T04:56:48Z` (`evidence/red-on-HEAD.xml`, taken by
`evidence/red-on-HEAD.log`).

**Owning capability and guideline.** `mini-map` — "Reusable embeddable widget"
(`openspec/specs/mini-map/spec.md:62`), which requires the widget to render without state from the main map
screen; the shipped spec names no stylesheet variant, hence the delta is an ADDED requirement. The
contract is `guidelines/MapStyling.md` §15 "Day/Night (daylight flag) contract": the flag selects the variant,
no tile cache survives the reload, and a presentation change must invalidate the surface's own frame. The
guideline already states the rule; this change makes the widget obey it, so no rule text moves (round 2 updates
the two places that name the *entry* a phone writer uses — see `design.md`, "Round 2").

**Surface.** The flag has one app-side writer today (`MapCanvasViewModelSettings.kt:246`) and this change adds a
second, value-deduped one. The cases that assert the flag (`grep -rln 'daylight\|STYLE_FLAG_DAYLIGHT'
app/src/test auto/src/test core/src/test`) are 11 files, none of them the mini map; the mini map's own tests are
`MiniMapComposeTest` and `MiniMapCurrentPositionPaletteTest` (2 files), and 16 `:app` call sites already pass
`isDarkPresentation`. No fixture asserts the widget's variant.

```
$ grep -rn 'isDarkPresentation' app/src/main/java app/src/test/java | wc -l
128        (measured 2026-11-04; the base e190d8729 gives 115)
$ grep -rln 'daylight\|STYLE_FLAG_DAYLIGHT' app/src/test auto/src/test core/src/test | wc -l
12
$ grep -rln 'MiniMap(' app/src/main/java app/src/test/java | wc -l
4
```

surface: 3 cases added in round 1, 3 in round 2, 0 rewritten, 0 fixtures realigned —
`grep -c '@Test' app/src/test/java/com/naviveylin/ui/map/MiniMapComposeTest.kt`
7 -> 13 against the base, and `grep -rn 'isDarkPresentation' app/src` 115 -> 128 (measured 2026-11-04: the
six cases, the `launch` helper's new parameter, the sink parameter and the applied-variant palette line).

## What Changes

- **`MiniMap.kt`** — one `LaunchedEffect(renderer, isDarkPresentation)` applies the variant through the shared
  `MapDaylightApplier` (`app/…/ui/map/MapDaylightApplier.kt`) before the first frame, so a client that already
  carries the value costs no native call and no stylesheet reload; on a later value change it calls
  `renderer.invalidateStyle()` so the widget's tile cache and front buffer cannot survive the variant switch. A
  rejected reload is reported through `MapStyleLoadReporter` and handed to the embedder's `onStyleLoadFailure`.
- **`MapDaylightApplier.kt`** (new) — the phone's one record of the `daylight` flag, per client and value; the
  main map's `ensureMapStyle`/`pushDaylight` go through it too, and a stylesheet load records the variant it
  installed.
- **`specs/mini-map/spec.md`** (delta) — one added requirement, three scenarios, each naming one case.
- **Guideline change in round 2 only.** `MapStyling.md` §15 already owns the variant rule and §15a the
  reporting rule; round 1 moved no text. The reviewer's dedupe and reporting findings made the widget a
  second *writer* on one client, so §15's "single entry" sentence and §15a's phone bullet now name
  `MapDaylightApplier` as the shared record and the widget as a load path.

Additive: the widget keeps its public signature (`isDarkPresentation` already exists) and gains no state the
caller must supply. Rollback: revert the one effect in `MiniMap.kt`; the three cases go red again.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `mini-map`: one requirement added beside "Reusable embeddable widget" — the widget's bitmap follows the resolved
  presentation's stylesheet variant, before its first frame and after a change. No shipped requirement's text
  changes.

## Impact

| path | what changes |
|---|---|
| `app/src/main/java/com/naviveylin/ui/map/MiniMap.kt` | one `LaunchedEffect` (~10 lines) pushing the daylight flag and invalidating the renderer on a value change |
| `app/src/test/java/com/naviveylin/ui/map/MiniMapComposeTest.kt` | 3 cases added; the `launch` helper takes `isDarkPresentation` |
| `openspec/specs/mini-map/spec.md` | one added requirement + 3 scenarios, through the delta and the archive |

The change touches compiled Kotlin (`:app`), so it owes the full JVM gate `./tools/loop/gate.sh`.
