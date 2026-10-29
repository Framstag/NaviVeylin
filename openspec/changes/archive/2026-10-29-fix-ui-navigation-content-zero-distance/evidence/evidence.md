# Evidence — fix-ui-navigation-content-zero-distance

## Measurement block

```
LOOP-MEASURE-PATH guidelines/UI.md
LOOP-MEASURE-CHECK tools/check-doc-routes.sh
LOOP-MEASURE-CHECK tools/check-doc-routes-selftest.sh
LOOP-MEASURE-CHECK tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list
LOOP-MEASURE-CHECK tools/check-todo-references.sh
LOOP-MEASURE-SUITE cases=276 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=3 suites=4
LOOP-MEASURE-CHANGE fix-ui-navigation-content-zero-distance scenarios=0 cases=0 unnamed=0
LOOP-MEASURE-TALLY openspec/changes/fix-ui-navigation-content-zero-distance/evidence/green-after-fix.xml tests=1 failures=0
LOOP-MEASURE-TALLY openspec/changes/fix-ui-navigation-content-zero-distance/evidence/red-on-head.xml tests=1 failures=1
LOOP-MEASURE-SURFACE surface: 1 guideline bullet edited, 1 evidence probe case added, 0 Gradle cases added, 0 cases rewritten,
```

The full block is `evidence/measure.txt`; `LOOP-MEASURE-SURFACE` is truncated at the measure tool's line bound,
so the proposal's `surface:` line carries the rest (`0 fixtures realigned`).

## Red and green cases

`evidence/red-on-head.xml` — `tests=1 failures=1`, timestamp `2026-10-29T07:30:05Z`:

```
FAIL ui-navigation-content#zero-versus-unknown — the NAVIGATION content bullet does not state:
  [known zero] [`0 m`] [absent or `NaN`] [`--`] [stats line] [hint's secondary text]
  [navigation-ongoing-notification]
```

`evidence/green-after-fix.xml` — `tests=1 failures=0`, timestamp `2026-10-29T07:30:48Z`:

```
PASS ui-navigation-content#zero-versus-unknown — the NAVIGATION content bullet states the zero-versus-unknown distance rule on both surfaces
```

## Gate

`./tools/loop/gate.sh` ran once; its transcript is `evidence/gate.log`, its tallies are
`evidence/gate-tallies/`, and its summary line is:

```
LOOP-GATE: tests=0 failures=0 errors=0 (tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 1s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 1s, tools/check-todo-references.sh 1s)
```

The suites print their own case counts (`check-doc-routes-selftest: 19 passed, 0 failed`); the gate's JUnit
tallies are `tests=0` because the two shell checks report through their transcript rather than per-case XML.

## Validation

`openspec validate fix-ui-navigation-content-zero-distance --strict` → `Change
'fix-ui-navigation-content-zero-distance' is valid` and the INFO that `skip_specs` is set
(`evidence/openspec-validate.log`). No capability spec changes: this is a documentation-only change.
