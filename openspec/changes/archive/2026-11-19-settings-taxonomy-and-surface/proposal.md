# Proposal — the phone's settings become a topic-organised surface, and the car's settings become reachable from it

## Why

The phone's settings dialog is one bottom sheet that accreted 21 controls behind ~45
parameters and no structure: `LocationOptionsOverlay.kt:82-793` renders one scrolling
`Column` whose only grouping is an ad-hoc `Text(titleSmall)` + `HorizontalDivider`, whose
ordering is historic, and whose content silently changes with `MapMode` (browse shows
orientation only, the driving modes add auto-zoom and "Follow direction"). Three different
scopes are mixed without a mark: values shared by both surfaces (dark mode, style, speeds,
avoidances, overspeed, GPS announcements), phone-only values (keep screen on, long-press
delay, phone voice, phone anchors), and car-only values (`autoVoiceGuidanceEnabled`,
`autoRoutingAnchorId`, `autoFreeDrivingAnchorId`) that the phone cannot edit at all.

The car's own surface is the mirror image: `PreferencesScreen` renders 14 `Row`s where
tapping a row cycles its value (`PreferencesScreenMapper.toggle`), including multi-value
settings such as dark mode and map style, and only two rows open a real picker. The car
therefore holds settings a driver cannot reach ergonomically, and the phone holds no route
to the car-own values — the two surfaces diverge in both directions.

Every future setting makes this worse: there is no place to put it, and no place to put
the help text a setting needs. This change gives the settings a durable structure before
the next settings land.

## What Changes

- **Two densities, one model.** The gear keeps a small bottom sheet holding only what
  changes mid-drive (auto-zoom, orientation) plus one row that opens everything else. A
  new full-height settings surface holds every setting, organised by topic. A declarative
  row model (`SettingSpec`: key, topic, scope, control, read, write, help) feeds both, so a
  new setting is one entry, not a new composable plus two call-site edits.
- **Topic-first navigation, scope as a row property.** The root page holds six entries
  (Karte, Navigation & Route, Hinweise & Sprache, Karten & Daten, Erweitert, Über
  NaviVeylin); depth stops at two. A row that belongs to one surface carries a leading
  phone or car icon; a shared row carries none. No surface tab, no filter chips — the
  filter returns only when the row count makes it earn its state (see `design.md`).
- **The car's own settings become editable from the phone.** Voice guidance for the car
  and both car vehicle anchors gain rows in their topics, badged with the car icon; a
  car anchor that inherits the phone's value shows that state and offers "use the phone
  value" to return to it. The car never shows a phone-only row.
- **`Karten & Daten` links to the Map Manager**, which stays the one screen for map source,
  downloads, basemap and update state. Reworking that screen is **out of scope** here.
- **Two new entry points.** The map menu and the no-map start screen's menu both gain
  "Einstellungen"; the gear keeps opening the quick sheet, and the quick sheet's last row
  opens the full surface. Sheet and surface are mutually exclusive.
- **The surface is an overlay on the map, not a navigation destination.** `MapCanvasScreen`
  is keyed by visit count (`NavGraph.kt:83`), so a destination change re-initialises the
  map; the settings surface lives in the existing `MapLayer.MODAL` band instead, so the
  renderer, follow loop and route session survive opening it.
- **The car's preferences become grouped sub-screens** whose rows present their options
  instead of cycling them; boolean rows keep tap-to-toggle.

Not breaking: no persisted field is renamed, removed or re-typed; every existing value
keeps its meaning and default, and a settings file from the current build decodes and
renders unchanged. Rollback is the revert of the change: the old sheet and the old car
preferences read the same `settings.json`.

## Capabilities

### New Capabilities

- `settings-taxonomy`: the durable shape of the settings — the topic tree and its depth
  bound, the scope of a row (shared / phone / car) and how a surface-specific row is
  marked, the rule that keeps the root page at six entries as settings are added, and the
  two help-text layers a row carries.
- `settings-surface`: the phone's settings surfaces — the quick sheet's bounded content,
  the full settings surface and its home in the map overlay, its entry points, back
  handling, in-page section groups, and the controls that move onto it (including the
  car-own values the phone gains).

### Modified Capabilities

- `location-options-ui`: the button opens a quick sheet; the control requirements that
  named the options sheet move to `settings-surface` (removed there, added there).
- `always-on-display`: the "Keep screen on" toggle moves from the options sheet to the
  settings surface.
- `compass-settings`: orientation now has three editors (quick sheet, settings surface,
  compass long press) that stay in sync.
- `map-long-press-timeout`: the long-press range and step are offered by the settings
  surface instead of the options sheet.
- `routing-avoidance`: the three avoidance switches sit in the settings surface beside the
  vehicle speeds.
- `voice-guidance`: the phone exposes the car's own voice switch and its vehicle anchors;
  the phone's two announcement switches live on the settings surface.
- `auto/preferences`: the car's preferences are grouped into sub-screens whose rows present
  their options instead of cycling them.
- `map-menu`: the menu gains a Settings entry with a leading icon.
- `map-canvas-screen`: the "menu extensible for future settings" placeholder becomes the
  real entry.
- `back-gesture`: system back dismisses the settings surface before it dismisses an
  underlying map overlay, and the ordering is named beside the existing sheet rules.

Capabilities examined and deliberately **not** modified: `dark-mode`,
`map-styles`, `routing-max-speed`, `compass-button` and `settings-persistence` state no place of
editing or no interaction, so their requirements hold unchanged; they keep owning the values
this change only relocates; `map-source-selection`,
`map-updates` and `map-download-infrastructure` keep owning the Map Manager behaviour this
change only links to. `landscape-layout`, `button-elevation`, `route-planning-session` and
`map-recenter-button` name the gear's position, not its content, and stay as written.

## Impact

Additive to the settings file; breaking to one UI surface's structure.

surface: 2 capability deltas created and 10 deltas modified — 11 requirement bodies rewritten, 5 removed,
2 added, 2 scenarios replaced by a removed requirement, 61 scenarios carried or added; 6 app screens/tables
touched, 2 car files, 1 new package under `app/src/main/java/com/naviveylin/ui/settings/` —
`grep -c 'LocationOptionsOverlay\|PreferencesScreen' app/src/main/java/com/naviveylin/ui/map/*.kt
auto/src/main/java/com/naviveylin/auto/*.kt`

- `app/src/main/java/com/naviveylin/ui/map/LocationOptionsOverlay.kt` — the sheet shrinks to
  the quick rows; the moved rows leave this file (the 45-parameter signature collapses to a
  state object plus one change callback).
- new `app/src/main/java/com/naviveylin/ui/settings/` — the settings surface, the topic
  tree, the row renderer, the `SettingSpec` model and its tables.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (`MapLayer`, the surface
  state, the back handling), `MapCanvasScreenModalBands.kt` (surface in the modal band),
  `MapCanvasScreenChromeBand.kt` (three sheet call sites, lines 151/331/545),
  `MapCanvasScreenParts.kt` (`MapMenu` gains an entry),
  `app/src/main/java/com/naviveylin/ui/MainScreen.kt` (menu entry and surface host).
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelSettings.kt` and
  `MapCanvasUiState.kt` — the car-own fields become readable/writable from the phone (the
  anchor resolution gains an explicit "inherits the phone value" state).
- new `app/src/main/java/com/naviveylin/ui/settings/SettingsViewModel.kt` (or a holder the
  no-map start screen can use), `app/src/main/res/values/strings.xml` (topic and row
  labels, help texts, surface icons' content descriptions),
  `app/src/main/res/values-de/strings.xml` if the project keeps a German catalog.
- `auto/src/main/java/com/naviveylin/auto/PreferencesScreen.kt`,
  `PreferencesScreenMapper.kt`, plus new sub-screen classes beside
  `OverspeedDeltaPickerScreen`/`VehicleAnchorPickerScreen` — grouped sub-screens that reuse
  the phone's labels; the row keys and `AutoSettingsProvider` seam stay.
- No manifest component, no dependency, no native/JNI change: this is Compose and Car App
  Library UI only. Karten: no submodule patch, no bridge override.
- Guidelines touched: `guidelines/UI.md` §8 (the location-options-only and per-surface
  announcement rules become scope rules) and §9 (where the dark-mode control lives),
  `guidelines/UIPhone.md` (phone surfaces), `guidelines/UIPhoneMap.md` sections 7/8 (the gear's
  content), `guidelines/UICar.md` §3/§3c (car preferences), `guidelines/DesignStack.md` §2
  (screen and sheet recipes) and §9 (data and search).
- Related open backlog item, not in scope: `#726` (the phone shell has no narrow-width
  layout) owns the wide-screen two-pane settings layout; this change stays single-column.

## Decisions already taken (with the owner)

- **A** — topic-first navigation, scope as a row property. The alternative (three tabs
  Allgemein/Telefon/AA) was rejected: it splits one mental model into three drawers and
  leaves shared settings un-findable from the surface a user is thinking of.
- **A1** — the gear keeps a bounded quick sheet; the full surface is separate. Alternative
  A2 (one full-height surface only) was rejected because it covers the map while adjusting
  mid-drive; A3 (the surface as a navigation destination) because it re-initialises the map.
- **L** — `Karten & Daten` links to the Map Manager rather than embedding source/download
  state; the Map Manager's own rework is a later, separate item.
