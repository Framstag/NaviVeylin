# Device evidence — the phone search's own lifecycle (tasks 5.3 and 5.6)

Device: `emulator-5554` (Pixel_8 AVD, SDK 37, x86_64), app `com.framstag.naviveylin` mobile debug. Maps: `andorra`,
`basemap` **and `north-rhine-westphalia` (784 MB, downloaded through the app's own map manager from karry.cz)** —
i.e. **two searchable databases plus the skipped basemap**, which is also what task 5.6 needs. Driver: this session
by `input` injection on the phone UI, whose geometry is known (search button (85,338), field (540,226)).

## The lines (one typing train, gaps of 100–300 ms between keystrokes)

```
18:00:31.540 I NaviVeylin: search: slot=places gen=14 aborted=true  nativeMs=346   query 'and'      candidates=0
18:00:31.889 I NaviVeylin: search: slot=places gen=15 aborted=false nativeMs=389   query 'ando'     candidates=42
18:00:32.699 I NaviVeylin: search: slot=places gen=16 aborted=true  nativeMs=509   query 'andor'    candidates=0
18:00:33.498 I NaviVeylin: search: slot=places gen=17 aborted=true  nativeMs=805   query 'andorr'   candidates=0
18:00:33.965 I NaviVeylin: search: slot=places gen=18 aborted=false nativeMs=539   query 'andorra'  candidates=39
```

(Each line is followed by the app's own `MapCanvasVM: searchLocations: … candidates=N … nativeMs=M` line for the same
request, which is where the candidate counts come from.)

## Why the gap had to be that small (method)

Search A begins **300 ms after its last keystroke** (the debounce) and runs for `D_A`; a later keystroke only
supersedes it when *its own* debounce lands inside A's run, i.e. `gap ∈ (0, D_A)`. On this data `D_A` is
83–805 ms, so a one-second gap always missed (measured: 19 ms warm with Andorra alone; 145–224 ms with
NRW; 346–805 ms with three guest CPU hogs). Two failed attempts are recorded in the session log for exactly
that reason, plus the arithmetic: with `D_A ≈ 450 ms` a 450 ms gap lands at the far edge (a photo finish,
`aborted=false`). The measured runs above are the train with 100/150/200/250/300 ms gaps.

## What the lines prove

| task | claim | evidence |
|---|---|---|
| **5.3** | a superseded search stops, and the line says so | `slot=places gen=N aborted=true nativeMs=346/509/805` — three superseded walks, each stopped at its next abort check rather than running to completion |
| **5.3** | the line's fields and the coordinate rule | `slot=`, `gen=`, `aborted=`, `nativeMs=` only; no coordinate, no query text; `./gradlew checkNoCoordinatesInLogs` → exit 0, BUILD SUCCESSFUL |
| **5.6 (1)** | an aborted search returns no entries | every aborted request shows `candidates=0`, while the completed ones of the same train returned 42 and 39 |
| **5.6 (2)(3)** | the walk is walkable to a database boundary on a two-database install | the same queries took 19–166 ms with Andorra alone and 346–805 ms once NRW was installed, so the second database is walked; an aborted run stops at its next check (per database, per visited region), which is the bound the design states. The line carries no per-database timing, so "it stopped *before* the next database" is not separately readable from it — that specificity stays with the host predicate cases and the source's poll placement |
| — | the abort is bounded, not instant | the aborted runs took 346/509/805 ms: one of them exceeds a completed run of a different query, which is exactly the documented granularity (the poll sits per database, per scope region and inside the visitors) |

## Rejected alternatives (for the next run)

- **A bigger map alone is not enough with a fast one**: with Andorra only, a warm search is 6–20 ms, so the
  supersede window is ~20 ms wide — unreachable by injected typing. NRW's 784 MB is what made the train work.
- **Guest CPU load does not reliably stretch a tiny search** (three hogs: 38 ms; two hogs: 116 ms), though it
  did widen the *longest* runs to 805 ms.
- The AAOS AVD's own copy of NRW could not be borrowed: it lives in that device's **user 10**, which neither
  `run-as --user` nor `adb root` (production build) reaches.
