# Car verification (6.2) — state of the attempt

Device: `emulator-5556`, AAOS AVD ("Distant Display", 1080×600 display 0, density 120).
Users: `0:Fahrer` and `10:Driver`; the car session normally runs as **user 10**.

## What was done

1. `adb install -r -t app-automotive-debug.apk` was **refused**:
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.framstag.naviveylin signatures do not
   match newer version`. The AVD carried a release-signed install from 2026-10-29.
2. The owner approved an uninstall on this AVD (irreversible for its app data, which included the
   provisioned maps). `adb uninstall` + `adb install -r -t` succeeded; location and notification
   permissions granted.
3. The car app started and logged the session normally — `Diag/SESSION: Session created`,
   `Push surface callback registered`, `Warmup complete`, `Pop to RootScreen` — but:
   `Diag/WARMUP: No maps dir at /data/user/10/com.framstag.naviveylin/files/maps`.

## Why the car half is still unmeasured

- **No map data on the device, and no way for the car surface to fetch any.** The automotive flavor
  ships no map-download UI, and `MainActivity` on automotive hardware always forwards to
  `CarAppActivity` (`MainActivity.onCreate`, `AutomotiveDevice.isAutomotive`) in **both** flavors, so
  the phone map manager cannot be reached on this device even with the mobile APK installed.
- **No root to place a database by hand.** `adb -s emulator-5556 root` →
  `adbd cannot run as root in production builds`.
- `run-as` works (the app is debuggable now), but it addresses **user 0**, while the car session runs
  as user 10.
- The car's Preferences screen is behind the map screen's action strip; the strip is a **horizontal
  row of four pills in the header** (y 69–125), not a vertical right-edge column — a screenshot read
  by a child session settled that after right-edge taps found nothing, and the browse list it draws
  carries no settings row of its own.

## Route being attempted (needs a database from the phone AVD)

```
1  boot the phone AVD (its Andorra database survived this session)
2  pull /data/user/0/com.framstag.naviveylin/files/maps/andorra to the host
3  adb push it to the AAOS AVD's /sdcard, copy into files/maps with `run-as` under user 0
4  start the car session in user 0 (am start --user 0 … CarAppActivity) and read
   Diag/VOICE surface=car lines with the car's switch on and the phone's off
```

### Result of that route (executed, 2026-10-30)

Steps 1–3 succeeded: the database was streamed out of the phone AVD with
`adb exec-out run-as … tar cf -` (5.93 MB, `files/maps/andorra/*.dat`), and streamed back in with
`adb shell 'run-as com.framstag.naviveylin sh -c "tar xf -"' < andorra.tar` (`rc=0`, the app's own
`files/maps/andorra` under **user 0**).

Step 4 failed, and it is the end of the route:

- `am start --user 0 …/CarAppActivity` creates the activity in user 0 but leaves it
  `visible=false`; the car host then starts its own session, and that session reads
  `Diag/WARMUP: No maps dir at /data/user/10/com.framstag.naviveylin/files/maps`.
- The host always binds the car session in **user 10** (`Driver`), so a database in user 0 is invisible
  to it, and nothing on this AVD can move it across users: `run-as` addresses user 0 only,
  `adb root` is refused on this production image, the app has no import path
  (`MapDataRegistrar` only re-opens installed databases), and no surface of the app can download one
  (the automotive flavor has no map manager, and `MainActivity` forwards to `CarAppActivity` on
  automotive hardware in both flavors).

A userdebug AAOS AVD (root) or a car device that already has map data would finish the check; the
reviewer decided not to start another emulator for it, so **6.2 remains open-device evidence**.

## What the car half rests on until a car device is available

- `PreferencesScreenMapperTest` — both rows carry their values, the car row flips only the car's
  value, the GPS row flips the shared value, and no row edits the phone's switch.
- `PreferencesScreenTest` — the screen renders 13 rows over the same mapper.
- `NavigationEngineVoiceGuidanceTest#theCarSessionReadsTheCarSwitch` — a car session speaks by
  `autoVoiceGuidanceEnabled` while the phone's switch is off, and the `VOICE` line carries
  `surface=car`.
- `SettingsStorageVoiceSwitchTest#aCarWriteLeavesThePhoneVoiceValueAlone` — the mapping writes only the
  car's field.
