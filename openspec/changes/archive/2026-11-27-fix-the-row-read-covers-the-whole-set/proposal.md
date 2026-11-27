# The row's own read covers the whole tracker

## Why

`sensors_issues_of_row` is the one read a check row is decided on: its condition list, the record it comments on, and
the fingerprint lookup that says whether a return is that record or a second filing. It asked the tracker for a fixed
window — `gh issue list --state all --limit 400` (`tools/sensors/lib.sh:476` on the base, `800d1d5d`) — and the
list answers newest-created first, so every item older than the newest 400 is invisible to the row that owns it. Measured on this tree,
2026-11-27T22:16Z: `gh issue list --state all --limit 2000 --json number | jq length` → `762`, i.e. 362 items sit
outside the window the row asks for.

What makes it a defect rather than a tuning knob is what the row reports when it misses: the condition list comes back
empty, so the row neither comments on the item it already owns nor closes it on a clear, and `sensors_file_record`
files a second item beside it — while `report.sh` prints a counting line for a set it never read. The sibling half of
this defect (the metadata check's read) is already on master through `#1096`, which is why this delta is the row's read
alone.

Root cause, owning requirement and route:

- **root cause** — the row's selection is a label over the whole tracker, but the read that selects it was a constant
  of the tool (400) rather than the size of the set the label is in, and nothing in the read refused a window it could
  not cover.
- **requirement** — `backlog-sensors`: "The row's own read covers the whole tracker" (added by this change). The
  whole-set read's own shape is `issue-backlog`'s, landed by `#1096` in `tools/issues/lib.sh`:352-372.
- **route** — `guidelines/BacklogTooling.md` §9, the section that owns what a check reads and what its bound is.

## What changes

- `tools/sensors/lib.sh` — `sensors_issues_of_row` reads through `issues_set_window all` (the tracker's own count plus
  one) and calls `issues_set_covered` on what came back, so a read that fills its own window is refused with
  `issue-set-uncovered` instead of being reported as the row's set. The label filter, the fields requested and the
  function's output shape stay as they are.
- `guidelines/BacklogTooling.md` §9 — one rule: a check's own selection is read as the tracker's whole set; a fixed
  window is a narrowing that fills its window and is refused, never a coverage.
- `tools/sensors/tests/sensor-cases.sh` — one case, `lib#theRowReadCoversTheWholeSet`: a 401-item fixture whose row
  label sits on its oldest item only, where the condition list, the fingerprint lookup and the refusal of an
  uncoverable read are all asserted.

## Impact

Three files, no compiled file: the engine's row read (one function), the section that states the rule, and the case
that guards it. Nothing outside `tools/sensors/**` and that one section is touched; the metadata half of `#1091` is
already on master and is not re-landed here.
