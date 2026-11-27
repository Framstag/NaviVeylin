# Proposal — the record a green row files carries the row's type

## Why

**Root cause.** `sensors_file_record` (`tools/sensors/run.sh:314`) builds the record issue a green row files — the
item a row's measurements go to, held on the registry's own `record.status` so no selector takes it — from seven
fields at `tools/sensors/run.sh:341-345`, and `type` is not one of them. The writer already reads one
(`tools/issues/create.sh:111`, `type_name=$(jq -r '.type // ""' <<< "$finding")`) and refuses a name the account
does not declare (`:437-441`, `type-opaque`), #1069 taught the registry to derive a type from a row's class
(`tools/sensors/lib.sh:126`, `sensors_row_type`), and #1070 carried it at the condition filing
(`sensors_file`, `tools/sensors/run.sh:282-287`). This second payload site was not touched by any of the three, so a
record arrived untyped and `tools/issues/create.sh:111` had nothing to read at it.

**The red case on the base** `f4234e6afe4134219e91664b701b2e5a4b80c7f6`: `run#aRecordIssueCarriesTheRowsType`
(`tools/sensors/tests/sensor-cases.sh:1222`), one case — a `bug`-class clear finding with no condition issue files
the row's record and the stub's `issue create` line must carry `--type Bug`. On the base it prints `'--type Bug' not
found in: gh issue create --title sensor record: fixture-emit …` (`evidence/red-on-HEAD.log`): the recorded write
carries six `--label` flags and no `--type`. After the change the case is green
(`evidence/green-aRecordIssueCarriesTheRowsType.log`), and record `8.38` reddens it by emptying this payload's type
(`evidence/mutation-8.38.log`).

**Surface, counted before the plan:**

```
$ tools/look.sh 'aRecordIssueCarriesTheRowsType'
LOOK: 4 matches in 2 files … tools/sensors/tests/sensor-cases.sh 3
      · tools/sensors/tests/sensor-mutations.sh 1
$ tools/look.sh 'sensors_file_record'
LOOK: 10 matches in 8 files … tools/sensors/run.sh 2 · tools/sensors/tests/sensor-cases.sh 1
      · the archived #257 and #1070 changes (7)
```

surface: 1 source file (2 net lines), 1 case added at three sites, 1 mutation record, 1 guideline section, 0
fixtures — `tools/look.sh 'aRecordIssueCarriesTheRowsType'` and `tools/look.sh 'sensors_file_record'`

so the plan's files are one source file (two net lines), one case file (three sites: the case index, the dispatcher
and the body), one mutation record and one guideline section. No fixture: the case drives the fixture registry the
other `run#` cases drive, whose `fixture-emit` row declares the `bug` class the registry maps to `Bug`.

## What Changes

- `tools/sensors/run.sh` (`sensors_file_record`, `:341-345`): the finding the record is built from names the row's
  type — `--arg type "$(sensors_row_type "$row")"` and `type: $type` — so the writer files the record typed, as it
  already files a condition.
- `openspec/specs/backlog-sensors/spec.md`: the requirement "A check's issues arrive classified and unauthorised"
  states that every issue a check files carries the row's type, and that a green row's record is such an issue.
- `tools/sensors/tests/sensor-cases.sh`: `run#aRecordIssueCarriesTheRowsType`.
- `tools/sensors/tests/sensor-mutations.sh`: record `8.38`, which empties this payload's type.
- `guidelines/BacklogTooling.md` §9: the row rule's pointer names both payload sites, so a reader asking why a filed
  item arrived untyped is sent to the record's payload as well as the condition's.

**Owning requirement.** `openspec/specs/backlog-sensors/spec.md:357` — "A check's issues arrive classified and
unauthorised": an issue filed by a check SHALL carry the kind and area its row declares and the tracker type that
kind maps to. The record a green row files is an issue a check files, and the type half of that sentence was never
made true at its payload site.

**Owning guideline section.** `guidelines/BacklogTooling.md` §9 "Checks", the rule at `:17-20`.

**What this change does not do.** It does not touch the condition filing's payload (`:282-287`), which #1070
landed, nor `tools/issues/create.sh`, which is the seam and already accepts a type. It adds no second case for the
class-to-type derivation: `Registry#everyRowIsComplete` and `run#aFilingCarriesTheRowsType` own that, and this case
asserts only that the record's write carries the type its row declares.
