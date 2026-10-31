# Device evidence — the overlays in dark presentation (phone)

One AVD, `Pixel_8` (`emulator-5554`, Android 15 / SDK 37, 1080x2400, 420 dpi, cold boot, `-no-window -gpu
swiftshader_indirect`), the `:app` mobileDebug build of this change (`adb install -r -t
app/build/outputs/apk/mobile/debug/app-mobile-debug.apk`, 31 Oct 11:13). Lane: `.pi/skills/device-check`;
captures in `.pi/logs/device-check/` (session-local; the numbers below are the record).

The map database on this AVD is **Andorra**; the fix is injected with `adb emu geo fix <lon> <lat> 60 8 7`
and re-injected at ~1 Hz, because one injection ages out (after ~90 s the app logs `GPS fix quality: NONE`
and the marker disappears).

## What the app's own diagnostics say (coordinate-free, `adb logcat -s MapCanvasVM`)

```
D MapCanvasVM: GPS fix acc=5.0 bearing=0.0 speed=13.0 follow=false
D MapCanvasVM: renderMap mag=15.0 (viewport 15.0)
D MapCanvasVM: GPS fix quality: GOOD
D MapCanvasVM: renderMap mag=20.0 (viewport 20.0)      ← the app's maximum magnification
```

## The measurement (exact-colour pixel counts)

`bash .pi/logs/device-check/count-color.sh <shot.png> <#RRGGBB> …` counts the pixels carrying exactly one ARGB
value — exact, which is why the current-position marker's **opaque** dot is measurable this way.

### The mini map's current-position marker, one screen in both presentations

Same location details sheet (Hotel NH Andorra La Vella), fix at the displayed object, so the marker is inside
the mini map's viewport. The two captures differ only in the resolved presentation (`adb shell cmd uimode night
yes|no`, then a fresh start of the flow — the details sheet's mini map takes the flag the app resolves).

| capture | `#0B57D0` (palette **light** dot) | `#4DA3FF` (palette **dark** dot) | `#1A73E8` (the pre-change literal) |
|---|---|---|---|
| `details-light.png`, light presentation | **599 px** | 0 px | **0 px** |
| `details-3.png`, dark presentation | 0 px | **594 px** | **0 px** |

`#E8F1FF` (the palette's dark inner highlight) measured **132 px** in the dark frame. That is the change's
claim measured on a device: the dot is drawn from the palette, the tone follows the resolved presentation, and
the colour the dot used to share with the primary object marker (`#1A73E8`, i.e. `ui/theme/Color.kt:6
PrimaryLight`) appears in neither frame.

The areas agree with the widget's geometry: at density 2.625 the dot is `0.7 × 9 dp × 2.625 ≈ 16.5 px` in
radius (≈ 850 px gross, 594–599 px inside the antialiased edge) and the inner highlight `≈ 7 px` (≈ 158 px,
132 px inside its edge). Both presentations land within 1 % of each other, as a palette-only branch must.

### The main map at maximum zoom with a fix

`main-map-max-fix.png` (dark presentation, mag 20.0): `#0E1622` — the vehicle marker's dark casing —
**27 px**, so the marker is drawn. The arrow's gradient stops (`#BBDEFB` / `#1E88E5`) count 0 px because the
core is a *gradient*, where no single stop value dominates; the marker's palette is not what this change
touches (it was already correct).

## The accuracy circle: **the measurement could not be taken here**, and why

`guidelines/Build.md` and `.pi/skills/pixel-check` both require this to be stated rather than implied, so:

- the ring is drawn only when its projected radius reaches **20 px**
  (`LocationMarkerOverlay.kt`, `POOR_ACCURACY_THRESHOLD_PX`);
- the emulator's injected fix reports **acc = 5.0 m** always (`geo fix` has no accuracy argument), and at mag
  20 that projects to `5 / 1.953 ≈ **2.6 px**` — an order of magnitude below the threshold, so the overlay
  correctly draws no ring;
- the accuracy cannot be widened from the host: `cmd location providers add-test-provider … --accuracy` takes a
  *provider class* in `[1, 2]` (`ACCESS_COARSE`/`ACCESS_FINE`), not metres — measured, "accuracy is out of
  range of [1, 2] (too high)"; and `set-test-provider-location … --accuracy 60` is refused with
  `SecurityException: android from uid 2000 not allowed to perform MOCK_LOCATION`.

An independent look confirmed the same thing: a throwaway child session read
`main-map-max-fix.png` and reported the marker with **no accuracy circle** around it.

**So the ring's device measurement is the one measurement this task could not take.** The proof of the ring's
palette is the host raster cases — `evidence/green-app-accuracy-ring.xml` (`tests=4 failures=0`): 24 of 24
sampled ring pixels differ between the presentations, the drawn radius (60.0 px) and stroke (2 px) are
identical in both, and the light tone equals the shipped `#4A90D9` composite — and the car surface's ring is
proven equal to the same shared palette by `evidence/green-auto-accuracy-ring.xml`. Producing a poor fix on a
device needs either a real drive into a poor-accuracy area or a debug-only accuracy override, which is
production code this change does not own; the recipe gap is worth a `guidelines/Build.md` §10 note.

## The look (triage, not the record)

One throwaway child session read both frames (`.pi/skills/pixel-check`: the look establishes the precondition,
the counts are the evidence). It reported: both screens dark; the mini map's dot a solid blue centre inside a
white ring with a soft halo — the marker's four-part structure drawn as intended; no accuracy circle on the
main map (consistent with the threshold above); and — unprompted — that **the details sheet's mini-map tiles
are daylight-styled while the UI around them is dark**. That last observation is an independent corroboration
of **issue #729** (filed from this change: `MiniMap`'s own renderer never pushes the daylight style flag), from
a reader who had not seen the code.
