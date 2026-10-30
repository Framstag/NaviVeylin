# Phone measurement — task 5.2

Device: `emulator-5554` (1080×2480, Andorra database provisioned, fix injected with
`adb emu geo fix`), app installed with `adb install -r -t` (no uninstall, no data cleared).

## Geometry — the two switches render in the location-options sheet

BROWSE-state sheet, `uiautomator dump` (`uidump`, one dump per reading):

```
Keep screen on    switch bounds [880,614][1017,740]    checked=true
Lane instructions switch bounds [880,785][1017,911]    checked=true
Voice guidance    switch bounds [880,956][1017,1082]   checked=true
GPS announcements switch bounds [880,1103][1017,1229]  checked=false
```

Rows measured 56 dp tall (label bounds 63 px wide inset, 63 px high per row text), and the
navigation-state sheet carries the same two rows shifted by the shorter header:

```
Voice guidance    switch bounds [880,1106][1017,1232]  checked=true
GPS announcements switch bounds [880,1253][1017,1379]  checked=false
```

The defaults the spec names are the ones on the device: the phone's voice switch is **on**, the shared
GPS switch is **off**.

## Speaking, then not speaking — the `VOICE` diagnostics tag

A route was calculated in Escaldes-Engordany (107 steps, 1.7 km / 2 min, `Diag/ROUTE` census in the
logcat capture) and navigation started; fixes were injected along the route.

```
18:29:47  D Diag/VOICE: voice: kind=turn surface=phone outcome=spoken
```

The voice switch was then turned off **during the session** (sheet row tapped at its measured
bounds), the sheet closed, and more fixes injected:

```
18:30:28  D Diag/VOICE: voice: kind=gps  surface=phone outcome=suppressed reason=gps-switch-off
18:30:59  D Diag/VOICE: voice: kind=turn surface=phone outcome=suppressed reason=voice-switch-off
18:31:16  D Diag/VOICE: voice: kind=turn surface=phone outcome=suppressed reason=voice-switch-off
```

The persisted file agrees:

```
$ adb shell run-as com.framstag.naviveylin cat files/maps/settings.json
… "voiceGuidanceEnabled":false …
```

## What this settles

- Both switches are rendered and reachable in the options sheet, in BROWSE and during navigation.
- A turn is spoken with the switch on and silent with it off, and the change applies to the next
  instruction — no restart, no session reset.
- A GPS edge arrives while the shared switch is at its default and is suppressed with its own reason,
  which is the "GPS chatter is off unless asked for" half of the change.
- Every line is coordinate-free (identity only: kind, surface, outcome, reason), and
  `:app:checkNoCoordinatesInLogs` is green.

Note for a later run: this device's persisted `voiceGuidanceEnabled` is `false` after the measurement
(the switch was toggled to produce the suppression evidence); it is user data and was left as it was.
