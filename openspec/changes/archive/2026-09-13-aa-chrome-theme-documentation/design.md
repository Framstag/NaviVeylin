# Design: AA Chrome Theme Ownership

## Decision

Template chrome on Android Auto is **host-owned**. NaviVeylin SHALL NOT attempt to theme or override it; the host-side theme control is the only switch. The app's theme lever is its own map surface + optional per-element `Action` accents. Documented in `specs/dark-mode/spec.md` (delta) and `guidelines/UI.md` §9.

## Layer Ownership

```
ANDROID AUTO SCREEN — two rendering layers
====================================================================
  CHROME  (menus, dialogs, lists, panels, banner, ETA card, headers)
    rendered by the HOST process — remote in both deployments:
      projection  → com.google.android.projection.gearhead
      AAOS        → Automotive App Host (system app, OEM theme)
                   CarAppActivity (fallback wrapper) binds the same
                   host via IRendererService + TemplateSurfaceView
    day/night: host Display > Theme (Auto/Light/Dark); OEM-pinned
    on many AAOS units. App has NO input.

  SURFACE (map + markers + speed badge + nav hints + street label)
    app-drawn via SurfaceCallback (libosmscout Cairo)
    day/night: stylesheet daylight flag ← host night state
               (CarDaylightApplier, onCarConfigurationChanged)
    app-owned colors, light/dark pairs passed per draw call.
====================================================================
```

## API Verification (car-app library 1.7.0, AAR-inspected)

- No theme class, no window-theme hook, no `AppBarColors`/`AppBarManager`; `carColorPrimary/Dark`, `carColorSecondary/Dark` styled attrs exist but only `CarNotificationManager` consumes them.
- Only chrome element accepting a color: `androidx.car.app.model.Action` (`getBackgroundColor`) → supports `CarColor.createCustom(light, dark)` for day/night pairs.
- `values-night` in the app is never read by the projected host (templates travel over IPC as resolved colors). `Theme.DeviceDefault` on `CarAppActivity` styles only the activity's loading/error frame, never templates.
- Conclusion: no app-side lever for chrome backgrounds/rows/panels/banner on any host.

## Host-Side Control Map

| Context | Control | Notes |
|---|---|---|
| Projection | AA app → Settings → Display → Theme (Auto/Light/Dark) | New system-wide light theme: hidden in AA 14.7.152544 (mid-2025), rolling out; chrome was dark-only 2019–2025 |
| Projection (legacy) | "Day/Night mode for maps" (old setting) | Map-only; does NOT change chrome — explains "map flips, UI stays black" report |
| AAOS | head-unit system display/theme setting (OEM) | Many OEMs pin dark; no user switch; app cannot influence |

## Implications

- A user report "UI elements always black in AA, map works" is expected host behavior, not an app bug — route to the host Theme setting or host rollout, not code.
- Optional future polish within the app's own surface: light/dark pairs for overlay paints (mostly present), and `CarColor.createCustom` accents on `Action` chips. Purely cosmetic; not required by this change.

## Files Touched (this change)

- `openspec/specs/dark-mode/spec.md` (via delta) — one ADDED requirement.
- `guidelines/UI.md` §9 — chrome/surface ownership note + host Theme pointer.
- This design record. No code.
