# Design — settings taxonomy and surface

Motivation and scope: see `proposal.md`. Requirement-level behaviour: see
`specs/settings-taxonomy/spec.md` and `specs/settings-surface/spec.md`.

## Context

- `LocationOptionsOverlay.kt` is 1124 lines: the sheet content at :239 takes ~45 parameters
  and owns 21 controls; call sites are `MapCanvasScreenChromeBand.kt:151`, `:331`, `:545`.
- The map screen lives behind `key(visitCount)` in `NavGraph.kt:83`, so any navigation
  destination change re-initialises renderer, follow state and route session.
- `MapCanvasViewModel` already owns settings reads and writes (`MapCanvasViewModelSettings.kt`)
  over `SettingsStorage`, while the start screen (`ui/MainScreen.kt`) has no settings path at
  all and shares no state with the map screen.
- Car-side settings live in `core/AutoSettings.kt`; `autoRoutingAnchorId == null` means
  "inherits the phone's value", a semantic the phone currently cannot express.

## Goals / Non-Goals

Goals at design level:

- One declarative table of settings that both renderers read, so adding a setting is one entry.
- A settings surface that can be opened from two hosts (map screen, start screen) without
  duplicating state and without touching the map pipeline.
- Car-owned values become reachable from the phone without changing their stored meaning.

Non-goals (design level): no new persistence format, no native/JNI change, no navigation graph
change, no wide two-pane layout, no search over settings, no Map Manager rework.

## Decisions

### D1 — A registry of `SettingSpec`, rendered by two thin renderers

```kotlin
enum class SettingScope { SHARED, PHONE, CAR }
enum class SettingControl { SWITCH, SLIDER, CHOICE, SELECT, LINK }

data class SettingSpec(
    val key: String,
    val topic: SettingsTopic,
    val section: SettingsSection,
    val scope: SettingScope,
    @StringRes val labelRes: Int,
    @StringRes val helpShortRes: Int? = null,
    @StringRes val helpLongRes: Int? = null,
    val control: SettingControl,
    val enabled: (AppSettings) -> Boolean = { true },          // conditional rows
    val read: (AppSettings) -> String,                          // display value / current choice
    ...control-specific payload: slider range+step, choice keys, link target,
)
```

Tables live in one file per topic (`ui/settings/specs/MapTopic.kt`, …). `read`/`write` operate on
`AppSettings`, so a row never touches a ViewModel. Alternatives rejected: a composable per
setting (status quo — that is exactly the 45-parameter surface), and a reflection/serialisation
driven form (Kotlin, R8 and Compose resource loading make it fragile and untestable).

Car-own fields are rows too: their `read`/`write` reach `AutoSettings`, which the phone's
settings store now carries end to end. The anchor rows use a small tri-state: `null` renders the
"uses the phone value" state plus a "use the phone value" action; a set value renders the anchor
label.

### D2 — `SettingsController` is the one writer, both hosts read it

A `@Singleton SettingsController` wraps `SettingsStorage`, exposes
`StateFlow<AppSettings>` and one `update(transform)` per setting. `MapCanvasViewModel` migrates
its settings writes onto it and collects the flow instead of holding a second copy; the start
screen and the surface read the same flow. Alternatives rejected: a second ViewModel with its own
copy (two sources of truth; the car-owned rows would need cross-VM plumbing), and handing the
start screen a map ViewModel (a map-sized dependency for a settings list).

The surface's visibility and current topic live in the controller too
(`surfaceState: StateFlow<SettingsSurfaceState>`), which makes "one surface at a time" structural
rather than a per-host convention and lets the sheet close itself when the surface opens.

### D3 — The surface is an overlay in the map's modal band, not a destination

`MapCanvasScreen` renders it above the map inside the existing `MapLayer.MODAL` band
(`MapCanvasScreen.kt:1057`), sizing it to fill the height while the map surface underneath keeps
its size. `BackHandler` inside the surface owns the level-by-level dismissal. Alternatives
rejected: a nav destination (re-initialises the map through `key(visitCount)`), and a
`ModalBottomSheet` (the sheet/backdrop interaction fights the map's gesture handlers and cannot
fill the height).

### D4 — The quick sheet keeps only mode-live controls

`LocationOptionsSheetContent` shrinks to an optional "In this mode" group plus the entry row; its
~45 parameters collapse to a state object and a single `onOpenSettings` callback. The `MapMode`
dependence stays — it is the one density that is allowed to change with the mode.

### D5 — Control kinds are fixed per value shape

Switch: whole row toggles. Slider: label and value above, endpoints labelled, change applied
live. Choice: inline radio list when ≤4 values (dark mode, orientation), otherwise `SELECT`
opens a sub-surface listing the values (map style, anchors, render mode). Link: chevron, no
inline control. Cycling by repeated taps is not a route to a value on either surface.

### D6 — Android Auto: the same topics in two to three list screens

`PreferencesScreen` becomes a topic list; each topic is a `ListTemplate` of its rows; a `CHOICE`
or `SELECT` row pushes an options `ListTemplate`, reusing the pattern of
`VehicleAnchorPickerScreen`/`OverspeedDeltaPickerScreen`. Row keys stay stable so
`PreferencesScreenMapperTest` cases keep their subject; a topic keeps at most ten rows (template
comfort and driver safety), which the phone's twelve-row split rule accommodates.

### D7 — Help text is two resources, resolved at compile time

`helpShortRes` renders as `supportingContent`; `helpLongRes`, when non-null, opens a
`ModalBottomSheet` on the phone and a text-only `PaneTemplate` on the car. No lookup by resource
name (not reflection-safe under R8), no tooltip (not touch-discoverable).

### D8 — Deferred by decision, not by omission

- A surface filter (Alle / Telefon / Android Auto) and search arrive only once one surface holds
  more than eight scope-marked rows or more than forty rows in total; the scope icon already
  makes the surface of a row readable, so the filter would be state without a question today.
- A wide two-pane layout belongs to the narrow/wide shell work (`#726`); this change renders one
  column at every width and does not add `material3-adaptive`.

## Risks / Trade-offs

- **Collapsing 45 parameters (3 call sites) risks a long red build** → land the surface topic by
  topic behind the old sheet, keeping each topic's rows available on both until the sheet is cut
  down in its own task.
- **A second writer during migration could drop a write** → the same task group migrates
  `MapCanvasViewModel` and deletes its direct `SettingsStorage` write path; no task leaves both
  paths live at group end.
- **An overlay must not disturb frames or follow** → the map composable keeps its measured size;
  the case `SettingsSurfaceTest#theMapKeepsRenderingWhileTheSurfaceIsOpen` fails if the surface
  resizes the surface view.
- **Car-own anchor semantics are easy to freeze by accident** → the tri-state row is asserted by
  its own case, and "uses the phone value" is a visible row state, not a hidden default.
- **Test churn**: `LocationOptionsOverlayComposeTest` cases for long-press, anchors and speeds
  move to `SettingsSurfaceTest`; the move deletes the old case in the same task so no duplicate
  name survives.

## Migration Plan

1. `SettingsController` + `SettingSpec` model with tests, no UI change yet (green gate).
2. Settings surface rendering from the registry, opened from the map menu behind a flag.
3. Topics land one by one (Karte, Navigation & Route, Hinweise & Sprache, Erweitert/links), each
   moving its rows and its tests.
4. The quick sheet shrinks to its bounded content; the gear no longer shows the moved rows.
5. Android Auto sub-screens replace the flat cycling list.
6. Docs: `guidelines/UI.md` §8/§9, `UIPhone.md`, `UIPhoneMap.md` sections 7/8, `UICar.md` §3/§3c.

Rollback: revert the change. No persisted field is renamed, re-typed or removed, so a settings
file written by the new build loads in the old one unchanged.

## Open decisions

none

The German label wording per row and the text of each help resource are settled while the rows are
written, resource by resource; they change no requirement, no approach and no task.
