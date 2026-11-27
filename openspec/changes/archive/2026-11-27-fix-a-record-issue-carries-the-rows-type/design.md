# Design — fix-a-record-issue-carries-the-rows-type

LOOP-ROUTE: guidelines/BacklogTooling.md §9

## Root cause

`tools/sensors/run.sh:341-345` assembles the record issue's finding — the payload `sensors_file_record` hands to
`tools/issues/create.sh` — from `{title, class, category, claim, fingerprint, labels, body}` and never names a type.
`create.sh:111` reads `.type` from that same finding, `sensors_row_type` (`tools/sensors/lib.sh:126`) has answered a
row's type since #1069, and `sensors_file` (`:282-287`) already sends one at the condition site since #1070. The only
broken joint is this payload object: no type is sent, so `create.sh` passes no `--type` flag and the record arrives
untyped.

## The single fix

Two net lines in `sensors_file_record`:

- `--arg type "$(sensors_row_type "$row")"`, and
- `type: $type` in the object literal.

An unmapped row yields the empty string, which `create.sh:437` reads as "no type named": no flag is passed and no
refusal is raised, so a registry whose mapping is thin files its records exactly as it did before. The engine still
holds no account value — the name comes from the registry through `sensors_row_type` (`D12`), which
`tools/check-portability.sh` re-checks.

The type argument rides the record's `--arg claim` line rather than a line of its own, and that is a deliberate
choice of the mutation's: the condition site's `--arg type` line is byte-identical to a standalone one here, so a
`sed` expression could not tell the two payloads apart, and record `8.38` would redden the condition's case or
neither. The record's claim expression (`$claim`, against the condition site's `jq -r '.claim // .measurement'`) is
what distinguishes the two lines, so the type is stated beside it.

## Blast radius

`tools/sensors/run.sh` — the record filing only. `sensors_file` (`:282-287`) is untouched, so the condition filing
keeps the payload #1070 verified; `tools/issues/create.sh` is untouched: it is the seam, and it already accepts what
this change starts sending. The number `sensors_file_record` prints is read by `sensors_record_number` (`:408`) and
is unaffected — the writer's output is unchanged in shape. Nothing in the Gradle build, the app or the loop reads
this field.

## Alternatives the evidence eliminated

- **Derive the type in the writer.** Rejected: the writer is a project-agnostic engine and a class is this project's
  vocabulary while a type is the account's; `create.sh` reads a type it is handed or none (`D12`, and
  `sensors#engineNamesNoProjectValue` is the case that keeps project values out of engines).
- **Assert the type inside `run#aFilingCarriesTheRowsType` instead of adding a case.** Rejected: the record is
  reached by a path the condition filing never takes — its number comes back from the writer's own stdout — so an
  assertion there would be paid on a run that never files a record.
- **Carry the type by adding a label.** Rejected: a type is the tracker's own field, not one more label, and
  `create.sh:111` reads the field rather than the labels.

## Rollback

The change is the revert of its own two lines: dropping `--arg type` and `type: $type` returns the record to the
seven-field payload, and `run#aRecordIssueCarriesTheRowsType` reddens again on the next run — which is exactly what
record `8.38` proves by doing it.
