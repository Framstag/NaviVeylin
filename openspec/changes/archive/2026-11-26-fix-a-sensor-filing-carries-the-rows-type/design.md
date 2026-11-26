# Design — fix-a-sensor-filing-carries-the-rows-type

LOOP-ROUTE: guidelines/BacklogTooling.md §9

## Root cause

`tools/sensors/run.sh:282-287` assembles the finding `sensors_write` hands to `tools/issues/create.sh` from seven
named fields and never names a type. `create.sh` reads `.type` from that same finding (`:111`) and refuses a name
the account does not declare (`:437-441`), and `sensors_row_type` (`tools/sensors/lib.sh:126`) has answered a row's
type since #1069 — so the only broken joint is the payload object.

## The single fix

Two lines in `sensors_file`:

- `--arg type "$(sensors_row_type "$row")"` beside the existing `--arg class` / `--arg category`, and
- `type: $type` in the object literal.

An unmapped row yields the empty string, which `create.sh:437` reads as "no type named": no flag is passed and no
refusal is raised, so a registry whose mapping is thin files exactly as it did before. The engine still holds no
account value — the name comes from the registry through `sensors_row_type` (`D12`), which
`tools/check-portability.sh` re-checks.

## Blast radius

`tools/sensors/run.sh` — the condition filing only. The bound summary a run files past its filing bound goes
through the same function, so a summary carries the row's type too, which is what its row declares it carries.
`tools/issues/create.sh` is untouched: it is the seam, and it already accepts what this change starts sending.
Nothing in the Gradle build, the app or the loop reads this field.

## Alternatives the evidence eliminated

- **Derive the type in the writer.** Rejected: the writer is a project-agnostic engine and a class is this
  project's vocabulary while a type is the account's; `create.sh` reads a type it is handed or none (`D12`, and
  `sensors#engineNamesNoProjectValue` is the case that keeps project values out of engines).
- **Derive it in `sensors_write`.** Rejected: `sensors_write` is the verb wrapper and holds no row; the payload site
  is the one place that has both the row and the finding.
- **File the record issue typed in the same change.** Rejected as scope: `sensors_file_record`
  (`tools/sensors/run.sh:313`) builds a second payload, the sibling item child C of #1021 owns it, and one change
  that edits two payloads is a diff a reader cannot falsify in one round.

## Rollback

Revert the two lines; the case returns red with `'--type Bug' not found`, and no data was written — the tracker is
only reached through the stub in the self-test.
