# Device evidence — phone search lifecycle (tasks 5.3, 5.5 phone half)

Device: `emulator-5554`, Pixel_8 AVD, SDK 37, x86_64, app `com.framstag.naviveylin` (mobile debug, code 19),
German UI. Installed maps: `andorra`, `basemap` — **no network on the emulator**, so no larger database could be
fetched for this run. Native library: the APK installed by `./gradlew :app:installMobileDebug
-Pandroid.injected.build.abi=x86_64`, i.e. it carries this change's JNI code — which the log line below proves.

## The search line is present, and correct (task 5.3, 5.6: the diagnosable half)

A typing burst in the phone search dialog produced one line per request, generations advancing per request:

```
I NaviVeylin: search: slot=places gen=1 aborted=false nativeMs=114
D MapCanvasVM: searchLocations: query='an', adminRegionHandle=1, candidates=60, limit=60, nativeMs=167
I NaviVeylin: search: slot=places gen=2 aborted=false nativeMs=60
I NaviVeylin: search: slot=places gen=3 aborted=false nativeMs=27
… gen=4 … gen=13, aborted=false, nativeMs 6–52
```

- `slot=places` on every line, `gen` monotonic per request (1 → 13 across the run) — the generation the app
  begins per request reaches the native side.
- No coordinate, no query text: slot, generation, abort state and duration only.
- Two further typing rounds with the sleeps *inside* the device shell (gaps of 320–400 ms, i.e. just past the
  300 ms debounce) produced generations 7–13, all `aborted=false`.

## Why `aborted=true` could not be produced on this install (5.3's second half, 5.6)

A supersede can only abort a search that is *still running*, and the window is
`gap > 300 ms` (the debounce) **and** `gap < 300 ms + search duration`. Measured on this device:

| round | query | nativeMs |
|---|---|---|
| cold | `an` | 114 (app-side line: 167) |
| warm | `and` … `andorra` | 17–60 |
| warm, second pass | `an` … | 6–20 |

So the window is 10–120 ms wide and `adb input text` jitter exceeds it: every search finished before the next
keystroke. Nothing was aborted because nothing needed to be — which is itself the measurement that makes the
design's point: the abort matters on a database whose search outlasts a typing gap, and the recorded numbers for
NRW (993 / 1606 / 4310 ms) are 10–400× this. **Tasks 5.3 (the `aborted=true` half) and 5.6 stay open with that
reason**: they need a slow database, which this emulator does not carry and cannot fetch offline. The abort
*predicate* remains pinned on the host by `SearchBreakerTest` (29 assertions / 8 cases).

## One character runs nothing (task 5.5, phone half)

With the field holding one character (`b`):

- `adb logcat` after clearing it: **0** `search: slot=` lines — the native search never ran.
- The results area holds no result row. `uiautomator` dump of the dialog at that moment lists exactly
  `text="b"`, `text="Orte"`, `text="POIs"`, `text="Suche in Andorra la Vella"` — the field text, the mode
  switch and the scope label, and **no result row** (the previous query's results were gone).
- Control in the same run: typing `an` (two characters) does produce a search line, so the zero above is the
  minimum length and not a broken input path.

The **car half of 5.5** needs a car host (AAOS head unit); a phone emulator has none, so the task stays open and
its car half is device-gated.

## Method notes (so the next run does not repeat my three dead ends)

1. `adb devices` first: this emulator was attached the whole time, and my earlier "no device attached" claim was
   simply wrong.
2. The launcher activity is `com.naviveylin.MainActivity`; `am start -n com.framstag.naviveylin/.MainActivity`
   silently does nothing.
3. On the first launch an in-app rationale dialog (**"Adressbuch-Zugriff"**, buttons `Später` / `Weiter`) covers
   the map screen; tapping `Später` (620, 1489) dismisses it. Until then every key and tap goes nowhere.
4. The dialog's search field is **not auto-focused in this build**: after opening the dialog the dump showed
   `text="Ort suchen..."` with `mInputShown=false`, and `input text` silently went nowhere until the field was
   tapped. That is a *separate* observation, not this change's: spec `location-search` requires "Auto-focused
   search input", so it is a candidate for its own check (§ its own requirement, and a phone emulator is enough
   to reproduce it).
5. Reading the accessibility tree without naming a device path (the shell policy rejects absolute device paths):
   `adb shell 'f=$(uiautomator dump 2>&1 | tail -1 | cut -d" " -f5); cat "$f"'`.
6. Loading the *guest* to widen the abort window was tried and abandoned: 6 busy loops (`(while true; do :; done) &`)
   pushed the load average to 6.2 and stalled `adb` itself (one command hit the 300 s limit, the hogs had to be
   killed one by one, and the loops survive `killall sh`). A later run that wants `aborted=true` on a small
   database should use **two** loops at most, with `timeout` on every `adb` call, or — better — a database whose
   search is genuinely slow.
