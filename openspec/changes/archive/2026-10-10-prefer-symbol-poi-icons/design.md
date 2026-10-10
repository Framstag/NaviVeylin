# Design

## Context

See `proposal.md` — Why. The pieces this design has to fit into, as they exist today:

- The icon/symbol decision is taken inside native layout, in
  `MapPainter::LayoutPointLabels` (`libosmscout-map/src/osmscoutmap/MapPainter.cpp:480-510`): icon name
  plus `HasIcon()` first, `else if (GetSymbol())` second. The two call sites cover area labels (:700)
  and node labels (:856). Drawing then happens in `LabelLayouter.h:884-897`.
- `MapPainterCairo::HasIcon` (`MapPainterCairo.cpp:468-534`) sets the icon's width/height as a side
  effect, and on a failed load memoizes the failure with `style.SetIconId(0)` and logs
  `ERROR while loading image '<name>'`. `IconMode` only selects sizes; it never selects the symbol.
- The render path builds its own `osmscout::MapParameter` per frame
  (`libosmscout-client-java/src/OSMScoutClient.cpp:1514-1528`), so a value held in `ClientData` reaches
  every frame without a signature change.
- One `OSMScoutClient` singleton per process (`di/MapDownloadModule.kt:35`), used by the phone render
  path and by the car screens. Settings are persisted in one file, written by both surfaces.
- The app already has the exact invalidation levers this change needs: `MapRenderer.invalidateStyle()`
  (`MapRenderer.kt:484-489`) does epoch++, tile-cache clear and a forced full render; the render-mode
  toggle (`MapCanvasViewModel.kt:801-808`) is the precedent.
- The submodule is a branch (`naviveylin-local`) pinned by gitlink; the app's
  `:osmscout-client-java` Gradle module compiles its own copies of five submodule Java files.

## Goals / Non-Goals

- Goal: one persisted preference, default off, that changes which of an entry's two renderings is
  drawn, applied at start and flippable at runtime without a stylesheet reload.
- Goal: keep the native patch minimal and upstreamable — two files in `libosmscout-map`, one JNI file.
- Non-goal: per-surface state. The car renders the phone's choice; it gets no control (`map-styles`,
  `cross-variant-ui-parity` deltas).
- Non-goal: changing how entries that offer only one rendering behave.
- Non-goal: pushing the patch upstream in this change.

## Decisions

### 1. The knob lives on `MapParameter`, reached by a JNI setter

`MapParameter` gains `preferSymbolIcons` (default `false`) with a getter and setter; the JNI side gains
`setPreferSymbolIcons(boolean)`, storing the value in `ClientData`, and the render path calls
`params.SetPreferSymbolIcons(...)` where it already sets the other parameters.

Alternatives rejected:

- **A parameter on the render entry points** — `render`, `renderInto` and two `renderWithRouteAndPois`
  overloads would all change signature, which drags in the `native-bridge-signature-change` compiler
  sweep and every fake, for a value that is not per-frame.
- **The stylesheet `FLAG` route** (split each dual entry into `[FLAG …]` / `[!FLAG …]` blocks and reuse
  `setStyleSheetFlag`) — no C++ change, but every flip costs a full stylesheet reload
  (`DBThread::SetStyleFlag` → `LoadStyleInternal`), which is the cost `TODO.md` §120 already records,
  and it would only ever cover the entries an author split by hand.
- **A Kotlin-side choice** — impossible; the choice happens in native layout and feeds the collision
  mask.

### 2. Three branches in `LayoutPointLabels`, preference first

```
if (iconStyle) {
  symbol = iconStyle->GetSymbol()
  if (preferSymbolIcons && symbol)          -> Symbol
  else if (!iconName.empty() && HasIcon(…)) -> Icon
  else if (symbol)                          -> Symbol
}
```

The icon branch is *not* reached when the preference is on and a symbol exists, so no PNG is loaded
for that frame and no failed-load line is logged. The branch must not depend on `HasIcon`'s side
effects: the symbol branch takes its extent from the symbol (`GetWidth/GetHeight(projection)`), never
from the icon style.

### 3. Invalidation reuses `invalidateStyle()`

Flipping the preference changes pixels that cached tiles hold, so the change reuses the rendered-mode
precedent: set the native flag, then `invalidateStyle()` (epoch++, cache clear, forced full render).
No new invalidation mechanism.

### 4. Persistence and application points

`AppSettings.preferSymbolPoiIcons: Boolean = false` — a serializable default, so settings files written
before the change decode unchanged. It is applied where the persisted style is applied today
(`MapCanvasViewModel.kt:1573-1610`), so both take effect "before or as part of the first map display".
No `AutoSettings` field: the car never reads this value — it renders through the same native client
instance the phone configured.

### 5. The Java declaration is added twice

The new native method is declared in the submodule's `java/.../OSMScoutClient.java` and mirrored into
the committed override copy (`osmscout-client-java/src/main/java/.../OSMScoutClient.java`), which
replaces that file in the Gradle build. The two declaration sets are identical today and must stay so.

## Risks / Trade-offs

- **A new native method is invisible to the compiler** (`Build.md` §481) → update every fake in the
  same change (`:auto` tests subclass `OSMScoutClient`), run the affected suites, and treat the
  host-side suites as compile coverage only: the call path is proven on device.
- **Label layout shifts when the preference is on** — a symbol's extent differs from a scaled PNG's, so
  labels around a POI can reflow → accept it (it is the point of the preference) and compare a map
  capture with the preference off/on as part of the on-device pass.
- **The documented evidence recipe goes stale** — with the preference on, the two symbol-backed entries
  no longer attempt a PNG load, so the `ERROR while loading image` lines for `charging_station` and
  `mini_roundabout` disappear → update `MapRendering.md` §16a in the same change.
- **Submodule discipline** — the patch lives on `naviveylin-local` with a clean tree, the gitlink is
  bumped, and one session owns the push (`git ls-remote origin naviveylin-local` immediately before).
- **Upstream drift** — if upstream adds its own knob later, the two shapes must be reconciled; keeping
  the patch to two files and one option name keeps that cheap.
- **Interaction with `dedupe-stylesheet-loads` / §120** — deliberately none: this path never reloads a
  stylesheet.

## Migration Plan

No migration: the default keeps today's rendering, so frames, tiles and the stylesheets are unchanged
until a user flips the toggle. Rollback is the app change plus the gitlink bump.

## Open Questions

- Whether upstream accepts `MapParameter::preferSymbolIcons` as-is. Deferrable: the patch is not pushed
  upstream by this change, and nothing in the app depends on the answer.
