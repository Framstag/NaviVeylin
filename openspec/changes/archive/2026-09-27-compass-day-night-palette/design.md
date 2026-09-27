# Design

## Context

See `proposal.md` — Why. Current state that shapes the approach:

- Phone compass (`app/src/main/java/com/naviveylin/ui/map/CompassButton.kt`): fixed light
  fill literals; needle/label/rim read `MaterialTheme.colorScheme`
  (`onSecondaryContainer`, `outline`). The resolved presentation already reaches the map
  screen as `MapCanvasUiState.isDarkPresentation`
  (`MapCanvasViewModel.kt:228`, populated from `DarkModeController.isDarkPresentation`)
  and is used this way by `LocationMarkerOverlay` at its call site
  (`MapCanvasScreen.kt:1236`).
- AA compass rose (`auto/src/main/java/com/naviveylin/auto/SurfaceIndicators.kt`): one
  fixed chip `ROSE_BG = 0xCC1C1B1F`, `ROSE_FG = 0xFFFFFFFF`, `ROSE_NORTH = 0xFFE53935`.
  `AutoMapRenderer.darkPresentation` (set via `RendererGate.setDarkPresentation`) is the
  documented signal for app-drawn overlays (`AutoMapRenderer.kt:114`), and it already
  branches the vehicle marker palette.
- The resolved value on the car is **preference × host**, not the host alone:
  `resolveCarDark(pref, hostDark)` in `NavigationSession.kt:905` (ON → dark,
  OFF → light, AUTOMATIC/unknown → host), fed by `onCarConfigurationChanged`
  (`isNightUiMode`). `MapScreen` and `NavigationScreen` each observe it through their
  `*ScreenObservations` class and call `rendererGate.setDarkPresentation(dark)` plus
  `pushDark()` → `rendererGate.requestDaylightPush` (stylesheet variant, applied on a
  background dispatcher).
- `FreeDrivingScreen` has **no** dark handling at all: its own `RendererGate()`
  (line 183), no `resolvedDark`, no `KEY_DARK` observation in
  `FreeDrivingScreenObservations`, no `pushDark()`. Its renderer therefore keeps
  `darkPresentation = false` and the daylight stylesheet variant for the whole session,
  and its rose can never reach a night treatment.
- Precedent for "palette branches on presentation, geometry never does":
  `core/src/main/java/com/naviveylin/core/VehicleMarkerGeometry.kt`.

## Goals / Non-Goals

Goals: both compass widgets pick their colors from a per-presentation palette whose
contrast is verifiable statically; the free-driving AA surface reaches the resolved
presentation like the map and navigation screens do.

Non-Goals (design-level boundaries):

- No change to compass geometry, rotation convention (`ProjectionUtils
  .compassRotationDegrees`), sizing, hit targets, shadows or animation.
- No change to the AA speed badge or the speed-limit sign palettes (proposal scope
  boundary — the sign is a standard-mandated sign).
- No shared `:core` palette object (see D1), no new settings, no new DI binding, no
  native/JNI change.
- Not fixing the AA speed badge / limit sign / street pill presentation, and not
  auditing other app-drawn overlays for the same class of defect (that is a `TODO.md`
  entry).

## Decisions

### D1 — Palette shape and location: phone-local functions, AA-local constants, shared convention

Alternatives: (a) a `CompassPalette` object in `:app` plus presentation-aware constants
in `SurfaceIndicators`; (b) one `:core` palette object used by both surfaces; (c)
runtime tone derivation (one base hue shifted to a dark lightness).

Chosen (a). The two widgets are not the same component: the phone compass carries
GPS-fix status in its fill, the AA rose is a chip with no status fill. A shared object
would force a lowest-common-denominator API for no parity gain, whereas
`VehicleMarkerGeometry` is shared because the *same marker* exists on both surfaces.
Only the *convention* is shared: hue family is presentation-independent, tone is not,
and the branch input is the resolved presentation. Alternatives rejected: (b) fake
parity, one consumer per function; (c) no static literals to review or test, and a
runtime shift can land outside the measured contrast envelope.

### D2 — Where the dark tones come from: fixed literal pairs

Alternatives: (a) fixed hex pairs, Material tone-100 analogue for light / tone-30
analogue for dark; (b) the theme's `error`/`tertiary` roles; (c) an alpha or darken
filter over the light fill.

Chosen (a). (b) fails on the evidence: `Color.kt` `ErrorDark = #F28B82` is a *light*
pink — a light fill again — and M3 has no role for yellow/green status;
`guidelines/UI.md` §8 already rejected theme-derived colors for the overspeed warning
red for exactly this contrast reason. (c) makes the status hue a function of the map
underneath (same fix quality looks different over forest and over motorway), which
contradicts the at-a-glance requirement.

### D3 — Phone plumbing: explicit parameter from the existing state

Alternatives: (a) `isDarkPresentation: Boolean` parameter threaded through
`MapCompassBlock` / `MapRightWidgetColumn` from `state.isDarkPresentation`; (b) a
`CompositionLocal`; (c) `isSystemInDarkTheme()` inside the composable.

Chosen (a). (c) is a defect by construction: it bypasses the On/Off preference and
would make the manual overrides in the specs unobservable. (b) hides the dependency,
and every preview and test rig would need a provider for a single consumer that already
exists in the state. (a) matches the call-site pattern of `LocationMarkerOverlay` and is
directly testable by rendering the composable in both modes.

### D4 — AA free-driving dark plumbing: per-screen observation, same pattern as the other screens

Alternatives: (a) `resolvedDark: StateFlow<Boolean>` constructor parameter on
`FreeDrivingScreen`, a `KEY_DARK` observation in `FreeDrivingScreenObservations`, and a
`pushDark()` copy of the `NavigationScreen` shape; (b) a session-level push into every
screen's gate at creation; (c) leave free driving to `TODO.md`.

Chosen (a). It is the established pattern (`CarScreenObservations` owns lifetime, the
`*Observations` class owns what is observed — an AGENTS.md hard rule), it reuses the
existing `DaylightApplier` dedup so no new logic is introduced, and it fixes the
free-driving daylight-at-night defect as a side effect rather than as a separate change.
(b) needs a session→gate registry that does not exist and would reach into the
host-fault-isolation seam for no benefit. (c) was rejected by the user: free driving
draws the same rose, so it would reintroduce the reported symptom.

### D5 — Phone fill stays opaque

Alternatives: (a) opaque fill; (b) the speed badge's 0.92 alpha convention.

Chosen (a). The compass has no text on the fill, so it cannot use the badge's
"fill flip carries the warning, text carries the reading" trick; with alpha the status
hue mixes with the map and stops being a stable signal. Separation from the map is the
rim's job (see D7's AA analogue).

### D6 — Contrast enforcement: literals plus a luminance test

Alternatives: (a) fixed pairs, with a pure unit test computing WCAG relative luminance
and asserting the ratios; (b) runtime legibility adjustment of the on-color.

Chosen (a). A runtime adjustment produces colors that no reviewer or test can pin, and
the whole point of this change is that the current combination is unverifiable by
construction. Measured values (WCAG 2.x relative luminance, computed with awk):

```
phone, light presentation                phone, dark presentation
fill     on#1F1F1F   vs map(light)       fill     on#E8EAED   vs map(#1E1E1E)
FFCDD2   11.71:1     -                   93000A    7.76:1     1.78:1
FFF9C4   15.38:1     -                   5C4300    7.72:1     1.79:1
C8E6C9   12.26:1     -                   1B4A24    8.49:1     1.63:1
```

Hue check (HSV hue angle) — family preserved across presentations:
`354° -> 356°` (red), `54° -> 44°` (yellow), `122° -> 131°` (green). Pairwise luminance
separation inside one presentation is deliberately small in both (`1.05–1.31` light,
`1.01–1.10` dark): the states are hue-differentiated exactly as the light palette
already is, so the test asserts hue-family membership and mutual distinctness rather
than a luminance gap. Dark fill vs dark map is only `1.63–1.79:1` — the fill is not
meant to separate from the map by itself; the rim (D7) and the button shadow do that,
and the requirement is on needle/fill contrast (≥4.5:1), not fill/map.

### D7 — AA rose per-presentation treatment: light rim at night, chip tone kept

The current chip is `0xCC1C1B1F` at 80 % alpha, i.e. **1.03:1 against a dark map at
`#1E1E1E`** — at night the rose body effectively disappears and only the white ticks and
the red pointer float. So the AA branch is contrast-driven, not a polarity flip.

Alternatives: (a) keep the dark chip and add a light rim in night presentation;
(b) lighten the chip to a mid grey at night; (c) invert the polarity to a light chip at
night (mirroring "dark chip on light land").

Chosen (a). Measured:

```
candidate                 vs day land   vs dark land   white fg   north #E53935
#1C1B1F (today, day use)  15.87:1       1.03:1         17.13:1    4.05:1
#3A3A3F                    10.48:1       1.47:1         11.31:1    2.68:1
#4A4A50                     8.15:1       1.89:1          8.80:1    2.08:1
#5A5A62                     6.33:1       2.44:1          6.83:1    1.62:1
#F5F5F5 (inverted)         15.29:1(vs dark land)        1.09:1     3.88:1
```

(b) tops out at `2.74:1` chip/land while dropping the red north pointer to
`1.62–2.68:1`, below the 3:1 non-text floor — it trades the pointer for the body.
(c) separates the body well but (i) a bright chip in a night cabin is glare, and (ii)
white ticks on a white chip become unreadable (`1.09:1`). (a) keeps the best existing
numbers (white `17.13:1`, north `4.05:1`), restores body separation via a light rim
(rim vs dark land ≈ `15.3:1`), and adds no glare. Day presentation keeps the palette
exactly as today, so the change is additive there.

### D8 — Threading and lifecycle

No new dispatcher, thread or coroutine scope.

- Phone: the palette is a pure function of `(quality, isDarkPresentation)` evaluated
  during composition. No allocation in a draw path beyond the existing color values.
- AA rose: `SurfaceIndicators.draw` runs inside the existing overlay drawer on the
  render thread and only reads the flag; it must stay free of native calls and of
  anything that could block (host-fault-isolation rule).
- Free driving: the dark flag arrives through `FreeDrivingScreenObservations`
  (started in `onStart`, stopped in `onStop`), so no duplicate collector can leak across
  background round trips; `setDarkPresentation` is applied on the renderer as today, and
  the stylesheet variant goes through `rendererGate.requestDaylightPush` +
  `DaylightApplier` dedup, applied on the background dispatcher that already exists. The
  screen cancels its own scope in `onDestroy` unchanged.

## Risks / Trade-offs

- [Adding a parameter to `FreeDrivingScreen` breaks its two construction sites] →
  both (`MapScreen.kt:496`, `NavigationSession.kt:768`) already hold `resolvedDark`;
  compiler-enforced, covered by the `:auto` unit tests.
- [A rim is a new visual element, and its alpha may read too strong or too weak in a
  real cabin] → the rim is one constant next to the rose palette; tune it in the
  on-device check without touching the requirements (the spec demands legibility, not a
  specific alpha).
- [Dark fills at `1.63–1.79:1` against dark land could look like they have no body] →
  the rim (D7) and the existing 3 dp button shadow carry separation; verified on device
  in both presentations.
- [The free-driving fix widens the diff into the AA stylesheet path] → the change adds
  the *existing* `pushDark()` shape, reusing `DaylightApplier`; no new stylesheet,
  renderer or native code.
- [Phone and AA palettes can drift apart] → both are asserted against the same
  convention (hue family constant, tone per presentation) and each has a unit test; the
  widgets are intentionally not the same component (D1).

## Migration Plan

No data, setting or persistence migration; no `versionCode`, manifest or asset change.
Delivery is a normal app update for both flavors. Rollback is a revert of the palette
branch and the free-driving observation: the light presentation is untouched, so a
revert restores today's behavior exactly (except the free-driving daylight-at-night
defect, which returns).

## Open Questions

None that affect the specs, the approach or the task breakdown. The rim alpha (D7) and
the exact dark fill literals are tunable constants inside the envelope the specs and D6
pin down, and are confirmed on device rather than by a further planning step.
