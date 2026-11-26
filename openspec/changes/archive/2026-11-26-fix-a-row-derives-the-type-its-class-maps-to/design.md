# Design — a row's class derives the tracker type its item carries

LOOP-ROUTE: guidelines/BacklogTooling.md §9

## The root cause

One mechanism: the sensor tooling had no class-to-type mapping, and `sensors_row_problems`
(`tools/sensors/lib.sh:170`) validated a row's `class` against the profile's *label* vocabulary and stopped there.
A row's kind therefore reached its item as a label only. `Bug`, `Task` and `Feature` are the tracker account's own
names, so the engine may not hold either side — `sensors#engineNamesNoProjectValue`
(`tools/sensors/tests/sensor-mutations.sh:39`) is the invariant — and the mapping is project data. It belongs in
the registry, beside the `labels` and `escalation` the registry already declares (D12), and one new root key plus
one derivation function is the whole fix.

## The single fix

- The registry declares `classTypes` (`tools/sensors/sensors.json:9`; the fixture registry carries the same key),
  mapping `bug` → `Bug`, `improvement` → `Task`, `feature` → `Feature`.
- `sensors_row_type` (`tools/sensors/lib.sh:126`) answers a row's own `type` first — the override a person uses
  where the derivation's judgement is theirs, an `improvement` being a `Task` or a `Feature` — and the class's
  mapping otherwise, empty when neither names one.
- `sensors_row_problems` refuses a class the mapping does not cover as `row-type-unmapped`
  (`tools/sensors/lib.sh:236`), beside the class-vocabulary refusal at `:231`, so a registry that would file an
  untyped item is refused before any filing instead of after one.
- `guidelines/BacklogTooling.md:13` states the mapping in the row rule, because "a check is a declared row" is
  the section that says what a row declares; the added clause is at `:15`.

## Blast radius

One engine file, seven fixtures, one case file, one mutation record and one guideline section, counted before the
plan with `tools/look.sh 'classTypes'` (4 files at plan time) and `tools/look.sh 'sensors_row_type'` (2 files). The
fixture count grew when the refusal was run: five further fixture registries carry no mapping, so every case that
reads them reddened with a `row-type-unmapped` refusal about an entry it does not test — nine cases of the suite's
94, before the five carried the key and none after (`evidence/sensor-selftest-all-cases.log`). A registry fixture
that
exists to be incomplete is now incomplete in the one entry its own case names, not in the mapping as well. No filing
path is
wired to the derivation here: the two payload children of #1021 read it, which is why they are blocked by this
change and this change adds no `--type` to any create call. No shipped row loses its completeness — the 20 shipped
rows are 4 `bug` and 16 `improvement`, both mapped; the `feature` mapping has no live witness in the shipped
registry, so the new fixture is its witness.

## Rollback

Revert the change directory: the delta is one root key (`classTypes`), one function (`sensors_row_type`) and one
refusal branch (`row-type-unmapped`). Removing the branch restores the old completeness verdict exactly; the key and
the function are unreferenced once it is gone, so no other reader changes behaviour.

## Alternatives eliminated

- **A type in the engine.** Refused by `sensors#engineNamesNoProjectValue` and by the portability check: `Bug`,
  `Task` and `Feature` are account-scoped values, and an engine that names one binds every project to one
  tracker's vocabulary.
- **A type hard-coded on every row.** It would make the type a per-row restatement of the class, and the mapping
  would still be missing for the next row added; the row's own `type` survives as the override, not as the
  mechanism.
- **Deriving the type at filing time from the class name.** The class vocabulary and the account's type names are
  in different vocabularies (`improvement` ↔ `Task` inverts the word), so a derivation would have to hold the
  mapping in code — the value this change keeps in the registry instead.
