## Context

See proposal.md — Why. Current state shaping the approach:

- The APK already bundles every top-level `*.oss` from the libosmscout
  submodule (`syncSubmoduleStylesheets` copies the whole directory; `AssetCopier`
  mirrors it to the device stylesheet dir on every app start).
- The JNI bridge already implements the full switch mechanics:
  `OSMScoutClient.SetStyle(name)` loads asynchronously on the database thread
  and rolls back to the previous style on parse errors; `getAvailableStyleSheets()`
  enumerates the top-level `*.oss` files of the device stylesheet directory with
  names stripped of the `.oss` postfix. Style errors surface through the native
  error signal and the `NaviVeylin` log bridge.
- The app has a persisted settings mechanism (`AppSettings` via `SettingsStorage`)
  that is mapped into the car domain (`AutoSettingsMapping` → `AutoSettings`),
  used by the `:auto` module's `PreferencesScreen`.
- `MapCanvasViewModel` already pushes renderer options after the database opens
  (dark-presentation flag, with a first-frame re-push safeguard), establishing
  the pattern for applying a persisted value to the native renderer.

## Goals / Non-Goals

**Goals:**
- Expose every bundled top-level `*.oss` stylesheet as a selectable style on
  phone and Android Auto, name = file name without `.oss`.
- Persist the selection once, share it across phone and car surfaces.
- Apply the persisted style at startup and immediately on change.
- Rely on existing native switch/rollback mechanics; no native changes.

**Non-Goals:**
- No new stylesheet files, no stylesheet editing, no per-style flag bundles
  (e.g. auto-enabling `daylight` for `cycle`).
- No style switching mid-navigation on the car: phone selection applies to the
  car on the next session start (per spec).
- No migration of old per-surface style state (none exists).

## Decisions

### D1: Style list comes from the native `getAvailableStyleSheets()`
The picker lists styles returned by the JNI client, which enumerates the
device stylesheet directory — the same directory the renderer loads from.
Rationale: single source of truth, identical set for phone and car, no Kotlin
re-implementation of the enumeration, and the list automatically tracks
stylesheet dir contents (spec: "derived from stylesheets actually present").
Alternative considered: scanning APK assets `assets/stylesheets/*.oss` in
Kotlin — rejected: during an update the APK set can differ from the device
copy `AssetCopier` is still refreshing, and the picker could offer a style the
renderer cannot yet load.

### D2: Persist `styleSheet: String = "standard"` in `AppSettings`
One field, default `standard`, through the existing `SettingsStorage`
serialization; `AutoSettingsMapping` maps it into `AutoSettings` for the car
surface. Rationale: existing settings plumbing already syncs phone ↔ car; a
new field rides that for free. Alternative considered: separate per-surface
preference keys — rejected: spec requires a shared selection.

### D3: Apply after the database opens, before the first render
`MapCanvasViewModel` applies the persisted style once the database is open:
`initMap` loads it — blocking on the native DB thread, so on the injectable
`defaultDispatcher` — before the first render, so the first frame already uses
the selected style. The settings-load path re-applies the style if `initMap`
ran before the persisted settings finished loading (fast user flow), and a
selection change re-applies immediately. Rationale: the established
post-DB-open ordering against the native database-thread lifecycle, plus
injectable dispatchers keep the blocking call unit-testable. The car surface
applies the shared style when a navigation session starts (the same singleton
client in the same process).

### D4: Failure surfacing stays log-based
`SetStyle` returning `false` and native style-error events are logged via the
existing `NaviVeylin` bridge / `Log.e` diagnostics. Native already keeps the
previous style. Alternative considered: error snackbar in the picker —
rejected for now: rare failure mode, log channel satisfies the spec's error
reporting requirement; UI surfacing can be added later without spec change.

### D5: Build guard for the bundled stylesheet set
Extend the existing `AssetCopierTest` (which already asserts on the packaged
stylesheet set) to require all current top-level `*.oss` files by name, so a
submodule bump cannot silently drop a style from the APK. Rationale: the sync
task already copies everything; the guard converts that implicit behavior into
a verified contract.

## Risks / Trade-offs

- Some stylesheets (`cycle`, `winter-sports`) may look suboptimal without
  their intended stylesheet flags → Mitigation: out of scope (Non-Goals); the
  renderer applies the same flags as today; revisiting is a separate change.
- `basemap-render.oss` is a top-level `.oss` and thus appears in the picker;
  it is primarily a basemap-layer style and may render sparsely as a main
  style → Mitigation: accepted per spec ("all top-level `*.oss`"); native loads
  one style for both main and basemap databases, so no split-brain rendering.
- A style switch invalidates and re-renders tiles (one-time cost) → Mitigation:
  acceptable; switches are user-initiated and infrequent.
- Phone ↔ car list divergence if the device stylesheet dir is mid-refresh →
  Mitigation: `AssetCopier` runs at app start before either surface reads the
  list; both share the same directory.

## Migration Plan

- Deployment: additive field with a safe default (`standard`) — existing
  installs keep today's rendering unchanged; no data migration.
- Rollback: reverting the app version restores the previous behavior; an old
  APK simply ignores the new setting.
- No feature flags; the picker is the only new surface and is inert until the
  user opens it.

## Open Questions

- None — deferrable unknowns (flag presets per style, whether to hide
  `basemap-render.oss`) would change specs or tasks and are explicitly parked
  in Non-Goals.
