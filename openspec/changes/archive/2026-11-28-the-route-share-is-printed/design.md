# Design — the route share is printed

## Open decisions

none

## D1 — One definition of the resolution, in `lib.sh`

`tools/loop/evidence-check.sh:218` reads a document's heading ids with one `sed` and `:219` compares the cited id
against them with `grep -qxF`. That pair is the rule `#1081`'s requirement states, and a report that restated it
would be a second definition of the same rule — the failure `tools/loop/lib.sh` exists to prevent, and the one a
mutation record could then falsify in one file while the other stayed green.

So the rule moves to `tools/loop/lib.sh`, in three functions:

- `loop_route_lines <dir>` → the anchored `LOOP-ROUTE:` lines of the markdown under `<dir>`, by the anchor rule C6
  already applies (`^[[:space:]>*-]*LOOP-ROUTE:`), so a paragraph quoting the form is not a line here either.
- `loop_route_ids <doc>` → the ids the document's own `## ` headings carry, by the two shapes this project writes
  (the id followed by a `.` and whitespace, and the id written after the section sign) and no other.
- `loop_route_resolves <root> <doc> <id>` → 0 when `<doc>` sits under the configured guideline directory, exists,
  and carries `<id>`.

C6 keeps every verdict it prints and every count it keeps; it calls the three functions instead of holding the
rule. The configuration the directory comes from (`LOOP_GUIDELINE_DIR`, `loop.conf`) is read by the caller and
passed in, so `tools/check-portability.sh` still finds no project value in an engine.

## D2 — The population is the items that wrote a report, and it is read from the record the run already keeps

`#974`'s baseline is "the share of an item's reports whose route resolves … 55 % of the items that wrote a report",
so the denominator is an item whose record names a change, and the numerator is an item whose change's every route
line resolves. Both are read from the fields `state.sh done` already writes:

- `change` — the change the item worked, present on every item that reached a landed attempt;
- the change directory — `loop_change_path` for a live change, `loop_change_archive_dir` for the archived one, tried in
  that order, because a landed item's change is an archive entry by the time a run's report is read.

The lookup is an index built once (`route_dir_index`) rather than one `loop_change_archive_dir` call per item: that
call walks the archive's directories, this tree holds 788 of them, and the per-item form made one `report.sh` run take
2 m 19 s against 3.9 s with the index. The index is keyed by the name `loop_change_path` derives — the name a change
carries after its archive's prefix is stripped — with the live directory assigned last so it wins, and it aliases the
archived directory's own spelling to that key because a record keeps the name the change had when the item started.

An item whose record names no change is outside the denominator, and so is a change carrying no `LOOP-ROUTE:` line.
Counting either as a miss would print a figure that falls as the tree heals in the one direction the line exists to
expose — the same reason `bound_share` (`report.sh:328`) counts only the records that *can* name a mechanism.

**No record field is added.** A field would have to be written by a run that predates it, so the archive — which is
the only side the line is compared against — would answer nothing until it aged.

## D3 — The figure is printed beside the bound rate, and it names its denominator

The line goes in `tools/loop/report.sh` beside the `bound:` line (`report.sh:393`), because both answer "how often
does this run miss a rule it enforces", and both are read in the same pass over the state file:

```
route: 2 of 3 item(s) whose report routes (66.7 %) · archive: 41 of 96 (42.7 %)
```

`-` in place of the share when the denominator is zero, which is `bound_share`'s own answer and keeps a run that
attempted nothing from printing `0.0 %`. The archive is read in the one-pass shape `report.sh:373` already uses for
the bound rate, over the same run files and with the run being reported left out.

## D4 — No configuration key, and no mode

`LOOP_EVIDENCE_ROUTE` decides whether a *missing* route line is a warning or a failure, and that judgement is the
project's. How a share is computed is not: it is stated by the requirement, so `loop.conf` gains nothing. A project
that configures no guideline directory prints the line as `-` with the reason, not as `0.0 %`.

## D5 — What the case asserts, and what the mutation falsifies

`report#theRouteShareIsPrinted` builds a state file with three items — a resolving route, an unresolvable one, and
none — and asserts the line's numbers, so the numerator, the denominator and the archive clause are each visible.
`report#anItemWithNoRouteIsOutsideTheCount` asserts the denominator alone, which is the half a numerator-only case
hides. One record in `tools/loop/tests/loop-mutations.sh` makes `loop_route_resolves` answer true for every id, and
both `report#theRouteShareIsPrinted` and `check#aRouteSectionResolvesAgainstItsDocument` must fail on it: one
mutation, two readers, which is what D1 buys.
