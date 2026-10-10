# Proposal

## Why

A stylesheet icon entry can carry a raster icon name and a vector symbol at once (e.g.
`include/amenity.oss:871` `{ symbol: amenity_hospital; name: hospital; }`), and the renderer's
precedence is fixed in native code: `MapPainter::LayoutPointLabels` tries the raster PNG first and
reaches the symbol only when the image is unavailable (`MapPainter.cpp:480-510`). Nothing in the
stylesheet syntax, `IconStyle`, `MapParameter`, the JNI surface or the app exposes that choice, so a
user who prefers the vector rendering — crisper at small sizes and identical to the entries that are
symbol-only today — cannot have it.

## What Changes

- Add a native preference to `MapParameter` (`preferSymbolIcons`, default `false`) and consult it in
  `MapPainter::LayoutPointLabels`: symbol first when the preference is set and the style has a
  symbol, otherwise today's icon-first order with the symbol kept as the fallback. Default `false`
  leaves every current frame byte-identical.
- Expose it through one new JNI setter on `OSMScoutClient` (`setPreferSymbolIcons(boolean)`), stored
  in `ClientData` and read by the render path. The render entry-point signatures do not change.
- Add a persisted phone setting (`AppSettings.preferSymbolPoiIcons`, default `false`), applied to the
  client at setup and on change, with a toggle in the phone options sheet next to the map style
  picker. Flipping it clears the tile cache, bumps the render epoch and forces a render, so no frame
  or tile from the previous preference survives.
- No car control: the Android Auto / AAOS surface renders whatever preference the phone persisted,
  because the native client is one process-wide instance. The missing car control is a deliberate,
  documented deviation from cross-variant parity.
- Not breaking. No existing user-visible default changes, and no JNI signature changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `map-styles`: the capability gains a second, independent phone-side presentation choice on the same
  stylesheet — how a style's icon entries resolve when they offer both a raster icon and a symbol —
  with its own persistence, start-up application and invalidation rules.
- `cross-variant-ui-parity`: states explicitly that this setting has no car control, that the car
  surface still renders the phone's choice, and that this is an accepted deviation rather than a
  platform-constrained one.

## Impact

- **libosmscout submodule** (branch `naviveylin-local`, gitlink bumped by this change):
  `libosmscout-map` (`MapParameter.h`/`MapParameter.cpp`, `MapPainter.cpp`) and
  `libosmscout-client-java` (`src/OSMScoutClient.cpp`, `java/.../OSMScoutClient.java`). The Java
  declaration must also be mirrored into the committed override copy
  (`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`), which
  replaces the submodule file in the Gradle build.
- **app**: `data/SettingsStorage.kt`, `ui/map/LocationOptionsOverlay.kt`,
  `ui/map/MapCanvasViewModel.kt`; the value is applied where the persisted style is applied today.
- **tests**: every `:auto` / `:app` fake of `OSMScoutClient` must override the new method in the same
  change — a new native method is invisible to the compiler and only fails at its first call
  (`Build.md` §481).
- **docs**: `MapRendering.md` §16a's on-device evidence recipe changes — with the preference on, the
  two symbol-backed entries no longer attempt a PNG load, so the
  `ERROR while loading image` lines for `charging_station` and `mini_roundabout` disappear.
- No new dependency, no manifest, licence or privacy change, no new log line carrying a coordinate.
