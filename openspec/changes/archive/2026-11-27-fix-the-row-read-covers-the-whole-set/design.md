# Design — the counted set, and the one read that covers it

## Root cause

One mechanism: the row's read asked for a constant window instead of the set the row's label lives in. The row's
selection is `sensors_label "$1"` — a label over the tracker — while the read that must reach it was
`gh issue list --state all --limit 400` — `tools/sensors/lib.sh:476` on the base, `800d1d5d`, and the line that
asks for the counted window after this change — and `gh issue list` answers newest-created first.
Once the tracker holds more items than the window (`762` on 2026-11-27), an older item carrying the row's label is not
in the answer, so the condition list is short by exactly the items the row most needs to see: the ones already filed,
which it would have commented on or closed.

The second half is what the short answer costs. `sensors_file_record` decides "comment on the record" or "file beside
it" from the fingerprint lookup over the row's own issues, so a missed item is not a missing comment but a new item
filed beside the old one — a duplicate per return. `report.sh` builds the row's counting line from the same read, so
the report states a row's history from a set it did not cover.

## The single fix

`sensors_issues_of_row` reads the whole set, in the shape `#1096` landed for every other deciding reader
(`tools/issues/lib.sh`): `issues_set_window all` (the tracker's own count plus one), `gh issue list --state all --limit
"$window"`, then `issues_set_covered "$((window - 1))" "$window" "$(jq 'length' …)"`. The label filter, the field list
(`createdAt` among them, because one write produces an issue and its marker — `#668`) and the JSON-array output are
unchanged, so every caller keeps its contract.

Why the count rather than "page until a short page": the count and the refusal are one mechanism, already implemented
once and already the rule for the sibling readers. A paging loop would be a second whole-set reader in this tree and
would leave the "read came back full" case unrefused.

Why `issues_set_covered` with `window - 1`: `issues_set_window` asks for the counted set plus one, so a read smaller
than the window is the whole set and a read *at* the window is proof the read could not have covered it. The
refusal (`issue-set-uncovered`, exit 3) is `issues_refuse`'s own, so the row's read refuses in the same voice as the
metadata check and the repair passes.

## Blast radius

- `tools/sensors/lib.sh` — one function, `sensors_issues_of_row`; two callers (`tools/sensors/run.sh:828`,
  `tools/sensors/report.sh:43`). One extra tracker round trip per row read (the count) — the read itself is one
  request, not a page loop.
- `guidelines/BacklogTooling.md` §9 — one added rule, stating for a check's own read what the section already states
  for the sweep it was written for.
- `tools/sensors/tests/sensor-cases.sh` — one case, `lib#theRowReadCoversTheWholeSet`, in the `lib#` group, and its
  two dispatch entries.

Nothing else: no compiled file, no `tools/issues/**` edit (that half is on master through `#1096`), no rename, no
`tools/tooling.json` exception, no gate waiver.

## Rollback

Revert the three files; the function returns to the fixed window and the case to red. Nothing is migrated, nothing is
written outside the change's own `evidence/`.

## What this change does not do

- `tools/loop/run.sh:674-684` and `tools/issues/labels.sh:53` also pass `--limit 400`: the first is the loop's decision
  queue (a `list.sh` read, its own item), the second is `gh label list` (labels, not issues). Neither is the read a
  check row is decided on, so neither is this delta's.
- The two call sites capture the function's output and do not propagate its exit status, so a refusal inside the read
  is printed and the caller continues with an empty set. That is a defect of the same family, in the callers rather
  than in the read; it is named in this item's report as a `NOTE:` rather than repaired here, because the recorded
  decision scopes this change to the read and its case.

## Alternatives eliminated

- **Page until a short page** — a second whole-set reader, and no refusal for the case where a page comes back full.
- **Raise the window to the tracker's size at the call site** — the caller would then own the set's coverage, which is
  what `issues_set_covered` exists to own; two callers would drift.
- **Land the branch of the earlier attempt as it stands** — rejected by the recorded decision (C): its metadata half is
  on master, so it would be a second whole-set reader and a content conflict in `tools/issues/lib.sh`.
