# Proposal — a check's filing carries the type its row declares

## Why

**Root cause.** `sensors_file` (`tools/sensors/run.sh:268`) builds the finding the writer is handed from seven
fields — `{title, class, category, claim, fingerprint, labels, body}` — at `tools/sensors/run.sh:282-287`, and
`type` is not one of them. The writer already accepts one (`tools/issues/create.sh:111`,
`type_name=$(jq -r '.type // ""' <<< "$finding")`) and refuses a name the account does not declare before anything
is written (`:437-441`, `type-opaque`), and #1069 taught the registry to derive a type from a row's class
(`tools/sensors/lib.sh:126`, `sensors_row_type`). Nothing joins the two, so every condition a check filed arrived
untyped even after the registry mapped a type for its row.

**The red case on the base** `37b3b83fa80a8f6ea0a241831e6eb798b4bba69d`: `run#aFilingCarriesTheRowsType`
(`tools/sensors/tests/sensor-cases.sh:1180`), one case with two scenarios — a `bug`-class row files with
`--type Bug`, and a row that declares its own `type` files with that one instead. On the base the first scenario
prints `'--type Bug' not found in: gh issue create --title fixture-emit: 9 …` (`evidence/red-on-HEAD.log`); the
stub's recorded write carries six `--label` flags and no `--type`. After the change the case is green
(`evidence/green-aFilingCarriesTheRowsType.log`) and record `8.37` reddens it by emptying the payload's type
(`evidence/mutation-8.37.log`).

**Surface, counted before the plan:**

```
$ tools/look.sh 'sensors_row_type'
LOOK: 21 matches in 10 files … tools/sensors/lib.sh 2 · tools/sensors/run.sh 1
      · tools/sensors/tests/sensor-cases.sh 3 · tools/sensors/tests/sensor-mutations.sh 1
      · the archived #1069 change (10)
$ tools/look.sh 'aFilingCarriesTheRowsType'
LOOK: 16 matches in 9 files … tools/sensors/tests/sensor-cases.sh 3 · this change's own markdown and evidence (7)
```

surface: 1 source file (2 net lines), 1 case added at three sites, 1 mutation record, 1 guideline section, 0
fixtures — `tools/look.sh 'sensors_row_type'` and `tools/look.sh 'aFilingCarriesTheRowsType'`

so the plan's files are one source file (two net lines), one case file (three sites: the case index, the
dispatcher and the body), one mutation record and one guideline section. No fixture: the row that overrides its
type is a `jq`-patched copy of the fixture registry, so the case adds none.

## What Changes

- `tools/sensors/run.sh` (`sensors_file`, `:282-287`): the finding a filing is built from names the row's type —
  `--arg type "$(sensors_row_type "$row")"` and `type: $type` — so the writer files the item typed.
- `openspec/specs/backlog-sensors/spec.md`: the requirement "A check's issues arrive classified and unauthorised"
  states that the type reaches the writer as the tracker's own field, in the same write as the labels.
- `tools/sensors/tests/sensor-cases.sh`: `run#aFilingCarriesTheRowsType`.
- `tools/sensors/tests/sensor-mutations.sh`: record `8.37`, which empties the payload's type.
- `guidelines/BacklogTooling.md` §9: the row rule states that the filing site carries what the row declares.

**Owning requirement.** `openspec/specs/backlog-sensors/spec.md:357` — "A check's issues arrive classified and
unauthorised": an issue filed by a check SHALL carry the kind and area its row declares and the tracker type that
kind maps to. The type half of that sentence was never made true at the payload site.

**Owning guideline section.** `guidelines/BacklogTooling.md` §9 "Checks", the row rule at `:13-16`.

**What this change does not do.** The record issue a green row files (`sensors_file_record`,
`tools/sensors/run.sh:313`) builds its own payload and is the sibling item child C of #1021 named in the tracker,
not this one: two payload sites in one file is one change too many, and the two would disagree about a bound.
