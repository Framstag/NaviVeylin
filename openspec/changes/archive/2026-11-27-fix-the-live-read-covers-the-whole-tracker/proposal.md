# The live read covers the whole tracker, not its newest 400

## Why

`tools/issues/check-metadata.sh:150` reads the tracker with `gh issue list --state all --limit 400`, and
`tools/sensors/lib.sh:476` (the rows' condition list, `sensors_issues_of_row`) reads it through the same fixed
window. `gh issue list` answers newest-created first, so the window is a slice of the tracker and not the tracker:
on 2026-11-26 this tree's tracker held 738 items (`gh issue list --state all --limit 2000 --json number | jq
length` → 738), so 338 of them were never checked — and the report printed
`check-metadata: 400 issue(s) checked · 0 violation(s) · 3 untyped`, which is the shape of a clean tracker. The
population that goes unread is the oldest one, and no guard notices the truncation: the only thing that separates a
read from an unreadable tracker is that `checked` is non-zero.

**Root cause, in one sentence:** a fixed window over a newest-first list stands in for the tracker, so the read
stops at the 400th newest item and the count it prints is read as the population it covered.

**The red case, on the base `5e9b8da4`** (`evidence/red-on-HEAD.log`; the shell runner writes no JUnit XML, so the
log is kept beside its tally): `metadata#theLiveReadDoesNotTruncateTheTracker` — a stub tracker of 401 items whose
only drift (a `prio/p2` label over a `band=p1` marker) sits on its **oldest** item. The base prints

```
check-metadata: 400 issue(s) checked · 0 violation(s) · 0 untyped
```

and exits 0, where the drift it never read belongs in that report. The same fixture through the tool's own
`--live` half, by hand, is `evidence/repro-1091.log`.

**Owning requirement:** `openspec/specs/backlog-sensors/spec.md`, "The priority pair is re-checked on a schedule"
(`:539`) — the scheduled re-read of the tracker's priority invariant, which a truncated read silently half-does.

**Owning guideline section:** the rule that a read is the set and not a window over it —

- LOOP-ROUTE: guidelines/Backlog.md §5 — "The queue reads the whole labelled set, and refuses one it could not
  cover" (`:260`). This change makes the two readers that rule's too; its text names the queue as its example and
  needs no rewording.

surface: 1 case added, 0 rewritten, 0 fixtures realigned — the fixed bound sat on 4 engine sites
(`tools/look.sh 'gh issue list .*--limit 400' --files`, 1 match in `tools/issues/resolve-stale.sh` left today), and
this change moves three of them (`tools/issues/check-metadata.sh`, `tools/sensors/lib.sh`, `tools/issues/lib.sh`)
onto one shared read.

## What Changes

- **One read, to the list's end.** `tools/issues/lib.sh` gains `issues_issue_list_all <json fields>`: the same
  `gh issue list --state all` call, whose window doubles until a page comes back short. A short page is the answer
  that nothing was cut off, so the reader holds the tracker; a tracker inside the first window still costs the one
  call it always cost.
- **The metadata pass reads through it.** `tools/issues/check-metadata.sh:150` asks for its fields through the
  helper, so its `checked` count is the tracker's own population and its per-item half reaches every item.
- **The dedup and selection queries read through it too.** `issues_issue_list` (`tools/issues/lib.sh:329`) is the
  same read with its own field list, so it asks the helper rather than a window: `create.sh` and `import.sh` search
  for a duplicate over the whole tracker, and the item a window drops is the one they cannot find.
- **The rows' condition list reads through it.** `tools/sensors/lib.sh:476` asks for its fields through the same
  helper, so `sensors_issue_for_fingerprint` can see an item older than any one window — the row can close it on a
  clear and comment on it on a return instead of filing beside it.
- **A case that fails on the base.** `metadata#theLiveReadDoesNotTruncateTheTracker` in
  `tools/issues/tests/issue-tooling-cases.sh`: 401 items, the drift on the oldest, `--live` must name it and report
  the whole tracker.

The row's own guard is unchanged: a non-zero `checked` is still the only thing that separates a read from an
unreadable tracker.

## Impact

- Affected spec: `backlog-sensors` — one added requirement, one scenario, one case.
- Affected code: `tools/issues/lib.sh` (the helper, and `issues_issue_list` delegating to it),
  `tools/issues/check-metadata.sh`, `tools/sensors/lib.sh` (the two call sites),
  `tools/issues/tests/issue-tooling-cases.sh` (the case).
- Not changed: the one remaining fixed-400 site (`tools/issues/resolve-stale.sh:116`, the citation repair) — the
  same class of defect, outside this item's cited pair, and left named rather than half-fixed.
- No compiled file, no device: the gate for this pathspec is the issue tooling's own shell suite.
