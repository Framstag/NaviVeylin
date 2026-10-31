# The `document-size` condition, before and after

Run 2026-10-31: `bash tools/sensors/run.sh --only document-size` (bound **32 000 B**, registry
`tools/sensors/sensors.json`).

## After the cut — 1 condition

    23 rows measured, 22 normative documents, every one under the bound
    doc-context: UICar.md bytes=21562 lines=298 (bound 32000 bytes)
    doc-context: UI.md bytes=24881 lines=339 (bound 32000 bytes)
    dry-run: would file a new issue for document-size (fingerprint 01167e7d…, measurement 35887)
    document-size: verdict=violation measurement=35887 issue=- action=filed

The one row still over the bound is `guidelines/Backlog.md` at **35 887 B** — the fourth and last cut
(`guideline-backlog-cut`), not this change's subject. It is the same single violation the cut before
this one reported, so this change moved the count from **2 → 1** and introduced none.

## Before the cut — 2 conditions

`guidelines/UI.md` 45 327 B and `guidelines/Backlog.md` 35 887 B were the two rows over 32 000 B.

## What the cut cost the corpus

`UI.md` 45 327 → 24 881 B and `UICar.md` 21 562 B: 46 443 B where there were 45 327 B. The corpus grew
by **1 116 B** — the price of a second preamble (1 048 B across the two, where one document had one)
plus 68 B of named edits inside three moved sections and §20. Both halves have room: 7 119 B and
10 438 B under the bound. The largest section anywhere in the pair is §10 at 8 816 B, against the
12 000 B section bound.
