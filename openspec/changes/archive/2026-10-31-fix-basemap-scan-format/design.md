# Design

## D1 — The refusal belongs to the scan, not to a basemap exemption in the scan

Two candidates were on the table for #716:

1. Exempt the basemap subtree from the scan by path, as `DBThread` does for the render loop.
2. Skip any candidate whose data format this build does not read.

Candidate 1 cannot fire in the case #716 describes. The `DBThread` exemption keys on
`basemapLookupDirectory`, and the app leaves that unset precisely when it refuses the installed basemap — so a
scan-side exemption would be dead code in the one situation that needs it. It would also add basemap knowledge
to `MapManager.h`, which carries none today: an API surface plus app wiring for a guard that cannot run.

Candidate 2 is self-contained in the scan, needs no app knowledge of the basemap, and states the real rule: a
database this build cannot read does not belong in the registered set. It also covers the same class of
failure for a regional map downloaded at a version this build does not read — not only the basemap.

## D2 — An unreadable version decides nothing

`TypeConfig::GetDatabaseFileFormatVersion` throws when `types.dat` is missing, shorter than the version field,
or unreadable. Turning that into a skip would refuse a directory the reader can still explain — an empty
`types.dat` is exactly what a partially installed map looks like, and the app's own admission
(`BasemapDataFormat`) admits it unchanged for the same reason. The helper catches the exception, logs at debug
level and returns no value, and the scan publishes that directory as it always did. Only a version that reads
and differs produces a skip.

## D3 — The skip is one warning line per directory, and it carries no position

The scan already prints one line per found directory and one summary line per lookup directory, so a skipped
directory gets one line in the same voice, naming the directory, its version and this build's version. That is
what #716's own quoted lines lacked: the pair came from the reader, deep in the open path, after the directory
had already joined the set. The line carries a directory path and two integers — no coordinate, no user data —
and matches what the scan's existing `skipping invalid map directory:` line already prints.

The skip also increments the scan's existing `invalidCount`, so the summary's arithmetic stays honest
(`N candidates, V valid, I invalid`).

## D4 — The version is read once per candidate, and only when a candidate is found

The read is inside the branch that already found a `types.dat` and inside the existing `try`, so the scan's
cost per directory changes by one small file read on the host build; nothing on the render or query path
changes. A scan of K installed maps reads K+1 small headers, the same order as the `MapDirectory` construction
that follows it.

## D5 — The change stays in the submodule and needs no JNI surface

The scan is library behaviour behind an unchanged JNI contract, so the fix is one submodule commit on
`naviveylin-local` plus the gitlink bump — no Java declaration, no native-parity entry, no app-side edit. The
app's admission keeps its own job (withhold the overlay) and the scan gains the one it lacked (do not register
what the reader refuses).
