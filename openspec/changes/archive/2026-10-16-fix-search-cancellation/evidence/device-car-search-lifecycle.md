# Device evidence — the car search's own lifecycle (tasks 5.4, 5.5 car half, 5.6 first item)

Device: `emulator-5556` (AAOS distant-display AVD, SDK 33), app `com.framstag.naviveylin` automotive debug,
**my build**, verified by extracting its dex before installing (`searchGate` in classes8/13/19) and installed
into the car's user at 17:40:32. Map in play: `nordrhein-westfalen` (searches 1.5–4.5 s — the slow database this
check needs). Driver: the operator, by mouse and keyboard on the AVD window; I read `adb logcat`.

## The lines

```
session created 17:41:34 (my build)     Pop to RootScreen 17:41:35
17:41:55.209  I NaviVeylin: search: slot=places gen=1 aborted=true nativeMs=1535    <- abandoned car search
17:41:55.274  D SearchScreen: Search results: 0 for 'Bochum'                          <- fail-closed: nothing published
17:41:58-17:42:01  Diag/MAP: render mag=13.00 …          (the operator returned to the map)
17:42:09.237  D Diag/HOST: surface release 210730206     (the session ended: a normal release, not a fault)
no further "SCREEN"/"HOST" fault line after the pop
```

and, in the same session, exactly one search line: the control above. The one-character pass left **no**
`search:` line at all.

## What each line proves

| task | claim | evidence |
|---|---|---|
| **5.4** | the car search screen stops its own search when it leaves its started period | the abandoned search's line carries `aborted=true` with a 1535 ms walk (i.e. it was aborted, not completed: NRW would have taken ≈1.5–4.5 s and the abort landed inside it), the screen then published nothing (`Search results: 0`), and no host-facing fault followed the pop — only the session's normal `surface release` |
| **5.4** | the *gate's* abandon is what stopped it | this run is my build (dex verified) and `SearchScreen`'s gate is the only owner of that request: its `onDestroy` calls `searchGate.abandon()` → `cancelSearch(generation)` → the walk aborts. The pre-gate code path could only be aborted by a *successor* (the earlier 17:19/17:30 runs, which is what the transitional wrapper did), and here no successor followed |
| **5.5 car half** | a one-character query runs nothing on the car either | the same session's control (a two-character query) produced a line, and the one-character pass produced none |
| **5.6 (first item)** | an aborted search returns no entries rather than a partial list | `Search results: 0 for 'Bochum'` immediately after the aborted walk, while the same query had returned 20 rows when it completed (17:19:36) |

## The defect these runs exposed, and its fix

The line's `slot=` field was **wrong**: `DoSearchLocations` serves every surface (phone places, route, car) and
the line hardcoded `kSearchSlotPlaces`, so a car search read `slot=places` and I initially misread it as an
older build — including three unnecessary reinstalls before I checked the source. The registry now reports the
request's own slot (`SearchBreakerRegistry::SlotOf`, read before `End`, which forgets it), both call sites use
it, and the host case `the registry reports a live request's own slot` pins it (`34 assertions in 9 test
cases`). A device line reading `slot=car` is the confirming check; it needs the rebuilt APK, and is optional
because no task's wording depends on the label.

## Still open from this area

- **5.6's remaining two items** (the form pass stopping before the next database, the free-text pass not running
  on the next database) need a *multi-database* install; this device searches one database plus a skipped
  basemap, so the database boundary they describe does not exist here. Two downloaded maps on the phone AVD, or
  a second map on the car AVD, would provide it.
- **5.3's phone half** (the same abort, typed in the *phone* search dialog): the phone AVD has only Andorra
  (6–20 ms warm), so the abort window is ~10 ms; the mobile AVD needs a slow map, which its own guest network
  can fetch.
