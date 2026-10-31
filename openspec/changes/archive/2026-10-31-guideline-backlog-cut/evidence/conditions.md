# The `document-size` condition, after the cut — 0

Run 2026-10-31: `bash tools/sensors/run.sh --only document-size` (bound **32 000 B**, registry
`tools/sensors/sensors.json`).

    doc-context: UICar.md bytes=21562 lines=298 (bound 32000 bytes)
    doc-context: UI.md bytes=24881 lines=339 (bound 32000 bytes)
    doc-context: UIPhoneMap.md bytes=29340 lines=382 (bound 32000 bytes)
    doc-context: UIPhone.md bytes=19395 lines=261 (bound 32000 bytes)
    dry-run: would file the record issue for document-size (fingerprint record-document-size, status on-hold)
    document-size: verdict=clear measurement=none issue=- action=recorded on the row's record

**verdict=clear, 0 conditions.** This change took the row from **1 → 0**: `Backlog.md` at 35 887 B was the
last document over the bound, and it is now 26 032 B with `BacklogTooling.md` at 10 829 B beside it.

## The whole set's effect on the row

| document | before the four cuts | after |
|---|---|---|
| `UIPhone.md` | 47 926 | 19 395 (+ `UIPhoneMap.md` 29 340) |
| `MapRendering.md` | 46 751 | 30 344 (+ `MapFrames.md` 11 040, `MapRoute.md` 3 425) |
| `UI.md` | 45 327 | 24 881 (+ `UICar.md` 21 562) |
| `Backlog.md` | 35 887 | 26 032 (+ `BacklogTooling.md` 10 829) |

Every normative document is now under 32 000 B and every section under 12 000 B — the largest section in the
corpus is `BacklogTooling.md` §9 at 5 837 B after the retargets (`UI.md` §10 is 8 816 B, and is the largest
anywhere).

## What the cut cost

`Backlog.md` 35 887 → 26 032 B and `BacklogTooling.md` 10 829 B: 36 861 B where there were 35 887 B — **+974 B**,
the second preamble, exactly the shape every cut in this set has had.
