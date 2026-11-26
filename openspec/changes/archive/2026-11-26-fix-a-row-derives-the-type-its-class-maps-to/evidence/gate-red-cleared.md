# The red gate this branch carried, and what cleared it — 2026-11-26

The run that made this change ended its gate red on one step, `tools/tests/check-portability-selftest.sh`, whose
case `foreign#toolchainRunsEndToEnd` refused the foreign project's sensor row:

    the sensor row failed in the foreign project: row-incomplete: foreign-row: row-type-unmapped: improvement

That refusal belongs to this change: `classTypes` became a required registry root, so every registry that declares
a class has to map it. The shipped registry and the six upstream test fixtures gained the mapping in the change
itself; the stand-in for *another* project did not, because it is the file a reader least expects this change to
touch.

The repair, and nothing else:

- `tools/tests/fixtures/foreign-project/tools/sensors/sensors.json` — a `classTypes` root, in the foreign project's
  own type names (`Defect`, `Maintenance`, `Enhancement`) rather than this project's (`Bug`, `Task`, `Feature`), so
  an engine that knew a type name would fail the fixture instead of passing it by accident.
- `tools/tests/fixtures/foreign-project/gh/issue-types.json` — the foreign account's declared types, which the
  writer validates a filing's `type` against. The fixture had none because no filing carried a type before this
  change; one entry is declared and disabled, as a listing with no disabled type would pass a reader that ignores
  `isEnabled`.

No engine file and no case changed: the guard, the derivation, the case and mutation 8.36 are the change's own.

Re-measured on the branch: `LOOP-GATE: tests=364 failures=0 errors=0` (`evidence/gate.log`), the whole
`tools/tests/check-portability-selftest.sh` green including `foreign#toolchainRunsEndToEnd`,
`foreign#fixtureIsVersioned` and `foreign#fixtureDeclaresEveryBudgetDefault`.

`evidence/gate-red.log` — the tally this branch carried, one line, kept by `end-item` as the reason the branch is
its owner's — is removed with this repair, because the verdict it records no longer holds. The tally it carried read:

    tests=364 failures=1 errors=1 … — failing: tools/check-openspec-hygiene.sh tools/tests/check-portability-selftest.sh

The second of those two reds, `tools/check-openspec-hygiene.sh`, was never this change's: it named open change
directories beside the worktree. It passes on the branch's tree in the re-measured gate.
