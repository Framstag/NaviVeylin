---
name: provision-phone-emulator
description: Provisions a phone emulator/AVD for NaviVeylin device verification — installs the app and test APKs without wiping the map data, downloads the basemap and a routable region through the app's own map manager, applies the default device configuration (location grants, GPS fix), and verifies the result. Use when a change has device-gated tasks, when an AVD has no maps, when the map manager must be driven, or after any emulator/package-manager instability.
---

# Provision the phone emulator

Every device-gated verification in this repo (OpenSpec tasks of the form "on-device run", the
stylesheet `Unknown types` checks, the diagnostics/coordinate rules, the car-surface scenarios on an
AAOS AVD, instrumented tests) needs an AVD that has **map data installed and a healthy package
manager**. Getting there is fiddly, and the failure modes are silent: a run can look green while the
test did nothing, and one careless reinstall erases a 784 MB download.

This skill exists because that happened twice while finishing `route-planning-session`: the AVD's map
databases were wiped when the emulator's `PackageManager` dropped the package (losing a package
deletes its data), and an `assumeTrue`-skipped instrumentation run reported `OK (3 tests)` in 0.02 s.

## Contract

| Element | Rule |
|---|---|
| Installing | `adb install -r -t <apk>` for the app **and** the androidTest APK. Never uninstall. |
| After maps exist | **Never** reinstall/uninstall the app, and **never** run `connectedAndroidTest`: AGP uninstalls/reinstalls, which deletes `files/maps` along with the package data. |
| Device-side runs | `adb shell am instrument -w -e class <FQCN> com.framstag.naviveylin.test/androidx.test.runner.AndroidJUnitRunner` |
| Reading a result | The JUnit XML (`app/build/outputs/androidTest-results/…/TEST-*.xml`) — `tests`/`skipped`/`failures`. A run that finishes in ~0.02 s and prints `OK (N tests)` is `assumeTrue` **skipping**, not a pass. |
| The basemap | A rendering-only lookup database with **no routing data**. Never pass it to `openDatabases()`, a routing service or a route test — the app opens it through its own lookup directory. (Prudence, not a proven cause: see pitfall 3.) |
| Map data | Downloaded through the **app's own map manager** (provider `karry.cz`, built in). The host cannot fetch it: the provider's `latest.php` needs the client's own version arguments, and the directory listing is not the download path. |
| Reboots | `adb reboot` is safe for the data and repairs a sick package manager. `-wipe-data` is not — it re-provisions everything, including the maps. |

## Procedure

### 1. Check the emulator's health before trusting anything

```bash
adb devices                                  # device present?
adb shell getprop sys.boot_completed          # 1
adb shell am get-current-user                 # which user is the shell talking to?
adb shell pm list users                       # multi-user AVDs exist; data lives per user
adb shell pm list packages --user 0 | grep naviveylin   # NEVER trust the plain listing
adb shell 'df -h "$EXTERNAL_STORAGE" | tail -1'   # >= 1.5 GB free for NRW + basemap
```

**Read the package state only with `--user N`.** On this AVD `pm list packages | grep naviveylin`
returned **0** while the app and the test APK were both installed (`pm list packages --user 0` → 2) —
a bare listing is not evidence of a missing package, and concluding "the package was dropped" from it
sends the whole session down the wrong path. The definitive data check is
`adb shell run-as com.framstag.naviveylin ls files/maps`.

A multi-user AVD can also *look* like lost data: before treating an empty-looking app as a loss, check
`am get-current-user` / `pm list users` / `pm list packages --user <N>` for every user, because the
maps are stored per user (`/data/user/<N>/…`).

Sick-state symptoms and the fix (**reboot first, do not wipe**):

- `adb install` fails with `NullPointerException … PackageManagerInternal.freeStorage` — the
  package manager is broken.
- `pm list packages` loses an installed package, or `run-as <pkg>` says `unknown package`.
- Launcher / System-UI ANR dialogs stack up.

```bash
adb reboot && sleep 30
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 10; done
adb shell pm list packages | grep naviveylin   # re-check before installing
```

A reboot restores a working package manager but does **not** bring back data that a package loss
already deleted — check `run-as <pkg> ls files` before assuming the maps are still there.

### 2. Build and install (keeping any existing data)

```bash
./gradlew :app:assembleMobileDebug :app:assembleMobileDebugAndroidTest   # add the ABI flag to shrink it
adb install -r -t app/build/outputs/apk/mobile/debug/app-mobile-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/mobile/debug/app-mobile-debug-androidTest.apk
```

For a faster iteration build: `-Pandroid.injected.build.abi=arm64-v8a` does **not** help the usual
x86_64 emulator; keep all ABIs or add `x86_64` explicitly.

### 3. Default device configuration

```bash
adb shell pm grant com.framstag.naviveylin android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.framstag.naviveylin android.permission.ACCESS_COARSE_LOCATION
# a fix inside the downloaded region (lon before lat):
adb emu geo fix 7.4653 51.5136
# fewer ANRs on a software-rendered emulator:
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

`adb emu geo fix` is the emulator console command (`lon lat`); without it a GPS-gated flow (start =
current location, navigation, follow mode) has no fix. The app's own settings (units, dark mode,
vehicle anchor) are not device prerequisites — set them in the app when a scenario needs them.

### 4. Download the maps through the app's map manager

`./gradlew install...` is not enough: **the region must be downloaded on the device**, by a human
tapping the UI or by the steps below.

Provider list (2026-10, sizes measured on the emulator):

| Item | Size | Role |
|---|---|---|
| Basemap **Minimal** | 2.4 MB | Enough to render a world background — the quick start |
| Basemap **Full** | 39.0 MB | Full world basemap |
| `europe → germany → North Rhine-Westphalia` | 783.7 MB | The routable region used by the phone device checks (Dortmund / Cologne / Bochum coordinates are inside it) |

Walking the UI:

```bash
# start screen has "Karten holen" (map download), the map screen has content-desc="Menü"
adb shell input tap 540 1348            # 1080x2400/420: "Karten holen"
# in the map manager:
#   "Aktualisieren" (~867,383) loads the provider list (provider karry.cz is built in)
#   the search field (~336,552) filters, but on this screen every adb interaction
#   RE-COLLAPSES the list — prefer tapping a group row by dumped bounds instead
#   of typing: europe -> germany -> North Rhine-Westphalia -> "Herunterladen"
```

Read the screen with a **unique literal path and a success check** — a variable that expands empty, or a
failed dump that leaves the previous file, silently feeds you a stale hierarchy (pitfall 9):

```sh
N=0
ui() {  # fresh hierarchy or nothing
  N=$((N+1)); f="/sdcard/ui-$$-$N.xml"
  for i in 1 2 3 4 5; do
    out=$(adb shell uiautomator dump --compressed "$f" 2>&1 | tr -d '\r')
    case "$out" in *"dumped to"*) break;; *) sleep 4;; esac
  done
  adb shell cat "$f" 2>/dev/null | tr '<' '\n'
}
ui | grep -oE '(text|content-desc)="[^"]+"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"'
```

Tap the centre of a row's bounds; keep an eye on `Total installed maps found:` in logcat rather than on
the UI, which lags and re-lays-out.

### 5. Verify the provisioning

```bash
adb logcat -c && adb shell am start -n com.framstag.naviveylin/com.naviveylin.MainActivity
sleep 12 && adb logcat -d -s NaviVeylin | grep -E "found valid map|Total installed maps"
#   found valid map 'North Rhine-Westphalia' at …/files/maps/north-rhine-westphalia (with metadata)
#   Total installed maps found: 1            <- the basemap is listed separately from the route maps
adb shell run-as com.framstag.naviveylin ls files/maps     # north-rhine-westphalia, (basemap)
```

An app that starts on the map instead of the "Karten holen" start screen is provisioned. `Unknown
types in '<file>'` warnings are expected on an install whose map data predates the stylesheet
(`TODO.md` §91/§99) — one line per style file, not a failure.

### 6. Run the device checks

```bash
# instrumented test (no reinstall, no uninstall):
adb shell am instrument -w -e class com.naviveylin.route.RouteInstructionPositionDeviceTest \
  com.framstag.naviveylin.test/androidx.test.runner.AndroidJUnitRunner
# then read the XML, not the console summary:
grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  "app/build/outputs/androidTest-results/connected/*/flavors/mobile/TEST-*.xml"
```

For a UI scenario (anchors, step analysis, grace expiry, car surfaces): drive it with `adb shell input
tap/swipe` between dumps, watch `adb logcat -s NaviVeylin`, and export the diagnostics file for the
coordinate rule (spec `auto-diagnostics`: diagnostics carry no coordinates).

### 7. Driving the app's own UI (route session, day/dark, navigation)

Anchors, step analysis, the grace and the day/dark presentation are **UI** states; the dumps above are
the only way to reach them, and this app's flow is long enough that one missed tap sends the run down a
wrong branch (a stray tap started *free driving* and silently ended a route session three times).

```sh
# map screen -> route panel (coordinates for 1080x2400/420, verified 2026-10-03)
tapd "Ort suchen"                     # content-desc; (84,337)
tap  540 226; input text "Bochum"     # the EditText node's centre
# pick the RESULT row, not the field: the result is a TextView with that text,
# the field is an EditText; tap the bounds of the node line you selected (never re-grep by text)
tapm 'text="Route berechnen"'         # details sheet; (582,1789)
tapm 'text="Berechnen"'               # (540,2190) while the panel is compact -> 17,3 km / 20 min
tapm 'text="Navigation starten"'      # (540,2158)
```

What to know before driving it:

- **Three anchors.** `Routenfenster aufklappen` / `zuklappen` (the label swaps) and
  `Routenfenster minimieren`. Expanded is the only anchor that shows the *vehicle profiles* and the
  **step list**; minimized leaves the `RouteReadyPill` (`Route` + distance + duration) on the map.
- **The step list is the expanded panel, not the `Route anzeigen` dialog.** The dialog's rows carry no
  `onStepSelected`, so they are not clickable — a tap there does nothing and looks like a broken app.
- **Camera evidence**: after tapping a step, minimise the panel and read the zoom label in the dump
  (`MANOEUVRE_FOCUS_MAG` = **17**). The step list itself can be scrolled inside the sheet; do **not**
  drag from `y <= 70` (that opens the notification shade).
- **Day/dark** lives on the map screen → `Standortoptionen` → `Dunkelmodus`
  (`Ein`/`Aus`/`Automatisch`/`Adaptiv nach Umgebungslicht`). It is a `ModalBottomSheet`: tap the option,
  then leave via the sheet's own affordance — neither back nor a `y=63` drag dismisses it reliably.
- **Grant the notification permission first** (`pm grant … android.permission.POST_NOTIFICATIONS`): the
  first-run dialog appears late and covers the screen mid-flow.
- **Free driving is a trap.** `Freie Fahrt starten`/`beenden` sits near the navigation overlay's bottom
  icons; a tap meant for `Navigation beenden` turned free driving on three times, and starting free
  driving *ends navigation by itself*, so the session never reaches its stopped state (and the
  `RoutePanelVM: session grace period expired` line never appears). Always print the
  `Freie Fahrt …` label before and after such a tap.
- **Clear logcat deliberately, then take a positive control.** `adb logcat -c` before a step isolates
  its lines, but an empty `-s NaviVeylin` stream proves nothing — restart the app and render once (the
  bridge then logs startup, incl. the stylesheet `Unknown types` lines) so "0 lines" means "quiet",
  not "stream dead".

## Pitfalls

1. **`connectedAndroidTest` on a provisioned AVD.** It uninstalls/reinstalls; the maps go with the
   package data. Use `am instrument` (above).
2. **A green-looking instrumentation run that did nothing.** `assumeTrue("no installed map …")` inside
   a device test makes JUnit report `OK (N tests)` with a ~0.02 s runtime. Always read
   `tests`/`skipped`/`failures` from the XML, and treat a skip as "not verified".
3. **Handing the basemap to routing.** The basemap is a rendering lookup database with **no routing
data**, so keep it out of `openDatabases()`/any routing setup — the app opens it through its own lookup
directory. Filter it: `listFiles { it.isDirectory && it.name != "basemap" }`. **Honest scope**: this is
prudence, not a proven cause — a routing failure (`Route calculation failed` on a valid dataset) was
suspected to come from it on 2026-10-03 and was **not** reproduced once the download had settled; a
device run with the same coordinates then passed. Do not cite the basemap as the reason for a routing
failure you have not otherwise explained.
4. **Trusting the UI dump over logcat.** The map manager's list collapses on adb interaction (dumps
   lag, taps land on re-laid-out rows); the app's own log lines (`found valid map`, `Total installed
   maps found`) are the reliable progress signal.
5. **Fetching the map from the host.** `curl https://osmscout.karry.cz/...` gives directory listings,
   not the download; `latest.php` needs the client's version arguments and returns `[]` without them.
   Download on the device.
6. **`-wipe-data` to fix a sick emulator.** It repairs the package manager and destroys every
   provisioning step, including the 784 MB region.
7. **Reading package state from a bare `pm list packages`.** It reported `0` for an installed app on
   this AVD (`--user 0` reported 2), which reads as "the package was dropped and its data is gone".
   Always use `--user N`, and confirm data with `run-as <pkg> ls files/maps` before concluding a loss.
8. **Assuming a reboot wiped the maps.** It does not — a *package loss* does, and on this AVD the
   likely cause was `connectedAndroidTest` (AGP uninstalls first; that run also logged
   `Failed to uninstall package … DELETE_FAILED_INTERNAL_ERROR`). See pitfall 1.
9. **A stale hierarchy driving your taps.** `uiautomator dump "$EXTERNAL_STORAGE/ui.xml"` can expand
   the variable to nothing (`cat: /ui.xml: No such file or directory`) and, worse, a dump that fails
   leaves the *previous* file in place, so every later `cat` returns the old screen with old
   coordinates — which is how a tap aimed at a panel row hit the map's *free driving* button, and how a
   settings sheet appeared to ignore back/dismiss. Dump to a **literal unique path**
   (`/sdcard/ui-$$-N.xml`), require `dumped to` in the output, retry a few times, and re-resolve every
   coordinate from a fresh dump before tapping.
10. **Tapping a re-grepped node.** Selecting a node line and then searching the *text* again on a new
    dump can match a different node (the search field's text vs. the result row both say `Bochum`).
    Tap the bounds of the line you already selected.
11. **A swipe starting near the top of the screen.** `input swipe 540 63 540 2100` opens the
    **notification shade** (`dumpsys window | grep mCurrentFocus` → `NotificationShade`); afterwards
    back/dismiss appear dead while everything actually works fine underneath. Check `mCurrentFocus`
    before concluding the app ignores input.
12. **Blaming the app for an ANR while the host is loaded.** One device run ANR'd with
    `Reason: Input dispatching timed out … Waited 5001ms for FocusEvent(hasFocus=true)` and the ANR
    report's CPU block showing the app at 109 % — with the *host* at **load average 19.85 on 16 cores**.
    Check `uptime` first; a software-rendered emulator starves and then gets blamed for it.

## References

- `.pi/skills/build-app/SKILL.md` — building; `.pi/skills/run-tests/SKILL.md` — the JVM suites
- `AGENTS.md` — JNI stub + Robolectric classloader rule (why a wide-window host test cannot be a
  device substitute), build/test commands
- `guidelines/Build.md` §10 — on-device recipe (logcat, diagnostics viewer)
- `TODO.md` §120 — the phone AVD with map data used for the stylesheet attribution; §121 — navigation
  test flakiness under full-suite load (not device related)
- `ki_processing_failures.log` — the 2026-10-03 `route-planning-session` entries behind the pitfall list
- Evidence of the two data losses and the `OK (3 tests)` skip: `openspec/changes/route-planning-session/tasks.md`
  items 10.2/10.3
