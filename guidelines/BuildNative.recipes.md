# Build Native — recipes

Device recipes for `guidelines/BuildNative.md`. Read beside the section it anchors to.

## §10a Booting the AAOS AVD and driving the car UI

**Boot the AAOS AVD and install the automotive build.** The AVD's ABI is x86_64, so an
arm64-only APK will not install:

```bash
/home/tim/Android/Sdk/emulator/emulator -avd Automotive_Distant_Display_with_Google_Play \
    -no-snapshot-load -no-boot-anim &
./gradlew :app:assembleAutomotiveDebug -Pandroid.injected.build.abi=x86_64
adb -s emulator-5556 install -r -t app/build/intermediates/apk/automotive/debug/app-automotive-debug.apk
adb -s emulator-5556 shell am start -n com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity
```

The car UI itself is not reachable through `uiautomator` (the templates are drawn into a
`SurfaceView`, so a dump returns the harness with empty texts) — but it **is** visible on the AVD's
default display and **is** tappable, which is how the favourites device checks were run (`car-favorites-group-level`
§1.1/§6.1, `fix-car-favorites-loading-stall` §5.1). Drive it by OCR + coordinate, and confirm every tap
from the app's own diagnostics tags:

```bash
adb -s emulator-5554 shell am force-stop com.framstag.naviveylin
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell am start -n com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity
sleep 14
d=$(date -u +%H%M%S); adb -s emulator-5554 exec-out screencap -p > .pi/logs/car-$d/root.png
sh .pi/logs/ocr.sh .pi/logs/car-$d/root.png deu     # "text [x=… y=… w=… h=… conf=…]" per line
adb -s emulator-5554 shell input tap <x+w/2> <y+h/2>   # centre of the OCR'd row
adb -s emulator-5554 logcat -d | grep -E 'Diag/(SCREEN|HOST|SESSION)'
```

Three things this session got wrong once each, and they are the whole cost of the method:

- **A live session is not the same as a connected host.** `Diag/WARMUP` lines appear while the native
  client builds, but the host binds to `NaviVeylinCarAppService` only then: if `logcat` shows no
  `Diag/SESSION:` line and no `Diag/HOST:` line, the session never started a screen — restart the
  activity (`am force-stop` first) and check again **before** interpreting a dead tap as "input cannot
  reach the app".
- **`dumpsys display` shows the app's own `CarAppService` virtual display as `touch NONE`** — that is the
  surface the app *creates*; the templates the host renders are on the emulator's physical display 0,
  where `input tap` works. Do not conclude from that line that the UI is unreachable.
- **The tap lands on whatever screen is on top.** The session's root is `RootScreen` (a list: `Freie Fahrt`,
  `Markierte Favoriten`, `Alle Favoriten`, …), but a restored free-driving or map screen can be in front,
  and then the same coordinate opens nothing — the OCR of the screenshot is what tells the two apart
  (place names = the map).
- **System BACK does not reach the car app on this AVD; the screen's own back action does.** Measured
  2026-10-30 while checking the route description (`share-route-rows-in-core`): `input keyevent KEYCODE_BACK`
  and `DPAD_UP`+`ENTER` left the screen in place with no new host template, while a tap on the header's back
  chevron (measured at `x 32..53, y 82..103` on a 1080x600 pane whose header reads `< Route`) popped it — the
  host answered with a `NavigationTemplate` and the app re-adopted its surface. Use the header action as the
  back affordance in a recipe, and read the pop from the host's template type, not from the screenshot.

The car UI's own tags are the readable half of every check: `Diag/SCREEN` (what a screen observed, incl. the
list capacity it read), `Diag/HOST` (what the app sent the host), `Diag/SESSION` (session steps) and the
plain `RootScreen`/`FavoritesScreen` `Log.d` lines from the click actions.

A deep link drives navigation without needing the UI at all (`geo:` starts navigation, `KEYCODE_HOME`
backgrounds the app):

```bash
adb -s emulator-5556 shell am start -n com.framstag.naviveylin/com.naviveylin.DeepLinkActivity \
    -a android.intent.action.VIEW -d "geo:51.5142,7.4653"
adb -s emulator-5556 shell input keyevent KEYCODE_HOME       # background the car app
```

Start the bare URI form without `-n` only if you want the system chooser; with several
`geo:` handlers it opens the resolver dialog instead of the app.

**Four device prerequisites that each cost a round** (measured 2026-10-30, `fix-car-rail-widget-tap`
tasks 4.2/4.3, AAOS `emulator-5556` and phone `emulator-5554`):

- **The notification permission is granted per user.** A plain `pm grant com.framstag.naviveylin
  android.permission.POST_NOTIFICATIONS` grants user 0 while the driving user on the automotive AVD is
  10; the app then enqueues and is refused — `AggregatedStats key='com.framstag.naviveylin'
  numEnqueuedByApp=9 numPostedByApp=0 numBlocked=9` with `AppSettings … importance=NONE` — and no
  `NotificationRecord` exists at all, which reads like "the app never posts". Use `pm grant --user 10 …`
  and require a `NotificationRecord(… id=1002 …)` before judging how a notification is displayed.
- **The deep link is single-shot.** `am start -n …/DeepLinkActivity` works once; afterwards am answers
  `Warning: Activity not started, intent has been delivered to currently running top-most instance` and
  nothing reaches the app, because `DeepLinkActivity` has no `onNewIntent`. `am force-stop` first, then
  either that activity or `am start -n …/MainActivity -a android.intent.action.VIEW -d geo:…` (which the
  car build forwards) — it appears as `Diag/SESSION: onNewIntent … scheme=geo` and starts navigation.
- **`logcat -G` is unsupported on these images and `CarPropertyService` writes ~5 lines/s**, so the
  `Diag/*` lines carrying the evidence are gone within a minute. Read them within seconds of the action,
  never after a long wait.
- **On the phone UI a `geo:` link does not start navigation** — it queues a candidate
  (`MainActivity: Shared location parsed: shape=coordinates`); there navigation starts through the UI
  (search → POI sheet → `Calculate route` → `Start Navigation`). And `uiautomator dump` answers
  `ERROR: could not get idle state` while the shade animates and then leaves the **previous** file in
  place, so a stale hierarchy reads as the current state: use a unique dump path per call and require the
  `dumped to` line.

## §10b Judging the car surface and the render loop

**Judge the car surface and the render loop.** `lockCanvas` succeeds only for a live
surface, and the renderer logs it, so counting those lines is the cheapest "is the map
drawing" test:

```bash
adb -s emulator-5556 logcat -c
# ... run the scenario ...
L=$(adb -s emulator-5556 logcat -d | grep -E 'AutoMapRenderer|CarSurfaceHost')
echo "lock OK:   $(echo "$L" | grep -c 'lock OK')"          # frames actually drawn
echo "created:   $(echo "$L" | grep -c 'surface created')"  # surface adoptions
echo "releases:  $(echo "$L" | grep -c 'releasing session surface')"
echo "failures:  $(echo "$L" | grep -c 'surface invalid\|lockCanvas failed')"
echo "drops:     $(echo "$L" | grep -c 'dropping frame')"   # stale frames dropped
echo "detached:  $(echo "$L" | grep -c 'detaching surface')" # screen stop released its surface + buffer
echo "re-deliver: $(echo "$L" | grep -c 're-delivered')"     # a released instance came back
```

Expected after a push/pop or a background round trip: a `surface created` for the incoming
screen **with the same surface id** the outgoing one had, **no** `releasing session surface`
in between, and `lock OK` continuing. A `detaching surface` for the outgoing screen should
precede the incoming `surface created` (the session revokes the superseded owner at `attach`)
and appear once per screen stop — a stop **without** it means the screen's `onStop` did not
detach, so that renderer still holds the surface and its overrun buffer. Any `surface
invalid`/`lockCanvas failed` (or an
`invalidate … attempt` line from a screen's surface-refresh recovery) is a defect, not noise.

`re-delivered` means the host handed back a `Surface` instance this session had already
released (change `fix-car-surface-ownership-and-host-callbacks` tracks lifetimes per delivery,
so the instance is adopted and released again). It is expected only when the host really
re-delivers; a run that shows it repeatedly is worth keeping, because it is the case the
per-instance bookkeeping used to leak the buffer-queue producer on.

**Host-side failure.** The AVD's logcat covers `system_server`, the templates host and
`systemui`, so the host's stack trace is retrievable even though the app has none:

```bash
adb -s emulator-5556 logcat -b crash -d
adb -s emulator-5556 shell dumpsys dropbox --print | grep -B5 -A40 -iE 'templates.host|system_server|systemui'
adb -s emulator-5556 logcat -d | grep -iE 'lmkd|lowmemorykiller|Killing'   # host killed by pressure?
adb -s emulator-5556 shell dumpsys meminfo | head -40
adb -s emulator-5556 shell dumpsys cpuinfo
```

## §10c Footprint, the app's host sends and route numbers

**Measuring the app's own footprint — the rule.** Every footprint counter this repo reads
(`Graphics`, `EGL mtrack`, `GL mtrack`, native heap, `Bitmap (malloced)`, `TOTAL PSS`) is a **high-water
mark inside a process**: measured 2026-09-27, `GL mtrack` went 80.6 MB → 130.6 MB across three zoom
gestures and was still 130.6 MB 25 s later, and the native heap went 115 MB → 337 MB across one scripted
map walk (retaining 324 MB after returning to the start viewport). So each "before" and each "after"
needs its **own freshly started process**, and two numbers are only comparable at the same gesture/walk
script and the same render count. **Quote the UI state with every number** — whether the phone canvas was
visible (map or car-session surface), whether a car session was live, and whether the sample was taken idle or
mid-gesture — because the same device state measured a `Graphics` 31.8 MB for the car-session surface and
100.2 MB for the map canvas in one process minutes apart, and a "before" number whose UI state is undocumented
cannot be compared with an "after" one (that is what left `reduce-render-peak-memory` task 5.3 without a valid
comparison against its own recorded baseline). A walk script that needs no car and no driving:
`adb shell input keyevent 69` (zoom out) ×10, then `adb shell input swipe` pan pairs — the app must be in
the foreground (`topResumedActivity`, not `mCurrentFocus`).

**App-side host sends.** The app records what it sent the host with the diagnostics tag
`HOST` (notification posts, trip updates, host navigation-state calls, surface
adopt/release) and the native client build with the thread that ran it (tag `WARMUP`). The
logcat route works on every device and is the primary one:

```bash
adb -s emulator-5556 logcat -d | grep -E 'Diag/HOST|Diag/WARMUP'
```

The phone's route analysis records its per-step values under `ROUTE` — what the step rows add up to
against the route's own total distance, numbers only (spec: `route-analysis` — Step values describe the
step's own leg). It is the number that tells a leg from a geometry edge, so read it next to the row text:

```bash
adb logcat -d | grep 'Diag/ROUTE route analysis'
```

The instrumented route check logs the same relationship with the per-step extremes, under its own tag:

```bash
adb logcat -d | grep 'RouteDeviceTest: per-step values'
```

## §10d Rejected mutations, the error notice and database registration

**Rejected host mutations and confined faults.** Every screen-stack mutation (push, pop,
popToRoot, remove) runs through the guarded seam, and every car-facing scope carries a fault
handler (change `fix-car-host-mutation-guards`), so a mutation the host refused and a fault
inside a session/screen/renderer coroutine are both recorded instead of killing the process:

```bash
G=$(adb -s emulator-5556 logcat -d | grep -E 'Diag/(HOST|SESSION|SCREEN)')
echo "rejected:  $(echo "$G" | grep -c 'rejected')"     # expected 0 in a healthy run
echo "confined:  $(echo "$G" | grep -c 'failed: ')"     # expected 0 in a healthy run
```

A rejection names the mutation (`push FavoritesScreen rejected: …`), so the screen the driver
tapped is identifiable; a confinement names the coroutine (`session work 'trip' failed`,
`screen work '…' failed`, `observation 'gps' failed`) plus its first stack frame. Both are
**defects to chase**, not noise: either the driver saw no navigation, or a screen stopped
updating. Correlate them with the app crash buffer — a guarded rejection must never be
followed by `Diag/CRASH`.

**The error-notice path.** An error raised while navigating must leave the navigation view on
the stack (it used to be popped with the notice). Navigate, trigger the notice (a route to an
unreachable destination, or the existing error path), let it clear: the navigation template and
its live guidance must still be shown, with `Diag/HOST` naming the notice push and its removal
and no rejection.

Map registration on a startup is readable in the same stream (change
`fix-native-database-open-race`): one `openDatabases -> N/M registered` line per batch call,
then one `openDatabase(<directory>) -> <true|false>` line per directory. A **per-directory**
run of `openDatabases -> 1/1 registered` lines instead of one line with the whole set means a
caller still registers one by one - each of those calls closes and reopens every open database
and stalls the render path:

The same lines are mirrored to the file-backed diagnostics log, readable without a
debugger (verified on the phone AVD). On the automotive AVD `run-as` resolves in **user 0**
while the app process runs in **user 10**, so `files/diagnostics/` looks missing although it is
written under `/data/user/10/com.framstag.naviveylin/files/` (measured 2026-09-21 on
`emulator-5556`; on a userdebug image `adb root && ls /data/user/10/com.framstag.naviveylin/files/diagnostics/`
shows it). Use the logcat route below as the primary one there:

```bash
adb -s emulator-5554 shell run-as com.framstag.naviveylin cat files/diagnostics/app.log | tail -50
```

**Timing of the file route (change `fix-diagnostics-log-host-path-io`).** A line is buffered in
memory and written by the log's worker thread, so it reaches `app.log` within the flush bound
(250 ms) rather than instantly; the logcat line is immediate. For the last moments before a crash,
read logcat, and treat a file tail that ends a fraction of a second earlier as normal. A crash
trace is the exception — the uncaught-exception handler writes it directly, so it is on disk before
the process dies.

Correlate: the last `HOST …` line before the host's death names the sender (a `NOTIF post`
that repeats every second, a `trip update` burst, a surface release at a moment the host
still owned the buffer). The car's own `DiagnosticsScreen` shows the same tail on screen.

**Bisect by disabling one sender per run** (routing active, app backgrounded, wait): skip
the `CarAppExtender`, no-op the trip publishing, gate the session observers. Whichever run
keeps the host up names the sender — or rules the app out and points at the host itself.

**Pitfall — never install over a live session.** Replacing the APK of a car app the host is
bound to force-stops the app, invalidates the host's `CarHost`, and the host's queued
template application then dies with `IllegalStateException: Accessed the car host after it
became invalidated` (`com.google.android.apps.automotive.templates.host:renderer_service`,
observed 2026-09-21 on `emulator-5556`). The crash is host-side and needs no app code to run,
so an install mid-scenario contaminates every measurement in it: install first, then start
the session, and treat a host crash whose log shows `onPackageUpdateFinished`/`replacing=true`
for the app package as a harness artifact, not an app defect.

## §10e Expectation baselines and the car search scope

**Expectation baseline for a healthy run** (stationary vehicle, navigation active, ~75 s,
browse -> navigate -> HOME -> return): a handful of `lock OK` (one per full render), one
`surface adopt` per delivery and one `surface release` per host-driven teardown, **0**
`surface invalid`/`lockCanvas failed`, and - with the change `fix-aaos-host-crash` in -
**one** `Diag/HOST NOTIF post` and **one** `Diag/HOST trip update` for a session whose
displayed content never changed (before it: one of each per second).

**Expectation baseline for a session restart while free driving is active** (with the change
`fix-host-crash-residual-paths` in): exactly **one** `Diag/SESSION Push FreeDrivingScreen
(restore)` per session, and one `BACK`/`Exit` returns to the map root - a second restore push
means two free-driving screens and two native renderers (before the change, the first screen
creation and the warmup completion each pushed one).

**Car search scoped by the driver's region** (change `fix-car-search-default-admin-region`,
spec `auto-search`). The car search is scoped with the admin region containing the car's
position, exactly as the phone search panel is, so a query naming a POI without its city
resolves. The car adapter logs its own scope decision under the `CarSearchRegion` tag
(`android.util.Log`, not the `Diag/` stream), with the handle and the region name and
**never** a position:

```bash
adb -s emulator-5556 logcat -c
adb -s emulator-5556 shell am start -n com.framstag.naviveylin/androidx.car.app.activity.CarAppActivity
# car UI -> search, then type a POI name WITHOUT its city (e.g. "Hilpert Theater")
adb -s emulator-5556 logcat -d | grep -E 'CarSearchRegion'
```

Expected with a usable fix: one `resolveAdminRegion -> handle=<N>` (`N != 0`) followed by
`search scope RESOLVED: handle=<N> region=<name>`, then `search scope REUSED: handle=<N>
region=<name>` for further queries while the car stays within the movement threshold (one
line per query; a second `resolveAdminRegion` line there would mean the threshold is not
holding). With no usable fix (location off, or before the
first fix) the same query logs `search scope FIX_UNUSABLE: handle=0 region=null` and the
search runs unconstrained, exactly as before the change; a coarse fix logs the same. After a
drive beyond ~500 m the next query logs `RESOLVED` with a new handle and the previous one
appears in no released-handle error line.

**Data blocker for this check** (TODO.md §91): the installed map sets on the AVDs do not
carry every POI type the stylesheets declare, so an empty result list for a POI-only query does
**not** by itself mean the scoping failed — read the `CarSearchRegion` line for that, and use a
street/address query when a result is needed. Those missing types appear as **one line per parsed
style file** (change `condense-style-load-warnings`), not as one line per rule occurrence:

```bash
adb logcat -s NaviVeylin | grep "Unknown types in"
# W NaviVeylin: Unknown types in '…/stylesheets/standard.oss': 42 (amenity_theatre, …, 34 more)
```

One line per style file that references a type the installed database's type configuration lacks
— the top-level stylesheet and each `MODULE` include; a file whose names all resolve logs nothing,
and a healthy install logs no such line at all. Count them with
`adb logcat -d | grep -c "Unknown types in"`; thousands of lines here mean the map data predates
the stylesheet, not a defect. **The same file appears once per load**, and a startup loads the
stylesheet set more than once (measured 2026-10-02 on the phone AVD with stale data: 8 files × 13
loads = 104 lines per startup, `Created new style` 12×), so read the count per file and not the raw
total; the repeated loads themselves are `TODO.md` §120. The full per-name list is obtainable
programmatically and, with `osmscout::log.Debug(true)`, as one additional debug line; the
per-reference findings are kept (one per occurrence, with its position), so the condensation loses
no evidence.
Phone parity is checked with the same query and a fix on the phone
(`resolveAdminRegion -> handle=…` in `adb logcat -s MapCanvasVM`): both surfaces must
show the same matches for the same position.

## §10g Start-map selection on the phone (`fix-start-map-selection`)

The phone opens the map resolved by `StartMapResolver`: the last opened database, else the
lexicographically smallest installed one, else the entry point that leads to the map manager
(spec: `start-map-selection`). It never opens the basemap overlay. Two installed regional maps are
enough to exercise it (the map manager's *Download Maps* screen shows what is installed):

```bash
L='com.naviveylin.ui.map.MapCanvasViewModel'
# 1. open map B in the map manager, then restart the app cold
a=$(adb logcat -d -s "$L" | grep -c 'initMap: initialising with path=')  # per-open lines
adb shell am force-stop com.framstag.naviveylin && adb shell am start -n com.framstag.naviveylin/.MainActivity
adb logcat -d -s "$L" | grep 'initMap: initialising with path=' | tail -1   # expect map B
adb logcat -d -s StartMapResolver | grep 'start map resolved'               # chosen + recorded
# 2. delete map B in the map manager, restart again
#    expect: the same remaining database on every restart, and no
adb logcat -d | grep -c 'Could not open map database'                        # must stay 0
# 3. the basemap overlay is never the primary map
adb logcat -d -s NaviVeylin | grep -c "Cannot open db '.*/maps/basemap"      # must stay 0
```

`StartMapResolver` logs the chosen path and the recorded one (identity only, no coordinates), so a
start that picked the wrong database is visible without a debug build. The car side needs no change
(its session registers every installed database except the basemap): a car session started after a
phone start must still report the same database count in the `WARMUP` line.

## §10h Stylesheet load count per start (`dedupe-stylesheet-loads`)

The app applies the stylesheet/flag pair once per (style name, flag set) — see
`guidelines/MapStyling.md` §15 for the key and the legitimate triggers. Both the phone and the car
report every load through the shared native logger, so a start can be counted without a debug build
(the `NaviVeylin` tag carries `osmscout::log` output):

```bash
adb shell am force-stop com.framstag.naviveylin
adb logcat -c
adb shell am start -n com.framstag.naviveylin/.MainActivity
sleep 20
# one native load of the whole set; the basemap's own stylesheet adds one more on its open
adb logcat -d -s NaviVeylin | grep -c 'Created new style with'
# the condensed unresolved-type report: one line per parsed style file per load
adb logcat -d -s NaviVeylin | grep -c "Unknown types in '"
# which style/flag applications happened, in order
adb logcat -d -s NaviVeylin | grep -E 'Created new style with|ensureMapStyle'
```

Expected on the phone: **1-2** `Created new style with` lines per start (the main style plus the
basemap's stylesheet) and **8** report lines (8 files × 1 load) against the 12 loads / 104 lines the
2026-10-02 baseline measured (`TODO.md` §120). Both numbers move with a legitimate trigger: a style
switch, a real day/night change, a map database open (each `initMap`, i.e. every map re-entry, is one),
an app update that refreshed the bundled stylesheets, and a basemap download
(`reloadBasemap`). Count per start and compare per source rather than trusting one number —
and remember `TODO.md` §17: a gradle or logcat verdict must be an execution, not a cache hit.

## §12d Rebuilding vcpkg packages

```bash
# Force rebuild of specific package
rm -rf vcpkg/buildtrees/<package>
./setup-vcpkg.sh
```

## §10i Device recipes that cost a round every time (2026-10-05)

- **Device paths and dumps.** The harness refuses absolute *device* paths in a command (`/sdcard/…`,
  `/data/local/tmp/…` — `external_directory_read`): keep paths relative inside `adb shell`
  (`adb shell 'cd /data/local/tmp && uiautomator dump map.xml'`), read the default dump through the device's
  own variable (`adb shell "cat \$EXTERNAL_STORAGE/window_dump.xml"`), or build the path from an octal-escaped
  separator (`S=$(printf '\57'); DUMP="${S}sdcard${S}ui.xml"`). A dump is usable only when it is provably
  **fresh**: require `dumped to` in the output, use a unique literal path per call, retry a few times — a
  *failed* dump (`ERROR: could not get idle state.`) leaves the previous file behind, and every later tap
  then aims at the old screen (this cost two contaminated runs in one session).
- **Tap the node, not its text.** Resolve a node once from one dump and tap *that* node's bounds, class
  filtered (a result row is a `TextView`, the search field an `EditText`) — re-grepping the text can match the
  search field and tap (0,0). Read `clickable="true"` (a node carrying the `content-desc` is not necessarily
  the touch target) and check `mCurrentFocus` before concluding the app ignored input. The top strip
  (y ≈ 63) belongs to the notification-shade gesture, not to the app.
- **Verify the install, not the script that installed it.** `dumpsys package <pkg> | grep lastUpdateTime`
  must match the build's timestamp — a timed-out `adb install` leaves the previous build running and the next
  smoke run silently exercises it. For a native change, probe the packaged entry that **exists** (the debug
  library is `libosmscout_client_javad.so`) and grep a *calibrated* control literal from the same file: a
  surprising probe result is a candidate verification bug before it is a product defect (grep a known
  literal first to find the library that owns the code).
- **Host load explains ANR-shaped evidence.** Read `uptime`/`ps` before blaming the app — `./gradlew --stop`
  stops the daemon but not the native `cc1plus` children it already spawned; ANRs with the app at 63-109 %
  CPU arrived at host load 19-27 and vanished once the build finished. Check `isKeyguardShowing`/
  `mDreamingLockscreen` before trusting any UI-driven step (a secure lockscreen returns empty dumps, a
  `Graphics` reading of ~10 MB, and silently redirects taps; `run-as` does not work on a Play-signed build,
  so logcat is the only stream). Keep `logcat -G 16M` for a long pass — the default buffer rotates records
  away before they are counted.
- **A long-press drag on device is a motionevent sequence.** `input draganddrop x1 y1 x2 y2 900` and
  `input swipe` never start a reorder library's drag (its own long-press timeout never elapses):
  `adb shell input motionevent DOWN x y`, `sleep 1`, a loop of `input motionevent MOVE x' y'` at ~120 ms per
  step, then `input motionevent UP x' y'` — consecutive invocations share pointer 0, so the library sees one
  long press followed by a drag.
- **Multi-user and a11y-less car surfaces.** The car session may live in user 10 while `run-as` reaches
  user 0 (`/data/user/<id>/…` in the app's own log lines; `run-as --user` is rejected on the API 33 toybox;
  `adb root` fails on a production image): build the state through the car's **own UI** instead of seeding a
  fixture, or use a userdebug/eng image (`TODO.md` §106). The car surface exposes no accessibility nodes, so
  read text and positions from `adb exec-out screencap -p` + `tesseract <png> out tsv`, and expect the
  distant-display mirror to re-assert `CarAppActivity` within seconds — the phone UI is not a stand-in
  (`am start --user 10` on an external display is refused with a `SecurityException`). A force-stop with a
  live car session crashes the *host's* renderer process, so it is not a neutral restart on that AVD.
- **Record a partial pass as partial.** A disappeared AVD (`offline` → gone, no crash trace), an unreachable
  state or a stale frame goes into the change's tasks with the numbers actually collected, never implied as
  proof.

- **Never install an ABI-filtered APK for an on-device run** (§40.39): packaging strips the other ABIs, the
  install succeeds and the app dies at `System.loadLibrary`. Verify with
  `unzip -l <apk> | grep -o 'lib/[a-z0-9_-]*/'` (the character class needs the underscore) and check the APK
  mtime against the newest source edit.
- **An emulator cannot inject a bearing** (§40.40): `geo fix` has no bearing argument (always `bear=0.0`) and
  NMEA RMC is dropped by `FusedLocationProviderClient` ("too close / too fast"). Cover heading/bearing
  behaviour with unit tests, never with a GMS-emulator run.
- **A follow-mode reproduction needs no real drive — `geo fix` injects a SPEED, in KNOTS** (measured
  2026-10-12 on `emulator-5554` while reproducing `fix-follow-display-pump`):
  `adb emu geo fix <lon> <lat> <alt> <sat> <velocity>` reads `velocity` as knots (13.9 → `vel=7.1508`
  m/s, 27.0 → `vel=13.889988` m/s = 50.0 km/h), and Fused passes it straight to the app, so the follow
  display loop runs at a chosen speed from nothing but `geo fix` at 1 Hz. Two constraints make it a
  *controlled* rather than a faithful stimulus: the bearing stays 0 (only a due-north track is truthful)
  and the positions are noise-free (`hAcc=5.0`), so any GPS jitter to be tested must be injected
  deliberately on the track. The full recipe — injector, screenrecord, and the per-frame shift
  measurement that turns the video into a rate curve — is in change `fix-follow-display-pump`'s archived
  `tasks.md` (tasks 3.4, 5.2, 5.3).
- **A headless emulator needs `-dns-server 8.8.8.8`** (§40.41): broken DNS answers "Unable to resolve host"
  while a raw IP works — compare `adb shell ping` against the app's network code before debugging the app.
- **Check which maps are already installed before attempting a catalog download** (§40.42) — `adb install -r`
  preserves the app's data, so the maps survive a reinstall (§95).
- **After toggling `location_mode`, re-send `geo fix`** (§40.43) — Fused may need provider re-registration.
- **`adb shell input text` goes through the active IME** (§40.44): GBoard rewrote `Erbstollenstrasse` →
  `Er Stollenstraße`. Disable the IME for a replay and assert the field's actual value from a fresh dump.
- **The AAOS/car AVD is not usable for headless on-device steps** (§40.45): car system-UI ANRs swallow
  `input tap`, `uiautomator dump` returns an empty hierarchy, `geo fix` is answered OK but no fix reaches the
  app, and the host `RendererService` disconnects ~40 s after launch. Plan car verification for an
  interactive window or a real head unit, and state the blocker instead of burning a session.

