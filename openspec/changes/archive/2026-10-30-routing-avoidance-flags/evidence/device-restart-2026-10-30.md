# Device run: the avoidance switches survive a restart (task 4.3)

Device: `Pixel_8(AVD) - 17`, `emulator-5554`, 1080x2400 @ 420 dpi.
Build: `:app:installMobileDebug` from `ed35728f` (mobile debug, applicationId `com.framstag.naviveylin`).
Map: the `andorra` database already installed on the device.

## What was done

1. Launched the app; the system location prompt was answered with "While using the app".
2. Opened the location-options sheet and scrolled to the "Routing avoidance" section: the three rows
   "Avoid toll roads", "Avoid ferry routes", "Avoid unpaved roads" are rendered, all three switches in the
   off state (track outline, thumb left).
3. Tapped the toll and the unpaved switch (the ferry switch was left alone, as the control).
4. Read the persisted file without stopping the app:

   ```
   adb shell run-as com.framstag.naviveylin cat files/maps/settings.json
   {"avoidTolls":true,"avoidUnpaved":true,"lastMapPath":".../files/maps/andorra"}
   ```

   The toggle reaches storage immediately, and the untouched choice is absent, i.e. default `false`.
5. Force-stopped the app, relaunched it, reopened the sheet and scrolled to the same section.
6. Read the switches from the rendering (a switch is on when its track is filled and the thumb sits right,
   which the frames before and after the toggle established as this build's appearance):

   | row | after restart |
   |---|---|
   | Avoid toll roads | on |
   | Avoid ferry routes | off |
   | Avoid unpaved roads | on |

   The two values the user set came back; the one that was never touched is still off.

## What this closes, and what it does not

- Closes 4.3: a stored choice survives a restart and appears in the sheet.
- Does not exercise a route: the andorra database carries no toll information (it is imported by an older
  importer), so the toll switch changes nothing on this device by design — the spec requirement "A choice
  the data cannot express changes nothing" is the contract, and the route-level effect is measured in the
  submodule change (`client-java-avoid-tolls`).

## Frames

`.pi/device/01-map.png` … `07-after-restart.png` (not versioned: `.pi/` is ignored). `05-avoidance.png` is
the section before toggling, `06-toggled.png` after, `07-after-restart.png` after the restart.
